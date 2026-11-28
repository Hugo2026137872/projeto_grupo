package com.example.projeto_grupo.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.projeto_grupo.ml.OcrResult
import com.example.projeto_grupo.ui.EquationViewModel
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import com.example.projeto_grupo.ui.MathFormulaView
import com.example.projeto_grupo.ui.components.CalculatorInputField
import com.example.projeto_grupo.ui.components.ScientificCalculatorKeyboard
import com.example.projeto_grupo.ui.components.backspaceAtSelection
import com.example.projeto_grupo.ui.components.insertTokenAtSelection
import com.example.projeto_grupo.ui.components.moveSelectionCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val SCAN_EXPR_FIELD_KEY = "__SCAN_EXPR__"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanEquationScreen(
    viewModel: EquationViewModel,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onPlotInAR: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.25f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.75f) }

    var isRecognizing by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var ocrError by remember { mutableStateOf<String?>(null) }

    var expressionFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var paramFieldValues by remember { mutableStateOf<Map<String, TextFieldValue>>(emptyMap()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var activeCalcField by remember { mutableStateOf<String?>(null) }

    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

    fun loadImageFromUri(uri: Uri) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                decodeOrientedBitmap(context, uri, maxDim = 1600)
            }
            if (loaded != null) {
                selectedBitmap = loaded
                cropLeft = 0.05f
                cropTop = 0.25f
                cropRight = 0.95f
                cropBottom = 0.75f
                ocrResult = null
                ocrError = null
                validationError = null
                activeCalcField = null
            } else {
                ocrError = "Não foi possível carregar a imagem selecionada."
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        if (success && uri != null) {
            loadImageFromUri(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            loadImageFromUri(uri)
        }
    }

    fun launchCameraCapture() {
        if (!hasCameraPermission) {
            onRequestCameraPermission()
            return
        }
        val cacheDir = File(context.cacheDir, "ocr_photos").apply { mkdirs() }
        val photoFile = File(cacheDir, "eq_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        pendingPhotoUri = uri
        takePictureLauncher.launch(uri)
    }

    val detectedParams = remember(expressionFieldValue.text) {
        MathExpressionEvaluator.extractParameters(expressionFieldValue.text)
    }

    LaunchedEffect(detectedParams) {
        val updated = mutableMapOf<String, TextFieldValue>()
        detectedParams.forEach { param ->
            val defaultStr = if (param == "c") "0.0" else "1.0"
            updated[param] = paramFieldValues[param]
                ?: TextFieldValue(defaultStr, TextRange(defaultStr.length))
        }
        paramFieldValues = updated
        if (activeCalcField != null && activeCalcField != SCAN_EXPR_FIELD_KEY && activeCalcField !in detectedParams) {
            activeCalcField = SCAN_EXPR_FIELD_KEY
        }
    }

    LaunchedEffect(activeCalcField) {
        if (activeCalcField != null) {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Digitalizar Equação Manuscrita",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Tira uma foto ou escolhe uma imagem da galeria. A rede neuronal lê a equação e permite confirmar antes de plotar em 3D.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Capture / Pick buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { launchCameraCapture() },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tirar Foto")
            }

            OutlinedButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Galeria")
            }
        }

        ocrError?.let { err ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = err,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        val bitmap = selectedBitmap
        if (bitmap == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Nenhuma imagem selecionada",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Escreve uma equação num papel com boa iluminação, tira foto e ajusta o recorte à volta da fórmula.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Stage 2: Crop & Recognize
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recorte da Equação",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        TextButton(
                            onClick = {
                                cropLeft = 0f
                                cropTop = 0f
                                cropRight = 1f
                                cropBottom = 1f
                            }
                        ) {
                            Text("Imagem toda")
                        }
                    }

                    CropImageOverlay(
                        bitmap = bitmap,
                        left = cropLeft,
                        top = cropTop,
                        right = cropRight,
                        bottom = cropBottom,
                        onCropChange = { l, t, r, b ->
                            cropLeft = l
                            cropTop = t
                            cropRight = r
                            cropBottom = b
                        }
                    )

                    Button(
                        onClick = {
                            scope.launch {
                                isRecognizing = true
                                ocrError = null
                                validationError = null
                                val rect = RectF(cropLeft, cropTop, cropRight, cropBottom)
                                val res = viewModel.recognizeHandwrittenEquation(bitmap, rect)
                                isRecognizing = false
                                res.fold(
                                    onSuccess = { recognized ->
                                        ocrResult = recognized
                                        val expr = recognized.conversion.expression
                                        expressionFieldValue = TextFieldValue(expr, TextRange(expr.length))
                                    },
                                    onFailure = { e ->
                                        ocrError = "Erro ao reconhecer equação: ${e.localizedMessage ?: "desconhecido"}"
                                    }
                                )
                            }
                        },
                        enabled = !isRecognizing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRecognizing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("A reconhecer com Rede Neuronal...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reconhecer Equação")
                        }
                    }
                }
            }

            // Stage 3: User Review & Confirmation before plotting
            ocrResult?.let { result ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Confirmar Equação Detetada",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Verifica se a leitura está correta ou edita antes de gerar o gráfico 3D em RA:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (result.rawLatex.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "LaTeX Reconhecido",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = result.rawLatex,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        result.conversion.warning?.let { warn ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = warn,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (expressionFieldValue.text.isNotBlank()) {
                            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                MathFormulaView(
                                    expression = expressionFieldValue.text,
                                    prefix = "z = f(x, y) = ",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        CalculatorInputField(
                            value = expressionFieldValue,
                            onValueChange = {
                                expressionFieldValue = it
                                validationError = null
                            },
                            onActivateCalculator = { activeCalcField = SCAN_EXPR_FIELD_KEY },
                            label = { Text("Expressão z = f(x, y)") },
                            trailingIcon = {
                                IconButton(onClick = { activeCalcField = SCAN_EXPR_FIELD_KEY }) {
                                    Icon(
                                        imageVector = Icons.Default.Calculate,
                                        contentDescription = "Abrir teclado científico"
                                    )
                                }
                            },
                            isError = validationError != null,
                            supportingText = validationError?.let { err -> { Text(err) } },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (detectedParams.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                detectedParams.forEach { param ->
                                    CalculatorInputField(
                                        value = paramFieldValues[param] ?: TextFieldValue("1.0"),
                                        onValueChange = { updated ->
                                            paramFieldValues = paramFieldValues.toMutableMap().apply {
                                                put(param, updated)
                                            }
                                            validationError = null
                                        },
                                        onActivateCalculator = { activeCalcField = param },
                                        label = { Text(param) },
                                        modifier = Modifier.width(96.dp)
                                    )
                                }
                            }
                        }

                        activeCalcField?.let { targetField ->
                            val isExpr = targetField == SCAN_EXPR_FIELD_KEY
                            ScientificCalculatorKeyboard(
                                targetLabel = if (isExpr) "Expressão z = f(x, y)" else "Parâmetro '$targetField'",
                                isNumericOnly = !isExpr,
                                onInsert = { token ->
                                    validationError = null
                                    if (isExpr) {
                                        expressionFieldValue = insertTokenAtSelection(expressionFieldValue, token)
                                    } else {
                                        val curr = paramFieldValues[targetField] ?: TextFieldValue("")
                                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                                            put(targetField, insertTokenAtSelection(curr, token))
                                        }
                                    }
                                },
                                onBackspace = {
                                    validationError = null
                                    if (isExpr) {
                                        expressionFieldValue = backspaceAtSelection(expressionFieldValue)
                                    } else {
                                        val curr = paramFieldValues[targetField] ?: TextFieldValue("")
                                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                                            put(targetField, backspaceAtSelection(curr))
                                        }
                                    }
                                },
                                onClear = {
                                    validationError = null
                                    if (isExpr) {
                                        expressionFieldValue = TextFieldValue("")
                                    } else {
                                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                                            put(targetField, TextFieldValue(""))
                                        }
                                    }
                                },
                                onMoveCursor = { delta ->
                                    if (isExpr) {
                                        expressionFieldValue = moveSelectionCursor(expressionFieldValue, delta)
                                    } else {
                                        val curr = paramFieldValues[targetField] ?: TextFieldValue("")
                                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                                            put(targetField, moveSelectionCursor(curr, delta))
                                        }
                                    }
                                },
                                onDismiss = { activeCalcField = null }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedBitmap = null
                                    ocrResult = null
                                    ocrError = null
                                    validationError = null
                                    activeCalcField = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Nova Foto")
                            }

                            Button(
                                onClick = {
                                    val expr = expressionFieldValue.text.trim()
                                    val parsed = mutableMapOf<String, Double>()
                                    for (param in detectedParams) {
                                        val raw = paramFieldValues[param]?.text ?: if (param == "c") "0.0" else "1.0"
                                        val value = raw.trim().toDoubleOrNull()
                                            ?: runCatching {
                                                MathExpressionEvaluator.evaluate(raw, 0.0, 0.0, emptyMap())
                                            }.getOrNull()?.takeIf { it.isFinite() }
                                        if (value == null) {
                                            validationError = "Valor inválido para '$param'."
                                            return@Button
                                        }
                                        parsed[param] = value
                                    }

                                    val err = MathExpressionEvaluator.validate(expr, parsed)
                                    if (err != null) {
                                        validationError = err
                                    } else {
                                        activeCalcField = null
                                        viewModel.applyCustomEquation(expr, parsed)
                                        onPlotInAR()
                                    }
                                },
                                modifier = Modifier.weight(1.4f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Plotar em RA")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropImageOverlay(
    bitmap: Bitmap,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    onCropChange: (Float, Float, Float, Float) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var activeHandle by remember { mutableIntStateOf(0) } // 1=TL, 2=TR, 3=BL, 4=BR, 5=MOVE

    val latestLeft by rememberUpdatedState(left)
    val latestTop by rememberUpdatedState(top)
    val latestRight by rememberUpdatedState(right)
    val latestBottom by rememberUpdatedState(bottom)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect.coerceIn(0.6f, 2.2f))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .onSizeChanged { boxSize = it }
            .pointerInput(bitmap) {
                detectDragGestures(
                    onDragStart = { start ->
                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                        val lx = latestLeft * w
                        val ty = latestTop * h
                        val rx = latestRight * w
                        val by = latestBottom * h
                        val hitRadius = 48f

                        activeHandle = when {
                            abs(start.x - lx) < hitRadius && abs(start.y - ty) < hitRadius -> 1
                            abs(start.x - rx) < hitRadius && abs(start.y - ty) < hitRadius -> 2
                            abs(start.x - lx) < hitRadius && abs(start.y - by) < hitRadius -> 3
                            abs(start.x - rx) < hitRadius && abs(start.y - by) < hitRadius -> 4
                            start.x in lx..rx && start.y in ty..by -> 5
                            else -> 0
                        }
                    },
                    onDragEnd = { activeHandle = 0 },
                    onDragCancel = { activeHandle = 0 },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val w = boxSize.width.toFloat().coerceAtLeast(1f)
                        val h = boxSize.height.toFloat().coerceAtLeast(1f)
                        val dx = dragAmount.x / w
                        val dy = dragAmount.y / h
                        val minSpan = 0.08f

                        var l = latestLeft
                        var t = latestTop
                        var r = latestRight
                        var b = latestBottom

                        when (activeHandle) {
                            1 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            2 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                t = (t + dy).coerceIn(0f, b - minSpan)
                            }
                            3 -> {
                                l = (l + dx).coerceIn(0f, r - minSpan)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                            4 -> {
                                r = (r + dx).coerceIn(l + minSpan, 1f)
                                b = (b + dy).coerceIn(t + minSpan, 1f)
                            }
                            5 -> {
                                val spanX = r - l
                                val spanY = b - t
                                l = (l + dx).coerceIn(0f, 1f - spanX)
                                t = (t + dy).coerceIn(0f, 1f - spanY)
                                r = l + spanX
                                b = t + spanY
                            }
                        }
                        onCropChange(l, t, r, b)
                    }
                )
            }
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = "Equação manuscrita",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        val borderColor = MaterialTheme.colorScheme.primary
        Canvas(modifier = Modifier.fillMaxSize()) {
            val l = left * size.width
            val t = top * size.height
            val r = right * size.width
            val b = bottom * size.height

            val dim = Color.Black.copy(alpha = 0.45f)
            drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, t))
            drawRect(dim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
            drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
            drawRect(dim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))

            drawRect(
                color = borderColor,
                topLeft = Offset(l, t),
                size = Size(r - l, b - t),
                style = Stroke(width = 3.dp.toPx())
            )

            val handleRadius = 8.dp.toPx()
            listOf(
                Offset(l, t),
                Offset(r, t),
                Offset(l, b),
                Offset(r, b)
            ).forEach { corner ->
                drawCircle(color = Color.White, radius = handleRadius, center = corner)
                drawCircle(color = borderColor, radius = handleRadius - 2.dp.toPx(), center = corner)
            }
        }
    }
}

private fun decodeOrientedBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDim) {
        sampleSize *= 2
    }

    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOpts)
    } ?: return null

    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
    }

    if (matrix.isIdentity) return rawBitmap
    val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
    if (rotated !== rawBitmap) rawBitmap.recycle()
    return rotated
}
