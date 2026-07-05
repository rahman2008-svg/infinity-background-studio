package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE isInTrash = 0 AND isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE isArchived = 1 AND isInTrash = 0 ORDER BY updatedAt DESC")
    fun getArchivedProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE isInTrash = 1 ORDER BY updatedAt DESC")
    fun getTrashProjects(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: Int): Project?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long

    @Update
    suspend fun updateProject(project: Project)

    @Delete
    suspend fun deleteProject(project: Project)

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun getFolders(): Flow<List<Folder>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("SELECT * FROM version_history WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getVersionHistory(projectId: Int): Flow<List<VersionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: VersionHistory)
}
