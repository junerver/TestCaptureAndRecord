package xyz.junerver.testcaptureandrecord

import android.graphics.Bitmap

/**
 * by 王喆   at 2017/7/6.
 */
fun interface OnBitmapAvailableListener {
    fun onAvailable(bitmap: Bitmap?)
}