package com.example.klwpdemo.document

import android.graphics.Color
import android.text.format.DateFormat
import com.example.klwpdemo.runtime.RuntimeFrameState
import com.example.klwpdemo.runtime.RuntimeViewport
import kotlin.math.min

/**
 * 生成演示模板文档，并在运行时按需附加动态效果。
 *
 * 当前这份数据明确采用「项目 -> 组件 -> 子项」的层级，
 * 让编辑器首页可以先显示项目内容，而不是直接跳进图形参数。
 */
object DemoWallpaperDocumentFactory {

    /** 根据当前视口生成一份可落盘的静态模板文档。 */
    fun createTemplateDocument(viewport: RuntimeViewport): WallpaperDocument {
        val width = viewport.width.toFloat()
        val height = viewport.height.toFloat()
        val minSide = min(width, height)

        val outlineBounds = LayerBoundsDocument(
            left = width * 0.15f,
            top = height * 0.11f,
            width = width * 0.22f,
            height = width * 0.34f
        )
        val infoPanelBounds = LayerBoundsDocument(
            left = width * 0.09f,
            top = height * 0.58f,
            width = width * 0.57f,
            height = height * 0.16f
        )
        val orbSize = minSide * 0.21f
        val orbCenterX = width * 0.57f
        val orbCenterY = height * 0.27f
        val orbGlowSize = orbSize * 1.42f
        val orbCoreSize = orbSize * 0.28f

        val mainVisualChildren = mutableListOf<LayerDocument>(
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
                transform = LayerTransformDocument(),
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
            )
        )

        val infoPanelChildren = mutableListOf<LayerDocument>(
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
                text = "时间 --:--:--",
                x = infoPanelBounds.left + width * 0.04f,
                baselineY = infoPanelBounds.top + infoPanelBounds.height * 0.68f,
                textSize = width * 0.045f,
                color = Color.parseColor("#D7E5EB")
            )
        )

        val wallpaperChildren = mutableListOf<LayerDocument>(
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
            )
        )

        return WallpaperDocument(
            background = BackgroundDocument.Solid(Color.parseColor("#1C1C1C")),
            layers = listOf(
                GroupLayerDocument(
                    id = "project-hh301",
                    children = listOf(
                        GroupLayerDocument(
                            id = "component-main-visual",
                            children = mainVisualChildren
                        ),
                        GroupLayerDocument(
                            id = "component-info-panel",
                            children = infoPanelChildren
                        )
                    )
                ),
                GroupLayerDocument(
                    id = "project-main-wallpaper",
                    children = listOf(
                        GroupLayerDocument(
                            id = "component-atmosphere",
                            children = wallpaperChildren
                        )
                    )
                )
            )
        )
    }

    /** 在不污染项目源文档的前提下，为运行时附加动态文本和触摸波纹。 */
    fun decorateRuntimeDocument(
        document: WallpaperDocument,
        viewport: RuntimeViewport,
        frameState: RuntimeFrameState
    ): WallpaperDocument {
        val withoutRipple = document.copy(
            layers = removeLayerById(document.layers, "touch-ripple")
        )
        val withLiveClock = withoutRipple.copy(
            layers = updateTextLayerById(
                layers = withoutRipple.layers,
                layerId = "info-clock",
                updater = { layer ->
                    layer.copy(text = "时间 " + DateFormat.format("HH:mm:ss", frameState.wallClockMillis))
                }
            )
        )
        val rippleLayer = buildRippleLayer(viewport, frameState) ?: return withLiveClock
        return withLiveClock.copy(
            layers = appendLayerToGroup(
                layers = withLiveClock.layers,
                groupId = "component-atmosphere",
                layer = rippleLayer
            )
        )
    }

    /** 触摸时叠加短暂的波纹反馈，仍然挂在项目层级里。 */
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

    /** 递归移除指定 id 的图层，避免运行时波纹被持久化。 */
    private fun removeLayerById(
        layers: List<LayerDocument>,
        layerId: String
    ): List<LayerDocument> {
        return layers.mapNotNull { layer ->
            when {
                layer.id == layerId -> null
                layer is GroupLayerDocument -> layer.copy(
                    children = removeLayerById(layer.children, layerId)
                )

                else -> layer
            }
        }
    }

    /** 递归更新指定文本图层。 */
    private fun updateTextLayerById(
        layers: List<LayerDocument>,
        layerId: String,
        updater: (TextLayerDocument) -> TextLayerDocument
    ): List<LayerDocument> {
        return layers.map { layer ->
            when {
                layer is TextLayerDocument && layer.id == layerId -> updater(layer)
                layer is GroupLayerDocument -> layer.copy(
                    children = updateTextLayerById(layer.children, layerId, updater)
                )

                else -> layer
            }
        }
    }

    /** 把临时图层挂到目标组件下，找不到目标组时保持原文档不变。 */
    private fun appendLayerToGroup(
        layers: List<LayerDocument>,
        groupId: String,
        layer: LayerDocument
    ): List<LayerDocument> {
        var inserted = false
        val updatedLayers = layers.map { current ->
            when {
                current is GroupLayerDocument && current.id == groupId -> {
                    inserted = true
                    current.copy(children = current.children + layer)
                }

                current is GroupLayerDocument -> {
                    val updatedChildren = appendLayerToGroup(current.children, groupId, layer)
                    if (updatedChildren !== current.children) {
                        inserted = true
                        current.copy(children = updatedChildren)
                    } else {
                        current
                    }
                }

                else -> current
            }
        }
        return if (inserted) updatedLayers else layers
    }
}
