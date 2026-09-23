# Live Football Scoreboard Library

Lightweight, thread-safe, in-memory Java 21 library for tracking live football scores. Zero production dependencies.

---

## Public API & Design

The library exposes a clean interface `Scoreboard` and an immutable value object `Match`.

* **`Scoreboard` (Interface):** Defines the API contract (`startMatch`, `updateScore`, `finishMatch`, `summary`). Kept separate from the concrete implementation (`InMemoryScoreboard`) to decouple the caller from internal implementation details and simplify mocking in unit tests.
* **`InMemoryScoreboard` (Implementation):** In-memory state management guarded by a `ReentrantReadWriteLock`.
* **`Match` (Record):** Immutable snapshot of a match at a specific point in time.

---

## Assumptions

1. **Match Identity:** Every match gets a unique `UUID` upon creation. The same pair of teams can play against each other multiple times; each game is tracked independently by its `UUID`.
2. **Active State:** A match is considered active **solely by its presence in memory** (`HashMap`). There is no redundant `isActive` flag. Calling `finishMatch` removes the match from the store permanently (no memory leaks from finished matches).
3. **Start Ordering (Recency):** Recency is tracked via an internal monotonic `long sequence` incremented on `startMatch`. System clocks (`Instant.now()`) were intentionally avoided to eliminate issues with clock drift, sub-millisecond ties, or NTP adjustments.
4. **Score Updates:** Scores represent total current values (not incremental deltas) and cannot be negative. Updating a score returns a new `Match` instance while preserving the original `UUID`, team names, and initial `sequence`.

---

## Engineering Decisions

### 1. Lock-Free Sorting Strategy
`InMemoryScoreboard` uses a `ReentrantReadWriteLock` to balance safety and performance:
* **Mutations (`startMatch`, `updateScore`, `finishMatch`):** Executed under `writeLock` to guarantee atomic state updates and gapless sequence generation.
* **Reads (`summary`):** Under `readLock`, a defensive copy of the active matches is extracted into a list. The `readLock` is released **immediately** after copying. Sorting ($O(N \log N)$) and wrapping into `List.copyOf` happen outside the lock. This keeps sorting from blocking incoming write operations.

### 2. Defensive Integer Overflow Protection
Sorting matches by total score (`homeScore + awayScore`) descending can wrap around to negative numbers if `int` values are large (e.g., `Integer.MAX_VALUE`). The comparator explicitly converts scores to `long` before addition:

```java
private static final Comparator<Match> SUMMARY_ORDER =
        Comparator.comparingLong((Match match) -> (long) match.homeScore() + match.awayScore())
                  .thenComparingLong(Match::sequence)
                  .reversed();
```
This ensures mathematical correctness across the entire valid int range without throwing arithmetic exceptions or corrupting sort order
### 3. Fail-Fast Validation
All operations validate inputs early with explicit exceptions:
1. `NullPointerException` — if `matchId` or any required reference is `null`.
2. `IllegalArgumentException` — for invalid inputs (blank team names, identical home/away teams, negative scores).
3. `IllegalStateException` — when attempting to update or finish a match ID that does not exist in the active map.

In `updateScore`, basic argument checks (like negative scores) run **before** acquiring the write lock, failing fast without synchronization overhead.

---

## Trade-offs

| Aspect | Choice Made | Rationale & Alternative Considered |
| :--- | :--- | :--- |
| **Concurrency** | `ReentrantReadWriteLock` + `HashMap` | *Alternative: `ConcurrentHashMap`.* CHM alone cannot atomically update multiple state variables (e.g. generating a unique `sequence` + putting the match). An explicit write lock ensures compound operations remain strictly atomic. |
| **Ordering State** | Monotonic `sequence` counter | *Alternative: `Instant.now()` timestamps.* Timestamps add clock synchronization risks and potential duplicate values under high concurrency. A `long` sequence is simple, deterministic, and free of system clock dependencies. |
| **Dependencies** | Pure Java 21 Standard Library | *Alternative: Lombok, SLF4J, Guava.* Zero external production dependencies means zero transitive dependency conflicts or CVE vulnerabilities for library consumers. |


---

## Quickstart

```java
import com.andrewsavich.scoreboard.InMemoryScoreboard;
import com.andrewsavich.scoreboard.Match;
import com.andrewsavich.scoreboard.Scoreboard;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Scoreboard scoreboard = new InMemoryScoreboard();

        // Start matches
        Match mexicoCanada = scoreboard.startMatch("Mexico", "Canada");
        Match spainBrazil = scoreboard.startMatch("Spain", "Brazil");
        Match germanyFrance = scoreboard.startMatch("Germany", "France");

        // Update scores
        scoreboard.updateScore(mexicoCanada.id(), 0, 5);
        scoreboard.updateScore(spainBrazil.id(), 10, 2);
        scoreboard.updateScore(germanyFrance.id(), 2, 2);

        // Get live summary (Ordered by total score DESC, recency DESC)
        List<Match> summary = scoreboard.summary();
        summary.forEach(m -> System.out.printf("%s %d - %d %s%n", 
            m.homeTeam(), m.homeScore(), m.awayScore(), m.awayTeam()));

        // Finish match
        scoreboard.finishMatch(germanyFrance.id());
    }
}

```
## Build & Test

```bash
# Run unit & concurrency stress tests (16 threads)
mvn clean verify

# Check Javadoc build
mvn javadoc:javadoc
```
