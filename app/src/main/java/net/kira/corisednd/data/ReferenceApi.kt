package net.kira.corisednd.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Универсальное чтение справочников: ответ разбирается как JSON и показывается по описанию раздела. */
interface ReferenceApi {
    @GET("{path}")
    suspend fun list(
        @Path("path") path: String,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null,
    ): JsonElement

    @GET("{path}/{id}")
    suspend fun item(@Path("path") path: String, @Path("id") id: Long): JsonObject

    @POST("{path}")
    suspend fun create(@Path("path") path: String, @Body body: JsonObject): JsonObject

    @PUT("{path}/{id}")
    suspend fun update(@Path("path") path: String, @Path("id") id: Long, @Body body: JsonObject): JsonObject

    @DELETE("{path}/{id}")
    suspend fun delete(@Path("path") path: String, @Path("id") id: Long)
}