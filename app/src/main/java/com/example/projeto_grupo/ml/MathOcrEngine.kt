package com.example.projeto_grupo.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.example.projeto_grupo.ui.LatexConversionResult
import com.example.projeto_grupo.ui.LatexToExpressionConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class OcrResult(
    val rawLatex: String,
    val conversion: LatexConversionResult
)

class MathOcrEngine(context: Context) {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()
    private val ortEnv: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var invVocab: Map<Int, String> = emptyMap()
    private var bosId: Int = 2
    private var eosId: Int = 3
    private var padId: Int = 0
    private var unkId: Int = 1

    private suspend fun ensureInitialized() {
        if (encoderSession != null && decoderSession != null && invVocab.isNotEmpty()) return
        initMutex.withLock {
            if (encoderSession != null && decoderSession != null && invVocab.isNotEmpty()) return

            val vocabJsonStr = appContext.assets.open("$ASSET_DIR/vocabulary.json")
                .bufferedReader().use { it.readText() }
            val vocabObj = JSONObject(vocabJsonStr)
            val map = HashMap<Int, String>(vocabObj.length())
            val keys = vocabObj.keys()
            while (keys.hasNext()) {
                val token = keys.next()
                val id = vocabObj.getInt(token)
                map[id] = token
            }
            invVocab = map
            bosId = vocabObj.optInt("<BOS>", 2)
            eosId = vocabObj.optInt("<EOS>", 3)
            padId = vocabObj.optInt("<PAD>", 0)
            unkId = vocabObj.optInt("<UNK>", 1)

            val modelDir = File(appContext.filesDir, ASSET_DIR).apply { mkdirs() }
            val encoderFile = copyAssetIfNeeded("$ASSET_DIR/$ENCODER_FILE", File(modelDir, ENCODER_FILE))
            val decoderFile = copyAssetIfNeeded("$ASSET_DIR/$DECODER_FILE", File(modelDir, DECODER_FILE))

            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 4))
            }
            encoderSession = ortEnv.createSession(encoderFile.absolutePath, opts)
            decoderSession = ortEnv.createSession(decoderFile.absolutePath, opts)
        }
    }

    private fun copyAssetIfNeeded(assetPath: String, destFile: File): File {
        val assetLen = runCatching {
            appContext.assets.openFd(assetPath).use { it.length }
        }.getOrDefault(-1L)

        if (destFile.exists() && destFile.length() > 0L && (assetLen < 0L || destFile.length() == assetLen)) {
            return destFile
        }
        appContext.assets.open(assetPath).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return destFile
    }

    suspend fun recognize(bitmap: Bitmap, cropRect: RectF? = null): OcrResult = withContext(Dispatchers.Default) {
        ensureInitialized()
        val enc = encoderSession ?: error("Encoder ONNX não inicializado")
        val dec = decoderSession ?: error("Decoder ONNX não inicializado")

        val preprocessed = preprocessBitmap(bitmap, cropRect)

        val memoryTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(preprocessed),
            longArrayOf(1L, 1L, TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong())
        ).use { imgTensor ->
            val encResults = enc.run(mapOf("images" to imgTensor))
            encResults.use { res ->
                val rawMem = res[0].value as Array<Array<FloatArray>>
                val batch = rawMem[0]
                val rows = batch.size
                val cols = batch[0].size
                val flat = FloatArray(rows * cols)
                for (r in 0 until rows) {
                    System.arraycopy(batch[r], 0, flat, r * cols, cols)
                }
                OnnxTensor.createTensor(
                    ortEnv,
                    FloatBuffer.wrap(flat),
                    longArrayOf(1L, rows.toLong(), cols.toLong())
                )
            }
        }

        val generatedIds = ArrayList<Int>(32)
        generatedIds.add(bosId)

        try {
            for (step in 0 until MAX_DECODE_TOKENS) {
                val seqLen = generatedIds.size
                val idsArray = LongArray(seqLen) { idx -> generatedIds[idx].toLong() }
                val nextTokenId = OnnxTensor.createTensor(
                    ortEnv,
                    LongBuffer.wrap(idsArray),
                    longArrayOf(1L, seqLen.toLong())
                ).use { idsTensor ->
                    dec.run(mapOf("decoder_ids" to idsTensor, "memory" to memoryTensor)).use { res ->
                        @Suppress("UNCHECKED_CAST")
                        val logits = res[0].value as Array<Array<FloatArray>>
                        val lastLogits = logits[0][seqLen - 1]
                        argmax(lastLogits)
                    }
                }

                if (nextTokenId == eosId) break
                generatedIds.add(nextTokenId)
            }
        } finally {
            memoryTensor.close()
        }

        val tokens = generatedIds.drop(1)
            .filter { it != padId && it != unkId && it != bosId && it != eosId }
            .mapNotNull { invVocab[it] }
        val rawLatex = tokens.joinToString(" ")
        val conversion = LatexToExpressionConverter.convert(rawLatex)
        OcrResult(rawLatex = rawLatex, conversion = conversion)
    }

    fun close() {
        runCatching { encoderSession?.close() }
        runCatching { decoderSession?.close() }
        encoderSession = null
        decoderSession = null
    }

    private fun argmax(arr: FloatArray): Int {
        var bestIdx = 0
        var bestVal = Float.NEGATIVE_INFINITY
        for (i in arr.indices) {
            if (arr[i] > bestVal) {
                bestVal = arr[i]
                bestIdx = i
            }
        }
        return bestIdx
    }

    companion object {
        private const val ASSET_DIR = "math_ocr"
        private const val ENCODER_FILE = "math_encoder.onnx"
        private const val DECODER_FILE = "math_decoder.onnx"
        const val TARGET_WIDTH = 512
        const val TARGET_HEIGHT = 128
        private const val MAX_DECODE_TOKENS = 128

        fun preprocessBitmap(bitmap: Bitmap, cropRect: RectF? = null): FloatArray {
            val srcRect = if (cropRect != null) {
                val left = (cropRect.left.coerceIn(0f, 1f) * bitmap.width).roundToInt()
                    .coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                val top = (cropRect.top.coerceIn(0f, 1f) * bitmap.height).roundToInt()
                    .coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                val right = (cropRect.right.coerceIn(0f, 1f) * bitmap.width).roundToInt()
                    .coerceIn(left + 1, bitmap.width.coerceAtLeast(1))
                val bottom = (cropRect.bottom.coerceIn(0f, 1f) * bitmap.height).roundToInt()
                    .coerceIn(top + 1, bitmap.height.coerceAtLeast(1))
                Rect(left, top, right, bottom)
            } else {
                Rect(0, 0, bitmap.width, bitmap.height)
            }

            val cropW = srcRect.width().coerceAtLeast(1)
            val cropH = srcRect.height().coerceAtLeast(1)
            val scale = min(TARGET_WIDTH.toFloat() / cropW, TARGET_HEIGHT.toFloat() / cropH)
            val newW = (cropW * scale).roundToInt().coerceIn(1, TARGET_WIDTH)
            val newH = (cropH * scale).roundToInt().coerceIn(1, TARGET_HEIGHT)

            val canvasBitmap = Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            canvas.drawColor(Color.WHITE)

            val pasteX = (TARGET_WIDTH - newW) / 2
            val pasteY = (TARGET_HEIGHT - newH) / 2
            val destRect = Rect(pasteX, pasteY, pasteX + newW, pasteY + newH)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
            canvas.drawBitmap(bitmap, srcRect, destRect, paint)

            val totalPixels = TARGET_WIDTH * TARGET_HEIGHT
            val argb = IntArray(totalPixels)
            canvasBitmap.getPixels(argb, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)
            canvasBitmap.recycle()

            val gray = IntArray(totalPixels)
            for (i in 0 until totalPixels) {
                val px = argb[i]
                val r = (px shr 16) and 0xFF
                val g = (px shr 8) and 0xFF
                val b = px and 0xFF
                gray[i] = ((299 * r + 587 * g + 114 * b) / 1000).coerceIn(0, 255)
            }

            val enhanced = applyClahe(gray, TARGET_WIDTH, TARGET_HEIGHT, clipLimit = 2.5f, gridX = 8, gridY = 8)
            return FloatArray(totalPixels) { idx -> enhanced[idx].toFloat() }
        }

        private fun applyClahe(
            gray: IntArray,
            width: Int,
            height: Int,
            clipLimit: Float,
            gridX: Int,
            gridY: Int
        ): IntArray {
            val tileW = width.toFloat() / gridX
            val tileH = height.toFloat() / gridY
            val cdfs = Array(gridY) { Array(gridX) { FloatArray(256) } }

            for (ty in 0 until gridY) {
                val y0 = (ty * tileH).roundToInt().coerceIn(0, height - 1)
                val y1 = ((ty + 1) * tileH).roundToInt().coerceIn(y0 + 1, height)
                for (tx in 0 until gridX) {
                    val x0 = (tx * tileW).roundToInt().coerceIn(0, width - 1)
                    val x1 = ((tx + 1) * tileW).roundToInt().coerceIn(x0 + 1, width)

                    val hist = IntArray(256)
                    var count = 0
                    for (y in y0 until y1) {
                        val rowOffset = y * width
                        for (x in x0 until x1) {
                            hist[gray[rowOffset + x]]++
                            count++
                        }
                    }

                    val maxBinCount = max(1, ((clipLimit * count) / 256f).roundToInt())
                    var excess = 0
                    for (b in 0 until 256) {
                        if (hist[b] > maxBinCount) {
                            excess += hist[b] - maxBinCount
                            hist[b] = maxBinCount
                        }
                    }
                    val redist = excess / 256
                    val rem = excess % 256
                    for (b in 0 until 256) {
                        hist[b] += redist + if (b < rem) 1 else 0
                    }

                    var cumulative = 0
                    val cdf = cdfs[ty][tx]
                    val denom = count.coerceAtLeast(1).toFloat()
                    for (b in 0 until 256) {
                        cumulative += hist[b]
                        cdf[b] = (cumulative / denom) * 255f
                    }
                }
            }

            val out = IntArray(width * height)
            for (y in 0 until height) {
                val fy = (y + 0.5f) / tileH - 0.5f
                val ty0 = fy.toInt().coerceIn(0, gridY - 1)
                val ty1 = (ty0 + 1).coerceIn(0, gridY - 1)
                val wy = (fy - ty0).coerceIn(0f, 1f)

                val rowOffset = y * width
                for (x in 0 until width) {
                    val fx = (x + 0.5f) / tileW - 0.5f
                    val tx0 = fx.toInt().coerceIn(0, gridX - 1)
                    val tx1 = (tx0 + 1).coerceIn(0, gridX - 1)
                    val wx = (fx - tx0).coerceIn(0f, 1f)

                    val v = gray[rowOffset + x]
                    val top = cdfs[ty0][tx0][v] * (1f - wx) + cdfs[ty0][tx1][v] * wx
                    val bot = cdfs[ty1][tx0][v] * (1f - wx) + cdfs[ty1][tx1][v] * wx
                    out[rowOffset + x] = (top * (1f - wy) + bot * wy).roundToInt().coerceIn(0, 255)
                }
            }
            return out
        }
    }
}
