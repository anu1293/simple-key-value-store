package com.store.test;

import com.store.api.KeyValueStore;
import com.store.api.VersionedValue;
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

        // New OCC / CAS and Versioning tests
        testVersionIncrementOnPut();
        testPutIfVersion();
        testCompareAndSet();
        testPutIfAbsent();
        testRemoveIfVersion();
        testConcurrentOptimisticConcurrency();

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

    private static void assertDoubleEquals(double expected, double actual, String message) {
        if (Math.abs(expected - actual) > 0.1) {
            throw new AssertionError(String.format("%s -> Expected: %f, Actual: %f", message, expected, actual));
        }
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
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(3)
                    .evictionPolicy(EvictionPolicy.LRU)
                    .build()) {

                store.put("A", "1");
                store.put("B", "2");
                store.put("C", "3");

                store.get("A"); // A accessed: order becomes B, C, A

                // Insert D -> B is LRU, should be evicted
                store.put("D", "4");

                assertTrue(!store.containsKey("B"), "B should have been evicted by LRU");
                assertTrue(store.containsKey("A"), "A should still be present");
                assertTrue(store.containsKey("C"), "C should still be present");
                assertTrue(store.containsKey("D"), "D should still be present");
                assertEquals(3, store.size(), "Store size should remain at capacity 3");
            }
        });
    }

    private static void testLFUEviction() {
        runTest("testLFUEviction", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                    .capacity(3)
                    .evictionPolicy(EvictionPolicy.LFU)
                    .build()) {

                store.put("A", "1"); // freq 1
                store.put("B", "2"); // freq 1
                store.put("C", "3"); // freq 1

                store.get("A"); // freq 2
                store.get("A"); // freq 3
                store.get("B"); // freq 2

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
                assertDoubleEquals(66.666, store.getStats().getHitRate(), "Hit rate should be ~66.67%");
            }
        });
    }

    private static void testVersionIncrementOnPut() {
        runTest("testVersionIncrementOnPut", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().build()) {
                store.put("item", "v1");
                Optional<VersionedValue<String>> snap1 = store.getVersioned("item");
                assertTrue(snap1.isPresent(), "Snapshot 1 should be present");
                assertEquals("v1", snap1.get().getValue(), "Value should be v1");
                assertEquals(1L, snap1.get().getVersion(), "Initial version should be 1");

                store.put("item", "v2");
                Optional<VersionedValue<String>> snap2 = store.getVersioned("item");
                assertTrue(snap2.isPresent(), "Snapshot 2 should be present");
                assertEquals("v2", snap2.get().getValue(), "Value should be v2");
                assertEquals(2L, snap2.get().getVersion(), "Second version should be 2");

                store.put("item", "v3");
                Optional<VersionedValue<String>> snap3 = store.getVersioned("item");
                assertTrue(snap3.isPresent(), "Snapshot 3 should be present");
                assertEquals("v3", snap3.get().getValue(), "Value should be v3");
                assertEquals(3L, snap3.get().getVersion(), "Third version should be 3");
            }
        });
    }

    private static void testPutIfVersion() {
        runTest("testPutIfVersion", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().build()) {
                store.put("order:1", "PENDING");
                assertEquals(1L, store.getVersioned("order:1").get().getVersion(), "Version should be 1");

                // Stale update attempt (expecting version 99)
                boolean failed = store.putIfVersion("order:1", "SHIPPED", 99L);
                assertTrue(!failed, "putIfVersion with wrong version should fail");
                assertEquals("PENDING", store.get("order:1").get(), "Value should not change on failed version update");
                assertEquals(1L, store.getVersioned("order:1").get().getVersion(), "Version should stay at 1");

                // Valid update attempt (expecting version 1)
                boolean success = store.putIfVersion("order:1", "CONFIRMED", 1L);
                assertTrue(success, "putIfVersion with matching version should succeed");
                assertEquals("CONFIRMED", store.get("order:1").get(), "Value should be updated to CONFIRMED");
                assertEquals(2L, store.getVersioned("order:1").get().getVersion(), "Version should be bumped to 2");

                // Updating non-existent key
                boolean nonExistent = store.putIfVersion("order:999", "NONE", 1L);
                assertTrue(!nonExistent, "putIfVersion on non-existent key should fail");
            }
        });
    }

    private static void testCompareAndSet() {
        runTest("testCompareAndSet", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().build()) {
                store.put("state", "IDLE");

                // Mismatch CAS
                boolean casFail = store.compareAndSet("state", "BUSY", "RUNNING");
                assertTrue(!casFail, "CAS with mismatched expected value should return false");
                assertEquals("IDLE", store.get("state").get(), "Value should remain IDLE");

                // Match CAS
                boolean casSuccess = store.compareAndSet("state", "IDLE", "RUNNING");
                assertTrue(casSuccess, "CAS with matching expected value should return true");
                assertEquals("RUNNING", store.get("state").get(), "Value should update to RUNNING");
                assertEquals(2L, store.getVersioned("state").get().getVersion(), "Version should increment on CAS");

                // CAS on non-existent key
                boolean casMissing = store.compareAndSet("unknown", "X", "Y");
                assertTrue(!casMissing, "CAS on non-existent key should return false");
            }
        });
    }

    private static void testPutIfAbsent() {
        runTest("testPutIfAbsent", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().build()) {
                boolean first = store.putIfAbsent("singleton", "first");
                assertTrue(first, "putIfAbsent on new key should return true");
                assertEquals("first", store.get("singleton").get(), "Value should be 'first'");
                assertEquals(1L, store.getVersioned("singleton").get().getVersion(), "Version should be 1");

                boolean second = store.putIfAbsent("singleton", "second");
                assertTrue(!second, "putIfAbsent on existing key should return false");
                assertEquals("first", store.get("singleton").get(), "Value should remain 'first'");
            }
        });
    }

    private static void testRemoveIfVersion() {
        runTest("testRemoveIfVersion", () -> {
            try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().build()) {
                store.put("mutex", "token123");
                assertEquals(1L, store.getVersioned("mutex").get().getVersion(), "Version should be 1");

                // Stale remove
                boolean failRemove = store.removeIfVersion("mutex", 5L);
                assertTrue(!failRemove, "removeIfVersion with stale version should return false");
                assertTrue(store.containsKey("mutex"), "mutex should still exist");

                // Correct remove
                boolean okRemove = store.removeIfVersion("mutex", 1L);
                assertTrue(okRemove, "removeIfVersion with correct version should return true");
                assertTrue(!store.containsKey("mutex"), "mutex should be removed");
            }
        });
    }

    private static void testConcurrentOptimisticConcurrency() {
        runTest("testConcurrentOptimisticConcurrency", () -> {
            int threadCount = 10;
            int incrementsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try (KeyValueStore<String, Integer> store = KeyValueStoreBuilder.<String, Integer>create()
                    .capacity(100)
                    .build()) {

                store.put("counter", 0);

                List<Callable<Void>> tasks = new ArrayList<>();
                for (int t = 0; t < threadCount; t++) {
                    tasks.add(() -> {
                        for (int i = 0; i < incrementsPerThread; i++) {
                            boolean updated = false;
                            while (!updated) {
                                VersionedValue<Integer> current = store.getVersioned("counter")
                                        .orElseThrow(() -> new IllegalStateException("Counter not found"));
                                int nextVal = current.getValue() + 1;
                                updated = store.putIfVersion("counter", nextVal, current.getVersion());
                            }
                        }
                        return null;
                    });
                }

                List<Future<Void>> futures = executor.invokeAll(tasks);
                for (Future<Void> f : futures) {
                    f.get(10, TimeUnit.SECONDS);
                }

                int expectedTotal = threadCount * incrementsPerThread;
                assertEquals(Integer.valueOf(expectedTotal), store.get("counter").orElse(-1), "Final counter must equal total increments");
                assertEquals((long) expectedTotal + 1, store.getVersioned("counter").get().getVersion(), "Version should equal total increments + 1");
                assertTrue(store.getStats().getCasSuccesses() == expectedTotal, "CAS successes must equal expected total");
                assertTrue(store.getStats().getCasFailures() > 0, "Concurrent contention must produce retried CAS failures");
            } finally {
                executor.shutdownNow();
            }
        });
    }
}
