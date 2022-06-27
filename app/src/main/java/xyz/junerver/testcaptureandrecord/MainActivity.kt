package xyz.junerver.testcaptureandrecord

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import xyz.junerver.testcaptureandrecord.capture.CaptureScreen
import xyz.junerver.testcaptureandrecord.record.RecordWithServerActivity

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
//            startActivity(Intent(this, RecordScreenActivity::class.java))
            startActivity(Intent(this, RecordWithServerActivity::class.java))
        }


        //绝对不要放到resume
        if (Utils.checkPermission(this)) {
//            有权限请求
            Utils.createPermission(this);
        } else {
            //无权限 去授权
            showDialogTipUserGoToAppSettings(this)
        }
    }


    /**
     * 申请悬浮窗权限
     */
    private fun showDialogTipUserGoToAppSettings(activity: Activity) {
        val runnable = Runnable {
            val builder: AlertDialog.Builder = AlertDialog.Builder(activity)
                .setCancelable(false)
                .setTitle("申请悬浮窗权限")
                .setMessage("请前往应用设置中，允许悬浮窗（在其他应用的上层显示）权限")
                .setPositiveButton("前往", DialogInterface.OnClickListener { dialog, which ->
                    Utils.goToAppSetting(activity)
                    if (Utils.isHuaWeiHD(activity)) {
                        activity.finish()
                    }
                })
            if (!activity.isFinishing) {
                builder.show()
            }
        }
        //handler 延迟执行，避免在onCreate中执行
        Handler(Looper.getMainLooper()).postDelayed(runnable, 300)
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