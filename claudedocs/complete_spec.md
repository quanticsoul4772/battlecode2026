# Battlecode 2026: Uneasy Alliances - Complete Specification

## Tournament Schedule

- **Sprint 1**: Jan 12, 2026
- **Sprint 2**: Jan 19, 2026
- **International Qualifiers**: Jan 24, 2026
- **U.S. Qualifiers**: Jan 26, 2026
- **Newbie/High School Qualifiers**: Jan 27, 2026
- **Final Tournament**: Jan 31, 2026

**Reference Player Release**: Week 3 (around Jan 19)

## Game Overview

**Theme**: Rats vs Cats - Cooperative/competitive game theory

**Match Structure**:
- Best of 3 games
- Each game = 2,000 rounds
- Map: 30x30 to 60x60 (even dimensions)
- Symmetric (rotation or reflection)

### Win Conditions

**Instant Loss**: All rat kings die
**Game End Triggers**:
1. All cats defeated (cooperation mode)
2. All enemy rat kings defeated
3. Round 2,000 reached

### Scoring System

**Cooperation Mode** (Eq. 1):
```
score = round(0.5 * %damage_to_cats + 0.3 * %living_kings + 0.2 * %cheese_transferred)
```

**Backstabbing Mode** (Eq. 2):
```
score = round(0.3 * %damage_to_cats + 0.5 * %living_kings + 0.2 * %cheese_transferred)
```

**Tiebreakers** (in order):
1. Global cheese at game end
2. Total rats alive (baby + kings)
3. Random selection

## Cooperation & Backstabbing

**Starting State**: All games begin in cooperation

**Backstab Triggers** (any one):
1. Rat attacks enemy rat
2. Rat triggers enemy trap
3. Rat ratnaps enemy rat

**Effect**: Game state immediately changes to backstabbing
**Reset**: Next game starts in cooperation again

**Strategic Implication**:
- Cooperation: Emphasize cat damage (50% weight)
- Backstabbing: Emphasize king survival (50% weight)

## Units

### Baby Rat

**Stats**:
- HP: 100
- Size: 1x1
- Movement cooldown: 10
- Turning cooldown: 10
- Vision: sqrt(20) radius, 90 degree cone (facing direction)
- Bytecode limit: 17,500

**Abilities**:

1. **Attack (bite)**:
   - Range: Adjacent (8 neighbors)
   - Must be in vision cone
   - Base damage: 10
   - Enhanced: Spend X cheese → add ceil(log X) damage
   - Raw cheese consumed first, then global

2. **Ratnapping (carry)**:
   - Can carry 1 baby rat at a time
   - Target must be adjacent + visible
   - Can grab if target:
     - Facing away (cannot see you), OR
     - Has less health, OR
     - Is on your team
   - Carrier has normal speed, carried rat stunned
   - Auto-drop after 10 rounds
   - Carried rat immune to attacks except cat pounce

3. **Throwing**:
   - Throw carried rat forward
   - Speed: 2 tiles/turn for 4 turns
   - Damage: 5 * (4 - airtime) to thrown rat + hit target
   - Landing stun:
     - 10 cooldown (dropped after 4 turns)
     - 30 cooldown (hit target early)
   - Thrown rat cannot trigger traps, be attacked, or pick up cheese
   - If hits cat: rat dies, cat sleeps 2 turns

4. **Trap Placement**:
   - **Rat Trap**: 5 cheese, cooldown 5, max 25 active
     - Trigger radius: sqrt(2)
     - Damage: 50 + stun (20 movement cooldown)
     - Hidden from enemies
   - **Cat Trap**: 10 cheese, cooldown 10, max 10 active
     - Only placeable in cooperation mode
     - Trigger radius: sqrt(2)
     - Damage: 100 + stun (20 movement cooldown)
   - No refund on removal

5. **Dirt Management**:
   - Dig/place: 10 cheese, cooldown 25
   - Range: Adjacent + in vision cone
   - Dirt is global team resource
   - Cannot place on: robot, wall, dirt, cheese, cheese mine

6. **Cheese Collection**:
   - Pick up from ground (in vision cone)
   - Becomes "raw cheese" (local to rat)
   - Must transfer to rat king to become global
   - Carrying penalty: 0.01 * cheese_amount multiplier to cooldowns
   - Dropped on death

### Rat King

**Stats**:
- HP: 500
- Size: 3x3
- Movement cooldown: 40
- Turning cooldown: 10
- Vision: sqrt(25) radius, 360 degrees (omnidirectional)
- Bytecode limit: 20,000
- Consumption: 3 cheese/round (or lose 10 HP)

**Abilities**:

1. **Spawn Baby Rats**:
   - Location: Adjacent to king
   - Cost: 10 + 10 * floor(baby_rats_alive / 4) cheese
   - Examples:
     - 0-3 rats alive: 10 cheese
     - 4-7 rats alive: 20 cheese
     - 25 rats alive: 70 cheese
   - Cooldown: 10
   - Spawned rat inherits king's facing direction

2. **Write Shared Array**:
   - Only kings can write
   - 64 slots, values 0-1023
   - All robots can read

3. **Attack, Dirt, Traps**: Same as baby rat

**King Creation**:
- Requires 7+ allied rats in 3x3 square around center rat
- Center rat becomes king
- Cost: 50 cheese
- Max 5 kings per team
- HP = sum of individual rat HPs (capped at 500)
- Surrounding rats destroyed, raw cheese added to global
- Center rat retains cooldowns
- Traps in 3x3 triggered

### Cat (NPC)

**Stats**:
- HP: 10,000
- Size: 2x2
- Movement cooldown: 10
- Vision: sqrt(30) radius, 180 degree cone
- Spawns: Even number, symmetric, at map center

**Attack Modes**:
1. **Pounce**: Jump up to 3 tiles, kills all rats on landing, cooldown 20
2. **Scratch**: 50 damage, action cooldown 15
3. **Movement**: Walking onto baby rat kills it instantly
4. **Feeding**: Eats thrown rats, sleeps 2 turns

**Behavior AI**:
- **Explore**: Move toward waypoints, dig dirt, attack blocking rats
  - Sees rat → Attack mode
  - Hears squeak → Chase mode
- **Chase**: Move toward squeak location → Search mode on arrival
- **Search**: Rotate 90 degrees four times looking for rats
  - Sees rat → Attack mode
  - No rats → Explore mode
- **Attack**: Focus single target, scratch if possible, pounce/move toward
  - Loses sight → Explore mode

**Important**:
- Cannot differentiate team colors (attacks both!)
- Hears all squeaks (knows source location)
- Knows all wall locations (uses BFS)
- Permanently removes dirt (does not add to stash)

## Map Elements

### Cheese Mines
- Fixed locations, even number, symmetric
- Minimum spacing: sqrt(5) between mines
- Spawn mechanics:
  - Amount: 5 cheese per spawn
  - Location: Random in 9x9 square around mine
  - Probability: 1 - (1 - 0.01)^r where r = rounds since last spawn
  - Spawns symmetrically across map
  - Cannot spawn on walls, can spawn on dirt
- Rats can sense mine centers in vision cone

### Walls
- Impassable, permanent
- Max 20% of map
- Known to cats (use BFS)

### Dirt
- Impassable, modifiable
- Max 50% of map at start
- Global team resource (conserved)
- Dig/place: 10 cheese, cooldown 25
- Range: Adjacent + in vision cone
- Rat king range: (3/2 + sqrt(2)) from center
- Baby rat range: (0.5 + sqrt(2)) from center

## Resources

### Cheese

**Types**:
- **Raw Cheese**: Held by individual baby rat, slows movement
- **Global Cheese**: Team pool, accessible by all

**Starting Amount**: 2,500 global cheese per team

**Collection Flow**:
1. Baby rat picks up cheese → raw cheese
2. Baby rat transfers to king → global cheese
3. King picks up cheese → directly global

**Spending Priority**: Raw first, then global

**Carrying Penalty**:
- Cooldown multiplier: 0.01 * raw_cheese_amount
- Example: 50 cheese = 1.5x slower movement

**On Death**: Raw cheese dropped at location

### Dirt
- Global team resource
- Gained by digging existing dirt
- Used to place new dirt
- Cat-dug dirt permanently removed

## Communication

**No Shared Variables**: Each robot has separate JVM instance

**Two Methods**:

1. **Shared Array**:
   - 64 integer slots
   - Values: 0-1023 (10 bits)
   - Write: Rat kings only
   - Read: All robots

2. **Squeaking**:
   - Max 1 per robot per turn
   - Range: sqrt(16) = 4 radius
   - Received by: Allied rats + CATS
   - Duration: 5 rounds
   - Content: messageContent (int) + robot ID + location + round number
   - **WARNING**: Cats hear squeaks and know source location!

## Movement & Cooldowns

**Facing Direction**: 8 directions (N, NE, E, SE, S, SW, W, NW)
- All robots start facing map center
- Affects vision cone center

**Movement**:
- Can move when movement cooldown < 10
- Move to 1 of 8 adjacent tiles
- Cooldowns reduced by 10 per round
- No stacking (except ratnapping)

**Turning**:
- Can turn when turning cooldown < 10
- Turn up to 90 degrees per turn
- Can turn before or after moving

**Cooldown Types**:
- Movement cooldown
- Turning cooldown
- Action cooldown
- All reduced by 10 at round start

## Bytecode Limits

**Per-Unit Limits**:
- Baby Rat: 17,500
- Rat King: 20,000

**Team Execution Limit**: Aggregated across all robots for entire game

**Python Support**:
- Bytecode multiplied by 3x to convert to Java equivalent
- Java recommended for bytecode optimization

**Exception Penalty**: 500 bytecode

**Auto-yield**: Exceeding limit pauses execution, resumes next turn
- Use Clock.yield() to control turn ending

**Fixed Costs**: See MethodCosts.txt for standard functions

**Array Creation**: Cost = array length (NEWARRAY, ANEWARRAY, MULTIANEWARRAY)

## Chirality & Symmetry

**Chirality**: Affects sensing order and part location order
- Example (vertical symmetry):
  - Team A: Increasing x, increasing y
  - Team B: Decreasing x, increasing y

**Map Symmetry**: Rotation or horizontal/vertical reflection

## Critical Mechanics

### Ratnapping Details
- Must drop within 10 rounds or auto-drop in front
- If drop location blocked: SWAP (carrier becomes carried)
- Carried rat drops any rat it was carrying
- Chain breaks: A carries B, C grabs A → A drops B
- Dropped rat resumes original facing direction
- Carried rat can squeak + sense only

### Throwing Details
- Speed: 2 tiles/turn for 4 turns
- Damage formula: 5 * (4 - airtime)
- Hits obstacles early: Drops immediately
- Stun on landing:
  - Normal landing (4 turns): 10 cooldown
  - Hit target: 30 cooldown
- Collateral damage: Not stunned
- Cannot trigger traps while airborne

### King Formation
- Requires: 7+ allied rats in 3x3 around center
- Cost: 50 cheese
- Max 5 kings per team
- 3x3 must be passable, no kings/cats
- Center rat retains cooldowns
- HP = sum of all rats (capped 500)
- Surrounding rats destroyed
- Raw cheese from destroyed rats → global
- Traps in 3x3 triggered

### Cheese Spawn Math
- Probability per round: 1 - (1 - 0.01)^r
- At r=1: ~1% chance
- At r=69: ~50% chance
- At r=230: ~90% chance
- Always spawns 5 cheese per event

## Cat AI Deep Dive

**Mode Transitions**:
```
Explore → (see rat) → Attack
Explore → (hear squeak) → Chase → (arrive) → Search
Search → (see rat) → Attack
Search → (no rats) → Explore
Attack → (lose sight) → Explore
```

**Explore**:
- Follow waypoints (symmetric, invisible to rats)
- Use BFS around walls
- Dig dirt if blocking (cooldown 30)
- Attack blocking rats

**Chase**:
- Move toward squeak location
- Pounce if possible
- Enter search on arrival

**Search**:
- Four 90 degree turns looking for rats
- First rat seen → Attack mode

**Attack**:
- Focus single target, ignore others
- Priority: Scratch if possible → Pounce → Move
- Lost sight → Return to waypoint

## Key Constants

### Costs
- Spawn baby rat: 10 + 10 * floor(count/4)
- Upgrade to king: 50
- Rat trap: 5 cheese, cooldown 5
- Cat trap: 10 cheese, cooldown 10
- Dig/place dirt: 10 cheese, cooldown 25

### Limits
- Max 5 rat kings per team
- Max 25 rat traps per team
- Max 10 cat traps per team
- Max 10 rounds carrying
- Max 8 MB heap per robot

### Damage
- Baby rat bite: 10 + ceil(log X) if X cheese spent
- Cat scratch: 50
- Cat pounce: Instant kill (baby rats)
- Throw damage: 5 * (4 - airtime)
- Rat trap: 50 + stun
- Cat trap: 100 + stun
- Stun: +20 movement cooldown

### Vision Ranges
- Baby Rat: sqrt(20) radius, 90 degree cone
- Rat King: sqrt(25) radius, 360 degree cone
- Cat: sqrt(30) radius, 180 degree cone

### Communication
- Squeak range: sqrt(16) = 4 radius
- Squeak duration: 5 rounds
- Shared array: 64 slots, 0-1023 values

## Strategic Insights

### Game Theory Dilemma
**Cooperation Score**: 50% cat damage, 30% kings
**Backstabbing Score**: 30% cat damage, 50% kings

**Analysis**:
- Cooperation rewards aggressive cat fighting
- Backstabbing rewards king preservation
- Early backstab: More time to kill enemy kings
- Late backstab: Risk losing cat damage contribution
- Optimal timing: After significant cat damage, before enemy gets too strong

### Cheese Economy
**Income**: Probabilistic from mines
**Expenses**:
- 3/round per king (critical!)
- 10+ per spawn (scales with count)
- Traps, dirt (optional)

**Break-even**: Need ~3 cheese/round per king minimum
- 1 king = 3 cheese/round
- 2 kings = 6 cheese/round
- etc.

**Collection**: Baby rats must physically deliver to kings

### King Management
**Single King**:
- Lower consumption (3/round)
- Single spawn location
- Single point of failure

**Multiple Kings**:
- Higher consumption (3n/round)
- More spawn locations (better unit production)
- Distributed risk
- Harder to feed all

**Optimal**: Likely 2-3 kings (balance spawn rate vs consumption)

### Communication Strategy
**Squeak Risks**:
- Cats hear ALL squeaks
- Cats know squeak source location
- Can trigger chase mode

**Strategies**:
- Minimal squeaking (avoid attracting cats)
- Squeak only for critical info
- Use shared array for non-urgent data
- Decoy squeaks to lure cats?

### Throwing Tactics
**Offensive**:
- Ratnap enemy rat → throw at cat (sacrifices enemy, stuns cat)
- Throw ally at enemy (20 damage if landed turn 0)
- Rapid deployment (2 tiles/turn × 4 = 8 tile range)

**Defensive**:
- Extract low-HP allies from danger
- Throw over obstacles for retreat

**Considerations**:
- 30 cooldown stun on landing (big cost)
- Requires ratnapping first (complex setup)
- High risk if throw fails

## Vision Cone Mechanics

**Baby Rat** (90 degree cone):
- Can only see ~1/4 of surroundings
- Must turn to scout
- Vulnerable to flanking
- Turning costs cooldown

**Implications**:
- Facing matters for combat
- Rotation management critical
- Prediction systems need directional awareness
- Multi-rat coverage needed for 360 degree sensing

## Bytecode Optimization Priorities

**From 2025 Experience**:
1. Backward loops: for (int i = arr.length; --i >= 0;)
2. Cache sensing: Don't re-sense same data
3. Static variables for frequently accessed data
4. Avoid array allocation in loops
5. Pre-compute lookup tables

**2026-Specific**:
- Vision cone calculations expensive?
- Directional pathfinding overhead
- Ratnapping state management
- Communication encoding/decoding

## Open Strategic Questions

1. **Backstab Timing**: When is optimal to backstab?
2. **King Count**: 1, 2, 3, 4, or 5 kings?
3. **Squeak Usage**: Worth attracting cats?
4. **Trap Investment**: Cost-effective vs spawning?
5. **Throwing**: Viable strategy or niche?
6. **Dirt Walls**: Defensive or offensive use?
7. **Cheese Mine Control**: Camp mines or roving collection?
8. **Cat Baiting**: Intentional squeak lures?
9. **Enemy Sacrifice**: Ratnap enemy → feed to cat?

## Differences from Battlecode 2025

| Aspect | 2025 (Paint) | 2026 (Cheese) |
|--------|--------------|---------------|
| Theme | Territory control | Resource collection + NPC threat |
| Units | Soldier/Splasher/Mopper/Towers | Baby Rat/Rat King/Cat (NPC) |
| Resource | Paint (infinite) | Cheese (limited) |
| Vision | Omnidirectional | Directional cones |
| Movement | Direct 8-way | Facing + turning costs |
| Win | 70% paint coverage | Points (damage/kings/cheese) |
| Team Interaction | Opposed only | Cooperation → Backstabbing |
| Key Mechanic | Paint spreading | Ratnapping/throwing |

## Next Steps

1. Clone scaffold: git clone https://github.com/battlecode/battlecode26-scaffold
2. Review examplefuncsplayer
3. Set up Java 21 JDK
4. Test basic movement + vision
5. Implement cheese collection loop
6. Design communication protocol
7. Build combat micro system
8. Create visibility logging (like 2025)

## Scaffold Status

**Note**: As of Jan 5, 2026, the scaffold repository (https://github.com/battlecode/battlecode26-scaffold) returns 404.

**Possible Reasons**:
1. Not released yet (presentation says it's available, but may be delayed)
2. Different URL
3. Private repository before public release

**Action**: Check Discord (http://bit.ly/battlecode-discord) for scaffold link or ask devs at battlecode@mit.edu
