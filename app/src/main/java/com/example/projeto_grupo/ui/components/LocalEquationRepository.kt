package com.example.projeto_grupo.data.local

import android.content.Context
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedEquation(
    val id: String,
    val name: String,
    val expression: String,
    val params: Map<String, Double> = emptyMap(),
    val a: Double = 1.0,
    val b: Double = 1.0,
    val c: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun getUsedParams(): Map<String, Double> {
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        if (usedNames.isEmpty()) return emptyMap()
        return usedNames.associateWith { param ->
            params[param] ?: when (param) {
                "a" -> a
                "b" -> b
                "c" -> c
                else -> 1.0
            }
        }
    }
}

class LocalEquationRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveActiveEquation(expression: String, params: Map<String, Double>) {
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        val filteredParams = usedNames.associateWith { key ->
            params[key] ?: if (key == "c") 0.0 else 1.0
        }
        val json = JSONObject().apply {
            put("expression", expression)
            if (filteredParams.isNotEmpty()) {
                val paramsJson = JSONObject()
                filteredParams.forEach { (k, v) -> paramsJson.put(k, v) }
                put("params", paramsJson)
            }
        }
        prefs.edit().putString(KEY_ACTIVE_EQUATION, json.toString()).apply()
    }

    fun loadActiveEquation(): SavedEquation? {
        val raw = prefs.getString(KEY_ACTIVE_EQUATION, null) ?: return null
        return runCatching {
            val obj = JSONObject(raw)
            val expr = obj.getString("expression")
            if (expr.isBlank()) return null
            val parsedParams = parseParamsFromJson(obj, expr)
            SavedEquation(
                id = "active",
                name = "Ativa",
                expression = expr,
                params = parsedParams,
                a = parsedParams["a"] ?: 1.0,
                b = parsedParams["b"] ?: 1.0,
                c = parsedParams["c"] ?: 0.0
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
                val expr = obj.getString("expression")
                val parsedParams = parseParamsFromJson(obj, expr)
                list += SavedEquation(
                    id = obj.getString("id"),
                    name = obj.optString("name", "Equação ${i + 1}"),
                    expression = expr,
                    params = parsedParams,
                    a = parsedParams["a"] ?: 1.0,
                    b = parsedParams["b"] ?: 1.0,
                    c = parsedParams["c"] ?: 0.0,
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
            list.sortedByDescending { it.updatedAt }
        }.getOrDefault(emptyList())
    }

    fun saveEquation(
        name: String,
        expression: String,
        params: Map<String, Double>
    ): List<SavedEquation> {
        val current = getSavedEquations().toMutableList()
        val cleanName = name.trim().ifEmpty { "Equação #${current.size + 1}" }
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        val filteredParams = usedNames.associateWith { key ->
            params[key] ?: if (key == "c") 0.0 else 1.0
        }

        val existingIndex = current.indexOfFirst {
            it.name.equals(cleanName, ignoreCase = true) ||
                    (it.expression == expression && it.getUsedParams() == filteredParams)
        }

        val entry = SavedEquation(
            id = if (existingIndex >= 0) current[existingIndex].id else UUID.randomUUID().toString(),
            name = cleanName,
            expression = expression,
            params = filteredParams,
            a = filteredParams["a"] ?: 1.0,
            b = filteredParams["b"] ?: 1.0,
            c = filteredParams["c"] ?: 0.0,
            updatedAt = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            current[existingIndex] = entry
        } else {
            current.add(0, entry)
        }

        persistList(current)
        saveActiveEquation(expression, filteredParams)
        return current.sortedByDescending { it.updatedAt }
    }

    fun deleteEquation(id: String): List<SavedEquation> {
        val updated = getSavedEquations().filterNot { it.id == id }
        persistList(updated)
        return updated
    }

    private fun parseParamsFromJson(obj: JSONObject, expression: String): Map<String, Double> {
        val usedNames = MathExpressionEvaluator.extractParameters(expression)
        if (usedNames.isEmpty()) return emptyMap()

        val paramsObj = obj.optJSONObject("params")
        if (paramsObj != null) {
            return usedNames.associateWith { key ->
                if (paramsObj.has(key)) paramsObj.optDouble(key, if (key == "c") 0.0 else 1.0)
                else if (key == "c") 0.0 else 1.0
            }
        }

        // Fallback for legacy entries saved before dynamic params map
        return usedNames.associateWith { key ->
            when (key) {
                "a" -> obj.optDouble("a", 1.0)
                "b" -> obj.optDouble("b", 1.0)
                "c" -> obj.optDouble("c", 0.0)
                else -> 1.0
            }
        }
    }

    private fun persistList(list: List<SavedEquation>) {
        val array = JSONArray()
        list.forEach { item ->
            val usedParams = item.getUsedParams()
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("name", item.name)
                    put("expression", item.expression)
                    if (usedParams.isNotEmpty()) {
                        val paramsJson = JSONObject()
                        usedParams.forEach { (k, v) -> paramsJson.put(k, v) }
                        put("params", paramsJson)
                    }
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
