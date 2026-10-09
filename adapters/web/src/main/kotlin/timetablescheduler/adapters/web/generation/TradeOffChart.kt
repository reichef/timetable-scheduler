package timetablescheduler.adapters.web.generation

import kotlinx.html.FlowContent
import kotlinx.html.div
import kotlinx.html.unsafe
import java.util.Locale

/** One alternative in the trade-off chart: its number, the two selected objective values and the page it links to. */
data class ChartPoint(val number: Int, val x: Double, val y: Double, val href: String)

/**
 * Server-rendered SVG scatter chart of two objectives (FR-020) — no script needed. Every point links to its
 * alternative; the points that are best in these two objectives (none is better in both) are connected, so the
 * trade-off between them is visible. All text is escaped, as objective titles may contain names.
 */
object TradeOffChart {
    private const val WIDTH = 480.0
    private const val HEIGHT = 360.0
    private const val LEFT = 60.0
    private const val RIGHT = 20.0
    private const val TOP = 20.0
    private const val BOTTOM = 50.0
    private const val PLOT_WIDTH = WIDTH - LEFT - RIGHT
    private const val PLOT_HEIGHT = HEIGHT - TOP - BOTTOM

    /** Smallest axis span, so equal values do not collapse the axis to a point. */
    private const val MINIMUM_SPAN = 0.01

    fun render(points: List<ChartPoint>, xTitle: String, yTitle: String, caption: String): String {
        val xAxis = Axis.of(points.map { it.x })
        val yAxis = Axis.of(points.map { it.y })
        fun px(value: Double) = LEFT + xAxis.fraction(value) * PLOT_WIDTH
        fun py(value: Double) = TOP + (1 - yAxis.fraction(value)) * PLOT_HEIGHT
        return buildString {
            append("""<svg class="trade-off" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${n(WIDTH)} ${n(HEIGHT)}" role="img">""")
            append("<title>").append(escape(caption)).append("</title>")
            append("""<line class="axis" x1="${n(LEFT)}" y1="${n(TOP + PLOT_HEIGHT)}" x2="${n(LEFT + PLOT_WIDTH)}" y2="${n(TOP + PLOT_HEIGHT)}"/>""")
            append("""<line class="axis" x1="${n(LEFT)}" y1="${n(TOP)}" x2="${n(LEFT)}" y2="${n(TOP + PLOT_HEIGHT)}"/>""")
            append("""<text x="${n(LEFT)}" y="${n(TOP + PLOT_HEIGHT + 16)}">${percent(xAxis.min)}</text>""")
            append("""<text x="${n(LEFT + PLOT_WIDTH)}" y="${n(TOP + PLOT_HEIGHT + 16)}" text-anchor="end">${percent(xAxis.max)}</text>""")
            append("""<text x="${n(LEFT + PLOT_WIDTH / 2)}" y="${n(HEIGHT - 8)}" text-anchor="middle">${escape(xTitle)}</text>""")
            append("""<text x="${n(LEFT - 6)}" y="${n(TOP + PLOT_HEIGHT)}" text-anchor="end">${percent(yAxis.min)}</text>""")
            append("""<text x="${n(LEFT - 6)}" y="${n(TOP + 10)}" text-anchor="end">${percent(yAxis.max)}</text>""")
            append("""<text transform="translate(14 ${n(TOP + PLOT_HEIGHT / 2)}) rotate(-90)" text-anchor="middle">${escape(yTitle)}</text>""")
            val front = front(points)
            if (front.size > 1) {
                append("""<polyline class="front" points="${front.joinToString(" ") { "${n(px(it.x))},${n(py(it.y))}" }}"/>""")
            }
            points.forEach { point ->
                append("""<a href="${escape(point.href)}">""")
                append("""<circle cx="${n(px(point.x))}" cy="${n(py(point.y))}" r="6">""")
                append("<title>").append("#${point.number}: ${percent(point.x)} / ${percent(point.y)}").append("</title></circle>")
                append("""<text x="${n(px(point.x) + 8)}" y="${n(py(point.y) - 8)}">${point.number}</text>""")
                append("</a>")
            }
            append("</svg>")
        }
    }

    /** The points no other point beats in both objectives (higher is better), ordered along the x axis. */
    fun front(points: List<ChartPoint>): List<ChartPoint> =
        points.filter { point -> points.none { other -> other.x >= point.x && other.y >= point.y && (other.x > point.x || other.y > point.y) } }
            .sortedBy { it.x }

    /**
     * Axis scaling: the axis spans the shown values plus 5 % on each side (at least [MINIMUM_SPAN]), clamped to
     * 0 – 100 %, so alternatives that differ only slightly are still told apart.
     */
    private class Axis(val min: Double, val max: Double) {
        fun fraction(value: Double) = (value - min) / (max - min)

        companion object {
            fun of(values: List<Double>): Axis {
                val low = values.minOrNull() ?: 0.0
                val high = values.maxOrNull() ?: 1.0
                val padding = maxOf(high - low, MINIMUM_SPAN) * 0.05
                var min = (low - padding).coerceAtLeast(0.0)
                var max = (high + padding).coerceAtMost(1.0)
                if (max - min < MINIMUM_SPAN) {
                    if (min + MINIMUM_SPAN <= 1.0) max = min + MINIMUM_SPAN else min = max - MINIMUM_SPAN
                }
                return Axis(min, max)
            }
        }
    }

    private fun n(value: Double) = String.format(Locale.ROOT, "%.1f", value)

    private fun percent(value: Double) = String.format(Locale.ROOT, "%.1f %%", value * 100)

    private fun escape(text: String) = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

fun FlowContent.tradeOffChart(points: List<ChartPoint>, xTitle: String, yTitle: String, caption: String) {
    div(classes = "chart") { unsafe { +TradeOffChart.render(points, xTitle, yTitle, caption) } }
}
