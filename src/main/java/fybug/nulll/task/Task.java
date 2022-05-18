package fybug.nulll.task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * <h2>任务对象.</h2>
 * <p>
 * 需要注册任务实体 {@link Runnable} 和反馈对象 {@link Back}<br/>
 * 通过该对象实现任务队列和反馈对象 {@link Back} 的交互
 *
 * @author fybug
 * @version 0.0.1
 * @see TaskQueue
 * @see Back
 * @since PDTasks 0.0.1
 */
public
class Task implements Runnable {
    /** 任务内容 */
    @NotNull protected final Optional<Runnable> runnable;
    /** 反馈对象 */
    @NotNull protected final Back commput;

    //----------------------------------------------------------------------------------------------

    /**
     * 构造任务对象
     *
     * @param runnable 任务内容
     * @param commput  反馈对象
     */
    public
    Task(@Nullable Runnable runnable, @NotNull Back commput) {
        this.runnable = Optional.ofNullable(runnable);
        this.commput = commput;
    }

    //----------------------------------------------------------------------------------------------

    /** 执行线程内容 */
    public
    void run() {
        commput.thread = Thread.currentThread();
        runnable.ifPresent(Runnable::run);
    }

    /**
     * 结束反馈
     * <p>
     * 在此时对应的 {@link Back#sync()} 会终止等待
     *
     * @see Back#sync()
     * @see #commput
     */
    public
    void end() {
        synchronized ( commput ){
            commput.thread = null;
            commput.end = true;
            commput.notifyAll();
        }
    }
}