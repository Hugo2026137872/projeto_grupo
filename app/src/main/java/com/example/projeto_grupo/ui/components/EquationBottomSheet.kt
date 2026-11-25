package com.example.projeto_grupo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.projeto_grupo.data.local.SavedEquation
import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType

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
                    onAChange = viewModel::updateAInput,
                    onBChange = viewModel::updateBInput,
                    onCChange = viewModel::updateCInput,
                    onApply = viewModel::updateEquationFromInputs,
                    onSaveNameChange = viewModel::updateSaveNameInput,
                    onSaveToDevice = viewModel::saveCurrentEquationToDevice,
                    onSelectSaved = viewModel::applySavedEquation,
                    onDeleteSaved = viewModel::deleteSavedEquation
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
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MathFormulaView(
                expression = uiState.aInput.ifBlank { uiState.equation.a.toString() },
                prefix = "a = ",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MathFormulaView(
                expression = uiState.bInput.ifBlank { uiState.equation.b.toString() },
                prefix = "b = ",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MathFormulaView(
                expression = uiState.cInput.ifBlank { uiState.equation.c.toString() },
                prefix = "c = ",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Pontos de Interesse Detetados (Domínio [-2, 2])",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (pois.isEmpty()) {
            Text(
                text = "Nenhum ponto detetado no domínio [-2, 2].",
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

@Composable
private fun EditEquationTab(
    uiState: EquationUiState,
    onSelectPreset: (EquationPreset) -> Unit,
    onExpressionChange: (String) -> Unit,
    onAChange: (String) -> Unit,
    onBChange: (String) -> Unit,
    onCChange: (String) -> Unit,
    onApply: () -> Unit,
    onSaveNameChange: (String) -> Unit,
    onSaveToDevice: () -> Unit,
    onSelectSaved: (SavedEquation) -> Unit,
    onDeleteSaved: (String) -> Unit
) {
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

        OutlinedTextField(
            value = uiState.expressionInput,
            onValueChange = onExpressionChange,
            label = { Text("Expressão z = f(x, y)") },
            isError = uiState.expressionError != null,
            supportingText = uiState.expressionError?.let { err -> { Text(err) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.aInput,
                onValueChange = onAChange,
                label = { Text("a") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uiState.bInput,
                onValueChange = onBChange,
                label = { Text("b") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uiState.cInput,
                onValueChange = onCChange,
                label = { Text("c") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = onApply,
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
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onSaveToDevice,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar")
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
                                uiState.equation.a == saved.a &&
                                uiState.equation.b == saved.b &&
                                uiState.equation.c == saved.c,
                        onSelect = { onSelectSaved(saved) },
                        onDelete = { onDeleteSaved(saved.id) }
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "a = ${saved.a}   b = ${saved.b}   c = ${saved.c}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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