# Ratbot2 - Complete Technical Implementation Plan

**Created**: January 6, 2026
**Sprint 1 Deadline**: January 12, 2026 (6 days)
**Approach**: Fresh start from examplefuncsplayer with full spec knowledge

---

## Phase 0: Verified Game Mechanics (From Spec + Testing)

### Scoring Formula (Cooperation Mode)
```
score = round(0.5 * %catDamage + 0.3 * %kings + 0.2 * %cheese)

Where:
%catDamage = ourDamage / (ourDamage + enemyDamage)
%kings = ourKings / (ourKings + enemyKings)
%cheese = ourCheese / (ourCheese + enemyCheese)
```

**Source**: Battlecode 2026 PDF page 4, Equation 1

### Critical Constraints (From Javadoc + Testing)

**King Spawning** (HARD-WON):
- King is 3×3, occupies 9 tiles
- Adjacent tiles (distance=1) are king's own footprint
- **Must spawn at distance=2**: `location.add(dir).add(dir)`
- Spawn cost: 10 + 10×floor(n/4) where n = baby rats alive
- Cooldown: 10 (can spawn every turn)

**Vision Cones** (From spec page 5):
- Baby rat: sqrt(20) radius, **90° cone** (facing direction)
- Rat king: sqrt(25) radius, **360° omnidirectional**
- Attack requires: distance≤2 AND in vision cone AND action ready

**Cat Stats** (From spec page 12):
- HP: 10,000
- Damage: Scratch 50, Pounce instant kill
- Size: 2×2
- Vision: 180° cone, sqrt(30) radius

**Cat Traps** (From spec page 10):
- Cost: 10 cheese each
- Damage: 100 + stun (20 movement cooldown)
- Max: 10 active per team
- Only placeable in cooperation mode
- Trigger radius: sqrt(2) = 1.4 tiles

**Cheese Transfer** (From CONSTANTS_VERIFIED.md):
- Range: CHEESE_DROP_RADIUS_SQUARED = 9 (3 tiles)
- Cooldown: 10

**Movement Mechanics** (From Testing):
- Turn adds 10 cooldown
- Move adds 10 cooldown (forward) or 18 (strafe)
- Cannot turn AND move same round
- Must: turn → return → move next round

---

## Phase 1: Package Structure

### Directory Layout
```
src/ratbot2/
├── RobotPlayer.java          # Entry point
├── RatKing.java              # King behavior (spawning, traps, tracking)
├── CombatRat.java            # Dedicated cat attacker (70% of rats)
├── EconomyRat.java           # Dedicated cheese collector (30% of rats)
├── Communications.java       # Shared array protocol
└── utils/
    ├── CatTracker.java       # Cat position tracking
    ├── Movement.java         # Obstacle avoidance, turn-then-move
    ├── Vision.java           # Cone calculations (KEEP from ratbot)
    ├── Constants.java        # Game constants (KEEP from ratbot)
    └── Debug.java            # Debug utilities (KEEP from ratbot)
```

### What to Copy from Ratbot

**KEEP EXACTLY AS-IS:**
- `algorithms/Vision.java` - Vision cone math (proven correct)
- `algorithms/Constants.java` - Game constants (verified from javadoc)
- `algorithms/DirectionUtil.java` - Turn optimization
- `algorithms/Geometry.java` - Distance calculations
- `Debug.java` - Debugging system (invaluable)
- `DebugConfig.java` - Debug configuration
- `Logger.java` - Performance logging

**ADAPT:**
- Shared array communication pattern
- Emergency circuit breaker concept

**DISCARD:**
- All of RatKing.java (rewrite from scratch)
- All of BabyRat.java (rewrite as CombatRat + EconomyRat)
- Spawn limiting logic
- Repositioning logic
- Economic hoarding mentality

---

## Phase 2: Core Architecture

### Communications.java - Shared Array Protocol

**Slot Allocation** (64 slots, 10-bit values):
```java
public class Communications {
    // Emergency & King
    public static final int SLOT_EMERGENCY = 0;      // 999=critical, 0-200=rounds remaining
    public static final int SLOT_KING_X = 1;
    public static final int SLOT_KING_Y = 2;

    // Cat Tracking (up to 4 cats)
    public static final int SLOT_CAT1_X = 3;
    public static final int SLOT_CAT1_Y = 4;
    public static final int SLOT_CAT2_X = 5;
    public static final int SLOT_CAT2_Y = 6;
    public static final int SLOT_CAT3_X = 7;
    public static final int SLOT_CAT3_Y = 8;
    public static final int SLOT_CAT4_X = 9;
    public static final int SLOT_CAT4_Y = 10;

    // Primary Target
    public static final int SLOT_PRIMARY_CAT_X = 11;  // Focus fire target
    public static final int SLOT_PRIMARY_CAT_Y = 12;

    // Damage Tracking
    public static final int SLOT_OUR_CAT_DAMAGE_LOW = 13;   // Low 10 bits
    public static final int SLOT_OUR_CAT_DAMAGE_HIGH = 14;  // High 10 bits (20-bit total)

    // Cheese Count
    public static final int SLOT_CHEESE_COLLECTED = 15;  // Running total

    // 16-63: Reserved for future
}
```

### RobotPlayer.java - Entry Point with Role Assignment

```java
public class RobotPlayer {
    private static int myRole = -1;  // 0=combat, 1=economy

    public static void run(RobotController rc) throws GameActionException {
        // Assign role once at spawn
        if (myRole == -1) {
            // 70% combat (IDs ending in 0-6)
            // 30% economy (IDs ending in 7-9)
            myRole = (rc.getID() % 10) <= 6 ? 0 : 1;
        }

        while (true) {
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    RatKing.run(rc);
                } else if (myRole == 0) {
                    CombatRat.run(rc);  // 70% of rats
                } else {
                    EconomyRat.run(rc);  // 30% of rats
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}
```

---

## Phase 3: RatKing Implementation

### Objectives
1. Spawn every turn (aggressive)
2. Place 10 cat traps (rounds 15-25)
3. Track cats (360° vision)
4. Broadcast king position
5. Consume 3 cheese/round

### Code Structure

```java
public class RatKing {
    private static int lastGlobalCheese = 2500;
    private static boolean trapsPlaced = false;
    private static int trapCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int globalCheese = rc.getGlobalCheese();

        // CRITICAL: Check emergency FIRST
        if (globalCheese < 50) {
            // Broadcast emergency
            rc.writeSharedArray(Communications.SLOT_EMERGENCY, 999);
            return; // Stop all spending
        }

        // Broadcast king position (for cheese delivery)
        MapLocation myLoc = rc.getLocation();
        rc.writeSharedArray(Communications.SLOT_KING_X, myLoc.x);
        rc.writeSharedArray(Communications.SLOT_KING_Y, myLoc.y);

        // Spawn baby rats (AGGRESSIVE - every turn)
        spawnBabyRat(rc);

        // Track cats (360° vision sees all)
        trackCats(rc);

        // Place cat traps (one-time, rounds 15-25)
        if (!trapsPlaced && round >= 15 && round <= 50 && globalCheese >= 150) {
            placeDefensiveTraps(rc);
        }

        // Economy logging
        if (round % 20 == 0) {
            int income = (globalCheese - lastGlobalCheese + 60) / 2; // +60 = 20 rounds × 3 consumption
            System.out.println("KING:" + round + ":cheese=" + globalCheese + ":income=" + income);
            lastGlobalCheese = globalCheese;
        }
    }

    private static void spawnBabyRat(RobotController rc) throws GameActionException {
        if (rc.getGlobalCheese() < rc.getCurrentRatCost() + 50) {
            return; // Reserve 50 cheese
        }

        // Try all 8 directions at distance=2 (outside 3×3 footprint)
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            MapLocation spawnLoc = rc.getLocation().add(dir).add(dir);

            if (rc.canBuildRat(spawnLoc)) {
                rc.buildRat(spawnLoc);
                System.out.println("SPAWN:" + rc.getRoundNum() + ":cost=" + rc.getCurrentRatCost());
                return;
            }
        }
    }

    private static void trackCats(RobotController rc) throws GameActionException {
        RobotInfo[] cats = rc.senseNearbyRobots(-1, Team.NEUTRAL);

        int catIndex = 0;
        MapLocation closest = null;
        int closestDist = Integer.MAX_VALUE;

        for (RobotInfo cat : cats) {
            if (cat.getType() == UnitType.CAT && catIndex < 4) {
                MapLocation catLoc = cat.getLocation();

                // Write to cat slots
                rc.writeSharedArray(Communications.SLOT_CAT1_X + catIndex*2, catLoc.x);
                rc.writeSharedArray(Communications.SLOT_CAT1_Y + catIndex*2, catLoc.y);

                // Track closest for primary target
                int dist = rc.getLocation().distanceSquaredTo(catLoc);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = catLoc;
                }

                catIndex++;
            }
        }

        // Set primary target (focus fire)
        if (closest != null) {
            rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_X, closest.x);
            rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_Y, closest.y);
        }

        // Clear unused slots
        for (int i = catIndex; i < 4; i++) {
            rc.writeSharedArray(Communications.SLOT_CAT1_X + i*2, 0);
        }
    }

    private static void placeDefensiveTraps(RobotController rc) throws GameActionException {
        MapLocation kingLoc = rc.getLocation();

        // Place 10 traps in defensive ring (distance 3-4 from king)
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            if (trapCount >= 10) break;

            for (int dist = 3; dist <= 4; dist++) {
                MapLocation trapLoc = kingLoc;
                for (int i = 0; i < dist; i++) {
                    trapLoc = trapLoc.add(dir);
                }

                if (rc.canPlaceCatTrap(trapLoc)) {
                    rc.placeCatTrap(trapLoc);
                    trapCount++;
                    System.out.println("TRAP:" + rc.getRoundNum() + ":" + trapLoc + " (#" + trapCount + ")");

                    if (trapCount >= 10) {
                        trapsPlaced = true;
                        System.out.println("DEFENSE:" + rc.getRoundNum() + ":All 10 traps placed");
                        return;
                    }
                    break;
                }
            }
        }

        if (trapCount > 0) {
            trapsPlaced = true; // Don't retry if some placed
        }
    }
}
```

---

## Phase 4: CombatRat Implementation (70% of Army)

### Objectives
1. Attack cats continuously (50% of score)
2. Focus fire on primary target
3. Use vision cone effectively
4. Emergency: Defend king if cat nearby

### Behavior Logic

```java
public class CombatRat {
    public static void run(RobotController rc) throws GameActionException {
        // Check emergency mode
        int emergency = rc.readSharedArray(Communications.SLOT_EMERGENCY);
        if (emergency == 999) {
            // Critical mode - switch to cheese collection
            EconomyRat.run(rc);
            return;
        }

        // PRIMARY OBJECTIVE: Attack cats
        attackPrimaryCat(rc);
    }

    private static void attackPrimaryCat(RobotController rc) throws GameActionException {
        // Get primary target from shared array (focus fire)
        int catX = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_X);
        int catY = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_Y);

        if (catX == 0) {
            // No cat tracked - explore toward map center
            MapLocation center = new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2);
            Movement.moveToward(rc, center);
            return;
        }

        MapLocation targetCat = new MapLocation(catX, catY);
        MapLocation me = rc.getLocation();
        int distance = me.distanceSquaredTo(targetCat);

        // Check if cat in vision
        RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
        RobotInfo visibleCat = null;

        for (RobotInfo robot : nearby) {
            if (robot.getType() == UnitType.CAT) {
                visibleCat = robot;
                break;
            }
        }

        if (visibleCat != null) {
            MapLocation catLoc = visibleCat.getLocation();
            distance = me.distanceSquaredTo(catLoc);

            // Can we attack?
            if (distance <= 2) {
                // Check vision cone
                boolean inVision = Vision.canSee(me, rc.getDirection(), catLoc, UnitType.BABY_RAT);

                if (inVision && rc.canAttack(catLoc)) {
                    rc.attack(catLoc);
                    Debug.status(rc, "ATTACKING CAT!");
                    return;
                }

                // Adjacent but not facing - turn toward cat
                if (!inVision) {
                    Direction toCat = me.directionTo(catLoc);
                    if (rc.canTurn()) {
                        rc.turn(toCat);
                        return;
                    }
                }
            }

            // Move toward cat
            Movement.moveToward(rc, catLoc);
        } else {
            // Cat not in vision - navigate to tracked position
            Movement.moveToward(rc, targetCat);
        }
    }
}
```

---

## Phase 5: EconomyRat Implementation (30% of Army)

### Objectives
1. Collect cheese from mines
2. Deliver to king (within 3 tiles)
3. Sustain king's 3 cheese/round consumption
4. Build cheese reserve

### Behavior Logic

```java
public class EconomyRat {
    private static int cheeseThreshold = 15;  // Deliver when carrying this much

    public static void run(RobotController rc) throws GameActionException {
        int rawCheese = rc.getRawCheese();

        // State: DELIVER if carrying enough cheese
        if (rawCheese >= cheeseThreshold) {
            deliverCheese(rc);
            return;
        }

        // State: COLLECT cheese
        collectCheese(rc);
    }

    private static void collectCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Find nearest cheese
        MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
        MapLocation nearestCheese = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapLocation loc : nearby) {
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);
                if (info.getCheeseAmount() > 0) {
                    int dist = me.distanceSquaredTo(loc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearestCheese = loc;
                    }
                }
            }
        }

        if (nearestCheese != null) {
            // Pick up if possible
            if (rc.canPickUpCheese(nearestCheese)) {
                rc.pickUpCheese(nearestCheese);
                System.out.println("COLLECT:" + rc.getRoundNum() + ":" + rc.getID() + ":cheese=" + rc.getRawCheese());
            } else {
                // Move toward cheese
                Movement.moveToward(rc, nearestCheese);
            }
        } else {
            // No cheese visible - explore
            MapLocation center = new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2);
            Movement.moveToward(rc, center);
        }
    }

    private static void deliverCheese(RobotController rc) throws GameActionException {
        // Get king position from shared array
        int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
        int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);

        if (kingX == 0 && kingY == 0) {
            return; // No king position yet
        }

        MapLocation kingLoc = new MapLocation(kingX, kingY);
        int distance = rc.getLocation().distanceSquaredTo(kingLoc);

        // Can transfer? (dist ≤ 9 = 3 tiles)
        if (distance <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            int amount = rc.getRawCheese();
            rc.transferCheese(kingLoc, amount);
            System.out.println("DELIVER:" + rc.getRoundNum() + ":" + rc.getID() + ":amount=" + amount);
        } else {
            // Move toward king
            Movement.moveToward(rc, kingLoc);
        }
    }
}
```

---

## Phase 6: Movement Utility (CRITICAL)

### Movement.java - Proven Pattern

**From Testing: Must separate turn and move**

```java
public class Movement {
    /**
     * Move toward target with obstacle avoidance.
     * CRITICAL: Returns after turning (cooldown conflict fix).
     */
    public static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction toTarget = me.directionTo(target);
        Direction facing = rc.getDirection();

        // Already facing target?
        if (facing == toTarget) {
            if (rc.canMoveForward()) {
                rc.moveForward();
                return;
            }
            // Blocked - try alternatives
        }

        // Try turning toward target
        if (rc.canTurn()) {
            rc.turn(toTarget);
            return;  // CRITICAL: Move next turn, not same turn
        }

        // Can't turn - try moving in current direction or alternatives
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }

        // Completely blocked - try any direction
        for (Direction dir : DirectionUtil.orderedDirections(toTarget)) {
            if (rc.canMove(dir)) {
                if (rc.getDirection() == dir && rc.canMoveForward()) {
                    rc.moveForward();
                    return;
                } else if (rc.canTurn()) {
                    rc.turn(dir);
                    return;
                }
            }
        }
    }
}
```

---

## Phase 7: Implementation Schedule

### Day 1 (Tonight - 3 hours)

**Hour 1: Setup**
- Create ratbot2/ package
- Copy Vision.java, Constants.java, DirectionUtil.java, Geometry.java
- Copy Debug.java, DebugConfig.java
- Create Communications.java

**Hour 2: Core Implementation**
- Implement RatKing.java (spawning, traps, tracking)
- Implement Movement.java
- Implement RobotPlayer.java (role assignment)

**Hour 3: Rat Behaviors**
- Implement CombatRat.java (focus fire on cats)
- Implement EconomyRat.java (collect & deliver)
- Test locally

### Day 2 (Tomorrow - 2 hours)

**Testing & Tuning:**
- Test spawn rate (40+ rats by round 50?)
- Test trap placement (10 traps by round 25?)
- Test cat damage (>1,000 by round 100?)
- Tune thresholds

**Submission:**
- Create submission.zip
- Upload to scrimmages
- Analyze results

### Day 3-6 (Iteration)

**Based on scrimmage results:**
- Tune combat vs economy ratio
- Adjust trap timing
- Optimize damage race strategy
- Implement backstab decision

---

## Phase 8: Success Metrics

### Minimum Viable Product (Day 1)

- ✅ 30+ rats by round 30
- ✅ 10 traps placed by round 25
- ✅ 70% rats attacking cats
- ✅ 30% rats collecting cheese
- ✅ King survives to round 500

### Competitive Product (Day 3)

- ✅ Cat damage >50% of total
- ✅ Cooperation score >40
- ✅ Beat "Just Woke Up" opponent
- ✅ Top 50% in scrimmages

### Sprint 1 Ready (Day 6)

- ✅ Cat damage >60% of total
- ✅ Cooperation score >50
- ✅ Backstab decision implemented
- ✅ Top 25% in scrimmages

---

## Phase 9: Risk Mitigation

### Known Pitfalls to Avoid

1. **Don't hoard cheese** - 2,500 starting is for spending
2. **Don't limit spawns** - Build army aggressively
3. **Don't make all rats multi-task** - Role specialization works
4. **Don't delay traps** - 1,000 damage is 10% head start
5. **Don't backstab early** - Stay cooperation until round 700+

### Testing Checkpoints

**After each major change:**
- Run `./gradlew test` (111 tests must pass)
- Run local match vs examplefuncsplayer
- Check spawn count at round 30
- Check trap count at round 25
- Check king survival time

---

## Phase 10: What Not to Implement

### Explicitly EXCLUDED (Scope Creep Prevention)

- ❌ King movement/repositioning (waste of early game)
- ❌ Spawn rate limiting (already proven to fail)
- ❌ Economic buffer checks (wrong mentality)
- ❌ Complex pathfinding for rats (simple moveToward() sufficient)
- ❌ Multi-king formation (single king sufficient for Sprint 1)
- ❌ Advanced micro (focus on macro strategy first)

### Save for Later (Post-Sprint 1)

- ⏰ Cheese-enhanced bites (marginal benefit)
- ⏰ Dirt placement (defensive)
- ⏰ Swarm coordination (advanced)
- ⏰ Cat movement prediction (optimization)
- ⏰ Enemy position tracking (tactical)

---

## Execution Command

**When ready:**
```bash
cd scaffold
mkdir -p src/ratbot2/{utils}

# Copy proven utilities
cp src/ratbot/algorithms/Vision.java src/ratbot2/utils/
cp src/ratbot/algorithms/Constants.java src/ratbot2/utils/
cp src/ratbot/algorithms/DirectionUtil.java src/ratbot2/utils/
cp src/ratbot/algorithms/Geometry.java src/ratbot2/utils/
cp src/ratbot/Debug.java src/ratbot2/
cp src/ratbot/DebugConfig.java src/ratbot2/

# Create new files (don't copy old RatKing/BabyRat)
# Implement according to this plan
```

**This plan is READY TO EXECUTE.**

All decisions are made. All mechanics understood. No assumptions remain.

Ready to start?
