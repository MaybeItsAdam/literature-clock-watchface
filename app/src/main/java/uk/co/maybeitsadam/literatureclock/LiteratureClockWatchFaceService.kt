package uk.co.maybeitsadam.literatureclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.util.JsonReader
import android.util.Log
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import java.io.IOException
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.util.Locale

class LiteratureClockLightWatchFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val quotes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            QuoteRepository.loadQuotesFromAssets(applicationContext)
        }
        val renderer = LiteratureClockRenderer(
            context = this,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            quotesMap = quotes,
            darkMode = false
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

class LiteratureClockDarkWatchFaceService : WatchFaceService() {
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val quotes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            QuoteRepository.loadQuotesFromAssets(applicationContext)
        }
        val renderer = LiteratureClockRenderer(
            context = this,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            quotesMap = quotes,
            darkMode = true
        )
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}

object QuoteRepository {
    private const val TAG = "LitClockWatchFace"

    fun loadQuotesFromAssets(context: Context): Map<String, List<QuoteEntry>> {
        return try {
            val map = mutableMapOf<String, List<QuoteEntry>>()
            var total = 0
            val reader = JsonReader(InputStreamReader(context.assets.open("quotes.json")))
            reader.beginObject()
            while (reader.hasNext()) {
                val timeKey = reader.nextName()
                val list = mutableListOf<QuoteEntry>()
                reader.beginArray()
                while (reader.hasNext()) {
                    var qf = ""; var qt = ""; var ql = ""
                    var ti = ""; var au = ""
                    var nsfw = false; var egg = false
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "qf" -> qf = reader.nextString()
                            "qt" -> qt = reader.nextString()
                            "ql" -> ql = reader.nextString()
                            "ti" -> ti = reader.nextString()
                            "au" -> au = reader.nextString()
                            "n" -> nsfw = reader.nextBoolean()
                            "e" -> egg = reader.nextBoolean()
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                    list.add(QuoteEntry(timeKey, qf, qt, ql, ti, au, nsfw, egg))
                    total++
                }
                reader.endArray()
                map[timeKey] = list
            }
            reader.endObject()
            reader.close()
            Log.d(TAG, "Loaded $total quotes across ${map.size} minutes")
            map
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load quotes.json", e)
            emptyMap()
        }
    }
}

internal class LiteratureClockRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    currentUserStyleRepository: CurrentUserStyleRepository,
    private val quotesMap: Map<String, List<QuoteEntry>>,
    private val darkMode: Boolean
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.HARDWARE,
    interactiveDrawModeUpdateDelayMillis = 60_000L
) {
    private val tfRegular: Typeface = loadFont(R.font.merriweather_regular)
    private val tfItalic: Typeface = loadFont(R.font.merriweather_italic)

    private val lightBg = Color.parseColor("#F2F0E6")
    private val lightText = Color.parseColor("#333333")
    private val lightAttrib = Color.parseColor("#777777")

    private val darkBg = Color.parseColor("#181A1B")
    private val darkText = Color.parseColor("#A69E92")
    private val darkAttrib = Color.parseColor("#7A7468")

    private val ambientText = Color.parseColor("#666666")
    private val ambientAttrib = Color.parseColor("#444444")

    private val quotePaint = TextPaint().apply { isAntiAlias = true; typeface = tfRegular }
    private val attribPaint = TextPaint().apply { isAntiAlias = true; typeface = tfItalic }

    private var lastMinute = ""
    private var cachedQuote: QuoteEntry? = null

    private companion object {
        private const val PREFS_NAME = "literature_clock_prefs"
        private const val KEY_FILTER_NSFW = "filter_nsfw"
        private const val KEY_STOP_BEING_ANNOYING = "stop_being_annoying"
    }

    private fun loadFont(resId: Int): Typeface =
        ResourcesCompat.getFont(context, resId) ?: Typeface.SERIF

    private fun prefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT

        val bgColor = when {
            isAmbient -> Color.BLACK
            darkMode -> darkBg
            else -> lightBg
        }
        canvas.drawColor(bgColor)

        val timeKey = String.format(
            Locale.US, "%02d:%02d",
            zonedDateTime.hour, zonedDateTime.minute
        )
        val entry = getQuoteForMinute(timeKey)

        val textColor = when {
            isAmbient -> ambientText
            darkMode -> darkText
            else -> lightText
        }
        val attribColor = when {
            isAmbient -> ambientAttrib
            darkMode -> darkAttrib
            else -> lightAttrib
        }

        quotePaint.isAntiAlias = !isAmbient
        attribPaint.isAntiAlias = !isAmbient

        val quoteSpannable = SpannableStringBuilder().apply {
            append(entry.quoteFirst)
            val boldStart = length
            append(entry.quoteTime)
            val boldEnd = length
            if (boldStart < boldEnd) {
                setSpan(
                    StyleSpan(Typeface.BOLD),
                    boldStart, boldEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            append(entry.quoteLast)
        }

        val padding = (bounds.width() * 0.10f).toInt()
        val textWidth = bounds.width() - padding * 2

        quotePaint.textSize = (bounds.width() * 0.042f)
        quotePaint.color = textColor
        quotePaint.typeface = tfRegular
        val quoteLayout = StaticLayout.Builder
            .obtain(quoteSpannable, 0, quoteSpannable.length, quotePaint, textWidth)
            .setLineSpacing(4f, 1.15f)
            .build()

        val attribution = "— ${entry.author}, ${entry.title}"
        attribPaint.textSize = (bounds.width() * 0.032f)
        attribPaint.color = attribColor
        val attribLayout = StaticLayout.Builder
            .obtain(attribution, 0, attribution.length, attribPaint, textWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_OPPOSITE)
            .setLineSpacing(0f, 1.1f)
            .build()

        val gap = (bounds.height() * 0.03f)
        val totalHeight = quoteLayout.height + gap + attribLayout.height
        val startY = (bounds.height() - totalHeight) / 2f

        canvas.save()
        canvas.translate(padding.toFloat(), startY)
        quoteLayout.draw(canvas)
        canvas.restore()

        canvas.save()
        canvas.translate(padding.toFloat(), startY + quoteLayout.height + gap)
        attribLayout.draw(canvas)
        canvas.restore()
    }

    override fun renderHighlightLayer(
        canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime
    ) {
        // No highlight layer needed
    }

    private fun getQuoteForMinute(timeKey: String): QuoteEntry {
        if (timeKey == lastMinute && cachedQuote != null) {
            return cachedQuote!!
        }

        val filterNsfw = prefs().getBoolean(KEY_FILTER_NSFW, true)
        val filterEasterEggs = prefs().getBoolean(KEY_STOP_BEING_ANNOYING, false)

        fun candidates(key: String): List<QuoteEntry> {
            val entries = quotesMap[key] ?: return emptyList()
            return entries.filter { entry ->
                (!filterNsfw || !entry.nsfw) && (!filterEasterEggs || !entry.easterEgg)
            }
        }

        var quote = candidates(timeKey).randomOrNull()

        if (quote == null) {
            val parts = timeKey.split(":")
            val hour = parts[0].toIntOrNull() ?: 0
            val minute = parts[1].toIntOrNull() ?: 0
            for (delta in 1..59) {
                val earlier = (minute - delta + 60) % 60
                val key = String.format(Locale.US, "%02d:%02d", hour, earlier)
                quote = candidates(key).randomOrNull()
                if (quote != null) break
            }
        }

        if (quote == null) {
            quote = QuoteEntry(
                time = "00:00",
                quoteFirst = "",
                quoteTime = "Time is the longest distance between two places.",
                quoteLast = "",
                title = "The Glass Menagerie",
                author = "Tennessee Williams"
            )
        }

        lastMinute = timeKey
        cachedQuote = quote
        return quote
    }
}

data class QuoteEntry(
    val time: String,
    val quoteFirst: String,
    val quoteTime: String,
    val quoteLast: String,
    val title: String,
    val author: String,
    val nsfw: Boolean = false,
    val easterEgg: Boolean = false
)
