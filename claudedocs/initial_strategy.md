# Battlecode 2026 - Initial Strategic Analysis

## Core Strategic Pillars

### 1. Cheese Economy Management
**Critical**: Rat Kings consume cheese per round - mismanagement = death

**Priorities**:
- Establish sustainable cheese collection early
- Balance spawning costs vs king maintenance
- Optimal king count (more kings = more spawning but more consumption)
- Transfer efficiency to kings

**Questions**:
- What's optimal king count?
- When to upgrade Baby Rats to Kings?
- Cheese collection patrol patterns?

### 2. Vision & Facing Direction
**New Mechanic**: Units have facing direction affecting:
- Vision (cone-based, not omnidirectional)
- Movement cost (forward cheaper than strafing)
- Combat effectiveness

**Implications**:
- Scouting requires rotation management
- Flanking attacks more valuable
- Prediction systems need directional awareness

### 3. Throwing as Tactical Tool
**Unique Mechanic**: Rats can carry/throw other rats

**Offensive Uses**:
- Rapid unit deployment (catapult effect)
- Damage amplification (throw damage)
- Surprise attacks

**Defensive Uses**:
- Retreat assistance
- Repositioning wounded units
- Creating rat towers (height advantage?)

**Costs**:
- Cooldown multiplier while carrying
- Stun duration on impact
- Health threshold requirement

### 4. Trap Strategy
**Three Trap Types**:
- Dirt: Terrain control, create chokepoints
- Rat Traps: Anti-rat defense
- Cat Traps: Cat mitigation

**Strategic Questions**:
- Where to place traps optimally?
- Dirt placement for cheese mine protection?
- When to invest cheese in traps vs units?

### 5. Communication Architecture
**Two Systems**:
1. **Squeak** (broadcast): Short-range, all units
2. **Shared Array** (global): King-only write, all read

**Design Needs**:
- Message encoding protocol (4 bytes per squeak)
- Shared array allocation scheme
- Priority signaling (cheese location, threats, coordination)

## Opening Strategy Hypotheses

### Early Game (Rounds 1-100)
1. **Fast Expansion**: Maximize cheese collection
   - Scout cheese mine locations
   - Establish collection routes
   - First king upgrade timing critical

2. **Safe Development**: Conservative growth
   - Delay king upgrades
   - Build map knowledge
   - Focus on efficiency

3. **Aggressive Rush**: Early pressure
   - Fast king upgrade
   - Spawn Baby Rats immediately
   - Deny enemy cheese

### Mid Game (Rounds 100-500)
- Multiple kings or single king focus?
- Combat engagement criteria
- Cheese mine control vs collection efficiency

### Late Game (Rounds 500+)
- All-in strategies?
- King count optimization
- Endgame cheese maximization

## Technical Challenges

### Navigation
**Problem**: Facing direction complicates pathfinding
- Bug2 needs directional state
- Turn costs affect optimal paths
- Vision cones limit sensing

**Solutions**:
- Directional Bug2 variant
- Lookahead planning for turns
- Pre-compute facing for efficiency

### Bytecode Budget
**Constraints**:
- Per-unit limits
- Team execution time limit
- Exception penalties

**Optimization Priorities**:
1. Backward loops (proven 30% savings)
2. Static variable caching
3. Minimal sensing calls
4. Pre-computed lookup tables

### Multi-Unit Coordination
**Without Centralized Control**:
- Distributed decision making
- Avoid clustering at cheese mines
- Combat formations
- Retreat protocols

**Communication Patterns**:
- Cheese location broadcasts
- Threat warnings
- King status updates
- Coordination requests

## Open Questions
1. Can rats see while being thrown?
2. Do carried rats contribute to combat?
3. What's optimal throw trajectory?
4. How do cats prioritize targets?
5. Cheese mine spawn patterns?
6. Map generation symmetry?
7. Cooperation mode details?

## Competitive Analysis Needs
- Watch top 2025 teams for patterns
- Study previous year's winning strategies
- Identify meta-game evolution
- Analyze map variety

## Initial Bot Priorities
1. **Basic Functionality** (Week 1)
   - Cheese collection
   - Movement with facing
   - King upgrade logic
   - Simple spawning

2. **Combat System** (Week 2)
   - Threat detection
   - Attack prioritization
   - Retreat triggers
   - Health management

3. **Coordination** (Week 3)
   - Communication protocol
   - Multi-king management
   - Resource allocation
   - Territory control

4. **Advanced Tactics** (Week 4)
   - Throwing mechanics
   - Trap strategies
   - Micro optimizations
   - Bytecode tuning
