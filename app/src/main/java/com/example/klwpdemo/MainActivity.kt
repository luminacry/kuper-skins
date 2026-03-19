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

    /** 右侧属性面板与底部页签相关控件。 */
    private lateinit var fillColorSection: View
    private lateinit var fillColorRoseButton: ImageButton
    private lateinit var fillColorBlueButton: ImageButton
    private lateinit var fillColorMintButton: ImageButton
    private lateinit var layersTab: TextView
    private lateinit var actionTab: TextView
    private lateinit var dataTab: TextView
    private lateinit var globalTab: TextView
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

    /** 当前编辑上下文围绕选中图层与编辑状态变化。 */
    private var selectedLayerId: String? = null
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
        layersTab = findViewById(R.id.tab_layers)
        actionTab = findViewById(R.id.tab_action)
        dataTab = findViewById(R.id.tab_data)
        globalTab = findViewById(R.id.tab_global)
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
        layersTab.setOnClickListener { closePropertyPanel() }
        actionTab.setOnClickListener { openPropertyPanelForSelection() }
        dataTab.setOnClickListener {
            Toast.makeText(this, R.string.editor_tab_coming_soon, Toast.LENGTH_SHORT).show()
        }
        globalTab.setOnClickListener {
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

        if (!hasAutoSelectedLayer && flattened.isNotEmpty()) {
            val defaultSelection = flattened.firstOrNull { it.id == DEFAULT_SELECTED_LAYER_ID }
                ?: flattened.firstOrNull { it.isShapeLayer }
            if (defaultSelection != null) {
                hasAutoSelectedLayer = true
                selectedLayerId = defaultSelection.id
                syncPreviewSelection(defaultSelection.id)
            }
        }

        updateHistoryButtons()

        // 用轻量签名判断图层数据是否真正变化，避免重复重建整个列表。
        val signature = flattened.joinToString(separator = "|") { layer ->
            "${layer.id}:${layer.depth}:${layer.visible}:${layer.transform.translationX}:" +
                "${layer.transform.translationY}:${layer.transform.scaleX}:${layer.transform.scaleY}:" +
                "${layer.transform.alpha}:${layer.description}"
        }

        val visibleCount = flattened.count { it.visible }
        layerCountText.text = getString(
            R.string.editor_layers_subtitle_dynamic,
            visibleCount,
            flattened.size
        )

        if (signature != latestLayerSignature) {
            latestLayerSignature = signature
            renderLayerRows(flattened)
            if (selectedLayerId != null && editorUiState == EditorUiState.EDITING) {
                syncPropertyPanelFromSelection()
            }
        } else if (selectedLayerId != null && editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        }
    }

    /** 根据拍平后的图层数据渲染底部列表，并维护选中和显隐状态。 */
    private fun renderLayerRows(items: List<LayerUiItem>) {
        layerListContainer.removeAllViews()

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
            textLayoutParams.marginStart = dp(8 + item.depth * 12)
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
                selectLayer(item.id)
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
            renderLayerRows(latestLayerItems)
            return
        }
        selectedLayerId = layerId
        if (editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        } else {
            applyEditorUiState(EditorUiState.SELECTED)
        }
        renderLayerRows(latestLayerItems)
    }

    /** 处理底部列表点击选中。 */
    private fun selectLayer(layerId: String) {
        selectedLayerId = layerId
        syncPreviewSelection(layerId)
        if (editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        } else {
            applyEditorUiState(EditorUiState.SELECTED)
        }
        renderLayerRows(latestLayerItems)
    }

    /** 仅在存在选中图层时打开属性面板。 */
    private fun openPropertyPanelForSelection() {
        if (selectedLayerId == null) {
            Toast.makeText(this, R.string.editor_property_none, Toast.LENGTH_SHORT).show()
            return
        }
        applyEditorUiState(EditorUiState.EDITING)
        syncPropertyPanelFromSelection()
    }

    /** 关闭属性面板后，根据选中状态切回合适的编辑态。 */
    private fun closePropertyPanel() {
        if (selectedLayerId == null) {
            applyEditorUiState(EditorUiState.IDLE)
        } else {
            applyEditorUiState(EditorUiState.SELECTED)
        }
        renderLayerRows(latestLayerItems)
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
        val layerId = runtimePreview.addShape(kind) ?: run {
            Toast.makeText(this, R.string.editor_add_shape_failed, Toast.LENGTH_SHORT).show()
            return false
        }
        selectLayer(layerId)
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

    /** 根据是否正在编辑属性，切换底部页签、右侧属性面板和工具栏。 */
    private fun applyEditorUiState(state: EditorUiState) {
        editorUiState = state
        val editing = state == EditorUiState.EDITING
        editorToolsRail.visibility = if (editing) View.GONE else View.VISIBLE
        editorPropertyPanel.visibility = if (editing) View.VISIBLE else View.GONE
        applyTabVisualState(layersTab, active = !editing)
        applyTabVisualState(actionTab, active = editing)
        applyTabVisualState(dataTab, active = false)
        applyTabVisualState(globalTab, active = false)
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
        if (selectedLayerId != null && editorUiState == EditorUiState.EDITING) {
            syncPropertyPanelFromSelection()
        }
    }

    /** 把树状图层拍平成列表，方便底部清单逐行渲染。 */
    private fun flattenLayers(layers: List<LayerDocument>): List<LayerUiItem> {
        val result = mutableListOf<LayerUiItem>()
        flattenInto(layers, depth = 0, destination = result)
        return result
    }

    /** 递归遍历图层树，并保留层级深度用于缩进。 */
    private fun flattenInto(
        layers: List<LayerDocument>,
        depth: Int,
        destination: MutableList<LayerUiItem>
    ) {
        layers.forEach { layer ->
            destination += layer.toLayerUiItem(depth)
            if (layer is GroupLayerDocument) {
                flattenInto(layer.children, depth + 1, destination)
            }
        }
    }

    /** 把运行时图层转换成底部列表需要的展示模型。 */
    private fun LayerDocument.toLayerUiItem(depth: Int): LayerUiItem {
        return LayerUiItem(
            id = id,
            displayName = localizedLayerDisplayName(id),
            description = describeLayer(this),
            depth = depth,
            visible = visible,
            transform = transform,
            isShapeLayer = this is ShapeLayerDocument,
            typeIconRes = resolveTypeIcon(this)
        )
    }

    /** 为底部列表生成简短描述。 */
    private fun describeLayer(layer: LayerDocument): String {
        return when (layer) {
            is GroupLayerDocument -> {
                val summary = layer.children
                    .take(4)
                    .joinToString(separator = ", ") { child -> layerKindLabel(child) }
                if (layer.children.size > 4) "$summary…" else summary
            }

            is ShapeLayerDocument -> when (layer.shapeKind) {
                ShapeKind.RECTANGLE -> "矩形"
                ShapeKind.CIRCLE -> "圆形"
            }

            is ImageLayerDocument -> "图片"
            is TextLayerDocument -> {
                val compactText = layer.text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
                if (compactText.length > 12) compactText.take(12) + "…" else compactText
            }
        }
    }

    /** 把子图层类型翻译成短标签，供组合层摘要复用。 */
    private fun layerKindLabel(layer: LayerDocument): String {
        return when (layer) {
            is GroupLayerDocument -> "编组"
            is ShapeLayerDocument -> when (layer.shapeKind) {
                ShapeKind.RECTANGLE -> "矩形"
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
            "rect" -> "矩形"
            "circle" -> "圆形"
            "left" -> "左侧"
            "right" -> "右侧"
            "haze" -> "雾层"
            "outline" -> "轮廓"
            "card" -> "卡片"
            "orb" -> "光球"
            "glow" -> "光晕"
            "core" -> "核心"
            "cluster" -> "编组"
            "info" -> "信息"
            "panel" -> "面板"
            "title" -> "标题"
            "clock" -> "时间"
            "touch" -> "触摸"
            "ripple" -> "涟漪"
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
        val displayName: String,
        val description: String,
        val depth: Int,
        val visible: Boolean,
        val transform: LayerTransformDocument,
        val isShapeLayer: Boolean,
        val typeIconRes: Int
    )

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
        const val DEFAULT_SELECTED_LAYER_ID = "outline-card"

        val localizedLayerNameOverrides = mapOf(
            "left-haze" to "应用页",
            "outline-card" to "应用页",
            "orb-cluster" to "应用页",
            "orb-glow" to "光球光晕",
            "orb-main" to "焦点圆",
            "orb-core" to "中心点",
            "right-haze" to "主屏壁纸",
            "info-panel" to "主屏壁纸",
            "info-panel-bg" to "面板背景",
            "info-title" to "标题文本",
            "info-clock" to "时间文本",
            "touch-ripple" to "触摸涟漪"
        )
    }
}
