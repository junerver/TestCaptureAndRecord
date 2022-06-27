package xyz.junerver.testcaptureandrecord.capture;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.nio.ByteBuffer;

import xyz.junerver.testcaptureandrecord.GlobalThreadPools;
import xyz.junerver.testcaptureandrecord.LogUtils;
import xyz.junerver.testcaptureandrecord.OnBitmapAvailableListener;

/**
 * 截图器
 * by 王喆   at 2017/7/6.
 */
public class CaptureScreen {
    private final static String TAG = "CaptureScreen";
    private final static int NORMAL_STATE = 7;
    private final static int REST_STATE = 8;

    private static CaptureScreen sInstance;
    private ImageReader mImageReader;
    private Context mContext;
    private WindowManager mWindowManager;
    private int mFramerate = 15;
    private int mWidth = 1280;
    private int mHeight = 720;
    private int screenDensity;
    private int mRequestCode = 999;
    private Intent mIntent;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private MediaProjectionManager mMediaProjectionManager;
    private boolean isRunning;

    private int mCacheWidth;
    private boolean isCache;
    private volatile Bitmap mBitmapCache;
    private ByteBuffer mByteBuffer;
    /**
     * 息屏Bitmap
     */
    private Bitmap mBlackBitmap;
    @SuppressWarnings("unused")
    private int mScreenState = NORMAL_STATE;
    private OnBitmapAvailableListener mListener;

    /**
     * 截图器
     */
    public static CaptureScreen getInstance(Context context,Intent intent) {
        if (sInstance == null) {
            synchronized (CaptureScreen.class) {
                if (sInstance == null) {
                    CaptureScreen.sInstance = new CaptureScreen(context,intent);

                }
            }
        }

        return sInstance;
    }

    private CaptureScreen(Context context,Intent intent) {
        mContext = context;
        mIntent = intent;

        DisplayMetrics displayMetrics = new DisplayMetrics();
        mMediaProjectionManager = (MediaProjectionManager) mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mWindowManager = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        mWindowManager.getDefaultDisplay().getMetrics(displayMetrics);
        screenDensity = displayMetrics.densityDpi;

    }

    public void setHeight(int newHeight) {
        mHeight = newHeight;
        Point point = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(point);
        float width = point.x;
        float height = point.y;
        if (height > width) {
            float x = width;
            width = height;
            height = x;
        }

        float scale = width / height;
        mWidth = (int) (mHeight * scale);
    }

    /**
     * 帧率设置 默认 15
     *
     * @param framerate
     */
    public void setFramerate(int framerate) {
        mFramerate = framerate;
        LogUtils.d("当前帧率" + mFramerate);
    }

    /**
     * @return 获取高
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * @return 获取宽
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * 开始截图
     *
     * @param listener 数据回调接口
     */
    public synchronized void startVirtual(OnBitmapAvailableListener listener) {
        LogUtils.e("录屏配置虚拟Surface");
        if (mMediaProjection == null) {
            //在高版本的系统上，Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            //必须将截屏器放置在一个前台服务中，否则会报错，请求函数中的intent 是onActivityResult中的intent
            mMediaProjection = mMediaProjectionManager.getMediaProjection(Activity.RESULT_OK, mIntent);
            LogUtils.e("mMediaProjection创建成功？"+mMediaProjection);
        } else {
            LogUtils.e("mMediaProjection创建失败:\n" +
                    "mRequestCode的值：" + mRequestCode + "\n" +
                    "mIntent是否等于空：" + (mIntent == null));
        }

        if (mImageReader == null) {
            mImageReader = ImageReader.newInstance(mWidth, mHeight, 0x1, 3);
            LogUtils.e("mImageReader创建成功");
        } else {
            LogUtils.e("mImageReader创建失败:\nmWidth的值：" + mWidth + "\n" + "mHeight的值：" + mHeight);
        }

        if (mVirtualDisplay == null && mMediaProjection != null && mImageReader != null && mWidth > 0 && mHeight > 0) {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay("push_screen", mWidth, mHeight, screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader
                            .getSurface(), null, null);
        } else {
            LogUtils.e("mVirtualDisplay：" + (mVirtualDisplay == null) + "\n"
                    + "mMediaProjection：" + (mMediaProjection != null) + "\n"
                    + "mImageReader：" + (mImageReader != null) + "\n"
                    + "mWidth：" + (mWidth > 0) + "\n"
                    + "mHeight：" + (mHeight > 0));
        }

        isRunning = true;
        mListener = listener;
        startCaptureScreen();
    }

    /**
     * 开始截屏
     */
    private void startCaptureScreen() {
        LogUtils.d("开始截屏");
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (isRunning) {
                    try {
                        if (mScreenState == NORMAL_STATE) {
                            captureScreen();
                        } else {
                            sendBlackBitmap();
                        }

                        Thread.sleep(1000 / mFramerate);
                    } catch (Exception e) {
                        // maxImages (3) has already been acquired, call #close before acquiring more.
                        //停止截屏，释放资源，延时后立即重启截屏器
                        stop();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                        startVirtual(mListener);
                        break;
                    }
                }
            }
        };

        GlobalThreadPools.Companion.getInstance().execute(runnable);
    }

    /**
     * 停止截屏
     */
    public synchronized void stop() {
        isRunning = false;
        release();
    }

    private synchronized void release() {
        LogUtils.d("CaptureScreen release() 释放资源、释放虚拟Surface");
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }

        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        clearCacheBitmap();
    }

    /**
     * 处理图片数据 发送给接口
     */
    private void captureScreen() {
        if (mImageReader == null) {
            return;
        }

        Image image = mImageReader.acquireLatestImage();

        if (image == null) {
            if (mCacheWidth != 0) {
                flushByteBuffer(null);
            }
            return;
        }

        final Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();
        final ByteBuffer buffer = planes[0].getBuffer();

        if (mCacheWidth == 0) {
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            mCacheWidth = width + rowPadding / pixelStride;
        }
        flushByteBuffer(buffer);
        image.close();
    }

    private void flushByteBuffer(ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            byteBuffer = mByteBuffer;
        }

        if (byteBuffer == null) {
            return;
        }

        Bitmap bitmap = Bitmap.createBitmap(mCacheWidth, mHeight, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(byteBuffer);

        if (isCache) {
            mBitmapCache = Bitmap.createBitmap(bitmap, 0, 0, mWidth, mHeight);
            isCache = false;
        }

        if (mListener != null) {
            mListener.onAvailable(bitmap);
        }

        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap=null;
        }

        mByteBuffer = byteBuffer;
        mByteBuffer.position(0);
        Log.d(TAG, "captureScreen");
    }

    /**
     * 处理图片数据 发送给接口
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * 息屏数据发送
     */
    private void sendBlackBitmap() {
        if (mBlackBitmap == null || mBlackBitmap.isRecycled()) {
            mBlackBitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888);
            mBlackBitmap.eraseColor(Color.BLACK);
        }

        if (mListener != null) {
            mListener.onAvailable(mBlackBitmap);
        }
    }

    /**
     * 非UI线程调用
     *
     * @return 推屏中的Bitmap
     * @throws InterruptedException 休眠异常
     */
    public Bitmap cacheBitmap() throws InterruptedException {
        clearCacheBitmap();
        if (mBitmapCache != null && !mBitmapCache.isRecycled()) {
            mBitmapCache.recycle();
            mBitmapCache = null;
        }

        isCache = true;
        while (isCache) {
            Thread.sleep(1);
        }

        return mBitmapCache;
    }

    public void clearCacheBitmap() {
        mCacheWidth = 0;
        if (mBitmapCache != null && !mBitmapCache.isRecycled()) {
            mBitmapCache.recycle();
            mBitmapCache = null;
        }
    }
}
