package org.dynmap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderAdmissionTest {
    @Test void expiredRetryRejoinsBehindFreshWaiters() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var admission = new RenderAdmission(clock::get);
        Object running = new Object(), expired = new Object(), fresh = new Object();
        assertEquals(1, admission.acquire(running, true, 1, 1));
        assertEquals(0, admission.acquire(expired, true, 1, 1));
        clock.set(500_000_000L);
        assertEquals(0, admission.acquire(fresh, true, 1, 1));
        admission.release(true, 1);
        clock.set(1_100_000_000L);
        assertEquals(0, admission.acquire(expired, true, 1, 1));
        assertEquals(1, admission.acquire(fresh, true, 1, 1));
        admission.release(true, 1);
        assertEquals(1, admission.acquire(expired, true, 1, 1));
        admission.release(true, 1);
    }
    @Test void protectedUpdatesTakePriorityOverWaitingFullRenders() {
        var admission = new RenderAdmission();
        Object full = new Object(), update = new Object();
        assertEquals(1, admission.acquire(full, true, 1, 1, true));
        assertEquals(0, admission.acquire(update, false, 1, 1, true));
        admission.release(true, 1);
        assertEquals(0, admission.acquire(full, true, 1, 1, true));
        assertEquals(1, admission.acquire(update, false, 1, 1, true));
        admission.release(false, 1);
    }
    @Test void otherWorldsGetTheirTurnBeforeAFastWorldReenters() {
        var admission = new RenderAdmission();
        Object first = new Object(), second = new Object();
        assertEquals(1, admission.acquire(first, true, 1, 1));
        assertEquals(0, admission.acquire(second, true, 1, 1));
        admission.release(true, 1);
        assertEquals(0, admission.acquire(first, true, 1, 1));
        assertEquals(1, admission.acquire(second, true, 1, 1));
        admission.release(true, 1);
        assertEquals(1, admission.acquire(first, true, 1, 1));
        admission.release(true, 1);
    }

    @Test void interruptedParentDoesNotReleaseItsRunningChildrenSlots() {
        var admission = new RenderAdmission();
        int granted = admission.acquire(true, 3, 3);
        var parent = admission.reservation(true, granted);
        var child = parent.split();
        parent.close();
        assertEquals(1, admission.active());
        assertEquals(2, admission.acquire(false, 3, 3));
        admission.release(false, 2);
        child.close();
        child.close();
        assertEquals(0, admission.active());
    }
    @Test void cancelledWaitersCannotStarveTheRemainingClass() {
        var clock = new java.util.concurrent.atomic.AtomicLong();
        var admission = new RenderAdmission(clock::get);
        assertEquals(1, admission.acquire(true, 1, 1));
        assertEquals(0, admission.acquire(false, 1, 1));
        admission.release(true, 1);
        // The update was cancelled before retrying.
        clock.set(1_000_000_001L);
        assertEquals(1, admission.acquire(true, 1, 1));
        admission.release(true, 1);
    }
    @Test void singleWorkerAlternatesWaitingFullAndUpdateWork() {
        var admission = new RenderAdmission();
        assertEquals(1, admission.acquire(true, 4, 1));
        assertEquals(0, admission.acquire(false, 1, 1));
        admission.release(true, 1);
        assertEquals(0, admission.acquire(true, 1, 1));
        assertEquals(1, admission.acquire(false, 1, 1));
        admission.release(false, 1);
        assertEquals(0, admission.acquire(false, 1, 1));
        assertEquals(1, admission.acquire(true, 1, 1));
        admission.release(true, 1);
        assertEquals(0, admission.active());
    }

    @Test void shrinkingCapacityLetsExistingWorkDrain() {
        var admission = new RenderAdmission();
        assertEquals(4, admission.acquire(true, 4, 4));
        assertEquals(0, admission.acquire(false, 1, 1));
        admission.release(true, 4);
        assertEquals(1, admission.acquire(false, 1, 1));
        admission.release(false, 1);
        assertEquals(0, admission.active());
    }

    @Test void neverExceedsGlobalLimitAcrossConcurrentWorldsAndUpdates() throws Exception {
        var admission = new RenderAdmission();
        AtomicInteger maximum = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> jobs = new ArrayList<>();
            for (int job = 0; job < 8; job++) {
                boolean full = job % 2 == 0;
                jobs.add(executor.submit(() -> {
                    for (int attempt = 0; attempt < 2000; attempt++) {
                        int granted = admission.acquire(full, full ? 3 : 1, 4);
                        if (granted > 0) {
                            try { maximum.accumulateAndGet(admission.active(), Math::max); }
                            finally { admission.release(full, granted); }
                        }
                    }
                }));
            }
            for (Future<?> job : jobs) job.get(10, TimeUnit.SECONDS);
        }
        assertTrue(maximum.get() <= 4);
        assertEquals(0, admission.active());
    }
}
