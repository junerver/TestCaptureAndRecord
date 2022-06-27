package xyz.junerver.testcaptureandrecord

import android.util.Log
import xyz.junerver.testcaptureandrecord.LogUtils

/**
 * Description:
 *
 * @author Junerver
 * date: 2022/6/20-9:37
 * Email: junerver@gmail.com
 * Version: v1.0
 */
object LogUtils {
    private const val TAG = "CaptureScreen"
    @JvmStatic
    fun d(msg: String?) {
        Log.d(TAG, msg!!)
    }

    @JvmStatic
    fun e(msg: String?) {
        Log.e(TAG, msg!!)
    }
}