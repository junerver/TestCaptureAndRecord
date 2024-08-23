package xyz.junerver.testcaptureandrecord.utils

import android.util.Log

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

    @JvmStatic
    fun i(msg: String?) {
        Log.i(TAG, msg!!)
    }
}