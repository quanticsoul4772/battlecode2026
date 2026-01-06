# Battlecode 2026 - Strategic Priorities & Analysis

## Week 1 Priorities (Jan 5-12, Sprint 1)

### 1. Basic Infrastructure
**Goal**: Get functional bot running

**Must-Have**:
- Cheese collection patrol
- Transfer to king
- Basic spawning logic
- King feeding management (critical!)
- Simple cat avoidance

**Metrics**:
- Can sustain 1 king for 2,000 rounds
- Avoid starvation deaths
- Collect >100 cheese/game

### 2. Movement System
**Goal**: Navigate with directional constraints

**Challenges**:
- Forward movement cheaper than strafing
- Turning costs cooldown
- Vision cone limits sensing
- Pathfinding must consider facing

**Implementation**:
- Directional Bug2 variant
- Lookahead for turning
- Facing optimization (minimize turns)

### 3. Cat Survival
**Goal**: Don't die to cats

**Strategy**:
- Detect cat in vision
- Retreat when cat approaches
- NO SQUEAKING (attracts cats!)
- Use shared array instead
- Learn cat waypoint patterns

## Week 2 Priorities (Jan 13-19, Sprint 2)

### 4. Combat System
**Goal**: Win cooperation phase

**Cat Damage**:
- Bite attacks (10 damage base)
- Cheese-enhanced bites (log scaling)
- Cat trap placement (100 damage)
- Optimal damage/cheese ratio

**Micro**:
- Facing-aware combat positioning
- Flanking attacks (target facing away)
- Multi-rat coordination
- Retreat thresholds

### 5. Multi-King Management
**Goal**: Scale production without starving

**Analysis**:
- 1 king: 3 cheese/round, 1 spawn location
- 2 kings: 6 cheese/round, 2 spawn locations (2x production)
- 3 kings: 9 cheese/round, 3 spawn locations (3x production)

**Breakpoint**: Need enough cheese income to sustain N kings
- Estimate: ~4 cheese/round/king (including spawning)
- 2 kings viable if: >8 cheese/round income
- 3 kings viable if: >12 cheese/round income

**King Formation Strategy**:
- Create 2nd king around round 200-300?
- Requires sustained surplus cheese
- 7 rats + 50 cheese investment

### 6. Communication Protocol
**Goal**: Coordinate without attracting cats

**Shared Array Allocation** (64 slots):
- Slots 0-15: Cheese mine locations (4 slots per mine, ~4 mines)
- Slots 16-31: Enemy rat positions (recent sightings)
- Slots 32-39: Cat positions + mode
- Slots 40-47: King status (HP, cheese, position)
- Slots 48-63: Tactical signals

**Encoding Scheme** (10 bits):
- 6 bits X coordinate (0-63)
- 4 bits Y coordinate (0-15) OR
- 5 bits X, 5 bits Y (0-31)

**Squeak Protocol**:
- Emergency only (rat king in danger)
- Combat coordination (but risky!)
- Avoid unless critical

## Week 3 Priorities (Jan 20-26, Qualifiers)

### 7. Backstab Decision System
**Goal**: Optimize cooperation → backstab timing

**Game Theory**:
- Cooperation: 50% cat damage weight
- Backstabbing: 50% king survival weight

**Optimal Timing**:
- **Too early**: Lose cat damage contribution, cats still strong
- **Too late**: Enemy gets high score, cats weakened
- **Sweet spot**: After 70-80% cat HP removed?

**Decision Criteria**:
- Our cat damage: >60% of total?
- Enemy kings: ≤2 and vulnerable?
- Our kings: ≥2 and healthy?
- Cat HP: <30%?

**Implementation**:
- Track damage ratio continuously
- Simulate score under both modes
- Backstab when expected score delta >20%

### 8. Advanced Tactics

**Ratnapping**:
- Enemy sacrifice to cat (removes enemy, stuns cat)
- Ally extraction from danger
- Throwing for rapid deployment

**Trap Strategy**:
- Cat traps: High-traffic cat paths
- Rat traps: Cheese mine defense (backstab mode)
- Dirt walls: King protection

**Cheese Mine Control**:
- Identify all mines early
- Establish patrol routes
- Defend mines in backstab mode

## Week 4 Priorities (Jan 27-31, Finals)

### 9. Bytecode Optimization
**Goal**: Maximize computation efficiency

**Techniques** (from 2025):
- Backward loops
- Static variable caching
- Minimal sensing
- Pre-computed lookup tables

**2026-Specific**:
- Vision cone calculations (expensive?)
- Directional pathfinding (extra state)
- Ratnapping tracking (complex)

**Target**: <10,000 bytecode/turn average

### 10. Meta-Game Adaptation
**Goal**: Counter opponent strategies

**Scouting**:
- Watch scrimmages
- Identify opponent patterns
- Adapt counter-strategies

**Counter-Strategies**:
- If enemy backstabs early: Focus king defense
- If enemy never backstabs: Backstab when advantageous
- If enemy traps heavily: Avoid known positions
- If enemy squeaks often: Exploit cat attraction

## Critical Success Factors

### Must-Have (Non-Negotiable)
1. **King feeding**: Never starve (auto-loss)
2. **Cheese income**: Sustain king(s) + spawning
3. **Cat avoidance**: Don't feed cats our rats
4. **Basic combat**: Contribute cat damage

### High-Value (Competitive Advantage)
1. **Backstab timing**: Optimal game theory decision
2. **Multi-king**: Scale production efficiently
3. **Communication**: Coordinate without attracting cats
4. **Trap placement**: Force multiplier

### Nice-to-Have (Polish)
1. **Throwing**: Advanced mechanic, high skill
2. **Dirt tactics**: Terrain control
3. **Enemy ratnapping**: Sacrifice to cats
4. **Decoy squeaks**: Cat manipulation

## Risk Assessment

### High Risk
- **King starvation**: Auto-loss, must prevent
- **All kings in one area**: Cat pounce can hit multiple
- **Over-squeaking**: Attracts cats unnecessarily
- **Early backstab**: Lose cat damage, cats still dangerous

### Medium Risk
- **Single king**: Point of failure
- **Many kings**: Hard to feed all
- **Trap over-investment**: Cheese better spent on units
- **Throwing complexity**: 30 cooldown stun is harsh

### Low Risk
- **Conservative play**: May lose to aggressive opponent
- **No backstab**: Opponent may backstab and win
- **Minimal traps**: Miss damage opportunities

## Key Metrics to Track

### Economy
- Cheese income rate (per round)
- King consumption (3n per round)
- Spawn rate (units created)
- Net cheese trend (increasing or decreasing?)

### Combat
- Cat damage dealt
- Cat damage ratio (us vs enemy)
- Rat casualties (to cats, to enemy)
- Trap trigger count

### Kings
- King HP (all kings)
- King positions (spread or clustered?)
- Rounds without feeding (danger!)
- King count (1-5)

### Map Control
- Cheese mines known
- Cheese mines controlled
- Enemy king locations
- Cat positions + modes

## Hypothesis Testing

### H1: Optimal King Count
**Hypothesis**: 2 kings optimal (balance production vs consumption)
**Test**: Compare 1-king vs 2-king vs 3-king strategies
**Metrics**: Games won, cheese surplus, unit production rate

### H2: Backstab Timing
**Hypothesis**: Backstab at 70-80% cat HP removed
**Test**: Vary backstab timing (50%, 70%, 90%, never)
**Metrics**: Final score, win rate

### H3: Squeak Avoidance
**Hypothesis**: Squeaking attracts cats more than benefit from coordination
**Test**: No-squeak vs squeak-heavy strategies
**Metrics**: Cat deaths to team, coordination effectiveness

### H4: Throwing Value
**Hypothesis**: Throwing not worth 30 cooldown stun
**Test**: Throwing-heavy vs no-throwing strategies
**Metrics**: Combat wins, cheese efficiency

### H5: Trap ROI
**Hypothesis**: Traps have positive ROI if placed in high-traffic areas
**Test**: Heavy-trap vs minimal-trap strategies
**Metrics**: Damage per cheese spent on traps

## Competitive Landscape

### Likely Meta Strategies

**Aggressive**:
- Fast 2nd king
- Early backstab
- Heavy spawning
- Trap investment

**Economic**:
- Single king long-term
- Late/never backstab
- Maximize cheese collection
- Minimal trap investment

**Defensive**:
- Multi-king for redundancy
- Heavy cat traps
- Dirt walls around kings
- Conservative backstab (only if winning)

**Our Approach**: Adaptive based on opponent scouting

## Design Philosophy

**From 2025 Experience**:
1. **Visibility First**: Comprehensive logging for analysis
2. **Test-Driven**: JUnit tests before implementation
3. **Iterative**: Run → analyze → improve → repeat
4. **Bytecode Aware**: Profile and optimize continuously

**2026 Adaptations**:
1. **Game Theory**: Model cooperation vs backstab decisions
2. **Directional**: Navigation must respect facing
3. **Stochastic**: Cheese spawns are probabilistic
4. **Multi-Threat**: Cats + enemy rats simultaneously

## Success Criteria

### Sprint 1 (Jan 12)
- Functional bot that doesn't starve
- Can collect cheese and sustain 1 king
- Avoids obvious cat deaths
- Top 50% of submissions

### Sprint 2 (Jan 19)
- Beat reference player
- 2-king management working
- Effective cat combat
- Backstab decision logic implemented
- Top 25% of submissions

### Qualifiers (Jan 24-27)
- Optimized bytecode (<12k average)
- Sophisticated combat micro
- Adaptive backstab timing
- Multiple strategies for different maps
- Top 16 qualification

### Finals (Jan 31)
- Meta-game adapted
- Opponent-specific strategies
- Polished execution
- Top 4 finish goal
