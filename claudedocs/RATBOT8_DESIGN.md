# Ratbot8 Design Document: "Battle Intelligence"

## Executive Summary

Ratbot8 combines the best of all previous iterations into a **battle-smart** bot that:
- Survives any attack (proven ratbot7 defense)
- Attacks strategically when advantageous (learned timing)
- Never stops moving (continuous action)
- Easily tunable (profile-based constants)

**Core Philosophy:** "We can survive anything. Now we choose WHEN to attack."

---

## Lessons Learned from Each Bot

### ratbot5 (3500 lines)
**Strengths:**
- Complex kiting state machine (approach/attack/retreat)
- Squeak relay system for communication
- Assassin role for targeted king attacks
- Adaptive strategy detection (rush/turtle)
- Phase-based army composition

**Weaknesses:**
- Fragile - too many interdependent systems
- Bytecode heavy (~1500/turn)
- Kiting wastes tempo
- Squeak relay breaks when collectors die
- No defense against rush attacks

**Key Takeaway:** Strategic awareness is valuable, but complexity creates failure points.

### ratbot6 (750 lines)
**Strengths:**
- Unified value function (one algorithm for all decisions)
- Simple architecture, easy to understand
- Focus fire coordination
- Integer-only arithmetic (no floats)
- ~15 tunable constants

**Weaknesses:**
- Incomplete implementation
- No defensive systems
- King too mobile (rats can't deliver)
- No cat handling
- Missing interceptor logic

**Key Takeaway:** Value functions create emergent behavior and simplify tuning.

### ratbot7 (4800+ lines)
**Strengths:**
- Survives rush attacks (guardian positioning)
- Starvation prevention (mass emergency, urgent delivery)
- Economy protection (spawn caps, cheese reserves)
- Bytecode optimized (bitmasks, loop unrolling, inlining)
- Multi-layer defense (guardians, sentries, interceptors)
- Hysteresis logic prevents oscillation

**Weaknesses:**
- Pure defense - rarely attacks enemy king
- Too many roles (5 roles = complexity)
- Still loses some maps
- Large codebase

**Key Takeaway:** Defense is solvable. Now optimize offense timing.

---

## Ratbot8 Architecture

### Core Concept: Situational Value Function

Instead of static roles OR static values, use a **context-aware value function** where weights shift based on game state:

```
GAME STATES:
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   SURVIVE   │ ←→ │   PRESSURE  │ ←→ │   EXECUTE   │
└─────────────┘    └─────────────┘    └─────────────┘

SURVIVE: King threatened (HP<300, cheese<200, threat>6)
  → Defensive weights: King safety HIGH, attack LOW

PRESSURE: Normal operations
  → Balanced weights: configurable per profile

EXECUTE: Kill opportunity (enemy king<200, strong advantage)
  → Attack weights: Enemy king VERY HIGH
```

### State Machine with Hysteresis

> ⚠️ **CRITICAL FIX #1**: State transitions use different entry/exit thresholds to prevent oscillation.

```java
// Game states determine value function weights
private static final int STATE_SURVIVE = 0;
private static final int STATE_PRESSURE = 1;
private static final int STATE_EXECUTE = 2;

// HYSTERESIS THRESHOLDS - Different values for entering vs exiting states
// This prevents oscillation at boundary values (learned from ratbot7!)
private static final int SURVIVE_ENTER_HP = 300;      // Enter SURVIVE when kingHP < 300
private static final int SURVIVE_EXIT_HP = 400;       // Exit SURVIVE when kingHP > 400
private static final int SURVIVE_ENTER_CHEESE = 200;  // Enter SURVIVE when cheese < 200
private static final int SURVIVE_EXIT_CHEESE = 400;   // Exit SURVIVE when cheese > 400
private static final int SURVIVE_ENTER_THREAT = 6;    // Enter SURVIVE when threat > 6
private static final int SURVIVE_EXIT_THREAT = 3;     // Exit SURVIVE when threat < 3

private static final int EXECUTE_ENTER_ENEMY_HP = 200;  // Enter EXECUTE when enemy king < 200
private static final int EXECUTE_EXIT_ENEMY_HP = 250;   // Exit EXECUTE when enemy king > 250
private static final int EXECUTE_ENTER_ADVANTAGE = 50;  // Enter EXECUTE when advantage > 50%
private static final int EXECUTE_EXIT_ADVANTAGE = 20;   // Exit EXECUTE when advantage < 20%

private static int currentGameState = STATE_PRESSURE;

private static int determineGameState(RobotController rc) {
    int kingHP = cachedOurKingHP;
    int cheese = cachedGlobalCheese;
    int threat = cachedThreatLevel;
    int enemyKingHP = cachedEnemyKingHP;
    int advantage = cachedArmyAdvantage;
    
    // HYSTERESIS: Use different thresholds based on current state
    switch (currentGameState) {
        case STATE_SURVIVE:
            // Exit SURVIVE only when ALL conditions are safe (conservative)
            if (kingHP > SURVIVE_EXIT_HP 
                && cheese > SURVIVE_EXIT_CHEESE 
                && threat < SURVIVE_EXIT_THREAT) {
                // Check if we should go to EXECUTE or PRESSURE
                if (enemyKingHP < EXECUTE_ENTER_ENEMY_HP 
                    || (advantage > EXECUTE_ENTER_ADVANTAGE && cheese > 500)) {
                    return STATE_EXECUTE;
                }
                return STATE_PRESSURE;
            }
            return STATE_SURVIVE;  // Stay in SURVIVE
            
        case STATE_EXECUTE:
            // Drop to SURVIVE if we're in danger (highest priority)
            if (kingHP < SURVIVE_ENTER_HP 
                || cheese < SURVIVE_ENTER_CHEESE 
                || threat > SURVIVE_ENTER_THREAT) {
                return STATE_SURVIVE;
            }
            // Exit EXECUTE if advantage is lost
            if (enemyKingHP > EXECUTE_EXIT_ENEMY_HP 
                && advantage < EXECUTE_EXIT_ADVANTAGE) {
                return STATE_PRESSURE;
            }
            return STATE_EXECUTE;  // Stay in EXECUTE
            
        case STATE_PRESSURE:
        default:
            // Enter SURVIVE if threatened
            if (kingHP < SURVIVE_ENTER_HP 
                || cheese < SURVIVE_ENTER_CHEESE 
                || threat > SURVIVE_ENTER_THREAT) {
                return STATE_SURVIVE;
            }
            // Enter EXECUTE if opportunity
            if (enemyKingHP < EXECUTE_ENTER_ENEMY_HP 
                || (advantage > EXECUTE_ENTER_ADVANTAGE && cheese > 500)) {
                return STATE_EXECUTE;
            }
            return STATE_PRESSURE;  // Stay in PRESSURE
    }
}
```

### State Transition Diagram

```
                    ┌─────────────────────────────────────────┐
                    │                                         │
                    ▼                                         │
┌─────────────┐  danger  ┌─────────────┐  danger   ┌─────────────┐
│   SURVIVE   │ ◄─────── │   PRESSURE  │ ─────────►│   EXECUTE   │
│ (defensive) │          │ (balanced)  │ opportunity│ (offensive)│
└─────────────┘          └─────────────┘           └─────────────┘
       │                        ▲                         │
       │    all safe            │      advantage lost     │
       └────────────────────────┴─────────────────────────┘

Entry thresholds (aggressive): kingHP<300, cheese<200, threat>6
Exit thresholds (conservative): kingHP>400, cheese>400, threat<3
```

---

## Role System: Simplified to 3 Roles

| Role | % | Purpose | Key Behavior |
|------|---|---------|--------------|
| **CORE** | 10% | King protection | Never leaves king (ratbot7 guardians) |
| **FLEX** | 70% | Value-driven | Target scoring + value function |
| **SPECIALIST** | 20% | Mission focus | Scouts→Raiders→Assassins by phase |

### Role Assignment

```java
// Simple role assignment by ID modulo
int roleSelector = rc.getID() % 100;

if (roleSelector < 10) {
    return ROLE_CORE;           // 0-9: Guardian duty
} else if (roleSelector < 80) {
    return ROLE_FLEX;           // 10-79: Value function
} else {
    return ROLE_SPECIALIST;     // 80-99: Mission focus
}
```

---

## SPECIALIST Role Specification

> ⚠️ **CRITICAL FIX #3**: Fully specified SPECIALIST role with phase transitions, coordination, and fallback behavior.

### SPECIALIST Phase Transitions

SPECIALISTs evolve based on game round and confirmed intelligence:

| Round | Mode | Behavior | Transition Trigger |
|-------|------|----------|--------------------|
| 1-50 | **SCOUT** | Explore, find enemy king | Enemy king confirmed OR round > 50 |
| 50-200 | **RAIDER** | Harass, target collectors | Enemy king HP < 300 OR advantage > 40% |
| 200+ | **ASSASSIN** | Kill enemy king | Automatic after round 200 |

```java
// SPECIALIST mode constants
private static final int SPEC_SCOUT = 0;
private static final int SPEC_RAIDER = 1;
private static final int SPEC_ASSASSIN = 2;

private static int getSpecialistMode(RobotController rc) {
    int round = cachedRound;
    
    // Phase 3: ASSASSIN mode (endgame)
    if (round > 200 || cachedEnemyKingHP < 200) {
        return SPEC_ASSASSIN;
    }
    
    // Phase 2: RAIDER mode (mid-game harass)
    if (round > 50 || cachedEnemyKingConfirmed) {
        return SPEC_RAIDER;
    }
    
    // Phase 1: SCOUT mode (early game)
    return SPEC_SCOUT;
}
```

### SPECIALIST Behavior by Mode

```java
private static void runSpecialist(RobotController rc) {
    int mode = getSpecialistMode(rc);
    
    switch (mode) {
        case SPEC_SCOUT:
            // Explore toward enemy half of map
            // Broadcast enemy king position if found
            // Flee if outnumbered
            runScoutBehavior(rc);
            break;
            
        case SPEC_RAIDER:
            // Target enemy rats carrying cheese
            // Patrol enemy economy routes
            // Attack of opportunity, don't commit to fights
            runRaiderBehavior(rc);
            break;
            
        case SPEC_ASSASSIN:
            // Beeline to enemy king
            // Ignore other targets unless blocking
            // Accept trap damage to reach king
            runAssassinBehavior(rc);
            break;
    }
}

// SCOUT: Explore and gather intelligence
private static void runScoutBehavior(RobotController rc) {
    // Priority 1: Broadcast enemy king if visible
    if (canSeeEnemyKing(rc)) {
        broadcastEnemyKing(rc);
    }
    
    // Priority 2: Flee if outnumbered (scouts are fragile)
    if (cachedNearbyEnemyCount > 2) {
        bug2MoveToUrgent(rc, cachedOurKingLoc);
        return;
    }
    
    // Priority 3: Explore toward enemy half
    MapLocation exploreTarget = getScoutExplorationTarget(rc);
    bug2MoveTo(rc, exploreTarget);
}

// ===== HELPER FUNCTION STUBS =====

// Check if enemy king is visible in current sensing range
private static boolean canSeeEnemyKing(RobotController rc) {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    for (int i = enemies.length; --i >= 0; ) {
        if (enemies[i].getType() == UnitType.RAT_KING) return true;
    }
    return false;
}

// Broadcast enemy king position to shared array
private static void broadcastEnemyKing(RobotController rc) {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    for (int i = enemies.length; --i >= 0; ) {
        if (enemies[i].getType() == UnitType.RAT_KING) {
            MapLocation loc = enemies[i].getLocation();
            rc.writeSharedArray(SLOT_ENEMY_KING_X, loc.x);
            rc.writeSharedArray(SLOT_ENEMY_KING_Y, loc.y);
            rc.writeSharedArray(SLOT_ENEMY_KING_CONFIRMED, 1);
            return;
        }
    }
}

// Get exploration target for scouts (toward enemy half)
private static MapLocation getScoutExplorationTarget(RobotController rc) {
    // Scouts explore toward estimated enemy king location
    // Uses ID-based sector assignment to spread scouts out
    int sector = rc.getID() % 4;
    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();
    
    switch (sector) {
        case 0: return new MapLocation(mapWidth * 3 / 4, mapHeight * 3 / 4);
        case 1: return new MapLocation(mapWidth * 3 / 4, mapHeight / 4);
        case 2: return new MapLocation(mapWidth / 4, mapHeight * 3 / 4);
        default: return new MapLocation(mapWidth / 4, mapHeight / 4);
    }
}

// Get patrol point for raiders (enemy economy routes)
private static MapLocation getEnemyEconomyRoute(RobotController rc) {
    // Patrol between enemy king and map center (likely cheese sources)
    if (cachedEnemyKingLoc != null) {
        int midX = (cachedEnemyKingLoc.x + rc.getMapWidth() / 2) / 2;
        int midY = (cachedEnemyKingLoc.y + rc.getMapHeight() / 2) / 2;
        return new MapLocation(midX, midY);
    }
    return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
}

// Report that a zone is defended (for probing intelligence)
private static void reportDefendedZone(RobotController rc, int x, int y) {
    // Mark zone as having enemy defenders
    // Could use enemy sighting buffer (slots 40-49) to store this
    int packed = (x << 8) | y;
    int slot = SLOT_ENEMY_SIGHTING_START + (cachedRound % 10);
    rc.writeSharedArray(slot, packed);
}

// Report enemy defense level near their king
private static void reportEnemyDefenseLevel(RobotController rc) {
    // Count enemies near enemy king and broadcast
    // Used by attack planning to know when to assault
    int defenders = 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    for (int i = enemies.length; --i >= 0; ) {
        if (enemies[i].getType().isBabyRatType()) defenders++;
    }
    // Store in threat level slot (repurposed for enemy defense when scouting)
    // Note: King should not overwrite this if it's from a scout
}

// RAIDER: Harass enemy economy
private static void runRaiderBehavior(RobotController rc) {
    // Priority 1: Target enemy rats carrying cheese
    RobotInfo cheeseCarrier = findEnemyWithCheese(rc);
    if (cheeseCarrier != null) {
        bug2MoveTo(rc, cheeseCarrier.getLocation());
        return;
    }
    
    // Priority 2: Attack of opportunity (don't overcommit)
    if (cachedNearbyEnemyCount > 0 && cachedNearbyEnemyCount <= 2) {
        // Engage small groups
        bug2MoveTo(rc, cachedClosestEnemy.getLocation());
        return;
    }
    
    // Priority 3: Patrol between enemy king and cheese sources
    MapLocation patrolPoint = getEnemyEconomyRoute(rc);
    bug2MoveTo(rc, patrolPoint);
}

// ASSASSIN: Kill the enemy king
private static void runAssassinBehavior(RobotController rc) {
    // ONLY target: Enemy king (ignore everything else)
    if (cachedEnemyKingLoc != null) {
        // Use urgent movement - accept trap damage
        bug2MoveToUrgent(rc, cachedEnemyKingLoc);
    } else {
        // No confirmed position - go to symmetry estimate
        bug2MoveTo(rc, getEnemyKingEstimate(rc));
    }
}
```

### SPECIALIST Coordination

```java
// Shared array slots for SPECIALIST coordination
private static final int SLOT_SCOUT_COUNT = 10;      // Active scouts
private static final int SLOT_RAIDER_COUNT = 11;     // Active raiders
private static final int SLOT_ASSASSIN_COUNT = 12;   // Active assassins
private static final int SLOT_ENEMY_KING_CONFIRMED = 13;  // 1 if confirmed

// King updates specialist counts each round (for coordination)
private static void updateSpecialistCounts(RobotController rc) {
    // Counts are self-reported by specialists
    // Stale counts cleared each round by king
    rc.writeSharedArray(SLOT_SCOUT_COUNT, 0);
    rc.writeSharedArray(SLOT_RAIDER_COUNT, 0);
    rc.writeSharedArray(SLOT_ASSASSIN_COUNT, 0);
}

// Specialists report their mode each round
private static void reportSpecialistMode(RobotController rc, int mode) {
    int slot = SLOT_SCOUT_COUNT + mode;
    int current = rc.readSharedArray(slot);
    rc.writeSharedArray(slot, current + 1);
}
```

### SPECIALIST Fallback Behavior

```java
// Fallback: If all specialists die early, FLEX rats inherit specialist behavior
private static boolean shouldFlexActAsSpecialist(RobotController rc) {
    // Check if specialists exist
    int totalSpecs = rc.readSharedArray(SLOT_SCOUT_COUNT) 
                   + rc.readSharedArray(SLOT_RAIDER_COUNT)
                   + rc.readSharedArray(SLOT_ASSASSIN_COUNT);
    
    // If no specialists and we're in EXECUTE mode, some FLEX become assassins
    if (totalSpecs == 0 && currentGameState == STATE_EXECUTE) {
        // Top 20% of FLEX by ID become emergency assassins
        return (rc.getID() % 100) >= 60 && (rc.getID() % 100) < 80;
    }
    
    return false;
}
```

### FLEX→CORE Fallback Behavior

> **POLISH #1**: If all CORE guardians die, some FLEX rats become emergency guardians.

```java
// Shared array slot for CORE guardian count
private static final int SLOT_CORE_COUNT = 14;  // Active guardians

// FLEX rats check if they should become emergency guardians
private static boolean shouldFlexActAsCore(RobotController rc) {
    // Check if any CORE guardians exist
    int coreCount = rc.readSharedArray(SLOT_CORE_COUNT);
    
    // If no guardians and king is threatened, some FLEX become emergency guardians
    if (coreCount == 0 && currentGameState == STATE_SURVIVE) {
        // Lowest 10% of FLEX by ID become emergency guardians
        int roleSelector = rc.getID() % 100;
        return roleSelector >= 10 && roleSelector < 20;
    }
    
    // If only 1-2 guardians and under heavy attack, recruit more
    if (coreCount <= 2 && cachedThreatLevel > 4) {
        int roleSelector = rc.getID() % 100;
        return roleSelector >= 10 && roleSelector < 25;  // 15% become emergency guardians
    }
    
    return false;
}

// CORE guardians report their presence each round
private static void reportCorePresence(RobotController rc) {
    int current = rc.readSharedArray(SLOT_CORE_COUNT);
    rc.writeSharedArray(SLOT_CORE_COUNT, current + 1);
}

// King clears CORE count each round (before rats report)
private static void clearCoreCount(RobotController rc) {
    rc.writeSharedArray(SLOT_CORE_COUNT, 0);
}
```

### Emergency Guardian Behavior

```java
// In runFlexRat(), check for emergency guardian duty
private static void runFlexRat(RobotController rc) {
    // Check for emergency guardian duty FIRST
    if (shouldFlexActAsCore(rc)) {
        runCoreRat(rc);  // Act as guardian
        return;
    }
    
    // Check for emergency assassin duty
    if (shouldFlexActAsSpecialist(rc)) {
        runAssassinBehavior(rc);
        return;
    }
    
    // Normal FLEX behavior...
}
```

### FLEX Role: The Heart of ratbot8

FLEX rats use the value function to dynamically choose targets:

```java
void runFlexRat(RobotController rc) {
    // 1. Get current game state weights
    int[] weights = getStateWeights(currentGameState);
    
    // 2. Score all visible targets
    scoreAllTargets(rc, weights);
    
    // 3. Take immediate action if possible
    if (tryImmediateAction(rc)) return;
    
    // 4. Move toward best target
    if (cachedBestTarget != null) {
        bug2MoveTo(rc, cachedBestTarget);
    }
}
```

---

## Profile-Based Tuning System

> ⚠️ **CRITICAL FIX #5**: Clear mapping from 3 profile weights to 5 value function weights.

All tunable constants grouped into **profiles** for easy adjustment:

```java
// ===== TUNING PROFILES =====
// Change ONE line to shift entire playstyle

// PROFILE: Balanced (default)
private static final int ATTACK_WEIGHT = 100;   // Affects: enemyKing, enemyRat
private static final int DEFENSE_WEIGHT = 100;  // Affects: delivery, guardian radius
private static final int ECONOMY_WEIGHT = 100;  // Affects: cheese, explore

// PROFILE: Aggressive (uncomment to use)
// private static final int ATTACK_WEIGHT = 150;
// private static final int DEFENSE_WEIGHT = 70;
// private static final int ECONOMY_WEIGHT = 80;

// PROFILE: Defensive (uncomment to use)
// private static final int ATTACK_WEIGHT = 60;
// private static final int DEFENSE_WEIGHT = 150;
// private static final int ECONOMY_WEIGHT = 120;
```

### Profile → Value Weight Mapping

The 3 profile weights map to 5 value function weights as follows:

| Profile Weight | Value Weights Affected | Mapping Formula |
|----------------|------------------------|------------------|
| `ATTACK_WEIGHT` | `enemyKing`, `enemyRat` | Direct 1:1 scaling |
| `DEFENSE_WEIGHT` | `delivery` | Direct 1:1 scaling |
| `ECONOMY_WEIGHT` | `cheese`, `explore` | Direct 1:1 scaling |

```java
// Generate value weights from profile weights
private static int[] getProfileAdjustedWeights(int[] baseWeights) {
    // baseWeights = {attack, enemyRat, cheese, delivery, explore}
    return new int[] {
        baseWeights[0] * ATTACK_WEIGHT / 100,    // enemyKing
        baseWeights[1] * ATTACK_WEIGHT / 100,    // enemyRat
        baseWeights[2] * ECONOMY_WEIGHT / 100,   // cheese
        baseWeights[3] * DEFENSE_WEIGHT / 100,   // delivery
        baseWeights[4] * ECONOMY_WEIGHT / 100    // explore
    };
}

// Example: Aggressive profile with STATE_PRESSURE base weights
// Base:     {100, 100, 100, 100, 50}
// Profile:  ATTACK=150, DEFENSE=70, ECONOMY=80
// Result:   {150, 150, 80, 70, 40}
//            ↑↑ attack boosted, economy/defense reduced
```

### Profile Applications

```java
// Attack target scoring uses ATTACK_WEIGHT
int scoreEnemyKing(int distSq) {
    return (ENEMY_KING_BASE * ATTACK_WEIGHT / 100) * 1000 / (1000 + distSq * DIST_WEIGHT);
}

// Cheese scoring uses ECONOMY_WEIGHT
int scoreCheese(int distSq) {
    return (CHEESE_BASE * ECONOMY_WEIGHT / 100) * 1000 / (1000 + distSq * DIST_WEIGHT);
}

// Guardian radius uses DEFENSE_WEIGHT
int guardianOuterDist() {
    return GUARDIAN_BASE_OUTER * DEFENSE_WEIGHT / 100;
}

// Spawn reserve uses ECONOMY_WEIGHT (higher economy = more conservative)
int getSpawnReserve() {
    return ABSOLUTE_CHEESE_RESERVE * ECONOMY_WEIGHT / 100;
}
```

---

## Continuous Awareness System

Every turn, assess the battlefield:

```java
private static void updateAwareness(RobotController rc) {
    // 1. Threat level (enemies near king)
    cachedThreatLevel = countEnemiesNearKing(rc, 36);
    
    // 2. Economy trajectory
    int cheeseDelta = cachedGlobalCheese - lastRoundCheese;
    cachedEconomyTrend = (cheeseDelta > 0) ? TREND_GROWING : TREND_SHRINKING;
    
    // 3. Advantage score
    int ourStrength = cachedArmySize * cachedAverageHP;
    int theirStrength = cachedEnemyCount * 50;  // Estimate
    cachedArmyAdvantage = (ourStrength * 100) / Math.max(theirStrength, 1) - 100;
    
    // 4. Opportunity windows
    if (cachedEnemyKingHP < 200 && cachedArmyAdvantage > 0) {
        cachedOpportunityWindow = true;
    }
}
```

---

## Strategic Attack Timing

> ⚠️ **CRITICAL FIX #7**: Race condition calculation now factors in cheese reserves.

Attack in EXECUTE mode when:

```java
private static boolean shouldEnterExecuteMode() {
    // Condition 1: Enemy king is wounded
    if (cachedEnemyKingHP < 200) return true;
    
    // Condition 2: Strong advantage with safe economy
    if (cachedArmyAdvantage > 50 && cachedGlobalCheese > 500) return true;
    
    // Condition 3: Race condition (both kings low, we're ahead)
    // FIXED: Factor in cheese reserves, not just HP
    if (cachedOurKingHP < 200 || cachedEnemyKingHP < 200) {
        int ourSurvivalTime = calculateSurvivalTime(cachedOurKingHP, cachedGlobalCheese);
        int theirSurvivalTime = calculateEnemySurvivalTime(cachedEnemyKingHP);
        if (theirSurvivalTime < ourSurvivalTime) return true;  // We win the race
    }
    
    // Condition 4: Late game with any advantage
    if (cachedRound > 400 && cachedArmyAdvantage > 20) return true;
    
    return false;
}

/**
 * Calculate how many rounds our king can survive.
 * 
 * King loses 10 HP/round when unfed (cheese=0).
 * King consumes 3 cheese/round when fed.
 * 
 * @param kingHP Current king HP
 * @param cheese Current cheese reserves
 * @return Estimated rounds until king death
 */
private static int calculateSurvivalTime(int kingHP, int cheese) {
    // Phase 1: Rounds we can survive with cheese (fed)
    // Cheese consumed at 3/round, king gains no HP but doesn't lose HP
    int fedRounds = cheese / 3;
    
    // Phase 2: Rounds king survives after cheese runs out (starvation)
    // King loses 10 HP/round when unfed
    int starvationRounds = kingHP / 10;
    
    return fedRounds + starvationRounds;
}

/**
 * Estimate enemy survival time.
 * 
 * We don't know enemy cheese, so estimate based on:
 * - If enemy king is low HP, they're likely low on cheese too
 * - Conservative estimate: assume they have some cheese
 */
private static int calculateEnemySurvivalTime(int enemyKingHP) {
    // Conservative: Assume enemy has 100 cheese (~33 rounds fed)
    int estimatedEnemyCheese = 100;
    
    // If enemy king is very low, they're probably starving already
    if (enemyKingHP < 100) {
        estimatedEnemyCheese = 0;  // Likely already starving
    }
    
    return calculateSurvivalTime(enemyKingHP, estimatedEnemyCheese);
}
```

### Race Condition Examples

```
Scenario 1: We have cheese advantage
  Our king:   HP=150, cheese=300 → survivalTime = 300/3 + 150/10 = 100+15 = 115 rounds
  Enemy king: HP=200, cheese≈0  → survivalTime = 0/3 + 200/10 = 0+20 = 20 rounds
  Result: EXECUTE mode! Enemy dies in 20 rounds, we survive 115.

Scenario 2: Close race
  Our king:   HP=100, cheese=50 → survivalTime = 50/3 + 100/10 = 16+10 = 26 rounds
  Enemy king: HP=80, cheese≈100 → survivalTime = 100/3 + 80/10 = 33+8 = 41 rounds
  Result: Stay in PRESSURE. Enemy survives longer.

Scenario 3: Enemy starving
  Our king:   HP=200, cheese=400 → survivalTime = 400/3 + 200/10 = 133+20 = 153 rounds
  Enemy king: HP=50, cheese≈0   → survivalTime = 0/3 + 50/10 = 0+5 = 5 rounds
  Result: EXECUTE mode! All-in attack, enemy dies in 5 rounds.
```

---

## Strategic Attack Intelligence System

> **Core Philosophy:** "Every opponent action reveals information. We don't just react - we READ and COUNTER."

### The Key Insight

**If we survive a rush, the enemy has revealed their hand:**
- They committed resources that FAILED
- Their economy is STRESSED (spent cheese on attackers who died)
- Their defense is THIN (rats are with us, not home)
- Their collectors are EXPOSED (no protection)

**The counter isn't to attack their king immediately - it's to attack their ECONOMY first.** Collapse their economy, and the king starves or becomes undefended.

**If they aren't attacking:**
- They're building economy (we should too, or pressure them)
- They might be waiting to accumulate for a big push
- Their defense is likely strong (rats are home)
- We should PROBE to gather intelligence

---

### Opponent Behavior Classification

```java
// Opponent behavior types (detected from observable patterns)
private static final int OPPONENT_UNKNOWN = 0;    // Early game, insufficient data
private static final int OPPONENT_RUSHING = 1;    // Early aggression (threat > 4 before round 100)
private static final int OPPONENT_TURTLING = 2;   // Building economy (no threats for 50+ rounds)
private static final int OPPONENT_BALANCED = 3;   // Mixed strategy (moderate everything)
private static final int OPPONENT_DESPERATE = 4;  // All-in attack (low HP king + high threat)

private static int cachedOpponentBehavior = OPPONENT_UNKNOWN;
private static int roundsSinceEnemyNearKing = 0;
private static int lastEnemyNearKingRound = 0;
private static boolean survivedRushFlag = false;
private static int rushSurvivedRound = 0;

private static int classifyOpponentBehavior() {
    int round = cachedRound;
    int threat = cachedThreatLevel;
    int enemyKingHP = cachedEnemyKingHP;
    
    // Track rounds since enemy near our king
    if (threat > 0) {
        lastEnemyNearKingRound = round;
    }
    roundsSinceEnemyNearKing = round - lastEnemyNearKingRound;
    
    // RUSHING: High threat early (before round 100)
    if (round < 100 && threat > 4) {
        return OPPONENT_RUSHING;
    }
    
    // RUSHING detection: Significant threat in mid-game
    if (round < 200 && threat > 6) {
        return OPPONENT_RUSHING;
    }
    
    // TURTLING: No aggression for extended period
    if (round > 100 && roundsSinceEnemyNearKing > 50) {
        return OPPONENT_TURTLING;
    }
    
    // DESPERATE: Their king is dying, they're throwing everything at us
    if (enemyKingHP < 150 && threat > 6) {
        return OPPONENT_DESPERATE;
    }
    
    // BALANCED: Moderate threat levels, mixed strategy
    return OPPONENT_BALANCED;
}

// Detect when we've survived a rush
private static void detectRushSurvival() {
    int round = cachedRound;
    int kingHP = cachedOurKingHP;
    int threat = cachedThreatLevel;
    
    // Rush survival: We were (or recently were) threatened but now safe, and king survived
    // FIX: Check if we WERE rushing recently, not just current behavior
    boolean wasRecentlyRushed = (cachedOpponentBehavior == OPPONENT_RUSHING) || 
                                (lastEnemyNearKingRound > 0 && (round - lastEnemyNearKingRound) < 20);
    
    if (wasRecentlyRushed && threat < 3 && kingHP > 200) {
        if (!survivedRushFlag) {
            survivedRushFlag = true;
            rushSurvivedRound = round;
        }
    }
    
    // Reset rush flag after 100 rounds (rush is no longer recent)
    if (survivedRushFlag && (round - rushSurvivedRound) > 100) {
        survivedRushFlag = false;
    }
}

private static boolean survivedRushRecently() {
    return survivedRushFlag && (cachedRound - rushSurvivedRound) < 80;
}
```

---

### Counter-Strategy Matrix

| Opponent Behavior | What We Know | Counter-Strategy | Attack Target | Commitment Level |
|-------------------|--------------|------------------|---------------|------------------|
| **RUSHING** | Weak economy, thin defense | Survive → Economy Raid | Their collectors | RAID (40%) |
| **TURTLING** | Strong economy, strong defense | Probe & Pressure | Defense gaps | PROBE (20%) |
| **BALANCED** | Nothing certain | Focus Fire + Opportunism | Weakest enemy | ASSAULT (60%) |
| **DESPERATE** | All-in, nothing left to lose | Defensive Turtle | Protect our king | DEFEND (10%) |

```java
private static int getCounterStrategyCommitment() {
    switch (cachedOpponentBehavior) {
        case OPPONENT_RUSHING:
            // After surviving rush, counter-attack their economy
            if (survivedRushRecently()) return COMMITMENT_RAID;    // 40%
            return COMMITMENT_DEFEND;  // Still in rush, defend
            
        case OPPONENT_TURTLING:
            // Probe their defenses, don't overcommit
            return COMMITMENT_PROBE;   // 20%
            
        case OPPONENT_DESPERATE:
            // They're all-in, focus on defense
            return COMMITMENT_DEFEND;  // 10%
            
        case OPPONENT_BALANCED:
        default:
            // Opportunistic - escalate based on success
            return currentAttackCommitment;
    }
}
```

---

### Post-Rush Counter-Attack Sequence

When we detect we've survived a rush attack, execute this sequence:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    POST-RUSH COUNTER-ATTACK SEQUENCE                     │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  Phase 0: STABILIZE (rounds 0-10 after rush ends)                       │
│  ├─ Ensure king is safe (HP > 200, cheese > 300)                        │
│  ├─ Rebuild lost guardians (CORE role recruitment)                      │
│  └─ Count surviving army vs expected enemy army                         │
│                                                                          │
│  Phase 1: ECONOMY RAID (rounds 10-50 after rush)                        │
│  ├─ Send 40% of army as RAIDERS                                         │
│  ├─ Target: Enemy collectors carrying cheese                            │
│  ├─ Goal: Prevent their economic recovery                               │
│  └─ Continue our own gathering with remaining 60%                       │
│                                                                          │
│  Phase 2: ASSAULT PREPARATION (rounds 50-80 after rush)                 │
│  ├─ Build army advantage while raiding                                  │
│  ├─ Scout enemy king location and defenses                              │
│  └─ Wait for 40%+ army advantage                                        │
│                                                                          │
│  Phase 3: KING ASSAULT (when conditions met)                            │
│  ├─ Transition to EXECUTE state                                         │
│  ├─ All-in attack on enemy king                                         │
│  └─ They can't defend AND recover economy simultaneously                │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

```java
// Post-rush counter-attack state machine
private static final int POST_RUSH_NONE = 0;
private static final int POST_RUSH_STABILIZE = 1;
private static final int POST_RUSH_ECONOMY_RAID = 2;
private static final int POST_RUSH_ASSAULT_PREP = 3;
private static final int POST_RUSH_KING_ASSAULT = 4;

private static int postRushPhase = POST_RUSH_NONE;

private static int getPostRushPhase() {
    if (!survivedRushRecently()) return POST_RUSH_NONE;
    
    int roundsSinceSurvival = cachedRound - rushSurvivedRound;
    
    // Phase 0: Stabilize (first 10 rounds)
    if (roundsSinceSurvival < 10) {
        if (cachedOurKingHP < 200 || cachedGlobalCheese < 300) {
            return POST_RUSH_STABILIZE;
        }
    }
    
    // Phase 3: King assault (when conditions met)
    if (cachedArmyAdvantage > 40 || cachedEnemyKingHP < 200) {
        return POST_RUSH_KING_ASSAULT;
    }
    
    // Phase 2: Assault prep (50-80 rounds after)
    if (roundsSinceSurvival > 50) {
        return POST_RUSH_ASSAULT_PREP;
    }
    
    // Phase 1: Economy raid (default 10-50 rounds)
    return POST_RUSH_ECONOMY_RAID;
}
```

---

### Graduated Attack Commitment System

**Don't go all-in immediately. Escalate based on success:**

```
         10%              20%              40%              60%              100%
┌────────────────┬────────────────┬────────────────┬────────────────┬────────────────┐
│    DEFEND      │     PROBE      │     RAID       │    ASSAULT     │    ALL-IN      │
│   (protect)    │   (scout)      │   (harass)     │   (attack)     │   (kill)       │
└────────────────┴────────────────┴────────────────┴────────────────┴────────────────┘
       ↑                ↑                ↑                ↑                ↑
   Default         Found enemy      Killed 2+        Army adv        King HP
   (turtle)          king          collectors         >40%            <200

            ────────► Escalate when successful ────────►
            ◄──────── De-escalate when threatened ◄────────
```

```java
// Attack commitment levels
private static final int COMMITMENT_DEFEND = 0;   // 10% - Minimal offense
private static final int COMMITMENT_PROBE = 1;    // 20% - Scouts only
private static final int COMMITMENT_RAID = 2;     // 40% - Economy harassment
private static final int COMMITMENT_ASSAULT = 3;  // 60% - Serious attack
private static final int COMMITMENT_ALL_IN = 4;   // 100% - Kill the king

private static int currentAttackCommitment = COMMITMENT_PROBE;
private static int killsThisGame = 0;

private static int getAttackPercentage(int commitment) {
    switch (commitment) {
        case COMMITMENT_DEFEND: return 10;
        case COMMITMENT_PROBE: return 20;
        case COMMITMENT_RAID: return 40;
        case COMMITMENT_ASSAULT: return 60;
        case COMMITMENT_ALL_IN: return 100;
        default: return 20;
    }
}

private static void updateAttackCommitment() {
    int current = currentAttackCommitment;
    
    // ESCALATE conditions
    if (cachedEnemyKingHP < 200 && cachedOurKingHP > 200) {
        currentAttackCommitment = COMMITMENT_ALL_IN;
        return;
    }
    
    if (cachedArmyAdvantage > 40 && current < COMMITMENT_ASSAULT) {
        currentAttackCommitment = COMMITMENT_ASSAULT;
        return;
    }
    
    if (killsThisGame >= 2 && current < COMMITMENT_RAID) {
        currentAttackCommitment = COMMITMENT_RAID;
        return;
    }
    
    if (cachedEnemyKingConfirmed && current < COMMITMENT_PROBE) {
        currentAttackCommitment = COMMITMENT_PROBE;
    }
    
    // DE-ESCALATE conditions (threat takes priority)
    if (cachedThreatLevel > 5 || cachedGlobalCheese < 200) {
        currentAttackCommitment = COMMITMENT_DEFEND;
    }
}
```

---

### Probing Defense (When Enemy Is Turtling)

If the enemy isn't attacking, we need to gather intelligence:

```java
// Probing behavior for PROBE commitment level
private static void runProbeBehavior(RobotController rc) {
    // Goal: Find enemy king and assess defenses without dying
    
    // Priority 1: If outnumbered, retreat and report
    if (cachedNearbyEnemyCount > 1) {
        // Mark this zone as defended
        reportDefendedZone(rc, myLocX, myLocY);
        bug2MoveToUrgent(rc, cachedOurKingLoc);
        return;
    }
    
    // Priority 2: If found enemy king, broadcast and stay alive
    if (canSeeEnemyKing(rc)) {
        broadcastEnemyKing(rc);
        reportEnemyDefenseLevel(rc);
        // Don't attack alone - just report
        return;
    }
    
    // Priority 3: Explore toward enemy half
    MapLocation probeTarget = getProbeTarget(rc);
    bug2MoveTo(rc, probeTarget);
}

// Probe targets: Rotate through quadrants to find enemy
private static MapLocation getProbeTarget(RobotController rc) {
    int id = rc.getID();
    int quadrant = (id + (cachedRound / 20)) % 4;  // Rotate quadrant every 20 rounds
    
    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();
    int halfW = mapWidth / 2;
    int halfH = mapHeight / 2;
    
    // Probe enemy half of map
    switch (quadrant) {
        case 0: return new MapLocation(halfW + halfW/2, halfH + halfH/2);  // NE
        case 1: return new MapLocation(halfW + halfW/2, halfH/2);           // SE
        case 2: return new MapLocation(halfW/2, halfH + halfH/2);           // NW
        case 3: return new MapLocation(halfW/2, halfH/2);                   // SW
        default: return getEnemyKingEstimate(rc);
    }
}
```

---

### Attack Target Priority by Situation

Different situations require different targeting:

| Situation | Priority 1 | Priority 2 | Priority 3 | Avoid |
|-----------|------------|------------|------------|-------|
| **ECONOMY RAID** | Cheese carriers (+500) | Near-king delivery (+300) | Low HP rats (+200) | Enemy king (waste of time) |
| **KING ASSAULT** | Enemy king (+1000) | Blocking defenders (+400) | Focus target (+500) | Collectors (irrelevant) |
| **PROBING** | None (just observe) | Isolated enemies (+100) | - | Groups (flee instead) |
| **DEFENSE** | Threats near our king (+800) | Cheese carriers (+300) | - | Far enemies (waste) |

```java
/**
 * Get attack target score bonus based on current commitment level.
 * Called during target scoring to adjust priorities.
 */
private static int getCommitmentTargetBonus(RobotInfo enemy, int commitment) {
    boolean isKing = enemy.getType() == UnitType.RAT_KING;
    boolean carryingCheese = enemy.getRawCheese() > 0;
    boolean nearOurKing = enemy.getLocation().distanceSquaredTo(cachedOurKingLoc) <= 36;
    boolean nearEnemyKing = cachedEnemyKingLoc != null && 
                            enemy.getLocation().distanceSquaredTo(cachedEnemyKingLoc) <= 36;
    int hp = enemy.getHealth();
    
    int bonus = 0;
    
    switch (commitment) {
        case COMMITMENT_RAID:
            // Economy raid priorities
            if (carryingCheese) bonus += 500;      // Deny their economy
            if (nearEnemyKing) bonus += 300;        // About to deliver
            if (hp < 30) bonus += 200;              // Easy kill
            if (isKing) bonus -= 200;               // Don't waste time on king yet
            break;
            
        case COMMITMENT_ASSAULT:
        case COMMITMENT_ALL_IN:
            // King assault priorities
            if (isKing) bonus += 1000;              // PRIMARY TARGET
            if (nearOurKing && !isKing) bonus += 400;  // Blocking our path
            // Focus fire bonus handled elsewhere
            break;
            
        case COMMITMENT_DEFEND:
            // Defense priorities
            if (nearOurKing) bonus += 800;          // Immediate threat
            if (carryingCheese) bonus += 300;       // Deny their economy
            break;
            
        case COMMITMENT_PROBE:
            // Probing - avoid fights, just observe
            bonus -= 100;  // Slight penalty to attacking
            break;
    }
    
    return bonus;
}
```

---

### Attack Window Detection

```java
/**
 * Detect optimal attack windows - moments when attacking is advantageous.
 * Returns true if we should escalate attack commitment.
 */
private static boolean hasAttackWindow() {
    int round = cachedRound;
    int ourHP = cachedOurKingHP;
    int enemyHP = cachedEnemyKingHP;
    int cheese = cachedGlobalCheese;
    int advantage = cachedArmyAdvantage;
    int threat = cachedThreatLevel;
    
    // Can't attack if we're threatened
    if (threat > 4 || cheese < 300 || ourHP < 250) {
        return false;
    }
    
    // Window 1: Post-rush counter (just survived, they're weak)
    if (survivedRushRecently() && threat < 3) {
        return true;  // Their attack failed, counter now!
    }
    
    // Window 2: Economy dominance (we have 2x their estimated cheese)
    // If we have 800+ cheese and they likely have <400, we're ahead
    if (cheese > 800 && round > 100) {
        return true;  // Economic advantage
    }
    
    // Window 3: HP advantage (their king wounded, ours healthy)
    if (enemyHP < 200 && ourHP > 350) {
        return true;  // Finish them!
    }
    
    // Window 4: Army advantage (60%+ more strength)
    if (advantage > 60) {
        return true;  // Overwhelming force
    }
    
    // Window 5: Late game forcing (round > 400, any advantage)
    if (round > 400 && advantage > 20) {
        return true;  // Force the issue in late game
    }
    
    // Window 6: Turtling enemy (they haven't attacked in 100 rounds)
    if (cachedOpponentBehavior == OPPONENT_TURTLING && advantage > 0) {
        return true;  // Don't let them build forever
    }
    
    return false;
}

/**
 * Get the type of attack window (for logging/decision making).
 */
private static int getAttackWindowType() {
    if (survivedRushRecently()) return WINDOW_POST_RUSH;
    if (cachedGlobalCheese > 800) return WINDOW_ECONOMY;
    if (cachedEnemyKingHP < 200) return WINDOW_WOUNDED_KING;
    if (cachedArmyAdvantage > 60) return WINDOW_ARMY;
    if (cachedRound > 400) return WINDOW_LATE_GAME;
    if (cachedOpponentBehavior == OPPONENT_TURTLING) return WINDOW_TURTLE_PUNISH;
    return WINDOW_NONE;
}
```

---

### Intelligence Gathering Requirements

```java
// What we need to know to make smart attack decisions:

// 1. Enemy king location & HP (confirmed vs estimated)
private static MapLocation cachedEnemyKingLoc;        // Last known position
private static int cachedEnemyKingHP;                  // Estimated HP (500 - damage dealt)
private static boolean cachedEnemyKingConfirmed;       // Actually seen vs symmetry guess
private static int enemyKingLastSeenRound;             // Staleness of position

// 2. Enemy collector positions (for economy raiding)
private static int enemyCollectorsSpotted;             // Count of enemies carrying cheese
private static MapLocation lastEnemyCollectorLoc;      // Where we saw a collector

// 3. Enemy defense density (for assault planning)
private static int enemyDefendersNearKing;             // Enemies within 6 tiles of their king
private static int estimatedEnemyDefenseStrength;      // 0-10 scale

// 4. Time since last enemy seen in each zone (staleness map)
private static int[] zoneLastScoutedRound = new int[4]; // Quadrant staleness

// 5. Attack window type constants
private static final int WINDOW_NONE = 0;
private static final int WINDOW_POST_RUSH = 1;
private static final int WINDOW_ECONOMY = 2;
private static final int WINDOW_WOUNDED_KING = 3;
private static final int WINDOW_ARMY = 4;
private static final int WINDOW_LATE_GAME = 5;
private static final int WINDOW_TURTLE_PUNISH = 6;

/**
 * Update intelligence each round.
 */
private static void updateIntelligence(RobotController rc, RobotInfo[] enemies) {
    int round = cachedRound;
    
    // RESET counters at start of each round
    enemyCollectorsSpotted = 0;
    enemyDefendersNearKing = 0;  // FIX: Reset before counting
    
    // Track enemy collectors
    for (int i = enemies.length; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        if (enemy.getRawCheese() > 0) {
            enemyCollectorsSpotted++;
            lastEnemyCollectorLoc = enemy.getLocation();
        }
        
        // Track enemies near enemy king
        if (cachedEnemyKingLoc != null) {
            int distToEnemyKing = enemy.getLocation().distanceSquaredTo(cachedEnemyKingLoc);
            if (distToEnemyKing <= 36) {
                enemyDefendersNearKing++;
            }
        }
    }
    
    // Update zone staleness (which quadrants have we scouted?)
    int quadrant = getQuadrant(myLocX, myLocY, rc.getMapWidth(), rc.getMapHeight());
    zoneLastScoutedRound[quadrant] = round;
    
    // Estimate defense strength (0-10)
    if (cachedEnemyKingConfirmed) {
        estimatedEnemyDefenseStrength = Math.min(10, enemyDefendersNearKing * 2);
    }
}

private static int getQuadrant(int x, int y, int mapWidth, int mapHeight) {
    int halfW = mapWidth / 2;
    int halfH = mapHeight / 2;
    if (x >= halfW) {
        return (y >= halfH) ? 0 : 1;  // NE or SE
    } else {
        return (y >= halfH) ? 2 : 3;  // NW or SW
    }
}
```

---

### Integration with Game State Machine

The Strategic Attack Intelligence System integrates with the existing state machine:

```java
private static int determineGameState(RobotController rc) {
    // First, classify opponent behavior
    cachedOpponentBehavior = classifyOpponentBehavior();
    
    // Detect rush survival
    detectRushSurvival();
    
    // Update attack commitment based on conditions
    if (hasAttackWindow()) {
        updateAttackCommitment();  // May escalate
    }
    
    // Get counter-strategy commitment
    int counterCommitment = getCounterStrategyCommitment();
    
    // Merge counter-strategy with current commitment (take higher)
    currentAttackCommitment = Math.max(currentAttackCommitment, counterCommitment);
    
    // Original state machine logic (with attack window influence)
    // ... (existing hysteresis logic)
    
    // If we have an attack window and are safe, lean toward EXECUTE
    if (hasAttackWindow() && cachedThreatLevel < 3 && currentGameState == STATE_PRESSURE) {
        // Check if we should transition to EXECUTE
        if (currentAttackCommitment >= COMMITMENT_ASSAULT) {
            return STATE_EXECUTE;
        }
    }
    
    return currentGameState;  // Default to existing state
}
```

---

### Strategic Attack Design Principles

```
┌─────────────────────────────────────────────────────────────────────────────┐
│              6 PRINCIPLES OF STRATEGIC ATTACK                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. ATTACK ECONOMY FIRST, KING SECOND                                      │
│     A starving king is an easy kill. Destroy their economy, then finish.    │
│                                                                              │
│  2. ESCALATE, DON'T ALL-IN                                                  │
│     PROBE → RAID → ASSAULT → ALL-IN. Each step gathers more intel.         │
│                                                                              │
│  3. EVERY OPPONENT ACTION IS INTELLIGENCE                                   │
│     Rush reveals weak defense. Turtle reveals strong economy.               │
│     No attack reveals they're building. Use this information!               │
│                                                                              │
│  4. COUNTER-ATTACK WHEN THEY'RE WEAK, NOT WHEN WE'RE STRONG                │
│     Post-rush counter > Pre-emptive strike. Timing beats numbers.           │
│                                                                              │
│  5. PROBING IS FREE                                                         │
│     20% scouts cost little but reveal much. Always know where they are.     │
│                                                                              │
│  6. TIMING > NUMBERS                                                        │
│     10 rats post-rush beats 15 rats during rush.                            │
│     Attack when THEY'RE vulnerable, not when we're ready.                   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Attack Intelligence Summary Table

| Situation | What We Know | What To Do | Commitment |
|-----------|--------------|------------|------------|
| **Survived rush** | Enemy weak, economy stressed | Counter-attack economy | RAID (40%) |
| **No attack for 50+ rounds** | Enemy turtling, strong defense | Probe for weaknesses | PROBE (20%) |
| **Enemy king spotted, low HP** | Kill opportunity | All-in assault | ALL-IN (100%) |
| **We have 2x economy** | Economic dominance | Pressure with numbers | ASSAULT (60%) |
| **Late game (round 400+)** | Time running out | Force engagement | ASSAULT (60%) |
| **Under heavy attack** | Survival priority | Defend first | DEFEND (10%) |
| **Unknown/early game** | Insufficient data | Scout and gather | PROBE (20%) |

---

## Never Stop Moving Guarantee

Every rat has guaranteed action:

```java
void runRat(RobotController rc) {
    // Priority 1: High-value target
    if (cachedBestTarget != null && cachedBestScore > MIN_SCORE_THRESHOLD) {
        bug2MoveTo(rc, cachedBestTarget);
        return;
    }
    
    // Priority 2: Explore unexplored area
    MapLocation explorationTarget = getExplorationTarget(rc);
    if (explorationTarget != null) {
        bug2MoveTo(rc, explorationTarget);
        return;
    }
    
    // Priority 3: Turn to scan (vision cone management)
    if (rc.isMovementReady()) {
        Direction scanDir = getBestScanDirection(rc);
        if (scanDir != Direction.CENTER) {
            rc.turn(scanDir);
            return;
        }
    }
    
    // Fallback: Move toward map center
    bug2MoveTo(rc, cachedMapCenter);
}
```

---

## Integrated Defense Systems (from ratbot7)

### Proven Systems to Keep

```java
// 1. Guardian tight positioning
private static final int GUARDIAN_INNER_DIST_SQ = 5;
private static final int GUARDIAN_OUTER_DIST_SQ = 13;

// 2. Urgent pathfinding through traps
private static void bug2MoveToUrgent(RobotController rc, MapLocation target) {
    // Ignores rat traps when king survival is critical
}

// 3. Mass emergency override
boolean massEmergency = cachedGlobalCheese == 0 && cachedKingHP < 200;
if (massEmergency) {
    // ALL rats become gatherers, regardless of role
}

// 4. Starvation prevention with hysteresis
private static final int STARVATION_THRESHOLD = 200;
private static final int STARVATION_EXIT = 500;

// 5. Economy protection layers
private static final int ABSOLUTE_CHEESE_RESERVE = 300;
private static final int SPAWN_CAP_MAX = 20;
```

---

## King Movement Behavior

> **POLISH #2**: Explicit king movement specification for ratbot8.

### King Movement Philosophy

Ratbot8 king uses a **"safe zone"** approach learned from ratbot6 and ratbot7:
- **Mobile enough** to avoid cached position exploits (ratbot5 assassins)
- **Stationary enough** for rats to deliver cheese reliably

### King Movement Constants

```java
// King safe zone - stay within this radius of spawn point
private static final int KING_SAFE_ZONE_RADIUS_SQ = 36;  // 6 tiles from spawn

// King movement cooldown is 40 (not 10 like baby rats!)
// King can only move every ~4 rounds
private static final int KING_MOVEMENT_COOLDOWN = 40;

// Pause movement to allow cheese delivery
private static final int KING_DELIVERY_PAUSE_ROUNDS = 3;  // Pause 3 rounds after delivery
```

### King Movement State Machine

```java
private static MapLocation kingSpawnPoint = null;  // Remember where king started
private static int lastDeliveryRound = 0;           // Track when rats delivered

private static void runKingMovement(RobotController rc, RobotInfo[] enemies) {
    if (!rc.isMovementReady()) return;
    
    MapLocation me = rc.getLocation();
    
    // Initialize spawn point on first run
    if (kingSpawnPoint == null) {
        kingSpawnPoint = me;
    }
    
    // Priority 1: FLEE from nearby enemies
    if (enemies.length > 0) {
        // Calculate center of enemy mass
        int sumX = 0, sumY = 0;
        for (int i = enemies.length; --i >= 0; ) {
            sumX += enemies[i].getLocation().x;
            sumY += enemies[i].getLocation().y;
        }
        MapLocation enemyCenter = new MapLocation(sumX / enemies.length, sumY / enemies.length);
        
        // Move away from enemies
        Direction awayDir = enemyCenter.directionTo(me);
        tryKingMove(rc, awayDir);
        return;
    }
    
    // Priority 2: PAUSE for cheese delivery
    int roundsSinceDelivery = cachedRound - lastDeliveryRound;
    if (roundsSinceDelivery <= KING_DELIVERY_PAUSE_ROUNDS) {
        return;  // Stay still to let rats deliver
    }
    
    // Priority 3: RETURN to safe zone if too far
    int distFromSpawn = me.distanceSquaredTo(kingSpawnPoint);
    if (distFromSpawn > KING_SAFE_ZONE_RADIUS_SQ) {
        Direction toSpawn = me.directionTo(kingSpawnPoint);
        tryKingMove(rc, toSpawn);
        return;
    }
    
    // Priority 4: RANDOM walk within safe zone (invalidates cached position)
    // Only move occasionally to conserve action for traps/spawns
    if (cachedRound % 5 == 0) {
        Direction randomDir = DIRECTIONS[cachedRound % 8];
        MapLocation newLoc = me.add(randomDir);
        
        // Only move if still in safe zone
        if (newLoc.distanceSquaredTo(kingSpawnPoint) <= KING_SAFE_ZONE_RADIUS_SQ) {
            tryKingMove(rc, randomDir);
        }
    }
}

// Safe king movement (avoid traps)
private static boolean tryKingMove(RobotController rc, Direction dir) {
    if (dir == null || dir == Direction.CENTER) return false;
    
    MapLocation target = rc.getLocation().add(dir);
    
    // Check for traps before moving
    if (rc.canSenseLocation(target)) {
        MapInfo info = rc.senseMapInfo(target);
        if (info.getTrap() == TrapType.RAT_TRAP) {
            return false;  // Don't walk into traps!
        }
    }
    
    if (rc.canMove(dir)) {
        try { rc.move(dir); return true; } catch (Exception e) {}
    }
    
    // Try rotating if direct path blocked
    Direction left = dir.rotateLeft();
    Direction right = dir.rotateRight();
    
    if (rc.canMove(left)) {
        try { rc.move(left); return true; } catch (Exception e) {}
    }
    if (rc.canMove(right)) {
        try { rc.move(right); return true; } catch (Exception e) {}
    }
    
    return false;
}

// Called when a rat delivers cheese (update delivery timestamp)
private static void onCheeseDelivered() {
    lastDeliveryRound = cachedRound;
}
```

### King Movement Decision Tree

```
┌─────────────────────────────────────────────────────────────┐
│              KING MOVEMENT DECISION                          │
│                                                              │
│  1. Movement ready?                                          │
│     └─ No: Skip movement this turn                          │
│                                                              │
│  2. Enemies nearby?                                          │
│     └─ Yes: FLEE away from enemy center                     │
│                                                              │
│  3. Recent cheese delivery? (last 3 rounds)                  │
│     └─ Yes: PAUSE to let more rats deliver                  │
│                                                              │
│  4. Too far from spawn? (> 6 tiles)                         │
│     └─ Yes: RETURN toward spawn point                       │
│                                                              │
│  5. Every 5th round:                                         │
│     └─ RANDOM walk within safe zone (invalidate cache)      │
│                                                              │
│  Default: Stay still (conserve action for traps/spawns)     │
└─────────────────────────────────────────────────────────────┘
```

---

## Cat Handling System

> ⚠️ **CRITICAL FIX #2**: Explicit cat evasion - cats can one-shot rats!

### Cat Threat Assessment

```java
// Cat constants
private static final int CAT_DANGER_RADIUS_SQ = 36;   // 6 tiles - cat pounce range + buffer
private static final int CAT_IMMEDIATE_THREAT_SQ = 16; // 4 tiles - RUN NOW
private static final int CAT_DAMAGE = 50;              // One-shot kill potential

// Cat handling runs BEFORE any other logic (highest priority)
private static RobotInfo findDangerousCat(RobotController rc) {
    RobotInfo[] neutrals = rc.senseNearbyRobots(-1, Team.NEUTRAL);
    
    for (int i = neutrals.length; --i >= 0; ) {
        RobotInfo robot = neutrals[i];
        if (robot.getType() == UnitType.CAT) {
            return robot;  // Found a cat!
        }
    }
    return null;
}
```

### Cat Evasion Behavior

```java
/**
 * Flee from cat - HIGHEST PRIORITY in all rat behavior.
 * Called at the START of every turn before any other logic.
 */
private static boolean handleCatThreat(RobotController rc) {
    RobotInfo cat = findDangerousCat(rc);
    if (cat == null) return false;  // No cat, continue normal behavior
    
    MapLocation me = rc.getLocation();
    MapLocation catLoc = cat.getLocation();
    int distToCat = me.distanceSquaredTo(catLoc);
    
    // If cat is too close, DROP EVERYTHING and flee
    if (distToCat <= CAT_DANGER_RADIUS_SQ) {
        // Priority 1: Place trap between us and cat (slow it down)
        if (distToCat <= CAT_IMMEDIATE_THREAT_SQ && rc.isActionReady()) {
            Direction toCat = me.directionTo(catLoc);
            MapLocation trapLoc = me.add(toCat);
            if (rc.canPlaceTrap(trapLoc, TrapType.RAT_TRAP)) {
                rc.placeTrap(trapLoc, TrapType.RAT_TRAP);
            }
        }
        
        // Priority 2: Flee in opposite direction
        Direction awayFromCat = catLoc.directionTo(me);
        if (awayFromCat == Direction.CENTER) {
            awayFromCat = DIRECTIONS[cachedRound & 7];  // Random if on top of cat
        }
        
        // Try to move away (use urgent movement - ignore traps)
        if (tryMoveAway(rc, awayFromCat)) {
            return true;  // Successfully fled
        }
        
        // Can't move - try turning away at least
        if (rc.isMovementReady()) {
            try { rc.turn(awayFromCat); } catch (Exception e) {}
        }
        
        return true;  // Cat threat handled (even if we couldn't move)
    }
    
    return false;  // Cat exists but not close enough to flee
}

private static boolean tryMoveAway(RobotController rc, Direction awayDir) {
    // Try away direction first
    if (rc.canMove(awayDir)) {
        try { rc.move(awayDir); return true; } catch (Exception e) {}
    }
    
    // Try adjacent directions
    Direction left = awayDir.rotateLeft();
    Direction right = awayDir.rotateRight();
    
    if (rc.canMove(left)) {
        try { rc.move(left); return true; } catch (Exception e) {}
    }
    if (rc.canMove(right)) {
        try { rc.move(right); return true; } catch (Exception e) {}
    }
    
    // Try 90 degree turns
    Direction leftLeft = left.rotateLeft();
    Direction rightRight = right.rotateRight();
    
    if (rc.canMove(leftLeft)) {
        try { rc.move(leftLeft); return true; } catch (Exception e) {}
    }
    if (rc.canMove(rightRight)) {
        try { rc.move(rightRight); return true; } catch (Exception e) {}
    }
    
    return false;  // Couldn't move at all
}
```

### Cat Handling Integration

```java
// In runBabyRat(), cat handling is FIRST
private static void runBabyRat(RobotController rc) {
    // PRIORITY 0: Cat evasion (before ANYTHING else)
    if (handleCatThreat(rc)) {
        return;  // Cat threat handled, end turn
    }
    
    // ... rest of rat logic
}
```

---

## Value Function (ratbot6 inspired)

### Core Scoring Formula

```java
/**
 * Score a target using integer arithmetic.
 * Higher score = higher priority.
 *
 * Formula: score = (baseValue * stateWeight / 100) * 1000 / (1000 + distSq * DIST_WEIGHT)
 */
private static int scoreTarget(int targetType, int distSq, int[] weights) {
    int baseValue = getBaseValue(targetType);
    int weight = getWeight(targetType, weights);
    
    // Apply weight and distance penalty
    int weighted = baseValue * weight / 100;
    return weighted * 1000 / (1000 + distSq * DISTANCE_WEIGHT);
}

private static int getBaseValue(int targetType) {
    switch (targetType) {
        case TARGET_ENEMY_KING: return ENEMY_KING_BASE;
        case TARGET_ENEMY_RAT: return ENEMY_RAT_BASE;
        case TARGET_CHEESE: return getCheeseValue();
        case TARGET_DELIVERY: return DELIVERY_BASE;
        default: return 0;
    }
}

private static int getCheeseValue() {
    // Dynamic based on economy
    if (cachedGlobalCheese < CRITICAL_CHEESE) return CHEESE_CRITICAL_VALUE;
    if (cachedGlobalCheese < LOW_CHEESE) return CHEESE_LOW_VALUE;
    return CHEESE_NORMAL_VALUE;
}
```

### State-Based Weights

```java
private static final int[][] STATE_WEIGHTS = {
    // [STATE_SURVIVE]  : Prioritize defense and economy
    {50, 30, 150, 200, 0},   // attack, enemyRat, cheese, delivery, explore
    
    // [STATE_PRESSURE] : Balanced
    {100, 100, 100, 100, 50},
    
    // [STATE_EXECUTE]  : All-out attack
    {250, 80, 50, 80, 30}
};

private static int[] getStateWeights(int state) {
    return STATE_WEIGHTS[state];
}
```

---

## Focus Fire Integration

> ⚠️ **CRITICAL FIX #6**: Focus fire target gets bonus in value function scoring.

### Focus Fire Constants

```java
// Shared array slot for focus fire coordination
private static final int SLOT_FOCUS_TARGET = 6;    // Robot ID of priority target (mod 1024)
private static final int SLOT_FOCUS_HP = 7;        // HP of focus target
private static final int SLOT_FOCUS_ROUND = 8;     // Round when target was set

// Focus fire scoring bonus
private static final int FOCUS_FIRE_BONUS = 500;   // Massive bonus for focus target
private static final int FOCUS_STALE_ROUNDS = 3;   // Target expires after 3 rounds
```

### Focus Fire in Value Function

```java
/**
 * Score an enemy rat target, with focus fire bonus.
 * 
 * The focus fire target gets a massive scoring bonus so all rats
 * converge on the same enemy, killing them faster.
 */
private static int scoreEnemyRat(RobotController rc, RobotInfo enemy, int distSq, int[] weights) {
    int baseScore = scoreTarget(TARGET_ENEMY_RAT, distSq, weights);
    
    // Check if this is the focus fire target
    int focusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    int focusRound = rc.readSharedArray(SLOT_FOCUS_ROUND);
    
    // Focus target is valid if:
    // 1. ID matches (mod 1024 to fit in shared array)
    // 2. Target was set recently (not stale)
    if (focusTargetId > 0 
        && (enemy.getID() % 1024) == focusTargetId
        && (cachedRound - focusRound) <= FOCUS_STALE_ROUNDS) {
        
        // MASSIVE bonus for focus target
        baseScore += FOCUS_FIRE_BONUS * 1000 / (1000 + distSq * DISTANCE_WEIGHT);
    }
    
    // Additional bonus for low HP enemies (finish them off)
    int enemyHP = enemy.getHealth();
    if (enemyHP <= 20) {
        baseScore += 100;  // Nearly dead = high priority
    } else if (enemyHP <= 40) {
        baseScore += 50;   // Wounded = medium priority
    }
    
    return baseScore;
}
```

### Focus Fire Target Selection (King)

```java
/**
 * King selects the focus fire target for all rats.
 * Called each round by the king to update the target.
 */
private static void updateFocusFireTarget(RobotController rc, RobotInfo[] enemies) {
    if (enemies.length == 0) {
        // No enemies - clear target
        rc.writeSharedArray(SLOT_FOCUS_TARGET, 0);
        return;
    }
    
    // Find best focus target:
    // Priority 1: Enemy KING (always highest priority)
    // Priority 2: Lowest HP enemy (easiest to kill)
    // Priority 3: Enemy carrying cheese (economy denial)
    
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    
    for (int i = enemies.length; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        int score = 0;
        
        // Enemy king is ALWAYS priority
        if (enemy.getType() == UnitType.RAT_KING) {
            score += 10000;
        }
        
        // Lower HP = higher priority (invert HP for scoring)
        score += (100 - enemy.getHealth());
        
        // Carrying cheese = bonus priority
        if (enemy.getRawCheese() > 0) {
            score += 50;
        }
        
        if (score > bestScore) {
            bestScore = score;
            bestTarget = enemy;
        }
    }
    
    if (bestTarget != null) {
        rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.getID() % 1024);
        rc.writeSharedArray(SLOT_FOCUS_HP, Math.min(bestTarget.getHealth(), 1023));
        rc.writeSharedArray(SLOT_FOCUS_ROUND, cachedRound % 1024);
    }
}
```

### Focus Fire Debug Logging

```java
// In attack logic
if (DEBUG && isFocusTarget) {
    System.out.println("FOCUS_FIRE:" + cachedRound + ":" + rc.getID() 
        + ":target=" + enemy.getID() + ":hp=" + enemy.getHealth());
}
```

---

## Bytecode Optimizations (Lessons Learned)

> 📚 **Reference:** See `claudedocs/RATBOT7_BYTECODE_MASTERCLASS.md` for the complete optimization guide.

### Bytecode Cost Reference (Quick Reference)

| Operation | Cost | Notes |
|-----------|------|-------|
| Local variable access | 1 BC | **Cheapest - use locals!** |
| Static field access | 2-3 BC | Cache in locals at turn start |
| Array access `arr[i]` | 3-6 BC | Index calc adds overhead |
| Method call overhead | 5-15 BC | Inline hot methods |
| `new Object()` | 10-20 BC | **Avoid in hot paths!** |
| `MapLocation.add(dir)` | 8-12 BC | Use int arithmetic instead |
| `rc.senseNearbyRobots()` | 100+ BC | Call once, cache result |
| For loop iteration | 3-5 BC | Unroll fixed-size loops |

### 🥇 Technique #1: Loop Unrolling

**Problem:** Loop overhead = 5 BC × 8 iterations = 40 BC wasted

```java
// BAD: Loop overhead
for (int i = 8; --i >= 0; ) {
    if (rc.canMove(DIRECTIONS[i])) return DIRECTIONS[i];
}

// GOOD: Unrolled (0 BC overhead)
if (rc.canMove(Direction.NORTH)) return Direction.NORTH;
if (rc.canMove(Direction.NORTHEAST)) return Direction.NORTHEAST;
if (rc.canMove(Direction.EAST)) return Direction.EAST;
// ... etc
```

**Savings:** 40 BC per loop × 10+ loops = **400-1200 BC/turn**

### 🥈 Technique #2: MapLocation Avoidance

**Problem:** `new MapLocation(x, y)` costs 15-20 BC

```java
// BAD: Creates MapLocation objects
MapLocation target = myLoc.add(dir);           // ~12 BC
int dist = myLoc.distanceSquaredTo(target);    // ~8 BC
// Total: ~20 BC

// GOOD: Pure integer arithmetic
int targetX = myLocX + DIR_DX[dirOrd];         // ~3 BC
int targetY = myLocY + DIR_DY[dirOrd];         // ~3 BC
int dx = myLocX - targetX;
int dy = myLocY - targetY;
int dist = dx * dx + dy * dy;                  // ~4 BC
// Total: ~10 BC (50% savings)
```

**Implementation:**
```java
// Pre-computed direction deltas
private static final int[] DIR_DX = {0, 1, 1, 1, 0, -1, -1, -1};
private static final int[] DIR_DY = {1, 1, 0, -1, -1, -1, 0, 1};
```

**Savings:** 10 BC × 50+ operations = **200-400 BC/turn**

### 🥉 Technique #3: Method Inlining

**Problem:** Method call overhead = 10-20 BC per call

```java
// BAD: Method call 80 times = 1200 BC overhead
private static boolean canMoveSafely(Direction dir) {
    return rc.canMove(dir) && !ADJACENT_RAT_TRAP[dir.ordinal()];
}

// GOOD: Inline with bitmask
// In Bug2 hot path:
if (rc.canMove(Direction.NORTH) && (adjacentTrapMask & 1) == 0) { ... }
```

**Savings:** 15 BC × 80 calls = **400-800 BC/turn**

### 🏅 Technique #4: Bit-Packed Flags

**Problem:** 8 boolean array accesses = 48 BC

```java
// BAD: Array of booleans
boolean[] blocked = new boolean[8];
if (blocked[0] && blocked[1]) { ... }  // 12 BC

// GOOD: Single int bitmask
int blockedMask = 0;
blockedMask |= (1 << 0);  // Set NORTH blocked
if ((blockedMask & 0b00000011) == 0b00000011) { ... }  // 3 BC
```

**Implementation:**
```java
// Trap detection bitmask
private static int adjacentTrapMask = 0;

// Cache traps at turn start
private static void cacheAdjacentTraps(RobotController rc) {
    adjacentTrapMask = 0;
    MapInfo[] nearby = rc.senseNearbyMapInfos(2);
    for (int i = nearby.length; --i >= 0; ) {
        MapInfo info = nearby[i];
        if (info.getTrap() == TrapType.RAT_TRAP) {
            int dx = info.getMapLocation().x - myLocX + 1;
            int dy = info.getMapLocation().y - myLocY + 1;
            int ordinal = DX_DY_TO_DIR_ORDINAL[dy][dx];
            if (ordinal >= 0) adjacentTrapMask |= (1 << ordinal);
        }
    }
}
```

**Savings:** 45 BC × 10+ checks = **100-200 BC/turn**

### 🎯 Technique #5: Local Variable Caching

**Problem:** Static field access = 2-3 BC vs local = 1 BC

```java
// BAD: Multiple static field accesses
if (cachedRound > 100 && cachedGlobalCheese < 500) { ... }  // 6 BC

// GOOD: Cache in locals at turn start
final int round = cachedRound;      // Copy once
final int cheese = cachedGlobalCheese;
if (round > 100 && cheese < 500) { ... }  // 2 BC
```

**Implementation:**
```java
private static void runBabyRat(RobotController rc) {
    // Cache ALL frequently-used values at turn start
    final int round = cachedRound;
    final int myX = myLocX;
    final int myY = myLocY;
    final int cheese = cachedGlobalCheese;
    final int kingHP = cachedOurKingHP;
    
    // Use locals for rest of turn
}
```

**Savings:** 1-2 BC × 100+ accesses = **50-100 BC/turn**

### 🔬 Technique #6: Debug Code Elimination

**Problem:** Debug checks cost ~50 BC even when disabled

```java
// BAD: Runtime check
if (Debug7.ENABLED) {
    System.out.println("Debug");  // ~50 BC even when false!
}

// GOOD: Compile-time constant
private static final boolean DEBUG = false;  // Compiler eliminates dead code
if (DEBUG) {
    System.out.println("Debug");  // 0 BC when false!
}
```

### 🏆 Technique #7: Lookup Tables

```java
// Lookup table for dx/dy to direction ordinal
private static final int[][] DX_DY_TO_DIR_ORDINAL = {
    {7, 0, 1},  // dy=-1: SW, N, NE
    {6, -1, 2}, // dy=0:  W, CENTER, E
    {5, 4, 3}   // dy=1:  NW, S, SE
};

// Usage: O(1) lookup instead of loop
int ordinal = DX_DY_TO_DIR_ORDINAL[dy + 1][dx + 1];
```

### Bytecode Optimization Summary

| Technique | Potential Savings | Priority |
|-----------|-------------------|----------|
| Loop Unrolling | 400-1200 BC/turn | 🥇 HIGH |
| Method Inlining | 400-800 BC/turn | 🥇 HIGH |
| MapLocation Avoidance | 200-400 BC/turn | 🥈 MEDIUM |
| Bit-Packed Flags | 100-200 BC/turn | 🥉 MEDIUM |
| Local Variable Caching | 50-100 BC/turn | 🎯 LOW |
| Debug Elimination | Variable | 🔬 LOW |

**Total Potential Savings: 1150-2700 BC/turn (40-50% reduction)**

### ratbot8 Bytecode Targets

| Unit | Before Optimization | After Optimization | Budget | % Used |
|------|---------------------|-------------------|--------|--------|
| Baby Rat | 2000-2900 BC | **1200-1600 BC** | 17,500 | **7-9%** |
| King | 3500-6600 BC | **2500-4000 BC** | 20,000 | **13-20%** |

This leaves **80-90% of bytecode budget** available for:
- More sophisticated AI
- Better pathfinding
- Advanced coordination
- Real-time adaptation

---

## File Structure

> ⚠️ **CRITICAL FIX #4**: Revised line count to realistic 1800-2000 lines.

```java
// ================================================================
// SECTION 1: PROFILES & TUNABLE CONSTANTS (~100 lines)
// ================================================================
// All tunable values in one place for easy adjustment
// Includes hysteresis thresholds, profile weights, constants

// ================================================================
// SECTION 2: GAME STATE & CACHING (~120 lines)
// ================================================================
// Static fields for cross-turn state
// Includes all cached values and state variables

// ================================================================
// SECTION 3: AWARENESS & STATE MACHINE (~120 lines)
// ================================================================
// determineGameState() with hysteresis
// updateAwareness(), state transitions

// ================================================================
// SECTION 4: VALUE FUNCTION & TARGET SCORING (~180 lines)
// ================================================================
// scoreTarget(), scoreAllTargets(), getStateWeights()
// Profile→weight mapping, focus fire integration

// ================================================================
// SECTION 5: MOVEMENT & PATHFINDING (~220 lines)
// ================================================================
// bug2MoveTo(), bug2MoveToUrgent(), bytecode-optimized
// Includes trap avoidance, wall following

// ================================================================
// SECTION 6: COMBAT & ACTIONS (~120 lines)
// ================================================================
// tryAttack(), tryCollect(), tryDeliver()
// Focus fire target selection

// ================================================================
// SECTION 7: DEFENSE SYSTEMS (~250 lines)
// ================================================================
// runCoreRat(), mass emergency, starvation prevention
// Cat handling system (new)

// ================================================================
// SECTION 8: SPECIALIST ROLE (~200 lines)
// ================================================================
// runSpecialist(), scout/raider/assassin behaviors
// Specialist coordination and fallback

// ================================================================
// SECTION 9: KING LOGIC (~180 lines)
// ================================================================
// runKing(), spawning, trap placement
// Focus fire target updates, economy mode

// ================================================================
// SECTION 10: STRATEGIC ATTACK INTELLIGENCE (~150 lines)
// ================================================================
// Opponent behavior classification
// Attack commitment levels (PROBE/RAID/ASSAULT/ALL-IN)
// Attack window detection
// Counter-strategy logic

// ================================================================
// SECTION 11: COMMUNICATION (~100 lines)
// ================================================================
// Shared array management
// Specialist counts, focus fire, enemy king position

// ================================================================
// SECTION 12: MAIN LOOP & UTILITIES (~80 lines)
// ================================================================
// run(), runBabyRat(), role dispatch
// Debug logging, helper functions

// ================================================================
// SECTION 13: BYTECODE OPTIMIZATIONS (~130 lines)
// ================================================================
// Pre-computed tables, bitmasks
// Lookup tables, unrolled loops

// TOTAL: ~1950 lines (realistic estimate)
// Buffer: ~200 lines for edge cases and debug
// Final estimate: 1950-2200 lines
```

---

## Target Metrics

| Metric | ratbot5 | ratbot6 | ratbot7 | **ratbot8** |
|--------|---------|---------|---------|-------------|
| **Lines** | 3500 | 750 | 4800+ | **1950-2200** |
| **Bytecode** | ~1500 | ~1000 | ~2000 | **~1500** |
| **Offensive** | ★★★★★ | ★★★★☆ | ★★☆☆☆ | **★★★★☆** |
| **Defensive** | ★★☆☆☆ | ★★☆☆☆ | ★★★★★ | **★★★★★** |
| **Tunable** | ★★☆☆☆ | ★★★★☆ | ★★★☆☆ | **★★★★★** |
| **Readable** | ★★☆☆☆ | ★★★★☆ | ★★★☆☆ | **★★★★☆** |

---

## How ratbot8 Wins

### vs ratbot5
- Simpler code = fewer bugs
- Survives assassin rush (proven defense)
- Attacks strategically, not constantly

### vs ratbot6
- Complete implementation (ratbot6 was incomplete)
- Proven defense systems
- Better economy management

### vs ratbot7
- Adds strategic offense while maintaining defense
- Simpler architecture (3 roles vs 5)
- Profile-based tuning

---

## Implementation Phases

### Phase 1: Foundation (MVP)
- [ ] Basic game state machine (SURVIVE/PRESSURE/EXECUTE)
- [ ] 3 role system (CORE/FLEX/SPECIALIST)
- [ ] Value function from ratbot6
- [ ] Bug2 pathfinding from ratbot7
- **Test:** Beat examplefuncsplayer

### Phase 2: Defense Integration
- [ ] Port ratbot7 guardian system (CORE role)
- [ ] Port starvation prevention
- [ ] Port economy protection
- [ ] Mass emergency override
- **Test:** Survive ratbot5 rush

### Phase 3: Strategic Offense
- [ ] Attack timing logic (shouldEnterExecuteMode)
- [ ] SPECIALIST role behavior (scout→raider→assassin)
- [ ] Opportunity window detection
- [ ] Focus fire coordination
- **Test:** Beat ratbot7 consistently

### Phase 4: Optimization & Tuning
- [ ] Apply bytecode optimizations
- [ ] Profile benchmarking
- [ ] Map-specific adjustments
- [ ] Profile variants (aggressive/defensive)
- **Test:** Win all maps vs all previous bots

---

## Success Criteria

- [ ] Beats ratbot5 on all maps (both teams)
- [ ] Beats ratbot6 on all maps (both teams)
- [ ] Beats ratbot7 on all maps (both teams)
- [ ] Code under 2000 lines
- [ ] Bytecode under 1500/turn average
- [ ] Clear profile-based tuning
- [ ] No critical starvation deaths
- [ ] No cat-related deaths (cat evasion working)
- [ ] No state oscillation (hysteresis working)

---

## Key Insights

### Strategic Insights
1. **Defense is solved** - ratbot7 proved we can survive any rush
2. **Attack timing matters more than attack capability** - Strategic offense > constant offense
3. **Value functions create emergent behavior** - Simpler than role assignment
4. **Profiles enable rapid experimentation** - One variable = playstyle shift
5. **3 roles is enough** - CORE (defend), FLEX (value), SPECIALIST (mission)
6. **Hysteresis prevents oscillation** - Different entry/exit thresholds are essential
7. **Cat handling is non-negotiable** - One-shot deaths must be prevented
8. **Focus fire wins fights** - Concentrated damage > distributed damage
9. **Race conditions need accurate calculations** - Factor in cheese, not just HP

### Strategic Attack Insights
10. **Attack economy first, king second** - Destroy their collectors, then the king starves
11. **Escalate, don't all-in** - PROBE → RAID → ASSAULT → ALL-IN based on success
12. **Every opponent action is intelligence** - Rush reveals weak defense, turtle reveals strong economy
13. **Counter-attack when THEY'RE weak** - Post-rush timing beats pre-emptive strikes
14. **Probing is free** - 20% scouts cost little but reveal enemy positions
15. **Classify opponent behavior** - RUSHING/TURTLING/BALANCED/DESPERATE need different counters

### Bytecode Efficiency Insights
16. **Loop unrolling is the #1 optimization** - 40 BC saved per 8-iteration loop
17. **Avoid MapLocation allocation** - Use int x,y coordinates instead (50% savings)
18. **Inline hot methods** - 15 BC overhead per call adds up fast (80 calls = 1200 BC)
19. **Bitmasks beat boolean arrays** - 8 booleans = 48 BC, 1 bitmask = 3 BC
20. **Cache statics in locals** - Local access = 1 BC, static = 2-3 BC
21. **Use compile-time constants for debug** - `if (DEBUG)` where `DEBUG = false` = 0 BC
22. **Lookup tables beat computation** - Pre-compute dx/dy→direction mappings

---

## Shared Array Layout

> **POLISH #3**: Complete shared array slot documentation to prevent conflicts.

### Shared Array Slot Allocation

Battlecode provides **64 shared array slots** (indices 0-63), each storing a 16-bit value (0-65535).

```
┌────────────────────────────────────────────────────────────────────┐
│                    SHARED ARRAY LAYOUT                              │
├──────┬─────────────────────────────────────────────────────────────┤
│ SLOT │ PURPOSE                                                      │
├──────┼─────────────────────────────────────────────────────────────┤
│  0   │ Our king X coordinate (0-60)                                │
│  1   │ Our king Y coordinate (0-60)                                │
│  2   │ Enemy king X coordinate (0-60, 0=unknown)                   │
│  3   │ Enemy king Y coordinate (0-60)                              │
│  4   │ Enemy king HP (0-500, clamped to fit)                       │
│  5   │ Economy mode (0=normal, 1=low, 2=critical)                  │
├──────┼─────────────────────────────────────────────────────────────┤
│  6   │ Focus fire target ID (mod 1024)                             │
│  7   │ Focus fire target HP (0-1023)                               │
│  8   │ Focus fire round set (mod 1024)                             │
│  9   │ Game state (0=SURVIVE, 1=PRESSURE, 2=EXECUTE)               │
├──────┼─────────────────────────────────────────────────────────────┤
│ 10   │ Scout count (active scouts this round)                      │
│ 11   │ Raider count (active raiders this round)                    │
│ 12   │ Assassin count (active assassins this round)                │
│ 13   │ Enemy king confirmed (1=yes, 0=no)                          │
│ 14   │ CORE guardian count (active guardians this round)           │
├──────┼─────────────────────────────────────────────────────────────┤
│ 15   │ Threat level (enemies near king, 0-20+)                     │
│ 16   │ Army advantage (percentage, 0-200 where 100=equal)          │
│ 17   │ Total rat count (our baby rats alive)                       │
│ 18   │ Spawn count this game (cumulative)                          │
│ 19   │ Last delivery round (for king pause logic)                  │
├──────┼─────────────────────────────────────────────────────────────┤
│ 20   │ Rush survived round (0=none, >0=round survived)             │
│ 21   │ Current attack commitment (0-4: DEFEND/PROBE/RAID/...)      │
│ 22   │ Kills this game (cumulative enemy rats killed)              │
│ 23   │ Opponent behavior (0-4: UNKNOWN/RUSHING/TURTLING/...)       │
├──────┼─────────────────────────────────────────────────────────────┤
│24-29 │ RESERVED for future use                                     │
├──────┼─────────────────────────────────────────────────────────────┤
│30-39 │ Cheese mine locations (packed x,y coordinates)              │
├──────┼─────────────────────────────────────────────────────────────┤
│40-49 │ Enemy sighting buffer (ring buffer of enemy positions)      │
├──────┼─────────────────────────────────────────────────────────────┤
│50-59 │ Debug/profiling (optional, compile-time disabled)           │
├──────┼─────────────────────────────────────────────────────────────┤
│60-63 │ RESERVED for emergency communication                        │
└──────┴─────────────────────────────────────────────────────────────┘
```

### Slot Constants

```java
// ===== SHARED ARRAY SLOT CONSTANTS =====

// Standard 8-direction array (used throughout)
private static final Direction[] DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
};

// King position (slots 0-1)
private static final int SLOT_OUR_KING_X = 0;
private static final int SLOT_OUR_KING_Y = 1;

// Enemy king position (slots 2-4)
private static final int SLOT_ENEMY_KING_X = 2;
private static final int SLOT_ENEMY_KING_Y = 3;
private static final int SLOT_ENEMY_KING_HP = 4;

// Economy (slot 5)
private static final int SLOT_ECONOMY_MODE = 5;

// Focus fire (slots 6-8)
private static final int SLOT_FOCUS_TARGET = 6;
private static final int SLOT_FOCUS_HP = 7;
private static final int SLOT_FOCUS_ROUND = 8;

// Game state (slot 9)
private static final int SLOT_GAME_STATE = 9;

// Role counts (slots 10-14)
private static final int SLOT_SCOUT_COUNT = 10;
private static final int SLOT_RAIDER_COUNT = 11;
private static final int SLOT_ASSASSIN_COUNT = 12;
private static final int SLOT_ENEMY_KING_CONFIRMED = 13;
private static final int SLOT_CORE_COUNT = 14;

// Army status (slots 15-19)
private static final int SLOT_THREAT_LEVEL = 15;
private static final int SLOT_ARMY_ADVANTAGE = 16;
private static final int SLOT_RAT_COUNT = 17;
private static final int SLOT_SPAWN_COUNT = 18;
private static final int SLOT_LAST_DELIVERY = 19;

// Attack intelligence (slots 20-23)
private static final int SLOT_RUSH_SURVIVED_ROUND = 20;   // Round when rush was survived
private static final int SLOT_ATTACK_COMMITMENT = 21;     // Current attack commitment level
private static final int SLOT_KILLS_THIS_GAME = 22;       // Cumulative enemy kills
private static final int SLOT_OPPONENT_BEHAVIOR = 23;     // Detected opponent behavior

// Cheese mines (slots 30-39)
private static final int SLOT_MINE_START = 30;
private static final int SLOT_MINE_END = 39;

// Enemy sightings (slots 40-49)
private static final int SLOT_ENEMY_SIGHTING_START = 40;
private static final int SLOT_ENEMY_SIGHTING_END = 49;
```

### Slot Usage by Unit

| Unit | Reads | Writes |
|------|-------|--------|
| **King** | 0-19 | 0-19 (all coordination slots) |
| **CORE** | 0-9, 14-15 | 14 (guardian count) |
| **FLEX** | 0-9, 14-17 | 17 (rat count, optional) |
| **SPECIALIST** | 0-13 | 10-12 (mode counts) |

### Slot Freshness

Some slots need to be cleared each round to track "active" counts:

```java
// King clears count slots at start of each round
private static void clearRoundCounts(RobotController rc) {
    rc.writeSharedArray(SLOT_SCOUT_COUNT, 0);
    rc.writeSharedArray(SLOT_RAIDER_COUNT, 0);
    rc.writeSharedArray(SLOT_ASSASSIN_COUNT, 0);
    rc.writeSharedArray(SLOT_CORE_COUNT, 0);
}
```

---

## Code Review Issues Addressed

| # | Issue | Fix Applied |
|---|-------|-------------|
| 1 | State transition hysteresis missing | Added `SURVIVE_ENTER_*` and `SURVIVE_EXIT_*` thresholds |
| 2 | Cat handling not specified | Added complete "Cat Handling System" section |
| 3 | SPECIALIST role underspecified | Added "SPECIALIST Role Specification" with phases, behaviors, coordination |
| 4 | Unrealistic line count | Revised to 1800-2000 lines with detailed breakdown |
| 5 | Profile→value weight mapping unclear | Added mapping table and `getProfileAdjustedWeights()` function |
| 6 | Focus fire integration missing | Added "Focus Fire Integration" section with scoring bonus |
| 7 | Race condition calculation bug | Added `calculateSurvivalTime()` factoring in cheese reserves |

### Polish Items Added

| # | Polish Item | Section Added |
|---|-------------|---------------|
| P1 | FLEX→CORE fallback if guardians die | "FLEX→CORE Fallback Behavior" in Role System |
| P2 | King movement behavior | "King Movement Behavior" section |
| P3 | Full shared array layout | "Shared Array Layout" section |

---

*Document created: January 2025*
*Updated: January 2025 - Addressed code review issues*
*Updated: January 2025 - Added polish items (FLEX→CORE fallback, king movement, shared array layout)*
*Updated: January 2025 - Added Strategic Attack Intelligence System (opponent classification, counter-strategies, attack windows)*
*Based on learnings from ratbot5, ratbot6, and ratbot7*
