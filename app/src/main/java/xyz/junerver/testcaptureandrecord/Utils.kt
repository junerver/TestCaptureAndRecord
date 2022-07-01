package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.content.Intent

/**
 * Description:
 *
 * @author Junerver
 * date: 2022/6/20-9:53
 * Email: junerver@gmail.com
 * Version: v1.0
 */
internal object Utils {
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
}