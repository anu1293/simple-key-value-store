# Simple Key-Value Store (In-Memory Cache)

A high-performance, thread-safe, in-memory generic key-value store implemented in Java with support for configurable Time-To-Live (TTL) expiration, pluggable eviction policies (LRU, LFU, FIFO), active background cleanup, and comprehensive metrics tracking.

---

## Features

- **Generic & Extensible (`KeyValueStore<K, V>`)**: Strongly typed key-value pairs with clean interface abstractions.
- **Pluggable Eviction Strategies**:
  - **LRU (Least Recently Used)**: Implemented in $O(1)$ time complexity using a custom doubly-linked list with sentinel nodes and hash map.
  - **LFU (Least Frequently Used)**: Implemented in $O(1)$ time complexity using frequency buckets and doubly-linked lists. Ties are resolved in LRU order.
  - **FIFO (First-In-First-Out)**: Evicts oldest inserted items.
- **Flexible Expiration (TTL)**:
  - Per-key TTL or store-wide default TTL.
  - **Passive (Lazy) Expiration**: Expired keys are intercepted and evicted upon access (`get`, `containsKey`).
  - **Active (Proactive) Expiration**: Configurable background daemon scheduler periodically sweeps and removes expired keys to prevent memory leaks.
- **Thread Safety**: Fully thread-safe operations guarded by reentrant synchronization locks.
- **Metrics & Observability**: Real-time tracking of puts, cache hits, cache misses, hit rate percentage, evictions, and expirations via `StoreStats`.
- **Interactive CLI & REPL**: Built-in interactive console for manual operations and automated demos.
- **Zero External Dependencies**: Pure Java SE standard library (compatible with Java 8+, 17, and 21+).

---

## Project Structure

```
simple-key-value-store/
├── .gitignore
├── README.md
├── simple-key-value-store.iml
├── .idea/
│   ├── misc.xml
│   └── modules.xml
└── src/
    └── com/
        └── store/
            ├── Main.java                          # Interactive CLI REPL & Demo runner
            ├── api/
            │   ├── KeyValueStore.java             # Core Key-Value Store interface
            │   └── StoreStats.java                # Runtime metrics tracker
            ├── core/
            │   ├── CacheEntry.java                # Value container with timestamp & TTL
            │   ├── InMemoryKeyValueStore.java     # Thread-safe store implementation
            │   └── KeyValueStoreBuilder.java      # Fluent builder
            ├── eviction/
            │   ├── EvictionPolicy.java            # Policy enum (LRU, LFU, FIFO)
            │   ├── EvictionStrategy.java          # Strategy interface
            │   ├── LRUEvictionStrategy.java       # O(1) LRU implementation
            │   ├── LFUEvictionStrategy.java       # O(1) LFU implementation
            │   └── FIFOEvictionStrategy.java      # FIFO implementation
            ├── exception/
            │   ├── KeyValueStoreException.java    # Base exception
            │   └── CapacityExceededException.java # Eviction/capacity failure exception
            └── test/
                └── KeyValueStoreTestSuite.java    # Standalone test runner
```

---

## Code Example

```java
import com.store.api.KeyValueStore;
import com.store.core.KeyValueStoreBuilder;
import com.store.eviction.EvictionPolicy;

public class Example {
    public static void main(String[] args) {
        // Create store with Capacity=3, LRU policy, and 1000ms active TTL cleanup
        try (KeyValueStore<String, String> cache = KeyValueStoreBuilder.<String, String>create()
                .capacity(3)
                .evictionPolicy(EvictionPolicy.LRU)
                .enableActiveExpiration(1000)
                .build()) {

            // Put with TTL (expires in 500ms)
            cache.put("session_token", "abc123xyz", 500);

            // Put without TTL (persists until evicted)
            cache.put("user:1", "John Doe");

            // Retrieve value
            System.out.println(cache.get("user:1").orElse("Not Found"));

            // Inspect metrics
            System.out.println(cache.getStats());
        }
    }
}
```

---

## How to Build and Run

### 1. Compile Sources

From the project root:
```powershell
javac -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
```

### 2. Run Test Suite

Run the built-in test suite (covers basic CRUD, LRU, LFU, TTL expiration, concurrency, and statistics):
```powershell
java -cp out com.store.test.KeyValueStoreTestSuite
```

### 3. Run Automated Feature Demo

```powershell
java -cp out com.store.Main --demo
```

### 4. Run Interactive REPL Console

```powershell
java -cp out com.store.Main
```

Inside the REPL:
```text
kv-store> PUT user:1 Alice 5000
OK: Stored [user:1 -> Alice] with TTL 5000 ms

kv-store> GET user:1
Alice

kv-store> STATS
StoreStats [Puts: 1 | Hits: 1 | Misses: 0 | Total Reads: 1 | Hit Rate: 100.00% | Evictions: 0 | Expirations: 0]

kv-store> HELP
Available Commands:
  PUT <key> <value> [ttlMillis] - Insert or update a key with optional TTL (ms)
  GET <key>                     - Retrieve the value for a key
  DEL <key>                     - Delete a key from the store
  EXISTS <key>                  - Check if key exists and is not expired
  KEYS                          - List all active non-expired keys
  SIZE                          - Show the number of active entries
  STATS                         - Show cache hits, misses, hit rate, evictions
  CLEAR                         - Flush all keys from the store
  DEMO                          - Run an automated live showcase of features
  EXIT / QUIT                   - Exit the console
```

---

## Opening in IntelliJ IDEA

1. Open IntelliJ IDEA.
2. Click **File -> Open...** and select `A:\intellij-workspace\simple-key-value-store`.
3. IntelliJ IDEA will recognize the project configuration (`.iml` and `.idea`), and you can immediately run `Main.java` or `KeyValueStoreTestSuite.java`.