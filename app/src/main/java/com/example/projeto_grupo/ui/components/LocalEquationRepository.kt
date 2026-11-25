package com.example.projeto_grupo.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedEquation(
    val id: String,
    val name: String,
    val expression: String,
    val a: Double,
    val b: Double,
    val c: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

class LocalEquationRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveActiveEquation(expression: String, a: Double, b: Double, c: Double) {
        val json = JSONObject().apply {
            put("expression", expression)
            put("a", a)
            put("b", b)
            put("c", c)
        }
        prefs.edit().putString(KEY_ACTIVE_EQUATION, json.toString()).apply()
    }

    fun loadActiveEquation(): SavedEquation? {
        val raw = prefs.getString(KEY_ACTIVE_EQUATION, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val expr = obj.getString("expression")
            if (expr.isBlank()) return null
            SavedEquation(
                id = "active",
                name = "Ativa",
                expression = expr,
                a = obj.optDouble("a", 1.0),
                b = obj.optDouble("b", 1.0),
                c = obj.optDouble("c", 0.0)
            )
        }.getOrNull()
    }

    fun getSavedEquations(): List<SavedEquation> {
        val raw = prefs.getString(KEY_SAVED_LIST, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = ArrayList<SavedEquation>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list += SavedEquation(
                    id = obj.getString("id"),
                    name = obj.optString("name", "Equação ${i + 1}"),
                    expression = obj.getString("expression"),
                    a = obj.optDouble("a", 1.0),
                    b = obj.optDouble("b", 1.0),
                    c = obj.optDouble("c", 0.0),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            list.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun saveEquation(
        name: String,
        expression: String,
        a: Double,
        b: Double,
        c: Double
    ): List<SavedEquation> {
        val current = getSavedEquations().toMutableList()
        val cleanName = name.trim().ifEmpty { "Equação #${current.size + 1}" }

        // Update existing entry if an identical name or identical formula+params already exists
        val existingIndex = current.indexOfFirst {
            it.name.equals(cleanName, ignoreCase = true) ||
                    (it.expression == expression && it.a == a && it.b == b && it.c == c)
        }

        val entry = SavedEquation(
            id = if (existingIndex >= 0) current[existingIndex].id else UUID.randomUUID().toString(),
            name = cleanName,
            expression = expression,
            a = a,
            b = b,
            c = c,
            updatedAt = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            current[existingIndex] = entry
        } else {
            current.add(0, entry)
        }

        persistList(current)
        saveActiveEquation(expression, a, b, c)
        return current.sortedByDescending { it.updatedAt }
    }

    fun deleteEquation(id: String): List<SavedEquation> {
        val updated = getSavedEquations().filterNot { it.id == id }
        persistList(updated)
        return updated
    }

    private fun persistList(list: List<SavedEquation>) {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("expression", item.expression)
                    put("a", item.a)
                    put("b", item.b)
                    put("c", item.c)
                    put("updatedAt", item.updatedAt)
                }
            )
        }
        prefs.edit().putString(KEY_SAVED_LIST, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "device_equations_storage"
        private const val KEY_ACTIVE_EQUATION = "active_equation_json"
        private const val KEY_SAVED_LIST = "saved_equations_list_json"
    }
}