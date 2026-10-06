package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "knowledge_items")
data class KnowledgeItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: String,
    val content: String,
    val keywords: String = "",
    val sourceType: String = "MANUAL", // MANUAL, FILE_IMPORT, SYSTEM_DEFAULT
    val fileName: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
