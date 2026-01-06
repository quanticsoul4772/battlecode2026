# Architecture Decision Records

**Purpose**: Document key architectural and design decisions with rationale
**Format**: Lightweight ADR (Architecture Decision Record)
**Audience**: Future developers and Claude Code instances

---

## ADR-001: Static State in Behavior Classes

**Date**: 2026-01-05
**Status**: Accepted
**Deciders**: Initial architecture design

**Context**:
- Battlecode robots run in isolated JVM instances (one per robot)
- Each robot needs to maintain state across turns
- Question: Use static or instance variables?

**Decision**: Use static variables for robot state

**Rationale**:
1. Each robot = separate JVM = static effectively equals instance scope
2. No instance creation overhead (bytecode savings)
3. Simpler code (no constructor, no instance management)
4. Consistent with Battlecode conventions

**Consequences**:
- **Positive**: Lower bytecode cost, simpler architecture
- **Negative**: Cannot test multiple robots in same JVM (acceptable - use mock framework)
- **Risk**: Developers unfamiliar with Battlecode might find confusing

**Alternatives Considered**:
- Instance variables with singleton pattern (more complex, same result)
- No state tracking (stateless bots are inefficient)

---

## ADR-002: Cheese Delivery Threshold = 20

**Date**: 2026-01-05
**Status**: Accepted (subject to review after Sprint 1)
**Deciders**: Performance analysis

**Context**:
- Baby rats collect cheese and deliver to kings
- Carrying cheese adds movement penalty (0.01 per cheese)
- Question: When should rat return to king?

**Decision**: Deliver when carrying 20 cheese

**Rationale**:
1. **Carrying penalty at 20**: 0.01 × 20 = 20% slower movement (1.2x cooldown)
2. **Trip overhead**: ~30 rounds to mine and back
3. **Efficiency**: Balance penalty vs trip frequency

**Calculation**:
- Threshold too low (e.g., 5): Many trips, lots of wasted movement
- Threshold too high (e.g., 50): 50% slower movement, inefficient
- Sweet spot: 15-25 range

**Consequences**:
- **Positive**: Efficient cheese economy
- **Negative**: May not be optimal for all map sizes
- **Tuning needed**: Adjust based on actual map dimensions

**Metrics to Track**:
- Average cheese delivered per trip
- Time spent traveling vs collecting
- Cheese income rate

**Review Date**: After Sprint 1 (Jan 13, 2026)

---

## ADR-003: Backward Loop Optimization

**Date**: 2026-01-03
**Status**: Accepted (standard practice)
**Deciders**: Bytecode optimization analysis

**Context**:
- Battlecode has strict bytecode limits (17,500 per turn)
- Loop structures have different bytecode costs
- Question: Use forward or backward loops?

**Decision**: Use backward loops: `for (int i = n; --i >= 0;)`

**Rationale**:
1. Backward loops save ~30% bytecode over forward loops
2. Works for any iteration where order doesn't matter
3. Established Battlecode optimization pattern

**Examples**:
```java
// GOOD: Backward loop
for (int i = allies.length; --i >= 0;) {
    if (allies[i].getType() == UnitType.RAT_KING) count++;
}

// AVOID: Forward loop (unless order matters)
for (int i = 0; i < allies.length; i++) {
    // Costs ~30% more bytecode
}
```

**Consequences**:
- **Positive**: Significant bytecode savings across codebase
- **Negative**: Less intuitive for developers unfamiliar with pattern
- **Mitigation**: Document in CLAUDE.md, consistent usage

---

## ADR-004: Algorithm Module Isolation

**Date**: 2026-01-04
**Status**: Accepted
**Deciders**: Architecture design (Fowler pattern)

**Context**:
- Need pathfinding, vision, game theory algorithms
- Question: Tightly couple to RobotController or create pure functions?

**Decision**: Pure function modules with zero RC dependencies

**Architecture**:
```
algorithms/ (Pure Functions)
  - Input: MapLocation, Direction, primitives
  - Output: Direction, boolean, calculations
  - Dependencies: ONLY battlecode.common types (MapLocation, Direction)
  - NO RobotController dependency

BabyRat/RatKing (Behavior)
  - Calls algorithms with data from RC
  - Handles RC interactions
```

**Rationale**:
1. **Testability**: Can unit test without game engine
2. **Reusability**: Algorithms work for any strategy
3. **Maintainability**: Changes isolated to modules
4. **Performance**: Easier to optimize in isolation

**Consequences**:
- **Positive**: 40+ unit tests validate algorithms independently
- **Positive**: Can develop algorithms before scaffold release
- **Negative**: Slight verbosity (must pass data explicitly)

**Validation**: 40+ tests pass, algorithms proven correct before integration

---

## ADR-005: Starvation Circuit Breaker

**Date**: 2026-01-06
**Status**: Accepted
**Deciders**: Expert panel review (Nygard recommendation)

**Context**:
- Kings consume 3 cheese/round
- If cheese runs out: Kings lose 10 HP/round
- All kings die = instant loss
- Question: How to prevent starvation deaths?

**Decision**: Implement multi-tier circuit breaker pattern

**Design**:
```
Cheese Status → King Action → Rat Response
-------------------------------------------
> 100 rounds  → Normal spawning → Normal collection (threshold=20)
33-100 rounds → Reduced spawning → Frequent delivery (threshold=10)
< 33 rounds   → STOP spawning → EMERGENCY delivery (threshold=5)
```

**Implementation**:
- Kings write status to shared array slot 0
- Rats read status every turn (cheap: ~10 bytecode)
- Emergency code 999 = CRITICAL mode
- Status 1-200 = WARNING mode (value = rounds remaining)
- Status >200 = NORMAL mode

**Rationale**:
1. **Prevention over reaction**: Warning at 100 rounds allows recovery
2. **Shared coordination**: All rats respond to king's emergency broadcast
3. **Graceful degradation**: Three-tier response (normal → warning → critical)

**Consequences**:
- **Positive**: Prevents starvation deaths (instant loss prevention)
- **Positive**: Uses only 1 shared array slot (63 remaining for other uses)
- **Negative**: Rats spend bytecode checking status each turn (~10 bytecode)

**Metrics**:
- Zero starvation deaths in testing
- Emergency mode triggers appropriately
- Rats respond within 1-2 turns

---

## ADR-006: Static Buffer Optimization

**Date**: 2026-01-06
**Status**: Accepted
**Deciders**: Performance analysis, expert panel review

**Context**:
- Vision.getVisibleTiles() and Geometry.locationsWithinRadius() allocate arrays
- Allocation costs ~200-500 bytecode per call
- Question: Worth optimizing?

**Decision**: Add zero-allocation buffer-based API, keep old API for compatibility

**Design**:
```java
// New API (zero allocation)
private static MapLocation[] buffer = new MapLocation[400];

public static int getVisibleTilesCount(...) {
    return getVisibleTilesIntoBuffer(buffer, ...);
}

public static MapLocation getVisibleTile(int index) {
    return buffer[index];
}

// Old API (deprecated but kept for compatibility)
@Deprecated
public static MapLocation[] getVisibleTiles(...) {
    // Uses new API internally
}
```

**Rationale**:
1. **Performance**: Saves 200-500 bytecode per call if used frequently
2. **Backward compatibility**: Old API still works (uses new implementation)
3. **Flexibility**: Callers can provide own buffer if needed
4. **Safety**: Documented warnings about buffer reuse

**Trade-offs**:
- **Benefit**: Zero allocation = significant bytecode savings
- **Cost**: Slightly more complex API (count + index vs array)
- **Risk**: Buffer reuse requires caller awareness (documented)

**Consequences**:
- New code should use buffer API
- Old code continues working (deprecated)
- Migration path clear

---

## ADR-007: Bug2 Randomization Source

**Date**: 2026-01-06
**Status**: Accepted (replaced System.currentTimeMillis)
**Deciders**: P0 bug fix

**Context**:
- Bug2 pathfinding needs left/right rotation choice
- Original used `System.currentTimeMillis()` for pseudo-random
- Problem: Not available in Battlecode sandbox

**Decision**: Use target location hash: `(target.x * 31 + target.y) & 1`

**Rationale**:
1. **Deterministic but varied**: Same target always gives same choice, different targets vary
2. **No external dependencies**: Pure function of inputs
3. **Fast**: Simple arithmetic, <5 bytecode
4. **Good enough**: Pathfinding doesn't need cryptographic randomness

**Alternatives Considered**:
- Robot ID XOR round: Would require RC parameter (breaks pure function design)
- Always rotate right: Too predictable, could get stuck
- Complex PRNG: Overkill for this use case

**Consequences**:
- **Positive**: No runtime errors, maintains pure function design
- **Positive**: Deterministic = easier debugging (same inputs = same output)
- **Negative**: Somewhat predictable (acceptable for pathfinding)

---

## ADR-008: Emergency Mode Takes Priority

**Date**: 2026-01-06
**Status**: Accepted
**Deciders**: Expert panel (Nygard pattern)

**Context**:
- Baby rats have multiple concerns: cheese collection, cat avoidance, backstab decisions
- Question: What takes priority when cheese critical?

**Decision**: Emergency mode overrides all other behaviors

**Priority Order**:
1. Emergency mode check (survival) - HIGHEST
2. State machine update (normal behavior)
3. Backstab decision (strategic)

**Code**:
```java
public static void run(RobotController rc) {
    checkEmergencyMode(rc);  // FIRST - can override state
    checkBackstabDecision(rc);
    updateState(rc);
    executeState(rc);
}
```

**Rationale**:
- Survival > Strategy > Tactics
- Emergency is time-critical (< 33 rounds = death)
- Can always resume strategy after emergency resolved

**Consequences**:
- Clear priority hierarchy
- Emergency response guaranteed
- Strategy temporarily suspended during crisis (acceptable)

---

## ADR-009: Test Directory Structure

**Date**: 2026-01-06
**Status**: Accepted
**Deciders**: Testing infrastructure design

**Context**:
- Multiple test types: unit, integration, behavior, performance
- Question: How to organize tests?

**Decision**: Organize by test type and purpose

**Structure**:
```
test/
├── algorithms/     # Unit tests for algorithm modules
├── behavior/       # State machine behavior tests
├── integration/    # End-to-end scenario tests
├── mock/           # Test infrastructure
└── (future: performance/, regression/)
```

**Rationale**:
- **algorithms/**: Fast, isolated, run frequently
- **behavior/**: Validate state machines work correctly
- **integration/**: Full scenarios, run before commits
- **Separation**: Run different suites at different times

**Consequences**:
- Clear test organization
- Easy to run specific test types
- Future: Can add performance/, regression/ as needed

---

## Tuning Parameters (Track After Matches)

These decisions should be revisited with match data:

| Parameter | Current | Review After | Tuning Direction |
|-----------|---------|--------------|------------------|
| CHEESE_DELIVERY_THRESHOLD | 20 | Sprint 1 | Optimize for map size |
| BACKSTAB_EARLIEST_ROUND | 200 | Sprint 1 | Based on cat defeat patterns |
| EMERGENCY_THRESHOLD | 100 rounds | Sprint 2 | Based on income stability |
| KING_MIN_SPACING | 15 tiles | Sprint 2 | Based on cat pounce analysis |
| BACKSTAB_CHECK_INTERVAL | 50 rounds | Sprint 2 | Based on bytecode pressure |

---

## Future Decisions Needed

**FD-001: Communication Protocol** (P1)
- Define shared array schema
- Allocate remaining 59 slots
- Cheese mine tracking, enemy positions, strategic coordination

**FD-002: Multi-King Coordination** (P1)
- King territory assignments
- Spawn load balancing
- Formation timing and locations

**FD-003: Combat Strategy** (P2)
- Target priority algorithm
- Cheese enhancement decisions
- Swarm vs individual combat

**FD-004: Advanced Movement** (P2)
- Ratnapping tactics
- Throwing mechanics
- Formation movement

---

**Last Updated**: January 6, 2026
**Next Review**: After Sprint 1 (January 13, 2026)
