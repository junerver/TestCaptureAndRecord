package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Surface
import android.view.SurfaceView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException


class RecordScreenActivity : AppCompatActivity() {

    lateinit var mSvPreview: SurfaceView
    lateinit var mBtnStartRecord:Button
    lateinit var mBtnStopRecord:Button

    //媒体投影
    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var surface: Surface? = null
    private var mMediaCodec: MediaCodec? = null

    @Volatile
    private var isRun = true //用于控制 是否录制，这个无关紧要

    lateinit var mContext:Context

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_screen)
        mContext = this
        mSvPreview = findViewById(R.id.sv_preview)
        mBtnStartRecord = findViewById(R.id.btn_start_record)
        mBtnStopRecord = findViewById(R.id.btn_stop_record)

        mBtnStartRecord.setOnClickListener {
            startRecord()
        }
        mBtnStopRecord.setOnClickListener {
            stopRecord()
        }
    }

    private fun startRecord(){
        (mContext.getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(Activity.RESULT_OK,
            GlobalConfig.intent!!
        ).apply {
            mediaProjection = this
        }

        mMediaCodec = MediaCodec.createEncoderByType("video/avc")
        val codecName = mMediaCodec?.codecInfo?.name
        val mediaFormat = MediaFormat.createVideoFormat("video/avc", 720, 1280)
        //比特率
        mediaFormat.setInteger(
            MediaFormat.KEY_BIT_RATE,
            409600
        )
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
        mMediaCodec?.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        surface = mMediaCodec?.createInputSurface()
        LogUtils.d("使用编码器类型：$codecName")
        createVirtualDisplay()
        mMediaCodec?.start()
        GlobalThreadPools.instance?.execute {
            var timeoutUs:Long = -1
            val mBufferInfo = MediaCodec.BufferInfo()
            while (isRun) {
                val outputBufferIndex = mMediaCodec?.dequeueOutputBuffer(
                    mBufferInfo,
                    timeoutUs
                ) ?: -1
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mMediaCodec?.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.position(mBufferInfo.offset)
                    outputBuffer?.limit(mBufferInfo.offset + mBufferInfo.size)
                    val chunk = ByteArray(mBufferInfo.size)
                    outputBuffer?.get(chunk)
                    mMediaCodec?.releaseOutputBuffer(outputBufferIndex, false)
                    LogUtils.d("视频数据：${chunk.size}")
                }
                if (mBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    LogUtils.d("视频结束")
                    break
                }
            }

        }
    }

    fun stopRecord() {
        isRun = false
//        try {
//            mediaRecorder!!.stop()
//            mediaRecorder!!.reset()
//            virtualDisplay!!.release()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            Toast.makeText(this, "录屏出错,保存失败", Toast.LENGTH_SHORT).show()
//            return false
//        }
//        Toast.makeText(this, "录屏完成，已保存。", Toast.LENGTH_SHORT).show()
//        return true
    }

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

    private fun initRecorder() {
        mediaRecorder = MediaRecorder()
        //设置声音来源
        mediaRecorder?.setAudioSource(MediaRecorder.AudioSource.MIC)
        //设置视频来源
        mediaRecorder?.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        //设置视频格式
        mediaRecorder?.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)

        //设置视频大小
        mediaRecorder?.setVideoSize(720, 1280)
        //设置视频编码
        mediaRecorder?.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        //设置声音编码
        mediaRecorder?.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
        //视频码率
        mediaRecorder?.setVideoEncodingBitRate(2 * 1920 * 1080)
        mediaRecorder?.setVideoFrameRate(18)
        try {
            mediaRecorder?.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "prepare出错，录屏失败！", Toast.LENGTH_SHORT).show()
        }
    }
}