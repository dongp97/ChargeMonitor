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
 * 自绘充电曲线 View
 * 支持：三线绘制（功率/电压/电流）、Y 轴数值刻度、双指缩放、实时滚动
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

    // 数据
    private val dataPoints = CopyOnWriteArrayList<CurvePoint>()
    private val maxPoints = 1800  // 实时模式最多保留约 1 小时的点（2 秒一个）

    // 绘制范围
    private var startTime = 0L
    private var endTime = 0L
    private var minValue = 0.0
    private var maxValue = 100.0

    // 缩放
    private var scaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    /**
     * true 时显示全部数据（历史回看）；false 时显示 30 分钟实时窗口
     */
    var isFullRange = false

    // 画笔
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

    // 显示控制
    var showPower = true
    var showVoltage = true
    var showCurrent = true

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
                if (isFullRange) return false  // 历史全览模式不滚动
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

    /**
     * 添加新数据点（实时模式）
     */
    fun addPoint(point: CurvePoint) {
        dataPoints.add(point)
        while (dataPoints.size > maxPoints) {
            dataPoints.removeAt(0)
        }
        updateTimeWindow()
        invalidate()
    }

    /**
     * 清空数据
     */
    fun clear() {
        dataPoints.clear()
        invalidate()
    }

    /**
     * 获取当前全部数据点（用于全屏切换时复制到全屏曲线）
     */
    fun getPoints(): List<CurvePoint> = dataPoints.toList()

    /**
     * 设置完整数据（用于历史回看）
     */
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
            val windowMs = (30 * 60 * 1000 / scaleFactor).toLong()  // 30 分钟基础窗口
            endTime = latest
            startTime = latest - windowMs
        }

        // 计算值域（三条线共享）
        val visiblePoints = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visiblePoints.isNotEmpty()) {
            val values = mutableListOf<Double>()
            if (showPower) values.addAll(visiblePoints.map { it.powerW })
            if (showVoltage) values.addAll(visiblePoints.map { it.voltageV })
            if (showCurrent) values.addAll(visiblePoints.map { it.currentMa })

            if (values.isNotEmpty()) {
                var lo = values.minOrNull() ?: return
                var hi = values.maxOrNull() ?: return
                if (hi - lo < 1e-9) {
                    // 所有值相同，给一个默认范围，避免除零
                    hi = lo + 1.0
                    lo = lo - 1.0
                } else {
                    val padding = (hi - lo) * 0.1
                    lo -= padding
                    hi += padding
                }
                minValue = lo
                maxValue = hi
            }
        }
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

        val paddingLeft = 72f    // 左侧放 Y 轴刻度
        val paddingRight = 16f
        val paddingTop = 16f
        val paddingBottom = 36f  // 底部放 X 轴时间

        val chartLeft = paddingLeft
        val chartTop = paddingTop
        val chartWidth = width - paddingLeft - paddingRight
        val chartHeight = height - paddingTop - paddingBottom

        if (chartWidth <= 0 || chartHeight <= 0) return

        // 网格
        drawGrid(canvas, chartLeft, chartTop, chartWidth, chartHeight)

        // Y 轴数值刻度
        drawYAxisLabels(canvas, chartLeft, chartTop, chartHeight)

        // 曲线
        val visiblePoints = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visiblePoints.size >= 2) {
            if (showPower) drawCurve(canvas, visiblePoints, { it.powerW }, powerPaint, chartLeft, chartTop, chartWidth, chartHeight)
            if (showVoltage) drawCurve(canvas, visiblePoints, { it.voltageV }, voltagePaint, chartLeft, chartTop, chartWidth, chartHeight)
            if (showCurrent) drawCurve(canvas, visiblePoints, { it.currentMa }, currentPaint, chartLeft, chartTop, chartWidth, chartHeight)
        }

        // X 轴时间
        drawTimeAxis(canvas, chartLeft, chartTop, chartWidth, chartHeight)
    }

    private fun drawGrid(canvas: Canvas, left: Float, top: Float, chartWidth: Float, chartHeight: Float) {
        for (i in 0..5) {
            val y = top + chartHeight * i / 5
            canvas.drawLine(left, y, left + chartWidth, y, gridPaint)
        }
        for (i in 0..6) {
            val x = left + chartWidth * i / 6
            canvas.drawLine(x, top, x, top + chartHeight, gridPaint)
        }
    }

    private fun drawYAxisLabels(canvas: Canvas, left: Float, top: Float, chartHeight: Float) {
        for (i in 0..5) {
            val value = maxValue - (maxValue - minValue) * i / 5.0
            val y = top + chartHeight * i / 5
            val label = formatAxisValue(value)
            // 右对齐到 Y 轴左侧
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, left - textWidth - 8f, y + 7f, textPaint)
        }
    }

    private fun drawCurve(
        canvas: Canvas,
        points: List<CurvePoint>,
        valueExtractor: (CurvePoint) -> Double,
        paint: Paint,
        left: Float,
        top: Float,
        chartWidth: Float,
        chartHeight: Float
    ) {
        val path = Path()
        var first = true
        val timeSpan = (endTime - startTime).toFloat()
        val valueSpan = (maxValue - minValue).toFloat()
        if (timeSpan <= 0f || valueSpan <= 0f) return

        for (point in points) {
            val x = left + (point.timestamp - startTime).toFloat() / timeSpan * chartWidth
            val y = top + chartHeight - ((valueExtractor(point) - minValue).toFloat() / valueSpan * chartHeight)

            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }
        canvas.drawPath(path, paint)
    }

    private fun drawTimeAxis(canvas: Canvas, left: Float, top: Float, chartWidth: Float, chartHeight: Float) {
        val timeSpan = endTime - startTime
        if (timeSpan <= 0) return
        for (i in 0..6) {
            val x = left + chartWidth * i / 6
            val time = startTime + timeSpan * i / 6
            val label = formatTime(time)
            val textWidth = textPaint.measureText(label)
            canvas.drawText(label, x - textWidth / 2f, top + chartHeight + 24f, textPaint)
        }
    }

    private fun formatAxisValue(v: Double): String {
        val a = abs(v)
        return when {
            a >= 1000 -> String.format("%.0f", v)
            a >= 100 -> String.format("%.0f", v)
            a >= 10 -> String.format("%.0f", v)
            else -> String.format("%.1f", v)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
