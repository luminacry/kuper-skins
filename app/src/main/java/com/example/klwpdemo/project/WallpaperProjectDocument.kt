package com.example.klwpdemo.project

import com.example.klwpdemo.document.WallpaperDocument

/** 壁纸工程的正式领域对象。 */
data class WallpaperProjectDocument(
    val id: String,
    val name: String,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceViewportWidth: Int? = null,
    val sourceViewportHeight: Int? = null,
    val document: WallpaperDocument
) {
    companion object {
        /** 当前工程文件的 schema 版本。 */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** 项目列表页后续可直接复用的轻量摘要。 */
data class WallpaperProjectSummary(
    val id: String,
    val name: String,
    val updatedAtMillis: Long
)
