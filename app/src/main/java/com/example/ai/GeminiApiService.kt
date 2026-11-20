package com.example.ai

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApiService {

    @GET("v1beta/models")
    suspend fun listModels(
        @Header("x-goog-api-key") apiKey: String
    ): Response<ModelsListResponse>

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateContentRequest
    ): Response<GenerateContentResponse>
}
