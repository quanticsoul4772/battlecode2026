package ratbot6;

import battlecode.common.*;
import java.util.Random;

/**
 * Ratbot6 - Value Function Architecture
 *
 * <p>Philosophy: Intelligence in the algorithm, not in roles. Every rat uses a single unified value
 * function to decide what to do. "Roles" emerge from game state, not from assignment.
 *
 * <p>Target: ~750 lines (vs ratbot5's 3500 lines)
 *
 * @see RATBOT6_DESIGN.md for full design documentation
 */
public class RobotPlayer {

  // ========================================================================
  // SHARED ARRAY SLOT CONSTANTS
  // ========================================================================
  private static final int SLOT_OUR_KING_X = 0;
  private static final int SLOT_OUR_KING_Y = 1;
  private static final int SLOT_ENEMY_KING_X = 2;
  private static final int SLOT_ENEMY_KING_Y = 3;
  private static final int SLOT_ENEMY_KING_HP = 4;
  private static final int SLOT_ECONOMY_MODE = 5;
  private static final int SLOT_FOCUS_TARGET = 6;
  private static final int SLOT_FOCUS_HP = 7;
  private static final int SLOT_ROUND = 8;
  private static final int SLOT_ENEMY_KING_ROUND = 9; // Round when enemy king was last seen

  // ========================================================================
  // SQUEAK CONSTANTS
  // ========================================================================
  private static final int SQUEAK_TYPE_ENEMY_KING = 1;
  // PERF: SQUEAK_TYPE_MINE removed - not used
  private static final int SQUEAK_THROTTLE_ROUNDS = 10; // Min rounds between squeaks
  private static final int MAX_SQUEAKS_TO_READ = 5; // Limit squeaks processed per turn

  // ========================================================================
  // CAT TRAP CONSTANTS
  // ========================================================================
  private static final int CAT_TRAP_TARGET = 5; // Target number of cat traps in coop mode

  // ========================================================================
  // VALUE FUNCTION CONSTANTS (Primary tuning knobs)
  // ========================================================================
  private static final int ENEMY_KING_BASE_VALUE = 200; // HIGH - kill the king!
  private static final int ENEMY_RAT_VALUE = 40; // Engage blocking enemies
  private static final int CHEESE_VALUE_NORMAL = 30; // Lower - prioritize attack!
  private static final int CHEESE_VALUE_LOW_ECONOMY = 50; // Still attack-focused
  private static final int CHEESE_VALUE_CRITICAL = 80; // Only critical matters
  private static final int DELIVERY_PRIORITY = 150;
  private static final int DIRT_VALUE = 5;
  private static final int FOCUS_FIRE_BONUS = 80; // Stronger focus fire = faster kills

  // ========================================================================
  // THRESHOLD CONSTANTS
  // ========================================================================
  private static final int LOW_ECONOMY_THRESHOLD = 300; // Much higher - prevent starvation
  private static final int CRITICAL_ECONOMY_THRESHOLD = 150; // ~50 rounds - earlier emergency
  private static final int DELIVERY_RANGE_SQ = 9; // 3 tiles - actual API limit
  private static final int WOUNDED_KING_HP =
      350; // Enemy king HP to all-in (70% HP) - EARLY aggression!
  private static final int INTERCEPTOR_RANGE_SQ = 100; // Distance² for interception

  // ========================================================================
  // BEHAVIORAL CONSTANTS
  // ========================================================================
  private static final int DISTANCE_WEIGHT_INT = 15; // Integer weight for distance
  private static final int ANTI_CLUMP_PENALTY = 50; // Penalty for tiles with allies
  private static final int SPAWN_CHEESE_RESERVE = 100; // Aggressive spawning - kill fast!
  private static final int FORWARD_MOVE_BONUS = 20; // Bonus for forward movement

  // ========================================================================
  // KING CONSTANTS
  // ========================================================================
  private static final int KING_SAFE_ZONE_RADIUS_SQ = 64; // King stays near spawn
  private static final int KING_DELIVERY_PAUSE_ROUNDS = 3; // Pause for delivery

  // ========================================================================
  // CAT CONSTANTS
  // ========================================================================
  private static final int CAT_DANGER_RADIUS_SQ = 100; // 10 tiles - flee range (increased from 36)
  private static final int CAT_CAUTION_RADIUS_SQ = 169; // 13 tiles - avoid moving toward cat
  private static final int CAT_POUNCE_RANGE_SQ = 9; // 3 tiles - instant kill range

  // ========================================================================
  // TRAP CONSTANTS
  // ========================================================================
  private static final int RAT_TRAP_EARLY_WINDOW = 20; // Place traps in first 20 rounds
  private static final int RAT_TRAP_TARGET = 10; // Target number of rat traps - more defense
  private static final int MIN_SPAWNS_BEFORE_TRAPS = 3; // Spawn rats before placing traps
  private static final int BABY_RAT_TRAP_COOLDOWN = 20; // Cooldown between baby rat trap placements

  // ========================================================================
  // TARGET TYPE CONSTANTS (avoid enum overhead)
  // ========================================================================
  private static final int TARGET_NONE = 0;
  private static final int TARGET_ENEMY_KING = 1;
  private static final int TARGET_ENEMY_RAT = 2;
  private static final int TARGET_CHEESE = 3;
  private static final int TARGET_DELIVERY = 4;
  private static final int TARGET_DIRT = 5;
  private static final int TARGET_FOCUS = 6;

  // ========================================================================
  // DIRECTION ARRAY
  // ========================================================================
  private static final Direction[] DIRECTIONS = {
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST
  };

  // ========================================================================
  // STATIC FIELDS - Cached Game State (no allocation per turn)
  // ========================================================================
  private static int cachedOurCheese;
  private static int cachedRound;
  private static boolean cachedCarryingCheese;
  private static MapLocation cachedOurKingLoc;
  private static MapLocation cachedEnemyKingLoc;
  private static int cachedEnemyKingHP;
  private static int cachedDistToOurKing;
  private static int cachedEconomyMode; // 0=normal, 1=low, 2=critical
  private static Team cachedOurTeam;
  private static Team cachedEnemyTeam;

  // PERF: Cache current location to avoid repeated rc.getLocation() calls
  private static MapLocation myLoc;
  private static int myLocX;
  private static int myLocY;

  // PERF: Cached king coordinates to avoid MapLocation allocation
  private static int cachedOurKingX;
  private static int cachedOurKingY;
  private static int cachedEnemyKingX;
  private static int cachedEnemyKingY;

  // Target scoring results (static to avoid allocation)
  private static MapLocation cachedBestTarget;
  private static int cachedBestTargetType;
  private static int cachedBestScore;

  // Bug2 pathfinding state
  private static MapLocation bug2Target;
  private static boolean bug2WallFollowing = false;
  private static Direction bug2WallDir;
  private static MapLocation bug2StartLoc;
  private static int bug2StartDist;

  // King state
  private static MapLocation kingSpawnPoint;
  private static int lastDeliveryRound = 0;
  private static int ratTrapCount = 0; // Track rat traps placed
  private static int spawnCount = 0; // Track rats spawned

  // Map symmetry (0=rotational, 1=horizontal, 2=vertical)
  private static int mapSymmetry = -1;
  private static MapLocation estimatedEnemyKingLoc;

  // RNG for random movement
  private static Random rng;

  // Trap placement arrays (avoid allocation)
  private static final Direction[] TRAP_PRIORITY_DIRS = new Direction[8];
  private static final Direction[] BABY_TRAP_DIRS = new Direction[3];

  // Baby rat trap state
  private static int lastBabyTrapRound = -100;
  private static int lastBabyTrapID = -1;

  // Initialization flag
  private static boolean initialized = false;

  // Cached map size flag (set once during init)
  private static boolean isLargeMap = false;

  // Cooperation mode cached at init
  private static boolean cachedIsCooperation = false;

  // Squeak state
  private static int lastSqueakRound = -100;
  private static int lastSqueakID = -1;

  // Cat trap count
  private static int catTrapCount = 0;

  // PERF: Mine tracking removed - not worth bytecode

  // Cat tracking for king and baby rats
  private static MapLocation lastKnownCatLoc = null;
  private static int lastCatSeenRound = -100;

  // ========================================================================
  // ENTRY POINT
  // ========================================================================

  /**
   * Main entry point - called by the game engine. Loops forever, handling exceptions gracefully.
   */
  @SuppressWarnings("unused")
  public static void run(RobotController rc) throws GameActionException {
    rng = new Random(rc.getID());

    while (true) {
      try {
        // Initialize on first turn
        if (!initialized) {
          initializeRobot(rc);
          initialized = true;
        }

        // Run the appropriate behavior based on robot type
        switch (rc.getType()) {
          case RAT_KING:
            runKing(rc);
            break;
          case BABY_RAT:
            runBabyRat(rc);
            break;
          default:
            break;
        }
      } catch (GameActionException e) {
        System.out.println("GameActionException: " + e.getMessage());
        e.printStackTrace();
      } catch (Exception e) {
        System.out.println("Exception: " + e.getMessage());
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ========================================================================
  // INITIALIZATION
  // ========================================================================

  /** Called once on first turn for each robot. */
  private static void initializeRobot(RobotController rc) throws GameActionException {
    cachedOurTeam = rc.getTeam();
    cachedEnemyTeam = cachedOurTeam.opponent();

    // Cache map size for interceptor ratio decision
    int mapArea = rc.getMapWidth() * rc.getMapHeight();
    isLargeMap = mapArea > 2500; // 50x50+

    // Cache cooperation mode - affects trap placement strategy
    cachedIsCooperation = rc.isCooperation();

    if (rc.getType().isRatKingType()) {
      // King initialization
      kingSpawnPoint = rc.getLocation();
      mapSymmetry = 0; // Assume rotational (most common)

      // Write initial position to shared array
      rc.writeSharedArray(SLOT_OUR_KING_X, rc.getLocation().x);
      rc.writeSharedArray(SLOT_OUR_KING_Y, rc.getLocation().y);

      // Calculate and broadcast estimated enemy king position
      cachedOurKingLoc = rc.getLocation();
      estimatedEnemyKingLoc = getEnemyKingEstimate(rc);
      if (estimatedEnemyKingLoc != null) {
        rc.writeSharedArray(SLOT_ENEMY_KING_X, estimatedEnemyKingLoc.x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, estimatedEnemyKingLoc.y);
      }
    }
  }

  // ========================================================================
  // GAME STATE MANAGEMENT
  // ========================================================================

  /**
   * Update all cached game state variables. Called at the start of each turn to minimize API calls.
   */
  private static void updateGameState(RobotController rc) throws GameActionException {
    cachedRound = rc.getRoundNum();
    myLoc = rc.getLocation();
    myLocX = myLoc.x;
    myLocY = myLoc.y;

    // Cheese state
    cachedCarryingCheese = rc.getRawCheese() > 0;
    cachedOurCheese = rc.getGlobalCheese();

    // Read our king position from shared array - avoid allocation
    cachedOurKingX = rc.readSharedArray(SLOT_OUR_KING_X);
    cachedOurKingY = rc.readSharedArray(SLOT_OUR_KING_Y);
    if (cachedOurKingX > 0 || cachedOurKingY > 0) {
      // PERF: Only allocate if position changed
      if (cachedOurKingLoc == null
          || cachedOurKingLoc.x != cachedOurKingX
          || cachedOurKingLoc.y != cachedOurKingY) {
        cachedOurKingLoc = new MapLocation(cachedOurKingX, cachedOurKingY);
      }
      // PERF: Inline distanceSquared calculation
      int dx = myLocX - cachedOurKingX;
      int dy = myLocY - cachedOurKingY;
      cachedDistToOurKing = dx * dx + dy * dy;
    }

    // Read enemy king position from shared array - avoid allocation
    cachedEnemyKingX = rc.readSharedArray(SLOT_ENEMY_KING_X);
    cachedEnemyKingY = rc.readSharedArray(SLOT_ENEMY_KING_Y);
    if (cachedEnemyKingX > 0 || cachedEnemyKingY > 0) {
      // PERF: Only allocate if position changed
      if (cachedEnemyKingLoc == null
          || cachedEnemyKingLoc.x != cachedEnemyKingX
          || cachedEnemyKingLoc.y != cachedEnemyKingY) {
        cachedEnemyKingLoc = new MapLocation(cachedEnemyKingX, cachedEnemyKingY);
      }
    } else {
      // Fallback to symmetry-based estimate
      cachedEnemyKingLoc = getEnemyKingEstimate(rc);
    }

    // Read enemy king HP
    cachedEnemyKingHP = rc.readSharedArray(SLOT_ENEMY_KING_HP);
    if (cachedEnemyKingHP == 0) cachedEnemyKingHP = 500; // Default to full HP

    // Economy mode
    cachedEconomyMode = rc.readSharedArray(SLOT_ECONOMY_MODE);
  }

  /** Estimate enemy king position based on map symmetry. */
  private static MapLocation getEnemyKingEstimate(RobotController rc) throws GameActionException {
    if (estimatedEnemyKingLoc != null) return estimatedEnemyKingLoc;
    if (cachedOurKingLoc == null) return null;

    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();
    int ourX = cachedOurKingLoc.x;
    int ourY = cachedOurKingLoc.y;

    // Default: assume rotational symmetry (most common)
    if (mapSymmetry == -1 || mapSymmetry == 0) {
      estimatedEnemyKingLoc = new MapLocation(mapWidth - ourX - 1, mapHeight - ourY - 1);
    } else if (mapSymmetry == 1) {
      // Horizontal reflection
      estimatedEnemyKingLoc = new MapLocation(mapWidth - ourX - 1, ourY);
    } else {
      // Vertical reflection
      estimatedEnemyKingLoc = new MapLocation(ourX, mapHeight - ourY - 1);
    }

    return estimatedEnemyKingLoc;
  }

  // ========================================================================
  // VALUE FUNCTION (Integer Arithmetic)
  // ========================================================================

  /** Score a potential target using INTEGER arithmetic. Higher score = higher priority. */
  private static int scoreTarget(int targetType, int distanceSq) {
    int baseValue = getBaseValue(targetType);
    // Integer division - no floats!
    return (baseValue * 1000) / (1000 + distanceSq * DISTANCE_WEIGHT_INT);
  }

  /** Get base value for a target type. */
  private static int getBaseValue(int targetType) {
    switch (targetType) {
      case TARGET_ENEMY_KING:
        // Base 100, scales up when enemy king is wounded
        int hpFactor = 100 + (100 * (500 - cachedEnemyKingHP) / 500);
        return ENEMY_KING_BASE_VALUE * hpFactor / 100;

      case TARGET_ENEMY_RAT:
        return ENEMY_RAT_VALUE;

      case TARGET_CHEESE:
        if (cachedOurCheese < CRITICAL_ECONOMY_THRESHOLD) {
          return CHEESE_VALUE_CRITICAL;
        }
        if (cachedOurCheese < LOW_ECONOMY_THRESHOLD) {
          return CHEESE_VALUE_LOW_ECONOMY;
        }
        return CHEESE_VALUE_NORMAL;

      case TARGET_DELIVERY:
        return cachedCarryingCheese ? DELIVERY_PRIORITY : 0;

      case TARGET_DIRT:
        return DIRT_VALUE;

      case TARGET_FOCUS:
        return FOCUS_FIRE_BONUS;

      default:
        return 0;
    }
  }

  /**
   * Score all visible targets and find the best one. Results stored in static fields to avoid
   * allocation. Uses cheeseBuffer/cheeseCount for cheese locations. PERF: Uses indexed loops, early
   * exits, and inline calculations.
   */
  private static void scoreAllTargets(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    // PERF: Use cached location
    int meX = myLocX;
    int meY = myLocY;

    // CRITICAL FIX: Check for VISIBLE enemy king FIRST - use ACTUAL location!
    MapLocation actualEnemyKingLoc = null;
    int enemyLen = enemies.length;
    for (int i = enemyLen; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        actualEnemyKingLoc = enemy.getLocation();
        cachedEnemyKingLoc = actualEnemyKingLoc;
        cachedEnemyKingHP = enemy.getHealth();
        break;
      }
    }

    // PERF: EARLY EXIT - If we can SEE the enemy king, just target it!
    if (actualEnemyKingLoc != null) {
      cachedBestTarget = actualEnemyKingLoc;
      cachedBestTargetType = TARGET_ENEMY_KING;
      cachedBestScore = 1000; // High priority
      return;
    }

    // Use cached location if we didn't see the king
    MapLocation enemyKingTarget = cachedEnemyKingLoc;

    // ALL-IN MODE: If enemy king is wounded, prioritize killing it!
    boolean allInMode = cachedEnemyKingHP > 0 && cachedEnemyKingHP < WOUNDED_KING_HP;

    // Score enemy king
    if (enemyKingTarget != null) {
      // PERF: Inline distance calculation
      int dx = meX - enemyKingTarget.x;
      int dy = meY - enemyKingTarget.y;
      int distSq = dx * dx + dy * dy;
      int score = scoreTarget(TARGET_ENEMY_KING, distSq);

      if (allInMode) {
        score += 500;
      }

      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = enemyKingTarget;
        cachedBestTargetType = TARGET_ENEMY_KING;
      }
    }

    // Score visible enemy rats - PERF: indexed loop
    int focusId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    for (int i = enemyLen; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) continue;
      MapLocation enemyLoc = enemy.getLocation();
      int dx = meX - enemyLoc.x;
      int dy = meY - enemyLoc.y;
      int distSq = dx * dx + dy * dy;
      int score = scoreTarget(TARGET_ENEMY_RAT, distSq);

      // PERF: Inline isFocusTarget
      if (focusId > 0 && (enemy.getID() & 1023) == focusId) {
        score += FOCUS_FIRE_BONUS * 1000 / (1000 + distSq * DISTANCE_WEIGHT_INT);
      }

      // PERF: Use bit shift for division by 3 approximation (multiply by 0.33)
      if (allInMode) {
        score = (score * 11) >> 5; // ~0.34
      }

      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = enemyLoc;
        cachedBestTargetType = TARGET_ENEMY_RAT;
      }
    }

    // Score cheese (skip if all-in mode) - PERF: removed mine proximity loop
    if (!allInMode) {
      for (int i = cheeseCount; --i >= 0; ) {
        MapLocation cheese = cheeseBuffer[i];
        int dx = meX - cheese.x;
        int dy = meY - cheese.y;
        int distSq = dx * dx + dy * dy;
        int score = scoreTarget(TARGET_CHEESE, distSq);

        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = cheese;
          cachedBestTargetType = TARGET_CHEESE;
        }
      }
    }

    // Score delivery
    if (cachedCarryingCheese && cachedOurKingLoc != null) {
      int dx = meX - cachedOurKingX;
      int dy = meY - cachedOurKingY;
      int distSq = dx * dx + dy * dy;
      int score = scoreTarget(TARGET_DELIVERY, distSq);
      if (allInMode) {
        score >>= 1; // PERF: bit shift for /2
      }
      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
      }
    }

    // Fallback: move toward estimated enemy king
    if (cachedBestTarget == null) {
      cachedBestTarget = getEnemyKingEstimate(rc);
      cachedBestTargetType = TARGET_ENEMY_KING;
      cachedBestScore = 1;
    }
  }

  // ========================================================================
  // IMMEDIATE ACTIONS
  // ========================================================================

  /**
   * Try immediate actions in priority order: attack, deliver, collect, dig. Returns true if an
   * action was taken. Uses cheeseBuffer/cheeseCount for cheese locations.
   */
  private static boolean tryImmediateAction(
      RobotController rc, RobotInfo[] enemies, int focusTargetId) throws GameActionException {
    if (!rc.isActionReady()) return false;

    MapLocation me = rc.getLocation();

    // Priority 1: Attack
    if (enemies.length > 0) {
      if (tryAttack(rc, enemies, focusTargetId)) return true;
    }

    // Priority 2: Deliver cheese
    if (cachedCarryingCheese && cachedOurKingLoc != null) {
      int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
      if (distToKing <= DELIVERY_RANGE_SQ) {
        if (tryDeliverCheese(rc)) return true;
      }
    }

    // Priority 3: Collect cheese
    if (cheeseCount > 0) {
      if (tryCollectCheese(rc)) return true;
    }

    // Priority 4: Dig dirt
    if (tryDigDirt(rc)) return true;

    return false;
  }

  /** Attack enemies, prioritizing ENEMY KING FIRST, then focus fire target. PERF: indexed loops */
  private static boolean tryAttack(RobotController rc, RobotInfo[] enemies, int focusTargetId)
      throws GameActionException {
    if (!rc.isActionReady()) return false;

    int len = enemies.length;

    // CRITICAL: Check if enemy KING is attackable FIRST - always prioritize king!
    for (int i = len; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        if (rc.canAttack(loc)) {
          rc.attack(loc);
          return true;
        }
      }
    }

    // Then score other targets
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;

    for (int i = len; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      MapLocation loc = enemy.getLocation();
      if (!rc.canAttack(loc)) continue;

      int score = 0;

      // PERF: Inline isFocusTarget
      if (focusTargetId > 0 && (enemy.getID() & 1023) == focusTargetId) {
        score += 5000;
      }

      // Prioritize wounded enemies
      score += 1000 - enemy.getHealth();

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    if (bestTarget != null) {
      rc.attack(bestTarget.getLocation());
      return true;
    }
    return false;
  }

  /** Deliver cheese to our king. */
  private static boolean tryDeliverCheese(RobotController rc) throws GameActionException {
    if (!cachedCarryingCheese || cachedOurKingLoc == null) return false;

    int amount = rc.getRawCheese();
    if (amount <= 0) return false;

    if (rc.canTransferCheese(cachedOurKingLoc, amount)) {
      rc.transferCheese(cachedOurKingLoc, amount);
      cachedCarryingCheese = false;
      lastDeliveryRound = cachedRound; // Track delivery for king pause logic
      return true;
    }
    return false;
  }

  /** Collect adjacent cheese. Uses cheeseBuffer/cheeseCount. */
  private static boolean tryCollectCheese(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return false;

    MapLocation me = rc.getLocation();

    for (int i = 0; i < cheeseCount; i++) {
      MapLocation cheese = cheeseBuffer[i];
      if (me.distanceSquaredTo(cheese) <= 2) {
        if (rc.canPickUpCheese(cheese)) {
          rc.pickUpCheese(cheese);
          cachedCarryingCheese = true;
          return true;
        }
      }
    }
    return false;
  }

  /** Dig dirt blocking our path. */
  private static boolean tryDigDirt(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return false;

    Direction facing = rc.getDirection();
    MapLocation me = rc.getLocation();
    MapLocation ahead = me.add(facing);

    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      return true;
    }

    // Try diagonals
    MapLocation leftAhead = me.add(facing.rotateLeft());
    MapLocation rightAhead = me.add(facing.rotateRight());

    if (rc.canRemoveDirt(leftAhead)) {
      rc.removeDirt(leftAhead);
      return true;
    }
    if (rc.canRemoveDirt(rightAhead)) {
      rc.removeDirt(rightAhead);
      return true;
    }

    return false;
  }

  // ========================================================================
  // FOCUS FIRE
  // ========================================================================

  // PERF: isFocusTarget inlined everywhere it was used, method removed

  /** King updates the focus fire target. PERF: indexed loop, inline Math.min */
  private static void updateFocusFireTarget(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;

    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      int score;
      if (enemy.getType() == UnitType.RAT_KING) {
        score = 10000 - enemy.getHealth();
      } else {
        score = 1000 - enemy.getHealth();
      }

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    if (bestTarget != null) {
      rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.getID() & 1023); // PERF: bitwise AND
      int hp = bestTarget.getHealth();
      rc.writeSharedArray(SLOT_FOCUS_HP, hp < 1023 ? hp : 1023); // PERF: inline min
    }
  }

  // ========================================================================
  // BUG2 PATHFINDING
  // ========================================================================

  /** Bug2 pathfinding: Move toward target, wall-follow when blocked. Uses movement scoring. */
  private static void bug2MoveTo(RobotController rc, MapLocation target)
      throws GameActionException {
    if (target == null || !rc.isMovementReady()) return;

    MapLocation me = rc.getLocation();

    // Reset wall following if target changed
    if (!target.equals(bug2Target)) {
      bug2Target = target;
      bug2WallFollowing = false;
    }

    if (me.equals(target)) return;

    Direction toTarget = me.directionTo(target);
    int distToTarget = me.distanceSquaredTo(target);

    // CRITICAL: When close to enemy king, IGNORE TRAPS and charge!
    // It's worth 50 trap damage to kill the king!
    // Charge if we're within 4 tiles of the enemy king location
    boolean chargeMode =
        cachedEnemyKingLoc != null && me.distanceSquaredTo(cachedEnemyKingLoc) <= 16;

    if (!bug2WallFollowing) {
      // CHARGE MODE: When close to enemy king, just move directly!
      if (chargeMode) {
        if (rc.canMove(toTarget)) {
          rc.move(toTarget);
          return;
        }
        // Try adjacent directions
        Direction left = toTarget.rotateLeft();
        Direction right = toTarget.rotateRight();
        if (rc.canMove(left)) {
          rc.move(left);
          return;
        }
        if (rc.canMove(right)) {
          rc.move(right);
          return;
        }
        // Try wider angles
        if (rc.canMove(left.rotateLeft())) {
          rc.move(left.rotateLeft());
          return;
        }
        if (rc.canMove(right.rotateRight())) {
          rc.move(right.rotateRight());
          return;
        }
      }

      // Use movement scoring to pick best direction (considers anti-clump and forward bonus)
      Direction bestDir = getBestMoveDirection(rc, target);
      if (bestDir != null && canMoveSafely(rc, bestDir)) {
        rc.move(bestDir);
        return;
      }

      // Fallback: Try direct path
      if (canMoveSafely(rc, toTarget)) {
        rc.move(toTarget);
        return;
      }

      // Try adjacent directions
      Direction left = toTarget.rotateLeft();
      Direction right = toTarget.rotateRight();

      if (canMoveSafely(rc, left)) {
        rc.move(left);
        return;
      }
      if (canMoveSafely(rc, right)) {
        rc.move(right);
        return;
      }

      // Start wall following
      bug2WallFollowing = true;
      bug2WallDir = toTarget;
      bug2StartLoc = me;
      bug2StartDist = me.distanceSquaredTo(target);
    }

    // Wall following mode
    if (bug2WallFollowing) {
      int currentDist = me.distanceSquaredTo(target);
      if (currentDist < bug2StartDist && !me.equals(bug2StartLoc)) {
        bug2WallFollowing = false;
        return; // Next turn handles direct path
      }

      // Follow wall (turn right at walls)
      for (int i = 0; i < 8; i++) {
        if (chargeMode ? rc.canMove(bug2WallDir) : canMoveSafely(rc, bug2WallDir)) {
          rc.move(bug2WallDir);
          bug2WallDir = bug2WallDir.rotateLeft().rotateLeft();
          return;
        }
        bug2WallDir = bug2WallDir.rotateRight();
      }
    }
  }

  /** Score movement directions and return the best one. PERF: indexed loop, inline distance */
  private static Direction getBestMoveDirection(RobotController rc, MapLocation target)
      throws GameActionException {
    Direction facing = rc.getDirection();
    Direction toTarget = myLoc.directionTo(target);
    int targetX = target.x;
    int targetY = target.y;

    Direction bestDir = null;
    int bestScore = Integer.MIN_VALUE;

    // PERF: Indexed loop over DIRECTIONS
    for (int i = 8; --i >= 0; ) {
      Direction dir = DIRECTIONS[i];
      if (!canMoveSafely(rc, dir)) continue;

      // PERF: Inline distance calculation
      int newX = myLocX + dir.dx;
      int newY = myLocY + dir.dy;
      int dx = newX - targetX;
      int dy = newY - targetY;
      int distSq = dx * dx + dy * dy;
      int score = -distSq * 10;

      // Forward move bonus (lower cooldown)
      if (dir == facing) {
        score += FORWARD_MOVE_BONUS;
      } else if (dir == facing.rotateLeft() || dir == facing.rotateRight()) {
        score += FORWARD_MOVE_BONUS >> 1; // PERF: bit shift
      }

      // Anti-clumping - skip sense check for speed, just penalize
      // PERF: Removed rc.senseRobotAtLocation call (expensive)

      // Prefer directions closer to target direction
      int angleDiff = getAngleDifference(dir, toTarget);
      score -= angleDiff;

      if (score > bestScore) {
        bestScore = score;
        bestDir = dir;
      }
    }

    return bestDir;
  }

  /** Check if we can move safely (no traps, passable terrain). */
  private static boolean canMoveSafely(RobotController rc, Direction dir)
      throws GameActionException {
    if (!rc.canMove(dir)) return false;

    MapLocation target = rc.adjacentLocation(dir);

    // Check for enemy traps
    if (rc.canSenseLocation(target)) {
      MapInfo info = rc.senseMapInfo(target);
      if (info.getTrap() == TrapType.RAT_TRAP) {
        return false;
      }
    }

    return true;
  }

  // ========================================================================
  // VISION CONE MANAGEMENT
  // ========================================================================

  /** Turn toward the most likely valuable direction when nothing is visible. */
  private static void tryTurnTowardTarget(RobotController rc) throws GameActionException {
    if (!rc.isMovementReady()) return; // Turn uses movement cooldown

    Direction currentFacing = rc.getDirection();
    MapLocation me = rc.getLocation();

    // Priority 1: Turn toward enemy king
    MapLocation target = cachedEnemyKingLoc;
    if (target == null) {
      target = getEnemyKingEstimate(rc);
    }

    if (target != null && !target.equals(me)) {
      Direction toTarget = me.directionTo(target);
      if (toTarget != Direction.CENTER) {
        int angleDiff = getAngleDifference(currentFacing, toTarget);
        if (angleDiff > 45 && rc.canTurn(toTarget)) {
          rc.turn(toTarget);
          return;
        }
      }
    }

    // Priority 2: Turn toward our king if carrying cheese
    if (cachedCarryingCheese && cachedOurKingLoc != null && !cachedOurKingLoc.equals(me)) {
      Direction toKing = me.directionTo(cachedOurKingLoc);
      if (toKing != Direction.CENTER) {
        int angleDiff = getAngleDifference(currentFacing, toKing);
        if (angleDiff > 45 && rc.canTurn(toKing)) {
          rc.turn(toKing);
          return;
        }
      }
    }
  }

  /** Returns angle difference in degrees (0-180). PERF: inline Math.abs */
  private static int getAngleDifference(Direction a, Direction b) {
    int diff = a.ordinal() - b.ordinal();
    if (diff < 0) diff = -diff; // PERF: inline abs
    if (diff > 4) diff = 8 - diff;
    return diff * 45;
  }

  /** PERF: Calculate direction from delta without MapLocation allocation */
  private static Direction directionFromDelta(int dx, int dy) {
    if (dx > 0) {
      if (dy > 0) return Direction.NORTHEAST;
      if (dy < 0) return Direction.SOUTHEAST;
      return Direction.EAST;
    } else if (dx < 0) {
      if (dy > 0) return Direction.NORTHWEST;
      if (dy < 0) return Direction.SOUTHWEST;
      return Direction.WEST;
    } else {
      if (dy > 0) return Direction.NORTH;
      if (dy < 0) return Direction.SOUTH;
      return Direction.CENTER;
    }
  }

  // ========================================================================
  // CAT HANDLING
  // ========================================================================

  /** Check if any cats are dangerous nearby. PERF: indexed loop */
  private static RobotInfo findDangerousCat(RobotInfo[] neutrals) {
    for (int i = neutrals.length; --i >= 0; ) {
      RobotInfo neutral = neutrals[i];
      if (neutral.getType() == UnitType.CAT) {
        return neutral;
      }
    }
    return null;
  }

  /** Flee from cat - highest priority. */
  private static void fleeFromCat(RobotController rc, RobotInfo cat) throws GameActionException {
    if (!rc.isMovementReady()) return;

    MapLocation me = rc.getLocation();
    Direction awayFromCat = cat.getLocation().directionTo(me);

    if (rc.canMove(awayFromCat)) {
      rc.move(awayFromCat);
      return;
    }

    Direction left = awayFromCat.rotateLeft();
    Direction right = awayFromCat.rotateRight();

    if (rc.canMove(left)) rc.move(left);
    else if (rc.canMove(right)) rc.move(right);
  }

  /**
   * King-specific flee from cat - scores all directions and picks the best one. Returns true if
   * successfully moved.
   */
  private static boolean kingFleeFromCat(RobotController rc, MapLocation me, MapLocation catLoc)
      throws GameActionException {
    if (!rc.isMovementReady()) return false;

    int currentDistToCat = me.distanceSquaredTo(catLoc);
    Direction bestDir = null;
    int bestScore = Integer.MIN_VALUE;

    // Score all 8 directions
    for (Direction dir : DIRECTIONS) {
      if (dir == Direction.CENTER) continue;

      MapLocation newLoc = me.add(dir);

      // Check if we can move there (including trap check)
      if (!rc.canMove(dir)) continue;

      // Check for traps
      if (rc.canSenseLocation(newLoc)) {
        MapInfo info = rc.senseMapInfo(newLoc);
        if (info.getTrap() == TrapType.RAT_TRAP) {
          continue; // Don't step on traps
        }
      }

      int newDistToCat = newLoc.distanceSquaredTo(catLoc);
      int score = 0;

      // Primary: Maximize distance from cat
      score += (newDistToCat - currentDistToCat) * 100;

      // Bonus for moving directly away from cat
      Direction awayFromCat = catLoc.directionTo(me);
      if (dir == awayFromCat) {
        score += 500;
      } else if (dir == awayFromCat.rotateLeft() || dir == awayFromCat.rotateRight()) {
        score += 300;
      } else if (dir == awayFromCat.rotateLeft().rotateLeft()
          || dir == awayFromCat.rotateRight().rotateRight()) {
        score += 100;
      }

      // Heavy bonus for escaping pounce range
      if (currentDistToCat <= CAT_POUNCE_RANGE_SQ && newDistToCat > CAT_POUNCE_RANGE_SQ) {
        score += 1000; // Escaping immediate danger
      }

      // Penalty for staying within danger radius
      if (newDistToCat < CAT_DANGER_RADIUS_SQ) {
        score -= 200;
      }

      // Prefer staying near spawn point
      if (kingSpawnPoint != null) {
        int distToSpawn = newLoc.distanceSquaredTo(kingSpawnPoint);
        if (distToSpawn <= KING_SAFE_ZONE_RADIUS_SQ) {
          score += 50;
        }
      }

      if (score > bestScore) {
        bestScore = score;
        bestDir = dir;
      }
    }

    // Execute the best move
    if (bestDir != null && rc.canMove(bestDir)) {
      rc.move(bestDir);
      return true;
    }

    // Fallback: try any direction that increases distance
    Direction awayFromCat = catLoc.directionTo(me);
    Direction[] tryDirs = {
      awayFromCat,
      awayFromCat.rotateLeft(),
      awayFromCat.rotateRight(),
      awayFromCat.rotateLeft().rotateLeft(),
      awayFromCat.rotateRight().rotateRight()
    };

    for (Direction dir : tryDirs) {
      if (rc.canMove(dir)) {
        rc.move(dir);
        return true;
      }
    }

    return false;
  }

  // ========================================================================
  // INTERCEPTOR BEHAVIOR
  // ========================================================================

  /** Check if this rat should act as interceptor (defend our king). SIMPLE: fixed ratio. */
  private static boolean shouldIntercept(RobotController rc) {
    // Only 5% interceptors - almost everyone ATTACKS!
    return rc.getID() % 20 == 0;
  }

  /**
   * Interceptor behavior: patrol near our king, attack enemies, collect cheese. SIMPLE: Guard the
   * king, attack threats, help economy.
   */
  private static void runInterceptor(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    MapLocation me = rc.getLocation();
    int focusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);

    // Priority 1: Attack any enemies!
    if (rc.isActionReady() && enemies.length > 0) {
      tryAttack(rc, enemies, focusTargetId);
    }

    // Priority 2: Deliver cheese if carrying and near king
    if (rc.isActionReady() && rc.getRawCheese() > 0 && cachedOurKingLoc != null) {
      if (me.distanceSquaredTo(cachedOurKingLoc) <= DELIVERY_RANGE_SQ) {
        tryDeliverCheese(rc);
      }
    }

    // Priority 3: Collect adjacent cheese
    if (rc.isActionReady() && cheeseCount > 0) {
      tryCollectCheese(rc);
    }

    // Priority 4: Move toward enemies near our king
    for (RobotInfo enemy : enemies) {
      if (cachedOurKingLoc != null
          && enemy.getLocation().distanceSquaredTo(cachedOurKingLoc) < INTERCEPTOR_RANGE_SQ) {
        bug2MoveTo(rc, enemy.getLocation());
        return;
      }
    }

    // Priority 5: Patrol near our king
    if (cachedOurKingLoc != null) {
      int distToKing = me.distanceSquaredTo(cachedOurKingLoc);

      if (distToKing > INTERCEPTOR_RANGE_SQ) {
        // Too far - move back toward king
        bug2MoveTo(rc, cachedOurKingLoc);
      } else if (cheeseCount > 0 && !cachedCarryingCheese) {
        // Near king but cheese visible - go collect it
        MapLocation nearestCheese = null;
        int nearestDist = Integer.MAX_VALUE;
        for (int i = 0; i < cheeseCount; i++) {
          int dist = me.distanceSquaredTo(cheeseBuffer[i]);
          if (dist < nearestDist && dist < INTERCEPTOR_RANGE_SQ) {
            nearestDist = dist;
            nearestCheese = cheeseBuffer[i];
          }
        }
        if (nearestCheese != null) {
          bug2MoveTo(rc, nearestCheese);
          return;
        }
        randomPatrol(rc);
      } else if (cachedCarryingCheese) {
        bug2MoveTo(rc, cachedOurKingLoc);
      } else {
        randomPatrol(rc);
      }
    }
  }

  /** Random patrol movement for interceptors. */
  private static void randomPatrol(RobotController rc) throws GameActionException {
    Direction randomDir = DIRECTIONS[rng.nextInt(8)];
    if (rc.canMove(randomDir)) {
      rc.move(randomDir);
    }
  }

  // ========================================================================
  // KING BEHAVIOR
  // ========================================================================

  // Track king HP to detect invisible attackers
  private static int lastKingHP = 500;

  /** King main loop. */
  private static void runKing(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    int hp = rc.getHealth();
    int round = rc.getRoundNum();

    // PERF: Debug output removed for production

    // CRITICAL: Check for cats FIRST - highest priority escape!
    RobotInfo[] neutrals = rc.senseNearbyRobots(-1, Team.NEUTRAL);
    RobotInfo dangerousCat = findDangerousCat(neutrals);

    // Update cat tracking
    if (dangerousCat != null) {
      lastKnownCatLoc = dangerousCat.getLocation();
      lastCatSeenRound = round;
    }

    // Flee from cat if within danger radius
    if (dangerousCat != null) {
      MapLocation catLoc = dangerousCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);

      if (distToCat < CAT_DANGER_RADIUS_SQ) {
        // PRIORITY ESCAPE - find the best flee direction
        if (kingFleeFromCat(rc, me, catLoc)) {
          return; // Successfully fled
        }
      }
    }

    RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);

    // CRITICAL: Detect damage BEFORE updating lastKingHP!
    // This flag is used later for spawn decisions
    boolean tookDamageThisTurn = hp < lastKingHP;

    // Detect invisible attackers by HP loss
    // If we're losing HP but can't see enemies, they're in our blind spot
    boolean takingInvisibleDamage = tookDamageThisTurn && (nearbyEnemies.length == 0);
    lastKingHP = hp; // Update AFTER checking for damage

    // If taking damage from invisible enemies, TURN TO SCAN!
    if (takingInvisibleDamage && rc.isMovementReady()) {
      Direction currentFacing = rc.getDirection();
      Direction opposite = currentFacing.opposite();
      rc.turn(opposite); // Turn 180° to look behind us
      // Re-sense after turning - now we can see them!
      nearbyEnemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    }

    // 1. Broadcast our position
    broadcastKingPosition(rc);

    // 2. Update game state
    updateGameState(rc);

    // 3. Check for enemies
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    boolean enemiesNearby = enemies.length > 0;

    if (enemies.length > 0) {
      updateFocusFireTarget(rc, enemies);
    }

    // 4. Early game: ALTERNATE between spawning and trap placement
    // Spawn first 3 rats, then place traps every 3rd round to not slow army buildup too much
    boolean inTrapWindow =
        round <= RAT_TRAP_EARLY_WINDOW
            && ratTrapCount < RAT_TRAP_TARGET
            && spawnCount >= MIN_SPAWNS_BEFORE_TRAPS;
    boolean trapRound = inTrapWindow && (round % 2 == 0); // Every 2nd round = trap (more traps!)

    if (trapRound && rc.isActionReady()) {
      // In cooperation mode, place cat traps (100 damage) instead of rat traps (50 damage)
      if (cachedIsCooperation && catTrapCount < CAT_TRAP_TARGET) {
        placeCatTrapsTowardEnemy(rc, me);
      } else {
        placeDefensiveTrapsTowardEnemy(rc, me);
      }
    }

    // Later: place traps when enemies are nearby (reactive)
    if (round > RAT_TRAP_EARLY_WINDOW && enemiesNearby && rc.isActionReady()) {
      tryPlaceTrap(rc);
    }

    // 5. Spawn rats when affordable (skip on trap rounds to save action)
    // CRITICAL: Stop spawning if taking damage and low on cheese - prioritize survival!
    boolean underAttack = tookDamageThisTurn || enemiesNearby;
    int cheeseReserve =
        underAttack
            ? SPAWN_CHEESE_RESERVE * 2
            : SPAWN_CHEESE_RESERVE; // Double reserve when under attack
    if (!trapRound && rc.getGlobalCheese() > cheeseReserve + getSpawnCost(rc)) {
      if (trySpawnRat(rc)) {
        spawnCount++;
      }
    }

    // 6. Mobile king - move when safe
    if (rc.isMovementReady()) {
      int roundsSinceDelivery = rc.getRoundNum() - lastDeliveryRound;

      // Proactive cat avoidance - if cat is in caution zone, move away
      if (dangerousCat != null) {
        MapLocation catLoc = dangerousCat.getLocation();
        int distToCat = me.distanceSquaredTo(catLoc);
        if (distToCat < CAT_CAUTION_RADIUS_SQ && distToCat >= CAT_DANGER_RADIUS_SQ) {
          // Cat is getting close - proactively move away
          kingFleeFromCat(rc, me, catLoc);
        } else if (enemiesNearby) {
          evadeFromEnemies(rc, enemies);
        } else if (roundsSinceDelivery > KING_DELIVERY_PAUSE_ROUNDS) {
          randomMoveInSafeZone(rc);
        }
      } else if (enemiesNearby) {
        evadeFromEnemies(rc, enemies);
      } else if (roundsSinceDelivery > KING_DELIVERY_PAUSE_ROUNDS) {
        randomMoveInSafeZone(rc);
      }
    }

    // 7. Read squeaks from baby rats - get fresh enemy king intel!
    kingReadSqueaks(rc);

    // 8. Broadcast enemy king if visible
    broadcastEnemyKing(rc, enemies);

    // 9. Update economy mode
    updateEconomyMode(rc);
  }

  /** Broadcast king position to shared array. */
  private static void broadcastKingPosition(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    rc.writeSharedArray(SLOT_OUR_KING_X, me.x);
    rc.writeSharedArray(SLOT_OUR_KING_Y, me.y);
  }

  /** Update economy mode in shared array. */
  private static void updateEconomyMode(RobotController rc) throws GameActionException {
    int cheese = rc.getGlobalCheese();
    int mode;

    if (cheese < CRITICAL_ECONOMY_THRESHOLD) {
      mode = 2;
    } else if (cheese < LOW_ECONOMY_THRESHOLD) {
      mode = 1;
    } else {
      mode = 0;
    }

    rc.writeSharedArray(SLOT_ECONOMY_MODE, mode);
  }

  /** Broadcast enemy king position if visible. PERF: indexed loop, inline min */
  private static void broadcastEnemyKing(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        rc.writeSharedArray(SLOT_ENEMY_KING_X, loc.x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, loc.y);
        int hp = enemy.getHealth();
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, hp < 1023 ? hp : 1023);
        return;
      }
    }
  }

  /** Try to spawn a baby rat. */
  private static boolean trySpawnRat(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();

    // Try to spawn at distance 2
    for (int i = 0; i < 8; i++) {
      Direction dir = DIRECTIONS[i];
      MapLocation spawnLoc = kingLoc.add(dir).add(dir);

      if (rc.canBuildRat(spawnLoc)) {
        rc.buildRat(spawnLoc);
        return true;
      }
    }

    // Try distance 3 and 4
    for (int dist = 3; dist <= 4; dist++) {
      for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        int dx = dir.dx * dist;
        int dy = dir.dy * dist;
        MapLocation spawnLoc = kingLoc.translate(dx, dy);

        if (rc.canBuildRat(spawnLoc)) {
          rc.buildRat(spawnLoc);
          return true;
        }
      }
    }

    return false;
  }

  /** Get spawn cost using the API method for accuracy. */
  private static int getSpawnCost(RobotController rc) throws GameActionException {
    return rc.getCurrentRatCost();
  }

  /** Try to place a trap near the king (reactive - when enemies nearby). */
  private static boolean tryPlaceTrap(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return false;

    MapLocation me = rc.getLocation();

    // Try distance 2-3 toward enemy direction
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    for (int dist = 2; dist <= 3; dist++) {
      MapLocation trapLoc = me.translate(toEnemy.dx * dist, toEnemy.dy * dist);
      if (rc.canPlaceRatTrap(trapLoc)) {
        rc.placeRatTrap(trapLoc);
        ratTrapCount++;
        return true;
      }
    }

    return false;
  }

  /** Place defensive traps toward enemy (early game - proactive). Like ratbot5. */
  private static void placeDefensiveTrapsTowardEnemy(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!rc.isActionReady()) return;

    Direction toEnemy;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    } else {
      toEnemy = Direction.NORTH;
    }

    // Populate priority directions toward enemy
    TRAP_PRIORITY_DIRS[0] = toEnemy;
    TRAP_PRIORITY_DIRS[1] = toEnemy.rotateLeft();
    TRAP_PRIORITY_DIRS[2] = toEnemy.rotateRight();
    TRAP_PRIORITY_DIRS[3] = toEnemy.rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[4] = toEnemy.rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[5] = toEnemy.rotateLeft().rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[6] = toEnemy.rotateRight().rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[7] = toEnemy.opposite();

    // Try each direction at distance 3-4 (like ratbot5)
    for (int p = 0; p < 8; p++) {
      Direction dir = TRAP_PRIORITY_DIRS[p];
      if (dir == Direction.CENTER) continue;
      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 3; dist <= 4; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          return;
        }
      }
    }
  }

  /** Baby rat places trap when near king (defensive helper). Like ratbot5. */
  private static void babyRatPlaceTrap(RobotController rc, MapLocation me, int round, int id)
      throws GameActionException {
    if (!rc.isActionReady()) return;

    // Reset cooldown for new rat
    if (lastBabyTrapID != id) {
      lastBabyTrapRound = -100;
      lastBabyTrapID = id;
    }

    // Check cooldown
    if (round - lastBabyTrapRound < BABY_RAT_TRAP_COOLDOWN) return;

    // Only place traps when near our king
    if (cachedOurKingLoc == null) return;
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
    if (distToKing > 16) return; // Within 4 tiles of king

    // Place traps toward enemy direction
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    BABY_TRAP_DIRS[0] = toEnemy;
    BABY_TRAP_DIRS[1] = toEnemy.rotateLeft();
    BABY_TRAP_DIRS[2] = toEnemy.rotateRight();

    for (int d = 0; d < 3; d++) {
      Direction dir = BABY_TRAP_DIRS[d];
      for (int dist = 1; dist <= 2; dist++) {
        MapLocation trapLoc = me.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          lastBabyTrapRound = round;
          return;
        }
      }
    }
  }

  /** King moves away from enemies, also considering cat position. PERF: avoid allocation */
  private static void evadeFromEnemies(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    MapLocation me = rc.getLocation();
    int round = rc.getRoundNum();

    // PERF: Calculate enemy center without allocating MapLocation
    int sumX = 0, sumY = 0;
    int len = enemies.length;
    for (int i = len; --i >= 0; ) {
      MapLocation loc = enemies[i].getLocation();
      sumX += loc.x;
      sumY += loc.y;
    }
    int centerX = sumX / len;
    int centerY = sumY / len;

    // PERF: Calculate direction from center to me without MapLocation allocation
    int dx = me.x - centerX;
    int dy = me.y - centerY;
    Direction awayDir = directionFromDelta(dx, dy);

    // Check if moving away from enemies would bring us closer to the cat
    if (lastKnownCatLoc != null && round - lastCatSeenRound < 30) {
      MapLocation newLoc = me.add(awayDir);
      if (newLoc.distanceSquaredTo(lastKnownCatLoc) < me.distanceSquaredTo(lastKnownCatLoc)) {
        // Would move toward cat - find a compromise direction
        // Try perpendicular directions
        Direction left = awayDir.rotateLeft().rotateLeft();
        Direction right = awayDir.rotateRight().rotateRight();

        MapLocation leftLoc = me.add(left);
        MapLocation rightLoc = me.add(right);

        if (leftLoc.distanceSquaredTo(lastKnownCatLoc)
            > rightLoc.distanceSquaredTo(lastKnownCatLoc)) {
          if (tryKingMove(rc, left)) return;
        } else {
          if (tryKingMove(rc, right)) return;
        }
      }
    }

    tryKingMove(rc, awayDir);
  }

  /** King random walk in safe zone, avoiding cat. */
  private static void randomMoveInSafeZone(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    int round = rc.getRoundNum();

    // If too far from spawn, move back
    if (me.distanceSquaredTo(kingSpawnPoint) > KING_SAFE_ZONE_RADIUS_SQ) {
      Direction toSpawn = me.directionTo(kingSpawnPoint);
      // But don't move toward the cat!
      if (lastKnownCatLoc != null && round - lastCatSeenRound < 30) {
        MapLocation newLoc = me.add(toSpawn);
        if (newLoc.distanceSquaredTo(lastKnownCatLoc) < me.distanceSquaredTo(lastKnownCatLoc)) {
          // This would move us toward the cat - try a different direction
          Direction awayFromCat = lastKnownCatLoc.directionTo(me);
          tryKingMove(rc, awayFromCat);
          return;
        }
      }
      tryKingMove(rc, toSpawn);
      return;
    }

    // Pick a random direction, but avoid moving toward the cat
    Direction bestDir = null;
    int bestScore = Integer.MIN_VALUE;

    for (Direction dir : DIRECTIONS) {
      MapLocation newLoc = me.add(dir);
      if (!rc.canMove(dir)) continue;
      if (newLoc.distanceSquaredTo(kingSpawnPoint) > KING_SAFE_ZONE_RADIUS_SQ) continue;

      int score = rng.nextInt(100); // Random base score

      // Penalize moving toward the cat
      if (lastKnownCatLoc != null && round - lastCatSeenRound < 30) {
        int currentDistToCat = me.distanceSquaredTo(lastKnownCatLoc);
        int newDistToCat = newLoc.distanceSquaredTo(lastKnownCatLoc);

        if (newDistToCat < currentDistToCat) {
          score -= 500; // Heavy penalty for moving toward cat
        } else if (newDistToCat > currentDistToCat) {
          score += 100; // Bonus for moving away from cat
        }

        // Extra penalty if we'd be within caution radius
        if (newDistToCat < CAT_CAUTION_RADIUS_SQ) {
          score -= 200;
        }
      }

      if (score > bestScore) {
        bestScore = score;
        bestDir = dir;
      }
    }

    if (bestDir != null) {
      tryKingMove(rc, bestDir);
    }
  }

  /** King movement with trap avoidance. */
  private static boolean tryKingMove(RobotController rc, Direction dir) throws GameActionException {
    if (!rc.isMovementReady()) return false;
    if (dir == null || dir == Direction.CENTER) return false;

    MapLocation me = rc.getLocation();
    MapLocation target = me.add(dir);

    if (rc.canSenseLocation(target)) {
      MapInfo info = rc.senseMapInfo(target);
      if (info.getTrap() == TrapType.RAT_TRAP) {
        return false;
      }
    }

    if (rc.canMove(dir)) {
      rc.move(dir);
      return true;
    }

    Direction left = dir.rotateLeft();
    Direction right = dir.rotateRight();

    if (rc.canMove(left)) {
      rc.move(left);
      return true;
    }
    if (rc.canMove(right)) {
      rc.move(right);
      return true;
    }

    return false;
  }

  // ========================================================================
  // CHEESE SENSING HELPER
  // ========================================================================

  // Static array for cheese locations (avoid allocation per turn)
  private static final MapLocation[] cheeseBuffer = new MapLocation[50];
  private static int cheeseCount = 0;

  /**
   * Find nearby cheese locations by scanning MapInfo. PERF: indexed loop, removed mine tracking.
   */
  private static void findNearbyCheese(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, 20);
    cheeseCount = 0;
    // PERF: Removed mine tracking - not worth bytecode

    int bufLen = cheeseBuffer.length;
    for (int i = nearbyTiles.length; --i >= 0 && cheeseCount < bufLen; ) {
      MapInfo info = nearbyTiles[i];
      if (info.getCheeseAmount() > 0) {
        cheeseBuffer[cheeseCount++] = info.getMapLocation();
      }
    }
  }

  // ========================================================================
  // BABY RAT MAIN LOOP
  // ========================================================================

  /** Baby rat main loop. SIMPLE: Seek and destroy. Get there and attack. Don't stop. */
  private static void runBabyRat(RobotController rc) throws GameActionException {
    // Skip turn if being ratnapped
    if (rc.isBeingThrown() || rc.isBeingCarried()) {
      return;
    }

    // 1. Update game state
    updateGameState(rc);

    // PERF: Use cached location
    MapLocation me = myLoc;
    int round = cachedRound;
    int id = rc.getID();

    // 2. Sense enemies and cheese
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    findNearbyCheese(rc);

    // 3. Check if interceptor (small % to defend king)
    if (shouldIntercept(rc)) {
      runInterceptor(rc, enemies);
      return;
    }

    // 4. CRITICAL: If close to enemy king location but CAN'T SEE any enemies, TURN TO LOOK!
    // This is the #1 reason rats fail to attack - they arrive facing the wrong direction
    if (cachedEnemyKingLoc != null && enemies.length == 0) {
      int distToEnemyKing = me.distanceSquaredTo(cachedEnemyKingLoc);
      Direction facing = rc.getDirection();
      Direction toKing = me.directionTo(cachedEnemyKingLoc);

      if (distToEnemyKing <= 2 && toKing == facing) {
        // We're AT the cached location, facing it, but see nothing - king moved! Scan around.
        Direction scanDir = facing.rotateRight().rotateRight(); // Try 90° right
        if (rc.canTurn(scanDir)) {
          rc.turn(scanDir);
          enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
        }
      } else if (distToEnemyKing <= 8 && toKing != Direction.CENTER && toKing != facing) {
        // Within ~3 tiles - turn to face the king location
        if (rc.canTurn(toKing)) {
          rc.turn(toKing);
          enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
        }
      } else if (distToEnemyKing <= 20 && toKing != Direction.CENTER) {
        // Within ~4.5 tiles - turn if significantly off
        if (getAngleDifference(facing, toKing) > 45 && rc.canTurn(toKing)) {
          rc.turn(toKing);
          enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
        }
      }
    }

    // 5. Vision management - turn toward target if nothing visible
    if (enemies.length == 0 && cheeseCount == 0) {
      tryTurnTowardTarget(rc);
    }

    // 6. Focus fire target
    int focusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);

    // 7. Try immediate actions (attack, deliver, collect)
    if (tryImmediateAction(rc, enemies, focusTargetId)) {
      // After delivering, help place traps if enemies nearby
      if (cachedOurKingLoc != null) {
        int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
        if (distToKing <= 16 && enemies.length > 0) {
          babyRatPlaceTrap(rc, me, round, id);
        }
      }
      return;
    }

    // 8. Score all targets and find best one
    scoreAllTargets(rc, enemies);

    // PERF: Debug output removed for production

    // 9. MOVE TOWARD TARGET - simple, direct, relentless
    if (cachedBestTarget != null) {
      bug2MoveTo(rc, cachedBestTarget);
    }

    // 10. Squeak enemy king position when found
    updateEnemyKingFromBabyRat(rc, enemies);

    // PERF: Removed mine squeaking - not worth bytecode
  }

  // PERF: squeakMineLocation removed - not worth bytecode

  /**
   * Baby rat updates local cached enemy king position if visible and squeaks to share intel. PERF:
   * indexed loop, inline min
   */
  private static void updateEnemyKingFromBabyRat(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation enemyLoc = enemy.getLocation();
        cachedEnemyKingLoc = enemyLoc;
        cachedEnemyKingHP = enemy.getHealth();

        // SQUEAK the enemy king position to share with team!
        int round = cachedRound;
        int id = rc.getID();
        if (round - lastSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
          int hp = enemy.getHealth();
          int hpBits = hp / 35;
          if (hpBits > 15) hpBits = 15; // PERF: inline min
          int squeak =
              (SQUEAK_TYPE_ENEMY_KING << 28) | (enemyLoc.y << 16) | (enemyLoc.x << 4) | hpBits;
          rc.squeak(squeak);
          lastSqueakRound = round;
          lastSqueakID = id;
        }
        return;
      }
    }
  }

  // ========================================================================
  // SQUEAK COMMUNICATION SYSTEM
  // ========================================================================

  /**
   * King reads squeaks from baby rats and updates shared array with enemy king position. PERF:
   * avoid allocation, inline min
   */
  private static void kingReadSqueaks(RobotController rc) throws GameActionException {
    Message[] squeaks = rc.readSqueaks(-1);
    int len = squeaks.length;
    if (len == 0) return;

    int limit = len < MAX_SQUEAKS_TO_READ ? len : MAX_SQUEAKS_TO_READ;
    int round = cachedRound;
    int stopIdx = len - limit;
    if (stopIdx < 0) stopIdx = 0;

    for (int i = len - 1; i >= stopIdx; --i) {
      int content = squeaks[i].getBytes();
      int type = (content >> 28) & 0xF;

      if (type == SQUEAK_TYPE_ENEMY_KING) {
        int y = (content >> 16) & 0xFFF;
        int x = (content >> 4) & 0xFFF;
        int hpBits = content & 0xF;
        int hp = hpBits * 35;

        rc.writeSharedArray(SLOT_ENEMY_KING_X, x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, y);
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, hp < 1023 ? hp : 1023);
        rc.writeSharedArray(SLOT_ENEMY_KING_ROUND, round);

        // PERF: Only allocate if position changed
        if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
          cachedEnemyKingLoc = new MapLocation(x, y);
        }
        cachedEnemyKingHP = hp;
        return;
      }
    }
  }

  // ========================================================================
  // CAT TRAP PLACEMENT (Cooperation Mode)
  // ========================================================================

  /**
   * Place cat traps in cooperation mode. Cat traps do 100 damage vs rat traps' 50. Only available
   * when rc.isCooperation() is true.
   */
  private static void placeCatTrapsTowardEnemy(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!cachedIsCooperation) return; // Cat traps only in cooperation mode
    if (!rc.isActionReady()) return;
    if (catTrapCount >= CAT_TRAP_TARGET) return;

    Direction toEnemy;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    } else {
      toEnemy = Direction.NORTH;
    }

    // Populate priority directions toward enemy
    TRAP_PRIORITY_DIRS[0] = toEnemy;
    TRAP_PRIORITY_DIRS[1] = toEnemy.rotateLeft();
    TRAP_PRIORITY_DIRS[2] = toEnemy.rotateRight();
    TRAP_PRIORITY_DIRS[3] = toEnemy.rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[4] = toEnemy.rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[5] = toEnemy.rotateLeft().rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[6] = toEnemy.rotateRight().rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[7] = toEnemy.opposite();

    // Try each direction at distance 3-4
    for (int p = 0; p < 8; p++) {
      Direction dir = TRAP_PRIORITY_DIRS[p];
      if (dir == Direction.CENTER) continue;
      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 3; dist <= 4; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapCount++;
          return;
        }
      }
    }
  }

  // PERF: Mine detection removed - not worth bytecode
}
