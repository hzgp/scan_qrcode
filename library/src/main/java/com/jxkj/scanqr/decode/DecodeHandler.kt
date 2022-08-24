package com.jxkj.scanqr.decode

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.jxkj.scanqr.android.CaptureActivity
import com.jxkj.scanqr.common.Constant

/**
 * Created by DY on 2019/12/23.
 */
class DecodeHandler internal constructor(activity: CaptureActivity, hints: Map<DecodeHintType?, Any?>?) : Handler() {
    private val activity: CaptureActivity
    private val multiFormatReader: MultiFormatReader
    private var running = true
    override fun handleMessage(message: Message) {
        if (!running) {
            return
        }
        when (message.what) {
            Constant.DECODE -> decode(message.obj as ByteArray, message.arg1, message.arg2)
            Constant.QUIT -> {
                running = false
                Looper.myLooper()?.quit()
            }
        }
    }

    /**
     *
     * 解码
     */
    private fun decode(data: ByteArray, width: Int, height: Int) {
        var deData = data
        var w = width
        var h = height
        var rawResult: Result? = null
        val rotatedData = ByteArray(deData.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                rotatedData[x * h + h - y - 1] = deData[x + y * w]
            }
        }
        val tmp = w // Here we are swapping, that's the difference to #11
        w = h
        h = tmp
        deData = rotatedData
        val source = activity.cameraManager?.buildLuminanceSource(deData, w, h)
        if (source != null) {
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                rawResult = multiFormatReader.decodeWithState(bitmap)
            } catch (re: ReaderException) {

                //Log.i("解码异常",re.toString());
            } finally {
                multiFormatReader.reset()
            }
        }
        val handler = activity.handler
        if (rawResult != null) {
            if (handler != null) {
                val message = Message.obtain(handler,
                        Constant.Companion.DECODE_SUCCEEDED, rawResult)
                message.sendToTarget()
            }
        } else {
            if (handler != null) {
                val message: Message = Message.obtain(handler, Constant.Companion.DECODE_FAILED)
                message.sendToTarget()
            }
        }
    }

    init {
        multiFormatReader = MultiFormatReader()
        multiFormatReader.setHints(hints)
        this.activity = activity
    }
}