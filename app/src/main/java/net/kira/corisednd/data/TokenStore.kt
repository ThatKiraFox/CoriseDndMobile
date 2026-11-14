package net.kira.corisednd.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authStore by preferencesDataStore("auth")

class TokenStore(private val context: Context) {
    private val key = stringPreferencesKey("token")

    val token: Flow<String?> = context.authStore.data.map { it[key] }

    suspend fun get(): String? = token.first()
    suspend fun save(value: String) { context.authStore.edit { it[key] = value } }
    suspend fun clear() { context.authStore.edit { it.remove(key) } }
}