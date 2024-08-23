package xyz.junerver.testcaptureandrecord.utils

import android.os.Build
import android.util.Base64
import androidx.lifecycle.LifecycleObserver
import org.java_websocket.WebSocketListener
import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import xyz.junerver.testcaptureandrecord.utils.WsUtilsKt.toByteArray
import java.net.URI
import java.nio.ByteBuffer
import java.util.*
import javax.net.ssl.SSLParameters
import kotlin.concurrent.thread

/**
 * @Author Junerver
 * @Date 2021/8/5-08:59
 * @Email junerver@gmail.com
 * @Version v1.0
 * @Description  websocket client的操作类
 */
object WebSocketHelper : LifecycleObserver {
    //ws uri
    private lateinit var uri: URI

    //header
    private lateinit var header: Map<String, String>

    //ws 客户端
    private var client: WebSocketClient? = null

    //接受到501之前不应该随意指令 因为此时pc 不能正常接受
    private var enableWs = false

    /**
     * @Description 连接到ws
     * @Author Junerver
     * Created at 2021/8/5 09:31
     * @param
     * @return
     */
    fun connect(uriStr: String, header: Map<String, String>? = null) {
        uri = URI.create(uriStr)
        if (client != null) {
            client?.close()
            client = null
        }

        client = object : WebSocketClient(uri, Draft_6455(), header) {

            override fun onSetSSLParameters(sslParameters: SSLParameters?) {
                if (Build.VERSION.SDK_INT >= 24) {
                    super.onSetSSLParameters(sslParameters)
                }
                LogUtils.d("为了兼容android 此处override")
            }

            override fun onOpen(handshakedata: ServerHandshake?) {
                //连接成功
                LogUtils.d("连接ws服务成功")
                Timer().schedule(object : TimerTask() {
                    override fun run() {
                        if (isOpen) {
                            client?.sendPing()
                        }
                    }
                }, 30 * 1000, 30 * 1000)
            }

            override fun onMessage(message: String?) {
                //收到的消息是经过base64加密的需要解密操作
                message?.let {
                    if (it.startsWith("$")) {
                        //切片数据处理
                    } else {
                        val decode: ByteArray = Base64.decode(it, Base64.NO_WRAP)
                        val decodeStr = String(decode)
                        LogUtils.d(
                            "收到ws服务器消息\n${
                                if (decodeStr.length > 300) decodeStr.subSequence(
                                    0..300
                                ) else decodeStr
                            }"
                        )
                    }

                }
            }

            override fun onMessage(bytes: ByteBuffer?) {
                LogUtils.d("收到ws服务器消息\n$bytes")
                bytes?.let {
                    val data = it.toByteArray()
                    notifyObserver(data)
                }
            }

            /**
             *  -1 false 客户端无法连接服务器 (ip错误、连接超时、ws服务器关闭)
             *  1000 false 客户单主动断开连接
             *  1006 true 客户端网络变化导致的连接断开或者服务器主动关闭
             */
            override fun onClose(code: Int, reason: String?, remote: Boolean) {
                disable()
                if (remote) {
                    //远程服务关闭

                } else {
                    //client主动断开或本地连接不上

                }
                LogUtils.d("ws服务关闭 ：\nCode：$code  \nReason：$reason\nRemote：$remote")
            }

            override fun onError(ex: Exception?) {
                disable()
                LogUtils.e(ex.toString())
            }

        }
        //设置连接超时时间 默认时间为60秒,设置为5后，发ping后超过7.5秒没有收到pong则认为掉线会出发onClose
        client?.connectionLostTimeout = 60
        if (uriStr.startsWith(WebSocketConfig.PROTOCOL_HEAD_WSS)) {
            //信任全部证书 简单粗暴
            client?.setSocketFactory(SslUtils.getSslSocketFactory().sslSocketFactory)
        }

        try {
            client?.connect()
        } catch (e: Exception) {
            LogUtils.e(e.stackTrace.toString())
            e.printStackTrace()
        }
    }

    /**
     * @Description 强制重连，该方法会断开已经连接的ws，然后重新发起连接
     * @Author Junerver
     * Created at 2021/8/5 10:13
     * @param
     * @return
     */
    fun forceReConnect() {
        try {
            LogUtils.d("调用了重连ws")
            thread {
                try {
                    client?.reconnectBlocking()
                } catch (e: Exception) {
                    LogUtils.d("WebSocket执行重连操作失败, 直接下课=$e")
                }
            }
        } catch (e: Exception) {
            LogUtils.e(e.stackTrace.toString())
        }
    }


    /**
     * @Description 发送数据流
     * @Author Junerver
     * Created at 2021/8/10 13:34
     * @param
     * @return
     */
    fun send(byteArray: ByteArray) {
        if (isConnect()) {
            client?.send(byteArray)
        } else {
            LogUtils.d("尚未连接上ws服务")
        }
    }

    fun send(message: String) {
        //判断是否已经收到501，没有收到不发送，如果指令为4001开头的则允许发送
        when {
            isConnect() && enableWs -> {
                //收到501 已经使能，将消息入队，然后清空队列
                enQueue(message)
                repeat(queue.size) {
                    client?.send(deQueue())
                }
            }

            isConnect() && message.contains(WebSocketConfig.TEACHER_CONNECT_COMMAND) -> {
                //4001开头
                client?.send(message)
            }

            else -> {
                //其他情况 加入队列
                enQueue(message)
            }
        }

    }

    /**
     * @Description 判断是否连接
     * @Author Junerver
     * Created at 2021/8/5 10:13
     * @param
     * @return
     */
    private fun isConnect(): Boolean {
        return client?.isOpen == true
    }

    /**
     * @Description 断开ws连接
     * @Author Junerver
     * Created at 2021/8/5 10:13
     * @param
     * @return
     */
    fun stop() {
        try {
            client?.close()
            client = null
//            removeWebSocketListener() //移除监听
            removerAllObserver() //移除数据观察
            clearQueue() //清空队列
        } catch (e: Exception) {
            LogUtils.e(e.stackTrace.toString())
        }
    }


    //用于使能ws发送 ，只有在收到pc端501后才能发送其他指令
    fun enable() {
        enableWs = true
    }

    fun disable() {
        enableWs = false
    }

    /**
     * @Description 添加监听器，当单个业务页面需要使用时，请添加，注意该业务页面会移除之前添加的其他页面的监听器
     * @Author Junerver
     * Created at 2021/8/6 10:04
     * @param
     * @return
     */
    fun setWebSocketListener(listener: WebSocketListener) {
        removeWebSocketListener()
    }

    /**
     * @Description 移除监听器
     * @Author Junerver
     * Created at 2021/8/6 10:20
     * @param
     * @return
     */
    public fun removeWebSocketListener() {
    }


    //仅关注接收到的数据的业务页面直接使用observer 观察者模式接收消息
    interface Observer {
        fun onReceive(data: String)
        fun onReceive(data: ByteArray)
    }

    private val oVector: Vector<Observer> = Vector()

    //增加一个观察者
    fun addObserver(observer: Observer?) {
        oVector.add(observer)
    }

    //删除一个观察者
    fun removerObserver(observer: Observer?) {
        oVector.remove(observer)
    }

    private fun removerAllObserver() {
        oVector.removeAllElements()
    }

    //通知所有观察者
    fun notifyObserver(data: String) {
        for (observer in oVector) {
            observer?.onReceive(data)
        }
    }

    //通知所有观察者
    fun notifyObserver(data: ByteArray) {
        for (observer in oVector) {
            observer?.onReceive(data)
        }
    }


    //指令队列 用于暂存在未能接受到501enable指令之前发送的消息，指令操作前应该先清空指令队列中的指令
    private val queue by lazy { LinkedList<String>() }

    //清空
    private fun clearQueue() {
        queue.clear()
    }

    //入队
    private fun enQueue(message: String) {
        LogUtils.d("消息入队：\n$message")
        queue.addLast(message)
    }

    //出队
    private fun deQueue(): String {
        return queue.removeFirst() ?: ""
    }
}