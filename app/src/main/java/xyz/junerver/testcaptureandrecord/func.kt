package xyz.junerver.testcaptureandrecord

import java.util.*

/**
 * Description:
 * @author Junerver
 * date: 2022/6/27-12:55
 * Email: junerver@gmail.com
 * Version: v1.0
 */

fun setInterval(interval: Long, block: () -> Unit) {
    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            block()
        }
    }, 0, interval)
}
