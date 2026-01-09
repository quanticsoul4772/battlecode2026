# ratbot5 - Intelligent Movement Strategy

## Root Cause Analysis (MCP Reasoning)

**Why rats stop moving in late rounds**:

1. **Traffic Deadlock** (PRIMARY CAUSE)
   - Round-robin yielding: `if (id % 10 != round % 10) return;`
   - All rats check same condition → all yield together
   - Creates permanent gridlock

2. **Insufficient Stuck Recovery**
   - Try perpendicular: helps with walls, not traffic
   - Emergency any direction: still respects traffic rules
   - No override when truly stuck

3. **No Collective Failure Detection**
   - Each rat tracks own stuck status
   - No detection when ENTIRE swarm is stuck
   - No team-level recovery

4. **Target Loss Without Fallback**
   - Enemy king dies → attackers have no target
   - Just wander in small area
   - No productive alternate behavior

## ratbot5 Movement System

### Layer 1: Immediate Movement (Always Try First)

**Remove traffic yielding** - it causes deadlock!

```java
// OLD (ratbot4):
if (friendlies.length >= 4 && mySlot != round % 10) {
    return; // DON'T MOVE - causes gridlock!
}

// NEW (ratbot5):
// NO YIELDING - always try to move
// If truly blocked, stuck recovery will handle it
```

**Benefit**: Rats always attempt movement, no gridlock

### Layer 2: Stuck Detection (Enhanced)

**Track position history** (not just last position)

```java
// Track last 3 positions
private static MapLocation[] positionHistory = new MapLocation[3];
private static int historyIndex = 0;

// Check if oscillating or truly stuck
positionHistory[historyIndex] = me;
historyIndex = (historyIndex + 1) % 3;

boolean stuck = true;
for (MapLocation pos : positionHistory) {
    if (pos != null && !pos.equals(me)) {
        stuck = false; // Made progress in last 3 rounds
        break;
    }
}

if (stuck) {
    // FORCE MOVEMENT - ignore all constraints
    forceMove(rc);
    return;
}
```

**Benefit**: Detect oscillation (ping-ponging between 2 spots)

### Layer 3: Escalating Recovery

**Progressive recovery** (get more aggressive as stuck worsens)

```java
if (stuckRounds == 1) {
    // Mild: Try perpendicular
    tryPerpendicular();
}
else if (stuckRounds == 2) {
    // Medium: Try any adjacent direction
    tryAnyDirection();
}
else if (stuckRounds >= 3) {
    // Severe: FORCE movement, ignore all rules
    // Turn to random direction
    Direction random = directions[(round + id) % 8];
    if (rc.canTurn()) {
        rc.turn(random);
        stuckRounds = 0; // Reset after forcing
        return;
    }
}
```

**Benefit**: Guaranteed to do SOMETHING after 3 stuck rounds

### Layer 4: Collective Failure Detection

**Shared array heartbeat** - detect when whole team stuck

```java
// Shared array slot 5: Movement heartbeat
// Each rat that moves increments this counter

// In simpleMove(), after successful move:
if (moved) {
    int heartbeat = rc.readSharedArray(5);
    rc.writeSharedArray(5, (heartbeat + 1) % 1000);
}

// In king, check if team is moving:
static int lastHeartbeat = 0;
int currentHeartbeat = rc.readSharedArray(5);
if (currentHeartbeat == lastHeartbeat) {
    // NO RAT MOVED in last round!
    // EMERGENCY: Reset all coordination
    rc.writeSharedArray(6, 1); // Emergency signal
}
lastHeartbeat = currentHeartbeat;

// In baby rats, check emergency signal:
if (rc.readSharedArray(6) == 1) {
    // Team-wide stuck! Force random movement
    Direction random = directions[(round * id) % 8];
    if (rc.canMove(random)) {
        rc.move(random); // Accept strafe penalty to break deadlock
    }
}
```

**Benefit**: Detects and recovers from team-wide gridlock

### Layer 5: Productive Idle Behavior

**When no targets, do something useful**

```java
// For attackers with no enemies:
if (enemies.length == 0) {
    // Don't just wander - be productive!

    // Option 1: Patrol defensive perimeter
    MapLocation ourKing = cached position;
    MapLocation patrolPoint = calculatePatrolPoint(id, ourKing);
    simpleMove(rc, patrolPoint);

    // Option 2: Collect cheese (help economy)
    MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, visionRange);
    MapLocation nearestCheese = findNearestCheese(nearby);
    if (nearestCheese != null) {
        simpleMove(rc, nearestCheese); // Attackers collect when idle
    }

    // Option 3: Form 2nd king
    if (round > 50 && rc.readSharedArray(4) == 1) {
        // Formation signal active - join formation
        moveToFormationPoint(rc);
    }
}
```

**Benefit**: Rats always productive, never idle

### Layer 6: Direction.fromDelta() for Precise Movement

**Use Direction.fromDelta()** for exact movement control

```java
// Calculate exact dx, dy needed
int dx = target.x - me.x;
int dy = target.y - me.y;

// Clamp to -1, 0, 1
int clampedDx = Math.max(-1, Math.min(1, dx));
int clampedDy = Math.max(-1, Math.min(1, dy));

// Get exact direction
Direction exact = Direction.fromDelta(clampedDx, clampedDy);

// Move in that direction
if (rc.canTurn() && rc.getDirection() != exact) {
    rc.turn(exact);
} else if (rc.canMoveForward()) {
    rc.moveForward();
}
```

**Benefit**: Optimal pathing, no direction approximation

## ratbot5 Implementation Plan

### Phase 1: Remove Deadlock Sources

**Changes**:
1. Remove traffic yielding completely
2. Add position history tracking (3 positions)
3. Add escalating recovery (mild → medium → severe)

**Test**: Do rats still move in late rounds?

### Phase 2: Add Collective Coordination

**Changes**:
1. Movement heartbeat in shared array
2. King monitors team movement
3. Emergency signal when team stuck
4. Force random movement on emergency

**Test**: Does team recover from gridlock?

### Phase 3: Productive Idle Behavior

**Changes**:
1. Attackers collect cheese when no enemies
2. Patrol points for defensive positioning
3. Join 2nd king formation when signaled
4. Never truly idle

**Test**: Are rats always doing something useful?

### Phase 4: Direction.fromDelta() Optimization

**Changes**:
1. Use fromDelta() for precise movement
2. Calculate exact direction to target
3. No approximation errors

**Test**: Better pathfinding accuracy?

## Bytecode Budget

**Current ratbot4**: 3,700/round (21%)

**ratbot5 additions**:
- Position history: +100 bytecode
- Heartbeat checking: +50 bytecode
- Escalating recovery: +200 bytecode
- Productive idle: +300 bytecode
- fromDelta(): +50 bytecode
**Total new**: +700 bytecode

**ratbot5 projected**: 4,400/round (25%)
**Still available**: 13,100 bytecode (75%)

Acceptable overhead for reliability

## Success Criteria

**Must Fix**:
- [ ] Rats never freeze (movement in all rounds)
- [ ] No traffic deadlock (team keeps moving)
- [ ] Stuck rats recover (within 3 rounds)
- [ ] Late rounds active (no idle clustering)

**Performance**:
- [ ] Win in < 60 rounds (maintain ratbot4 speed)
- [ ] Collector count stable (always 4+)
- [ ] No mass attacker death (conditional suicide only)

---

**Key Insight from MCP Reasoning**:
> "Traffic yielding causes permanent gridlock when all rats yield simultaneously"

**Solution**: Remove yielding, add smarter stuck recovery with team-level coordination

Ready to implement ratbot5 with intelligent movement?
