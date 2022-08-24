package com.jxkj.scanqr.android

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.LinearLayoutCompat
import com.google.zxing.Result
import com.jxkj.scanqr.R
import com.jxkj.scanqr.bean.ScanCompleteEvent
import com.jxkj.scanqr.bean.ZxingConfig
import com.jxkj.scanqr.camera.CameraManager
import com.jxkj.scanqr.common.Constant
import com.jxkj.scanqr.decode.*
import com.jxkj.scanqr.view.ViewfinderView
import org.greenrobot.eventbus.EventBus
import java.io.IOException

/**
 * Created by DY on 2019/12/23.
 */
open class CaptureActivity : AppCompatActivity(), SurfaceHolder.Callback, View.OnClickListener {
    lateinit var config: ZxingConfig
    protected var previewView: SurfaceView? = null
    var viewfinderView: ViewfinderView? = null
        protected set
    protected var flashLightIv: AppCompatImageView? = null
    protected var flashLightTv: TextView? = null
    protected var backIv: AppCompatImageView? = null
    protected var flashLightLayout: LinearLayoutCompat? = null
    protected var albumLayout: LinearLayoutCompat? = null
    protected var bottomLayout: LinearLayoutCompat? = null
    protected var hasSurface = false
    protected var inactivityTimer: InactivityTimer? = null
    protected var beepManager: BeepManager? = null
    var cameraManager: CameraManager? = null
        protected set
    var handler: CaptureActivityHandler? = null
    protected var surfaceHolder: SurfaceHolder? = null

    fun drawViewfinder() {
        viewfinderView?.drawViewfinder()
    }

    companion object {
        protected val TAG = CaptureActivity::class.java.simpleName

        /**
         * @param pm
         * @return 是否有闪光灯
         */
        fun isSupportCameraLedFlash(pm: PackageManager?): Boolean {
            if (pm != null) {
                val features = pm.systemAvailableFeatures
                if (features != null) {
                    for (f in features) {
                        if (f != null && PackageManager.FEATURE_CAMERA_FLASH == f.name) {
                            return true
                        }
                    }
                }
            }
            return false
        }

        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 保持Activity处于唤醒状态
        val window = window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.statusBarColor = Color.BLACK
        }

        /*先获取配置信息*/
        val extra: Any? = intent.getSerializableExtra(Constant.INTENT_ZXING_CONFIG)
        config = if (extra != null && extra is ZxingConfig) extra else ZxingConfig()
        setContentView(R.layout.activity_capture)
        initView()
        hasSurface = false
        inactivityTimer = InactivityTimer(this)
        beepManager = BeepManager(this)
        beepManager?.setPlayBeep(config.isPlayBeep)
        beepManager?.setVibrate(config.isShake)
    }

    protected fun initView() {
        previewView = findViewById(R.id.preview_view)
        previewView?.setOnClickListener(this)
        viewfinderView = findViewById(R.id.viewfinder_view)
        viewfinderView?.setZxingConfig(config)
        backIv = findViewById(R.id.backIv)
        backIv?.setOnClickListener(this)
        flashLightIv = findViewById(R.id.flashLightIv)
        flashLightTv = findViewById(R.id.flashLightTv)
        flashLightLayout = findViewById(R.id.flashLightLayout)
        flashLightLayout?.setOnClickListener(this)
        albumLayout = findViewById(R.id.albumLayout)
        albumLayout?.setOnClickListener(this)
        bottomLayout = findViewById(R.id.bottomLayout)
        switchVisibility(bottomLayout, config.isShowbottomLayout)
        switchVisibility(flashLightLayout, config.isShowFlashLight)
        switchVisibility(albumLayout, config.isShowAlbum)

/*有闪光灯就显示手电筒按钮  否则不显示*/
        if (isSupportCameraLedFlash(packageManager)) {
            flashLightLayout?.visibility = View.VISIBLE
        } else {
            flashLightLayout?.visibility = View.GONE
        }
    }

    /**
     * @param flashState 切换闪光灯图片
     */
    fun switchFlashImg(flashState: Int) {
        if (flashState == Constant.FLASH_OPEN) {
            flashLightIv?.setImageResource(R.drawable.ic_open)
            flashLightTv?.setText(R.string.close_flash)
        } else {
            flashLightIv?.setImageResource(R.drawable.ic_close)
            flashLightTv?.setText(R.string.open_flash)
        }
    }

    /**
     * @param rawResult 返回的扫描结果
     */
    fun handleDecode(rawResult: Result) {
        inactivityTimer?.onActivity()
        beepManager?.playBeepSoundAndVibrate()
        EventBus.getDefault().post(ScanCompleteEvent(rawResult.text))
        finish()
    }

    protected fun switchVisibility(view: View?, b: Boolean) {
        if (b) {
            view?.visibility = View.VISIBLE
        } else {
            view?.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        cameraManager = CameraManager(application, config)
        viewfinderView?.setCameraManager(cameraManager)
        handler = null
        surfaceHolder = previewView?.holder
        if (hasSurface) {
            initCamera(surfaceHolder)
        } else {
            // 重置callback，等待surfaceCreated()来初始化camera
            surfaceHolder?.addCallback(this)
        }
        beepManager?.updatePrefs()
        inactivityTimer?.onResume()
    }

    protected fun initCamera(surfaceHolder: SurfaceHolder?) {
        checkNotNull(surfaceHolder) { "No SurfaceHolder provided" }
        if (cameraManager?.isOpen == true) {
            return
        }
        try {
            // 打开Camera硬件设备
            cameraManager?.openDriver(surfaceHolder)
            // 创建一个handler来打开预览，并抛出一个运行时异常
            if (handler == null) {
                handler = CaptureActivityHandler(this, cameraManager)
            }
        } catch (ioe: IOException) {
            Log.w(TAG, ioe)
            displayFrameworkBugMessageAndExit()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Unexpected error initializing camera", e)
            displayFrameworkBugMessageAndExit()
        }
    }

    protected fun displayFrameworkBugMessageAndExit() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("扫一扫")
        builder.setMessage(getString(R.string.msg_camera_framework_bug))
        builder.setPositiveButton(R.string.button_ok, FinishListener(this))
        builder.setOnCancelListener(FinishListener(this))
        builder.show()
    }

    override fun onPause() {
        Log.i("CaptureActivity", "onPause")
        if (handler != null) {
            handler?.quitSynchronously()
            handler = null
        }
        inactivityTimer?.onPause()
        beepManager?.close()
        cameraManager?.closeDriver()
        if (!hasSurface) {
            surfaceHolder?.removeCallback(this)
        }
        super.onPause()
    }

    override fun onDestroy() {
        inactivityTimer?.shutdown()
        viewfinderView?.stopAnimator()
        super.onDestroy()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!hasSurface) {
            hasSurface = true
            initCamera(holder)
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        hasSurface = false
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int,
                                height: Int) {
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.flashLightLayout) {
            /*切换闪光灯*/
            cameraManager?.switchFlashLight(handler)
        } else if (id == R.id.albumLayout) {
            /*打开相册*/
            val intent = Intent()
            intent.action = Intent.ACTION_PICK
            intent.type = "image/*"
            startActivityForResult(intent, Constant.REQUEST_IMAGE)
        } else if (id == R.id.backIv) {
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Constant.REQUEST_IMAGE && resultCode == RESULT_OK) {
            val path = ImageUtil.getImageAbsolutePath(this, data?.data)
            DecodeImgThread(path, object : DecodeImgCallback {
                override fun onImageDecodeSuccess(result: Result) {
                    handleDecode(result)
                }

                override fun onImageDecodeFailed() {
                    Toast.makeText(this@CaptureActivity, R.string.scan_failed_tip, Toast.LENGTH_SHORT).show()
                }
            }).start()
        }
    }
}