package fybug.nulll.task.serializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.READ;

/**
 * <h2>可序列化的任务列表执行工具.</h2>
 * <p>
 * 该类所有操作均已上锁，并发安全<br/>
 * 如果需要反序列化该对象，可以调用 {@link TaskStorage#readTaskStorage(String, String)} 方法快速反序列化
 * <br/><br/>
 * 该类内部用一个 {@link LinkedHashSet} 来保存任务对象，因此需要将一个任务对象从队列中删除时，可以使用该任务对象本身 {@link #removeTask(TaskMedium)} 也可以使用任务对象在队列中的位置 {@link #removeTask(int)}<br/>
 * 任务对象的执行是按照插入顺序进行的，插入任务对象通过 {@link #addTask(TaskMedium)} 方法进行<br/>
 * 需要注意的时，队列内的任务对象 {@link TaskMedium} 必须是可序列化的，其中的参数也必须可序列化
 * <br/><br/>
 * 需要运行时调用 {@link #run()} 方法，调用后会从上次运行到的位置开始继续运行任务列表，通过 {@link #getNowNum()} 获取当前运行的位置，通过 {@link #getNowTaskMedium()} 获取下次要运行的任务的对象<br/>
 * 如果需要从头运行请调用 {@link #reset()} 这将会重置 {@link #num} 为0，这可以让任务列表从头开始运行<br/>
 * {@link #run()} 方法开始运行后会先调用一次 {@link #save()} 对任务列表进行一次序列化保存，随后每次运行完一个任务都会调用一次进行序列化保存
 * <br/><br/>
 * 如果任务对象运行途中发生异常将不会抛出该异常，只会进行打印，并中断任务列表的执行，这会导致返回一个 false<br/>
 * {@link #run()} 运行途中发生异常通常是序列化失败导致，请检查是否能够对指定的路径和文件写入数据
 * <br/>
 * <pre>示例：
 * public static
 * void main(String[] args) {
 *     // 假设需要检查是否有任务列表需要恢复
 *     // 因为每执行一个任务都会保存一次，所以任务列表恢复后不会执行以前已经完成了的任务
 *
 *     // 序列化保存的路径
 *     String pa = "/tmp/";
 *     String filname = "a.save";
 *
 *     try {
 *         // 反序列化检查是否有需要恢复的任务列表
 *         TaskStorage taskStorage = TaskStorage.readTaskStorage(pa, filname);
 *
 *         // 无法读取时会返回 null
 *         if (taskStorage == null) {
 *             // 为 null 则无需要恢复的任务列表，但是这里示例依旧执行
 *             taskStorage = new TaskStorage(pa, filname);
 *             // 添加任务
 *             taskStorage.addTask(() -> System.out.println("任务1"));
 *             taskStorage.addTask(() -> System.out.println("任务2"));
 *             taskStorage.addTask(() -> System.out.println("任务3"));
 *             taskStorage.addTask(() -> System.out.println("任务4"));
 *         }
 *
 *         // 执行
 *         taskStorage.run();
 *     } catch ( IOException | ClassNotFoundException e ) {
 *         e.printStackTrace();
 *     }
 * }</pre>
 *
 * @author fybug
 * @version 0.0.1
 * @see TaskMedium
 * @see CanSerializable
 * @since serializable 0.0.1
 */
public
class TaskStorage extends CanSerializable {
    /** 任务列表 */
    @NotNull protected transient LinkedHashSet<TaskMedium> TASK_LIST = new LinkedHashSet<>();
    /**
     * 任务执行计数
     * 用来记录执行到任务列表中的第几个任务
     */
    protected int num = 0;

    //----------------------------------------------------------------------------------------------

    /**
     * @param path      保存任务文件的路径
     * @param filenamne 保存的任务文件名称
     */
    public
    TaskStorage(@NotNull String path, @NotNull String filenamne) { super(path, filenamne); }

    //----------------------------------------------------------------------------------------------

    /**
     * 追加一个任务对象到列表中
     *
     * @param ta 要加入列表的任务对象
     *
     * @return this
     *
     * @see #TASK_LIST
     * @see LinkedHashSet
     */
    @NotNull
    public
    TaskStorage addTask(@NotNull TaskMedium ta) {
        Optional.of(ta).ifPresent(t -> LOCK.write(() -> TASK_LIST.add(t)));
        return this;
    }

    /**
     * 删除掉指定的任务对象
     *
     * @param t 要删除的任务对象
     *
     * @return 是否删除成功
     *
     * @see #TASK_LIST
     * @see LinkedHashSet
     */
    public
    boolean removeTask(@NotNull TaskMedium t) {
        if (t == null)
            return false;
        return LOCK.write(() -> {
            // 删除
            if (TASK_LIST.remove(t)) {
                // 检查游标的位置，防止参数偏移
                if (num == TASK_LIST.size())
                    num = TASK_LIST.size() - 1;
                else if (num > TASK_LIST.size())
                    num = TASK_LIST.size();
                return true;
            }
            return false;
        });
    }

    /**
     * 删除掉指定位置的任务对象
     *
     * @param s 要删除的任务对象的位置
     *
     * @return 是否删除成功
     *
     * @see #TASK_LIST
     * @see LinkedHashSet
     * @see #num
     */
    public
    boolean removeTask(int s) {
        if (s < -1 || s >= TASK_LIST.size())
            return false;
        return LOCK.write(() -> {
            TaskMedium t = null;
            int i = 0;
            // 检查任务列表
            for ( TaskMedium taskMedium : TASK_LIST ){
                // 如果是这个位置
                if (i++ == s) {
                    t = taskMedium;
                    break;
                }
            }
            // 删除
            if (t != null && TASK_LIST.remove(t)) {
                // 检查游标的位置，防止参数偏移
                if (num == TASK_LIST.size())
                    num = TASK_LIST.size() - 1;
                else if (num > TASK_LIST.size())
                    num = TASK_LIST.size();
                return true;
            }
            return false;
        });
    }

    /**
     * @return 当前要执行的任务对象
     *
     * @see #TASK_LIST
     * @see #num
     */
    @Nullable
    public
    TaskMedium getNowTaskMedium() {
        return LOCK.read(() -> {
            int i = 0;
            // 检查任务列表
            for ( TaskMedium taskMedium : TASK_LIST ){
                // 如果是这个位置
                if (i++ == num) {
                    return taskMedium;
                }
            }
            return null;
        });
    }

    /**
     * @return 当前要执行的任务在任务列表中的位置
     *
     * @see #num
     */
    public
    int getNowNum() { return LOCK.read(() -> num); }

    /**
     * @return 当前任务列表
     *
     * @see #TASK_LIST
     */
    @NotNull
    public
    TaskMedium[] getTaskList() { return LOCK.read(() -> TASK_LIST.toArray(TaskMedium[]::new)); }

    /**
     * @return 当前任务列表的长度
     *
     * @see #TASK_LIST
     * @see LinkedHashSet
     */
    public
    int size() { return LOCK.read(() -> TASK_LIST.size()); }

    /**
     * @return 是否执行完成了整个列表
     *
     * @see #num
     * @see #size()
     */
    public
    boolean isEnd() { return LOCK.read(() -> num >= TASK_LIST.size()); }

    //----------------------------------------------------------------------------------------------

    /**
     * 执行任务列表
     * <p>
     * 执行时会上读锁，每执行完一个任务就会覆盖保存一次任务文件
     * <p>
     * 如果其中一个任务出错则会中断整个任务列表的执行
     *
     * @return 是否执行完成整个列表
     *
     * @throws IOException 系统IO出错时
     * @see TaskMedium#run()
     */
    public
    boolean run() throws IOException {
        // 是否执行完整
        final boolean[] ok = {true};

        LOCK.trywrite(IOException.class, () -> {
            // 当前计数，用于跳过之前已经执行过的任务
            var i = 0;
            // 执行任务列表
            for ( TaskMedium taskMedium : TASK_LIST ){
                // 当前为需要执行的任务
                if (i++ == num) {
                    // 保存一次
                    save1();
                    try {
                        // 执行
                        taskMedium.run();
                    } catch ( Exception e ) {
                        // 出错跳出执行
                        ok[0] = false;
                        break;
                    }
                    num++;
                }
            }
        });

        return ok[0];
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 重置任务执行计数
     * <p>
     * 重置 {@link #num}
     *
     * @return this
     *
     * @see #num
     */
    @NotNull
    public
    TaskStorage reset() {
        LOCK.write(() -> num = 0);
        return this;
    }

    /**
     * 清除任务列表和执行计数
     * <p>
     * 清除 {@link #TASK_LIST} 和 {@link #num}
     *
     * @return this
     *
     * @see #TASK_LIST
     * @see #num
     */
    @NotNull
    public
    TaskStorage clean() {
        LOCK.write(() -> {
            num = 0;
            TASK_LIST.clear();
        });
        return this;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 保存任务文件
     * <p>
     * 执行时会上读锁
     *
     * @return this
     *
     * @throws IOException java 底层发生IO错误时
     * @see Externalizable
     * @see #save1()
     */
    @NotNull
    public
    TaskStorage save() throws IOException {
        LOCK.tryread(IOException.class, this::save1);
        return this;
    }

    /**
     * 删除任务文件
     * <p>
     * 执行时会上读锁
     *
     * @return 是否删除成功
     *
     * @throws IOException java 底层发生IO错误时
     * @see #delte1()
     */
    public
    boolean delete() throws IOException { return LOCK.tryread(IOException.class, this::delte1); }

    /**
     * 删除序列化时的临时文件
     * <p>
     * 执行时会上读锁
     *
     * @return 是否删除成功
     *
     * @throws IOException java 底层发生IO错误时
     * @see #deltetmp1()
     */
    public
    boolean deletetmp() throws IOException
    { return LOCK.tryread(IOException.class, this::deltetmp1); }

    //----------------------------------------------------------------------------------------------

    @Override
    public
    void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        // 记录任务列表的长度
        out.write(TASK_LIST.size());
        // 单独序列化其中的任务列表
        for ( TaskMedium i : TASK_LIST ){
            out.writeObject(i);
        }
        // 保存其他参数
        out.writeInt(num);
    }

    @Override
    public
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        super.readExternal(in);
        // 重建任务列表
        this.TASK_LIST = new LinkedHashSet<>();
        // 获取任务列表的长度
        int i = in.readInt();
        // 重新填充任务列表
        for ( ; i > 0; i-- ){
            TASK_LIST.add((TaskMedium) in.readObject());
        }
        // 重新获取其他参数
        this.num = in.readInt();
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 反序列化
     *
     * @param path      保存任务文件的路径
     * @param filenamne 保存的任务文件名称
     *
     * @return 如果无法读取，将会返回 null
     *
     * @throws IOException,ClassNotFoundException 发生底层错误时
     */
    @Nullable
    public static
    TaskStorage readTaskStorage(@NotNull String path, @NotNull String filenamne)
    throws IOException, ClassNotFoundException
    {
        TaskStorage a = null;

        var files = Path.of(path, filenamne);
        if (Files.isExecutable(files)) {
            // 打开读取流
            var in = new ObjectInputStream(Files.newInputStream(files, READ));
            a = (TaskStorage) in.readObject();
            in.close();
        }
        return a;
    }
}
