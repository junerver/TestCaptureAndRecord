package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager

/**
 * Description:
 *
 * @author Junerver
 * date: 2022/6/20-9:53
 * Email: junerver@gmail.com
 * Version: v1.0
 */
object Utils {
    const val REQUEST_MEDIA_PROJECTION = 1

    /**
     * 申请录屏权限
     */
    fun createPermission(activity: Activity) {
        val mediaProjectionManager =
            activity.application.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION)
    }

    //获得视频格式，编码解码都需要使用
    fun getMediaFormat(): MediaFormat {
        val mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, 720, 1280)
        //比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 409600)
        //必须设置25左右大小,才能对修改码率生效,其他值无效
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
        //固定码率
        mediaFormat.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        )
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        //设置I帧间隔，注意这里设置的间隔可能不会生效，原因比较复杂，有可能是因为上面的color_format设置不正确导致的
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        return mediaFormat
    }
}