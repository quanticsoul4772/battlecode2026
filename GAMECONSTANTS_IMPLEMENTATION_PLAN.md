# GameConstants Implementation Plan

## Constants We Should Use

### High Priority (Immediate Use):

**1. CHEESE_COOLDOWN_PENALTY = 0.01**
- **What**: 1% cooldown penalty per cheese carried
- **Impact**: Carrying 20 cheese = 20% slower movement
- **Current**: We ignore this (collectors might carry 30+ cheese)
- **Fix**: Deliver at 10 cheese instead of waiting for 15+
- **Already doing**: Collectors deliver at 10 (threshold in code)
- **Action**: Document why we use 10 (optimal for speed)

**2. COOLDOWN_LIMIT = 10**
- **What**: Can't act/move if cooldown >= 10
- **Current**: We assume this but don't check explicitly
- **Fix**: Before movement, verify cooldown < 10
- **Benefit**: Avoid wasted turn() calls when cooldown high
- **Code**:
```java
if (rc.getMovementCooldownTurns() >= GameConstants.COOLDOWN_LIMIT) {
    return; // Can't move this round, save bytecode
}
```

**3. RATKING_CHEESE_CONSUMPTION = 3**
- **What**: King consumes 3 cheese per round
- **Current**: We hardcode 100 cheese reserve = 33 rounds
- **Fix**: Use constant for clarity
- **Code**:
```java
// Reserve calculation
int reserveRounds = 33; // How many rounds king can survive
int reserve = GameConstants.RATKING_CHEESE_CONSUMPTION * reserveRounds;
```

**4. MAX_NUMBER_OF_RAT_KINGS = 5**
- **What**: Can have up to 5 kings simultaneously!
- **Current**: We only use 1 king (planned 2nd king but not implemented)
- **Opportunity**: Form multiple kings for redundancy
- **Strategy**:
  - Primary king: Spawns rats
  - Secondary kings: Collect cheese directly, backup if primary dies
- **When**: Round 75+ if we have 7+ rats and 50 cheese surplus

### Medium Priority (Future Enhancement):

**5. RAT_KING_UPGRADE_CHEESE_COST = 50**
- **What**: Cost to form new king (7 rats + 50 cheese)
- **Use**: Calculate if we can afford 2nd king formation
- **Code**:
```java
if (spawnCount > 15 && rc.getGlobalCheese() > 1000) {
    // Check if can form 2nd king
    RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
    if (allies.length >= 6 && rc.canBecomeRatKing()) {
        rc.becomeRatKing(); // Form 2nd king!
    }
}
```

**6. SQUEAK_RADIUS_SQUARED = 256**
- **What**: Squeaks reach 16 tiles
- **Current**: We mention in comments but don't use
- **Use**: Check if target is in squeak range before squeaking
- **Code**:
```java
if (me.distanceSquaredTo(allyPosition) <= GameConstants.SQUEAK_RADIUS_SQUARED) {
    rc.squeak(message); // Ally can receive
}
```

**7. BUILD_ROBOT_COST_INCREASE = 10**
- **What**: Every 4 rats alive, spawn cost increases by 10
- **Formula**: cost = BASE_COST + (livingRats / 4) * 10
- **Current**: We use rc.getCurrentRatCost() (already correct!)
- **Use**: Predict future costs for economic planning

**8. THROW_DAMAGE = 20**
- **What**: Base damage when thrown rat hits ground
- **Plus**: THROW_DAMAGE_PER_TILE = 5 per tile traveled
- **Current**: We ratnap but don't calculate damage
- **Use**: Optimize throw distance for max damage
- **Code**:
```java
// Throw rat 4 tiles for max damage
// Damage = 20 + (4 * 5) = 40 total
```

### Low Priority (Nice to Have):

**9. EXCEPTION_BYTECODE_PENALTY**
- Each exception costs extra bytecode
- Minimize try/catch usage
- Current: 3 try/catch blocks (acceptable)

**10. MAP_MAX/MIN dimensions**
- Could validate positions before creating MapLocation
- Probably not worth bytecode cost

## Implementation Phases

### Phase 1: Constants for Clarity (Zero Performance Impact)

Replace hardcoded values with GameConstants for maintainability:

```java
// Before:
if (cheese > cost + 100) // Magic number

// After:
int reserve = GameConstants.RATKING_CHEESE_CONSUMPTION * 33;
if (cheese > cost + reserve) // Clear meaning
```

**Changes**:
- RATKING_CHEESE_CONSUMPTION (hardcoded 3)
- RATKING_HEALTH_LOSS (mentioned in comments)
- BUILD_ROBOT_BASE_COST (use in comments)

**Benefit**: Code self-documents, easier to tune
**Cost**: 0 bytecode (constants are compile-time)

### Phase 2: Performance Checks (Small Savings)

Add cooldown checks to avoid wasted operations:

```java
// Before moving
if (rc.getMovementCooldownTurns() >= GameConstants.COOLDOWN_LIMIT) {
    return; // Can't move, skip expensive pathfinding
}

// Before attacking
if (rc.getActionCooldownTurns() >= GameConstants.COOLDOWN_LIMIT) {
    return; // Can't attack, skip checks
}
```

**Benefit**: Skip expensive operations when on cooldown
**Cost**: 100 bytecode (2 checks)
**Savings**: 500-1000 bytecode when skipping pathfinding/attack logic

### Phase 3: Trap Avoidance (Safety)

Check for traps before moving:

```java
// In simpleMove(), before moving
MapInfo nextInfo = rc.senseMapInfo(nextLoc);
if (nextInfo.getTrap() != TrapType.NONE) {
    // Enemy trap! Try different direction
    // Saves 50 damage + 20 cooldown stun
}
```

**Benefit**: Avoid enemy traps (50 damage saved)
**Cost**: 100 bytecode per move (senseMapInfo call)
**Worth it**: Yes, 50 damage > 100 bytecode cost

### Phase 4: Second King Formation (Advanced)

Form 2nd king for redundancy:

```java
// In baby rat, round 75+
if (round > 75 && rc.getGlobalCheese() > 1000) {
    RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
    if (allies.length >= 6) { // Need 7 total including self
        if (rc.canBecomeRatKing()) {
            rc.becomeRatKing(); // Costs 50 cheese
            // This rat becomes a 2nd king (HP = sum of 7 rats)
        }
    }
}
```

**Benefit**:
- Backup if 1st king dies
- Two spawn points (double production)
- Tankier king (500 HP vs 100)

**Risk**:
- Loses 7 attackers temporarily
- 50 cheese cost
- Requires coordination

## Recommended Implementation Order

**Week 1 (Now)**:
1. Phase 1: Add constants for clarity (commit tonight)
2. Phase 2: Cooldown checks (test tomorrow)
3. Phase 3: Trap avoidance (test tomorrow)

**Week 2**:
4. Phase 4: 2nd king formation (test in scrimmages)

## Validation Criteria

**Phase 1 (Constants)**:
- [ ] Code compiles
- [ ] No functionality change
- [ ] Constants documented in comments

**Phase 2 (Cooldown checks)**:
- [ ] Bytecode savings measured (500-1000 per round)
- [ ] No functionality regression
- [ ] Match length unchanged (still wins)

**Phase 3 (Trap avoidance)**:
- [ ] Rats avoid enemy traps
- [ ] Fewer deaths from traps (test vs lectureplayer)
- [ ] Bytecode cost acceptable (<200 per round)

**Phase 4 (2nd king)**:
- [ ] Formation succeeds (7 rats → 1 king)
- [ ] 2nd king spawns rats
- [ ] Economy improves (2 spawn points)
- [ ] Test: Can we win with 2 kings vs 1 king enemy?

---

**Ready to implement Phase 1?**
