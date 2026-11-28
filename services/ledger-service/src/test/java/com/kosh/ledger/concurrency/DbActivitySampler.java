package com.kosh.ledger.concurrency;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.zaxxer.hikari.HikariDataSource;

/**
 * While a benchmark runs, asks Postgres every 20 ms what each of our connections is doing
 * (pg_stat_activity) and how many locks are being waited for (pg_locks), then averages it.
 * Uses its OWN connection, so it never queues behind the 10 in the pool.
 */
final class DbActivitySampler implements AutoCloseable {

    /** Averages over the whole run: of our connections, how many were in each state. */
    record Averages(double running, double waitingOnLock, double idleInTransaction, int samples) {
        @Override
        public String toString() {
            return String.format("%.1f running, %.1f waiting on a lock, %.1f idle in transaction",
                    running, waitingOnLock, idleInTransaction);
        }
    }

    private static final String SQL = """
            SELECT
              count(*) FILTER (WHERE state = 'active' AND wait_event_type IS DISTINCT FROM 'Lock') AS running,
              count(*) FILTER (WHERE wait_event_type = 'Lock')                                     AS waiting_on_lock,
              count(*) FILTER (WHERE state = 'idle in transaction')                                AS idle_in_tx
            FROM pg_stat_activity
            WHERE datname = current_database()
              AND backend_type = 'client backend'
              AND pid <> pg_backend_pid()
            """;

    private final Connection connection;
    private final Thread thread;
    private volatile boolean running = true;
    // only the sampler thread writes these; close() reads them after join(), which makes them visible
    private long sumRunning, sumWaiting, sumIdleInTx, samples;

    DbActivitySampler(HikariDataSource pool) throws SQLException {
        this.connection = DriverManager.getConnection(pool.getJdbcUrl(), pool.getUsername(), pool.getPassword());
        this.thread = new Thread(this::loop, "db-activity-sampler");
        thread.setDaemon(true);
        thread.start();
    }

    private void loop() {
        try (Statement st = connection.createStatement()) {
            while (running) {
                try (ResultSet rs = st.executeQuery(SQL)) {
                    rs.next();
                    sumRunning += rs.getInt(1);
                    sumWaiting += rs.getInt(2);
                    sumIdleInTx += rs.getInt(3);
                    samples++;
                }
                Thread.sleep(20);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (SQLException e) {
            System.out.println("sampler stopped: " + e.getMessage());
        }
    }

    /** Stops sampling and returns the averages. */
    Averages stop() throws InterruptedException {
        running = false;
        thread.join(2000);
        long n = Math.max(1, samples);
        return new Averages((double) sumRunning / n, (double) sumWaiting / n, (double) sumIdleInTx / n, (int) samples);
    }

    @Override
    public void close() throws Exception {
        running = false;
        thread.join(2000);
        connection.close();
    }
}
