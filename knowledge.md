# Project Knowledge - Battlecode 2026

Competition bot for MIT Battlecode 2026 "Uneasy Alliances". Java 21, Gradle build, bytecode-optimized rats.

## Quickstart

```bash
cd scaffold
./gradlew build          # Compile
./gradlew test           # Run tests
./gradlew run            # Run match (uses gradle.properties settings)
./client/battlecode-client.exe  # View replay
```

## Key Commands

```bash
# Development
./gradlew build                    # Compile all code
./gradlew test                     # Run all tests
./gradlew test --tests "PathfindingTest"  # Run specific test
./gradlew spotlessApply            # Auto-format code
./gradlew run -PteamA=ratbot5 -PteamB=lectureplayer  # Custom match

# Submission
./gradlew zipForSubmit             # Create submission.zip

# Utilities
./gradlew listPlayers              # List all bots
./gradlew listMaps                 # List all maps
./gradlew update                   # Update engine version
```

## Architecture

- **scaffold/src/** - Bot implementations (ratbot through ratbot7, lectureplayer)
- **scaffold/test/** - Unit tests (algorithms/, ratbot/, integration/)
- **claudedocs/** - Game specs (complete_spec.md, quick_reference.md)
- **scaffold/gradle.properties** - Match configuration (teamA, teamB, maps)

### Current Bots

- **ratbot6** - Current champion (~1,700 lines) - Value function architecture, squeak communication
- **ratbot7** - In development - Defensive-economic strategy with body blocking
- **ratbot5** - Previous iteration (~3,500 lines) - Kiting state machine

All bots use single-file `RobotPlayer.java` implementations for bytecode efficiency.

## Conventions

- **Java 21** required
- **Google Java Format** via Spotless
- **Bytecode limits**: Baby Rat 17,500, King 20,000 per turn
- **Zero-allocation patterns**: Static buffers, backward loops, avoid `new` in hot paths
- **Naming**: PascalCase (classes), camelCase (methods), UPPER_SNAKE_CASE (constants)

## Game Mechanics (Critical)

- **Movement**: Forward=10cd, Strafe=18cd (avoid!), Turn=10cd
- **Combat**: Adjacent only (dist²≤2), 10 base damage, 13 enhanced (costs 8 cheese)
- **Spawning**: `rc.buildRat(loc)` - cost = 10 + 10×floor(count/4)
- **King**: Consumes 3 cheese/round, loses 10 HP/round when unfed
- **Transfer**: Range dist²≤9 (3 tiles)

## Gotchas

1. Use `rc.buildRat()` NOT `buildRobot()` for spawning
2. Use `rc.move(Direction)` - can strafe, not just `moveForward()`
3. King starvation = instant loss - prioritize cheese delivery
4. Bug2 pathfinding can get stuck in complex obstacle fields
5. Check `claudedocs/complete_spec.md` for full game rules

## Testing

```bash
./gradlew test                          # All tests
./gradlew test jacocoTestReport         # With coverage
# Coverage report: scaffold/build/reports/jacoco/test/html/index.html
```

## Resources

- **Game Specs**: claudedocs/complete_spec.md
- **Quick Reference**: claudedocs/quick_reference.md  
- **API Docs**: https://play.battlecode.org/bc26/api
- **Engine Version**: 1.1.0
- **Discord**: http://bit.ly/battlecode-discord
