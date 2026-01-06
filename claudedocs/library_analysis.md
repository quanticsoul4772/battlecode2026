# Battlecode 2026 - Library Analysis from awesome-java

## Critical Constraint: Battlecode Package Restrictions

**From Specs**:
> "Players may use classes from any of the packages listed in AllowedPackages.txt, except for classes listed in DisallowedPackages.txt"

**Restrictions**:
- `Object.wait`, `Object.notify`, `Object.notifyAll` not allowed
- `Class.forName`, `String.intern` not allowed
- `java.lang.System` only supports: `out`, `arraycopy`, `getProperty`
- `java.io.PrintStream` may not open files
- Most external libraries likely disallowed

**Implication**: We CANNOT use most awesome-java libraries in competition code.

However, we CAN:
1. Use concepts/patterns from libraries
2. Use tools for development/testing (outside game engine)
3. Use allowed standard Java features

---

## Useful Tools (Development/Testing - Not in Game Code)

### 1. Profiling Tools (HIGH VALUE)

**JMH (Java Microbenchmark Harness)**:
- **Use**: Benchmark pathfinding algorithms before competition
- **Value**: Accurate bytecode cost measurement
- **Example**: Test Bug2 vs BFS variants to find cheaper option

**Async Profiler**:
- **Use**: Profile bot during scrimmages
- **Value**: Identify bytecode hotspots without overhead
- **Example**: Find which methods exceed bytecode budget

**How to Use**:
```java
// Benchmark different pathfinding approaches
@Benchmark
public void testBug2Pathfinding() {
    Direction d = Nav.bug2(target);
}

@Benchmark
public void testGreedyPathfinding() {
    Direction d = Nav.greedy(target);
}
// Compare bytecode costs, choose cheaper
```

### 2. Testing Frameworks (MEDIUM VALUE)

**JUnit 5** (Standard):
- **Use**: Unit test bot components
- **Value**: Verify correctness before submission
- **Already planned**: From 2025 experience

**AssertJ** (Fluent Assertions):
- **Use**: More readable test assertions
- **Value**: Better test clarity
- **Example**:
```java
// Instead of: assertEquals(100, babyRat.getHealth());
assertThat(babyRat.getHealth()).isEqualTo(100);
assertThat(king.getGlobalCheese()).isGreaterThan(100);
```

**Mockito** (Mocking):
- **Use**: Mock RobotController for unit tests
- **Value**: Test without full game engine
- **Example**:
```java
RobotController mockRc = mock(RobotController.class);
when(mockRc.getLocation()).thenReturn(new MapLocation(10, 10));
// Test navigation logic without game
```

---

## Concepts to Borrow (No Library Import Needed)

### 1. RoaringBitmap Patterns (BIT MANIPULATION)

**Concept**: Efficient bit-packed data structures
**Battlecode Use**: POI explored tracking (like 2025)

**Already using in 2025**:
```java
// POI.java - 60-bit bitfield for explored tiles
public static long[] explored = new long[60];

// Check if explored
if (((explored[y] >> x) & 1) == 1) { }
```

**2026 Application**:
- Cheese mine discovery tracking
- Cat waypoint prediction
- Trap placement bitfield
- King formation viable locations

**Why not import**: We're already using primitives, library adds overhead

### 2. Disruptor Pattern (LOCK-FREE ALGORITHMS)

**Concept**: Ring buffers, lock-free data structures
**Battlecode Use**: Circular buffers for state tracking

**Application**:
```java
// Track last N cheese collections (lock-free)
private static final int CHEESE_HISTORY_SIZE = 32;
private static int[] cheeseHistory = new int[CHEESE_HISTORY_SIZE];
private static int cheeseHistoryIdx = 0;

void recordCheese(int amount) {
    cheeseHistory[cheeseHistoryIdx++ & 0x1F] = amount; // Modulo via AND
}
```

**Why not import**: Battlecode is single-threaded per robot, but pattern is useful

### 3. Guava Concepts (IMMUTABLE COLLECTIONS)

**Concept**: Immutable data structures prevent bugs
**Battlecode Use**: Static final arrays for constants

**Application**:
```java
// Instead of mutable arrays that can be accidentally modified
public static final Direction[] DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, // ...
};

// Cannot be reassigned or modified
```

**Already using**: This is standard practice from 2025

### 4. Eclipse Collections Patterns (PRIMITIVE SPECIALIZATION)

**Concept**: Avoid boxing overhead (int[] not Integer[])
**Battlecode Use**: Use primitives everywhere

**Application**:
```java
// BAD: Uses Integer objects (boxing cost)
List<Integer> catIds = new ArrayList<>();

// GOOD: Primitive array (no boxing)
int[] catIds = new int[10];
int catCount = 0;
```

**Why not import**: We already use primitives, library not allowed anyway

### 5. Apache Commons Math (ALGORITHM PATTERNS)

**Concept**: Efficient mathematical algorithms
**Battlecode Use**: Borrow algorithms, implement ourselves

**Useful Algorithms**:
- **Fast sqrt approximation**: For distance calculations
- **Binary search**: For sorted lookups
- **Statistical functions**: For probability calculations

**Implementation**:
```java
// Fast integer sqrt (Quake III algorithm variant)
public static int fastSqrt(int x) {
    if (x == 0) return 0;
    int sqrt = x / 2;
    int temp = 0;
    while (sqrt != temp) {
        temp = sqrt;
        sqrt = (x / temp + temp) / 2;
    }
    return sqrt;
}
```

### 6. JGraphT Patterns (GRAPH ALGORITHMS)

**Concept**: Efficient pathfinding algorithms
**Battlecode Use**: BFS for navigation

**Application**: Implement BFS ourselves
```java
// Simple BFS for pathfinding (SPAARK does this)
public static Direction bfsToTarget(MapLocation target) {
    // Queue-based BFS
    // Mark visited
    // Return first step on shortest path
}
```

**Why not import**: Must implement ourselves, library not allowed

---

## Recommended Development Tools (Outside Competition)

### 1. JMH - Bytecode Benchmarking (HIGH PRIORITY)

**Setup**:
```bash
# Add to separate benchmarking project
dependencies {
    implementation 'org.openjdk.jmh:jmh-core:1.37'
    annotationProcessor 'org.openjdk.jmh:jmh-generator-annprocess:1.37'
}
```

**Use Cases**:
- Benchmark Bug2 vs BFS pathfinding
- Compare vision cone algorithms
- Test communication encoding schemes
- Measure ratnapping state management cost

### 2. JUnit 5 + AssertJ - Testing (MEDIUM PRIORITY)

**Already planned** from 2025, but AssertJ makes tests more readable:
```java
@Test
void testKingFormation() {
    // Setup: 7 rats in 3x3
    assertThat(canFormKing()).isTrue();
    assertThat(formKingCost()).isEqualTo(50);

    formKing();

    assertThat(kingCount).isEqualTo(2);
    assertThat(kingHP).isLessThanOrEqualTo(500);
}
```

### 3. Mockito - Unit Testing (LOW PRIORITY)

**Use**: Mock RobotController for isolated tests
```java
@Test
void testCheeseCollection() {
    RobotController mockRc = mock(RobotController.class);
    when(mockRc.senseNearbyMapInfos()).thenReturn(mockMapInfo);

    // Test collection logic without game engine
}
```

**Value**: Test without full game environment

---

## Patterns to Implement Ourselves

### 1. Efficient Data Structures

**Priority Queue** (for pathfinding):
```java
// Simple binary heap for BFS priority queue
class MinHeap {
    private int[] heap;
    private int size;

    void push(int value) { /* ... */ }
    int pop() { /* ... */ }
}
```

**Circular Buffer** (for history):
```java
// Track last N events efficiently
class CircularBuffer {
    private int[] buffer;
    private int idx = 0;

    void add(int value) {
        buffer[idx++ & (buffer.length - 1)] = value;
    }
}
```

### 2. Bit Manipulation Techniques

**From RoaringBitmap concepts**:
```java
// Multiple boolean flags in single int (save memory)
int flags = 0;
final int FLAG_CARRYING = 1 << 0;
final int FLAG_FLEEING = 1 << 1;
final int FLAG_COLLECTING = 1 << 2;

// Set flag
flags |= FLAG_CARRYING;

// Check flag
boolean carrying = (flags & FLAG_CARRYING) != 0;

// Clear flag
flags &= ~FLAG_CARRYING;
```

### 3. Fast Math Operations

**From Apache Commons Math concepts**:
```java
// Fast distance approximation (avoid sqrt)
public static int distApprox(int dx, int dy) {
    int min = Math.min(Math.abs(dx), Math.abs(dy));
    int max = Math.max(Math.abs(dx), Math.abs(dy));
    return max + (min >> 1); // Approximation: max + min/2
}

// Fast integer division by power of 2
public static int divBy4(int x) {
    return x >> 2; // Much cheaper than x / 4
}

// Fast modulo by power of 2
public static int mod8(int x) {
    return x & 7; // Cheaper than x % 8
}
```

### 4. Cache-Friendly Patterns

**From high-performance computing concepts**:
```java
// Structure of Arrays (SoA) - better cache locality
// BAD: Array of structures (poor cache usage)
class RatInfo {
    int x, y, hp;
}
RatInfo[] rats = new RatInfo[100];

// GOOD: Separate arrays (better cache)
int[] ratX = new int[100];
int[] ratY = new int[100];
int[] ratHP = new int[100];
```

---

## What We CANNOT Use (Likely Restricted)

### External Libraries (Probably All Disallowed)

**Why**:
- Battlecode restricts to specific packages (AllowedPackages.txt)
- Most libraries use disallowed Java features
- Bytecode overhead from libraries is significant
- Memory footprint exceeds 8 MB limit

**Definitely Cannot Use**:
- Guava (external library)
- Apache Commons (external library)
- Eclipse Collections (external library)
- Any game engines
- Any AI frameworks
- Any optimization libraries

**Must Verify** (in AllowedPackages.txt when scaffold releases):
- `java.util.*` (probably allowed but check specifics)
- `java.math.*` (probably allowed)
- `java.lang.*` (partially allowed per specs)

---

## What We CAN Use (Standard Java)

### Allowed Standard Library (From Specs)

**Definitely Allowed**:
```java
// java.lang basics
Math.abs, Math.max, Math.min, Math.sqrt
String operations (except intern, matches, replaceAll, split)
System.out, System.arraycopy
Integer, Long, Double (primitive wrappers)

// Primitive arrays
int[], long[], boolean[], etc.

// Likely allowed (verify in AllowedPackages.txt)
java.util.Arrays
java.util.Random (but we have custom Random class already)
```

**Definitely NOT Allowed** (from specs):
```java
Object.wait(), Object.notify(), Object.notifyAll()
Class.forName()
String.intern(), String.matches(), String.replaceAll(), String.split()
Math.random(), StrictMath.random()
java.io.PrintStream (file operations)
```

---

## Recommended Approach

### For Competition Code (In-Game)

**Use**:
1. **Primitive arrays only** (int[], long[], boolean[])
2. **Standard Math** (abs, max, min, sqrt - check costs)
3. **Custom implementations** of needed algorithms
4. **Bit manipulation** for compact storage
5. **Static variables** for zero-allocation

**Avoid**:
1. **All external libraries** (assume disallowed)
2. **Collections framework** (ArrayList, HashMap - check if allowed)
3. **String operations** (most are expensive/restricted)
4. **Object allocation** (costs bytecode + memory)

### For Development/Testing (Outside Game)

**Use Freely**:
1. **JMH** - Benchmark bytecode costs
2. **JUnit 5** - Unit testing
3. **AssertJ** - Readable assertions
4. **Mockito** - Mock game engine
5. **Profilers** - Find bytecode hotspots

---

## Actionable Items

### Immediate (Before First Code)

1. **Download scaffold** (when available)
2. **Read AllowedPackages.txt** - Know exactly what we can use
3. **Read DisallowedPackages.txt** - Know what to avoid
4. **Read MethodCosts.txt** - Bytecode costs of standard functions

### Week 1 (Setup Development Tools)

1. **Set up JUnit 5** - Testing framework
2. **Add JMH** - Benchmarking (separate project)
3. **Configure profiler** - YourKit or Async Profiler
4. **Create test harness** - Mock game environment

### Week 2+ (Use Patterns, Not Libraries)

1. **Implement BFS ourselves** (JGraphT pattern)
2. **Implement bit packing** (RoaringBitmap pattern)
3. **Implement fast math** (Apache Commons Math pattern)
4. **Implement circular buffer** (Disruptor pattern)
5. **Use SoA layout** (High-performance computing pattern)

---

## Specific Recommendations

### For Pathfinding

**Don't**: Import JGraphT
**Do**: Implement BFS/Dijkstra ourselves using patterns from 2025

```java
// Simple BFS (inspired by JGraphT, implemented ourselves)
public static Direction bfs(MapLocation target) {
    // Use primitive arrays for queue
    int[] queueX = new int[60*60];
    int[] queueY = new int[60*60];
    int head = 0, tail = 0;

    // BFS implementation
    // ...

    return direction;
}
```

### For State Management

**Don't**: Use Collections framework (may not be allowed)
**Do**: Use primitive arrays with counters

```java
// Instead of ArrayList<RobotInfo>
RobotInfo[] enemies = new RobotInfo[50];
int enemyCount = 0;

// Instead of HashMap<MapLocation, Integer>
int[] locationX = new int[100];
int[] locationY = new int[100];
int[] locationValue = new int[100];
int locationCount = 0;
```

### For Testing

**Do**: Use JUnit 5 + AssertJ (development only)
```java
@Test
void testKingSustainability() {
    // Setup
    int globalCheese = 2500;
    int kings = 2;

    // Simulate 2000 rounds
    for (int round = 0; round < 2000; round++) {
        globalCheese -= kings * 3; // Consumption
        globalCheese += estimatedIncome(); // Collection
    }

    // Verify sustainability
    assertThat(globalCheese).isGreaterThan(0);
}
```

### For Profiling

**Do**: Use JMH for bytecode benchmarking
```java
@Benchmark
@BenchmarkMode(Mode.SingleShotTime)
public void benchmarkVisionCone() {
    boolean visible = G.inVisionCone(target, facing);
}

@Benchmark
public void benchmarkCheeseMineSearch() {
    MapLocation nearest = POI.findNearestMine();
}
```

---

## Library Concepts Worth Studying

### 1. Guava Cache Pattern

**Concept**: Memoization for expensive calculations
**Battlecode Application**:
```java
// Cache pathfinding results (if target unchanged)
private static MapLocation lastTarget;
private static Direction lastPath;

public static Direction pathTo(MapLocation target) {
    if (target.equals(lastTarget)) {
        return lastPath; // Cached
    }
    lastPath = computePath(target); // Expensive
    lastTarget = target;
    return lastPath;
}
```

### 2. Disruptor Ring Buffer

**Concept**: Lock-free circular buffer
**Battlecode Application**:
```java
// Track cheese spawn history for prediction
private static final int HISTORY_SIZE = 64; // Power of 2
private static int[] spawnRounds = new int[HISTORY_SIZE];
private static int historyIdx = 0;

void recordSpawn(int round) {
    spawnRounds[historyIdx++ & (HISTORY_SIZE - 1)] = round;
}
```

### 3. BitSet Pattern

**Concept**: Compact boolean storage
**Battlecode Application**:
```java
// Track which cheese mines we've visited (64 mines max)
private static long visitedMines = 0L; // 64 bits

void markVisited(int mineId) {
    visitedMines |= (1L << mineId);
}

boolean hasVisited(int mineId) {
    return (visitedMines & (1L << mineId)) != 0;
}
```

### 4. Object Pool Pattern

**Concept**: Reuse objects instead of allocating
**Battlecode Application**:
```java
// Reuse MapLocation objects (if we need many)
private static MapLocation[] locationPool = new MapLocation[100];
private static int poolIdx = 0;

static {
    for (int i = 0; i < 100; i++) {
        locationPool[i] = new MapLocation(0, 0);
    }
}

// Reuse instead of new MapLocation()
MapLocation getPooledLocation(int x, int y) {
    MapLocation loc = locationPool[poolIdx++ % 100];
    // ... somehow set x,y (MapLocation is immutable, this won't work)
    // Actually, bad example - MapLocation is immutable
}
```

**Reality**: MapLocation is immutable, but pattern shows thinking

---

## Concrete Action Plan

### Before Coding

1. **Read AllowedPackages.txt** - Know constraints
2. **Check Collections framework** - Can we use ArrayList/HashMap?
3. **Review MethodCosts.txt** - Bytecode costs
4. **Set up JMH** - Ready to benchmark

### Development Tools Setup

```bash
# Create benchmarking subproject
mkdir battlecode2026-benchmarks
cd battlecode2026-benchmarks

# Add build.gradle with JMH
# Run benchmarks outside competition
```

### Testing Tools Setup

```bash
# In scaffold project
# Add to build.gradle:
# testImplementation 'org.junit.jupiter:junit-jupiter:5.10.1'
# testImplementation 'org.assertj:assertj-core:3.24.2'
# testImplementation 'org.mockito:mockito-core:5.8.0'
```

### In-Game Code Philosophy

**Principle**: Assume we can use NOTHING except:
- `java.lang.*` (basics only)
- Primitive arrays
- Custom implementations

**Verify** when scaffold arrives, then selectively add if allowed.

---

## Summary

### Can Use (Development Tools)
- ✅ JMH (benchmarking)
- ✅ JUnit 5 (testing)
- ✅ AssertJ (assertions)
- ✅ Mockito (mocking)
- ✅ Profilers (bytecode analysis)

### Cannot Use (In Competition Code)
- ❌ Guava
- ❌ Apache Commons
- ❌ Eclipse Collections
- ❌ Any game/AI libraries
- ❌ Most external dependencies

### Should Implement Ourselves
- ✅ BFS pathfinding
- ✅ Bit manipulation patterns
- ✅ Fast math operations
- ✅ Circular buffers
- ✅ Primitive-based data structures

### Next Step
**Wait for scaffold → Read AllowedPackages.txt → Verify assumptions → Set up development tools**

The value from awesome-java is in **patterns and concepts**, not actual library usage. We'll implement efficient algorithms ourselves within Battlecode constraints.
