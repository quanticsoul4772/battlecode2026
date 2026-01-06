# Battlecode 2026 - Verified Constants

**Source**: Official javadoc constant-values.html
**Date**: January 5, 2026
**Spec Version**: 1

---

## Critical Constants (Verified from Javadoc)

### Starting Resources
- `INITIAL_TEAM_CHEESE = 2500` ✓ (matches PDF)

### Spawning Costs
- `BUILD_ROBOT_BASE_COST = 10` ✓
- `BUILD_ROBOT_COST_INCREASE = 10` ✓
- `NUM_ROBOTS_FOR_COST_INCREASE = 4` ✓
- **Formula**: `10 + 10 × floor(n/4)` where n = baby rats alive

### King Mechanics
- `RAT_KING_UPGRADE_CHEESE_COST = 50` ✓
- `RATKING_CHEESE_CONSUMPTION = 3` ✓ (per round)
- `RATKING_HEALTH_LOSS = 10` ✓ (when unfed)
- `MAX_NUMBER_OF_RAT_KINGS = 5` ✓
- `NUMBER_INITIAL_RAT_KINGS = 1` ✓

### Cooldowns
- `COOLDOWNS_PER_TURN = 10` (reduction per round)
- `COOLDOWN_LIMIT = 10` (threshold for readiness)
- `TURNING_COOLDOWN = 10` ✓
- `MOVE_STRAFE_COOLDOWN = 18` (NEW - not in PDF)
- `BUILD_ROBOT_COOLDOWN = 10` ✓
- `CHEESE_TRANSFER_COOLDOWN = 10` (NEW)
- `DIG_COOLDOWN = 25` ✓
- `CARRY_COOLDOWN_MULTIPLIER = 1.5` (NEW - PDF says this but no value)

### Damage Values
- `RAT_BITE_DAMAGE = 10` ✓
- `CAT_SCRATCH_DAMAGE = 50` ✓
- `THROW_DAMAGE = 20` ✓ (base, max when airtime = 0)
- `THROW_DAMAGE_PER_TILE = 5` ✓
- `CAT_POUNCE_ADJACENT_DAMAGE_PERCENT = 50` ✓

### Throwing Mechanics
- `THROW_DURATION = 4` ✓ (turns in air)
- `HIT_GROUND_COOLDOWN = 10` ✓
- `HIT_TARGET_COOLDOWN = 30` ✓
- `MAX_CARRY_DURATION = 10` ✓ (auto-drop)
- `MAX_CARRY_TOWER_HEIGHT = 2`
- `HEALTH_GRAB_THRESHOLD = 0`

### Cheese Economics
- `CHEESE_COOLDOWN_PENALTY = 0.01` ✓ (per cheese)
- `CHEESE_SPAWN_AMOUNT = 5` ✓

### Dirt
- `DIG_DIRT_CHEESE_COST = 10` ✓
- `PLACE_DIRT_CHEESE_COST = 10` ✓
- `DIG_COOLDOWN = 25` ✓
- `CAT_DIG_ADDITIONAL_COOLDOWN = 5`

### Traps (from TrapType enum)
- **Rat Trap**: 5 cheese cost (from PDF), 50 damage, sqrt(2) trigger radius
- **Cat Trap**: 10 cheese cost (from PDF), 100 damage, sqrt(2) trigger radius

### Communication
- `SHARED_ARRAY_SIZE = 64` ✓
- `COMM_ARRAY_MAX_VALUE = 1023` ✓ (10 bits)
- `SQUEAK_RADIUS_SQUARED = 16` ✓ (sqrt(16) = 4)
- `MAX_MESSAGES_SENT_ROBOT = 1` ✓ (per turn)
- `MESSAGE_ROUND_DURATION = 5` ✓

### Cats
- `CAT_SLEEP_TIME = 2` ✓
- `CAT_POUNCE_MAX_DISTANCE_SQUARED = 9` (sqrt(9) = 3)

### Misc
- `EXCEPTION_BYTECODE_PENALTY = 500`
- `INDICATOR_STRING_MAX_LENGTH = 256`
- `TIMELINE_LABEL_MAX_LENGTH = 64`

---

## Unit Stats (from PDF - javadoc doesn't expose)

### Baby Rat
- **Health**: 100
- **Size**: 1×1
- **Movement Cooldown**: 10 (base, forward)
- **Turning Cooldown**: 10
- **Vision Radius Squared**: 20 (sqrt(20) ≈ 4.47 tiles)
- **Vision Cone Angle**: 90°
- **Bytecode Limit**: 17,500

### Rat King
- **Health**: 500
- **Size**: 3×3
- **Movement Cooldown**: 40 (base)
- **Turning Cooldown**: 10
- **Vision Radius Squared**: 25 (sqrt(25) = 5 tiles)
- **Vision Cone Angle**: 360°
- **Bytecode Limit**: 20,000

### Cat
- **Health**: 10,000
- **Size**: 2×2
- **Movement Cooldown**: 10
- **Vision Radius Squared**: 30 (sqrt(30) ≈ 5.48 tiles)
- **Vision Cone Angle**: 180°

---

## Important Discrepancies to Verify

### Cheese Spawn Area
- **PDF says**: "9x9 square near cheese mines"
- **Javadoc says**: `SQ_CHEESE_SPAWN_RADIUS = 4` (radius² = 4, so radius ≈ 2)
- **Actual area**: If radius 2, that's roughly 5×5, not 9×9

**Status**: Need to test in actual game to verify
**Impact**: Affects cheese collection patrol radius

### Movement Cooldown Details
- **PDF**: Says "forward cheaper than strafing" but doesn't specify
- **Javadoc**: `MOVE_STRAFE_COOLDOWN = 18` vs base 10
- **Difference**: Strafing is 1.8× slower (18 vs 10)

**Status**: Good to know exact value
**Impact**: Affects movement optimization - strongly prefer forward movement

### Carry Cooldown Multiplier
- **PDF**: Mentions multiplier but no value
- **Javadoc**: `CARRY_COOLDOWN_MULTIPLIER = 1.5`

**Status**: Now have exact value
**Impact**: Carrying a rat = 1.5× slower movement

---

## Calculations to Update in Our Code

### Spawn Cost
```java
// Current (correct):
int cost = 10 + 10 * (babyRatsAlive / 4);

// Examples:
// 0-3 rats: 10
// 4-7 rats: 20
// 8-11 rats: 30
```

### Movement with Cheese
```java
// Current formula in mock (needs update):
// movementCooldown = 10 * (1 + 0.01 * rawCheese)

// Should be:
// Forward: 10 + (10 * 0.01 * rawCheese)
// Strafe: 18 + (18 * 0.01 * rawCheese)

// Example with 50 cheese:
// Forward: 10 + 5 = 15
// Strafe: 18 + 9 = 27
```

### Movement while Carrying Rat
```java
// Multiplier: 1.5x
// Forward: 10 * 1.5 = 15
// Strafe: 18 * 1.5 = 27
```

### Cheese Transfer Range
```java
// CHEESE_DROP_RADIUS_SQUARED = 9
// Range: sqrt(9) = 3 tiles
// Not 4 as I assumed in mock!
```

---

## Updates Needed to Mock Framework

### MockRobotController.move()
```java
public void move(Direction dir) {
    location = location.add(dir);

    // Base cooldown
    int baseCooldown = (dir == facing) ? 10 : MOVE_STRAFE_COOLDOWN; // 10 or 18

    // Cheese penalty
    movementCooldown += baseCooldown;
    if (rawCheese > 0) {
        int penalty = (int)(baseCooldown * CHEESE_COOLDOWN_PENALTY * rawCheese);
        movementCooldown += penalty;
    }

    // Carrying penalty
    if (isCarrying) {
        movementCooldown = (int)(movementCooldown * CARRY_COOLDOWN_MULTIPLIER);
    }
}
```

### MockRobotController.canTransferCheese()
```java
public boolean canTransferCheese(MapLocation loc, int amount) {
    if (rawCheese < amount) return false;
    if (location.distanceSquaredTo(loc) > CHEESE_DROP_RADIUS_SQUARED) return false; // Use 9 not 16!
    // ...
}
```

---

## Action Items

### Update Mock Framework
- [ ] Fix movement cooldown calculation (strafe = 18, not 10)
- [ ] Fix cheese transfer range (9, not 16)
- [ ] Add carrying cooldown multiplier (1.5×)
- [ ] Verify all constants match javadoc

### Update Algorithm Modules
- [ ] Import Constants.java for hardcoded values
- [ ] Replace magic numbers with named constants
- [ ] Update calculations to use exact formulas

### Create Tests for Constants
- [ ] Test spawn cost formula
- [ ] Test cheese spawn probability
- [ ] Test bite damage enhancement
- [ ] Test movement cooldown calculations

---

## Summary

**Good News**: PDF spec and javadoc constants align on critical values
- Starting cheese: 2500 ✓
- Spawn cost: 10 + 10×floor(n/4) ✓
- King consumption: 3/round ✓
- Bytecode limits: 17,500 / 20,000 ✓

**New Information** from javadoc:
- Strafe cooldown: 18 (not just "extra")
- Carry multiplier: 1.5× (exact value)
- Transfer range: 3 tiles (not 4)
- Cheese spawn radius: 2 tiles (not 4-5)

**Minor Discrepancy**:
- Cheese spawn area: PDF says 9×9, javadoc suggests 5×5
- Need to verify in actual game

**Impact**: Our algorithm modules are sound, need minor constant updates to mock framework

**Next**: Update mock framework with exact cooldown calculations
