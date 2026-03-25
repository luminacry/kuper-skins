package com.example.klwpdemo

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.example.klwpdemo.project.ProjectSessionManager
import com.example.klwpdemo.project.WallpaperProjectDocument
import com.example.klwpdemo.project.WallpaperProjectSummary
import kotlin.math.max

/** 项目首页：先选项目，再进入编辑器，保证“项目包含图形”的主逻辑不再混乱。 */
class ProjectHomeActivity : Activity() {
    private val rowInflater by lazy { LayoutInflater.from(this) }

    private lateinit var homeRootContent: View
    private lateinit var projectCountText: TextView
    private lateinit var currentProjectNameText: TextView
    private lateinit var currentProjectMetaText: TextView
    private lateinit var currentProjectHintText: TextView
    private lateinit var createProjectTopButton: TextView
    private lateinit var openProjectButton: TextView
    private lateinit var wallpaperActionButton: TextView
    private lateinit var recentTitleText: TextView
    private lateinit var recentHintText: TextView
    private lateinit var recentContainer: LinearLayout
    private lateinit var emptyStateView: View
    private lateinit var emptyStateTitleText: TextView
    private lateinit var emptyStateMessageText: TextView
    private var baseTopPadding = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_home)
        configureWindow()

        homeRootContent = findViewById(R.id.project_home_root_content)
        projectCountText = findViewById(R.id.text_project_home_count)
        currentProjectNameText = findViewById(R.id.text_project_home_current_name)
        currentProjectMetaText = findViewById(R.id.text_project_home_current_meta)
        currentProjectHintText = findViewById(R.id.text_project_home_current_hint)
        createProjectTopButton = findViewById(R.id.button_project_home_create_top)
        openProjectButton = findViewById(R.id.button_project_home_open)
        wallpaperActionButton = findViewById(R.id.button_project_home_apply_wallpaper)
        recentTitleText = findViewById(R.id.text_project_home_recent_title)
        recentHintText = findViewById(R.id.text_project_home_recent_hint)
        recentContainer = findViewById(R.id.layout_project_home_recent_container)
        emptyStateView = findViewById(R.id.layout_project_home_empty_state)
        emptyStateTitleText = findViewById(R.id.text_project_home_empty_title)
        emptyStateMessageText = findViewById(R.id.text_project_home_empty_message)
        baseTopPadding = homeRootContent.paddingTop

        applyStatusBarInsetPadding()
        applyPressAnimation(createProjectTopButton)
        applyPressAnimation(openProjectButton)
        applyPressAnimation(wallpaperActionButton)
        createProjectTopButton.setOnClickListener { createProject() }
    }

    override fun onResume() {
        super.onResume()
        refreshProjectHome()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureWindow()
        }
    }

    /** 每次回到首页都重新读一遍项目列表，保证它能反映编辑器里刚保存过的结果。 */
    private fun refreshProjectHome() {
        val activeProject = ProjectSessionManager.resolveActiveProject(this)
        val projectSummaries = ProjectSessionManager.listProjects(this)
        val otherProjects = projectSummaries.filterNot { it.id == activeProject?.id }

        projectCountText.text = getString(R.string.project_home_count_format, projectSummaries.size)
        bindCurrentProject(activeProject)

        recentTitleText.text = getString(
            if (activeProject != null) {
                R.string.project_home_other_projects
            } else {
                R.string.project_home_all_projects
            }
        )
        recentHintText.visibility = if (otherProjects.isNotEmpty()) View.VISIBLE else View.GONE

        renderOtherProjects(otherProjects)
        bindEmptyState(activeProject, otherProjects.isEmpty(), projectSummaries.isEmpty())
    }

    /** 当前项目卡片承接“继续编辑”和“新建项目”两个主入口。 */
    private fun bindCurrentProject(activeProject: WallpaperProjectDocument?) {
        if (activeProject == null) {
            currentProjectNameText.text = getString(R.string.project_home_empty_title)
            currentProjectMetaText.text = getString(R.string.project_home_empty_meta)
            currentProjectHintText.text = getString(R.string.project_home_empty_hint)
            openProjectButton.visibility = View.GONE
            openProjectButton.setOnClickListener(null)
            wallpaperActionButton.text = getString(R.string.project_home_create_first)
            wallpaperActionButton.setOnClickListener { createProject() }
            return
        }

        currentProjectNameText.text = activeProject.name
        currentProjectMetaText.text = getString(
            R.string.project_recent_updated_format,
            formatProjectUpdatedAt(activeProject.updatedAtMillis)
        )
        val wallpaperActive = WallpaperApplyHelper.isDemoWallpaperActive(this)
        currentProjectHintText.text = getString(
            if (wallpaperActive) {
                R.string.project_home_card_hint_active
            } else {
                R.string.project_home_card_hint
            }
        )
        openProjectButton.visibility = View.VISIBLE
        openProjectButton.text = getString(R.string.project_home_continue)
        openProjectButton.setOnClickListener { openProject(activeProject.id) }
        wallpaperActionButton.text = getString(
            if (wallpaperActive) {
                R.string.project_home_apply_active
            } else {
                R.string.project_home_apply
            }
        )
        wallpaperActionButton.setOnClickListener {
            WallpaperApplyHelper.applyOrPrompt(this, activeProject.name)
        }
    }

    /** 首页列表只展示“其他项目”，避免和当前项目卡片重复。 */
    private fun renderOtherProjects(projectSummaries: List<WallpaperProjectSummary>) {
        recentContainer.removeAllViews()
        recentContainer.visibility = if (projectSummaries.isEmpty()) View.GONE else View.VISIBLE
        projectSummaries.forEach { summary ->
            val row = rowInflater.inflate(R.layout.item_project_recent, recentContainer, false)
            val rowRoot = row.findViewById<LinearLayout>(R.id.layout_project_recent_item)
            val nameText = row.findViewById<TextView>(R.id.text_project_recent_name)
            val timeText = row.findViewById<TextView>(R.id.text_project_recent_time)
            val badgeText = row.findViewById<TextView>(R.id.text_project_recent_badge)

            nameText.text = summary.name
            timeText.text = getString(
                R.string.project_recent_updated_format,
                formatProjectUpdatedAt(summary.updatedAtMillis)
            )
            badgeText.visibility = View.GONE

            applyPressAnimation(rowRoot)
            rowRoot.setOnClickListener { openProject(summary.id) }
            recentContainer.addView(row)
        }
    }

    /** 空态分为“没有其他项目”和“整个应用还没有项目”两种语义。 */
    private fun bindEmptyState(
        activeProject: WallpaperProjectDocument?,
        isOtherProjectsEmpty: Boolean,
        isAllProjectsEmpty: Boolean
    ) {
        if (!isOtherProjectsEmpty) {
            emptyStateView.visibility = View.GONE
            return
        }

        emptyStateView.visibility = View.VISIBLE
        if (isAllProjectsEmpty || activeProject == null) {
            emptyStateTitleText.text = getString(R.string.project_home_empty_library_title)
            emptyStateMessageText.text = getString(R.string.project_home_empty_library_message)
        } else {
            emptyStateTitleText.text = getString(R.string.project_home_empty_recent_title)
            emptyStateMessageText.text = getString(R.string.project_home_empty_recent_message)
        }
    }

    /** 打开选中的项目并进入编辑器。 */
    private fun openProject(projectId: String) {
        startActivity(MainActivity.createProjectIntent(this, projectId))
    }

    /** 新建动作交给编辑器页真正落地，这样新项目可以直接按编辑器视口建立。 */
    private fun createProject() {
        startActivity(MainActivity.createNewProjectIntent(this))
    }

    /** 顶部继续保留状态栏留白，但不直接画系统状态内容。 */
    private fun configureWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    /** 把状态栏安全区叠加到首页顶部，和编辑器页维持同一套留白逻辑。 */
    private fun applyStatusBarInsetPadding() {
        homeRootContent.setOnApplyWindowInsetsListener { view, insets ->
            val statusBarTopInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.statusBars()).top
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop
            }
            val cutoutTopInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                insets.displayCutout?.safeInsetTop ?: 0
            } else {
                0
            }
            val topInset = max(statusBarTopInset, cutoutTopInset).coerceAtLeast(0)

            view.setPadding(
                view.paddingLeft,
                baseTopPadding + topInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        homeRootContent.requestApplyInsets()
    }

    /** 继续沿用当前应用里已经验证过的按压动画，保持首页和编辑器的触感一致。 */
    private fun applyPressAnimation(target: View) {
        target.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate()
                        .scaleX(0.965f)
                        .scaleY(0.965f)
                        .alpha(0.9f)
                        .setDuration(90L)
                        .setInterpolator(DecelerateInterpolator(1.2f))
                        .start()
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(150L)
                        .setInterpolator(DecelerateInterpolator(1.35f))
                        .start()
                }
            }
            false
        }
    }

    /** 首页项目时间文案保持和编辑器弹层一致，避免出现两套时间格式。 */
    private fun formatProjectUpdatedAt(updatedAtMillis: Long): String {
        return DateFormat.format("MM-dd HH:mm", updatedAtMillis).toString()
    }
}
