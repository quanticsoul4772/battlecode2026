# AGENTS.md - Battlecode 2026 Developer Guide

This document provides essential information for AI agents and developers working on the Battlecode 2026 competition bot (ratbot).

## Quick Start

```bash
# Navigate to the scaffold directory
cd scaffold

# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a match
./gradlew run

# View replay in client
./client/battlecode-client.exe
# Then load: matches/ratbot4-vs-examplefuncsplayer-on-DefaultSmall.bc26
```

## Project Structure

```
battlecode2026/
├── scaffold/              # Main development workspace
│   ├── src/              # Bot implementations
│   │   ├── ratbot/       # Current bot (ratbot, 2,500+ lines)
│   │   ├── ratbot2/      # Previous iteration
│   │   ├── ratbot3/      # Previous iteration
│   │   ├── ratbot4/      # Latest iteration
│   │   └── examplefuncsplayer/  # Reference implementation
│   ├── test/             # Unit and integration tests
│   │   ├── algorithms/   # Algorithm tests (pathfinding, vision, etc.)
│   │   ├── ratbot/       # Bot logic tests
│   │   └── integration/  # Full game simulation tests
│   ├── tools/            # Analysis scripts (Python)
│   ├── build.gradle      # Gradle build configuration
│   └── gradle.properties # Match configuration
├── lectureplayer/        # Separate minimal bot example
├── claudedocs/           # Game specifications (66KB docs)
├── data/                 # MCP reasoning cache (ignored)
├── README.md             # Project overview
├── AGENTS.md             # This file
└── .env.example          # Configuration template
```

## Build System (Gradle)

### Core Commands

```bash
# Build and compile
./gradlew build              # Compile all code
./gradlew clean              # Clean build artifacts
./gradlew clean build        # Clean rebuild

# Testing
./gradlew test               # Run all tests
./gradlew test --tests "*PathfindingTest"  # Run specific test
./gradlew check              # Run tests + quality checks

# Code Quality
./gradlew checkstyleMain     # Run checkstyle on main code
./gradlew checkstyleTest     # Run checkstyle on test code
./gradlew spotlessCheck      # Check code formatting
./gradlew spotlessApply      # Auto-format code
./gradlew jacocoTestReport   # Generate coverage report

# Running Matches
./gradlew run                # Run match with settings from gradle.properties
./gradlew run -PteamA=ratbot4 -PteamB=examplefuncsplayer -Pmaps=DefaultSmall

# Submission
./gradlew zipForSubmit       # Create submission.zip

# Utility
./gradlew tasks              # List all available tasks
./gradlew listPlayers        # List all bot implementations
./gradlew listMaps           # List all available maps
./gradlew update             # Update to latest engine version
```

### Configuration Files

**gradle.properties** - Match settings:
- `teamA`, `teamB` - Which bots to run
- `maps` - Map selection (DefaultSmall, DefaultMedium, DefaultLarge)
- `debug`, `outputVerbose`, `showIndicators` - Debug options

**.env.example** - Environment configuration template (copy to .env)

## Language & Environment

- **Language**: Java 21 (required)
- **Build Tool**: Gradle 8.x
- **Testing**: JUnit 4.13.2
- **Engine**: Battlecode 2026 v1.0.6
- **Bytecode Limits**: 
  - Baby Rat: 17,500 bytecode per turn
  - Rat King: 20,000 bytecode per turn

## Code Structure & Conventions

### Package Organization

```
src/
├── ratbot/                    # Current active bot
│   ├── RobotPlayer.java       # Main entry point (run() method)
│   ├── BabyRat.java          # Baby rat behavior
│   ├── RatKing.java          # King behavior
│   ├── algorithms/           # Reusable algorithms
│   │   ├── Pathfinding.java  # Bug2, navigation
│   │   ├── Vision.java       # Sensing utilities
│   │   ├── Geometry.java     # Distance, direction
│   │   ├── GameTheory.java   # Strategy calculations
│   │   └── Constants.java    # Game constants
│   └── logging/              # Performance tracking
│       ├── Logger.java       # Zero-allocation logging
│       ├── Profiler.java     # Bytecode profiling
│       └── BytecodeBudget.java  # Budget tracking
```

### Naming Conventions

- **Classes**: PascalCase (e.g., `RobotPlayer`, `BabyRat`)
- **Methods**: camelCase (e.g., `runBabyRat()`, `findNearestCheese()`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RATS`, `DELIVERY_RANGE`)
- **Variables**: camelCase (e.g., `enemyKingLoc`, `roundNumber`)
- **Packages**: lowercase (e.g., `ratbot.algorithms`)

### Code Style

- **Line Length**: Max 120 characters
- **Indentation**: 2 spaces (enforced by Google Java Format)
- **Imports**: No wildcard imports (avoid `import battlecode.common.*`)
- **Braces**: Always use braces for if/while/for blocks
- **Comments**: Minimal - only for complex algorithms or non-obvious logic

### Bytecode Optimization

This is a bytecode-limited competition. Key optimization techniques:

1. **Static Buffers**: Reuse arrays instead of allocating new ones
2. **Backward Loops**: `for (int i = n-1; i >= 0; i--)` is cheaper
3. **Zero Allocation**: Avoid `new` in main loop
4. **Early Returns**: Exit methods as soon as possible
5. **Inline Calculations**: Avoid method calls in hot paths
6. **Static Variables**: Share data across turns when possible

## Testing

### Test Structure

```
test/
├── algorithms/              # Algorithm unit tests
│   ├── PathfindingTest.java
│   ├── VisionTest.java
│   ├── GeometryTest.java
│   └── *EdgeCaseTest.java  # Edge case variants
├── ratbot/                  # Bot logic tests
│   ├── LoggerTest.java
│   ├── ProfilerTest.java
│   └── BabyRatStateMachineTest.java
├── integration/             # Full game tests
│   └── FullGameSimulationTest.java
└── mock/                    # Test utilities
    ├── MockRobotController.java
    └── MockGameState.java
```

### Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests "PathfindingTest"

# With coverage report
./gradlew test jacocoTestReport
# View: scaffold/build/reports/jacoco/test/html/index.html

# With code quality checks
./gradlew check
```

### Test Coverage

- **Minimum Coverage**: 60% (enforced by JaCoCo)
- **Coverage Report**: `build/reports/jacoco/test/html/index.html`
- **Focus**: Algorithm correctness, edge cases, bytecode limits

## Game Mechanics (Critical)

### Movement
- **Forward**: 10 cooldown
- **Strafe** (move without turning): 18 cooldown (PENALTY!)
- **Turn**: 10 cooldown
- **Best Practice**: Always turn + moveForward (20 total) instead of strafe

### Combat
- **Attack Range**: Adjacent only (distanceSquared ≤ 2)
- **Vision**: 90° cone, must face target
- **Base Damage**: 10 (Baby Rat HP = 100)
- **Enhanced Attack**: Costs 8 cheese, deals 13 damage
- **Backstab**: Triggered when attacking enemy rats

### Spawning
- **Cost**: 10 + 10 × floor(count/4) cheese
- **Range**: distanceSquared ≤ 4 (2 tiles from king center)
- **King Size**: 3×3 (spawn area is small)
- **Build Cooldown**: 10

### King Mechanics
- **Consumption**: 3 cheese/round
- **HP Loss**: 10 HP/round when unfed
- **Vision**: 360°, radius 5 tiles
- **Critical**: King starvation = loss

## Common Workflows

### Developing a New Feature

1. **Create branch** (optional for local work)
2. **Write failing test** in `test/`
3. **Implement feature** in `src/ratbot/`
4. **Run tests**: `./gradlew test`
5. **Check format**: `./gradlew spotlessCheck` (fix with `spotlessApply`)
6. **Run checkstyle**: `./gradlew checkstyleMain`
7. **Test in match**: Update `gradle.properties`, run `./gradlew run`
8. **Commit with descriptive message**

### Debugging a Match

1. **Enable verbose output**: Set `outputVerbose=true` in gradle.properties
2. **Add indicators**: Use `rc.setIndicatorString()` for visual debugging
3. **Run match**: `./gradlew run`
4. **Check logs**: Look at generated `.log` files in scaffold/
5. **View replay**: Open client and load `.bc26` file from matches/
6. **Analyze**: Use tools/log_parser.py or tools/performance_dashboard.py

### Submitting to Competition

1. **Run full test suite**: `./gradlew clean build test`
2. **Verify bot works**: `./gradlew run`
3. **Create submission**: `./gradlew zipForSubmit`
4. **Upload**: Submit `submission.zip` to battlecode.org
5. **Git tag** (optional): `git tag submission-v1.0 && git push --tags`

## Strategy Overview

### Current Bot (ratbot4)

**Population**: 12 rats
- 6 attackers (ID % 2 == 0)
- 6 collectors (ID % 2 == 1)

**Attacker Behavior**:
1. Rush enemy king location
2. Attack enemy baby rats (easier than 3×3 king)
3. Use cheese-enhanced attacks when surplus > 500
4. Suicide after 100 idle rounds (free population slot)

**Collector Behavior**:
1. Find nearest cheese
2. Collect cheese
3. Deliver to king (transfer range: 3 tiles)
4. Repeat

**King Behavior**:
1. Spawn 12 rats ASAP (rounds 1-12)
2. Broadcast position every 10 rounds
3. Track cheese economy
4. Spawn replacements every 50 rounds if < 12 alive

### Success Criteria

**Victory**: King survives longer than enemy king
**Failure**: King starves (cheese = 0)
**Key Metric**: Delivery rate must exceed 3 cheese/round (king consumption)

## Known Issues & Limitations

1. **Pathfinding**: Bug2 algorithm sometimes gets stuck in complex obstacle fields
2. **Combat**: Difficulty navigating to 3×3 king geometry (prefer baby rat targets)
3. **Traffic**: Can jam near king during high rat density
4. **Economy**: No emergency delivery protocol when king HP critical

See `SUCCESS_CRITERIA.md` and `RATBOT4_PLAN.md` for detailed analysis.

## Resources

### Documentation
- **Game Specs**: `claudedocs/complete_spec.md` (17KB, comprehensive rules)
- **Quick Reference**: `claudedocs/quick_reference.md` (stats, costs, ranges)
- **Strategy Guide**: `RATBOT4_PLAN.md` (current implementation plan)
- **Success Metrics**: `SUCCESS_CRITERIA.md` (how to measure performance)

### External Links
- **Competition Site**: https://battlecode.org
- **Game Engine**: https://github.com/battlecode/battlecode26
- **Discord**: http://bit.ly/battlecode-discord
- **API Docs**: https://play.battlecode.org/bc26/api

## Tournament Schedule

- **Sprint 1**: Jan 12, 2026 (qualifier)
- **Sprint 2**: Jan 19, 2026
- **Finals**: Jan 31, 2026

## Tips for AI Agents

1. **Read Specs First**: Check `claudedocs/complete_spec.md` for game rules
2. **Test Incrementally**: Run tests after each change
3. **Watch Bytecode**: Use `Profiler.java` to track bytecode usage
4. **Simulate Before Push**: Run full matches, not just unit tests
5. **Check Logs**: Look for `TRANSFER`, `ATTACK`, `SPAWN` messages
6. **Avoid Over-Engineering**: Simple code wins in bytecode-limited environment
7. **Profile Everything**: Performance > elegance in this competition

## Getting Help

- **Code Issues**: Check existing tests and mock implementations
- **Game Rules**: Read `claudedocs/complete_spec.md`
- **Strategy**: Review `RATBOT4_PLAN.md` for lessons learned
- **Build Issues**: Run `./gradlew clean build` to reset
- **Engine Updates**: Run `./gradlew update` regularly

---

**Last Updated**: January 8, 2026
**Engine Version**: 1.0.6
**Current Bot**: ratbot4 (12 rats, attack + collect strategy)
