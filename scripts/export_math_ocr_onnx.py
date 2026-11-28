import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
import torch
import torch.nn as nn
import torch.nn.functional as F
from onnxruntime.quantization import QuantType, quantize_dynamic
from PIL import Image

MODEL_REPO = Path(r"C:\Users\hugoa\Downloads\mathwriting-2024\mathwriting-2024")
DEFAULT_FINETUNED = MODEL_REPO / "checkpoints" / "best_finetuned.pt"
DEFAULT_BASE = MODEL_REPO / "checkpoints" / "best.pt"
REAL_IMAGES_DIR = MODEL_REPO / "real"
OUTPUT_DIR = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "math_ocr"


class EncoderWrapper(nn.Module):
    def __init__(self, model: nn.Module):
        super().__init__()
        self.image_encoder = model.image_encoder

    def forward(self, images: torch.Tensor) -> torch.Tensor:
        return self.image_encoder(images)


class DecoderWrapper(nn.Module):
    def __init__(self, model: nn.Module, num_heads: int):
        super().__init__()
        self.token_embedding = model.token_embedding
        self.position_embedding = model.position_embedding
        self.decoder_layers = model.decoder_layers
        self.classifier = model.classifier
        self.num_heads = num_heads
        self.head_dim = model.hidden_dim // num_heads

    def _mha(self, mha: nn.MultiheadAttention, query: torch.Tensor, key: torch.Tensor, value: torch.Tensor, attn_mask: torch.Tensor | None = None) -> torch.Tensor:
        bsz, tgt_len, embed_dim = query.shape
        src_len = key.shape[1]
        w = mha.in_proj_weight
        b = mha.in_proj_bias

        w_q, w_k, w_v = w.split(embed_dim, dim=0)
        b_q, b_k, b_v = b.split(embed_dim, dim=0)

        q = F.linear(query, w_q, b_q).view(bsz, tgt_len, self.num_heads, self.head_dim).transpose(1, 2)
        k = F.linear(key, w_k, b_k).view(bsz, src_len, self.num_heads, self.head_dim).transpose(1, 2)
        v = F.linear(value, w_v, b_v).view(bsz, src_len, self.num_heads, self.head_dim).transpose(1, 2)

        scores = torch.matmul(q, k.transpose(-2, -1)) * (self.head_dim ** -0.5)
        if attn_mask is not None:
            scores = scores + attn_mask.unsqueeze(0).unsqueeze(0)
        attn = torch.softmax(scores, dim=-1)
        out = torch.matmul(attn, v).transpose(1, 2).reshape(bsz, tgt_len, embed_dim)
        return mha.out_proj(out)

    def forward(self, decoder_ids: torch.Tensor, memory: torch.Tensor) -> torch.Tensor:
        length = decoder_ids.shape[1]
        positions = torch.arange(length, device=decoder_ids.device).unsqueeze(0)
        d = self.token_embedding(decoder_ids) + self.position_embedding(positions)

        causal_mask = torch.triu(
            torch.full((length, length), -1e9, dtype=torch.float32, device=decoder_ids.device),
            diagonal=1,
        )

        for layer in self.decoder_layers:
            attended = self._mha(layer["self_attention"], d, d, d, attn_mask=causal_mask)
            d = layer["norm1"](d + attended)

            attended = self._mha(layer["cross_attention"], d, memory, memory, attn_mask=None)
            d = layer["norm2"](d + attended)
            d = layer["norm3"](d + layer["ffn"](d))

        return self.classifier(d)


def preprocess_image_np(image_path: Path, target_h: int = 128, target_w: int = 512) -> np.ndarray:
    img = Image.open(image_path).convert("L")
    img_np = np.array(img, dtype=np.uint8)

    # Simple CLAHE via OpenCV if available, otherwise fallback to direct grayscale
    try:
        import cv2
        clahe = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8))
        img_np = clahe.apply(img_np)
        img = Image.fromarray(img_np)
    except ImportError:
        pass

    scale = min(target_w / img.width, target_h / img.height)
    new_w = max(1, int(img.width * scale))
    new_h = max(1, int(img.height * scale))
    img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)

    canvas = Image.new("L", (target_w, target_h), color=255)
    paste_x = (target_w - new_w) // 2
    paste_y = (target_h - new_h) // 2
    canvas.paste(img, (paste_x, paste_y))

    arr = np.array(canvas, dtype=np.float32)[None, None, :, :]
    return arr


@torch.no_grad()
def pytorch_greedy_decode(model: nn.Module, image_arr: np.ndarray, vocab: dict, max_steps: int = 128) -> str:
    inv_vocab = {idx: tok for tok, idx in vocab.items()}
    bos_id = vocab["<BOS>"]
    eos_id = vocab["<EOS>"]
    pad_id = vocab["<PAD>"]
    max_len = model.max_length

    image_tensor = torch.from_numpy(image_arr)
    decoder_ids = torch.tensor([[bos_id]], dtype=torch.long)

    for _ in range(max_steps):
        pad_len = max_len - decoder_ids.shape[1]
        padded = torch.nn.functional.pad(decoder_ids, (0, pad_len), value=pad_id)
        logits = model({"images": image_tensor, "decoder_ids": padded})
        next_id = int(logits[0, decoder_ids.shape[1] - 1, :].argmax(dim=-1).item())
        if next_id == eos_id:
            break
        decoder_ids = torch.cat([decoder_ids, torch.tensor([[next_id]], dtype=torch.long)], dim=1)

    return " ".join(inv_vocab[int(t)] for t in decoder_ids[0][1:])


def onnx_greedy_decode(
    enc_sess: ort.InferenceSession,
    dec_sess: ort.InferenceSession,
    image_arr: np.ndarray,
    vocab: dict,
    max_steps: int = 128,
) -> str:
    inv_vocab = {idx: tok for tok, idx in vocab.items()}
    bos_id = vocab["<BOS>"]
    eos_id = vocab["<EOS>"]

    memory = enc_sess.run(["memory"], {"images": image_arr})[0]
    ids = [bos_id]
    for _ in range(max_steps):
        dec_input = np.array([ids], dtype=np.int64)
        logits = dec_sess.run(["logits"], {"decoder_ids": dec_input, "memory": memory})[0]
        next_id = int(np.argmax(logits[0, -1, :]))
        if next_id == eos_id:
            break
        ids.append(next_id)

    return " ".join(inv_vocab[i] for i in ids[1:])


def main():
    parser = argparse.ArgumentParser(description="Export MathWriting PyTorch checkpoint to quantized ONNX.")
    parser.add_argument(
        "--checkpoint",
        type=Path,
        default=DEFAULT_FINETUNED if DEFAULT_FINETUNED.exists() else DEFAULT_BASE,
        help="Path to .pt checkpoint (defaults to best_finetuned.pt if present, else best.pt)",
    )
    args = parser.parse_args()
    checkpoint_path = args.checkpoint

    sys.path.insert(0, str(MODEL_REPO))
    from model import MathWritingImage2Latex

    print(f"Loading checkpoint: {checkpoint_path}")
    ckpt = torch.load(checkpoint_path, map_location="cpu", weights_only=False)
    vocab = ckpt["vocab"]

    model = MathWritingImage2Latex(
        vocab_size=ckpt["vocab_size"],
        hidden_dim=ckpt["hidden_dim"],
        max_length=ckpt["max_length"],
        num_heads=ckpt["num_heads"],
        decoder_layers=4,
        dropout=0.0,
    )
    model.load_state_dict(ckpt["model_state_dict"])
    model.eval()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    encoder_path = OUTPUT_DIR / "math_encoder.onnx"
    decoder_fp32_path = OUTPUT_DIR / "math_decoder_fp32.onnx"
    decoder_quant_path = OUTPUT_DIR / "math_decoder.onnx"
    vocab_path = OUTPUT_DIR / "vocabulary.json"

    encoder = EncoderWrapper(model).eval()
    decoder = DecoderWrapper(model, num_heads=ckpt["num_heads"]).eval()

    dummy_img = torch.zeros(1, 1, 128, 512, dtype=torch.float32)
    dummy_memory = torch.zeros(1, 256, ckpt["hidden_dim"], dtype=torch.float32)
    dummy_ids = torch.tensor([[vocab["<BOS>"], 14, 9, 15]], dtype=torch.long)

    print("Exporting encoder ONNX...")
    torch.onnx.export(
        encoder,
        (dummy_img,),
        str(encoder_path),
        input_names=["images"],
        output_names=["memory"],
        opset_version=17,
        dynamo=False,
    )

    print("Exporting decoder ONNX (FP32)...")
    torch.onnx.export(
        decoder,
        (dummy_ids, dummy_memory),
        str(decoder_fp32_path),
        input_names=["decoder_ids", "memory"],
        output_names=["logits"],
        dynamic_axes={"decoder_ids": {1: "seq_len"}, "logits": {1: "seq_len"}},
        opset_version=17,
        dynamo=False,
    )

    print("Quantizing decoder ONNX (QUInt8)...")
    quantize_dynamic(
        model_input=str(decoder_fp32_path),
        model_output=str(decoder_quant_path),
        op_types_to_quantize=["MatMul", "Gemm"],
        weight_type=QuantType.QUInt8,
    )
    decoder_fp32_path.unlink(missing_ok=True)

    with vocab_path.open("w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False, indent=2)

    print(
        f"Exported sizes: encoder={encoder_path.stat().st_size / 1e6:.2f} MB, "
        f"decoder={decoder_quant_path.stat().st_size / 1e6:.2f} MB"
    )

    # Parity check on real sample images
    sample_images = sorted(REAL_IMAGES_DIR.glob("*.*"))[:3]
    if sample_images:
        enc_sess = ort.InferenceSession(str(encoder_path), providers=["CPUExecutionProvider"])
        dec_sess = ort.InferenceSession(str(decoder_quant_path), providers=["CPUExecutionProvider"])
        for img_path in sample_images:
            arr = preprocess_image_np(img_path)
            pt_pred = pytorch_greedy_decode(model, arr, vocab)
            onnx_pred = onnx_greedy_decode(enc_sess, dec_sess, arr, vocab)
            print(f"[{img_path.name}]")
            print(f"  PyTorch: {pt_pred}")
            print(f"  ONNX   : {onnx_pred}")
            print(f"  Match  : {pt_pred == onnx_pred}")


if __name__ == "__main__":
    main()
