# Battlecode 2026 API Structure

**Source**: Official javadoc package hierarchy
**Package**: battlecode.common

---

## Complete Type Hierarchy

### Classes (7 total)

```
java.lang.Object
├── Clock
├── GameConstants
├── MapInfo
├── MapLocation (implements Comparable<T>, Serializable)
├── Message
├── RobotInfo
└── Throwable
    └── Exception
        └── GameActionException
```

**Clock**:
- Singleton for bytecode tracking
- Methods: getBytecodeNum(), yield()

**GameConstants**:
- All gameplay parameters
- 60+ constants (see Constants.java)

**MapInfo**:
- Map square state
- Fields: passable, dirt, traps, cheese

**MapLocation**:
- Immutable 2D coordinates
- Implements Comparable (for sorting)
- Methods: add(), directionTo(), distanceSquaredTo()

**Message**:
- Communication data
- Fields: content, sender ID, location, round

**RobotInfo**:
- Sensed robot data
- Fields: type, health, team, location, facing, carried robot

**GameActionException**:
- Game interaction errors
- Has typed error categories (GameActionExceptionType)

---

### Interface (1 total)

**RobotController**:
- Primary robot control interface
- ~80+ methods for:
  - Movement (move, turn, canMove, etc.)
  - Sensing (senseNearbyRobots, senseMapInfo, etc.)
  - Actions (attack, pickUpCheese, etc.)
  - Building (buildRobot, placeDirt, etc.)
  - Communication (squeak, readSharedArray, etc.)
  - Ratnapping (carryRat, throwRat, etc.)
  - Status (getLocation, getHealth, etc.)

---

### Enums (5 total)

```
java.lang.Enum
├── Direction
├── GameActionExceptionType
├── Team
├── TrapType
└── UnitType
```

**Direction**:
- 9 values: NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST, CENTER
- Methods: rotateLeft(), rotateRight(), opposite(), getDeltaX(), getDeltaY()

**GameActionExceptionType**:
- Error categories for GameActionException
- Types: CANT_DO_THAT, OUT_OF_RANGE, etc.

**Team**:
- 3 values: A, B, NEUTRAL
- Methods: opponent(), isPlayer()

**TrapType**:
- 3 values: CAT_TRAP, RAT_TRAP, NONE
- Fields: damage, cooldown, cost, stunDuration, triggerRadius

**UnitType**:
- 3 values: BABY_RAT, RAT_KING, CAT
- Fields: health, size, visionConeRadiusSquared, visionConeAngle, actionCooldown, movementCooldown, turningCooldown, bytecodeLimit
- Methods: isBabyRatType(), isRatKingType(), isCatType(), isThrowingType(), isThrowableType()

---

## TrapType Details (from enum)

### CAT_TRAP
- **Cost**: 10 cheese (from PDF)
- **Damage**: 100
- **Stun Duration**: 20 movement cooldown
- **Trigger Radius**: sqrt(2)
- **Cooldown**: 10 (placement)
- **Limit**: 10 per team

### RAT_TRAP
- **Cost**: 5 cheese (from PDF)
- **Damage**: 50
- **Stun Duration**: 20 movement cooldown
- **Trigger Radius**: sqrt(2)
- **Cooldown**: 5 (placement)
- **Limit**: 25 per team

### NONE
- Represents absence of trap

---

## Our Placeholder Types Status

### What We Have ✓
- Direction (complete with ordinal tracking)
- MapLocation (add, directionTo, distanceSquaredTo, equals)
- UnitType (BABY_RAT, RAT_KING, CAT)
- Team (A, B, NEUTRAL)
- RobotInfo (location, type, team, health, facing)
- MapInfo (passable, cheese, dirt, wall flags)
- Message (content, location, round)

### What We're Missing
- **TrapType** enum - Not needed in algorithm modules
- **GameActionException** - Only needed for error handling
- **GameActionExceptionType** - Error categories
- **Clock** - Will use in actual game for profiling

### Why Missing Types Don't Matter

**TrapType**: Used for trap queries, not in core algorithms
- Our modules don't need to know trap types
- Bot implementation will use it directly

**GameActionException**: Try-catch in bot code only
- Algorithm modules don't throw these
- RobotController methods throw them

**Clock**: Profiling utility
- Not needed in standalone algorithms
- Will use in actual bot for bytecode tracking

**Result**: Our placeholder types are sufficient for algorithm development

---

## Integration Checklist

### When Replacing Placeholders

Remove from algorithm files:
```java
// DELETE these placeholder sections
enum UnitType { BABY_RAT, RAT_KING, CAT }
enum Direction { NORTH(0), ... }
enum Team { A, B, NEUTRAL }
class MapLocation { ... }
class RobotInfo { ... }
class MapInfo { ... }
```

Add real imports:
```java
import battlecode.common.*;
```

**No other changes needed** - our algorithms use types correctly

---

## MockRobotController Coverage

### Implemented Methods (~30)

**Movement**:
- canMove(), move(), canTurn(), turn()
- isMovementReady(), isTurningReady()

**Sensing**:
- senseNearbyRobots(), senseRobotAtLocation()
- senseMapInfo(), sensePassability()
- canSenseLocation()

**Actions**:
- canAttack(), attack()
- canPickUpCheese(), pickUpCheese()
- canTransferCheese(), transferCheese()

**Ratnapping**:
- canCarryRat(), carryRat()
- canThrowRat(), throwRat()
- getCarrying(), isBeingCarried()

**Building**:
- canBuildRobot(), buildRobot()
- canPlaceDirt(), placeDirt()
- canRemoveDirt(), removeDirt()
- canPlaceRatTrap(), placeRatTrap()

**Communication**:
- readSharedArray(), writeSharedArray()
- squeak(), readSqueaks()

**Status**:
- getLocation(), getDirection(), getType(), getTeam()
- getHealth(), getRawCheese(), getGlobalCheese()
- getRoundNum(), getMapWidth(), getMapHeight()

### Not Implemented (yet)

**Advanced**:
- becomeRatKing() - King formation
- Cat traps (have rat traps)
- Drop rat (have throw)
- Indicator methods (debugging)

**Reason**: Core functionality covered, can add as needed

---

## API Coverage Assessment

### Algorithm Modules Need
- MapLocation ✓
- Direction ✓
- Basic math ✓

### Bot Implementation Will Need
- RobotController (full interface)
- All enums (Direction, Team, UnitType, TrapType)
- RobotInfo for sensing
- MapInfo for map queries
- Message for communication
- GameActionException for error handling
- Clock for bytecode profiling

### Mock Framework Provides
- RobotController simulation (30+ methods)
- Game state simulation
- Enough for algorithm testing

**Conclusion**: We have what we need for pre-scaffold development

---

## Complete battlecode.common Package

**Total Types**: 12
- 7 classes
- 1 interface (RobotController - the main API)
- 5 enums

**All documented in**:
- Constants.java (numerical values)
- This file (structure and relationships)
- Our placeholder types (standalone development)

**Ready to import**: Just `import battlecode.common.*;` when scaffold available

---

## Summary

**API Structure**: Fully understood and documented
**Placeholder Types**: Match real API structure
**Mock Framework**: Covers 30+ core methods
**Constants**: All 60+ values verified from javadoc

**Integration**: Remove placeholders, add import, done in <15 minutes

**Next**: Wait for scaffold or continue building automation tools (Days 3-4 plan)
