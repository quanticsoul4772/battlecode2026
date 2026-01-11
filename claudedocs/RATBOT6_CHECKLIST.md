# Ratbot6 Implementation Checklist

Track progress implementing ratbot6 based on RATBOT6_DESIGN.md.

---

## Phase 1: MVP (Get it working)

### 1.1 File Setup
- [ ] Create `scaffold/src/ratbot6/RobotPlayer.java`
- [ ] Add package declaration and imports
- [ ] Add `run()` entry point method
- [ ] Add `RobotType` switch for king vs baby rat

### 1.2 Constants
- [ ] Add shared array slot constants (`SLOT_OUR_KING_X`, etc.)
- [ ] Add value function constants (`ENEMY_KING_BASE_VALUE`, etc.)
- [ ] Add threshold constants (`LOW_ECONOMY_THRESHOLD`, etc.)
- [ ] Add behavioral constants (`DISTANCE_WEIGHT_INT`, etc.)
- [ ] Add `DIRECTIONS` array

### 1.3 Static Fields (No Allocation)
- [ ] `cachedOurCheese`, `cachedRound`, `cachedCarryingCheese`
- [ ] `cachedOurKingLoc`, `cachedEnemyKingLoc`, `cachedEnemyKingHP`
- [ ] `cachedDistToOurKing`, `cachedOurTeam`, `cachedEnemyTeam`
- [ ] `cachedBestTarget`, `cachedBestTargetType`, `cachedBestScore`
- [ ] `cachedEconomyMode`
- [ ] Bug2 state fields (`bug2Target`, `bug2WallFollowing`, etc.)

### 1.4 Game State Management
- [ ] Implement `updateGameState(rc)` - cache all variables
- [ ] Implement `initializeRobot(rc)` - first turn setup
- [ ] Implement `getEnemyKingEstimate(rc)` - symmetry-based fallback

### 1.5 Value Function
- [ ] Implement `scoreTarget(targetType, distanceSq)` - integer math
- [ ] Implement `getBaseValue(targetType)` - switch on target type
- [ ] Implement `scoreAllTargets(rc, enemies, cheeseLocs)`
- [ ] Add target type constants (`TARGET_ENEMY_KING`, etc.)

### 1.6 Immediate Actions
- [ ] Implement `tryImmediateAction(rc, enemies, cheeseLocs, focusTargetId)`
- [ ] Implement `tryAttack(rc, enemies, focusTargetId)`
- [ ] Implement `tryDeliverCheese(rc)` - with `canTransferCheese` check
- [ ] Implement `tryCollectCheese(rc, cheeseLocs)`
- [ ] Implement `tryDigDirt(rc)`

### 1.7 Bug2 Pathfinding (REQUIRED)
- [ ] Implement `bug2MoveTo(rc, target)`
- [ ] Implement `canMoveSafely(rc, dir)` - with trap avoidance
- [ ] Handle wall following state transitions
- [ ] Remove recursive call (just reset flag and return)

### 1.8 Vision Cone Management
- [ ] Implement `tryTurnTowardTarget(rc)`
- [ ] Implement `getAngleDifference(a, b)`

### 1.9 King Behavior (Basic)
- [ ] Implement `runKing(rc)` main loop
- [ ] Implement `broadcastKingPosition(rc)`
- [ ] Implement `trySpawnRat(rc)`
- [ ] Implement `getSpawnCost(rc)`

### 1.10 Baby Rat Main Loop
- [ ] Implement `runBabyRat(rc)` with all components connected
- [ ] Sensing enemies and cheese (once, not twice)
- [ ] Call immediate actions, scoring, movement in order

### 1.11 MVP Testing
- [ ] Build successfully (`gradlew build -x test`)
- [ ] Run vs examplefuncsplayer - must win
- [ ] Fix any runtime errors

---

## Phase 2: Core Features

### 2.1 Focus Fire System
- [ ] Implement `updateFocusFireTarget(rc, enemies)` - king broadcasts target
- [ ] Implement `isFocusTarget(enemy, focusTargetId)`
- [ ] Add focus fire bonus to attack targeting
- [ ] Add `SLOT_FOCUS_TARGET` and `SLOT_FOCUS_HP` writes

### 2.2 Mobile King
- [ ] Implement `evadeFromEnemies(rc, enemies)`
- [ ] Implement `randomMoveInSafeZone(rc)`
- [ ] Add `kingSpawnPoint` tracking
- [ ] Add `lastDeliveryRound` tracking for delivery pause
- [ ] Implement `tryKingMove(rc, dir)` with trap avoidance

### 2.3 Interceptor Behavior
- [ ] Implement `shouldIntercept(rc)` - ID % 5 == 0
- [ ] Implement `runInterceptor(rc, enemies)`
- [ ] Patrol near our king when no enemies nearby

### 2.4 Cat Handling
- [ ] Implement `findDangerousCat(neutrals)`
- [ ] Implement `fleeFromCat(rc, cat)`
- [ ] Add cat check at start of baby rat turn

### 2.5 Economy Management
- [ ] Implement `updateEconomyMode(rc)` - king updates slot 5
- [ ] Implement `broadcastEnemyKing(rc)` - king broadcasts if visible
- [ ] Add cheese exhaustion fallback to `scoreAllTargets`

### 2.6 Trap Placement
- [ ] Implement `tryPlaceTrap(rc)` - defensive traps near king

### 2.7 Phase 2 Testing
- [ ] Run vs ratbot4 - must win consistently
- [ ] Test on DefaultSmall, DefaultMedium, DefaultLarge
- [ ] Verify focus fire is working (enemies die faster)
- [ ] Verify interceptors defend king

---

## Phase 3: Optimization & Tuning

### 3.1 Bytecode Optimization
- [ ] Enable PROFILE mode and measure bytecode usage
- [ ] Target < 1500 bytecode/turn for baby rats
- [ ] Remove any remaining float operations
- [ ] Minimize API calls per turn

### 3.2 Constant Tuning
- [ ] Tune `ENEMY_KING_BASE_VALUE` for attack aggression
- [ ] Tune `CHEESE_VALUE_NORMAL` vs `CHEESE_VALUE_LOW_ECONOMY`
- [ ] Tune `LOW_ECONOMY_THRESHOLD` and `CRITICAL_ECONOMY_THRESHOLD`
- [ ] Tune `INTERCEPTOR_RANGE_SQ` for defense radius
- [ ] Tune `KING_SAFE_ZONE_RADIUS_SQ` for king mobility

### 3.3 Map-Specific Adjustments
- [ ] Implement `detectMapSymmetry(rc)` if needed
- [ ] Adjust safe zone based on map size
- [ ] Test on all tournament maps

### 3.4 Final Testing
- [ ] Run vs ratbot5 Team A - must win
- [ ] Run vs ratbot5 Team B - must win
- [ ] Verify < 800 lines of code
- [ ] Verify all success criteria from design doc

---

## Validation Checklist

### Functionality Tests
- [ ] Rats move toward enemy king
- [ ] Rats collect cheese when economy is low
- [ ] Rats deliver cheese to our king
- [ ] Rats dig through dirt walls
- [ ] Rats attack enemies (focus fire working)
- [ ] Rats avoid traps
- [ ] Rats flee from cats
- [ ] King spawns rats
- [ ] King places traps when enemies nearby
- [ ] King moves to evade enemies
- [ ] King stays in safe zone
- [ ] Interceptors defend our king

### Edge Case Tests
- [ ] Works when spawned as Team A
- [ ] Works when spawned as Team B
- [ ] Handles enemy king not visible (uses estimate)
- [ ] Handles all cheese collected (attack mode)
- [ ] Handles low economy (collection priority)
- [ ] Handles critical economy (emergency mode)
- [ ] Doesn't walk into traps
- [ ] Doesn't get stuck on walls (Bug2 working)
- [ ] Vision cone properly managed (rats turn to see)

### Performance Tests
- [ ] Baby rat bytecode < 1500/turn average
- [ ] King bytecode < 15000/turn average
- [ ] No bytecode exceeded warnings
- [ ] No `new` object allocation in main loop (except emergencies)

---

## Success Criteria (from Design Doc)

- [ ] Wins against ratbot5 on all maps, both Team A and Team B
- [ ] Code is under 800 lines
- [ ] Bytecode usage under 1500/turn average for baby rats
- [ ] No float arithmetic in hot paths
- [ ] No object allocation in main loop
- [ ] Vision cone properly handled
- [ ] Constants are clearly documented and easy to tune

---

## Quick Reference: File Location

```
scaffold/src/ratbot6/RobotPlayer.java
```

## Quick Reference: Build & Test Commands

```bash
# Build
cd scaffold && gradlew build -x test

# Run vs examplefuncsplayer
gradlew run -PteamA=ratbot6 -PteamB=examplefuncsplayer -Pmaps=DefaultMedium

# Run vs ratbot4
gradlew run -PteamA=ratbot6 -PteamB=ratbot4 -Pmaps=DefaultMedium

# Run vs ratbot5 (Team A)
gradlew run -PteamA=ratbot6 -PteamB=ratbot5 -Pmaps=DefaultMedium

# Run vs ratbot5 (Team B)
gradlew run -PteamA=ratbot5 -PteamB=ratbot6 -Pmaps=DefaultMedium

# Create submission
gradlew zipForSubmit
```

---

## Notes

- Reference RATBOT6_DESIGN.md for pseudocode
- Reference ratbot5/RobotPlayer.java for API usage patterns
- Test frequently - don't implement too much before testing
- Commit after each phase milestone
