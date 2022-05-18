package fybug.nulll.task.serializable;
import org.jetbrains.annotations.NotNull;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import fybug.nulll.pdconcurrent.RWLock;
import fybug.nulll.pdconcurrent.SyLock;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * <h2>可序列化类.</h2>
 * <p>
 * 内部指定了两个序列化时用的路径参数 {@link #path},{@link #filename}<br/>
 * {@link #path} 序列化文件的保存目录<br/>
 * {@link #filename} 序列化文件保存的名称<br/>
 * 内部定义了一个用于设置 {@link #path},{@link #filename} 参数的方法 {@link #setPath(String, String)}，该方法在设置新的路径参数时会检查并删除旧的文件（如果有的话）
 * <br/><br/>
 * 定义了两个用于保存序列化文件和删除序列化文件的方法 {@link #save1()} 和 {@link #delte1()}，这两个方法都是没有上锁且只能继承类调用<br/>
 * 调用时根据情况上锁，一般情况下上读锁，因为这两个方法没有修改任何参数<br/>
 * 需要内部的锁时调用 {@link #getLOCK()} 获取
 * <br/><br/>
 * 同时建议重写 {@link #writeExternal(ObjectOutput)} 和 {@link #readExternal(ObjectInput)} 方法，但是要记住调用父方法<br/>
 * {@code super.writeExternal(out);} 和 {@code super.readExternal(out);} 必不可少，因为原本的方法中已经对部分参数进行序列化，忽略会导致序列化功能在后续无法正常调用，同时也会导致内置的锁 {@link #LOCK} 无法使用
 *
 * @author fybug
 * @version 0.0.1
 * @see Externalizable
 * @since serializable 0.0.1
 */
public abstract
class CanSerializable implements Externalizable {
    /** 序列化文件保存的路径 */
    @NotNull protected String path = "";
    /** 序列化文件的保存名称 */
    @NotNull protected String filename = "";
    /** 序列化文件的临时文件名称 */
    @NotNull protected String filenametmp = "";
    /** 该对象的并发锁 */
    @NotNull protected transient RWLock LOCK = SyLock.newRWLock();

    //----------------------------------------------------------------------------------------------

    /**
     * @param path      序列化文件保存的路径
     * @param filenamne 序列化文件的保存名称
     */
    public
    CanSerializable(@NotNull String path, @NotNull String filenamne) { setPath(path, filenamne); }

    //----------------------------------------------------------------------------------------------

    /**
     * 设置新的保存路径时会同时清除旧的保存文件（如果有）
     *
     * @param path      序列化文件保存的路径
     * @param filenamne 序列化文件的保存名称
     *
     * @throws NullPointerException 当 path 和 filenamne 为 NULL 或 filenamne 为空字符串时
     * @see #path
     * @see #filename
     * @see #filenametmp
     */
    public
    void setPath(@NotNull String path, @NotNull String filenamne) {
        LOCK.write(() -> {
            // 保证不为空
            if (path == null || filenamne == null || filenamne.equals(""))
                throw new NullPointerException("'path' and 'filenamne' cannot be NULL,'filenamne' cannot be an empty string");

            // 如果曾经有路径信息，先清除之前的残留
            if (!this.filename.equals("")) {
                try {
                    // 删除掉
                    delte1();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }

                try {
                    // 删除掉临时文件
                    deltetmp1();
                } catch ( IOException e ) {
                    e.printStackTrace();
                }
            }

            // 设置新的路径信息
            this.path = path;
            this.filename = filenamne;
            this.filenametmp = filenamne + '.' + 't' + 'm' + 'p';
        });
    }

    /**
     * @return 内部的锁对象
     *
     * @see #LOCK
     */
    @NotNull
    public
    RWLock getLOCK() { return LOCK; }

    //----------------------------------------------------------------------------------------------

    /**
     * 保存序列化文件
     * <p>
     * 该方法没有加锁，使用时建议外部加一层读锁，因为只用到了 {@link #path} 和 {@link #filename}
     * <p>
     * 该方法写入时会先创建一个在原本的路径上加上 ".tmp" 后缀的临时文件，在写入完成后会重命名为指定的名称，使用经典的移动文件来重命名并覆盖原本的文件（如果有
     *
     * @see #path
     * @see #filename
     * @see #filenametmp
     */
    protected
    void save1() throws IOException {
        // 保存的文件
        var files = Path.of(path, filename);
        // 临时文件
        var tmpfiles = Path.of(path, filenametmp);

        // 保证目录存在
        Files.createDirectories(files.getParent());
        // 打开输出流
        ObjectOutputStream outputStream =
                new ObjectOutputStream(Files.newOutputStream(tmpfiles, WRITE, TRUNCATE_EXISTING, CREATE));
        // 输出到临时文件
        outputStream.writeObject(this);
        outputStream.flush();
        outputStream.close();

        // 移动临时文件为正式保存的文件
        Files.move(tmpfiles, files, REPLACE_EXISTING, ATOMIC_MOVE);
    }

    /**
     * 删除序列化文件
     * <p>
     * 该方法没有加锁，使用时建议外部加一层读锁，因为只用到了 {@link #path} 和 {@link #filename}
     *
     * @return 是否删除成功
     *
     * @see #path
     * @see #filename
     */
    protected
    boolean delte1() throws IOException { return Files.deleteIfExists(Path.of(path, filename)); }

    /**
     * 删除序列化用的临时文件
     * <p>
     * 该方法没有加锁，使用时建议外部加一层读锁，因为只用到了 {@link #path} 和 {@link #filenametmp}
     * <p>
     * 至于临时文件何时产生，请看 {@link #save1()}
     *
     * @return 是否删除成功
     *
     * @see #path
     * @see #filenametmp
     */
    protected
    boolean deltetmp1() throws IOException
    { return Files.deleteIfExists(Path.of(path, filenametmp)); }

    //----------------------------------------------------------------------------------------------

    @Override
    public
    void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(path);
        out.writeObject(filename);
    }

    @Override
    public
    void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.path = in.readObject().toString();
        this.filename = in.readObject().toString();
        this.LOCK = SyLock.newRWLock();
    }
}
