package com.jxkj.scanqr.camera

import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.SurfaceHolder
import com.google.zxing.PlanarYUVLuminanceSource
import com.jxkj.scanqr.R
import com.jxkj.scanqr.android.CaptureActivityHandler
import com.jxkj.scanqr.bean.ZxingConfig
import com.jxkj.scanqr.common.Constant
import com.jxkj.utils.Tools
import java.io.IOException

/**
 * Created by DY on 2019/12/23.
 */
class CameraManager(private val context: Context, config: ZxingConfig?) {
    private val configManager: CameraConfigurationManager
    private var config: ZxingConfig?
    private var camera: Camera? = null
    private var autoFocusManager: AutoFocusManager? = null
    var framingRect: Rect? = null
    var framingRectInPreview: Rect? = null
    private var initialized = false
    private var previewing = false
    private var requestedCameraId = -1
    private var requestedFramingRectWidth = 0
    private var requestedFramingRectHeight = 0
    private val preViewHeight: Int

    /**
     * Preview frames are delivered here, which we pass on to the registered
     * handler. Make sure to clear the handler so it will only receive one
     * message.
     */
    private val previewCallback: PreviewCallback
    //    public static void init(Context context) {
    //        if (cameraManager == null) {
    //            cameraManager = new CameraManager(context, null);
    //        }
    //    }
    /**
     * Opens the camera driver and initializes the hardware parameters.
     *
     * @param holder The surface object which the camera will draw preview frames
     * into.
     * @throws IOException Indicates the camera driver failed to open.
     */
    @Synchronized
    @Throws(IOException::class)
    fun openDriver(holder: SurfaceHolder?) {
        var theCamera = camera
        if (theCamera == null) {
            theCamera = if (requestedCameraId >= 0) {
                OpenCameraInterface.open(requestedCameraId)
            } else {
                OpenCameraInterface.open()
            }
            if (theCamera == null) {
                throw IOException()
            }
            camera = theCamera
        }
        theCamera.setPreviewDisplay(holder)
        if (!initialized) {
            initialized = true
            configManager.initFromCameraParameters(theCamera)
            if (requestedFramingRectWidth > 0 && requestedFramingRectHeight > 0) {
                setManualFramingRect(requestedFramingRectWidth,
                        requestedFramingRectHeight)
                requestedFramingRectWidth = 0
                requestedFramingRectHeight = 0
            }
        }
        var parameters = theCamera.parameters
        val parametersFlattened = parameters?.flatten() // Save these, temporarily
        try {
            configManager.setDesiredCameraParameters(theCamera)
        } catch (re: RuntimeException) {
            // Driver failed
            Log.w(TAG,
                    "Camera rejected parameters. Setting only minimal safe-mode parameters")
            Log.i(TAG, "Resetting to saved camera params: "
                    + parametersFlattened)
            // Reset:
            if (parametersFlattened != null) {
                parameters = theCamera.parameters
                parameters.unflatten(parametersFlattened)
                try {
                    theCamera.parameters = parameters
                    configManager.setDesiredCameraParameters(theCamera)
                } catch (re2: RuntimeException) {
                    // Well, darn. Give up
                    Log.w(TAG,
                            "Camera rejected even safe-mode parameters! No configuration")
                }
            }
        }
    }

    @get:Synchronized
    val isOpen: Boolean
        get() = camera != null

    /**
     * Closes the camera driver if still in use.
     */
    @Synchronized
    fun closeDriver() {
        if (camera != null) {
            camera!!.release()
            camera = null
            // Make sure to clear these each time we close the camera, so that
            // any scanning rect
            // requested by intent is forgotten.
            framingRect = null
            framingRectInPreview = null
        }
    }

    /*切换闪光灯*/
    fun switchFlashLight(handler: CaptureActivityHandler?) {
        //  Log.i("打开闪光灯", "openFlashLight");
        val parameters = camera!!.parameters
        val msg = Message()
        val flashMode = parameters.flashMode
        if (flashMode == Camera.Parameters.FLASH_MODE_TORCH) {
            /*关闭闪光灯*/
            parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            msg.what = Constant.FLASH_CLOSE
        } else {
            /*打开闪光灯*/
            parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            msg.what = Constant.FLASH_OPEN
        }
        camera!!.parameters = parameters
        handler!!.sendMessage(msg)
    }

    /**
     * Asks the camera hardware to begin drawing preview frames to the screen.
     */
    @Synchronized
    fun startPreview() {
        val theCamera = camera
        if (theCamera != null && !previewing) {
            theCamera.startPreview()
            previewing = true
            autoFocusManager = AutoFocusManager(camera, this, context)
        }
    }

    /**
     * Tells the camera to stop drawing preview frames.
     */
    @Synchronized
    fun stopPreview() {
        if (autoFocusManager != null) {
            autoFocusManager!!.stop()
            autoFocusManager = null
        }
        if (camera != null && previewing) {
            camera!!.stopPreview()
            previewCallback.setHandler(null, 0)
            previewing = false
        }
    }

    /**
     * A single preview frame will be returned to the handler supplied. The data
     * will arrive as byte[] in the message.obj field, with width and height
     * encoded as message.arg1 and message.arg2, respectively.
     *
     * @param handler The handler to send the message to.
     * @param message The what field of the message to be sent.
     */
    @Synchronized
    fun requestPreviewFrame(handler: Handler?, message: Int) {
        val theCamera = camera
        if (theCamera != null && previewing) {
            previewCallback.setHandler(handler, message)
            theCamera.setOneShotPreviewCallback(previewCallback)
        }
    }

    /*取景框*/
    @Synchronized
    fun reqRamingRect(): Rect? {
        if (framingRect == null) {
            if (camera == null) {
                return null
            }
            val screenResolution = configManager.screenResolution
                    ?: // Called early, before init even finished
                    return null
            val screenResolutionX = screenResolution.x
            val screenResolutions = Math.min(screenResolutionX, preViewHeight)
            val width = (screenResolutions * 0.8).toInt()


            /*水平居中  偏上显示*/
            val leftOffset = (screenResolution.x - width) / 2
            val topOffset = (preViewHeight - width) / 3
            framingRect = Rect(leftOffset, topOffset, leftOffset + width,
                    topOffset + width)
            Log.d(TAG, "Calculated framing rect: $framingRect")
        }
        return framingRect
    }

    /**
     * Like [.getFramingRect] but coordinates are in terms of the preview
     * frame, not UI / screen.
     *
     * @return [Rect] expressing barcode scan area in terms of the preview
     * size
     */
    @Synchronized
    fun reqFramingRectInPreview(): Rect? {
        if (framingRectInPreview == null) {
            val framingRect = reqRamingRect() ?: return null
            val rect = Rect(framingRect)
            val cameraResolution = configManager.cameraResolution
            val screenResolution = configManager.screenResolution
            if (cameraResolution == null || screenResolution == null) {
                // Called early, before init even finished
                return null
            }
            /******************** 竖屏更改1(cameraResolution.x/y互换)  */
            rect.left = rect.left * cameraResolution.y / screenResolution.x
            rect.right = rect.right * cameraResolution.y / screenResolution.x
            rect.top = rect.top * cameraResolution.x / preViewHeight
            rect.bottom = rect.bottom * cameraResolution.x / preViewHeight
            framingRectInPreview = rect
        }
        return framingRectInPreview
    }

    /**
     * Allows third party apps to specify the camera ID, rather than determine
     * it automatically based on available cameras and their orientation.
     *
     * @param cameraId camera ID of the camera to use. A negative value means
     * "no preference".
     */
    @Synchronized
    fun setManualCameraId(cameraId: Int) {
        requestedCameraId = cameraId
    }

    /**
     * Allows third party apps to specify the scanning rectangle dimensions,
     * rather than determine them automatically based on screen resolution.
     *
     * @param width  The width in pixels to scan.
     * @param height The height in pixels to scan.
     */
    @Synchronized
    fun setManualFramingRect(width: Int, height: Int) {
        var w = width
        var h = height
        if (initialized) {
            val screenResolution = configManager.screenResolution
            if (w > screenResolution!!.x) {
                w = screenResolution.x
            }
            if (h > screenResolution.y) {
                h = screenResolution.y
            }
            val leftOffset = (screenResolution.x - w) / 2
            val topOffset = (screenResolution.y - h) / 5
            framingRect = Rect(leftOffset, topOffset, leftOffset + w,
                    topOffset + h)
            Log.d(TAG, "Calculated manual framing rect: $framingRect")
            framingRectInPreview = null
        } else {
            requestedFramingRectWidth = w
            requestedFramingRectHeight = h
        }
    }

    /**
     * A factory method to build the appropriate LuminanceSource object based on
     * the format of the preview buffers, as described by Camera.Parameters.
     *
     * @param data   A preview frame.
     * @param width  The width of the image.
     * @param height The height of the image.
     * @return A PlanarYUVLuminanceSource instance.
     */
    fun buildLuminanceSource(data: ByteArray?,
                             width: Int, height: Int): PlanarYUVLuminanceSource? {
        val rect = reqFramingRectInPreview() ?: return null
        // Go ahead and assume it's YUV rather than die.
        if (config == null) {
            config = ZxingConfig()
        }
        return if (config!!.isFullScreenScan) {
            PlanarYUVLuminanceSource(data, width, height, 0,
                    0, width, height, false)
        } else {
            val actionbarHeight = context.resources.getDimensionPixelSize(R.dimen.dp_45)
            PlanarYUVLuminanceSource(data, width, height, rect.left,
                    rect.top + actionbarHeight, rect.width(), rect.height(), false)
        }
    }

    companion object {
        private val TAG = CameraManager::class.java.simpleName
        private var cameraManager: CameraManager? = null
        fun setCameraManager(cameraManager: CameraManager?) {
            Companion.cameraManager = cameraManager
        }

        fun get(): CameraManager? {
            return cameraManager
        }
    }

    init {
        configManager = CameraConfigurationManager(context)
        previewCallback = PreviewCallback(configManager)
        this.config = config
        preViewHeight = Tools.dp2px(context, 200f)
    }
}