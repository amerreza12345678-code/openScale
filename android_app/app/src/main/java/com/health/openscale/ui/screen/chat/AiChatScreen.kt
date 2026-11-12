package com.health.openscale.ui.screen.chat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class ChatLine(
    val fromUser: Boolean,
    val text: String
)

@Composable
fun AiChatScreen() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    val existingKey = remember {
        runCatching { ApiKeyVault.load(context) }.getOrNull().orEmpty()
    }

    var apiKey by remember { mutableStateOf(existingKey) }
    var keySaved by remember { mutableStateOf(existingKey.isNotBlank()) }
    var keyMessage by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var messages by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("دستیار سلامت", style = MaterialTheme.typography.headlineSmall)
        Text(
            "کلید فقط روی همین گوشی ذخیره می‌شود. پیام‌ها برای پاسخ به Gemini فرستاده می‌شوند.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = {
                apiKey = it
                keySaved = false
                keyMessage = ""
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("کلید API جمنای") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    try {
                        val cleanedKey = apiKey.trim()
                        require(cleanedKey.isNotEmpty()) { "کلید را وارد کن." }
                        ApiKeyVault.save(context, cleanedKey)
                        apiKey = cleanedKey
                        keySaved = true
                        keyMessage = "کلید روی گوشی ذخیره شد."
                    } catch (e: Exception) {
                        keyMessage = e.message ?: "ذخیرهٔ کلید انجام نشد."
                    }
                }
            ) {
                Text("ذخیرهٔ کلید")
            }

            Button(
                onClick = {
                    try {
                        ApiKeyVault.delete(context)
                        apiKey = ""
                        keySaved = false
                        keyMessage = "کلید پاک شد."
                    } catch (e: Exception) {
                        keyMessage = e.message ?: "پاک‌کردن کلید انجام نشد."
                    }
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("پاک‌کردن")
            }
        }

        if (keyMessage.isNotBlank()) {
            Text(keyMessage, style = MaterialTheme.typography.bodySmall)
        }

        Text(
            "این دستیار جایگزین پزشک نیست.",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("سؤالت را بنویس و برای دریافت پاسخ به Gemini بفرست.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = if (message.fromUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (message.fromUser) "شما" else "Gemini")
                            Text(message.text)
                        }
                    }
                }
            }
        }

        if (isSending) {
            CircularProgressIndicator()
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                label = { Text("پیام") }
            )

            Button(
                onClick = {
                    val question = input.trim()
                    val key = apiKey.trim()
                    if (question.isNotEmpty() && keySaved && key.isNotEmpty() && !isSending) {
                        val history = messages + ChatLine(true, question)
                        messages = history
                        input = ""
                        isSending = true

                        scope.launch {
                            try {
                                val answer = callGemini(key, history)
                                messages = messages + ChatLine(false, answer)
                            } catch (e: Exception) {
                                messages = messages + ChatLine(
                                    false,
                                    "ارسال به Gemini ناموفق بود: ${e.message ?: "خطای ارتباط"}"
                                )
                            } finally {
                                isSending = false
                            }
                        }
                    }
                },
                enabled = input.isNotBlank() && keySaved && !isSending,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("ارسال")
            }
        }
    }
}

private suspend fun callGemini(
    apiKey: String,
    history: List<ChatLine>
): String = withContext(Dispatchers.IO) {
    val endpoint =
        URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
    val connection = endpoint.openConnection() as HttpURLConnection

    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("x-goog-api-key", apiKey)

        val contents = JSONArray()
        history.takeLast(12).forEach { line ->
            val parts = JSONArray().put(JSONObject().put("text", line.text))
            contents.put(
                JSONObject()
                    .put("role", if (line.fromUser) "user" else "model")
                    .put("parts", parts)
            )
        }

        val body = JSONObject()
            .put(
                "systemInstruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(
                        JSONObject().put(
                            "text",
                            "به فارسی پاسخ بده. پاسخ‌ها را روشن و محتاطانه بنویس. " +
                                "تشخیص پزشکی نده و در صورت نگرانی، مراجعه به پزشک را پیشنهاد کن."
                        )
                    )
                )
            )
            .put("contents", contents)

        connection.outputStream.use {
            it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            val reason = runCatching {
                JSONObject(responseText)
                    .getJSONObject("error")
                    .getString("message")
            }.getOrNull()
            throw IllegalStateException(reason ?: "HTTP $status")
        }

        val parts = JSONObject(responseText)
            .getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")

        buildString {
            for (i in 0 until parts.length()) {
                val text = parts.getJSONObject(i).optString("text")
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append("
")
                    append(text)
                }
            }
        }.ifBlank { "پاسخی از Gemini دریافت نشد." }
    } finally {
        connection.disconnect()
    }
}

private object ApiKeyVault {
    private const val ALIAS = "openscale_gemini_key"
    private const val PREFS = "encrypted_ai_settings"
    private const val IV_KEY = "key_iv"
    private const val DATA_KEY = "key_data"

    fun save(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(IV_KEY, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(DATA_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ivText = prefs.getString(IV_KEY, null) ?: return null
        val dataText = prefs.getString(DATA_KEY, null) ?: return null

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
        )

        val decrypted = cipher.doFinal(Base64.decode(dataText, Base64.NO_WRAP))
        return String(decrypted, StandardCharsets.UTF_8)
    }

    fun delete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        if (keyStore.containsAlias(ALIAS)) {
            keyStore.deleteEntry(ALIAS)
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)

        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
