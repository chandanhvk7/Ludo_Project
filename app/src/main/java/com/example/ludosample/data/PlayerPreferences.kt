package com.example.ludosample.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "player_prefs")

private val KEY_PLAYER_ID = stringPreferencesKey("player_id")
private val KEY_PLAYER_NAME = stringPreferencesKey("player_name")

/**
 * Persists a stable player UUID and name across app sessions.
 */
class PlayerPreferences(private val context: Context) {

    suspend fun getPlayerId(): String {
        val existing = context.dataStore.data.map { it[KEY_PLAYER_ID] }.first()
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[KEY_PLAYER_ID] = newId }
        return newId
    }

    suspend fun getPlayerName(): String {
        return context.dataStore.data.map { it[KEY_PLAYER_NAME] }.first() ?: ""
    }

    suspend fun setPlayerName(name: String) {
        context.dataStore.edit { it[KEY_PLAYER_NAME] = name }
    }
}
