# ratbot4 - Comprehensive Review

## What We Built

**Code**: 825 lines, single file
**Population**: 12 rats (6 attackers, 6 collectors)
**Features**: 20+ features implemented
**Test Performance**: 69 round victories (10x faster than early versions)

### Core Features:

**Spawning**:
- 12 rats initially (rounds 1-12)
- Replacement: 1 per 50 rounds
- Spawns at distance 2 from king
- 50/50 split (even ID = attacker, odd = collector)

**Combat**:
- Cheese-enhanced attacks (8 cheese = 13 damage)
- Attack baby rats (1x1 easy targets)
- Attack all 9 king tiles (3x3 coverage)
- Ratnapping (grab wounded, throw at our king)
- Target prioritization (collectors with cheese)

**Movement**:
- Turn + moveForward (10 cd, avoids strafe penalty)
- Stuck recovery after 2 rounds
- Trap avoidance (only rat traps)
- Traffic yielding (round-robin)
- Safe king movement (checks for threats)

**Communication**:
- Shared array: King position (0-1), enemy king (2-3)
- Squeaks: Enemy king sightings, cheese mine locations
- Message filtering: Fresh messages only, skip own
- Combat zone convergence

**Economy**:
- Collectors find cheese, deliver at 10
- Delivery timeout after 10 stuck rounds
- King consumes 3/round, needs deliveries
- 100 cheese reserve for survival

**Optimizations**:
- All logging removed (-3,400 bytecode)
- Combined loops (-300 bytecode)
- Cached sensing (-500 bytecode)
- Cached shared array (-400 bytecode)
- Bytecode monitoring (graceful degradation)
- Cooldown checks (skip when not ready)
- Direction built-ins (not custom)
- GameConstants usage

## What Works Well

### Excellent:

1. **Fast king kills** (69 rounds vs 600-1000 earlier)
   - Attacking all 9 tiles breakthrough
   - Target prioritization effective

2. **No cooldown freeze** (turn+forward fix)
   - Critical bug fixed
   - Rats move consistently

3. **Squeak coordination**
   - Fresh message prioritization
   - Combat zone convergence
   - Faster than shared array

4. **Bytecode optimization**
   - 83% budget available
   - Room for features
   - Graceful degradation

### Good:

5. **Ratnapping** (20+ per match)
   - Removes wounded enemies
   - Fall damage bonus

6. **Trap avoidance**
   - Smart detection (only rat traps)
   - Saves 50 damage

7. **Target prioritization**
   - Kills collectors carrying cheese
   - Disrupts economy

## What Doesn't Work

### Critical Issues:

1. **No 2nd king formation**
   - Code added but never triggers
   - Conditions too strict (round > 75, cheese > 1000, spawnCount >= 15)
   - Rats never cluster in 3x3 to form
   - UNIQUE strategy unused!

2. **Static variable pollution risk**
   - All baby rats share same static vars
   - stuckRounds, cachedPositions, roundsSinceLastAttack
   - Works by accident (all rats update same values)
   - Could break if rats get out of sync

3. **Collectors still die**
   - No flee behavior
   - Replacement slow (1 per 50 rounds)
   - Late game starvation risk remains

### Medium Issues:

4. **Attacker suicide at 100 rounds**
   - Kills all attackers by round 150
   - Leaves us with only collectors
   - Enemy has full army, we don't

5. **Search pattern inefficient**
   - Spreading spiral around OLD enemy king position
   - Not actually searching map
   - Attackers wander aimlessly after king dies

6. **No Bug2 pathfinding**
   - Still using simple greedy movement
   - Gets stuck behind obstacles
   - Plan said to add Bug2, never did

## Lessons Learned from ratbot4

### Do More Of:

1. ✅ **Use ALL Battlecode API features**
   - Discovered bottomLeftDistanceSquaredTo (critical!)
   - Message metadata (getSenderID, getRound, getSource)
   - UnitType methods (isRatKingType, etc.)

2. ✅ **Optimize bytecode aggressively**
   - Started at 7,400/round (42%)
   - Ended at 3,700/round (21%)
   - Created room for features

3. ✅ **Test frequently**
   - 69 round victories prove fast kills
   - Found cooldown accumulation bug
   - Validated each optimization

4. ✅ **Extract constants**
   - All magic numbers named
   - Self-documenting
   - Easy to tune

### Do Less Of:

1. ❌ **Strict conditions for features**
   - 2nd king never forms (conditions impossible)
   - Make features actually trigger

2. ❌ **Suicide mechanics**
   - Attackers kill themselves
   - Leaves us weakened
   - Enemy keeps full army

3. ❌ **Ignoring pathfinding**
   - Said we'd add Bug2
   - Never did
   - Still get stuck

### New For ratbot5:

1. **Adaptive spawning**
   - Spawn more when losing combat
   - Spawn less when winning economy
   - Dynamic population

2. **Coordinated king formation**
   - Actually form 2nd king (not just code)
   - Explicit formation protocol
   - 7 rats cluster intentionally

3. **Cheese mine tracking**
   - Use shared array slots 40-59
   - Assign collectors to specific mines
   - No competition

4. **Better combat AI**
   - Focus fire (multiple rats, same target)
   - Retreat when outnumbered
   - Reinforce when ally fighting

## ratbot5 Requirements

### Primary Objectives:

1. **Form 2nd king** (unique competitive advantage)
2. **Win faster** (< 50 rounds vs current 69)
3. **Survive rush** (enemy kills us round 30 in some matches)
4. **Scale economy** (sustain 20+ rats, not 12-15)

### Strategy Changes:

**Population**: 15-20 rats (up from 12)
- 10 attackers (up from 6)
- 5-10 collectors (up from 6)
- Form 2nd king at round 50 (not 75)

**Combat**: Focus fire coordination
- Shared array slot 4: Primary target ID
- All attackers attack same enemy
- Kill enemies faster (8 rats × 13 damage = 104/round!)

**Economy**: Cheese mine specialization
- Track all mines (slots 40-59)
- Assign each collector to specific mine
- 2x collection rate

**Movement**: Bug2 pathfinding
- Integrate from ratbot2/utils/Pathfinding.java
- Use when stuck > 2 rounds
- Better obstacle navigation

### Implementation Plan Needed:

**Phase 1**: Core improvements (Week 2 - Jan 9-12)
- Increase spawn to 15 rats
- Add Bug2 pathfinding
- Fix 2nd king formation

**Phase 2**: Advanced features (Week 3 - Jan 13-19)
- Cheese mine tracking
- Focus fire coordination
- Adaptive spawning

**Phase 3**: Polish (Week 4 - Jan 20-31)
- Tune all parameters
- Test all strategies
- Tournament preparation

---

**Ready to create detailed ratbot5 implementation plan?**
