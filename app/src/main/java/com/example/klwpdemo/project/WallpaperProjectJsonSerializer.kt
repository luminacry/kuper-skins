package com.example.klwpdemo.project

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
import org.json.JSONArray
import org.json.JSONObject

/** 项目工程文件与内存对象之间的双向转换器。 */
object WallpaperProjectJsonSerializer {

    /** 把项目对象编码成可落盘的 JSON 字符串。 */
    fun encode(project: WallpaperProjectDocument): String {
        return buildProjectJson(project).toString(2)
    }

    /** 从 JSON 字符串恢复项目对象。 */
    fun decode(content: String): WallpaperProjectDocument {
        val json = JSONObject(content)
        return WallpaperProjectDocument(
            id = json.getString(KEY_ID),
            name = json.getString(KEY_NAME),
            schemaVersion = json.optInt(KEY_SCHEMA_VERSION, WallpaperProjectDocument.CURRENT_SCHEMA_VERSION),
            createdAtMillis = json.optLong(KEY_CREATED_AT, 0L),
            updatedAtMillis = json.optLong(KEY_UPDATED_AT, 0L),
            sourceViewportWidth = json.optIntOrNull(KEY_SOURCE_VIEWPORT_WIDTH),
            sourceViewportHeight = json.optIntOrNull(KEY_SOURCE_VIEWPORT_HEIGHT),
            document = decodeDocument(json.getJSONObject(KEY_DOCUMENT))
        )
    }

    /** 构建项目根 JSON。 */
    private fun buildProjectJson(project: WallpaperProjectDocument): JSONObject {
        return JSONObject()
            .put(KEY_ID, project.id)
            .put(KEY_NAME, project.name)
            .put(KEY_SCHEMA_VERSION, project.schemaVersion)
            .put(KEY_CREATED_AT, project.createdAtMillis)
            .put(KEY_UPDATED_AT, project.updatedAtMillis)
            .putOpt(KEY_SOURCE_VIEWPORT_WIDTH, project.sourceViewportWidth)
            .putOpt(KEY_SOURCE_VIEWPORT_HEIGHT, project.sourceViewportHeight)
            .put(KEY_DOCUMENT, encodeDocument(project.document))
    }

    /** 编码整份壁纸文档。 */
    private fun encodeDocument(document: WallpaperDocument): JSONObject {
        return JSONObject()
            .put(KEY_BACKGROUND, encodeBackground(document.background))
            .put(KEY_LAYERS, JSONArray().apply {
                document.layers.forEach { put(encodeLayer(it)) }
            })
    }

    /** 解码整份壁纸文档。 */
    private fun decodeDocument(json: JSONObject): WallpaperDocument {
        return WallpaperDocument(
            background = decodeBackground(json.getJSONObject(KEY_BACKGROUND)),
            layers = decodeLayers(json.getJSONArray(KEY_LAYERS))
        )
    }

    /** 编码背景定义。 */
    private fun encodeBackground(background: BackgroundDocument): JSONObject {
        return when (background) {
            is BackgroundDocument.Solid -> JSONObject()
                .put(KEY_TYPE, TYPE_BACKGROUND_SOLID)
                .put(KEY_COLOR, background.color)

            is BackgroundDocument.LinearGradient -> JSONObject()
                .put(KEY_TYPE, TYPE_BACKGROUND_LINEAR_GRADIENT)
                .put(KEY_COLORS, JSONArray().apply {
                    background.colors.forEach { put(it) }
                })
        }
    }

    /** 解码背景定义。 */
    private fun decodeBackground(json: JSONObject): BackgroundDocument {
        return when (json.getString(KEY_TYPE)) {
            TYPE_BACKGROUND_SOLID -> BackgroundDocument.Solid(
                color = json.getInt(KEY_COLOR)
            )

            TYPE_BACKGROUND_LINEAR_GRADIENT -> BackgroundDocument.LinearGradient(
                colors = decodeIntArray(json.getJSONArray(KEY_COLORS))
            )

            else -> error("不支持的背景类型: ${json.optString(KEY_TYPE)}")
        }
    }

    /** 编码单个图层节点。 */
    private fun encodeLayer(layer: LayerDocument): JSONObject {
        val base = JSONObject()
            .put(KEY_ID, layer.id)
            .put(KEY_VISIBLE, layer.visible)
            .put(KEY_TRANSFORM, encodeTransform(layer.transform))

        return when (layer) {
            is GroupLayerDocument -> base
                .put(KEY_TYPE, TYPE_LAYER_GROUP)
                .put(KEY_CHILDREN, JSONArray().apply {
                    layer.children.forEach { put(encodeLayer(it)) }
                })

            is ShapeLayerDocument -> base
                .put(KEY_TYPE, TYPE_LAYER_SHAPE)
                .put(KEY_SHAPE_KIND, layer.shapeKind.name)
                .put(KEY_BOUNDS, encodeBounds(layer.bounds))
                .put(KEY_STYLE, encodeShapeStyle(layer.style))

            is ImageLayerDocument -> base
                .put(KEY_TYPE, TYPE_LAYER_IMAGE)
                .put(KEY_DRAWABLE_RES_ID, layer.drawableResId)
                .put(KEY_BOUNDS, encodeBounds(layer.bounds))

            is TextLayerDocument -> base
                .put(KEY_TYPE, TYPE_LAYER_TEXT)
                .put(KEY_TEXT, layer.text)
                .put(KEY_X, layer.x)
                .put(KEY_BASELINE_Y, layer.baselineY)
                .put(KEY_TEXT_SIZE, layer.textSize)
                .put(KEY_COLOR, layer.color)
                .put(KEY_IS_BOLD, layer.isBold)
                .put(KEY_ALIGN, layer.align.name)
        }
    }

    /** 解码单个图层节点。 */
    private fun decodeLayer(json: JSONObject): LayerDocument {
        val id = json.getString(KEY_ID)
        val visible = json.optBoolean(KEY_VISIBLE, true)
        val transform = decodeTransform(json.optJSONObject(KEY_TRANSFORM))

        return when (json.getString(KEY_TYPE)) {
            TYPE_LAYER_GROUP -> GroupLayerDocument(
                id = id,
                children = decodeLayers(json.getJSONArray(KEY_CHILDREN)),
                visible = visible,
                transform = transform
            )

            TYPE_LAYER_SHAPE -> ShapeLayerDocument(
                id = id,
                shapeKind = ShapeKind.valueOf(json.getString(KEY_SHAPE_KIND)),
                bounds = decodeBounds(json.getJSONObject(KEY_BOUNDS)),
                style = decodeShapeStyle(json.getJSONObject(KEY_STYLE)),
                visible = visible,
                transform = transform
            )

            TYPE_LAYER_IMAGE -> ImageLayerDocument(
                id = id,
                drawableResId = json.getInt(KEY_DRAWABLE_RES_ID),
                bounds = decodeBounds(json.getJSONObject(KEY_BOUNDS)),
                visible = visible,
                transform = transform
            )

            TYPE_LAYER_TEXT -> TextLayerDocument(
                id = id,
                text = json.getString(KEY_TEXT),
                x = json.getDouble(KEY_X).toFloat(),
                baselineY = json.getDouble(KEY_BASELINE_Y).toFloat(),
                textSize = json.getDouble(KEY_TEXT_SIZE).toFloat(),
                color = json.getInt(KEY_COLOR),
                isBold = json.optBoolean(KEY_IS_BOLD, false),
                align = TextAlignment.valueOf(json.optString(KEY_ALIGN, TextAlignment.LEFT.name)),
                visible = visible,
                transform = transform
            )

            else -> error("不支持的图层类型: ${json.optString(KEY_TYPE)}")
        }
    }

    /** 编码变换对象。 */
    private fun encodeTransform(transform: LayerTransformDocument): JSONObject {
        return JSONObject()
            .put(KEY_TRANSLATION_X, transform.translationX)
            .put(KEY_TRANSLATION_Y, transform.translationY)
            .put(KEY_SCALE_X, transform.scaleX)
            .put(KEY_SCALE_Y, transform.scaleY)
            .put(KEY_ROTATION_DEGREES, transform.rotationDegrees)
            .put(KEY_ALPHA, transform.alpha)
    }

    /** 解码变换对象。 */
    private fun decodeTransform(json: JSONObject?): LayerTransformDocument {
        if (json == null) {
            return LayerTransformDocument()
        }
        return LayerTransformDocument(
            translationX = json.optDouble(KEY_TRANSLATION_X, 0.0).toFloat(),
            translationY = json.optDouble(KEY_TRANSLATION_Y, 0.0).toFloat(),
            scaleX = json.optDouble(KEY_SCALE_X, 1.0).toFloat(),
            scaleY = json.optDouble(KEY_SCALE_Y, 1.0).toFloat(),
            rotationDegrees = json.optDouble(KEY_ROTATION_DEGREES, 0.0).toFloat(),
            alpha = json.optDouble(KEY_ALPHA, 1.0).toFloat()
        )
    }

    /** 编码基础边界。 */
    private fun encodeBounds(bounds: LayerBoundsDocument): JSONObject {
        return JSONObject()
            .put(KEY_LEFT, bounds.left)
            .put(KEY_TOP, bounds.top)
            .put(KEY_WIDTH, bounds.width)
            .put(KEY_HEIGHT, bounds.height)
    }

    /** 解码基础边界。 */
    private fun decodeBounds(json: JSONObject): LayerBoundsDocument {
        return LayerBoundsDocument(
            left = json.getDouble(KEY_LEFT).toFloat(),
            top = json.getDouble(KEY_TOP).toFloat(),
            width = json.getDouble(KEY_WIDTH).toFloat(),
            height = json.getDouble(KEY_HEIGHT).toFloat()
        )
    }

    /** 编码形状样式。 */
    private fun encodeShapeStyle(style: ShapeStyleDocument): JSONObject {
        return JSONObject()
            .putOpt(KEY_FILL_COLOR, style.fillColor)
            .putOpt(KEY_STROKE_COLOR, style.strokeColor)
            .put(KEY_STROKE_WIDTH, style.strokeWidth)
            .put(KEY_CORNER_RADIUS, style.cornerRadius)
    }

    /** 解码形状样式。 */
    private fun decodeShapeStyle(json: JSONObject): ShapeStyleDocument {
        return ShapeStyleDocument(
            fillColor = json.optIntOrNull(KEY_FILL_COLOR),
            strokeColor = json.optIntOrNull(KEY_STROKE_COLOR),
            strokeWidth = json.optDouble(KEY_STROKE_WIDTH, 0.0).toFloat(),
            cornerRadius = json.optDouble(KEY_CORNER_RADIUS, 0.0).toFloat()
        )
    }

    /** 批量解码图层数组。 */
    private fun decodeLayers(array: JSONArray): List<LayerDocument> {
        val layers = mutableListOf<LayerDocument>()
        for (index in 0 until array.length()) {
            layers += decodeLayer(array.getJSONObject(index))
        }
        return layers
    }

    /** 把 JSON 数组转成 IntArray。 */
    private fun decodeIntArray(array: JSONArray): IntArray {
        val result = IntArray(array.length())
        for (index in 0 until array.length()) {
            result[index] = array.getInt(index)
        }
        return result
    }

    /** Int 字段允许为空，避免把不存在字段误读成 0。 */
    private fun JSONObject.optIntOrNull(key: String): Int? {
        return if (has(key) && !isNull(key)) optInt(key) else null
    }

    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_CREATED_AT = "createdAtMillis"
    private const val KEY_UPDATED_AT = "updatedAtMillis"
    private const val KEY_SOURCE_VIEWPORT_WIDTH = "sourceViewportWidth"
    private const val KEY_SOURCE_VIEWPORT_HEIGHT = "sourceViewportHeight"
    private const val KEY_DOCUMENT = "document"
    private const val KEY_BACKGROUND = "background"
    private const val KEY_LAYERS = "layers"
    private const val KEY_TYPE = "type"
    private const val KEY_COLOR = "color"
    private const val KEY_COLORS = "colors"
    private const val KEY_VISIBLE = "visible"
    private const val KEY_TRANSFORM = "transform"
    private const val KEY_CHILDREN = "children"
    private const val KEY_SHAPE_KIND = "shapeKind"
    private const val KEY_BOUNDS = "bounds"
    private const val KEY_STYLE = "style"
    private const val KEY_DRAWABLE_RES_ID = "drawableResId"
    private const val KEY_TEXT = "text"
    private const val KEY_X = "x"
    private const val KEY_BASELINE_Y = "baselineY"
    private const val KEY_TEXT_SIZE = "textSize"
    private const val KEY_IS_BOLD = "isBold"
    private const val KEY_ALIGN = "align"
    private const val KEY_TRANSLATION_X = "translationX"
    private const val KEY_TRANSLATION_Y = "translationY"
    private const val KEY_SCALE_X = "scaleX"
    private const val KEY_SCALE_Y = "scaleY"
    private const val KEY_ROTATION_DEGREES = "rotationDegrees"
    private const val KEY_ALPHA = "alpha"
    private const val KEY_LEFT = "left"
    private const val KEY_TOP = "top"
    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_FILL_COLOR = "fillColor"
    private const val KEY_STROKE_COLOR = "strokeColor"
    private const val KEY_STROKE_WIDTH = "strokeWidth"
    private const val KEY_CORNER_RADIUS = "cornerRadius"

    private const val TYPE_BACKGROUND_SOLID = "solid"
    private const val TYPE_BACKGROUND_LINEAR_GRADIENT = "linearGradient"
    private const val TYPE_LAYER_GROUP = "group"
    private const val TYPE_LAYER_SHAPE = "shape"
    private const val TYPE_LAYER_IMAGE = "image"
    private const val TYPE_LAYER_TEXT = "text"
}
