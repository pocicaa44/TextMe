package com.lactose.textme

import android.app.Application
import android.util.Log

class TextMeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TextMeApplication initialized.")

        // Validate Supabase configuration at startup
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY

        if (url.isBlank() || anonKey.isBlank()) {
            Log.e(TAG, "[SupabaseConfig] CRITICAL: SUPABASE_URL or SUPABASE_ANON_KEY is missing or empty in BuildConfig.")
        } else {
            val maskedHost = try {
                val uri = java.net.URI(url)
                uri.host ?: "unknown"
            } catch (_: Exception) {
                "configured"
            }
            Log.i(TAG, "[SupabaseConfig] Configured target host: $maskedHost (Key present: ${anonKey.length} chars)")
        }
    }

    companion object {
        private const val TAG = "TextMeApp"
    }
}
