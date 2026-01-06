# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Development guidance for Battlecode 2026.

## Build Commands

All commands are run from `scaffold/` directory:

```bash
cd scaffold

# Development
./gradlew build                    # Compile all players
./gradlew run                      # Run match with gradle.properties settings
./gradlew test                     # Run JUnit tests

# Custom matches
./gradlew run -PteamA=ratbot -PteamB=examplefuncsplayer -Pmaps=DefaultSmall

# Testing specific players
./gradlew listPlayers              # Show all available players
./gradlew listMaps                 # Show all available maps

# Tournament submission
./gradlew zipForSubmit             # Creates submission.zip in project root

# Version management
./gradlew version                  # Show current engine/client versions
./gradlew checkNewVersion          # Check for updates
./gradlew update                   # Update to latest version
```

**Windows Note**: Requires JAVA_HOME set to Java 21. Use `gradlew.bat` on Windows.

## Game Overview

**Win Condition**: Score more points than opponent through cat damage, living kings, and cheese transferred.

**Scoring**:
- **Cooperation**: 50% cat damage + 30% living kings + 20% cheese
- **Backstabbing**: 30% cat damage + 50% living kings + 20% cheese

**Instant Loss**: All rat kings die

### Unit Types
| Unit | HP | Size | Move CD | Vision | Bytecode |
|------|-----|------|---------|--------|----------|
| Baby Rat | 100 | 1×1 | 10 | √20, 90° | 17,500 |
| Rat King | 500 | 3×3 | 40 | √25, 360° | 20,000 |
| Cat (NPC) | 10,000 | 2×2 | 10 | √30, 180° | N/A |

### Key Constraints
- **Bytecode limits**: 17,500/rat, 20,000/king per turn
- **King consumption**: 3 cheese/round (or lose 10 HP)
- **Spawn cost**: 10 + 10×floor(baby_rats/4) cheese
- **Max kings**: 5 per team

## Project Structure

```
battlecode2026/
├── scaffold/                 # Official Battlecode scaffold
│   ├── src/
│   │   ├── ratbot/          # Our bot implementation
│   │   │   ├── algorithms/  # Pre-built algorithm library (3,528 lines)
│   │   │   │   ├── Vision.java       # Vision cone calculations
│   │   │   │   ├── Geometry.java     # Distance utilities
│   │   │   │   ├── DirectionUtil.java# Turn optimization
│   │   │   │   ├── Pathfinding.java  # BFS + Bug2 navigation
│   │   │   │   ├── GameTheory.java   # Backstab decisions
│   │   │   │   └── Constants.java    # Game constants
│   │   │   ├── logging/     # Performance tracking
│   │   │   │   ├── Logger.java       # Zero-allocation logging
│   │   │   │   ├── Profiler.java     # Bytecode profiling
│   │   │   │   └── BytecodeBudget.java# Budget tracking
│   │   │   ├── RobotPlayer.java      # Main entry point
│   │   │   ├── BabyRat.java          # Baby rat state machine
│   │   │   └── RatKing.java          # Rat king behavior
│   │   └── examplefuncsplayer/       # Official reference player
│   ├── test/                # Unit tests (40+ tests)
│   ├── client/              # Match viewer (auto-downloaded)
│   └── matches/             # Replay files (.bc26)
├── src/                     # Development modules (pre-integration)
├── test/                    # Development tests + mock framework
├── tools/                   # Analysis scripts
├── claudedocs/              # Strategy and spec documentation
└── INTEGRATION_GUIDE.md     # Module integration instructions
```

## Architecture Patterns

### Bot Execution Flow
1. **RobotPlayer.java** - Entry point, delegates to type-specific classes
2. **BabyRat.java** / **RatKing.java** - State machines for unit behavior
3. **algorithms/** - Pure functions for game logic (vision, pathfinding, decisions)
4. **logging/** - Performance tracking (bytecode profiling, state logging)

### Key Design Principles
- **State machines**: BabyRat uses EXPLORE → COLLECT → DELIVER → FLEE states
- **Zero allocation**: All algorithms use static arrays and backward loops for bytecode efficiency
- **Module isolation**: Algorithm modules are pure functions with no RobotController dependency
- **Logging infrastructure**: Structured logging with zero allocation for post-match analysis

### Communication Architecture
- **Shared array**: 64 slots × 10 bits (0-1023) for team communication
- **No squeaking**: Attracts cats, use shared array instead
- **Vision management**: Directional 90° cones require facing management

### Critical Implementation Notes
- **Movement**: `moveForward()` costs 10 cd, strafing costs 18 cd - always face target first
- **Bytecode limits**: 17,500/baby rat, 20,000/king - use Profiler to track expensive operations
- **King feeding**: Kings consume 3 cheese/round or lose 10 HP - track global cheese carefully
- **Vision cones**: Units can only see within their facing direction - use `Vision.canSee()` to validate

## Match Configuration

Edit `scaffold/gradle.properties`:
```properties
teamA=ratbot
teamB=examplefuncsplayer
maps=DefaultSmall
debug=false
outputVerbose=true           # Enable System.out from bots
showIndicators=true          # Show debug indicators in client
```

## Viewing Matches

1. Build downloads client automatically to `scaffold/client/`
2. Run `Battlecode Client.exe` (Windows) or `Battlecode Client.app` (Mac)
3. Load replay from `scaffold/matches/*.bc26`
4. Client shows indicators, logs, and bytecode usage

## Common Patterns

### Bytecode Optimization
```java
// Backward loops save bytecode
for (int i = array.length; --i >= 0;) { }

// Cache sensing (sense once per turn)
RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);

// Use static arrays (no allocation)
private static int[] buffer = new int[100];
```

### Vision Cone Handling
```java
// Check if enemy is visible
import ratbot.algorithms.Vision;
boolean canSee = Vision.inCone90(rc.getLocation(), rc.getDirection(), enemy.location);
```

### Navigation
```java
// BFS for shortest path
import ratbot.algorithms.Pathfinding;
Direction next = Pathfinding.bfs(start, target, passable, width, height);

// Bug2 for obstacle avoidance (cheaper)
Direction dir = Pathfinding.bug2(current, target, (d) -> rc.canMove(d));
```

### Logging
```java
// Structured logging for analysis
import ratbot.logging.Logger;
Logger.logState(round, "BABY_RAT", id, x, y, facing, hp, cheese, mode);

// Bytecode profiling
import ratbot.logging.Profiler;
Profiler.start();
expensiveOperation();
Profiler.end("operation_name", round, id);
```

## Pre-Built Algorithm Library

The `scaffold/src/ratbot/algorithms/` package contains production-ready modules (3,528 lines, 40+ tests):

### Vision.java
- `inCone90(observer, facing, target)` - Check if location is in 90° vision cone
- `canSee(observer, facing, target, unitType)` - Vision validation with auto-detection
- `getVisibleTiles(center, facing, radius, angle, w, h)` - Get all visible tiles
- **Optimized**: ~50 bytecode per check

### Geometry.java
- `closest(reference, locations)` - Find nearest location from array
- `manhattanDistance(a, b)` - Fast approximation for heuristics
- `withinRange(center, target, radiusSquared)` - Range validation
- **Optimized**: ~100 bytecode per operation

### DirectionUtil.java
- `turnsToFace(current, target)` - Calculate turn cost (0-2)
- `optimalMovement(facing, desired)` - Plan turn + move sequence
- `orderedDirections(preferred)` - Direction array sorted by proximity
- `opposite(direction)` - Get 180° opposite direction

### Pathfinding.java
- `bfs(start, target, passable, w, h)` - Shortest path (~2-5K bytecode)
- `bug2(current, target, canMoveFn)` - Obstacle avoidance (~200-500 bytecode)
- `greedy(current, target)` - Fastest, no obstacles (~50 bytecode)
- **Strategy**: Use greedy for open space, bug2 for obstacles, BFS when precision needed

### GameTheory.java
- `shouldBackstab(ourCat, enemyCat, ourKings, enemyKings, ourCheese, enemyCheese, threshold)` - Boolean decision
- `evaluate(gameState)` - Full recommendation with reasoning
- `worthEnhancingBite(cheeseAmount, targetHP, baseDamage)` - Cheese spending analysis
- **Formula**: Backstab when score advantage >threshold in backstab mode scoring

### Constants.java
- All verified game constants from official API
- Spawn costs, damage values, cooldowns, vision ranges
- Use instead of hardcoding magic numbers

### logging/ Package
- `Logger.logState()` - Zero-allocation state logging
- `Profiler.start/end()` - Bytecode profiling for optimization
- `BytecodeBudget.startTurn()` - Budget tracking
- All output parseable by `tools/` scripts

**Integration**: See `INTEGRATION_GUIDE.md` for detailed usage examples and patterns.

## Development Workflow

### Making Changes
1. Edit behavior in `scaffold/src/ratbot/BabyRat.java` or `RatKing.java`
2. Use algorithm modules from `algorithms/` package (pre-tested)
3. Add logging with `Logger.logState()` for analysis
4. Profile expensive operations with `Profiler.start()/end()`

### Testing
```bash
cd scaffold

# Quick test
./gradlew build && ./gradlew run

# Watch specific metrics
./gradlew run -PoutputVerbose=true | grep "CHEESE\|BACKSTAB\|EMERGENCY"

# Run unit tests
./gradlew test

# Test against different opponents
./gradlew run -PteamB=examplefuncsplayer -Pmaps=DefaultSmall
```

### Analysis
1. Load replay in client to visualize behavior
2. Check System.out logs for state transitions and decisions
3. Review bytecode usage (should stay <17.5K baby rat, <20K king)
4. Parse logs with `tools/` scripts for aggregate statistics

### Submission
```bash
cd scaffold
./gradlew zipForSubmit  # Creates submission.zip in project root
```
Upload `submission.zip` to https://play.battlecode.org/

## Tournament Schedule

- Jan 12: Sprint 1
- Jan 19: Sprint 2 + Reference Player
- Jan 24-27: Qualifiers
- Jan 31: Finals

## Testing Strategy

### Unit Tests (`scaffold/test/`)
- **Algorithm tests**: Vision, Pathfinding, Geometry, etc. (40+ tests)
- **Mock framework**: `test/mock/` provides MockRobotController for isolated testing
- Run: `./gradlew test` from scaffold directory

### Integration Testing
1. Test bot vs `examplefuncsplayer` on different maps
2. Verify state machine transitions (watch logs)
3. Check bytecode usage doesn't exceed limits
4. Validate cheese economy (income > consumption)
5. Test backstab triggers at different game states

### Performance Testing
```java
// In bot code
Profiler.start();
expensiveOperation();
Profiler.end("operation_name", rc.getRoundNum(), rc.getID());
```
Review profiler output to identify bytecode hotspots.

## Common Pitfalls

### Bytecode Explosions
- **Problem**: Exceed 17,500 limit and skip turn
- **Solution**: Profile with `Profiler`, avoid nested loops, cache sensing results
- **Check**: `rc.canMoveForward()` is cheap, `rc.senseNearbyRobots()` is expensive

### King Starvation
- **Problem**: Kings die from no cheese (3/round consumption)
- **Solution**: Track `rc.getGlobalCheese()` every turn, emergency mode at <100 cheese
- **Formula**: `roundsOfCheese = globalCheese / (kingCount * 3)`

### Vision Cone Issues
- **Problem**: Can't see enemies that are technically in range
- **Solution**: Use `Vision.canSee()` to validate visibility, manage facing direction
- **Remember**: Baby rats have 90° cone, must face target to see it

### Premature Backstabbing
- **Problem**: Backstab too early and lose cooperation bonus
- **Solution**: Use `GameTheory.evaluate()` with threshold, only backstab with clear advantage
- **Formula**: Backstab when `(0.3*catDmg + 0.5*kings + 0.2*cheese) > enemy equivalent + threshold`

## Resources

### Documentation
- **Complete Spec**: `claudedocs/complete_spec.md` - Full game rules
- **Constants**: `claudedocs/CONSTANTS_VERIFIED.md` - Verified from javadoc
- **Integration Guide**: `INTEGRATION_GUIDE.md` - Module usage examples
- **Strategy Guide**: `claudedocs/strategic_priorities.md` - Competitive roadmap
- **API Reference**: https://releases.battlecode.org/javadoc/battlecode26/1.0.1/

### External
- **Discord**: http://bit.ly/battlecode-discord - Questions and discussion
- **Registration**: https://play.battlecode.org/bc26/home
- **Lectures**: Daily 7PM EST on Twitch.tv/battlecode

## Quick Reference

### Critical Numbers
- Baby rat vision: √20 radius, 90° cone, 17,500 bytecode
- Rat king vision: √25 radius, 360° cone, 20,000 bytecode
- Spawn cost: 10 + 10×floor(n/4) cheese
- King consumption: 3 cheese/round (or -10 HP)
- Forward move: 10 cd, strafe: 18 cd, turn: 10 cd
- Rat bite: 10 base damage, cheese-enhanced: +ceil(log2(cheese))

### State Machine Pattern
```java
enum State { EXPLORE, COLLECT, DELIVER, FLEE }
// Update state based on conditions
// Execute behavior based on current state
// Log state transitions for debugging
```

### Bytecode Optimization
```java
// GOOD: Backward loop (saves bytecode)
for (int i = array.length; --i >= 0;) { }

// GOOD: Cache sensing
RobotInfo[] enemies = rc.senseNearbyRobots(-1, opponent);

// GOOD: Static arrays (no allocation)
private static int[] buffer = new int[100];

// BAD: Forward loop, repeated sensing, new allocations
```
