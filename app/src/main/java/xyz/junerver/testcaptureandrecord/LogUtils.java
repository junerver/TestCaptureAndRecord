package xyz.junerver.testcaptureandrecord;

import android.util.Log;

/**
 * Description:
 *
 * @author Junerver
 * date: 2022/6/20-9:37
 * Email: junerver@gmail.com
 * Version: v1.0
 */
class LogUtils {
    private static final String TAG = "CaptureScreen";
    public static void d( String msg) {
        Log.d(TAG, msg);
    }

    public static void e( String msg) {
        Log.e(TAG, msg);
    }
}
