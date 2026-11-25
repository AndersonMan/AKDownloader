package com.andersonman.akdownloader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: Long): DownloadEntity?

    @Query("UPDATE downloads SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun updateError(id: Long, status: String, error: String?)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :total WHERE id = :id")
    suspend fun updateProgress(id: Long, bytes: Long, total: Long)

    @Query(
        "UPDATE downloads SET status = 'COMPLETED', downloadedBytes = :total, totalBytes = :total, " +
            "contentUri = :uri, completedAt = :completedAt WHERE id = :id"
    )
    suspend fun markCompleted(id: Long, uri: String, total: Long, completedAt: Long)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
