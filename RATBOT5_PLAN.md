# ratbot5 Implementation Plan

## MCP Reasoning Analysis Results

**Priority ranking** (based on impact vs code cost):
1. **Critical**: Fix attacker suicide mechanism (catastrophic failure at round 100)
2. **High**: Implement fast collector replacement (economy sustainability)
3. **Medium**: Add Bug2 pathfinding (better navigation)
4. **Medium**: Fix 2nd king formation (strategic advantage)
5. **Low**: Refactor static variables (technical debt, but works)

## ratbot4 Performance Baseline

**Strengths**:
- 61 round victories (10x improvement!)
- No cooldown freeze (turn+forward fix working)
- Fast king kills (all 9 tiles attacked)
- Squeak coordination effective
- 83% bytecode available

**Critical Flaws**:
- Attackers suicide at round 100 (all die!)
- Collectors die without fast replacement
- 2nd king never forms (conditions impossible)
- No Bug2 (still greedy pathfinding)

## MCP Reasoning: Movement Freeze Root Cause

**Traffic yielding creates permanent gridlock**:
- When `friendlies >= 4`, rats yield if `id % 10 != round % 10`
- All rats check same condition → all yield simultaneously
- Result: Permanent freeze, no recovery

**Solution**: REMOVE traffic yielding, add intelligent stuck recovery

## ratbot5 Core Changes

### Change 1: Intelligent Movement System (CRITICAL - REPLACES SUICIDE)

**Remove**:
- Attacker suicide (doesn't solve movement freeze)
- Traffic yielding (CAUSES movement freeze)

**Problem**: `ATTACKER_IDLE_SUICIDE_THRESHOLD = 100`
- All attackers suicide by round 150
- Leaves us defenseless
- Enemy keeps full army

**Solution**: Increase to 500 rounds OR remove entirely
```java
// Option A: Much longer idle time
private static final int ATTACKER_IDLE_SUICIDE_THRESHOLD = 500;

// Option B: Only suicide if we're over population cap
if (roundsSinceLastAttack > 100 && spawnCount > 18) {
    rc.disintegrate(); // Make room for fresh spawns
}

// Option C: No suicide - rely on combat deaths only
// Remove suicide entirely
```

**NEW APPROACH**: Intelligent movement instead of suicide

**Additions**:
1. Position history (3 rounds) - detect oscillation
2. Escalating recovery (mild → severe, override constraints)
3. Team heartbeat (shared array tracks collective movement)
4. Productive idle (collect cheese, patrol, form kings when no combat)
5. Direction.fromDelta() (precise movement calculation)
6. Force movement after 3 stuck rounds (ignore all rules)

**Implementation**:
```java
// Position history tracking
private static MapLocation[] positionHistory = new MapLocation[3];

// Check if stuck (no progress in 3 rounds)
boolean allSame = true;
for (MapLocation pos : positionHistory) {
    if (pos != null && !pos.equals(me)) {
        allSame = false;
    }
}

// FORCE movement if stuck 3 rounds
if (allSame && stuckRounds >= 3) {
    Direction random = directions[(round + id * 7) % 8];
    if (rc.canTurn()) {
        rc.turn(random);
        return; // Forced turn breaks stuck
    }
}

// NO traffic yielding (removed - causes deadlock)
// Just try to move every round
```
- Suicide only if over 18 rats spawned
- Keeps population under control
- Doesn't kill all attackers unnecessarily

### Change 2: Instant Collector Replacement (HIGH PRIORITY)

**Problem**: Replacement every 50 rounds too slow
- Collector dies round 60
- Replacement round 100 (40 rounds without!)
- King may starve in those 40 rounds

**Solution**: Spawn immediately when collector count low
```java
// In king, every round (not just round % 50):
RobotInfo[] team = rc.senseNearbyRobots(visionRange, rc.getTeam());
int visibleCollectors = 0;
for (RobotInfo r : team) {
    if (r.getType().isBabyRatType()) {
        // Check if collector (odd ID)
        if (r.getID() % 2 == 1) {
            visibleCollectors++;
        }
    }
}

// IMMEDIATE replacement if collectors drop below 4
if (visibleCollectors < 4 && spawnCount < 25) {
    int cost = rc.getCurrentRatCost();
    if (cheese > cost + cheeseReserve) {
        spawnRat(rc); // Spawn collector immediately
    }
}
```

**Benefit**: Economy always has 4+ collectors
**Cost**: May spawn 20-25 rats instead of 12-15 (sustainable with cheese mines)

### Change 3: Bug2 Pathfinding Integration (MEDIUM)

**Problem**: Greedy movement gets stuck
- Rats stuck behind obstacles
- Can't navigate complex terrain
- Simple movement insufficient

**Solution**: Integrate Bug2 from ratbot2
```java
// Copy from ratbot2/utils/Pathfinding.java
// Add to ratbot5/RobotPlayer.java (bottom of file)

// In simpleMove(), when stuck:
if (stuckRounds >= 2) {
    Direction bug2Dir = bug2(me, target, (dir) -> rc.canMove(dir));
    if (bug2Dir != Direction.CENTER && rc.canTurn()) {
        rc.turn(bug2Dir);
        return;
    }
}
```

**Cost**: ~150 lines of code
**Benefit**: Better obstacle navigation, fewer stuck rats

### Change 4: Coordinated King Formation (MEDIUM)

**Problem**: 7 rats never cluster in 3x3
- Conditions: round > 75, cheese > 1000, spawnCount >= 15
- Rats spread across map
- Never 7 in same 3x3 area

**Solution**: Explicit formation protocol
```java
// Shared array slot 4: Formation signal (1 = form now, 0 = normal)
// King sets signal when conditions met:
if (round > 50 && cheese > 800 && spawnCount >= 14) {
    rc.writeSharedArray(4, 1); // FORM 2ND KING NOW!
}

// Collectors read signal and cluster:
int formSignal = rc.readSharedArray(4);
if (formSignal == 1 && myRole == COLLECTOR) {
    // Move to formation point (near our king)
    MapLocation formPoint = new MapLocation(ourKing.x + 5, ourKing.y + 5);
    simpleMove(rc, formPoint);

    // When 7 rats clustered:
    if (rc.canBecomeRatKing()) {
        rc.becomeRatKing(); // One becomes 2nd king!
    }
    return;
}
```

**Benefit**: Actually uses unique 2nd king strategy
**Risk**: Temporarily loses 7 collectors during formation

### Change 5: Cheese Mine Tracking (MEDIUM)

**Problem**: Collectors compete for same mines
- Multiple collectors go to same cheese
- Inefficient
- No coordination

**Solution**: King tracks mines, assigns collectors
```java
// King scans for cheese mines (every 10 rounds):
MapLocation[] nearbyLocs = rc.getAllLocationsWithinRadiusSquared(me, visionRange);
for (MapLocation loc : nearbyLocs) {
    if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        if (info.hasCheeseMine() && mineCount < 10) {
            // Write to shared array
            rc.writeSharedArray(40 + mineCount * 2, loc.x);
            rc.writeSharedArray(41 + mineCount * 2, loc.y);
            mineCount++;
        }
    }
}

// Collectors read assigned mine:
int myMineIndex = collectorNumber % mineCount;
int mineX = rc.readSharedArray(40 + myMineIndex * 2);
int mineY = rc.readSharedArray(41 + myMineIndex * 2);
MapLocation assignedMine = new MapLocation(mineX, mineY);
// Collect from assigned mine only
```

**Benefit**: 2x collection efficiency (no competition)
**Cost**: ~50 lines of code

## ratbot5 Architecture

### File Structure:
```
ratbot5/RobotPlayer.java (~950 lines, under 1000 limit)
├── King behavior (spawn, track, coordinate)
├── Baby rat roles (attacker vs collector)
├── Attacker behavior (combat, no suicide)
├── Collector behavior (mines, instant replacement)
├── Movement (greedy + Bug2)
├── Bug2 pathfinding (~150 lines from ratbot2)
├── Communication (squeaks + shared array)
├── Squeak encoding/decoding
└── Constants
```

### Shared Array Allocation (ratbot5):
```
Slot 0-1: Our king position
Slot 2-3: Enemy king position
Slot 4: Formation signal (0=normal, 1=form 2nd king)
Slot 5: Primary target ID (focus fire)
Slot 6-9: Combat stats (kills, deaths, damage dealt)
Slot 10-39: Cheese mine locations (15 mines × 2 slots)
Slot 40-63: Reserved
```

### Population Strategy:
- Initial: 15 rats (10 attackers, 5 collectors)
- Replacement: Instant when collectors < 4
- Maximum: 25 rats total
- 2nd king: Form at round 50 if 14+ rats and 800+ cheese

### Combat Strategy:
- Focus fire: All attackers attack lowest HP enemy
- Shared array slot 5: Primary target ID
- Enhanced attacks in backstab mode only
- NO suicide (unless population > 20)

## Implementation Phases

### Phase 1: Critical Fixes (Today - Jan 8)
1. Remove/fix attacker suicide
2. Add instant collector replacement
3. Test: Does economy survive longer?

### Phase 2: Pathfinding (Tomorrow - Jan 9)
1. Integrate Bug2 from ratbot2
2. Use when stuck > 2 rounds
3. Test: Better navigation?

### Phase 3: Advanced (Jan 10-12)
1. Fix 2nd king formation
2. Add cheese mine tracking
3. Add focus fire coordination
4. Test: Performance improvements?

### Phase 4: Polish (Jan 13+)
1. Tune all parameters
2. Remove any remaining logging
3. Test vs multiple opponents
4. Prepare for Sprint 1 (Jan 12)

## Success Criteria

**Must Have**:
- [ ] Win in < 50 rounds (faster than ratbot4's 61)
- [ ] Collectors always >= 4 (instant replacement working)
- [ ] Attackers survive past round 100 (no mass suicide)
- [ ] 2nd king forms at least once in 10 matches
- [ ] Bug2 navigation reduces stuck time

**Nice to Have**:
- [ ] Cheese mine coordination working
- [ ] Focus fire kills enemies faster
- [ ] Adaptive spawning based on game state
- [ ] Win rate > 80% vs ratbot4

## Validation Plan

**Before implementing**:
- Review this plan
- Confirm priorities
- Agree on phased approach

**After Phase 1**:
- Test vs ratbot4
- Verify economy survives
- Check collector count maintained

**After Phase 2**:
- Test navigation
- Count stuck instances
- Verify bytecode budget

**After Phase 3**:
- Full feature test
- Performance benchmarks
- Competitive readiness

---

**Ready to implement Phase 1 (critical fixes)?**
Or should I adjust the plan based on your priorities?
