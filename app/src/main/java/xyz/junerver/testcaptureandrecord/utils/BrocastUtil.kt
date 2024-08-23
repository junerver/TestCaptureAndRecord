package xyz.junerver.testcaptureandrecord.utils

import android.media.MediaCodec
import android.media.MediaFormat
import xyz.junerver.testcaptureandrecord.utils.WsUtilsKt.uncompressGzip
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Description:
 * @author Junerver
 * date: 2023/11/28-9:51
 * Email: junerver@gmail.com
 * Version: v1.0
 */
object BrocastUtil {
    @Throws(IOException::class)
    private fun readData(length: Int, inputStream: InputStream): ByteArray {
        var readBytes = 0
        val buffer = ByteArray(length)
        while (readBytes < length) {
            val read = inputStream.read(buffer, readBytes, length - readBytes)
            // 判断是不是读到了数据流的末尾 ，防止出现死循环。
            if (read == -1) {
                throw IOException("mInputStream read -1")
            }
            readBytes += read
        }
        return buffer
    }

    @JvmStatic
    @Throws(IOException::class)
    fun decodeDataByteArray(ba: ByteArray): Pair<Int, ByteArray> {
        return ByteArrayInputStream(ba).let {
//            val header = readData(3, it)
//            val category = header[0] // 广播的指定类别
//            val type = header[1] // 数据类型
//            val dataLenStrBytesLen = header[2]

            val header = readData(2, it)
//            val category = header[0] // 广播的指定类别
            val type = header[0] // 数据类型
            val dataLenStrBytesLen = header[1]
            val length = String(readData(dataLenStrBytesLen.toInt(), it)).toInt()
            LogUtils.i(
                "category=category ,type=$type ,dataLenStrBytesLen=$dataLenStrBytesLen ,len=$length"
            )
            val result = readData(length, it)
            type.toInt() to result
        }
    }

    /**
     * 新版解码处理
     */
    @JvmStatic
    fun processH264WithReceiver(
        data: ByteArray,
        receiver: (ByteArray) -> Unit?,
    ) {
        runCatching {
            val (type, result) = decodeDataByteArray(data)
            LogUtils.i( "receiver=$receiver")
            receiver.run {
                when (type) {
                    1 -> {}
                    3 -> {}
                    4 -> receiver(result)
                    5 -> receiver(result.uncompressGzip())
                    9 -> {}
                    else -> {}
                }
            }
        }.onFailure {
            LogUtils.e(it.toString())
        }
    }

    fun isAvcEncodedBlock(data: ByteArray): Boolean {
        if (data.isEmpty()) {
            return false
        }
        val startCodePrefix = byteArrayOf(0x00, 0x00, 0x00, 0x01)
        val startCodeAlt = byteArrayOf(0x00, 0x00, 0x01)
        if (data.size < startCodePrefix.size) {
            return false
        }
        return data.sliceArray(startCodePrefix.indices).contentEquals(startCodePrefix) ||
                data.sliceArray(startCodeAlt.indices).contentEquals(startCodeAlt)
    }
}