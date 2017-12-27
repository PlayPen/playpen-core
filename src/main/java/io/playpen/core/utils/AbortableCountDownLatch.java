package io.playpen.core.utils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// credit to https://stackoverflow.com/a/10455821/646180
public class AbortableCountDownLatch extends CountDownLatch {
    protected boolean aborted = false;

    public AbortableCountDownLatch(int count) {
        super(count);
    }


    /**
     * Unblocks all threads waiting on this latch and cause them to receive an
     * AbortedException.  If the latch has already counted all the way down,
     * this method does nothing.
     */
    public void abort() {
        if( getCount()==0 )
            return;

        this.aborted = true;
        while(getCount()>0)
            countDown();
    }


    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        final boolean rtrn = super.await(timeout,unit);
        if (aborted)
            throw new AbortedException();
        return rtrn;
    }

    @Override
    public void await() throws InterruptedException {
        super.await();
        if (aborted)
            throw new AbortedException();
    }


    public static class AbortedException extends InterruptedException {
        public AbortedException() {
        }

        public AbortedException(String detailMessage) {
            super(detailMessage);
        }
    }
}