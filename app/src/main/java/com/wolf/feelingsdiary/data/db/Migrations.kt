package com.wolf.feelingsdiary.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wolf.feelingsdiary.domain.FeelingSuggester

object Migrations {
    /**
     * Version 2 adds twelve base feelings. A custom feeling named like one of them (English or German,
     * ignoring case, accents and surrounding spaces) becomes that base feeling and keeps its id, colour,
     * valence, hidden flag and entries; when several match, the oldest wins and the rest stay custom.
     * The remaining version-2 base feelings are appended in [BaseFeelings.all] order.
     */
    val MIGRATION_1_2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val existingKeys = mutableSetOf<String>()
                val customs = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, baseKey, customName FROM feelings ORDER BY id").use { c ->
                    while (c.moveToNext()) {
                        if (c.isNull(1)) customs += c.getLong(0) to c.getString(2).orEmpty() else existingKeys += c.getString(1)
                    }
                }
                val keyByName =
                    V2_NAMES
                        .filterKeys { it !in existingKeys }
                        .flatMap { (key, names) -> names.map { FeelingSuggester.normalize(it) to key } }
                        .toMap()
                for ((id, name) in customs) {
                    val key = keyByName[FeelingSuggester.normalize(name.trim())] ?: continue
                    if (key in existingKeys) continue
                    val values =
                        ContentValues().apply {
                            put("baseKey", key)
                            putNull("customName")
                        }
                    db.update("feelings", SQLiteDatabase.CONFLICT_ABORT, values, "id = ?", arrayOf<Any?>(id))
                    existingKeys += key
                }
                BaseFeelings.all.filter { it.key in V2_NAMES && it.key !in existingKeys }.forEach { f ->
                    val values =
                        ContentValues().apply {
                            put("baseKey", f.key)
                            putNull("customName")
                            put("colorArgb", f.colorArgb)
                            put("valence", f.valence)
                            put("hidden", 0)
                            putNull("lastUsedAt")
                        }
                    db.insert("feelings", SQLiteDatabase.CONFLICT_ABORT, values)
                }
            }
        }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)

    /** English and German labels of the base feelings added in version 2, frozen for matching custom names. */
    private val V2_NAMES: Map<String, List<String>> =
        mapOf(
            "proud" to listOf("proud", "stolz"),
            "excited" to listOf("excited", "begeistert"),
            "content" to listOf("content", "zufrieden"),
            "motivated" to listOf("motivated", "motiviert"),
            "hopeful" to listOf("hopeful", "hoffnungsvoll"),
            "curious" to listOf("curious", "neugierig"),
            "thoughtful" to listOf("thoughtful", "nachdenklich"),
            "disappointed" to listOf("disappointed", "enttäuscht"),
            "frustrated" to listOf("frustrated", "frustriert"),
            "annoyed" to listOf("annoyed", "genervt"),
            "overwhelmed" to listOf("overwhelmed", "überfordert"),
            "exhausted" to listOf("exhausted", "erschöpft"),
        )
}
