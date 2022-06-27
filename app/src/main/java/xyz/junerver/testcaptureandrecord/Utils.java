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
    public static final int REQUEST_WINDOW = 2;
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

    /**
     * 悬浮窗权限判断
     *
     * @param context 上下文
     * @return [ true, 有权限 ][ false, 无权限 ]
     */
    public static boolean checkPermission(Context context) {
        boolean hasPermission = false;
        if (Build.VERSION.SDK_INT < 19) {
            hasPermission = true;
        } else if (Build.VERSION.SDK_INT >= 19 && Build.VERSION.SDK_INT < 23) {
            hasPermission = opPermissionCheck(context, 24);
        } else if (Build.VERSION.SDK_INT >= 23) {
            hasPermission = highVersionPermissionCheck(context);
        }
        return hasPermission;
    }

    /**
     * [19-23]之间版本通过[AppOpsManager]的权限判断
     *
     * @param context 上下文
     * @param op
     * @return [ true, 有权限 ][ false, 无权限 ]
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private static boolean opPermissionCheck(Context context, int op) {
        try {
            AppOpsManager manager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            Class clazz = AppOpsManager.class;
            Method method = clazz.getDeclaredMethod("checkOp", int.class, int.class, String.class);
            return AppOpsManager.MODE_ALLOWED == (int) method.invoke(manager, op, Binder.getCallingUid(), context.getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Android 6.0 版本及之后的权限判断
     *
     * @param context 上下文
     * @return [ true, 有权限 ][ false, 无权限 ]
     */
    private static boolean highVersionPermissionCheck(Context context) {
        try {
            Class clazz = Settings.class;
            Method canDrawOverlays = clazz.getDeclaredMethod("canDrawOverlays", Context.class);
            return (Boolean) canDrawOverlays.invoke(null, context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    /**
     * 跳转到当前应用的设置界面
     */
    public static void goToAppSetting(Activity activity) {
        Intent intent = new Intent();

        if (isHuaWeiHD(activity)) {
            String pkg = "com.huawei.systemmanager";
            String name = "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity";
            ComponentName componentName = new ComponentName(pkg, name);
            intent.setComponent(componentName);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                intent.setAction(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            }
        }

        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivityForResult(intent, REQUEST_WINDOW);
    }

    public static boolean isHuaWeiHD(Activity activity) {
        try {
            String custVer = Settings.System.getString(activity.getContentResolver(), "industry_version_name");
            ClassLoader loader = ClassLoader.getSystemClassLoader();
            Class systemPropertiesCls = loader.loadClass("android.os.SystemProperties");
            Method getMethod = systemPropertiesCls.getMethod("get", String.class);
            String romStr = (String) getMethod.invoke(systemPropertiesCls, "ro.build.display.id");
            return (romStr != null && romStr.contains("C824")) ||
                    (custVer != null && custVer.equals("dongshilixiang") ||
                            (Build.MANUFACTURER.contains("HUAWEI") &&
                                    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q));//华为Android10跳转方式同其他品牌
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
