# Ratbot6 Design Plan: Counter-Strategy to Beat Ratbot5

## Executive Summary

Ratbot6 is designed with one goal: **beat ratbot5**. By analyzing ratbot5's ~3500 lines of complex code, we've identified exploitable weaknesses and designed counter-strategies around three core principles: **Simplicity**, **Numbers**, and **Aggression**.

---

## Ratbot5's Exploitable Weaknesses

| Weakness | Why It's Exploitable |
|----------|---------------------|
| **Assassins are predictable** | They rush to cached king position - if king moves, they go to wrong spot |
| **Kiting wastes tempo** | Retreating gives us free hits and map control |
| **Squeak relay is fragile** | 4-tile range + collector dependency = easy to break |
| **Complex code = bytecode overhead** | ~3500 lines means less efficient per turn |
| **Swarm flanking splits forces** | Distributed attackers can be defeated in detail |
| **Emergency mode is defensive** | When triggered, they stop attacking |
| **Cached enemy king position** | Uses round-1 symmetry guess, can be stale |

---

## Ratbot6 Core Philosophy

### Design Principles
1. **No kiting** - Attack and hold position, force trades
2. **Mobile king** - Move every round, invalidate cached positions
3. **Collector hunters** - Starve enemy economy
4. **Concentrated force** - No flanking, everyone attacks together
5. **Early aggression** - Rush before ratbot5 establishes relay system
6. **Simpler code** - Save bytecode for more rats, fewer bugs

### Target Metrics
- **Code size**: < 1000 lines (vs ratbot5's 3500)
- **Bytecode/turn**: < 1000 average (vs ratbot5's 1200-1500)
- **Cheese reserve**: 50 (vs ratbot5's 100-400)

---

## Counter-Strategy Matrix

| Ratbot5 Feature | Ratbot6 Counter |
|-----------------|-----------------|
| Assassins rush cached king pos | **Mobile king** + trap corridor along direct path |
| Kiting retreat after attack | **Aggressive chase** - don't let wounded escape |
| Squeak relay system | **Kill collectors** to break chain + king mobility |
| All-in mode at HP < 150 | **Bait trigger**, then defend with traps |
| Emergency economy mode | **Collector hunters** force early starvation |
| Swarm flanking (L/R/C) | **Concentrated force** overwhelms one flank |
| Bug2 pathfinding | Simpler greedy movement, save bytecode |
| Focus fire broadcasts | Simple "attack nearest" - less coordination overhead |

---

## Ratbot6 Architecture

```
KING (Mobile + Trap Layer)
├── Moves toward center, then circles map edge
├── Spawns aggressively (50 cheese reserve vs ratbot5's 100-400)
├── Places trap corridor between spawn and enemy
└── Simple enemy broadcast (no complex focus fire)

ATTACKERS (70%) - No Kiting, Pure Aggression
├── Attack nearest enemy, hold position, don't retreat
├── Chase wounded enemies to finish kills
├── All converge on enemy king (no flanking)
└── Simple greedy pathfinding (save bytecode)

HUNTERS (20%) - Economy Warfare
├── Patrol enemy half of map
├── Priority: rats carrying cheese > collectors > others
├── Force enemy into emergency economy mode
└── Disrupt squeak relay by killing collectors

COLLECTORS (10% early, 30% after round 50)
├── Fast in-and-out cheese runs
├── Flee immediately on enemy sight
├── Direct pathfinding to king
└── Never fight - survival is priority
```

---

## Timing Strategy

| Phase | Rounds | Strategy | Rationale |
|-------|--------|----------|-----------|
| **Rush** | 1-30 | 90% attackers, spawn maximum, early pressure | Hit before relay system works |
| **Pressure** | 30-60 | Maintain aggression, deploy hunters | Starve their economy |
| **Sustain** | 60-150 | Balance 60/40 attack/collect, continue pressure | Maintain advantage |
| **Finish** | 150+ | All-in attack if winning, defend if behind | Close out game |

---

## Key Features to Implement

### 1. Mobile King (Counter Assassins)
```
Round 1-20: Move toward map center (escape corner trap)
Round 20+: Circle around map edge, never stay in one spot
- Invalidates ratbot5's cached enemy king position
- Assassins waste turns going to wrong location
- Harder to coordinate attacks on moving target
```

### 2. No-Kite Combat (Counter Kiting)
```
When in combat:
- Attack every turn possible
- Never retreat based on HP
- Chase wounded enemies
- Trade 1-for-1 is acceptable (we have numbers advantage)

Rationale: Ratbot5 kites retreat after each attack, wasting 1-3 turns.
If we don't retreat, we get 2-3x more attacks per engagement.
```

### 3. Collector Hunters (Counter Economy)
```
Hunter behavior:
- Patrol line between enemy king and map center
- Target priority: cheese carrier > collector > attacker
- 20% of army dedicated to this role
- Goal: force ratbot5 into emergency mode (cheese < 100)

When ratbot5 enters emergency mode:
- 80-100% of their rats become collectors
- Attack pressure drops to near zero
- We win by king damage
```

### 4. Concentrated Force (Counter Flanking)
```
All attackers go to same location:
- No left/center/right flanking
- Overwhelming local superiority
- Easier coordination
- Defeats ratbot5's spread formation in detail
```

### 5. Trap Corridor (Counter Rush)
```
King places traps along direct path from enemy spawn:
- 3-5 traps in a line toward enemy
- Slows assassin rush
- Damages attackers before they reach king
- Buys time for defenders
```

---

## Implementation Phases

### Phase 1: Core Foundation (MVP)
**Goal**: Working bot that can complete a game

- [ ] Basic king: spawn rats, place some traps
- [ ] Basic attacker: move toward enemy king, attack nearest
- [ ] Basic collector: find cheese, deliver to king
- [ ] Simple greedy movement (no Bug2)
- [ ] Role assignment by ID modulo

**Test**: Beat examplefuncsplayer on all maps

### Phase 2: Counter-Strategies
**Goal**: Implement ratbot5-specific counters

- [ ] Mobile king movement pattern
- [ ] No-kite combat (attack-hold, chase wounded)
- [ ] Hunter role implementation
- [ ] Trap corridor placement
- [ ] Aggressive spawn reserve (50 cheese)

**Test**: Beat ratbot5 on DefaultMedium

### Phase 3: Optimization
**Goal**: Win consistently on all maps

- [ ] Bytecode profiling and optimization
- [ ] Map-size specific tuning
- [ ] Ratio adjustments based on testing
- [ ] Edge case handling

**Test**: Win 8/10 games vs ratbot5 on all default maps

---

## Success Criteria

### Must Win
- DefaultSmall as Team A and Team B
- DefaultMedium as Team A and Team B  
- DefaultLarge as Team A and Team B

### Metrics to Track
- Win rate vs ratbot5 (target: >70%)
- Average game length (shorter = better aggression)
- Enemy king damage dealt (target: >300 by round 100)
- Cheese collected vs enemy (target: >1.5x ratio)

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Mobile king gets cornered | Always move toward center first |
| Hunters weaken our attack | Cap hunters at 20%, dynamic based on enemy economy |
| No kiting means more deaths | Spawn more rats, accept trades |
| Simple pathfinding gets stuck | Add basic obstacle avoidance |
| Ratbot5 adapts (if human opponent) | N/A - we're testing against fixed code |

---

## Code Structure

```
ratbot6/
├── RobotPlayer.java     # Main entry point (<1000 lines total)
│   ├── Constants        # All tunable parameters
│   ├── runKing()        # Mobile king + spawning + traps
│   ├── runAttacker()    # No-kite combat + chase
│   ├── runHunter()      # Collector hunting
│   ├── runCollector()   # Fast cheese runs
│   └── moveTo()         # Simple greedy movement
```

---

## Next Steps

1. **Create ratbot6 package** with basic structure
2. **Implement Phase 1** (MVP)
3. **Test vs examplefuncsplayer**
4. **Implement Phase 2** (counter-strategies)
5. **Test vs ratbot5**
6. **Iterate and optimize**
