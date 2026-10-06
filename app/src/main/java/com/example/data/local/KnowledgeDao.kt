package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_items ORDER BY updatedAt DESC")
    fun getAllKnowledgeFlow(): Flow<List<KnowledgeItem>>

    @Query("SELECT * FROM knowledge_items WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getActiveKnowledgeFlow(): Flow<List<KnowledgeItem>>

    @Query("SELECT * FROM knowledge_items WHERE isActive = 1")
    suspend fun getActiveKnowledge(): List<KnowledgeItem>

    @Query("SELECT * FROM knowledge_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): KnowledgeItem?

    @Query("""
        SELECT * FROM knowledge_items 
        WHERE title LIKE '%' || :query || '%' 
           OR content LIKE '%' || :query || '%' 
           OR keywords LIKE '%' || :query || '%'
           OR category LIKE '%' || :query || '%'
        ORDER BY updatedAt DESC
    """)
    fun searchKnowledge(query: String): Flow<List<KnowledgeItem>>

    @Query("SELECT COUNT(*) FROM knowledge_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: KnowledgeItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<KnowledgeItem>)

    @Update
    suspend fun update(item: KnowledgeItem)

    @Delete
    suspend fun delete(item: KnowledgeItem)

    @Query("DELETE FROM knowledge_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}
