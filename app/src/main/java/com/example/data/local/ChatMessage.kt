package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val sender: String, // "USER", "BOT", "SYSTEM"
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val referencedKnowledgeId: Long? = null,
    val referencedKnowledgeTitle: String? = null,
    val isHelpful: Boolean? = null,
    val responseTimeMs: Long = 0L
)
