package com.example.myapplication.data

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from

object CurrencyProvider {
    var currencyCode: String = ""
        private set

    suspend fun load() {
        try {
            val userId = SupabaseClient.client.auth.currentUserOrNull()?.id ?: return
            val profiles: List<Profile> = SupabaseClient.client.from("profiles")
                .select { filter { eq("user_id", userId) } }
                .decodeList()
            currencyCode = profiles.firstOrNull()?.currency ?: ""
        } catch (_: Exception) { }
    }

    fun formatPrice(value: Double): String {
        val formatted = "%.2f".format(value)
        return if (currencyCode.isNotBlank()) "$currencyCode $formatted" else formatted
    }
}
