package com.wolf.feelingsdiary.export

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.Entry
import com.wolf.feelingsdiary.domain.EntryTime
import com.wolf.feelingsdiary.domain.Feeling
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.PeriodKind
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiaryExporterTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val happy = Feeling(1, "happy", null, 0xFFE8B730.toInt(), 2, false, null)
    private val repo =
        mockk<DiaryRepository> {
            every { observeFeelings() } returns flowOf(listOf(happy))
            every { observeEntriesBetween(any(), any()) } answers {
                val from = firstArg<LocalDate>()
                val minute = (from.plusDays(1).atTime(10, 0).toEpochSecond(ZoneOffset.UTC) / 60).toInt()
                flowOf(listOf(Entry(1, EntryTime(minute, 0), 2, listOf(1L), listOf(2))))
            }
        }
    private val exporter = DiaryExporter(ctx, repo, FeelingLabeler { it.baseKey!! }, LocaleSource().apply { update(Locale.ENGLISH) })
    private val november = GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
    private val december = GraphPeriod(PeriodKind.MONTH, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31))
    private val dir get() = File(ctx.cacheDir, "export")

    private fun names() =
        dir
            .listFiles()
            .orEmpty()
            .map { it.name }
            .sorted()

    @Test
    fun csvOnlyWritesBomCsv() =
        runTest {
            val result = exporter.writeFiles(november, DayOfWeek.MONDAY, csv = true, graph = false)
            assertEquals(1, result.files.size)
            assertEquals("text/csv", result.mimeType)
            assertTrue(result.subject, result.subject.contains("Feelings diary"))
            assertEquals(listOf("feelings_2026-11-01_2026-11-30.csv"), names())
            val text = File(dir, names().single()).readText(Charsets.UTF_8)
            assertEquals(Char(0xFEFF), text.first())
            assertTrue(text, text.contains("2026-11-02,10:00,UTC+0,happy,happy (+2),2.00,2"))
        }

    @Test
    fun bothWritesTwoFiles() =
        runTest {
            val result = exporter.writeFiles(november, DayOfWeek.MONDAY, csv = true, graph = true)
            assertEquals(2, result.files.size)
            assertEquals("*/*", result.mimeType)
            assertEquals(listOf("feelings_2026-11-01_2026-11-30.csv", "feelings_2026-11-01_2026-11-30.png"), names())
            val png = BitmapFactory.decodeFile(File(dir, "feelings_2026-11-01_2026-11-30.png").path)
            assertEquals(GraphImageRenderer.WIDTH, png.width)
            assertEquals(GraphImageRenderer.HEIGHT, png.height)
        }

    @Test
    fun graphOnlyIsPng() =
        runTest {
            assertEquals("image/png", exporter.writeFiles(november, DayOfWeek.MONDAY, csv = false, graph = true).mimeType)
        }

    @Test
    fun secondExportRemovesOldFiles() =
        runTest {
            exporter.writeFiles(november, DayOfWeek.MONDAY, csv = true, graph = true)
            exporter.writeFiles(december, DayOfWeek.MONDAY, csv = true, graph = true)
            assertEquals(listOf("feelings_2026-12-01_2026-12-31.csv", "feelings_2026-12-01_2026-12-31.png"), names())
        }

    @Test
    fun clearCacheEmptiesExportDir() =
        runTest {
            exporter.writeFiles(november, DayOfWeek.MONDAY, csv = true, graph = false)
            exporter.clearCache()
            assertEquals(emptyList<String>(), names())
        }

    @Test
    fun urisUseExportAuthority() =
        runTest {
            // Robolectric's FileProvider compares paths with '/', so on Windows hosts it rejects every file.
            assumeTrue(File.separatorChar == '/')
            val uri = exporter.export(november, DayOfWeek.MONDAY, csv = true, graph = false).uris.single()
            assertEquals("${ctx.packageName}.export", uri.authority)
        }

    @Test
    fun clearStaleKeepsRecentFiles() {
        dir.mkdirs()
        val now = 10_000_000_000L
        val old = File(dir, "old.csv").apply { writeText("x") }
        val recent = File(dir, "recent.csv").apply { writeText("x") }
        old.setLastModified(now - DiaryExporter.STALE_AFTER_MILLIS - 1)
        recent.setLastModified(now - DiaryExporter.STALE_AFTER_MILLIS + 60_000)
        exporter.clearStale(now)
        assertEquals(listOf("recent.csv"), names())
    }
}
