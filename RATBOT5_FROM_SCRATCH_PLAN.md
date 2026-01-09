# ratbot5 - Built From Scratch Using Javadoc

## Starting Point: BLANK FILE

**NOT copying ratbot4** - building fresh with proper API usage

**Reference**: Battlecode javadoc (all classes, all methods)

## Core API Classes (Our Foundation)

### 1. RobotController (Primary API)
**Movement** (proper usage from javadoc):
- `move(Direction)` - Move in any direction (18 cd for strafe, 10 for forward)
- `moveForward()` - Move in facing direction (10 cd)
- `turn(Direction)` - Turn to face direction (10 cd)
- `canMove(Direction)` - Check if can move
- `canMoveForward()` - Check forward
- `canTurn()` - Check if can turn

**Combat** (proper usage from javadoc):
- `attack(MapLocation)` - 10 damage base
- `attack(MapLocation, int cheese)` - 10 + ceil(log2(cheese)) damage
- `canAttack(MapLocation)` - Check if can attack
- `canAttack(MapLocation, int)` - Check enhanced attack

**Sensing** (proper usage from javadoc):
- `senseNearbyRobots(int radius, Team)` - Get robots within radius
- `senseMapInfo(MapLocation)` - Get tile info
- `getAllLocationsWithinRadiusSquared(center, radius)` - Scan area

**Actions** (proper usage from javadoc):
- `carryRat(MapLocation)` - Grab rat
- `throwRat()` - Throw carried rat
- `disintegrate()` - Self-destruct
- `pickUpCheese(MapLocation)` - Collect cheese
- `transferCheese(MapLocation, int)` - Deliver to king
- `placeCatTrap(MapLocation)` - Build trap
- `removeDirt(MapLocation)` - Clear obstacle
- `removeRatTrap(MapLocation)` - Clear enemy trap

**State** (proper usage from javadoc):
- `isMovementReady()` - Can move this round
- `isActionReady()` - Can act this round
- `getMovementCooldownTurns()` - Cooldown remaining
- `getActionCooldownTurns()` - Action cooldown

### 2. MapLocation (Position Handling)
**Critical methods**:
- `bottomLeftDistanceSquaredTo(MapLocation)` - Distance to multi-part robots (KINGS!)
- `distanceSquaredTo(MapLocation)` - Distance to single tile
- `directionTo(MapLocation)` - Which direction to face
- `add(Direction)` - Move one tile
- `isAdjacentTo(MapLocation)` - Check if adjacent

### 3. Direction (Movement Vectors)
**Built-in methods**:
- `Direction.allDirections()` - Get all 8 directions
- `direction.rotateLeft()` - Rotate counter-clockwise
- `direction.rotateRight()` - Rotate clockwise
- `direction.opposite()` - Opposite direction
- `Direction.fromDelta(dx, dy)` - Create from x/y offsets

### 4. Clock (Performance Monitoring)
**Critical for bytecode**:
- `Clock.getBytecodesLeft()` - Remaining budget
- `Clock.getBytecodeNum()` - Used this round
- `Clock.yield()` - End turn

### 5. Message (Communication)
**Squeak metadata**:
- `msg.getBytes()` - Message content
- `msg.getSenderID()` - Who sent it
- `msg.getRound()` - When sent
- `msg.getSource()` - Where from

### 6. GameConstants (Game Rules)
**Critical constants**:
- `COOLDOWN_LIMIT` = 10 (can't act if cd >= 10)
- `RATKING_CHEESE_CONSUMPTION` = 3 (king eats per round)
- `SQUEAK_RADIUS_SQUARED` = 256 (squeak range)
- `MOVE_STRAFE_COOLDOWN` = 18 (strafe penalty)

## ratbot5 Architecture (From Scratch)

### Movement System (Built Correctly)

**NO traffic yielding** (causes deadlock)
**NO single position stuck tracking** (insufficient)
**YES position history** (detect oscillation)
**YES escalating recovery** (force movement when stuck)

```java
// Position history (detect oscillation, not just stuck)
private static MapLocation[] positionHistory = new MapLocation[5];
private static int historyIndex = 0;

// Update history
void updateHistory(MapLocation current) {
    positionHistory[historyIndex] = current;
    historyIndex = (historyIndex + 1) % 5;
}

// Check if stuck (no progress in 5 rounds)
boolean isStuck() {
    for (int i = 0; i < 5; i++) {
        if (positionHistory[i] != null && !positionHistory[i].equals(me)) {
            return false; // Made progress
        }
    }
    return true; // All positions same = stuck!
}

// FORCE movement when stuck
if (isStuck()) {
    // Calculate exact direction using fromDelta
    int dx = Math.max(-1, Math.min(1, target.x - me.x));
    int dy = Math.max(-1, Math.min(1, target.y - me.y));
    Direction exact = Direction.fromDelta(dx, dy);

    // FORCE turn (ignore constraints)
    if (exact != Direction.CENTER && rc.canTurn()) {
        rc.turn(exact);
        clearHistory(); // Reset after forcing
    }
}

// Normal movement (turn + forward, not strafe)
Direction desired = me.directionTo(target);
if (rc.getDirection() != desired && rc.canTurn()) {
    rc.turn(desired);
} else if (rc.canMoveForward()) {
    moveForward();
}
```

### Combat System (Built Correctly)

**Use bottomLeftDistanceSquaredTo for kings**:
```java
// For enemy king
if (enemy.getType().isRatKingType()) {
    int dist = (int) me.bottomLeftDistanceSquaredTo(enemy.getLocation());
    // Now distance is to NEAREST king tile, not center!

    // Attack ALL 9 king tiles
    MapLocation center = enemy.getLocation();
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = new MapLocation(center.x + dx, center.y + dy);
            if (rc.canAttack(tile)) {
                rc.attack(tile); // Attack whichever tile is adjacent
                return;
            }
        }
    }
}
```

**Prioritize targets by cheese**:
```java
// Find best target (most cheese)
RobotInfo bestTarget = null;
int maxCheese = 0;

for (RobotInfo enemy : enemies) {
    int cheese = enemy.getRawCheeseAmount();
    if (cheese > maxCheese) {
        maxCheese = cheese;
        bestTarget = enemy;
    }
}

// Attack collector carrying cheese (disrupt economy)
if (bestTarget != null && rc.canAttack(bestTarget.getLocation())) {
    rc.attack(bestTarget.getLocation());
}
```

### Squeak System (Built Correctly)

**Use all Message metadata**:
```java
// Send squeak with proper encoding
int squeak = (type << 28) | (y << 16) | (x << 4) | extra;
rc.squeak(squeak);

// Read squeaks with full metadata
Message[] squeaks = rc.readSqueaks(-1); // Last 5 rounds
Message freshest = null;
int newestRound = 0;

for (Message msg : squeaks) {
    // Skip own messages
    if (msg.getSenderID() == rc.getID()) continue;

    // Find freshest
    if (msg.getRound() > newestRound) {
        freshest = msg;
        newestRound = msg.getRound();
    }
}

// Use squeak source (where ally saw enemy)
if (freshest != null) {
    MapLocation combatZone = freshest.getSource();
    moveTo(combatZone); // Go where action is!
}
```

### Spawning System (Built Correctly)

**Know exact spawn constraints**:
```java
// From GameConstants:
// BUILD_ROBOT_RADIUS_SQUARED = 4 (max 2 tiles from king center)
// BUILD_ROBOT_BASE_COST = 10
// BUILD_ROBOT_COST_INCREASE = 10 (every 4 rats)
// NUM_ROBOTS_FOR_COST_INCREASE = 4

int cost = rc.getCurrentRatCost(); // API gives exact cost
int reserve = GameConstants.RATKING_CHEESE_CONSUMPTION * 50; // 50 rounds survival

// Try all directions at distance 2
for (Direction dir : Direction.allDirections()) {
    MapLocation loc = rc.getLocation().add(dir).add(dir);
    if (rc.canBuildRat(loc)) {
        rc.buildRat(loc);
        return;
    }
}
```

## ratbot5 Implementation Phases

### Phase 1: Movement System (From Javadoc)
1. Create blank ratbot5/RobotPlayer.java
2. Implement movement using proper API methods
3. Position history (5 rounds)
4. Direction.fromDelta() for precise calculation
5. NO traffic yielding
6. Force movement when stuck 3 rounds

**Test**: Do rats move consistently? No freeze?

### Phase 2: Combat System (From Javadoc)
1. Use bottomLeftDistanceSquaredTo for kings
2. Attack all 9 king tiles
3. Prioritize by getRawCheeseAmount
4. Ratnapping with proper carryRat/throwRat
5. isCooperation() for strategy switching

**Test**: Do attacks land? King kills fast?

### Phase 3: Communication (From Javadoc)
1. Squeaks with all Message metadata
2. Shared array with proper slots
3. King writes positions
4. Baby rats read and coordinate

**Test**: Do rats coordinate? Find enemies faster?

### Phase 4: Economy (From Javadoc)
1. Collectors use proper sensing
2. Instant replacement when < 4 visible
3. Cheese mine tracking
4. Transfer using proper range checks

**Test**: Does economy sustain? King survives?

## Code Structure (Clean Slate)

```java
package ratbot5;

import battlecode.common.*;

public class RobotPlayer {
    // === ENTRY POINT ===
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                if (rc.getType().isRatKingType()) {
                    runKing(rc);
                } else {
                    runBabyRat(rc);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // === KING (spawning, coordination) ===
    private static void runKing(RobotController rc) {
        // Spawn rats
        // Track enemies
        // Coordinate team
    }

    // === BABY RAT (attack or collect) ===
    private static void runBabyRat(RobotController rc) {
        // Assign role (even=attack, odd=collect)
        // Execute role behavior
    }

    // === MOVEMENT (intelligent, not broken) ===
    private static void moveTo(RobotController rc, MapLocation target) {
        // Position history (5 rounds)
        // Detect stuck (no progress)
        // Force movement when stuck
        // NO yielding
    }

    // === CONSTANTS (from GameConstants) ===
    private static final int COOLDOWN_LIMIT = GameConstants.COOLDOWN_LIMIT;
    // etc.
}
```

**Line budget**: ~1000 lines total
- Movement: ~200 lines (intelligent system)
- Combat: ~150 lines (proper API usage)
- Economy: ~100 lines
- Communication: ~100 lines
- King: ~150 lines
- Constants/helpers: ~100 lines
- Squeak encoding: ~50 lines
- Bug2 (optional): ~150 lines

## Validation Before Implementation

**Questions to answer**:
1. Start from blank file? (YES)
2. Use javadoc as primary reference? (YES)
3. No copying ratbot4 code? (YES - build fresh)
4. Focus on movement first? (YES - it's the core problem)

Ready to create blank ratbot5/RobotPlayer.java and build it correctly from the javadoc?
