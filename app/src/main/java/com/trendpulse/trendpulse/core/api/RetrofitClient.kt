package com.trendpulse.trendpulse.core.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit


/**
 * Provides a singleton instance of the Retrofit client used for network communication.
 *
 * This object is responsible for configuring and initializing the HTTP client,
 * including timeout settings and JSON serialization via Gson.
 *
 * It ensures a single, reusable instance of [ApiService] is available throughout
 * the application lifecycle, promoting efficient resource usage and consistency.
 */
object RetrofitClient {
    private const val BASE_URL = "https://tikgrub.onrender.com/"

    /**
     * Configured OkHttp client with customized timeout settings to support
     * long-running network operations such as comment scraping and analysis.
     */
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // ✅ 2 min to match ViewModel maxAttempts
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Lazily initialized Retrofit service instance for API interactions.
     */
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)              // ✅ Attach the custom client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}