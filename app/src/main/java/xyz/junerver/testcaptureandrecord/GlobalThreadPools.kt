package xyz.junerver.testcaptureandrecord

import java.lang.Runnable
import java.util.concurrent.atomic.AtomicInteger
import java.lang.Thread
import java.util.concurrent.*

/**
 * 全局使用的线程池
 */
class GlobalThreadPools private constructor() {
    //初始化线程池
    private fun initThreadPool() {
        THREAD_POOL_EXECUTOR = ThreadPoolExecutor(
            CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS.toLong(), TimeUnit.SECONDS,
            sPoolWorkQueue, sThreadFactory, RejectedHandler()
        )
    }

    private class RejectedHandler : RejectedExecutionHandler {
        override fun rejectedExecution(r: Runnable, executor: ThreadPoolExecutor) {
            //可在这里做一些提示用户的操作
//            Log.v("+++","is over the max task...");
        }
    }

    fun execute(command: Runnable?) {
        if (THREAD_POOL_EXECUTOR == null) {
            initThreadPool()
        }
        THREAD_POOL_EXECUTOR!!.execute(command)
    }

    /**
     * 排队总数+活动总数数量为则可以认定全部任务执行完毕
     *
     * @return
     */
    fun hasDone(): Boolean {
        return if (THREAD_POOL_EXECUTOR != null) {
            THREAD_POOL_EXECUTOR!!.queue.size + THREAD_POOL_EXECUTOR!!.activeCount == 0
        } else {
            //线程池已经置空任务结束
            true
        }
    }

    /**
     * 通过interrupt方法尝试停止正在执行的任务，但是不保证真的终止正在执行的任务
     * 停止队列中处于等待的任务的执行
     * 不再接收新的任务 慎用！
     *
     * @return 等待执行的任务列表
     */
    fun shutdownNow() {
        THREAD_POOL_EXECUTOR?.shutdownNow()
        THREAD_POOL_EXECUTOR = null
    }

    /**
     * 停止队列中处于等待的任务
     * 不再接收新的任务 慎用！
     * 已经执行的任务会继续执行
     * 如果任务已经执行完了没有必要再调用这个方法
     */
    fun shutDown() {
        THREAD_POOL_EXECUTOR?.shutdown()
        sPoolWorkQueue.clear()
    }

    companion object {
        private val TAG = GlobalThreadPools::class.java.simpleName

        //线程池
        private var THREAD_POOL_EXECUTOR: ThreadPoolExecutor? = null

        //CPU数量
        private val CPU_COUNT = Runtime.getRuntime().availableProcessors()

        //核心线程数
        private val CORE_POOL_SIZE = CPU_COUNT

        //最大线程数
        private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2

        //线程闲置后的存活时间
        private const val KEEP_ALIVE_SECONDS = 60

        //任务队列
        private val sPoolWorkQueue: BlockingQueue<Runnable> = LinkedBlockingQueue()

        //线程工厂
        private val sThreadFactory: ThreadFactory = object : ThreadFactory {
            private val mCount = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "MangoTask #" + mCount.getAndIncrement())
            }
        }
        var instance: GlobalThreadPools? = null
            get() {
                if (field == null) {
                    field = GlobalThreadPools()
                }
                return field
            }
            private set
    }

    init {
        initThreadPool()
    }
}