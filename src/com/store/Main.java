package com.store;

import com.store.api.KeyValueStore;
import com.store.core.KeyValueStoreBuilder;
import com.store.eviction.EvictionPolicy;

import java.util.Optional;
import java.util.Scanner;

/**
 * Interactive REPL Console and Demonstration Runner for the Key-Value Store.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && "--demo".equalsIgnoreCase(args[0])) {
            runAutomatedDemonstrations();
            return;
        }

        System.out.println("==================================================================");
        System.out.println("     WELCOME TO THE IN-MEMORY KEY-VALUE STORE WITH TTL & EVICTION ");
        System.out.println("==================================================================");
        System.out.println("Type 'help' to see available commands, or 'demo' to run an automated demo.");
        System.out.println("Default Store initialized with Capacity=5, Policy=LRU, Active TTL Cleanup=ON.");
        System.out.println();

        try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                .capacity(5)
                .evictionPolicy(EvictionPolicy.LRU)
                .enableActiveExpiration(1000)
                .build();
             Scanner scanner = new Scanner(System.in)) {

            while (true) {
                System.out.print("kv-store> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                String command = parts[0].toUpperCase();

                if ("EXIT".equals(command) || "QUIT".equals(command)) {
                    System.out.println("Exiting Key-Value Store. Goodbye!");
                    break;
                }

                switch (command) {
                    case "HELP":
                        printHelp();
                        break;

                    case "PUT":
                        if (parts.length < 3) {
                            System.out.println("Usage: PUT <key> <value> [ttlMillis]");
                        } else {
                            String key = parts[1];
                            String val = parts[2];
                            if (parts.length >= 4) {
                                try {
                                    long ttl = Long.parseLong(parts[3]);
                                    store.put(key, val, ttl);
                                    System.out.printf("OK: Stored [%s -> %s] with TTL %d ms%n", key, val, ttl);
                                } catch (NumberFormatException e) {
                                    System.out.println("Error: TTL must be a valid integer in milliseconds.");
                                }
                            } else {
                                store.put(key, val);
                                System.out.printf("OK: Stored [%s -> %s]%n", key, val);
                            }
                        }
                        break;

                    case "GET":
                        if (parts.length < 2) {
                            System.out.println("Usage: GET <key>");
                        } else {
                            Optional<String> val = store.get(parts[1]);
                            if (val.isPresent()) {
                                System.out.println(val.get());
                            } else {
                                System.out.println("(nil)");
                            }
                        }
                        break;

                    case "DEL":
                    case "DELETE":
                        if (parts.length < 2) {
                            System.out.println("Usage: DEL <key>");
                        } else {
                            boolean removed = store.remove(parts[1]);
                            System.out.println(removed ? "OK: Key deleted" : "(nil) Key not found");
                        }
                        break;

                    case "EXISTS":
                        if (parts.length < 2) {
                            System.out.println("Usage: EXISTS <key>");
                        } else {
                            boolean exists = store.containsKey(parts[1]);
                            System.out.println(exists ? "1 (true)" : "0 (false)");
                        }
                        break;

                    case "KEYS":
                        System.out.println("Active Keys: " + store.keySet());
                        break;

                    case "SIZE":
                        System.out.println("Current size: " + store.size());
                        break;

                    case "STATS":
                        System.out.println(store.getStats());
                        break;

                    case "CLEAR":
                        store.clear();
                        System.out.println("OK: Store cleared.");
                        break;

                    case "DEMO":
                        runAutomatedDemonstrations();
                        break;

                    default:
                        System.out.printf("Unknown command '%s'. Type 'help' for usage.%n", command);
                        break;
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("Available Commands:");
        System.out.println("  PUT <key> <value> [ttlMillis] - Insert or update a key with optional TTL (ms)");
        System.out.println("  GET <key>                     - Retrieve the value for a key");
        System.out.println("  DEL <key>                     - Delete a key from the store");
        System.out.println("  EXISTS <key>                  - Check if key exists and is not expired");
        System.out.println("  KEYS                          - List all active non-expired keys");
        System.out.println("  SIZE                          - Show the number of active entries");
        System.out.println("  STATS                         - Show cache hits, misses, hit rate, evictions");
        System.out.println("  CLEAR                         - Flush all keys from the store");
        System.out.println("  DEMO                          - Run an automated live showcase of features");
        System.out.println("  EXIT / QUIT                   - Exit the console");
    }

    public static void runAutomatedDemonstrations() {
        System.out.println("\n========================================================");
        System.out.println("        STARTING AUTOMATED FEATURE DEMONSTRATION        ");
        System.out.println("========================================================");

        // Demo 1: Basic Operations
        System.out.println("\n[DEMO 1] Basic CRUD Operations:");
        try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create().capacity(10).build()) {
            store.put("user:101", "Alice");
            store.put("user:102", "Bob");
            System.out.println("  -> Stored user:101=Alice, user:102=Bob");
            System.out.println("  -> GET user:101: " + store.get("user:101").orElse("(nil)"));
            System.out.println("  -> GET user:999: " + store.get("user:999").orElse("(nil)"));
            System.out.println("  -> Active keys: " + store.keySet());
            store.remove("user:102");
            System.out.println("  -> Removed user:102, active keys: " + store.keySet());
        }

        // Demo 2: TTL Expiration
        System.out.println("\n[DEMO 2] Time-To-Live (TTL) Expiration:");
        try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                .capacity(10)
                .enableActiveExpiration(100)
                .build()) {
            System.out.println("  -> Inserting 'sessionToken' with 500ms TTL and 'permConfig' with no TTL...");
            store.put("sessionToken", "secretXYZ", 500);
            store.put("permConfig", "theme=dark", -1);

            System.out.println("  -> Immediate GET sessionToken: " + store.get("sessionToken").orElse("(expired)"));
            System.out.println("  -> Sleeping 600ms to allow expiration...");
            try {
                Thread.sleep(600);
            } catch (InterruptedException ignored) {}

            System.out.println("  -> GET sessionToken after 600ms: " + store.get("sessionToken").orElse("(nil - EXPIRED)"));
            System.out.println("  -> GET permConfig after 600ms:   " + store.get("permConfig").orElse("(nil - EXPIRED)"));
            System.out.println("  -> " + store.getStats());
        }

        // Demo 3: LRU Eviction
        System.out.println("\n[DEMO 3] LRU (Least Recently Used) Eviction:");
        try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                .capacity(3)
                .evictionPolicy(EvictionPolicy.LRU)
                .build()) {
            System.out.println("  -> Initialized store with Capacity=3, Policy=LRU");
            store.put("K1", "V1");
            store.put("K2", "V2");
            store.put("K3", "V3");
            System.out.println("  -> Inserted K1, K2, K3. Keys: " + store.keySet());

            System.out.println("  -> Reading K1 to mark it as Recently Used...");
            store.get("K1");

            System.out.println("  -> Inserting K4 (exceeds capacity of 3)...");
            store.put("K4", "V4");

            System.out.println("  -> Resulting keys in store: " + store.keySet());
            System.out.println("  -> Notice K2 was evicted because it was the Least Recently Used!");
            System.out.println("  -> " + store.getStats());
        }

        // Demo 4: LFU Eviction
        System.out.println("\n[DEMO 4] LFU (Least Frequently Used) Eviction:");
        try (KeyValueStore<String, String> store = KeyValueStoreBuilder.<String, String>create()
                .capacity(3)
                .evictionPolicy(EvictionPolicy.LFU)
                .build()) {
            System.out.println("  -> Initialized store with Capacity=3, Policy=LFU");
            store.put("A", "Apple");
            store.put("B", "Banana");
            store.put("C", "Cherry");

            System.out.println("  -> Accessing 'A' 3 times, 'B' 2 times, 'C' 0 times...");
            store.get("A"); store.get("A"); store.get("A");
            store.get("B"); store.get("B");

            System.out.println("  -> Inserting 'D' (Dragonfruit), exceeding capacity...");
            store.put("D", "Dragonfruit");

            System.out.println("  -> Resulting keys in store: " + store.keySet());
            System.out.println("  -> Notice 'C' was evicted because its access frequency was the lowest!");
            System.out.println("  -> " + store.getStats());
        }

        System.out.println("\n========================================================");
        System.out.println("              DEMONSTRATION COMPLETED                   ");
        System.out.println("========================================================\n");
    }
}
