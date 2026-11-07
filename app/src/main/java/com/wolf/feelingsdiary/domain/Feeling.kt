package com.wolf.feelingsdiary.domain

data class Feeling(
    val id: Long,
    val baseKey: String?,
    val customName: String?,
    val colorArgb: Int,
    val valence: Int,
    val hidden: Boolean,
    val lastUsedAt: Long?,
)
