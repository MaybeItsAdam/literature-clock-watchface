package uk.co.maybeitsadam.literatureclock

import android.content.Context
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import org.json.JSONArray
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Complication data source that provides literary quotes keyed to the current minute.
 *
 * Reads from assets/quotes.json — each entry has:
 *   time, quote_first, quote_time, quote_last, title, author
 *
 * The full quote is assembled as: quote_first + quote_time + quote_last
 * The attribution line is: "— Author, Title"
 */
class LiteratureQuoteDataSource : SuspendingComplicationDataSourceService() {

    companion object {
        private const val TAG = "LitQuoteDataSource"
        private const val PREFS_NAME = "literature_clock_prefs"
        private const val KEY_FILTER_NSFW = "filter_nsfw"
        private const val FALLBACK_QUOTE = "Time is the longest distance between two places."
        private const val FALLBACK_ATTRIBUTION = "— Tennessee Williams, The Glass Menagerie"
    }

    /** Lazily loaded and cached map of "HH:mm" → list of QuoteEntries */
    private val quotesMap: Map<String, List<QuoteEntry>> by lazy { loadQuotes() }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        MinuteUpdateReceiver.schedule(this)
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        super.onComplicationDeactivated(complicationInstanceId)
        MinuteUpdateReceiver.cancel(this)
    }

    override suspend fun onComplicationRequest(
        request: ComplicationRequest
    ): ComplicationData? {
        // Re-arm the alarm on every request (belt-and-suspenders with the 300s fallback)
        MinuteUpdateReceiver.schedule(this)

        val timeKey = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        val entry = findQuote(timeKey)
        return buildComplicationData(entry)
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
                    nsfw = obj.optBoolean("nsfw", false)
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

    private fun isNsfwFiltered(): Boolean {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FILTER_NSFW, true) // filtered by default
    }

    /**
     * Look up the exact minute, filter by NSFW preference, and pick a random quote.
     * If no allowed quotes exist for this minute, rounds down minute-by-minute (max 60).
     * Falls back to a hardcoded quote.
     */
    private fun findQuote(timeKey: String): QuoteEntry {
        val filterNsfw = isNsfwFiltered()

        fun candidates(key: String): List<QuoteEntry> {
            val entries = quotesMap[key] ?: return emptyList()
            return if (filterNsfw) entries.filter { !it.nsfw } else entries
        }

        candidates(timeKey).randomOrNull()?.let { return it }

        // Round down: try earlier minutes within the same hour
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
        val nsfw: Boolean = false
    )
}
