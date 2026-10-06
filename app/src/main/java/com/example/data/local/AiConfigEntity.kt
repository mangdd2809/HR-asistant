package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_configurations")
data class AiConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val apiKey: String = "",
    val modelName: String = "gemini-3.5-flash",
    val businessName: String = "Mitra Layanan Pelanggan",
    val serviceTone: String = "Ramah & Profesional",
    val customSystemPrompt: String = "",
    val useOfflineFallback: Boolean = true
)
