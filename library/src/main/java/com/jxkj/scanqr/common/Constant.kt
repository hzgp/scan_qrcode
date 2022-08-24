package com.jxkj.scanqr.common

/**
 * Created by DY on 2019/12/23.
 */
interface Constant {
    companion object {
        const val DECODE = 1
        const val DECODE_FAILED = 2
        const val DECODE_SUCCEEDED = 3
        const val LAUNCH_PRODUCT_QUERY = 4
        const val QUIT = 5
        const val RESTART_PREVIEW = 6
        const val RETURN_SCAN_RESULT = 7
        const val FLASH_OPEN = 8
        const val FLASH_CLOSE = 9
        const val REQUEST_IMAGE = 10
        const val CODED_CONTENT = "codedContent"
        const val CODED_BITMAP = "codedBitmap"
        const val CODED_TYPE = "codedType"

        /*传递的zxingconfing*/
        const val INTENT_ZXING_CONFIG = "zxingConfig"
    }
}