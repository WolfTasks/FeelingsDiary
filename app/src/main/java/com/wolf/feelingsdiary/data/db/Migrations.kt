package com.wolf.feelingsdiary.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.text.Normalizer
import java.util.Locale

/** Room migrations. Each one is frozen: it never reads live app data such as [BaseFeelings.all]. */
object Migrations {
    private class V2Feeling(
        val key: String,
        val colorArgb: Int,
        val valence: Int,
        val names: List<String>,
    )

    /** Base feelings added in version 2, in seed order, with their English and German labels. */
    private val V2_FEELINGS =
        listOf(
            V2Feeling("proud", 0xFFB8860B.toInt(), 2, listOf("proud", "stolz")),
            V2Feeling("excited", 0xFFFF7A59.toInt(), 2, listOf("excited", "begeistert")),
            V2Feeling("content", 0xFF8BC34A.toInt(), 1, listOf("content", "zufrieden")),
            V2Feeling("motivated", 0xFF00A3E0.toInt(), 1, listOf("motivated", "motiviert")),
            V2Feeling("hopeful", 0xFFF5C26B.toInt(), 1, listOf("hopeful", "hoffnungsvoll")),
            V2Feeling("curious", 0xFF5C6BC0.toInt(), 1, listOf("curious", "neugierig")),
            V2Feeling("thoughtful", 0xFF6D8B74.toInt(), 0, listOf("thoughtful", "nachdenklich")),
            V2Feeling("disappointed", 0xFF7E6A9A.toInt(), -1, listOf("disappointed", "enttäuscht")),
            V2Feeling("frustrated", 0xFFB03A5B.toInt(), -1, listOf("frustrated", "frustriert")),
            V2Feeling("annoyed", 0xFFC6A700.toInt(), -1, listOf("annoyed", "genervt")),
            V2Feeling("overwhelmed", 0xFF6A1B9A.toInt(), -2, listOf("overwhelmed", "überfordert")),
            V2Feeling("exhausted", 0xFF546E7A.toInt(), -2, listOf("exhausted", "erschöpft")),
        )

    /** Lower case without accents or surrounding spaces; a frozen copy of the v1 name comparison. */
    private fun normalizeV2(text: String): String =
        Normalizer
            .normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }

    /**
     * Version 2 adds twelve base feelings. A custom feeling named like one of them (English or German,
     * ignoring case, accents and surrounding spaces) becomes that base feeling and keeps its id, colour,
     * valence, hidden flag and entries; when several match, the oldest wins and the rest stay custom.
     * The remaining version-2 base feelings are appended in seed order.
     */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingKeys = mutableSetOf<String>()
                val customs = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, baseKey, customName FROM feelings ORDER BY id").use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.isNull(1)) {
                            customs += cursor.getLong(0) to cursor.getString(2).orEmpty()
                        } else {
                            existingKeys += cursor.getString(1)
                        }
                    }
                }
                val keyByName =
                    V2_FEELINGS
                        .filter { it.key !in existingKeys }
                        .flatMap { feeling -> feeling.names.map { normalizeV2(it) to feeling.key } }
                        .toMap()
                for ((id, name) in customs) {
                    val key = keyByName[normalizeV2(name)] ?: continue
                    if (key in existingKeys) continue
                    val values =
                        ContentValues().apply {
                            put("baseKey", key)
                            putNull("customName")
                        }
                    db.update("feelings", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf<Any?>(id))
                    existingKeys += key
                }
                V2_FEELINGS.filter { it.key !in existingKeys }.forEach { feeling ->
                    val values =
                        ContentValues().apply {
                            put("baseKey", feeling.key)
                            putNull("customName")
                            put("colorArgb", feeling.colorArgb)
                            put("valence", feeling.valence)
                            put("hidden", 0)
                            putNull("lastUsedAt")
                        }
                    db.insert("feelings", SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
