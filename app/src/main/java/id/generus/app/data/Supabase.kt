package id.generus.app.data

import id.generus.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@OptIn(ExperimentalSerializationApi::class)
val AppJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

fun createSupabase(): SupabaseClient = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://belum-diatur.supabase.co" },
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "belum-diatur" },
) {
    defaultSerializer = KotlinXSerializer(AppJson)
    install(Auth) {
        // Sesi disimpan di perangkat dan diperbarui otomatis.
        alwaysAutoRefresh = true
        autoLoadFromStorage = true
    }
    install(Postgrest)
    install(Functions)
}

/** Panggil fungsi RPC public.mobile_* dan urai hasil JSON-nya. */
suspend inline fun <reified T> SupabaseClient.rpc(fn: String, params: JsonObject = buildJsonObject { }): T {
    val text = postgrest.rpc(fn, params).data
    return AppJson.decodeFromString<T>(text.ifBlank { "null" })
}

/** RPC tanpa hasil (void). */
suspend fun SupabaseClient.rpcUnit(fn: String, params: JsonObject = buildJsonObject { }) {
    postgrest.rpc(fn, params)
}

inline fun <reified T> JsonElement.decode(): T = AppJson.decodeFromJsonElement(this)
