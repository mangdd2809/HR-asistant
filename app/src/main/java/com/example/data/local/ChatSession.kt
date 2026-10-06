package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val customerName: String = "Pelanggan",
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val status: String = "OPEN" // "OPEN", "RESOLVED"
)
