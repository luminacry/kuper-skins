package com.example.klwpdemo.runtime

/** 运行时视口信息，用于把文档映射到当前画布尺寸。 */
data class RuntimeViewport(
    val width: Int,
    val height: Int
) {
    /** 只有拿到有效宽高后才允许渲染。 */
    val isReady: Boolean
        get() = width > 0 && height > 0
}

/** 最近一次触摸采样。 */
data class TouchSample(
    val x: Float,
    val y: Float
)

/** 电池状态采样，用于动态文本或图标展示。 */
data class BatteryStatus(
    val levelPercent: Int,
    val isCharging: Boolean
)

/** 单帧渲染时需要的动态上下文。 */
data class RuntimeFrameState(
    val uptimeMillis: Long,
    val wallClockMillis: Long,
    val xOffset: Float,
    val touchSample: TouchSample? = null,
    val rippleProgress: Float = -1f,
    val batteryStatus: BatteryStatus = BatteryStatus(
        levelPercent = 100,
        isCharging = false
    )
)

/** 编译后的运行时场景根节点。 */
data class RuntimeScene(
    val background: BackgroundFill,
    val root: GroupNode
)

/** 运行时背景填充定义。 */
sealed interface BackgroundFill {
    /** 纯色背景。 */
    data class Solid(
        val color: Int
    ) : BackgroundFill

    /** 线性渐变背景。 */
    data class LinearGradient(
        val colors: IntArray
    ) : BackgroundFill
}

/** 运行时节点变换信息。 */
data class NodeTransform(
    val translationX: Float = 0f,
    val translationY: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f
)

/** 所有运行时节点的公共协议。 */
sealed interface SceneNode {
    val id: String
    val visible: Boolean
    val transform: NodeTransform
}

/** 组合节点。 */
data class GroupNode(
    override val id: String,
    val children: List<SceneNode>,
    override val visible: Boolean = true,
    override val transform: NodeTransform = NodeTransform()
) : SceneNode

/** 节点边界，并补充便捷几何属性。 */
data class NodeBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    /** 右边界坐标。 */
    val right: Float
        get() = left + width

    /** 下边界坐标。 */
    val bottom: Float
        get() = top + height

    /** 中心点 X 坐标。 */
    val centerX: Float
        get() = left + width * 0.5f

    /** 中心点 Y 坐标。 */
    val centerY: Float
        get() = top + height * 0.5f
}

/** 运行时形状类型。 */
enum class ShapeType {
    RECTANGLE,
    CIRCLE
}

/** 运行时形状样式。 */
data class ShapeStyle(
    val fillColor: Int? = null,
    val strokeColor: Int? = null,
    val strokeWidth: Float = 0f,
    val cornerRadius: Float = 0f
)

/** 形状节点。 */
data class ShapeNode(
    override val id: String,
    val shapeType: ShapeType,
    val bounds: NodeBounds,
    val style: ShapeStyle,
    override val visible: Boolean = true,
    override val transform: NodeTransform = NodeTransform()
) : SceneNode

/** 图片节点。 */
data class ImageNode(
    override val id: String,
    val drawableResId: Int,
    val bounds: NodeBounds,
    override val visible: Boolean = true,
    override val transform: NodeTransform = NodeTransform()
) : SceneNode

/** 文本对齐。 */
enum class TextAlign {
    LEFT,
    CENTER,
    RIGHT
}

/** 文本节点。 */
data class TextNode(
    override val id: String,
    val text: String,
    val x: Float,
    val baselineY: Float,
    val textSize: Float,
    val color: Int,
    val isBold: Boolean = false,
    val align: TextAlign = TextAlign.LEFT,
    override val visible: Boolean = true,
    override val transform: NodeTransform = NodeTransform()
) : SceneNode
