package com.example.klwpdemo.project

import android.content.Context
import com.example.klwpdemo.document.DemoWallpaperDocumentFactory
import com.example.klwpdemo.runtime.RuntimeViewport
import java.io.File

/** 项目工程文件的本地仓库。 */
class ProjectRepository(context: Context) {
    /** 一律持有应用级上下文，避免泄漏页面对象。 */
    private val appContext = context.applicationContext

    /** 项目目录按应用私有存储保存。 */
    private val projectsDirectory by lazy {
        File(appContext.filesDir, PROJECTS_DIRECTORY_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /** 列出本地已有项目，后续项目页可以直接复用。 */
    fun listProjects(): List<WallpaperProjectSummary> {
        return projectsDirectory
            .listFiles { file -> file.isFile && file.extension == PROJECT_FILE_EXTENSION }
            .orEmpty()
            .mapNotNull { file ->
                runCatching { loadProjectFromFile(file) }.getOrNull()
            }
            .map { project ->
                WallpaperProjectSummary(
                    id = project.id,
                    name = project.name,
                    updatedAtMillis = project.updatedAtMillis
                )
            }
            .sortedByDescending { it.updatedAtMillis }
    }

    /** 读取当前激活项目。 */
    fun loadActiveProject(): WallpaperProjectDocument? {
        val activeProjectId = appContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_PROJECT_ID, null)

        if (activeProjectId != null) {
            loadProject(activeProjectId)?.let { return it }
        }

        val fallback = listProjects().firstOrNull() ?: return null
        return loadProject(fallback.id)?.also { activateProject(it.id) }
    }

    /** 按 id 读取单个项目。 */
    fun loadProject(projectId: String): WallpaperProjectDocument? {
        val file = projectFile(projectId)
        if (!file.exists()) {
            return null
        }
        return runCatching { loadProjectFromFile(file) }.getOrNull()
    }

    /** 保存项目，并把它设为当前激活项目。 */
    fun saveProject(project: WallpaperProjectDocument): WallpaperProjectDocument {
        val normalizedProject = project.copy(
            schemaVersion = WallpaperProjectDocument.CURRENT_SCHEMA_VERSION
        )
        projectFile(normalizedProject.id).writeText(
            WallpaperProjectJsonSerializer.encode(normalizedProject),
            Charsets.UTF_8
        )
        activateProject(normalizedProject.id)
        return normalizedProject
    }

    /** 仅更新当前激活项目 id。 */
    fun activateProject(projectId: String) {
        appContext
            .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_PROJECT_ID, projectId)
            .apply()
    }

    /** 没有项目时，根据当前视口创建一份默认项目。 */
    fun ensureActiveProject(viewport: RuntimeViewport): WallpaperProjectDocument {
        loadActiveProject()?.let { return it }
        val project = createDefaultProject(viewport)
        return saveProject(project)
    }

    /** 创建一份新的默认项目，并立即保存。 */
    fun createProject(
        name: String,
        viewport: RuntimeViewport
    ): WallpaperProjectDocument {
        val now = System.currentTimeMillis()
        return saveProject(
            WallpaperProjectDocument(
                id = "project-$now",
                name = name,
                createdAtMillis = now,
                updatedAtMillis = now,
                sourceViewportWidth = viewport.width,
                sourceViewportHeight = viewport.height,
                document = DemoWallpaperDocumentFactory.createTemplateDocument(viewport)
            )
        )
    }

    /** 用当前项目内容快速生成一份副本。 */
    fun duplicateProject(
        sourceProject: WallpaperProjectDocument,
        name: String
    ): WallpaperProjectDocument {
        val now = System.currentTimeMillis()
        return saveProject(
            sourceProject.copy(
                id = "project-$now",
                name = name,
                createdAtMillis = now,
                updatedAtMillis = now
            )
        )
    }

    /** 直接在原项目上更新名称，保持项目 id 与内容不变。 */
    fun renameProject(
        sourceProject: WallpaperProjectDocument,
        name: String
    ): WallpaperProjectDocument {
        return saveProject(
            sourceProject.copy(
                name = name,
                updatedAtMillis = System.currentTimeMillis()
            )
        )
    }

    /** 删除指定项目文件，并在必要时清掉激活记录。 */
    fun deleteProject(projectId: String): Boolean {
        val file = projectFile(projectId)
        if (file.exists() && !file.delete()) {
            return false
        }

        val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        if (preferences.getString(KEY_ACTIVE_PROJECT_ID, null) == projectId) {
            preferences.edit().remove(KEY_ACTIVE_PROJECT_ID).apply()
        }
        return true
    }

    /** 生成一份默认项目，作为首次安装后的起点。 */
    fun createDefaultProject(viewport: RuntimeViewport): WallpaperProjectDocument {
        val now = System.currentTimeMillis()
        return WallpaperProjectDocument(
            id = "project-$now",
            name = "默认项目",
            createdAtMillis = now,
            updatedAtMillis = now,
            sourceViewportWidth = viewport.width,
            sourceViewportHeight = viewport.height,
            document = DemoWallpaperDocumentFactory.createTemplateDocument(viewport)
        )
    }

    /** 用最新文档回写项目对象。 */
    fun updateProjectDocument(
        project: WallpaperProjectDocument,
        document: com.example.klwpdemo.document.WallpaperDocument,
        viewport: RuntimeViewport? = null
    ): WallpaperProjectDocument {
        return project.copy(
            updatedAtMillis = System.currentTimeMillis(),
            sourceViewportWidth = viewport?.width ?: project.sourceViewportWidth,
            sourceViewportHeight = viewport?.height ?: project.sourceViewportHeight,
            document = document
        )
    }

    /** 从文件读取并反序列化。 */
    private fun loadProjectFromFile(file: File): WallpaperProjectDocument {
        return WallpaperProjectJsonSerializer.decode(file.readText(Charsets.UTF_8))
    }

    /** 统一收口项目文件路径规则。 */
    private fun projectFile(projectId: String): File {
        return File(projectsDirectory, "$projectId.$PROJECT_FILE_EXTENSION")
    }

    private companion object {
        const val PREFERENCES_NAME = "kuper_project_store"
        const val KEY_ACTIVE_PROJECT_ID = "active_project_id"
        const val PROJECTS_DIRECTORY_NAME = "projects"
        const val PROJECT_FILE_EXTENSION = "json"
    }
}
