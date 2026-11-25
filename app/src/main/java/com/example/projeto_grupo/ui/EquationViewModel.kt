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
    val xDomain: Double = 2.0,
    val yDomain: Double = 2.0,
    val isBottomSheetVisible: Boolean = false,
    val selectedTab: Int = 0,
    val expressionInput: String = DEFAULT_EXPRESSION,
    val paramsInput: Map<String, String> = mapOf("a" to "1.2", "b" to "3.0", "c" to "0.0"),
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
        val activeParams = storedActive?.getUsedParams() ?: filterParamsForExpression(
            activeExpr,
            mapOf("a" to 1.2, "b" to 3.0, "c" to 0.0)
        )
        val activeA = activeParams["a"] ?: 1.0
        val activeB = activeParams["b"] ?: 1.0
        val activeC = activeParams["c"] ?: 0.0

        val initialPois = MathExpressionEvaluator.computePointsOfInterest(
            activeExpr, activeParams
        )

        _uiState.update { state ->
            state.copy(
                equation = state.equation.copy(
                    title = "z = f(x, y) = $activeExpr",
                    expression = activeExpr,
                    a = activeA,
                    b = activeB,
                    c = activeC,
                    params = activeParams,
                    pointsOfInterest = initialPois
                ),
                expressionInput = activeExpr,
                paramsInput = activeParams.mapValues { it.value.toString() },
                aInput = activeParams["a"]?.toString() ?: "",
                bInput = activeParams["b"]?.toString() ?: "",
                cInput = activeParams["c"]?.toString() ?: "",
                savedEquations = storedList
            )
        }
    }

    fun loadEquation() {
        if (localRepo.loadActiveEquation() != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val remote = runCatching { apiService.getEquation("eq_1") }.getOrNull()
            if (remote != null) {
                val remoteParams = filterParamsForExpression(
                    remote.expression,
                    mapOf("a" to remote.a, "b" to remote.b, "c" to remote.c)
                )
                if (MathExpressionEvaluator.validate(remote.expression, remoteParams) == null) {
                    val pois = MathExpressionEvaluator.computePointsOfInterest(
                        remote.expression, remoteParams
                    )
                    localRepo.saveActiveEquation(remote.expression, remoteParams)
                    _uiState.update {
                        it.copy(
                            equation = remote.copy(
                                title = "z = f(x, y) = ${remote.expression}",
                                params = remoteParams,
                                pointsOfInterest = pois
                            ),
                            isLoading = false,
                            expressionInput = remote.expression,
                            paramsInput = remoteParams.mapValues { e -> e.value.toString() },
                            aInput = remoteParams["a"]?.toString() ?: "",
                            bInput = remoteParams["b"]?.toString() ?: "",
                            cInput = remoteParams["c"]?.toString() ?: "",
                            expressionError = null
                        )
                    }
                    return@launch
                }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun updateEquationFromInputs() {
        val expr = _uiState.value.expressionInput.trim()
        val parsedParams = parseUsedParams(expr, _uiState.value.paramsInput)
        if (parsedParams == null) {
            _uiState.update {
                it.copy(
                    expressionError = "Valor inválido num dos parâmetros.",
                    saveFeedbackMessage = null
                )
            }
            return
        }

        val error = MathExpressionEvaluator.validate(expr, parsedParams)
        if (error != null) {
            _uiState.update { it.copy(expressionError = error, saveFeedbackMessage = null) }
            return
        }

        val pois = MathExpressionEvaluator.computePointsOfInterest(
            expr,
            parsedParams,
            _uiState.value.xDomain,
            _uiState.value.yDomain
        )
        localRepo.saveActiveEquation(expr, parsedParams)

        val a = parsedParams["a"] ?: 1.0
        val b = parsedParams["b"] ?: 1.0
        val c = parsedParams["c"] ?: 0.0

        val localUpdated = _uiState.value.equation.copy(
            title = "z = f(x, y) = $expr",
            expression = expr,
            a = a,
            b = b,
            c = c,
            params = parsedParams,
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
                        params = parsedParams,
                        pointsOfInterest = pois
                    ) ?: localUpdated,
                    isLoading = false
                )
            }
        }
    }

    fun saveCurrentEquationToDevice() {
        val expr = _uiState.value.expressionInput.trim()
        val parsedParams = parseUsedParams(expr, _uiState.value.paramsInput)
        if (parsedParams == null) {
            _uiState.update {
                it.copy(
                    expressionError = "Valor inválido num dos parâmetros.",
                    saveFeedbackMessage = null
                )
            }
            return
        }

        val error = MathExpressionEvaluator.validate(expr, parsedParams)
        if (error != null) {
            _uiState.update { it.copy(expressionError = error, saveFeedbackMessage = null) }
            return
        }

        val updatedList = localRepo.saveEquation(
            name = _uiState.value.saveNameInput,
            expression = expr,
            params = parsedParams
        )

        val pois = MathExpressionEvaluator.computePointsOfInterest(
            expr,
            parsedParams,
            _uiState.value.xDomain,
            _uiState.value.yDomain
        )
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams,
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
        paramsInput: Map<String, String>
    ): String? {
        val expr = expression.trim()
        val usedNames = MathExpressionEvaluator.extractParameters(expr)
        val parsedParams = mutableMapOf<String, Double>()
        for (param in usedNames) {
            val raw = paramsInput[param] ?: if (param == "c") "0.0" else "1.0"
            val value = parseParameterNumber(raw) ?: return "Valor inválido para '$param'."
            parsedParams[param] = value
        }

        val error = MathExpressionEvaluator.validate(expr, parsedParams)
        if (error != null) {
            return error
        }

        val updatedList = localRepo.saveEquation(
            name = name,
            expression = expr,
            params = parsedParams
        )

        val pois = MathExpressionEvaluator.computePointsOfInterest(
            expr,
            parsedParams,
            _uiState.value.xDomain,
            _uiState.value.yDomain
        )
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams,
                    pointsOfInterest = pois
                ),
                expressionInput = expr,
                paramsInput = parsedParams.mapValues { e -> e.value.toString() },
                aInput = parsedParams["a"]?.toString() ?: "",
                bInput = parsedParams["b"]?.toString() ?: "",
                cInput = parsedParams["c"]?.toString() ?: "",
                savedEquations = updatedList,
                saveNameInput = "",
                expressionError = null,
                saveFeedbackMessage = "Equação guardada no dispositivo."
            )
        }
        return null
    }

    fun applyCustomEquation(
        expression: String,
        params: Map<String, Double>
    ): EquationModel {
        val expr = expression.trim()
        val effectiveParams = filterParamsForExpression(expr, params)
        val a = effectiveParams["a"] ?: 1.0
        val b = effectiveParams["b"] ?: 1.0
        val c = effectiveParams["c"] ?: 0.0

        val pois = MathExpressionEvaluator.computePointsOfInterest(
            expr,
            effectiveParams,
            _uiState.value.xDomain,
            _uiState.value.yDomain
        )
        localRepo.saveActiveEquation(expr, effectiveParams)

        val updatedEquation = _uiState.value.equation.copy(
            title = "z = f(x, y) = $expr",
            expression = expr,
            a = a,
            b = b,
            c = c,
            params = effectiveParams,
            pointsOfInterest = pois
        )

        _uiState.update {
            it.copy(
                equation = updatedEquation,
                expressionInput = expr,
                paramsInput = effectiveParams.mapValues { e -> e.value.toString() },
                aInput = effectiveParams["a"]?.toString() ?: "",
                bInput = effectiveParams["b"]?.toString() ?: "",
                cInput = effectiveParams["c"]?.toString() ?: "",
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
        return updatedEquation
    }

    fun applySavedEquation(saved: SavedEquation): EquationModel {
        val usedParams = saved.getUsedParams()
        val pois = MathExpressionEvaluator.computePointsOfInterest(
            saved.expression,
            usedParams,
            _uiState.value.xDomain,
            _uiState.value.yDomain
        )
        localRepo.saveActiveEquation(saved.expression, usedParams)

        val updatedEquation = _uiState.value.equation.copy(
            title = "z = f(x, y) = ${saved.expression}",
            expression = saved.expression,
            a = usedParams["a"] ?: 1.0,
            b = usedParams["b"] ?: 1.0,
            c = usedParams["c"] ?: 0.0,
            params = usedParams,
            pointsOfInterest = pois
        )

        _uiState.update {
            it.copy(
                equation = updatedEquation,
                expressionInput = saved.expression,
                paramsInput = usedParams.mapValues { e -> e.value.toString() },
                aInput = usedParams["a"]?.toString() ?: "",
                bInput = usedParams["b"]?.toString() ?: "",
                cInput = usedParams["c"]?.toString() ?: "",
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
        return updatedEquation
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
        val presetParams = filterParamsForExpression(
            preset.expression,
            mapOf("a" to preset.a, "b" to preset.b, "c" to preset.c)
        )
        _uiState.update {
            it.copy(
                expressionInput = preset.expression,
                paramsInput = presetParams.mapValues { e -> e.value.toString() },
                aInput = presetParams["a"]?.toString() ?: "",
                bInput = presetParams["b"]?.toString() ?: "",
                cInput = presetParams["c"]?.toString() ?: "",
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

    fun updateXDomain(domain: Double) {
        val clamped = (kotlin.math.round(domain * 10.0) / 10.0).coerceIn(0.5, 10.0)
        _uiState.update { state ->
            val expr = state.equation.expression
            val params = state.equation.getEffectiveParams()
            val pois = MathExpressionEvaluator.computePointsOfInterest(
                expr,
                params,
                clamped,
                state.yDomain
            )
            state.copy(
                xDomain = clamped,
                equation = state.equation.copy(pointsOfInterest = pois)
            )
        }
    }

    fun updateYDomain(domain: Double) {
        val clamped = (kotlin.math.round(domain * 10.0) / 10.0).coerceIn(0.5, 10.0)
        _uiState.update { state ->
            val expr = state.equation.expression
            val params = state.equation.getEffectiveParams()
            val pois = MathExpressionEvaluator.computePointsOfInterest(
                expr,
                params,
                state.xDomain,
                clamped
            )
            state.copy(
                yDomain = clamped,
                equation = state.equation.copy(pointsOfInterest = pois)
            )
        }
    }

    fun updateExpressionInput(input: String) {
        val usedNames = MathExpressionEvaluator.extractParameters(input)
        _uiState.update { state ->
            val nextParams = usedNames.associateWith { param ->
                state.paramsInput[param] ?: if (param == "c") "0.0" else "1.0"
            }
            state.copy(
                expressionInput = input,
                paramsInput = nextParams,
                aInput = nextParams["a"] ?: "",
                bInput = nextParams["b"] ?: "",
                cInput = nextParams["c"] ?: "",
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
    }

    fun updateParamInput(param: String, input: String) {
        _uiState.update { state ->
            val nextParams = state.paramsInput.toMutableMap().apply {
                put(param, input)
            }
            state.copy(
                paramsInput = nextParams,
                aInput = if (param == "a") input else state.aInput,
                bInput = if (param == "b") input else state.bInput,
                cInput = if (param == "c") input else state.cInput,
                saveFeedbackMessage = null
            )
        }
        applyLiveParameterChange()
    }

    private fun parseParameterNumber(raw: String): Double? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toDoubleOrNull()?.let { return it }
        return runCatching {
            MathExpressionEvaluator.evaluate(trimmed, 0.0, 0.0, emptyMap())
        }.getOrNull()?.takeIf { it.isFinite() }
    }

    private fun parseUsedParams(
        expression: String,
        paramsInput: Map<String, String>
    ): Map<String, Double>? {
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        if (usedNames.isEmpty()) return emptyMap()
        val parsed = mutableMapOf<String, Double>()
        for (param in usedNames) {
            val raw = paramsInput[param] ?: if (param == "c") "0.0" else "1.0"
            val value = parseParameterNumber(raw) ?: return null
            parsed[param] = value
        }
        return parsed
    }

    private fun filterParamsForExpression(
        expression: String,
        source: Map<String, Double>
    ): Map<String, Double> {
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        if (usedNames.isEmpty()) return emptyMap()
        return usedNames.associateWith { key ->
            source[key] ?: if (key == "c") 0.0 else 1.0
        }
    }

    private fun applyLiveParameterChange() {
        val state = _uiState.value
        val expr = state.expressionInput.trim()
        val parsedParams = parseUsedParams(expr, state.paramsInput) ?: return
        if (MathExpressionEvaluator.validate(expr, parsedParams) != null) return

        val pois = MathExpressionEvaluator.computePointsOfInterest(
            expr,
            parsedParams,
            state.xDomain,
            state.yDomain
        )
        localRepo.saveActiveEquation(expr, parsedParams)

        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams,
                    pointsOfInterest = pois
                ),
                expressionError = null
            )
        }
    }
}
