package com.example.chargemonitor.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.example.chargemonitor.R
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * 曲线模式：POWER 单轴画功率；VOLTAGE_CURRENT 双轴画电压（左）+ 电流（右）。
 */
enum class CurveMode { POWER, VOLTAGE_CURRENT }

/**
 * 自绘充电曲线 View，支持双 Y 轴、数值刻度、双指缩放、实时滚动。
 */
class ChargeCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    data class CurvePoint(
        val timestamp: Long,
        val powerW: Double,
        val voltageV: Double,
        val currentMa: Double
    )

    private val dataPoints = CopyOnWriteArrayList<CurvePoint>()
    private val maxPoints = 1800

    private var startTime = 0L
    private var endTime = 0L
    private var leftMin = 0.0
    private var leftMax = 1.0
    private var rightMin = 0.0
    private var rightMax = 1.0

    private var scaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    var mode: CurveMode = CurveMode.POWER

    /** true 显示全部数据（历史回看）；false 显示 30 分钟实时窗口 */
    var isFullRange = false

    private val powerPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.power_red)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val voltagePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.voltage_blue)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val currentPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.current_green)
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.grid_line)
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.text_secondary)
        textSize = 22f
        isAntiAlias = true
    }

    init {
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
                updateTimeWindow()
                invalidate()
                return true
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (isFullRange) return false
                val timeSpan = endTime - startTime
                if (timeSpan <= 0 || width <= 0) return false
                val shift = (distanceX / width * timeSpan).toLong()
                startTime += shift
                endTime += shift
                invalidate()
                return true
            }
        })
    }

    fun addPoint(point: CurvePoint) {
        dataPoints.add(point)
        while (dataPoints.size > maxPoints) {
            dataPoints.removeAt(0)
        }
        updateTimeWindow()
        invalidate()
    }

    fun clear() {
        dataPoints.clear()
        invalidate()
    }

    fun setData(points: List<CurvePoint>) {
        dataPoints.clear()
        dataPoints.addAll(points)
        updateTimeWindow()
        invalidate()
    }

    private fun updateTimeWindow() {
        if (dataPoints.isEmpty()) return

        val latest = dataPoints.last().timestamp
        if (isFullRange) {
            startTime = dataPoints.first().timestamp
            endTime = latest
        } else {
            val windowMs = (30 * 60 * 1000 / scaleFactor).toLong()
            endTime = latest
            startTime = latest - windowMs
        }

        val visible = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visible.isEmpty()) return

        when (mode) {
            CurveMode.POWER -> {
                applyRange(visible.map { it.powerW }) { lo, hi ->
                    leftMin = lo; leftMax = hi
                }
            }
            CurveMode.VOLTAGE_CURRENT -> {
                applyRange(visible.map { it.voltageV }) { lo, hi ->
                    leftMin = lo; leftMax = hi
                }
                applyRange(visible.map { it.currentMa }) { lo, hi ->
                    rightMin = lo; rightMax = hi
                }
            }
        }
    }

    private inline fun applyRange(values: List<Double>, assign: (Double, Double) -> Unit) {
        if (values.isEmpty()) return
        var lo = values.minOrNull() ?: return
        var hi = values.maxOrNull() ?: return
        if (hi - lo < 1e-9) {
            hi = lo + 1.0
            lo = lo - 1.0
        } else {
            val padding = (hi - lo) * 0.1
            lo -= padding
            hi += padding
        }
        assign(lo, hi)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (dataPoints.isEmpty()) {
            canvas.drawText("等待数据...", width / 2f - 50, height / 2f, textPaint)
            return
        }

        val paddingLeft = 64f
        val paddingRight = if (mode == CurveMode.VOLTAGE_CURRENT) 64f else 24f
        val paddingTop = 16f
        val paddingBottom = 32f

        val chartLeft = paddingLeft
        val chartTop = paddingTop
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom
        if (chartWidth <= 0 || chartHeight <= 0) return

        drawGrid(canvas, chartLeft, chartTop, chartWidth, chartHeight)
        drawYAxisLabels(canvas, chartLeft, chartTop, chartWidth, chartHeight)

        val visible = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visible.size >= 2) {
            when (mode) {
                CurveMode.POWER ->
                    drawCurve(canvas, visible, { it.powerW }, powerPaint, chartLeft, chartTop, chartWidth, chartHeight, leftMin, leftMax)
                CurveMode.VOLTAGE_CURRENT -> {
                    drawCurve(canvas, visible, { it.voltageV }, voltagePaint, chartLeft, chartTop, chartWidth, chartHeight, leftMin, leftMax)
                    drawCurve(canvas, visible, { it.currentMa }, currentPaint, chartLeft, chartTop, chartWidth, chartHeight, rightMin, rightMax)
                }
            }
        }

        drawTimeAxis(canvas, chartLeft, chartTop, chartWidth, chartHeight)
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        for (i in 0..5) {
            val y = top + h * i / 5
            canvas.drawLine(left, y, left + w, y, gridPaint)
        }
        for (i in 0..6) {
            val x = left + w * i / 6
            canvas.drawLine(x, top, x, top + h, gridPaint)
        }
    }

    private fun drawYAxisLabels(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        // 左轴刻度
        for (i in 0..5) {
            val value = leftMax - (leftMax - leftMin) * i / 5.0
            val y = top + h * i / 5
            val label = formatAxisValue(value)
            val tw = textPaint.measureText(label)
            canvas.drawText(label, left - tw - 6f, y + 7f, textPaint)
        }
        // 右轴刻度（双轴模式）
        if (mode == CurveMode.VOLTAGE_CURRENT) {
            for (i in 0..5) {
                val value = rightMax - (rightMax - rightMin) * i / 5.0
                val y = top + h * i / 5
                val label = formatAxisValue(value)
                canvas.drawText(label, left + w + 6f, y + 7f, textPaint)
            }
        }
    }

    private fun drawCurve(
        canvas: Canvas,
        points: List<CurvePoint>,
        extractor: (CurvePoint) -> Double,
        paint: Paint,
        left: Float,
        top: Float,
        w: Float,
        h: Float,
        vMin: Double,
        vMax: Double
    ) {
        val timeSpan = (endTime - startTime).toFloat()
        val valueSpan = (vMax - vMin).toFloat()
        if (timeSpan <= 0f || valueSpan <= 0f) return

        val path = Path()
        var first = true
        for (p in points) {
            val x = left + (p.timestamp - startTime).toFloat() / timeSpan * w
            val y = top + h - ((extractor(p) - vMin).toFloat() / valueSpan * h)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTimeAxis(canvas: Canvas, left: Float, top: Float, w: Float, h: Float) {
        val timeSpan = endTime - startTime
        if (timeSpan <= 0) return
        for (i in 0..6) {
            val x = left + w * i / 6
            val time = startTime + timeSpan * i / 6
            val label = formatTime(time)
            val tw = textPaint.measureText(label)
            canvas.drawText(label, x - tw / 2f, top + h + 24f, textPaint)
        }
    }

    private fun formatAxisValue(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1000 -> String.format("%.0f", v)
            a >= 100 -> String.format("%.0f", v)
            a >= 10 -> String.format("%.1f", v)
            else -> String.format("%.2f", v)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
