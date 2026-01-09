# ratbot5 Tuning Options

## Option 1: All Constants at Top (Simple)

**Structure**:
```java
public class RobotPlayer {
    // ==================== TUNABLE PARAMETERS ====================
    // Combat
    private static final int ENHANCED_ATTACK_CHEESE = 8;
    private static final int ENHANCED_THRESHOLD = 500;

    // Population
    private static final int INITIAL_SPAWN_COUNT = 12;
    private static final int MAX_SPAWN_COUNT = 20;
    private static final int COLLECTOR_MINIMUM = 4;

    // Movement
    private static final int STUCK_THRESHOLD = 3;
    private static final int POSITION_HISTORY_SIZE = 5;

    // Economy
    private static final int DELIVERY_THRESHOLD = 10;
    private static final int KING_RESERVE_ROUNDS = 50;

    // ... rest of code ...
}
```

**Pros**:
- All parameters in one place
- Easy to find and change
- Clear what each does

**Cons**:
- Hard to see relationships between parameters
- No guidance on safe ranges
- No presets for different strategies

---

## Option 2: Grouped Configuration Sections (Better)

**Structure**:
```java
public class RobotPlayer {
    // ==================== COMBAT CONFIGURATION ====================
    // Damage formula: 10 + ceil(log2(cheese))
    // 8 cheese = 13 damage (30% increase)
    // SAFE RANGE: 4-32 cheese (12-15 damage)
    private static final int ENHANCED_ATTACK_CHEESE = 8;

    // Spend cheese when surplus above this
    // SAFE RANGE: 300-1000 (affects aggression)
    private static final int ENHANCED_THRESHOLD = 500;

    // Target priority: collectors > wounded > any
    // true = prioritize cheese carriers, false = first seen
    private static final boolean PRIORITIZE_COLLECTORS = true;

    // ==================== POPULATION CONFIGURATION ====================
    // Initial spawn count
    // SAFE RANGE: 8-15 (8=economy, 15=rush)
    private static final int INITIAL_SPAWN_COUNT = 12;

    // Maximum rats to spawn
    // SAFE RANGE: 15-25 (higher=more expensive)
    private static final int MAX_SPAWN_COUNT = 20;

    // Minimum collectors to maintain
    // SAFE RANGE: 3-6 (lower=risky, higher=safer)
    private static final int COLLECTOR_MINIMUM = 4;

    // ==================== MOVEMENT CONFIGURATION ====================
    // Rounds to track for stuck detection
    // SAFE RANGE: 3-7 (lower=aggressive, higher=patient)
    private static final int POSITION_HISTORY_SIZE = 5;

    // Rounds before forcing movement
    // SAFE RANGE: 2-5 (lower=aggressive, higher=patient)
    private static final int FORCED_MOVEMENT_THRESHOLD = 3;

    // ==================== ECONOMY CONFIGURATION ====================
    // Cheese to carry before delivering
    // SAFE RANGE: 5-15 (lower=frequent, higher=efficient)
    // NOTE: Carrying penalty is 1% per cheese (10 cheese = 10% slower)
    private static final int DELIVERY_THRESHOLD = 10;

    // King reserve (rounds of survival)
    // SAFE RANGE: 30-100 (affects spawn aggressiveness)
    private static final int KING_RESERVE_ROUNDS = 50;
}
```

**Pros**:
- Grouped by purpose
- Safe ranges documented
- Explains impact of changes
- Easy to tune related parameters together

**Cons**:
- Still just constants (no strategy switching)

---

## Option 3: Strategy Presets (Advanced)

**Structure**:
```java
public class RobotPlayer {
    // ==================== STRATEGY SELECTION ====================
    // Change this to switch entire strategy
    private static final Strategy CURRENT_STRATEGY = Strategy.BALANCED;

    private enum Strategy {
        RUSH,      // Early aggression, fast king kill
        BALANCED,  // Mix of combat and economy
        ECONOMY,   // Focus on cheese, defensive
        CHEESE     // Pure economy, max collection
    }

    // ==================== STRATEGY PRESETS ====================
    // Get parameters based on active strategy
    private static int getInitialSpawnCount() {
        switch (CURRENT_STRATEGY) {
            case RUSH: return 15;      // Lots of attackers
            case BALANCED: return 12;  // Mixed
            case ECONOMY: return 10;   // Fewer rats, more cheese
            case CHEESE: return 8;     // Minimal combat
            default: return 12;
        }
    }

    private static int getCollectorRatio() {
        switch (CURRENT_STRATEGY) {
            case RUSH: return 3;       // 30% collectors
            case BALANCED: return 5;   // 50% collectors
            case ECONOMY: return 7;    // 70% collectors
            case CHEESE: return 9;     // 90% collectors
            default: return 5;
        }
    }

    private static int getEnhancedThreshold() {
        switch (CURRENT_STRATEGY) {
            case RUSH: return 300;     // Aggressive spending
            case BALANCED: return 500; // Moderate
            case ECONOMY: return 700;  // Conservative
            case CHEESE: return 1000;  // Rarely enhance
            default: return 500;
        }
    }
}
```

**Pros**:
- Switch entire strategy with one line
- Parameters stay consistent
- Easy to test different approaches

**Cons**:
- More complex code
- Higher bytecode cost (switch statements)

---

## Option 4: Dynamic Tuning (Adaptive)

**Structure**:
```java
public class RobotPlayer {
    // ==================== ADAPTIVE PARAMETERS ====================
    // Parameters adjust based on game state

    private static int getEnhancedThreshold(RobotController rc) {
        int cheese = rc.getGlobalCheese();

        // Adjust based on cheese level
        if (cheese > 2000) return 300;      // Aggressive when rich
        else if (cheese > 1000) return 500; // Moderate
        else return 700;                     // Conservative when poor
    }

    private static int getSpawnCount(RobotController rc) {
        int round = rc.getRoundNum();

        // Early game: More rats
        if (round < 30) return 15;
        // Mid game: Moderate
        else if (round < 100) return 12;
        // Late game: Fewer (economy focus)
        else return 10;
    }

    private static int getCollectorMinimum(RobotController rc) {
        int cheese = rc.getGlobalCheese();

        // If starving, need more collectors
        if (cheese < 500) return 6;
        // Normal: moderate
        else if (cheese < 1500) return 4;
        // Rich: can afford fewer
        else return 3;
    }
}
```

**Pros**:
- Adapts to game state automatically
- No manual tuning needed
- Responds to conditions

**Cons**:
- Higher bytecode cost (function calls)
- Harder to predict behavior
- More complex

---

## Recommendation

**Use Option 2 (Grouped Configuration) for ratbot5**

**Why**:
- Easy to tune (all parameters visible)
- Safe ranges prevent bad values
- Impact documented (know what each does)
- Low bytecode cost (compile-time constants)
- Good for competition (tune between matches)

**Structure**:
```java
// ========== COMBAT CONFIG ==========
private static final int ENHANCED_CHEESE = 8;
private static final int ENHANCED_THRESHOLD = 500;

// ========== POPULATION CONFIG ==========
private static final int INITIAL_SPAWN = 12;
private static final int MAX_SPAWN = 20;
private static final int MIN_COLLECTORS = 4;

// ========== MOVEMENT CONFIG ==========
private static final int STUCK_THRESHOLD = 3;
private static final int HISTORY_SIZE = 5;

// ========== ECONOMY CONFIG ==========
private static final int DELIVERY_AT = 10;
private static final int KING_RESERVE = 150; // cheese
```

**Tuning workflow**:
1. Change constants at top
2. Rebuild
3. Test
4. Repeat

**Which option do you prefer for ratbot5?**
