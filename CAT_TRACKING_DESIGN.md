# Cat Position Tracking System Design

**Objective**: Enable baby rats to find and attack cats even when outside their limited 90° vision cone

---

## Problem Analysis

**Current Issue:**
- Baby rats: 4.5 tile vision radius, 90° cone
- Cats: Visible only when in cone and within range
- Rats enter ATTACK_CAT state briefly, then lose sight
- Result: Minimal cat damage (1-2 attacks per match)

**Why Kings Are Better Sensors:**
- Vision: sqrt(25) = 5 tile radius (better range)
- 360° omnidirectional vision (no cone limitation)
- Can ALWAYS see cats within 5 tiles
- Stationary (reliable sensor position)

---

## Shared Array Design

### Resource Allocation

**Available:** 64 slots × 10 bits (0-1023)

**Current Usage:**
- Slot 0: Cheese status / emergency codes
- Slot 1: King X position
- Slot 2: King Y position

**New Allocation for Cat Tracking:**
- Slots 3-4: Cat #1 position
- Slots 5-6: Cat #2 position
- Slots 7-8: Cat #3 position (if >2 cats)
- Slots 9-10: Cat #4 position (if >2 cats)

### Encoding Scheme

**10-bit encoding per slot:**

**Option 1: Separate X/Y (RECOMMENDED)**
```
Slot N:   X coordinate (0-1023, use actual value)
Slot N+1: Y coordinate (0-1023, use actual value)
```

**Pros**: Simple, supports maps up to 1023×1023
**Cons**: Uses 2 slots per cat

**Option 2: Packed coordinates**
```
Bits 0-4:  X coordinate (0-31, 5 bits)
Bits 5-9:  Y coordinate (0-31, 5 bits)
```

**Pros**: 1 slot per cat, saves space
**Cons**: Limited to 31×31 maps (current max is 60×60 - won't work!)

**Decision**: Use Option 1 (separate X/Y)

### Staleness Handling

**Problem**: Cat positions go stale (cats move)

**Solution**: Timestamp + validity

**Encoding:**
```
Slot N:   X coordinate OR 0 if no cat
Slot N+1: Y coordinate OR round_last_seen % 1024
```

**Validation:**
```java
int catX = rc.readSharedArray(3);
int catY = rc.readSharedArray(4);

if (catX == 0) {
    // No cat tracked
} else {
    int lastSeen = catY % 100; // Last 2 digits = round seen
    int age = rc.getRoundNum() - lastSeen;

    if (age < 50) {
        // Position fresh - navigate there
    } else {
        // Position stale - ignore
    }
}
```

**Alternative (simpler):** Don't track staleness, just overwrite with latest sighting

---

## Implementation Design

### King Behavior (Writer)

**In RatKing.java, after spawning:**

```java
public static void run(RobotController rc) throws GameActionException {
    // ... existing emergency/spawning logic ...

    // Track cats (kings have 360° vision, can always see them)
    trackCats(rc);
}

private static void trackCats(RobotController rc) throws GameActionException {
    // Sense all cats
    RobotInfo[] cats = rc.senseNearbyRobots(-1, Team.NEUTRAL);

    int catIndex = 0;
    for (int i = cats.length; --i >= 0;) {
        if (cats[i].getType() == UnitType.CAT && catIndex < 4) {
            MapLocation catLoc = cats[i].getLocation();

            // Write to shared array (slots 3-10, 2 slots per cat)
            int slotX = 3 + (catIndex * 2);
            int slotY = 4 + (catIndex * 2);

            rc.writeSharedArray(slotX, catLoc.x);
            rc.writeSharedArray(slotY, catLoc.y);

            catIndex++;
        }
    }

    // Clear unused cat slots (mark as invalid)
    for (int i = catIndex; i < 4; i++) {
        int slotX = 3 + (i * 2);
        rc.writeSharedArray(slotX, 0); // 0 = no cat
    }
}
```

### Baby Rat Behavior (Reader)

**In BabyRat.java attackCat() method:**

```java
private static void attackCat(RobotController rc) throws GameActionException {
    // Try to find cat in vision first
    RobotInfo nearestCat = RobotUtil.findNearestCat(rc);

    if (nearestCat == null) {
        // Can't see cat - check shared array for tracked positions
        MapLocation targetCat = getTrackedCatPosition(rc);

        if (targetCat == null) {
            // No cats tracked - switch to EXPLORE
            currentState = State.EXPLORE;
            return;
        }

        // Navigate to tracked cat position
        moveToward(rc, targetCat);

        if (DebugConfig.VISUAL_INDICATORS) {
            Debug.markTarget(rc, targetCat, "TRACKED_CAT");
        }
        return;
    }

    // Cat in vision - attack as normal
    MapLocation catLoc = nearestCat.getLocation();

    if (rc.canAttack(catLoc)) {
        rc.attack(catLoc);
        return;
    }

    moveToward(rc, catLoc);
}

private static MapLocation getTrackedCatPosition(RobotController rc) throws GameActionException {
    // Read all tracked cat positions, return nearest
    MapLocation me = rc.getLocation();
    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    for (int i = 0; i < 4; i++) {
        int slotX = 3 + (i * 2);
        int slotY = 4 + (i * 2);

        int catX = rc.readSharedArray(slotX);
        int catY = rc.readSharedArray(slotY);

        if (catX == 0) continue; // No cat in this slot

        MapLocation catLoc = new MapLocation(catX, catY);
        int dist = me.distanceSquaredTo(catLoc);

        if (dist < nearestDist) {
            nearestDist = dist;
            nearest = catLoc;
        }
    }

    return nearest; // null if no cats tracked
}
```

---

## Shared Array Layout

```
Slot  | Purpose              | Format
------|----------------------|------------------
0     | Cheese status        | Emergency codes or rounds remaining
1     | King X position      | X coordinate
2     | King Y position      | Y coordinate
3     | Cat #1 X position    | X coordinate or 0 if none
4     | Cat #1 Y position    | Y coordinate
5     | Cat #2 X position    | X coordinate or 0 if none
6     | Cat #2 Y position    | Y coordinate
7     | Cat #3 X position    | X coordinate or 0 if none
8     | Cat #3 Y position    | Y coordinate
9     | Cat #4 X position    | X coordinate or 0 if none
10    | Cat #4 Y position    | Y coordinate
11-63 | Future use           | Available
```

---

## Expected Behavior

**Without cat tracking (current):**
```
Rat spawns → Explores → Sees cat briefly → ATTACK_CAT → Turns → Loses sight → EXPLORE
Result: 1-2 attacks per cat encounter
```

**With cat tracking:**
```
King sees cat → Writes to shared array → All rats read position
Rat spawns → Reads shared array → Navigates TO cat position → ATTACK_CAT → Sustained combat
Result: Continuous attacks until cat dies
```

---

## Benefits

1. **Persistent Combat**: Rats don't lose cat position when it leaves vision
2. **Coordinated Attacks**: All rats know where cats are
3. **Better Coverage**: Kings' 360° vision finds cats rats miss
4. **Swarm Behavior**: Multiple rats converge on same cat
5. **Score Optimization**: Maximize cat damage (50% of cooperation score)

---

## Bytecode Cost

**King (per turn):**
- Sense nearby robots: ~100 bytecode
- Track cats (4 cats max): ~200 bytecode
- Write shared array (8 writes): ~160 bytecode
- **Total**: ~460 bytecode / 20,000 budget = 2.3%

**Baby Rat (when cat not in vision):**
- Read shared array (8 reads): ~160 bytecode
- Calculate nearest: ~100 bytecode
- **Total**: ~260 bytecode / 17,500 budget = 1.5%

**Acceptable overhead for 50% of cooperation score**

---

## Implementation Priority

**P0 - Core Tracking:**
1. Add trackCats() to RatKing
2. Add getTrackedCatPosition() to BabyRat
3. Update attackCat() to use tracked positions
4. Test: Rats navigate to cats even when out of vision

**P1 - Optimization:**
5. Prioritize closest cat (already in design)
6. Clear stale positions (cats defeated)
7. Add visual indicators for tracked cats

**P2 - Advanced:**
8. Predict cat movement (cats follow waypoints)
9. Coordinate swarm attacks (multiple rats on 1 cat)
10. Cat HP tracking (know when cat is low health)

---

## Testing Strategy

**Test 1: King tracks cats**
```bash
./gradlew run | grep "trackCats\|CAT.*shared"
# Should see cat positions written to shared array
```

**Test 2: Rats use tracked positions**
```bash
./gradlew run | grep "TRACKED_CAT\|Navigating to cat"
# Should see rats moving toward tracked positions
```

**Test 3: Cat damage increases**
```bash
./gradlew run | grep "COMBAT.*CAT"
# Should see many more combat events (50+ instead of 1-2)
```

**Test 4: Cooperation score**
```bash
# Load replay in client
# Check final cooperation score (should be >20 from cat damage alone)
```

---

## Success Criteria

**After implementation:**
- ✅ King writes cat positions every turn
- ✅ Baby rats read and navigate to tracked positions
- ✅ Sustained cat combat (10+ attacks per rat)
- ✅ Cat HP decreases significantly (from 10,000 to <5,000)
- ✅ Cooperation score >30 (cat damage contributing)

**Critical metric**: 100+ cat attacks in first 200 rounds (vs current 1-2)

---

## Files to Modify

**Primary:**
- `scaffold/src/ratbot/RatKing.java`
  - Add trackCats() method (~30 lines)
  - Call trackCats() after spawning

- `scaffold/src/ratbot/BabyRat.java`
  - Add getTrackedCatPosition() method (~30 lines)
  - Update attackCat() to use tracked positions (~10 lines)

**Documentation:**
- Update BehaviorConfig.java with shared array slot definitions

**Testing:**
- Add test for cat tracking system
- Verify shared array communication

**Estimated time**: 45 minutes
**Risk**: Low - pure additive feature
**Bytecode**: ~460 (king) + ~260 (baby rat) = acceptable
