# RATBOT4 Advanced Features (Competitive Advantage)

## 1. Multiple Rat Kings (UNIQUE STRATEGY)

**No one else is using this!** - Competitive advantage

### When to Form 2nd King:
- Round 75+ (after early rush survived)
- Conditions:
  - 10+ rats alive
  - Global cheese > 1,000
  - 7+ rats in close proximity
  - Safe location identified

### Benefits:
- **Redundancy**: If one king dies, we survive
- **Double spawn rate**: Two kings = 2 rats per round
- **Distributed defense**: Protect multiple areas
- **Combined HP**: 500 HP kings harder to kill than 100 HP rats

### Implementation:
```java
// King checks if should signal formation
if (round > 75 && globalCheese > 1000) {
    RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
    if (count baby rats > 10) {
        // Write formation signal to shared array
        rc.writeSharedArray(60, 1); // Signal = form 2nd king
    }
}

// Attackers check for formation signal
if (rc.readSharedArray(60) == 1) {
    // Move to form 3x3 with 6 other rats
    // Center rat calls becomeRatKing()
    if (rc.canBecomeRatKing()) {
        rc.becomeRatKing();
        // This rat is now a king!
    }
}
```

### 2nd King Strategy:
- **Location**: Opposite corner from 1st king
- **Role**: Spawn collectors only (economy focus)
- **Defense**: 1st king handles combat, 2nd king handles economy

## 2. Dirt Wall Defense

### Build Defensive Perimeter:
```java
// King builds 3x3 wall around itself (rounds 15-20)
// Costs 90 cheese (9 tiles × 10 cheese)
if (round >= 15 && round <= 20 && wallCount < 9) {
    for (int dx = -2; dx <= 2; dx += 2) {
        for (int dy = -2; dy <= 2; dy += 2) {
            MapLocation wallLoc = kingLoc.add(dx, dy);
            if (rc.canPlaceDirt(wallLoc)) {
                rc.placeDirt(wallLoc);
                wallCount++;
                return;
            }
        }
    }
}
```

### Benefits:
- Blocks enemy rush from reaching king
- Creates choke points for defense
- Forces enemies into predictable paths
- Low cost (10 cheese per tile)

### Weaknesses:
- Blocks our own rats from accessing king (need gaps!)
- 25 cooldown to remove if needed
- Enemy can also remove dirt

### Improved Design:
Build walls with GAPS for our rats:
```
. W . W .
W . . . W
. . K . .
W . . . W
. W . W .
```

4 walls, 4 gaps → our rats can deliver, enemies slowed

## 3. Cheese Mine Coordination

### Track All Cheese Mines:
```java
// King scans map, writes mine locations
MapLocation[] allLocs = rc.getAllLocationsWithinRadiusSquared(kingLoc, 625); // 25 tiles
int mineCount = 0;
for (MapLocation loc : allLocs) {
    if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        if (info.hasCheeseMine() && mineCount < 10) {
            rc.writeSharedArray(40 + mineCount * 2, loc.x);
            rc.writeSharedArray(41 + mineCount * 2, loc.y);
            mineCount++;
        }
    }
}
```

### Collector Assignment:
```java
// Each collector claims a mine (avoid competition)
int myMineIndex = (mySpawnNumber - 8) % mineCount; // Collectors 8-11 → mines 0-3
MapLocation myMine = read from slots (40 + myMineIndex * 2);

// Collect from assigned mine
move toward myMine;
```

### Benefits:
- No competition between collectors
- Efficient coverage of map
- Predictable collection patterns
- Higher total cheese collection

## 4. Cheese Carrying Penalty Optimization

**Current problem**: Plan ignores 1% penalty per cheese

### Impact:
- Carrying 10 cheese: 10% slower (11 cd instead of 10)
- Carrying 20 cheese: 20% slower (12 cd instead of 10)
- Carrying 50 cheese: 50% slower (15 cd instead of 10)

### Optimization:
```java
// Deliver at 10 cheese (not 15 or 20)
if (rc.getRawCheese() >= 10) {
    deliver(); // Minimize carrying penalty
}
```

### For Combat:
```java
// Don't pick up cheese during combat
if (enemies.length > 0 && rc.getRawCheese() > 0) {
    // Drop cheese to fight at full speed
    // (Can't explicitly drop, but don't pick up more)
}
```

## 5. Bytecode Monitoring

### Skip Expensive Operations When Low:
```java
if (Clock.getBytecodesLeft() < 3000) {
    // Skip Bug2 (costs 500-1000)
    // Use greedy movement instead
    simpleMove(target);
} else {
    moveWithBug2(target);
}
```

### Benefits:
- Avoid bytecode timeout
- Graceful degradation under load
- Safety margin

## Implementation Priority:

### Phase 1 (MVP):
1. ✅ Basic spawning and roles
2. ✅ Combat with cheese-enhanced attacks
3. ✅ Collection and delivery
4. ✅ Bug2 pathfinding

### Phase 2 (Competitive Features):
1. **Dirt walls** (rounds 15-20) - early defense
2. **Cheese mine tracking** - optimize collection
3. **Carrying penalty awareness** - deliver at 10 cheese

### Phase 3 (Advanced - If Time):
1. **2nd king formation** (round 75+) - unique strategy
2. **Bytecode monitoring** - safety
3. **Trap removal** - path clearing

## Dirt Wall Design (Recommended):

```
Distance 3 from king center:
. . W . W . .
. . . . . . .
W . . K . . W
. . . K . . .
W . . K . . W
. . . . . . .
. . W . W . .
```

4 walls at corners, 4 gaps at cardinals
- Blocks diagonal rushes
- Allows N/S/E/W delivery access
- Cost: 40 cheese (4 walls)
- Build time: 4 rounds

This is much better than full perimeter!
