package com.example.klwpdemo.runtime

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 运行时场景渲染器。
 *
 * 输入是编译后的轻量场景树，输出是直接绘制到 Canvas 的结果，
 * 因此这里保持尽量简单的绘制职责，不混入编辑逻辑。
 */
class RuntimeSceneRenderer(
    private val resources: Resources,
    private val theme: Resources.Theme
) {
    /** 常用 Paint 与缓存对象统一复用，减少每帧分配。 */
    private val backgroundPaint = Paint()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
    }
    private val rect = RectF()
    private val drawableCache = mutableMapOf<Int, Drawable>()

    private var cachedBackgroundShader: Shader? = null
    private var cachedBackgroundWidth = -1
    private var cachedBackgroundHeight = -1
    private var cachedBackgroundKey = ""

    /** 渲染一整棵场景树。 */
    fun render(
        scene: RuntimeScene,
        canvas: Canvas,
        viewport: RuntimeViewport
    ) {
        drawBackground(scene.background, canvas, viewport)
        drawNode(scene.root, canvas, inheritedAlpha = 1f)
    }

    /** 先绘制背景层，作为整帧的底图。 */
    private fun drawBackground(
        background: BackgroundFill,
        canvas: Canvas,
        viewport: RuntimeViewport
    ) {
        when (background) {
            is BackgroundFill.Solid -> {
                backgroundPaint.shader = null
                backgroundPaint.color = background.color
            }

            is BackgroundFill.LinearGradient -> {
                backgroundPaint.shader = resolveBackgroundShader(
                    viewport = viewport,
                    colors = background.colors
                )
            }
        }

        canvas.drawRect(0f, 0f, viewport.width.toFloat(), viewport.height.toFloat(), backgroundPaint)
    }

    /** 渐变背景按视口尺寸缓存 shader，避免重复创建。 */
    private fun resolveBackgroundShader(
        viewport: RuntimeViewport,
        colors: IntArray
    ): Shader {
        val cacheKey = colors.joinToString(separator = ",")
        if (
            cachedBackgroundShader != null &&
            cachedBackgroundWidth == viewport.width &&
            cachedBackgroundHeight == viewport.height &&
            cachedBackgroundKey == cacheKey
        ) {
            return cachedBackgroundShader!!
        }

        return LinearGradient(
            0f,
            0f,
            viewport.width.toFloat(),
            viewport.height.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP
        ).also { shader ->
            cachedBackgroundShader = shader
            cachedBackgroundWidth = viewport.width
            cachedBackgroundHeight = viewport.height
            cachedBackgroundKey = cacheKey
        }
    }

    /** 递归绘制场景节点，并把父级透明度与变换继续传下去。 */
    private fun drawNode(
        node: SceneNode,
        canvas: Canvas,
        inheritedAlpha: Float
    ) {
        if (!node.visible) {
            return
        }

        val nodeAlpha = (inheritedAlpha * node.transform.alpha).coerceIn(0f, 1f)
        canvas.save()
        applyTransform(canvas, node.transform)

        when (node) {
            is GroupNode -> node.children.forEach { child ->
                drawNode(child, canvas, nodeAlpha)
            }

            is ShapeNode -> drawShape(node, canvas, nodeAlpha)
            is ImageNode -> drawImage(node, canvas, nodeAlpha)
            is TextNode -> drawText(node, canvas, nodeAlpha)
        }

        canvas.restore()
    }

    /** 绘制矩形与圆形节点。 */
    private fun drawShape(
        node: ShapeNode,
        canvas: Canvas,
        alpha: Float
    ) {
        val fillColor = node.style.fillColor
        if (fillColor != null) {
            fillPaint.shader = null
            fillPaint.color = multiplyColorAlpha(fillColor, alpha)
            when (node.shapeType) {
                ShapeType.RECTANGLE -> {
                    rect.set(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom)
                    canvas.drawRoundRect(
                        rect,
                        node.style.cornerRadius,
                        node.style.cornerRadius,
                        fillPaint
                    )
                }

                ShapeType.CIRCLE -> {
                    canvas.drawCircle(
                        node.bounds.centerX,
                        node.bounds.centerY,
                        min(node.bounds.width, node.bounds.height) * 0.5f,
                        fillPaint
                    )
                }
            }
        }

        val strokeColor = node.style.strokeColor
        if (strokeColor != null && node.style.strokeWidth > 0f) {
            strokePaint.color = multiplyColorAlpha(strokeColor, alpha)
            strokePaint.strokeWidth = node.style.strokeWidth
            when (node.shapeType) {
                ShapeType.RECTANGLE -> {
                    rect.set(node.bounds.left, node.bounds.top, node.bounds.right, node.bounds.bottom)
                    canvas.drawRoundRect(
                        rect,
                        node.style.cornerRadius,
                        node.style.cornerRadius,
                        strokePaint
                    )
                }

                ShapeType.CIRCLE -> {
                    canvas.drawCircle(
                        node.bounds.centerX,
                        node.bounds.centerY,
                        min(node.bounds.width, node.bounds.height) * 0.5f,
                        strokePaint
                    )
                }
            }
        }
    }

    /** 绘制图片节点，并缓存 Drawable 实例。 */
    private fun drawImage(
        node: ImageNode,
        canvas: Canvas,
        alpha: Float
    ) {
        val drawable = drawableCache.getOrPut(node.drawableResId) {
            resources.getDrawable(node.drawableResId, theme).mutate()
        }

        drawable.alpha = (255 * alpha).roundToInt().coerceIn(0, 255)
        drawable.setBounds(
            node.bounds.left.roundToInt(),
            node.bounds.top.roundToInt(),
            node.bounds.right.roundToInt(),
            node.bounds.bottom.roundToInt()
        )
        drawable.draw(canvas)
    }

    /** 绘制文本节点。 */
    private fun drawText(
        node: TextNode,
        canvas: Canvas,
        alpha: Float
    ) {
        textPaint.color = multiplyColorAlpha(node.color, alpha)
        textPaint.textSize = node.textSize
        textPaint.isFakeBoldText = node.isBold
        textPaint.textAlign = when (node.align) {
            TextAlign.LEFT -> Paint.Align.LEFT
            TextAlign.CENTER -> Paint.Align.CENTER
            TextAlign.RIGHT -> Paint.Align.RIGHT
        }

        canvas.drawText(node.text, node.x, node.baselineY, textPaint)
    }

    /** 把节点变换按平移、旋转、缩放顺序作用到 Canvas。 */
    private fun applyTransform(
        canvas: Canvas,
        transform: NodeTransform
    ) {
        canvas.translate(transform.translationX, transform.translationY)
        if (transform.rotationDegrees != 0f) {
            canvas.rotate(transform.rotationDegrees)
        }
        if (transform.scaleX != 1f || transform.scaleY != 1f) {
            canvas.scale(transform.scaleX, transform.scaleY)
        }
    }

    /** 按继承透明度重新计算颜色 alpha。 */
    private fun multiplyColorAlpha(
        color: Int,
        alpha: Float
    ): Int {
        val resolvedAlpha = (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255)
        return Color.argb(
            resolvedAlpha,
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }
}
