package com.example.projeto_grupo.data.local

import android.content.Context
import com.example.projeto_grupo.data.model.UserResponse

class AuthTokenManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun saveBaseUrl(url: String) {
        var formatted = url.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "http://$formatted"
        }
        if (formatted.endsWith("/")) {
            formatted = formatted.dropLast(1)
        }
        prefs.edit().putString(KEY_BASE_URL, formatted).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }

    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }

    fun saveApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getUser(): UserResponse? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val username = prefs.getString(KEY_USER_NAME, "") ?: ""
        val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        val createdAt = prefs.getString(KEY_USER_CREATED_AT, null)
        return UserResponse(id = id, username = username, email = email, createdAt = createdAt)
    }

    fun saveAuthSession(token: String, user: UserResponse) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_USER_NAME, user.username)
            .putString(KEY_USER_EMAIL, user.email)
            .putString(KEY_USER_CREATED_AT, user.createdAt)
            .apply()
    }

    fun clearAuthSession() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_API_KEY)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_EMAIL)
            .remove(KEY_USER_CREATED_AT)
            .apply()
    }

    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrBlank() && getUser() != null
    }

    companion object {
        private const val PREFS_NAME = "auth_session_prefs"
        private const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
        private const val KEY_BASE_URL = "api_base_url"
        private const val KEY_TOKEN = "access_token"
        private const val KEY_API_KEY = "user_api_key"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_CREATED_AT = "user_created_at"
    }
}
