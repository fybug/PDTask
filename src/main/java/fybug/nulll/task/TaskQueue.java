package fybug.nulll.task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Condition;
import java.util.function.Consumer;

import fybug.nulll.pdconcurrent.ReLock;
import fybug.nulll.pdconcurrent.SyLock;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <h2>任务队列.</h2>
 * <p>
 * 队列式任务执行工具，任务在添加后会加入队列并等待执行<br/>
 * 内部通过一个异步执行的 {@code while} 循环不停的读取任务列表中的内容并执行，在没有任务可执行时会进入等待<br/>
 * 可指定使用线程池或单独的线程，建议使用 {@link #build()} 来构造
 * <br/><br/>
 * 内部使用一个 {@link Queue} 作为任务队列，所有任务并发安全。在关闭后任务队列会彻底清除，对应的线程任务也会结束<br/>
 * 可注入扫尾事件 {@link #CLOSE_CALL} 和观察事件 {@link #RUN_CALL}
 * <br/>
 * <pre>示例：
 * public static
 * void main(String[] args) {
 *     // 构造任务队列
 *     TaskQueue taskQueue = TaskQueue.build()
 *                                    // 结束监听
 *                                    .closeCall(() -> System.out.println("关闭了"))
 *                                    // 每次运行任务时的监听
 *                                    .runcall(r -> System.out.println("当前运行的：" + r.toString()))
 *                                    .build();
 *     try {
 *         // 添加并执行任务
 *         taskQueue.addtask(() -> System.out.println("第一个任务"));
 *         taskQueue.addtask(() -> System.out.println("第二个任务"));
 *         taskQueue.addtask(() -> System.out.println("第三个任务"));
 *         taskQueue.addtask(() -> System.out.println("第四个任务"));
 *
 *         // 关闭任务队列
 *         taskQueue.close();
 *     } catch ( InterruptedException e ) {
 *         e.printStackTrace();
 *     }
 * }</pre>
 *
 * @author fybug
 * @version 0.0.2
 * @see Task
 * @see Back
 * @since PDTasks 0.0.1
 */
public
class TaskQueue implements Closeable {
    /** 是否关闭 */
    protected boolean CLOSE = false;
    /** 结束监听 */
    @Nullable protected Runnable CLOSE_CALL = null;

    /** 队列锁 */
    @NotNull protected final ReLock LOCK = SyLock.newReLock();
    /** 队列锁的管理对象 */
    @NotNull protected Condition QUEUE_WAIT = LOCK.newCondition();

    /** 任务队列 */
    private Queue<Task> QUEUE = new LinkedList<>();
    /** 任务处理监听 */
    @Nullable private Consumer<Runnable> RUN_CALL = null;

    //----------------------------------------------------------------------------------------------

    /**
     * 使用线程池构造任务队列
     *
     * @param executorService 使用的线程池
     *
     * @see ExecutorService
     */
    public
    TaskQueue(@NotNull ExecutorService executorService)
    { executorService.submit(threadTask()); }

    /** 使用单独线程构造任务队列 */
    public
    TaskQueue() { new Thread(threadTask()); }

    /**
     * 使用线程池构造任务队列
     *
     * @param executorService 使用的线程池
     * @param closcall        指定关闭监听
     * @param consumer        任务处理监听
     *
     * @see ExecutorService
     */
    private
    TaskQueue(@NotNull ExecutorService executorService, @Nullable Runnable closcall,
              @Nullable Consumer<Runnable> consumer)
    {
        this(executorService);
        CLOSE_CALL = closcall;
        RUN_CALL = consumer;
    }

    /**
     * 使用单独线程构造任务队列
     *
     * @param closecall 指定关闭监听
     * @param consumer  任务处理监听
     */
    private
    TaskQueue(@Nullable Runnable closecall, @Nullable Consumer<Runnable> consumer) {
        this();
        CLOSE_CALL = closecall;
        RUN_CALL = consumer;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 任务队列处理线程代码
     * <p>
     * 通过不停的循环读取 {@link #QUEUE} 并执行其中的任务对象，如果队列中已经没有了，将会通过 {@link #QUEUE_WAIT} 等待任务内容
     * <p>
     * 如果 {@link #CLOSE} 被标记为 {@code true} 则下一次循环时会退出，并执行清除动作
     */
    private
    Runnable threadTask() {
        return () -> {
            while( !CLOSE ){
                // 拿取任务
                var task = LOCK.write(() -> {
                    var po = Optional.ofNullable(QUEUE.poll());
                    po.ifPresentOrElse(p -> {}, () -> {
                        try {
                            // 等待数据
                            QUEUE_WAIT.await();
                        } catch ( InterruptedException e ) {
                            // 出现异常，关闭
                            queueDestruction();
                        }
                    });
                    return po;
                });

                task.ifPresent(run -> {
                    // 启动监听
                    Optional.ofNullable(RUN_CALL).ifPresent(consumer -> consumer.accept(run));
                    /* 执行操作 */
                    run.run();
                    run.end();
                });
            }

            // 运行关闭监听
            Optional.ofNullable(CLOSE_CALL).ifPresent(Runnable::run);

            // 将队列中的任务标记为已完成，并清除队列
            Optional.ofNullable(QUEUE).ifPresent(q -> {
                q.forEach(Task::end);
                q.clear();
            });
            // 清除参数
            CLOSE_CALL = null;
            QUEUE = null;
            RUN_CALL = null;
        };
    }

    /**
     * 安全关闭任务队列
     * <p>
     * 设置 {@link #CLOSE} 为 {@code true} 随后唤醒任务等待线程，等待其结束循环并开始清除
     */
    private
    void queueDestruction() {
        LOCK.write(() -> {
            CLOSE = true;
            QUEUE_WAIT.signalAll();
        });
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 添加任务
     *
     * @param runnable 任务接口
     *
     * @return 任务反馈对象
     *
     * @throws InterruptedException 任务队列不可用
     * @see Task
     * @see Back
     * @see #QUEUE
     */
    @NotNull
    public
    Back addtask(@Nullable Runnable runnable) throws InterruptedException {
        var back = new Back();

        LOCK.trywrite(InterruptedException.class, () -> {
            canrun();
            Optional.ofNullable(QUEUE).ifPresent(q -> q.add(new Task(runnable, back)));
            QUEUE_WAIT.signalAll();
        });

        return back;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 异步关闭任务队列
     * <p>
     * 通过在任务队列最后插入一个调用 {@link #queueDestruction()} 函数的任务实现，该函数负责安全关闭队列
     * <p>
     * 这意味着在之前的任务完成前队列将会依旧存在，但是后续加入的任务将无法执行
     *
     * @param runnable 关闭处理
     *
     * @return 反馈对象，如果该队列已经关闭则返回 nll
     */
    @Nullable
    public
    Back close(@Nullable Runnable runnable) {
        final Back[] back = {new Back()};

        LOCK.write(() -> {
            if (CLOSE) {
                back[0] = null;
                return;
            }
            Optional.ofNullable(QUEUE).ifPresentOrElse(que -> {
                que.add(new Task(() -> {
                    queueDestruction();
                    Optional.ofNullable(runnable).ifPresent(Runnable::run);
                }, back[0]));
                QUEUE_WAIT.signal();
            }, () -> back[0] = null);
        });

        return back[0];
    }

    /**
     * 关闭任务队列
     * <p>
     * 直接调用 {@link #queueDestruction()} 关闭当前队列，除了当前仍在执行的一个任务外，后续的其他任务都会被忽略并清除
     *
     * @see #queueDestruction()
     */
    @Override
    public
    void close() { queueDestruction(); }

    //----------------------------------------------------------------------------------------------

    /**
     * 是否有任务
     *
     * @see #QUEUE
     */
    public
    boolean hasTask() { return LOCK.read(() -> QUEUE != null && QUEUE.size() > 0); }

    //----------------------------

    /**
     * 检查任务队列是否结束
     *
     * @see #CLOSE
     */
    public
    boolean isClose() {return LOCK.read(() -> CLOSE);}

    /**
     * 检查是否可用
     *
     * @throws InterruptedException 该队列被关闭则会抛出异常
     * @see #CLOSE
     */
    protected
    void canrun() throws InterruptedException {
        if (CLOSE)
            throw new InterruptedException();
    }

    //----------------------------------------------------------------------------------------------

    /** 获取构造工具 */
    @NotNull
    public static
    Build build() { return new Build(); }

    /*--------------------------------------------------------------------------------------------*/

    /**
     * <h2>任务队列构造工具.</h2>
     * {@link #pool(ExecutorService)} 设置使用的线程池
     * {@link #closeCall(Runnable)} 设置队列关闭监听
     * {@link #runcall(Consumer)} 设置任务拿取监听
     *
     * @author fybug
     * @version 0.0.1
     * @since TaskQueue 0.0.1
     */
    @Accessors( fluent = true, chain = true )
    public static
    class Build {
        /**
         * 任务执行监听
         * <p>
         * 每次执行新的任务前都会执行该回调。传入当前任务对象
         */
        @Setter
        @Nullable
        private Consumer<Runnable> runcall = null;
        /** 队列关闭监听 */
        @Setter
        @Nullable
        private Runnable closeCall = null;
        /** 设置线程池，不设置则使用单独的线程 */
        @Nullable
        @Setter
        private ExecutorService pool = null;

        /** 构造任务队列 */
        @NotNull
        public
        TaskQueue build() {
            if (pool == null)
                return new TaskQueue(closeCall, runcall);
            else
                return new TaskQueue(pool, closeCall, runcall);
        }
    }
}
