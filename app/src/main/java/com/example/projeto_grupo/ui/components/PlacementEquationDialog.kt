package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.projeto_grupo.data.local.SavedEquation
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import com.example.projeto_grupo.ui.MathFormulaView

enum class PlacementOption {
    SAVED_EQUATION,
    NEW_EQUATION
}

private const val EXPR_FIELD_KEY = "__EXPR__"

@Composable
fun PlacementEquationDialog(
    savedEquations: List<SavedEquation>,
    initialSelectedSavedId: String? = null,
    onDismiss: () -> Unit,
    onConfirmSaved: (SavedEquation) -> Unit,
    onConfirmNew: (expression: String, params: Map<String, Double>) -> Unit
) {
    val hasSaved = savedEquations.isNotEmpty()
    var selectedOption by remember(hasSaved) {
        mutableStateOf(
            if (hasSaved) PlacementOption.SAVED_EQUATION else PlacementOption.NEW_EQUATION
        )
    }
    var selectedSavedId by remember(savedEquations, initialSelectedSavedId) {
        mutableStateOf(
            savedEquations.firstOrNull { it.id == initialSelectedSavedId }?.id
                ?: savedEquations.firstOrNull()?.id
        )
    }

    var expressionValue by remember { mutableStateOf(TextFieldValue("")) }
    var paramValues by remember { mutableStateOf(emptyMap<String, TextFieldValue>()) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var activeCalcField by remember {
        mutableStateOf<String?>(if (!hasSaved) EXPR_FIELD_KEY else null)
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val detectedParams = remember(expressionValue.text) {
        MathExpressionEvaluator.extractParameters(expressionValue.text)
    }

    LaunchedEffect(detectedParams) {
        val updated = mutableMapOf<String, TextFieldValue>()
        detectedParams.forEach { param ->
            val defaultStr = if (param == "c") "0.0" else "1.0"
            updated[param] = paramValues[param] ?: TextFieldValue(defaultStr, TextRange(defaultStr.length))
        }
        paramValues = updated
        if (activeCalcField != null && activeCalcField != EXPR_FIELD_KEY && activeCalcField !in detectedParams) {
            activeCalcField = EXPR_FIELD_KEY
        }
    }

    LaunchedEffect(activeCalcField) {
        if (activeCalcField != null) {
            keyboardController?.hide()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
        tonalElevation = 0.dp,
        title = {
            val dialogView = LocalView.current
            SideEffect {
                (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0.08f)
            }
            Text(
                text = if (hasSaved) "Posicionar Equação em RA" else "Nova Equação em RA",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasSaved) {
                    Text(
                        text = "Pretende usar uma equação guardada ou criar uma nova?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Column(
                        modifier = Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedOption == PlacementOption.SAVED_EQUATION)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                            ),
                            onClick = {
                                selectedOption = PlacementOption.SAVED_EQUATION
                                activeCalcField = null
                                validationError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption == PlacementOption.SAVED_EQUATION,
                                    onClick = {
                                        selectedOption = PlacementOption.SAVED_EQUATION
                                        activeCalcField = null
                                        validationError = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Usar Equação Guardada",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${savedEquations.size} disponível(eis)",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedOption == PlacementOption.NEW_EQUATION)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                            ),
                            onClick = {
                                selectedOption = PlacementOption.NEW_EQUATION
                                activeCalcField = EXPR_FIELD_KEY
                                validationError = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedOption == PlacementOption.NEW_EQUATION,
                                    onClick = {
                                        selectedOption = PlacementOption.NEW_EQUATION
                                        activeCalcField = EXPR_FIELD_KEY
                                        validationError = null
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Criar Nova Equação",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Inserir nova expressão matemática",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Insira a expressão da nova equação para posicionar em RA:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (hasSaved && selectedOption == PlacementOption.SAVED_EQUATION) {
                    HorizontalDivider()
                    Text(
                        text = "Selecione uma equação guardada:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        savedEquations.forEach { saved ->
                            val isSelected = saved.id == selectedSavedId
                            val usedParams = saved.getUsedParams()
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                onClick = { selectedSavedId = saved.id },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedSavedId = saved.id }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "z = ${saved.expression}",
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (usedParams.isNotEmpty()) {
                                            Text(
                                                text = usedParams.entries.joinToString(", ") { "${it.key} = ${it.value}" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (hasSaved) {
                        HorizontalDivider()
                    }

                    val exprInteraction = remember { MutableInteractionSource() }
                    LaunchedEffect(exprInteraction) {
                        exprInteraction.interactions.collect { interaction ->
                            if (interaction is PressInteraction.Release) {
                                activeCalcField = EXPR_FIELD_KEY
                                keyboardController?.hide()
                            }
                        }
                    }

                    OutlinedTextField(
                        value = expressionValue,
                        onValueChange = {
                            expressionValue = it
                            validationError = null
                        },
                        readOnly = true,
                        interactionSource = exprInteraction,
                        label = { Text("Expressão z = f(x, y)") },
                        placeholder = { Text("Ex: a * sin(b * x) * cos(b * y) + c") },
                        trailingIcon = {
                            IconButton(onClick = { activeCalcField = EXPR_FIELD_KEY }) {
                                Icon(
                                    imageVector = Icons.Default.Calculate,
                                    contentDescription = "Abrir teclado científico"
                                )
                            }
                        },
                        isError = validationError != null,
                        supportingText = validationError?.let { err -> { Text(err) } },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    activeCalcField = EXPR_FIELD_KEY
                                    keyboardController?.hide()
                                }
                            }
                    )

                    if (detectedParams.isNotEmpty()) {
                        Text(
                            text = "Parâmetros Detetados:",
                            style = MaterialTheme.typography.labelMedium
                        )
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
                                OutlinedTextField(
                                    value = paramValues[param] ?: TextFieldValue("1.0"),
                                    onValueChange = { newVal ->
                                        paramValues = paramValues.toMutableMap().apply { put(param, newVal) }
                                        validationError = null
                                    },
                                    readOnly = true,
                                    interactionSource = paramInteraction,
                                    label = { Text(param) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .width(95.dp)
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

                    if (expressionValue.text.isNotBlank()) {
                        Text(
                            text = "Pré-visualização:",
                            style = MaterialTheme.typography.labelSmall
                        )
                        MathFormulaView(
                            expression = expressionValue.text,
                            prefix = "z = f(x, y) = ",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    activeCalcField?.let { targetField ->
                        val isExpr = targetField == EXPR_FIELD_KEY
                        ScientificCalculatorKeyboard(
                            targetLabel = if (isExpr) "Expressão z = f(x, y)" else "Parâmetro '$targetField'",
                            isNumericOnly = !isExpr,
                            onInsert = { token ->
                                validationError = null
                                if (isExpr) {
                                    expressionValue = insertTokenAtSelection(expressionValue, token)
                                } else {
                                    val curr = paramValues[targetField] ?: TextFieldValue("")
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(targetField, insertTokenAtSelection(curr, token))
                                    }
                                }
                            },
                            onBackspace = {
                                validationError = null
                                if (isExpr) {
                                    expressionValue = backspaceAtSelection(expressionValue)
                                } else {
                                    val curr = paramValues[targetField] ?: TextFieldValue("")
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(targetField, backspaceAtSelection(curr))
                                    }
                                }
                            },
                            onClear = {
                                validationError = null
                                if (isExpr) {
                                    expressionValue = TextFieldValue("")
                                } else {
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(targetField, TextFieldValue(""))
                                    }
                                }
                            },
                            onMoveCursor = { delta ->
                                if (isExpr) {
                                    expressionValue = moveSelectionCursor(expressionValue, delta)
                                } else {
                                    val curr = paramValues[targetField] ?: TextFieldValue("")
                                    paramValues = paramValues.toMutableMap().apply {
                                        put(targetField, moveSelectionCursor(curr, delta))
                                    }
                                }
                            },
                            onDismiss = { activeCalcField = null }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (hasSaved && selectedOption == PlacementOption.SAVED_EQUATION) {
                        val chosen = savedEquations.firstOrNull { it.id == selectedSavedId }
                            ?: savedEquations.first()
                        onConfirmSaved(chosen)
                    } else {
                        val parsed = mutableMapOf<String, Double>()
                        for (param in detectedParams) {
                            val raw = paramValues[param]?.text ?: if (param == "c") "0.0" else "1.0"
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

                        val expr = expressionValue.text.trim()
                        val err = MathExpressionEvaluator.validate(expr, parsed)
                        if (err != null) {
                            validationError = err
                        } else {
                            onConfirmNew(expr, parsed)
                        }
                    }
                }
            ) {
                Text("Posicionar 3D")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
