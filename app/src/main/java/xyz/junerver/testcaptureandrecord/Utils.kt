package xyz.junerver.testcaptureandrecord;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * Description:
 *
 * @author Junerver
 * date: 2022/6/20-9:53
 * Email: junerver@gmail.com
 * Version: v1.0
 */
class Utils {
    public static final int REQUEST_MEDIA_PROJECTION = 1;
    private static MediaProjectionManager sMediaProjectionManager;

    /**
     * 申请录屏权限
     */
    public static void createPermission(Activity activity) {
        if (sMediaProjectionManager == null) {
            sMediaProjectionManager = (MediaProjectionManager) activity.getApplication().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        }

        Intent intent = sMediaProjectionManager.createScreenCaptureIntent();
        activity.startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
    }
}
