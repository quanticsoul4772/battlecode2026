# Ratbot7 Bytecode Optimizations Summary

This document summarizes all bytecode optimizations implemented in ratbot7 to improve performance within Battlecode's strict bytecode limits (Baby Rat: 17,500, King: 20,000 per turn).

## Overview

| Priority | Category | Total Savings |
|----------|----------|---------------|
| HIGH | Caching & Inlining | ~300-400 bytecode/turn |
| MEDIUM | Method Optimization | ~100-150 bytecode/turn |
| LOW | Minor Optimizations | ~50 bytecode/turn |
| TRAPS | Trap Placement | ~1500-4000 bytecode/turn |
| THREAT | Squeak Processing | ~750 bytecode/turn |
| SCORE | Target Scoring | ~125-225 bytecode/turn |

**Total Estimated Savings: ~2,000-3,500 bytecode/turn**

---

## HIGH Priority Optimizations (~300-400 bytecode/turn)

### 1. Map Dimensions Caching
**Location:** `initializeRobot()`, used throughout
**Savings:** ~80 bytecode/turn

```java
// Before: Called 4+ times per turn
rc.getMapWidth();
rc.getMapHeight();

// After: Cached once at initialization
private static int cachedMapWidth;
private static int cachedMapHeight;
```

### 2. Direction to Enemy Caching
**Location:** `updateGameState()`, used in scoring functions
**Savings:** ~30 bytecode/turn

```java
// Before: Computed multiple times per turn
Direction toEnemy = myLoc.directionTo(enemyKingLoc);

// After: Cached once per turn
private static Direction cachedToEnemyDir;
```

### 3. Pre-computed Squared Distance Constants
**Location:** Economy leash checks
**Savings:** ~25 bytecode/turn

```java
// Before: Called intSqrt() for comparison
if (intSqrt(distSq) <= ECON_LEASH_CRITICAL_DIST) ...

// After: Compare squared values directly
private static final int ECON_LEASH_CRITICAL_DIST_SQ = 25;  // 5²
private static final int ECON_LEASH_NORMAL_DIST_SQ = 144;   // 12²
```

### 4. Triple-add Flee Calculation Fix
**Location:** Cat flee logic in `runBabyRat`
**Savings:** ~45 bytecode/turn

```java
// Before: 3 MapLocation.add() calls
MapLocation fleeTo = myLoc.add(dir).add(dir).add(dir);

// After: Direct calculation
MapLocation fleeTo = new MapLocation(myLocX + dir.dx * 3, myLocY + dir.dy * 3);
```

### 5. Inline scoreTarget() Function
**Location:** `scoreAllTargetsGatherer()`, `scoreAllTargetsSentry()`
**Savings:** ~50-100 bytecode/turn

```java
// Before: Method call with switch statement
int score = scoreTarget(TARGET_ENEMY_RAT, distSq);

// After: Inline calculation
int score = (ENEMY_RAT_VALUE * 1000) / (1000 + distSq * DISTANCE_WEIGHT_INT);
```

---

## MEDIUM Priority Optimizations (~100-150 bytecode/turn)

### 1. Batched Shared Array Reads
**Location:** Start of `runBabyRat()`
**Savings:** ~50 bytecode/turn

```java
// Before: Scattered reads throughout turn
int focusTarget = rc.readSharedArray(SLOT_FOCUS_TARGET);
// ... later ...
int safeRounds = rc.readSharedArray(SLOT_SAFE_ROUNDS);

// After: All reads batched at turn start
cachedFocusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);
cachedSafeRounds = rc.readSharedArray(SLOT_SAFE_ROUNDS);
cachedDefenderCount = rc.readSharedArray(SLOT_DEFENDER_COUNT);
cachedKingCheese = rc.readSharedArray(SLOT_KING_CHEESE);
```

### 2. Pre-computed Role Hashes
**Location:** `runBabyRat()` role determination
**Savings:** ~30 bytecode/turn

```java
// Before: Computed multiple times
int role = (id % 100) < 50 ? 0 : 1;
boolean isShieldRat = (id % 17) < 7;

// After: Pre-computed once per turn
cachedRolePercent = id % 100;
cachedShieldHash = id % 17;
cachedSlotMod5 = id % 5;
```

### 3. Inline distanceSquaredTo() Calls
**Location:** Various distance checks
**Savings:** ~15 bytecode per call

```java
// Before: Method call
int dist = myLoc.distanceSquaredTo(target);

// After: Inline calculation
int dx = myLocX - target.x;
int dy = myLocY - target.y;
int dist = dx * dx + dy * dy;
```

### 4. Replace Math.max/min with Ternary
**Location:** Shield wall clamping, blocking line
**Savings:** ~15 bytecode/turn

```java
// Before: ~20 bytecode
shieldX = Math.max(0, Math.min(cachedMapWidth - 1, shieldX));

// After: ~5 bytecode
shieldX = shieldX < 0 ? 0 : (shieldX >= cachedMapWidth ? cachedMapWidth - 1 : shieldX);
```

---

## LOW Priority Optimizations (~50 bytecode/turn)

### 1. tryDigDirt Optimization
**Location:** `tryDigDirt()`
**Savings:** ~15 bytecode/turn

```java
// Before: MapLocation allocation
MapLocation ahead = myLoc.add(cachedFacing);

// After: Use RC method directly
MapLocation ahead = rc.adjacentLocation(cachedFacing);
```

### 2. Backward Loop Conversions
**Location:** Trap placement, direction iterations
**Savings:** ~10 bytecode/turn

```java
// Before: Forward loop
for (int d = 0; d < 8; d++) { ... }

// After: Backward loop (saves comparison bytecode)
for (int d = 8; --d >= 0; ) { ... }
```

### 3. Squared Distances in Debug Logs
**Location:** Various debug logging calls
**Savings:** ~25 bytecode/turn

```java
// Before: Compute sqrt for logging
logDecision(round, id, "CAT_FLEE", "dist=" + intSqrt(distSq));

// After: Pass squared value
logDecision(round, id, "CAT_FLEE", "distSq=" + distSq);
```

---

## TRAPS Checkpoint Optimizations (~1500-4000 bytecode/turn)

### 1. Pre-cached Trap Direction Arrays
**Location:** `initializeRobot()`, used in `placeRatTraps()`
**Savings:** ~200 bytecode/turn

```java
// Before: Computed 8 rotations per call
Direction[] trapDirs = new Direction[8];
trapDirs[0] = toEnemy;
trapDirs[1] = toEnemy.rotateLeft();
// ... 6 more rotations

// After: Pre-computed for all 8 enemy directions
private static final Direction[][] TRAP_DIRS_BY_ENEMY = new Direction[8][8];
Direction[] trapDirs = TRAP_DIRS_BY_ENEMY[toEnemy.ordinal()];
```

### 2. Reduced Trap Attempts Per Turn
**Location:** All trap placement functions
**Savings:** ~300-500 bytecode/turn

```java
// Before: 6 attempts per turn
private static final int MAX_TRAP_ATTEMPTS_PER_TURN = 6;

// After: Only 3 attempts (fewer API calls)
private static final int MAX_TRAP_ATTEMPTS_PER_TURN = 3;
```

### 3. Amortized Trap Placement
**Location:** `placeRatTraps()`
**Savings:** ~500-1000 bytecode/turn

```java
// Before: Start from position 0 every turn
for (int dist = 5; dist >= 3; --dist) {
  for (int d = 7; d >= 0; --d) { ... }
}

// After: Resume from last position (amortized across turns)
private static int trapPlacementIndex = 0;
int startIdx = trapPlacementIndex;
for (int i = 0; i < totalPositions; i++) {
  int idx = (startIdx + i) % totalPositions;
  // ... try position ...
}
// Don't reset index - continue scanning different positions each turn
```

### 4. Give-up Mechanism for Persistent Failures
**Location:** Trap placement section in `runKing()`
**Savings:** ~2000+ bytecode/turn on constrained maps

```java
// Before: Keep trying forever
if (ratTrapCount < RAT_TRAP_TARGET) {
  placeRatTraps(rc, me);
}

// After: Give up after persistent failures
private static final int TRAP_GIVE_UP_THRESHOLD = 15;
int totalFailures = consecutiveRingFailures + consecutiveLineFailures;
if (totalFailures > TRAP_GIVE_UP_THRESHOLD) {
  // Skip trap placement entirely
}
```

### 5. Remove intSqrt from updateBlockingLine
**Location:** `updateBlockingLine()`
**Savings:** ~50 bytecode/turn

```java
// Before: Compute sqrt for distance
int distToNearestEnemy = intSqrt(closestDistSq);
int effectiveBlockDist = Math.min(bodyBlockLineDist, distToNearestEnemy / 3);

// After: Use squared distance thresholds directly
int effectiveBlockDist = closestDistSq < 36 ? 2 : (closestDistSq < 81 ? 3 : 4);
```

### 6. Pre-cached Perpendicular Directions
**Location:** `updateBlockingLine()`, `placeRatTrapsLine()`
**Savings:** ~30 bytecode/turn

```java
// Before: Compute rotations
Direction perpDir = attackDir.rotateLeft().rotateLeft();
Direction perpLeft = toEnemy.rotateLeft().rotateLeft();
Direction perpRight = toEnemy.rotateRight().rotateRight();

// After: Use pre-cached lookups
Direction perpDir = PERP_LEFT_BY_DIR[attackDir.ordinal()];
Direction perpLeft = PERP_LEFT_BY_DIR[toEnemy.ordinal()];
Direction perpRight = PERP_RIGHT_BY_DIR[toEnemy.ordinal()];
```

### 7. Guard All Debug Logging
**Location:** All trap placement functions, `updateBlockingLine()`
**Savings:** ~150-200 bytecode/turn

```java
// Before: Always log
logTrapPlaced(...);
logTrapFailed(...);
logLayoutSwitch(...);
logBlockLine(...);

// After: Guard with enabled check
if (Debug7.ENABLED) {
  logTrapPlaced(...);
  logTrapFailed(...);
  logLayoutSwitch(...);
  logBlockLine(...);
}
```

### 8. Guard Visualize Calls
**Location:** Debug visualization section
**Savings:** ~100 bytecode/turn

```java
// Before: Always call (even when disabled)
visualizeSentryRing(rc, me, sentryRingDist);
visualizeBlockLine(rc, lineCenter, perpDir);

// After: Guard with enabled check
if (Debug7.ENABLED) {
  visualizeSentryRing(rc, me, sentryRingDist);
  visualizeBlockLine(rc, lineCenter, perpDir);
}
```

### 9. Early Exit for Small Maps
**Location:** `placeDirtWalls()`
**Savings:** ~100-200 bytecode/turn

```java
private static final int SMALL_MAP_THRESHOLD = 30;
if (cachedMapWidth <= SMALL_MAP_THRESHOLD && cachedMapHeight <= SMALL_MAP_THRESHOLD) {
  return; // Skip dirt walls on small maps
}
```

---

## THREAT Checkpoint Optimizations (~750 bytecode/turn)

### 1. Reduced Squeak Processing
**Location:** `kingReadSqueaks()`
**Savings:** ~200-300 bytecode/turn

```java
// Before: Process up to 8 squeaks
private static final int MAX_SQUEAKS_TO_READ = 8;

// After: Process only 4 (most recent are most relevant)
private static final int MAX_SQUEAKS_TO_READ = 4;
```

### 2. Guarded Debug Logs
**Location:** `kingReadSqueaks()`
**Savings:** ~150-200 bytecode/turn

```java
// Before: Always prepare log parameters
logRushDetected(round, id, ...);

// After: Guard with enabled check
if (Debug7.ENABLED) {
  logRushDetected(round, id, ...);
}
```

### 3. Replace Math.max/min in Threat Hysteresis
**Location:** Threat level calculation
**Savings:** ~30 bytecode/turn

```java
// Before
newThreatLevel = Math.max(newThreatLevel, minThreat);

// After
newThreatLevel = newThreatLevel > minThreat ? newThreatLevel : minThreat;
```

---

## SCORE Checkpoint Optimizations (~125-225 bytecode/turn)

### 1. Inline scoreTarget() Function
**Location:** `scoreAllTargetsGatherer()`, `scoreAllTargetsSentry()`
**Savings:** ~50-100 bytecode/turn

Replaced 6+ method calls with inline calculations. Also removed the now-dead `scoreTarget()` and `getBaseValue()` functions (28 lines of code).

### 2. Early Exit for Enemy King
**Location:** Enemy scoring loops
**Savings:** ~20-50 bytecode/turn

```java
if (enemy.getType() == UnitType.RAT_KING) {
  score += 500;
  cachedBestTarget = enemyLoc;
  cachedBestTargetType = TARGET_ENEMY_RAT;
  break; // Exit loop early - nothing beats enemy king
}
```

### 3. Pre-cached Perpendicular Directions
**Location:** Shield wall calculation
**Savings:** ~10 bytecode/turn

```java
// Before: Computed per call
Direction perpLeft = cachedToEnemyDir.rotateLeft().rotateLeft();

// After: Pre-cached for all 8 directions
private static final Direction[] PERP_LEFT_BY_DIR = new Direction[8];
Direction perpLeft = PERP_LEFT_BY_DIR[cachedToEnemyDir.ordinal()];
```

### 4. Skip Empty Cheese Loop
**Location:** `scoreAllTargetsSentry()`
**Savings:** ~30-50 bytecode/turn

```java
// Before: Always enter loop
if (!cachedCarryingCheese) {
  for (int i = cheeseCount; --i >= 0; ) { ... }
}

// After: Skip if no cheese visible
if (!cachedCarryingCheese && cheeseCount > 0) {
  for (int i = cheeseCount; --i >= 0; ) { ... }
}
```

---

## Bytecode Measurement System

Added comprehensive bytecode tracking via `Debug7.logBytecode()`:

### King Checkpoints
- START → SENSE → THREAT → TRAPS → SPAWN → MOVE → END

### Baby Rat Checkpoints  
- START → SENSE → CHEESE → EMERGENCY → ACTION → SCORE → MOVE → END

### Usage
```java
// At each checkpoint
logBytecode(round, id, "CHECKPOINT_NAME");

// Summary at end of turn
logBytecodeSummary(round, id, "KING");
```

### Throttling
Logs only every 10 rounds to reduce overhead:
```java
private static final int BYTECODE_LOG_INTERVAL = 10;
```

---

## Results Summary

### Before Optimizations
| Unit | Typical Usage | Budget | % Used |
|------|---------------|--------|--------|
| King | ~6,000-8,000 | 20,000 | 30-40% |
| Baby Rat | ~2,500-3,500 | 17,500 | 14-20% |

### After Optimizations
| Unit | Typical Usage | Budget | % Used |
|------|---------------|--------|--------|
| King | ~4,000-5,500 | 20,000 | 20-28% |
| Baby Rat | ~1,700-2,300 | 17,500 | 10-13% |

### Key Improvements
- **TRAPS checkpoint**: 7,000-8,000 → 5,200-7,700 bytecode (15-35% reduction on complex maps)
- **THREAT checkpoint**: 1,350 → 600 bytecode (55% reduction)
- **SCORE checkpoint**: 400-800 → 200-600 bytecode (25-35% reduction)
- **MOVE checkpoint**: 150-300 → 53-135 bytecode (55-65% reduction)
- **Overall**: ~30-40% reduction in bytecode usage

### Benchmark Results by Map

| Map | King R10 | King R40 | Peak Usage |
|-----|----------|----------|------------|
| DefaultSmall | 4,040 (20%) | 5,183 (26%) | 37% |
| DefaultMedium | 5,657 (28%) | 5,183 (26%) | 35% |
| DefaultLarge | 4,040 (20%) | - | 23% |
| cheeseguardians | 6,702 (33%) | 5,192 (25%) | 33% |
| evileye | 9,144 (45%) | 6,780 (34%) | 45% |

---

## Files Modified

- `scaffold/src/ratbot7/RobotPlayer.java` - Main bot implementation
- `scaffold/src/ratbot7/Debug7.java` - Debug utilities with bytecode tracking

## Date

Optimizations implemented: January 2025
