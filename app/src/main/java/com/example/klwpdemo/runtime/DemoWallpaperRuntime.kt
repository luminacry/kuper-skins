package com.example.klwpdemo.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.os.BatteryManager
import android.os.SystemClock
import com.example.klwpdemo.compiler.DocumentToRuntimeSceneCompiler
import com.example.klwpdemo.document.DemoWallpaperDocumentFactory
import com.example.klwpdemo.document.GroupLayerDocument
import com.example.klwpdemo.document.ImageLayerDocument
import com.example.klwpdemo.document.LayerDocument
import com.example.klwpdemo.document.LayerBoundsDocument
import com.example.klwpdemo.document.LayerTransformDocument
import com.example.klwpdemo.document.ShapeKind
import com.example.klwpdemo.document.ShapeLayerDocument
import com.example.klwpdemo.document.ShapeStyleDocument
import com.example.klwpdemo.document.TextLayerDocument
import com.example.klwpdemo.document.WallpaperDocument
import com.example.klwpdemo.editor.AddShapeCommand
import com.example.klwpdemo.editor.DeleteLayerCommand
import com.example.klwpdemo.editor.DocumentLayerOps
import com.example.klwpdemo.editor.DocumentRepository
import com.example.klwpdemo.editor.UpdateLayerTransformCommand
import com.example.klwpdemo.editor.UpdateLayerVisibilityCommand
import com.example.klwpdemo.editor.UpdateShapeStyleCommand

/**
 * 编辑器预览与动态壁纸运行共用的运行时。
 *
 * 非编辑模式下它只负责每帧生成文档并渲染；
 * 编辑模式下则额外维护文档仓库、图层编辑命令和撤销重做历史。
 */
class DemoWallpaperRuntime(
    context: Context,
    private val editorMode: Boolean = false
) {
    /** 应用级上下文仅用于读取系统状态，避免泄漏短生命周期对象。 */
    private val appContext = context.applicationContext
    private val compiler = DocumentToRuntimeSceneCompiler()
    private val renderer = RuntimeSceneRenderer(context.resources, context.theme)
    private val documentRepository = DocumentRepository()

    /** 运行时需要维护视口、触摸样本、电量状态以及编辑态快照。 */
    private var viewport = RuntimeViewport(width = 0, height = 0)
    private var xOffset = 0.5f
    private var touchSample: TouchSample? = null
    private var rippleStartMillis = -1L
    private var batteryStatus = BatteryStatus(
        levelPercent = 100,
        isCharging = false
    )
    private var lastBatteryRefreshUptime = -1L
    private var latestDocumentSnapshot: WallpaperDocument? = null
    private val layerTransformOverrides = mutableMapOf<String, LayerTransformDocument>()
    private var generatedShapeSequence = 0L

    /** 更新当前渲染目标的宽高。 */
    fun updateViewport(
        width: Int,
        height: Int
    ) {
        viewport = RuntimeViewport(width = width, height = height)
    }

    /** 同步桌面滚动偏移，用于壁纸视差效果。 */
    fun updateOffset(xOffset: Float) {
        this.xOffset = xOffset.coerceIn(0f, 1f)
    }

    /** 记录触摸位置与起始时间，用于生成涟漪反馈。 */
    fun registerTouch(
        x: Float,
        y: Float,
        uptimeMillis: Long
    ) {
        touchSample = TouchSample(x = x, y = y)
        rippleStartMillis = uptimeMillis
    }

    /** 生成当前帧文档、编译场景并绘制到 Canvas。 */
    fun render(
        canvas: Canvas,
        uptimeMillis: Long,
        wallClockMillis: Long
    ) {
        if (!viewport.isReady) {
            return
        }

        refreshBatteryStatus(uptimeMillis)
        val rippleProgress = resolveRippleProgress(uptimeMillis)
        val frameState = RuntimeFrameState(
            uptimeMillis = uptimeMillis,
            wallClockMillis = wallClockMillis,
            xOffset = xOffset,
            touchSample = touchSample,
            rippleProgress = rippleProgress,
            batteryStatus = batteryStatus
        )
        val document = if (editorMode) {
            resolveEditorDocument(frameState)
        } else {
            val baseDocument = DemoWallpaperDocumentFactory.createDocument(
                viewport = viewport,
                frameState = frameState
            )
            applyTransformOverrides(baseDocument)
        }
        latestDocumentSnapshot = document
        val scene = compiler.compile(document)
        renderer.render(scene, canvas, viewport)
    }

    /** 供外部页面读取最近一次文档快照。 */
    fun snapshotDocument(): WallpaperDocument? {
        return latestDocumentSnapshot ?: documentRepository.snapshot()
    }

    /** 编辑模式下新增一个默认形状图层。 */
    fun addShape(shapeKind: ShapeKind): String? {
        if (!editorMode || !viewport.isReady) {
            return null
        }
        initializeEditorDocumentIfNeeded(
            uptimeMillis = SystemClock.uptimeMillis(),
            wallClockMillis = System.currentTimeMillis()
        )
        val currentDocument = documentRepository.snapshot() ?: return null
        val layerId = createShapeId(shapeKind)
        val shapeCount = countShapeLayers(currentDocument.layers)
        val shape = createDefaultShapeLayer(
            layerId = layerId,
            shapeKind = shapeKind,
            shapeIndex = shapeCount
        )
        val next = documentRepository.execute(AddShapeCommand(shape)) ?: return null
        latestDocumentSnapshot = next
        return layerId
    }

    /** 编辑模式下删除指定图层。 */
    fun deleteLayer(layerId: String): Boolean {
        if (!editorMode) {
            return false
        }
        val current = documentRepository.snapshot() ?: return false
        if (!DocumentLayerOps.hasLayer(current, layerId)) {
            return false
        }
        val next = documentRepository.execute(DeleteLayerCommand(layerId)) ?: return false
        latestDocumentSnapshot = next
        layerTransformOverrides.remove(layerId)
        return true
    }

    /** 编辑模式下更新图层变换。 */
    fun updateLayerTransform(
        layerId: String,
        transform: LayerTransformDocument
    ): Boolean {
        if (!editorMode) {
            return false
        }
        val next = documentRepository.execute(
            UpdateLayerTransformCommand(layerId, transform)
        ) ?: return false
        latestDocumentSnapshot = next
        return true
    }

    /** 编辑模式下更新图层显隐状态。 */
    fun updateLayerVisibility(
        layerId: String,
        visible: Boolean
    ): Boolean {
        if (!editorMode) {
            return false
        }
        val next = documentRepository.execute(
            UpdateLayerVisibilityCommand(layerId, visible)
        ) ?: return false
        latestDocumentSnapshot = next
        return true
    }

    /** 编辑模式下仅开放形状图层的样式更新。 */
    fun updateShapeStyle(
        layerId: String,
        style: ShapeStyleDocument
    ): Boolean {
        if (!editorMode) {
            return false
        }
        val next = documentRepository.execute(
            UpdateShapeStyleCommand(layerId, style)
        ) ?: return false
        latestDocumentSnapshot = next
        return true
    }

    /** 暴露撤销能力给上层页面。 */
    fun canUndoEdit(): Boolean = editorMode && documentRepository.canUndo()

    /** 暴露重做能力给上层页面。 */
    fun canRedoEdit(): Boolean = editorMode && documentRepository.canRedo()

    /** 撤销最近一次编辑命令。 */
    fun undoEdit(): WallpaperDocument? {
        if (!editorMode) {
            return null
        }
        val next = documentRepository.undo()
        latestDocumentSnapshot = next
        return next
    }

    /** 重做最近一次被撤销的编辑命令。 */
    fun redoEdit(): WallpaperDocument? {
        if (!editorMode) {
            return null
        }
        val next = documentRepository.redo()
        latestDocumentSnapshot = next
        return next
    }

    /** 非编辑模式使用临时覆盖；编辑模式直接回写文档。 */
    fun setLayerTransformOverride(
        layerId: String,
        transform: LayerTransformDocument?
    ) {
        if (editorMode) {
            if (transform == null) {
                return
            }
            updateLayerTransform(layerId, transform)
            return
        }

        if (transform == null) {
            layerTransformOverrides.remove(layerId)
        } else {
            layerTransformOverrides[layerId] = transform
        }
    }

    /** 编辑模式下优先使用文档仓库里的当前文档。 */
    private fun resolveEditorDocument(
        frameState: RuntimeFrameState
    ): WallpaperDocument {
        initializeEditorDocumentIfNeeded(
            uptimeMillis = frameState.uptimeMillis,
            wallClockMillis = frameState.wallClockMillis
        )
        return documentRepository.snapshot()
            ?: DemoWallpaperDocumentFactory.createDocument(viewport, frameState)
    }

    /** 首次进入编辑模式时，用当前运行时状态初始化一份可编辑文档。 */
    private fun initializeEditorDocumentIfNeeded(
        uptimeMillis: Long,
        wallClockMillis: Long
    ) {
        if (!editorMode || documentRepository.hasDocument() || !viewport.isReady) {
            return
        }
        val document = DemoWallpaperDocumentFactory.createDocument(
            viewport = viewport,
            frameState = RuntimeFrameState(
                uptimeMillis = uptimeMillis,
                wallClockMillis = wallClockMillis,
                xOffset = xOffset,
                touchSample = touchSample,
                rippleProgress = resolveRippleProgress(uptimeMillis),
                batteryStatus = batteryStatus
            )
        )
        documentRepository.setDocument(document)
        latestDocumentSnapshot = document
    }

    /** 非编辑模式下，把临时覆盖的变换重新套回生成文档。 */
    private fun applyTransformOverrides(document: WallpaperDocument): WallpaperDocument {
        if (layerTransformOverrides.isEmpty()) {
            return document
        }

        return document.copy(
            layers = document.layers.map(::applyLayerOverride)
        )
    }

    /** 递归把单个图层及其子图层的变换覆盖到最新文档上。 */
    private fun applyLayerOverride(layer: LayerDocument): LayerDocument {
        val override = layerTransformOverrides[layer.id]
        return when (layer) {
            is GroupLayerDocument -> {
                layer.copy(
                    transform = override ?: layer.transform,
                    children = layer.children.map(::applyLayerOverride)
                )
            }

            is ShapeLayerDocument -> layer.copy(transform = override ?: layer.transform)
            is ImageLayerDocument -> layer.copy(transform = override ?: layer.transform)
            is TextLayerDocument -> layer.copy(transform = override ?: layer.transform)
        }
    }

    /** 生成稳定递增的用户图形图层 id。 */
    private fun createShapeId(shapeKind: ShapeKind): String {
        generatedShapeSequence += 1
        val kind = when (shapeKind) {
            ShapeKind.RECTANGLE -> "rect"
            ShapeKind.CIRCLE -> "circle"
        }
        return "user-$kind-$generatedShapeSequence"
    }

    /** 为新增图形生成一套可立即拖拽编辑的默认参数。 */
    private fun createDefaultShapeLayer(
        layerId: String,
        shapeKind: ShapeKind,
        shapeIndex: Int
    ): ShapeLayerDocument {
        val baseSize = viewport.width.coerceAtMost(viewport.height) * 0.22f
        val width = if (shapeKind == ShapeKind.CIRCLE) baseSize else baseSize * 1.25f
        val height = baseSize
        val stackOffset = (shapeIndex % 5) * 20f
        val left = ((viewport.width - width) * 0.5f + stackOffset)
            .coerceIn(0f, (viewport.width - width).coerceAtLeast(0f))
        val top = ((viewport.height - height) * 0.5f + stackOffset)
            .coerceIn(0f, (viewport.height - height).coerceAtLeast(0f))
        val fillColor = if (shapeKind == ShapeKind.CIRCLE) {
            0x66FFB86CL.toInt()
        } else {
            0x663A86FF.toInt()
        }
        val strokeColor = 0xCCFFFFFF.toInt()

        return ShapeLayerDocument(
            id = layerId,
            shapeKind = shapeKind,
            bounds = LayerBoundsDocument(
                left = left,
                top = top,
                width = width,
                height = height
            ),
            style = ShapeStyleDocument(
                fillColor = fillColor,
                strokeColor = strokeColor,
                strokeWidth = 3f,
                cornerRadius = if (shapeKind == ShapeKind.RECTANGLE) 28f else 0f
            )
        )
    }

    /** 统计当前文档里已有的形状数量，用于给新增图层错位摆放。 */
    private fun countShapeLayers(layers: List<LayerDocument>): Int {
        var count = 0
        layers.forEach { layer ->
            when (layer) {
                is ShapeLayerDocument -> count += 1
                is GroupLayerDocument -> count += countShapeLayers(layer.children)
                is ImageLayerDocument, is TextLayerDocument -> Unit
            }
        }
        return count
    }

    /** 定时读取系统电量状态，避免每帧都查询广播。 */
    private fun refreshBatteryStatus(uptimeMillis: Long) {
        if (
            lastBatteryRefreshUptime > 0L &&
            uptimeMillis - lastBatteryRefreshUptime < batteryRefreshIntervalMillis
        ) {
            return
        }

        val intent = appContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) {
            return
        }

        val status = intent.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        )
        val isCharging =
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        batteryStatus = BatteryStatus(
            levelPercent = ((level * 100f) / scale).toInt().coerceIn(0, 100),
            isCharging = isCharging
        )
        lastBatteryRefreshUptime = uptimeMillis
    }

    /** 根据触摸开始时间计算当前涟漪进度。 */
    private fun resolveRippleProgress(uptimeMillis: Long): Float {
        if (rippleStartMillis < 0L) {
            return -1f
        }

        val progress = (uptimeMillis - rippleStartMillis) / rippleDurationMillis.toFloat()
        if (progress > 1f) {
            touchSample = null
            return -1f
        }
        return progress
    }

    /** 统一维护运行时动画和系统状态采样的时间常量。 */
    private companion object {
        const val rippleDurationMillis = 650L
        const val batteryRefreshIntervalMillis = 5_000L
    }
}
