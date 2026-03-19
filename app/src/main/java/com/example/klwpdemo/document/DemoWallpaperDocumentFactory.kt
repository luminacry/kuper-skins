package com.example.klwpdemo.document

import android.graphics.Color
import android.text.format.DateFormat
import com.example.klwpdemo.runtime.RuntimeFrameState
import com.example.klwpdemo.runtime.RuntimeViewport
import kotlin.math.min
import kotlin.math.sin

/**
 * 生成演示壁纸的基础文档。
 *
 * 这份工厂数据同时服务于运行时壁纸和编辑器初始预设，
 * 因此所有默认图层都在这里集中维护。
 */
object DemoWallpaperDocumentFactory {
    /** 根据当前视口和运行时状态生成一份完整文档。 */
    fun createDocument(
        viewport: RuntimeViewport,
        frameState: RuntimeFrameState
    ): WallpaperDocument {
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        val pulse = (((sin(frameState.uptimeMillis / 620.0) + 1.0) * 0.5) * 8.0).toFloat()
        val drift = (((sin(frameState.uptimeMillis / 1450.0) + 1.0) * 0.5) - 0.5).toFloat()
        val minSide = min(width, height)

        val outlineBounds = LayerBoundsDocument(
            left = width * 0.15f,
            top = height * 0.11f,
            width = width * 0.22f,
            height = width * 0.34f
        )
        val infoPanelBounds = LayerBoundsDocument(
            left = width * 0.09f,
            top = height * 0.58f + drift * 4f,
            width = width * 0.57f,
            height = height * 0.16f
        )
        val orbSize = minSide * 0.21f
        val orbCenterX = width * 0.57f + drift * width * 0.02f
        val orbCenterY = height * 0.27f + drift * height * 0.01f
        val orbGlowSize = orbSize * 1.42f
        val orbCoreSize = orbSize * 0.28f

        val layers = mutableListOf<LayerDocument>(
            ShapeLayerDocument(
                id = "left-haze",
                shapeKind = ShapeKind.CIRCLE,
                bounds = LayerBoundsDocument(
                    left = width * 0.11f,
                    top = height * 0.09f,
                    width = minSide * 0.36f,
                    height = minSide * 0.36f
                ),
                style = ShapeStyleDocument(fillColor = Color.argb(34, 55, 70, 84))
            ),
            ShapeLayerDocument(
                id = "outline-card",
                shapeKind = ShapeKind.RECTANGLE,
                bounds = outlineBounds,
                style = ShapeStyleDocument(
                    strokeColor = Color.parseColor("#F4A7B6"),
                    strokeWidth = maxOf(2.5f, width * 0.014f),
                    cornerRadius = width * 0.055f
                )
            ),
            GroupLayerDocument(
                id = "orb-cluster",
                transform = LayerTransformDocument(
                    translationY = pulse * 0.25f
                ),
                children = listOf(
                    ShapeLayerDocument(
                        id = "orb-glow",
                        shapeKind = ShapeKind.CIRCLE,
                        bounds = LayerBoundsDocument(
                            left = orbCenterX - orbGlowSize * 0.5f,
                            top = orbCenterY - orbGlowSize * 0.5f,
                            width = orbGlowSize,
                            height = orbGlowSize
                        ),
                        style = ShapeStyleDocument(fillColor = Color.argb(64, 255, 215, 106))
                    ),
                    ShapeLayerDocument(
                        id = "orb-main",
                        shapeKind = ShapeKind.CIRCLE,
                        bounds = LayerBoundsDocument(
                            left = orbCenterX - orbSize * 0.5f,
                            top = orbCenterY - orbSize * 0.5f,
                            width = orbSize,
                            height = orbSize
                        ),
                        style = ShapeStyleDocument(fillColor = Color.parseColor("#FFD563"))
                    ),
                    ShapeLayerDocument(
                        id = "orb-core",
                        shapeKind = ShapeKind.CIRCLE,
                        bounds = LayerBoundsDocument(
                            left = orbCenterX - orbCoreSize * 0.5f,
                            top = orbCenterY - orbCoreSize * 0.5f,
                            width = orbCoreSize,
                            height = orbCoreSize
                        ),
                        style = ShapeStyleDocument(fillColor = Color.parseColor("#FFF9E8"))
                    )
                )
            ),
            ShapeLayerDocument(
                id = "right-haze",
                shapeKind = ShapeKind.CIRCLE,
                bounds = LayerBoundsDocument(
                    left = width * 0.56f,
                    top = height * 0.42f,
                    width = minSide * 0.38f,
                    height = minSide * 0.38f
                ),
                style = ShapeStyleDocument(fillColor = Color.argb(74, 47, 66, 79))
            ),
            GroupLayerDocument(
                id = "info-panel",
                children = listOf(
                    ShapeLayerDocument(
                        id = "info-panel-bg",
                        shapeKind = ShapeKind.RECTANGLE,
                        bounds = infoPanelBounds,
                        style = ShapeStyleDocument(
                            fillColor = Color.argb(176, 49, 71, 82),
                            cornerRadius = width * 0.07f
                        )
                    ),
                    TextLayerDocument(
                        id = "info-title",
                        text = "KLWP 运行中",
                        x = infoPanelBounds.left + width * 0.04f,
                        baselineY = infoPanelBounds.top + infoPanelBounds.height * 0.43f,
                        textSize = width * 0.076f,
                        color = Color.WHITE,
                        isBold = true
                    ),
                    TextLayerDocument(
                        id = "info-clock",
                        text = "时间 " + DateFormat.format("HH:mm:ss", frameState.wallClockMillis),
                        x = infoPanelBounds.left + width * 0.04f,
                        baselineY = infoPanelBounds.top + infoPanelBounds.height * 0.68f,
                        textSize = width * 0.045f,
                        color = Color.parseColor("#D7E5EB")
                    )
                )
            )
        )

        buildRippleLayer(viewport, frameState)?.let(layers::add)

        return WallpaperDocument(
            background = BackgroundDocument.Solid(Color.parseColor("#1C1C1C")),
            layers = layers
        )
    }

    /** 当用户触摸桌面时，额外叠加一层短暂的涟漪效果。 */
    private fun buildRippleLayer(
        viewport: RuntimeViewport,
        frameState: RuntimeFrameState
    ): ShapeLayerDocument? {
        val touch = frameState.touchSample ?: return null
        val progress = frameState.rippleProgress
        if (progress !in 0f..1f) {
            return null
        }

        val radius = progress * min(viewport.width, viewport.height) * 0.14f
        return ShapeLayerDocument(
            id = "touch-ripple",
            shapeKind = ShapeKind.CIRCLE,
            bounds = LayerBoundsDocument(
                left = touch.x - radius,
                top = touch.y - radius,
                width = radius * 2f,
                height = radius * 2f
            ),
            style = ShapeStyleDocument(
                strokeColor = Color.argb(
                    ((1f - progress) * 180f).toInt(),
                    255,
                    255,
                    255
                ),
                strokeWidth = 4f
            )
        )
    }
}
