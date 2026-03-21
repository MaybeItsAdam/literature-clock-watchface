package uk.co.maybeitsadam.literatureclock

import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import android.graphics.Canvas
import android.graphics.Rect
import android.view.SurfaceHolder
import java.time.ZonedDateTime

/**
 * Minimal WatchFaceService stub so the system discovers this as a watch face.
 * WFF handles the actual rendering via res/raw/watchface.xml — this renderer
 * is a no-op fallback that should never be visible.
 */
class LiteratureClockWatchFaceService : WatchFaceService() {

    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManager: ComplicationSlotsManager,
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {
        val renderer = object : Renderer.CanvasRenderer(
            surfaceHolder = surfaceHolder,
            currentUserStyleRepository = currentUserStyleRepository,
            watchState = watchState,
            canvasType = CanvasType.HARDWARE,
            interactiveDrawModeUpdateDelayMillis = 60000L
        ) {
            override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
                // WFF handles rendering — this is a no-op stub
            }

            override fun renderHighlightLayer(
                canvas: Canvas,
                bounds: Rect,
                zonedDateTime: ZonedDateTime
            ) {
                // No-op
            }
        }
        return WatchFace(WatchFaceType.DIGITAL, renderer)
    }
}
