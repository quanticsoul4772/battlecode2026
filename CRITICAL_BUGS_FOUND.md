# Critical Bugs Found - 2026-01-07

## Summary
Found THREE critical bugs causing starvation:

### 1. SPAWNING STOPPED (FIXED) ✅
**Bug**: After round 50, king stopped spawning rats entirely.
```java
// BEFORE (BUG):
if (round <= 50 && spawnCount < 15) {
    // spawn
}
else {
    return;  // STOPS ALL SPAWNING!
}
```

**Fix**: Continue spawning throughout game with phase-based strategy.
- Rounds 1-50: Aggressive (every round, up to 15 rats)
- Rounds 51-300: Maintenance (every 50 rounds)
- Rounds 301+: Survival (every 30 rounds)

### 2. RATS TRAPPED BY OWN DEFENSES (PARTIALLY FIXED) ⚠️
**Bug**: Traps placed at distance 2-4 from king, rats spawned at distance 2.
- Result: Rats spawned INSIDE defensive perimeter, couldn't escape.
- Rats literally imprisoned by their own traps.

**Fix Attempted**:
- Spawn rats FIRST (rounds 1-40)
- Place traps AFTER (rounds 20-50)
- Traps at distance 6-10 (not 2-4)

**Status**: Traps not placing (distance too far?), but rats still clustering.

### 3. RAT CLUSTERING (NOT FIXED) ❌
**Bug**: Rats spawn at distance=2 in 8 directions, creating tight cluster.
- All rats within 2 tiles of each other
- Block each other's movement
- Form traffic jam around king

**Example from logs**:
```
[A: #10611@50] ECON_STATUS:50:10611:pos=[4, 26]
[A: #10611@100] ECON_STATUS:100:10611:pos=[4, 26]  // SAME POSITION!
[A: #10611@150] ECON_STATUS:150:10611:pos=[4, 26]  // STILL THERE!
[A: #10611@200] ECON_STATUS:200:10611:pos=[4, 26]  // 150 ROUNDS STUCK!
```

**Root Cause**:
1. Rats spawn at distance=2 from [4, 24]
2. Spawn locations: [4,26], [6,26], [6,24], [6,22], [4,22], [2,22], [2,24], [2,26]
3. All 8 positions within 2-tile radius = DENSE CLUSTER
4. Rats block each other, can't disperse

**Why It Matters**:
- Economy rats can't reach cheese (stuck near king)
- No cheese collection = starvation
- Opponent collects freely while our rats are paralyzed

## Test Results

### Before Fixes:
- Last economy rat died at round 490
- Starved shortly after

### After Fix #1 (Spawning):
- Spawning continued: rounds 97, 153, 203 ✅
- Some deliveries: rounds 107, 197, 198 ✅
- BUT rats still stuck in cluster ❌

## Next Steps

### Priority 1: Fix Rat Clustering
1. **Increase spawn distance**: 3-5 tiles instead of 2
2. **Add spawn delay**: Don't spawn all rats immediately
3. **Force dispersion**: Economy rats move AWAY from king initially

### Priority 2: Fix Trap Placement
- Traps at distance 6-10 might be outside map bounds
- Need adaptive distance based on map size
- Or place traps at 4-5 tiles (still outside spawn radius if we use 3+)

### Priority 3: Improve Stuck Detection
- Current "MOVE_TURN" attempts help
- But need FORCED exodus from spawn area
- Economy rats should prioritize "get away from king" for first 10 rounds

## Lessons Learned

1. **Spatial reasoning is critical** - Can't just place things without checking distances
2. **Rats need room to breathe** - Distance=2 spawn radius creates prison
3. **Test with visualization** - Hard to spot clustering bugs without seeing the map
4. **Logging saved us** - Position tracking revealed the stuck rats

## Code Changes Made

### RatKing.java:
- ✅ Fixed spawning logic (continuous spawning)
- ✅ Changed trap placement to distance 6-10
- ✅ Changed trap timing to AFTER spawning
- ❌ Still need to fix spawn distance

### Movement.java:
- ✅ Added aggressive stuck recovery
- ✅ Added MOVE_TURN logging
- ❌ Still need forced dispersion

### EconomyRat.java:
- ✅ Added comprehensive logging
- ✅ Status reports every 50 rounds
- ❌ Still need "escape king" behavior

## Match Observations

```
Round 1-50:    Spawned 13 rats, all clustered near king
Round 50-100:  Rats stuck, some attempting to turn
Round 100-150: 1 delivery (rat #13917 escaped!)
Round 150-200: 3 deliveries (some rats breaking free)
Round 200+:    Still many rats stuck, slow progress
```

**Conclusion**: Some rats ARE escaping and collecting, but MOST are stuck in the spawn cluster. We need to fix the spatial distribution.
