/*
 * Copyright (C) 2012 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jxkj.scanqr.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.hardware.Camera
import android.os.AsyncTask
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.jxkj.utils.AppManager
import com.jxkj.utils.CommonUtils
import com.jxkj.utils.Tools
import java.util.*
import java.util.concurrent.RejectedExecutionException

internal  // camera APIs
class AutoFocusManager(private val camera: Camera?, private val cameraManager: CameraManager, private val context: Context) : Camera.AutoFocusCallback {
    companion object {
        private val TAG = AutoFocusManager::class.java.simpleName

        /*聚焦间隔*/
        private const val AUTO_FOCUS_INTERVAL_MS = 1000L
        private var FOCUS_MODES_CALLING_AF: MutableCollection<String>? = null

        init {
            FOCUS_MODES_CALLING_AF = ArrayList()
            FOCUS_MODES_CALLING_AF?.run {
                add(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
                add(Camera.Parameters.FOCUS_MODE_AUTO)
                add(Camera.Parameters.FOCUS_MODE_MACRO)
            }
        }
    }

    private var stopped = false
    private var focusing = false
    private val useAutoFocus = true
    private var outstandingTask: AsyncTask<*, *, *>? = null
    private val focusRect: Rect? = null
    @Synchronized
    override fun onAutoFocus(success: Boolean, theCamera: Camera) {
        focusing = false
        autoFocusAgainLater()
        if (success) {
            //  logArea();
            hidemDialog()
        }
        Log.e("TAG", "success:$success")
    }

    @SuppressLint("NewApi")
    @Synchronized
    private fun autoFocusAgainLater() {
        if (!stopped && outstandingTask == null) {
            val newTask = AutoFocusTask()
            try {
                //  newTask.execute();
                newTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
                outstandingTask = newTask
            } catch (ree: RejectedExecutionException) {
                Log.w(TAG, "Could not request auto focus", ree)
            }
        }
    }

    private var isFirstFocus = true
    protected var mDialog: AlertDialog? = null
    @Synchronized
    fun start() {
        if (isFirstFocus) {
            showmDialog("聚焦中...")
            clearCameraFocus()
            setArea()
            isFirstFocus = false
        }
        if (useAutoFocus) {
            outstandingTask = null
            if (!stopped && !focusing) {
                try {
                    //focusOnTouch();
                    camera!!.autoFocus(this)
                    focusing = true
                } catch (re: RuntimeException) {
                    // Have heard RuntimeException reported in Android 4.0.x+; continue?
                    Log.w(TAG, "Unexpected exception while focusing", re)
                    // Try again later to keep cycle going
                    autoFocusAgainLater()
                }
            }
        }
    }

    /**
     * 清除自动对焦
     */
    private var parameters: Camera.Parameters? = null
    private fun clearCameraFocus() {
        camera?.cancelAutoFocus()
        parameters = camera?.parameters?.apply {
            focusAreas = null
            meteringAreas = null
        }
        try {
            camera?.parameters = parameters
        } catch (e: Exception) {
            Log.e(TAG, "failed to set parameters.\n$e")
        }
    }

    private fun setArea() {
        val frameRect = cameraManager.reqRamingRect()
        val focusRect = calculateTapArea(frameRect, 1f)
        val meteringRect = calculateTapArea(frameRect, 1f)
        // To start tap focus, should cancel auto focus first.
        val mFocusList: MutableList<Camera.Area> = ArrayList()
        mFocusList.add(Camera.Area(focusRect, 1000))
        val mMeteringList: MutableList<Camera.Area> = ArrayList()
        mMeteringList.add(Camera.Area(meteringRect, 1000))
        parameters = camera?.parameters?.apply {
            focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            if (maxNumFocusAreas > 0) { // Check if it is safe to set focusArea.
                focusAreas = mFocusList
            }
            if (maxNumMeteringAreas > 0) { // Check if it is safe to set meteringArea.
                meteringAreas = mMeteringList
            }
        }
        camera?.parameters = parameters
    }

    private fun calculateTapArea(frameRect: Rect?, coefficient: Float): Rect {
        val FOCUS_AREA_SIZE = Math.max(frameRect!!.width(), frameRect.height())
        val x = frameRect.centerX()
        val y = frameRect.centerY()
        //        //计算点击坐标点在新的坐标系中的位置
        val areaSize = java.lang.Float.valueOf(FOCUS_AREA_SIZE * coefficient).toInt()
        val left = clamp(java.lang.Float.valueOf(y * 1.0f / Tools.getScreenHeight(context) * 2000 - 1000).toInt(), areaSize)
        val top = clamp(java.lang.Float.valueOf((Tools.getScreenWidth(context) - x * 1.0f) / Tools.getScreenWidth(context) * 2000 - 1000).toInt(), areaSize)
        return Rect(left, top, left + areaSize, top + areaSize)
    }

    /**
     * 确保所选区域在合理范围内,不会超过边界值
     */
    private fun clamp(touchCoordinateInCameraReper: Int, focusAreaSize: Int): Int {
        val result: Int
        result = if (Math.abs(touchCoordinateInCameraReper) + focusAreaSize > 1000) {
            if (touchCoordinateInCameraReper > 0) {
                1000 - focusAreaSize
            } else {
                -1000 + focusAreaSize
            }
        } else {
            touchCoordinateInCameraReper - focusAreaSize / 2
        }
        return result
    }

    //    public void focusOnTouch() {
    //        int width = Tools.getScreenWidth(context);
    //        int screenHeight = Tools.getScreenHeight(context);
    //        Rect rect =cameraManager.getFramingRect();
    //        int left = rect.left * 2000 / width - 1000;
    //        int top = rect.top * 2000 / screenHeight - 1000;
    //        int right = rect.right * 2000 / width - 1000;
    //        int bottom = rect.bottom * 2000 / screenHeight - 1000;
    //        // 如果超出了(-1000,1000)到(1000, 1000)的范围，则会导致相机崩溃
    //        left = Math.max(left, -1000);
    //        top = Math.max(top, -1000);
    //        right = Math.min(right, 1000);
    //        bottom = Math.min(bottom, 1000);
    //        focusOnRect(new Rect(-1000, top, 1000, bottom));
    //    }
    //
    //    private void logArea(){
    //        Camera.Parameters parameters = camera.getParameters();
    //        List<Camera.Area> areas = parameters.getFocusAreas();
    //        for (Camera.Area area:areas){
    //            Log.e("TAG",area.rect+" "+ area.weight);
    //        }
    //    }
    //    protected void focusOnRect(Rect rect) {
    //        if (camera != null) {
    //            Camera.Parameters parameters = camera.getParameters(); // 先获取当前相机的参数配置对象
    //            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置聚焦模式
    //            Log.d(TAG, "parameters.getMaxNumFocusAreas() : " + parameters.getMaxNumFocusAreas());
    //            if (parameters.getMaxNumFocusAreas() > 0) {
    //                List<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
    //                focusAreas.add(new Camera.Area(rect, 1000));
    //                parameters.setFocusAreas(focusAreas);
    //            }
    //            camera.cancelAutoFocus(); // 先要取消掉进程中所有的聚焦功能
    //            camera.setParameters(parameters); // 一定要记得把相应参数设置给相机
    //            camera.autoFocus(this);
    //        }
    //    }
    @Synchronized
    private fun cancelOutstandingTask() {
        if (outstandingTask != null) {
            if (outstandingTask!!.status != AsyncTask.Status.FINISHED) {
                outstandingTask!!.cancel(true)
            }
            outstandingTask = null
        }
    }

    protected fun showmDialog(content: String?) {
        val current = AppManager.getAppManager().currentActivity()
        if (current.isFinishing)return
        if (mDialog == null) {

            mDialog = CommonUtils.getLoadingDialog(current, content)
        }
        mDialog?.show()
    }

    protected fun hidemDialog() {
        if (mDialog != null && mDialog?.isShowing==true) {
            mDialog?.dismiss()
            mDialog = null
        }
    }

    @Synchronized
    fun stop() {
        stopped = true
        hidemDialog()
        if (useAutoFocus) {
            cancelOutstandingTask()
            // Doesn't hurt to call this even if not focusing
            try {
                camera!!.cancelAutoFocus()
            } catch (re: RuntimeException) {
                // Have heard RuntimeException reported in Android 4.0.x+; continue?
                Log.w(TAG, "Unexpected exception while cancelling focusing", re)
            }
        }
    }

    private inner class AutoFocusTask : AsyncTask<Any, Any?, Any?>() {
        override fun doInBackground(vararg voids: Any): Any? {
            try {
                Thread.sleep(AUTO_FOCUS_INTERVAL_MS)
            } catch (e: InterruptedException) {
                // continue
            }
            start()
            return null
        }
    }

    init {
        start()
    }
}