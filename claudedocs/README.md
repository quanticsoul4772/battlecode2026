# Battlecode 2026 - Rat King Chronicles

## Project Status
**Created**: January 5th, 2026 (Competition Start Day)  
**Status**: Information gathering phase  
**Goal**: Build competitive AI for Battlecode 2026

## Game Summary
**Battlecode 2026** is a rats vs cats strategy game where teams:
- Control **BABY_RAT** units to collect cheese
- Upgrade to **RAT_KING** units for spawning and coordination
- Battle **CAT** enemies
- Compete to maximize cheese collection

### Key Differentiators from 2025
- **2025**: Paint-based territory control (Soldier/Splasher/Mopper, towers)
- **2026**: Resource collection (Rats/Cats, cheese economy, throwing mechanics)

## Resources
- **Javadoc**: https://releases.battlecode.org/javadoc/battlecode26/1.0.1/
- **Official Site**: https://battlecode.org
- **Discord**: https://discord.gg/N86mxkH
- **Kickoff**: https://docs.google.com/presentation/d/10HFxiVC9XXdGCtC1ik5nk3C3JMHNRiLv7aS0g90zsFg/

## Documentation Created
- [x] `game_mechanics.md` - Complete game mechanics reference
- [x] `research_plan.md` - Information gathering strategy
- [x] `initial_research.md` - Project kickoff notes

## Immediate Needs
1. **Find official scaffold repository** (likely GitHub)
2. **Access kickoff presentation content** (may need PDF export)
3. **Set up development environment**
4. **Join Discord** for community updates

## Next Steps
1. Download scaffold
2. Review sample bots
3. Design initial bot architecture
4. Implement basic cheese collection
5. Add combat logic
6. Optimize bytecode usage

## Project Structure
```
battlecode2026/
├── claudedocs/          # Documentation and research
│   ├── README.md
│   ├── game_mechanics.md
│   ├── research_plan.md
│   └── initial_research.md
└── src/                 # Bot source code (pending scaffold)
```

## Experience from 2025
We have proven patterns from Battlecode 2025:
- **Visibility logging** - STATE, ECONOMY, TACTICAL logs
- **Bytecode optimization** - Backward loops, caching, static variables
- **Navigation** - Bug2 pathfinding (needs adaptation for directional movement)
- **Micro combat** - Scoring-based tactical decisions
- **Test-driven development** - JUnit test coverage

These will adapt to 2026's cheese economy and throwing mechanics.
