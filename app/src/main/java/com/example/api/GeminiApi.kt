package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Common Data Classes ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class ResponseFormat(
    val text: ResponseFormatText? = null
)

@Serializable
data class ResponseFormatText(
    val mimeType: String,
    val schema: JsonObject? = null
)

@Serializable
data class GenerationConfig(
    val responseFormat: ResponseFormat? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val responseModalities: List<String>? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@Serializable
data class Candidate(
    val content: Content? = null
)

// --- Retrofit Setup ---

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.1-pro-preview:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

// Helper to convert Bitmap to Base64
fun Bitmap.toBase64(): String {
    val outputStream = ByteArrayOutputStream()
    // Compress and resize if necessary, here we just compress quality
    val scaledBitmap = if (width > 1024 || height > 1024) {
        val ratio = 1024f / maxOf(width, height)
        Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    } else this
    scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}

suspend fun analyzeBeachCleanup(bitmap: Bitmap?, userDescription: String): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isEmpty() || apiKey.contains("MY_GEMINI_API_KEY")) {
        return@withContext "Error: API Key is missing. Please configure via Secrets panel."
    }

    val partsList = mutableListOf<Part>()
    if (userDescription.isNotBlank()) {
        partsList.add(Part(text = userDescription))
    }
    if (bitmap != null) {
        partsList.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64())))
    }
    
    if (partsList.isEmpty()) {
       return@withContext "Error: Must provide an image or description."
    }

    val systemInstructionText = """
你是一款專門為「淨灘回報 App」設計的 AI 核心數據分析師。你的任務是接收使用者上傳的淨灘現場照片以及簡短的文字描述，自動分析並提取結構化的淨灘數據。
如果使用者同時提供照片與文字，以照片看到的實況為主，文字為輔（文字通常提供地點或照片拍不到的資訊）。
如果照片模糊或無法辨識任何垃圾，請在 "report_summary" 中說明，並將其他欄位設為 null 或空陣列。
保持客觀，不要猜測過度誇大的數量。
    """.trimIndent()

    val schemaJson = buildJsonObject {
        put("type", "OBJECT")
        putJsonObject("properties") {
            putJsonObject("report_summary") { put("type", "STRING"); put("description", "簡短的總結描述（一句話）") }
            putJsonObject("location_mentioned") { put("type", "STRING"); put("nullable", true); put("description", "文字中是否有提及具體地點，若無則填寫 null") }
            putJsonObject("trash_items") {
                put("type", "ARRAY")
                putJsonObject("items") {
                    put("type", "OBJECT")
                    putJsonObject("properties") {
                        putJsonObject("category") { put("type", "STRING"); put("description", "垃圾類別（選項：寶特瓶/塑膠袋/漁網漁具/玻璃瓶/保麗龍/煙蒂/大型廢棄物/其他）") }
                        putJsonObject("estimated_quantity") { put("type", "STRING"); put("description", "預估數量或件數（如：約10個、大量、無法估計）") }
                        putJsonObject("confidence_level") { put("type", "STRING"); put("description", "AI 辨識信心指數（High/Medium/Low）") }
                    }
                }
            }
            putJsonObject("pollution_level") { put("type", "STRING"); put("description", "污染嚴重程度（選項：低/中/高）") }
            putJsonObject("hazard_tags") {
                put("type", "ARRAY")
                putJsonObject("items") { put("type", "STRING") }
                put("description", "危險物品標籤，如：針頭、碎玻璃、化學藥桶，若無則留空")
            }
            putJsonObject("need_heavy_machinery") { put("type", "BOOLEAN"); put("description", "是否需要大型機具協助清運") }
        }
    }

    val request = GenerateContentRequest(
        contents = listOf(Content(parts = partsList)),
        systemInstruction = Content(parts = listOf(Part(text = systemInstructionText))),
        generationConfig = GenerationConfig(
            responseFormat = ResponseFormat(
                text = ResponseFormatText(
                    mimeType = "application/json",
                    schema = schemaJson
                )
            ),
            temperature = 0.2f
        )
    )

    try {
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No response text"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
