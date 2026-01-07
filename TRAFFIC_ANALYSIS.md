# Traffic Congestion Analysis - 2026-01-07

## The Fundamental Problem

**Observation**: All economy rats converge on same cheese locations → traffic jam → inefficiency

**Root Cause**: Every rat executes IDENTICAL logic:
1. Find nearest cheese within vision
2. Move toward it
3. When multiple rats see same cheese → all converge → cluster

## Current Behavior (BROKEN)

```java
// EconomyRat.java - collectCheese()
// Find nearest cheese ANYWHERE
for (MapLocation loc : nearby) {
    if (info.getCheeseAmount() > 0) {
        if (dist < nearestDist) {
            nearestDist = dist;
            nearestCheese = loc;  // ALL rats pick SAME cheese!
        }
    }
}
```

**Result**: 10 rats see same cheese pile → all move to it → cluster forms

## Coordination Options (from Spec)

### Option 1: Shared Array (64 slots, 10 bits each)
- **Current usage**: 13/64 slots (emergency, king pos, cat tracking)
- **Available**: 51 slots FREE
- **Capability**: King writes, all rats read
- **Use cases**:
  - Territory assignments
  - Cheese mine claiming
  - Traffic control signals
  - Work queue management

### Option 2: Squeaks (Messages)
- **Range**: SQUEAK_RADIUS_SQUARED = 16 (4 tiles)
- **Duration**: MESSAGE_ROUND_DURATION = 5 rounds
- **Limit**: MAX_MESSAGES_SENT_ROBOT = 1 per turn
- **PROBLEM**: Attracts cats! (spec explicitly warns)
- **Verdict**: Too risky for traffic management

### Option 3: Direct Sensing
- **Capability**: rc.senseNearbyRobots()
- **Range**: Vision radius (20 squared for baby rats)
- **Use**: Detect friendly rats, avoid their positions
- **Cost**: Cheap if already sensing for enemies

### Option 4: Deterministic Assignment (No Communication)
- **Method**: Use robot ID for zone assignment
- **Example**: `zone = rc.getID() % 4`
- **Advantage**: Zero communication overhead, zero coordination lag
- **Disadvantage**: Static, can't adapt to dynamic conditions

## Proposed Solutions

### Solution A: Zone-Based Territories (Deterministic)

**Concept**: Divide map into quadrants, assign rats by ID

```
Map divided into 4 zones:
- Zone 0 (NW): Rats with ID % 4 == 0
- Zone 1 (NE): Rats with ID % 4 == 1
- Zone 2 (SE): Rats with ID % 4 == 2
- Zone 3 (SW): Rats with ID % 4 == 3
```

**Implementation**:
```java
// In EconomyRat.collectCheese()
int zone = rc.getID() % 4;
MapLocation zoneCenter = getZoneCenter(rc, zone);

// Priority 1: Collect cheese in MY zone
// Priority 2: If no cheese, patrol zone center
// Priority 3: Deliver to king when carrying threshold reached
```

**Pros**:
- Simple, no coordination overhead
- Automatic load balancing
- No traffic jams (rats in different areas)

**Cons**:
- Inflexible if cheese distribution uneven
- Can't reassign if zone exhausted

### Solution B: Collision Avoidance (Local)

**Concept**: Rats avoid moving to occupied tiles

```java
// Before moving to target
RobotInfo[] friendlies = rc.senseNearbyRobots(2, rc.getTeam());
for (RobotInfo ally : friendlies) {
    if (ally.location.equals(targetLocation)) {
        // Someone already there, try alternative direction
        targetLocation = findAlternative(target);
    }
}
```

**Pros**:
- Works with any strategy
- Prevents rats stacking on same tile

**Cons**:
- Doesn't prevent multiple rats heading to same distant cheese
- Only helps with immediate collisions

### Solution C: Shared Array Work Queue

**Concept**: Use shared array slots to "claim" cheese locations

```
Slots 13-32: Cheese mine claims (20 slots = 10 mines, 2 slots per mine for X/Y)
- Rat claims mine by writing its ID to slot
- Other rats skip claimed mines
- Claim expires after N rounds
```

**Implementation**:
```java
// Check if cheese already claimed
int claimSlot = SLOT_CHEESE_CLAIMS + (mineIndex * 3);
int claimedBy = rc.readSharedArray(claimSlot);
int claimRound = rc.readSharedArray(claimSlot + 1);

if (claimedBy != 0 && round - claimRound < 50) {
    // Skip this mine, it's claimed
    continue;
}

// Claim this mine
rc.writeSharedArray(claimSlot, rc.getID() % 1000);  // Can't write as baby rat!
```

**Problem**: Baby rats CAN'T WRITE to shared array! Only kings can write.

**Alternative**: King assigns territories, rats read assignments.

### Solution D: Aggressive Combat (Different Strategy)

**Concept**: Combat rats should attack ENEMY KING, not patrol center

**Current** (DEFENSIVE):
```java
// CombatRat.java
// Patrol at center, wait for cats
Movement.moveToward(rc, center);
```

**Proposed** (OFFENSIVE):
```java
// Get enemy king position (track in shared array)
// Move directly to enemy king
// Attack enemy king and defenders
// This splits rats away from center, reduces congestion
```

**Implementation**:
```java
// King tracks enemy king position in shared array
int enemyKingX = rc.readSharedArray(SLOT_ENEMY_KING_X);
int enemyKingY = rc.readSharedArray(SLOT_ENEMY_KING_Y);
MapLocation enemyKing = new MapLocation(enemyKingX, enemyKingY);

// Combat rats go straight to enemy king
Movement.moveToward(rc, enemyKing);
```

## Recommended Approach: HYBRID

Combine multiple solutions:

### Phase 1: Zone Assignment (Immediate)
- **Economy rats**: 4 zones based on ID % 4
- **Combat rats**: Direct to enemy king (aggressive)
- **Effect**: Splits rats across map, no single convergence point

### Phase 2: Collision Avoidance (Add Later)
- Check for friendly rats before moving
- Spread out when clustered
- Maintain minimum spacing

### Phase 3: King Coordination (Advanced)
- King tracks cheese mine locations
- King assigns priorities via shared array
- Rats read assignments and adapt

## Implementation Priority

1. **NOW**: Zone-based economy + aggressive combat
2. **Next**: Collision avoidance in Movement.java
3. **Later**: King-directed coordination via shared array

## Expected Results

**Before** (Current):
- All rats converge on center
- Traffic jam at cheese piles
- Inefficient collection

**After** (Zone-based):
- Rats spread across 4 quadrants
- Each zone has dedicated collectors
- Combat rats attack enemy king (different location)
- No single convergence point

---

## Action Plan

### Files to Modify:
1. **Communications.java**: Add enemy king tracking slots
2. **RatKing.java**: Track enemy king position
3. **EconomyRat.java**: Implement zone-based collection
4. **CombatRat.java**: Attack enemy king directly
5. **Movement.java**: Add friendly collision detection

### Expected Improvement:
- 4x reduction in clustering (rats spread across 4 zones)
- Faster cheese collection (no traffic jams)
- Offensive pressure on enemy king
- Better map coverage
