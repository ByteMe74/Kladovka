package ru.kladovka

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest

class ServerApi(private val baseUrl: String = "https://kladovka.dr6ter.ru") {
    
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    data class LoginResponse(val token: String?, val error: String? = null)
    data class RegisterResponse(val token: String?, val emailVerified: Boolean = false, val error: String? = null)
    data class ProfileResponse(val username: String, val email: String, val verified: Boolean)
    
    suspend fun login(username: String, password: String): LoginResponse {
        return withContext(Dispatchers.IO) {
            val body = Json.encodeToString(
                mapOf("username" to username, "password" to password)
            ).toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=login")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    val data = json.decodeFromString<LoginResponse>(json)
                    data
                } else {
                    LoginResponse(error = "Ошибка: ${response.code}")
                }
            } catch (e: Exception) {
                LoginResponse(error = e.message)
            }
        }
    }
    
    suspend fun register(username: String, password: String, email: String): RegisterResponse {
        return withContext(Dispatchers.IO) {
            val body = Json.encodeToString(
                mapOf("username" to username, "password" to password, "email" to email)
            ).toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=register")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    val data = json.decodeFromString<RegisterResponse>(json)
                    data
                } else {
                    RegisterResponse(error = "Ошибка: ${response.code}")
                }
            } catch (e: Exception) {
                RegisterResponse(error = e.message)
            }
        }
    }
    
    suspend fun getProfile(token: String): ProfileResponse? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=getProfile")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    json.decodeFromString<ProfileResponse>(json)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun updateProfile(
        token: String,
        username: String?,
        email: String?,
        currentPassword: String?,
        newPassword: String?
    ): Pair<Boolean, Boolean> { // emailSent, verifiedAfter
        return withContext(Dispatchers.IO) {
            val bodyParams = mutableMapOf(
                "username" to username,
                "email" to email
            )
            if (!currentPassword.isNullOrEmpty()) {
                bodyParams["current_password"] = currentPassword
            }
            if (!newPassword.isNullOrEmpty()) {
                bodyParams["new_password"] = newPassword
            }
            
            val body = Json.encodeToString(bodyParams).toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=updateProfile")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    val data = json.decodeFromString<ProfileResponse>(json)
                    Pair(data.verified, data.verified)
                } else {
                    Pair(false, false)
                }
            } catch (e: Exception) {
                Pair(false, false)
            }
        }
    }
    
    suspend fun push(token: String, data: String): Boolean {
        return withContext(Dispatchers.IO) {
            val body = data.toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=push")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                response.code == 200
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun pull(token: String): String? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=pull")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    response.body?.string()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun share(token: String, username: String): String {
        return withContext(Dispatchers.IO) {
            val body = Json.encodeToString(
                mapOf("username" to username)
            ).toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=share")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    response.body?.string() ?: ""
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }
    }
    
    suspend fun unshare(token: String, username: String): Boolean {
        return withContext(Dispatchers.IO) {
            val body = Json.encodeToString(
                mapOf("username" to username)
            ).toRequestBody("application/json".toRequestBody())
            
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=unshare")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            
            try {
                val response = client.newCall(request).execute()
                response.code == 200
            } catch (e: Exception) {
                false
            }
        }
    }
    
    suspend fun getShares(token: String): Pair<List<String>, List<String>> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=shares")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    val data = json.decodeFromString<SharesResponse>(json)
                    Pair(data.given, data.received)
                } else {
                    Pair(emptyList(), emptyList())
                }
            } catch (e: Exception) {
                Pair(emptyList(), emptyList())
            }
        }
    }
    
    suspend fun latestVersion(): LatestVersion? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=latestApk")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    val json = response.body?.string() ?: ""
                    json.decodeFromString<LatestVersion>()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    suspend fun getLatestData(token: String): String? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$baseUrl/api.php?action=getLatestData")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            
            try {
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    response.body?.string()
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    @Serializable
    data class SharesResponse(val given: List<String>, val received: List<String>)
    
    @Serializable
    data class LatestVersion(
        val versionName: String,
        val versionCode: Int,
        val url: String
    )
}
