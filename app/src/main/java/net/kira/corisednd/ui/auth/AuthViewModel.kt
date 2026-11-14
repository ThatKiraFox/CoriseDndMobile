package net.kira.corisednd.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import net.kira.corisednd.data.*

class AuthViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.retrofit.create(AuthApi::class.java)

    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    fun clearError() { error = null }

    fun login(email: String, password: String) = run {
        api.login(LoginRequest(email.trim(), password))
    }

    fun register(email: String, username: String, password: String, confirm: String) = run {
        api.register(RegisterRequest(email.trim(), username.trim(), password, confirm))
    }

    private fun run(call: suspend () -> AuthResponse) {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            safeCall(call)
                .onSuccess { container.tokenStore.save(it.token) } // навигация переключится сама
                .onFailure { error = it.message }
            loading = false
        }
    }
}