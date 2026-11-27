package com.example.projeto_grupo.data.api

import com.example.projeto_grupo.data.model.ApiEquationResponse
import com.example.projeto_grupo.data.model.AuthResponse
import com.example.projeto_grupo.data.model.CreateEquationRequest
import com.example.projeto_grupo.data.model.EquationModel
import com.example.projeto_grupo.data.model.LoginRequest
import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.data.model.RegisterRequest
import com.example.projeto_grupo.data.model.UpdateEquationRequest
import com.example.projeto_grupo.data.model.UserResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

class UnauthorizedException(message: String) : Exception(message)

class EquationApiService(
    private var defaultBaseUrl: String = "http://10.0.2.2:8000"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
                encodeDefaults = true
            })
        }
    }

    fun setBaseUrl(url: String) {
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "http://$formatted"
        }
        if (formatted.endsWith("/")) {
            formatted = formatted.dropLast(1)
        }
        defaultBaseUrl = formatted
    }

    fun getBaseUrl(): String = defaultBaseUrl

    suspend fun healthCheck(baseUrl: String = defaultBaseUrl): Boolean {
        return try {
            val response = client.get("$baseUrl/health")
            response.status.isSuccess()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun register(
        baseUrl: String = defaultBaseUrl,
        request: RegisterRequest
    ): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Falha ao registar utilizador.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    suspend fun login(
        baseUrl: String = defaultBaseUrl,
        request: LoginRequest
    ): Result<AuthResponse> {
        return try {
            val response = client.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Credenciais inválidas.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    suspend fun getMe(
        baseUrl: String = defaultBaseUrl,
        token: String
    ): Result<UserResponse> {
        return try {
            val response = client.get("$baseUrl/auth/me") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Sessão expirada.")
                Result.failure(UnauthorizedException(errorMsg))
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Sessão inválida.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    suspend fun getEquations(
        baseUrl: String = defaultBaseUrl,
        token: String? = null
    ): Result<List<ApiEquationResponse>> {
        return try {
            val response = client.get("$baseUrl/equations") {
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Erro ao carregar equações.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    suspend fun getEquation(
        id: String,
        baseUrl: String = defaultBaseUrl,
        token: String? = null
    ): EquationModel {
        return try {
            val response = client.get("$baseUrl/equations/$id") {
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            if (response.status.isSuccess()) {
                val apiEq: ApiEquationResponse = response.body()
                apiEqToModel(apiEq)
            } else {
                fallbackMockEquation()
            }
        } catch (e: Exception) {
            fallbackMockEquation()
        }
    }

    suspend fun createEquation(
        baseUrl: String = defaultBaseUrl,
        request: CreateEquationRequest,
        token: String
    ): Result<ApiEquationResponse> {
        return try {
            val response = client.post("$baseUrl/equations") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(request)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Erro ao criar equação.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    suspend fun updateEquation(
        id: String,
        request: UpdateEquationRequest,
        baseUrl: String = defaultBaseUrl,
        token: String? = null
    ): EquationModel {
        return try {
            val response = client.put("$baseUrl/equations/$id") {
                contentType(ContentType.Application.Json)
                token?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(request)
            }
            if (response.status.isSuccess()) {
                val apiEq: ApiEquationResponse = response.body()
                apiEqToModel(apiEq)
            } else {
                calculateLocalFallbackModel(request)
            }
        } catch (e: Exception) {
            calculateLocalFallbackModel(request)
        }
    }

    suspend fun deleteEquation(
        id: String,
        baseUrl: String = defaultBaseUrl,
        token: String
    ): Result<Boolean> {
        return try {
            val response = client.delete("$baseUrl/equations/$id") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status.isSuccess() || response.status == HttpStatusCode.NoContent) {
                Result.success(true)
            } else {
                val errorMsg = extractErrorMessage(response.bodyAsText(), "Erro ao eliminar equação.")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Erro de ligação ao servidor."))
        }
    }

    private fun extractErrorMessage(bodyText: String, defaultMsg: String): String {
        return runCatching {
            val json = JSONObject(bodyText)
            if (json.has("detail")) {
                val detail = json.get("detail")
                if (detail is String) return@runCatching detail
                if (detail is JSONArray && detail.length() > 0) {
                    val first = detail.optJSONObject(0)
                    if (first != null && first.has("msg")) {
                        return@runCatching first.getString("msg")
                    }
                }
            }
            null
        }.getOrNull() ?: defaultMsg
    }

    private fun apiEqToModel(apiEq: ApiEquationResponse): EquationModel {
        return EquationModel(
            id = apiEq.id,
            title = apiEq.title,
            expression = apiEq.expression,
            a = apiEq.a,
            b = apiEq.b,
            c = apiEq.c,
            params = mapOf("a" to apiEq.a, "b" to apiEq.b, "c" to apiEq.c),
            pointsOfInterest = apiEq.pointsOfInterest
        )
    }

    private fun calculateLocalFallbackModel(request: UpdateEquationRequest): EquationModel {
        val roots = calculateRoots(request.a, request.b, request.c)
        val vertexX = if (request.a != 0.0) -request.b / (2 * request.a) else 0.0
        val vertexY = request.a * vertexX * vertexX + request.b * vertexX + request.c

        val pois = mutableListOf<PointOfInterest>()
        if (request.a != 0.0) {
            val type = if (request.a > 0) PointType.MINIMA else PointType.MAXIMA
            pois.add(PointOfInterest("vertex", type, vertexX, vertexY, 0.0, "Vertex: (%.2f, %.2f)".format(vertexX, vertexY)))
        }
        roots.forEachIndexed { index, root ->
            pois.add(PointOfInterest("root_$index", PointType.ZERO, root, 0.0, 0.0, "Zero: x = %.2f".format(root)))
        }

        return EquationModel(
            id = "eq_1",
            title = "z = f(x, y) = ${request.expression}",
            expression = request.expression,
            a = request.a,
            b = request.b,
            c = request.c,
            params = mapOf("a" to request.a, "b" to request.b, "c" to request.c),
            pointsOfInterest = pois
        )
    }

    private fun fallbackMockEquation(): EquationModel {
        return EquationModel(
            id = "eq_1",
            title = "Quadratic Function: f(x) = ax² + bx + c",
            expression = "a * x^2 + b * x + c",
            a = 1.0,
            b = -2.0,
            c = 1.0,
            pointsOfInterest = listOf(
                PointOfInterest("p1", PointType.MINIMA, 1.0, 0.0, 0.0, "Vertex (Minima): (1.00, 0.00)"),
                PointOfInterest("p2", PointType.ZERO, 1.0, 0.0, 0.0, "Root/Zero: x = 1.00")
            )
        )
    }

    private fun calculateRoots(a: Double, b: Double, c: Double): List<Double> {
        if (a == 0.0) {
            return if (b != 0.0) listOf(-c / b) else emptyList()
        }
        val discriminant = b * b - 4 * a * c
        return when {
            discriminant > 0 -> listOf(
                (-b + sqrt(discriminant)) / (2 * a),
                (-b - sqrt(discriminant)) / (2 * a)
            )
            discriminant == 0.0 -> listOf(-b / (2 * a))
            else -> emptyList()
        }
    }
}
