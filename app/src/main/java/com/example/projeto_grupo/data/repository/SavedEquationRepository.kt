package com.example.projeto_grupo.data.repository

import android.content.Context
import com.example.projeto_grupo.data.model.EquationModel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object SavedEquationRepository {
    private const val PREFS_NAME = "saved_equations_user_prefs"
    private const val KEY_EQUATIONS = "equations_list_user_v2"

    private val json = Json { ignoreUnknownKeys = true }

    fun getSavedEquations(context: Context): List<EquationModel> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_EQUATIONS, null)
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }
        return runCatching {
            json.decodeFromString<List<EquationModel>>(rawJson)
        }.getOrDefault(emptyList())
    }

    fun saveEquation(context: Context, equation: EquationModel): List<EquationModel> {
        val current = getSavedEquations(context).toMutableList()
        val title = if (equation.title.isNotBlank()) equation.title else "f(x, z) = ${equation.expression}"
        val formattedEq = equation.copy(title = title)

        val existingIndex = current.indexOfFirst {
            it.expression == formattedEq.expression && it.a == formattedEq.a && it.b == formattedEq.b && it.c == formattedEq.c
        }
        if (existingIndex >= 0) {
            current[existingIndex] = formattedEq
        } else {
            val newId = "saved_${System.currentTimeMillis()}"
            val newEq = formattedEq.copy(id = newId)
            current.add(0, newEq)
        }
        saveAll(context, current)
        return current
    }

    fun deleteEquation(context: Context, id: String): List<EquationModel> {
        val current = getSavedEquations(context).filterNot { it.id == id }
        saveAll(context, current)
        return current
    }

    private fun saveAll(context: Context, list: List<EquationModel>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = json.encodeToString(list)
        prefs.edit().putString(KEY_EQUATIONS, rawJson).apply()
    }
}
