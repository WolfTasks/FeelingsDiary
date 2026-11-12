package com.wolf.feelingsdiary.data

import android.content.Context
import android.database.Cursor
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wolf.feelingsdiary.data.db.AppDatabase
import com.wolf.feelingsdiary.data.db.BaseFeelings
import com.wolf.feelingsdiary.data.db.Migrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    // An absolute path: with a bare name, Room's driver rejects the resolved path it is handed under Robolectric.
    private val dbName: String =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath("migration-test.db")
            .absolutePath

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val v1Keys =
        listOf("happy", "grateful", "in_love", "relaxed", "neutral", "tired", "bored", "stressed", "anxious", "lonely", "sad", "angry")

    private fun SupportSQLiteDatabase.seedV1() {
        v1Keys.forEach { key ->
            execSQL("INSERT INTO feelings (baseKey, customName, colorArgb, valence, hidden) VALUES ('$key', NULL, 1, 0, 0)")
        }
    }

    private fun SupportSQLiteDatabase.addCustom(
        name: String,
        color: Int,
        valence: Int,
        hidden: Int = 0,
        lastUsedAt: Long? = null,
    ) = execSQL(
        "INSERT INTO feelings (baseKey, customName, colorArgb, valence, hidden, lastUsedAt) VALUES (NULL, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(name, color, valence, hidden, lastUsedAt),
    )

    private data class Row(
        val id: Long,
        val baseKey: String?,
        val customName: String?,
        val color: Int,
        val valence: Int,
        val hidden: Int,
        val lastUsedAt: Long?,
    )

    private fun SupportSQLiteDatabase.feelings(): List<Row> =
        query("SELECT id, baseKey, customName, colorArgb, valence, hidden, lastUsedAt FROM feelings ORDER BY id").use { cursor: Cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        Row(
                            cursor.getLong(0),
                            cursor.getString(1),
                            cursor.getString(2),
                            cursor.getInt(3),
                            cursor.getInt(4),
                            cursor.getInt(5),
                            if (cursor.isNull(6)) null else cursor.getLong(6),
                        ),
                    )
                }
            }
        }

    @Test
    fun addsMissingBaseFeelingsAfterExistingOnes() {
        helper.createDatabase(dbName, 1).apply {
            seedV1()
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, *Migrations.ALL)
        val rows = db.feelings()
        assertEquals(BaseFeelings.all.map { it.key }, rows.map { it.baseKey })
        val added = rows.drop(v1Keys.size)
        assertEquals(BaseFeelings.all.drop(v1Keys.size).map { it.valence }, added.map { it.valence })
        assertEquals(BaseFeelings.all.drop(v1Keys.size).map { it.colorArgb }, added.map { it.color })
    }

    @Test
    fun matchingCustomFeelingsBecomeBaseFeelingsKeepingTheirData() {
        helper.createDatabase(dbName, 1).apply {
            seedV1()
            addCustom("Frustriert ", color = 111, valence = -2, hidden = 1, lastUsedAt = 5L) // id 13
            addCustom("genervt", color = 222, valence = -1) // id 14
            addCustom("Wehmut", color = 333, valence = -1) // id 15
            addCustom("frustrated", color = 444, valence = 0) // id 16, same base key as id 13
            addCustom("ÜBERFORDERT", color = 555, valence = -2) // id 17, accents and case ignored
            execSQL("INSERT INTO entries (id, epochMinute, utcOffsetMinutes, people) VALUES (1, 100, 60, 0)")
            execSQL("INSERT INTO entry_feelings (entryId, position, feelingId, valence) VALUES (1, 0, 13, -2)")
            execSQL("INSERT INTO entry_feelings (entryId, position, feelingId, valence) VALUES (1, 1, 14, -1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, *Migrations.ALL)
        val byId = db.feelings().associateBy { it.id }

        assertEquals(Row(13, "frustrated", null, 111, -2, 1, 5L), byId.getValue(13))
        assertEquals(Row(14, "annoyed", null, 222, -1, 0, null), byId.getValue(14))
        assertEquals(Row(15, null, "Wehmut", 333, -1, 0, null), byId.getValue(15))
        assertEquals(Row(16, null, "frustrated", 444, 0, 0, null), byId.getValue(16))
        assertEquals(Row(17, "overwhelmed", null, 555, -2, 0, null), byId.getValue(17))

        val keys = byId.values.mapNotNull { it.baseKey }
        assertEquals(BaseFeelings.all.map { it.key }.toSet(), keys.toSet())
        assertEquals(keys.size, keys.toSet().size)
        // New base feelings follow every existing row, in seed order.
        val inserted = byId.values.filter { it.id > 17 }.map { it.baseKey }
        assertEquals(BaseFeelings.all.map { it.key }.filter { it !in v1Keys + listOf("frustrated", "annoyed", "overwhelmed") }, inserted)

        db.query("SELECT feelingId, valence FROM entry_feelings WHERE entryId = 1 ORDER BY position").use { cursor ->
            cursor.moveToNext()
            assertEquals(13L to -2, cursor.getLong(0) to cursor.getInt(1))
            cursor.moveToNext()
            assertEquals(14L to -1, cursor.getLong(0) to cursor.getInt(1))
        }
        assertNull(byId.values.firstOrNull { it.baseKey != null && it.customName != null })
    }
}
