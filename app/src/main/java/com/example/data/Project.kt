package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String = "All",
    val thumbnailUri: String = "",
    val sampleImageResId: Int? = null,
    val bgType: String = "solid", // solid, gradient, image, transparent
    val bgColorHex: String = "#FFFFFF",
    val bgGradientHexs: String = "#FFD269|#ff5858", // Pipe-separated
    val bgImageCategory: String = "", // nature, studio, etc.
    val bgImageName: String = "", // img name
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val isInTrash: Boolean = false,
    val folderId: Int? = null,
    val layersJson: String = "[]" // Serialized layers
)

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "version_history")
data class VersionHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val projectId: Int,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val layersJson: String
)
