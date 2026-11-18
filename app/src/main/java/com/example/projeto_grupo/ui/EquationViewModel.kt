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

data class EquationPreset(
    val name: String,
    val expression: String,
    val a: Double,
    val b: Double,
    val c: Double
)

val defaultEquationPresets = listOf(
    EquationPreset(
        name = "Onda Circular (Ripple)",
        expression = "a * cos(b * sqrt(x^2 + z^2)) * exp(-0.35 * (x^2 + z^2)) + c",
        a = 1.2,
        b = 3.0,
        c = 0.0
    ),
    EquationPreset(
        name = "Interferência Senoidal",
        expression = "a * sin(b * x) * cos(b * z) + c",
        a = 1.0,
        b = 2.2,
        c = 0.0
    ),
    EquationPreset(
        name = "Sela Hiperbólica",
        expression = "a * (x^2 - z^2) + b * x + c",
        a = 0.35,
        b = 0.2,
        c = 0.0
    ),
    EquationPreset(
        name = "Pico Gaussiano",
        expression = "a * exp(-b * (x^2 + z^2)) + c",
        a = 1.5,
        b = 0.8,
        c = -0.3
    ),
    EquationPreset(
        name = "Paraboloide 3D",
        expression = "a * (x^2 + z^2) + b * x + c",
        a = 0.35,
        b = 0.0,
        c = -0.6
    )
)

data class EquationUiState(
    val equation: EquationModel = EquationModel(),
    val isLoading: Boolean = false,
    val showMaxima: Boolean = true,
    val showMinima: Boolean = true,
    val showZeros: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    val selectedTab: Int = 0,
    val expressionInput: String = "a * cos(b * sqrt(x^2 + z^2)) * exp(-0.35 * (x^2 + z^2)) + c",
    val aInput: String = "1.2",
    val bInput: String = "3.0",
    val cInput: String = "0.0",
    val expressionError: String? = null,
    val presets: List<EquationPreset> = defaultEquationPresets
)

class EquationViewModel : ViewModel() {
    private val apiService = EquationApiService()

    private val _uiState = MutableStateFlow(EquationUiState())
    val uiState: StateFlow<EquationUiState> = _uiState.asStateFlow()

    init {
        val initial = _uiState.value.equation
        val initialPois = MathExpressionEvaluator.computePointsOfInterest(
            initial.expression, initial.a, initial.b, initial.c
        )
        _uiState.update {
            it.copy(equation = initial.copy(pointsOfInterest = initialPois))
        }
    }

    fun loadEquation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val remote = runCatching { apiService.getEquation("eq_1") }.getOrNull()
            if (remote != null && MathExpressionEvaluator.validate(remote.expression, remote.a, remote.b, remote.c) == null) {
                val pois = MathExpressionEvaluator.computePointsOfInterest(
                    remote.expression, remote.a, remote.b, remote.c
                )
                _uiState.update {
                    it.copy(
                        equation = remote.copy(pointsOfInterest = pois),
                        isLoading = false,
                        expressionInput = remote.expression,
                        aInput = remote.a.toString(),
                        bInput = remote.b.toString(),
                        cInput = remote.c.toString(),
                        expressionError = null
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun updateEquationFromInputs() {
        val expr = _uiState.value.expressionInput.trim()
        val a = parseParameterNumber(_uiState.value.aInput) ?: _uiState.value.equation.a
        val b = parseParameterNumber(_uiState.value.bInput) ?: _uiState.value.equation.b
        val c = parseParameterNumber(_uiState.value.cInput) ?: _uiState.value.equation.c

        val error = MathExpressionEvaluator.validate(expr, a, b, c)
        if (error != null) {
            _uiState.update { it.copy(expressionError = error) }
            return
        }

        val pois = MathExpressionEvaluator.computePointsOfInterest(expr, a, b, c)
        val localUpdated = _uiState.value.equation.copy(
            title = "f(x, z) = $expr",
            expression = expr,
            a = a,
            b = b,
            c = c,
            pointsOfInterest = pois
        )

        _uiState.update {
            it.copy(
                equation = localUpdated,
                expressionError = null,
                isLoading = true
            )
        }

        viewModelScope.launch {
            val request = UpdateEquationRequest(expression = expr, a = a, b = b, c = c)
            val remoteUpdated = runCatching { apiService.updateEquation("eq_1", request) }.getOrNull()
            _uiState.update {
                it.copy(
                    equation = remoteUpdated?.copy(pointsOfInterest = pois) ?: localUpdated,
                    isLoading = false
                )
            }
        }
    }

    fun applyPreset(preset: EquationPreset) {
        _uiState.update {
            it.copy(
                expressionInput = preset.expression,
                aInput = preset.a.toString(),
                bInput = preset.b.toString(),
                cInput = preset.c.toString(),
                expressionError = null
            )
        }
        updateEquationFromInputs()
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
        _uiState.update { it.copy(expressionInput = input, expressionError = null) }
    }

    fun updateAInput(input: String) {
        _uiState.update { it.copy(aInput = input) }
        parseParameterNumber(input)?.let { applyLiveParameterChange(a = it) }
    }

    fun updateBInput(input: String) {
        _uiState.update { it.copy(bInput = input) }
        parseParameterNumber(input)?.let { applyLiveParameterChange(b = it) }
    }

    fun updateCInput(input: String) {
        _uiState.update { it.copy(cInput = input) }
        parseParameterNumber(input)?.let { applyLiveParameterChange(c = it) }
    }

    private fun parseParameterNumber(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toDoubleOrNull()?.let { return it }
        return runCatching {
            MathExpressionEvaluator.evaluate(trimmed, 0.0, 0.0, 0.0, 0.0, 0.0)
        }.getOrNull()?.takeIf { it.isFinite() }
    }

    private fun applyLiveParameterChange(
        a: Double = _uiState.value.equation.a,
        b: Double = _uiState.value.equation.b,
        c: Double = _uiState.value.equation.c
    ) {
        val expr = _uiState.value.expressionInput.trim()
        if (MathExpressionEvaluator.validate(expr, a, b, c) != null) return

        val pois = MathExpressionEvaluator.computePointsOfInterest(expr, a, b, c)
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "f(x, z) = $expr",
                    expression = expr,
                    a = a,
                    b = b,
                    c = c,
                    pointsOfInterest = pois
                ),
                expressionError = null
            )
        }
    }
}