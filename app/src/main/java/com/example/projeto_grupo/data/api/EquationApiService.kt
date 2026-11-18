package com.example.projeto_grupo.data.api

import com.example.projeto_grupo.data.model.EquationModel
import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.data.model.UpdateEquationRequest
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlin.math.sqrt

class EquationApiService(
    private val baseUrl: String = "https://api.example.com/v1"
) {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private var mockEquation = EquationModel(
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

    suspend fun getEquation(id: String): EquationModel {
        return try {
            mockEquation
        } catch (e: Exception) {
            mockEquation
        }
    }

    suspend fun updateEquation(id: String, request: UpdateEquationRequest): EquationModel {
        return try {
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

            mockEquation = mockEquation.copy(
                expression = request.expression,
                a = request.a,
                b = request.b,
                c = request.c,
                pointsOfInterest = pois
            )
            mockEquation
        } catch (e: Exception) {
            mockEquation
        }
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
