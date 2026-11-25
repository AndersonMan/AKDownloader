package com.andersonman.akdownloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val displayName: String,
    val mimeType: String,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: String = Status.ENQUEUED.name,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val contentUri: String? = null,
) {
    enum class Status { ENQUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
}
