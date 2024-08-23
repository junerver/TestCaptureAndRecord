package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import xyz.junerver.testcaptureandrecord.capture.CaptureScreen
import xyz.junerver.testcaptureandrecord.record.RecordWithServerActivity
import xyz.junerver.testcaptureandrecord.utils.LogUtils
import xyz.junerver.testcaptureandrecord.utils.WebSocketConfig
import xyz.junerver.testcaptureandrecord.utils.WebSocketHelper

class MainActivity : AppCompatActivity() {
    lateinit var mIvPreview: ImageView
    lateinit var mBtnStartCapture: Button
    lateinit var mBtnStopCapture: Button
    lateinit var captureScreen: CaptureScreen

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mIvPreview = findViewById(R.id.iv_preview)
        mBtnStartCapture = findViewById(R.id.btn_start_capture)
        mBtnStopCapture = findViewById(R.id.btn_stop_capture)

        mBtnStartCapture.setOnClickListener {
            captureScreen.startVirtual {
                LogUtils.d("收到截图回调 - $it")
                mIvPreview.setImageBitmap(it)
            }
        }
        mBtnStopCapture.setOnClickListener {
            captureScreen.stop()
        }

        findViewById<Button>(R.id.btn_jump_to_record_screen).setOnClickListener {
            //录屏与显示都在一个activity中
//            startActivity(Intent(this, RecordScreenActivity::class.java))
            //录屏抽离到services中
            startActivity(Intent(this, RecordWithServerActivity::class.java))
        }

        //申请录屏权限
        Utils.createPermission(this)

        // ws 连接
        runCatching { WebSocketHelper.connect("ws:// ${WebSocketConfig.LOCAL_WS_PORT}") }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        //授权成功，保存intent，在后续需要使用该intent申请相关屏幕录制的对象
        if (requestCode == Utils.REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK) {
                GlobalConfig.intent = data!!
                captureScreen = CaptureScreen.getInstance(this, GlobalConfig.intent)
            }
        }
    }
}