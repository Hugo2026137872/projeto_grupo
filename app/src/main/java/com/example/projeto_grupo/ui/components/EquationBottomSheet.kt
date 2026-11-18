package com.example.projeto_grupo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.projeto_grupo.ui.EquationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EquationBottomSheet(
    viewModel: EquationViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val equation = uiState.equation

    if (uiState.isBottomSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setBottomSheetVisible(false) }
        ) {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Tabs Header
                TabRow(selectedTabIndex = uiState.selectedTab) {
                    Tab(
                        selected = uiState.selectedTab == 0,
                        onClick = { viewModel.setSelectedTab(0) },
                        text = { Text("Aba 1: Detalhes & POIs") }
                    )
                    Tab(
                        selected = uiState.selectedTab == 1,
                        onClick = { viewModel.setSelectedTab(1) },
                        text = { Text("Aba 2: Modificar") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.selectedTab == 0) {
                    // Tab 1: Equation details & Checkboxes for points of interest
                    Text(
                        text = equation.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Expressão: ${equation.expression}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Pontos de Interesse (Assinalados em RA):",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Checkboxes
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = uiState.showMaxima,
                            onCheckedChange = { viewModel.toggleMaxima(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mostrar Máximos")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = uiState.showMinima,
                            onCheckedChange = { viewModel.toggleMinima(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mostrar Mínimos / Vértice")
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = uiState.showZeros,
                            onCheckedChange = { viewModel.toggleZeros(it) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mostrar Zeros / Raízes")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // List of points of interest
                    equation.pointsOfInterest.forEach { poi ->
                        Text(
                            text = "• ${poi.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                } else {
                    // Tab 2: Modify function or values
                    Text(
                        text = "Modificar Valores ou Função (API Endpoint Sync)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = uiState.expressionInput,
                        onValueChange = { viewModel.updateExpressionInput(it) },
                        label = { Text("Função f(x)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.aInput,
                            onValueChange = { viewModel.updateAInput(it) },
                            label = { Text("Coeficiente a") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.bInput,
                            onValueChange = { viewModel.updateBInput(it) },
                            label = { Text("Coeficiente b") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = uiState.cInput,
                            onValueChange = { viewModel.updateCInput(it) },
                            label = { Text("Coeficiente c") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.updateEquationFromInputs()
                            viewModel.setBottomSheetVisible(false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Atualizar Modelo & Enviar para API")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
