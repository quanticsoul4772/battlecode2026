# ratbot5 Missing Features Implementation Plan

## Current State: Using 39 of ~80 Available Methods

**What we have**:
- Basic movement (turn, moveForward)
- Basic combat (attack, target prioritization)
- Basic economy (collect, deliver, cheese mines)
- Basic communication (squeaks, shared array)

**What we're MISSING from javadoc**:
- Ratnapping (carry, throw)
- Obstacle clearing (removeDirt, removeTraps)
- King distance (bottomLeftDistance)
- Trap detection before moving
- 2nd king formation
- Bytecode monitoring
- Full squeak metadata
- Defensive traps

## Phase 1: Combat Enhancements (HIGH PRIORITY)

### 1.1 Ratnapping (carryRat, throwRat)
**Location**: runAttacker(), after attack checks
```java
// After finding bestTarget:
if (bestTarget != null && bestTarget.getHealth() < 50) {
    if (rc.canCarryRat(bestTarget.getLocation())) {
        rc.carryRat(bestTarget.getLocation());
        return;
    }
}

// If carrying someone:
RobotInfo carrying = rc.getCarrying();
if (carrying != null && rc.canThrowRat()) {
    // Turn toward our king
    MapLocation ourKing = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
    Direction toKing = me.directionTo(ourKing);
    if (rc.getDirection() != toKing && rc.canTurn()) {
        rc.turn(toKing);
    } else {
        rc.throwRat(); // Yeet enemy
    }
    return;
}
```

### 1.2 King Distance (bottomLeftDistanceSquaredTo)
**Location**: runAttacker(), when attacking king
```java
if (enemy.getType().isRatKingType()) {
    MapLocation kingLoc = enemy.getLocation();
    // Use proper king distance (to nearest tile, not center)
    int dist = (int) me.bottomLeftDistanceSquaredTo(kingLoc);

    // Attack if close enough
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = new MapLocation(kingLoc.x + dx, kingLoc.y + dy);
            if (rc.canAttack(tile)) {
                rc.attack(tile);
                return;
            }
        }
    }
}
```

### 1.3 Game Mode Adaptation (isCooperation)
**Location**: runAttacker(), attack logic
```java
// Check game mode
boolean coop = rc.isCooperation();

// In cooperation: conserve cheese
// In backstab: use enhanced attacks for points
if (rc.canAttack(target)) {
    if (!coop && globalCheese > ENHANCED_THRESHOLD) {
        rc.attack(target, ENHANCED_ATTACK_CHEESE); // Backstab mode
    } else {
        rc.attack(target); // Cooperation mode
    }
}
```

## Phase 2: Movement Enhancements (HIGH PRIORITY)

### 2.1 Obstacle Clearing (removeDirt, removeRatTrap, removeCatTrap)
**Location**: moveTo(), before moving
```java
// Check ahead before moving
MapLocation ahead = rc.adjacentLocation(rc.getDirection());

// Clear obstacles
if (rc.canRemoveDirt(ahead)) {
    rc.removeDirt(ahead);
    return;
}
if (rc.canRemoveRatTrap(ahead)) {
    rc.removeRatTrap(ahead); // Clear enemy trap (50 damage saved!)
    return;
}
if (rc.canRemoveCatTrap(ahead)) {
    rc.removeCatTrap(ahead);
    return;
}
```

### 2.2 Trap Detection (senseMapInfo before moving)
**Location**: moveTo(), in normal movement section
```java
// Check for traps ahead
if (facing == desired && rc.canMoveForward()) {
    MapLocation nextLoc = rc.adjacentLocation(facing);
    if (rc.canSenseLocation(nextLoc)) {
        MapInfo nextInfo = rc.senseMapInfo(nextLoc);
        // Only avoid RAT traps (50 damage), walk through CAT traps (harmless)
        if (nextInfo.getTrap() == TrapType.RAT_TRAP) {
            // Try perpendicular instead
            for (Direction alt : new Direction[]{desired.rotateLeft(), desired.rotateRight()}) {
                if (rc.canTurn()) {
                    rc.turn(alt);
                    return;
                }
            }
        }
    }
    rc.moveForward();
}
```

## Phase 3: Economy Enhancements (MEDIUM PRIORITY)

### 3.1 Defensive Traps (placeCatTrap, placeRatTrap)
**Location**: runKing(), after spawning
```java
// Place traps for defense
private static int trapCount = 0;

if (spawnCount >= 10 && trapCount < 5 && cheese > 300) {
    for (Direction dir : Direction.allDirections()) {
        MapLocation loc = me.add(dir).add(dir);
        if (rc.canPlaceCatTrap(loc)) {
            rc.placeCatTrap(loc);
            trapCount++;
            return;
        }
    }
}
```

### 3.2 King Movement (canMoveForward for king)
**Location**: runKing(), at end
```java
// Mobile king (harder to kill)
MapLocation ahead = rc.adjacentLocation(rc.getDirection());

// Clear obstacles
if (rc.canRemoveDirt(ahead)) {
    rc.removeDirt(ahead);
}

// Move forward (every other round)
if (round % 2 == 0 && rc.canMoveForward()) {
    rc.moveForward();
} else if (rc.canTurn()) {
    // Turn to explore
    if (round % 10 == 0) {
        Direction newDir = Direction.allDirections()[round / 10 % 8];
        rc.turn(newDir);
    }
}
```

## Phase 4: Performance & Safety (MEDIUM PRIORITY)

### 4.1 Bytecode Monitoring (Clock.getBytecodesLeft)
**Location**: runAttacker() and runCollector(), at start
```java
// Check bytecode budget
int bytecodeLeft = Clock.getBytecodesLeft();
int bytecodeLimit = rc.getType().getBytecodeLimit();

// If < 10% budget, skip expensive operations
if (bytecodeLeft < bytecodeLimit * 0.1) {
    // Emergency: just move/attack, skip squeaks/scanning
    simpleAction(rc);
    return;
}
```

### 4.2 Full Squeak Metadata (getSenderID, getRound, getSource)
**Location**: collect(), when reading squeaks
```java
// Already using getBytes(), add:
Message freshestMine = null;
int newestRound = 0;

for (Message msg : squeaks) {
    // Skip own squeaks
    if (msg.getSenderID() == rc.getID()) continue;

    // Find freshest
    if (msg.getRound() > newestRound) {
        int type = (msg.getBytes() >> 28) & 0xF;
        if (type == 3) { // Mine squeak
            freshestMine = msg;
            newestRound = msg.getRound();
        }
    }
}

// Go to squeak source (where ally found mine)
if (freshestMine != null) {
    nearest = freshestMine.getSource();
}
```

## Phase 5: Advanced Features (LOW PRIORITY)

### 5.1 Second King Formation (becomeRatKing, canBecomeRatKing)
**Location**: runBabyRat(), check before role execution
```java
// Check for formation signal
int formSignal = rc.readSharedArray(4); // Slot 4 = formation command

if (formSignal == 1 && myRole == 1) { // Collectors only
    // Move to formation point
    MapLocation formPoint = new MapLocation(ourKing.x + 5, ourKing.y);
    moveTo(rc, formPoint);

    // Try to form king
    if (rc.canBecomeRatKing()) {
        rc.becomeRatKing(); // Become 2nd king!
    }
    return;
}

// King sets signal when ready:
if (round > 50 && cheese > 800 && spawnCount >= 14) {
    rc.writeSharedArray(4, 1); // FORM NOW
}
```

### 5.2 Additional Sensing Methods
**From javadoc, potentially useful**:
- `senseRobot(int id)` - Get specific robot by ID
- `senseRobotAtLocation(MapLocation)` - Check specific tile
- `isLocationOccupied(MapLocation)` - Quick occupation check
- `sensePassability(MapLocation)` - Check if can move there
- `onTheMap(MapLocation)` - Validate positions

## Implementation Order

**TODAY (Jan 9)**:
1. Phase 1.1: Ratnapping (30 min)
2. Phase 1.2: King distance (15 min)
3. Phase 2.1: Obstacle clearing (30 min)
4. Phase 2.2: Trap detection (30 min)

**TOMORROW (Jan 10)**:
5. Phase 1.3: Game mode adaptation (15 min)
6. Phase 3.1: Defensive traps (20 min)
7. Phase 4.1: Bytecode monitoring (20 min)
8. Phase 4.2: Full squeak metadata (30 min)

**WEEKEND (Jan 11-12)**:
9. Phase 3.2: King movement (20 min)
10. Phase 5.1: 2nd king formation (60 min)
11. Testing and tuning (all day)

## Success Criteria

**After Phase 1-2** (Critical Features):
- [ ] Ratnapping working (grab and throw enemies)
- [ ] King attacks accurate (bottomLeft distance)
- [ ] Obstacles cleared (traps, dirt)
- [ ] No deaths to enemy traps

**After Phase 3-4** (Performance):
- [ ] Defensive traps placed
- [ ] Bytecode never times out
- [ ] Squeak coordination optimal
- [ ] Game mode adaptation working

**After Phase 5** (Advanced):
- [ ] 2nd king forms successfully
- [ ] Multiple kings operational
- [ ] All javadoc features used

## Validation

**Test after each phase**:
1. vs ratbot5 (mirror) - features don't break
2. vs ratbot4 - improvements help
3. vs lectureplayer - competitive

**Metrics to track**:
- King kills (faster with bottomLeft distance?)
- Collector deaths (fewer with obstacle clearing?)
- Economy (better with trap protection?)
- Late game (no freeze with bytecode monitoring?)

---

**Ready to implement Phase 1 (Combat Enhancements)?**
