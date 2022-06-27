package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.content.Intent

/**
 * Description:
 * @author Junerver
 * date: 2022/6/20-11:10
 * Email: junerver@gmail.com
 * Version: v1.0
 */
object GlobalConfig {
    //用于请求MediaProjection实例的关键参数
    var intent:Intent? = null
    val requestCode = Activity.RESULT_OK
}