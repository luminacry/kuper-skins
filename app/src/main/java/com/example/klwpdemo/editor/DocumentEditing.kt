package com.example.klwpdemo.editor

import android.os.SystemClock
import com.example.klwpdemo.document.GroupLayerDocument
import com.example.klwpdemo.document.LayerDocument
import com.example.klwpdemo.document.LayerTransformDocument
import com.example.klwpdemo.document.ShapeLayerDocument
import com.example.klwpdemo.document.ShapeStyleDocument
import com.example.klwpdemo.document.WallpaperDocument
import java.util.ArrayDeque

/** 编辑器所有可撤销操作的统一协议。 */
interface DocumentCommand {
    /** 基于当前文档生成下一份文档快照。 */
    fun apply(document: WallpaperDocument): WallpaperDocument

    /** 拖拽类高频操作允许在短时间窗口内合并历史。 */
    fun canCoalesceWith(previous: DocumentCommand): Boolean = false
}

/** 新增一个形状图层。 */
data class AddShapeCommand(
    private val shape: ShapeLayerDocument
) : DocumentCommand {
    override fun apply(document: WallpaperDocument): WallpaperDocument {
        return document.copy(
            layers = document.layers + shape
        )
    }
}

/** 删除指定图层。 */
data class DeleteLayerCommand(
    private val layerId: String
) : DocumentCommand {
    override fun apply(document: WallpaperDocument): WallpaperDocument {
        return DocumentLayerOps.deleteLayer(document, layerId)
    }
}

/** 更新指定图层的位移、缩放和透明度。 */
data class UpdateLayerTransformCommand(
    private val layerId: String,
    private val transform: LayerTransformDocument
) : DocumentCommand {
    override fun apply(document: WallpaperDocument): WallpaperDocument {
        return DocumentLayerOps.updateLayer(document, layerId) { layer ->
            when (layer) {
                is GroupLayerDocument -> layer.copy(transform = transform)
                is ShapeLayerDocument -> layer.copy(transform = transform)
                is com.example.klwpdemo.document.ImageLayerDocument -> layer.copy(transform = transform)
                is com.example.klwpdemo.document.TextLayerDocument -> layer.copy(transform = transform)
            }
        }
    }

    override fun canCoalesceWith(previous: DocumentCommand): Boolean {
        return previous is UpdateLayerTransformCommand && previous.layerId == layerId
    }
}

/** 更新指定图层的显隐状态。 */
data class UpdateLayerVisibilityCommand(
    private val layerId: String,
    private val visible: Boolean
) : DocumentCommand {
    override fun apply(document: WallpaperDocument): WallpaperDocument {
        return DocumentLayerOps.updateLayer(document, layerId) { layer ->
            when (layer) {
                is GroupLayerDocument -> layer.copy(visible = visible)
                is ShapeLayerDocument -> layer.copy(visible = visible)
                is com.example.klwpdemo.document.ImageLayerDocument -> layer.copy(visible = visible)
                is com.example.klwpdemo.document.TextLayerDocument -> layer.copy(visible = visible)
            }
        }
    }
}

/** 更新形状图层的样式。 */
data class UpdateShapeStyleCommand(
    private val layerId: String,
    private val style: ShapeStyleDocument
) : DocumentCommand {
    override fun apply(document: WallpaperDocument): WallpaperDocument {
        return DocumentLayerOps.updateLayer(document, layerId) { layer ->
            if (layer is ShapeLayerDocument) {
                layer.copy(style = style)
            } else {
                layer
            }
        }
    }
}

/**
 * 编辑器文档仓库。
 *
 * 这里负责持有当前文档，并维护撤销栈、重做栈以及连续命令合并逻辑。
 */
class DocumentRepository(
    private val maxHistorySize: Int = 100,
    private val commandCoalesceWindowMillis: Long = 300L
) {
    private var document: WallpaperDocument? = null
    private val undoStack = ArrayDeque<WallpaperDocument>()
    private val redoStack = ArrayDeque<WallpaperDocument>()
    private var lastCommand: DocumentCommand? = null
    private var lastCommandUptimeMillis = 0L

    /** 当前是否已经装载了一份可编辑文档。 */
    fun hasDocument(): Boolean = document != null

    /** 返回当前文档快照。 */
    fun snapshot(): WallpaperDocument? = document

    /** 是否存在可撤销历史。 */
    fun canUndo(): Boolean = undoStack.isNotEmpty()

    /** 是否存在可重做历史。 */
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    /** 直接替换整份文档，并重置历史栈。 */
    fun setDocument(document: WallpaperDocument) {
        this.document = document
        undoStack.clear()
        redoStack.clear()
        clearCommandMergeState()
    }

    /** 执行一条命令，并在必要时把它并入上一条历史。 */
    fun execute(command: DocumentCommand): WallpaperDocument? {
        val current = document ?: return null
        val next = command.apply(current)
        if (next == current) {
            return current
        }
        val now = SystemClock.uptimeMillis()
        val previousCommand = lastCommand
        val shouldCoalesce = previousCommand != null &&
            command.canCoalesceWith(previousCommand) &&
            now - lastCommandUptimeMillis <= commandCoalesceWindowMillis

        if (!shouldCoalesce) {
            pushUndo(current)
        }
        document = next
        redoStack.clear()
        lastCommand = command
        lastCommandUptimeMillis = now
        return next
    }

    /** 以函数式方式直接修改文档，适合临时批量变更。 */
    fun mutate(
        mutator: (WallpaperDocument) -> WallpaperDocument
    ): WallpaperDocument? {
        val current = document ?: return null
        val next = mutator(current)
        if (next == current) {
            return current
        }
        pushUndo(current)
        document = next
        redoStack.clear()
        clearCommandMergeState()
        return next
    }

    /** 回退到上一份历史快照。 */
    fun undo(): WallpaperDocument? {
        if (undoStack.isEmpty() || document == null) {
            return document
        }
        redoStack.addLast(document!!)
        document = undoStack.removeLast()
        clearCommandMergeState()
        return document
    }

    /** 恢复最近一次撤销的快照。 */
    fun redo(): WallpaperDocument? {
        if (redoStack.isEmpty() || document == null) {
            return document
        }
        pushUndo(document!!)
        document = redoStack.removeLast()
        clearCommandMergeState()
        return document
    }

    /** 把旧快照压入撤销栈，并控制历史长度上限。 */
    private fun pushUndo(previous: WallpaperDocument) {
        undoStack.addLast(previous)
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
    }

    /** 只要历史被打断，就清空命令合并状态。 */
    private fun clearCommandMergeState() {
        lastCommand = null
        lastCommandUptimeMillis = 0L
    }
}

/** 图层树上的常用查找、更新和删除工具函数。 */
object DocumentLayerOps {
    /** 判断目标图层是否存在。 */
    fun hasLayer(
        document: WallpaperDocument,
        layerId: String
    ): Boolean {
        return findLayer(document, layerId) != null
    }

    /** 在整棵图层树里查找目标图层。 */
    fun findLayer(
        document: WallpaperDocument,
        layerId: String
    ): LayerDocument? {
        return findLayerInList(document.layers, layerId)
    }

    /** 递归更新命中的单个图层。 */
    fun updateLayer(
        document: WallpaperDocument,
        layerId: String,
        updater: (LayerDocument) -> LayerDocument
    ): WallpaperDocument {
        val (updatedLayers, changed) = updateLayerInList(
            layers = document.layers,
            layerId = layerId,
            updater = updater
        )
        return if (changed) {
            document.copy(layers = updatedLayers)
        } else {
            document
        }
    }

    /** 递归删除命中的单个图层。 */
    fun deleteLayer(
        document: WallpaperDocument,
        layerId: String
    ): WallpaperDocument {
        val (updatedLayers, deleted) = deleteLayerInList(
            layers = document.layers,
            layerId = layerId
        )
        return if (deleted) {
            document.copy(layers = updatedLayers)
        } else {
            document
        }
    }

    /** 从后往前查找，保证和界面里的图层叠放顺序一致。 */
    private fun findLayerInList(
        layers: List<LayerDocument>,
        layerId: String
    ): LayerDocument? {
        for (layer in layers.asReversed()) {
            if (layer.id == layerId) {
                return layer
            }
            if (layer is GroupLayerDocument) {
                val nested = findLayerInList(layer.children, layerId)
                if (nested != null) {
                    return nested
                }
            }
        }
        return null
    }

    /** 递归更新列表中的目标图层，并把变化一路向上回写。 */
    private fun updateLayerInList(
        layers: List<LayerDocument>,
        layerId: String,
        updater: (LayerDocument) -> LayerDocument
    ): Pair<List<LayerDocument>, Boolean> {
        var changed = false
        val updated = layers.map { layer ->
            when {
                layer.id == layerId -> {
                    val next = updater(layer)
                    if (next != layer) {
                        changed = true
                    }
                    next
                }

                layer is GroupLayerDocument -> {
                    val (children, childChanged) = updateLayerInList(
                        layers = layer.children,
                        layerId = layerId,
                        updater = updater
                    )
                    if (childChanged) {
                        changed = true
                        layer.copy(children = children)
                    } else {
                        layer
                    }
                }

                else -> layer
            }
        }
        return updated to changed
    }

    /** 递归删除列表中的目标图层，并保留其余图层顺序。 */
    private fun deleteLayerInList(
        layers: List<LayerDocument>,
        layerId: String
    ): Pair<List<LayerDocument>, Boolean> {
        var deleted = false
        val kept = ArrayList<LayerDocument>(layers.size)
        for (layer in layers) {
            if (layer.id == layerId) {
                deleted = true
                continue
            }
            if (layer is GroupLayerDocument) {
                val (children, childDeleted) = deleteLayerInList(
                    layers = layer.children,
                    layerId = layerId
                )
                if (childDeleted) {
                    deleted = true
                    kept += layer.copy(children = children)
                } else {
                    kept += layer
                }
            } else {
                kept += layer
            }
        }
        return kept to deleted
    }
}
