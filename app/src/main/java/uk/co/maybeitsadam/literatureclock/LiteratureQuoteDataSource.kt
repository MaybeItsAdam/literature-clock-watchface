package uk.co.maybeitsadam.literatureclock

import android.content.Context
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
import org.json.JSONArray
import java.io.IOException
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Complication data source that provides literary quotes keyed to the current minute.
 *
 * Uses ComplicationDataTimeline to batch the next 60 minutes of quotes in a single
 * response. The system swaps entries automatically — no per-minute wakeups needed.
 * UPDATE_PERIOD_SECONDS (3600) triggers a refresh once per hour to replenish the timeline.
 */
class LiteratureQuoteDataSource : ComplicationDataSourceService() {

    companion object {
        private const val TAG = "LitQuoteDataSource"
        private const val PREFS_NAME = "literature_clock_prefs"
        private const val KEY_FILTER_NSFW = "filter_nsfw"
        private const val KEY_STOP_BEING_ANNOYING = "stop_being_annoying"
        private const val TIMELINE_MINUTES = 90 // buffer beyond the 60-min update period
        private const val FALLBACK_QUOTE = "Time is the longest distance between two places."
    }

    /** Lazily loaded and cached map of "HH:mm" → list of QuoteEntries */
    private val quotesMap: Map<String, List<QuoteEntry>> by lazy { loadQuotes() }

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val now = ZonedDateTime.now()
        val minuteStart = now.truncatedTo(ChronoUnit.MINUTES)

        // Current minute's quote becomes the default (shown immediately)
        val currentEntry = findQuote(timeKeyFor(minuteStart.toLocalTime()))
        val defaultData = buildComplicationData(currentEntry)

        // Build timeline entries for the next TIMELINE_MINUTES
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

    // ──────────────────────────────────────────────────────────────
    // Data loading
    // ──────────────────────────────────────────────────────────────

    private fun loadQuotes(): Map<String, List<QuoteEntry>> {
        return try {
            val json = assets.open("quotes.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            val map = mutableMapOf<String, MutableList<QuoteEntry>>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val entry = QuoteEntry(
                    time = obj.getString("time"),
                    quoteFirst = obj.optString("quote_first", ""),
                    quoteTime = obj.optString("quote_time", ""),
                    quoteLast = obj.optString("quote_last", ""),
                    title = obj.optString("title", ""),
                    author = obj.optString("author", ""),
                    nsfw = obj.optBoolean("nsfw", false),
                    easterEgg = obj.optBoolean("easter_egg", false)
                )
                map.getOrPut(entry.time) { mutableListOf() }.add(entry)
            }
            Log.d(TAG, "Loaded ${array.length()} quotes across ${map.size} minutes")
            map
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load quotes.json", e)
            emptyMap()
        }
    }

    // ──────────────────────────────────────────────────────────────
    // Quote selection
    // ──────────────────────────────────────────────────────────────

    private fun prefs() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun isNsfwFiltered(): Boolean =
        prefs().getBoolean(KEY_FILTER_NSFW, true)

    private fun isStopBeingAnnoying(): Boolean =
        prefs().getBoolean(KEY_STOP_BEING_ANNOYING, false)

    private fun timeKeyFor(time: LocalTime): String =
        String.format(Locale.US, "%02d:%02d", time.hour, time.minute)

    /**
     * Look up the exact minute, filter by NSFW / easter-egg preferences,
     * and pick a random quote. If no allowed quotes exist for this minute,
     * rounds down minute-by-minute (max 60). Falls back to a hardcoded quote.
     */
    private fun findQuote(timeKey: String): QuoteEntry {
        val filterNsfw = isNsfwFiltered()
        val filterEasterEggs = isStopBeingAnnoying()

        fun candidates(key: String): List<QuoteEntry> {
            val entries = quotesMap[key] ?: return emptyList()
            return entries.filter { entry ->
                (!filterNsfw || !entry.nsfw) && (!filterEasterEggs || !entry.easterEgg)
            }
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

    // ──────────────────────────────────────────────────────────────
    // Complication building
    // ──────────────────────────────────────────────────────────────

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

    // ──────────────────────────────────────────────────────────────
    // Model
    // ──────────────────────────────────────────────────────────────

    private data class QuoteEntry(
        val time: String,
        val quoteFirst: String,
        val quoteTime: String,
        val quoteLast: String,
        val title: String,
        val author: String,
        val nsfw: Boolean = false,
        val easterEgg: Boolean = false
    )
}
