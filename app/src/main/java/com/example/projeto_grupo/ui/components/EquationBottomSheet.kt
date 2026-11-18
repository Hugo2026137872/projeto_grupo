package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.ui.EquationPreset
import com.example.projeto_grupo.ui.EquationUiState
import com.example.projeto_grupo.ui.EquationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationBottomSheet(
    viewModel: EquationViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (!uiState.isBottomSheetVisible) return

    ModalBottomSheet(
        onDismissRequest = { viewModel.setBottomSheetVisible(false) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("Aba 1: Detalhes & POIs") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { Text("Aba 2: Editar Equação") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.selectedTab) {
                0 -> DetailsAndPoisTab(
                    uiState = uiState,
                    onToggleMaxima = viewModel::toggleMaxima,
                    onToggleMinima = viewModel::toggleMinima,
                    onToggleZeros = viewModel::toggleZeros
                )
                1 -> EditEquationTab(
                    uiState = uiState,
                    onSelectPreset = viewModel::applyPreset,
                    onExpressionChange = viewModel::updateExpressionInput,
                    onAChange = viewModel::updateAInput,
                    onBChange = viewModel::updateBInput,
                    onCChange = viewModel::updateCInput,
                    onApply = viewModel::updateEquationFromInputs
                )
            }
        }
    }
}

@Composable
private fun DetailsAndPoisTab(
    uiState: EquationUiState,
    onToggleMaxima: (Boolean) -> Unit,
    onToggleMinima: (Boolean) -> Unit,
    onToggleZeros: (Boolean) -> Unit
) {
    val filteredPois = uiState.equation.pointsOfInterest.filter { poi ->
        when (poi.type) {
            PointType.MAXIMA -> uiState.showMaxima
            PointType.MINIMA -> uiState.showMinima
            PointType.ZERO -> uiState.showZeros
            PointType.INFLECTION -> true
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Equação Ativa",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "f(x, z) = ${uiState.equation.expression}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
        )
        Text(
            text = "Parâmetros: a = ${uiState.equation.a}   •   b = ${uiState.equation.b}   •   c = ${uiState.equation.c}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Pontos de Interesse no Modelo 3D",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Working Checkboxes synced with both ViewModel state and AR 3D markers
        PoiFilterCheckboxRow(
            label = "Máximos Locais",
            markerColor = Color(0xFFFFD600), // Matches Gold 3D marker
            checked = uiState.showMaxima,
            onCheckedChange = onToggleMaxima
        )
        PoiFilterCheckboxRow(
            label = "Mínimos Locais",
            markerColor = Color(0xFF00E676), // Matches Green 3D marker
            checked = uiState.showMinima,
            onCheckedChange = onToggleMinima
        )
        PoiFilterCheckboxRow(
            label = "Zeros (Plano y = 0)",
            markerColor = Color(0xFFFF5252), // Matches Coral Red 3D marker
            checked = uiState.showZeros,
            onCheckedChange = onToggleZeros
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredPois.isEmpty()) {
            Text(
                text = "Nenhum ponto selecionado ou detetado no domínio [-2, 2].",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredPois, key = { it.id }) { poi ->
                    PoiItemCard(poi = poi)
                }
            }
        }
    }
}

@Composable
private fun PoiFilterCheckboxRow(
    label: String,
    markerColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(markerColor)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
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
        tonalElevation = 2.dp,
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
    onApply: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            label = { Text("Expressão f(x, z)") },
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
    }
}