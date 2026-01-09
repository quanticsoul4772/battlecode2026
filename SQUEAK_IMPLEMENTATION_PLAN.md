# Squeak Communication Implementation Plan for ratbot4

## What Squeaks Are

**API**: `rc.squeak(int messageContent)` and `rc.readSqueaks(int roundNum)`
- **Range**: 16 tile radius (SQUEAK_RADIUS_SQUARED = 256)
- **Duration**: Messages last 5 rounds
- **Limit**: 1 message per robot per round (MAX_MESSAGES_SENT_ROBOT = 1)
- **Format**: 32-bit int (4 bytes)
- **Cost**: Low bytecode (faster than shared array in some cases)
- **RISK**: Attracts cats! (spec warns about this)

## Current ratbot4 Communication

**Shared Array Only**:
- Slot 0-1: Our king position
- Slot 2-3: Enemy king position
- All communication through king (only kings write)
- Baby rats can't communicate with each other

**Limitations**:
- Baby rats can't alert others (must wait for king to update shared array)
- Enemy sightings delayed (attacker sees enemy, king updates array next round)
- No peer-to-peer coordination between baby rats

## Proposed Squeak System

### Message Format (32-bit int encoding)

**Bits 0-3**: Message type (4 bits = 16 types)
```
0 = INVALID
1 = ENEMY_RAT_KING (location)
2 = ENEMY_BABY_RAT (location)
3 = CHEESE_MINE (location)
4 = DANGER (under attack)
5 = HELP_KING (king being attacked)
6-15 = RESERVED
```

**Bits 4-15**: X coordinate (12 bits, max 4096)
**Bits 16-27**: Y coordinate (12 bits, max 4096)
**Bits 28-31**: Count or HP or direction (4 bits)

**Encoding functions**:
```java
// Pack message
int squeak = (type << 28) | (y << 16) | (x << 4) | extra;

// Unpack message
int type = (squeak >> 28) & 0xF;
int x = (squeak >> 4) & 0xFFF;
int y = (squeak >> 16) & 0xFFF;
int extra = squeak & 0xF;
```

### Use Cases for ratbot4

**1. Enemy King Spotting** (Attackers)
```java
// When attacker sees enemy king
if (enemy.getType() == UnitType.RAT_KING) {
    MapLocation kingLoc = enemy.getLocation();
    int squeak = encodeSqueak(ENEMY_RAT_KING, kingLoc.x, kingLoc.y, 0);
    rc.squeak(squeak);
    // Now other attackers within 16 tiles know where king is!
}
```

**Benefit**: Attackers coordinate on king position without waiting for shared array update
**Risk**: Cats within 16 tiles detect squeak (acceptable for attackers in enemy territory)

**2. Cheese Mine Coordination** (Collectors)
```java
// When collector finds cheese mine
if (info.hasCheeseMine() && notAlreadyKnown) {
    int squeak = encodeSqueak(CHEESE_MINE, loc.x, loc.y, 0);
    rc.squeak(squeak);
    // Other collectors know about this mine, can coordinate
}

// King receives squeak, writes to shared array
Message[] squeaks = rc.readSqueaks(-1); // All squeaks from last 5 rounds
for (Message msg : squeaks) {
    if (getType(msg.getBytes()) == CHEESE_MINE) {
        // Write to shared array slots 40-59
        writeCheeseLocation(loc);
    }
}
```

**Benefit**: Faster cheese discovery, no duplicate searching
**Risk**: Cats attracted to cheese areas (collectors vulnerable)

**3. Danger Alerts** (All rats)
```java
// When rat sees enemy within 5 tiles
RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(25, opponent);
if (nearbyEnemies.length > 0 && myRole == COLLECTOR) {
    int squeak = encodeSqueak(DANGER, me.x, me.y, nearbyEnemies.length);
    rc.squeak(squeak);
    // Other collectors within 16 tiles flee this area
}
```

**Benefit**: Collectors warn each other, avoid getting killed
**Risk**: Attracts more cats to danger zone

**4. King Under Attack** (Defenders near king)
```java
// Attacker near our king sees enemy
if (distToOurKing < 100 && enemies.length > 0) {
    int squeak = encodeSqueak(HELP_KING, me.x, me.y, enemies.length);
    rc.squeak(squeak);
    // All nearby rats rush to defend king
}
```

**Benefit**: Coordinated defense when king threatened
**Risk**: Cats attracted to our king area (dangerous!)

## Implementation Plan

### Phase 1: Add Squeak Infrastructure (No behavior changes yet)

**Files to modify**:
- ratbot4/RobotPlayer.java

**Add**:
1. SqueakType enum (6 message types)
2. Encoding functions (pack/unpack ints)
3. Location encoding (x,y into 24 bits)
4. Message reading (in king and baby rats)

**Test**: Can send and receive squeaks? Encoding/decoding correct?

**Bytecode budget**:
- Encoding: ~100 bytecode
- Decoding: ~100 bytecode
- rc.squeak(): ~100 bytecode
- rc.readSqueaks(): ~200 bytecode per message
- Total: ~500 bytecode (affordable)

### Phase 2: Enemy King Coordination (Attackers only)

**Add to attackers**:
```java
// In attackEnemyKing(), after seeing enemy king
if (enemy.getType() == UnitType.RAT_KING) {
    // Squeak location
    int squeak = encodeSqueak(ENEMY_RAT_KING, enemy.getLocation().x, enemy.getLocation().y, 0);
    if (rc.canSqueak(squeak)) {
        rc.squeak(squeak);
    }
}

// Also read squeaks to find king
Message[] squeaks = rc.readSqueaks(-1);
for (Message msg : squeaks) {
    if (getType(msg) == ENEMY_RAT_KING) {
        MapLocation squeakedKing = getLocation(msg);
        // Use this instead of stale shared array position
        simpleMove(rc, squeakedKing);
        return;
    }
}
```

**Benefit**: Attackers share real-time king position (no 1-round delay)
**Test**: Do attackers find king faster? Do they cluster less?

### Phase 3: Cheese Mine Coordination (Collectors)

**Add to collectors**:
```java
// In collect(), when finding cheese mine
if (info.hasCheeseMine() && not already known) {
    int squeak = encodeSqueak(CHEESE_MINE, loc.x, loc.y, 0);
    rc.squeak(squeak);
}

// Read squeaks to find mines
Message[] squeaks = rc.readSqueaks(-1);
for (Message msg : squeaks) {
    if (getType(msg) == CHEESE_MINE) {
        MapLocation mineLoc = getLocation(msg);
        // Target this mine
    }
}
```

**Add to king**:
```java
// Read cheese mine squeaks, write to shared array
Message[] squeaks = rc.readSqueaks(-1);
for (Message msg : squeaks) {
    if (getType(msg) == CHEESE_MINE) {
        // Write to slots 40-59 (cheese mine tracking)
        writeToSharedArray(mineLoc);
    }
}
```

**Benefit**: Coordinated collection, no duplicate searching
**Test**: Do collectors spread across mines? Less competition?

### Phase 4: Danger Alerts (Optional - high risk)

**Add to all rats**:
```java
// Only squeak danger if critical
if (rc.getHealth() < 30 && enemies.length > 0) {
    int squeak = encodeSqueak(DANGER, me.x, me.y, enemies.length);
    rc.squeak(squeak);
}

// Collectors read danger squeaks and flee area
Message[] squeaks = rc.readSqueaks(-1);
for (Message msg : squeaks) {
    if (getType(msg) == DANGER) {
        // Avoid this location
    }
}
```

**Benefit**: Collectors avoid dangerous areas
**Risk**: HIGH - attracts cats to our collectors

**Recommendation**: Skip this unless testing shows it's worth the risk

## Risk Analysis

### Cat Attraction Risk

**From spec**: "Squeaking attracts cats"
- Cats can detect squeaks
- Move toward squeak source
- Puts squeaking rat in danger

**Mitigation strategies**:
1. **Attackers only** squeak (they're already in enemy territory)
2. **Conditional squeaking** (only when value > risk)
3. **King doesn't squeak** (too valuable to risk)
4. **Collectors squeak rarely** (only for cheese mines, not danger)

### Coordination vs Cats Trade-off

**Current (no squeaks)**:
- No cat attraction risk
- Slower coordination (shared array only)
- Attackers cluster at old king position

**With squeaks (enemy king only)**:
- Attackers attract cats (acceptable - they're in enemy territory)
- Faster coordination (real-time king position)
- Less clustering (updated positions)
- Worth the risk for attackers

**With squeaks (all uses)**:
- Everyone attracts cats (dangerous for collectors!)
- Best coordination possible
- High risk - collectors die to cats
- Probably NOT worth it

## Recommended Implementation

### Start with Attackers Only (Low Risk)

**Phase 1**: Infrastructure + enemy king squeaks
**Phase 2**: Test if attackers perform better
**Phase 3**: Add cheese mine squeaks if safe
**Phase 4**: Skip danger alerts (too risky)

### Success Criteria

**Must have**:
- [ ] Encoding/decoding works correctly
- [ ] Attackers squeak enemy king location
- [ ] Other attackers read and use squeaked locations
- [ ] No bytecode timeouts
- [ ] No increase in collector deaths (squeaks shouldn't affect them)

**Nice to have**:
- [ ] Cheese mine coordination
- [ ] Faster king kills (attackers find king quicker)
- [ ] Less attacker clustering

**Don't want**:
- [ ] Increased collector deaths from cat attraction
- [ ] King dying from cats attracted by squeaks
- [ ] Bytecode overhead causing slowdowns

## Implementation Checklist

**Before coding**:
- [ ] Understand message encoding (4 bits type, 24 bits data)
- [ ] Test encoding/decoding in isolation
- [ ] Confirm squeak range (16 tiles)
- [ ] Understand cat attraction risk

**While coding**:
- [ ] Add SqueakType enum
- [ ] Add encoding/decoding functions
- [ ] Test with print statements first
- [ ] Add to attackers only initially
- [ ] Keep collectors squeak-free

**After coding**:
- [ ] Test vs ratbot4 (measure performance change)
- [ ] Test vs lectureplayer (do we coordinate better?)
- [ ] Check logs for squeak messages
- [ ] Verify no collector death increase
- [ ] Measure attacker effectiveness (kills, king damage)

## Phased Rollout

**Week 1** (Jan 8-12):
- Phase 1: Infrastructure only (can send/receive, no behavior change)
- Test: No functionality change, just logging

**Sprint 1** (Jan 12):
- Phase 2: Enemy king squeaks for attackers
- Test: Do attackers cluster less? Find king faster?

**Week 2** (Jan 13-19):
- Phase 3: Cheese mine squeaks (if Phase 2 successful)
- Test: Better collection efficiency?

**Sprint 2** (Jan 19):
- Decision: Keep or remove based on performance data

## Alternative: Don't Use Squeaks

**If squeaks prove too risky**:
- Keep shared array only
- Improve existing coordination (better search patterns)
- Focus on other optimizations (pathfinding, combat)

Squeaks are optional - we can win without them if the cat risk is too high.

---

**Ready to implement Phase 1?**
Or should we test current ratbot4 more first to establish baseline performance?
