package fybug.nulll.task;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RunWith( Suite.class )
@Suite.SuiteClasses( {TaskQueueTest.class, TasksGroupTest.class} )
public
class RunTest {
    public static final String testdata = "akjhdjkxcvxcgjisasduiqewhncxzkj,v xnckvrs nkjflcxdv bzl";
    public static final ExecutorService pool = Executors.newCachedThreadPool();
}