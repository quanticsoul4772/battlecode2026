# Ratbot7 Defense System Documentation

This document details all defensive improvements implemented in ratbot7 to counter aggressive assault strategies like ratbot5.

---

## Overview

Ratbot7 is designed as a **pure defense bot** - it survives enemy onslaughts and then recovers to win through attrition. The defense system includes:

1. **Multi-layer defensive roles** (Guardians, Sentries, Interceptors)
2. **Economy protection** (spawn throttling, recovery mode)
3. **Defensive formations** (tight blocking, emergency convergence)
4. **Defensive combat** (kiting state machine)
5. **Strategic fortifications** (dirt walls, trap funnels)
6. **Starvation prevention** (urgent delivery, mass emergency) - **NEW**

---

## Role Distribution

| Role | % | Range | Purpose |
|------|---|-------|---------|
| **Gatherer** | 55% | 0-55 | Economy - collect and deliver cheese |
| **Sentry** | 15% | 55-70 | Outer defense ring - patrol and block |
| **Guardian** | 15% | 70-85 | Inner defense ring - NEVER leaves king |
| **Interceptor** | 10% | 85-95 | Mid-map patrol - early warning and delay |
| **Scout** | 5% | 95-100 | Exploration - find enemies and cheese |

---

## Session 3: Starvation Analysis & Critical Fixes (January 2025)

### The Discovery: King Dies from Starvation, Not Combat

**Match Analysis (Round 335 Death):**
```
Round 280-335: cheese=0 for 55 consecutive rounds
Round 310-335: King HP drops 250→10 (losing 10 HP/round from starvation)
Round 335: King dies with threat=1 (only 1 enemy nearby!)
```

**The king wasn't killed by enemies - it STARVED TO DEATH.**

### Root Cause Analysis

#### Timeline of the Economy Collapse

| Round | Event | Cheese | King HP | Spawns |
|-------|-------|--------|---------|--------|
| 1-30 | Build phase | 2500→2300 | 500 | 0→6 |
| 30-100 | Sustained combat | 2300→1500 | 500→400 | 6→20 |
| 100-200 | ATK phase begins | 1500→500 | 400→350 | 20→30 |
| 200-280 | Economy drains | 500→0 | 350→280 | 30→36 |
| 280-335 | **STARVATION** | **0** | 280→10 | 36 (stuck) |

#### The Kill Chain

1. **36 spawns depleted all cheese** (~1440 cheese spent on spawns)
2. **ATK phase sent gatherers too far** (15-20 tiles from king)
3. **CRISIS_DELIVER failed** - gatherers logged "rushing_to_king" but distance NEVER decreased
4. **Rat traps blocked the path** - `canMoveSafely()` returns false for rat traps
5. **King starved** - no cheese delivered for 55 rounds

#### Evidence: CRISIS_DELIVER Was Broken

```
Round 310: #13486 CRISIS_DELIVER:distKing=13
Round 315: #13486 CRISIS_DELIVER:distKing=13  ← No progress!
Round 320: #13486 CRISIS_DELIVER:distKing=17  ← Moved AWAY!
Round 325: #13486 CRISIS_DELIVER:distKing=17  ← Still stuck
```

Gatherers claimed they were rushing to the king but their distance stayed at 10-18 for 30+ rounds!

### The Fixes Implemented

#### Fix #1: Urgent Movement Through Rat Traps (CRITICAL)

**Problem:** `canMoveSafely()` blocks movement through rat traps, even when king is dying.

**Solution:** Added `bug2MoveToUrgent()` function that ignores rat traps:

```java
// Crisis movement - allows movement through rat traps when king is dying
private static boolean canMoveCrisis(RobotController rc, Direction dir) {
    return rc.canMove(dir); // Ignore rat traps - king survival is more important
}

private static void bug2MoveToUrgent(RobotController rc, MapLocation target) {
    // Uses canMoveCrisis instead of canMoveSafely
    // Accepts 30 HP trap damage to save the king from starvation
}
```

**Usage:** When `kingHP < 100`, CRISIS_DELIVER uses urgent movement:
```java
if (cachedKingHP > 0 && cachedKingHP < 100) {
    bug2MoveToUrgent(rc, cachedOurKingLoc);
} else {
    bug2MoveTo(rc, cachedOurKingLoc);
}
```

#### Fix #2: Mass Emergency - All Roles Become Gatherers

**Problem:** When cheese=0 and king HP < 200, guardians/sentries stayed in position while king starved.

**Solution:** Force ALL roles to act as gatherers during mass emergency:

```java
boolean massEmergency = cachedGlobalCheese == 0 
    && cachedKingHP > 0 
    && cachedKingHP < MASS_EMERGENCY_KING_HP;

if (massEmergency && hasOurKingLoc) {
    // ALL rats must help gather cheese - ignore role
    if (cachedCarryingCheese) {
        bug2MoveToUrgent(rc, cachedOurKingLoc); // Rush delivery
    } else {
        scoreAllTargetsGatherer(...); // Find cheese
        bug2MoveToUrgent(rc, cachedBestTarget);
    }
    return;
}
```

**Debug Log:**
```
MASS_EMERGENCY:380:10953:role=0:kingHP=30:converting_to_gatherer
MASS_EMERGENCY:380:12298:role=2:kingHP=30:converting_to_gatherer
```

#### Fix #3: Tighter ATK Phase Economy Limit

**Problem:** ATK_PHASE_EXPLORE_CHEESE = 1000 was too low - gatherers explored when cheese was getting critical.

**Solution:** Raised threshold to 1500:
```java
private static final int ATK_PHASE_EXPLORE_CHEESE = 1500; // Was 1000
```

Now gatherers stay closer to king when cheese drops below 1500.

#### Fix #4: Lower Spawn Cap

**Problem:** 36 spawns × ~40 cheese average = 1440 cheese depleted.

**Solution:** Reduced spawn cap from 25 to 20:
```java
private static final int SPAWN_CAP_MAX_SPAWNS = 20; // Was 25
```

Saves ~200 cheese over the course of a match.

### New Constants Added

| Constant | Value | Description |
|----------|-------|-------------|
| `MASS_EMERGENCY_KING_HP` | 200 | Threshold for mass emergency |
| `ATK_PHASE_EXPLORE_CHEESE` | 1500 | Don't explore if cheese < this (was 1000) |
| `SPAWN_CAP_MAX_SPAWNS` | 20 | Max spawns when cheese low (was 25) |

### New Functions Added

| Function | Purpose |
|----------|---------|
| `canMoveCrisis()` | Movement check that ignores rat traps |
| `bug2MoveToUrgent()` | Urgent pathfinding for crisis delivery |

### Debug Logs Added

```
// Mass Emergency (every 10 rounds during crisis)
MASS_EMERGENCY:380:10953:role=0:kingHP=30:converting_to_gatherer

// Crisis Deliver (now includes kingHP)
CRISIS_DELIVER:320:13486:rushing_to_king:distKing=13:kingHP=160
```

### Results

| Version | Survival | Change |
|---------|----------|--------|
| Before starvation fixes | 335 rounds | Baseline |
| After starvation fixes | **381 rounds** | **+46 rounds (+14%)** |

---

## Economy Protection (Updated)

### Economy Anti-Collapse System

| Constant | Value | Description |
|----------|-------|-------------|
| `ABSOLUTE_CHEESE_RESERVE` | 300 | NEVER spawn if cheese would drop below this |
| `SPAWN_CAP_CHEESE_THRESHOLD` | 500 | Apply spawn cap when cheese < this |
| `SPAWN_CAP_MAX_SPAWNS` | 20 | Max spawns when cheese is low |
| `EMERGENCY_RECALL_CHEESE` | 500 | Force all gatherers home when cheese < this |
| `ATK_PHASE_EXPLORE_CHEESE` | 1500 | Don't explore in ATK phase if cheese < this |
| `HOME_GUARD_COUNT` | 3 | Gatherers that ALWAYS stay near king |
| `HOME_GUARD_RADIUS_SQ` | 25 | Home guard must stay within 5 tiles |

### Multi-Layer Protection

```
Layer 1: ATK_PHASE_EXPLORE_CHEESE (1500) - Gatherers stay closer
Layer 2: ECONOMY_RECOVERY_THRESHOLD (800) - Recovery mode triggers
Layer 3: EMERGENCY_RECALL_CHEESE (500) - All gatherers recall
Layer 4: ABSOLUTE_CHEESE_RESERVE (300) - Spawn blocking
Layer 5: MASS_EMERGENCY (cheese=0, HP<200) - ALL roles gather
```

### Home Guard System

~17% of gatherers are designated as "home guards" who NEVER explore far:

```java
// Calculated once in updateGameState (ID never changes)
if (cachedRound == 1) {
    cachedIsHomeGuard = (rc.getID() % 18) < HOME_GUARD_COUNT;
}

// In gatherer logic
if (cachedIsHomeGuard && cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
    // Return to king immediately
    cachedBestTarget = cachedOurKingLoc;
}
```

---

## Defensive Roles

### Guardian Role (NEW)

**Purpose:** Inner defense ring that NEVER abandons the king.

| Constant | Value | Description |
|----------|-------|-------------|
| `GUARDIAN_INNER_DIST_SQ` | 9 | Stay at least 3 tiles from king |
| `GUARDIAN_OUTER_DIST_SQ` | 16 | Stay within 4 tiles of king |
| `GUARDIAN_ATTACK_RANGE_SQ` | 16 | Attack enemies within 4 tiles of king |

**Key Behaviors:**
- **Exempted from emergency CONVERGE** - Guardians execute their logic BEFORE the emergency check
- **Smart intercept logic** - Only attacks if enemy is BOTH within range AND closer to king than Guardian (`enemyBypassing` check)
- **GUARDIAN_RETURN** - Returns immediately if too far from king
- **GUARDIAN_HOLD** - Holds position when no bypassing enemies
- **GUARDIAN_PATROL** - Moves outward if too close to king

### Interceptor Role (NEW)

**Purpose:** Mid-map patrol that slows enemy rushes and provides early warning.

| Constant | Value | Description |
|----------|-------|-------------|
| `INTERCEPTOR_PATROL_DIST` | 15 | Patrol 15 tiles toward enemy |
| `INTERCEPTOR_ENGAGE_DIST_SQ` | 64 | Engage enemies within 8 tiles |
| `INTERCEPTOR_RETURN_THREAT` | 4 | Return to defend when threat >= 4 |

---

## Recovery Mode System

### Recovery Trigger Conditions

Recovery mode can be triggered by **4 different conditions**:

| Trigger | Condition | Reason Code |
|---------|-----------|-------------|
| **Low Threat** | `threat <= 3 && cheese < 800 && spawns > 5` | `LOW_THREAT` (1) |
| **Cheese Critical** | `cheese < 200 && spawns > 3` | `CHEESE_CRITICAL` (2) |
| **Sustained Combat** | `threat >= 3 && cheese < 1000 && spawns > 8` | `SUSTAINED_COMBAT` (3) |
| **King Pressure** | `kingHP < 400 && spawns > 5` | `KING_PRESSURE` (4) |

### Recovery Exit Conditions

**Critical Design Decision:** Exit condition depends on what triggered recovery!

| Trigger | Exit Condition | Rationale |
|---------|----------------|-----------|
| Low Threat | `cheese >= 1000` | Cheese-based exit |
| Cheese Critical | `cheese >= 1000` | Cheese-based exit |
| Sustained Combat | `cheese >= 1000` | Cheese-based exit |
| **King Pressure** | `kingHP > 450` OR `(rounds > 50 && cheese >= 1000)` | **HP-based exit with timeout fallback** |

---

## Critical Lessons Learned

### Lesson #1: Starvation is Deadlier Than Combat

The king loses 10 HP/round when unfed. With 500 starting HP, that's only 50 rounds of starvation to death. Combat damage is often LESS than starvation damage:
- Enemy attack: 10 damage/hit, maybe 2-3 hits/round = 20-30 damage
- Starvation: 10 damage/round GUARANTEED

**Always prioritize cheese delivery over combat!**

### Lesson #2: Rat Traps Can Kill Your Own King

Our defensive rat traps were blocking CRISIS_DELIVER gatherers from reaching the king. The 30 HP trap damage is worth it if it saves the king from starvation.

**Solution:** `bug2MoveToUrgent()` ignores rat traps when king HP < 100.

### Lesson #3: Track WHY Something Was Triggered

Different triggers may need different exit conditions. Recovery triggered by low cheese should exit when cheese is high. Recovery triggered by low king HP should exit when HP recovers.

### Lesson #4: Don't Assume Scarcity During Abundance

The spawn pause bug assumed "king under pressure = need to conserve resources". But king was under pressure BECAUSE we didn't have enough defenders. With 2000+ cheese, we should spawn MORE, not pause.

### Lesson #5: Mass Emergency Override

When cheese=0 AND king HP is critical, normal role behavior must be overridden. ALL rats must gather cheese, regardless of their assigned role.

---

## Performance Evolution

| Version | Survival (rounds) | Issue |
|---------|-------------------|-------|
| Original | 142 | No recovery mode |
| After 6 defensive improvements | 214 | Recovery mode oscillation |
| After recovery trigger fixes | 195 | Baseline stable |
| **After spawn pause bug** | **63** | Death spiral! |
| After spawn pause fix | 195 | ✅ Fixed |
| After economy anti-collapse | **335** | Improved |
| **After starvation fixes** | **381** | **+46 rounds** |
| After early rush defense | 559 | Faster trap placement |
| After predictive starvation | 520 | Early warning system |
| After late-game economy | 555 | Zero income detection |
| **After patrol death spiral fix** | **648 (WIN!)** | **Hysteresis explore mode** |
| After starvation search distribution fix | 326+ | All rats spread out during starvation |

---

## Session 4: Patrol Death Spiral Fix & Hysteresis Logic (January 2025)

### The Discovery: Gatherers Ignoring Visible Cheese

**Match Analysis (Round 555 Death):**
```
Round 520-555: cheese=0, but CHEESE VISIBLE ON MAP!
All gatherers: target=PATROL:distKing=5-20:emergency=false
No gatherers with target=CHEESE despite cheese being available
```

**The gatherers weren't collecting cheese - they were stuck patrolling near the king where cheese was already depleted!**

### Root Cause: The Patrol Death Spiral

```
┌─────────────────────────────────────────────────────────────┐
│                    PATROL DEATH SPIRAL                       │
│                                                              │
│  1. Gatherers patrol near king (distKing=5-20)              │
│                    ↓                                         │
│  2. Cheese scan radius = 13 tiles from GATHERER position    │
│                    ↓                                         │
│  3. Cheese at distance 15-25 from king = INVISIBLE          │
│                    ↓                                         │
│  4. No cheese in vision → fallback to PATROL toward king    │
│                    ↓                                         │
│  5. Patrol pulls gatherers CLOSER to depleted area          │
│                    ↓                                         │
│  6. REPEAT until king starves                               │
└─────────────────────────────────────────────────────────────┘
```

**The BUG:** When no cheese was visible, gatherers defaulted to patrolling TO the king instead of exploring AWAY to find distant cheese.

### The Fixes Implemented

#### Fix #1: Increased Scan Radius When Starving

**Problem:** Cheese scan radius was always 13 tiles, missing distant cheese.

**Solution:** Increase scan radius to 25 when cheese is critically low:

```java
// In findNearbyCheese()
int scanRadius = (cachedGlobalCheese < STARVATION_THRESHOLD) ? 25 : 13;
MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, scanRadius);
```

**Bytecode Note:** Radius 25 = sqrt(25) = 5 tiles = ~80 tiles to process (moderate increase from ~50 tiles).

#### Fix #2: Remove Distance Leash When Starving

**Problem:** Late-game leash (`LATE_GAME_MAX_DIST_SQ = 100`) trapped gatherers near depleted areas.

**Solution:** Remove leash entirely when cheese is critically low:

```java
boolean starvingRemoveLeash = globalCheese < STARVATION_LEASH_REMOVAL && !cachedCarryingCheese;
if (starvingRemoveLeash) {
    maxExploreDistSq = Integer.MAX_VALUE; // Remove leash - gatherers MUST find cheese!
}
```

#### Fix #3: Explore Instead of Patrol (with Hysteresis)

**Problem:** When no cheese was visible, gatherers patrolled TO the king (depleted area).

**Solution:** When starving, explore AWAY from king to find cheese:

```java
if (cachedBestTarget == null && cachedOurKingLoc != null) {
    boolean shouldExploreForCheese = globalCheese < STARVATION_EXPLORE_EXIT && !cachedCarryingCheese;
    boolean inStarvationZone = globalCheese < STARVATION_THRESHOLD;
    
    if (inStarvationZone) {
        // CRITICAL: Explore AWAY from king to find cheese!
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 100; // High priority
    } else if (shouldExploreForCheese) {
        // Hysteresis zone (200-500): continue exploring to be safe
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 50; // Lower priority
    } else {
        // Cheese is healthy (> 500): patrol near king
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_PATROL;
        cachedBestScore = 1;
    }
}
```

### Hysteresis Logic Explained

**Why Hysteresis?** Without hysteresis, gatherers oscillate:
```
Round 1: cheese=199 → EXPLORE
Round 2: cheese=201 → PATROL (found 2 cheese)
Round 3: cheese=199 → EXPLORE (delivered 2 cheese)
Round 4: cheese=201 → PATROL
... infinite oscillation
```

**The Solution:** Different thresholds for entry and exit:

```
┌─────────────────────────────────────────────────────────────┐
│              HYSTERESIS STATE MACHINE                        │
│                                                              │
│  Cheese < 200 (STARVATION_THRESHOLD)                        │
│      → EXPLORE with high priority (score=100)               │
│                                                              │
│  Cheese 200-500 (Hysteresis Zone)                           │
│      → EXPLORE with lower priority (score=50)               │
│      → Prevents oscillation at boundary values              │
│                                                              │
│  Cheese > 500 (STARVATION_EXPLORE_EXIT)                     │
│      → PATROL near king (score=1)                           │
│      → Economy is healthy, normal behavior                  │
└─────────────────────────────────────────────────────────────┘
```

**Important Note:** The hysteresis zone (200-500) defaults to EXPLORE because:
1. Static variables in Battlecode are shared across ALL robots
2. Per-robot state tracking is unreliable
3. It's safer to explore when cheese is low than risk starvation

### New Constants Added

| Constant | Value | Description |
|----------|-------|-------------|
| `STARVATION_THRESHOLD` | 200 | Enter explore mode when cheese < this |
| `STARVATION_EXPLORE_EXIT` | 500 | Exit explore mode when cheese > this |
| `STARVATION_LEASH_REMOVAL` | 150 | Remove distance leash when cheese < this |

### Debug Logs Added

```java
// Starvation explore mode (every 5 rounds)
STARVATION_EXPLORE:540:13688:cheese=150:exploring_for_cheese

// Leash removal (every 10 rounds)
STARVATION_LEASH_REMOVED:545:cheese=120:exploring_far
```

### Results

| Version | Survival | Change |
|---------|----------|--------|
| Before patrol fix | 555 rounds (starvation) | Gatherers stuck patrolling |
| **After patrol fix** | **648 rounds (WIN!)** | **Gatherers find distant cheese** |

### Key Insight: getExplorationTarget() Limitation

The `getExplorationTarget()` function returns symmetry-based targets aimed at finding the **enemy king**, not optimal cheese locations. This is a known limitation but still better than patrolling to depleted areas.

**Future Improvement:** Track cheese mine locations and explore toward them during starvation.

---

## Session 5: Starvation Search Distribution Fix (January 2025)

### The Discovery: Rats Near King Not Spreading Out

**Match Analysis (DefaultMedium, Round 375 Death):**
```
Round 350-375: cheese=0, MASS_EMERGENCY triggered
Most rats: distKing=5 showing STARVATION_LEASH_REMOVED:exploring_far
Only 1 rat showing STARVATION_SEARCH with actual target coordinates
King dies with 33 rats clustered near depleted area
```

**The gatherers were logging "exploring_far" but NOT actually spreading out to search!**

### Root Cause: Distance Check Blocked Near-King Rats

```
┌─────────────────────────────────────────────────────────────┐
│           STARVATION SEARCH DISTRIBUTION BUG                 │
│                                                              │
│  OLD CODE (line 954):                                        │
│  if (emergencyRecall && hasOurKingLoc                        │
│      && cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {        │
│      if (criticallyLow) {                                    │
│          // STARVATION_SEARCH - spread out!                  │
│      }                                                       │
│  }                                                           │
│                                                              │
│  PROBLEM: Rats with distKing=5 (< 25) SKIP this block!      │
│                                                              │
│  Result: Near-king rats fall through to PATROL behavior     │
│          → Stay in depleted area                             │
│          → Never spread out to find distant cheese           │
└─────────────────────────────────────────────────────────────┘
```

**The BUG:** The `cachedDistToKingSq > HOME_GUARD_RADIUS_SQ` condition meant only rats FAR from the king (> 5 tiles) would trigger starvation search. Rats NEAR the king (most of them during starvation) skipped the entire block!

### The Fix: Remove Distance Check When Critically Starving

**Solution:** When cheese is critically low (< 200), ALL rats spread out regardless of distance:

```java
// OLD: Only far rats spread out
if (emergencyRecall && hasOurKingLoc && cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
    if (criticallyLow) { ... }  // Near-king rats never reach this!
}

// NEW: All rats spread out when critically starving
if (emergencyRecall && hasOurKingLoc) {
    if (criticallyLow) {
        // CRITICAL STARVATION: ALL rats spread out!
        // Remove distance check - even rats NEAR king must spread out
        cachedBestTarget = getCheeseHuntTarget(rc);
        return;
    } else if (cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
        // MODERATE EMERGENCY (200-500): Only FAR rats return home
        cachedBestTarget = cachedOurKingLoc;
        return;
    }
    // Near king with moderate cheese - fall through to normal behavior
}
```

### Cheese Hunt Target Distribution

The `getCheeseHuntTarget()` function spreads rats in 8 directions around the king:

```
              N (group 0)
              ↑
    NW (7)  ╲ │ ╱  NE (1)
             ╲│╱
    W (6) ←──KING──→ E (2)
             ╱│╲
    SW (5)  ╱ │ ╲  SE (3)
              ↓
              S (4)

Each rat is assigned a group based on: id % 8
Search radius: Math.max(12, mapWidth / 3)
```

**Key Features:**
- **8 search sectors** - Rats spread in all directions around king
- **Dynamic radius** - Larger maps get wider search radius
- **Sector rotation** - After 10 rounds or reaching target, rotate to next sector
- **Cache invalidation** - Reassign when rat reaches assigned sector

### Debug Log Improvement

The `STARVATION_SEARCH` log now includes `distKing=` to verify all rats are spreading:

```
// Before fix: Only far rats got search targets
STARVATION_SEARCH:310:10597:cheese=0:distKing=362:target=[20, 20]

// After fix: ALL rats get search targets (including near-king)
STARVATION_SEARCH:310:13398:cheese=0:distKing=5:target=[16, 35]  ← Near-king rat!
STARVATION_SEARCH:310:10597:cheese=0:distKing=362:target=[20, 35]
STARVATION_SEARCH:310:11207:cheese=0:distKing=25:target=[16, 44]
```

### Interaction with Other Systems

**Q: What about rats carrying cheese during starvation?**

A: They're handled BEFORE `scoreAllTargetsGatherer()` is called:

```java
// In runBabyRat(), line ~4510 (runs BEFORE scoreAllTargetsGatherer)
boolean cheeseCrisisDelivery = cachedCarryingCheese 
    && (emergencyRecallActive || ...);  // emergencyRecallActive = cheese < 500

if (cheeseCrisisDelivery && hasOurKingLoc) {
    bug2MoveToUrgent(rc, cachedOurKingLoc);  // Rush delivery
    return;  // Never reaches scoreAllTargetsGatherer!
}
```

**Q: What about home guards?**

A: They're also bypassed during critical starvation. The home guard check at lines 999-1011 runs AFTER the starvation search code, so home guards spread out when cheese < 200. This is correct behavior - a starving king needs cheese more than guards.

### Results

| Version | Survival | Change |
|---------|----------|--------|
| Before search distribution fix | 375 rounds (DefaultMedium) | Near-king rats stuck |
| **After search distribution fix** | **326+ rounds** | **All rats spread out** |

**Note:** The match was still lost due to insufficient cheese found in time, but the coordination mechanics are now working correctly - ALL rats receive unique search targets during starvation.

### New Bug Fixed

| Bug | Problem | Fix |
|-----|---------|-----|
| #13 | **Near-king rats skip starvation search** | Remove distance check when cheese < 200 |

---

All defensive systems have detailed debug logging when `Debug7.ENABLED = true`:

```java
// Starvation Prevention
MASS_EMERGENCY:380:10953:role=0:kingHP=30:converting_to_gatherer
CRISIS_DELIVER:320:13486:rushing_to_king:distKing=13:kingHP=160

// Patrol Death Spiral Fix (NEW)
STARVATION_EXPLORE:540:13688:cheese=150:exploring_for_cheese
STARVATION_LEASH_REMOVED:545:cheese=120:exploring_far

// Economy Anti-Collapse
SPAWN_RESERVE_BLOCK:320:cheese=0:spawnCost=40:reserve=300
SPAWN_CAP_HIT:320:spawns=36:cheese=0:cap=20
EMERGENCY_RECALL:300:12188:globalCheese=400:distKing=50
HOME_GUARD_RETURN:30:distKing=36

// Recovery Mode
RECOVERY_ENTER:36:reason=KING_PRESSURE:cheese=2110:threat=10:kingHP=390
RECOVERY_EXIT:180:reason=HP_RECOVERED:450
RECOVERY_EXIT:200:reason=TIMEOUT_FALLBACK:rounds=50:cheese=1200
RECOVERY_STATE:40:mode=true:cheese=2100:threat=4:spawns=12

// Critical King Radius (every 50 rounds)
CRITICAL_KING_RADIUS:150:kingHP=180:maxDist=100

// Spawn Control
SPAWN_PAUSE:190:kingHP=70

// Guardian
GUARDIAN_RETURN:26:11680:distKing=25:returning_to_king
GUARDIAN_INTERCEPT:26:11680:enemyDistKing=8:myDistKing=13:bypassing=true:attacked=true
GUARDIAN_HOLD:50:11680:distKing=16:in_position

// Emergency Defense
EMERGENCY:23:13304:trigger=FULL_EMERGENCY:level=4
CONVERGE:25:13304:distToKing=41:threat=8:target=[3,25]
BLOCK:30:10134:slot=2:inPosition=true:distToLine=5:enemies=2
```

---

## Bug Fixes Summary

| Bug | Problem | Fix |
|-----|---------|-----|
| #1 | Guardians abandoned posts during emergency | Exempted from CONVERGE |
| #2 | Economy death spiral | Spawn throttling, recovery mode |
| #3 | Recovery exit mismatch | Track trigger reason, HP-based exit |
| #4 | Spawn pause during king pressure | Removed from spawn pause condition |
| #5 | No timeout fallback | 50-round timeout for recovery |
| #6 | **CRISIS_DELIVER stuck at rat traps** | `bug2MoveToUrgent()` ignores traps |
| #7 | **Roles don't help during mass emergency** | ALL roles become gatherers |
| #8 | **ATK phase gatherers too far** | Raised threshold to 1500 |
| #9 | **Too many spawns deplete cheese** | Lowered cap from 25 to 20 |
| #10 | **Patrol death spiral** | Explore away from king when starving |
| #11 | **Hysteresis oscillation** | Different entry/exit thresholds (200/500) |
| #12 | **Shared static variable bug** | Pure threshold-based logic (no per-robot state) |
| #13 | **Near-king rats skip starvation search** | Remove distance check when cheese < 200 |

---

## Key Design Principles

1. **Starvation kills faster than combat** - Prioritize cheese delivery
2. **Track WHY something was triggered** - Different triggers need different exits
3. **Don't assume scarcity during abundance** - High cheese + pressure = spawn more
4. **Add timeout fallbacks** - Prevent infinite loops
5. **Override roles during mass emergency** - King survival trumps all
6. **Accept tactical damage for strategic survival** - 30 HP trap damage < king death
7. **Explore AWAY from depleted areas** - Patrol toward king creates death spiral
8. **Use hysteresis for state transitions** - Prevents oscillation at threshold boundaries

---

## Future Improvements

1. **Adaptive thresholds** - Adjust economy thresholds based on map size
2. **Formation healing** - Prioritize healing low-HP defenders in formation
3. **Trap coordination** - Guide enemies into trap zones more actively
4. **Counter-assassin detection** - Identify and prioritize enemies targeting king
5. **Smarter cheese pathing** - Pre-compute trap-free routes to king
6. ~~**Earlier starvation detection** - Predict cheese depletion before it happens~~ ✅ DONE
7. **Cheese mine tracking** - Explore toward known mine locations during starvation
8. **Adaptive hysteresis thresholds** - Adjust based on map size and spawn count
