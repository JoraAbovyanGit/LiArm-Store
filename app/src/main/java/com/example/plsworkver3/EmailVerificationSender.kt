package com.example.plsworkver3

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Simple helper that calls a backend endpoint which sends email via SendGrid.
 *
 * IMPORTANT:
 * - Do NOT put your SendGrid API key in the app.
 * - Instead, host a secure backend (for example a Firebase HTTPS function)
 *   that accepts (email, code) and sends the email using SendGrid.
 * - Set EMAIL_VERIFICATION_ENDPOINT in strings.xml to your backend URL.
 */
object EmailVerificationSender {

    private const val TAG = "EmailVerificationSender"
    private val client = OkHttpClient()

    fun sendVerificationEmail(context: Context, email: String, code: String, name: String? = null) {
        val endpoint = context.getString(R.string.email_verification_endpoint)
        if (endpoint.isBlank()) {
            Log.e(TAG, "Email verification endpoint is not configured")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("email", email)
                    put("code", code)
                    if (name != null) {
                        put("name", name)
                    }
                }

                val body = json.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                val request = Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to send verification email: ${response.code} ${response.message}")
                } else {
                    Log.d(TAG, "Verification email request sent successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending verification email via backend", e)
            }
        }
    }
}


