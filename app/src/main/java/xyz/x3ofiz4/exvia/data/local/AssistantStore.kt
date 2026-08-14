package xyz.x3ofiz4.exvia.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.time.LocalDate
import java.time.YearMonth
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import xyz.x3ofiz4.exvia.domain.service.AssistantEndpointResolver

data class AssistantConfiguration(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

data class AssistantUsage(val dailyTokens: Long, val monthlyTokens: Long)

/**
 * Local-only Assistant configuration. The API key is encrypted with an AES/GCM key
 * held by Android Keystore; only the non-secret URL and model are stored as plain text.
 */
class AssistantStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadConfiguration(): AssistantConfiguration = AssistantConfiguration(
        baseUrl = normalizeBaseUrl(prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL).orEmpty()),
        apiKey = loadApiKey().orEmpty(),
        model = prefs.getString(KEY_MODEL, "").orEmpty(),
    )

    fun saveConfiguration(baseUrl: String, model: String, apiKey: String?) {
        prefs.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .putString(KEY_MODEL, model.trim())
            .apply()
        apiKey?.trim()?.takeIf { it.isNotBlank() }?.let(::saveApiKey)
    }

    fun isConfigured(): Boolean = loadConfiguration().isConfigured

    fun clearApiKey() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    fun usage(): AssistantUsage {
        rollUsagePeriods()
        return AssistantUsage(
            dailyTokens = prefs.getLong(KEY_DAY_TOKENS, 0L),
            monthlyTokens = prefs.getLong(KEY_MONTH_TOKENS, 0L),
        )
    }

    fun recordUsage(tokens: Int) {
        if (tokens <= 0) return
        rollUsagePeriods()
        prefs.edit()
            .putLong(KEY_DAY_TOKENS, prefs.getLong(KEY_DAY_TOKENS, 0L) + tokens)
            .putLong(KEY_MONTH_TOKENS, prefs.getLong(KEY_MONTH_TOKENS, 0L) + tokens)
            .apply()
    }

    private fun rollUsagePeriods() {
        val day = LocalDate.now().toString()
        val month = YearMonth.now().toString()
        val editor = prefs.edit()
        if (prefs.getString(KEY_DAY, null) != day) {
            editor.putString(KEY_DAY, day).putLong(KEY_DAY_TOKENS, 0L)
        }
        if (prefs.getString(KEY_MONTH, null) != month) {
            editor.putString(KEY_MONTH, month).putLong(KEY_MONTH_TOKENS, 0L)
        }
        editor.apply()
    }

    private fun saveApiKey(apiKey: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    private fun loadApiKey(): String? {
        val ciphertext = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch (_: Exception) {
            clearApiKey()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun normalizeBaseUrl(value: String): String {
        val clean = value.trim()
        if (clean.isBlank()) return clean
        return runCatching { AssistantEndpointResolver.normalize(clean) }
            .getOrElse { if (clean.endsWith('/')) clean else "$clean/" }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
        private const val PREFS = "exvia_assistant"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        private const val KEY_CIPHERTEXT = "api_key_ciphertext"
        private const val KEY_IV = "api_key_iv"
        private const val KEY_DAY = "usage_day"
        private const val KEY_DAY_TOKENS = "usage_day_tokens"
        private const val KEY_MONTH = "usage_month"
        private const val KEY_MONTH_TOKENS = "usage_month_tokens"
        private const val KEY_ALIAS = "exvia_assistant_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
