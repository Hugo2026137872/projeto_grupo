package com.example.projeto_grupo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.projeto_grupo.data.api.EquationApiService
import com.example.projeto_grupo.data.model.EquationModel
import com.example.projeto_grupo.data.model.UpdateEquationRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EquationUiState(
    val equation: EquationModel = EquationModel(),
    val isLoading: Boolean = false,
    val showMaxima: Boolean = true,
    val showMinima: Boolean = true,
    val showZeros: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    val selectedTab: Int = 0, // 0: Tab 1 (Details & POIs), 1: Tab 2 (Modify Function/Values)
    val expressionInput: String = "a * x^2 + b * x + c",
    val aInput: String = "1.0",
    val bInput: String = "-2.0",
    val cInput: String = "1.0"
)

class EquationViewModel : ViewModel() {
    private val apiService = EquationApiService()

    private val _uiState = MutableStateFlow(EquationUiState())
    val uiState: StateFlow<EquationUiState> = _uiState.asStateFlow()

    init {
        loadEquation()
    }

    fun loadEquation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val eq = apiService.getEquation("eq_1")
            _uiState.update {
                it.copy(
                    equation = eq,
                    isLoading = false,
                    expressionInput = eq.expression,
                    aInput = eq.a.toString(),
                    bInput = eq.b.toString(),
                    cInput = eq.c.toString()
                )
            }
        }
    }

    fun updateEquationFromInputs() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val a = uiState.value.aInput.toDoubleOrNull() ?: 1.0
            val b = uiState.value.bInput.toDoubleOrNull() ?: 0.0
            val c = uiState.value.cInput.toDoubleOrNull() ?: 0.0
            val request = UpdateEquationRequest(
                expression = uiState.value.expressionInput,
                a = a,
                b = b,
                c = c
            )
            val updated = apiService.updateEquation("eq_1", request)
            _uiState.update {
                it.copy(
                    equation = updated,
                    isLoading = false
                )
            }
        }
    }

    fun setBottomSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(isBottomSheetVisible = visible) }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun toggleMaxima(show: Boolean) {
        _uiState.update { it.copy(showMaxima = show) }
    }

    fun toggleMinima(show: Boolean) {
        _uiState.update { it.copy(showMinima = show) }
    }

    fun toggleZeros(show: Boolean) {
        _uiState.update { it.copy(showZeros = show) }
    }

    fun updateExpressionInput(input: String) {
        _uiState.update { it.copy(expressionInput = input) }
    }

    fun updateAInput(input: String) {
        _uiState.update { it.copy(aInput = input) }
    }

    fun updateBInput(input: String) {
        _uiState.update { it.copy(bInput = input) }
    }

    fun updateCInput(input: String) {
        _uiState.update { it.copy(cInput = input) }
    }
}
