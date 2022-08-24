/*
 * Copyright (C) 2008 ZXing authors
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
package com.jxkj.scanqr.decode

import android.os.Handler
import android.os.Looper
import com.google.zxing.BarcodeFormat
import com.google.zxing.DecodeHintType
import com.google.zxing.ResultPointCallback
import com.jxkj.scanqr.android.CaptureActivity
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * This thread does all the heavy lifting of decoding the images.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
class DecodeThread(private val activity: CaptureActivity, resultPointCallback: ResultPointCallback?) : Thread() {
    private val hints: Hashtable<DecodeHintType?, Any?>
     var handler: Handler? = null
      get() {
        try {
            handlerInitLatch.await()
        } catch (ie: InterruptedException) {
            // continue?
        }
        return field
    }
    private val handlerInitLatch: CountDownLatch

    override fun run() {
        Looper.prepare()
        handler = DecodeHandler(activity, hints)
        handlerInitLatch.countDown()
        Looper.loop()
    }

    init {
        handlerInitLatch = CountDownLatch(1)
        hints = Hashtable()
        val decodeFormats = Vector<BarcodeFormat?>()

        /*是否解析有条形码（一维码）*/if (activity.config.isDecodeBarCode) {
            decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS!!)
        }
        decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS)
        decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS)
        hints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
        hints[DecodeHintType.CHARACTER_SET] = "UTF-8"
        hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] = resultPointCallback
    }
}