# ratbot4 - Comprehensive Implementation Plan

## Lessons Learned (What Went Wrong)

### ratbot2 Failures:
1. **Over-spawning**: 40+ rats → traffic gridlock
2. **Zone territories**: Added complexity, didn't solve clustering
3. **Delivery queues**: Broke deliveries entirely
4. **Distance restrictions**: Trapped rats near king
5. **Traps too close**: Imprisoned our own rats
6. **Complex coordination**: Every addition broke something else

### ratbot3 Issues:
1. **Collectors die in combat**: No defensive behavior
2. **Can't reach dist<=2**: Movement too simple for final approach
3. **Starvation late game**: Collectors die → no deliveries → king starves
4. **Attack king strategy fails**: Can't navigate 3x3 king geometry
5. **Excessive logging**: 13K bytecode wasted
6. **Slow replacement**: 1 per 50 rounds too slow when collectors die

## What Actually Works

### Proven Successful:
1. ✅ **Controlled spawning**: 10 rats (not 30+)
2. ✅ **Simple role assignment**: ID % 2 (attack vs collect)
3. ✅ **Attacking baby rats**: 223 attacks when targeting 1x1 enemies
4. ✅ **Direct move()**: Better than turn+moveForward for exploration
5. ✅ **Stuck recovery after 2 rounds**: Prevents permanent gridlock
6. ✅ **Shared array for king position**: Works without vision dependency
7. ✅ **Enemy position from symmetry**: Accurate initial targeting
8. ✅ **Interval replacement**: 1 per 50 rounds maintains population

### Proven Failures:
1. ❌ **Mass spawning**: Creates traffic jams
2. ❌ **Zone coordination**: Added complexity without benefit
3. ❌ **Distance-reducing movement**: Made deliveries worse
4. ❌ **BFS pathfinding**: Too expensive bytecode-wise
5. ❌ **Attacking only king**: Can't navigate to 3x3 target
6. ❌ **Slow replacement**: Can't keep up with collector losses

## Game Mechanics (Critical Facts)

### Combat:
- **Attack range**: Adjacent only (distanceSquared <= 2)
- **Vision required**: 90° cone, must face target
- **Attack targets**: Baby rats (1x1) easy, kings (3x3) hard
- **Damage**: 10 base (baby rat HP = 100, need 10 hits to kill)
- **Backstab trigger**: Attacking enemy rat triggers backstab mode

### Movement:
- **Forward move**: 10 cooldown
- **Strafe move**: 18 cooldown (penalty!)
- **Turn**: 10 cooldown
- **Cooldown reduction**: 10 per round
- **Stuck if**: Multiple strafes accumulate cooldown >10

### Spawning:
- **Cost**: 10 + 10 × floor(count/4)
- **Range**: distanceSquared <= 4 (max 2 tiles from king center)
- **King is 3×3**: Actual spawn area is small
- **Build cooldown**: 10

### King Mechanics:
- **Size**: 3×3 (occupies 9 tiles)
- **Consumption**: 3 cheese/round
- **HP loss**: 10 HP/round when unfed
- **Vision**: 360°, radius √25 = 5 tiles
- **Can't see far**: Rats beyond 5 tiles invisible to king

### Cheese:
- **Transfer range**: distanceSquared <= 9 (3 tiles)
- **Collection**: Instant if adjacent
- **Carrying penalty**: 1% cooldown per cheese

## ratbot4 Requirements

### Primary Objective: **Survive**
King must not starve. This is the #1 priority.

### Secondary Objective: **Combat Effectiveness**
Attack enemy rats to reduce their economy.

### Constraints:
- **Bytecode limit**: 17,500 per baby rat, 20,000 per king
- **Simple code**: Single file, <600 lines
- **No over-engineering**: Each feature must solve a real problem
- **Testable**: Must work in practice, not just theory

## ratbot4 Architecture

### Population Strategy:

**REVISED RUSH DEFENSE** (Based on economic constraints):

**Phase 1 (Rounds 1-12): DEFENSIVE SPAWN**
- Spawn 12 rats as fast as possible (not 15 - can't afford it)
- Cost: ~200 cheese total
- ALL rats are attackers initially (pure defense)
- Spawn order: [0,1,2,3,4,5,6,7,8,9,10,11]

**Phase 2 (Round 13+): ROLE ASSIGNMENT**
- Rats 0-7: Remain attackers (8 attackers)
- Rats 8-11: Become collectors (4 collectors)
- King writes role assignments to shared array during spawn
- Rats read role from shared array slot (4 + spawnNumber)

**Phase 3 (Round 13+): CONTINUOUS REPLACEMENT**
- Check visible collectors every round
- If visible collectors < 3: spawn collector immediately
- If visible attackers < 6: spawn attacker immediately
- Cap at 20 total spawned
- Keeps economy running even when collectors die

**Economic Model**:
```
Initial cheese: 2,500
Spawn cost (12 rats): ~200
Dirt walls (9 tiles): ~90 cheese (rounds 15-20)
King consumption (75 rounds): 225 cheese
2nd King formation: 50 cheese + 7 rats (round 75)
Cheese-enhanced attacks: ~100 cheese (conditional use)
Total by round 75: ~665 cheese spent
Reserve needed: 1,000 minimum
```

**Rationale**:
- 12 rats affordable while keeping king alive
- Dirt walls provide early defense
- 2nd king creates redundancy (NO ONE ELSE USING THIS!)
- Clear role transition via shared array
- Sustainable cheese budget with 1,000 reserve

### Role Definitions:

**Role Definitions**:

**ATTACKER** (8 rats - spawn numbers 0-7):
- **Job**: Kill enemy rats, defend our territory
- **Behavior**:
  1. **RATNAP**: If enemy HP < 50 and adjacent, grab and throw toward our king
  2. **ATTACK**: Enemy rats in vision (priority: lowest HP first)
  3. **CLEAR TRAPS**: Remove enemy traps blocking paths
  4. **GUARD**: Stay near our king (within 20 tiles)
  5. **HUNT**: Search enemy territory if area secure
- **Combat**:
  - Cheese-enhanced IF: globalCheese > 500 AND enemy HP < 30
  - Otherwise: base attack (10 damage)

**Ratnapping Strategy**:
```java
RobotInfo[] enemies = rc.senseNearbyRobots(2, opponent);
for (RobotInfo enemy : enemies) {
    if (enemy.getHealth() < 50 && rc.canCarryRat(enemy.getLocation())) {
        rc.carryRat(enemy.getLocation());
        // Next rounds: throw toward our king or toward cat
        if (rc.canThrowRat()) {
            // Turn toward our king
            MapLocation kingLoc = read from shared array;
            Direction toKing = me.directionTo(kingLoc);
            if (rc.getDirection() != toKing && rc.canTurn()) {
                rc.turn(toKing);
            } else if (rc.canThrowRat()) {
                rc.throwRat(); // Yeet enemy toward our king to die from fall damage
            }
        }
        return;
    }
}
```

**COLLECTOR** (4 rats minimum - spawn numbers 8-11+):
- **Job**: Feed king, prevent starvation
- **Behavior**:
  1. **EMERGENCY DELIVERY**: If king HP < 100 → deliver immediately
  2. **COMBAT**: Attack enemies if they're adjacent (defend yourself)
  3. **DELIVER**: If carrying >= 10 cheese → deliver
  4. **COLLECT**: Find nearest cheese
- **Combat**: Fight back when attacked (don't run)
- **Replacement**: Spawn new collector when visible < 3

**No FIGHT behavior** - collectors fight, we replace losses aggressively

**Role Assignment System**:
```java
// King assigns during spawn
rc.writeSharedArray(4 + spawnCount, roleType); // 0=ATK, 1=COL

// Rats read on spawn
myRole = rc.readSharedArray(4 + mySpawnNumber);
```

**Spawn order produces**:
- Rats 0-7: Attackers (spawn rounds 1-8)
- Rats 8-11: Collectors (spawn rounds 9-12)
- Clear, deterministic, no contradictions

### Pathfinding Strategy:

**Use Bug2 for all navigation** (not BFS):
- **Cost**: 300-500 bytecode per call (affordable)
- **When**: Stuck > 3 rounds OR distance > 30 tiles
- **Fallback**: Greedy movement for short distances

**Greedy for**:
- Short distances (< 30 tiles)
- First 3 rounds of any journey
- When Bug2 fails

**Implementation**: Copy Bug2 from ratbot/algorithms/Pathfinding.java

### Defensive Behavior (Critical):

**ALL RATS in rounds 1-30**:
- Fight aggressively (everyone is attacker)
- Use cheese-enhanced attacks (8 cheese each)
- Focus fire: attack wounded enemies first
- Stay near our king (within 20 tiles)

**COLLECTOR BEHAVIOR**:

**Priority 1: EMERGENCY KING DELIVERY**
```java
int kingHP = rc.readSharedArray(29);
if (kingHP < 100 && rc.getRawCheese() > 0) {
    MapLocation kingLoc = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
    if (distToKing <= 9 && rc.canTransferCheese(kingLoc, cheese)) {
        rc.transferCheese(kingLoc, cheese);
        return;
    }
    moveWithBug2(kingLoc);
    return;
}
```

**Priority 2: DEFEND YOURSELF**
```java
RobotInfo[] enemies = rc.senseNearbyRobots(2, opponent);
for (RobotInfo enemy : enemies) {
    if (rc.canAttack(enemy.getLocation())) {
        rc.attack(enemy.getLocation());
        return;
    }
}
```

**Priority 3: CLEAR TRAPS**
```java
// Remove enemy traps blocking path to cheese or king
Direction facing = rc.getDirection();
MapLocation ahead = rc.adjacentLocation(facing);
if (rc.canRemoveRatTrap(ahead)) {
    rc.removeRatTrap(ahead); // Clear path
    return;
}
if (rc.canRemoveCatTrap(ahead)) {
    rc.removeCatTrap(ahead); // Clear path
    return;
}
```

**Priority 4: DELIVER**
```java
if (rc.getRawCheese() >= 10) {
    deliver to king;
}
```

**Priority 5: COLLECT**
```java
Find nearest cheese;
Move toward it;
Pick up if adjacent;
```

**Collectors fight back and clear traps** - aggressive replacement when they die

### Combat Behavior (CRITICAL - Must Match Enemy):

**Cheese-Enhanced Attacks**:
- Formula: `damage = 10 + ceil(log₂(cheese))`
- 8 cheese = 13 damage (8 hits vs 10)
- 16 cheese = 14 damage (8 hits)
- 32 cheese = 15 damage (7 hits)

**Attack Strategy (CONDITIONAL - preserve cheese)**:
```java
// Check if we should enhance
boolean shouldEnhance = false;
if (rc.getGlobalCheese() > 500) { // Surplus available
    if (enemy.getHealth() < 30) { // Killing blow
        shouldEnhance = true;
    } else if (distToOurKing < 100) { // Defending king area
        shouldEnhance = true;
    }
}

// Attack with or without cheese
if (rc.canAttack(enemyLoc)) {
    // Turn to face enemy first (required for vision cone!)
    Direction toEnemy = me.directionTo(enemyLoc);
    if (rc.getDirection() != toEnemy && rc.canTurn()) {
        rc.turn(toEnemy);
        return; // Attack next round
    }

    if (shouldEnhance && rc.getRawCheese() >= 8) {
        rc.attack(enemyLoc, 8); // 13 damage
    } else {
        rc.attack(enemyLoc); // 10 damage
    }
}
```

**Target Priority**:
1. Wounded enemies (HP < 50) - finish them off
2. Enemies near our king (dist < 100) - defend king
3. Enemies near our collectors - protect economy
4. Any enemy rats - reduce their numbers

**Focus Fire**: Attack lowest HP enemy when multiple visible

**King Attack Fix** (Using multi-part distance):
```java
// When attacking enemy king, check ALL king tiles (3x3)
if (enemy.getType() == UnitType.RAT_KING) {
    MapLocation kingCenter = enemy.getLocation();

    // Try attacking each of the 9 king tiles
    for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = new MapLocation(kingCenter.x + dx, kingCenter.y + dy);
            if (rc.canAttack(tile)) {
                // Turn to face tile if needed
                Direction toTile = me.directionTo(tile);
                if (rc.getDirection() != toTile && rc.canTurn()) {
                    rc.turn(toTile);
                    return;
                }
                // Attack the tile
                rc.attack(tile);
                return;
            }
        }
    }
}
```

**Trap Removal** (Clear paths):
```java
// Before collecting or delivering, remove enemy traps in path
MapLocation nextLoc = me.add(facingDirection);
if (rc.canRemoveRatTrap(nextLoc)) {
    rc.removeRatTrap(nextLoc); // Clear enemy trap
    return;
}
if (rc.canRemoveCatTrap(nextLoc)) {
    rc.removeCatTrap(nextLoc); // Clear enemy trap
    return;
}
```

### Communication Protocol:

**Shared array slots**:
- 0-1: Our king position (X, Y)
- 2-3: Enemy king position (X, Y)
- 4-23: Role assignments (20 rats, 0=ATK 1=COL)
- 24: Enemy position timestamp (round % 1000)
- 25-26: Last enemy rat sighting (X, Y)
- 27: Enemy rat timestamp
- 28: Danger signal (1 = enemies near king)
- 29: King HP (for emergency delivery detection)
- 30-39: Trap locations (up to 5 traps, 2 slots each)
- **40-59: Cheese mine locations** (10 mines, 2 slots each X,Y)
- **60-61: 2nd king position** (if formed)
- **62: 2nd king HP**
- **63: Kill count** (combat statistics)

**King responsibilities**:
- Write own position (slots 0-1)
- Calculate enemy king position (slots 2-3, try rotation first, verify on sight)
- Write own HP (slot 29) for emergency delivery detection
- Scan for enemies, write positions (slots 25-27)
- Set danger signal (slot 28) if enemies within 20 tiles
- Write trap locations (slots 30-39) when placed
- Write role assignments (slots 4-23) during spawn
- **NEW: Build dirt walls** (rounds 15-20) - 3x3 perimeter around king
- **NEW: Track cheese mines** (slots 40-59) - coordinate collection
- **NEW: Form 2nd king** (round 75+) - if 10+ rats alive and cheese > 1000

## Technical Specifications

### File Structure:
```
ratbot4/RobotPlayer.java (~600 lines)
├── run() - main loop
├── runKing() - spawn, trap, track enemies
├── runBabyRat() - role dispatch
├── runAttacker() - hunt enemies, ratnap, clear traps
├── runCollector() - feed king, defend, clear traps
├── move(target) - greedy movement
├── moveWithBug2(target) - obstacle navigation with Bug2
├── bug2(current, target, canMove) - Bug2 pathfinding
├── ratnap() - grab and throw enemy rats
└── clearTraps() - remove enemy traps from path
```

### Spawning Logic:
```java
// PHASE 1: Spawn 12 rats (affordable with cheese budget)
if (spawnCount < 12) {
    int cost = rc.getCurrentRatCost();
    if (globalCheese > cost + 100) { // Keep reserve
        // Spawn and assign role
        int roleType = (spawnCount < 8) ? 0 : 1; // 0=ATK, 1=COL

        spawn rat at distance 2;

        // Write role to shared array
        rc.writeSharedArray(4 + spawnCount, roleType);

        spawnCount++;
    }
}

// PHASE 2: Continuous replacement (after initial 12)
if (spawnCount >= 12 && spawnCount < 20) {
    // Count visible rats by role
    RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
    int visibleAttackers = 0;
    int visibleCollectors = 0;
    for (RobotInfo r : team) {
        if (r.getType() == UnitType.BABY_RAT) {
            int rSpawnNum = estimate from ID; // (ID - 10000) % 20
            int rRole = rc.readSharedArray(4 + rSpawnNum);
            if (rRole == 0) visibleAttackers++;
            else visibleCollectors++;
        }
    }

    // Replace immediately if below minimum
    if (visibleCollectors < 3) {
        int cost = rc.getCurrentRatCost();
        if (globalCheese > cost + 200) { // Higher reserve for replacement
            spawn rat;
            rc.writeSharedArray(4 + spawnCount, 1); // Spawn collector
            spawnCount++;
        }
    } else if (visibleAttackers < 6) {
        int cost = rc.getCurrentRatCost();
        if (globalCheese > cost + 200) {
            spawn rat;
            rc.writeSharedArray(4 + spawnCount, 0); // Spawn attacker
            spawnCount++;
        }
    }
}

// Role assignment (for baby rats):
// Read from shared array (king wrote during spawn)
private static int myRole = -1;
private static int mySpawnNumber = -1;

if (myRole == -1) {
    // Determine spawn number from visible count + timing
    // Or king writes rat ID to role map
    mySpawnNumber = (rc.getID() - 10000) % 20; // IDs start at 10000+
    myRole = rc.readSharedArray(4 + mySpawnNumber);
}
```

### Movement Decision Tree:
```
1. Check if stuck > 3 rounds → Use Bug2
2. Check if distance > 30 tiles → Use Bug2
3. Else → Use greedy movement
```

### Bug2 Integration:
Copy from ratbot/algorithms/Pathfinding.java:
- bug2() method
- CanMoveFunction interface
- State tracking (bugTarget, bugTracing, etc.)
- ~150 lines of code

Estimated cost: 300-500 bytecode per call
Usage: Only when needed (stuck or long distance)

## Performance Budget

**Per baby rat per round**:
- Sensing: 1,000 bytecode (sense enemies, cheese)
- Role logic: 500 bytecode (if/else dispatch)
- Movement:
  - Greedy: 200 bytecode
  - Bug2: 500 bytecode (when needed)
- Attack/Collect: 500 bytecode
- **Total**: 2,200-2,700 bytecode (13-16% of budget)

**Remaining**: 14,800 bytecode (safety margin)

## Critical Success Factors

### Must Have:
1. **Collectors survive**: FIGHT from enemies, don't die
2. **Regular deliveries**: 50+ per match minimum
3. **King survival**: Never reach cheese < 50
4. **Enemy combat**: Kill 5+ enemy rats per match

### Nice to Have:
1. Enemy king damage (not required for survival)
2. 100+ deliveries per match
3. Coordinated attacks
4. Advanced tactics

## Implementation Order

### Phase 1: Core (No pathfinding yet)
1. Spawn 8 rats with 3 roles
2. Defender: attack enemies near our king
3. Attacker: attack enemies anywhere
4. Collector: collect cheese, deliver, FIGHT

**Test**: Can collectors survive? Do defenders help?

### Phase 2: Add Bug2
1. Integrate Bug2 from ratbot/algorithms
2. Use in collect() when stuck > 3
3. Use in deliver() when stuck > 3
4. Use in assault when stuck > 3

**Test**: Do rats navigate obstacles better?

### Phase 3: Refine
1. Tune FIGHT distance
2. Tune spawn ratios (maybe 1 DEF, 4 ATK, 3 COL)
3. Optimize Bug2 usage conditions

## Critical Research Findings

### Why We Lose by Round 30:

1. **Enemy spawns 15+ rats quickly** (rounds 1-15)
2. **Enemy uses cheese-enhanced attacks** (13-16 damage)
3. **We spawn only 10 rats slowly** (rounds 1-10)
4. **We use basic attacks** (10 damage, no cheese)
5. **Result**: Outnumbered + outgunned = all rats dead by round 30

### Damage Math:
- Baby rat HP: 100
- Base attack (0 cheese): 10 damage → 10 hits to kill
- Enhanced (8 cheese): 13 damage → 8 hits to kill
- Enhanced (32 cheese): 15 damage → 7 hits to kill

**Enemy killing us faster because cheese-enhanced attacks!**

### King Formation (Not Immediate Priority):
- Requires 7+ rats in 3x3
- HP = sum of rats (max 500)
- Costs 50 cheese + 7 rats
- Use LATER for defense, not early rush

## Answers to Implementation Questions

1. **Spawn timing**: As fast as possible (15 rats by round 15)
2. **All attackers initially**: Fight during rush (rounds 1-30)
3. **Cheese-enhanced attacks**: ALWAYS use 8 cheese (13 damage)
4. **Bug2 for navigation**: When stuck > 3 rounds
5. **Transition to economy**: Round 31+ (after rush survival)

## Validation Criteria

**Before committing**:
- [ ] Code < 600 lines
- [ ] Zero logging (no System.out.println)
- [ ] Bytecode estimate < 5,000 per round average
- [ ] 8 attackers + 4 collectors verified after spawn
- [ ] FIGHT behavior implemented
- [ ] Bug2 integrated with instance state (not static)
- [ ] Emergency delivery protocol implemented
- [ ] Trap avoidance in FIGHT behavior

**Critical Tests** (Must pass):
- [ ] King never starves: cheese > 0 for all rounds
- [ ] Role assignment: exactly 8 ATK + 4 COL after spawn complete
- [ ] Cheese sustainability: globalCheese > 500 at round 50
- [ ] Emergency delivery: king receives cheese when HP < 100
- [ ] Collector survival: collector deaths < 3 in first 200 rounds

**Performance Tests**:
- [ ] vs ratbot3: More deliveries, fewer collector deaths
- [ ] Mirror match: King survives longer (measures economy)
- [ ] vs quanticbot: Survive past round 30 (defeat early rush)

**Success = King survives by getting deliveries**
Deliveries > 40 per match AND king cheese never < 50

## Ready to Proceed?

I will NOT write code until you confirm:
1. Role split (2 DEF, 3 ATK, 3 COL) - agree?
2. FIGHT behavior for collectors - agree?
3. Bug2 for all roles - agree?
4. Anything else needed in plan?

Please review this plan and tell me what to change before I start implementing ratbot4.
