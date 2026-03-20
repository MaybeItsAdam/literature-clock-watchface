package uk.co.maybeitsadam.literatureclock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import java.util.Calendar

/**
 * Fires at each minute boundary to trigger complication updates.
 *
 * Uses AlarmManager.setExactAndAllowWhileIdle() for reliable per-minute scheduling,
 * even during Doze. Each alarm reschedules the next one.
 *
 * Also handles BOOT_COMPLETED to restart the alarm chain after reboot.
 */
class MinuteUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MinuteUpdateReceiver"

        fun schedule(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Compute start of next minute
            val nextMinute = Calendar.getInstance().apply {
                add(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextMinute.timeInMillis,
                pendingIntent(context)
            )
            Log.d(TAG, "Scheduled next update for ${nextMinute.time}")
        }

        fun cancel(context: Context) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.cancel(pendingIntent(context))
            Log.d(TAG, "Cancelled minute alarm")
        }

        private fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MinuteUpdateReceiver::class.java)
            return PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed — restarting minute alarm")
            }
            // Default: alarm fired
        }

        // Request complication update
        ComplicationDataSourceUpdateRequester.create(
            context,
            ComponentName(context, LiteratureQuoteDataSource::class.java)
        ).requestUpdateAll()

        // Schedule the next minute
        schedule(context)
    }
}
