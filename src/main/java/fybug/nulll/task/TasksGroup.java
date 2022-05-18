package fybug.nulll.task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import fybug.nulll.pdconcurrent.ObjLock;
import fybug.nulll.pdconcurrent.SyLock;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * <h2>任务队列组.</h2>
 * 对 {@link TaskQueue} 进行组管理，使用对应 id 来操作对应的任务队列。也可以使用随机分配向队列插入任务<br/>
 * 使用任务组可以并行执行多条任务队列。<br/>
 * 可通过修改构造方法传入的回滚队列是否为空来选择启用或关闭任务 id 回滚，建议使用 {@link #build()} 来构造<br/>
 * 可注册全局任务处理回调和队列结束回调
 *
 * @author fybug
 * @version 0.0.1
 * @since PDTasks 0.0.1
 * todo test
 */
@Deprecated
public
class TasksGroup implements Closeable {
    // 操作队列
    private final List<TaskQueue> QUEUE = new ArrayList<>();

    // 最大 id 计数
    private int maxId = 0;
    // 可用的 id
    private final Optional<Set<Integer>> ID_POOL;
    // 生效 id 记录
    private final Set<Integer> IDS = new HashSet<>();

    private final SyLock LOCK = new ObjLock(this);

    // 任务处理监听
    private final Consumer<Runnable> RUN_CALL;
    // 结束监听
    private final Runnable CLOSE_CALL;

    //----------------------------------------------------------------------------------------------

    /**
     * 构造任务队列
     *
     * @param idpool 指定 id 回滚池
     */
    public
    TasksGroup(@Nullable Set<Integer> idpool) { this(idpool, null, null); }

    /**
     * 构造任务队列
     *
     * @param idpool    指定 id 回滚池
     * @param runcall   队列任务处理监听
     * @param closecall 队列关闭监听
     */
    public
    TasksGroup(@Nullable Set<Integer> idpool, Consumer<Runnable> runcall, Runnable closecall) {
        ID_POOL = Optional.ofNullable(idpool);
        RUN_CALL = runcall;
        CLOSE_CALL = closecall;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 追加任务队列
     *
     * @param p 队列的线程池
     *
     * @return 任务队列 id
     */
    public
    int addQueue(@NotNull ExecutorService p)
    { return addQueue(TaskQueue.build().pool(p).closeCall(CLOSE_CALL).runcall(RUN_CALL).build()); }

    /**
     * 追加任务队列.
     * <p>
     * 使用单独的线程
     *
     * @return 任务队列 id
     */
    public
    int addQueue()
    { return addQueue(TaskQueue.build().closeCall(CLOSE_CALL).runcall(RUN_CALL).build()); }

    /**
     * 追加任务队列.
     *
     * @param queue 任务队列
     *
     * @return 任务队列 id
     */
    public
    int addQueue(@NotNull TaskQueue queue) {
        return LOCK.write(() -> {
            var id = idget();
            QUEUE.add(id, queue);
            return id;
        });
    }

    /**
     * 获取队列
     *
     * @param id 任务队列 id
     */
    @NotNull
    public
    TaskQueue getQueue(int id) {
        return LOCK.read(() -> {
            checkId(id);
            return QUEUE.get(id);
        });
    }

    //------------------------------------

    // id 分配
    private
    int idget() {
        final int[] id = {0};
        /* 分配 id */
        ID_POOL.ifPresentOrElse(ids -> {
            if (ids.size() > 0) {
                for ( Integer i : ids ){
                    id[0] = i;
                    break;
                }
                ids.remove(id[0]);
            } else
                id[0] = maxId++;
        }, () -> id[0] = maxId++);

        IDS.add(id[0]);
        return id[0];
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 添加任务
     *
     * @param runnable 任务接口
     * @param id       任务队列 id
     *
     * @return 任务反馈对象
     *
     * @throws InterruptedException 任务队列不可用
     */
    @NotNull
    public
    Back addtask(@Nullable Runnable runnable, int id) throws InterruptedException
    { return getQueue(id).addtask(runnable); }

    /**
     * 添加任务
     * <p>
     * 随机选择一个队列进行
     *
     * @param runnable 任务接口
     *
     * @return 任务反馈对象
     *
     * @throws InterruptedException 任务队列不可用
     */
    @NotNull
    public
    Back addtask(@Nullable Runnable runnable) throws InterruptedException {
        return LOCK.tryread(InterruptedException.class, () -> {
            var pool = IDS.toArray(Integer[]::new);

            int id = pool.length <= 1 ? 0 : ((int) (Math.random() * (pool.length - 1)));
            id = pool[id];

            return addtask(runnable, id);
        });
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 关闭任务队列
     *
     * @param runnable 关闭处理
     * @param id       队列 id
     *
     * @return 反馈对象
     */
    @NotNull
    public
    Back close(@Nullable Runnable runnable, int id) {
        // 获取队列
        var q = getQueue(id);
        // 加入关闭事件
        return q.close(() -> {
            LOCK.write(() -> QUEUE.set(id, null));
            Optional.ofNullable(runnable).ifPresent(Runnable::run);
        });
    }

    /**
     * 关闭所有任务队列
     *
     * @param runnable 关闭处理
     *
     * @return 反馈对象
     */
    @NotNull
    public
    Back close(@Nullable Runnable runnable) {
        var ids = LOCK.read(() -> IDS.toArray(Integer[]::new));
        var backlist = new ArrayList<Back>(ids.length);
        // 追加关闭任务
        for ( Integer id : ids ){
            try {
                backlist.add(close(runnable, id));
            } catch ( IndexOutOfBoundsException e ) {
                // null
            }
        }

        // 反馈对象
        return new Back() {
            final LinkedList<Back> BACKS = new LinkedList<>(backlist);

            public
            void sync() {
                for ( var back = BACKS.poll(); back != null; back = BACKS.poll() )
                    back.sync();
            }
        };
    }

    @Override
    public
    void close() { close(null).sync(); }

    //----------------------------------------------------------------------------------------------

    // 校验 id
    private
    void checkId(int id) {
        if (!IDS.contains(id))
            throw new IndexOutOfBoundsException("id:" + id + " -> Queue is not runing;");
    }

    /** 是否有任务 */
    public
    boolean hasTask() {
        var b = false;
        for ( TaskQueue tasks : LOCK.read(() -> List.copyOf(QUEUE)) )
            b |= (tasks != null && tasks.hasTask());
        return b;
    }

    /**
     * 是否有任务
     *
     * @param id 任务队列 id
     */
    public
    boolean hasTask(int id) { return getQueue(id).hasTask(); }

    //----------------------------

    /** 是否有关闭的队列 */
    public
    boolean hasClose() {
        var b = false;
        for ( TaskQueue tasks : LOCK.read(() -> List.copyOf(QUEUE)) )
            b |= (tasks != null && tasks.isClose());
        return b;
    }

    /**
     * 检查任务队列是否结束
     *
     * @param id 任务队列 id
     */
    public
    boolean isClose(int id) { return getQueue(id).isClose(); }

    //----------------------------------------------------------------------------------------------

    /** 获取构造工具 */
    @NotNull
    public static
    Build build() { return new Build(); }

    /*--------------------------------------------------------------------------------------------*/

    /**
     * <h2>任务队列构造工具.</h2>
     * {@link #idRollBack()} 启用 id 回滚功能
     * {@link #runcall(Consumer)} 注册队列任务处理监听
     * {@link #closecall(Runnable)} 注册队列关闭监听
     *
     * @author fybug
     * @version 0.0.1
     * @since TaskQueue 0.0.1
     */
    @Accessors( chain = true, fluent = true )
    public static
    class Build {
        private boolean id_Rollback = false;
        /** 队列任务处理监听 */
        @Setter private Consumer<Runnable> runcall = null;
        /** 队列关闭监听 */
        @Setter private Runnable closecall = null;

        /** 启用 id 回滚 */
        @NotNull
        public
        Build idRollBack() {
            id_Rollback = true;
            return this;
        }

        /** 构造任务队列 */
        @NotNull
        public
        TasksGroup build()
        { return new TasksGroup(id_Rollback ? new HashSet<>() : null, runcall, closecall); }
    }
}
