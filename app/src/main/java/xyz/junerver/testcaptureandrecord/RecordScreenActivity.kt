package xyz.junerver.testcaptureandrecord

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// H.264编码格式
const val MIME_TYPE = "video/avc"
class RecordScreenActivity : AppCompatActivity() {

    private lateinit var mSvPreview: SurfaceView
    lateinit var mOutputSurface: Surface
    private lateinit var holder: SurfaceHolder
    private lateinit var mBtnStartRecord: Button
    private lateinit var mBtnStopRecord: Button

    //媒体投影
    private var mediaProjection: MediaProjection? = null

    private var virtualDisplay: VirtualDisplay? = null

    //录屏使用的由MediaCodec创建的surface，用于创建虚拟屏幕
    private var surface: Surface? = null

    //视频编码器
    private lateinit var mMediaCodecEncoder: MediaCodec
    //视频解码器
    private lateinit var mMediaCodecDecoder: MediaCodec
    //视频解码器的输入缓冲区
    private val mDecoderOutputBufferInfo = MediaCodec.BufferInfo()




    @Volatile
    private var isRun = true //用于控制 是否录制，这个无关紧要

    private lateinit var mContext: Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_screen)
        mContext = this
        mSvPreview = findViewById(R.id.sv_preview)
        holder = mSvPreview.holder
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                mOutputSurface = holder.surface
                //初始化视频解码器
                initMediaDecoder()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        })
        mBtnStartRecord = findViewById(R.id.btn_start_record)
        mBtnStopRecord = findViewById(R.id.btn_stop_record)

        //初始化视频编码器
        initMediaEncoder()
        mBtnStartRecord.setOnClickListener {
            startRecord()
        }
        mBtnStopRecord.setOnClickListener {
            isRun = false
        }
    }

    //初始化视频编码器
    private fun initMediaEncoder() {
        (mContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(
            RESULT_OK,
            GlobalConfig.intent!!
        ).apply {
            mediaProjection = this
        }

        mMediaCodecEncoder = MediaCodec.createEncoderByType(MIME_TYPE)
        val codecName = mMediaCodecEncoder.codecInfo.name
        val mediaFormat = getMediaFormat()
        mMediaCodecEncoder.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mMediaCodecEncoder.createInputSurface()
        LogUtils.d("使用编码器类型：$codecName")
        createVirtualDisplay()
        mMediaCodecEncoder.start()
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

    //初始化解码
    private fun initMediaDecoder() {
        LogUtils.d("initDecoder")
        mMediaCodecDecoder = MediaCodec.createDecoderByType(MIME_TYPE)
        val mediaFormat = getMediaFormat()
        mMediaCodecDecoder.configure(mediaFormat, mOutputSurface, null, 0)
        mMediaCodecDecoder.start()
    }

    //开始录屏
    private fun startRecord() {
        isRun = true
        GlobalThreadPools.instance?.execute {
            val timeoutUs: Long = -1
            val mBufferInfo = MediaCodec.BufferInfo()
            while (isRun) {
                val outputBufferIndex = mMediaCodecEncoder.dequeueOutputBuffer(
                    mBufferInfo,
                    timeoutUs
                )
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mMediaCodecEncoder.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.position(mBufferInfo.offset)
                    outputBuffer?.limit(mBufferInfo.offset + mBufferInfo.size)
                    val chunk = ByteArray(mBufferInfo.size)
                    outputBuffer?.get(chunk)
                    mMediaCodecEncoder.releaseOutputBuffer(outputBufferIndex, false)
                    LogUtils.d("视频数据：${chunk.size}")
                    //播放视频数据
                    decodeVideo(chunk)
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.d("视频结束")
                    break
                }
            }
        }
    }


    //解码视频数据
    private fun decodeVideo(chunk: ByteArray) {
        LogUtils.d("解码视频数据 ${chunk.size}")
        //出队输入缓冲区索引
        val inputBufferIndex = mMediaCodecDecoder.dequeueInputBuffer(100_000)
        LogUtils.d("解码视频数据 inputBufferIndex $inputBufferIndex")
        //当输入缓冲区有效时,就是索引值>=0
        if (inputBufferIndex >= 0) {
            //从解码器中获取输入缓冲区
            val inputBuffer = mMediaCodecDecoder.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            //往输入缓冲区写入数据,关键点
            inputBuffer?.put(chunk)
            //把数据传递给解码器进行解码
            mMediaCodecDecoder.queueInputBuffer(inputBufferIndex, 0, chunk.size, 0, 0)
            //拿到输出缓冲区的索引
            var outputBufferIndex = mMediaCodecDecoder.dequeueOutputBuffer(
                mDecoderOutputBufferInfo,
                100_000
            )
            //输出缓冲区有效
            while (outputBufferIndex >= 0) {
                LogUtils.d("surface 写入数据： ${mOutputSurface.isValid}")
                if (mOutputSurface.isValid) {
                    //releaseOutputBuffer方法的第二个参数为true，表示解码完成后释放输出缓冲区。
                    mMediaCodecDecoder.releaseOutputBuffer(outputBufferIndex, true)
                }
                outputBufferIndex = mMediaCodecDecoder.dequeueOutputBuffer(
                    mDecoderOutputBufferInfo,
                    0
                )
            }
        }
    }

    //获得视频格式，编码解码都需要使用
    private fun getMediaFormat(): MediaFormat {
        val mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE, 720, 1280)
        //比特率
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 409600)
        //必须设置25左右大小,才能对修改码率生效,其他值无效
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
        //固定码率
        mediaFormat.setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
        )
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        return mediaFormat
    }
}