package xyz.junerver.testcaptureandrecord.record

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xyz.junerver.testcaptureandrecord.GlobalConfig
import xyz.junerver.testcaptureandrecord.MIME_TYPE
import xyz.junerver.testcaptureandrecord.R
import xyz.junerver.testcaptureandrecord.Utils
import xyz.junerver.testcaptureandrecord.utils.BrocastUtil
import xyz.junerver.testcaptureandrecord.utils.LogUtils
import xyz.junerver.testcaptureandrecord.utils.WebSocketHelper
import kotlin.experimental.and

/**
 * Description: 抽离了录屏服务到server中
 * @author Junerver
 * @date: 2022/6/27-16:59
 * @Email: junerver@gmail.com
 * @Version: v1.0
 */
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
                val err = "lalalall".toByteArray()
                decodeVideo(err)
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


        RecordService.init(GlobalConfig.resultCode, GlobalConfig.intent!!)
        lifecycleScope.launch {
            launch(Dispatchers.IO) {
                RecordService.sH264DataFlow.collect {
//                找到开始码之后，使用开始码之后的第一个字节的低 5 位判断
//                type &0x1f==0x7表示这个nalu是sps， 序列参数集 SPS----7
//                type &0x1f==0x8表示是pps。 图像参数集 PPS----8：
//                5 表示IDR图像中的片 即 I帧
//                P帧 ----1：
//                https://zhuanlan.zhihu.com/p/281176576

                    val frame = when ((it[4] and 0x1f).toInt()) {
                        7 -> "SPS"
                        8 -> "PPS"
                        5 -> "I"
                        1 -> "P"
                        else -> "other"
                    }
                    LogUtils.d("收到i帧信息：$frame")
                    if (mOutputSurface.isValid) {
                        decodeVideo(it)
                    }
                }
            }
        }

        mBtnStartRecord.setOnClickListener {
            RecordService.start(this)
        }
        mBtnStopRecord.setOnClickListener {
            RecordService.stop(this)
        }
        WebSocketHelper.addObserver(object : WebSocketHelper.Observer {
            override fun onReceive(data: String) {

            }

            override fun onReceive(data: ByteArray) {
                BrocastUtil.processH264WithReceiver(data) {
                    decodeVideo(it)
                }
            }

        })
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
        val mediaFormat1 = Utils.getMediaFormat()
        val mediaFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
        )
        mMediaCodecDecoder.configure(mediaFormat, mOutputSurface, null, 0)
        mMediaCodecDecoder.start()
        LogUtils.d("解码器格式：\n$mediaFormat")
    }

    //解码视频数据
    private fun decodeVideo(chunk: ByteArray) {
        LogUtils.d(
            "@解码视频数据 ${chunk.size} " +
                    "\nisAvcEncodedBlock:${BrocastUtil.isAvcEncodedBlock(chunk)}"
        )
        //出队输入缓冲区索引
        val inputBufferIndex = mMediaCodecDecoder.dequeueInputBuffer(100_000)
        LogUtils.d("@解码视频数据 inputBufferIndex $inputBufferIndex")
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