package com.blockbuster.core.network.supabase

import com.blockbuster.core.network.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Creates the singleton client from public build configuration. Placeholder
 * values are used when configuration is missing so the app can still start and
 * fall back to the cached license.
 */
object SupabaseClientProvider {

    fun create(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.ifBlank { PLACEHOLDER_URL }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { PLACEHOLDER_KEY }

        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            // Auth is available as a dependency for future account linking, but
            // this iteration uses a device-scoped identity and only Postgrest.
            install(Postgrest)
        }
    }

    private const val PLACEHOLDER_URL = "https://placeholder.supabase.co"
    private const val PLACEHOLDER_KEY = "placeholder-anon-key"
}
