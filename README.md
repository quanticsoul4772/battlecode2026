# Battlecode 2026 - Ratbot

**Competition**: MIT Battlecode 2026 - Uneasy Alliances
**Status**: Scaffold integrated, bot functional
**Engine**: v1.0.3 (early release - some APIs pending)

---

## Quick Start

```bash
cd scaffold

# Build
./gradlew build

# Run match
./gradlew run

# Watch replay
./client/battlecode-client.exe
# Then load matches/ratbot-vs-examplefuncsplayer-on-DefaultSmall.bc26
```

---

## Project Structure

```
battlecode2026/
├── scaffold/              # Active development
│   ├── src/ratbot/       # Our bot (2,521 lines)
│   │   ├── algorithms/   # Pre-built modules
│   │   ├── logging/      # Performance tracking
│   │   ├── RobotPlayer.java
│   │   ├── BabyRat.java
│   │   └── RatKing.java
│   ├── tools/            # Analysis scripts
│   ├── CLAUDE.md         # Dev commands
│   └── API_STATUS.md     # API tracking
└── claudedocs/           # Full documentation (66KB)
    ├── complete_spec.md  # Game rules
    ├── quick_reference.md # Stats lookup
    └── ... (28 more docs)
```

---

## Current Capabilities

**Working** (API v1.0.3):
- Movement and navigation
- Cheese detection and collection
- Economy tracking
- Structured logging

**Pending API Updates**:
- Spawning baby rats
- Traps and dirt
- Ratnapping
- King formation

See `scaffold/API_STATUS.md` for details.

---

## Documentation

**Start Here**:
- `scaffold/CLAUDE.md` - Build commands and dev guide
- `claudedocs/complete_spec.md` - Full game rules (17KB)
- `claudedocs/quick_reference.md` - Stats and costs

**Tournament**: Jan 12 (Sprint 1) - Jan 31 (Finals)

**Resources**:
- Discord: http://bit.ly/battlecode-discord
- Website: https://battlecode.org
- Specs: Already documented in claudedocs/

---

**Status**: Bot running, waiting for complete API release
