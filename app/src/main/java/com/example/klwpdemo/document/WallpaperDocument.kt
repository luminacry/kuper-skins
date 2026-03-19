package com.example.klwpdemo.document

/** 编辑器与运行时共用的壁纸文档根节点。 */
data class WallpaperDocument(
    val background: BackgroundDocument,
    val layers: List<LayerDocument>
)

/** 壁纸背景的文档表达。 */
sealed interface BackgroundDocument {
    /** 纯色背景。 */
    data class Solid(
        val color: Int
    ) : BackgroundDocument

    /** 线性渐变背景。 */
    data class LinearGradient(
        val colors: IntArray
    ) : BackgroundDocument
}

/** 图层局部变换信息。 */
data class LayerTransformDocument(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f
)

/** 所有图层文档节点的公共字段。 */
sealed interface LayerDocument {
    val id: String
    val visible: Boolean
    val transform: LayerTransformDocument
}

/** 组合图层，负责承载子图层并叠加父级变换。 */
data class GroupLayerDocument(
    override val id: String,
    val children: List<LayerDocument>,
    override val visible: Boolean = true,
    override val transform: LayerTransformDocument = LayerTransformDocument()
) : LayerDocument

/** 基础几何边界，统一使用左上角 + 宽高描述。 */
data class LayerBoundsDocument(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)

/** 当前编辑器支持的基础形状类型。 */
enum class ShapeKind {
    RECTANGLE,
    CIRCLE
}

/** 形状图层的视觉样式。 */
data class ShapeStyleDocument(
    val fillColor: Int? = null,
    val strokeColor: Int? = null,
    val strokeWidth: Float = 0f,
    val cornerRadius: Float = 0f
)

/** 矢量形状图层。 */
data class ShapeLayerDocument(
    override val id: String,
    val shapeKind: ShapeKind,
    val bounds: LayerBoundsDocument,
    val style: ShapeStyleDocument,
    override val visible: Boolean = true,
    override val transform: LayerTransformDocument = LayerTransformDocument()
) : LayerDocument

/** 位图图层。 */
data class ImageLayerDocument(
    override val id: String,
    val drawableResId: Int,
    val bounds: LayerBoundsDocument,
    override val visible: Boolean = true,
    override val transform: LayerTransformDocument = LayerTransformDocument()
) : LayerDocument

/** 文本对齐方式。 */
enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}

/** 文本图层。 */
data class TextLayerDocument(
    override val id: String,
    val text: String,
    val x: Float,
    val baselineY: Float,
    val textSize: Float,
    val color: Int,
    val isBold: Boolean = false,
    val align: TextAlignment = TextAlignment.LEFT,
    override val visible: Boolean = true,
    override val transform: LayerTransformDocument = LayerTransformDocument()
) : LayerDocument
