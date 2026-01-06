# Spawn API Investigation - Blocking Issue

**Status**: BLOCKER for competition
**Date**: January 6, 2026
**Issue**: canBuildRat() returns false for all locations despite meeting documented requirements

---

## Problem Summary

King cannot spawn baby rats anywhere on map, even after:
- ✅ Repositioning to map center [15,15]
- ✅ Having 2,353 cheese (cost is only 10)
- ✅ Action ready (cooldown = 0)
- ✅ Trying all 8 adjacent directions

**Debug Output:**
```
Round 50:  Spawn economics: cost=10 apiCost=10 cheese=2353 rats=0 actionReady=true cd=0
Round 50:  Spawn capacity: 0/8 at [15, 15]
Round 100: Spawn capacity: 0/8 at [15, 15]
```

---

## Conditions Verified

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Cheese available | ✅ Met | 2,353 >= 10 |
| Action ready | ✅ Met | actionReady=true, cd=0 |
| Valid locations | ✅ Met | Trying 8 adjacents, map center |
| Correct API method | ✅ Met | Using buildRat/canBuildRat (not buildRobot) |
| King type | ✅ Met | Unit is RAT_KING |
| Within radius | ✅ Met | Adjacent tiles (distance=1) within sqrt(4)=2 |

**All documented requirements met, but spawn still fails!**

---

## Hypotheses

### H1: King Footprint Blocks Adjacent Tiles
**Theory**: King is 3x3, occupies 9 tiles. Adjacent tiles might overlap with footprint.

**King at [15,15] occupies:**
- [14,14] to [16,16] (3x3 grid)

**Adjacent spawn tiles:**
- [15,16]: Part of king? (Within 3x3 bounds)
- [16,16]: Definitely part of king
- [14,15]: Part of king

**Problem**: If king footprint blocks adjacents, NO position allows spawning!

**Test**: Need to understand king coordinate system. Is [15,15] the center or corner of 3x3?

### H2: Game State Not Initialized
**Theory**: Game hasn't properly started, some initialization missing

**Evidence against**:
- Game is running (200+ rounds)
- Cheese consumption working (2500 -> 1903)
- Movement working (repositioning executes)

### H3: API Method Not Fully Implemented
**Theory**: canBuildRat() exists but has hidden requirements not documented

**Possible hidden requirements:**
- Minimum round number?
- Specific game mode?
- Cheese must be in global pool not raw?
- Team coordination required?

### H4: Starting Game Has No Spawn Capability
**Theory**: Initial king can't spawn, only formed kings can

**Evidence against**:
- Spec says all kings can spawn
- No mention of this limitation

---

## Investigation Steps

### 1. Check King Footprint Coordinate System
```java
// Add debug in RatKing
MapLocation[] allParts = rc.getAllPartLocations();
for (MapLocation part : allParts) {
    Debug.dot(rc, part, Debug.Color.PURPLE);
    Debug.info(rc, "King footprint: " + part);
}
```

**Expected**: 9 tiles if 3x3, showing which tiles king occupies

### 2. Check Adjacent Tile States
```java
for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
    MapLocation adj = rc.getLocation().add(dir);

    boolean onMap = rc.onTheMap(adj);
    boolean passable = rc.sensePassability(adj);
    boolean occupied = rc.isLocationOccupied(adj);
    int distance = rc.getLocation().distanceSquaredTo(adj);

    Debug.info(rc, "Adjacent " + dir + " at " + adj +
               ": onMap=" + onMap +
               " passable=" + passable +
               " occupied=" + occupied +
               " dist²=" + distance);
}
```

**Expected**: Should show WHY each location fails

### 3. Test on Different Map
```bash
./gradlew run -Pmaps=DefaultMedium
./gradlew run -Pmaps=DefaultLarge
```

**If spawning works on different map**: Map-specific issue
**If spawning fails everywhere**: API/game state issue

### 4. Check if Baby Rats Exist But Hidden
```java
RobotInfo[] all = rc.senseNearbyRobots(-1, null); // All robots
Debug.info(rc, "Total robots sensed: " + all.length);

for (RobotInfo robot : all) {
    Debug.info(rc, "Robot: " + robot.getType() + " at " + robot.getLocation() + " team=" + robot.getTeam());
}
```

**Expected**: Should see all units on map

### 5. Contact Battlecode Staff
**Discord**: http://bit.ly/battlecode-discord (#questions channel)
**Email**: battlecode@mit.edu

Ask: "canBuildRat() returns false for all locations despite meeting documented requirements. Is there a hidden requirement or known issue in v1.0.3?"

---

## Workarounds

### Workaround 1: Wait for API Fix
**Action**: Monitor for engine updates
**Command**: `./gradlew update && ./gradlew version`

### Workaround 2: Manual Baby Rat Placement (if available)
**Check if exists**: Different spawn method?

### Workaround 3: Focus on Single King Strategy
**Accept**: Can't spawn, optimize what we have
- Focus baby rats on efficiency
- Perfect cheese collection
- Optimal combat

**Viability**: Low - will lose to opponents who can spawn

---

## Current Workaround Status

**What's Working:**
- ✅ King movement and repositioning
- ✅ Emergency circuit breaker
- ✅ Debugging system
- ✅ All 111 tests passing

**What's Blocked:**
- ❌ Baby rat spawning (core functionality)
- ❌ Cheese economy (income = 0)
- ❌ Army building

**Competition Impact**: CRITICAL - Cannot compete without spawning

---

## Next Actions

**Immediate (Next Hour):**
1. Add detailed adjacency debugging (investigation steps 1-2)
2. Test on all available maps
3. Post Discord question
4. Document findings

**If API Issue Confirmed:**
- Report bug to Battlecode team
- Monitor for hotfix
- Prepare workaround strategy

**If Configuration Issue:**
- Fix configuration
- Resume development

---

## Debug Commands

```bash
# Enable verbose debugging
# Edit DebugConfig.java: LEVEL = Debug.Level.VERBOSE

# Run with detailed output
cd scaffold
./gradlew run | tee spawn_debug.log

# Check specific events
grep "King footprint" spawn_debug.log
grep "Adjacent" spawn_debug.log
grep "canBuildRat" spawn_debug.log

# Try different maps
./gradlew run -Pmaps=DefaultMedium
./gradlew run -Pmaps=DefaultLarge
```

---

**Priority**: CRITICAL - Must resolve before Sprint 1 (Jan 12)
**Status**: Under investigation
**Owner**: Needs Battlecode team input
