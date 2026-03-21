package uk.co.maybeitsadam.literatureclock

import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

class LiteratureQuoteDataSource : ComplicationDataSourceService() {

    companion object {
        private const val TAG = "LitQuoteDataSource"
        private const val TIMELINE_MINUTES = 90
        private const val FALLBACK_QUOTE = "Time is the longest distance between two places."
    }

    private val quotesMap: Map<String, List<QuoteEntry>> by lazy {
        QuoteRepository.loadQuotesFromAssets(this)
    }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val now = ZonedDateTime.now()
        val minuteStart = now.truncatedTo(ChronoUnit.MINUTES)

        val currentEntry = findQuote(timeKeyFor(minuteStart.toLocalTime()))
        val defaultData = buildComplicationData(currentEntry)

        val timelineEntries = mutableListOf<TimelineEntry>()
        for (offset in 1..TIMELINE_MINUTES) {
            val entryStart = minuteStart.plusMinutes(offset.toLong())
            val entryEnd = entryStart.plusMinutes(1)
            val quote = findQuote(timeKeyFor(entryStart.toLocalTime()))

            timelineEntries.add(
                TimelineEntry(
                    validity = TimeInterval(entryStart.toInstant(), entryEnd.toInstant()),
                    complicationData = buildComplicationData(quote)
                )
            )
        }

        val timeline = ComplicationDataTimeline(defaultData, timelineEntries)
        Log.d(TAG, "Built timeline: 1 default + ${timelineEntries.size} entries from $minuteStart")
        listener.onComplicationDataTimeline(timeline)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        val preview = QuoteEntry(
            time = "10:08",
            quoteFirst = "It was exactly ",
            quoteTime = "ten past ten",
            quoteLast = " when the first stranger arrived.",
            title = "The Night Circus",
            author = "Erin Morgenstern"
        )
        return buildComplicationData(preview)
    }

    private fun timeKeyFor(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    private fun findQuote(timeKey: String): QuoteEntry {
        val filterNsfw = getSharedPreferences("literature_clock_prefs", MODE_PRIVATE)
            .getBoolean("filter_nsfw", true)

        fun candidates(key: String): List<QuoteEntry> {
            val entries = quotesMap[key] ?: return emptyList()
            return if (filterNsfw) entries.filter { !it.nsfw && !it.easterEgg } else entries
        }

        candidates(timeKey).randomOrNull()?.let { return it }

        val parts = timeKey.split(":")
        val hour = parts[0].toIntOrNull() ?: return fallbackEntry()
        val minute = parts[1].toIntOrNull() ?: return fallbackEntry()

        for (delta in 1..59) {
            val earlier = (minute - delta + 60) % 60
            val key = String.format(Locale.US, "%02d:%02d", hour, earlier)
            candidates(key).randomOrNull()?.let {
                Log.d(TAG, "No quote for $timeKey, fell back to $key")
                return it
            }
        }

        return fallbackEntry()
    }

    private fun fallbackEntry() = QuoteEntry(
        time = "00:00",
        quoteFirst = "",
        quoteTime = FALLBACK_QUOTE,
        quoteLast = "",
        title = "The Glass Menagerie",
        author = "Tennessee Williams"
    )

    private fun buildComplicationData(entry: QuoteEntry): LongTextComplicationData {
        val fullQuote = "${entry.quoteFirst}${entry.quoteTime}${entry.quoteLast}"
        val attribution = "— ${entry.author}, ${entry.title}"

        return LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(fullQuote).build(),
            contentDescription = PlainComplicationText.Builder(
                "Literary quote: $fullQuote by ${entry.author}"
            ).build()
        )
            .setTitle(PlainComplicationText.Builder(attribution).build())
            .build()
    }
}
