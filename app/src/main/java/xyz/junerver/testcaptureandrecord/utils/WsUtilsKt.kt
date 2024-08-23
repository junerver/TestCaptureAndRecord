package xyz.junerver.testcaptureandrecord.utils

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * @Author Junerver
 * @Date 2021/8/12-11:06
 * @Email junerver@gmail.com
 * @Version v1.0
 * @Description 使用单例模式 方便以java的方式调用
 */
object WsUtilsKt {

    //bf转ba
    @JvmStatic
    fun bytebuffer2ByteArray(buffer: ByteBuffer): ByteArray =
        ByteArray(buffer.capacity()).apply { buffer.get(this) }

    //bf转ba
    fun ByteBuffer.toByteArray(): ByteArray = bytebuffer2ByteArray(this)

    //ba转base64
    fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    @JvmStatic
    fun byteArray2Base64(ba: ByteArray): String = ba.toBase64()


    //gzip压缩字节流
    @JvmStatic
    fun compressByGzip(data: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        GZIPOutputStream(this).apply {
            write(data)
            finish()
        }
    }.toByteArray()

    //gzip压缩字节流
    fun ByteArray.gzipCompress(): ByteArray = compressByGzip(this)

    //gzip解压缩字节流
    @JvmStatic
    fun uncompressByGzip(data: ByteArray): ByteArray =
        GZIPInputStream(data.inputStream()).use { it.readBytes() }

    //gzip解压缩字节流
    fun ByteArray.uncompressGzip(): ByteArray = uncompressByGzip(this)

    /** 把字byte转换为十六进制的表现形式，如ff  */
    fun byteToHex(byte: Byte) = String.format("%02x", byte.toInt() and 0xFF)

    @JvmStatic
    //读取文件的全部字节
    fun readBytes(file: File): ByteArray = file.readBytes()

    @JvmStatic
    fun groupByteArrayByLength(ba: ByteArray, length: Int): List<ByteArray> {
        val list: MutableList<ByteArray> = ArrayList()
        var num = getTotalPiece(ba.size.toLong(), length)
        for (i in 0 until num) {
            list.add(ba.copyOfRange(i * length, if (i == num - 1) ba.size else i * length + length))
        }
        return list
    }

    @JvmStatic
    fun getTotalPiece(total: Long, piece: Int): Int =
        if (total % piece > 0) (total / piece).toInt() + 1 else (total / piece).toInt()


    //计算一个BA列表的总长度
    @JvmStatic
    fun countByteArrayList(l: List<ByteArray>): Long = l.fold(0) { a, i -> a + i.size }


    /**
     * @Description 按照设定的字节长度分组一个字符串
     * @Author Junerver
     * Created at 2021/9/1 08:46
     * @param str 传入的待分组的字符串
     * @param length 分组字符串所用的长度
     * @return 分组后的字符串列表
     */
    @JvmStatic
    fun groupStringByLength(str: String, length: Int): List<String> {
        val list: MutableList<String> = ArrayList()
        val num = getTotalPiece(str.length.toLong(),length)
        for (i in 0 until num) {
            list.add(str.substring(i * length until if (i == num - 1) str.length else i * length + length))
        }
        return list
    }

    //字符串按长度分组
    fun String.groupByLength(length: Int): List<String> = groupStringByLength(this, length)

    //字符串直接转换为Base64
    fun String.toBase64(): String = this.toByteArray().toBase64()

    @JvmStatic
    fun stringToBase64(string: String): String = string.toBase64()

    //base64字符串 转码为BA
    @JvmStatic
    fun String.base64toByteArray(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    //base64字符串直接解码为普通字符串
    fun String.decodeBase64(): String = String(this.base64toByteArray())

    //uuid
    fun getUUID(): String = UUID.randomUUID().toString()

    /**
     * @Description 字节总数转成具体的数据体积
     * @Author Junerver
     * Created at 2018/12/22 09:37
     * @param
     * @return
     */
    @JvmStatic
    fun dataFormat(total: Long): String {
        var result: String
        var speedReal: Int = (total / (1024)).toInt()
        result = if (speedReal < 512) {
            "$speedReal KB"
        } else {
            val mSpeed = speedReal / 1024.0
            (Math.round(mSpeed * 100) / 100.0).toString() + " MB"
        }
        return result
    }
}