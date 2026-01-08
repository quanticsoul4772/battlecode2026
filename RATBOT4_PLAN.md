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
6. **No flee behavior**: Rats don't escape when low HP

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
6. ❌ **No HP awareness**: Rats don't flee when dying

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

**EARLY RUSH DEFENSE** (Critical - enemy kills us by round 30!):

**Phase 1 (Rounds 1-15): MASS SPAWN FOR DEFENSE**
- Spawn as fast as possible: 15 rats
- ALL rats are attackers initially (pure defense)
- Use cheese-enhanced attacks (8 cheese per attack = 13 damage)
- Goal: Survive enemy rush, kill their attackers

**Phase 2 (Rounds 16-30): ECONOMY TRANSITION**
- Convert 5 attackers to collectors
- Keep 10 attackers for ongoing defense
- Start delivering cheese to sustain king

**Phase 3 (Rounds 31+): SUSTAINED OPERATION**
- 10 attackers (defend + hunt)
- 5 collectors (economy)
- Replacement: 1 per 50 rounds

**Rationale**:
- Enemy rushes with 15+ rats by round 15
- We need equal numbers to defend
- Cheese-enhanced attacks to match their damage
- Transition to economy AFTER surviving rush

### Role Definitions:

**PHASE 1 (Rounds 1-30): ALL ATTACKERS**

All 15 rats are attackers during enemy rush:
- **Job**: Kill enemy attackers, protect king
- **Behavior**:
  1. Attack any enemy rat in vision (cheese-enhanced!)
  2. Guard area around our king (within 15 tiles)
  3. Chase enemies toward our king
- **Combat**: Use 8 cheese per attack (13 damage)
- **Goal**: Survive rush, kill enemy attackers

**PHASE 2 (Rounds 31+): ROLE SPLIT**

After surviving rush, assign roles:

**ATTACKER** (10 rats):
- **Job**: Continuous enemy harassment
- **Behavior**:
  1. Attack enemy rats (cheese-enhanced)
  2. Hunt in enemy territory
  3. Protect our collectors
- **Combat**: 8 cheese per attack
- **Range**: Anywhere

**COLLECTOR** (5 rats):
- **Job**: Feed king, survive
- **Behavior**:
  1. FLEE if: enemy within 15 tiles OR HP < 50
  2. Deliver when: carrying >= 10 cheese AND safe
  3. Collect: nearest cheese
- **Combat**: FLEE, don't fight (preserve economy)
- **Range**: Anywhere with cheese

**Role assignment**:
- First 10 spawned = attackers (keep fighting)
- Last 5 spawned = collectors (switch to economy)

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

**COLLECTORS in rounds 31+**:

**FLEE conditions**:
- Enemy within 15 tiles
- HP < 50 (half health)
- Already fleeing (don't stop mid-flee)

**FLEE behavior**:
```java
// Check for enemies
RobotInfo[] enemies = rc.senseNearbyRobots(225, opponent); // 15 tiles
RobotInfo closest = find closest enemy;

if (closest != null && distance < 225) {
    // FLEE toward our king (safest direction)
    MapLocation kingLoc = read from shared array;
    move toward king; // Run to safety!
    return; // Don't collect, don't deliver - just RUN
}

if (rc.getHealth() < 50) {
    // Low HP - flee to king
    move toward king;
    return;
}
```

**Never fight as collector** - only flee and survive

### Combat Behavior (CRITICAL - Must Match Enemy):

**Cheese-Enhanced Attacks**:
- Formula: `damage = 10 + ceil(log₂(cheese))`
- 8 cheese = 13 damage (8 hits to kill vs 10)
- 16 cheese = 14 damage (8 hits to kill)
- 32 cheese = 15 damage (7 hits to kill)

**Attack strategy**:
```java
// ALWAYS use cheese-enhanced attacks
if (rc.canAttack(enemyLoc)) {
    int cheeseToSpend = Math.min(8, rc.getRawCheese()); // Spend 8 if available
    if (cheeseToSpend > 0) {
        rc.attack(enemyLoc, cheeseToSpend); // 13 damage
    } else {
        rc.attack(enemyLoc); // 10 damage fallback
    }
}
```

**Target priority**:
1. Enemy rats attacking our king (highest threat)
2. Enemy rats near our collectors (protect economy)
3. Enemy rats anywhere (reduce their numbers)
4. Enemy king (only if alone and vulnerable)

**Coordinate attacks**: When 2+ rats attack same target, focus fire to kill faster

### Communication Protocol:

**Shared array slots**:
- 0-1: Our king position (X, Y)
- 2-3: Enemy king position (X, Y)
- 4: Enemy position timestamp
- 5-6: Last enemy rat sighting (X, Y)
- 7: Enemy rat timestamp
- 8: Danger signal (1 = enemies near our king)

**King responsibilities**:
- Write own position
- Calculate enemy king position (symmetry)
- Scan for enemies, write positions
- Set danger signal if enemies within 20 tiles

## Technical Specifications

### File Structure:
```
ratbot4/RobotPlayer.java (~500 lines)
├── run() - main loop
├── runKing() - spawn, trap, track enemies
├── runBabyRat() - role dispatch
├── runDefender() - protect king area
├── runAttacker() - hunt enemies
├── runCollector() - feed king, flee danger
├── move(target) - greedy movement
├── moveWithBug2(target) - obstacle navigation
└── bug2() - Bug2 pathfinding
```

### Spawning Logic:
```java
// PHASE 1: Spawn 15 rats ASAP (rounds 1-15) for rush defense
if (spawnCount < 15 && round <= 30) {
    int cost = rc.getCurrentRatCost();
    if (globalCheese > cost + 50) { // Keep small reserve
        spawn rat; // All are attackers initially
        spawnCount++;
    }
}

// PHASE 2: Replacements (after round 30)
if (round > 30 && round % 50 == 0 && spawnCount < 25) {
    spawn rat; // Role depends on losses
}

// Role assignment (for rats):
if (round <= 30) {
    myRole = ATTACKER; // Everyone fights during rush
} else {
    myRole = (spawnNumber < 10) ? ATTACKER : COLLECTOR;
}
```

### Movement Decision Tree:
```
1. Check if enemy nearby → FLEE (collectors only)
2. Check if stuck > 3 rounds → Use Bug2
3. Check if distance > 30 tiles → Use Bug2
4. Else → Use greedy movement
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
1. **Collectors survive**: FLEE from enemies, don't die
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
4. Collector: collect cheese, deliver, FLEE

**Test**: Can collectors survive? Do defenders help?

### Phase 2: Add Bug2
1. Integrate Bug2 from ratbot/algorithms
2. Use in collect() when stuck > 3
3. Use in deliver() when stuck > 3
4. Use in assault when stuck > 3

**Test**: Do rats navigate obstacles better?

### Phase 3: Refine
1. Tune FLEE distance
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
- [ ] No logging (or minimal)
- [ ] Bytecode estimate < 5,000 per round
- [ ] Roles clearly defined
- [ ] FLEE behavior implemented
- [ ] Bug2 integrated

**Testing checklist**:
- [ ] vs ratbot3: Do defenders help?
- [ ] vs ratbot3: Do collectors survive better?
- [ ] Mirror match: Which king starves (measure economy)
- [ ] vs examplefuncsplayer: Basic functionality

**Success = King survives by getting deliveries, not by round count**

## Ready to Proceed?

I will NOT write code until you confirm:
1. Role split (2 DEF, 3 ATK, 3 COL) - agree?
2. FLEE behavior for collectors - agree?
3. Bug2 for all roles - agree?
4. Anything else needed in plan?

Please review this plan and tell me what to change before I start implementing ratbot4.
