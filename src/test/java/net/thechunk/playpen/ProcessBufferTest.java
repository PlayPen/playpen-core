package net.thechunk.playpen;

import com.google.common.collect.Lists;
import net.thechunk.playpen.utils.process.ProcessBuffer;
import org.junit.Assert;
import org.junit.Test;

import java.nio.CharBuffer;
import java.util.List;

public class ProcessBufferTest {
    @Test
    public void verifyFunctionality() {
        TestBuffer buffer = new TestBuffer(Lists.newArrayList("Test"));
        buffer.append(CharBuffer.wrap("Test\n"));

        sanityCheck(buffer);
    }

    @Test
    public void verifyMultipleLines() {
        TestBuffer buffer = new TestBuffer(Lists.newArrayList("Test", "Testing2", "Potato"));
        buffer.append(CharBuffer.wrap("Test\nTesting2\nPotato\n"));

        sanityCheck(buffer);
    }

    @Test
    public void verifyIncompleteLine() {
        TestBuffer buffer = new TestBuffer(Lists.newArrayList("Test", "Testing2"));
        buffer.append(CharBuffer.wrap("Test\nTesting2\nPotato"));

        sanityCheck(buffer);
    }

    @Test
    public void verifyLaterCompletedLine() {
        TestBuffer buffer = new TestBuffer(Lists.newArrayList("Test", "Testing2"));
        buffer.append(CharBuffer.wrap("Test\nTesting2\nPotato"));
        sanityCheck(buffer);

        buffer.expected = Lists.newArrayList("Potato");
        buffer.origSize = 1;
        buffer.append(CharBuffer.wrap("\n"));
        sanityCheck(buffer);
    }

    private static void sanityCheck(TestBuffer buffer) {
        if (!buffer.expected.isEmpty()) {
            int hasRead = buffer.origSize - buffer.expected.size();
            Assert.fail("Didn't read enough data (read " + hasRead + " lines, wanted " + buffer.origSize + " lines)");
        }
    }

    private class TestBuffer extends ProcessBuffer {
        private List<String> expected;
        private int origSize;

        private TestBuffer(List<String> expected) {
            this.expected = expected;
            origSize = expected.size();
        }

        @Override
        protected void onOutput(String output) {
            if (expected.isEmpty()) {
                // We are reading too much.
                Assert.fail("Read too much data (wanted " + origSize + " elements)");
                return;
            }
            Assert.assertEquals(expected.remove(0), output);
        }
    }
}
