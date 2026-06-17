package com.example.brainrottracker.ui.screens.signin

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.prefs.AppPreferences
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class SignInState {
    object Idle : SignInState()
    object Loading : SignInState()
    data class Success(val name: String, val email: String) : SignInState()
    data class Error(val message: String) : SignInState()
    object Cancelled : SignInState()
}

class GoogleSignInViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state

    fun signIn(activityContext: Context, webClientId: String) {
        _state.value = SignInState.Loading
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(activityContext)

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    context = activityContext,
                    request = request
                )

                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    // Exchange the Google ID token for a Firebase user so Firestore rules
                    // can scope data to the account. Skipped when Firebase isn't configured
                    // (no google-services.json) — sign-in then remains local-only.
                    if (FirebaseApp.getApps(activityContext).isNotEmpty()) {
                        val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                        FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                    }
                    AppPreferences.setSignedIn(
                        context = activityContext,
                        email = googleCred.id,
                        name = googleCred.displayName ?: "",
                        photoUrl = googleCred.profilePictureUri?.toString()
                    )
                    _state.value = SignInState.Success(
                        name = googleCred.displayName ?: "",
                        email = googleCred.id
                    )
                } else {
                    _state.value = SignInState.Error("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                _state.value = SignInState.Cancelled
            } catch (e: GetCredentialException) {
                _state.value = SignInState.Error(e.message ?: "Sign-in failed. Try again.")
            } catch (e: Exception) {
                // e.g. the Firebase credential exchange failed
                _state.value = SignInState.Error(e.message ?: "Sign-in failed. Try again.")
            }
        }
    }

    fun resetError() {
        if (_state.value is SignInState.Error || _state.value is SignInState.Cancelled) {
            _state.value = SignInState.Idle
        }
    }
}
