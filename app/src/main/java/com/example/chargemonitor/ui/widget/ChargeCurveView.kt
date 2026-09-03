package com.example.chargemonitor.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
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
import kotlin.math.max
import kotlin.math.min

/**
 * 自绘充电曲线 View
 * 支持：三线绘制（功率/电压/电流）、双指缩放、实时滚动
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
    private var maxPoints = 900  // 30 分钟 × 60 秒 ÷ 2 秒 = 900 点

    // 绘制范围
    private var startTime = 0L
    private var endTime = 0L
    private var minValue = 0.0
    private var maxValue = 100.0

    // 缩放
    private var scaleFactor = 1.0f
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

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
        textSize = 24f
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
                // 滚动查看历史
                val timeSpan = endTime - startTime
                val shift = (distanceX / width * timeSpan).toLong()
                startTime += shift
                endTime += shift
                invalidate()
                return true
            }
        })
    }

    /**
     * 添加新数据点
     */
    fun addPoint(point: CurvePoint) {
        dataPoints.add(point)
        if (dataPoints.size > maxPoints * 2) {
            // 防止无限增长
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
        val windowMs = (30 * 60 * 1000 / scaleFactor).toLong()  // 30 分钟基础窗口

        endTime = latest
        startTime = latest - windowMs

        // 计算值域
        val visiblePoints = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visiblePoints.isNotEmpty()) {
            val values = mutableListOf<Double>()
            if (showPower) values.addAll(visiblePoints.map { it.powerW })
            if (showVoltage) values.addAll(visiblePoints.map { it.voltageV })
            if (showCurrent) values.addAll(visiblePoints.map { it.currentMa })

            if (values.isNotEmpty()) {
                minValue = values.min()
                maxValue = values.max()
                val padding = (maxValue - minValue) * 0.1
                minValue -= padding
                maxValue += padding
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

        val padding = 40f
        val chartWidth = width - 2 * padding
        val chartHeight = height - 2 * padding

        // 绘制网格
        drawGrid(canvas, padding, chartWidth, chartHeight)

        // 绘制曲线
        val visiblePoints = dataPoints.filter { it.timestamp in startTime..endTime }
        if (visiblePoints.size < 2) return

        if (showPower) drawCurve(canvas, visiblePoints, { it.powerW }, powerPaint, padding, chartWidth, chartHeight)
        if (showVoltage) drawCurve(canvas, visiblePoints, { it.voltageV }, voltagePaint, padding, chartWidth, chartHeight)
        if (showCurrent) drawCurve(canvas, visiblePoints, { it.currentMa }, currentPaint, padding, chartWidth, chartHeight)

        // 绘制时间轴
        drawTimeAxis(canvas, padding, chartWidth, chartHeight)
    }

    private fun drawGrid(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float) {
        // 水平网格线
        for (i in 0..5) {
            val y = padding + chartHeight * i / 5
            canvas.drawLine(padding, y, padding + chartWidth, y, gridPaint)
        }

        // 垂直网格线
        for (i in 0..6) {
            val x = padding + chartWidth * i / 6
            canvas.drawLine(x, padding, x, padding + chartHeight, gridPaint)
        }
    }

    private fun drawCurve(
        canvas: Canvas,
        points: List<CurvePoint>,
        valueExtractor: (CurvePoint) -> Double,
        paint: Paint,
        padding: Float,
        chartWidth: Float,
        chartHeight: Float
    ) {
        val path = Path()
        var first = true

        for (point in points) {
            val x = padding + (point.timestamp - startTime).toFloat() / (endTime - startTime) * chartWidth
            val y = padding + chartHeight - ((valueExtractor(point) - minValue) / (maxValue - minValue) * chartHeight).toFloat()

            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
        }

        canvas.drawPath(path, paint)
    }

    private fun drawTimeAxis(canvas: Canvas, padding: Float, chartWidth: Float, chartHeight: Float) {
        val timeSpan = endTime - startTime
        for (i in 0..6) {
            val x = padding + chartWidth * i / 6
            val time = startTime + timeSpan * i / 6
            val label = formatTime(time)
            canvas.drawText(label, x - 30, height - 10f, textPaint)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return String.format("%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
