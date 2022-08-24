package com.jxkj.scanqr.android

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Message
import com.google.zxing.Result
import com.jxkj.scanqr.camera.CameraManager
import com.jxkj.scanqr.common.Constant
import com.jxkj.scanqr.decode.DecodeThread
import com.jxkj.scanqr.view.ViewfinderResultPointCallback

/**
 * Created by DY on 2019/12/23.
 */
class CaptureActivityHandler(private val activity: CaptureActivity, cameraManager: CameraManager?) : Handler() {
    private val decodeThread: DecodeThread = DecodeThread(activity, ViewfinderResultPointCallback(
            activity.viewfinderView))
    private var state: State
    private val cameraManager: CameraManager?

    private enum class State {
        PREVIEW, SUCCESS, DONE
    }

    override fun handleMessage(message: Message) {
        when (message.what) {
            Constant.RESTART_PREVIEW ->                 // 重新预览
                restartPreviewAndDecode()
            Constant.DECODE_SUCCEEDED -> {
                // 解码成功
                state = State.SUCCESS
                activity.handleDecode(message.obj as Result)
            }
            Constant.DECODE_FAILED -> {

                // 尽可能快的解码，以便可以在解码失败时，开始另一次解码
                state = State.PREVIEW
                cameraManager?.requestPreviewFrame(decodeThread.handler,
                        Constant.DECODE)
            }
            Constant.RETURN_SCAN_RESULT -> {
                activity.setResult(Activity.RESULT_OK, message.obj as Intent)
                activity.finish()
            }
            Constant.FLASH_OPEN -> {
                activity.switchFlashImg(Constant.FLASH_OPEN)
            }
            Constant.FLASH_CLOSE -> {
                activity.switchFlashImg(Constant.FLASH_CLOSE)
            }
        }
    }

    /**
     * 完全退出
     */
    fun quitSynchronously() {
        state = State.DONE
        cameraManager?.stopPreview()
        val quit: Message = Message.obtain(decodeThread.handler, Constant.QUIT)
        quit.sendToTarget()
        try {
            // Wait at most half a second; should be enough time, and onPause()
            // will timeout quickly
            decodeThread.join(500L)
        } catch (e: InterruptedException) {
            // continue
        }

        // Be absolutely sure we don't send any queued up messages
        //确保不会发送任何队列消息
        removeMessages(Constant.DECODE_SUCCEEDED)
        removeMessages(Constant.DECODE_FAILED)
    }

    private fun restartPreviewAndDecode() {
        if (state == State.SUCCESS) {
            state = State.PREVIEW
            cameraManager?.requestPreviewFrame(decodeThread.handler,
                    Constant.DECODE)
            activity.drawViewfinder()
        }
    }
    init {
        decodeThread.start()
        state = State.SUCCESS

        // Start ourselves capturing previews and decoding.
        // 开始拍摄预览和解码
        this.cameraManager = cameraManager
        cameraManager?.startPreview()
        restartPreviewAndDecode()
    }
}