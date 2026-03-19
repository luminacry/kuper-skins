package com.example.klwpdemo.compiler

import com.example.klwpdemo.document.BackgroundDocument
import com.example.klwpdemo.document.GroupLayerDocument
import com.example.klwpdemo.document.ImageLayerDocument
import com.example.klwpdemo.document.LayerBoundsDocument
import com.example.klwpdemo.document.LayerDocument
import com.example.klwpdemo.document.LayerTransformDocument
import com.example.klwpdemo.document.ShapeKind
import com.example.klwpdemo.document.ShapeLayerDocument
import com.example.klwpdemo.document.ShapeStyleDocument
import com.example.klwpdemo.document.TextAlignment
import com.example.klwpdemo.document.TextLayerDocument
import com.example.klwpdemo.document.WallpaperDocument
import com.example.klwpdemo.runtime.BackgroundFill
import com.example.klwpdemo.runtime.GroupNode
import com.example.klwpdemo.runtime.ImageNode
import com.example.klwpdemo.runtime.NodeBounds
import com.example.klwpdemo.runtime.NodeTransform
import com.example.klwpdemo.runtime.RuntimeScene
import com.example.klwpdemo.runtime.SceneNode
import com.example.klwpdemo.runtime.ShapeNode
import com.example.klwpdemo.runtime.ShapeStyle
import com.example.klwpdemo.runtime.ShapeType
import com.example.klwpdemo.runtime.TextAlign
import com.example.klwpdemo.runtime.TextNode

/** 把编辑态文档编译为渲染器可直接消费的运行时场景。 */
class DocumentToRuntimeSceneCompiler {
    /** 编译整份文档。 */
    fun compile(document: WallpaperDocument): RuntimeScene {
        return RuntimeScene(
            background = compileBackground(document.background),
            root = GroupNode(
                id = "root",
                children = document.layers.map(::compileLayer)
            )
        )
    }

    /** 编译背景定义。 */
    private fun compileBackground(background: BackgroundDocument): BackgroundFill {
        return when (background) {
            is BackgroundDocument.Solid -> BackgroundFill.Solid(background.color)
            is BackgroundDocument.LinearGradient -> BackgroundFill.LinearGradient(background.colors.copyOf())
        }
    }

    /** 编译单个图层节点。 */
    private fun compileLayer(layer: LayerDocument): SceneNode {
        return when (layer) {
            is GroupLayerDocument -> GroupNode(
                id = layer.id,
                children = layer.children.map(::compileLayer),
                visible = layer.visible,
                transform = compileTransform(layer.transform)
            )

            is ShapeLayerDocument -> ShapeNode(
                id = layer.id,
                shapeType = compileShapeKind(layer.shapeKind),
                bounds = compileBounds(layer.bounds),
                style = compileShapeStyle(layer.style),
                visible = layer.visible,
                transform = compileTransform(layer.transform)
            )

            is ImageLayerDocument -> ImageNode(
                id = layer.id,
                drawableResId = layer.drawableResId,
                bounds = compileBounds(layer.bounds),
                visible = layer.visible,
                transform = compileTransform(layer.transform)
            )

            is TextLayerDocument -> TextNode(
                id = layer.id,
                text = layer.text,
                x = layer.x,
                baselineY = layer.baselineY,
                textSize = layer.textSize,
                color = layer.color,
                isBold = layer.isBold,
                align = compileTextAlignment(layer.align),
                visible = layer.visible,
                transform = compileTransform(layer.transform)
            )
        }
    }

    /** 复制变换信息。 */
    private fun compileTransform(transform: LayerTransformDocument): NodeTransform {
        return NodeTransform(
            translationX = transform.translationX,
            translationY = transform.translationY,
            scaleX = transform.scaleX,
            scaleY = transform.scaleY,
            rotationDegrees = transform.rotationDegrees,
            alpha = transform.alpha
        )
    }

    /** 复制边界信息。 */
    private fun compileBounds(bounds: LayerBoundsDocument): NodeBounds {
        return NodeBounds(
            left = bounds.left,
            top = bounds.top,
            width = bounds.width,
            height = bounds.height
        )
    }

    /** 复制形状样式。 */
    private fun compileShapeStyle(style: ShapeStyleDocument): ShapeStyle {
        return ShapeStyle(
            fillColor = style.fillColor,
            strokeColor = style.strokeColor,
            strokeWidth = style.strokeWidth,
            cornerRadius = style.cornerRadius
        )
    }

    /** 映射形状类型。 */
    private fun compileShapeKind(shapeKind: ShapeKind): ShapeType {
        return when (shapeKind) {
            ShapeKind.RECTANGLE -> ShapeType.RECTANGLE
            ShapeKind.CIRCLE -> ShapeType.CIRCLE
        }
    }

    /** 映射文本对齐。 */
    private fun compileTextAlignment(alignment: TextAlignment): TextAlign {
        return when (alignment) {
            TextAlignment.LEFT -> TextAlign.LEFT
            TextAlignment.CENTER -> TextAlign.CENTER
            TextAlignment.RIGHT -> TextAlign.RIGHT
        }
    }
}
