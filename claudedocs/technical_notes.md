# Battlecode 2026 - Technical Implementation Notes

## Navigation System Design

### Challenge: Directional Movement
Unlike 2025's omnidirectional movement, 2026 has:
- Facing direction affects vision
- Turning costs cooldown
- Forward movement vs strafing cost difference

### Proposed Solution: Directional Bug2

**State Variables**:
```java
private static MapLocation bugTarget;
private static Direction targetFacing; // NEW
private static boolean bugTracing;
private static Direction bugTracingDir;
private static MapLocation bugStartLoc;
private static int bugStartDist;
private static boolean bugRotateRight;
private static int bugTurns;
```

**Algorithm**:
1. Compute direction to target
2. Compute optimal facing (minimize turns)
3. If can move forward: do it
4. Else: Enter tracing mode
5. Consider turn costs in pathfinding

**Optimization**:
- Pre-compute facing changes needed
- Lookahead: Is turning now cheaper than later?
- Cache direction calculations

### Vision Management

**Problem**: 90 degree cone = blind spots

**Solutions**:
1. **Scanning**: Periodic rotation to scan surroundings
2. **Formation**: Multi-rat coverage patterns
3. **Prediction**: Track enemy positions when out of view
4. **Communication**: Share sightings via array

**Implementation**:
```java
// Every N rounds, do a full scan
if (round % 20 == 0 && !inCombat) {
    // Rotate 360 degrees over 4 turns
    turn(currentFacing.rotateLeft().rotateLeft());
}
```

## Cheese Collection Architecture

### Collector Pattern

**Roles**:
- **Collectors**: Baby rats assigned to specific mines
- **Runners**: Shuttle cheese from collectors to kings
- **Guards**: Protect collectors from cats/enemies

**Flow**:
1. Scout phase: Find all cheese mines (early game)
2. Assignment: Assign collectors to nearest mines
3. Collection: Patrol mine area, pick up cheese
4. Transfer: When carrying >20 cheese, return to king
5. Repeat

**Optimization**:
- Minimize travel distance (assign to nearest mine)
- Avoid overcrowding (max 2-3 rats per mine?)
- Balance collection vs combat needs

### Alternative: Opportunistic Collection

**Flow**:
- All rats collect cheese opportunistically
- Transfer when passing near king
- No fixed assignments

**Pros**: Flexible, adaptive
**Cons**: May miss cheese, inefficient routes

## King Management System

### Single King Strategy

**Pros**:
- Low consumption (3 cheese/round = 6,000 over game)
- Simple feeding logic
- Easy to protect

**Cons**:
- Single spawn point (production bottleneck)
- Single point of failure
- Lower spawn rate

**Viability**: Early game, low cheese income

### Dual King Strategy

**Pros**:
- 2x spawn locations (higher unit production)
- Distributed risk
- Better map coverage

**Cons**:
- 6 cheese/round consumption (12,000 over game)
- Need consistent income
- Harder to feed both

**Trigger**: Create 2nd king when:
- Cheese income >8/round sustained
- Have 200+ global cheese buffer
- Safe location for formation

### Multi-King (3-5) Strategy

**Pros**:
- Maximum production
- Extreme redundancy
- Map domination

**Cons**:
- 9-15 cheese/round consumption (18,000-30,000 over game!)
- Very hard to sustain
- Coordination complexity

**Viability**: Late game with high income, or backstab mode (50% king weight)

## Communication Protocol Design

### Shared Array Schema (64 slots, 0-1023)

**Slot Allocation**:

```
Slots 0-3: King Status
  [0]: King1 HP (0-500) + alive bit
  [1]: King1 cheese need urgency (0-10 scale)
  [2]: King2 HP + alive bit (if exists)
  [3]: King2 cheese need urgency

Slots 4-11: Cheese Mine Locations (8 slots for 4 mines)
  [4-5]: Mine1 X,Y coordinates
  [6-7]: Mine2 X,Y coordinates
  [8-9]: Mine3 X,Y coordinates
  [10-11]: Mine4 X,Y coordinates

Slots 12-19: Cat Tracking (up to 4 cats)
  [12]: Cat1 X coordinate
  [13]: Cat1 Y coordinate
  [14]: Cat2 X coordinate
  [15]: Cat2 Y coordinate
  ... etc

Slots 20-27: Enemy Rat King Sightings
  [20-21]: Enemy King1 last seen X,Y
  [22]: Enemy King1 last seen round
  [23-24]: Enemy King2 last seen X,Y
  [25]: Enemy King2 last seen round

Slots 28-35: Tactical Flags
  [28]: Backstab decision (0=coop, 1=backstab)
  [29]: Cat damage done by us
  [30]: Cat damage done by enemy (estimated)
  [31]: Cheese transferred count

Slots 36-63: Reserved for expansion
```

**Encoding Helpers**:
```java
// Encode location to 10 bits
int encodeLocation(MapLocation loc) {
    return (loc.x << 5) | loc.y; // 5 bits X, 5 bits Y
}

// Decode location from 10 bits
MapLocation decodeLocation(int encoded) {
    int x = (encoded >> 5) & 0x1F;
    int y = encoded & 0x1F;
    return new MapLocation(x, y);
}
```

### Squeak Usage Policy

**When to Squeak**:
- King under attack (emergency)
- Cheese mine discovered (first time only)
- Enemy king sighted (first time only)

**When NOT to Squeak**:
- Routine coordination
- General movement
- Non-critical updates

**Reason**: Cats hear squeaks and will chase!

## Combat Micro System

### Threat Assessment

**Threat Scoring**:
```java
int scoreThreat(RobotInfo enemy) {
    int score = 0;

    // Type priority
    if (enemy.type == CAT) score += 1000;
    else if (enemy.type == RAT_KING) score += 500;
    else score += 100; // Baby rat

    // Distance (closer = more urgent)
    int dist = myLoc.distanceSquaredTo(enemy.location);
    score += (30 - dist) * 10;

    // Health (low HP = easier kill)
    score += (100 - enemy.health);

    // Facing (if facing away = vulnerable)
    if (!canSeeUs(enemy)) score += 200;

    return score;
}
```

### Attack Decision

**Target Selection**:
1. Prioritize targets facing away (cannot see us)
2. Prioritize low HP targets (finish kills)
3. Prioritize cats in cooperation mode
4. Prioritize enemy kings in backstab mode

**Cheese Investment**:
- Base bite (0 cheese): 10 damage
- 2 cheese: 10 + ceil(log 2) = 11 damage (0.5 dmg/cheese)
- 10 cheese: 10 + ceil(log 10) = 14 damage (0.4 dmg/cheese)
- 100 cheese: 10 + ceil(log 100) = 17 damage (0.07 dmg/cheese)

**Analysis**: Cheese investment has diminishing returns!
- Only worth it for finishing kills?
- Or when cheese is abundant?

### Retreat Logic

**Retreat Triggers**:
- HP <30 and cat nearby
- HP <50 and outnumbered
- Carrying >50 cheese (protect investment)

**Retreat Behavior**:
- Face away from threat
- Move forward (cheapest movement)
- Head toward nearest king
- Transfer cheese if possible

## Ratnapping Implementation

### Grab Logic

```java
boolean canGrabRat(RobotInfo target) {
    if (!rc.canSenseRobotAtLocation(target.location)) return false;
    if (!target.type.isBabyRatType()) return false;
    if (target.location.distanceSquaredTo(myLoc) > 2) return false;

    // Check conditions
    boolean facingAway = !canTheySeeMeDirection(target);
    boolean lessHealth = target.health < rc.getHealth();
    boolean ally = target.team == rc.getTeam();

    return facingAway || lessHealth || ally;
}
```

### Throw Calculation

**Throw Trajectory**:
- Direction: Current facing
- Range: 2 tiles/turn × 4 turns = 8 tiles max
- Early hit: Stops on obstacle

**Damage Calculation**:
```java
// If throw at max range (4 turns airborne)
int damage = 5 * (4 - 4) = 0 damage (just stun)

// If hit after 0 turns (immediate obstacle)
int damage = 5 * (4 - 0) = 20 damage + 30 stun

// If hit after 2 turns
int damage = 5 * (4 - 2) = 10 damage + 30 stun
```

**Strategic Use**:
- Throw at close range (higher damage)
- Sacrifice to cat (enemy rat → stun cat 2 turns)
- Emergency extraction (throw ally away from danger)

## State Machine Architecture

### Baby Rat States

```
EXPLORE → (cheese seen) → COLLECT
COLLECT → (carrying >20) → DELIVER
DELIVER → (transferred) → EXPLORE

EXPLORE → (cat seen) → FLEE
COLLECT → (cat seen) → FLEE
FLEE → (safe) → EXPLORE

EXPLORE → (enemy seen) → COMBAT
COMBAT → (enemy dead/fled) → EXPLORE
COMBAT → (low HP) → FLEE
```

### Rat King States

```
SPAWN → (cannot afford) → WAIT
WAIT → (can afford) → SPAWN

SPAWN → (cat nearby) → DEFEND
DEFEND → (cat gone) → SPAWN
```

## Global State Singleton (G.java pattern)

```java
public class G {
    // Robot controller
    public static RobotController rc;

    // Current state
    public static MapLocation me;
    public static int round;
    public static Team team;
    public static Team opponent;
    public static UnitType type;
    public static int id;
    public static Direction facing; // NEW
    public static int rawCheese;     // NEW
    public static int globalCheese;  // NEW

    // Map info (cached)
    public static int mapWidth;
    public static int mapHeight;
    public static MapLocation mapCenter;

    // Sensing cache
    private static RobotInfo[] allies;
    private static RobotInfo[] enemies;
    private static RobotInfo[] cats; // NEW

    // Vision utilities
    public static boolean inVisionCone(MapLocation target) {
        // Compute if target is in 90 degree cone
        Direction toTarget = me.directionTo(target);
        return Math.abs(facing.directionTo(toTarget)) <= 1;
    }
}
```

## Cheese Mine Tracking (POI.java pattern)

```java
public class POI {
    // Cheese mine storage
    private static final int MAX_MINES = 32;
    private static int[] mineX = new int[MAX_MINES];
    private static int[] mineY = new int[MAX_MINES];
    private static int[] lastSpawn = new int[MAX_MINES]; // Round of last spawn
    private static int mineCount = 0;

    // Cat tracking
    private static int[] catX = new int[10];
    private static int[] catY = new int[10];
    private static int catCount = 0;

    // King tracking
    private static int[] allyKingX = new int[5];
    private static int[] allyKingY = new int[5];
    private static int allyKingCount = 0;

    public static MapLocation findNearestMine() {
        // Find closest unexploited mine
    }

    public static MapLocation findNearestKing() {
        // For cheese delivery
    }
}
```

## Visibility Logging System (from 2025)

### Log Categories

**STATE**: Per-robot state tracking
```
STATE:{round}:{type}:{id}:pos={x,y}:facing={dir}:hp={hp}:rawCheese={n}:mode={mode}
```

**ECONOMY**: Team-wide economics
```
ECONOMY:{round}:globalCheese={n}:cheeseIncome={n}:kings={n}:rats={n}
```

**SPAWN**: Unit creation events
```
SPAWN:{round}:KING:{id}:pos={x,y}:cost={n}:totalRats={n}
```

**COMBAT**: Attack events
```
COMBAT:{round}:{type}:{id}:target={x,y}:damage={n}:cheeseSpent={n}
```

**CAT_TRACKING**: Cat monitoring
```
CAT:{round}:id={id}:pos={x,y}:mode={mode}:hp={hp}
```

**BACKSTAB**: Game state transitions
```
BACKSTAB:{round}:triggered_by={team}:our_damage={n}:enemy_damage={n}
```

## Test-Driven Development Plan

### Unit Tests Needed

**Navigation Tests**:
- `testDirectionalMovement()`: Verify facing affects movement
- `testTurningCost()`: Verify cooldown on turns
- `testVisionCone()`: Verify 90 degree sensing
- `testBug2Directional()`: Pathfinding with facing

**Cheese Tests**:
- `testCheeseCollection()`: Pick up + transfer
- `testCarryingPenalty()`: Verify 0.01 multiplier
- `testCheeseSpending()`: Raw before global

**Combat Tests**:
- `testBiteDamage()`: Base 10 damage
- `testCheeseEnhancement()`: Log scaling
- `testVisionConeAttack()`: Must see target

**Ratnapping Tests**:
- `testGrabConditions()`: Facing, health, team checks
- `testCarryingMechanics()`: Stun, immunity
- `testThrowDamage()`: 5 * (4 - airtime)
- `testAutoDropSwap()`: 10 round limit, swap on blocked

**King Tests**:
- `testKingConsumption()`: 3 cheese/round
- `testStarvation()`: Lose 10 HP when no cheese
- `testSpawnCost()`: 10 + 10 * floor(n/4)
- `testKingFormation()`: 7 rats, 50 cheese

## Bytecode Profiling

**From 2025 Experience**:
```java
public static void profileStart() {
    profileStartBytecode = Clock.getBytecodeNum();
}

public static void profileEnd(String section) {
    int cost = Clock.getBytecodeNum() - profileStartBytecode;
    if (round % 20 == 0) {
        System.out.println("PROFILE:" + round + ":" + id + ":" + section + ":" + cost);
    }
}
```

**Critical Sections to Profile**:
- Vision cone sensing
- Pathfinding with facing
- Communication encoding/decoding
- Ratnapping state management

**Target Budgets**:
- Baby Rat: <10,000/turn average (70% of limit)
- Rat King: <15,000/turn average (75% of limit)

## Data Structures

### Avoid Allocations

**Bad** (allocates every loop):
```java
for (int i = 0; i < enemies.length; i++) {
    Direction[] dirs = new Direction[8]; // EXPENSIVE
}
```

**Good** (static allocation):
```java
private static final Direction[] DIRS = {
    Direction.NORTH, Direction.NORTHEAST, // ...
};
```

### Cache Sensing Results

**Bad** (re-senses every call):
```java
void attackCat() {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
    // ... find cat
}

void avoidCat() {
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);
    // ... find cat AGAIN
}
```

**Good** (sense once per turn):
```java
// In RobotPlayer main loop
RobotInfo[] allEnemies = rc.senseNearbyRobots(-1, opponent);

// Pass to functions
attackCat(allEnemies);
avoidCat(allEnemies);
```

## Symmetry Detection

### Algorithm

```java
public static boolean[] detectSymmetry() {
    boolean[] sym = new boolean[3]; // [horizontal, vertical, rotational]

    // Check known features (walls, cheese mines)
    for (MapLocation feature : knownFeatures) {
        // Check horizontal symmetry
        MapLocation hMirror = new MapLocation(feature.x, mapHeight - 1 - feature.y);
        if (sensePassability(hMirror) != sensePassability(feature)) {
            sym[0] = false;
        }

        // Check vertical symmetry
        MapLocation vMirror = new MapLocation(mapWidth - 1 - feature.x, feature.y);
        if (sensePassability(vMirror) != sensePassability(feature)) {
            sym[1] = false;
        }

        // Check rotational symmetry
        MapLocation rMirror = new MapLocation(mapWidth - 1 - feature.x, mapHeight - 1 - feature.y);
        if (sensePassability(rMirror) != sensePassability(feature)) {
            sym[2] = false;
        }
    }

    return sym;
}
```

**Usage**: Predict enemy king locations

## Backstab Decision Engine

### Scoring Simulation

```java
class BackstabDecision {
    // Track game state
    int ourCatDamage;
    int enemyCatDamage;
    int ourKings;
    int enemyKings;
    int ourCheeseTransferred;
    int enemyCheeseTransferred;

    int scoreCoop() {
        double catPct = ourCatDamage / (double)(ourCatDamage + enemyCatDamage);
        double kingPct = ourKings / (double)(ourKings + enemyKings);
        double cheesePct = ourCheeseTransferred / (double)(ourCheeseTransferred + enemyCheeseTransferred);
        return (int)(0.5 * catPct + 0.3 * kingPct + 0.2 * cheesePct);
    }

    int scoreBackstab() {
        // Assume we kill all enemy kings eventually
        double catPct = ourCatDamage / (double)(ourCatDamage + enemyCatDamage);
        double kingPct = 1.0; // We win kings
        double cheesePct = ourCheeseTransferred / (double)(ourCheeseTransferred + enemyCheeseTransferred);
        return (int)(0.3 * catPct + 0.5 * kingPct + 0.2 * cheesePct);
    }

    boolean shouldBackstab() {
        // Only backstab if we'd score higher
        return scoreBackstab() > scoreCoop() + 10; // 10 point buffer
    }
}
```

## Advanced Mechanics

### Throwing Trajectories

**Compute landing position**:
```java
MapLocation computeThrowLanding(Direction throwDir) {
    MapLocation pos = myLoc;
    for (int t = 0; t < 4; t++) {
        pos = pos.add(throwDir).add(throwDir); // 2 tiles/turn

        // Check collision
        if (!rc.onTheMap(pos)) return pos; // Off map
        if (!rc.sensePassability(pos)) return pos; // Hit wall/dirt
        if (rc.senseRobotAtLocation(pos) != null) return pos; // Hit robot
    }
    return pos; // Max range
}
```

### Vision Cone Geometry

**Check if location in cone**:
```java
boolean inVisionCone(MapLocation target, Direction facing) {
    Direction toTarget = myLoc.directionTo(target);

    // Compute angle difference
    int facingOrd = facing.ordinal();
    int targetOrd = toTarget.ordinal();
    int diff = Math.abs(facingOrd - targetOrd);
    if (diff > 4) diff = 8 - diff; // Wrap around

    // 90 degree cone = ±1 direction (45 degrees each side)
    return diff <= 1;
}
```

## Performance Benchmarks

### Target Metrics

**Cheese Collection**:
- Early game (0-500): 5+ cheese/round
- Mid game (500-1000): 8+ cheese/round
- Late game (1000-2000): 12+ cheese/round

**King Sustainability**:
- 1 king: Never starve
- 2 kings: Starve <5% of rounds
- 3 kings: Only if income supports

**Combat**:
- Cat damage: >60% of total (cooperation)
- Rat casualties: <20% of spawned
- Kill/death ratio: >1.5 (backstab mode)

**Bytecode**:
- Baby rat: <10,000 average, <15,000 peak
- Rat king: <15,000 average, <18,000 peak

## Architecture Decisions

### File Structure

```
src/ratbot/
├── RobotPlayer.java      # Entry point, main loop
├── G.java                # Global state singleton
├── POI.java              # Cheese mine + king tracking
├── Nav.java              # Directional pathfinding
├── Micro.java            # Combat micro
├── Comm.java             # Communication protocol
├── BabyRat.java          # Baby rat behavior
├── RatKing.java          # King behavior
└── Util.java             # Helper functions
```

### Design Patterns from 2025

**Keep**:
- Global state singleton (G.java)
- Static variables for performance
- Backward loops
- Cached sensing
- Visibility logging
- Test-driven development

**Adapt**:
- Navigation for directional movement
- POI for cheese mines (not paint towers)
- Communication for 10-bit encoding
- Combat for vision cone constraints

**New**:
- Ratnapping state management
- Backstab decision system
- King feeding orchestration
- Cat avoidance AI

## Risk Mitigation

### King Starvation Prevention

**System**:
1. Track global cheese continuously
2. Predict consumption (3 per king per round)
3. Alert when cheese <100 (33 rounds buffer)
4. Emergency: Stop spawning, focus collection
5. Last resort: Let weakest king starve (if >1 king)

**Implementation**:
```java
if (globalCheese < kingCount * 100) {
    // EMERGENCY MODE
    emergencyCollection = true;
    stopSpawning = true;
}
```

### Cat Death Prevention

**System**:
1. Track all cats in shared array
2. Predict cat paths (waypoints symmetric)
3. Avoid cat vision cones
4. Never squeak near cats
5. Retreat when cat in attack mode

### Bytecode Overflow Prevention

**System**:
1. Profile all sections
2. Identify expensive operations
3. Optimize or amortize over multiple turns
4. Use Clock.yield() strategically
5. Monitor with Clock.getBytecodeNum()
