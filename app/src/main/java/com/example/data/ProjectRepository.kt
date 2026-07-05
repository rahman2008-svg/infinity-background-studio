package com.example.data

import kotlinx.coroutines.flow.Flow

class ProjectRepository(private val projectDao: ProjectDao) {
    val activeProjects: Flow<List<Project>> = projectDao.getActiveProjects()
    val archivedProjects: Flow<List<Project>> = projectDao.getArchivedProjects()
    val trashProjects: Flow<List<Project>> = projectDao.getTrashProjects()
    val folders: Flow<List<Folder>> = projectDao.getFolders()

    suspend fun getProjectById(id: Int): Project? {
        return projectDao.getProjectById(id)
    }

    suspend fun insertProject(project: Project): Long {
        return projectDao.insertProject(project)
    }

    suspend fun updateProject(project: Project) {
        projectDao.updateProject(project)
    }

    suspend fun deleteProject(project: Project) {
        projectDao.deleteProject(project)
    }

    suspend fun insertFolder(folder: Folder): Long {
        return projectDao.insertFolder(folder)
    }

    suspend fun deleteFolder(folder: Folder) {
        projectDao.deleteFolder(folder)
    }

    fun getVersionHistory(projectId: Int): Flow<List<VersionHistory>> {
        return projectDao.getVersionHistory(projectId)
    }

    suspend fun insertVersion(version: VersionHistory) {
        projectDao.insertVersion(version)
    }
}
