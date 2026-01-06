# Integration Guide - Using Pre-Built Modules

**When scaffold releases**: Follow this guide to integrate algorithm modules in ~15 minutes

---

## Step 1: Prepare Modules (5 minutes)

### Remove Placeholder Types

Each algorithm file has placeholder types at the bottom. Delete these sections:

**In Vision.java, Geometry.java, DirectionUtil.java, Pathfinding.java, GameTheory.java**:

Delete from `// Placeholder types` to end of file.

Remove lines like:
```java
// Placeholder types for standalone compilation
enum UnitType { BABY_RAT, RAT_KING, CAT }
enum Direction { ... }
class MapLocation { ... }
```

### Add Real Imports

At the top of each file, add:
```java
import battlecode.common.*;
```

---

## Step 2: Copy to Scaffold (2 minutes)

```bash
cd C:/Development/Projects/battlecode2026

# Copy algorithm modules to scaffold
cp src/algorithms/*.java <scaffold-path>/src/ratbot/

# Copy mock framework for testing (optional)
cp test/mock/*.java <scaffold-path>/test/mock/
cp test/algorithms/*Test.java <scaffold-path>/test/algorithms/
```

---

## Step 3: Use in Bot Code (5 minutes)

### In RobotPlayer.java or BabyRat.java

```java
package ratbot;

import battlecode.common.*;

public class BabyRat {

    // Use Vision module for sensing
    public static RobotInfo findNearestVisibleEnemy(RobotController rc) {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        MapLocation myLoc = rc.getLocation();
        Direction myFacing = rc.getDirection();

        // Filter to visible (Vision module)
        RobotInfo[] visible = filterVisible(enemies, myLoc, myFacing);

        // Find closest (Geometry module)
        if (visible.length == 0) return null;

        MapLocation[] locs = extractLocations(visible);
        MapLocation nearest = Geometry.closest(myLoc, locs);

        return getRobotAt(visible, nearest);
    }

    // Use Pathfinding module for navigation
    public static Direction navigateTo(RobotController rc, MapLocation target) {
        MapLocation current = rc.getLocation();

        // Build passability map
        boolean[][] passable = buildPassabilityMap(rc);

        // Get path (Pathfinding module)
        Direction path = Pathfinding.bfs(
            current,
            target,
            passable,
            rc.getMapWidth(),
            rc.getMapHeight()
        );

        return path;
    }

    // Use DirectionUtil for facing optimization
    public static void moveOptimally(RobotController rc, Direction targetDir)
        throws GameActionException {

        Direction currentFacing = rc.getDirection();

        // Check if need to turn (DirectionUtil module)
        int turns = DirectionUtil.turnsToFace(currentFacing, targetDir);

        if (turns > 0 && rc.isTurningReady()) {
            rc.turn(targetDir);
        } else if (rc.isMovementReady() && rc.canMove(targetDir)) {
            rc.move(targetDir);
        }
    }

    // Use GameTheory for strategic decisions
    public static boolean shouldBackstabNow(RobotController rc) {
        // Read game state from shared array or tracking
        int ourCatDamage = readSharedArray(rc, 29);
        int enemyCatDamage = readSharedArray(rc, 30);
        // ... read other values

        // Make decision (GameTheory module)
        return GameTheory.shouldBackstab(
            ourCatDamage, enemyCatDamage,
            ourKings, enemyKings,
            ourCheese, enemyCheese,
            10 // confidence threshold
        );
    }

    // Helpers
    private static RobotInfo[] filterVisible(RobotInfo[] robots, MapLocation myLoc, Direction myFacing) {
        RobotInfo[] result = new RobotInfo[robots.length];
        int count = 0;

        for (int i = robots.length; --i >= 0;) {
            if (Vision.canSee(myLoc, myFacing, robots[i].location, UnitType.BABY_RAT)) {
                result[count++] = robots[i];
            }
        }

        RobotInfo[] trimmed = new RobotInfo[count];
        System.arraycopy(result, 0, trimmed, 0, count);
        return trimmed;
    }
}
```

---

## Step 4: Test Integration (3 minutes)

```bash
cd <scaffold-path>

# Compile
./gradlew build

# Run tests
./gradlew test

# Run a match
./gradlew run
```

**Expected**: All modules compile, tests pass, bot runs

---

## Module Usage Patterns

### Vision Module

```java
// Check if enemy is visible
Direction myFacing = rc.getDirection();
MapLocation enemyLoc = enemy.location;
boolean canSee = Vision.inCone90(rc.getLocation(), myFacing, enemyLoc);

// Get all visible tiles (for area search)
MapLocation[] tiles = Vision.getVisibleTiles(
    rc.getLocation(),
    rc.getDirection(),
    20, // radiusSquared for baby rat
    90, // cone angle
    rc.getMapWidth(),
    rc.getMapHeight()
);

// Check if unit can see target (auto-detects cone angle/radius)
boolean visible = Vision.canSee(
    rc.getLocation(),
    rc.getDirection(),
    target,
    rc.getType()
);
```

### Geometry Module

```java
// Find nearest cheese mine
MapLocation[] mines = getKnownMines();
MapLocation nearest = Geometry.closest(rc.getLocation(), mines);

// Check if in range for transfer
boolean inRange = Geometry.withinRange(
    rc.getLocation(),
    kingLocation,
    16 // sqrt(16) = 4 tile range
);

// Fast distance for heuristics
int dx = target.x - current.x;
int dy = target.y - current.y;
int approxDist = Geometry.approximateDistance(dx, dy);
```

### DirectionUtil Module

```java
// Compute turn cost to face enemy
Direction toEnemy = rc.getLocation().directionTo(enemy.location);
int turnCost = DirectionUtil.turnsToFace(rc.getDirection(), toEnemy);

// Get optimal movement plan
DirectionUtil.MovementPlan plan = DirectionUtil.optimalMovement(
    rc.getDirection(),
    desiredDirection
);

if (plan.needsTurn && rc.isTurningReady()) {
    rc.turn(plan.targetFacing);
} else if (rc.isMovementReady()) {
    rc.move(plan.targetFacing);
}

// Try directions in preference order
Direction[] ordered = DirectionUtil.orderedDirections(targetDir);
for (Direction dir : ordered) {
    if (rc.canMove(dir)) {
        rc.move(dir);
        break;
    }
}
```

### Pathfinding Module

```java
// BFS for shortest path
boolean[][] passable = buildPassabilityMap(rc);
Direction next = Pathfinding.bfs(
    rc.getLocation(),
    target,
    passable,
    rc.getMapWidth(),
    rc.getMapHeight()
);

// Bug2 for obstacle avoidance (lower bytecode)
Direction dir = Pathfinding.bug2(
    rc.getLocation(),
    target,
    (d) -> rc.canMove(d) // Lambda for movement check
);

// Greedy (cheapest)
Direction greedy = Pathfinding.greedy(rc.getLocation(), target);
```

### GameTheory Module

```java
// Read tracked game state
int ourCatDamage = readSharedArray(29);
int enemyCatDamage = readSharedArray(30);
int ourKings = countAllyKings(rc);
int enemyKings = countEnemyKings(rc);

// Decide on backstab
GameTheory.GameState state = new GameTheory.GameState(
    rc.getRoundNum(),
    ourCatDamage, enemyCatDamage,
    ourKings, enemyKings,
    ourCheese, enemyCheese
);

GameTheory.BackstabRecommendation rec = GameTheory.evaluate(state);

if (rec.shouldBackstab) {
    // Initiate backstab by attacking enemy rat
    System.out.println("BACKSTAB:" + rc.getRoundNum() + ":" + rec.reasoning);
}

// Decide if cheese enhancement worth it for bite
if (enemy.health <= 20) {
    int cheeseToSpend = 32; // ceil(log2(32)) = 5 extra damage
    if (GameTheory.worthEnhancingBite(cheeseToSpend, enemy.health, 10)) {
        rc.attack(enemy.location, cheeseToSpend);
    }
}
```

---

## Testing Without Engine

### Using Mock Framework

```java
// Create mock game
MockGameState game = new MockGameState(30, 30);

// Add your rat
MockRobotController myRat = game.addRobot(
    new MapLocation(15, 15),
    Direction.NORTH,
    UnitType.BABY_RAT,
    Team.A
);

// Add cheese
game.addCheese(new MapLocation(15, 18), 5);

// Test collection logic
Direction towardCheese = Pathfinding.greedy(myRat.getLocation(), new MapLocation(15, 18));
myRat.move(towardCheese);
myRat.stepRound();

myRat.move(Direction.NORTH);
myRat.stepRound();

myRat.move(Direction.NORTH);
myRat.stepRound();

myRat.pickUpCheese(new MapLocation(15, 18));

assertThat(myRat.getRawCheese()).isEqualTo(5);
```

---

## Common Patterns

### Cheese Collection

```java
// Find nearest visible cheese
MapLocation[] visibleTiles = Vision.getVisibleTiles(
    rc.getLocation(), rc.getDirection(), 20, 90, rc.getMapWidth(), rc.getMapHeight()
);

MapLocation nearestCheese = null;
int nearestDist = Integer.MAX_VALUE;

for (MapLocation tile : visibleTiles) {
    if (rc.senseMapInfo(tile).hasCheese()) { // Placeholder - check actual API
        int dist = rc.getLocation().distanceSquaredTo(tile);
        if (dist < nearestDist) {
            nearestDist = dist;
            nearestCheese = tile;
        }
    }
}

if (nearestCheese != null) {
    Direction path = Pathfinding.bfs(...);
    // Move toward cheese
}
```

### Combat Targeting

```java
// Find best attack target
RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

RobotInfo bestTarget = null;
int bestScore = Integer.MIN_VALUE;

for (RobotInfo enemy : enemies) {
    // Check if visible
    if (!Vision.canSee(rc.getLocation(), rc.getDirection(), enemy.location, rc.getType())) {
        continue;
    }

    // Check if in attack range (adjacent)
    if (rc.getLocation().distanceSquaredTo(enemy.location) > 2) {
        continue;
    }

    int score = 0;

    // Prioritize cats in cooperation mode
    if (enemy.type == UnitType.CAT) score += 1000;

    // Prioritize low HP (finish kills)
    score += (100 - enemy.health);

    // Prioritize close targets
    score += (20 - rc.getLocation().distanceSquaredTo(enemy.location));

    if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
    }
}

if (bestTarget != null && rc.canAttack(bestTarget.location)) {
    // Decide if cheese enhancement worth it
    if (GameTheory.worthEnhancingBite(32, bestTarget.health, 10)) {
        rc.attack(bestTarget.location, 32);
    } else {
        rc.attack(bestTarget.location);
    }
}
```

### King Feeding

```java
// Track global cheese continuously
int globalCheese = rc.getGlobalCheese();
int kingCount = countAllyKings(rc);
int cheesePerRound = kingCount * 3;
int roundsOfCheese = globalCheese / cheesePerRound;

if (roundsOfCheese < 33) {
    // EMERGENCY: Less than 100 cheese (33 rounds buffer)
    // All baby rats: return to kings with cheese immediately
    // Stop spawning
    System.out.println("EMERGENCY:LOW_CHEESE:" + globalCheese);
}
```

---

## Troubleshooting

### Compilation Errors

**Error**: `cannot find symbol: MapLocation`
**Fix**: Make sure you removed placeholder types and added `import battlecode.common.*;`

**Error**: `package algorithms does not exist`
**Fix**: Ensure modules are in correct package directory matching scaffold structure

### Test Failures

**Error**: Tests fail with real API
**Fix**: Mock may not perfectly match real API - update mock or disable incompatible tests

**Error**: Vision cone calculations wrong
**Fix**: Verify vision cone angle interpretation matches game engine

---

## Performance Notes

### Bytecode Costs (estimate)

Based on optimizations:
- **Vision.inCone90()**: ~50 bytecode
- **Geometry.closest()**: ~100 bytecode per item
- **Pathfinding.bfs()**: ~2,000-5,000 bytecode (depends on map)
- **Bug2**: ~200-500 bytecode (depends on obstacles)
- **GameTheory.shouldBackstab()**: ~100 bytecode

**All modules use**:
- Backward loops
- Static arrays (pre-allocated)
- Primitive types only
- Minimal allocations

---

## What to Build Next (After Integration)

### Immediate (Hours 1-8)

1. **RobotPlayer.java** - Main loop
2. **BabyRat.java** - Baby rat state machine
3. **RatKing.java** - Rat king behavior
4. **Comm.java** - Communication protocol encoding

### Core Systems (Day 2-3)

1. **CheeseCollection.java** - Collection and delivery
2. **CatAvoidance.java** - Cat tracking and retreat
3. **Combat.java** - Attack decisions and micro
4. **KingFeeding.java** - Prevent starvation

### Advanced (Day 4+)

1. **MultiKing.java** - Multi-king coordination
2. **Backstab.java** - Backstab trigger and behavior
3. **Traps.java** - Trap placement strategy
4. **Ratnapping.java** - Carry/throw tactics

---

## Quick Reference: Module APIs

### Vision
- `Vision.inCone90(observer, facing, target)` → boolean
- `Vision.canSee(observer, facing, target, unitType)` → boolean
- `Vision.getVisibleTiles(center, facing, radius, angle, w, h)` → MapLocation[]

### Geometry
- `Geometry.closest(reference, locations)` → MapLocation
- `Geometry.manhattanDistance(a, b)` → int
- `Geometry.withinRange(center, target, radiusSq)` → boolean

### DirectionUtil
- `DirectionUtil.turnsToFace(current, target)` → int (0-2)
- `DirectionUtil.optimalMovement(facing, desired)` → MovementPlan
- `DirectionUtil.orderedDirections(preferred)` → Direction[]

### Pathfinding
- `Pathfinding.bfs(start, target, passable, w, h)` → Direction
- `Pathfinding.bug2(current, target, canMoveFn)` → Direction
- `Pathfinding.greedy(current, target)` → Direction

### GameTheory
- `GameTheory.shouldBackstab(catDmg, eCatDmg, kings, eKings, cheese, eCheese, threshold)` → boolean
- `GameTheory.evaluate(gameState)` → BackstabRecommendation
- `GameTheory.worthEnhancingBite(cheese, targetHP, baseDmg)` → boolean

---

**Integration time**: ~15 minutes
**Ready to build**: Bot behavior on top of tested algorithms
