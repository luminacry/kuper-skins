package com.example.klwpdemo

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.klwpdemo.document.GroupLayerDocument
import com.example.klwpdemo.document.ImageLayerDocument
import com.example.klwpdemo.document.LayerDocument
import com.example.klwpdemo.document.LayerTransformDocument
import com.example.klwpdemo.document.ShapeKind
import com.example.klwpdemo.document.ShapeLayerDocument
import com.example.klwpdemo.document.TextLayerDocument
import com.example.klwpdemo.document.WallpaperDocument
import com.example.klwpdemo.project.ProjectSessionManager
import com.example.klwpdemo.project.WallpaperProjectSummary
import com.example.klwpdemo.project.WallpaperProjectDocument
import com.example.klwpdemo.runtime.RuntimeViewport
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 编辑器主页面。
 *
 * 这里负责把运行时预览、图层列表、属性面板和顶部工具栏组织成一个完整的编辑器界面，
 * 并把交互状态同步到当前 1:1 复刻中的各个可视区域。
 */
class MainActivity : Activity() {
    /** 图层行使用独立布局，因此统一缓存一个 inflater 复用。 */
    private val rowInflater by lazy { LayoutInflater.from(this) }
    /** 项目自动保存使用主线程延迟合并。 */
    private val projectSaveHandler = Handler(Looper.getMainLooper())
    private val projectSaveRunnable = Runnable { flushActiveProjectSave() }

    /** 预览区和底部图层区相关控件。 */
    private lateinit var runtimePreview: EditorRuntimePreviewView
    private lateinit var layerListContainer: LinearLayout
    private lateinit var layerCountText: TextView
    private lateinit var undoButton: ImageButton
    private lateinit var redoButton: ImageButton
    private lateinit var editorToolsRail: View
    private lateinit var editorPropertyPanel: View

    /** 右侧属性面板与底部项目页签相关控件。 */
    private lateinit var fillColorSection: View
    private lateinit var fillColorRoseButton: ImageButton
    private lateinit var fillColorBlueButton: ImageButton
    private lateinit var fillColorMintButton: ImageButton
    private lateinit var projectTab: TextView
    private lateinit var layerTab: TextView
    private lateinit var positionTab: TextView
    private lateinit var globalTab: TextView
    private lateinit var animationTab: TextView
    private lateinit var selectedLayerNameText: TextView
    private lateinit var positionXValueText: TextView
    private lateinit var positionYValueText: TextView
    private lateinit var opacityValueText: TextView
    private lateinit var scaleValueText: TextView
    private lateinit var seekPositionX: SeekBar
    private lateinit var seekPositionY: SeekBar
    private lateinit var seekOpacity: SeekBar
    private lateinit var seekScale: SeekBar
    private lateinit var editorRootContent: View
    private var editorBaseTopPadding = 0
    private lateinit var headerTitleText: TextView
    private lateinit var headerSubtitleText: TextView
    private lateinit var projectStripBackground: ImageView
    private lateinit var contextStripOverlay: View
    private lateinit var contextStripIcon: ImageView
    private lateinit var contextStripLine1Text: TextView
    private lateinit var contextStripLine2Text: TextView
    private lateinit var issuePrimaryText: TextView
    private lateinit var issueActionText: TextView
    private lateinit var editorLayerPanel: View
    private lateinit var toolsActiveIndicator: View
    private lateinit var projectPanelOverlay: View
    private lateinit var projectPanelCardHost: View
    private lateinit var projectPanelNameText: TextView
    private lateinit var projectPanelRecentScroll: ScrollView
    private lateinit var projectPanelRecentContainer: LinearLayout

    /** 当前编辑上下文围绕选中图层、当前容器与编辑状态变化。 */
    private var selectedLayerId: String? = null
    private var currentContainerId: String? = null
    private var suppressSeekCallbacks = false
    private var suppressPreviewSelectionCallback = false
    private var latestLayerItems: List<LayerUiItem> = emptyList()
    private var latestLayerSignature = ""
    private var editorUiState = EditorUiState.IDLE
    private var hasAutoSelectedLayer = false
    private var activeProject: WallpaperProjectDocument? = null
    private var projectBootstrapFinished = false
    private var projectNameDialog: Dialog? = null
    private var projectConfirmDialog: Dialog? = null
    private var launchProjectId: String? = null
    private var launchCreateNewProject = false
    private var issueActionHandler: (() -> Unit)? = null

    /** 属性面板里可直接套用的填充色预设。 */
    private val shapeFillPresets = intArrayOf(
        Color.parseColor("#F28EAE"),
        Color.parseColor("#8EA8FF"),
        Color.parseColor("#7FD6BF")
    )

    /** 初始化页面并完成静态控件绑定。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launchProjectId = if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_PROJECT_ID)
        } else {
            null
        }
        launchCreateNewProject =
            savedInstanceState == null && intent.getBooleanExtra(EXTRA_CREATE_NEW_PROJECT, false)
        setContentView(R.layout.activity_main)
        configureEditorWindow()

        runtimePreview = findViewById(R.id.runtime_preview)
        layerListContainer = findViewById(R.id.layer_list_container)
        layerCountText = findViewById(R.id.text_layer_count)
        redoButton = findViewById(R.id.button_editor_redo)
        undoButton = findViewById(R.id.button_editor_undo)
        editorToolsRail = findViewById(R.id.editor_tools_rail)
        editorPropertyPanel = findViewById(R.id.editor_property_panel)
        fillColorSection = findViewById(R.id.property_fill_section)
        fillColorRoseButton = findViewById(R.id.button_fill_color_rose)
        fillColorBlueButton = findViewById(R.id.button_fill_color_blue)
        fillColorMintButton = findViewById(R.id.button_fill_color_mint)
        projectTab = findViewById(R.id.tab_project)
        layerTab = findViewById(R.id.tab_layer)
        positionTab = findViewById(R.id.tab_position)
        globalTab = findViewById(R.id.tab_global)
        animationTab = findViewById(R.id.tab_animation)
        selectedLayerNameText = findViewById(R.id.text_selected_layer_name)
        positionXValueText = findViewById(R.id.text_position_x_value)
        positionYValueText = findViewById(R.id.text_position_y_value)
        opacityValueText = findViewById(R.id.text_opacity_value)
        scaleValueText = findViewById(R.id.text_scale_value)
        seekPositionX = findViewById(R.id.seek_position_x)
        seekPositionY = findViewById(R.id.seek_position_y)
        seekOpacity = findViewById(R.id.seek_opacity)
        seekScale = findViewById(R.id.seek_scale)
        editorRootContent = findViewById(R.id.editor_root_content)
        editorBaseTopPadding = editorRootContent.paddingTop
        headerTitleText = findViewById(R.id.text_header_title)
        headerSubtitleText = findViewById(R.id.text_header_subtitle)
        projectStripBackground = findViewById(R.id.image_project_strip_background)
        contextStripOverlay = findViewById(R.id.layout_context_strip_overlay)
        contextStripIcon = findViewById(R.id.image_context_strip_icon)
        contextStripLine1Text = findViewById(R.id.text_context_strip_line_1)
        contextStripLine2Text = findViewById(R.id.text_context_strip_line_2)
        issuePrimaryText = findViewById(R.id.text_issue_primary)
        issueActionText = findViewById(R.id.text_issue_action)
        editorLayerPanel = findViewById(R.id.editor_layer_panel)
        toolsActiveIndicator = findViewById(R.id.view_tools_active_indicator)
        projectPanelOverlay = findViewById(R.id.project_panel_overlay)
        projectPanelCardHost = findViewById(R.id.project_panel_card_host)
        projectPanelNameText = findViewById(R.id.text_project_panel_name)
        projectPanelRecentScroll = findViewById(R.id.scroll_project_recent)
        projectPanelRecentContainer = findViewById(R.id.layout_project_recent_container)

        applyStatusBarInsetPadding()

        val addLayerButton = findViewById<ImageButton>(R.id.button_add_layer)
        val closePropertyButton = findViewById<ImageButton>(R.id.button_property_close)
        val deletePropertyButton = findViewById<Button>(R.id.button_property_delete)
        val toolAdjustButton = findViewById<ImageButton>(R.id.button_tool_adjust)
        val toolLayerButton = findViewById<ImageButton>(R.id.button_tool_layers)
        val editorMenuButton = findViewById<View>(R.id.button_editor_menu)

        editorMenuButton.setOnClickListener { navigateToProjectHome() }
        addLayerButton.setOnClickListener { showAddShapeMenu(addLayerButton) }
        redoButton.setOnClickListener { showProjectMenu(redoButton) }
        undoButton.setOnClickListener { performUndo() }
        closePropertyButton.setOnClickListener { closePropertyPanel() }
        deletePropertyButton.setOnClickListener { deleteSelectedLayer() }
        toolAdjustButton.setOnClickListener { openPropertyPanelForSelection() }
        toolLayerButton.setOnClickListener { closePropertyPanel() }
        projectStripBackground.setOnClickListener { navigateToParentContainer() }
        contextStripOverlay.setOnClickListener { navigateToParentContainer() }
        issueActionText.setOnClickListener { issueActionHandler?.invoke() }
        projectPanelOverlay.setOnClickListener { dismissProjectPanel() }
        projectPanelCardHost.setOnClickListener { Unit }
        projectTab.setOnClickListener { closePropertyPanel() }
        layerTab.setOnClickListener {
            Toast.makeText(this, R.string.editor_tab_coming_soon, Toast.LENGTH_SHORT).show()
        }
        positionTab.setOnClickListener {
            Toast.makeText(this, R.string.editor_tab_coming_soon, Toast.LENGTH_SHORT).show()
        }
        globalTab.setOnClickListener {
            Toast.makeText(this, R.string.editor_tab_coming_soon, Toast.LENGTH_SHORT).show()
        }
        animationTab.setOnClickListener {
            Toast.makeText(this, R.string.editor_tab_coming_soon, Toast.LENGTH_SHORT).show()
        }

        setupPropertyPanelControls()
        setupFillColorControls()
        setupProjectPanelActions()
        applyPressAnimation(editorMenuButton)
        applyPressAnimation(redoButton)
        updateHistoryButtons()
        applyEditorUiState(EditorUiState.IDLE)

        runtimePreview.onDocumentSnapshot = { document ->
            bindLayerList(document)
            onEditorDocumentSnapshot(document)
        }
        runtimePreview.onSelectionChanged = { layerId ->
            onPreviewSelectionChanged(layerId)
        }

        runtimePreview.post { bootstrapActiveProject() }
    }

    /** 页面重新拿到焦点时，重新校正系统栏展示状态。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureEditorWindow()
        }
    }

    /** 页面切到后台前，先把当前项目刷回本地。 */
    override fun onPause() {
        flushActiveProjectSave()
        super.onPause()
    }

    /** 从系统壁纸预览页返回时，立即重刷顶部与底部状态文案。 */
    override fun onResume() {
        super.onResume()
        if (projectBootstrapFinished) {
            syncVisibleEditorState()
        }
    }

    /** 页面销毁时清理挂起的自动保存任务。 */
    override fun onDestroy() {
        projectSaveHandler.removeCallbacks(projectSaveRunnable)
        dismissProjectPanel()
        dismissProjectDialogs()
        super.onDestroy()
    }

    /** 把当前文档拍平成图层列表，并同步默认选中项。 */
    private fun bindLayerList(document: WallpaperDocument) {
        val flattened = flattenLayers(document.layers)
        latestLayerItems = flattened
        val defaultProjectId = flattened.firstOrNull { it.containerRole == ContainerRole.PROJECT }?.id

        if (selectedLayerId != null && flattened.none { it.id == selectedLayerId }) {
            selectedLayerId = null
        }
        if (currentContainerId != null && flattened.none { it.id == currentContainerId }) {
            currentContainerId = null
        }

        if (!hasAutoSelectedLayer && defaultProjectId != null) {
            hasAutoSelectedLayer = true
            currentContainerId = defaultProjectId
            selectedLayerId = null
            syncPreviewSelection(null)
            applyEditorUiState(EditorUiState.IDLE)
        } else if (selectedLayerId != null) {
            currentContainerId = resolveContainerIdForSelection(selectedLayerId!!)
                ?: currentContainerId
                ?: defaultProjectId
        } else if (currentContainerId == null) {
            currentContainerId = defaultProjectId
        }

        updateHistoryButtons()

        // 用轻量签名判断图层数据是否真正变化，避免重复重建整个列表。
        val signature = flattened.joinToString(separator = "|") { layer ->
            "${layer.id}:${layer.parentId}:${layer.depth}:${layer.visible}:${layer.transform.translationX}:" +
                "${layer.transform.translationY}:${layer.transform.scaleX}:${layer.transform.scaleY}:" +
                "${layer.transform.rotationDegrees}:${layer.transform.alpha}:${layer.description}:" +
                "${layer.shapeKindLabel}:${layer.shapeWidthPx}:${layer.shapeCornerRadiusPx}:" +
                "${layer.containerRole}:${layer.isContainer}"
        }

        val visibleItems = visibleItemsForCurrentContainer()
        val visibleCount = visibleItems.count { it.visible }
        layerCountText.text = getString(
            R.string.editor_layers_subtitle_dynamic,
            visibleCount,
            visibleItems.size
        )

        if (signature != latestLayerSignature) {
            latestLayerSignature = signature
            renderLayerRows(visibleItems)
        }
        syncVisibleEditorState()
    }

    /** 预览拿到第一帧尺寸后，装入当前活动项目。 */
    private fun bootstrapActiveProject() {
        if (projectBootstrapFinished) {
            return
        }
        if (runtimePreview.width <= 0 || runtimePreview.height <= 0) {
            runtimePreview.post { bootstrapActiveProject() }
            return
        }

        val project = when {
            launchCreateNewProject -> {
                launchCreateNewProject = false
                ProjectSessionManager.createAndActivateProject(
                    context = this,
                    viewport = editorViewport(),
                    name = buildProjectName(getString(R.string.project_name_new_prefix))
                )
            }

            !launchProjectId.isNullOrBlank() -> {
                val projectId = launchProjectId
                launchProjectId = null
                ProjectSessionManager.switchActiveProject(this, projectId!!)
                    ?: ProjectSessionManager.loadOrCreateActiveProject(
                        context = this,
                        viewport = editorViewport()
                    )
            }

            else -> ProjectSessionManager.loadOrCreateActiveProject(
                context = this,
                viewport = editorViewport()
            )
        }
        openProject(project)
    }

    /** 编辑器左上角回到项目首页，保持“先选项目，再进编辑器”的主路径。 */
    private fun navigateToProjectHome() {
        startActivity(
            Intent(this, ProjectHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        )
        finish()
    }

    /** 文档快照变化后，更新当前活动项目并延迟保存。 */
    private fun onEditorDocumentSnapshot(document: WallpaperDocument) {
        if (!projectBootstrapFinished) {
            return
        }
        val changed = ProjectSessionManager.updateActiveDocument(
            context = this,
            document = document,
            viewport = editorViewport()
        )
        if (!changed) {
            return
        }
        activeProject = ProjectSessionManager.snapshotActiveProject()
        scheduleActiveProjectSave()
    }

    /** 把连续编辑合并成一次磁盘写入，避免拖拽期间频繁落盘。 */
    private fun scheduleActiveProjectSave() {
        projectSaveHandler.removeCallbacks(projectSaveRunnable)
        projectSaveHandler.postDelayed(projectSaveRunnable, AUTO_SAVE_DELAY_MILLIS)
    }

    /** 立即把当前活动项目刷到本地。 */
    private fun flushActiveProjectSave() {
        projectSaveHandler.removeCallbacks(projectSaveRunnable)
        activeProject = ProjectSessionManager.persistActiveProject(this)
    }

    /** 当前编辑器视口会作为项目的基准尺寸一并记录。 */
    private fun editorViewport(): RuntimeViewport {
        return RuntimeViewport(
            width = runtimePreview.width.coerceAtLeast(1),
            height = runtimePreview.height.coerceAtLeast(1)
        )
    }

    /** 根据拍平后的图层数据渲染底部列表，并维护选中和显隐状态。 */
    private fun renderLayerRows(items: List<LayerUiItem>) {
        layerListContainer.removeAllViews()
        val currentDepth = currentContainerItem()?.depth ?: -1

        items.forEachIndexed { index, item ->
            val row = rowInflater.inflate(
                R.layout.item_editor_layer_dynamic,
                layerListContainer,
                false
            )

            val rowRoot = row.findViewById<LinearLayout>(R.id.layer_row_root)
            val layerTypeImage = row.findViewById<ImageView>(R.id.image_layer_type)
            val textContainer = row.findViewById<LinearLayout>(R.id.layer_text_container)
            val layerNameText = row.findViewById<TextView>(R.id.text_layer_name)
            val layerDescText = row.findViewById<TextView>(R.id.text_layer_desc)
            val layerVisibleImage = row.findViewById<ImageView>(R.id.image_layer_visible)

            val textLayoutParams = textContainer.layoutParams as LinearLayout.LayoutParams
            val relativeDepth = (item.depth - currentDepth - 1).coerceAtLeast(0)
            textLayoutParams.marginStart = dp(8 + relativeDepth * 12)
            textContainer.layoutParams = textLayoutParams

            layerNameText.text = item.displayName
            layerDescText.text = item.description
            layerTypeImage.setImageResource(item.typeIconRes)
            if (item.visible) {
                layerVisibleImage.background = null
                layerVisibleImage.setImageResource(R.drawable.ic_pencil_editor_checkbox)
            } else {
                layerVisibleImage.setImageResource(0)
                layerVisibleImage.setBackgroundResource(R.drawable.bg_editor_toggle_box_checked)
            }

            val selected = item.id == selectedLayerId
            rowRoot.background = if (selected) {
                resources.getDrawable(R.drawable.bg_editor_layer_row_selected, theme)
            } else {
                null
            }

            rowRoot.setOnClickListener {
                onLayerRowClicked(item)
            }
            layerVisibleImage.setOnClickListener {
                setLayerVisibility(item.id, !item.visible)
            }

            layerListContainer.addView(row)

            if (index < items.lastIndex) {
                layerListContainer.addView(
                    View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            dp(1)
                        )
                        setBackgroundColor(resources.getColor(R.color.editor_divider, theme))
                    }
                )
            }
        }
    }

    /** 绑定属性面板四个滑杆，让它们直接驱动当前选中图层。 */
    private fun setupPropertyPanelControls() {
        seekPositionX.setOnSeekBarChangeListener(
            createSeekChangeListener { progress ->
                val selectedId = selectedLayerId ?: return@createSeekChangeListener
                val current = resolveTransformForSelected(selectedId) ?: return@createSeekChangeListener
                val updated = current.copy(translationX = progress - 200f)
                applyLayerTransformOverride(selectedId, updated)
                updatePropertyValueLabels(updated)
            }
        )

        seekPositionY.setOnSeekBarChangeListener(
            createSeekChangeListener { progress ->
                val selectedId = selectedLayerId ?: return@createSeekChangeListener
                val current = resolveTransformForSelected(selectedId) ?: return@createSeekChangeListener
                val updated = current.copy(translationY = progress - 200f)
                applyLayerTransformOverride(selectedId, updated)
                updatePropertyValueLabels(updated)
            }
        )

        seekOpacity.setOnSeekBarChangeListener(
            createSeekChangeListener { progress ->
                val selectedId = selectedLayerId ?: return@createSeekChangeListener
                val current = resolveTransformForSelected(selectedId) ?: return@createSeekChangeListener
                val updated = current.copy(alpha = (progress / 100f).coerceIn(0f, 1f))
                applyLayerTransformOverride(selectedId, updated)
                updatePropertyValueLabels(updated)
            }
        )

        seekScale.setOnSeekBarChangeListener(
            createSeekChangeListener { progress ->
                val selectedId = selectedLayerId ?: return@createSeekChangeListener
                val current = resolveTransformForSelected(selectedId) ?: return@createSeekChangeListener
                val scale = (progress / 100f).coerceIn(0.2f, 4f)
                val updated = current.copy(scaleX = scale, scaleY = scale)
                applyLayerTransformOverride(selectedId, updated)
                updatePropertyValueLabels(updated)
            }
        )
    }

    /** 绑定三个填充色预设按钮。 */
    private fun setupFillColorControls() {
        val buttons = listOf(
            fillColorRoseButton,
            fillColorBlueButton,
            fillColorMintButton
        )
        buttons.forEachIndexed { index, button ->
            val color = shapeFillPresets[index]
            button.backgroundTintList = ColorStateList.valueOf(color)
            button.setOnClickListener { applySelectedShapeFillColor(color) }
        }
    }

    /** 统一包装滑杆监听，屏蔽程序性回填带来的重复触发。 */
    private fun createSeekChangeListener(
        onProgress: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (!fromUser || suppressSeekCallbacks) {
                    return
                }
                onProgress(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    /** 处理预览区选中变化，并把状态同步到底部列表和右侧面板。 */
    private fun onPreviewSelectionChanged(layerId: String?) {
        if (suppressPreviewSelectionCallback || selectedLayerId == layerId) {
            return
        }
        if (layerId == null) {
            selectedLayerId = null
            applyEditorUiState(EditorUiState.IDLE)
            renderLayerRows(visibleItemsForCurrentContainer())
            return
        }
        selectedLayerId = layerId
        currentContainerId = resolveContainerIdForSelection(layerId) ?: currentContainerId
        applyPreferredStateForCurrentSelection()
        renderLayerRows(visibleItemsForCurrentContainer())
    }

    /** 项目页列表点击时，容器进入下一层，叶子节点则直接选中。 */
    private fun onLayerRowClicked(item: LayerUiItem) {
        if (item.isContainer) {
            currentContainerId = item.id
            selectedLayerId = null
            syncPreviewSelection(null)
            applyEditorUiState(EditorUiState.IDLE)
        } else {
            selectedLayerId = item.id
            currentContainerId = item.parentId ?: currentContainerId
            syncPreviewSelection(item.id)
            applyPreferredStateForCurrentSelection()
        }
        renderLayerRows(visibleItemsForCurrentContainer())
    }

    /** 仅在存在具体图层选中时打开属性面板。 */
    private fun openPropertyPanelForSelection() {
        val selectedItem = currentSelectedItem()
        if (selectedItem == null) {
            Toast.makeText(this, R.string.editor_property_none, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedItem.isContainer) {
            Toast.makeText(this, R.string.editor_property_group_only, Toast.LENGTH_SHORT).show()
            return
        }
        applyEditorUiState(EditorUiState.EDITING)
        syncVisibleEditorState()
    }

    /** 关闭属性面板后，根据选中状态切回合适的编辑态。 */
    private fun closePropertyPanel() {
        if (selectedLayerId == null) {
            applyEditorUiState(EditorUiState.IDLE)
        } else {
            applyEditorUiState(EditorUiState.SELECTED)
        }
        renderLayerRows(visibleItemsForCurrentContainer())
    }

    /** 统一获取当前选中的图层展示模型，避免多处重复查找。 */
    private fun currentSelectedItem(): LayerUiItem? {
        val selectedId = selectedLayerId ?: return null
        return latestLayerItems.find { it.id == selectedId }
    }

    /** 当前首屏只区分是否选中了具体子项，不再自动跳进图形参数页。 */
    private fun applyPreferredStateForCurrentSelection() {
        val selectedItem = currentSelectedItem()
        applyEditorUiState(if (selectedItem == null) EditorUiState.IDLE else EditorUiState.SELECTED)
        syncVisibleEditorState()
    }

    /** 根据当前容器和选中项刷新页眉、左侧条与状态栏文案。 */
    private fun syncVisibleEditorState() {
        val selectedItem = currentSelectedItem()
        val container = currentContainerItem()

        when (container?.containerRole) {
            ContainerRole.PROJECT -> updateProjectChrome(container, selectedItem)
            ContainerRole.COMPONENT -> updateComponentChrome(container, selectedItem)
            else -> resetFocusedChrome()
        }

        if (editorUiState == EditorUiState.EDITING && selectedItem != null && !selectedItem.isContainer) {
            syncPropertyPanelFromSelection()
        }
    }

    /** 项目层使用默认的项目夹条，状态栏主文案指向当前项目。 */
    private fun updateProjectChrome(
        container: LayerUiItem,
        selectedItem: LayerUiItem?
    ) {
        val wallpaperActive = WallpaperApplyHelper.isDemoWallpaperActive(this)
        headerTitleText.text = container.displayName
        headerSubtitleText.text = getString(R.string.editor_header_project_subtitle)
        projectStripBackground.visibility = View.VISIBLE
        contextStripOverlay.visibility = View.GONE
        issuePrimaryText.text = selectedItem?.let {
            getString(R.string.editor_status_selected_item, it.displayName)
        } ?: getString(R.string.editor_status_project_format, activeProjectDisplayName(container))
        issueActionText.text = getString(
            if (wallpaperActive) {
                R.string.editor_status_wallpaper_active
            } else {
                R.string.editor_status_wallpaper_action
            }
        )
        issueActionHandler = { applyCurrentProjectToWallpaper() }
    }

    /** 组件层改用左侧组件条，并允许从状态栏返回上一级项目。 */
    private fun updateComponentChrome(
        container: LayerUiItem,
        selectedItem: LayerUiItem?
    ) {
        headerTitleText.text = container.displayName
        headerSubtitleText.text = getString(
            if (editorUiState == EditorUiState.EDITING && selectedItem != null && !selectedItem.isContainer) {
                R.string.editor_header_shape_subtitle
            } else {
                R.string.editor_header_component_subtitle
            }
        )
        projectStripBackground.visibility = View.GONE
        contextStripOverlay.visibility = View.VISIBLE
        contextStripIcon.setImageResource(R.drawable.ic_pencil_editor_folder)
        contextStripLine1Text.text = getString(R.string.editor_context_strip_line_1)
        contextStripLine2Text.text = getString(R.string.editor_context_strip_line_2)
        issuePrimaryText.text = selectedItem?.let {
            getString(R.string.editor_status_selected_item, it.displayName)
        } ?: getString(R.string.editor_status_component_format, container.displayName)
        issueActionText.text = getString(R.string.editor_status_back_parent)
        issueActionHandler = { navigateToParentContainer() }
    }

    /** 无容器上下文时回到默认编辑器标题。 */
    private fun resetFocusedChrome() {
        headerTitleText.text = getString(R.string.editor_header_default_title)
        headerSubtitleText.text = getString(R.string.editor_header_default_subtitle)
        projectStripBackground.visibility = View.VISIBLE
        contextStripOverlay.visibility = View.GONE
        issuePrimaryText.text = getString(R.string.editor_issue_missing_request)
        issueActionText.text = getString(R.string.editor_status_wallpaper_action)
        issueActionHandler = { applyCurrentProjectToWallpaper() }
    }

    /** 项目态文案优先展示真正的活动项目名，避免把内部项目层名字误当成项目标题。 */
    private fun activeProjectDisplayName(container: LayerUiItem): String {
        return activeProject?.name?.takeIf { it.isNotBlank() } ?: container.displayName
    }

    /** 编辑器里触发“设为壁纸”前先把当前项目刷回本地，避免桌面拿到旧版本。 */
    private fun applyCurrentProjectToWallpaper() {
        flushActiveProjectSave()
        val projectName = activeProject?.name ?: getString(R.string.project_panel_empty_name)
        WallpaperApplyHelper.applyOrPrompt(this, projectName)
    }

    /** 当前容器优先显示它的直接子项，保持项目页逻辑稳定。 */
    private fun visibleItemsForCurrentContainer(): List<LayerUiItem> {
        val containerId = currentContainerId ?: return emptyList()
        return latestLayerItems.filter { it.parentId == containerId }
    }

    /** 取当前列表所处的容器。 */
    private fun currentContainerItem(): LayerUiItem? {
        val containerId = currentContainerId ?: return null
        return latestLayerItems.find { it.id == containerId }
    }

    /** 叶子节点回到父容器，容器节点则显示自己的子项。 */
    private fun resolveContainerIdForSelection(layerId: String): String? {
        val item = latestLayerItems.find { it.id == layerId } ?: return null
        return if (item.isContainer) item.id else item.parentId
    }

    /** 返回上一级容器，便于在项目和组件之间切换。 */
    private fun navigateToParentContainer() {
        val currentContainer = currentContainerItem() ?: return
        val parentId = currentContainer.parentId ?: return
        currentContainerId = parentId
        selectedLayerId = null
        syncPreviewSelection(null)
        applyEditorUiState(EditorUiState.IDLE)
        renderLayerRows(visibleItemsForCurrentContainer())
        syncVisibleEditorState()
    }

    /** 新增图形默认落到当前组件内；如果还停留在项目层，就取第一个组件承接。 */
    private fun resolveShapeInsertionParentId(): String? {
        val currentContainer = currentContainerItem()
        return when (currentContainer?.containerRole) {
            ContainerRole.COMPONENT -> currentContainer.id
            ContainerRole.PROJECT -> latestLayerItems.firstOrNull {
                it.parentId == currentContainer.id && it.containerRole == ContainerRole.COMPONENT
            }?.id

            null -> currentContainerId
        }
    }

    /** 主列表与预览区双向同步时，需要临时抑制回调避免循环。 */
    private fun syncPreviewSelection(layerId: String?) {
        suppressPreviewSelectionCallback = true
        runtimePreview.setSelectedLayer(layerId)
        suppressPreviewSelectionCallback = false
    }

    /** 把当前选中图层的属性回填到右侧属性面板。 */
    private fun syncPropertyPanelFromSelection() {
        val selectedId = selectedLayerId ?: return
        val selectedItem = latestLayerItems.find { it.id == selectedId } ?: return

        val transform = selectedItem.transform
        selectedLayerNameText.text = selectedItem.displayName

        suppressSeekCallbacks = true
        seekPositionX.progress = (transform.translationX + 200f).roundToInt().coerceIn(0, 400)
        seekPositionY.progress = (transform.translationY + 200f).roundToInt().coerceIn(0, 400)
        seekOpacity.progress = (transform.alpha * 100f).roundToInt().coerceIn(0, 100)
        seekScale.progress = (((transform.scaleX + transform.scaleY) * 0.5f) * 100f)
            .roundToInt()
            .coerceIn(20, 400)
        suppressSeekCallbacks = false

        updatePropertyValueLabels(transform)
        updateShapeStyleControls(selectedItem)
    }

    /** 从当前缓存中拿到选中图层的最新变换参数。 */
    private fun resolveTransformForSelected(layerId: String): LayerTransformDocument? {
        return latestLayerItems.find { it.id == layerId }?.transform
    }

    /** 统一把属性修改落到预览运行时。 */
    private fun applyLayerTransformOverride(
        layerId: String,
        transform: LayerTransformDocument
    ) {
        runtimePreview.setLayerTransformOverride(layerId, transform)
    }

    /** 切换图层显隐状态。 */
    private fun setLayerVisibility(
        layerId: String,
        visible: Boolean
    ) {
        if (!runtimePreview.setLayerVisibility(layerId, visible)) {
            Toast.makeText(this, R.string.editor_layer_visibility_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedLayerId == layerId && editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        }
    }

    /** 只有形状图层支持直接修改填充色。 */
    private fun applySelectedShapeFillColor(fillColor: Int) {
        val selectedId = selectedLayerId ?: run {
            Toast.makeText(this, R.string.editor_property_none, Toast.LENGTH_SHORT).show()
            return
        }
        val selectedItem = latestLayerItems.find { it.id == selectedId }
        if (selectedItem?.isShapeLayer != true) {
            Toast.makeText(this, R.string.editor_fill_shape_only, Toast.LENGTH_SHORT).show()
            return
        }
        if (!runtimePreview.updateSelectedShapeFillColor(fillColor)) {
            Toast.makeText(this, R.string.editor_fill_apply_failed, Toast.LENGTH_SHORT).show()
            return
        }
        updateFillColorButtons(fillColor)
    }

    /** 根据图层类型决定是否展示填充色区域。 */
    private fun updateShapeStyleControls(selectedItem: LayerUiItem) {
        if (!selectedItem.isShapeLayer) {
            fillColorSection.visibility = View.GONE
            updateFillColorButtons(null)
            return
        }
        fillColorSection.visibility = View.VISIBLE
        updateFillColorButtons(runtimePreview.selectedShapeFillColor())
    }

    /** 用透明度和缩放强调当前选中的色板。 */
    private fun updateFillColorButtons(selectedFillColor: Int?) {
        val selectedRgb = selectedFillColor?.and(0x00FFFFFF)
        val buttons = listOf(
            fillColorRoseButton,
            fillColorBlueButton,
            fillColorMintButton
        )
        buttons.forEachIndexed { index, button ->
            val preset = shapeFillPresets[index]
            val active = selectedRgb != null && (preset and 0x00FFFFFF) == selectedRgb
            button.alpha = if (active) 1f else 0.55f
            button.scaleX = if (active) 1f else 0.88f
            button.scaleY = if (active) 1f else 0.88f
        }
    }

    /** 把数值类属性格式化成面板上可直接阅读的文本。 */
    private fun updatePropertyValueLabels(transform: LayerTransformDocument) {
        positionXValueText.text = transform.translationX.roundToInt().toString()
        positionYValueText.text = transform.translationY.roundToInt().toString()
        opacityValueText.text = "${(transform.alpha * 100f).roundToInt()}%"
        scaleValueText.text = "${((transform.scaleX + transform.scaleY) * 50f).roundToInt()}%"
    }

    /** 顶部标题区域弹出最小项目菜单，先支持新建、另存和切换。 */
    private fun showProjectMenu(anchor: View) {
        if (projectPanelOverlay.visibility == View.VISIBLE) {
            dismissProjectPanel()
            return
        }

        val projectSummaries = ProjectSessionManager.listProjects(this)
        projectPanelNameText.text = activeProject?.name ?: getString(R.string.project_panel_empty_name)
        populateProjectRecentList(
            container = projectPanelRecentContainer,
            projectSummaries = projectSummaries
        )

        projectPanelCardHost.visibility = View.INVISIBLE
        projectPanelOverlay.visibility = View.VISIBLE
        projectPanelOverlay.bringToFront()
        projectPanelOverlay.post {
            if (projectPanelOverlay.visibility != View.VISIBLE) {
                return@post
            }
            updateProjectPanelLayout(anchor, projectSummaries.size)
            animateProjectPanelEntrance()
        }
    }

    /** 保存图标进入的是项目菜单，因此这里补一个显式立即保存动作。 */
    /** 椤圭洰闈㈡澘鍐呯殑鎸夐挳鍦ㄥ垵濮嬪寲鏃朵竴娆℃€х粦瀹氾紝閬垮厤姣忔寮瑰嚭閮藉啀鍒涘缓鐩稿悓閫昏緫銆?*/
    private fun setupProjectPanelActions() {
        bindProjectPanelAction(R.id.button_project_panel_save) {
            saveCurrentProjectNow()
        }
        bindProjectPanelAction(R.id.button_project_panel_new) {
            createNewProject()
        }
        bindProjectPanelAction(R.id.button_project_panel_duplicate) {
            duplicateCurrentProject()
        }
        bindProjectPanelAction(R.id.button_project_panel_rename) {
            promptRenameActiveProject()
        }
        bindProjectPanelAction(R.id.button_project_panel_delete) {
            confirmDeleteActiveProject()
        }
    }

    /** 鎸夐挳鐨勭湡姝ｄ笟鍔″墠鍏堟敹璧烽」鐩崱鐗囷紝閬垮厤杩炵画鎿嶄綔鏃堕噸鍙犳尜鍘嬨€?*/
    private fun bindProjectPanelAction(viewId: Int, action: () -> Unit) {
        findViewById<TextView>(viewId).apply {
            applyPressAnimation(this)
            setOnClickListener {
                dismissProjectPanel(action)
            }
        }
    }

    /** 鎶婇」鐩崱鐗囧畾浣嶅埌淇濆瓨鍥炬爣闄勮繎锛屽悓鏃舵妸鏈€杩戦」鐩垪琛ㄦ敹鍦ㄥ崱鐗囧唴閮ㄦ粴鍔ㄣ€?*/
    private fun updateProjectPanelLayout(anchor: View, recentCount: Int) {
        val overlayWidth = projectPanelOverlay.width.coerceAtLeast(resources.displayMetrics.widthPixels)
        val overlayHeight = projectPanelOverlay.height.coerceAtLeast(resources.displayMetrics.heightPixels)
        val panelMargin = dp(8)
        val panelWidth = minOf(
            dp(272),
            (overlayWidth - panelMargin * 2).coerceAtLeast(dp(220))
        )

        val overlayLocation = IntArray(2)
        val anchorLocation = IntArray(2)
        projectPanelOverlay.getLocationInWindow(overlayLocation)
        anchor.getLocationInWindow(anchorLocation)

        val anchorTop = anchorLocation[1] - overlayLocation[1]
        val anchorRight = anchorLocation[0] - overlayLocation[0] + anchor.width
        val anchorBottom = anchorTop + anchor.height
        val preferredTop = anchorBottom + dp(10)
        val availableBelow = (overlayHeight - preferredTop - panelMargin).coerceAtLeast(0)
        val availableAbove = (anchorTop - panelMargin).coerceAtLeast(0)
        val recentMaxHeight = (maxOf(availableBelow, availableAbove) - dp(148)).coerceAtLeast(dp(112))

        projectPanelRecentScroll.layoutParams = projectPanelRecentScroll.layoutParams.apply {
            height = if (recentCount > 4) {
                minOf(dp(224), recentMaxHeight)
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
        }

        projectPanelCardHost.layoutParams =
            (projectPanelCardHost.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                width = panelWidth
                gravity = Gravity.TOP or Gravity.END
            }

        projectPanelCardHost.measure(
            View.MeasureSpec.makeMeasureSpec(panelWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val panelHeight = projectPanelCardHost.measuredHeight
        val maxTopMargin = (overlayHeight - panelHeight - panelMargin).coerceAtLeast(panelMargin)
        val maxEndMargin = (overlayWidth - panelWidth - panelMargin).coerceAtLeast(panelMargin)
        val preferredEndMargin = dp(18)
        val anchorDrivenEndMargin = (overlayWidth - anchorRight - dp(56)).coerceAtLeast(panelMargin)
        val showAbove = availableBelow < panelHeight && availableAbove > availableBelow

        projectPanelCardHost.layoutParams =
            (projectPanelCardHost.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                topMargin = if (showAbove) {
                    (anchorTop - panelHeight - dp(8)).coerceAtLeast(panelMargin)
                } else {
                    preferredTop.coerceIn(panelMargin, maxTopMargin)
                }
                marginEnd = anchorDrivenEndMargin.coerceIn(
                    panelMargin,
                    minOf(maxEndMargin, preferredEndMargin)
                )
            }
        projectPanelCardHost.requestLayout()
    }

    private fun animateProjectPanelEntrance() {
        projectPanelOverlay.animate().cancel()
        projectPanelCardHost.animate().cancel()
        projectPanelOverlay.alpha = 1f
        projectPanelCardHost.apply {
            visibility = View.VISIBLE
            pivotX = width.toFloat()
            pivotY = 0f
            alpha = 0f
            scaleX = 0.96f
            scaleY = 0.96f
            translationY = (-dp(8)).toFloat()
        }
        projectPanelCardHost.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator(1.35f))
            .start()
    }

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

    private fun saveCurrentProjectNow() {
        if (activeProject == null) {
            Toast.makeText(this, R.string.project_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        flushActiveProjectSave()
        Toast.makeText(this, R.string.project_save_success, Toast.LENGTH_SHORT).show()
    }

    /** 用统一列表行渲染最近项目，并在当前项目上补一个状态标识。 */
    private fun populateProjectRecentList(
        container: LinearLayout,
        projectSummaries: List<WallpaperProjectSummary>
    ) {
        container.removeAllViews()
        projectSummaries.forEach { summary ->
            val row = rowInflater.inflate(R.layout.item_project_recent, container, false)
            val rowRoot = row.findViewById<LinearLayout>(R.id.layout_project_recent_item)
            val nameText = row.findViewById<TextView>(R.id.text_project_recent_name)
            val timeText = row.findViewById<TextView>(R.id.text_project_recent_time)
            val badgeText = row.findViewById<TextView>(R.id.text_project_recent_badge)
            val isCurrent = summary.id == activeProject?.id

            rowRoot.setBackgroundResource(
                if (isCurrent) {
                    R.drawable.bg_project_recent_item_current
                } else {
                    R.drawable.bg_project_recent_item
                }
            )
            nameText.text = summary.name
            timeText.text = getString(
                R.string.project_recent_updated_format,
                formatProjectUpdatedAt(summary.updatedAtMillis)
            )
            badgeText.visibility = if (isCurrent) View.VISIBLE else View.GONE
            applyPressAnimation(rowRoot)
            rowRoot.setOnClickListener {
                dismissProjectPanel {
                    switchProject(summary.id)
                }
            }

            container.addView(row)
        }
    }

    /** 项目卡片里的时间文案保持简短，避免把顶部浮层挤得太重。 */
    private fun formatProjectUpdatedAt(updatedAtMillis: Long): String {
        return DateFormat.format("MM-dd HH:mm", updatedAtMillis).toString()
    }

    /** 重命名只作用于当前活动项目，避免在最近列表里出现语义不清的行内编辑。 */
    private fun promptRenameActiveProject() {
        val currentProject = activeProject ?: run {
            Toast.makeText(this, R.string.project_rename_failed, Toast.LENGTH_SHORT).show()
            return
        }
        showProjectNameDialog(
            title = getString(R.string.project_dialog_rename_title),
            message = getString(R.string.project_dialog_rename_message),
            initialValue = currentProject.name
        ) { name ->
            if (name == currentProject.name) {
                return@showProjectNameDialog
            }
            val renamedProject = ProjectSessionManager.renameActiveProject(this, name) ?: run {
                Toast.makeText(this, R.string.project_rename_failed, Toast.LENGTH_SHORT).show()
                return@showProjectNameDialog
            }
            openProject(renamedProject)
            Toast.makeText(
                this,
                getString(R.string.project_rename_success_format, renamedProject.name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 删除动作放到二次确认里，避免用户在保存入口误触当前项目。 */
    private fun confirmDeleteActiveProject() {
        val currentProject = activeProject ?: run {
            Toast.makeText(this, R.string.project_delete_failed, Toast.LENGTH_SHORT).show()
            return
        }
        showProjectDeleteDialog(currentProject.name) {
            val deletedProjectName = currentProject.name
            val fallbackProject = ProjectSessionManager.deleteActiveProject(
                context = this,
                viewport = editorViewport()
            ) ?: run {
                Toast.makeText(this, R.string.project_delete_failed, Toast.LENGTH_SHORT).show()
                return@showProjectDeleteDialog
            }
            openProject(fallbackProject)
            Toast.makeText(
                this,
                getString(R.string.project_delete_success_format, deletedProjectName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** 统一封装名称输入弹窗，保证项目相关操作和悬浮面板的视觉语气一致。 */
    private fun showProjectNameDialog(
        title: String,
        message: String,
        initialValue: String,
        onConfirm: (String) -> Unit
    ) {
        projectNameDialog?.dismiss()
        val dialogView = layoutInflater.inflate(R.layout.dialog_project_input, null)
        val dialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialogView.findViewById<TextView>(R.id.text_project_dialog_title).text = title
        dialogView.findViewById<TextView>(R.id.text_project_dialog_message).text = message
        val inputView = dialogView.findViewById<EditText>(R.id.input_project_dialog_name)
        inputView.setText(initialValue)
        inputView.setSelection(inputView.text.length)

        dialogView.findViewById<TextView>(R.id.button_project_dialog_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.button_project_dialog_confirm).setOnClickListener {
            val trimmedName = inputView.text.toString().trim()
            if (trimmedName.isEmpty()) {
                Toast.makeText(this, R.string.project_dialog_name_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            onConfirm(trimmedName)
        }

        dialog.setOnDismissListener {
            if (projectNameDialog === dialog) {
                projectNameDialog = null
            }
        }
        projectNameDialog = dialog
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** 删除确认弹窗保持最小信息量，重点只放风险和当前项目名。 */
    private fun showProjectDeleteDialog(
        projectName: String,
        onDelete: () -> Unit
    ) {
        projectConfirmDialog?.dismiss()
        val dialogView = layoutInflater.inflate(R.layout.dialog_project_confirm, null)
        val dialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(true)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        dialogView.findViewById<TextView>(R.id.text_project_confirm_title).text =
            getString(R.string.project_dialog_delete_title)
        dialogView.findViewById<TextView>(R.id.text_project_confirm_message).text =
            getString(R.string.project_dialog_delete_message_format, projectName)
        dialogView.findViewById<TextView>(R.id.button_project_confirm_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialogView.findViewById<TextView>(R.id.button_project_confirm_delete).setOnClickListener {
            dialog.dismiss()
            onDelete()
        }

        dialog.setOnDismissListener {
            if (projectConfirmDialog === dialog) {
                projectConfirmDialog = null
            }
        }
        projectConfirmDialog = dialog
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** 进入其他操作前先把项目浮层收掉，避免顶部连续点击时叠层。 */
    private fun dismissProjectPanel(afterDismiss: (() -> Unit)? = null) {
        if (projectPanelOverlay.visibility != View.VISIBLE) {
            afterDismiss?.invoke()
            return
        }

        projectPanelOverlay.animate().cancel()
        projectPanelCardHost.animate().cancel()
        projectPanelCardHost.pivotX = projectPanelCardHost.width.toFloat()
        projectPanelCardHost.pivotY = 0f
        projectPanelCardHost.animate()
            .alpha(0f)
            .scaleX(0.975f)
            .scaleY(0.975f)
            .translationY((-dp(6)).toFloat())
            .setDuration(150L)
            .setInterpolator(DecelerateInterpolator(1.2f))
            .withEndAction {
                projectPanelOverlay.visibility = View.GONE
                projectPanelOverlay.alpha = 1f
                projectPanelCardHost.visibility = View.VISIBLE
                projectPanelCardHost.alpha = 1f
                projectPanelCardHost.scaleX = 1f
                projectPanelCardHost.scaleY = 1f
                projectPanelCardHost.translationY = 0f
                afterDismiss?.invoke()
            }
            .start()
    }

    /** 项目相关弹窗统一在页面销毁时兜底释放。 */
    private fun dismissProjectDialogs() {
        projectNameDialog?.dismiss()
        projectNameDialog = null
        projectConfirmDialog?.dismiss()
        projectConfirmDialog = null
    }

    /** 顶部加号按钮弹出新增图形菜单。 */
    private fun showAddShapeMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(Menu.NONE, MENU_ADD_RECTANGLE, Menu.NONE, R.string.editor_add_shape_rectangle)
            menu.add(Menu.NONE, MENU_ADD_CIRCLE, Menu.NONE, R.string.editor_add_shape_circle)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_ADD_RECTANGLE -> addShape(ShapeKind.RECTANGLE)
                    MENU_ADD_CIRCLE -> addShape(ShapeKind.CIRCLE)
                    else -> false
                }
            }
        }.show()
    }

    /** 生成一份新的默认项目并立即切换。 */
    private fun createNewProject() {
        flushActiveProjectSave()
        val project = ProjectSessionManager.createAndActivateProject(
            context = this,
            viewport = editorViewport(),
            name = buildProjectName(getString(R.string.project_name_new_prefix))
        )
        openProject(project)
        Toast.makeText(
            this,
            getString(R.string.project_created_format, project.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 用当前项目内容快速生成一份副本。 */
    private fun duplicateCurrentProject() {
        if (activeProject == null) {
            Toast.makeText(this, R.string.project_duplicate_failed, Toast.LENGTH_SHORT).show()
            return
        }
        flushActiveProjectSave()
        val project = ProjectSessionManager.duplicateActiveProject(
            context = this,
            name = buildProjectName(getString(R.string.project_name_copy_prefix))
        ) ?: run {
            Toast.makeText(this, R.string.project_duplicate_failed, Toast.LENGTH_SHORT).show()
            return
        }
        openProject(project)
        Toast.makeText(
            this,
            getString(R.string.project_duplicate_success_format, project.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 切换到用户选择的项目。 */
    private fun switchProject(projectId: String) {
        if (activeProject?.id == projectId) {
            Toast.makeText(this, R.string.project_switch_current, Toast.LENGTH_SHORT).show()
            return
        }
        flushActiveProjectSave()
        val project = ProjectSessionManager.switchActiveProject(this, projectId) ?: run {
            Toast.makeText(this, R.string.project_switch_failed, Toast.LENGTH_SHORT).show()
            return
        }
        openProject(project)
        Toast.makeText(
            this,
            getString(R.string.project_switch_success_format, project.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** 打开新项目时统一重置列表状态、选中状态和当前预览文档。 */
    private fun openProject(project: WallpaperProjectDocument) {
        activeProject = project
        projectBootstrapFinished = true
        hasAutoSelectedLayer = false
        selectedLayerId = null
        currentContainerId = null
        latestLayerItems = emptyList()
        latestLayerSignature = ""
        applyEditorUiState(EditorUiState.IDLE)
        runtimePreview.loadDocument(project.document)
    }

    /** 菜单里给当前项目加一个轻量高亮，方便快速识别。 */
    private fun formatProjectMenuTitle(summary: WallpaperProjectSummary): String {
        return if (summary.id == activeProject?.id) {
            getString(R.string.project_menu_current_format, summary.name)
        } else {
            summary.name
        }
    }

    /** 项目名先按时间戳生成，后续再补正式重命名页。 */
    private fun buildProjectName(prefix: String): String {
        return "$prefix " + DateFormat.format("MM-dd HH:mm", System.currentTimeMillis())
    }

    private fun addShape(kind: ShapeKind): Boolean {
        val parentLayerId = resolveShapeInsertionParentId()
        val layerId = runtimePreview.addShape(kind, parentLayerId) ?: run {
            Toast.makeText(this, R.string.editor_add_shape_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        selectedLayerId = layerId
        currentContainerId = resolveContainerIdForSelection(layerId) ?: parentLayerId
        applyPreferredStateForCurrentSelection()
        renderLayerRows(visibleItemsForCurrentContainer())
        return true
    }

    /** 删除按钮只在已有选中图层时生效。 */
    private fun deleteSelectedLayer() {
        val layerId = selectedLayerId
        if (layerId == null) {
            Toast.makeText(this, R.string.editor_property_none, Toast.LENGTH_SHORT).show()
            return
        }
        deleteLayerById(layerId)
    }

    /** 真正执行删除，并在必要时清空选中状态。 */
    private fun deleteLayerById(layerId: String) {
        if (!runtimePreview.deleteLayer(layerId)) {
            Toast.makeText(this, R.string.editor_delete_shape_failed, Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedLayerId == layerId) {
            selectedLayerId = null
            applyEditorUiState(EditorUiState.IDLE)
        }
        renderLayerRows(visibleItemsForCurrentContainer())
        syncVisibleEditorState()
    }

    /** 撤销后同步属性面板与顶部按钮状态。 */
    private fun performUndo() {
        if (!runtimePreview.undoEdit()) {
            Toast.makeText(this, R.string.editor_undo_empty, Toast.LENGTH_SHORT).show()
            updateHistoryButtons()
            return
        }
        syncPropertyPanelIfNeeded()
        updateHistoryButtons()
    }

    /** 重做后同步属性面板与顶部按钮状态。 */
    private fun performRedo() {
        if (!runtimePreview.redoEdit()) {
            Toast.makeText(this, R.string.editor_redo_empty, Toast.LENGTH_SHORT).show()
            updateHistoryButtons()
            return
        }
        syncPropertyPanelIfNeeded()
        updateHistoryButtons()
    }

    /** 当前设计稿要求顶部四个按钮始终可见，因此这里保持启用态。 */
    private fun updateHistoryButtons() {
        undoButton.isEnabled = true
        redoButton.isEnabled = true
        undoButton.alpha = 1f
        redoButton.alpha = 1f
    }

    /** 根据是否正在编辑属性，切换右侧属性面板并保持项目页为默认页签。 */
    private fun applyEditorUiState(state: EditorUiState) {
        editorUiState = state
        editorToolsRail.visibility = View.VISIBLE
        editorPropertyPanel.visibility = if (state == EditorUiState.EDITING) View.VISIBLE else View.GONE
        editorLayerPanel.visibility = View.VISIBLE
        updateToolsRailIndicator(state)
        applyTabVisualState(projectTab, active = true)
        applyTabVisualState(layerTab, active = false)
        applyTabVisualState(positionTab, active = false)
        applyTabVisualState(globalTab, active = false)
        applyTabVisualState(animationTab, active = false)
    }

    /** 用固定槽位高亮当前主操作：默认是图层，打开属性面板后切到调整。 */
    private fun updateToolsRailIndicator(state: EditorUiState) {
        val targetIndex = if (state == EditorUiState.EDITING) 4 else 3
        toolsActiveIndicator.translationY = dp(targetIndex * 48).toFloat()
    }

    /** 底部四个页签只需要视觉区分，不涉及真实页面切换。 */
    private fun applyTabVisualState(
        tab: TextView,
        active: Boolean
    ) {
        tab.setBackgroundResource(
            if (active) R.drawable.bg_editor_tab_active else R.drawable.bg_editor_tab_idle
        )
        tab.setTextColor(
            resources.getColor(
                if (active) R.color.editor_text_primary else R.color.editor_text_secondary,
                theme
            )
        )
    }

    /** 只有在属性面板展开时才需要回刷右侧控件。 */
    private fun syncPropertyPanelIfNeeded() {
        if (editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        }
        syncVisibleEditorState()
    }

    /** 把树状图层拍平成列表，方便底部清单逐行渲染。 */
    private fun flattenLayers(layers: List<LayerDocument>): List<LayerUiItem> {
        val result = mutableListOf<LayerUiItem>()
        flattenInto(layers, depth = 0, parentId = null, destination = result)
        return result
    }

    /** 递归遍历图层树，并保留层级深度用于缩进。 */
    private fun flattenInto(
        layers: List<LayerDocument>,
        depth: Int,
        parentId: String?,
        destination: MutableList<LayerUiItem>
    ) {
        layers.forEach { layer ->
            destination += layer.toLayerUiItem(depth = depth, parentId = parentId)
            if (layer is GroupLayerDocument) {
                flattenInto(
                    layers = layer.children,
                    depth = depth + 1,
                    parentId = layer.id,
                    destination = destination
                )
            }
        }
    }

    /** 把运行时图层转换成底部列表需要的展示模型。 */
    private fun LayerDocument.toLayerUiItem(
        depth: Int,
        parentId: String?
    ): LayerUiItem {
        val shapeLayer = this as? ShapeLayerDocument
        val averageScale = ((transform.scaleX + transform.scaleY) * 0.5f).coerceAtLeast(0.2f)
        val containerRole = when {
            this !is GroupLayerDocument -> null
            parentId == null -> ContainerRole.PROJECT
            else -> ContainerRole.COMPONENT
        }
        return LayerUiItem(
            id = id,
            parentId = parentId,
            displayName = localizedLayerDisplayName(id),
            description = describeLayer(
                layer = this,
                containerRole = containerRole
            ),
            depth = depth,
            visible = visible,
            transform = transform,
            isContainer = this is GroupLayerDocument,
            containerRole = containerRole,
            isShapeLayer = shapeLayer != null,
            typeIconRes = resolveTypeIcon(this),
            shapeKindLabel = shapeLayer?.shapeKind?.let(::localizedShapeKind),
            shapeWidthPx = shapeLayer?.let { (it.bounds.width * transform.scaleX).roundToInt() },
            shapeCornerRadiusPx = shapeLayer?.let { (it.style.cornerRadius * averageScale).roundToInt() },
            shapeRotationDegrees = transform.rotationDegrees.roundToInt()
        )
    }

    /** 为底部列表生成简短描述。 */
    private fun describeLayer(
        layer: LayerDocument,
        containerRole: ContainerRole?
    ): String {
        return when (layer) {
            is GroupLayerDocument -> when (containerRole) {
                ContainerRole.PROJECT -> "项目"
                ContainerRole.COMPONENT -> "组件"
                null -> "组件"
            }

            is ShapeLayerDocument -> when (layer.shapeKind) {
                ShapeKind.RECTANGLE -> "方形"
                ShapeKind.CIRCLE -> "圆形"
            }

            is ImageLayerDocument -> "图片"
            is TextLayerDocument -> "文本"
        }
    }

    /** 不同图层类型对应不同的小图标。 */
    private fun resolveTypeIcon(layer: LayerDocument): Int {
        return when (layer) {
            is GroupLayerDocument -> R.drawable.ic_pencil_editor_type_group
            is ShapeLayerDocument -> when (layer.shapeKind) {
                ShapeKind.RECTANGLE -> R.drawable.ic_pencil_editor_type_rect
                ShapeKind.CIRCLE -> R.drawable.ic_editor_type_circle
            }

            is ImageLayerDocument, is TextLayerDocument -> R.drawable.ic_pencil_editor_type_group
        }
    }

    /** 对运行时图层 id 做本地化展示。 */
    /** 把形状类型同步成首屏成品稿里的中文语义。 */
    private fun localizedShapeKind(shapeKind: ShapeKind): String {
        return when (shapeKind) {
            ShapeKind.RECTANGLE -> "方形"
            ShapeKind.CIRCLE -> "圆形"
        }
    }

    private fun localizedLayerDisplayName(id: String): String {
        localizedLayerNameOverrides[id]?.let { return it }

        val translatedTokens = id
            .split('-', '_')
            .filter { it.isNotBlank() }
            .map { token ->
                if (token.all { it.isDigit() }) token else translateLayerToken(token)
            }

        if (translatedTokens.isEmpty()) {
            return id
        }

        val lastToken = translatedTokens.last()
        return if (lastToken.all { it.isDigit() }) {
            translatedTokens.dropLast(1).joinToString(separator = "") + " " + lastToken
        } else {
            translatedTokens.joinToString(separator = "")
        }
    }

    /** 把英文 token 映射成中文标签，保证图层语言风格统一。 */
    private fun translateLayerToken(token: String): String {
        return when (token) {
            "user" -> "自定义"
            "rect" -> "方形"
            "circle" -> "圆形"
            "left" -> "左侧"
            "right" -> "右侧"
            "haze" -> "雾层"
            "outline" -> "轮廓"
            "card" -> "卡片"
            "orb" -> "光球"
            "glow" -> "光晕"
            "core" -> "核心"
            "cluster" -> "组件"
            "info" -> "信息"
            "panel" -> "面板"
            "title" -> "标题"
            "clock" -> "时间"
            "touch" -> "触摸"
            "ripple" -> "涟漪"
            "project" -> "项目"
            "component" -> "组件"
            "main" -> "主"
            "visual" -> "视觉"
            "wallpaper" -> "壁纸"
            "atmosphere" -> "氛围"
            else -> token
        }
    }

    /** 按当前屏幕密度把 dp 转成实际像素。 */
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    /** 让页面保留状态栏留白，但不覆盖编辑器自己的内容。 */
    private fun configureEditorWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.show(WindowInsets.Type.statusBars())
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }

    /** 把顶部安全区叠加到根容器，兼容刘海屏与不同系统版本。 */
    private fun applyStatusBarInsetPadding() {
        editorRootContent.setOnApplyWindowInsetsListener { view, insets ->
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
            val topInset = (max(statusBarTopInset, cutoutTopInset) - dp(2)).coerceAtLeast(0)

            view.setPadding(
                view.paddingLeft,
                editorBaseTopPadding + topInset,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }
        editorRootContent.requestApplyInsets()
    }

    /** 底部列表渲染时使用的轻量展示模型。 */
    private data class LayerUiItem(
        val id: String,
        val parentId: String?,
        val displayName: String,
        val description: String,
        val depth: Int,
        val visible: Boolean,
        val transform: LayerTransformDocument,
        val isContainer: Boolean,
        val containerRole: ContainerRole?,
        val isShapeLayer: Boolean,
        val typeIconRes: Int,
        val shapeKindLabel: String? = null,
        val shapeWidthPx: Int? = null,
        val shapeCornerRadiusPx: Int? = null,
        val shapeRotationDegrees: Int = 0
    )

    /** 这里只区分顶层项目和项目内组件。 */
    private enum class ContainerRole {
        PROJECT,
        COMPONENT
    }

    /** 编辑器只区分三种视图状态。 */
    private enum class EditorUiState {
        IDLE,
        SELECTED,
        EDITING
    }

    /** 统一收口页面常量，避免魔法数字散落。 */
    companion object {
        private const val EXTRA_PROJECT_ID = "com.example.klwpdemo.extra.PROJECT_ID"
        private const val EXTRA_CREATE_NEW_PROJECT = "com.example.klwpdemo.extra.CREATE_NEW_PROJECT"
        const val MENU_ADD_RECTANGLE = 1001
        const val MENU_ADD_CIRCLE = 1002
        const val MENU_PROJECT_SAVE = 2000
        const val MENU_PROJECT_CREATE = 2001
        const val MENU_PROJECT_DUPLICATE = 2002
        const val MENU_PROJECT_LABEL = 2003
        const val MENU_PROJECT_SWITCH_BASE = 3000
        const val AUTO_SAVE_DELAY_MILLIS = 600L

        /** 从项目首页按指定项目进入编辑器。 */
        fun createProjectIntent(
            context: Context,
            projectId: String
        ): Intent {
            return Intent(context, MainActivity::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
        }

        /** 从项目首页直接进入“新建项目”流程，真正的项目在编辑器拿到视口后再创建。 */
        fun createNewProjectIntent(context: Context): Intent {
            return Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CREATE_NEW_PROJECT, true)
        }

        val localizedLayerNameOverrides = mapOf(
            "project-hh301" to "HH301",
            "project-main-wallpaper" to "主屏壁纸",
            "component-main-visual" to "主视觉",
            "component-info-panel" to "信息面板",
            "component-atmosphere" to "背景氛围",
            "left-haze" to "左侧雾层",
            "outline-card" to "轮廓卡片",
            "orb-cluster" to "光球组件",
            "orb-glow" to "光球光晕",
            "orb-main" to "焦点圆",
            "orb-core" to "中心点",
            "right-haze" to "右侧雾层",
            "info-panel" to "信息面板",
            "info-panel-bg" to "面板背景",
            "info-title" to "标题文本",
            "info-clock" to "时间文本",
            "touch-ripple" to "触摸涟漪"
        )
    }
}
