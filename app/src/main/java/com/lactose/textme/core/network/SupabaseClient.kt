package com.lactose.textme.core.network

import android.util.Log
import com.lactose.textme.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime

object SupabaseNetworkClient {
    private const val TAG = "SupabaseClient"

    fun isConfigured(): Boolean {
        return BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    }

    val client: SupabaseClient by lazy {
        Log.i(TAG, "[Supabase] Initializing Supabase client...")
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY

        require(url.isNotBlank()) {
            "SUPABASE_URL is empty. Please ensure supabase.url is configured in local.properties."
        }
        require(anonKey.isNotBlank()) {
            "SUPABASE_ANON_KEY is empty. Please ensure supabase.anon.key is configured in local.properties."
        }

        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }.also {
            Log.i(TAG, "[Supabase] Client initialized successfully.")
        }
    }

    val auth: Auth get() = client.auth
    val postgrest: Postgrest get() = client.postgrest
    val realtime: Realtime get() = client.realtime

    suspend fun awaitAuthInitialized() {
        Log.d(TAG, "[Auth] Awaiting auth session restoration from persistent storage...")
        auth.awaitInitialization()
        val user = auth.currentUserOrNull()
        if (user != null) {
            Log.d(TAG, "[Auth] Restored session. Authenticated User ID: ${user.id}")
        } else {
            Log.d(TAG, "[Auth] Storage restoration completed. No existing session found.")
        }
    }
}
