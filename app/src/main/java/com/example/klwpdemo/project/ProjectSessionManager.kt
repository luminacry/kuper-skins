package com.example.klwpdemo.project

import android.content.Context
import com.example.klwpdemo.document.WallpaperDocument
import com.example.klwpdemo.runtime.RuntimeViewport

/** 进程内共享的活动项目会话。 */
object ProjectSessionManager {
    @Volatile
    private var activeProject: WallpaperProjectDocument? = null
    private val activeProjectListeners = linkedSetOf<ActiveProjectListener>()

    /** 项目切换或内容更新后，给壁纸服务等模块发送同步信号。 */
    fun interface ActiveProjectListener {
        fun onActiveProjectChanged(project: WallpaperProjectDocument?)
    }

    /** 读取当前活动项目，没有就按视口创建默认项目。 */
    @Synchronized
    fun loadOrCreateActiveProject(
        context: Context,
        viewport: RuntimeViewport
    ): WallpaperProjectDocument {
        activeProject?.let { return it }
        val repository = ProjectRepository(context)
        return setActiveProject(repository.ensureActiveProject(viewport))
    }

    /** 从会话内存或本地持久层中恢复当前活动项目。 */
    @Synchronized
    fun resolveActiveProject(
        context: Context,
        viewport: RuntimeViewport? = null
    ): WallpaperProjectDocument? {
        activeProject?.let { return it }
        val repository = ProjectRepository(context)
        val project = if (viewport != null) {
            repository.ensureActiveProject(viewport)
        } else {
            repository.loadActiveProject()
        }
        return project?.let(::setActiveProject)
    }

    /** 只读取内存中的活动项目快照。 */
    @Synchronized
    fun snapshotActiveProject(): WallpaperProjectDocument? {
        return activeProject
    }

    /** 手动切换当前活动项目。 */
    @Synchronized
    fun activateProject(project: WallpaperProjectDocument) {
        setActiveProject(project)
    }

    /** 注册活动项目监听器。 */
    @Synchronized
    fun registerActiveProjectListener(listener: ActiveProjectListener) {
        activeProjectListeners += listener
    }

    /** 解除活动项目监听器。 */
    @Synchronized
    fun unregisterActiveProjectListener(listener: ActiveProjectListener) {
        activeProjectListeners -= listener
    }

    /** 列出本地项目摘要，供项目菜单和项目列表页复用。 */
    @Synchronized
    fun listProjects(context: Context): List<WallpaperProjectSummary> {
        return ProjectRepository(context).listProjects()
    }

    /** 切换当前活动项目，并同步更新激活 id。 */
    @Synchronized
    fun switchActiveProject(
        context: Context,
        projectId: String
    ): WallpaperProjectDocument? {
        val repository = ProjectRepository(context)
        val project = repository.loadProject(projectId) ?: return null
        repository.activateProject(project.id)
        return setActiveProject(project)
    }

    /** 创建新项目后直接切为当前活动项目。 */
    @Synchronized
    fun createAndActivateProject(
        context: Context,
        viewport: RuntimeViewport,
        name: String
    ): WallpaperProjectDocument {
        val repository = ProjectRepository(context)
        return setActiveProject(repository.createProject(name, viewport))
    }

    /** 基于当前项目创建一份副本，并切换过去。 */
    @Synchronized
    fun duplicateActiveProject(
        context: Context,
        name: String
    ): WallpaperProjectDocument? {
        val current = activeProject ?: return null
        val repository = ProjectRepository(context)
        return setActiveProject(repository.duplicateProject(current, name))
    }

    /** 重命名当前活动项目。 */
    @Synchronized
    fun renameActiveProject(
        context: Context,
        name: String
    ): WallpaperProjectDocument? {
        val current = activeProject ?: return null
        val repository = ProjectRepository(context)
        return setActiveProject(repository.renameProject(current, name))
    }

    /** 删除当前活动项目，如有必要自动补一个默认项目兜底。 */
    @Synchronized
    fun deleteActiveProject(
        context: Context,
        viewport: RuntimeViewport
    ): WallpaperProjectDocument? {
        val current = activeProject ?: return null
        val repository = ProjectRepository(context)
        if (!repository.deleteProject(current.id)) {
            return null
        }

        val fallbackProject = repository.loadActiveProject()
            ?: repository.saveProject(repository.createDefaultProject(viewport))
        return setActiveProject(fallbackProject)
    }

    /** 用最新文档更新活动项目，但先不立即写盘。 */
    @Synchronized
    fun updateActiveDocument(
        context: Context,
        document: WallpaperDocument,
        viewport: RuntimeViewport? = null
    ): Boolean {
        val current = activeProject ?: return false
        val sameViewport =
            viewport == null ||
                (current.sourceViewportWidth == viewport.width &&
                    current.sourceViewportHeight == viewport.height)
        if (current.document == document && sameViewport) {
            return false
        }

        val repository = ProjectRepository(context)
        setActiveProject(
            repository.updateProjectDocument(
                project = current,
                document = document,
                viewport = viewport
            )
        )
        return true
    }

    /** 把当前活动项目刷回本地文件。 */
    @Synchronized
    fun persistActiveProject(context: Context): WallpaperProjectDocument? {
        val current = activeProject ?: return null
        return setActiveProject(ProjectRepository(context).saveProject(current))
    }

    /** 统一维护内存中的活动项目，并把变化广播给监听方。 */
    @Synchronized
    private fun setActiveProject(project: WallpaperProjectDocument): WallpaperProjectDocument {
        val changed = activeProject != project
        activeProject = project
        if (changed) {
            activeProjectListeners.toList().forEach { listener ->
                listener.onActiveProjectChanged(project)
            }
        }
        return project
    }
}
