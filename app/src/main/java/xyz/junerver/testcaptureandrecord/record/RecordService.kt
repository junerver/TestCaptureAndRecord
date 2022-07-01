package xyz.junerver.testcaptureandrecord.record

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import xyz.junerver.testcaptureandrecord.*
import kotlin.experimental.and

class RecordService : LifecycleService() {

    //媒体投影
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    //录屏使用的由MediaCodec创建的surface，用于创建虚拟屏幕
    private var surface: Surface? = null

    //视频编码器
    private lateinit var mMediaCodecEncoder: MediaCodec


    @Volatile
    private var isRun = true //用于控制 是否录制，这个无关紧要

    //当录制服务收到来自编码器的数据时调用回调函数
    fun interface OnReceiveH264DataCallback {
        fun onReceiveH264Data(data: ByteArray)
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        GlobalConfig.intent?.let {
            (this.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                AppCompatActivity.RESULT_OK,
                it
            ).apply {
                mediaProjection = this
            }
        } ?: run {
            LogUtils.e("RecordService intent is null")
            return
        }

        mMediaCodecEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val codecName = mMediaCodecEncoder.codecInfo.name
        val mediaFormat = GlobalConfig.getMediaFormat()
        mMediaCodecEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mMediaCodecEncoder.createInputSurface()
        LogUtils.d("使用编码器类型：$codecName")
        createVirtualDisplay()
        mMediaCodecEncoder.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startRecord()
            }
            ACTION_STOP -> {
                stopRecord()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stopRecord() {
        isRun = false
    }

    private var h264SpsPpsData: ByteArray? = null

    private fun startRecord() {
        isRun = true
        //方法1：正常发送I帧 P帧，但是每隔1秒强制请求一次关键帧 I帧，
//        setInterval(1000,1000) {
//            if (isRun) {
//                val params = Bundle()
//                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
//                mMediaCodecEncoder.setParameters(params)
//            }
//        }
        GlobalThreadPools.instance?.execute {
            val timeoutUs: Long = -1
            val mBufferInfo = MediaCodec.BufferInfo()
            while (isRun) {
                val outputBufferIndex = mMediaCodecEncoder.dequeueOutputBuffer(
                    mBufferInfo,
                    timeoutUs
                )
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    LogUtils.d("输出格式变化")
                    val format: MediaFormat = mMediaCodecEncoder.outputFormat
                    var byteBuffer = format.getByteBuffer("csd-0")
                    val sps = ByteArray(byteBuffer?.capacity()!!)
                    byteBuffer.get(sps)
                    byteBuffer = format.getByteBuffer("csd-1")
                    val pps = ByteArray(byteBuffer?.capacity()!!)
                    byteBuffer?.get(pps)
                    //拼接sps和pps
                    val spsPps = ByteArray(sps.size + pps.size)
                    System.arraycopy(sps, 0, spsPps, 0, sps.size)
                    System.arraycopy(pps, 0, spsPps, sps.size, pps.size)
                    h264SpsPpsData = spsPps
                }
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mMediaCodecEncoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.position(mBufferInfo.offset)
                    outputBuffer?.limit(mBufferInfo.offset + mBufferInfo.size)
                    val chunk = ByteArray(mBufferInfo.size)
                    outputBuffer?.get(chunk)
                    mMediaCodecEncoder.releaseOutputBuffer(outputBufferIndex, false)
//                    LogUtils.d("视频数据：${chunk.size}")
                    //播放视频数据
                    if (chunk.isNotEmpty()) {
                        //方法2：融合sps和pps，配合format中的每隔1秒请求一次关键帧 I帧
                        if ((chunk[4] and 0x1f).toInt() == 5) {
                            LogUtils.d("关键帧数据处理")
                            lifecycleScope.launch {
                                h264SpsPpsData?.let { h264DataFlow.emit(it) }
                            }
                        }

                        //flow 与 回调各给一份 爱咋用咋用
                        lifecycleScope.launch {
                            h264DataFlow.emit(chunk)
                        }
                        sOnReceiveH264DataCallback?.onReceiveH264Data(chunk)
                    }
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.d("视频结束")
                    break
                }
            }
        }
    }

    //创建虚拟屏幕
    private fun createVirtualDisplay() {
        /**
         * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR：当没有内容显示时，允许将内容镜像到专用显示器上。
         * VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY：仅显示此屏幕的内容，不镜像显示其他屏幕的内容。
         * VIRTUAL_DISPLAY_FLAG_PRESENTATION：创建演示文稿的屏幕。
         * VIRTUAL_DISPLAY_FLAG_PUBLIC：创建公开的屏幕。
         * VIRTUAL_DISPLAY_FLAG_SECURE：创建一个安全的屏幕
         */
        try {
            val dpi = resources.displayMetrics.densityDpi
            LogUtils.d("dpi:$dpi")
            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "MainScreen", 720, 1280, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null
            )
            LogUtils.d("创建成功: ${virtualDisplay?.display?.width} x ${virtualDisplay?.display?.height}")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "virtualDisplay 录屏出错！", Toast.LENGTH_SHORT).show()
        }
    }


    companion object {
        private const val ACTION_START = "xyz.junerver.testcaptureandrecord.record.ACTION_START"
        private const val ACTION_STOP = "xyz.junerver.testcaptureandrecord.record.ACTION_STOP"
        var sIntent: Intent? = null
        var sRequestCode = Activity.RESULT_OK
        var sOnReceiveH264DataCallback: OnReceiveH264DataCallback? = null

        //流数据的Flow
        val h264DataFlow = MutableSharedFlow<ByteArray>()

        //发送开启录屏服务
        fun start(context: android.content.Context) {
            val intent = Intent(context, RecordService::class.java)
            intent.action = ACTION_START
            context.startService(intent)
        }

        fun stop(context: android.content.Context) {
            val intent = Intent(context, RecordService::class.java)
            intent.action = ACTION_STOP
            context.startService(intent)
        }

        //初始化必要信息，必须调用
        fun init(
            requestCode: Int,
            intent: Intent,
            onReceiveH264DataCallback: OnReceiveH264DataCallback? = null
        ) {
            this.sRequestCode = requestCode
            this.sIntent = intent
            this.sOnReceiveH264DataCallback = onReceiveH264DataCallback
        }

    }
}