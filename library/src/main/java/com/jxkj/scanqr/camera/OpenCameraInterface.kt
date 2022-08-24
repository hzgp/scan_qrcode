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
import android.hardware.Camera
import android.util.Log

/**
 * Abstraction over the [Camera] API that helps open them and return their metadata.
 */
// camera APIs
object OpenCameraInterface {
    private val TAG = OpenCameraInterface::class.java.name

    /**
     * Opens the requested camera with [Camera.open], if one exists.
     *
     * @param cameraId camera ID of the camera to use. A negative value means "no preference"
     * @return handle to [Camera] that was opened
     */
    @SuppressLint("NewApi")
    fun open(cameraId: Int): Camera? {
        var dyCameraId = cameraId
        val numCameras = Camera.getNumberOfCameras()
        if (numCameras == 0) {
            Log.w(TAG, "No cameras!")
            return null
        }
        val explicitRequest = dyCameraId >= 0
        if (!explicitRequest) {
            // Select a camera if no explicit camera requested
            var index = 0
            while (index < numCameras) {
                val cameraInfo = Camera.CameraInfo()
                Camera.getCameraInfo(index, cameraInfo)
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    break
                }
                index++
            }
            dyCameraId = index
        }
        val camera: Camera?
        camera = if (dyCameraId < numCameras) {
            Log.i(TAG, "Opening camera #$dyCameraId")
            Camera.open(dyCameraId)
        } else {
            if (explicitRequest) {
                Log.w(TAG, "Requested camera does not exist: $dyCameraId")
                null
            } else {
                Log.i(TAG, "No camera facing back; returning camera #0")
                Camera.open(0)
            }
        }
        return camera
    }

    /**
     * Opens a rear-facing camera with [Camera.open], if one exists, or opens camera 0.
     *
     * @return handle to [Camera] that was opened
     */
    fun open(): Camera? {
        return open(-1)
    }
}