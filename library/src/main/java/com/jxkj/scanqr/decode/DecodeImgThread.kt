package com.jxkj.scanqr.decode

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.TextUtils
import android.util.Log
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.*

/**
 * Created by DY on 2019/12/23.
 */
class DecodeImgThread(/*图片路径*/
        private val imgPath: String?, /*回调*/
        private val callback: DecodeImgCallback?) : Thread() {
    private val scanBitmap: Bitmap? = null
    override fun run() {
        super.run()
        if (TextUtils.isEmpty(imgPath) || callback == null) {
            return
        }
        val scanBitmap = getBitmap(imgPath, 400, 400)
        val multiFormatReader = MultiFormatReader()
        // 解码的参数
        val hints = Hashtable<DecodeHintType, Any?>(2)
        // 可以解析的编码类型
        val decodeFormats = Vector<BarcodeFormat?>()


        // 扫描的类型  一维码和二维码
        decodeFormats.addAll(DecodeFormatManager.ONE_D_FORMATS!!)
        decodeFormats.addAll(DecodeFormatManager.QR_CODE_FORMATS)
        decodeFormats.addAll(DecodeFormatManager.DATA_MATRIX_FORMATS)
        hints[DecodeHintType.POSSIBLE_FORMATS] = decodeFormats
        // 设置解析的字符编码格式为UTF8
        //  hints.put(DecodeHintType.CHARACTER_SET, "UTF8");
        // 设置解析配置参数
        multiFormatReader.setHints(hints)
        // 开始对图像资源解码
        var rawResult: Result? = null
        try {
            rawResult = multiFormatReader.decodeWithState(BinaryBitmap(HybridBinarizer(BitmapLuminanceSource(scanBitmap))))
            Log.i("解析结果", rawResult.text)
        } catch (e: Exception) {
            e.printStackTrace()
            //  Log.i("解析的图片结果","失败");
        }
        if (rawResult != null) {
            callback.onImageDecodeSuccess(rawResult)
        } else {
            callback.onImageDecodeFailed()
        }
    }

    companion object {
        /**
         * 根据路径获取图片
         *
         * @param filePath  文件路径
         * @param maxWidth  图片最大宽度
         * @param maxHeight 图片最大高度
         * @return bitmap
         */
        private fun getBitmap(filePath: String?, maxWidth: Int, maxHeight: Int): Bitmap {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(filePath, options)
            options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(filePath, options)
        }

        /**
         * Return the sample size.
         *
         * @param options   The options.
         * @param maxWidth  The maximum width.
         * @param maxHeight The maximum height.
         * @return the sample size
         */
        private fun calculateInSampleSize(options: BitmapFactory.Options,
                                          maxWidth: Int,
                                          maxHeight: Int): Int {
            var height = options.outHeight
            var width = options.outWidth
            var inSampleSize = 1
            while (1.let { width = width shr it; width } >= maxWidth && 1.let { height = height shr it; height } >= maxHeight) {
                inSampleSize = inSampleSize shl 1
            }
            return inSampleSize
        }
    }
}