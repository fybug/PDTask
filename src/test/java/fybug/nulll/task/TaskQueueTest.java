package fybug.nulll.task;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;

public
class TaskQueueTest {
    TaskQueue tasks;
    StringWriter writer;

    @Before
    public
    void setUp() {
        writer = new StringWriter();
        tasks = TaskQueue.build()
                         .runcall(r -> writer.write(RunTest.testdata))
                         .pool(RunTest.pool)
                         .build();
    }

    @After
    public
    void tearDown() throws IOException {
        tasks.close();
        writer.close();
    }

    @Test
    public
    void addtask() throws InterruptedException {
        var s = tasks.addtask(() -> {
            try {
                Thread.sleep(1000);
            } catch ( InterruptedException ignored ) {
            }
            writer.write(RunTest.testdata);
        });
        s.sync();
        writer.write("pring");

        Assert.assertEquals(writer.toString(), RunTest.testdata + RunTest.testdata + "pring");
    }
}