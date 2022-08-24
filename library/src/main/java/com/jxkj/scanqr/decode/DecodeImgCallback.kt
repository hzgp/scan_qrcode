package com.jxkj.scanqr.decode

import com.google.zxing.Result

/**
 * Created by DY on 2019/12/23.
 */
interface DecodeImgCallback {
    fun onImageDecodeSuccess(result: Result)
    fun onImageDecodeFailed()
}