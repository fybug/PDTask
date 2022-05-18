package fybug.nulll.task;

/**
 * <h2>任务反馈对象.</h2>
 * <p>
 * 用于等待检查任务执行状态或干预任务使任务接受停止通知<br/>
 * 在任务完成前 {@link #sync()} 都会阻塞
 *
 * @author fybug
 * @version 0.0.2
 * @see TaskQueue
 * @see Task
 * @since PDTasks 0.0.1
 */
public
class Back {
    /** 是否结束 */
    public volatile boolean end = false;
    /** 执行的线程对象 */
    public volatile Thread thread = null;

    //----------------------------------------------------------------------------------------------

    /**
     * @return 是否执行完毕
     *
     * @see #end
     * @since Back 0.0.2
     */
    public
    boolean isEnd() {
        synchronized ( Back.this ){
            return end;
        }
    }

    /**
     * 等待任务结束
     * <p>
     * 在 {@link Task#end()} 被执行后导致 {@link #end} 改变时即结束等待
     */
    public
    void sync() {
        try {
            synchronized ( Back.this ){
                while( !end )
                    Back.this.wait();
            }
        } catch ( InterruptedException e ) {
            e.printStackTrace();
        }
    }

    /**
     * 通知任务可以停止
     * <p>
     * 采用 {@link Thread#interrupt()}
     */
    public
    void tryStop() {
        synchronized ( Back.this ){
            var thread = this.thread;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }
    }
}
