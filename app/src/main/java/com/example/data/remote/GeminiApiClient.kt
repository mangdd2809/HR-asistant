package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiApiClient {
    private const val TAG = "GeminiApiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Send prompt with conversation history and database context to Gemini API.
     */
    suspend fun generateAnswer(
        apiKey: String,
        modelName: String = "gemini-3.5-flash",
        systemPrompt: String,
        conversationHistory: List<Pair<String, String>>, // sender (user/model) to message
        userQuestion: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("API Key belum dikonfigurasi"))
            }

            val sanitizedModel = when {
                modelName.isBlank() -> "gemini-3.5-flash"
                modelName.contains("gemini-1.5") || modelName.contains("gemini-2.0") -> "gemini-3.5-flash"
                else -> modelName
            }

            val url = "$BASE_URL/$sanitizedModel:generateContent?key=$apiKey"

            val rootJson = JSONObject()

            // System instruction
            if (systemPrompt.isNotBlank()) {
                val sysPart = JSONObject().put("text", systemPrompt)
                val sysParts = JSONArray().put(sysPart)
                rootJson.put("systemInstruction", JSONObject().put("parts", sysParts))
            }

            // Contents array
            val contentsArray = JSONArray()

            // Add previous recent turns (up to last 6 turns to keep context fast and focused)
            val recentHistory = conversationHistory.takeLast(6)
            for ((role, text) in recentHistory) {
                val apiRole = if (role.equals("USER", ignoreCase = true)) "user" else "model"
                val partObj = JSONObject().put("text", text)
                val parts = JSONArray().put(partObj)
                val contentObj = JSONObject().put("role", apiRole).put("parts", parts)
                contentsArray.put(contentObj)
            }

            // Current user turn
            val currentPartObj = JSONObject().put("text", userQuestion)
            val currentParts = JSONArray().put(currentPartObj)
            contentsArray.put(JSONObject().put("role", "user").put("parts", currentParts))

            rootJson.put("contents", contentsArray)

            // Generation config
            val genConfig = JSONObject()
                .put("temperature", 0.4)
                .put("topP", 0.9)
            rootJson.put("generationConfig", genConfig)

            val requestBody = rootJson.toString().toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "Gemini API Error: ${response.code} - $responseBody")
                val errorMsg = try {
                    val errJson = JSONObject(responseBody)
                    val errObj = errJson.optJSONObject("error")
                    errObj?.optString("message") ?: "Error ${response.code}"
                } catch (e: Exception) {
                    "Koneksi gagal dengan status kode ${response.code}"
                }
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                return@withContext Result.failure(Exception("Tidak ada respons yang diterima dari model."))
            }

            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                }
            }

            val finalReply = textBuilder.toString().trim()
            if (finalReply.isEmpty()) {
                return@withContext Result.failure(Exception("Format respons teks kosong."))
            }

            Result.success(finalReply)
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling Gemini API", e)
            Result.failure(e)
        }
    }

    /**
     * Test API connection with a simple ping query.
     */
    suspend fun testConnection(apiKey: String, modelName: String = "gemini-3.5-flash"): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Kunci API tidak boleh kosong"))
                }
                val result = generateAnswer(
                    apiKey = apiKey,
                    modelName = modelName,
                    systemPrompt = "Anda adalah tester koneksi.",
                    conversationHistory = emptyList(),
                    userQuestion = "Halo, konfirmasi satu kata jika koneksi berhasil: ONLINE"
                )
                result
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
