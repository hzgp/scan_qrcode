package com.jxkj.scanqr.android

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Vibrator
import android.util.Log
import com.jxkj.scanqr.R
import java.io.Closeable
import java.io.IOException

/**
 * Created by DY on 2019/12/23.
 */
class BeepManager(private val activity: Activity) : MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, Closeable {
    private var mediaPlayer: MediaPlayer? = null
    private var playBeep = false
    private var vibrate = false
    fun setPlayBeep(playBeep: Boolean) {
        this.playBeep = playBeep
    }

    fun setVibrate(vibrate: Boolean) {
        this.vibrate = vibrate
    }

    @Synchronized
    fun updatePrefs() {
        if (playBeep && mediaPlayer == null) {
            // The volume on STREAM_SYSTEM is not adjustable, and users found it
            // too loud,
            // so we now play on the music stream.
            // 设置activity音量控制键控制的音频流
            activity.volumeControlStream = AudioManager.STREAM_MUSIC
            mediaPlayer = buildMediaPlayer(activity)
        }
    }

    /**
     * 开启响铃和震动
     */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer!!.start()
        }
        if (vibrate) {
            val vibrator = activity
                    .getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VIBRATE_DURATION)
        }
    }

    /**
     * 创建MediaPlayer
     *
     * @param activity
     * @return
     */
    private fun buildMediaPlayer(activity: Context): MediaPlayer? {
        val mediaPlayer = MediaPlayer()
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        // 监听是否播放完成
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnErrorListener(this)
        // 配置播放资源
        return try {
            val file = activity.resources
                    .openRawResourceFd(R.raw.beep)
            try {
                mediaPlayer.setDataSource(file.fileDescriptor,
                        file.startOffset, file.length)
            } finally {
                file.close()
            }
            // 设置音量
            mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME)
            mediaPlayer.prepare()
            mediaPlayer
        } catch (ioe: IOException) {
            Log.w(TAG, ioe)
            mediaPlayer.release()
            null
        }
    }

    override fun onCompletion(mp: MediaPlayer) {
        // When the beep has finished playing, rewind to queue up another one.
        mp.seekTo(0)
    }

    @Synchronized
    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            // we are finished, so put up an appropriate error toast if required
            // and finish
            activity.finish()
        } else {
            // possibly media player error, so release and recreate
            mp.release()
            mediaPlayer = null
            updatePrefs()
        }
        return true
    }

    @Synchronized
    override fun close() {
        if (mediaPlayer != null) {
            mediaPlayer!!.release()
            mediaPlayer = null
        }
    }

    companion object {
        private val TAG = BeepManager::class.java.simpleName
        private const val BEEP_VOLUME = 0.10f
        private const val VIBRATE_DURATION = 200L
    }

    init {
        updatePrefs()
    }
}