# Pre-Scaffold Action Plan - 7 Days to Sprint 1

**Created**: January 5, 2026
**Sprint 1 Date**: January 12, 2026 (7 days)
**Goal**: Maximum readiness for immediate implementation when scaffold releases

## Strategic Analysis (from mcp-reasoning)

**Decision Analysis**: Test framework (0.82) > Algorithms (0.775) > Architecture (0.74)

**Key Insight**: Focus on "velocity multipliers" not immediate bot coding
- Preparation should accelerate development when scaffold arrives
- Build reusable components that work in any scaffold structure
- Create tools that compound advantages throughout competition

**Divergent Perspectives**:
1. Strategic Intelligence: Study meta-game and opponents
2. Infrastructure Maximalist: Build tools that compound
3. Human Systems: Team protocols (N/A - solo)
4. Adaptive Minimalist: Maintain flexibility, avoid over-commitment

**Synthesis**: Build irreplaceable infrastructure (testing, algorithms) while maintaining architectural flexibility

---

## Time-Allocated Action Plan

### Days 1-2 (40% time) - Core Infrastructure

**Priority 1: Standalone Algorithm Modules**
- **BFS pathfinding** with directional awareness
- **Vision cone calculations** (90° and 180° variants)
- **Distance utilities** (Manhattan, Chebyshev, Euclidean)
- **Direction optimization** (minimize turns to face target)
- **Cheese spawn probability** calculator

**Deliverable**: Java files that work independently, can integrate into any scaffold

**Priority 2: Mock Testing Framework**
- **Mock RobotController** interface based on javadoc
- **Mock game state** (map, units, cheese)
- **Test harness** for running bot logic without engine
- **JUnit 5 setup** with example tests

**Deliverable**: Can test bot logic immediately when scaffold arrives

**Daily Validation**: Test algorithm modules against mock framework

---

### Days 3-4 (35% time) - Development Tools & Validation

**Priority 3: Automation Pipeline**
- **Match replay parser** (when scaffold gives replay format)
- **Log analysis tools** (STATE, ECONOMY, COMBAT parsing)
- **Bytecode profiler wrapper** (Clock.getBytecodeNum() tracking)
- **Performance dashboard** (visualize bytecode usage)

**Deliverable**: Automated analysis of scrimmage matches

**Priority 4: Visibility Logging Templates**
- **Log format specifications** (building on 2025)
- **Logger utility class** (zero-allocation logging)
- **Analysis scripts** (parse logs, generate reports)

**Deliverable**: Copy-paste logging system ready to integrate

**Daily Validation**: Test tools against mock data

---

### Days 5-6 (20% time) - Advanced Models

**Priority 5: Game Theory Decision Engine**
- **Backstab timing model** (cooperation vs backstab score simulation)
- **King count optimizer** (consumption vs production model)
- **Cheese allocation strategy** (spawning vs traps vs feeding)
- **Monte Carlo simulations** (test strategies)

**Deliverable**: Mathematical model for strategic decisions

**Priority 6: Performance Optimization**
- **JMH benchmark suite** setup
- **Bytecode optimization patterns** documented
- **Common operations cost catalog**

**Deliverable**: Ready to optimize immediately

**Daily Validation**: Verify models produce sensible recommendations

---

### Day 7 (5% time) - Integration Buffer

**Priority 7: Documentation & Interfaces**
- **Integration guide** (how to add modules to scaffold)
- **API contracts** documented (what each module expects)
- **Quick start checklist** (when scaffold arrives)

**Deliverable**: Smooth integration path

**Contingency**: If scaffold appears early, drop Days 5-7 and integrate immediately

---

## Risk Mitigation

### Risk: Scaffold doesn't appear until Day 6
**Impact**: Only 1 day to implement
**Mitigation**: Modules are standalone, can integrate quickly
**Backup**: Focus on strongest component (algorithms + tests)

### Risk: Scaffold structure incompatible with our modules
**Impact**: Must refactor modules to fit scaffold patterns
**Mitigation**: Keep modules loosely coupled, clean interfaces
**Backup**: Copy-paste algorithms into scaffold structure

### Risk: Time overruns on complex features
**Impact**: Don't finish all priorities
**Mitigation**: Strict time-boxing, daily validation
**Backup**: Game theory model is optional, core algorithms essential

### Risk: Mock framework doesn't match real API
**Impact**: Tests may not reflect real behavior
**Mitigation**: Base mock on official javadoc exactly
**Backup**: Rapid test rewrite when scaffold available

---

## Daily Validation Checkpoints

**Every day at 8PM**:
1. What did I complete today?
2. Do modules integrate with each other?
3. Can I explain this to future-me quickly?
4. Is this ready to drop into scaffold?
5. Would this help in Sprint 1?

**Success Criteria**:
- ✅ Can integrate algorithms in <1 hour when scaffold arrives
- ✅ Can test bot logic without full game engine
- ✅ Can profile bytecode usage immediately
- ✅ Can analyze match replays automatically

---

## Detailed Task Breakdown

### Task 1: Standalone Algorithms (8 hours)

**BFS Pathfinding** (2 hours):
```java
// src/algorithms/BFS.java
public class BFS {
    public static Direction bfsToTarget(
        MapLocation start,
        MapLocation target,
        boolean[][] passable,
        int mapWidth,
        int mapHeight
    ) {
        // Queue-based BFS
        // Return first step on shortest path
    }
}
```

**Vision Cone** (2 hours):
```java
// src/algorithms/Vision.java
public class Vision {
    public static boolean inCone90(
        MapLocation observer,
        Direction facing,
        MapLocation target
    ) {
        // Compute if target in 90° cone
    }

    public static MapLocation[] getVisibleTiles(
        MapLocation center,
        Direction facing,
        int radiusSquared,
        int coneAngle
    ) {
        // Return all tiles in vision cone
    }
}
```

**Direction Utilities** (2 hours):
```java
// src/algorithms/DirectionUtil.java
public class DirectionUtil {
    public static int turnsToFace(Direction from, Direction to) {
        // Compute minimum turns needed
    }

    public static Direction optimalFacing(
        MapLocation current,
        MapLocation target
    ) {
        // Best direction to face for vision + movement
    }
}
```

**Distance & Geometry** (2 hours):
```java
// src/algorithms/Geometry.java
public class Geometry {
    public static int manhattanDistance(MapLocation a, MapLocation b);
    public static int chebyshevDistance(MapLocation a, MapLocation b);
    public static MapLocation closest(MapLocation me, MapLocation[] locs);
    public static MapLocation[] sortByDistance(MapLocation from, MapLocation[] locs);
}
```

### Task 2: Mock Framework (6 hours)

**Mock RobotController** (4 hours):
```java
// test/mock/MockRobotController.java
public class MockRobotController implements RobotController {
    private MapLocation location;
    private Direction facing;
    private int rawCheese;
    private int globalCheese;
    private MockMap map;

    // Implement all RobotController methods
    @Override
    public boolean canMove(Direction d) { /* ... */ }

    @Override
    public void move(Direction d) { /* ... */ }

    // ... all other methods
}
```

**Mock Game State** (2 hours):
```java
// test/mock/MockGameState.java
public class MockGameState {
    private MockRobotController[] rats;
    private MockRobotController[] cats;
    private MockMap map;

    public void step() {
        // Advance one round
    }

    public void run(int rounds) {
        // Run full game simulation
    }
}
```

### Task 3: Test Suite (4 hours)

**Algorithm Tests**:
```java
@Test
void testBFSFindsShortestPath() {
    MapLocation start = new MapLocation(0, 0);
    MapLocation target = new MapLocation(10, 10);
    boolean[][] passable = createTestMap();

    Direction first = BFS.bfsToTarget(start, target, passable, 30, 30);

    assertThat(first).isNotNull();
    // Verify it's on shortest path
}
```

**Vision Cone Tests**:
```java
@Test
void testVisionCone90Degrees() {
    MapLocation observer = new MapLocation(15, 15);
    Direction facing = Direction.NORTH;

    // Target directly ahead - should be visible
    assertThat(Vision.inCone90(observer, facing, new MapLocation(15, 20))).isTrue();

    // Target behind - should NOT be visible
    assertThat(Vision.inCone90(observer, facing, new MapLocation(15, 10))).isFalse();
}
```

### Task 4: Game Theory Model (4 hours)

**Backstab Decision Engine**:
```java
// src/strategy/BackstabDecision.java
public class BackstabDecision {
    private int ourCatDamage;
    private int enemyCatDamage;
    private int ourKings;
    private int enemyKings;
    private int ourCheese;
    private int enemyCheese;

    public double scoreCooperation() {
        // Equation 1
    }

    public double scoreBackstabbing() {
        // Equation 2 (assume we win kings)
    }

    public boolean shouldBackstab() {
        return scoreBackstabbing() > scoreCooperation() + threshold;
    }

    public int optimalBackstabRound(int currentRound) {
        // Monte Carlo: try different timings, find best
    }
}
```

### Task 5: Automation Pipeline (3 hours)

**Log Parser**:
```java
// tools/LogParser.java
public class LogParser {
    public static MatchData parseReplay(String replayFile) {
        // Parse .bc26 file (format TBD)
        // Extract STATE, ECONOMY, COMBAT logs
        // Return structured data
    }
}
```

**Performance Dashboard**:
```bash
# tools/analyze.sh
#!/bin/bash
# Parse logs, generate report
# Show: cheese income, king HP, bytecode usage, combat stats
```

### Task 6: JMH Setup (2 hours)

**Benchmark Project**:
```bash
mkdir battlecode2026-benchmarks
cd battlecode2026-benchmarks

# build.gradle
dependencies {
    implementation 'org.openjdk.jmh:jmh-core:1.37'
}
```

**Benchmark Tests**:
```java
@Benchmark
public void benchmarkBFS() {
    Direction d = BFS.bfsToTarget(start, target, map, 30, 30);
}

@Benchmark
public void benchmarkVisionCone() {
    boolean visible = Vision.inCone90(observer, facing, target);
}
```

---

## Success Metrics

### By Day 2
- [ ] BFS, Vision, Direction, Geometry modules complete
- [ ] Mock RobotController implements 20+ key methods
- [ ] 10+ unit tests passing

### By Day 4
- [ ] Mock framework can simulate basic game
- [ ] Log parser handles mock data
- [ ] Automation pipeline runs end-to-end
- [ ] 30+ unit tests passing

### By Day 6
- [ ] Backstab decision model validated
- [ ] JMH benchmarks show bytecode costs
- [ ] All modules tested together
- [ ] Integration guide written

### Day 7
- [ ] Ready to integrate within 1 hour of scaffold release
- [ ] All tests green
- [ ] Documentation complete

---

## What This Enables

**When scaffold releases**:
1. Copy algorithm modules → instant pathfinding
2. Import test framework → immediate validation
3. Run automation → analyze first matches
4. Apply backstab model → strategic advantage

**Competitive advantage**:
- Other teams: Learning API, basic movement
- Us: Advanced pathfinding, combat micro, strategic decisions

**Velocity multiplier**: 2-3x faster development in Sprint 1

---

## Implementation Order (Prioritized)

### MUST DO (Critical Path)
1. **Vision.inCone90()** - Required for all sensing
2. **BFS.bfsToTarget()** - Core pathfinding
3. **DirectionUtil.turnsToFace()** - Movement optimization
4. **MockRobotController basics** - Testing capability

### SHOULD DO (High Value)
5. **Geometry utilities** - Distance calculations
6. **Mock game state** - Integration testing
7. **BackstabDecision.shouldBackstab()** - Strategic edge
8. **Log parser** - Analysis automation

### NICE TO DO (If Time)
9. **JMH benchmarks** - Performance tuning
10. **Advanced mock features** - Ratnapping, throwing
11. **Monte Carlo simulations** - Strategy validation

---

## Contingency Plans

### If scaffold appears Day 1
- **Action**: Drop Days 5-7, integrate immediately
- **Use**: Whatever modules are complete (even if partial)
- **Focus**: Get functional bot running first

### If scaffold appears Day 6
- **Action**: All modules should be complete
- **Use**: 1 day for integration and Sprint 1 submission
- **Advantage**: Full preparation pays off

### If scaffold never appears
- **Action**: Our modules become the bot foundation
- **Build**: Minimal scaffold ourselves based on javadoc
- **Risk**: May not match official, but we're functional

---

## Next Actions (Right Now)

1. Create `src/algorithms/` directory structure
2. Implement `Vision.java` with cone calculations
3. Implement `BFS.java` with directional pathfinding
4. Create `test/mock/MockRobotController.java`
5. Write first unit tests

**Start with**: Vision cone calculations (most fundamental)

---

## References

- **Reasoning Analysis**: mcp-reasoning decision + divergent + reflection
- **Decision Scores**: Test framework (0.82), Algorithms (0.775), Architecture (0.74)
- **Key Insight**: Preparation = velocity multipliers, not bot coding
- **Validation**: Daily integration tests to verify assumptions
