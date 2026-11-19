package com.wolf.feelingsdiary.export

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import com.wolf.feelingsdiary.R
import com.wolf.feelingsdiary.data.DiaryRepository
import com.wolf.feelingsdiary.domain.CsvExporter
import com.wolf.feelingsdiary.domain.CsvFormat
import com.wolf.feelingsdiary.domain.FeelingSuggester
import com.wolf.feelingsdiary.domain.GraphBuilder
import com.wolf.feelingsdiary.domain.GraphPeriod
import com.wolf.feelingsdiary.domain.UTF8_BOM
import com.wolf.feelingsdiary.ui.FeelingLabeler
import com.wolf.feelingsdiary.ui.LocaleSource
import com.wolf.feelingsdiary.ui.home.formatPeriod
import com.wolf.feelingsdiary.ui.home.xAxisLabels
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

/** Written export files; [mimeType] covers all of them. */
data class ExportFiles(
    val files: List<File>,
    val subject: String,
    val mimeType: String,
)

/** Files to hand to the share sheet; [mimeType] covers all of them. */
data class ExportResult(
    val uris: List<Uri>,
    val subject: String,
    val mimeType: String,
)

/** Writes the CSV and/or graph image of a period to cache/export/ and returns their FileProvider URIs. */
@Singleton
class DiaryExporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val repo: DiaryRepository,
        private val labeler: FeelingLabeler,
        private val localeSource: LocaleSource,
    ) {
        private val dir get() = File(context.cacheDir, EXPORT_DIR)

        suspend fun export(
            period: GraphPeriod,
            firstDayOfWeek: DayOfWeek,
            csv: Boolean,
            graph: Boolean,
        ): ExportResult {
            val written = writeFiles(period, firstDayOfWeek, csv, graph)
            val authority = "${context.packageName}.$EXPORT_DIR"
            return ExportResult(written.files.map { FileProvider.getUriForFile(context, authority, it) }, written.subject, written.mimeType)
        }

        /** Replaces earlier exports with the CSV and/or PNG of [period] in cache/export/. */
        suspend fun writeFiles(
            period: GraphPeriod,
            firstDayOfWeek: DayOfWeek,
            csv: Boolean,
            graph: Boolean,
        ): ExportFiles {
            require(csv || graph) { "nothing to export" }
            return withContext(Dispatchers.IO) {
                val locale = localeSource.locale.value
                val res = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
                val entries = repo.observeEntriesBetween(period.start, period.endInclusive).first()
                val feelings = repo.observeFeelings().first().associateBy { it.id }
                val periodText = formatPeriod(period, locale)
                val subject = res.getString(R.string.export_subject, periodText)
                val base = "${res.getString(R.string.export_file_prefix)}_${period.start}_${period.endInclusive}"

                clearCache()
                dir.mkdirs()
                val files = mutableListOf<File>()
                if (csv) {
                    val headers = CSV_HEADERS.map(res::getString)
                    val text = CsvExporter.build(entries, { id -> feelings[id]?.let(labeler::label) }, headers, CsvFormat.forLocale(locale))
                    files += File(dir, "$base.csv").apply { writeText(UTF8_BOM + text, Charsets.UTF_8) }
                }
                if (graph) {
                    val data = GraphBuilder.build(entries, period, firstDayOfWeek)
                    val used = feelings.values.filter { it.id in data.feelingIds }
                    val image =
                        GraphImage(
                            title = subject,
                            data = data,
                            colorOf = { id -> feelings[id]?.let { Color(it.colorArgb) } ?: Color.Unspecified },
                            xLabels = xAxisLabels(period, locale, data.dayStartMinute),
                            valenceTexts = VALENCE_TEXTS.map(res::getString),
                            legend = FeelingSuggester.baseListOrder(used, labeler::label).map { labeler.label(it) to Color(it.colorArgb) },
                            peopleLabel = res.getString(R.string.history_people_axis),
                            moreLabel = { n -> res.getString(R.string.export_legend_more, n) },
                        )
                    val bitmap = GraphImageRenderer.render(context, image)
                    files +=
                        File(dir, "$base.png").apply {
                            outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
                        }
                    bitmap.recycle()
                }
                ExportFiles(
                    files = files,
                    subject = subject,
                    mimeType =
                        if (files.size > 1) {
                            "*/*"
                        } else if (csv) {
                            "text/csv"
                        } else {
                            "image/png"
                        },
                )
            }
        }

        /** Removes earlier exports; the share target has had its copy by then. */
        fun clearCache() {
            dir.deleteRecursively()
        }

        /**
         * App start: removes only exports older than [STALE_AFTER_MILLIS]. A share target that reads its file late
         * (e.g. a mail app sending later) may restart the process via the FileProvider, so fresh files must survive.
         */
        fun clearStale(nowMillis: Long = System.currentTimeMillis()) {
            dir
                .listFiles()
                .orEmpty()
                .filter { nowMillis - it.lastModified() > STALE_AFTER_MILLIS }
                .forEach { it.delete() }
        }

        companion object {
            const val STALE_AFTER_MILLIS = 60 * 60 * 1000L
            private const val EXPORT_DIR = "export"
            private const val PNG_QUALITY = 100
            private val CSV_HEADERS =
                listOf(
                    R.string.export_csv_date,
                    R.string.export_csv_time,
                    R.string.export_csv_zone,
                    R.string.export_csv_main,
                    R.string.export_csv_feelings,
                    R.string.export_csv_mood,
                    R.string.export_csv_people,
                )
            private val VALENCE_TEXTS = listOf(R.string.valence_good, R.string.valence_neutral, R.string.valence_bad)
        }
    }
