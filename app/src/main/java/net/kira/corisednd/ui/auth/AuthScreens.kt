package net.kira.corisednd.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val USERNAME_RE = Regex("^[A-Za-z0-9_.-]{3,50}$")

@Composable
private fun Field(
    value: String, onChange: (String) -> Unit, label: String,
    error: String? = null, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { if (error != null) Text(error) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthLayout(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun ErrorAndButton(vm: AuthViewModel, text: String, enabled: Boolean, onClick: () -> Unit) {
    vm.error?.let {
        Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
    }
    Button(onClick = onClick, enabled = enabled && !vm.loading, modifier = Modifier.fillMaxWidth()) {
        if (vm.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(text)
    }
}

@Composable
fun LoginScreen(vm: AuthViewModel, onRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthLayout("CoriseDND") {
        Field(email, { email = it; vm.clearError() }, "Email", keyboard = KeyboardType.Email)
        Field(password, { password = it; vm.clearError() }, "Пароль", password = true)
        ErrorAndButton(vm, "Войти", email.isNotBlank() && password.isNotEmpty()) {
            vm.login(email, password)
        }
        TextButton(onClick = onRegister) { Text("Нет аккаунта? Регистрация") }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf(false) }

    val emailErr = if (!email.trim().matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) "Неверный формат email" else null
    val userErr = if (!USERNAME_RE.matches(username.trim())) "3–50 символов: латиница, цифры, _ . -" else null
    val passErr = if (password.length < 8) "Минимум 8 символов" else null
    val confErr = if (confirm != password) "Пароли не совпадают" else null

    AuthLayout("Регистрация") {
        Field(email, { email = it; vm.clearError() }, "Email", if (submitted) emailErr else null, keyboard = KeyboardType.Email)
        Field(username, { username = it; vm.clearError() }, "Имя пользователя", if (submitted) userErr else null)
        Field(password, { password = it; vm.clearError() }, "Пароль", if (submitted) passErr else null, password = true)
        Field(confirm, { confirm = it; vm.clearError() }, "Повторите пароль", if (submitted) confErr else null, password = true)
        ErrorAndButton(vm, "Зарегистрироваться", true) {
            submitted = true
            if (listOf(emailErr, userErr, passErr, confErr).all { it == null }) {
                vm.register(email, username, password, confirm)
            }
        }
        TextButton(onClick = onBack) { Text("Уже есть аккаунт? Войти") }
    }
}