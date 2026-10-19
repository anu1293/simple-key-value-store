package com.store.test;

import com.store.api.KeyValueStore;
import com.store.core.KeyValueStoreBuilder;
import com.store.eviction.EvictionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Self-contained verification and test suite for KeyValueStore (no external dependencies required).
 */
public class KeyValueStoreTestSuite {

    @FunctionalInterface
    interface TestRunnable {
        void run() throws Throwable;
    }

    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("       RUNNING KEY-VALUE STORE TEST SUITE        ");
        System.out.println("=================================================");

        testBasicOperations();
        testLRUEviction();
        testLFUEviction();
        testPassiveTtlExpiration();
        testActiveTtlCleanup();
        testConcurrentAccess();
        testStatsTracking();

        System.out.println("=================================================");
        System.out.printf("Test Results: %d Passed, %d Failed%n", testsPassed, testsFailed);
        System.out.println("=================================================");

        if (testsFailed > 0) {
            System.exit(1);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(String.format("%s -> Expected: %s, Actual: %s", message, expected, actual));
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void runTest(String testName, TestRunnable testRunnable) {
        try {
            testRunnable.run();
            testsPassed++;
            System.out.printf("  [PASS] %s%n", testName);
        } catch (Throwable t) {
            testsFailed++;
            System.out.printf("  [FAIL] %s - Reason: %s%n", testName, t.getMessage());
            t.printStackTrace(System.err);
        }
    }

    private static void testBasicOperations() {
        runTest("testBasicOperations", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(10)
                    .build()) {

                store.put("k1", "v1");
                store.put("k2", "v2");

                assertEquals("v1", store.get("k1").orElse(null), "Get k1 should return v1");
                assertEquals("v2", store.get("k2").orElse(null), "Get k2 should return v2");
                assertEquals(Optional.empty(), store.get("k3"), "Get k3 should be empty");

                assertTrue(store.containsKey("k1"), "Store should contain k1");
                assertEquals(2, store.size(), "Store size should be 2");

                assertTrue(store.remove("k1"), "Remove k1 should return true");
                assertEquals(1, store.size(), "Store size should be 1 after remove");
                assertTrue(!store.containsKey("k1"), "Store should not contain k1");

                store.clear();
                assertEquals(0, store.size(), "Store should be empty after clear");
            }
        });
    }

    private static void testLRUEviction() {
        runTest("testLRUEviction", () -> {
            // Capacity 3: Insert A, B, C. Access A. Insert D.
            // Expect B (least recently used) to be evicted.
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(3)
                    .evictionPolicy(EvictionPolicy.LRU)
                    .build()) {

                store.put("A", "1");
                store.put("B", "2");
                store.put("C", "3");

                // Access A (making order of recent usage: A, then C, then B is least recent)
                store.get("A");

                // Insert D -> should evict B
                store.put("D", "4");

                assertTrue(store.containsKey("A"), "A should be kept");
                assertTrue(!store.containsKey("B"), "B should have been evicted by LRU");
                assertTrue(store.containsKey("C"), "C should be kept");
                assertTrue(store.containsKey("D"), "D should be kept");
                assertEquals(3, store.size(), "Size must remain at capacity 3");
                assertEquals(1L, store.getStats().getEvictions(), "Eviction count must be 1");
            }
        });
    }

    private static void testLFUEviction() {
        runTest("testLFUEviction", () -> {
            // Capacity 3: Insert A, B, C.
            // Access A 3 times, B 2 times, C 1 time.
            // Insert D -> C has lowest frequency, so C should be evicted.
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(3)
                    .evictionPolicy(EvictionPolicy.LFU)
                    .build()) {

                store.put("A", "1");
                store.put("B", "2");
                store.put("C", "3");

                // Frequency of A: 1 (initial) + 2 = 3
                store.get("A");
                store.get("A");

                // Frequency of B: 1 (initial) + 1 = 2
                store.get("B");

                // Frequency of C: 1 (initial)

                // Insert D -> Should evict C (freq 1)
                store.put("D", "4");

                assertTrue(store.containsKey("A"), "A should be kept (freq 3)");
                assertTrue(store.containsKey("B"), "B should be kept (freq 2)");
                assertTrue(!store.containsKey("C"), "C should have been evicted by LFU (freq 1)");
                assertTrue(store.containsKey("D"), "D should be kept");
                assertEquals(3, store.size(), "Size must remain at capacity 3");
            }
        });
    }

    private static void testPassiveTtlExpiration() {
        runTest("testPassiveTtlExpiration", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(5)
                    .disableActiveExpiration()
                    .build()) {

                store.put("tempKey", "tempVal", 80); // 80ms TTL
                store.put("permKey", "permVal", -1); // No TTL

                assertTrue(store.containsKey("tempKey"), "tempKey should be present immediately");

                // Sleep past expiration
                Thread.sleep(150);

                assertEquals(Optional.empty(), store.get("tempKey"), "tempKey should expire and return empty");
                assertTrue(!store.containsKey("tempKey"), "containsKey should return false for expired key");
                assertEquals("permVal", store.get("permKey").orElse(null), "permKey should still be present");
                assertTrue(store.getStats().getExpirations() >= 1, "Expirations should be recorded");
            }
        });
    }

    private static void testActiveTtlCleanup() {
        runTest("testActiveTtlCleanup", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(10)
                    .enableActiveExpiration(50) // Runs every 50ms
                    .build()) {

                store.put("short1", "val1", 50);
                store.put("short2", "val2", 50);

                // Wait for background thread to clean it up
                Thread.sleep(250);

                assertEquals(0, store.size(), "Store size should be 0 after active background cleanup");
                assertTrue(store.getStats().getExpirations() >= 2, "Active expirations should be recorded");
            }
        });
    }

    private static void testConcurrentAccess() {
        runTest("testConcurrentAccess", () -> {
            int threadCount = 8;
            int opsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try (KeyValueStore<String, Integer> store = KeyValueStoreBuilder.<String, Integer>create()
                    .capacity(200)
                    .evictionPolicy(EvictionPolicy.LRU)
                    .build()) {

                List<Callable<Void>> tasks = new ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    final int threadId = t;
                    tasks.add(() -> {
                        for (int i = 0; i < opsPerThread; i++) {
                            String key = "key-" + (i % 50);
                            store.put(key, threadId * 1000 + i);
                            store.get(key);
                            if (i % 10 == 0) {
                                store.containsKey(key);
                            }
                        }
                        return null;
                    });
                }

                List<Future<Void>> futures = executor.invokeAll(tasks);
                for (Future<Void> f : futures) {
                    f.get(5, TimeUnit.SECONDS); // Check for any exceptions
                }

                assertTrue(store.size() <= 200, "Store size must not exceed capacity");
                assertTrue(store.getStats().getPuts() > 0, "Puts should be tracked");
                assertTrue(store.getStats().getHits() > 0, "Hits should be tracked");
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private static void testStatsTracking() {
        runTest("testStatsTracking", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(10)
                    .build()) {

                store.put("k1", "v1");
                store.get("k1"); // Hit 1
                store.get("k1"); // Hit 2
                store.get("unknown"); // Miss 1

                assertEquals(2L, store.getStats().getHits(), "Hits should be 2");
                assertEquals(1L, store.getStats().getMisses(), "Misses should be 1");
                assertEquals(1L, store.getStats().getPuts(), "Puts should be 1");
                assertEquals(66.666, store.getStats().getHitRate(), "Hit rate should be ~66.67%");
            }
        });
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.1) {
            throw new AssertionError(String.format("%s -> Expected: %f, Actual: %f", message, expected, actual));
        }
    }
}