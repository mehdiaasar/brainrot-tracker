package com.example.brainrottracker.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brainrot_app_prefs")

object AppPreferences {

    private val IS_SIGNED_IN   = booleanPreferencesKey("is_signed_in")
    private val USER_EMAIL     = stringPreferencesKey("user_email")
    private val USER_NAME      = stringPreferencesKey("user_name")
    private val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")

    data class SignedInUser(val email: String, val name: String)

    fun isSignedInFlow(context: Context): Flow<Boolean> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { it[IS_SIGNED_IN] ?: false }

    fun userFlow(context: Context): Flow<SignedInUser?> =
        context.dataStore.data
            .catch { emit(emptyPreferences()) }
            .map { prefs ->
                if (prefs[IS_SIGNED_IN] == true) {
                    SignedInUser(prefs[USER_EMAIL] ?: "", prefs[USER_NAME] ?: "")
                } else null
            }

    suspend fun setSignedIn(
        context: Context,
        email: String,
        name: String,
        photoUrl: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[IS_SIGNED_IN]   = true
            prefs[USER_EMAIL]     = email
            prefs[USER_NAME]      = name
            prefs[USER_PHOTO_URL] = photoUrl ?: ""
        }
    }

    suspend fun signOut(context: Context) {
        context.dataStore.edit { it.clear() }
    }
}
