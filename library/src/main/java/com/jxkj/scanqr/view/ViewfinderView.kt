package com.jxkj.scanqr.view

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.jxkj.scanqr.R
import com.jxkj.scanqr.bean.ZxingConfig
import com.jxkj.scanqr.camera.CameraManager
import java.util.*

/**
 * Created by DY on 2019/12/23.
 */
class ViewfinderView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : View(context, attrs, defStyleAttr) {
    private var cameraManager: CameraManager? = null
    private var paint: Paint? = null
    private var scanLinePaint: Paint? = null
    private var reactPaint: Paint? = null
    private var frameLinePaint: Paint? = null
    private var resultBitmap: Bitmap? = null
    private val maskColor: Int           // 取景框外的背景颜色
            
    private val resultColor: Int   // result Bitmap的颜色
            
    private val resultPointColor: Int // 特征点的颜色
            
    private var reactColor = 0 //四个角的颜色
           
    private var scanLineColor = 0 //扫描线的颜色
           
    private var frameLineColor = -1 //边框线的颜色
    private var possibleResultPoints: MutableList<ResultPoint>
    private var lastPossibleResultPoints: List<ResultPoint>?

    // 扫描线移动的y
    private var scanLineTop = 0
    private var config: ZxingConfig? = null
    private var valueAnimator: ValueAnimator? = null
    private var frame: Rect? = null

    @JvmOverloads
    constructor(context: Context?, attrs: AttributeSet? = null) : this(context, attrs, 0) {
    }

    fun setZxingConfig(config: ZxingConfig?) {
        this.config = config
        config?.let {
            reactColor = ContextCompat.getColor(context, it.reactColor)
            if (it.frameLineColor != -1) {
                frameLineColor = ContextCompat.getColor(context, it.frameLineColor)
            }
            scanLineColor = ContextCompat.getColor(context, it.scanLineColor)
        }
        
        initPaint()
    }

    private fun initPaint() {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)

        /*四个角的画笔*/reactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        reactPaint!!.color = reactColor
        reactPaint!!.style = Paint.Style.FILL
        reactPaint!!.strokeWidth = dp2px(1).toFloat()

        /*边框线画笔*/if (frameLineColor != -1) {
            frameLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            frameLinePaint!!.color = ContextCompat.getColor(context, config?.frameLineColor?:-1)
            frameLinePaint!!.strokeWidth = dp2px(1).toFloat()
            frameLinePaint!!.style = Paint.Style.STROKE
        }


        /*扫描线画笔*/scanLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        scanLinePaint!!.strokeWidth = dp2px(2).toFloat()
        scanLinePaint!!.style = Paint.Style.FILL
        scanLinePaint!!.isDither = true
        scanLinePaint!!.color = scanLineColor
    }

    private fun initAnimator() {
        if (valueAnimator == null) {
            valueAnimator = ValueAnimator.ofInt(frame!!.top, frame!!.bottom).apply {
                duration = 3000
                interpolator = DecelerateInterpolator()
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animation: ValueAnimator ->
                    scanLineTop = animation.animatedValue as Int
                    invalidate()
                }
            }
            valueAnimator?.start()
        }
    }

    fun setCameraManager(cameraManager: CameraManager?) {
        this.cameraManager = cameraManager
    }

    fun stopAnimator() {
        if (valueAnimator != null) {
            valueAnimator!!.end()
            valueAnimator!!.cancel()
            valueAnimator = null
        }
    }

    @SuppressLint("DrawAllocation")
    public override fun onDraw(canvas: Canvas) {
        if (cameraManager == null) {
            return
        }

        // frame为取景框
        frame = cameraManager!!.framingRect
        val previewFrame = cameraManager!!.reqFramingRectInPreview()
        if (frame == null || previewFrame == null) {
            return
        }
        //initAnimator();

        /*绘制遮罩*/
        // drawMaskView(canvas, frame, width, height);

        /*绘制取景框边框*/drawFrameBounds(canvas, frame!!)
        if (resultBitmap != null) {
            // Draw the opaque result bitmap over the scanning rectangle
            // 如果有二维码结果的Bitmap，在扫取景框内绘制不透明的result Bitmap
            paint!!.alpha = CURRENT_POINT_OPACITY
            canvas.drawBitmap(resultBitmap!!, null, frame!!, paint)
        } else {

            /*绘制扫描线*/
            drawScanLight(canvas, frame!!)

            /*绘制闪动的点*/drawPoint(canvas, frame!!, previewFrame)
        }
    }

    private fun drawPoint(canvas: Canvas, frame: Rect, previewFrame: Rect) {
        val scaleX = frame.width() / previewFrame.width().toFloat()
        val scaleY = frame.height() / previewFrame.height().toFloat()

        // 绘制扫描线周围的特征点
        val currentPossible: List<ResultPoint> = possibleResultPoints
        val currentLast = lastPossibleResultPoints
        val frameLeft = frame.left
        val frameTop = frame.top
        if (currentPossible.isEmpty()) {
            lastPossibleResultPoints = null
        } else {
            possibleResultPoints = ArrayList(5)
            lastPossibleResultPoints = currentPossible
            paint!!.alpha = CURRENT_POINT_OPACITY
            paint!!.color = resultPointColor
            synchronized(currentPossible) {
                for (point in currentPossible) {
                    canvas.drawCircle((frameLeft
                            + (point.x * scaleX).toInt()).toFloat(), (frameTop
                            + (point.y * scaleY).toInt()).toFloat(), POINT_SIZE.toFloat(),
                            paint!!)
                }
            }
        }
        if (currentLast != null) {
            paint!!.alpha = CURRENT_POINT_OPACITY / 2
            paint!!.color = resultPointColor
            synchronized(currentLast) {
                val radius = POINT_SIZE / 2.0f
                for (point in currentLast) {
                    canvas.drawCircle((frameLeft
                            + (point.x * scaleX).toInt()).toFloat(), (frameTop
                            + (point.y * scaleY).toInt()).toFloat(), radius, paint!!)
                }
            }
        }

        // Request another update at the animation interval, but only
        // repaint the laser line,
        // not the entire viewfinder mask.
        postInvalidateDelayed(ANIMATION_DELAY, frame.left - POINT_SIZE,
                frame.top - POINT_SIZE, frame.right + POINT_SIZE,
                frame.bottom + POINT_SIZE)
    }

    /**
     * 绘制取景框边框
     *
     * @param canvas
     * @param frame
     */
    private fun drawFrameBounds(canvas: Canvas, frame: Rect) {

        /*扫描框的边框线*/
        if (frameLineColor != -1) {
            canvas.drawRect(frame, frameLinePaint!!)
        }


        /*四个角的长度和宽度*/
        val width = frame.width()
        val corLength = (width * 0.07).toInt()
        var corWidth = (corLength * 0.2).toInt()
        corWidth = if (corWidth > 15) 15 else corWidth

        reactPaint?:return
        /*角在线外*/
        // 左上角
        canvas.drawRect((frame.left - corWidth).toFloat(), frame.top.toFloat(), frame.left.toFloat(), (frame.top
                + corLength).toFloat(), reactPaint)
        canvas.drawRect((frame.left - corWidth).toFloat(), (frame.top - corWidth).toFloat(), (frame.left
                + corLength).toFloat(), frame.top.toFloat(), reactPaint)
        // 右上角
        canvas.drawRect(frame.right.toFloat(), frame.top.toFloat(), (frame.right + corWidth).toFloat(), (
                frame.top + corLength).toFloat(), reactPaint)
        canvas.drawRect((frame.right - corLength).toFloat(), (frame.top - corWidth).toFloat(), (
                frame.right + corWidth).toFloat(), frame.top.toFloat(), reactPaint)
        // 左下角
        canvas.drawRect((frame.left - corWidth).toFloat(), (frame.bottom - corLength).toFloat(),
                frame.left.toFloat(), frame.bottom.toFloat(), reactPaint)
        canvas.drawRect((frame.left - corWidth).toFloat(), frame.bottom.toFloat(), (frame.left
                + corLength).toFloat(), (frame.bottom + corWidth).toFloat(), reactPaint)
        // 右下角
        canvas.drawRect(frame.right.toFloat(), (frame.bottom - corLength).toFloat(), (frame.right
                + corWidth).toFloat(), frame.bottom.toFloat(), reactPaint)
        canvas.drawRect((frame.right - corLength).toFloat(), frame.bottom.toFloat(), (frame.right
                + corWidth).toFloat(), (frame.bottom + corWidth).toFloat(), reactPaint)
    }

    /**
     * 绘制移动扫描线
     *
     * @param canvas
     * @param frame
     */
    private fun drawScanLight(canvas: Canvas, frame: Rect) {

        //canvas.drawLine(frame.left, scanLineTop, frame.right, scanLineTop, scanLinePaint);
    }

    fun drawViewfinder() {
        val resultBitmap = resultBitmap
        this.resultBitmap = null
        resultBitmap?.recycle()
        invalidate()
    }

    /**
     * Draw a bitmap with the result points highlighted instead of the live
     * scanning display.
     *
     * @param barcode An image of the decoded barcode.
     */
    fun drawResultBitmap(barcode: Bitmap?) {
        resultBitmap = barcode
        invalidate()
    }

    fun addPossibleResultPoint(point: ResultPoint) {
        val points = possibleResultPoints
        synchronized(points) {
            points.add(point)
            val size = points.size
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear()
            }
        }
    }

    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    companion object {
        /*界面刷新间隔时间*/
        private const val ANIMATION_DELAY = 80L
        private const val CURRENT_POINT_OPACITY = 0xA0
        private const val MAX_RESULT_POINTS = 20
        private const val POINT_SIZE = 6
    }

    init {
        maskColor = ContextCompat.getColor(getContext(), R.color.viewfinder_mask)
        resultColor = ContextCompat.getColor(getContext(), R.color.result_view)
        resultPointColor = ContextCompat.getColor(getContext(), R.color.possible_result_points)
        possibleResultPoints = ArrayList(10)
        lastPossibleResultPoints = null
    }
}