package com.example.myapplication.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://yyedzpamdmabmxgirnme.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl5ZWR6cGFtZG1hYm14Z2lybm1lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjE4MDI5NDUsImV4cCI6MjA3NzM3ODk0NX0.lZKrNNQ2aHWAKDh4PeHnFzzjsOuNwYuQjuAo7-0MKKQ"
    ) {
        install(Auth)
        install(Postgrest)
    }
}
