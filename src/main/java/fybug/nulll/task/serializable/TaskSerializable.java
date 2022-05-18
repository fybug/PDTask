package fybug.nulll.task.serializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.READ;

/**
 * <h2>可序列化的任务列表执行工具.</h2>
 * <p>
 * 该类用于实现可序列化的单个任务对象，内置的所有操作已经进行线程安全处理<br/>
 * 可通过 {@link #getLOCK()} 获取内部使用的锁对象<br/>
 * 需要反序列化时可以调用 {@link TaskSerializable#readTaskSerializable(String, String)} 快速反序列化
 * <br/><br/>
 * 在需要序列化时调用 {@link #save()} 即可自动序列化，需要在构造时就传入 {@link #path} 和 {@link #filename}，这两个参数用于指定序列化文件保存的路径和文件名，是必须有的参数<br/>
 * 在需要删除时，调用 {@link #delete()} 即可
 * <br/><br/>
 * 自行重写 {@link #run()} 方法，在需要时调用 {@link #save()} 和 {@link #delete()} 即可实现一个可随时序列化的任务对象
 * <br/>
 * <pre>示例：
 * public
 * class a extends TaskSerializable {
 *     public static
 *     void main(String[] args) {
 *         // 假设每次启动时检查是否有未执行完成的任务
 *         // 未执行完成的任务通常在执行开始前就已经开始序列化，这里通过检查是否有序列化文件来确定是否需要恢复任务进行执行
 *
 *         // 序列化保存的路径
 *         String pa = "/tmp/";
 *         String filname = "a.save";
 *
 *         a taskSerializable;
 *         try {
 *             // 反序列化
 *             taskSerializable = (a) TaskSerializable.readTaskSerializable(pa, filname);
 *             // 如果读取失败，会返回 null
 *             if (taskSerializable == null) {
 *                 // 这里为 null 则意味着没有需要恢复的任务文件，但是这里作为示例依旧执行一次
 *                 taskSerializable = new a(pa, filname);
 *             }
 *             // 执行
 *             taskSerializable.run();
 *         } catch ( IOException | ClassNotFoundException e ) {
 *             e.printStackTrace();
 *         }
 *     }
 *
 *     // 必须有的
 *     public
 *     a(@NotNull String path, @NotNull String filename)
 *     { super(path, filename); }
 *
 *     public
 *     void run() {
 *         try {
 *             // 看情况，有些业务需要进行检查先
 *             System.out.println("初始检查");
 *             // 正式执行前应该保存一次
 *             save();
 *             System.out.println("执行中");
 *             System.out.println("执行完成");
 *             // 注意，应该在执行完成后再进行删除，当然，需要根据业务灵活调整
 *             delete();
 *         } catch ( IOException e ) {
 *             e.printStackTrace();
 *         }
 *     }
 * }</pre>
 *
 * @author fybug
 * @version 0.0.1
 * @see CanSerializable
 * @see Runnable
 * @since serializable 0.0.1
 */
public abstract
class TaskSerializable extends CanSerializable implements Runnable {

    public
    TaskSerializable(@NotNull String path, @NotNull String filename) { super(path, filename); }

    //----------------------------------------------------------------------------------------------

    public
    TaskSerializable save() throws IOException {
        LOCK.tryread(IOException.class, this::save1);
        return this;
    }

    public
    boolean delete() throws IOException { return LOCK.tryread(IOException.class, this::delte1); }

    public
    boolean deletetmp() throws IOException
    { return LOCK.tryread(IOException.class, this::deltetmp1); }

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
    TaskSerializable readTaskSerializable(@NotNull String path, @NotNull String filenamne)
    throws IOException, ClassNotFoundException
    {
        TaskSerializable a = null;

        var files = Path.of(path, filenamne);
        if (Files.isExecutable(files)) {
            // 打开读取流
            var in = new ObjectInputStream(Files.newInputStream(files, READ));
            a = (TaskSerializable) in.readObject();
            in.close();
        }
        return a;
    }
}
