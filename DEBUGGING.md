# Debugging Guide - Battlecode 2026

**Robust debugging system for match analysis and development**

---

## Quick Start

### Enable Debugging

Edit `scaffold/src/ratbot/DebugConfig.java`:
```java
public static final boolean ENABLED = true;
public static final Debug.Level LEVEL = Debug.Level.INFO;
```

### Run Match with Debugging
```bash
cd scaffold
./gradlew run
# Watch console for DEBUG: messages
# Open client and load replay to see visual indicators
```

---

## Debug System Features

### 1. Visual Indicators (In Client)

**Colors:**
- 🟢 GREEN: Success (spawns, deliveries)
- 🔴 RED: Danger (cats, blocked spawns, emergencies)
- 🟡 YELLOW: Targets (cheese, objectives)
- 🔵 CYAN: Vision/paths
- 🟠 ORANGE: Enemies
- 🟣 MAGENTA: Special markers

**Usage:**
```java
Debug.dot(rc, location, Debug.Color.RED);      // Mark location
Debug.line(rc, start, end, Debug.Color.CYAN);  // Draw path
Debug.status(rc, "Current state: EXPLORE");    // Show status text
Debug.timeline(rc, "BACKSTAB", Debug.Color.RED); // Timeline marker
```

### 2. Debug Levels

| Level | Output | Use Case |
|-------|--------|----------|
| **OFF** | Nothing | Competition submission |
| **ERROR** | Critical errors only | Production debugging |
| **WARNING** | Errors + warnings | Issue investigation |
| **INFO** | Important events | Development (default) |
| **VERBOSE** | Everything | Deep debugging |

**Set level:**
```java
Debug.setLevel(Debug.Level.VERBOSE);
```

### 3. Feature Flags (DebugConfig.java)

Toggle specific debugging features:

```java
public static final boolean DEBUG_SPAWNING = true;       // Spawn visualization
public static final boolean DEBUG_CHEESE = true;          // Cheese collection
public static final boolean DEBUG_EMERGENCY = true;       // Emergency mode
public static final boolean DEBUG_PATHFINDING = false;    // Path visualization
public static final boolean DEBUG_VISION = false;         // Vision cones
public static final boolean DEBUG_COMBAT = false;         // Combat decisions
public static final boolean DEBUG_BACKSTAB = true;        // Backstab logic
```

### 4. Conditional Debugging

**Debug specific robots:**
```java
public static final int[] DEBUG_ROBOT_IDS = {1234, 5678}; // Only these IDs
```

**Debug specific time ranges:**
```java
public static final int DEBUG_AFTER_ROUND = 500;   // Start at round 500
public static final int DEBUG_BEFORE_ROUND = 1000; // Stop at round 1000
```

---

## Debugging Workflows

### Problem: Spawning Not Working

**Current Issue:** King at [4, 24] can't spawn (all locations blocked)

**Debug output:**
```
[A: #3@10] DEBUG:10:3:WARNING:Spawn blocked - checked 8 locations
```

**Visual in client:**
- 8 red dots around king = blocked spawn locations
- Green dot = successful spawn

**Solutions to try:**
1. Check king position (might be against wall/edge)
2. Add king movement to find better spawn position
3. Check map layout in client replay
4. Verify spawn radius (sqrt(4) = 2 tiles)

### Problem: Rats Not Collecting Cheese

**Enable cheese debugging:**
```java
public static final boolean DEBUG_CHEESE = true;
```

**In match, you'll see:**
- Yellow dots = cheese locations
- Magenta line = target cheese pile
- Status text = "COLLECT cheese=5"

### Problem: Emergency Mode Not Triggering

**Enable emergency debugging:**
```java
public static final boolean DEBUG_EMERGENCY = true;
```

**Look for:**
- Red timeline marker: "EMERGENCY:CRITICAL_STARVATION"
- DEBUG messages with emergency status
- Shared array value = 999 (critical)

### Problem: Cats Killing Rats

**Enable combat visualization:**
```java
public static final boolean DEBUG_COMBAT = true;
public static final boolean VISUAL_INDICATORS = true;
```

**In client:**
- Red dots = cat locations
- Red lines = cat threats
- Status changes to "FLEE" when cat detected

---

## Performance Tuning

### Bytecode Cost of Debugging

**Minimal mode** (competition):
```java
public static final boolean ENABLED = false; // ~0 bytecode overhead
```

**Development mode:**
```java
public static final boolean ENABLED = true;
public static final Debug.Level LEVEL = Debug.Level.INFO;
public static final boolean VISUAL_INDICATORS = true;
// ~100-200 bytecode per turn
```

**Reduce visual spam:**
```java
public static final int INDICATOR_INTERVAL = 10; // Update every 10 rounds
public static final int LOG_INTERVAL = 20;       // Log every 20 rounds
```

---

## Common Debug Patterns

### Track Specific Robot
```java
// In DebugConfig.java
public static final int[] DEBUG_ROBOT_IDS = {1234};

// Now only robot 1234 produces debug output
```

### Debug Mid-Game Issue
```java
// In DebugConfig.java
public static final int DEBUG_AFTER_ROUND = 800;
public static final int DEBUG_BEFORE_ROUND = 1200;

// Only debug rounds 800-1200
```

### Find Why State Machine Stuck
```java
// Enable state logging
public static final boolean STATE_LOGGING = true;

// Look for:
DEBUG:100:1234:INFO:State: EXPLORE -> COLLECT (updateState)
```

### Visualize Pathfinding
```java
public static final boolean DEBUG_PATHFINDING = true;

// In code:
Debug.debugPath(rc, current, target, nextStep);

// Shows yellow dot at target, cyan line for path
```

---

## Match Analysis

### Console Output

```bash
./gradlew run | grep "DEBUG:" > debug.log
./gradlew run | grep "SPAWN" > spawn_analysis.log
./gradlew run | grep "EMERGENCY" > emergency_events.log
```

### Client Replay

1. Run match: `./gradlew run`
2. Open client: `client/battlecode-client.exe`
3. Load replay: `matches/*.bc26`
4. Watch indicators:
   - Red dots = problems (blocked spawns, cats)
   - Green dots = success (spawns, safe locations)
   - Yellow dots = targets (cheese, objectives)
   - Lines = paths, threats, delivery routes
   - Status text = current state
   - Timeline = major events

---

## Debug Output Examples

### Successful Spawn
```
DEBUG:50:3:INFO:Spawned at [6, 25]
SPAWN:50:RAT_KING:3:pos=[6,25]:cost=10:totalRats=1
```
Client: Green dot at [6, 25], timeline marker "SPAWN:1"

### Emergency Mode
```
EMERGENCY:800:CRITICAL_STARVATION:rounds=30:cheese=90
DEBUG:800:3:ERROR:EMERGENCY: CRITICAL_STARVATION (cheese status: 30)
DEBUG:801:1234:ERROR:EMERGENCY: RAT_RESPONSE (cheese status: 999)
```
Client: Red timeline marker, emergency indicators

### State Transition
```
DEBUG:125:1234:INFO:State: EXPLORE -> COLLECT (updateState)
```
Status text updates: "COLLECT cheese=5"

### Cat Detection
```
DEBUG:200:1234:WARNING:CAT DETECTED at [15, 18]
```
Client: Red dot at cat location, red line from rat to cat

---

## Debugging Checklist

**Before match:**
- [ ] Set debug level in DebugConfig.java
- [ ] Enable relevant feature flags
- [ ] Check VISUAL_INDICATORS = true

**During match:**
- [ ] Watch console for DEBUG: messages
- [ ] Note any WARNING or ERROR messages
- [ ] Check for SPAWN_BLOCKED messages

**After match:**
- [ ] Load replay in client
- [ ] Watch visual indicators
- [ ] Check timeline for major events
- [ ] Review console logs for patterns

**For competition:**
- [ ] Set ENABLED = false in DebugConfig.java
- [ ] Verify no debug overhead (bytecode check)
- [ ] Remove any debug-only code

---

## Advanced Debugging

### Create Custom Debug Helpers

```java
// In Debug.java
public static void debugCustomEvent(RobotController rc, String event, int data) {
    if (!DEBUG_ENABLED) return;

    info(rc, "CUSTOM: " + event + " = " + data);
    timeline(rc, event, Color.PURPLE);
}
```

### Profile Specific Code Section

```java
import ratbot.logging.Profiler;

Profiler.start();
expensiveOperation();
Profiler.end("operation_name", rc.getRoundNum(), rc.getID());

// Check profiler output for bytecode cost
```

### Visualize Algorithm Behavior

```java
// In pathfinding code
if (DebugConfig.DEBUG_PATHFINDING) {
    for (MapLocation visited : visitedTiles) {
        Debug.dot(rc, visited, Debug.Color.GRAY);
    }
    Debug.dot(rc, target, Debug.Color.YELLOW);
}
```

---

## Troubleshooting

### No Visual Indicators in Client

**Check:**
1. `DebugConfig.VISUAL_INDICATORS = true`
2. `gradle.properties`: `showIndicators=true`
3. Client settings: Indicators enabled

### Too Much Spam

**Reduce output:**
```java
public static final int LOG_INTERVAL = 50;        // Less frequent
public static final int INDICATOR_INTERVAL = 10;  // Less frequent
public static final Debug.Level LEVEL = Debug.Level.WARNING; // Higher threshold
```

### Bytecode Limit Hit

**Disable expensive features:**
```java
public static final boolean DEBUG_PATHFINDING = false;
public static final boolean DEBUG_VISION = false;
public static final Debug.Level LEVEL = Debug.Level.ERROR;
```

Or disable entirely:
```java
public static final boolean ENABLED = false;
```

---

## Next Steps

Based on current debugging output showing spawn blocking:

1. **Investigate map layout** - Load replay to see why all locations blocked
2. **Add king movement** - If stuck, king should relocate
3. **Improve spawn logic** - Check larger radius or better positions
4. **Test different maps** - Try maps where king has more space

**Debug commands to run:**
```bash
# Try different map
./gradlew run -Pmaps=DefaultLarge

# Watch spawn debugging
./gradlew run | grep "SPAWN"

# Full verbose output
# (Set LEVEL=VERBOSE in DebugConfig.java first)
./gradlew run | grep "DEBUG:"
```

---

**Debugging system ready!** All visual indicators, logging, and analysis tools are active.
