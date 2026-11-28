package com.example.projeto_grupo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projeto_grupo.data.local.SavedEquation
import com.example.projeto_grupo.data.model.ApiEquationResponse
import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.ui.components.ScientificCalculatorKeyboard
import com.example.projeto_grupo.ui.components.backspaceAtSelection
import com.example.projeto_grupo.ui.components.insertTokenAtSelection
import com.example.projeto_grupo.ui.components.moveSelectionCursor

private const val SHEET_EXPR_FIELD_KEY = "__EXPR__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationBottomSheet(
    viewModel: EquationViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isBottomSheetVisible) return

    ModalBottomSheet(
        onDismissRequest = { viewModel.setBottomSheetVisible(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        scrimColor = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("Aba 1: Detalhes & POIs") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("Aba 2: Editar & Guardar") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.selectedTab) {
                0 -> DetailsAndPoisTab(uiState = uiState)
                1 -> EditEquationTab(
                    uiState = uiState,
                    onSelectPreset = viewModel::applyPreset,
                    onExpressionChange = viewModel::updateExpressionInput,
                    onParamChange = viewModel::updateParamInput,
                    onApply = viewModel::updateEquationFromInputs,
                    onSaveNameChange = viewModel::updateSaveNameInput,
                    onSaveToDevice = viewModel::saveCurrentEquationToDevice,
                    onSaveToCloud = { viewModel.saveCurrentEquationToCloud() },
                    onSelectSaved = viewModel::applySavedEquation,
                    onDeleteSaved = viewModel::deleteSavedEquation,
                    onSelectCloud = viewModel::applyApiEquation,
                    onDeleteCloud = viewModel::deleteServerEquation
                )
            }
        }
    }
}

@Composable
private fun DetailsAndPoisTab(
    uiState: EquationUiState
) {
    val pois = uiState.equation.pointsOfInterest
    val usedNames = remember(uiState.equation.expression) {
        MathExpressionEvaluator.extractParameters(uiState.equation.expression)
    }
    val effectiveParams = uiState.equation.getEffectiveParams()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Equação Ativa",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        MathFormulaView(
            expression = uiState.equation.expression,
            prefix = "z = f(x, y) = ",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (usedNames.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                usedNames.forEach { param ->
                    val displayVal = uiState.paramsInput[param]?.ifBlank { null }
                        ?: (effectiveParams[param]?.toString() ?: "1.0")
                    MathFormulaView(
                        expression = displayVal,
                        prefix = "$param = ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Pontos de Interesse Detetados (X: [-${uiState.xDomain}, ${uiState.xDomain}], Y: [-${uiState.yDomain}, ${uiState.yDomain}])",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (pois.isEmpty()) {
            Text(
                text = "Nenhum ponto detetado no domínio atual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(pois, key = { it.id }) { poi ->
                    PoiItemCard(poi = poi)
                }
            }
        }
    }
}

@Composable
private fun PoiItemCard(poi: PointOfInterest) {
    val badgeColor = when (poi.type) {
        PointType.MAXIMA -> Color(0xFFFFD600)
        PointType.MINIMA -> Color(0xFF00E676)
        PointType.ZERO -> Color(0xFFFF5252)
        PointType.INFLECTION -> Color.White
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(badgeColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = poi.label,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditEquationTab(
    uiState: EquationUiState,
    onSelectPreset: (EquationPreset) -> Unit,
    onExpressionChange: (String) -> Unit,
    onParamChange: (String, String) -> Unit,
    onApply: () -> Unit,
    onSaveNameChange: (String) -> Unit,
    onSaveToDevice: () -> Unit,
    onSaveToCloud: () -> Unit,
    onSelectSaved: (SavedEquation) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onSelectCloud: (ApiEquationResponse) -> Unit,
    onDeleteCloud: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var activeCalcField by remember { mutableStateOf<String?>(null) }

    var exprFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = uiState.expressionInput,
                selection = TextRange(uiState.expressionInput.length)
            )
        )
    }
    LaunchedEffect(uiState.expressionInput) {
        if (uiState.expressionInput != exprFieldValue.text) {
            exprFieldValue = TextFieldValue(
                text = uiState.expressionInput,
                selection = TextRange(uiState.expressionInput.length)
            )
        }
    }

    val detectedParams = remember(uiState.expressionInput) {
        MathExpressionEvaluator.extractParameters(uiState.expressionInput)
    }

    var paramFieldValues by remember {
        mutableStateOf(
            uiState.paramsInput.mapValues { (_, v) -> TextFieldValue(v, TextRange(v.length)) }
        )
    }
    LaunchedEffect(uiState.paramsInput, detectedParams) {
        val synced = mutableMapOf<String, TextFieldValue>()
        detectedParams.forEach { param ->
            val str = uiState.paramsInput[param] ?: if (param == "c") "0.0" else "1.0"
            val existing = paramFieldValues[param]
            synced[param] = if (existing != null && existing.text == str) {
                existing
            } else {
                TextFieldValue(str, TextRange(str.length))
            }
        }
        paramFieldValues = synced
        if (activeCalcField != null && activeCalcField != SHEET_EXPR_FIELD_KEY && activeCalcField !in detectedParams) {
            activeCalcField = SHEET_EXPR_FIELD_KEY
        }
    }

    LaunchedEffect(activeCalcField) {
        if (activeCalcField != null) {
            keyboardController?.hide()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Predefinições Rápidas",
            style = MaterialTheme.typography.labelMedium
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            uiState.presets.forEach { preset ->
                FilterChip(
                    selected = uiState.equation.expression == preset.expression,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.name) }
                )
            }
        }

        val exprInteraction = remember { MutableInteractionSource() }
        LaunchedEffect(exprInteraction) {
            exprInteraction.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) {
                    activeCalcField = SHEET_EXPR_FIELD_KEY
                    keyboardController?.hide()
                }
            }
        }

        OutlinedTextField(
            value = exprFieldValue,
            onValueChange = { updated ->
                exprFieldValue = updated
                if (updated.text != uiState.expressionInput) {
                    onExpressionChange(updated.text)
                }
            },
            readOnly = true,
            interactionSource = exprInteraction,
            label = { Text("Expressão z = f(x, y)") },
            trailingIcon = {
                IconButton(onClick = { activeCalcField = SHEET_EXPR_FIELD_KEY }) {
                    Icon(
                        imageVector = Icons.Default.Calculate,
                        contentDescription = "Abrir teclado científico"
                    )
                }
            },
            isError = uiState.expressionError != null,
            supportingText = uiState.expressionError?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        activeCalcField = SHEET_EXPR_FIELD_KEY
                        keyboardController?.hide()
                    }
                }
        )

        if (detectedParams.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detectedParams.forEach { param ->
                    val paramInteraction = remember(param) { MutableInteractionSource() }
                    LaunchedEffect(paramInteraction, param) {
                        paramInteraction.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) {
                                activeCalcField = param
                                keyboardController?.hide()
                            }
                        }
                    }
                    val currVal = paramFieldValues[param]
                        ?: TextFieldValue(uiState.paramsInput[param] ?: "1.0")
                    OutlinedTextField(
                        value = currVal,
                        onValueChange = { updated ->
                            paramFieldValues = paramFieldValues.toMutableMap().apply {
                                put(param, updated)
                            }
                            if (updated.text != uiState.paramsInput[param]) {
                                onParamChange(param, updated.text)
                            }
                        },
                        readOnly = true,
                        interactionSource = paramInteraction,
                        label = { Text(param) },
                        singleLine = true,
                        modifier = Modifier
                            .width(96.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    activeCalcField = param
                                    keyboardController?.hide()
                                }
                            }
                    )
                }
            }
        }

        activeCalcField?.let { targetField ->
            val isExpr = targetField == SHEET_EXPR_FIELD_KEY
            ScientificCalculatorKeyboard(
                targetLabel = if (isExpr) "Expressão z = f(x, y)" else "Parâmetro '$targetField'",
                isNumericOnly = !isExpr,
                onInsert = { token ->
                    if (isExpr) {
                        val updated = insertTokenAtSelection(exprFieldValue, token)
                        exprFieldValue = updated
                        onExpressionChange(updated.text)
                    } else {
                        val curr = paramFieldValues[targetField] ?: TextFieldValue("")
                        val updated = insertTokenAtSelection(curr, token)
                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                            put(targetField, updated)
                        }
                        onParamChange(targetField, updated.text)
                    }
                },
                onBackspace = {
                    if (isExpr) {
                        val updated = backspaceAtSelection(exprFieldValue)
                        exprFieldValue = updated
                        onExpressionChange(updated.text)
                    } else {
                        val curr = paramFieldValues[targetField] ?: TextFieldValue("")
                        val updated = backspaceAtSelection(curr)
                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                            put(targetField, updated)
                        }
                        onParamChange(targetField, updated.text)
                    }
                },
                onClear = {
                    if (isExpr) {
                        exprFieldValue = TextFieldValue("")
                        onExpressionChange("")
                    } else {
                        paramFieldValues = paramFieldValues.toMutableMap().apply {
                            put(targetField, TextFieldValue(""))
                        }
                        onParamChange(targetField, "")
                    }
                },
                onMoveCursor = { delta ->
                    if (isExpr) {
                        exprFieldValue = moveSelectionCursor(exprFieldValue, delta)
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

        Button(
            onClick = {
                activeCalcField = null
                onApply()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aplicar no Modelo 3D")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        Text(
            text = "Guardar Equação no Dispositivo",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.saveNameInput,
                onValueChange = onSaveNameChange,
                label = { Text("Nome (opcional)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            activeCalcField = null
                        }
                    }
            )
            FilledTonalButton(
                onClick = {
                    activeCalcField = null
                    onSaveToDevice()
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Local")
            }

            if (uiState.isLoggedIn) {
                Button(
                    onClick = {
                        activeCalcField = null
                        onSaveToCloud()
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nuvem")
                }
            }
        }

        uiState.saveFeedbackMessage?.let { feedback ->
            Text(
                text = feedback,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (uiState.savedEquations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Equações Guardadas (${uiState.savedEquations.size})",
                style = MaterialTheme.typography.titleSmall
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.savedEquations.forEach { saved ->
                    SavedEquationCard(
                        saved = saved,
                        isSelected = uiState.equation.expression == saved.expression &&
                                uiState.equation.getEffectiveParams() == saved.getUsedParams(),
                        onSelect = { onSelectSaved(saved) },
                        onDelete = { onDeleteSaved(saved.id) }
                    )
                }
            }
        }

        if (uiState.serverEquations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Equações na Nuvem (${uiState.serverEquations.size})",
                style = MaterialTheme.typography.titleSmall
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.serverEquations.forEach { apiEq ->
                    val canDelete = uiState.isLoggedIn &&
                            (apiEq.ownerId == uiState.currentUser?.id || apiEq.id != "eq_1")
                    CloudEquationCard(
                        apiEq = apiEq,
                        isSelected = uiState.equation.id == apiEq.id &&
                                uiState.equation.expression == apiEq.expression,
                        canDelete = canDelete,
                        onSelect = { onSelectCloud(apiEq) },
                        onDelete = { onDeleteCloud(apiEq.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedEquationCard(
    saved: SavedEquation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val usedParams = saved.getUsedParams()
    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = saved.name,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MathFormulaView(
                        expression = saved.expression,
                        prefix = "z = ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (usedParams.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usedParams.entries.joinToString("   ") { "${it.key} = ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Eliminar equação guardada",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CloudEquationCard(
    apiEq: ApiEquationResponse,
    isSelected: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val allParams = mapOf("a" to apiEq.a, "b" to apiEq.b, "c" to apiEq.c)
    val usedNames = remember(apiEq.expression) {
        MathExpressionEvaluator.extractParameters(apiEq.expression)
    }
    val usedParams = usedNames.associateWith { allParams[it] ?: if (it == "c") 0.0 else 1.0 }

    Surface(
        shape = MaterialTheme.shapes.small,
        tonalElevation = 0.dp,
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apiEq.title.ifBlank { "Equação Servidor ${apiEq.id}" },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MathFormulaView(
                        expression = apiEq.expression,
                        prefix = "z = ",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (usedParams.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = usedParams.entries.joinToString("   ") { "${it.key} = ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar equação da nuvem",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
