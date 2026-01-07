# Complete Restart Plan - Battlecode 2026

**Date**: January 6, 2026 (Sprint 1 in 6 days)
**Current Status**: Bot functional but fundamentally wrong strategy
**Decision**: Start fresh with proper game understanding

---

## What We Learned (Hard-Won Knowledge)

### Game Mechanics (From Spec + Testing)

1. **Scoring is RELATIVE not absolute**
   - % damage = ourDamage / (ourDamage + enemyDamage)
   - Competition is a RACE against opponent, not just killing cats
   - 50% of cooperation score comes from cat damage RATIO

2. **3x3 King Spawning**
   - King's 3x3 footprint occupies distance=1 tiles
   - Must spawn at distance=2: `pos.add(dir).add(dir)`
   - This was CRITICAL - took hours to discover

3. **Vision Cone Limitations**
   - Baby rats: 90° cone only
   - Must be FACING target to attack
   - Cats often outside vision even when nearby

4. **Movement + Attack Timing**
   - Can't turn AND move same round (cooldown conflict)
   - Must turn one round, move/attack next round
   - `moveToward()` needs to return after turn

5. **Transfer Range**
   - CHEESE_DROP_RADIUS_SQUARED = 9 (3 tiles)
   - Rats stuck at dist²=52 couldn't deliver
   - Shared array king position necessary

6. **Spawn Economics**
   - API allows spawning every turn (cooldown 10)
   - Starting 2,500 cheese should fuel army growth
   - Spawn limiting BLOCKS all spawns (killed competition)

7. **Cat Defense Mechanics**
   - 10 damage attacks are USELESS (need 1,000 attacks)
   - Cat traps: 100 damage each (10 max = 1,000 damage)
   - Throwing rats stuns cat 2 turns (tactical delay)
   - Traps are THE primary cat damage source

8. **Cooperation vs Backstab**
   - Game starts cooperation (50% cat damage weight)
   - Backstab early = lose 20% weight on cat damage
   - Never backstab before round 500+
   - Enemy may backstab us (watch for it)

### Technical Discoveries

9. **Debugging System**
   - Visual indicators essential (dots, lines, timeline)
   - Debug levels prevent spam
   - Client replay critical for understanding behavior

10. **Test Coverage**
    - Mock framework enables rapid testing
    - 111 tests passing but tested wrong strategy
    - Integration tests valuable for full behavior validation

---

## What to Keep vs Discard

### KEEP (Proven Valuable)

**Infrastructure:**
- ✅ Debug.java & DebugConfig.java (debugging system)
- ✅ RobotUtil.java (sensing utilities)
- ✅ BehaviorConfig.java (configuration constants)
- ✅ Constants.java (game constants)
- ✅ Logger.java & Profiler.java (performance tracking)
- ✅ Mock framework (testing infrastructure)

**Algorithms:**
- ✅ Vision.java (cone calculations - CRITICAL for attacks)
- ✅ DirectionUtil.java (turn optimization)
- ✅ Geometry.java (distance calculations)
- ✅ Pathfinding.java (Bug2, BFS - may need for advanced movement)
- ✅ GameTheory.java (backstab scoring - correct formulas!)

**Key Discoveries:**
- ✅ Spawn distance=2 fix (king 3x3 footprint)
- ✅ Movement return after turn
- ✅ Shared array communication
- ✅ Cat tracking system
- ✅ Emergency circuit breaker

### DISCARD (Wrong Strategy)

**RatKing.java:**
- ❌ Economic spawn limiting (killed competition)
- ❌ Repositioning priority (wasted early game)
- ❌ Conservative trap placement (only 2 traps)
- ❌ Survival-first mentality (wrong objective)

**BabyRat.java:**
- ❌ Cheese-first state machine (wrong priority)
- ❌ No dedicated combat rats (all multi-task)
- ❌ EXPLORE default state (should be ATTACK)
- ❌ Cheese collection overrides combat

**Strategy:**
- ❌ Economic hoarding (2,500 starting cheese should be spent)
- ❌ Spawn limiting gates (interval, buffer)
- ❌ Defensive positioning (should be aggressive)

---

## Starting Point Options

### Option 1: Soft Reset (Recommended)

**Keep:**
- All algorithm modules (Vision, Pathfinding, etc.)
- Debug system
- RobotUtil, Logger, Constants
- Test infrastructure
- Spawn distance=2 fix
- Movement fix

**Rewrite:**
- RatKing.java (aggressive spawning, 10 traps, no limiting)
- BabyRat.java (combat-first state machine, dedicated roles)
- Strategy focus (damage race, not survival)

**Time**: 2-3 hours
**Risk**: Medium (keep proven code, fix strategy)

### Option 2: Hard Reset (From Scaffold)

**Start from:**
- `examplefuncsplayer/` as template
- Clean slate

**Reimplement:**
- Everything from scratch
- But WITH game understanding this time

**Time**: 8-12 hours
**Risk**: High (lose all working code, might hit same bugs)

### Option 3: Git Rollback (Middle Ground)

**Find commit BEFORE spawn limiting:**
```bash
git log --oneline | grep "before limiting"
```

**Rollback to:**
- Working spawning
- Basic movement
- No complex features

**Add:**
- Correct strategy
- Cat traps
- Combat focus

**Time**: 4-6 hours
**Risk**: Medium (known good baseline)

---

## Recommended Approach: Soft Reset

**Why:**
- Keep proven discoveries (spawn distance=2, vision checks, movement)
- Keep infrastructure (debug, logging, utilities)
- Rewrite just the strategy layer (RatKing, BabyRat)
- Can reference working code for patterns

**What We'd Rewrite:**

**RatKing.java** (~200 lines):
```java
public static void run(RobotController rc) {
    // 1. Emergency check (cheese < 100)
    // 2. Spawn every turn (aggressive)
    // 3. Place 10 cat traps (rounds 15-25)
    // 4. Track cats (already have)
    // 5. Broadcast position (already have)
}
```

**BabyRat.java** (~300 lines):
```java
// Role assignment at spawn
static int ratRole = assignRole(rc.getID());

public static void run(RobotController rc) {
    if (ratRole == COMBAT_RAT) {
        // Full-time cat attacker (70% of rats)
        findAndAttackCats(rc);
    } else if (ratRole == ECONOMY_RAT) {
        // Full-time cheese collector (30% of rats)
        collectAndDeliverCheese(rc);
    }
}
```

---

## The Clean Rewrite Plan

### Phase 1: Strategy Design (30 min)

**Create spec-based strategy document:**
1. Cooperation objectives (race to out-damage enemy)
2. Resource allocation (2,500 cheese budget)
3. Army composition (70% combat, 30% economy)
4. Trap strategy (10 traps, strategic placement)
5. Backstab timing (round 700-900, if winning)

### Phase 2: Core Rewrite (2 hours)

**RatKing.java:**
- Aggressive spawning (every turn)
- 10 trap placement (rounds 15-25)
- Cat tracking (keep existing)
- No repositioning waste

**BabyRat.java:**
- Role-based assignment (ID % 10 determines role)
- Combat rats: Full-time cat attackers
- Economy rats: Full-time collectors
- Simple, focused behavior per role

### Phase 3: Testing & Tuning (1 hour)

- Test spawn rate (40+ rats by round 50?)
- Test trap placement (10 placed?)
- Test cat damage (out-damage enemy?)
- Test king survival (2,000 rounds?)

### Phase 4: Competition (30 min)

- Create submission
- Upload to scrimmages
- Analyze results
- Iterate

**Total Time**: 4 hours

---

## Success Criteria for Restart

**By End of Session:**
- ✅ 40+ rats by round 50
- ✅ 10 cat traps placed by round 25
- ✅ 1,000+ trap damage guaranteed
- ✅ 70% of rats dedicated to cat combat
- ✅ Cat damage >50% of total (winning the race)
- ✅ King survives to round 500+

**For Sprint 1 (Jan 12):**
- ✅ Competitive with scrimmage opponents
- ✅ Cooperation score >40
- ✅ Understand when to backstab
- ✅ Top 50% placement

---

## What Do You Want to Do?

**Option A**: Soft reset - Keep algorithms/utils, rewrite RatKing + BabyRat (~3 hours)
**Option B**: Hard reset - Start from examplefuncsplayer (~10 hours)
**Option C**: Git rollback - Find good commit, build forward (~4 hours)
**Option D**: Something else - Your call

I will NOT touch code until you decide.
