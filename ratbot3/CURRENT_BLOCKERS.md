# ratbot3 - Current Blockers

## Primary Blocker: Final Approach Navigation

**Both attack AND collect strategies blocked by same issue:**

### Attack Rats:
- Get to dist=4-5 from enemy king
- Need dist<=2 for attack (adjacent)
- Stuck at dist=4 barrier, can't close final 2 tiles
- Result: Zero attacks landing

### Collect Rats:
- Get to dist=4-5 from own king
- Need dist<=9 for transfer
- Stuck at dist=4-5 barrier, can't close to dist<=9
- Result: 14 deliveries (was 163 before distance-reducing navigation)

## Root Cause

**Simple movement can't handle precise final positioning:**
- Obstacles (traps, walls, other rats) around kings
- Need to navigate final 1-3 tiles with precision
- Current movement: greedy + stuck recovery
- Not enough for complex final approach

## What We've Tried:

1. ✅ Simple turn+move → Works for long distance
2. ✅ Direct move() → Better, still fails at close range
3. ✅ Distance-reducing search → Gets close but stuck
4. ✅ Flanking/repositioning → Helps but doesn't breakthrough
5. ❌ **Precision final approach** → NOT WORKING

## What's Needed:

**Sophisticated pathfinding for final 10 tiles:**
- BFS with obstacle avoidance
- Coordinate multiple rats (clear path for delivery)
- Alternative targets (attack king edge vs center)
- Persistence (keep trying different angles)

## Current State:

**Working**:
- Spawn control (10 rats)
- Role assignment (50/50)
- Cheese collection
- Long-range navigation
- Stuck recovery

**Broken**:
- Final approach (last 2-3 tiles)
- Attack execution (zero attacks)
- Delivery completion (14 vs 163)

**Next**: Either implement proper BFS pathfinding or accept that we can't execute close-range actions reliably.
