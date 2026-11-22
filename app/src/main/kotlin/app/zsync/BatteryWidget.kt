package app.zsync

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.widget.RemoteViews

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        render(context, manager, id)
    }

    companion object {
        private const val REGEN_SEC = 360

        private fun hm(sec: Long): String {
            val h = sec / 3600; val m = (sec % 3600) / 60
            return if (h > 0) "${h}H ${m}M" else "${m}M"
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, BatteryWidget::class.java)).forEach { render(context, manager, it) }
        }

        private class Run(val text: String, val scale: Float, val top: Int, val bottom: Int)

        // RemoteViews cannot draw outlined or gradient text, so the number is rendered to a bitmap.
        private fun rankText(context: Context, lines: List<Run>): Bitmap {
            val d = context.resources.displayMetrics.density
            val base = 28 * d
            val stroke = 3 * d
            val lineGap = d
            fun paintFor(r: Run) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
                textSize = base * r.scale
                letterSpacing = 0.02f
            }
            val paints = lines.map(::paintFor)
            val widths = paints.mapIndexed { i, p -> p.measureText(lines[i].text) }

            val heights = paints.map { p -> -p.fontMetrics.ascent * 0.78f }
            val w = (widths.max() + stroke * 2 + 6 * d).toInt().coerceAtLeast(1)
            val h = (heights.sum() + lineGap * (lines.size - 1) + stroke * 2 + 2 * d).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            var top = stroke + d
            lines.forEachIndexed { i, r ->
                val p = paints[i]
                val y = top + heights[i]
                val x = (w - widths[i]) / 2f

                p.style = Paint.Style.STROKE
                p.strokeWidth = stroke * r.scale.coerceAtLeast(0.6f)
                p.strokeJoin = Paint.Join.ROUND
                p.color = 0xFF050505.toInt()
                c.drawText(r.text, x, y, p)
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(0f, y - heights[i], 0f, y, r.top, r.bottom, Shader.TileMode.CLAMP)
                c.drawText(r.text, x, y, p)
                top = y + lineGap + (if (i == 0) 3 * d else 0f)
            }
            return bmp
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            runCatching { manager.updateAppWidget(id, build(context)) }
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_battery)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val app = context.applicationContext as App
            val notes = app.repo.accounts.value.flatMap { it.activeRoles() }.firstNotNullOfOrNull { app.repo.state.value.notes[it.key] }

            val now = System.currentTimeMillis()
            val cur = notes?.staminaNow(now, REGEN_SEC)
            val runs = mutableListOf(Run(if (notes == null) "--- / 240" else "%03d / %d".format(cur, notes.stamina.max), 1f, 0xFFFFFFFF.toInt(), 0xFFC9CDD3.toInt()))
            if (notes != null) {
                val left = ((notes.fetchedAt + notes.stamina.recoverySec * 1000 - now) / 1000).coerceAtLeast(0)
                val label = if (cur!! >= notes.stamina.max || left == 0L) "FULL" else "FULL IN " + hm(left)
                runs += Run(label, 0.5f, 0xFFE6E8EC.toInt(), 0xFF9EA3AB.toInt())
            }
            views.setImageViewBitmap(R.id.widget_value, rankText(context, runs))
            return views
        }
    }
}
