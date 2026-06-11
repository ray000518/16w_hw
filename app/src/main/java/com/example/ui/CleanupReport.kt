package com.example.ui

import kotlinx.serialization.Serializable

@Serializable
data class CleanupReport(
    val report_summary: String = "",
    val location_mentioned: String? = null,
    val trash_items: List<TrashItem> = emptyList(),
    val pollution_level: String = "",
    val hazard_tags: List<String> = emptyList(),
    val need_heavy_machinery: Boolean = false
)

@Serializable
data class TrashItem(
    val category: String = "",
    val estimated_quantity: String = "",
    val confidence_level: String = ""
)
