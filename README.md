# Battlecode 2026 - Ratbot 🐀

[![CI Status](https://github.com/quanticsoul4772/battlecode2026/workflows/CI%20-%20Test%20and%20Quality%20Checks/badge.svg)](https://github.com/quanticsoul4772/battlecode2026/actions)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
[![Engine v1.0.6](https://img.shields.io/badge/Battlecode-v1.0.6-blue.svg)](https://battlecode.org)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Competition Bot for MIT Battlecode 2026 - "Uneasy Alliances"**

A competitive AI bot featuring strategic resource management, pathfinding algorithms, and adaptive combat behavior. Built with bytecode optimization and comprehensive testing infrastructure.

---

## 🚀 Quick Start

```bash
# Clone the repository
git clone https://github.com/quanticsoul4772/battlecode2026.git
cd battlecode2026/scaffold

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

**For detailed instructions**, see [AGENTS.md](AGENTS.md) - the comprehensive developer guide.

---

## 📁 Project Structure

```
battlecode2026/
├── scaffold/              # Main development workspace
│   ├── src/              # Bot implementations
│   │   ├── ratbot/       # Base bot (2,500+ lines)
│   │   ├── ratbot2/      # Iteration 2
│   │   ├── ratbot3/      # Iteration 3
│   │   ├── ratbot4/      # Current active bot
│   │   └── examplefuncsplayer/  # Reference implementation
│   ├── test/             # Unit and integration tests
│   │   ├── algorithms/   # Algorithm tests (pathfinding, vision, geometry)
│   │   ├── ratbot/       # Bot logic tests
│   │   ├── integration/  # Full game simulation tests
│   │   └── mock/         # Test utilities and mocks
│   ├── tools/            # Analysis scripts (Python)
│   ├── build.gradle      # Gradle build configuration
│   ├── checkstyle.xml    # Code style rules
│   └── gradle.properties # Match configuration
├── lectureplayer/        # Minimal bot example
├── claudedocs/           # Game specifications (66KB)
│   ├── complete_spec.md  # Full game rules (17KB)
│   ├── quick_reference.md # Stats and costs
│   └── ...               # 28+ documentation files
├── .github/workflows/    # CI/CD pipelines
├── AGENTS.md             # 📖 Developer guide (START HERE)
├── RATBOT4_PLAN.md       # Current implementation strategy
├── SUCCESS_CRITERIA.md   # Performance metrics
├── .env.example          # Configuration template
└── README.md             # This file
```

---

## 🤖 Current Bot: ratbot4

### Strategy Overview

**Population**: 12 rats (economic balance)
- **6 Attackers** (ID % 2 == 0): Rush enemy positions, attack baby rats, use cheese-enhanced attacks
- **6 Collectors** (ID % 2 == 1): Gather cheese, deliver to king, maintain economy

### Key Features

- ✅ **Adaptive Spawning**: 12 rats initially, replacement every 50 rounds
- ✅ **Smart Combat**: Target baby rats (easier than 3×3 king), cheese-enhanced attacks when surplus > 500
- ✅ **Economy Management**: Track cheese flow, emergency delivery protocols
- ✅ **Pathfinding**: Bug2 algorithm for obstacle navigation
- ✅ **Suicide Protocol**: Attackers self-destruct after 100 idle rounds (free population slots)
- ✅ **Zero-Allocation Logging**: Performance tracking without bytecode overhead
- ✅ **Bytecode Optimization**: Static buffers, backward loops, minimal allocations

### Performance Metrics

**Success Criteria**:
- King survives longer than enemy king
- Positive cheese income (deliveries > 3 cheese/round)
- Regular TRANSFER messages in logs
- Death from combat (not starvation)

See [SUCCESS_CRITERIA.md](SUCCESS_CRITERIA.md) for detailed metrics.

---

## 🛠️ Development

### Prerequisites

- **Java 21** (required)
- **Gradle 8.x** (included via wrapper)
- **Git** (for version control)

### Build Commands

```bash
# Build and test
./gradlew build              # Compile all code
./gradlew test               # Run all tests
./gradlew check              # Run tests + quality checks

# Code quality
./gradlew checkstyleMain     # Run linter
./gradlew spotlessApply      # Auto-format code
./gradlew jacocoTestReport   # Generate coverage report

# Run matches
./gradlew run                              # Use gradle.properties settings
./gradlew run -PteamA=ratbot4 -PteamB=examplefuncsplayer

# Submission
./gradlew zipForSubmit       # Create submission.zip
```

### Code Quality Tools

- **Checkstyle**: Java linting with style enforcement
- **Spotless**: Google Java Format for consistent code style
- **JaCoCo**: Test coverage tracking (60% minimum threshold)
- **CI/CD**: GitHub Actions workflow for automated testing

### Testing Infrastructure

- **Unit Tests**: 18+ test files for algorithms and bot logic
- **Integration Tests**: Full game simulation tests
- **Mock Framework**: Engine-independent testing
- **Coverage**: 60%+ required (enforced by JaCoCo)

```bash
# Run specific tests
./gradlew test --tests "PathfindingTest"
./gradlew test --tests "*EdgeCaseTest"

# View coverage report
./gradlew test jacocoTestReport
# Open: scaffold/build/reports/jacoco/test/html/index.html
```

---

## 📚 Documentation

### Essential Reading

1. **[AGENTS.md](AGENTS.md)** - Complete developer guide (commands, conventions, workflows)
2. **[RATBOT4_PLAN.md](RATBOT4_PLAN.md)** - Current implementation strategy and lessons learned
3. **[SUCCESS_CRITERIA.md](SUCCESS_CRITERIA.md)** - How to measure bot performance
4. **[.env.example](.env.example)** - Configuration options

### Game Documentation

- **[claudedocs/complete_spec.md](claudedocs/complete_spec.md)** - Full game rules (17KB)
- **[claudedocs/quick_reference.md](claudedocs/quick_reference.md)** - Stats, costs, and ranges
- **[claudedocs/technical_notes.md](claudedocs/technical_notes.md)** - Engine internals

### External Resources

- **Competition Site**: https://battlecode.org
- **Discord Community**: http://bit.ly/battlecode-discord
- **Game Engine**: https://github.com/battlecode/battlecode26
- **API Documentation**: https://play.battlecode.org/bc26/api

---

## 🏆 Tournament Schedule

- **Sprint 1**: January 12, 2026 (Qualifier)
- **Sprint 2**: January 19, 2026
- **Finals**: January 31, 2026

---

## 🤝 Contributing

### Development Workflow

1. **Create feature branch**: `git checkout -b feature/new-strategy`
2. **Write tests first**: Add failing tests in `test/`
3. **Implement feature**: Update code in `src/ratbot/`
4. **Run quality checks**: `./gradlew check`
5. **Format code**: `./gradlew spotlessApply`
6. **Test in match**: Update `gradle.properties`, run `./gradlew run`
7. **Commit with description**: Follow conventional commits
8. **Push and create PR**: CI will run automatically

### Code Conventions

- **Naming**: PascalCase (classes), camelCase (methods/variables), UPPER_SNAKE_CASE (constants)
- **Line Length**: Max 120 characters
- **Formatting**: Google Java Format (auto-applied by spotless)
- **Comments**: Minimal - only for complex algorithms
- **Bytecode**: Zero-allocation patterns, static buffers, backward loops

See [AGENTS.md](AGENTS.md) for detailed conventions.

---

## 🔬 Game Mechanics (Critical)

### Movement
- **Forward**: 10 cooldown
- **Strafe**: 18 cooldown (PENALTY - avoid!)
- **Turn**: 10 cooldown
- **Best Practice**: Turn + moveForward (20 total)

### Combat
- **Range**: Adjacent only (distanceSquared ≤ 2)
- **Vision**: 90° cone, must face target
- **Damage**: 10 base, 13 enhanced (costs 8 cheese)
- **Baby Rat HP**: 100 (need 10 hits to kill)

### Economy
- **King Consumption**: 3 cheese/round
- **Spawn Cost**: 10 + 10 × floor(count/4)
- **Transfer Range**: 3 tiles (distanceSquared ≤ 9)
- **Critical**: King starvation = instant loss

---

## 📊 Tech Stack

- **Language**: Java 21
- **Build**: Gradle 8.x
- **Testing**: JUnit 4.13.2
- **CI/CD**: GitHub Actions
- **Code Quality**: Checkstyle, Spotless, JaCoCo
- **Engine**: Battlecode 2026 v1.0.6

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **MIT Battlecode Team** - For hosting the competition
- **Claude Sonnet 4.5** - AI pair programming partner (50+ commits)
- **Battlecode Community** - For strategies, discussions, and support

---

**Status**: 🟢 Active Development | **Engine**: v1.0.6 | **Bot**: ratbot4

*Competing in MIT Battlecode 2026 - "Uneasy Alliances"*
