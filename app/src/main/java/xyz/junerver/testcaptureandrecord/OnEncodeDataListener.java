package xyz.junerver.testcaptureandrecord;

import android.graphics.Bitmap;

/**
 * 编码数据回调
 * by 王喆   at 2017/7/6.
 */
public interface OnEncodeDataListener {
    void onEncode(byte[] data);

    void onBitmapResult(Bitmap bitmap);

    void onClose();
}