# Ratbot Development Guide

**Battlecode 2026 Bot** - Complete implementation guide
**Engine Version**: 1.0.3 (early release)
**Code**: 2,885 lines

---

## Architecture Overview

### Module Organization

```
src/ratbot/
├── RobotPlayer.java          # Entry point (70 lines)
├── BabyRat.java              # Cheese collection (200 lines)
├── RatKing.java              # Economy & spawning (180 lines)
├── algorithms/               # Pre-built modules (1,605 lines)
│   ├── Vision.java           # Cone-based visibility
│   ├── Geometry.java         # Distance calculations
│   ├── DirectionUtil.java    # Turn optimization
│   ├── Pathfinding.java      # BFS + Bug2 navigation
│   ├── GameTheory.java       # Backstab decisions
│   └── Constants.java        # Game constants
└── logging/                  # Performance tracking (470 lines)
    ├── Logger.java           # Structured logging
    ├── Profiler.java         # Bytecode profiling
    └── BytecodeBudget.java   # Budget enforcement
```

---

## RobotPlayer.java - Main Entry Point

### Purpose
Dispatches robot behavior based on type and handles error recovery.

### Key Responsibilities
1. Initialize turn tracking
2. Dispatch to BabyRat or RatKing
3. Handle exceptions gracefully
4. Bytecode budget initialization
5. Ensure Clock.yield() always called

### Code Flow
```java
while (true) {
    BytecodeBudget.startTurn()
    ↓
    Dispatch by type:
    - BABY_RAT → BabyRat.run()
    - RAT_KING → RatKing.run()
    ↓
    Catch exceptions
    ↓
    Clock.yield()
}
```

### Modification Points
- Add new unit types (if game adds them)
- Add global state initialization
- Add team-wide coordination

---

## BabyRat.java - Cheese Collection

### State Machine

```
EXPLORE → (cheese found) → COLLECT
COLLECT → (carrying >= 20) → DELIVER
DELIVER → (transferred) → EXPLORE
ANY → (cat detected) → FLEE → (safe) → EXPLORE
```

### Methods

**run(RobotController rc)**:
- Main behavior loop
- Updates state machine
- Executes state-specific behavior
- Logs state every 20 rounds

**updateState(RobotController rc)**:
- Check for cats (highest priority)
- Update state based on raw cheese amount
- Transitions: 0 cheese → EXPLORE, >0 → COLLECT, >=20 → DELIVER

**explore(RobotController rc)**:
- Scan vision cone for cheese (MapInfo.getCheeseAmount())
- Move toward nearest cheese if found
- Otherwise move toward map center

**collect(RobotController rc)**:
- Find nearest cheese using canPickUpCheese()
- Pick up cheese when adjacent
- Log collection events
- Transition to DELIVER when carrying >= 20

**deliver(RobotController rc)**:
- Find nearest rat king
- Transfer all raw cheese when in range (3 tiles)
- Log transfer events
- Transition to EXPLORE when complete

**flee(RobotController rc)**:
- Detect nearest cat
- Turn to face away
- Move forward (away from cat)
- Transition back when cat not visible

**moveToward(MapLocation target)**:
- Turn to face target
- Move forward
- Simple but bytecode-efficient

### Tunable Parameters

```java
private static int cheeseThreshold = 20; // When to return to king
```

**Optimization**: Lower = more frequent trips, higher = fewer trips but riskier if killed

---

## RatKing.java - Economy & Spawning

### Responsibilities
1. Track global cheese and consumption
2. Warn when approaching starvation
3. Spawn baby rats (when API available)
4. Emergency circuit breaker

### Emergency Thresholds

```java
EMERGENCY_THRESHOLD = 100 rounds  // Reduce spawning
CRITICAL_THRESHOLD = 33 rounds    // STOP all spawning
```

**Safety Buffer**: Never spawn if <100 rounds of cheese left

### Methods

**run(RobotController rc)**:
- Calculate survival metrics (rounds of cheese left)
- Emergency circuit breaker (stop spawning if critical)
- Broadcast cheese status via shared array
- Economy logging every 10 rounds
- Attempt spawning (when API available)

**trySpawn(RobotController rc)** [Commented out - API pending]:
- Check safety buffer (100 rounds minimum)
- Try all 8 adjacent directions
- Call rc.buildRobot() when available
- Log spawn events

**countBabyRats(rc)** / **countAllyKings(rc)**:
- Sense nearby allies
- Count by type
- Used for spawn cost and consumption calculations

### Shared Array Usage

**Slot 0**: Cheese status
- Normal: rounds of cheese left (0-1023)
- Emergency: 999 = CRITICAL

Baby rats read this to know emergency state.

---

## Algorithm Modules (Pre-Built)

### Vision.java - Vision Cone Calculations

**Key Methods**:
- `inCone90(observer, facing, target)` - Baby rat 90° cone
- `inCone180(observer, facing, target)` - Cat 180° cone
- `canSee(observer, facing, target, unitType)` - Auto-detect cone/range
- `getVisibleTiles(center, facing, radius, angle, w, h)` - Get all visible

**Usage**:
```java
boolean visible = Vision.canSee(
    rc.getLocation(),
    rc.getDirection(),
    enemy.location,
    rc.getType()
);
```

### Geometry.java - Distance & Spatial

**Key Methods**:
- `closest(reference, locations)` - Find nearest
- `manhattanDistance(a, b)` - Taxicab distance
- `withinRange(center, target, radiusSq)` - Range check
- `sortByDistance(reference, locations)` - In-place sort

**Usage**:
```java
MapLocation nearest = Geometry.closest(
    rc.getLocation(),
    cheeseMines
);
```

### DirectionUtil.java - Turn Optimization

**Key Methods**:
- `turnsToFace(current, target)` - Compute turn cost (0-2)
- `optimalMovement(facing, desired)` - Movement plan
- `orderedDirections(preferred)` - Try directions in order
- `opposite(dir)` - 180° reverse

**Usage**:
```java
int turns = DirectionUtil.turnsToFace(
    rc.getDirection(),
    targetDirection
);

if (turns == 0) {
    rc.moveForward(); // Already facing right way
} else {
    rc.turn(targetDirection);
}
```

### Pathfinding.java - Navigation

**Key Methods**:
- `bfs(start, target, passable, w, h)` - Shortest path
- `bug2(current, target, canMoveFn)` - Obstacle avoidance
- `greedy(current, target)` - Simple move-toward

**Usage**:
```java
// Expensive but accurate
Direction path = Pathfinding.bfs(
    rc.getLocation(),
    target,
    buildPassabilityMap(rc),
    rc.getMapWidth(),
    rc.getMapHeight()
);

// Cheap obstacle avoidance
Direction dir = Pathfinding.bug2(
    rc.getLocation(),
    target,
    (d) -> rc.canMove(d)
);
```

**Bytecode Cost**: BFS ~3000, Bug2 ~300, Greedy ~50

### GameTheory.java - Strategic Decisions

**Key Methods**:
- `shouldBackstab(...)` - Cooperation vs backstab decision
- `evaluate(GameState)` - Full recommendation with reasoning
- `worthEnhancingBite(cheese, targetHP, baseDmg)` - Cheese investment

**Usage**:
```java
GameTheory.GameState state = new GameTheory.GameState(
    round, ourCatDmg, enemyCatDmg,
    ourKings, enemyKings, ourCheese, enemyCheese
);

GameTheory.BackstabRecommendation rec = GameTheory.evaluate(state);

if (rec.shouldBackstab) {
    // Trigger backstab
}
```

### Constants.java - Game Parameters

**Helper Methods**:
- `getSpawnCost(babyRatsAlive)` - Calculate spawn cost
- `getCheeseSpawnProbability(roundsSince)` - Cheese spawn math
- `getBiteDamage(cheeseSpent)` - Damage with enhancement
- `roundsOfCheeseSupply(cheese, kings)` - Sustainability check

**Usage**:
```java
int cost = Constants.getSpawnCost(babyRatCount);
int roundsLeft = Constants.roundsOfCheeseSupply(
    rc.getGlobalCheese(),
    kingCount
);
```

---

## Logging System

### Logger.java - Structured Logging

**Log Categories** (11 types):
- STATE - Robot state snapshots
- ECONOMY - Team economics
- SPAWN - Unit creation
- COMBAT - Attack events
- CAT - Cat tracking
- BACKSTAB - Game state transitions
- CHEESE - Collection/transfer
- TACTICAL - Decision context
- PROFILE - Bytecode usage
- WARNING - Issues detected
- ERROR - Exceptional conditions
- TRAP - Trap placement

**Format**: `{CATEGORY}:{round}:{type}:{id}:{key}={value}:...`

**Usage**:
```java
Logger.logState(round, "BABY_RAT", id, x, y, facing, hp, cheese, mode);
Logger.logCombat(round, type, id, fromX, fromY, targetX, targetY, dmg, cheeseSpent, targetHP);
```

**Bytecode Cost**: ~50-100 per call (zero allocation)

### Profiler.java - Performance Tracking

**Usage**:
```java
Profiler.start();
expensiveOperation();
Profiler.end("operation_name", round, id);
```

**Sampling**: Logs every 20 rounds to reduce overhead

### BytecodeBudget.java - Budget Management

**Usage**:
```java
BytecodeBudget.startTurn(unitType);

if (!BytecodeBudget.canAfford(3000)) {
    // Use cheaper alternative
}

if (BytecodeBudget.isCritical()) {
    Clock.yield(); // End turn early
}
```

**Thresholds**: Warning (80%), Critical (90%)

---

## Development Workflow

### 1. Build & Test
```bash
cd scaffold
./gradlew build           # Compile
./gradlew run             # Run match
```

### 2. Analyze Logs
```bash
# Extract structured logs
./gradlew run 2>&1 | grep -E "ECONOMY|STATE|COMBAT" > analysis.log

# Parse with Python
./tools/log_parser.py analysis.log

# Generate visualizations
./tools/performance_dashboard.py analysis.log
```

### 3. Profile Bytecode
Add profiling to expensive sections:
```java
Profiler.start();
Direction path = Pathfinding.bfs(...);
Profiler.end("pathfinding", round, id);
```

Check logs for PROFILE entries.

### 4. Watch Replays
```bash
./client/battlecode-client.exe
# Load matches/*.bc26
```

---

## API Status (v1.0.3)

### Working ✓
- Movement: moveForward(), turn()
- Sensing: senseNearbyRobots(), senseMapInfo()
- Cheese: pickUpCheese(), transferCheese()
- MapInfo: getCheeseAmount(), hasCheeseMine()

### Pending ✗
- Spawning: buildRobot() - Not yet in API
- Traps: placeRatTrap(), placeCatTrap()
- Dirt: removeDirt(), placeDirt()
- Ratnapping: carryRat(), throwRat()
- King formation: becomeRatKing()

**Update Check**: `./gradlew update`

See `API_STATUS.md` for complete tracking.

---

## Optimization Guidelines

### Bytecode Efficiency

**Always Use**:
```java
// Backward loops (30% savings)
for (int i = array.length; --i >= 0;) { }

// Cache sensing
RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
// Use enemies array multiple times

// Static arrays (no allocation)
private static int[] buffer = new int[100];
```

**Avoid**:
```java
// Forward loops
for (int i = 0; i < array.length; i++) { } // Slower

// Re-sensing
for (...) {
    RobotInfo[] enemies = rc.senseNearbyRobots(...); // BAD
}

// Allocations in loops
for (...) {
    int[] temp = new int[10]; // Expensive
}
```

### Algorithm Selection

**Pathfinding**:
- Greedy: ~50 bytecode - Use for simple navigation
- Bug2: ~300 bytecode - Use for obstacles
- BFS: ~3000 bytecode - Use when accuracy critical

**When to use which**:
- Greedy: Open areas, no obstacles
- Bug2: General navigation, obstacles common
- BFS: When you need guaranteed shortest path

### Movement Costs

**Forward**: 10 cooldown (prefer this)
**Strafe** (not facing): 18 cooldown (1.8× slower)

**Strategy**: Always turn to face movement direction when possible

---

## Common Patterns

### Cheese Collection Loop

```java
// Find nearest cheese
MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
MapLocation nearestCheese = null;
int nearestDist = Integer.MAX_VALUE;

for (MapLocation loc : nearby) {
    if (rc.canPickUpCheese(loc)) {
        int dist = me.distanceSquaredTo(loc);
        if (dist < nearestDist) {
            nearestDist = dist;
            nearestCheese = loc;
        }
    }
}

// Move toward and collect
if (nearestCheese != null) {
    Direction dir = me.directionTo(nearestCheese);
    if (rc.canTurn()) rc.turn(dir);
    if (rc.canMoveForward()) rc.moveForward();

    if (rc.canPickUpCheese(nearestCheese)) {
        rc.pickUpCheese(nearestCheese);
    }
}
```

### Cheese Transfer

```java
// Find king
RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
MapLocation kingLoc = null;

for (RobotInfo ally : allies) {
    if (ally.getType() == UnitType.RAT_KING) {
        kingLoc = ally.getLocation();
        break;
    }
}

// Transfer when in range
if (kingLoc != null && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
    rc.transferCheese(kingLoc, rc.getRawCheese());
}
```

### Cat Avoidance

```java
// Detect cats
RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
for (RobotInfo robot : nearby) {
    if (robot.getType() == UnitType.CAT) {
        // Flee away
        Direction away = DirectionUtil.opposite(
            me.directionTo(robot.getLocation())
        );

        if (rc.canTurn()) rc.turn(away);
        if (rc.canMoveForward()) rc.moveForward();

        return; // High priority
    }
}
```

### Shared Array Communication

```java
// King writes cheese status
int roundsLeft = globalCheese / (kingCount * 3);
rc.writeSharedArray(0, roundsLeft);

// Baby rats read status
int cheeseStatus = rc.readSharedArray(0);
if (cheeseStatus == 999) {
    // EMERGENCY - all rats focus on collection
}
```

---

## Debugging

### Indicator Strings

```java
rc.setIndicatorString("State: " + currentState + " Cheese: " + rc.getRawCheese());
```

Shows in client visualization.

### Console Logging

```java
System.out.println("DEBUG:" + round + ":myId=" + id + ":issue=stuck");
```

Shows in match output.

### Structured Logging

```java
Logger.logTactical(round, "BABY_RAT", id, enemies, cats, nearestDist, "FLEE");
```

Parseable with tools/log_parser.py.

---

## Testing

### Run Multiple Matches

```bash
./tools/run_tests.sh ratbot examplefuncsplayer 10
```

Runs 10 matches, reports win rate.

### Analyze Performance

```bash
./gradlew run > match.log 2>&1
./tools/log_parser.py match.log
./tools/performance_dashboard.py match.log
```

Generates:
- Text analysis
- 4 performance plots
- CSV data export

---

## Known Issues

### Division by Zero (FIXED)
**Issue**: countAllyKings() returns 0 in early API
**Fix**: `Math.max(1, countAllyKings(rc))`
**Location**: RatKing.java:34

### Spawn API Missing
**Issue**: buildRobot() not in v1.0.3
**Status**: Code ready, commented out
**Location**: RatKing.java:66

### Cheese Detection
**Working**: MapInfo.getCheeseAmount() and canPickUpCheese()
**Status**: Functional

---

## Next Features (When API Complete)

### Spawning (Week 1)
Uncomment trySpawn() in RatKing.java when buildRobot() available.

### Combat (Week 1)
Add attack behavior:
```java
if (rc.canAttack(enemy.location)) {
    // Check if cheese enhancement worth it
    if (GameTheory.worthEnhancingBite(32, enemy.health, 10)) {
        rc.attack(enemy.location, 32);
    } else {
        rc.attack(enemy.location);
    }
}
```

### Backstab Decisions (Week 2)
Use GameTheory.evaluate() to trigger backstab at optimal time.

### Traps (Week 2)
When placeCatTrap() available, place along cat paths.

### Multi-King (Week 3)
Coordinate multiple kings for higher spawn rate.

---

## Performance Targets

**Bytecode**:
- Baby Rat: <10,000 average (57% of limit)
- Rat King: <15,000 average (75% of limit)

**Cheese Economy**:
- Income: >5 cheese/round minimum
- Never starve kings
- Sustain >10 baby rats continuously

**Survival**:
- Reach round 2000 consistently
- >90% unit survival rate
- King HP >80% average

---

## File Modification Guide

### Adding New Behavior

**Baby Rat**: Edit BabyRat.java
- Add new state to enum
- Add case in switch statement
- Implement behavior method

**Rat King**: Edit RatKing.java
- Add coordination logic
- Update economy tracking
- Modify spawn strategy

**Shared**: Edit RobotPlayer.java
- Add global state variables
- Add initialization code

### Adding Logging

```java
// Add to relevant behavior method
Logger.logCategory(round, type, id, ...params...);
```

Categories defined in Logger.java.

### Adding Tests

Create test file in test/algorithms/:
```java
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class NewFeatureTest {
    @Test
    void testFeature() {
        // Test code
        assertThat(result).isEqualTo(expected);
    }
}
```

---

## References

**Game Specs**: ../claudedocs/complete_spec.md
**Constants**: ../claudedocs/CONSTANTS_VERIFIED.md
**Strategy**: ../claudedocs/strategic_priorities.md
**API**: API_STATUS.md (this directory)
**Build Commands**: CLAUDE.md (this directory)

---

**Last Updated**: January 6, 2026
**Bot Version**: ratbot v0.1 (early development)
**API Version**: 1.0.3 (early release)
