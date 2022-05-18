package fybug.nulll.task.serializable;
import java.io.Serializable;

/**
 * <h2>可序列化的任务对象.</h2>
 * <p>
 * 该对象为 {@link Runnable} 的变种，用于在可以序列化的任务列表 {@link TaskStorage} 中使用
 * <br/><br/>
 * 需要注意：该类中的所有参数必须可以序列化，或继承 {@link java.io.Externalizable} 进行自定义序列化处理，否则会在 {@link TaskStorage} 的自动序列化途中发生错误
 *
 * @author fybug
 * @version 0.0.1
 * @see TaskStorage
 * @since serializable 0.0.1
 */
public
interface TaskMedium extends Serializable, Runnable {}
