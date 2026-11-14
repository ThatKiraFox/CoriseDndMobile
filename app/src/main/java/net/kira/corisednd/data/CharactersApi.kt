package net.kira.corisednd.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

@Serializable
data class NamedRef(val id: Long, val name: String)

// Только поля, нужные списку; остальное из ответа сервера игнорируется.
@Serializable
data class CharacterListItem(
    val id: Long,
    val name: String,
    val race: NamedRef? = null,
    val level: Int = 1,
)

@Serializable
data class Page<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

@Serializable
data class RaceItem(val id: Long, val outName: String)

interface CharactersApi {
    @GET("characters")
    suspend fun list(
        @Query("search") search: String?,
        @Query("raceId") raceId: Long?,
        @Query("sort") sort: String,
        @Query("order") order: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): Page<CharacterListItem>

    @GET("races")
    suspend fun races(): List<RaceItem>

    @GET("characters/{id}")
    suspend fun get(@Path("id") id: Long): CharacterDetail

    @DELETE("characters/{id}")
    suspend fun delete(@Path("id") id: Long)

    @GET("cultures")
    suspend fun cultures(): List<NamedRef>

    @GET("religions")
    suspend fun religions(): List<NamedRef>

    @GET("backstories")
    suspend fun backstories(): List<NamedRef>

    @GET("spells")
    suspend fun spells(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): Page<SpellItem>

    @POST("characters")
    suspend fun create(@Body body: CharacterCreateRequest): CharacterDetail

    @PUT("characters/{id}")
    suspend fun update(@Path("id") id: Long, @Body body: CharacterUpdateRequest): CharacterDetail

    @POST("characters/{id}/spells")
    suspend fun addSpell(@Path("id") id: Long, @Body body: AddSpellRequest)

    @DELETE("characters/{id}/spells/{spellId}")
    suspend fun removeSpell(@Path("id") id: Long, @Path("spellId") spellId: Long)

    @GET("weapons")
    suspend fun weapons(): List<WeaponItem>

    @GET("armours")
    suspend fun armours(): List<ArmourItem>

    @PUT("characters/{id}/items/{itemId}")
    suspend fun putItem(
        @Path("id") id: Long,
        @Path("itemId") itemId: Long,
        @Body body: InventoryItemRequest,
    )

    @DELETE("characters/{id}/items/{itemId}")
    suspend fun removeItem(@Path("id") id: Long, @Path("itemId") itemId: Long)

    @PUT("characters/{id}/equipment")
    suspend fun setEquipment(@Path("id") id: Long, @Body body: EquipmentRequest)

    @PUT("characters/{id}/attributes/{attributeId}")
    suspend fun setAttribute(
        @Path("id") id: Long,
        @Path("attributeId") attributeId: Long,
        @Body body: SetValueRequest,
    )

    @PUT("characters/{id}/skills/{skillId}")
    suspend fun setSkill(
        @Path("id") id: Long,
        @Path("skillId") skillId: Long,
        @Body body: SetValueRequest,
    )
}

@Serializable
data class SpellItem(
    val id: Long,
    val name: String,
    val type: String? = null,
    val cost: Int = 0,
)

@Serializable
data class EquipmentInfo(
    val weapon: NamedRef? = null,
    val armour: NamedRef? = null,
    val shield: NamedRef? = null,
)

@Serializable
data class InventoryEntry(
    val itemId: Long,
    val name: String,
    val category: String? = null,
    val weight: Double = 0.0,
    val value: Double = 0.0,
    val amount: Int = 1,
)

@Serializable
data class CharacterDetail(
    val id: Long,
    val name: String,
    val race: NamedRef? = null,
    val culture: NamedRef? = null,
    val religion: NamedRef? = null,
    val backstory: NamedRef? = null,
    val level: Int = 1,
    val exp: Int = 0,
    val hpCur: Int = 0,
    val mlCur: Int = 0,
    val spells: List<SpellItem> = emptyList(),
    val equipment: EquipmentInfo = EquipmentInfo(),
    val inventory: List<InventoryEntry> = emptyList(),
    val attributes: List<AttributeValue> = emptyList(),
    val skills: List<SkillValue> = emptyList(),
)

@Serializable
data class CharacterCreateRequest(
    val name: String,
    val raceId: Long,
    val cultureId: Long?,
    val religionId: Long?,
    val backstoryId: Long?,
    val level: Int,
    val exp: Int,
    val hpCur: Int,
    val mlCur: Int,
    val spellIds: List<Long>,
)

@Serializable
data class CharacterUpdateRequest(
    val name: String,
    val raceId: Long,
    val cultureId: Long?,
    val religionId: Long?,
    val backstoryId: Long?,
    val level: Int,
    val exp: Int,
    val hpCur: Int,
    val mlCur: Int,
)

@Serializable
data class AddSpellRequest(val spellId: Long)

@Serializable
data class WeaponItem(
    val id: Long,
    val itemId: Long,
    val name: String,
    val weight: Double = 0.0,
    val value: Double = 0.0,
    val damage: String? = null,
    val damageType: String? = null,
    val grip: String? = null,
)

@Serializable
data class ArmourItem(
    val id: Long,
    val itemId: Long,
    val name: String,
    val category: String? = null,
    val weight: Double = 0.0,
    val value: Double = 0.0,
    val damageMitigation: Double? = null,
    val armourType: String? = null,
)

fun ArmourItem.isShield(): Boolean =
    (armourType ?: "").trim().lowercase().let { it == "shield" || it == "щит" }

@Serializable
data class InventoryItemRequest(val amount: Int)

@Serializable
data class EquipmentRequest(
    val weaponId: Long?,
    val armourId: Long?,
    val shieldId: Long?,
)

@Serializable
data class SetValueRequest(val value: Int)

@Serializable
data class AttributeValue(val attributeId: Long, val name: String, val value: Int = 0)

@Serializable
data class SkillValue(
    val skillId: Long,
    val name: String,
    val attributeId: Long,
    val level: Int = 0,
)