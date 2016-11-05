package io.playpen.core;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import io.playpen.core.utils.process.ProcessBuffer;
import io.playpen.core.utils.process.ProcessBuffer;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.CharBuffer;
import java.util.List;

@RunWith(Parameterized.class)
public class ProcessBufferTest {
    private static final String UNIX_LINE_SEPERATOR = "\n";
    private static final String WINDOWS_LINE_SEPERATOR = "\r\n";

    @Parameterized.Parameter(0)
    public String lineSeparator;

    @Parameterized.Parameters
    public static Iterable<Object[]> data() {
        return ImmutableList.of(
                new Object[] { UNIX_LINE_SEPERATOR },
                new Object[] { WINDOWS_LINE_SEPERATOR }
        );
    }

    private String fullBuffer(List<String> strings) {
        return String.join(lineSeparator, strings) + lineSeparator;
    }

    @Test
    public void verifyFunctionality() {
        TestBuffer buffer = new TestBuffer(Lists.newArrayList("Test"));
        buffer.append(CharBuffer.wrap(fullBuffer(ImmutableList.of("Test"))));

        sanityCheck(buffer);
    }

    @Test
    public void verifyMultipleLines() {
        List<String> strings = Lists.newArrayList("Test", "Testing2", "Potato");
        TestBuffer buffer = new TestBuffer(strings);
        buffer.append(CharBuffer.wrap(fullBuffer(strings)));

        sanityCheck(buffer);
    }

    @Test
    public void verifyIncompleteLine() {
        List<String> strings = Lists.newArrayList("Test", "Testing2");
        TestBuffer buffer = new TestBuffer(strings);
        buffer.append(CharBuffer.wrap(fullBuffer(strings) + "Potato"));

        sanityCheck(buffer);
    }

    @Test
    public void verifyLaterCompletedLine() {
        List<String> strings = Lists.newArrayList("Test", "Testing2", "Potato");
        TestBuffer buffer = new TestBuffer(strings);
        buffer.append(CharBuffer.wrap(String.join(lineSeparator, strings)));
        buffer.append(CharBuffer.wrap(lineSeparator));
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
