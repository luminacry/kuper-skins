package com.example.klwpdemo

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.SeekBar
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

    /** 当前编辑上下文围绕选中图层、当前容器与编辑状态变化。 */
    private var selectedLayerId: String? = null
    private var currentContainerId: String? = null
    private var suppressSeekCallbacks = false
    private var suppressPreviewSelectionCallback = false
    private var latestLayerItems: List<LayerUiItem> = emptyList()
    private var latestLayerSignature = ""
    private var editorUiState = EditorUiState.IDLE
    private var hasAutoSelectedLayer = false

    /** 属性面板里可直接套用的填充色预设。 */
    private val shapeFillPresets = intArrayOf(
        Color.parseColor("#F28EAE"),
        Color.parseColor("#8EA8FF"),
        Color.parseColor("#7FD6BF")
    )

    /** 初始化页面并完成静态控件绑定。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        applyStatusBarInsetPadding()

        val addLayerButton = findViewById<ImageButton>(R.id.button_add_layer)
        val closePropertyButton = findViewById<ImageButton>(R.id.button_property_close)
        val deletePropertyButton = findViewById<Button>(R.id.button_property_delete)
        val toolAdjustButton = findViewById<ImageButton>(R.id.button_tool_adjust)
        val toolLayerButton = findViewById<ImageButton>(R.id.button_tool_layers)

        addLayerButton.setOnClickListener { showAddShapeMenu(addLayerButton) }
        redoButton.setOnClickListener { performRedo() }
        undoButton.setOnClickListener { performUndo() }
        closePropertyButton.setOnClickListener { closePropertyPanel() }
        deletePropertyButton.setOnClickListener { deleteSelectedLayer() }
        toolAdjustButton.setOnClickListener { openPropertyPanelForSelection() }
        toolLayerButton.setOnClickListener { closePropertyPanel() }
        projectStripBackground.setOnClickListener { navigateToParentContainer() }
        contextStripOverlay.setOnClickListener { navigateToParentContainer() }
        issueActionText.setOnClickListener { navigateToParentContainer() }
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
        updateHistoryButtons()
        applyEditorUiState(EditorUiState.IDLE)

        runtimePreview.onDocumentSnapshot = { document ->
            bindLayerList(document)
        }
        runtimePreview.onSelectionChanged = { layerId ->
            onPreviewSelectionChanged(layerId)
        }
    }

    /** 页面重新拿到焦点时，重新校正系统栏展示状态。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureEditorWindow()
        }
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
        headerTitleText.text = container.displayName
        headerSubtitleText.text = getString(R.string.editor_header_project_subtitle)
        projectStripBackground.visibility = View.VISIBLE
        contextStripOverlay.visibility = View.GONE
        issuePrimaryText.text = selectedItem?.let {
            getString(R.string.editor_status_selected_item, it.displayName)
        } ?: getString(R.string.editor_status_project_format, container.displayName)
        issueActionText.text = getString(R.string.editor_status_project_tag)
    }

    /** 组件层改用左侧组件条，并允许从状态栏返回上一级项目。 */
    private fun updateComponentChrome(
        container: LayerUiItem,
        selectedItem: LayerUiItem?
    ) {
        headerTitleText.text = container.displayName
        headerSubtitleText.text = getString(R.string.editor_header_component_subtitle)
        projectStripBackground.visibility = View.GONE
        contextStripOverlay.visibility = View.VISIBLE
        contextStripIcon.setImageResource(R.drawable.ic_pencil_editor_folder)
        contextStripLine1Text.text = getString(R.string.editor_context_strip_line_1)
        contextStripLine2Text.text = getString(R.string.editor_context_strip_line_2)
        issuePrimaryText.text = selectedItem?.let {
            getString(R.string.editor_status_selected_item, it.displayName)
        } ?: getString(R.string.editor_status_component_format, container.displayName)
        issueActionText.text = getString(R.string.editor_status_back_parent)
    }

    /** 无容器上下文时回到默认编辑器标题。 */
    private fun resetFocusedChrome() {
        headerTitleText.text = getString(R.string.editor_header_default_title)
        headerSubtitleText.text = getString(R.string.editor_header_default_subtitle)
        projectStripBackground.visibility = View.VISIBLE
        contextStripOverlay.visibility = View.GONE
        issuePrimaryText.text = getString(R.string.editor_issue_missing_request)
        issueActionText.text = getString(R.string.editor_fix_now)
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
        renderLayerRows(latestLayerItems)
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
        applyTabVisualState(projectTab, active = true)
        applyTabVisualState(layerTab, active = false)
        applyTabVisualState(positionTab, active = false)
        applyTabVisualState(globalTab, active = false)
        applyTabVisualState(animationTab, active = false)
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
            val topInset = max(statusBarTopInset, cutoutTopInset)

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
    private companion object {
        const val MENU_ADD_RECTANGLE = 1001
        const val MENU_ADD_CIRCLE = 1002

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
