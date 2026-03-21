package uk.co.maybeitsadam.literatureclock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
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
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.UserStyleSetting.BooleanUserStyleSetting
import java.io.IOException
import java.io.InputStreamReader
import java.time.ZonedDateTime
import java.util.Locale

class LiteratureClockWatchFaceService : WatchFaceService() {

    companion object {
        internal val STYLE_DARK_MODE = UserStyleSetting.Id("dark_mode")
        internal val STYLE_FILTER_NSFW = UserStyleSetting.Id("filter_nsfw")
        internal val STYLE_HIDE_EASTER_EGGS = UserStyleSetting.Id("hide_easter_eggs")
    }

    override fun createUserStyleSchema(): UserStyleSchema {
        return UserStyleSchema(
            listOf(
                BooleanUserStyleSetting(
                    id = STYLE_DARK_MODE,
                    resources = resources,
                    displayNameResourceId = R.string.style_dark_mode,
                    descriptionResourceId = R.string.style_dark_mode_desc,
                    icon = null,
                    affectsWatchFaceLayers = listOf(
                        androidx.wear.watchface.style.WatchFaceLayer.BASE,
                        androidx.wear.watchface.style.WatchFaceLayer.COMPLICATIONS_OVERLAY
                    ),
                    defaultValue = false
                ),
                BooleanUserStyleSetting(
                    id = STYLE_FILTER_NSFW,
                    resources = resources,
                    displayNameResourceId = R.string.style_filter_nsfw,
                    descriptionResourceId = R.string.style_filter_nsfw_desc,
                    icon = null,
                    affectsWatchFaceLayers = listOf(
                        androidx.wear.watchface.style.WatchFaceLayer.BASE
                    ),
                    defaultValue = true
                ),
                BooleanUserStyleSetting(
                    id = STYLE_HIDE_EASTER_EGGS,
                    resources = resources,
                    displayNameResourceId = R.string.style_hide_easter_eggs,
                    descriptionResourceId = R.string.style_hide_easter_eggs_desc,
                    icon = null,
                    affectsWatchFaceLayers = listOf(
                        androidx.wear.watchface.style.WatchFaceLayer.BASE
                    ),
                    defaultValue = false
                )
            )
        )
    }

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
            quotesMap = quotes
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
            JsonReader(InputStreamReader(context.assets.open("quotes.json"))).use { reader ->
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
            }
            Log.d(TAG, "Loaded $total quotes across ${map.size} minutes")
            map
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load quotes.json", e)
            emptyMap()
        }
    }
}

private class CustomTypefaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) {
        paint.typeface = typeface
    }
    override fun updateMeasureState(paint: TextPaint) {
        paint.typeface = typeface
    }
}

internal class LiteratureClockRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    private val quotesMap: Map<String, List<QuoteEntry>>
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = CanvasType.HARDWARE,
    interactiveDrawModeUpdateDelayMillis = 60_000L
) {
    private val tfRegular: Typeface = loadFont(R.font.merriweather_regular)
    private val tfBold: Typeface = loadFont(R.font.merriweather_bold)
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
    private var lastFilterNsfw = true
    private var lastHideEasterEggs = false

    private fun loadFont(resId: Int): Typeface =
        ResourcesCompat.getFont(context, resId) ?: Typeface.SERIF

    private fun getBoolStyle(id: UserStyleSetting.Id): Boolean {
        val option = currentUserStyleRepository.userStyle.value[id]
        return (option as? BooleanUserStyleSetting.BooleanOption)?.value ?: false
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        val darkMode = getBoolStyle(LiteratureClockWatchFaceService.STYLE_DARK_MODE)
        val filterNsfw = getBoolStyle(LiteratureClockWatchFaceService.STYLE_FILTER_NSFW)
        val hideEasterEggs = getBoolStyle(LiteratureClockWatchFaceService.STYLE_HIDE_EASTER_EGGS)

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

        if (filterNsfw != lastFilterNsfw || hideEasterEggs != lastHideEasterEggs) {
            lastMinute = ""
            cachedQuote = null
            lastFilterNsfw = filterNsfw
            lastHideEasterEggs = hideEasterEggs
        }

        val entry = getQuoteForMinute(timeKey, filterNsfw, hideEasterEggs)

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
                    CustomTypefaceSpan(tfBold),
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

    private fun getQuoteForMinute(
        timeKey: String,
        filterNsfw: Boolean,
        hideEasterEggs: Boolean
    ): QuoteEntry {
        if (timeKey == lastMinute && cachedQuote != null) {
            return cachedQuote!!
        }

        fun candidates(key: String): List<QuoteEntry> {
            val entries = quotesMap[key] ?: return emptyList()
            return entries.filter { entry ->
                (!filterNsfw || !entry.nsfw) && (!hideEasterEggs || !entry.easterEgg)
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
