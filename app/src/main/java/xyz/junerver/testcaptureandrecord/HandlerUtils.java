package xyz.junerver.testcaptureandrecord;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.SparseArray;


public class HandlerUtils {
    private static HandlerUtils sInstance;
    private static String TAG = "MyHandler";
    private Handler mMainHandler;
    private Handler mChildHandler;
    private SparseArray<Runnable> mRunnables;

    public static HandlerUtils getInstance() {
        if (sInstance == null) {
            synchronized (HandlerUtils.class) {
                if (sInstance == null) {
                    sInstance = new HandlerUtils();
                }
            }
        }
        return sInstance;
    }

    private HandlerUtils() {
        mMainHandler = new MyHandler(Looper.getMainLooper());
        HandlerThread handlerThread = new HandlerThread("Child_Thread");
        handlerThread.start();
        mChildHandler = new MyHandler(handlerThread.getLooper());
        mRunnables = new SparseArray<>();
    }

    /***
     * @param runnable 运行UI线程
     */
    public void runUIThread(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        mMainHandler.post(runnable);
    }

    /***
     * @param runnable 逻辑线程
     * @param delayed 延时
     */
    public void runDelayed(Runnable runnable, int delayed) {
        if (runnable == null) {
            return;
        }

        mMainHandler.postDelayed(runnable, delayed);
    }

    /***
     * @param what 消息
     * @param delayed 延时
     */
    public void sendMessageDelayed(int what, int delayed) {
        if (mMainHandler == null) {
            return;
        }

        mMainHandler.sendEmptyMessageDelayed(what, delayed);
    }

    /***
     * @param message 消息
     * @param delayed 延时
     */
    public void sendMessageDelayed(Message message, int delayed) {
        if (mMainHandler == null) {
            return;
        }

        mMainHandler.sendMessageDelayed(message, delayed);
    }

    /**
     * 移除逻辑
     *
     * @param what 逻辑指令标识
     */
    public void removeCallback(int what) {
        mMainHandler.removeMessages(what);
    }

    /**
     * 发送先关信息
     * 2xx wangz
     * 5xx wangxh
     * 6xx miss
     * 7xx wangyu
     *
     * @param what 标识
     */
    public void sendMainLooperMsg(int what) {
        mMainHandler.sendEmptyMessage(what);
    }

    public void sendMainLooperMsg(Message message) {
        mMainHandler.sendMessage(message);
    }

    public void sendChildLooperMsg(int what) {
        mChildHandler.sendEmptyMessage(what);
    }

    public void sendChildLooperMsg(Message message) {
        mChildHandler.sendMessage(message);
    }

    /**
     * 处理标识的逻辑线程
     *
     * @param what     标识
     * @param runnable run方法
     */
    public void setWhatRunnable(int what, Runnable runnable) {
        if (mRunnables.get(what) != null) {
            mRunnables.delete(what);
        }

        mRunnables.put(what, runnable);
    }

    /**
     * 重构Handler
     * <p>
     * 我是真鸡贼~~
     */
    private class MyHandler extends Handler {
        MyHandler(Looper looper) {
            super(looper);
        }

        /**
         * 核心：根据标识运行 对应的run方法
         *
         * @param msg
         */
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Runnable runnable = mRunnables.get(msg.what);

            if (runnable == null) {
                return;
            }

            runnable.run();
        }
    }

    public void clearId(int id) {
        mRunnables.delete(id);
    }

    public void clear() {
        if (mRunnables != null) {
            mRunnables.clear();
            mRunnables = null;
        }

        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
            mMainHandler = null;
        }

        if (mChildHandler != null) {
            mChildHandler.removeCallbacksAndMessages(null);
            mChildHandler = null;
        }

        if (sInstance != null) {
            sInstance = null;
        }
    }
}
