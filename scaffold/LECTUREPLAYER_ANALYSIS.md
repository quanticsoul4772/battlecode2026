# Lectureplayer Analysis

**Source**: https://github.com/battlecode/battlecode26-lectureplayer
**Lines**: 226
**Purpose**: Reference implementation for learning

---

## Working API Methods (from lectureplayer)

### Spawning ✓ WORKS
```java
rc.getCurrentRatCost()           // Get spawn cost
rc.canBuildRat(MapLocation loc)  // Check if can spawn
rc.buildRat(MapLocation loc)     // Spawn baby rat
```

**Note**: Method is `buildRat()` not `buildRobot()`!

### Cheese ✓ WORKS
```java
rc.getAllCheese()                // Total cheese (different from getGlobalCheese()?)
rc.getRawCheese()                // Carried cheese
rc.canPickUpCheese(loc)          // Check pickup
rc.pickUpCheese(loc)             // Collect
rc.canTransferCheese(loc, amt)   // Check transfer
rc.transferCheese(loc, amt)      // Transfer to king
```

### Movement ✓ WORKS
```java
rc.canMove(Direction dir)        // Check if can move in direction
rc.move(Direction dir)           // Move in any direction (not just forward!)
rc.canMoveForward()              // Forward check
rc.moveForward()                 // Forward move
rc.canTurn()                     // Turn check
rc.turn(Direction dir)           // Turn to face
```

**Key Discovery**: `rc.move(Direction)` works - can strafe!

### Dirt ✓ WORKS
```java
rc.canRemoveDirt(MapLocation loc)
rc.removeDirt(MapLocation loc)
```

### Communication ✓ WORKS
```java
rc.writeSharedArray(int index, int value)
rc.readSharedArray(int index)
```

---

## Lectureplayer Strategy

### State Machine

```
INITIALIZE → (early game or cheap spawn) → FIND_CHEESE
          → (late game or expensive spawn) → EXPLORE_AND_ATTACK

FIND_CHEESE → (collected >= 10) → RETURN_TO_KING
RETURN_TO_KING → (transferred) → FIND_CHEESE

BUILD_TRAPS → (cat approaching) [NOT YET IMPLEMENTED]
EXPLORE_AND_ATTACK → (general behavior) [NOT YET IMPLEMENTED]
```

### Rat King Behavior

**Spawning Logic**:
```java
if (currentCost <= 10 || rc.getAllCheese() > currentCost + 2500) {
    // Spawn if cheap OR if we have surplus (>2500 buffer)
    rc.buildRat(loc);
}
```

**Communication**:
- Writes king location to shared array slots 0-1 (x, y)
- Baby rats read this to navigate back

**Movement**:
- Random movement (moveRandom)
- Can dig dirt if blocking

### Baby Rat Behavior

**FIND_CHEESE**:
- Scan nearby map infos for cheese
- Turn toward cheese
- Pick up when adjacent
- Collect until >= 10 cheese
- Then switch to RETURN_TO_KING

**RETURN_TO_KING**:
- Read king location from shared array (slots 0, 1)
- Navigate toward king
- Remove dirt if blocking
- Transfer cheese when in range
- Switch to FIND_CHEESE when transferred

**EXPLORE_AND_ATTACK**:
- Used when spawn cost high (late game)
- TODO in lectureplayer

---

## Key Insights

### API Differences from Javadoc

**Spawn API**:
- Method: `buildRat(loc)` not `buildRobot(loc)`
- Cost query: `getCurrentRatCost()` not manual calculation

**Movement API**:
- Can use `move(Direction)` - strafe works!
- Not limited to `moveForward()`

**Cheese API**:
- `getAllCheese()` exists (check if same as `getGlobalCheese()`)

### Strategy Differences from Our Bot

**Lectureplayer**:
- Spawns aggressively (when cost <= 10 OR surplus > 2500)
- Collects only 10 cheese per trip (low threshold)
- Uses shared array for king location
- Random movement

**Our Bot**:
- Doesn't spawn yet (API confusion)
- Collects 20 cheese per trip (higher threshold)
- Uses sensing to find king (no shared array yet)
- State machine movement

---

## What to Copy

### 1. Spawn API (Immediate)

Update RatKing.java:
```java
if (rc.getCurrentRatCost() <= 10 || rc.getAllCheese() > currentCost + 2500) {
    if (rc.canBuildRat(spawnLoc)) {
        rc.buildRat(spawnLoc);
    }
}
```

### 2. Movement Options

Can use `rc.move(Direction)` for better pathfinding:
```java
// Instead of turn + moveForward:
Direction target = me.directionTo(goal);
if (rc.canMove(target)) {
    rc.move(target);
}
```

### 3. Shared Array for King Location

King writes location, rats read it:
```java
// King
rc.writeSharedArray(0, rc.getLocation().x);
rc.writeSharedArray(1, rc.getLocation().y);

// Baby rat
MapLocation kingLoc = new MapLocation(
    rc.readSharedArray(0),
    rc.readSharedArray(1)
);
```

### 4. Dirt Removal

Can clear obstacles:
```java
if (rc.canRemoveDirt(nextLoc)) {
    rc.removeDirt(nextLoc);
}
```

---

## Integration Steps

1. Update RatKing.java with `buildRat()` API
2. Update BabyRat.java to use `move(Direction)` 
3. Add shared array communication
4. Test spawning actually works
5. Refine cheese collection threshold

---

## Match Performance

**ratbot4 vs lectureplayer**: Won at round 588
- Much longer than vs examplefuncsplayer (232)
- Lectureplayer spawns units and collects cheese
- Still won (need to analyze why)

**Next**: Analyze full match logs to understand lectureplayer's performance
