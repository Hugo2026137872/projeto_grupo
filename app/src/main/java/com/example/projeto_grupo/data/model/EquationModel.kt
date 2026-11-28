package com.example.projeto_grupo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class EquationModel(
    val id: String = "1",
    val title: String = "f(x, z) = a * cos(b * sqrt(x^2 + z^2)) * exp(-0.35 * (x^2 + z^2)) + c",
    val expression: String = "a * cos(b * sqrt(x^2 + z^2)) * exp(-0.35 * (x^2 + z^2)) + c",
    val a: Double = 1.2,
    val b: Double = 3.0,
    val c: Double = 0.0,
    val params: Map<String, Double> = mapOf("a" to 1.2, "b" to 3.0, "c" to 0.0),
    val pointsOfInterest: List<PointOfInterest> = emptyList()
) {
    fun getEffectiveParams(): Map<String, Double> {
        if (params.isNotEmpty()) return params
        return mapOf("a" to a, "b" to b, "c" to c)
    }
}

@Serializable
data class PointOfInterest(
    val id: String,
    val type: PointType,
    val x: Double,
    val y: Double,
    val z: Double = 0.0,
    val label: String
)

@Serializable
enum class PointType {
    MAXIMA, MINIMA, ZERO, INFLECTION
}

@Serializable
data class UpdateEquationRequest(
    val expression: String,
    val a: Double,
    val b: Double,
    val c: Double,
    val params: Map<String, Double> = emptyMap()
)