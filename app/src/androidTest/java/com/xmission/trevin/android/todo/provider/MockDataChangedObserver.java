package com.xmission.trevin.android.todo.provider;

import static org.junit.Assert.assertTrue;

import android.database.DataSetObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * An observer to install on the repository to watch for
 * data set changes.
 *
 * @author Trevin Beattie
 */
public class MockDataChangedObserver extends DataSetObserver {
    private final ToDoRepository repository;
    private final CountDownLatch latch;
    private boolean changed = false;

    public MockDataChangedObserver(ToDoRepository repository) {
        this.repository = repository;
        latch = new CountDownLatch(1);
    }

    @Override
    public void onChanged() {
        changed = true;
        latch.countDown();
    }
    @Override
    public void onInvalidated() {
        latch.countDown();
    }

    /**
     * Wait for the latch to show changes.
     * Times out after 0.2 seconds.
     *
     * @param expectChanged whether the data set should have changed
     *
     * @throws AssertionError if {@code expectChanged} is {@code true}
     * and the latch times out, or if {@code expectChanged} is
     * {@code false} and {@link #onChanged()} is called before the
     * timeout.
     * @throws InterruptedException if the wait was interrupted
     */
    public void await(boolean expectChanged) throws InterruptedException {
        try {
            latch.await(200, TimeUnit.MILLISECONDS);
            if (expectChanged)
                assertTrue("Timed out waiting for the repository to finish", changed);
        } finally {
            repository.unregisterDataSetObserver(this);
        }
    }

    /** @return whether the data set has changed */
    public boolean hasChanged() {
        return changed;
    }

}
