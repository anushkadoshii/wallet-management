package com.kosh.ledger.concurrency;

import java.util.Arrays;
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
    record Outcome(int succeeded, int insufficientFunds, List<Throwable> unexpected, long millis, Latency latency) {

        /** Finished payments (worked or refused) per second of wall-clock time. */
        double perSecond() {
            return (succeeded + insufficientFunds) * 1000.0 / Math.max(1, millis);
        }
    }

    /** How long one payment took, in milliseconds, from the moment a worker picked it up. */
    record Latency(double p50, double p95, double p99, double max) {

        /** Nearest-rank percentile: sort, then take the value p% of the way along. */
        static Latency of(long[] nanos) {
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            return new Latency(pick(sorted, 50), pick(sorted, 95), pick(sorted, 99), sorted[sorted.length - 1] / 1e6);
        }

        private static double pick(long[] sorted, double p) {
            int rank = (int) Math.ceil(p / 100 * sorted.length);   // e.g. p95 of 1,000 -> the 950th value
            return sorted[Math.max(0, rank - 1)] / 1e6;            // nanoseconds -> milliseconds
        }
    }

    private ConcurrencyHarness() {
    }

    static Outcome run(int times, int threads, IntConsumer action) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        long[] latencyNanos = new long[times];   // each task writes only its own slot

        for (int i = 0; i < times; i++) {
            int n = i;
            pool.submit(() -> {
                long begin = 0;
                try {
                    startGate.await();            // every thread waits here...
                    begin = System.nanoTime();
                    action.accept(n);
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    insufficient.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.add(t);
                } finally {
                    latencyNanos[n] = begin == 0 ? 0 : System.nanoTime() - begin;
                }
            });
        }

        long start = System.nanoTime();
        startGate.countDown();                    // ...until this opens the gate for all of them
        pool.shutdown();
        if (!pool.awaitTermination(5, TimeUnit.MINUTES)) {
            pool.shutdownNow();
            throw new IllegalStateException("Tasks did not finish within 5 minutes");
        }
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        // awaitTermination guarantees every task's writes to latencyNanos are visible here
        return new Outcome(succeeded.get(), insufficient.get(), List.copyOf(unexpected), millis,
                Latency.of(latencyNanos));
    }
}
