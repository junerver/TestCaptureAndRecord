package xyz.junerver.testcaptureandrecord.utils

/**
 * @Author Junerver
 * @Date 2021/8/5-13:43
 * @Email junerver@gmail.com
 * @Version v1.0
 * @Description
 */
object WebSocketConfig {
    const val TEACHER_CONNECT_COMMAND = "0x4001"  //确认连接信息专用指令头
    const val CTRL_COMMAND = "0x4002"  //手势、鼠标、激光笔等需要使用的指令头
    const val SEND_COMMAND = "0x4000"  //普通信息通用的指令头
    const val LOCAL_WS_PORT = ":37628"  //本地ws的端口
    const val PART_LENGTH_LIMIT = 40000  //普通大消息切片大小
    const val VIDEO_PART_LENGTH_LIMIT = 30000  //视频文件切片大小
    const val PROTOCOL_HEAD_WS= "ws://"
    const val PROTOCOL_HEAD_WSS= "wss://"

}