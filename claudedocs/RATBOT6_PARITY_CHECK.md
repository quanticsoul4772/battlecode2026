# Ratbot6 vs Ratbot5 Feature Parity Check

**Based on**: Official Battlecode 2026 JavaDoc (v1.0.1)
**API Reference**: https://releases.battlecode.org/javadoc/battlecode26/1.0.1/battlecode/common/RobotController.html

---

## Available RobotController Methods (from used_methods.txt)

### Sensing Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.getLocation()` | ✅ | ✅ | |
| `rc.getDirection()` | ✅ | ✅ | |
| `rc.getHealth()` | ✅ | ✅ | Via `senseNearbyRobots` |
| `rc.getTeam()` | ✅ | ✅ | |
| `rc.getType()` | ✅ | ✅ | |
| `rc.getID()` | ✅ | ✅ | |
| `rc.getRoundNum()` | ✅ | ✅ | |
| `rc.getMapWidth()` | ✅ | ✅ | |
| `rc.getMapHeight()` | ✅ | ✅ | |
| `rc.getGlobalCheese()` | ✅ | ✅ | |
| `rc.getRawCheese()` | ✅ | ❌ | **GAP** - Could optimize cheese delivery |
| `rc.senseNearbyRobots()` | ✅ | ✅ | |
| `rc.senseRobotAtLocation()` | ✅ | ✅ | |
| `rc.senseNearbyMapInfos()` | ✅ | ❌ | **GAP** - Used for mine detection |
| `rc.senseMapInfo()` | ✅ | ✅ | |
| `rc.sensePassability()` | ✅ | ✅ | |
| `rc.canSenseLocation()` | ✅ | ✅ | |
| `rc.onTheMap()` | ✅ | ✅ | |
| `rc.isLocationOccupied()` | ✅ | ✅ | |
| `rc.adjacentLocation()` | ✅ | ✅ | |

### Movement Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canMoveForward()` | ✅ | ✅ | |
| `rc.moveForward()` | ✅ | ✅ | |
| `rc.canTurn()` | ✅ | ✅ | |
| `rc.turn()` | ✅ | ✅ | |
| `rc.isMovementReady()` | ✅ | ✅ | |

### Action Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canAttack()` | ✅ | ✅ | |
| `rc.attack()` | ✅ | ✅ | |
| `rc.isActionReady()` | ✅ | ✅ | |
| `rc.canBuildRat()` | ✅ | ✅ | |
| `rc.buildRat()` | ✅ | ✅ | |
| `rc.getCurrentRatCost()` | ✅ | ❌ | **GAP** - More accurate spawn decisions |
| `rc.canBecomeRatKing()` | ✅ | ❌ | Not relevant (single king strategy) |
| `rc.becomeRatKing()` | ✅ | ❌ | Not relevant |

### Trap Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canPlaceRatTrap()` | ✅ | ✅ | |
| `rc.placeRatTrap()` | ✅ | ✅ | |
| `rc.canRemoveRatTrap()` | ✅ | ❌ | Could recover traps |
| `rc.removeRatTrap()` | ✅ | ❌ | |
| `rc.canPlaceCatTrap()` | ✅ | ❌ | **GAP** - Cooperation mode |
| `rc.placeCatTrap()` | ✅ | ❌ | **GAP** |
| `rc.canRemoveCatTrap()` | ✅ | ❌ | |
| `rc.removeCatTrap()` | ✅ | ❌ | |

### Cheese Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canPickUpCheese()` | ✅ | ✅ | |
| `rc.pickUpCheese()` | ✅ | ✅ | |
| `rc.canTransferCheese()` | ✅ | ✅ | |
| `rc.transferCheese()` | ✅ | ✅ | |

### Ratnapping Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canCarryRat()` | ✅ | ❌ | **GAP** - Offensive ratnapping |
| `rc.carryRat()` | ✅ | ❌ | |
| `rc.canThrowRat()` | ✅ | ❌ | |
| `rc.throwRat()` | ✅ | ❌ | |
| `rc.getCarrying()` | ✅ | ❌ | |
| `rc.isBeingCarried()` | ✅ | ❌ | **GAP** - Waste bytecode if carried |
| `rc.isBeingThrown()` | ✅ | ❌ | **GAP** |

### Communication Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.readSharedArray()` | ✅ | ✅ | |
| `rc.writeSharedArray()` | ✅ | ✅ | |
| `rc.squeak()` | ✅ | ❌ | **CRITICAL GAP** |
| `rc.readSqueaks()` | ✅ | ❌ | **CRITICAL GAP** |

### Dirt Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.canRemoveDirt()` | ✅ | ❌ | Could be useful for terrain |
| `rc.removeDirt()` | ✅ | ❌ | |

### Game State Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.isCooperation()` | ✅ | ❌ | **GAP** - Different strategy per mode |

### Debug Methods
| Method | Ratbot5 | Ratbot6 | Notes |
|--------|---------|---------|-------|
| `rc.setIndicatorString()` | ✅ | ✅ | |
| `rc.setIndicatorLine()` | ✅ | ❌ | Could add for visualization |

---

## 🔴 CRITICAL GAPS (Highest Impact)

### 1. Squeak Communication System
**Ratbot5 Implementation:**
- Attackers squeak enemy king position when found
- Collectors relay squeaks when delivering cheese
- King reads squeaks and updates shared array
- All rats get fresh enemy king position

**Ratbot6 Status:** ❌ NOT IMPLEMENTED

**Impact:** This is likely THE main reason ratbot6 sometimes loses. Without squeak relay:
- Baby rats update their local cache only
- Info dies when the rat dies
- Stale enemy king position = rats wandering aimlessly
- No real-time intelligence sharing

**Methods Needed:**
```java
rc.squeak(int message)        // Send message (heard by allies + CATS!)
rc.readSqueaks(int radius)    // Read nearby squeaks (-1 for all)
```

**Implementation Effort:** ~100 lines
- Add squeak encoding/decoding (enemy king location)
- King listens for squeaks and updates shared array
- Throttle to avoid attracting cats
- Skip on small maps (not needed)

---

### 2. Cooperation Mode Detection
**Ratbot5 Implementation:**
- Checks `rc.isCooperation()` at game start
- Places cat traps (100 damage!) in cooperation mode
- Adjusts strategy based on game mode

**Ratbot6 Status:** ❌ NOT IMPLEMENTED

**Impact:** 
- Missing ~50% of scoring potential in cooperation games
- Cat traps do 100 damage vs rat traps' 50 damage
- Not adapting strategy to game mode

**Methods Needed:**
```java
rc.isCooperation()           // Check game mode
rc.canPlaceCatTrap(loc)      // Only works in cooperation
rc.placeCatTrap(loc)         // 100 damage trap
```

**Implementation Effort:** ~50 lines

---

### 3. Ratnapping Defense
**Ratbot5 Implementation:**
```java
if (rc.isBeingThrown() || rc.isBeingCarried()) {
    return; // Skip turn - we can't do anything useful
}
```

**Ratbot6 Status:** ❌ NOT IMPLEMENTED

**Impact:**
- Wastes bytecode when ratnapped
- Could still squeak while carried (useful for intel)

**Methods Needed:**
```java
rc.isBeingCarried()  // Check if being held
rc.isBeingThrown()   // Check if in flight
```

**Implementation Effort:** 2 lines!

---

## 🟡 MODERATE GAPS

### 4. Mine Detection & Prioritization
**Ratbot5 Implementation:**
- Uses `senseNearbyMapInfos()` to find cheese mines
- Broadcasts mine locations via shared array
- Collectors prioritize mines (renewable cheese)
- Denial patrol targets enemy-side mines

**Ratbot6 Status:** ❌ NOT IMPLEMENTED

**Methods Needed:**
```java
MapInfo[] infos = rc.senseNearbyMapInfos();
for (MapInfo info : infos) {
    if (info.hasCheeseMine()) {
        // Track mine location
    }
}
```

**Impact:** Mines spawn 5 cheese every ~69 rounds. Controlling mines = economy dominance.

**Implementation Effort:** ~40 lines

---

### 5. Get Current Rat Cost
**Ratbot5 Implementation:**
```java
int cost = rc.getCurrentRatCost();
if (cheese >= cost + RESERVE) {
    rc.buildRat(dir);
}
```

**Ratbot6 Status:** Uses manual calculation `10 + 10 * (allyCount / 4)`

**Impact:** More accurate spawn decisions, especially as ally count changes.

**Implementation Effort:** 1 line change

---

### 6. Get Raw Cheese
**Ratbot5 Implementation:**
- Uses `rc.getRawCheese()` to check how much cheese a rat is carrying
- Prioritizes delivery when carrying significant amount

**Ratbot6 Status:** ❌ NOT IMPLEMENTED

**Impact:** Could optimize cheese delivery timing.

**Implementation Effort:** ~10 lines

---

## 🟢 MINOR GAPS

### 7. Trap Removal
- `rc.canRemoveRatTrap()` / `rc.removeRatTrap()`
- Could recover 5 cheese by removing poorly-placed traps
- Low priority

### 8. Dirt Management
- `rc.canRemoveDirt()` / `rc.removeDirt()`
- Could clear paths or create defensive structures
- Low priority

### 9. Indicator Line
- `rc.setIndicatorLine(loc1, loc2, r, g, b)`
- Better debug visualization
- Non-functional, just debug

---

## Feature Comparison Summary

| Category | Ratbot5 | Ratbot6 | Gap |
|----------|---------|---------|-----|
| **Communication** | Squeak relay + 45 array slots | 9 array slots only | **CRITICAL** |
| **Game Mode** | Cooperation detection + cat traps | Ignores game mode | **HIGH** |
| **Ratnapping** | Defensive checks | None | **MEDIUM** |
| **Mine Detection** | Full tracking + denial | None | **MEDIUM** |
| **Role System** | Collector/Attacker/Assassin | Value function blending | Architectural |
| **Kiting** | Retreat turns after engagement | Attack or flee only | Low |
| **Economy** | Dynamic thresholds | Static thresholds | Low |

---

## Recommended Implementation Order

### Phase 1: Quick Wins (< 1 hour)
1. **Ratnapping defense** - 2 lines, prevents bytecode waste
2. **Use `getCurrentRatCost()`** - 1 line, more accurate spawning
3. **Cooperation mode check** - Cache `rc.isCooperation()` at init

### Phase 2: High Impact (2-4 hours)
4. **Squeak relay system** - ~100 lines, biggest impact on win rate
   - Encode enemy king position in squeak
   - King reads squeaks and updates shared array
   - Throttle to avoid attracting cats

5. **Cat trap placement** - ~50 lines
   - Only in cooperation mode
   - Place toward cat spawn areas
   - 100 damage vs 50 damage rat traps

### Phase 3: Optimization (4+ hours)
6. **Mine detection** - ~40 lines
   - Track mine locations
   - Prioritize collection from mines
   - Consider denial patrol

7. **Raw cheese awareness** - ~20 lines
   - Check `rc.getRawCheese()` for delivery priority
   - Avoid picking up more when already loaded

---

## API Method Quick Reference

```java
// CRITICAL - Not in ratbot6
rc.squeak(int message)              // Broadcast (allies + CATS hear it!)
Message[] msgs = rc.readSqueaks(-1) // Read all squeaks
boolean coop = rc.isCooperation()   // Check game mode
boolean carried = rc.isBeingCarried()
boolean thrown = rc.isBeingThrown()

// Traps (cooperation mode)
rc.canPlaceCatTrap(MapLocation)     // 100 damage trap
rc.placeCatTrap(MapLocation)

// Ratnapping (offensive)
rc.canCarryRat(RobotInfo)
rc.carryRat(RobotInfo)
rc.canThrowRat(Direction)
rc.throwRat(Direction)
RobotInfo carried = rc.getCarrying()

// Economy
int cost = rc.getCurrentRatCost()   // Exact spawn cost
int raw = rc.getRawCheese()         // Cheese being carried

// Mine detection
MapInfo[] infos = rc.senseNearbyMapInfos()
boolean mine = info.hasCheeseMine()
```

---

## Squeak Message Format (from Ratbot5)

```java
// Encoding: 4-bit type | 12-bit Y | 12-bit X | 4-bit extra
int squeak = (type << 28) | (y << 16) | (x << 4) | extra;

// Types:
// 1 = Enemy king position
// 3 = Mine location

// Decoding:
int type = (msg >> 28) & 0xF;
int y = (msg >> 16) & 0xFFF;
int x = (msg >> 4) & 0xFFF;
```

---

## Conclusion

The biggest gap between ratbot5 and ratbot6 is the **squeak relay system**. This allows ratbot5 to share real-time enemy king positions across the team, while ratbot6 relies on stale cached data that dies with each rat.

Implementing squeaks + cooperation mode detection would likely close most of the performance gap between the two bots.
