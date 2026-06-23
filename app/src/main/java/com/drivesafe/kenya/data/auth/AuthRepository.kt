package com.drivesafe.kenya.data.auth

sealed class AuthResult {
    data class Success(val email: String, val name: String?) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

class AuthRepository(
    private val api: AuthApi,
    private val session: SessionManager
) {

    suspend fun register(email: String, password: String, name: String?): AuthResult {
        return try {
            val res = api.register(RegisterRequest(email = email, password = password, name = name))
            session.save(res.token, res.user.id.toString(), res.user.email, res.user.name)
            AuthResult.Success(res.user.email, res.user.name)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 409) AuthResult.Error("Email already registered")
            else AuthResult.Error("Registration failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Registration failed")
        }
    }

    suspend fun login(email: String, password: String): AuthResult {
        return try {
            val res = api.login(LoginRequest(email = email, password = password))
            session.save(res.token, res.user.id.toString(), res.user.email, res.user.name)
            AuthResult.Success(res.user.email, res.user.name)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) AuthResult.Error("Invalid email or password")
            else AuthResult.Error("Login failed")
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Login failed")
        }
    }

    suspend fun logout() {
        session.clear()
    }
}
