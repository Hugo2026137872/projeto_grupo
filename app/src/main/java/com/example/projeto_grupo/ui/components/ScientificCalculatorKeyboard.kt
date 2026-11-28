package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.awaitCancellation

fun insertTokenAtSelection(current: TextFieldValue, token: String): TextFieldValue {
    val start = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
    val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
    val updatedText = current.text.replaceRange(start, end, token)
    val newCursor = start + token.length
    return TextFieldValue(
        text = updatedText,
        selection = TextRange(newCursor)
    )
}

fun backspaceAtSelection(current: TextFieldValue): TextFieldValue {
    val start = minOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
    val end = maxOf(current.selection.start, current.selection.end).coerceIn(0, current.text.length)
    if (start != end) {
        val updatedText = current.text.removeRange(start, end)
        return TextFieldValue(text = updatedText, selection = TextRange(start))
    }
    if (start == 0) return current
    val updatedText = current.text.removeRange(start - 1, start)
    return TextFieldValue(text = updatedText, selection = TextRange(start - 1))
}

fun moveSelectionCursor(current: TextFieldValue, delta: Int): TextFieldValue {
    val newPos = (current.selection.end + delta).coerceIn(0, current.text.length)
    return current.copy(selection = TextRange(newPos))
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CalculatorInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onActivateCalculator: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                keyboardController?.hide()
                onActivateCalculator()
            }
        }
    }

    InterceptPlatformTextInput(
        interceptor = { _, _ -> awaitCancellation() }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = false,
            interactionSource = interactionSource,
            label = label,
            placeholder = placeholder,
            trailingIcon = trailingIcon,
            isError = isError,
            supportingText = supportingText,
            singleLine = true,
            modifier = modifier.onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    keyboardController?.hide()
                    onActivateCalculator()
                }
            }
        )
    }
}

@Composable
fun ScientificCalculatorKeyboard(
    targetLabel: String,
    isNumericOnly: Boolean = false,
    onInsert: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onMoveCursor: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSymbolsMode by remember(isNumericOnly) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header bar with mode toggle, cursor controls, and hide button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = targetLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isNumericOnly) {
                        TextButton(
                            onClick = { showSymbolsMode = !showSymbolsMode },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = if (showSymbolsMode) "123" else "∂ / αβ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    IconButton(
                        onClick = { onMoveCursor(-1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Mover cursor para a esquerda",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMoveCursor(1) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Mover cursor para a direita",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardHide,
                            contentDescription = "Fechar teclado científico",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            if (!isNumericOnly && showSymbolsMode) {
                CalcRow(
                    keys = listOf(
                        CalcKey("\\partial", "∂", isFunction = true),
                        CalcKey("pdx(", "∂/∂x", isFunction = true),
                        CalcKey("pdy(", "∂/∂y", isFunction = true),
                        CalcKey("grad(", "∇", isFunction = true),
                        CalcKey("\\frac{", "a/b", isFunction = true),
                        CalcKey("\\sqrt{", "√{}", isFunction = true)
                    ),
                    onInsert = onInsert
                )
                CalcRow(
                    keys = listOf(
                        CalcKey("floor(", "⌊x⌋", isFunction = true),
                        CalcKey("ceil(", "⌈x⌉", isFunction = true),
                        CalcKey("!", isOperator = true),
                        CalcKey("\\cdot", "·", isOperator = true),
                        CalcKey("{", isOperator = true),
                        CalcKey("}", isOperator = true)
                    ),
                    onInsert = onInsert
                )
                CalcRow(
                    keys = listOf(
                        CalcKey("\\alpha", "α", isVariable = true),
                        CalcKey("\\beta", "β", isVariable = true),
                        CalcKey("\\theta", "θ", isVariable = true),
                        CalcKey("\\lambda", "λ", isVariable = true),
                        CalcKey("\\omega", "ω", isVariable = true),
                        CalcKey("\\phi", "φ", isVariable = true)
                    ),
                    onInsert = onInsert
                )
                CalcRow(
                    keys = listOf(
                        CalcKey("\\Delta", "Δ", isVariable = true),
                        CalcKey("\\sigma", "σ", isVariable = true),
                        CalcKey("\\pi", "π", isVariable = true),
                        CalcKey("_", isOperator = true),
                        CalcKey("|", isOperator = true),
                        CalcKey("=", isOperator = true)
                    ),
                    onInsert = onInsert
                )
            } else if (!isNumericOnly) {
                // Scientific functions row 1
                CalcRow(
                    keys = listOf(
                        CalcKey("sin(", isFunction = true),
                        CalcKey("cos(", isFunction = true),
                        CalcKey("tan(", isFunction = true),
                        CalcKey("sqrt(", "√(", isFunction = true),
                        CalcKey("exp(", isFunction = true),
                        CalcKey("ln(", isFunction = true)
                    ),
                    onInsert = onInsert
                )

                // Scientific functions & variables row 2
                CalcRow(
                    keys = listOf(
                        CalcKey("abs(", "|x|", isFunction = true),
                        CalcKey("log(", isFunction = true),
                        CalcKey("x", isVariable = true),
                        CalcKey("y", isVariable = true),
                        CalcKey("pi", "π", isVariable = true),
                        CalcKey("e", isVariable = true)
                    ),
                    onInsert = onInsert
                )
            }

            if (!showSymbolsMode) {
                // Numbers + operators rows
                CalcRow(
                    keys = listOf(
                        CalcKey("7"),
                        CalcKey("8"),
                        CalcKey("9"),
                        CalcKey("/", isOperator = true),
                        CalcKey("(", isOperator = true),
                        CalcKey(")", isOperator = true)
                    ),
                    onInsert = onInsert
                )

                CalcRow(
                    keys = if (isNumericOnly) {
                        listOf(
                            CalcKey("4"),
                            CalcKey("5"),
                            CalcKey("6"),
                            CalcKey("*", "×", isOperator = true),
                            CalcKey("pi", "π", isVariable = true),
                            CalcKey("e", isVariable = true)
                        )
                    } else {
                        listOf(
                            CalcKey("4"),
                            CalcKey("5"),
                            CalcKey("6"),
                            CalcKey("*", "×", isOperator = true),
                            CalcKey("^", isOperator = true),
                            CalcKey("a", isVariable = true)
                        )
                    },
                    onInsert = onInsert
                )

                CalcRow(
                    keys = if (isNumericOnly) {
                        listOf(
                            CalcKey("1"),
                            CalcKey("2"),
                            CalcKey("3"),
                            CalcKey("-", isOperator = true),
                            CalcKey("^", isOperator = true),
                            CalcKey("sqrt(", "√(", isFunction = true)
                        )
                    } else {
                        listOf(
                            CalcKey("1"),
                            CalcKey("2"),
                            CalcKey("3"),
                            CalcKey("-", isOperator = true),
                            CalcKey("b", isVariable = true),
                            CalcKey("c", isVariable = true)
                        )
                    },
                    onInsert = onInsert
                )
            }

            // Bottom row with 0, ., +, C, Backspace
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CalcKeyButton(
                    label = "0",
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert("0") }
                )
                CalcKeyButton(
                    label = ".",
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert(".") }
                )
                CalcKeyButton(
                    label = "+",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert("+") }
                )
                CalcKeyButton(
                    label = "^2",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    onClick = { onInsert("^2") }
                )
                CalcKeyButton(
                    label = "C",
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                    onClick = onClear
                )
                FilledTonalButton(
                    onClick = onBackspace,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Apagar",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private data class CalcKey(
    val token: String,
    val label: String = token,
    val isFunction: Boolean = false,
    val isVariable: Boolean = false,
    val isOperator: Boolean = false
)

@Composable
private fun CalcRow(
    keys: List<CalcKey>,
    onInsert: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { key ->
            val (bg, fg) = when {
                key.isVariable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) to
                    MaterialTheme.colorScheme.onPrimaryContainer
                key.isFunction || key.isOperator -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f) to
                    MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.82f) to
                    MaterialTheme.colorScheme.onSurface
            }
            CalcKeyButton(
                label = key.label,
                containerColor = bg,
                contentColor = fg,
                modifier = Modifier.weight(1f),
                onClick = { onInsert(key.token) }
            )
        }
    }
}

@Composable
private fun CalcKeyButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false
        )
    }
}
