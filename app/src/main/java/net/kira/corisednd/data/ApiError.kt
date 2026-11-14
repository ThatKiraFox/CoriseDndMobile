package net.kira.corisednd.data

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

@Serializable
data class ApiError(
    val code: String? = null,
    val message: String = "Ошибка",
    val details: JsonElement? = null
)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Превращает любую ошибку сети или сервера в понятный текст. */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: HttpException) {
        val text = e.response()?.errorBody()?.string()
        val msg = try {
            val err = errorJson.decodeFromString<ApiError>(text ?: "")
            val extra = (err.details as? kotlinx.serialization.json.JsonObject)
                ?.values?.joinToString("\n") { it.toString().trim('"') }
            if (extra.isNullOrBlank()) err.message else "${err.message}\n$extra"
        } catch (_: Exception) {
            "Ошибка сервера (${e.code()})"
        }
        Result.failure(Exception(msg))
    } catch (e: IOException) {
        Result.failure(Exception("Нет связи с сервером"))
    }