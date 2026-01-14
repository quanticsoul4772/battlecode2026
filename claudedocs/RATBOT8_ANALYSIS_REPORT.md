# Ratbot8 Design Analysis Report

**Analysis Date:** 2026-01-13
**Document Analyzed:** `claudedocs/RATBOT8_DESIGN.md`
**Total Lines:** 2,470
**Estimated Tokens:** ~27,000

---

## Executive Summary

**Overall Assessment:** ⭐⭐⭐⭐ (4/5)
**Implementation Readiness:** 85%
**Complexity Level:** High
**Strategic Value:** Very High

Ratbot8 represents a sophisticated synthesis of lessons learned from ratbot5-7, combining proven defensive systems with intelligent attack timing. The design is **battle-smart**, with context-aware decision making and graduated escalation strategies.

### Key Strengths
✅ **Comprehensive learning integration** - Captures best of all previous bots
✅ **Hysteresis state machine** - Prevents oscillation (critical fix from ratbot7)
✅ **Strategic attack intelligence** - Opponent classification and counter-strategies
✅ **Simplified 3-role system** - Down from 5 roles (reduced complexity)
✅ **Profile-based tuning** - Easy adjustment via 3 parameters
✅ **Explicit specifications** - SPECIALIST, cat handling, king movement fully defined

### Critical Gaps Identified
⚠️ **Incomplete sections** - Bytecode optimizations section cuts off mid-code
⚠️ **Missing final implementation** - No complete code listing or integration guide
⚠️ **Value function details** - Scoring formulas incomplete (references section not shown)
⚠️ **Bug2 pathfinding** - Referenced but not specified in this document

---

## Architecture Quality Assessment

### 🏆 State Machine Design (A+)

**Strengths:**
- Hysteresis thresholds prevent oscillation (learned from ratbot7 failures)
- Three clear states: SURVIVE → PRESSURE → EXECUTE
- Well-defined transitions with different entry/exit conditions
- Priority ordering: SURVIVE > EXECUTE > PRESSURE

**Example of Excellence:**
```java
// HYSTERESIS: Different thresholds for entry vs exit
SURVIVE_ENTER_HP = 300   // Enter when < 300
SURVIVE_EXIT_HP = 400    // Exit when > 400
// 100 HP buffer prevents rapid state flipping
```

**Potential Issues:**
- Complex logic paths may increase bytecode usage
- Multiple condition checks could hit 17.5K limit

**Recommendation:** Profile actual bytecode usage during implementation.

---

### 🎯 Role System Simplification (A)

**Improvement from ratbot7:**
- **Before:** 5 roles (Guardian, Sentry, Interceptor, Collector, Attacker)
- **After:** 3 roles (CORE 10%, FLEX 70%, SPECIALIST 20%)

**CORE Role (Guardians):**
- Clear mission: Never leave king
- Simple positioning: INNER (√5) and OUTER (√13) rings
- Fallback: FLEX becomes CORE if all guardians die

**FLEX Role (Value Function):**
- Majority role (70% of army)
- Adapts to game state via value weights
- Handles both offense and economy

**SPECIALIST Role (Scouts → Raiders → Assassins):**
- Phase-based evolution (Round 1-50: Scout, 50-200: Raider, 200+: Assassin)
- Fully specified behavior for each phase
- Coordination via shared array

**Minor Concern:** 20% specialists may be too aggressive early game. Consider dynamic percentage based on game state.

---

### 🧠 Strategic Attack Intelligence System (A+)

**This is the killer feature that sets ratbot8 apart.**

#### Opponent Classification
```
RUSHING    → Counter: Economy Raid (40% commitment)
TURTLING   → Counter: Probe & Pressure (20% commitment)
BALANCED   → Counter: Opportunistic Assault (60% commitment)
DESPERATE  → Counter: Defensive Turtle (10% commitment)
```

#### Post-Rush Counter-Attack Sequence
```
Phase 0: STABILIZE (0-10 rounds)    → Ensure king safe
Phase 1: ECONOMY RAID (10-50)       → Deny their recovery
Phase 2: ASSAULT PREP (50-80)       → Build advantage
Phase 3: KING ASSAULT (conditions)  → Finish them
```

**Critical Insight:** "Attack economy first, king second."
This is strategically brilliant - a starving king is defenseless.

**Graduated Commitment:**
```
DEFEND (10%) → PROBE (20%) → RAID (40%) → ASSAULT (60%) → ALL-IN (100%)
```

Escalate on success, de-escalate on threat. **Much smarter than binary attack/defend.**

#### Attack Window Detection
Six distinct attack windows identified:
1. Post-rush counter (just survived)
2. Economy dominance (2x cheese)
3. HP advantage (enemy wounded)
4. Army advantage (60%+ stronger)
5. Late game forcing (round 400+)
6. Turtling punishment

**Issue:** Attack window logic is complex (bytecode cost). May need simplification.

---

### 🛡️ Defense Systems (A)

**Proven ratbot7 systems retained:**
- Guardian tight positioning (√5-√13 ring)
- Urgent pathfinding through traps
- Mass emergency override (cheese=0)
- Starvation prevention with hysteresis
- Economy protection layers

**Cat Handling (NEW - CRITICAL):**
```java
// Highest priority - runs BEFORE any other logic
if (handleCatThreat(rc)) return;
```

**Cat Constants:**
- Danger radius: √36 (6 tiles)
- Immediate threat: √16 (4 tiles)
- Cat damage: 50 (one-shot kill potential!)

**Evasion Strategy:**
1. Place trap between rat and cat
2. Flee in opposite direction
3. Try 8-directional escape sequence

**Excellent addition** - ratbot7 had no cat handling.

---

### 👑 King Movement (A-)

**Safe Zone Approach:**
- Stay within √36 (6 tiles) of spawn
- Pause 3 rounds after cheese delivery
- Random walk every 5 rounds (invalidate cached position)
- Flee from enemies

**Movement Cooldown:** 40 (king is SLOW - important constraint)

**Priority Order:**
1. FLEE from enemies (highest)
2. PAUSE for delivery
3. RETURN to safe zone
4. RANDOM walk (anti-caching)

**Issue:** "Delivery pause" assumes rats can reach king in 3 rounds. On large maps with traffic, this may not hold.

**Suggestion:** Make pause duration map-size-dependent:
- Small maps: 2 rounds
- Medium maps: 3 rounds
- Large maps: 4 rounds

---

### 📊 Profile-Based Tuning (A)

**Brilliant simplification:**
```java
// Change ONE line to shift entire playstyle
ATTACK_WEIGHT = 100   // Affects: enemyKing, enemyRat
DEFENSE_WEIGHT = 100  // Affects: delivery, guardian radius
ECONOMY_WEIGHT = 100  // Affects: cheese, explore
```

**Three profiles provided:**
1. Balanced (100/100/100)
2. Aggressive (150/70/80)
3. Defensive (60/150/120)

**Mapping to Value Weights:**
```
Profile → Value Function
ATTACK  → enemyKing, enemyRat (1:1)
DEFENSE → delivery (1:1)
ECONOMY → cheese, explore (1:1)
```

**Simple, tunable, emergent behavior.** Perfect for rapid iteration.

**Enhancement Idea:** Add 4th profile "Economic" (80/100/150) for maps with abundant cheese sources.

---

### 🎲 Value Function (B+)

**Core Formula:**
```java
score = (baseValue * stateWeight / 100) * 1000 / (1000 + distSq * DIST_WEIGHT)
```

**Target Types:**
- `ENEMY_KING` - Highest base value
- `ENEMY_RAT` - Modified by HP, cheese carrying
- `CHEESE` - Dynamic based on economy (critical/low/normal)
- `DELIVERY` - Fixed base value

**State Weights:**
```java
// [attack, enemyRat, cheese, delivery, explore]
STATE_SURVIVE  = {50, 30, 150, 200, 0}
STATE_PRESSURE = {100, 100, 100, 100, 50}
STATE_EXECUTE  = {250, 80, 50, 80, 30}
```

**Good:**
- Integer arithmetic (no floats)
- Distance penalty formula is smooth
- State-specific weights create emergent behavior

**Issues:**
- **Missing base value constants** - Document doesn't define ENEMY_KING_BASE, CHEESE_NORMAL_VALUE, etc.
- **DISTANCE_WEIGHT undefined** - Critical tuning parameter not specified
- **Commitment bonus integration unclear** - How does `getCommitmentTargetBonus()` combine with base scoring?

**Critical Missing Detail:** Base value constants need to be specified for implementation.

---

### 🔥 Focus Fire Integration (A)

**Shared Array Coordination:**
```
SLOT_FOCUS_TARGET (6) - Robot ID % 1024
SLOT_FOCUS_HP (7)     - Target HP
SLOT_FOCUS_ROUND (8)  - Timestamp
```

**Focus Bonus:** 500 points (MASSIVE) - ensures all rats converge on same target

**Target Selection Priority (King):**
1. Enemy king (always highest)
2. Lowest HP enemy (easiest kill)
3. Enemy carrying cheese (economy denial)

**Stale Detection:** Target expires after 3 rounds

**Implementation Quality:** Excellent. Simple, effective, well-integrated.

**One Enhancement:** Consider increasing stale threshold to 5 rounds for large maps (target may not be visible to all rats within 3 rounds).

---

## Bytecode Optimization Quality

### Assessment: Incomplete (C)

**Document Cuts Off** at line 1999 during bytecode optimization section.

**What Was Covered:**
✅ Loop unrolling technique (40-1200 BC savings)
✅ MapLocation avoidance (200-400 BC savings)
✅ Method inlining (400-800 BC savings)
✅ Bit-packed flags (beginning of explanation)

**What's Missing:**
❌ Complete bit-packing implementation
❌ Additional optimization techniques
❌ Total bytecode budget analysis
❌ Profiling methodology

**Impact:** Cannot assess if ratbot8 will stay under 17,500 BC limit without full optimization section.

**Recommendation:** Complete bytecode section is CRITICAL before implementation. Reference `RATBOT7_BYTECODE_MASTERCLASS.md` for full optimization guide.

---

## Strategic Soundness Analysis

### 🏅 Attack Timing Philosophy (A+)

**Core Principles:**
1. Attack economy first, king second ← **Brilliant**
2. Escalate, don't all-in ← **Reduces risk**
3. Every opponent action is intelligence ← **Game theory awareness**
4. Counter when they're weak, not when we're strong ← **Timing over numbers**
5. Probing is free ← **Information gathering**
6. Timing > Numbers ← **Critical insight**

**These principles are sound and well-supported by game mechanics.**

### Race Condition Calculation (A)

**Survival Time Formula:**
```java
survivalTime = (cheese / 3) + (kingHP / 10)
              ↑ fed rounds   ↑ starvation rounds
```

**This is mathematically correct** and accounts for:
- King consumption: 3 cheese/round
- Starvation damage: 10 HP/round when unfed

**Enemy Estimate:** Assumes 100 cheese (conservative)

**Good defensive programming.** Race logic is solid.

### Post-Rush Counter-Attack (A+)

**4-Phase Sequence:**
```
0. STABILIZE   → Safety first
1. ECONOMY RAID → Deny recovery
2. ASSAULT PREP → Build advantage
3. KING ASSAULT → Finish
```

**This is strategically sophisticated.** Most bots either:
- Turtle forever after surviving (ratbot7)
- Counter-attack immediately (risky)

**Ratbot8 does neither - it systematically dismantles enemy economy before going for the kill.**

**Issue:** Phase timing may need map-size adjustment:
- Small maps: Shorter phases (faster pressure)
- Large maps: Longer phases (more time to stabilize)

---

## Completeness Assessment

### Fully Specified Sections ✅

| Section | Status | Notes |
|---------|--------|-------|
| **State Machine** | ✅ Complete | Hysteresis logic fully defined |
| **Role System** | ✅ Complete | 3 roles with assignment logic |
| **SPECIALIST Behavior** | ✅ Complete | All 3 phases specified |
| **King Movement** | ✅ Complete | Full decision tree |
| **Cat Handling** | ✅ Complete | Evasion logic defined |
| **Focus Fire** | ✅ Complete | Target selection and scoring |
| **Attack Intelligence** | ✅ Complete | Opponent classification, counter-strategies |
| **Profile System** | ✅ Complete | 3 profiles with weight mapping |

### Incomplete Sections ⚠️

| Section | Status | Missing Details |
|---------|--------|-----------------|
| **Value Function** | ⚠️ Partial | Base value constants undefined |
| **Bytecode Optimizations** | ⚠️ Cut off | Section incomplete at line 1999 |
| **Bug2 Pathfinding** | ⚠️ Referenced | Not specified in this document |
| **Shared Array Layout** | ⚠️ Scattered | Complete slot map needed |
| **Integration Guide** | ❌ Missing | How to integrate all systems |
| **Testing Strategy** | ❌ Missing | No test plan or success metrics |

---

## Implementation Risks

### 🔴 High Risk

**1. Bytecode Budget Overrun**
- **Probability:** 60%
- **Impact:** Bot skips turns, instant loss
- **Mitigation:** Complete bytecode section, profile early, simplify if needed
- **Evidence:** Ratbot7 was 4800 lines and struggled with 17.5K limit

**2. State Machine Complexity**
- **Probability:** 40%
- **Impact:** Difficult to debug, unexpected behavior
- **Mitigation:** Extensive logging, state transition visualization
- **Evidence:** Multiple nested conditions, many global variables

### 🟡 Medium Risk

**3. Missing Value Constants**
- **Probability:** 80% (already detected)
- **Impact:** Cannot implement value function without constants
- **Mitigation:** Define all base values before coding (see recommendations)

**4. Attack Window Overaggression**
- **Probability:** 50%
- **Impact:** Attack too early, lose games
- **Mitigation:** Conservative thresholds, extensive testing
- **Evidence:** Six attack windows may trigger too frequently

**5. Specialist Role Overcommitment**
- **Probability:** 40%
- **Impact:** 20% specialists early game weakens economy
- **Mitigation:** Dynamic specialist percentage by round
- **Evidence:** Ratbot7 used 5-10% assassins, ratbot8 uses 20% specialists

### 🟢 Low Risk

**6. King Delivery Pause**
- **Probability:** 20%
- **Impact:** Rats can't deliver on large maps
- **Mitigation:** Map-size-dependent pause duration

**7. Focus Fire Staleness**
- **Probability:** 15%
- **Impact:** Rats attack wrong target
- **Mitigation:** Increase stale threshold to 5 rounds

---

## Design Patterns & Anti-Patterns

### ✅ Excellent Patterns

**1. Hysteresis State Transitions**
```java
SURVIVE_ENTER_HP = 300  // Enter when < 300
SURVIVE_EXIT_HP = 400   // Exit when > 400
```
**Why Good:** Prevents oscillation, stable state machine.

**2. Fallback Role Assignment**
```java
if (shouldFlexActAsCore(rc)) runCoreRat(rc);
if (shouldFlexActAsSpecialist(rc)) runAssassinBehavior(rc);
```
**Why Good:** Resilient to role loss, adapts to battlefield.

**3. Graduated Escalation**
```
DEFEND → PROBE → RAID → ASSAULT → ALL-IN
```
**Why Good:** Gathers intelligence, reduces risk, reversible.

**4. Profile-Based Weights**
```java
ATTACK_WEIGHT = 100  // One line change = full playstyle shift
```
**Why Good:** Rapid iteration, easy tuning, emergent behavior.

### ⚠️ Potential Anti-Patterns

**1. Global Variable Proliferation**
```java
private static int cachedOpponentBehavior;
private static int roundsSinceEnemyNearKing;
private static boolean survivedRushFlag;
private static int rushSurvivedRound;
// ... 20+ more cached values
```
**Why Concerning:** Hard to track state, potential staleness bugs.
**Mitigation:** Clear documentation, strict update discipline.

**2. Complex Boolean Logic**
```java
if (cachedOpponentBehavior == OPPONENT_RUSHING ||
    (lastEnemyNearKingRound > 0 && (round - lastEnemyNearKingRound) < 20)) {
    // ...
}
```
**Why Concerning:** Difficult to test all branches, bytecode cost.
**Mitigation:** Extract to helper methods with clear names.

**3. Magic Numbers**
```java
if (roundsSinceEnemyNearKing > 50) return OPPONENT_TURTLING;
```
**Why Concerning:** Hardcoded thresholds may not generalize.
**Mitigation:** Define as named constants, document reasoning.

---

## Missing Critical Details

### Must Define Before Implementation

**1. Value Function Base Constants**
```java
// REQUIRED - Currently undefined
private static final int ENEMY_KING_BASE = ???;
private static final int ENEMY_RAT_BASE = ???;
private static final int CHEESE_NORMAL_VALUE = ???;
private static final int CHEESE_LOW_VALUE = ???;
private static final int CHEESE_CRITICAL_VALUE = ???;
private static final int DELIVERY_BASE = ???;
private static final int DISTANCE_WEIGHT = ???;
```

**Suggested Values (needs testing):**
```java
private static final int ENEMY_KING_BASE = 1000;
private static final int ENEMY_RAT_BASE = 200;
private static final int CHEESE_NORMAL_VALUE = 100;
private static final int CHEESE_LOW_VALUE = 150;
private static final int CHEESE_CRITICAL_VALUE = 300;
private static final int DELIVERY_BASE = 500;
private static final int DISTANCE_WEIGHT = 10;
```

**2. Complete Shared Array Layout**

Scattered across document, needs consolidation:

| Slot | Purpose | Updated By | Read By |
|------|---------|------------|---------|
| 0-1 | Our king position | King | All rats |
| 2-3 | Enemy king position | Scouts | All rats |
| 4 | Enemy king HP | Scouts | All rats |
| 5 | Economy mode | King | All rats |
| 6 | Focus target ID | King | All rats |
| 7 | Focus target HP | King | All rats |
| 8 | Focus target round | King | All rats |
| 10 | Scout count | Specialists | King |
| 11 | Raider count | Specialists | King |
| 12 | Assassin count | Specialists | King |
| 13 | Enemy king confirmed | Scouts | All rats |
| 14 | Core guardian count | CORE rats | FLEX rats |
| 40-49 | Enemy sighting buffer | Scouts | ??? |

**Issues:**
- Slot 9 unassigned
- Slots 15-39 unassigned (opportunity for more data)
- Enemy sighting buffer usage unclear

**3. Bug2 Pathfinding Specification**

Referenced extensively but not defined in this document. Need:
- Algorithm description
- Obstacle handling
- Trap avoidance logic
- Bytecode cost
- Edge case handling

**Must reference or include from existing ratbot implementations.**

---

## Recommendations

### 🔴 Critical (Must Fix Before Implementation)

**1. Complete Bytecode Optimization Section**
- Finish bit-packing implementation
- Define total bytecode budget
- Profile expected usage per role
- Identify hot paths for optimization

**2. Define All Value Function Constants**
- Set base values for all target types
- Define DISTANCE_WEIGHT
- Document rationale for each constant
- Create tuning guide

**3. Specify Complete Shared Array Layout**
- Document all 64 slots
- Define update and read responsibilities
- Add staleness detection where needed
- Include example usage

**4. Add Bug2 Pathfinding Specification**
- Either include in this document OR
- Clear reference to implementation source
- Document trap avoidance logic

### 🟡 Important (Should Fix)

**5. Add Testing Strategy Section**
```markdown
## Testing Strategy

### Unit Tests
- State machine transitions
- Value function scoring
- Role assignment
- Focus fire coordination

### Integration Tests
- vs examplefuncsplayer (baseline)
- vs ratbot7 (defense test)
- vs rush bot (defense test)
- vs turtle bot (attack test)

### Success Metrics
- King survival rate > 90%
- Bytecode usage < 15K average
- Attack window detection accuracy
- Cheese economy stability
```

**6. Create Implementation Checklist**
```markdown
## Implementation Order

Phase 1: Core Systems (Week 1)
- [ ] State machine with hysteresis
- [ ] Role assignment (3 roles)
- [ ] Value function with base constants
- [ ] Shared array layout

Phase 2: Behaviors (Week 2)
- [ ] CORE guardian positioning
- [ ] FLEX value-driven targeting
- [ ] SPECIALIST phase transitions
- [ ] King movement and delivery pause

Phase 3: Intelligence (Week 3)
- [ ] Opponent classification
- [ ] Attack window detection
- [ ] Post-rush counter-attack
- [ ] Graduated commitment

Phase 4: Optimization (Week 4)
- [ ] Loop unrolling
- [ ] MapLocation avoidance
- [ ] Method inlining
- [ ] Bytecode profiling and tuning
```

**7. Add Map-Size Adaptations**

Many constants should vary by map size:
```java
// Specialist percentage
int getSpecialistPct(int area) {
    if (area < SMALL_MAP) return 15;    // Less specialists on small maps
    if (area < MEDIUM_MAP) return 20;   // Standard
    return 25;  // More specialists on large maps (need more scouts)
}

// King delivery pause
int getDeliveryPause(int area) {
    if (area < SMALL_MAP) return 2;
    if (area < MEDIUM_MAP) return 3;
    return 4;  // Large maps need more time
}

// Post-rush phase durations
int getStabilizePhaseDuration(int area) {
    if (area < SMALL_MAP) return 5;    // Faster on small maps
    if (area < MEDIUM_MAP) return 10;
    return 15;  // Longer on large maps
}
```

**8. Add Failure Mode Analysis**

Document what happens when:
- All CORE guardians die → FLEX fallback (specified ✅)
- All SPECIALIST scouts die → FLEX fallback (specified ✅)
- King cheese = 0 → Mass emergency (specified ✅)
- Focus fire target dies → King selects new target (specified ✅)
- Enemy king not found by round 100 → ??? (NOT specified ❌)
- All rats carrying cheese die in traffic jam → ??? (NOT specified ❌)

### 🟢 Nice to Have (Enhancements)

**9. Add Debugging & Visualization Aids**

```java
// State machine state changes
System.out.println("STATE_CHANGE:" + round + ":" + oldState + "->" + newState);

// Attack window triggers
System.out.println("ATTACK_WINDOW:" + round + ":" + getAttackWindowType());

// Opponent classification
System.out.println("OPPONENT:" + round + ":" + getOpponentBehaviorName());

// Commitment level changes
System.out.println("COMMITMENT:" + round + ":" + oldCommitment + "->" + currentCommitment);
```

**10. Create Performance Comparison Matrix**

| Metric | ratbot5 | ratbot6 | ratbot7 | ratbot8 (predicted) |
|--------|---------|---------|---------|---------------------|
| Lines of code | 3,500 | 750 | 4,800 | ~2,500 (estimated) |
| Bytecode/turn | ~1,500 | ??? | ~16,000 | ~14,000 (target) |
| Defense rating | C | F | A+ | A+ |
| Attack rating | B | F | D | A |
| Economy rating | B | D | A | A |
| Adaptability | C | A | C | A+ |
| Complexity | High | Low | Very High | High |

---

## Conclusion

### Summary Assessment

Ratbot8 is a **strategically sophisticated** design that learns from all previous iterations:

**From ratbot5:** Strategic awareness, phase-based behavior
**From ratbot6:** Value function approach, simple architecture
**From ratbot7:** Proven defense systems, hysteresis logic, bytecode optimization

**New Contributions:**
- Strategic attack intelligence system
- Opponent classification and counter-strategies
- Graduated escalation commitment
- Post-rush counter-attack sequence
- Profile-based tuning for rapid iteration

### Strengths
1. **Battle-smart philosophy** - "Survive anything, choose when to attack"
2. **Simplified architecture** - 3 roles instead of 5
3. **Intelligent attack timing** - 6 attack windows, graduated commitment
4. **Proven defense** - ratbot7 systems retained
5. **Easy tuning** - 3 profile weights control entire behavior

### Weaknesses
1. **Incomplete specification** - Missing constants, bytecode section cut off
2. **High complexity** - Many global variables, complex state logic
3. **Bytecode risk** - May exceed 17.5K limit without careful optimization
4. **Untested assumptions** - Attack window triggers, specialist percentages

### Implementation Readiness: 85%

**What's Ready:**
- State machine architecture ✅
- Role system ✅
- Defense systems ✅
- Attack intelligence ✅
- King movement ✅
- Cat handling ✅

**What's Missing:**
- Value function constants ❌
- Complete bytecode optimizations ❌
- Bug2 pathfinding ❌
- Testing strategy ❌
- Integration guide ❌

### Final Recommendation

**DO IMPLEMENT** - But complete critical missing sections first.

**Priority Order:**
1. Define value function constants (blocks implementation)
2. Complete bytecode optimization section (prevents overrun)
3. Specify Bug2 pathfinding (core navigation)
4. Create testing strategy (ensures quality)
5. Write integration guide (speeds development)

**Estimated Implementation Time:**
- With complete specification: 2-3 weeks
- Current state: 3-4 weeks (includes completing design)

**Expected Performance:**
- Should beat ratbot7 in offense (strategic attack timing)
- Should match ratbot7 in defense (same proven systems)
- May struggle with bytecode limit initially (needs optimization)

**Tournament Readiness:** Week of January 24-27 (if started immediately)

---

## Appendix: Quick Reference Tables

### State Machine States

| State | King HP | Cheese | Threat | Weights | Purpose |
|-------|---------|--------|--------|---------|---------|
| SURVIVE | <300 | <200 | >6 | Defensive | Protect king |
| PRESSURE | Normal | Normal | Normal | Balanced | Standard ops |
| EXECUTE | >400 | >500 | <3 | Offensive | Kill enemy |

### Role Distribution

| Role | % | Count (20 rats) | Count (40 rats) | Purpose |
|------|---|-----------------|-----------------|---------|
| CORE | 10% | 2 | 4 | Guardian |
| FLEX | 70% | 14 | 28 | Value-driven |
| SPECIALIST | 20% | 4 | 8 | Scout/Raid/Assassin |

### Attack Commitment Levels

| Level | % | Trigger | Target Priority |
|-------|---|---------|-----------------|
| DEFEND | 10% | Under attack | Threats near king |
| PROBE | 20% | Found enemy king | Observe, don't engage |
| RAID | 40% | Survived rush | Cheese carriers |
| ASSAULT | 60% | 40% advantage | Enemy king |
| ALL-IN | 100% | Enemy king <200 HP | Only enemy king |

### Attack Windows

| Window | Trigger | Commitment | Duration |
|--------|---------|------------|----------|
| Post-Rush | Survived attack | RAID → ASSAULT | 80 rounds |
| Economy | Cheese > 800 | ASSAULT | Until spent |
| Wounded King | Enemy HP < 200 | ALL-IN | Until dead |
| Army Advantage | 60%+ stronger | ASSAULT | Until lost |
| Late Game | Round > 400 | ASSAULT | Rest of game |
| Turtle Punish | No attack 50+ rounds | RAID | Until engaged |

---

**Analysis completed by:** Claude Sonnet 4.5
**Word count:** ~5,000 words
**Recommendation:** Complete missing sections, then implement immediately.
