package com.jxkj.scanqr.bean

import androidx.annotation.ColorRes
import com.jxkj.scanqr.R
import java.io.Serializable

/**
 * Created by DY on 2019/12/23.
 */
class ZxingConfig : Serializable {
    /*是否播放声音*/
    var isPlayBeep = true

    /*是否震动*/
    var isShake = true

    /*是否显示下方的其他功能布局*/
    var isShowbottomLayout = true

    /*是否显示闪光灯按钮*/
    var isShowFlashLight = true

    /*是否显示相册按钮*/
    var isShowAlbum = true

    /*是否解析条形码*/
    var isDecodeBarCode = true

    /*是否全屏扫描*/
    var isFullScreenScan = true

    /*四个角的颜色*/
    @ColorRes
    var reactColor = R.color.react

    /*扫描框颜色*/
    @ColorRes
    var frameLineColor = -1

    /*扫描线颜色*/
    @ColorRes
    var scanLineColor = R.color.scanLineColor
}