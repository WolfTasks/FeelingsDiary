package com.wolf.feelingsdiary.data.db

import androidx.annotation.StringRes
import com.wolf.feelingsdiary.R

data class BaseFeeling(
    val key: String,
    @StringRes val label: Int,
    val colorArgb: Int,
    val valence: Int,
)

object BaseFeelings {
    /** Seed order; ids are assigned ascending in this order. */
    val all: List<BaseFeeling> =
        listOf(
            BaseFeeling("happy", R.string.feeling_happy, 0xFFF2B705.toInt(), 2),
            BaseFeeling("grateful", R.string.feeling_grateful, 0xFF2FB8A8.toInt(), 2),
            BaseFeeling("in_love", R.string.feeling_in_love, 0xFFE86AA6.toInt(), 2),
            BaseFeeling("relaxed", R.string.feeling_relaxed, 0xFF3FAE5A.toInt(), 1),
            BaseFeeling("neutral", R.string.feeling_neutral, 0xFF9AA0A6.toInt(), 0),
            BaseFeeling("tired", R.string.feeling_tired, 0xFF8A7FD0.toInt(), -1),
            BaseFeeling("bored", R.string.feeling_bored, 0xFFA68A64.toInt(), -1),
            BaseFeeling("stressed", R.string.feeling_stressed, 0xFFF07B1D.toInt(), -1),
            BaseFeeling("anxious", R.string.feeling_anxious, 0xFFC77DDB.toInt(), -1),
            BaseFeeling("lonely", R.string.feeling_lonely, 0xFF5F7FA8.toInt(), -2),
            BaseFeeling("sad", R.string.feeling_sad, 0xFF4A84F0.toInt(), -2),
            BaseFeeling("angry", R.string.feeling_angry, 0xFFE04848.toInt(), -2),
        )

    @StringRes
    fun labelFor(key: String): Int = all.first { it.key == key }.label
}
