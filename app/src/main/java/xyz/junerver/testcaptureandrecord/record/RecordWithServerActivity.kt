package xyz.junerver.testcaptureandrecord.record

import android.media.MediaCodec
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import xyz.junerver.testcaptureandrecord.GlobalConfig
import xyz.junerver.testcaptureandrecord.LogUtils
import xyz.junerver.testcaptureandrecord.MIME_TYPE
import xyz.junerver.testcaptureandrecord.R
import kotlin.experimental.and

class RecordWithServerActivity : AppCompatActivity() {

    private lateinit var mSvPreview: SurfaceView
    //输出录屏使用的Surface
    lateinit var mOutputSurface: Surface
    private lateinit var holder: SurfaceHolder
    private lateinit var mBtnStartRecord: Button
    private lateinit var mBtnStopRecord: Button

    //视频解码器
    private lateinit var mMediaCodecDecoder: MediaCodec
    //视频解码器的输入缓冲区
    private val mDecoderOutputBufferInfo = MediaCodec.BufferInfo()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_screen)

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


        RecordService.init(GlobalConfig.requestCode,GlobalConfig.intent!!)
        lifecycleScope.launch {
            RecordService.h264DataFlow.collect{
                val i: Int = (it[4] and 0x1f).toInt()
                //7 代表i帧
                if (i == 7) {
                    LogUtils.d("收到i帧信息：$i")
                    hasI = true
                }
                if (mOutputSurface.isValid && hasI) {
                    decodeVideo(it)
                }
            }
        }

        mBtnStartRecord.setOnClickListener {
            RecordService.start(this)
        }
        mBtnStopRecord.setOnClickListener {
            RecordService.stop(this)
        }
    }

    var hasI = false

    override fun onResume() {
        super.onResume()
        //没有i帧会直接黑掉
        hasI = false
    }

    //初始化解码
    private fun initMediaDecoder() {
        LogUtils.d("initDecoder")
        mMediaCodecDecoder = MediaCodec.createDecoderByType(MIME_TYPE)
        val mediaFormat = GlobalConfig.getMediaFormat()
        mMediaCodecDecoder.configure(mediaFormat, mOutputSurface, null, 0)
        mMediaCodecDecoder.start()
    }

    //解码视频数据
    private fun decodeVideo(chunk: ByteArray) {
//        LogUtils.d("@解码视频数据 ${chunk.size}")
        //出队输入缓冲区索引
        val inputBufferIndex = mMediaCodecDecoder.dequeueInputBuffer(100_000)
//        LogUtils.d("@解码视频数据 inputBufferIndex $inputBufferIndex")
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
//                LogUtils.d("surface 写入数据： ${mOutputSurface.isValid}")
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
}