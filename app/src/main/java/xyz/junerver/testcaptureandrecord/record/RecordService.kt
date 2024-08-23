package xyz.junerver.testcaptureandrecord.record

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.view.Surface
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import xyz.junerver.testcaptureandrecord.*
import xyz.junerver.testcaptureandrecord.utils.LogUtils
import kotlin.experimental.and

class RecordService : LifecycleService() {


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
        //实例化编码器
        mMediaCodecEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val codecName = mMediaCodecEncoder.codecInfo.name
        val mediaFormat = Utils.getMediaFormat()
        mMediaCodecEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mMediaCodecEncoder.createInputSurface()
        LogUtils.d("使用编码器类型：$codecName")
        mMediaCodecEncoder.start()

        sIntent?.let {
            (this.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
                AppCompatActivity.RESULT_OK,
                it
            ).apply {
                //使用MediaProjection创建VirtualDisplay
                val dpi = resources.displayMetrics.densityDpi
                LogUtils.d("dpi:$dpi")
                /**
                 * VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR：当没有内容显示时，允许将内容镜像到专用显示器上。
                 * VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY：仅显示此屏幕的内容，不镜像显示其他屏幕的内容。
                 * VIRTUAL_DISPLAY_FLAG_PRESENTATION：创建演示文稿的屏幕。
                 * VIRTUAL_DISPLAY_FLAG_PUBLIC：创建公开的屏幕。
                 * VIRTUAL_DISPLAY_FLAG_SECURE：创建一个安全的屏幕
                 */
                val virtualDisplay = this.createVirtualDisplay(
                    "MainScreen", 720, 1280, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, surface, null, null
                )
                LogUtils.d("创建成功: ${virtualDisplay?.display?.width} x ${virtualDisplay?.display?.height}")
            }
        } ?: run {
            LogUtils.e("RecordService intent is null")
            return
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecord()
            ACTION_STOP -> stopRecord()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun stopRecord() {
        isRun = false
    }

    private var h264SpsPpsData: ByteArray? = null

    private fun startRecord() {
        isRun = true
        //防断流黑屏方法1：正常发送I帧 P帧，但是每隔1秒强制请求一次关键帧 I帧，
//        setInterval(1000,1000) {
//            if (isRun) {
//                val params = Bundle()
//                params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
//                mMediaCodecEncoder.setParameters(params)
//            }
//        }
        GlobalThreadPools.instance?.execute {
            val mBufferInfo = MediaCodec.BufferInfo()
            while (isRun) {
                //输出缓冲区出列，返回缓冲的索引
                val outputBufferIndex = mMediaCodecEncoder.dequeueOutputBuffer(
                    mBufferInfo,
                    -1 //超时时间，负数表示无限等待
                )
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    LogUtils.d("输出格式变化")
                    val format: MediaFormat = mMediaCodecEncoder.outputFormat
                    var byteBuffer = format.getByteBuffer("csd-0")
                    //根据缓冲区的容量创建一个字节数组，用于存储视频编码器的sps数据
                    val sps = ByteArray(byteBuffer?.capacity()!!)
                    byteBuffer.get(sps)
                    byteBuffer = format.getByteBuffer("csd-1")
                    //根据缓冲区的容量创建一个字节数组，用于存储视频编码器的pps数据
                    val pps = ByteArray(byteBuffer?.capacity()!!)
                    byteBuffer?.get(pps)
                    //拼接sps和pps
                    val spsPps = ByteArray(sps.size + pps.size)
                    System.arraycopy(sps, 0, spsPps, 0, sps.size)
                    System.arraycopy(pps, 0, spsPps, sps.size, pps.size)
                    h264SpsPpsData = spsPps
                }
                //索引为正数，表示缓冲区存在，可以获取缓冲区数据
                if (outputBufferIndex >= 0) {
                    //传入索引值，获取缓冲区对象
                    val outputBuffer = mMediaCodecEncoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.apply {
                        //确定该帧的起止位置
                        position(mBufferInfo.offset)
                        limit(mBufferInfo.offset + mBufferInfo.size)
                        //根据该帧的大小创建字节数组，并从缓冲区获取数据
                        val chunk = ByteArray(mBufferInfo.size)
                        get(chunk)
                        //获取帧画面数据完毕，调用编码器函数释放缓冲区，因为我们是录制屏幕，不需要渲染到surface，所以参数2传递false
                        mMediaCodecEncoder.releaseOutputBuffer(outputBufferIndex, false)
                        LogUtils.d("拿到录屏流数据：${chunk.size}")
                        //将流数据发送
                        if (chunk.isNotEmpty()) {
                            //防断流黑屏方法2：融合sps和pps，配合format中的每隔1秒请求一次关键帧 I帧
                            if ((chunk[4] and 0x1f).toInt() == 5) {
                                LogUtils.d("关键帧数据处理")
                                lifecycleScope.launch {
                                    //发送sps和pps数据，这样可以避免掉线重连时因为没有sps和pps数据而导致黑屏
                                    h264SpsPpsData?.let { data ->
                                        sH264DataFlow.emit(data)
                                        sOnReceiveH264DataCallback?.onReceiveH264Data(data)
                                    }
                                }
                            }
                            //flow 与 回调各给一份 用kotlin的就用flow拿数据，用java就从回调拿数据
                            lifecycleScope.launch {
                                sH264DataFlow.emit(chunk)
                            }
                            sOnReceiveH264DataCallback?.onReceiveH264Data(chunk)
                        }
                    }
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.d("视频结束")
                    break
                }
            }
        }
    }

    companion object {
        private const val ACTION_START = "xyz.junerver.testcaptureandrecord.record.ACTION_START"
        private const val ACTION_STOP = "xyz.junerver.testcaptureandrecord.record.ACTION_STOP"
        var sIntent: Intent? = null
        var sResultCode = Activity.RESULT_OK
        var sOnReceiveH264DataCallback: OnReceiveH264DataCallback? = null

        //流数据的Flow
        val sH264DataFlow = MutableSharedFlow<ByteArray>()

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
            resultCode: Int,
            intent: Intent,
            onReceiveH264DataCallback: OnReceiveH264DataCallback? = null
        ) {
            this.sResultCode = resultCode
            this.sIntent = intent
            this.sOnReceiveH264DataCallback = onReceiveH264DataCallback
        }

    }
}