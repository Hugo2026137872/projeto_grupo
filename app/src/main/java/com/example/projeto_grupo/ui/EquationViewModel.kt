package com.example.projeto_grupo.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projeto_grupo.data.api.EquationApiService
import com.example.projeto_grupo.data.local.LocalEquationRepository
import com.example.projeto_grupo.data.local.SavedEquation
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
        expression = "a * cos(b * sqrt(x^2 + y^2)) * exp(-0.35 * (x^2 + y^2)) + c",
        a = 1.2,
        b = 3.0,
        c = 0.0
    ),
    EquationPreset(
        name = "Interferência Senoidal",
        expression = "a * sin(b * x) * cos(b * y) + c",
        a = 1.0,
        b = 2.2,
        c = 0.0
    ),
    EquationPreset(
        name = "Sela Hiperbólica",
        expression = "a * (x^2 - y^2) + b * x + c",
        a = 0.35,
        b = 0.2,
        c = 0.0
    ),
    EquationPreset(
        name = "Pico Gaussiano",
        expression = "a * exp(-b * (x^2 + y^2)) + c",
        a = 1.5,
        b = 0.8,
        c = -0.3
    ),
    EquationPreset(
        name = "Paraboloide 3D",
        expression = "a * (x^2 + y^2) + b * x + c",
        a = 0.35,
        b = 0.0,
        c = -0.6
    )
)

private const val DEFAULT_EXPRESSION =
    "a * cos(b * sqrt(x^2 + y^2)) * exp(-0.35 * (x^2 + y^2)) + c"

data class EquationUiState(
    val equation: EquationModel = EquationModel(
        title = "z = f(x, y) = $DEFAULT_EXPRESSION",
        expression = DEFAULT_EXPRESSION
    ),
    val isLoading: Boolean = false,
    val showMaxima: Boolean = true,
    val showMinima: Boolean = true,
    val showZeros: Boolean = true,
    val isBottomSheetVisible: Boolean = false,
    val selectedTab: Int = 0,
    val expressionInput: String = DEFAULT_EXPRESSION,
    val aInput: String = "1.2",
    val bInput: String = "3.0",
    val cInput: String = "0.0",
    val expressionError: String? = null,
    val presets: List<EquationPreset> = defaultEquationPresets,
    val savedEquations: List<SavedEquation> = emptyList(),
    val saveNameInput: String = "",
    val saveFeedbackMessage: String? = null
)

class EquationViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = EquationApiService()
    private val localRepo = LocalEquationRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(EquationUiState())
    val uiState: StateFlow<EquationUiState> = _uiState.asStateFlow()

    init {
        val storedList = localRepo.getSavedEquations()
        val storedActive = localRepo.loadActiveEquation()

        val activeExpr = storedActive?.expression ?: _uiState.value.equation.expression
        val activeA = storedActive?.a ?: _uiState.value.equation.a
        val activeB = storedActive?.b ?: _uiState.value.equation.b
        val activeC = storedActive?.c ?: _uiState.value.equation.c

        val initialPois = MathExpressionEvaluator.computePointsOfInterest(
            activeExpr, activeA, activeB, activeC
        )

        _uiState.update { state ->
            state.copy(
                equation = state.equation.copy(
                    title = "z = f(x, y) = $activeExpr",
                    expression = activeExpr,
                    a = activeA,
                    b = activeB,
                    c = activeC,
                    pointsOfInterest = initialPois
                ),
                expressionInput = activeExpr,
                aInput = activeA.toString(),
                bInput = activeB.toString(),
                cInput = activeC.toString(),
                savedEquations = storedList
            )
        }
    }

    fun loadEquation() {
        // Keep the user's locally stored active equation if one exists on the device
        if (localRepo.loadActiveEquation() != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val remote = runCatching { apiService.getEquation("eq_1") }.getOrNull()
            if (remote != null && MathExpressionEvaluator.validate(remote.expression, remote.a, remote.b, remote.c) == null) {
                val pois = MathExpressionEvaluator.computePointsOfInterest(
                    remote.expression, remote.a, remote.b, remote.c
                )
                localRepo.saveActiveEquation(remote.expression, remote.a, remote.b, remote.c)
                _uiState.update {
                    it.copy(
                        equation = remote.copy(
                            title = "z = f(x, y) = ${remote.expression}",
                            pointsOfInterest = pois
                        ),
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
            _uiState.update { it.copy(expressionError = error, saveFeedbackMessage = null) }
            return
        }

        val pois = MathExpressionEvaluator.computePointsOfInterest(expr, a, b, c)
        localRepo.saveActiveEquation(expr, a, b, c)

        val localUpdated = _uiState.value.equation.copy(
            title = "z = f(x, y) = $expr",
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
                    equation = remoteUpdated?.copy(
                        title = "z = f(x, y) = $expr",
                        pointsOfInterest = pois
                    ) ?: localUpdated,
                    isLoading = false
                )
            }
        }
    }

    fun saveCurrentEquationToDevice() {
        val expr = _uiState.value.expressionInput.trim()
        val a = parseParameterNumber(_uiState.value.aInput) ?: _uiState.value.equation.a
        val b = parseParameterNumber(_uiState.value.bInput) ?: _uiState.value.equation.b
        val c = parseParameterNumber(_uiState.value.cInput) ?: _uiState.value.equation.c

        val error = MathExpressionEvaluator.validate(expr, a, b, c)
        if (error != null) {
            _uiState.update { it.copy(expressionError = error, saveFeedbackMessage = null) }
            return
        }

        val updatedList = localRepo.saveEquation(
            name = _uiState.value.saveNameInput,
            expression = expr,
            a = a,
            b = b,
            c = c
        )

        val pois = MathExpressionEvaluator.computePointsOfInterest(expr, a, b, c)
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = a,
                    b = b,
                    c = c,
                    pointsOfInterest = pois
                ),
                savedEquations = updatedList,
                saveNameInput = "",
                expressionError = null,
                saveFeedbackMessage = "Equação guardada no dispositivo."
            )
        }
    }

    fun saveEquationToDevice(
        name: String,
        expression: String,
        aInput: String,
        bInput: String,
        cInput: String
    ): String? {
        val expr = expression.trim()
        val a = parseParameterNumber(aInput) ?: return "Valor inválido para 'a'."
        val b = parseParameterNumber(bInput) ?: return "Valor inválido para 'b'."
        val c = parseParameterNumber(cInput) ?: return "Valor inválido para 'c'."

        val error = MathExpressionEvaluator.validate(expr, a, b, c)
        if (error != null) {
            return error
        }

        val updatedList = localRepo.saveEquation(
            name = name,
            expression = expr,
            a = a,
            b = b,
            c = c
        )

        val pois = MathExpressionEvaluator.computePointsOfInterest(expr, a, b, c)
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = a,
                    b = b,
                    c = c,
                    pointsOfInterest = pois
                ),
                expressionInput = expr,
                aInput = a.toString(),
                bInput = b.toString(),
                cInput = c.toString(),
                savedEquations = updatedList,
                saveNameInput = "",
                expressionError = null,
                saveFeedbackMessage = "Equação guardada no dispositivo."
            )
        }
        return null
    }

    fun applySavedEquation(saved: SavedEquation) {
        _uiState.update {
            it.copy(
                expressionInput = saved.expression,
                aInput = saved.a.toString(),
                bInput = saved.b.toString(),
                cInput = saved.c.toString(),
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
        updateEquationFromInputs()
    }

    fun deleteSavedEquation(id: String) {
        val updatedList = localRepo.deleteEquation(id)
        _uiState.update {
            it.copy(
                savedEquations = updatedList,
                saveFeedbackMessage = null
            )
        }
    }

    fun applyPreset(preset: EquationPreset) {
        _uiState.update {
            it.copy(
                expressionInput = preset.expression,
                aInput = preset.a.toString(),
                bInput = preset.b.toString(),
                cInput = preset.c.toString(),
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
        updateEquationFromInputs()
    }

    fun updateSaveNameInput(input: String) {
        _uiState.update { it.copy(saveNameInput = input, saveFeedbackMessage = null) }
    }

    fun setBottomSheetVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                isBottomSheetVisible = visible,
                saveFeedbackMessage = if (!visible) null else it.saveFeedbackMessage
            )
        }
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab, saveFeedbackMessage = null) }
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
        _uiState.update {
            it.copy(
                expressionInput = input,
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
    }

    fun updateAInput(input: String) {
        _uiState.update { it.copy(aInput = input, saveFeedbackMessage = null) }
        parseParameterNumber(input)?.let { applyLiveParameterChange(a = it) }
    }

    fun updateBInput(input: String) {
        _uiState.update { it.copy(bInput = input, saveFeedbackMessage = null) }
        parseParameterNumber(input)?.let { applyLiveParameterChange(b = it) }
    }

    fun updateCInput(input: String) {
        _uiState.update { it.copy(cInput = input, saveFeedbackMessage = null) }
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
        localRepo.saveActiveEquation(expr, a, b, c)

        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
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