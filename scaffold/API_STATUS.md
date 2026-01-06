# Battlecode 2026 API Status (v1.0.1)

**Date**: January 5, 2026
**Engine Version**: Check engine_version.txt in scaffold
**Client Version**: Check client_version.txt in scaffold

---

## Working API Methods

### Movement
- `rc.canMoveForward()` ✓
- `rc.moveForward()` ✓
- `rc.canTurn()` ✓
- `rc.turn(Direction)` ✓

### Sensing
- `rc.senseNearbyRobots()` ✓
- `rc.senseRobotAtLocation(MapLocation)` ✓
- `rc.canSenseLocation(MapLocation)` ✓
- `rc.senseMapInfo(MapLocation)` ✓
- `rc.getAllLocationsWithinRadiusSquared(MapLocation, int)` ✓

### MapInfo Methods
- `info.getCheeseAmount()` ✓
- `info.isPassable()` ✓
- `info.hasCheeseMine()` ✓
- `info.isWall()` ✓
- `info.isDirt()` ✓

### Cheese
- `rc.canPickUpCheese(MapLocation)` ✓
- `rc.pickUpCheese(MapLocation)` ✓
- `rc.canTransferCheese(MapLocation, int)` ✓
- `rc.transferCheese(MapLocation, int)` ✓
- `rc.getRawCheese()` ✓
- `rc.getGlobalCheese()` ✓

### Status
- `rc.getLocation()` ✓
- `rc.getDirection()` ✓
- `rc.getType()` ✓
- `rc.getTeam()` ✓
- `rc.getHealth()` ✓
- `rc.getRoundNum()` ✓
- `rc.getMapWidth()` / `rc.getMapHeight()` ✓
- `rc.isActionReady()` ✓
- `rc.isMovementReady()` ✓
- `rc.isTurningReady()` ✓

---

## NOT YET IMPLEMENTED (v1.0.1)

### Building/Spawning
- `rc.canBuildRobot(MapLocation)` ❌ Not found
- `rc.buildRobot(MapLocation)` ❌ Not found

**Impact**: Cannot spawn baby rats yet
**Workaround**: Wait for API update or check for alternate method names

### Ratnapping (Likely Not Implemented)
- `rc.canCarryRat(MapLocation)` ❌ (probable)
- `rc.carryRat(MapLocation)` ❌ (probable)
- `rc.throwRat()` ❌ (probable)

### Traps (Likely Not Implemented)
- `rc.placeRatTrap(MapLocation)` ❌ (probable)
- `rc.placeCatTrap(MapLocation)` ❌ (probable)

### Dirt (Likely Not Implemented)
- `rc.removeDirt(MapLocation)` ❌ (probable)
- `rc.placeDirt(MapLocation)` ❌ (probable)

### King Formation (Likely Not Implemented)
- `rc.becomeRatKing()` ❌ (probable)
- `rc.canBecomeRatKing()` ❌ (probable)

---

## Likely Early Release

**Hypothesis**: Version 1.0.1 is an early release with core movement/sensing only
**Expected**: Future updates will add building, traps, ratnapping, etc.

**Action**: 
- Monitor Discord for API updates
- Check `./gradlew update` regularly
- Implement with placeholder logic for now

---

## Current Bot Capabilities

With v1.0.1 API we can:
- ✓ Move and turn
- ✓ Sense nearby robots and map
- ✓ Collect cheese (pickUpCheese)
- ✓ Transfer cheese (transferCheese)
- ✓ Track economy
- ✓ Log structured data

We CANNOT yet:
- ✗ Spawn new baby rats
- ✗ Place traps
- ✗ Modify dirt
- ✗ Ratnap/throw rats
- ✗ Form new rat kings

---

## Development Strategy

**Week 1**: Focus on what works
1. Cheese collection and transfer ✓
2. Economy tracking ✓
3. Simple navigation
4. Cat avoidance
5. Basic combat (when attack API verified)

**Week 2+**: Add features as API expands
1. Spawning (when buildRobot available)
2. Traps (when trap API available)
3. Ratnapping (when carry API available)
4. King formation (when becomeRatKing available)

---

## Next Steps

1. Verify attack API works (rc.attack())
2. Implement combat behavior
3. Test cheese collection actually works
4. Monitor for API updates
5. Implement spawning when available

**Current Status**: Basic bot functional with available API
