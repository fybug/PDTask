package fybug.nulll.task;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.StringWriter;

import static fybug.nulll.task.RunTest.pool;
import static fybug.nulll.task.RunTest.testdata;
public
class TasksGroupTest {
    TasksGroup group;
    StringWriter writer;
    int id;

    @Before
    public
    void setUp() {
        writer = new StringWriter();
        group = TasksGroup.build().idRollBack().runcall(r -> writer.write(testdata)).build();
        id = group.addQueue(pool);
    }

    @After
    public
    void tearDown() throws Exception {
        group.close();
        writer.close();
    }

    @Test
    public
    void addtask() throws InterruptedException {
        var s = group.addtask(() -> {
            try {
                Thread.sleep(1000);
            } catch ( InterruptedException ignored ) {
            }
            writer.write(RunTest.testdata);
        }, id);
        s.sync();
        writer.write("pring");

        Assert.assertEquals(writer.toString(), RunTest.testdata + RunTest.testdata + "pring");
    }
}