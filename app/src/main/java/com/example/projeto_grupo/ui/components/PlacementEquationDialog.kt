package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.projeto_grupo.ui.EquationPreset
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import com.example.projeto_grupo.ui.MathFormulaView
import com.example.projeto_grupo.ui.defaultEquationPresets

enum class PlacementOption {
    DEFAULT,
    NEW_EQUATION
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlacementEquationDialog(
    currentExpression: String,
    currentA: String,
    currentB: String,
    currentC: String,
    presets: List<EquationPreset> = defaultEquationPresets,
    onDismiss: () -> Unit,
    onConfirm: (isDefault: Boolean, expression: String, a: String, b: String, c: String) -> Unit
) {
    var selectedOption by remember { mutableStateOf(PlacementOption.DEFAULT) }

    var expressionInput by remember { mutableStateOf(currentExpression) }
    var paramValues by remember {
        mutableStateOf(mapOf("a" to currentA, "b" to currentB, "c" to currentC))
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    val detectedParams = remember(expressionInput) {
        MathExpressionEvaluator.extractParameters(expressionInput)
    }

    LaunchedEffect(detectedParams) {
        val updated = mutableMapOf<String, String>()
        detectedParams.forEach { param ->
            updated[param] = paramValues[param] ?: "1.0"
        }
        paramValues = updated
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Posicionar Equação em RA",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Escolha como deseja posicionar a equação na superfície detectada:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option 1: Default
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedOption == PlacementOption.DEFAULT)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { selectedOption = PlacementOption.DEFAULT },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedOption == PlacementOption.DEFAULT,
                                onClick = { selectedOption = PlacementOption.DEFAULT }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Usar Equação Padrão",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Onda Circular (Ripple)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Option 2: New Equation
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedOption == PlacementOption.NEW_EQUATION)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = { selectedOption = PlacementOption.NEW_EQUATION },
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
                                onClick = { selectedOption = PlacementOption.NEW_EQUATION }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Criar Nova Equação",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Inserir texto no input ou escolher preset",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                if (selectedOption == PlacementOption.NEW_EQUATION) {
                    HorizontalDivider()

                    Text(
                        text = "Predefinições Rápidas:",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = expressionInput == preset.expression,
                                onClick = {
                                    expressionInput = preset.expression
                                    paramValues = mapOf(
                                        "a" to preset.a.toString(),
                                        "b" to preset.b.toString(),
                                        "c" to preset.c.toString()
                                    )
                                    validationError = null
                                },
                                label = { Text(preset.name) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = expressionInput,
                        onValueChange = {
                            expressionInput = it
                            validationError = null
                        },
                        label = { Text("Expressão z = f(x, y)") },
                        isError = validationError != null,
                        supportingText = validationError?.let { err -> { Text(err) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                                OutlinedTextField(
                                    value = paramValues[param] ?: "1.0",
                                    onValueChange = { newVal ->
                                        paramValues = paramValues.toMutableMap().apply { put(param, newVal) }
                                    },
                                    label = { Text(param) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "Pré-visualização:",
                        style = MaterialTheme.typography.labelSmall
                    )
                    MathFormulaView(
                        expression = expressionInput,
                        prefix = "z = f(x, y) = ",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedOption == PlacementOption.DEFAULT) {
                        val defaultPreset = presets.firstOrNull() ?: defaultEquationPresets.first()
                        onConfirm(
                            true,
                            defaultPreset.expression,
                            defaultPreset.a.toString(),
                            defaultPreset.b.toString(),
                            defaultPreset.c.toString()
                        )
                    } else {
                        val parsed = paramValues.mapValues { (_, raw) ->
                            raw.trim().toDoubleOrNull()
                                ?: runCatching {
                                    MathExpressionEvaluator.evaluate(raw, 0.0, 0.0, emptyMap())
                                }.getOrNull()?.takeIf { it.isFinite() }
                                ?: 1.0
                        }
                        val err = MathExpressionEvaluator.validate(expressionInput, parsed)
                        if (err != null) {
                            validationError = err
                        } else {
                            val a = paramValues["a"] ?: "1.0"
                            val b = paramValues["b"] ?: "1.0"
                            val c = paramValues["c"] ?: "0.0"
                            onConfirm(false, expressionInput, a, b, c)
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