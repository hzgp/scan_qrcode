package com.jxkj.scanqr.camera

import android.content.Context
import android.graphics.Point
import android.hardware.Camera
import android.util.Log
import android.view.WindowManager
import com.jxkj.utils.Tools
import java.lang.Boolean
import java.util.regex.Pattern

/**
 * Created by DY on 2019/12/23.
 */
class CameraConfigurationManager internal constructor(private val context: Context) {
    var screenResolution: Point? = null
        private set
    var cameraResolution: Point? = null
        private set

    fun initFromCameraParameters(camera: Camera) {
        val parameters = camera.parameters
        val manager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = manager.defaultDisplay
        screenResolution = Point(display.width, Tools.dp2px(context, 200f))
        Log.d(TAG, "Screen resolution: $screenResolution")
        val screenResolutionForCamera = Point()
        screenResolutionForCamera.x = screenResolution!!.x
        screenResolutionForCamera.y = screenResolution!!.y
        if (screenResolution!!.x < screenResolution!!.y) {
            screenResolutionForCamera.x = screenResolution!!.y
            screenResolutionForCamera.y = screenResolution!!.x
        }
        cameraResolution = getCameraResolution(parameters, screenResolutionForCamera)
    }

    fun setDesiredCameraParameters(camera: Camera) {
        val parameters = camera.parameters
        Log.d("TAG", "cameraResolution.x:" + cameraResolution!!.x + " cameraResolution.Y:" + cameraResolution!!.y)
        parameters.setPreviewSize(cameraResolution!!.x, cameraResolution!!.y)
        setZoom(parameters)
        //setSharpness(parameters);
        //modify here
        camera.setDisplayOrientation(90)
        camera.parameters = parameters
    }

    private fun setZoom(parameters: Camera.Parameters) {
        Log.i("CameraManager", "setZoom")
        val zoomSupportedString = parameters["zoom-supported"]
        if (zoomSupportedString != null && !Boolean.parseBoolean(zoomSupportedString)) {
            return
        }
        var tenDesiredZoom = TEN_DESIRED_ZOOM
        val maxZoomString = parameters["max-zoom"]
        if (maxZoomString != null) {
            try {
                val tenMaxZoom = (10.0 * maxZoomString.toDouble()).toInt()
                if (tenDesiredZoom > tenMaxZoom) {
                    tenDesiredZoom = tenMaxZoom
                }
            } catch (nfe: NumberFormatException) {
                Log.w(TAG, "Bad max-zoom: $maxZoomString")
            }
        }
        val takingPictureZoomMaxString = parameters["taking-picture-zoom-max"]
        if (takingPictureZoomMaxString != null) {
            try {
                val tenMaxZoom = takingPictureZoomMaxString.toInt()
                if (tenDesiredZoom > tenMaxZoom) {
                    tenDesiredZoom = tenMaxZoom
                }
            } catch (nfe: NumberFormatException) {
                Log.w(TAG, "Bad taking-picture-zoom-max: $takingPictureZoomMaxString")
            }
        }
        val motZoomValuesString = parameters["mot-zoom-values"]
        if (motZoomValuesString != null) {
            tenDesiredZoom = findBestMotZoomValue(motZoomValuesString, tenDesiredZoom)
        }
        val motZoomStepString = parameters["mot-zoom-step"]
        if (motZoomStepString != null) {
            try {
                val motZoomStep = motZoomStepString.trim { it <= ' ' }.toDouble()
                val tenZoomStep = (10.0 * motZoomStep).toInt()
                if (tenZoomStep > 1) {
                    tenDesiredZoom -= tenDesiredZoom % tenZoomStep
                }
            } catch (nfe: NumberFormatException) {
                // continue
            }
        }

        // Set zoom. This helps encourage the user to pull back.
        // Some devices like the Behold have a zoom parameter
        if (maxZoomString != null || motZoomValuesString != null) {
            parameters["zoom"] = (tenDesiredZoom / 10.0).toString()
        }

        // Most devices, like the Hero, appear to expose this zoom parameter.
        // It takes on values like "27" which appears to mean 2.7x zoom
        if (takingPictureZoomMaxString != null) {
            parameters["taking-picture-zoom"] = tenDesiredZoom
        }
    }

    companion object {
        private val TAG = CameraConfigurationManager::class.java.simpleName
        private const val TEN_DESIRED_ZOOM = 5
        const val desiredSharpness = 30
        private val COMMA_PATTERN = Pattern.compile(",")
        private fun getCameraResolution(parameters: Camera.Parameters, screenResolution: Point): Point {
            var previewSizeValueString = parameters["preview-size-values"]
            // saw this on Xperia
            if (previewSizeValueString == null) {
                previewSizeValueString = parameters["preview-size-value"]
            }
            Log.e("TAG", "previewSizeValueString:$previewSizeValueString")
            var cameraResolution: Point? = null
            if (previewSizeValueString != null) {
                Log.e(TAG, "preview-size-values parameter: $previewSizeValueString")
                cameraResolution = findBestPreviewSizeValue(previewSizeValueString, screenResolution)
            }
            if (cameraResolution == null) {
                // Ensure that the camera resolution is a multiple of 8, as the screen may not be.
                cameraResolution = Point(
                        screenResolution.x shr 3 shl 3,
                        screenResolution.y shr 3 shl 3)
            }
            return cameraResolution
        }

        private fun findBestPreviewSizeValue(previewSizeValueString: CharSequence, screenResolution: Point): Point? {
            var bestX = 0
            var bestY = 0
            var diff = Int.MAX_VALUE
            for (previewSize in COMMA_PATTERN.split(previewSizeValueString)) {
              val  formatSize = previewSize.trim { it <= ' ' }
                val dimPosition = formatSize.indexOf('x')
                if (dimPosition < 0) {
                    Log.w(TAG, "Bad preview-size: $formatSize")
                    continue
                }
                var newX: Int
                var newY: Int
                try {
                    newX = formatSize.substring(0, dimPosition).toInt()
                    newY = formatSize.substring(dimPosition + 1).toInt()
                } catch (nfe: NumberFormatException) {
                    Log.w(TAG, "Bad preview-size: $formatSize")
                    continue
                }
                val newDiff = Math.abs(newX - screenResolution.x) + Math.abs(newY - screenResolution.y)
                if (newDiff == 0) {
                    bestX = newX
                    bestY = newY
                    break
                } else if (newDiff < diff) {
                    bestX = newX
                    bestY = newY
                    diff = newDiff
                }
            }
            return if (bestX > 0 && bestY > 0) {
                Point(bestX, bestY)
            } else null
        }

        private fun findBestMotZoomValue(stringValues: CharSequence, tenDesiredZoom: Int): Int {
            Log.i("CameraManager", "findBestMotZoomValue")
            var tenBestValue = 0
            for (stringValue in COMMA_PATTERN.split(stringValues)) {
                val formatValue = stringValue.trim { it <= ' ' }
                var value: Double
                value = try {
                    formatValue.toDouble()
                } catch (nfe: NumberFormatException) {
                    return tenDesiredZoom
                }
                val tenValue = (10.0 * value).toInt()
                if (Math.abs(tenDesiredZoom - value) < Math.abs(tenDesiredZoom - tenBestValue)) {
                    tenBestValue = tenValue
                }
            }
            Log.i("findBestMotZoomValue", tenBestValue.toString() + "")
            return tenBestValue
        }
    }
}