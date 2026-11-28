package com.kosh.ledger.concurrency;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import com.kosh.ledger.service.InsufficientFundsException;

/** Runs the same action many times on many threads, all released at the same moment. */
final class ConcurrencyHarness {

    /** What happened: how many worked, how many were refused for lack of money, anything else. */
    record Outcome(int succeeded, int insufficientFunds, List<Throwable> unexpected, long millis) {
    }

    private ConcurrencyHarness() {
    }

    static Outcome run(int times, int threads, IntConsumer action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < times; i++) {
            int n = i;
            pool.submit(() -> {
                try {
                    startGate.await();            // every thread waits here...
                    action.accept(n);
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficient.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }

        long start = System.nanoTime();
        startGate.countDown();                    // ...until this opens the gate for all of them
        pool.shutdown();
        if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IllegalStateException("Tasks did not finish within 2 minutes");
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return new Outcome(succeeded.get(), insufficient.get(), List.copyOf(unexpected), millis);
    }
}
