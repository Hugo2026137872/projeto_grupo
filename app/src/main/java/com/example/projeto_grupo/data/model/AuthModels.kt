package com.example.projeto_grupo.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val username: String? = null,
    val email: String? = null,
    val identifier: String? = null,
    val password: String
)

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    @SerialName("createdAt")
    val createdAt: String? = null
)

@Serializable
data class AuthResponse(
    @SerialName("access_token")
    val accessToken: String,
    @SerialName("token_type")
    val tokenType: String = "bearer",
    val user: UserResponse
)

@Serializable
data class CreateEquationRequest(
    val title: String? = null,
    val expression: String,
    val a: Double = 1.0,
    val b: Double = 1.0,
    val c: Double = 0.0
)

@Serializable
data class ApiEquationResponse(
    val id: String,
    val title: String,
    val expression: String,
    val a: Double = 1.0,
    val b: Double = 1.0,
    val c: Double = 0.0,
    @SerialName("pointsOfInterest")
    val pointsOfInterest: List<PointOfInterest> = emptyList(),
    @SerialName("ownerId")
    val ownerId: String? = null,
    @SerialName("updatedAt")
    val updatedAt: String? = null
)
