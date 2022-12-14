package com.jxkj.scanqr.camera

import android.hardware.Camera
import android.os.Handler
import android.util.Log

/**
 * Created by DY on 2019/12/23.
 */
class PreviewCallback internal constructor(private val configManager: CameraConfigurationManager) : Camera.PreviewCallback {
    private var previewHandler: Handler? = null
    private var previewMessage = 0
    fun setHandler(previewHandler: Handler?, previewMessage: Int) {
        this.previewHandler = previewHandler
        this.previewMessage = previewMessage
    }

    override fun onPreviewFrame(data: ByteArray, camera: Camera) {
        val cameraResolution = configManager.cameraResolution
        val thePreviewHandler = previewHandler
        if (cameraResolution != null && thePreviewHandler != null) {
            val message = thePreviewHandler.obtainMessage(previewMessage, cameraResolution.x,
                    cameraResolution.y, data)
            message.sendToTarget()
            previewHandler = null
        } else {
            Log.d(TAG, "Got preview callback, but no handler or resolution available")
        }
    }

    companion object {
        private val TAG = PreviewCallback::class.java.simpleName
    }
}