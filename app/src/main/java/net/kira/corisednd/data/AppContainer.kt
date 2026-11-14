package net.kira.corisednd.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

// Эмулятор видит компьютер по 10.0.2.2. Для реального телефона поменять на IP компьютера.
const val BASE_URL = "http://10.0.2.2:8080/api/v1/"

class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val token = runBlocking { tokenStore.get() }
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            val response = chain.proceed(request)
            // токен истёк или недействителен: стираем, приложение само вернёт на экран входа
            if (response.code == 401 && token != null) runBlocking { tokenStore.clear() }
            response
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}