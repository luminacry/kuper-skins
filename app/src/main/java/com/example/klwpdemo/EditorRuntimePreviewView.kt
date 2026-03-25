package com.example.klwpdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.klwpdemo.document.GroupLayerDocument
import com.example.klwpdemo.document.LayerBoundsDocument
import com.example.klwpdemo.document.LayerDocument
import com.example.klwpdemo.document.LayerTransformDocument
import com.example.klwpdemo.document.ShapeKind
import com.example.klwpdemo.document.ShapeLayerDocument
import com.example.klwpdemo.document.WallpaperDocument
import com.example.klwpdemo.editor.DocumentLayerOps
import com.example.klwpdemo.runtime.DemoWallpaperRuntime
import kotlin.math.hypot
import kotlin.math.max

/**
 * 编辑器中央预览区。
 *
 * 它同时承担三件事：驱动运行时绘制、把文档快照回传给 Activity、
 * 以及处理图层的点击选中、拖拽与缩放手势。
 */
class EditorRuntimePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 预览区使用独立运行时，避免和壁纸服务互相影响。 */
    private val runtime = DemoWallpaperRuntime(context, editorMode = true)
    private var isRendering = false
    private var lastSnapshotDispatchUptime = -1L
    private var selectedLayerId: String? = null
    private var gestureMode = GestureMode.NONE
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureStartDistance = 1f
    private var initialTransform = LayerTransformDocument()
    private var initialParentScaleX = 1f
    private var initialParentScaleY = 1f

    private val selectionRect = RectF()
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 253, 217, 228)
        style = Paint.Style.FILL
    }
    private val selectionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C4A5E")
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val handleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8C4A5E")
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    var onDocumentSnapshot: ((WallpaperDocument) -> Unit)? = null
    var onSelectionChanged: ((String?) -> Unit)? = null

    /** 挂载到窗口后才开始按帧刷新。 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRenderingState()
    }

    /** 脱离窗口后立即停止重绘循环。 */
    override fun onDetachedFromWindow() {
        isRendering = false
        super.onDetachedFromWindow()
    }

    /** 仅在 View 真实可见时才维持动画刷新。 */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateRenderingState()
    }

    /** 预览区尺寸变化后，把新的视口同步给运行时。 */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        runtime.updateViewport(w, h)
    }

    /** 每一帧都由运行时出图，并在其上叠加选中框。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val uptime = SystemClock.uptimeMillis()
        runtime.render(
            canvas = canvas,
            uptimeMillis = uptime,
            wallClockMillis = System.currentTimeMillis()
        )
        val snapshot = runtime.snapshotDocument()
        if (snapshot != null) {
            if (selectedLayerId != null && DocumentLayerOps.findLayer(snapshot, selectedLayerId!!) == null) {
                updateSelectedLayer(null, notify = true)
            }
            dispatchSnapshot(snapshot, uptimeMillis = uptime)
            drawSelectionOverlay(canvas, snapshot)
        }
        if (isRendering) {
            postInvalidateOnAnimation()
        }
    }

    /** 处理点击选中、拖拽和平面缩放手势。 */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width <= 0 || height <= 0 || !isEnabled) {
            return false
        }

        val document = runtime.snapshotDocument() ?: return false
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val selectedHit = resolveSelectedShapeHit(document)
                if (selectedHit != null) {
                    val hitHandle = resolveResizeHandle(selectedHit.bounds, x, y)
                    if (hitHandle != null) {
                        beginResize(
                            hit = selectedHit,
                            handle = hitHandle,
                            downX = x,
                            downY = y
                        )
                        return true
                    }
                    if (selectedHit.bounds.contains(x, y)) {
                        beginDrag(selectedHit, downX = x, downY = y)
                        return true
                    }
                }

                val hit = hitTestTopmostShape(document, x, y)
                if (hit != null) {
                    updateSelectedLayer(hit.id, notify = true)
                    beginDrag(hit, downX = x, downY = y)
                    return true
                }

                updateSelectedLayer(null, notify = true)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val layerId = selectedLayerId ?: return false
                when (gestureMode) {
                    GestureMode.DRAG -> {
                        val dx = x - gestureStartX
                        val dy = y - gestureStartY
                        val updated = initialTransform.copy(
                            translationX = initialTransform.translationX + dx / initialParentScaleX,
                            translationY = initialTransform.translationY + dy / initialParentScaleY
                        )
                        runtime.updateLayerTransform(layerId, updated)
                        dispatchCurrentSnapshot()
                        invalidate()
                        return true
                    }

                    GestureMode.RESIZE -> {
                        val selectedHit = resolveSelectedShapeHit(runtime.snapshotDocument() ?: document)
                            ?: return true
                        val centerX = selectedHit.bounds.centerX()
                        val centerY = selectedHit.bounds.centerY()
                        val distance = max(1f, hypot(x - centerX, y - centerY))
                        val factor = (distance / gestureStartDistance).coerceIn(0.2f, 4.0f)
                        val updated = initialTransform.copy(
                            scaleX = (initialTransform.scaleX * factor).coerceIn(0.2f, 4f),
                            scaleY = (initialTransform.scaleY * factor).coerceIn(0.2f, 4f)
                        )
                        runtime.updateLayerTransform(layerId, updated)
                        dispatchCurrentSnapshot()
                        invalidate()
                        return true
                    }

                    GestureMode.NONE -> return false
                }
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (gestureMode != GestureMode.NONE) {
                    gestureMode = GestureMode.NONE
                    dispatchCurrentSnapshot(force = true)
                    performClick()
                    return true
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** 自定义触摸结束后仍然维持标准点击语义。 */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** 从外部项目仓库装入一份完整文档。 */
    fun loadDocument(document: WallpaperDocument) {
        runtime.setBaseDocument(document)
        updateSelectedLayer(null, notify = true)
        dispatchCurrentSnapshot(force = true)
        invalidate()
    }

    /** 供外部属性面板直接覆盖选中图层的变换值。 */
    fun setLayerTransformOverride(
        layerId: String,
        transform: LayerTransformDocument?
    ) {
        if (transform == null) {
            return
        }
        runtime.updateLayerTransform(layerId, transform)
        dispatchCurrentSnapshot()
        invalidate()
    }

    /** 切换图层显隐，并在必要时刷新选中框。 */
    fun setLayerVisibility(
        layerId: String,
        visible: Boolean
    ): Boolean {
        val updated = runtime.updateLayerVisibility(layerId, visible)
        if (!updated) {
            return false
        }
        if (!visible && selectedLayerId == layerId) {
            // Keep the selection id for editing context, only overlay disappears.
            invalidate()
        }
        dispatchCurrentSnapshot(force = true)
        invalidate()
        return true
    }

    /** 当前只开放形状图层的填充色编辑。 */
    fun updateSelectedShapeFillColor(fillColor: Int): Boolean {
        val selectedId = selectedLayerId ?: return false
        val snapshot = runtime.snapshotDocument() ?: return false
        val shapeLayer = DocumentLayerOps.findLayer(snapshot, selectedId) as? ShapeLayerDocument
            ?: return false
        val updated = runtime.updateShapeStyle(
            selectedId,
            shapeLayer.style.copy(fillColor = fillColor)
        )
        if (!updated) {
            return false
        }
        dispatchCurrentSnapshot(force = true)
        invalidate()
        return true
    }

    /** 新增图形后会自动选中新图层。 */
    fun addShape(
        shapeKind: ShapeKind,
        parentLayerId: String? = null
    ): String? {
        val layerId = runtime.addShape(shapeKind, parentLayerId) ?: return null
        updateSelectedLayer(layerId, notify = true)
        dispatchCurrentSnapshot(force = true)
        invalidate()
        return layerId
    }

    /** 删除指定图层，并同步更新选中状态。 */
    fun deleteLayer(layerId: String): Boolean {
        val deleted = runtime.deleteLayer(layerId)
        if (!deleted) {
            return false
        }
        if (selectedLayerId == layerId) {
            updateSelectedLayer(null, notify = true)
        }
        dispatchCurrentSnapshot(force = true)
        invalidate()
        return true
    }

    /** 删除当前选中的形状图层。 */
    fun deleteSelectedShape(): Boolean {
        val selectedId = selectedLayerId ?: return false
        return deleteLayer(selectedId)
    }

    /** 暴露撤销能力给外部页面。 */
    fun canUndoEdit(): Boolean = runtime.canUndoEdit()

    /** 暴露重做能力给外部页面。 */
    fun canRedoEdit(): Boolean = runtime.canRedoEdit()

    /** 读取当前选中形状的填充色，供属性面板高亮使用。 */
    fun selectedShapeFillColor(): Int? {
        val selectedId = selectedLayerId ?: return null
        val snapshot = runtime.snapshotDocument() ?: return null
        val shapeLayer = DocumentLayerOps.findLayer(snapshot, selectedId) as? ShapeLayerDocument
            ?: return null
        return shapeLayer.style.fillColor
    }

    /** 执行撤销，并把最新快照立即回传给 Activity。 */
    fun undoEdit(): Boolean {
        if (!runtime.canUndoEdit()) {
            return false
        }
        val snapshot = runtime.undoEdit() ?: return false
        if (selectedLayerId != null && DocumentLayerOps.findLayer(snapshot, selectedLayerId!!) == null) {
            updateSelectedLayer(null, notify = true)
        }
        dispatchSnapshot(
            snapshot = snapshot,
            uptimeMillis = SystemClock.uptimeMillis(),
            force = true
        )
        invalidate()
        return true
    }

    /** 执行重做，并把最新快照立即回传给 Activity。 */
    fun redoEdit(): Boolean {
        if (!runtime.canRedoEdit()) {
            return false
        }
        val snapshot = runtime.redoEdit() ?: return false
        if (selectedLayerId != null && DocumentLayerOps.findLayer(snapshot, selectedLayerId!!) == null) {
            updateSelectedLayer(null, notify = true)
        }
        dispatchSnapshot(
            snapshot = snapshot,
            uptimeMillis = SystemClock.uptimeMillis(),
            force = true
        )
        invalidate()
        return true
    }

    /** 接收外部选中变化，并刷新选中框。 */
    fun setSelectedLayer(layerId: String?) {
        updateSelectedLayer(layerId, notify = false)
        invalidate()
    }

    /** 内部统一维护选中图层，并按需向外抛出回调。 */
    private fun updateSelectedLayer(
        layerId: String?,
        notify: Boolean
    ) {
        if (selectedLayerId == layerId) {
            return
        }
        selectedLayerId = layerId
        if (notify) {
            onSelectionChanged?.invoke(layerId)
        }
    }

    /** 节流派发文档快照，避免拖拽过程中把 Activity 刷得过于频繁。 */
    private fun dispatchSnapshot(
        snapshot: WallpaperDocument,
        uptimeMillis: Long,
        force: Boolean = false
    ) {
        if (
            force ||
            lastSnapshotDispatchUptime < 0L ||
            uptimeMillis - lastSnapshotDispatchUptime >= 120L
        ) {
            lastSnapshotDispatchUptime = uptimeMillis
            onDocumentSnapshot?.invoke(snapshot)
        }
    }

    /** 用当前运行时快照重新触发一次外部同步。 */
    private fun dispatchCurrentSnapshot(force: Boolean = false) {
        runtime.snapshotDocument()?.let { snapshot ->
            dispatchSnapshot(
                snapshot = snapshot,
                uptimeMillis = SystemClock.uptimeMillis(),
                force = force
            )
        }
    }

    /** 在选中图层外围绘制可交互的高亮框。 */
    private fun drawSelectionOverlay(
        canvas: Canvas,
        document: WallpaperDocument
    ) {
        val hit = resolveSelectedShapeHit(document) ?: return
        selectionRect.set(hit.bounds)
        canvas.drawRoundRect(selectionRect, dp(8f), dp(8f), selectionFillPaint)
        canvas.drawRoundRect(selectionRect, dp(8f), dp(8f), selectionStrokePaint)
        drawHandles(canvas, selectionRect)
    }

    /** 四角控制点用于提示当前图层可缩放。 */
    private fun drawHandles(
        canvas: Canvas,
        rect: RectF
    ) {
        val radius = dp(6f)
        val points = arrayOf(
            rect.left to rect.top,
            rect.right to rect.top,
            rect.right to rect.bottom,
            rect.left to rect.bottom
        )
        points.forEach { (x, y) ->
            canvas.drawCircle(x, y, radius, handlePaint)
            canvas.drawCircle(x, y, radius, handleStrokePaint)
        }
    }

    /** 开始拖拽前缓存起点和初始变换。 */
    private fun beginDrag(
        hit: ShapeHit,
        downX: Float,
        downY: Float
    ) {
        gestureMode = GestureMode.DRAG
        gestureStartX = downX
        gestureStartY = downY
        initialTransform = hit.localTransform
        initialParentScaleX = if (hit.parentScaleX == 0f) 1f else hit.parentScaleX
        initialParentScaleY = if (hit.parentScaleY == 0f) 1f else hit.parentScaleY
    }

    /** 开始缩放前记录对角锚点和起始距离。 */
    private fun beginResize(
        hit: ShapeHit,
        handle: ResizeHandle,
        downX: Float,
        downY: Float
    ) {
        gestureMode = GestureMode.RESIZE
        gestureStartX = downX
        gestureStartY = downY
        initialTransform = hit.localTransform
        initialParentScaleX = if (hit.parentScaleX == 0f) 1f else hit.parentScaleX
        initialParentScaleY = if (hit.parentScaleY == 0f) 1f else hit.parentScaleY
        val anchorX = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.BOTTOM_LEFT -> hit.bounds.right
            ResizeHandle.TOP_RIGHT, ResizeHandle.BOTTOM_RIGHT -> hit.bounds.left
        }
        val anchorY = when (handle) {
            ResizeHandle.TOP_LEFT, ResizeHandle.TOP_RIGHT -> hit.bounds.bottom
            ResizeHandle.BOTTOM_LEFT, ResizeHandle.BOTTOM_RIGHT -> hit.bounds.top
        }
        gestureStartDistance = max(1f, hypot(downX - anchorX, downY - anchorY))
    }

    /** 从当前文档中解析选中图层的实时命中区域。 */
    private fun resolveSelectedShapeHit(document: WallpaperDocument): ShapeHit? {
        val selectedId = selectedLayerId ?: return null
        return collectShapeHits(document).firstOrNull { it.id == selectedId }
    }

    /** 从上往下命中最上层的可交互形状。 */
    private fun hitTestTopmostShape(
        document: WallpaperDocument,
        x: Float,
        y: Float
    ): ShapeHit? {
        val hits = collectShapeHits(document)
        for (index in hits.indices.reversed()) {
            if (hits[index].bounds.contains(x, y)) {
                return hits[index]
            }
        }
        return null
    }

    /** 收集当前文档中所有可交互的形状命中区。 */
    private fun collectShapeHits(document: WallpaperDocument): List<ShapeHit> {
        val hits = ArrayList<ShapeHit>()
        collectShapeHits(
            layers = document.layers,
            parentTransform = AggregatedTransform(),
            destination = hits
        )
        return hits
    }

    /** 递归展开组合层，并把父级缩放叠加到子图层上。 */
    private fun collectShapeHits(
        layers: List<LayerDocument>,
        parentTransform: AggregatedTransform,
        destination: MutableList<ShapeHit>
    ) {
        layers.forEach { layer ->
            if (!layer.visible) {
                return@forEach
            }
            when (layer) {
                is ShapeLayerDocument -> {
                    val aggregate = parentTransform.combine(layer.transform)
                    destination += ShapeHit(
                        id = layer.id,
                        bounds = aggregate.applyToBounds(layer.bounds),
                        localTransform = layer.transform,
                        parentScaleX = parentTransform.scaleX,
                        parentScaleY = parentTransform.scaleY
                    )
                }

                is GroupLayerDocument -> {
                    val aggregate = parentTransform.combine(layer.transform)
                    collectShapeHits(layer.children, aggregate, destination)
                }

                else -> Unit
            }
        }
    }

    /** 判断手指是否按在四个缩放控制点附近。 */
    private fun resolveResizeHandle(
        bounds: RectF,
        x: Float,
        y: Float
    ): ResizeHandle? {
        val radius = dp(18f)
        val handles = mapOf(
            ResizeHandle.TOP_LEFT to Pair(bounds.left, bounds.top),
            ResizeHandle.TOP_RIGHT to Pair(bounds.right, bounds.top),
            ResizeHandle.BOTTOM_RIGHT to Pair(bounds.right, bounds.bottom),
            ResizeHandle.BOTTOM_LEFT to Pair(bounds.left, bounds.bottom)
        )
        handles.forEach { (handle, point) ->
            if (hypot(x - point.first, y - point.second) <= radius) {
                return handle
            }
        }
        return null
    }

    /** 按当前屏幕密度把 dp 换算成像素。 */
    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }

    /** 只在窗口可见时保留逐帧重绘。 */
    private fun updateRenderingState() {
        isRendering = isAttachedToWindow && windowVisibility == VISIBLE
        if (isRendering) {
            postInvalidateOnAnimation()
        }
    }

    /** 当前手势只区分空闲、拖拽和缩放三种模式。 */
    private enum class GestureMode {
        NONE,
        DRAG,
        RESIZE
    }

    /** 四个缩放控制点的枚举。 */
    private enum class ResizeHandle {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT
    }

    private data class ShapeHit(
        val id: String,
        val bounds: RectF,
        val localTransform: LayerTransformDocument,
        val parentScaleX: Float,
        val parentScaleY: Float
    )

    /** 累积父级平移和缩放，用于把局部边界换算到屏幕坐标。 */
    private data class AggregatedTransform(
        val translationX: Float = 0f,
        val translationY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f
    ) {
        /** 把当前节点的局部变换叠加到父级累计结果上。 */
        fun combine(local: LayerTransformDocument): AggregatedTransform {
            return AggregatedTransform(
                translationX = translationX + local.translationX * scaleX,
                translationY = translationY + local.translationY * scaleY,
                scaleX = scaleX * local.scaleX,
                scaleY = scaleY * local.scaleY
            )
        }

        /** 把文档里的局部边界换算成预览区上的真实矩形。 */
        fun applyToBounds(bounds: LayerBoundsDocument): RectF {
            val left = translationX + bounds.left * scaleX
            val top = translationY + bounds.top * scaleY
            val width = bounds.width * scaleX
            val height = bounds.height * scaleY
            return RectF(
                left,
                top,
                left + width,
                top + height
            )
        }
    }
}
