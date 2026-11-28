package com.example.projeto_grupo.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.projeto_grupo.data.api.EquationApiService
import com.example.projeto_grupo.data.local.AuthTokenManager
import com.example.projeto_grupo.data.local.LocalEquationRepository
import com.example.projeto_grupo.data.local.SavedEquation
import com.example.projeto_grupo.data.model.ApiEquationResponse
import com.example.projeto_grupo.data.model.CreateEquationRequest
import com.example.projeto_grupo.data.model.EquationModel
import com.example.projeto_grupo.data.model.LoginRequest
import com.example.projeto_grupo.data.model.RegisterRequest
import com.example.projeto_grupo.data.model.UpdateEquationRequest
import com.example.projeto_grupo.data.model.UserResponse
import com.example.projeto_grupo.ml.MathOcrEngine
import com.example.projeto_grupo.ml.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    val saveFeedbackMessage: String? = null,

    // Server & Auth State
    val baseUrlInput: String = "http://10.0.2.2:8000",
    val isLoggedIn: Boolean = false,
    val currentUser: UserResponse? = null,
    val authToken: String? = null,
    val userApiKey: String? = null,
    val isServerConnected: Boolean? = null,
    val authLoading: Boolean = false,
    val authErrorMessage: String? = null,
    val authSuccessMessage: String? = null,
    val serverEquations: List<ApiEquationResponse> = emptyList(),
    val isSyncingCloud: Boolean = false,
    val activeCloudEquationId: String? = null,
    val activeCloudUpdatedAt: String? = null
)

class EquationViewModel(application: Application) : AndroidViewModel(application) {
    private val apiService = EquationApiService()
    private val localRepo = LocalEquationRepository(application.applicationContext)
    private val authManager = AuthTokenManager(application.applicationContext)
    private val ocrEngine by lazy { MathOcrEngine(application.applicationContext) }
    private var poiJob: Job? = null
    private var cloudWatchJob: Job? = null

    private val _uiState = MutableStateFlow(EquationUiState())
    val uiState: StateFlow<EquationUiState> = _uiState.asStateFlow()

    init {
        val storedList = localRepo.getSavedEquations()
        val storedActive = localRepo.loadActiveEquation()

        val savedBaseUrl = authManager.getBaseUrl()
        apiService.setBaseUrl(savedBaseUrl)

        val token = authManager.getToken()
        val storedApiKey = authManager.getApiKey()
        val user = authManager.getUser()
        val loggedIn = authManager.isLoggedIn()

        val activeExpr = storedActive?.expression ?: _uiState.value.equation.expression
        val activeParams = storedActive?.getUsedParams() ?: filterParamsForExpression(
            activeExpr,
            mapOf("a" to 1.2, "b" to 3.0, "c" to 0.0)
        )
        val activeA = activeParams["a"] ?: 1.0
        val activeB = activeParams["b"] ?: 1.0
        val activeC = activeParams["c"] ?: 0.0

        _uiState.update { state ->
            state.copy(
                equation = state.equation.copy(
                    title = "z = f(x, y) = $activeExpr",
                    expression = activeExpr,
                    a = activeA,
                    b = activeB,
                    c = activeC,
                    params = activeParams,
                    pointsOfInterest = emptyList()
                ),
                expressionInput = activeExpr,
                paramsInput = activeParams.mapValues { it.value.toString() },
                aInput = activeParams["a"]?.toString() ?: "",
                bInput = activeParams["b"]?.toString() ?: "",
                cInput = activeParams["c"]?.toString() ?: "",
                savedEquations = storedList,
                baseUrlInput = savedBaseUrl,
                isLoggedIn = loggedIn,
                currentUser = user,
                authToken = token,
                userApiKey = storedApiKey
            )
        }
        recomputePoisAsync(activeExpr, activeParams, _uiState.value.xDomain, _uiState.value.yDomain)

        viewModelScope.launch {
            checkServerHealth()
            if (loggedIn && !token.isNullOrBlank()) {
                verifySessionAndLoadServerData()
            } else {
                fetchServerEquations()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cloudWatchJob?.cancel()
        apiService.close()
        ocrEngine.close()
    }

    private fun stopCloudEquationWatcher() {
        cloudWatchJob?.cancel()
        cloudWatchJob = null
        _uiState.update {
            it.copy(
                activeCloudEquationId = null,
                activeCloudUpdatedAt = null
            )
        }
    }

    private fun startCloudEquationWatcher(equationId: String) {
        cloudWatchJob?.cancel()
        cloudWatchJob = viewModelScope.launch {
            while (isActive) {
                delay(1_500L)
                val current = _uiState.value
                if (current.activeCloudEquationId != equationId) break

                val res = apiService.getEquationRaw(
                    id = equationId,
                    baseUrl = current.baseUrlInput,
                    token = current.authToken
                )
                res.onSuccess { remote ->
                    val latest = _uiState.value
                    if (latest.activeCloudEquationId != equationId) return@onSuccess

                    val remoteSource = remote.params.ifEmpty {
                        mapOf("a" to remote.a, "b" to remote.b, "c" to remote.c)
                    }
                    val remoteParams = filterParamsForExpression(remote.expression, remoteSource)
                    val changed = remote.updatedAt != latest.activeCloudUpdatedAt ||
                        remoteParams != latest.equation.getEffectiveParams() ||
                        remote.expression != latest.equation.expression

                    if (changed && MathExpressionEvaluator.validate(remote.expression, remoteParams) == null) {
                        localRepo.saveActiveEquation(remote.expression, remoteParams)
                        _uiState.update { state ->
                            state.copy(
                                equation = state.equation.copy(
                                    id = remote.id,
                                    title = remote.title,
                                    expression = remote.expression,
                                    a = remoteParams["a"] ?: remote.a,
                                    b = remoteParams["b"] ?: remote.b,
                                    c = remoteParams["c"] ?: remote.c,
                                    params = remoteParams
                                ),
                                expressionInput = remote.expression,
                                paramsInput = remoteParams.mapValues { e -> e.value.toString() },
                                aInput = remoteParams["a"]?.toString() ?: "",
                                bInput = remoteParams["b"]?.toString() ?: "",
                                cInput = remoteParams["c"]?.toString() ?: "",
                                activeCloudUpdatedAt = remote.updatedAt,
                                expressionError = null
                            )
                        }
                        recomputePoisAsync(remote.expression, remoteParams, latest.xDomain, latest.yDomain)
                    } else if (remote.updatedAt != latest.activeCloudUpdatedAt) {
                        _uiState.update { it.copy(activeCloudUpdatedAt = remote.updatedAt) }
                    }
                }
            }
        }
    }

    suspend fun recognizeHandwrittenEquation(
        bitmap: Bitmap,
        cropRect: RectF? = null
    ): Result<OcrResult> = runCatching {
        ocrEngine.recognize(bitmap, cropRect)
    }

    private fun recomputePoisAsync(
        expression: String,
        params: Map<String, Double>,
        xDomain: Double,
        yDomain: Double
    ) {
        poiJob?.cancel()
        poiJob = viewModelScope.launch(Dispatchers.Default) {
            val pois = MathExpressionEvaluator.computePointsOfInterest(
                expression = expression,
                params = params,
                xDomain = xDomain,
                yDomain = yDomain
            )
            _uiState.update { state ->
                if (state.equation.expression == expression &&
                    state.equation.getEffectiveParams() == params &&
                    state.xDomain == xDomain &&
                    state.yDomain == yDomain
                ) {
                    state.copy(equation = state.equation.copy(pointsOfInterest = pois))
                } else {
                    state
                }
            }
        }
    }

    // --- Server & Auth Actions ---

    fun checkServerHealth(customUrl: String? = null) {
        val targetUrl = customUrl?.trim()?.ifBlank { null } ?: _uiState.value.baseUrlInput
        _uiState.update { it.copy(isServerConnected = null) }
        viewModelScope.launch {
            val isOk = apiService.healthCheck(targetUrl)
            _uiState.update { it.copy(isServerConnected = isOk) }
        }
    }

    fun updateBaseUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        authManager.saveBaseUrl(trimmed)
        val storedUrl = authManager.getBaseUrl()
        apiService.setBaseUrl(storedUrl)
        _uiState.update { it.copy(baseUrlInput = storedUrl) }
        checkServerHealth(storedUrl)
    }

    fun registerUser(username: String, email: String, pass: String) {
        val uName = username.trim()
        val uEmail = email.trim()
        val uPass = pass.trim()

        if (uName.length < 3) {
            _uiState.update { it.copy(authErrorMessage = "O nome de utilizador deve ter pelo menos 3 caracteres.") }
            return
        }
        if (!uEmail.contains("@")) {
            _uiState.update { it.copy(authErrorMessage = "Por favor, insira um email válido.") }
            return
        }
        if (uPass.length < 6) {
            _uiState.update { it.copy(authErrorMessage = "A palavra-passe deve ter pelo menos 6 caracteres.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authErrorMessage = null, authSuccessMessage = null) }
            val req = RegisterRequest(username = uName, email = uEmail, password = uPass)
            val res = apiService.register(_uiState.value.baseUrlInput, req)

            res.fold(
                onSuccess = { authResp ->
                    authManager.saveAuthSession(authResp.accessToken, authResp.user)
                    authResp.apiKey?.let { authManager.saveApiKey(it) }
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            isLoggedIn = true,
                            currentUser = authResp.user,
                            authToken = authResp.accessToken,
                            userApiKey = authResp.apiKey ?: authManager.getApiKey(),
                            authSuccessMessage = "Registo efetuado com sucesso!",
                            authErrorMessage = null
                        )
                    }
                    fetchServerEquations()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authErrorMessage = err.message ?: "Falha ao registar utilizador."
                        )
                    }
                }
            )
        }
    }

    fun loginUser(identifier: String, pass: String) {
        val idInput = identifier.trim()
        val uPass = pass.trim()

        if (idInput.isEmpty()) {
            _uiState.update { it.copy(authErrorMessage = "Insira o seu utilizador ou email.") }
            return
        }
        if (uPass.isEmpty()) {
            _uiState.update { it.copy(authErrorMessage = "Insira a sua palavra-passe.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authErrorMessage = null, authSuccessMessage = null) }
            val req = LoginRequest(identifier = idInput, password = uPass)
            val res = apiService.login(_uiState.value.baseUrlInput, req)

            res.fold(
                onSuccess = { authResp ->
                    authManager.saveAuthSession(authResp.accessToken, authResp.user)
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            isLoggedIn = true,
                            currentUser = authResp.user,
                            authToken = authResp.accessToken,
                            authSuccessMessage = "Sessão iniciada com sucesso!",
                            authErrorMessage = null
                        )
                    }
                    fetchServerEquations()
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authErrorMessage = err.message ?: "Credenciais inválidas."
                        )
                    }
                }
            )
        }
    }

    fun rotateApiKey() {
        val token = _uiState.value.authToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authErrorMessage = null, authSuccessMessage = null) }
            val res = apiService.rotateApiKey(_uiState.value.baseUrlInput, token)
            res.fold(
                onSuccess = { resp ->
                    authManager.saveApiKey(resp.apiKey)
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            userApiKey = resp.apiKey,
                            authSuccessMessage = "Nova API Key gerada com sucesso!"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authErrorMessage = err.message ?: "Erro ao gerar API Key."
                        )
                    }
                }
            )
        }
    }

    fun logoutUser() {
        stopCloudEquationWatcher()
        authManager.clearAuthSession()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                currentUser = null,
                authToken = null,
                userApiKey = null,
                authSuccessMessage = "Sessão terminada.",
                authErrorMessage = null,
                serverEquations = emptyList()
            )
        }
    }

    fun clearAuthFeedback() {
        _uiState.update { it.copy(authErrorMessage = null, authSuccessMessage = null) }
    }

    fun clearSaveFeedback() {
        _uiState.update { it.copy(saveFeedbackMessage = null) }
    }

    private suspend fun verifySessionAndLoadServerData() {
        val token = _uiState.value.authToken ?: return
        val res = apiService.getMe(_uiState.value.baseUrlInput, token)
        res.fold(
            onSuccess = { updatedUser ->
                authManager.saveAuthSession(token, updatedUser)
                _uiState.update { it.copy(currentUser = updatedUser) }
                fetchServerEquations()
            },
            onFailure = { err ->
                if (err is com.example.projeto_grupo.data.api.UnauthorizedException) {
                    logoutUser()
                } else {
                    fetchServerEquations()
                }
            }
        )
    }

    fun fetchServerEquations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingCloud = true) }
            val res = apiService.getEquations(_uiState.value.baseUrlInput, _uiState.value.authToken)
            res.fold(
                onSuccess = { list ->
                    val userCloudEquations = list.filterNot { it.id == "eq_1" }
                    _uiState.update {
                        it.copy(
                            serverEquations = userCloudEquations,
                            isSyncingCloud = false,
                            isServerConnected = true
                        )
                    }
                },
                onFailure = {
                    _uiState.update { it.copy(isSyncingCloud = false) }
                }
            )
        }
    }

    fun saveEquationToCloud(
        name: String,
        expression: String,
        paramsInput: Map<String, String>,
        onResult: ((Result<String>) -> Unit)? = null
    ): String? {
        val token = _uiState.value.authToken
        if (!_uiState.value.isLoggedIn || token.isNullOrBlank()) {
            return "Inicie sessão no separador Perfil para guardar na nuvem."
        }

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

        val a = parsedParams["a"] ?: 1.0
        val b = parsedParams["b"] ?: 1.0
        val c = parsedParams["c"] ?: 0.0
        val cleanName = name.trim().ifEmpty { "z = f(x, y) = $expr" }

        if (_uiState.value.serverEquations.any { it.title.equals(cleanName, ignoreCase = true) }) {
            return "Já existe uma equação na nuvem com o nome '$cleanName'. Escolha outro nome."
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, saveFeedbackMessage = null) }
            val req = CreateEquationRequest(
                title = cleanName,
                expression = expr,
                a = a,
                b = b,
                c = c,
                params = parsedParams
            )
            val res = apiService.createEquation(_uiState.value.baseUrlInput, req, token)
            res.fold(
                onSuccess = { created ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saveFeedbackMessage = "Equação guardada na nuvem com o nome '$cleanName'!",
                            saveNameInput = "",
                            activeCloudEquationId = created.id,
                            activeCloudUpdatedAt = created.updatedAt
                        )
                    }
                    startCloudEquationWatcher(created.id)
                    fetchServerEquations()
                    onResult?.invoke(Result.success(cleanName))
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saveFeedbackMessage = "Erro ao guardar na nuvem: ${err.message}"
                        )
                    }
                    onResult?.invoke(Result.failure(err))
                }
            )
        }
        return null
    }

    fun saveCurrentEquationToCloud(title: String? = null) {
        val customName = title?.trim()?.ifBlank { null }
            ?: _uiState.value.saveNameInput.trim().ifBlank { null }
        val expr = _uiState.value.expressionInput.trim()
        val err = saveEquationToCloud(
            name = customName ?: "z = f(x, y) = $expr",
            expression = expr,
            paramsInput = _uiState.value.paramsInput
        )
        if (err != null) {
            _uiState.update { it.copy(saveFeedbackMessage = err) }
        }
    }

    fun deleteServerEquation(id: String) {
        val token = _uiState.value.authToken ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val res = apiService.deleteEquation(id = id, baseUrl = _uiState.value.baseUrlInput, token = token)
            res.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            serverEquations = state.serverEquations.filterNot { it.id == id },
                            saveFeedbackMessage = "Equação eliminada da nuvem."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saveFeedbackMessage = "Erro ao eliminar: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    fun applyApiEquation(apiEq: ApiEquationResponse): EquationModel {
        val expr = apiEq.expression
        val sourceParams = apiEq.params.ifEmpty { mapOf("a" to apiEq.a, "b" to apiEq.b, "c" to apiEq.c) }
        val effectiveParams = filterParamsForExpression(expr, sourceParams)
        localRepo.saveActiveEquation(expr, effectiveParams)

        val currentState = _uiState.value
        val updatedEquation = currentState.equation.copy(
            id = apiEq.id,
            title = apiEq.title,
            expression = expr,
            a = effectiveParams["a"] ?: apiEq.a,
            b = effectiveParams["b"] ?: apiEq.b,
            c = effectiveParams["c"] ?: apiEq.c,
            params = effectiveParams
        )

        _uiState.update {
            it.copy(
                equation = updatedEquation,
                expressionInput = expr,
                paramsInput = effectiveParams.mapValues { e -> e.value.toString() },
                aInput = effectiveParams["a"]?.toString() ?: "",
                bInput = effectiveParams["b"]?.toString() ?: "",
                cInput = effectiveParams["c"]?.toString() ?: "",
                activeCloudEquationId = apiEq.id,
                activeCloudUpdatedAt = apiEq.updatedAt,
                expressionError = null,
                saveFeedbackMessage = null
            )
        }
        startCloudEquationWatcher(apiEq.id)
        recomputePoisAsync(expr, effectiveParams, currentState.xDomain, currentState.yDomain)
        return updatedEquation
    }

    // --- Original Local / AR Methods ---

    fun loadEquation() {
        if (localRepo.loadActiveEquation() != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val remote = runCatching {
                apiService.getEquation("eq_1", _uiState.value.baseUrlInput, _uiState.value.authToken)
            }.getOrNull()

            if (remote != null) {
                val remoteParams = filterParamsForExpression(
                    remote.expression,
                    mapOf("a" to remote.a, "b" to remote.b, "c" to remote.c)
                )
                if (MathExpressionEvaluator.validate(remote.expression, remoteParams) == null) {
                    localRepo.saveActiveEquation(remote.expression, remoteParams)
                    val currentState = _uiState.value
                    _uiState.update {
                        it.copy(
                            equation = remote.copy(
                                title = "z = f(x, y) = ${remote.expression}",
                                params = remoteParams
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
                    recomputePoisAsync(remote.expression, remoteParams, currentState.xDomain, currentState.yDomain)
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

        localRepo.saveActiveEquation(expr, parsedParams)

        val a = parsedParams["a"] ?: 1.0
        val b = parsedParams["b"] ?: 1.0
        val c = parsedParams["c"] ?: 0.0

        val currentState = _uiState.value
        val localUpdated = currentState.equation.copy(
            title = "z = f(x, y) = $expr",
            expression = expr,
            a = a,
            b = b,
            c = c,
            params = parsedParams
        )

        _uiState.update {
            it.copy(
                equation = localUpdated,
                expressionError = null,
                isLoading = true
            )
        }
        recomputePoisAsync(expr, parsedParams, currentState.xDomain, currentState.yDomain)

        viewModelScope.launch {
            val request = UpdateEquationRequest(expression = expr, a = a, b = b, c = c)
            val remoteUpdated = runCatching {
                apiService.updateEquation("eq_1", request, _uiState.value.baseUrlInput, _uiState.value.authToken)
            }.getOrNull()

            _uiState.update { state ->
                val latestPois = state.equation.pointsOfInterest
                state.copy(
                    equation = remoteUpdated?.copy(
                        title = "z = f(x, y) = $expr",
                        params = parsedParams,
                        pointsOfInterest = latestPois
                    ) ?: state.equation,
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

        val currentState = _uiState.value
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams
                ),
                savedEquations = updatedList,
                saveNameInput = "",
                expressionError = null,
                saveFeedbackMessage = "Equação guardada no dispositivo."
            )
        }
        recomputePoisAsync(expr, parsedParams, currentState.xDomain, currentState.yDomain)
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

        val currentState = _uiState.value
        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams
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
        recomputePoisAsync(expr, parsedParams, currentState.xDomain, currentState.yDomain)
        return null
    }

    fun applyCustomEquation(
        expression: String,
        params: Map<String, Double>
    ): EquationModel {
        stopCloudEquationWatcher()
        val expr = expression.trim()
        val effectiveParams = filterParamsForExpression(expr, params)
        val a = effectiveParams["a"] ?: 1.0
        val b = effectiveParams["b"] ?: 1.0
        val c = effectiveParams["c"] ?: 0.0

        localRepo.saveActiveEquation(expr, effectiveParams)

        val currentState = _uiState.value
        val updatedEquation = currentState.equation.copy(
            title = "z = f(x, y) = $expr",
            expression = expr,
            a = a,
            b = b,
            c = c,
            params = effectiveParams
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
        recomputePoisAsync(expr, effectiveParams, currentState.xDomain, currentState.yDomain)
        return updatedEquation
    }

    fun applySavedEquation(saved: SavedEquation): EquationModel {
        stopCloudEquationWatcher()
        val usedParams = saved.getUsedParams()
        localRepo.saveActiveEquation(saved.expression, usedParams)

        val currentState = _uiState.value
        val updatedEquation = currentState.equation.copy(
            title = "z = f(x, y) = ${saved.expression}",
            expression = saved.expression,
            a = usedParams["a"] ?: 1.0,
            b = usedParams["b"] ?: 1.0,
            c = usedParams["c"] ?: 0.0,
            params = usedParams
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
        recomputePoisAsync(saved.expression, usedParams, currentState.xDomain, currentState.yDomain)
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
        stopCloudEquationWatcher()
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
            state.copy(xDomain = clamped)
        }
        val state = _uiState.value
        recomputePoisAsync(
            state.equation.expression,
            state.equation.getEffectiveParams(),
            clamped,
            state.yDomain
        )
    }

    fun updateYDomain(domain: Double) {
        val clamped = (kotlin.math.round(domain * 10.0) / 10.0).coerceIn(0.5, 10.0)
        _uiState.update { state ->
            state.copy(yDomain = clamped)
        }
        val state = _uiState.value
        recomputePoisAsync(
            state.equation.expression,
            state.equation.getEffectiveParams(),
            state.xDomain,
            clamped
        )
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

        localRepo.saveActiveEquation(expr, parsedParams)

        _uiState.update {
            it.copy(
                equation = it.equation.copy(
                    title = "z = f(x, y) = $expr",
                    expression = expr,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    params = parsedParams
                ),
                expressionError = null
            )
        }
        recomputePoisAsync(expr, parsedParams, state.xDomain, state.yDomain)
    }
}
