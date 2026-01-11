# Ratbot6 Design Document
## Value Function Architecture (v2 - Revised)

**Philosophy:** Intelligence in the algorithm, not in roles.

---

## Executive Summary

Ratbot6 uses a **single unified value function** to drive all rat behavior. Instead of assigning roles (assassin, collector, interceptor), every rat evaluates all visible targets, scores them, and pursues the highest-value option.

**Key Insight:** The "role" emerges from the game state, not from assignment.
- Low cheese? → Value function naturally prioritizes collection
- Enemy king wounded? → Value function naturally prioritizes attack
- Carrying cheese near our king? → Delivery priority spikes

**Target:** ~600-800 lines (vs ratbot5's 3500 lines)

---

## Critical Design Constraints

> ⚠️ **These constraints MUST be respected in implementation**

| Constraint | Value | Impact |
|------------|-------|--------|
| Baby rat vision | **90° cone**, √20 radius | Can only see forward! Must turn to scan |
| King movement cooldown | **40** (not 10) | King can only move every 4+ rounds |
| King size | **3x3** | King blocks large area, affects delivery |
| Cheese delivery range | **√9 = 3 tiles** | Rats must get close to king |
| King cheese consumption | **3/round** | 100 cheese = 33 rounds survival |
| Strafe cooldown | **18** vs forward **10** | 1.8x slower to move sideways |

---

## Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Simplicity** | One behavior loop, one value function |
| **Emergent Roles** | Priorities shift based on game state |
| **Easy Tuning** | ~15 meaningful constants |
| **Counter ratbot5** | Mobile king, no-kite combat, focus fire |
| **Vision-aware** | Turn to scan, optimize facing direction |

---

## Core Architecture

### Main Loop (Every Rat, Every Turn)

```java
// Static fields to avoid allocation (reused every turn)
private static int cachedOurCheese;
private static int cachedRound;
private static boolean cachedCarryingCheese;
private static MapLocation cachedOurKingLoc;
private static MapLocation cachedEnemyKingLoc;
private static int cachedEnemyKingHP;
private static int cachedDistToOurKing;
private static MapLocation cachedBestTarget;
private static int cachedBestTargetType;
private static int cachedBestScore;
private static int cachedEconomyMode;  // 0=normal, 1=low, 2=critical
private static Team cachedOurTeam;
private static Team cachedEnemyTeam;

void runBabyRat(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    
    // 1. Update cached game state (uses static fields, no allocation)
    updateGameState(rc);
    
    // 2. Vision management: Turn toward best quadrant if no targets visible
    // NOTE: -1 means max vision range (√20 for baby rats, √34 for king)
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    MapLocation[] cheeseLocs = rc.senseNearbyCheese(-1);
    
    if (enemies.length == 0 && cheeseLocs.length == 0) {
        tryTurnTowardTarget(rc);  // Turn toward enemy king or goal
    }
    
    // 3. Check focus fire target from shared array
    int focusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    
    // 4. Try immediate actions (attack, collect, deliver, dig)
    if (tryImmediateAction(rc, enemies, cheeseLocs, focusTargetId)) return;
    
    // 5. Score all targets and find best (uses static fields)
    scoreAllTargets(rc, enemies, cheeseLocs);
    
    // 6. Move toward best target using Bug2 pathfinding
    if (cachedBestTarget != null) {
        bug2MoveTo(rc, cachedBestTarget);
    }
}
```

### Vision Cone Management

> ⚠️ **CRITICAL**: Baby rats have 90° vision cone. They CANNOT see behind or to the sides!

```java
/**
 * Turn toward the most likely valuable direction when nothing is visible.
 * This ensures rats don't walk past targets they can't see.
 */
void tryTurnTowardTarget(RobotController rc) throws GameActionException {
    if (!rc.isMovementReady()) return;  // Turning uses movement cooldown
    
    Direction currentFacing = rc.getDirection();
    MapLocation me = rc.getLocation();
    
    // Priority 1: Turn toward enemy king (known or estimated)
    MapLocation target = cachedEnemyKingLoc;
    if (target == null) {
        target = getEnemyKingEstimate(rc);
    }
    
    if (target != null) {
        Direction toTarget = me.directionTo(target);
        
        // Only turn if target is outside vision cone (>45° from facing)
        int angleDiff = getAngleDifference(currentFacing, toTarget);
        if (angleDiff > 45) {
            // Turn toward target (costs TURNING_COOLDOWN = 10)
            rc.turn(toTarget);
            return;
        }
    }
    
    // Priority 2: Turn toward our king if carrying cheese
    if (cachedCarryingCheese && cachedOurKingLoc != null) {
        Direction toKing = me.directionTo(cachedOurKingLoc);
        int angleDiff = getAngleDifference(currentFacing, toKing);
        if (angleDiff > 45) {
            rc.turn(toKing);
            return;
        }
    }
}

/** Returns angle difference in degrees (0-180) */
int getAngleDifference(Direction a, Direction b) {
    int diff = Math.abs(a.ordinal() - b.ordinal());
    if (diff > 4) diff = 8 - diff;
    return diff * 45;
}
```

---

## Missing Helper Functions (ADDED)

### tryImmediateAction() - Core Action Logic

```java
/**
 * Try immediate actions in priority order: attack, deliver, collect, dig.
 * Returns true if an action was taken (caller should return early).
 */
boolean tryImmediateAction(RobotController rc, RobotInfo[] enemies, 
        MapLocation[] cheeseLocs, int focusTargetId) throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    MapLocation me = rc.getLocation();
    
    // Priority 1: Attack (highest priority - combat is critical)
    if (enemies.length > 0) {
        if (tryAttack(rc, enemies, focusTargetId)) return true;
    }
    
    // Priority 2: Deliver cheese to our king (if carrying and close)
    if (cachedCarryingCheese && cachedOurKingLoc != null) {
        int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
        if (distToKing <= 9) {  // Delivery range is sqrt(9) = 3 tiles
            if (tryDeliverCheese(rc)) return true;
        }
    }
    
    // Priority 3: Collect cheese (if adjacent)
    if (cheeseLocs.length > 0) {
        if (tryCollectCheese(rc, cheeseLocs)) return true;
    }
    
    // Priority 4: Dig dirt (if blocking path forward)
    if (tryDigDirt(rc)) return true;
    
    return false;
}

/** Attack enemies, prioritizing focus fire target */
boolean tryAttack(RobotController rc, RobotInfo[] enemies, int focusTargetId) 
        throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    
    for (RobotInfo enemy : enemies) {
        if (!rc.canAttack(enemy.location)) continue;
        
        int score = 0;
        
        // Focus fire bonus (huge priority)
        if (focusTargetId > 0 && (enemy.ID % 1024) == focusTargetId) {
            score += 5000;
        }
        
        // Prioritize kings, then wounded enemies
        if (enemy.type == RobotType.RAT_KING) {
            score += 10000 - enemy.health;  // Kings are highest priority
        } else {
            score += 1000 - enemy.health;   // Lower HP = higher priority
        }
        
        if (score > bestScore) {
            bestScore = score;
            bestTarget = enemy;
        }
    }
    
    if (bestTarget != null) {
        rc.attack(bestTarget.location);
        return true;
    }
    return false;
}

/** Deliver cheese to our king */
boolean tryDeliverCheese(RobotController rc) throws GameActionException {
    if (!cachedCarryingCheese || cachedOurKingLoc == null) return false;
    
    int amount = rc.getRawCheese();
    if (amount <= 0) return false;
    
    // IMPORTANT: Always check canTransferCheese before transferring
    if (rc.canTransferCheese(cachedOurKingLoc, amount)) {
        rc.transferCheese(cachedOurKingLoc, amount);
        cachedCarryingCheese = false;  // Update cached state
        return true;
    }
    return false;
}

/** Collect adjacent cheese.
 * NOTE: Pickup range is distanceSquaredTo <= 2 (adjacent including diagonals).
 * This is verified from ratbot5 usage patterns.
 */
boolean tryCollectCheese(RobotController rc, MapLocation[] cheeseLocs) 
        throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    MapLocation me = rc.getLocation();
    
    for (MapLocation cheese : cheeseLocs) {
        // Check if cheese is adjacent (distance squared <= 2 = within 1 tile including diagonals)
        if (me.distanceSquaredTo(cheese) <= 2) {
            if (rc.canPickupCheese(cheese)) {
                rc.pickupCheese(cheese);
                cachedCarryingCheese = true;  // Update cached state
                return true;
            }
        }
    }
    return false;
}

/** Dig dirt blocking our path.
 * NOTE: rc.adjacentLocation(dir) returns location in that direction (verified API).
 */
boolean tryDigDirt(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    Direction facing = rc.getDirection();
    MapLocation me = rc.getLocation();
    MapLocation ahead = me.add(facing);  // Equivalent to rc.adjacentLocation(facing)
    
    // Dig dirt directly ahead
    if (rc.canRemoveDirt(ahead)) {
        rc.removeDirt(ahead);
        return true;
    }
    
    // Try diagonals if directly ahead is blocked
    MapLocation leftAhead = me.add(facing.rotateLeft());
    MapLocation rightAhead = me.add(facing.rotateRight());
    
    if (rc.canRemoveDirt(leftAhead)) {
        rc.removeDirt(leftAhead);
        return true;
    }
    if (rc.canRemoveDirt(rightAhead)) {
        rc.removeDirt(rightAhead);
        return true;
    }
    
    return false;
}
```

### updateGameState() - Cache All Game State

```java
/**
 * Update all cached game state variables.
 * Called at the start of each turn to minimize API calls.
 */
void updateGameState(RobotController rc) throws GameActionException {
    // Round and location (always fresh)
    cachedRound = rc.getRoundNum();
    MapLocation me = rc.getLocation();
    
    // Team (only set once)
    if (cachedOurTeam == null) {
        cachedOurTeam = rc.getTeam();
        cachedEnemyTeam = cachedOurTeam.opponent();
    }
    
    // Cheese state
    cachedCarryingCheese = rc.getRawCheese() > 0;
    cachedOurCheese = rc.getTeamCheese();
    
    // Read our king position from shared array
    int kingX = rc.readSharedArray(SLOT_OUR_KING_X);
    int kingY = rc.readSharedArray(SLOT_OUR_KING_Y);
    if (kingX > 0 || kingY > 0) {
        cachedOurKingLoc = new MapLocation(kingX, kingY);
        cachedDistToOurKing = me.distanceSquaredTo(cachedOurKingLoc);
    }
    
    // Read enemy king position from shared array
    int enemyX = rc.readSharedArray(SLOT_ENEMY_KING_X);
    int enemyY = rc.readSharedArray(SLOT_ENEMY_KING_Y);
    if (enemyX > 0 || enemyY > 0) {
        cachedEnemyKingLoc = new MapLocation(enemyX, enemyY);
    } else {
        // Fallback to symmetry-based estimate
        cachedEnemyKingLoc = getEnemyKingEstimate(rc);
    }
    
    // Read enemy king HP
    cachedEnemyKingHP = rc.readSharedArray(SLOT_ENEMY_KING_HP);
    if (cachedEnemyKingHP == 0) cachedEnemyKingHP = 500;  // Default to full HP
    
    // Economy mode
    cachedEconomyMode = rc.readSharedArray(SLOT_ECONOMY_MODE);
}
```

### King Helper Functions

```java
/** Broadcast king position to shared array */
void broadcastKingPosition(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    rc.writeSharedArray(SLOT_OUR_KING_X, me.x);
    rc.writeSharedArray(SLOT_OUR_KING_Y, me.y);
}

/** Update economy mode in shared array */
void updateEconomyMode(RobotController rc) throws GameActionException {
    int cheese = rc.getTeamCheese();
    int mode;
    
    if (cheese < CRITICAL_ECONOMY_THRESHOLD) {
        mode = 2;  // Critical - prioritize collection heavily
    } else if (cheese < LOW_ECONOMY_THRESHOLD) {
        mode = 1;  // Low - increase collection priority
    } else {
        mode = 0;  // Normal - balanced priorities
    }
    
    rc.writeSharedArray(SLOT_ECONOMY_MODE, mode);
}

/** Broadcast enemy king position if visible */
void broadcastEnemyKing(RobotController rc) throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    
    for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.RAT_KING) {
            rc.writeSharedArray(SLOT_ENEMY_KING_X, enemy.location.x);
            rc.writeSharedArray(SLOT_ENEMY_KING_Y, enemy.location.y);
            rc.writeSharedArray(SLOT_ENEMY_KING_HP, Math.min(enemy.health, 1023));
            return;
        }
    }
}

/** Try to spawn a baby rat */
boolean trySpawnRat(RobotController rc) throws GameActionException {
    // Check if we can afford to spawn
    int cost = getSpawnCost(rc);
    if (rc.getTeamCheese() < SPAWN_CHEESE_RESERVE + cost) {
        return false;  // Not enough cheese
    }
    
    MapLocation kingLoc = rc.getLocation();
    
    // Try to spawn in all 8 directions at distance 2
    // (King is 3x3, so distance 1 is inside king's footprint)
    for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        MapLocation spawnLoc = kingLoc.add(dir).add(dir);  // Distance 2
        
        if (rc.canBuildRat(spawnLoc)) {
            rc.buildRat(spawnLoc);
            return true;
        }
    }
    
    // Try distance 3 and 4 if distance 2 is blocked
    for (int dist = 3; dist <= 4; dist++) {
        for (int i = 0; i < 8; i++) {
            Direction dir = DIRECTIONS[i];
            int dx = dir.dx * dist;
            int dy = dir.dy * dist;
            MapLocation spawnLoc = kingLoc.translate(dx, dy);
            
            if (rc.canBuildRat(spawnLoc)) {
                rc.buildRat(spawnLoc);
                return true;
            }
        }
    }
    
    return false;  // No valid spawn location
}

/** Get the cost to spawn a baby rat based on current population */
int getSpawnCost(RobotController rc) throws GameActionException {
    // Spawn cost increases with population:
    // 0-3 rats: 10 cheese
    // 4-7 rats: 20 cheese
    // 8-11 rats: 30 cheese
    // etc.
    
    int babyRats = rc.getRobotCount() - 1;  // Subtract king
    if (babyRats < 0) babyRats = 0;
    
    int tier = babyRats / 4;
    return 10 + (tier * 10);
}

/** Try to place a trap near the king for defense */
boolean tryPlaceTrap(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    MapLocation me = rc.getLocation();
    
    // Place traps in a ring around the king
    for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        MapLocation trapLoc = me.add(dir).add(dir);  // Distance 2
        
        if (rc.canPlaceTrap(trapLoc, TrapType.RAT_TRAP)) {
            rc.placeTrap(trapLoc, TrapType.RAT_TRAP);
            return true;
        }
    }
    
    return false;
}

/** King movement wrapper with trap avoidance */
boolean tryKingMove(RobotController rc, Direction dir) throws GameActionException {
    if (!rc.isMovementReady()) return false;
    if (dir == null || dir == Direction.CENTER) return false;
    
    MapLocation me = rc.getLocation();
    MapLocation target = me.add(dir);
    
    // Check for traps before moving
    if (rc.canSenseLocation(target)) {
        MapInfo info = rc.senseMapInfo(target);
        if (info.getTrap() == TrapType.RAT_TRAP) {
            return false;  // Don't walk into traps!
        }
    }
    
    if (rc.canMove(dir)) {
        rc.move(dir);
        return true;
    }
    
    // Try rotating if direct path blocked
    Direction left = dir.rotateLeft();
    Direction right = dir.rotateRight();
    
    if (rc.canMove(left)) {
        rc.move(left);
        return true;
    }
    if (rc.canMove(right)) {
        rc.move(right);
        return true;
    }
    
    return false;
}
```

---

## The Value Function (Integer Arithmetic)

> ⚠️ **FIXED**: Uses integer math to avoid expensive float operations and Math.sqrt()

```java
/**
 * Score a potential target using INTEGER arithmetic.
 * Higher score = higher priority.
 * 
 * Formula: score = (baseValue * 1000) / (1000 + distSq * DISTANCE_WEIGHT)
 * This avoids Math.sqrt() which costs ~50 bytecode.
 */
int scoreTarget(int targetType, int distanceSq) {
    int baseValue = getBaseValue(targetType);
    
    // Integer division - no floats!
    // Equivalent to: baseValue / (1 + sqrt(distSq) * 0.15)
    // But faster: baseValue * 1000 / (1000 + distSq * weight)
    return (baseValue * 1000) / (1000 + distanceSq * DISTANCE_WEIGHT_INT);
}

int getBaseValue(int targetType) {
    switch (targetType) {
        case TARGET_ENEMY_KING:
            // Base 100, scales up to 200 when enemy king is wounded
            int hpFactor = 100 + (100 * (500 - cachedEnemyKingHP) / 500);
            return ENEMY_KING_BASE_VALUE * hpFactor / 100;
            
        case TARGET_ENEMY_RAT:
            return ENEMY_RAT_VALUE;
            
        case TARGET_CHEESE:
            // High value when economy is bad, low when economy is good
            if (cachedOurCheese < LOW_ECONOMY_THRESHOLD) {
                return CHEESE_VALUE_LOW_ECONOMY;
            }
            if (cachedOurCheese < CRITICAL_ECONOMY_THRESHOLD) {
                return CHEESE_VALUE_CRITICAL;  // Even higher!
            }
            return CHEESE_VALUE_NORMAL;
            
        case TARGET_DELIVERY:
            // Only high value when carrying cheese
            if (cachedCarryingCheese) {
                return DELIVERY_PRIORITY;
            }
            return 0;
            
        case TARGET_DIRT:
            return DIRT_VALUE;
            
        case TARGET_FOCUS:  // Focus fire target from shared array
            return FOCUS_FIRE_BONUS;
            
        default:
            return 0;
    }
}
```

---

## Constants (Tunable)

### Primary Value Constants

| Constant | Default | Description |
|----------|---------|-------------|
| `ENEMY_KING_BASE_VALUE` | 100 | Base attraction to enemy king |
| `ENEMY_RAT_VALUE` | 30 | Attraction to enemy rats |
| `CHEESE_VALUE_NORMAL` | 10 | Cheese value when economy is healthy |
| `CHEESE_VALUE_LOW_ECONOMY` | 60 | Cheese value when economy is low |
| `CHEESE_VALUE_CRITICAL` | 120 | Cheese value when starving |
| `DELIVERY_PRIORITY` | 150 | Priority to deliver cheese to our king |
| `DIRT_VALUE` | 5 | Attraction to dig through dirt |
| `FOCUS_FIRE_BONUS` | 50 | Extra value for focus fire target |
| `CAT_AVOIDANCE_VALUE` | -100 | Repulsion from cats |

### Threshold Constants (RAISED from original)

| Constant | Default | Description |
|----------|---------|-------------|
| `LOW_ECONOMY_THRESHOLD` | **200** | ~66 rounds survival (was 100) |
| `CRITICAL_ECONOMY_THRESHOLD` | **80** | ~26 rounds - emergency mode |
| `DELIVERY_RANGE_SQ` | **9** | 3 tiles - actual API limit |
| `WOUNDED_KING_HP` | 250 | Enemy king HP below which to all-in |
| `INTERCEPTOR_RANGE_SQ` | 100 | Distance² to intercept enemies near our king |

### Behavioral Constants

| Constant | Default | Description |
|----------|---------|-------------|
| `DISTANCE_WEIGHT_INT` | 15 | Integer weight for distance penalty |
| `ANTI_CLUMP_PENALTY` | 50 | Penalty for tiles with allies |
| `SPAWN_CHEESE_RESERVE` | **100** | Min cheese before spawning (was 50) |
| `FORWARD_MOVE_BONUS` | 20 | Bonus for moving forward (facing dir) |

### Shared Array Slot Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `SLOT_OUR_KING_X` | 0 | Our king X coordinate |
| `SLOT_OUR_KING_Y` | 1 | Our king Y coordinate |
| `SLOT_ENEMY_KING_X` | 2 | Enemy king X coordinate |
| `SLOT_ENEMY_KING_Y` | 3 | Enemy king Y coordinate |
| `SLOT_ENEMY_KING_HP` | 4 | Enemy king HP (clamped to 1023) |
| `SLOT_ECONOMY_MODE` | 5 | Economy mode (0=normal, 1=low, 2=critical) |
| `SLOT_FOCUS_TARGET` | 6 | Focus fire target ID % 1024 |
| `SLOT_FOCUS_HP` | 7 | Focus fire target HP |
| `SLOT_ROUND` | 8 | Round number for freshness checks |

### King Constants (FIXED cooldowns)

| Constant | Default | Description |
|----------|---------|-------------|
| `KING_MOVEMENT_COOLDOWN` | 40 | Actual king movement cooldown |
| `KING_SAFE_ZONE_RADIUS_SQ` | 64 | King stays within this of spawn |
| `KING_DELIVERY_PAUSE_ROUNDS` | 3 | Rounds to pause for delivery |

---

## King Behavior (FIXED)

> ⚠️ **CRITICAL FIXES**:
> 1. King movement cooldown is 40, NOT 10. Can only move every 4+ rounds.
> 2. King must pause for cheese delivery or rats can't deliver.
> 3. King should stay near spawn point for cheese access.

```java
// King state
private static MapLocation kingSpawnPoint;  // Where king started
private static int lastDeliveryRound = 0;   // Track when rats delivered
private static boolean enemiesNearby = false;

void runKing(RobotController rc) throws GameActionException {
    if (kingSpawnPoint == null) {
        kingSpawnPoint = rc.getLocation();  // Remember spawn point
    }
    
    // 1. Broadcast our position (slot 0-1)
    broadcastKingPosition(rc);
    
    // 2. Check for enemies and update focus fire target
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    enemiesNearby = enemies.length > 0;
    
    if (enemies.length > 0) {
        updateFocusFireTarget(rc, enemies);
    }
    
    // 3. Spawn rats when affordable
    if (rc.getTeamCheese() > SPAWN_CHEESE_RESERVE + getSpawnCost(rc)) {
        trySpawnRat(rc);
    }
    
    // 4. Place traps near self for defense
    if (enemiesNearby) {
        tryPlaceTrap(rc);
    }
    
    // 5. Mobile king - but ONLY when safe and movement ready
    if (rc.isMovementReady()) {
        // Check if rats recently delivered - pause to allow more deliveries
        int roundsSinceDelivery = rc.getRoundNum() - lastDeliveryRound;
        
        if (enemiesNearby) {
            // FLEE from enemies - priority over everything
            evadeFromEnemies(rc, enemies);
        } else if (roundsSinceDelivery > KING_DELIVERY_PAUSE_ROUNDS) {
            // Only move if no recent delivery (give rats time to deliver)
            // AND stay within safe zone of spawn
            randomMoveInSafeZone(rc);
        }
    }
    
    // 6. Broadcast enemy king position if visible
    broadcastEnemyKing(rc);
    
    // 7. Update economy mode (slot 5)
    updateEconomyMode(rc);
}

/** King moves away from enemies */
void evadeFromEnemies(RobotController rc, RobotInfo[] enemies) throws GameActionException {
    MapLocation me = rc.getLocation();
    
    // Calculate center of enemy mass
    int sumX = 0, sumY = 0;
    for (RobotInfo enemy : enemies) {
        sumX += enemy.location.x;
        sumY += enemy.location.y;
    }
    MapLocation enemyCenter = new MapLocation(sumX / enemies.length, sumY / enemies.length);
    
    // Move away from enemies
    Direction awayDir = enemyCenter.directionTo(me);
    tryKingMove(rc, awayDir);
}

/** King random walk but stays within safe zone of spawn */
void randomMoveInSafeZone(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    
    // If too far from spawn, move back
    if (me.distanceSquaredTo(kingSpawnPoint) > KING_SAFE_ZONE_RADIUS_SQ) {
        Direction toSpawn = me.directionTo(kingSpawnPoint);
        tryKingMove(rc, toSpawn);
        return;
    }
    
    // Random walk within safe zone
    Direction randomDir = DIRECTIONS[rng.nextInt(8)];
    MapLocation newLoc = me.add(randomDir);
    
    // Only move if still in safe zone
    if (newLoc.distanceSquaredTo(kingSpawnPoint) <= KING_SAFE_ZONE_RADIUS_SQ) {
        tryKingMove(rc, randomDir);
    }
}

/** Called when a rat delivers cheese to king */
void onCheeseDelivered(RobotController rc) {
    lastDeliveryRound = rc.getRoundNum();
}
```

---

## Focus Fire System (NEW)

> ⚠️ **CRITICAL**: Without focus fire, ratbot6 loses 3v3 fights by spreading damage.

```java
// Shared array slot for focus fire
private static final int SLOT_FOCUS_TARGET = 6;  // Robot ID of priority target
private static final int SLOT_FOCUS_HP = 7;      // HP of focus target (to detect kills)

/** King updates the focus fire target for all rats */
void updateFocusFireTarget(RobotController rc, RobotInfo[] enemies) throws GameActionException {
    // Priority: lowest HP enemy that's a king, then lowest HP rat
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    
    for (RobotInfo enemy : enemies) {
        int score = 0;
        
        if (enemy.type == RobotType.RAT_KING) {
            score = 10000 - enemy.health;  // Kings are highest priority
        } else {
            score = 1000 - enemy.health;   // Wounded rats next
        }
        
        if (score > bestScore) {
            bestScore = score;
            bestTarget = enemy;
        }
    }
    
    if (bestTarget != null) {
        rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.ID % 1024);  // Mod to fit 10 bits
        rc.writeSharedArray(SLOT_FOCUS_HP, Math.min(bestTarget.health, 1023));
    }
}

/** Baby rat checks if target is the focus fire target */
boolean isFocusTarget(RobotInfo enemy, int focusTargetId) {
    return (enemy.ID % 1024) == focusTargetId;
}
```

---

## Combat: No-Kite + Focus Fire

```java
boolean tryCombat(RobotController rc, RobotInfo[] enemies, int focusTargetId) 
        throws GameActionException {
    if (!rc.isActionReady()) return false;
    
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    
    for (RobotInfo enemy : enemies) {
        if (!rc.canAttack(enemy.location)) continue;
        
        int score = 0;
        
        // FOCUS FIRE: Huge bonus for the designated target
        if (isFocusTarget(enemy, focusTargetId)) {
            score += 5000;
        }
        
        // Prioritize kings, then wounded enemies
        if (enemy.type == RobotType.RAT_KING) {
            score += 1000 - enemy.health;
        } else {
            score += 100 - enemy.health;
        }
        
        if (score > bestScore) {
            bestScore = score;
            bestTarget = enemy;
        }
    }
    
    if (bestTarget != null) {
        rc.attack(bestTarget.location);
        return true;
    }
    return false;
}
```

---

## Defensive Interceptor Behavior (NEW)

> Some rats should defend our king instead of all rushing enemy king.

```java
/** 
 * Check if this rat should act as interceptor (defend our king).
 * Uses robot ID to deterministically assign ~20% of rats to defense.
 */
boolean shouldIntercept(RobotController rc) {
    // 20% of rats are interceptors (ID % 5 == 0)
    return rc.getID() % 5 == 0;
}

/** Interceptor behavior: patrol near our king.
 * @param enemies Pre-sensed enemy array (avoids duplicate sensing)
 */
void runInterceptor(RobotController rc, RobotInfo[] enemies) throws GameActionException {
    MapLocation me = rc.getLocation();
    
    // If enemy near our king, attack them!
    for (RobotInfo enemy : enemies) {
        if (cachedOurKingLoc != null && 
            enemy.location.distanceSquaredTo(cachedOurKingLoc) < INTERCEPTOR_RANGE_SQ) {
            // This enemy is near our king - prioritize them
            bug2MoveTo(rc, enemy.location);
            return;
        }
    }
    
    // Otherwise, patrol near our king
    if (cachedOurKingLoc != null) {
        int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
        
        if (distToKing > INTERCEPTOR_RANGE_SQ) {
            // Too far from king, move closer
            bug2MoveTo(rc, cachedOurKingLoc);
        } else {
            // Near king, random patrol
            Direction randomDir = DIRECTIONS[rng.nextInt(8)];
            if (rc.canMove(randomDir)) {
                rc.move(randomDir);
            }
        }
    }
}
```

---

## Movement: Bug2 Pathfinding (REQUIRED from Phase 1)

> ⚠️ **CRITICAL**: Simple greedy movement gets stuck on walls. Bug2 is NOT optional.

```java
// Bug2 state (static to persist between turns)
private static MapLocation bug2Target;
private static boolean bug2WallFollowing = false;
private static Direction bug2WallDir;
private static MapLocation bug2StartLoc;
private static int bug2StartDist;

/**
 * Bug2 pathfinding: Move toward target, wall-follow when blocked.
 * This is essential - greedy movement fails at any obstacle.
 */
void bug2MoveTo(RobotController rc, MapLocation target) throws GameActionException {
    if (target == null || !rc.isMovementReady()) return;
    
    MapLocation me = rc.getLocation();
    
    // Reset wall following if target changed
    if (!target.equals(bug2Target)) {
        bug2Target = target;
        bug2WallFollowing = false;
    }
    
    // At target?
    if (me.equals(target)) return;
    
    Direction toTarget = me.directionTo(target);
    
    if (!bug2WallFollowing) {
        // Try direct path with trap avoidance
        if (canMoveSafely(rc, toTarget)) {
            rc.move(toTarget);
            return;
        }
        
        // Try adjacent directions
        Direction left = toTarget.rotateLeft();
        Direction right = toTarget.rotateRight();
        
        if (canMoveSafely(rc, left)) {
            rc.move(left);
            return;
        }
        if (canMoveSafely(rc, right)) {
            rc.move(right);
            return;
        }
        
        // Blocked - start wall following
        bug2WallFollowing = true;
        bug2WallDir = toTarget;
        bug2StartLoc = me;
        bug2StartDist = me.distanceSquaredTo(target);
    }
    
    // Wall following mode
    if (bug2WallFollowing) {
        // Check if we've reached a better position (closer to target on the m-line)
        int currentDist = me.distanceSquaredTo(target);
        if (currentDist < bug2StartDist && !me.equals(bug2StartLoc)) {
            // FIXED: Don't recurse - just reset and return, next turn handles it
            bug2WallFollowing = false;
            return;
        }
        
        // Follow wall (turn right at walls) with trap avoidance
        for (int i = 0; i < 8; i++) {
            if (canMoveSafely(rc, bug2WallDir)) {
                rc.move(bug2WallDir);
                bug2WallDir = bug2WallDir.rotateLeft().rotateLeft();  // Turn left after moving
                return;
            }
            bug2WallDir = bug2WallDir.rotateRight();  // Turn right if blocked
        }
    }
}

/** 
 * Check if we can move safely (no traps, passable terrain).
 * Also checks for enemy traps to avoid walking into them.
 */
boolean canMoveSafely(RobotController rc, Direction dir) throws GameActionException {
    if (!rc.canMove(dir)) return false;
    
    MapLocation target = rc.adjacentLocation(dir);
    
    // CRITICAL: Check for enemy traps before moving
    if (rc.canSenseLocation(target)) {
        MapInfo info = rc.senseMapInfo(target);
        if (info.getTrap() == TrapType.RAT_TRAP) {
            return false;  // Don't walk into traps!
        }
    }
    
    return true;
}
```

---

## Movement Scoring with Strafe Penalty

```java
/** Score a movement direction (higher = better) */
int scoreMovement(RobotController rc, Direction dir, MapLocation target) 
        throws GameActionException {
    if (!rc.canMove(dir)) return Integer.MIN_VALUE;
    
    MapLocation me = rc.getLocation();
    MapLocation newLoc = me.add(dir);
    Direction facing = rc.getDirection();
    
    int score = 0;
    
    // Closer to target is better (negative distance)
    score -= newLoc.distanceSquaredTo(target) * 10;
    
    // Bonus for forward movement (lower cooldown)
    if (dir == facing) {
        score += FORWARD_MOVE_BONUS;
    } else if (dir == facing.rotateLeft() || dir == facing.rotateRight()) {
        score += FORWARD_MOVE_BONUS / 2;  // Slight bonus for near-forward
    }
    
    // Anti-clumping: penalty for tiles with allies
    RobotInfo robot = rc.senseRobotAtLocation(newLoc);
    if (robot != null && robot.team == rc.getTeam()) {
        score -= ANTI_CLUMP_PENALTY;
    }
    
    return score;
}
```

---

## Enemy King Position Estimation (DEFINED)

> ⚠️ **FIXED**: This function was referenced but never defined.

```java
// Map symmetry (detected on round 1)
private static int mapSymmetry = -1;  // 0=rotational, 1=horizontal, 2=vertical
private static MapLocation estimatedEnemyKingLoc;

/** 
 * Estimate enemy king position based on map symmetry.
 * Called when we don't have confirmed enemy king location.
 */
MapLocation getEnemyKingEstimate(RobotController rc) throws GameActionException {
    // If we have a cached estimate, use it
    if (estimatedEnemyKingLoc != null) return estimatedEnemyKingLoc;
    
    // Calculate based on our king position and map symmetry
    if (cachedOurKingLoc == null) return null;
    
    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();
    int ourX = cachedOurKingLoc.x;
    int ourY = cachedOurKingLoc.y;
    
    // Default: assume rotational symmetry (most common)
    if (mapSymmetry == -1 || mapSymmetry == 0) {
        // Rotational: enemy at (width - x - 1, height - y - 1)
        estimatedEnemyKingLoc = new MapLocation(
            mapWidth - ourX - 1,
            mapHeight - ourY - 1
        );
    } else if (mapSymmetry == 1) {
        // Horizontal reflection: enemy at (width - x - 1, y)
        estimatedEnemyKingLoc = new MapLocation(
            mapWidth - ourX - 1,
            ourY
        );
    } else {
        // Vertical reflection: enemy at (x, height - y - 1)
        estimatedEnemyKingLoc = new MapLocation(
            ourX,
            mapHeight - ourY - 1
        );
    }
    
    return estimatedEnemyKingLoc;
}

/** Detect map symmetry by checking wall patterns (called on round 1) */
void detectMapSymmetry(RobotController rc) throws GameActionException {
    // For MVP, assume rotational symmetry (most common in Battlecode)
    mapSymmetry = 0;
    
    // TODO: Can be refined by checking wall locations if needed
}
```

---

## Cat Handling (NEW SECTION)

```java
// Cat constants
private static final int CAT_DANGER_RADIUS_SQ = 36;  // 6 tiles - cat pounce range + buffer
private static final int CAT_SCRATCH_DAMAGE = 50;     // One-shot kill potential

/** Check if any cats are dangerous nearby */
RobotInfo findDangerousCat(RobotInfo[] neutrals) {
    for (RobotInfo neutral : neutrals) {
        if (neutral.type == RobotType.CAT) {
            // Cat is dangerous if not sleeping
            // (Sleeping cats have high cooldown)
            return neutral;
        }
    }
    return null;
}

/** Flee from cat - highest priority */
void fleeFromCat(RobotController rc, RobotInfo cat) throws GameActionException {
    if (!rc.isMovementReady()) return;
    
    MapLocation me = rc.getLocation();
    Direction awayFromCat = cat.location.directionTo(me);
    
    // Try to move away
    if (rc.canMove(awayFromCat)) {
        rc.move(awayFromCat);
        return;
    }
    
    // Try adjacent directions
    Direction left = awayFromCat.rotateLeft();
    Direction right = awayFromCat.rotateRight();
    
    if (rc.canMove(left)) rc.move(left);
    else if (rc.canMove(right)) rc.move(right);
}
```

---

## Round 1 Initialization (NEW SECTION)

```java
/** Called once on first turn for each robot */
void initializeRobot(RobotController rc) throws GameActionException {
    cachedOurTeam = rc.getTeam();
    cachedEnemyTeam = cachedOurTeam.opponent();
    
    if (rc.getType() == RobotType.RAT_KING) {
        // King initialization
        kingSpawnPoint = rc.getLocation();
        detectMapSymmetry(rc);
        
        // Write initial position to shared array
        rc.writeSharedArray(SLOT_OUR_KING_X, rc.getLocation().x);
        rc.writeSharedArray(SLOT_OUR_KING_Y, rc.getLocation().y);
        
        // Calculate and broadcast estimated enemy king position
        estimatedEnemyKingLoc = getEnemyKingEstimate(rc);
        if (estimatedEnemyKingLoc != null) {
            rc.writeSharedArray(SLOT_ENEMY_KING_X, estimatedEnemyKingLoc.x);
            rc.writeSharedArray(SLOT_ENEMY_KING_Y, estimatedEnemyKingLoc.y);
        }
    }
}
```

---

## Shared Array Layout (EXPANDED)

| Slot | Purpose | Format |
|------|---------|--------|
| Slot | Purpose | Format | Constant |
|------|---------|--------|----------|
| 0 | Our king X | 0-60 | `SLOT_OUR_KING_X` |
| 1 | Our king Y | 0-60 | `SLOT_OUR_KING_Y` |
| 2 | Enemy king X | 0-60 (0 = unknown) | `SLOT_ENEMY_KING_X` |
| 3 | Enemy king Y | 0-60 | `SLOT_ENEMY_KING_Y` |
| 4 | Enemy king HP | 0-500 (clamped) | `SLOT_ENEMY_KING_HP` |
| 5 | Economy mode | 0=normal, 1=low, 2=critical | `SLOT_ECONOMY_MODE` |
| 6 | **Focus target ID** | Robot ID % 1024 | `SLOT_FOCUS_TARGET` |
| 7 | **Focus target HP** | 0-1023 | `SLOT_FOCUS_HP` |
| 8 | Round number (freshness) | Round % 1024 | `SLOT_ROUND` |

---

## Target Selection (Static Fields)

> ⚠️ **FIXED**: Uses static fields instead of allocating new objects.

```java
// Target types (constants, not enum to save bytecode)
private static final int TARGET_NONE = 0;
private static final int TARGET_ENEMY_KING = 1;
private static final int TARGET_ENEMY_RAT = 2;
private static final int TARGET_CHEESE = 3;
private static final int TARGET_DELIVERY = 4;
private static final int TARGET_DIRT = 5;
private static final int TARGET_FOCUS = 6;

// Static result fields (no allocation)
private static MapLocation cachedBestTarget;
private static int cachedBestTargetType;
private static int cachedBestScore;

void scoreAllTargets(RobotController rc, RobotInfo[] enemies, MapLocation[] cheeseLocs) 
        throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;
    
    MapLocation me = rc.getLocation();
    
    // Score enemy king
    if (cachedEnemyKingLoc != null) {
        int distSq = me.distanceSquaredTo(cachedEnemyKingLoc);
        int score = scoreTarget(TARGET_ENEMY_KING, distSq);
        if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = cachedEnemyKingLoc;
            cachedBestTargetType = TARGET_ENEMY_KING;
        }
    }
    
    // Score visible enemy rats
    for (RobotInfo enemy : enemies) {
        if (enemy.type == RobotType.RAT_KING) continue;
        int distSq = me.distanceSquaredTo(enemy.location);
        int score = scoreTarget(TARGET_ENEMY_RAT, distSq);
        
        // Check if this is focus fire target
        int focusId = 0;
        try { focusId = rc.readSharedArray(SLOT_FOCUS_TARGET); } catch (Exception e) {}
        if (isFocusTarget(enemy, focusId)) {
            score += FOCUS_FIRE_BONUS * 1000 / (1000 + distSq * DISTANCE_WEIGHT_INT);
        }
        
        if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = enemy.location;
            cachedBestTargetType = TARGET_ENEMY_RAT;
        }
    }
    
    // Score cheese
    for (MapLocation cheese : cheeseLocs) {
        int distSq = me.distanceSquaredTo(cheese);
        int score = scoreTarget(TARGET_CHEESE, distSq);
        if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = cheese;
            cachedBestTargetType = TARGET_CHEESE;
        }
    }
    
    // Score delivery
    if (cachedCarryingCheese && cachedOurKingLoc != null) {
        int distSq = me.distanceSquaredTo(cachedOurKingLoc);
        int score = scoreTarget(TARGET_DELIVERY, distSq);
        if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = cachedOurKingLoc;
            cachedBestTargetType = TARGET_DELIVERY;
        }
    }
    
    // Fallback: move toward estimated enemy king
    if (cachedBestTarget == null) {
        cachedBestTarget = getEnemyKingEstimate(rc);
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 1;
    }
    
    // CHEESE EXHAUSTION FALLBACK: If no cheese visible and economy is healthy,
    // prioritize attacking (all rats become attackers)
    if (cheeseLocs.length == 0 && cachedOurCheese > LOW_ECONOMY_THRESHOLD) {
        // No cheese to collect - go full attack mode
        if (cachedEnemyKingLoc != null && cachedBestTargetType == TARGET_CHEESE) {
            cachedBestTarget = cachedEnemyKingLoc;
            cachedBestTargetType = TARGET_ENEMY_KING;
            cachedBestScore = ENEMY_KING_BASE_VALUE;  // Override cheese priority
        }
    }
}
```

---

## Complete Main Loop (REVISED)

> All helper functions now defined above.

```java
void runBabyRat(RobotController rc) throws GameActionException {
    // 1. Update cached game state (no allocation, uses static fields)
    updateGameState(rc);
    
    MapLocation me = rc.getLocation();
    
    // 2. Check for dangerous cats first (highest priority flee)
    // NOTE: -1 means max vision range for sensing
    RobotInfo[] neutrals = rc.senseNearbyRobots(-1, Team.NEUTRAL);
    RobotInfo dangerousCat = findDangerousCat(neutrals);
    if (dangerousCat != null && me.distanceSquaredTo(dangerousCat.location) < CAT_DANGER_RADIUS_SQ) {
        fleeFromCat(rc, dangerousCat);
        return;
    }
    
    // 3. Sense enemies and cheese ONCE (avoid duplicate sensing)
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    MapLocation[] cheeseLocs = rc.senseNearbyCheese(-1);
    
    // 4. Check if this rat should be an interceptor (20% of rats)
    if (shouldIntercept(rc)) {
        runInterceptor(rc, enemies);  // Pass already-sensed enemies
        return;
    }
    
    // 5. Vision management: Turn toward target if nothing visible
    if (enemies.length == 0 && cheeseLocs.length == 0) {
        tryTurnTowardTarget(rc);
    }
    
    // 6. Read focus fire target from shared array
    int focusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    
    // 7. Try immediate actions (attack, deliver, collect, dig)
    if (tryImmediateAction(rc, enemies, cheeseLocs, focusTargetId)) {
        return;  // Action taken, turn complete
    }
    
    // 8. Score all targets and find best
    scoreAllTargets(rc, enemies, cheeseLocs);
    
    // 9. Move toward best target using Bug2 pathfinding
    if (cachedBestTarget != null) {
        bug2MoveTo(rc, cachedBestTarget);
    }
}
```

---

## Ratbot5 Counter Strategies (UPDATED)

| Ratbot5 Feature | Ratbot6 Counter |
|-----------------|-----------------|
| Assassins rush cached king pos | **Mobile king** in safe zone + traps |
| Complex kiting state machine | **No-kite combat** + focus fire |
| Squeak relay chain | **Shared array focus fire** - instant coordination |
| 20% assassin / 80% other split | **80% attack / 20% interceptor** - defend + attack |
| Spread flanking (left/center/right) | **Concentrated force** with focus fire |
| 3500 lines, ~1500 bytecode/turn | **700 lines, ~1000 bytecode/turn** |
| No focus fire | **Focus fire** - kill enemies faster |

---

## Estimated Line Count (REVISED)

| Component | Lines |
|-----------|-------|
| Constants & imports | 60 |
| Value function (int math) | 50 |
| Target scoring | 80 |
| Combat + focus fire | 60 |
| Movement + Bug2 | 100 |
| Vision management | 40 |
| King behavior | 100 |
| Interceptor behavior | 40 |
| Game state (static) | 50 |
| Shared array helpers | 50 |
| Cat handling | 30 |
| Initialization | 40 |
| Main loop | 50 |
| **Total** | **~750 lines** |

---

## Implementation Phases (REVISED)

### Phase 1: MVP (Get it working)
- [x] Basic value function with integer arithmetic
- [x] **Bug2 pathfinding** (REQUIRED, not optional)
- [x] Vision cone management (turning)
- [x] Attack when adjacent
- [x] Pick up cheese when adjacent
- [x] Deliver to king when adjacent
- [x] King spawns rats (correct cooldowns)
- [x] Enemy king estimation (symmetry-based)
- **Goal:** Beat examplefuncsplayer

### Phase 2: Core Features
- [ ] Focus fire system (shared array)
- [ ] Mobile king with safe zone
- [ ] Interceptor behavior (20% of rats)
- [ ] Cat avoidance
- [ ] Trap placement
- [ ] Anti-clumping with strafe optimization
- **Goal:** Beat ratbot4

### Phase 3: Optimization
- [ ] Tune constants via testing
- [ ] Bytecode optimization
- [ ] Map symmetry detection
- [ ] Map-specific adjustments
- **Goal:** Beat ratbot5

---

## Tuning Guide (EXPANDED)

### Economy Problems
| Symptom | Fix |
|---------|-----|
| Rats dying of starvation | Increase `LOW_ECONOMY_THRESHOLD` to 300+ |
| Never attacking, always collecting | Decrease `CHEESE_VALUE_NORMAL` to 5 |
| King dies from lack of cheese | Increase `SPAWN_CHEESE_RESERVE` to 150 |

### Combat Problems
| Symptom | Fix |
|---------|-----|
| Rats not attacking enemy king | Increase `ENEMY_KING_BASE_VALUE` to 150 |
| Losing focus fire fights | Increase `FOCUS_FIRE_BONUS` to 80 |
| Rats ignoring nearby enemies | Decrease `DISTANCE_WEIGHT_INT` to 10 |

### King Problems
| Symptom | Fix |
|---------|-----|
| King getting assassinated | Decrease `KING_SAFE_ZONE_RADIUS_SQ` to 36 |
| Rats can't deliver to moving king | Increase `KING_DELIVERY_PAUSE_ROUNDS` to 5 |
| King too passive | Increase `KING_SAFE_ZONE_RADIUS_SQ` to 100 |

### Defense Problems
| Symptom | Fix |
|---------|-----|
| Our king undefended | Change interceptor ratio from 20% to 30% |
| Too many interceptors, slow offense | Reduce interceptor ratio to 10% |

---

## Test Plan

1. **vs examplefuncsplayer**: Must win easily (validates basic functionality)
2. **vs ratbot4**: Must win consistently (validates combat effectiveness)
3. **vs ratbot5 Team A**: Must win (validates counter-strategy)
4. **vs ratbot5 Team B**: Must win (validates symmetry handling)
5. **All three maps**: DefaultSmall, DefaultMedium, DefaultLarge

### Specific Tests
- [ ] Rats don't get stuck on walls (Bug2 working)
- [ ] Rats turn to see targets outside vision cone
- [ ] King stays in safe zone
- [ ] Focus fire kills enemies faster than spread damage
- [ ] Economy doesn't crash (cheese collected and delivered)
- [ ] Interceptors defend our king

---

## Success Criteria

- [ ] Wins against ratbot5 on all maps, both Team A and Team B
- [ ] Code is under 800 lines
- [ ] Bytecode usage under 1500/turn average for baby rats
- [ ] No float arithmetic in hot paths
- [ ] No object allocation in main loop
- [ ] Vision cone properly handled
- [ ] Constants are clearly documented and easy to tune

---

## Appendix A: Key Differences from Original Design

| Issue | Original | Fixed |
|-------|----------|-------|
| Vision cone | Ignored | Turn toward targets when nothing visible |
| King cooldown | 3 rounds | Use `isMovementReady()`, stay in safe zone |
| Pathfinding | Phase 3 (optional) | Phase 1 (required) |
| Focus fire | None | Shared array slot for priority target |
| Defense | None | 20% interceptors |
| Economy threshold | 100 cheese | 200 cheese |
| Delivery range | 25 (5 tiles) | 9 (3 tiles) - actual API limit |
| Float math | Yes | Integer only |
| Object allocation | Every turn | Static fields |
| Enemy king estimate | Undefined | Symmetry-based calculation |
| Cat handling | "Avoid" | Flee behavior defined |
| Round 1 | Undefined | Initialize and broadcast |

---

## Appendix B: Complete API Reference Used

| Method | Purpose | Notes |
|--------|---------|-------|
| `rc.canAttack(loc)` | Check if can attack | Must be adjacent, action ready |
| `rc.attack(loc)` | Attack enemy | 10 base damage |
| `rc.attack(loc, cheese)` | Enhanced attack | Uses cheese for bonus damage |
| `rc.canPickupCheese(loc)` | Check if can collect | Must be adjacent |
| `rc.pickupCheese(loc)` | Collect cheese | Adds to raw cheese |
| `rc.getRawCheese()` | Get carried cheese | Amount rat is carrying |
| `rc.canTransferCheese(loc, amt)` | Check delivery | Must be in range of king |
| `rc.transferCheese(loc, amt)` | Deliver to king | Transfers to team cheese |
| `rc.getTeamCheese()` | Get team cheese | Total team stockpile |
| `rc.canRemoveDirt(loc)` | Check if can dig | Dirt must be at location |
| `rc.removeDirt(loc)` | Dig dirt | Clears the tile |
| `rc.canBuildRat(loc)` | Check spawn | Location, range, cost |
| `rc.buildRat(loc)` | Spawn baby rat | King only |
| `rc.getRobotCount()` | Count our robots | For spawn cost calculation |
| `rc.canPlaceTrap(loc, type)` | Check trap placement | King only |
| `rc.placeTrap(loc, type)` | Place trap | RAT_TRAP or CAT_TRAP |
| `rc.senseTrap(loc)` | Detect trap type | For avoidance |
| `rc.senseMapInfo(loc)` | Get tile info | Includes trap, passability |
| `rc.turn(dir)` | Change facing | Uses movement cooldown |
| `rc.getDirection()` | Get facing dir | Current orientation |
| `rc.canMove(dir)` | Check movement | Passable, cooldown ready |
| `rc.move(dir)` | Move one tile | Forward = 10 cd, strafe = 18 |
| `rc.isMovementReady()` | Check cooldown | True if can move |
| `rc.isActionReady()` | Check cooldown | True if can act |
| `rc.readSharedArray(i)` | Read shared slot | 64 slots available |
| `rc.writeSharedArray(i, v)` | Write shared slot | Values 0-65535 |
| `rc.senseNearbyRobots(r, t)` | Sense robots | -1 = max vision range |
| `rc.senseNearbyCheese(r)` | Sense cheese | -1 = max vision range |
| `rc.adjacentLocation(dir)` | Get adjacent tile | Equivalent to `me.add(dir)` |
