package ratbot6;

import battlecode.common.*;
import java.util.Random;

/**
 * Ratbot6 - Value Function Architecture
 *
 * <h2>Philosophy</h2>
 *
 * Intelligence in the algorithm, not in roles. Every rat uses a single unified value function to
 * decide what to do. "Roles" emerge from game state, not from assignment.
 *
 * <h2>Key Features</h2>
 *
 * <ul>
 *   <li>Unified value function for target selection
 *   <li>Squeak-based enemy king position sharing
 *   <li>Smart spawn direction (toward enemy)
 *   <li>Charge mode for attacking enemy king through traps
 *   <li>King cat evasion with proactive avoidance
 *   <li>Focus fire coordination via shared array
 * </ul>
 *
 * <h2>Performance</h2>
 *
 * Optimized for bytecode efficiency with indexed loops, cached values, and minimal object
 * allocation.
 *
 * @see scaffold/RATBOT6_README.md for full documentation
 */
public class RobotPlayer {

  // ========================================================================
  // DEBUG CONFIG
  // ========================================================================
  private static final boolean PROFILE = false;

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
  // SLOT 8 reserved for future use
  private static final int SLOT_ENEMY_KING_ROUND = 9; // Round when enemy king was last seen
  private static final int SLOT_ESTIMATE_CHECKED =
      10; // Round when estimated position was checked (0 = not checked)

  // ========================================================================
  // SQUEAK CONSTANTS
  // ========================================================================
  private static final int SQUEAK_TYPE_ENEMY_KING = 1;
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
  private static final int SPAWN_CHEESE_RESERVE = 100; // Aggressive spawning
  private static final int FORWARD_MOVE_BONUS = 20; // Bonus for forward movement

  // ========================================================================
  // KING CONSTANTS
  // ========================================================================
  private static final int KING_SAFE_ZONE_RADIUS_SQ = 64; // King stays near spawn
  private static final int KING_DELIVERY_PAUSE_ROUNDS = 3; // Pause for delivery

  // ========================================================================
  // CAT CONSTANTS
  // ========================================================================
  private static final int CAT_DANGER_RADIUS_SQ = 100; // 10 tiles - flee range
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

  // Cached current location (updated each turn)
  private static MapLocation myLoc;
  private static int myLocX;
  private static int myLocY;

  // ========== RC METHOD CACHING (Bytecode Optimization) ==========
  private static int cachedMyID;
  private static Direction cachedMyDirection;

  // Cached king coordinates
  private static int cachedOurKingX;
  private static int cachedOurKingY;
  private static int cachedEnemyKingX;
  private static int cachedEnemyKingY;

  // ========== SHARED ARRAY CACHING (Bytecode Optimization) ==========
  private static int[] sharedArrayCache = new int[16];
  private static boolean sharedArrayCacheValid = false;

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

  // Track if enemy king has been CONFIRMED (actually seen) vs just estimated
  private static boolean enemyKingConfirmed = false;

  // Track if estimated position has been CHECKED (rat went there and didn't find king)
  private static int estimateCheckedRound = 0;

  // Note: Exploration targets are computed per-rat based on ID, not cached
  // (static fields are shared across all rats in the same JVM)

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

  // Cooperation mode cached at init
  private static boolean cachedIsCooperation = false;

  // Squeak state
  private static int lastSqueakRound = -100;
  private static int lastSqueakID = -1;

  // Timeout for verifying enemy king estimate (king-side, not relay-based)
  // Lower = more exploration time on non-standard maps
  private static final int BASE_VERIFICATION_TIMEOUT = 30;

  // Cat trap count
  private static int catTrapCount = 0;

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
    // NOTE: cachedRound, myLoc already cached at start of runBabyRat()/runKing()

    // Cheese state
    cachedCarryingCheese = rc.getRawCheese() > 0;
    cachedOurCheese = rc.getGlobalCheese();

    // ========== SHARED ARRAY CACHE (Bytecode Optimization) ==========
    // Batch read first 10 slots once (saves ~15 bytecode × 8-10 reads = 120-150 bytecode)
    for (int i = 10; --i >= 0; ) {
      sharedArrayCache[i] = rc.readSharedArray(i);
    }

    // Read our king position from cached array
    cachedOurKingX = sharedArrayCache[SLOT_OUR_KING_X];
    cachedOurKingY = sharedArrayCache[SLOT_OUR_KING_Y];
    if (cachedOurKingX > 0 || cachedOurKingY > 0) {
      // Only allocate if position changed
      if (cachedOurKingLoc == null
          || cachedOurKingLoc.x != cachedOurKingX
          || cachedOurKingLoc.y != cachedOurKingY) {
        cachedOurKingLoc = new MapLocation(cachedOurKingX, cachedOurKingY);
      }
      int dx = myLocX - cachedOurKingX;
      int dy = myLocY - cachedOurKingY;
      cachedDistToOurKing = dx * dx + dy * dy;
    }

    // Read enemy king position from cached array
    cachedEnemyKingX = sharedArrayCache[SLOT_ENEMY_KING_X];
    cachedEnemyKingY = sharedArrayCache[SLOT_ENEMY_KING_Y];
    int enemyKingSeenRound = sharedArrayCache[SLOT_ENEMY_KING_ROUND];

    // Check if enemy king has been CONFIRMED (actually seen by someone)
    if (enemyKingSeenRound > 0) {
      enemyKingConfirmed = true;
      if (cachedEnemyKingX > 0 || cachedEnemyKingY > 0) {
        // Only allocate if position changed
        if (cachedEnemyKingLoc == null
            || cachedEnemyKingLoc.x != cachedEnemyKingX
            || cachedEnemyKingLoc.y != cachedEnemyKingY) {
          cachedEnemyKingLoc = new MapLocation(cachedEnemyKingX, cachedEnemyKingY);
        }
      }
    } else {
      // Enemy king NOT confirmed yet
      enemyKingConfirmed = false;
      // Cache the estimate for targeting
      if (cachedEnemyKingX > 0 || cachedEnemyKingY > 0) {
        if (cachedEnemyKingLoc == null
            || cachedEnemyKingLoc.x != cachedEnemyKingX
            || cachedEnemyKingLoc.y != cachedEnemyKingY) {
          cachedEnemyKingLoc = new MapLocation(cachedEnemyKingX, cachedEnemyKingY);
        }
      } else {
        cachedEnemyKingLoc = getEnemyKingEstimate(rc);
      }
    }

    // Read estimate checked round (0 = not checked, >0 = checked and wrong)
    estimateCheckedRound = rc.readSharedArray(SLOT_ESTIMATE_CHECKED);

    // Read enemy king HP from cached array
    cachedEnemyKingHP = sharedArrayCache[SLOT_ENEMY_KING_HP];
    if (cachedEnemyKingHP == 0) cachedEnemyKingHP = 500; // Default to full HP

    // Economy mode from cached array
    cachedEconomyMode = sharedArrayCache[SLOT_ECONOMY_MODE];
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

  /**
   * Get exploration target when enemy king hasn't been found yet. Different rats explore different
   * areas based on their ID to spread out and find the king faster.
   */
  private static MapLocation getExplorationTarget(RobotController rc) throws GameActionException {
    int id = rc.getID();
    int group = id % 4;

    // Note: Don't cache exploration target - static fields are shared across all rats
    // Each rat needs to compute its own target based on its ID

    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();

    int targetX, targetY;

    if (group == 0) {
      // Explore opposite corner (original estimate)
      if (cachedOurKingLoc != null) {
        targetX = mapWidth - cachedOurKingLoc.x - 1;
        targetY = mapHeight - cachedOurKingLoc.y - 1;
      } else {
        targetX = mapWidth - 1;
        targetY = mapHeight - 1;
      }
    } else if (group == 1) {
      // Explore horizontal reflection
      if (cachedOurKingLoc != null) {
        targetX = mapWidth - cachedOurKingLoc.x - 1;
        targetY = cachedOurKingLoc.y;
      } else {
        targetX = mapWidth - 1;
        targetY = mapHeight / 2;
      }
    } else if (group == 2) {
      // Explore vertical reflection
      if (cachedOurKingLoc != null) {
        targetX = cachedOurKingLoc.x;
        targetY = mapHeight - cachedOurKingLoc.y - 1;
      } else {
        targetX = mapWidth / 2;
        targetY = mapHeight - 1;
      }
    } else {
      // Explore center of map
      targetX = mapWidth / 2;
      targetY = mapHeight / 2;
    }

    // Clamp to valid map coordinates
    if (targetX < 0) targetX = 0;
    if (targetX >= mapWidth) targetX = mapWidth - 1;
    if (targetY < 0) targetY = 0;
    if (targetY >= mapHeight) targetY = mapHeight - 1;

    return new MapLocation(targetX, targetY);
  }

  // ========================================================================
  // VALUE FUNCTION (Integer Arithmetic)
  // ========================================================================

  /** Score a potential target using integer arithmetic. Higher score = higher priority. */
  private static int scoreTarget(int targetType, int distanceSq) {
    int baseValue = getBaseValue(targetType);
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
   * Score all visible targets and find the best one. Results stored in static fields. Uses
   * cheeseBuffer/cheeseCount for cheese locations.
   */
  private static void scoreAllTargets(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    int meX = myLocX;
    int meY = myLocY;

    // Check for VISIBLE enemy king FIRST - use actual location!
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

    // Early exit - if we can see the enemy king, just target it!
    if (actualEnemyKingLoc != null) {
      cachedBestTarget = actualEnemyKingLoc;
      cachedBestTargetType = TARGET_ENEMY_KING;
      cachedBestScore = 1000;
      return;
    }

    // Use cached location if we didn't see the king
    MapLocation enemyKingTarget = cachedEnemyKingLoc;

    // ALL-IN MODE: If enemy king is wounded, prioritize killing it!
    boolean allInMode = cachedEnemyKingHP > 0 && cachedEnemyKingHP < WOUNDED_KING_HP;

    // TRUST BUT VERIFY: Score enemy king based on confirmation state
    // - If confirmed: Go to confirmed position (attack!)
    // - If not confirmed AND not checked: Go to estimated position (trust)
    // - If not confirmed AND checked (wrong): Explore different quadrants (verify failed)
    if (enemyKingTarget != null) {
      if (enemyKingConfirmed) {
        // CONFIRMED: Go attack the king!
        int dx = meX - enemyKingTarget.x;
        int dy = meY - enemyKingTarget.y;
        int distSq = dx * dx + dy * dy;
        int score = scoreTarget(TARGET_ENEMY_KING, distSq);
        if (allInMode) score += 500;
        score += 100; // Confirmed bonus
        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = enemyKingTarget;
          cachedBestTargetType = TARGET_ENEMY_KING;
        }
      } else if (estimateCheckedRound == 0) {
        // NOT CHECKED YET: Trust the estimate, go to estimated position
        int dx = meX - enemyKingTarget.x;
        int dy = meY - enemyKingTarget.y;
        int distSq = dx * dx + dy * dy;
        int score = scoreTarget(TARGET_ENEMY_KING, distSq);
        // Lower priority than confirmed, but still go there
        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = enemyKingTarget;
          cachedBestTargetType = TARGET_ENEMY_KING;
        }
      }
      // If estimateCheckedRound > 0 AND !enemyKingConfirmed, don't score estimated position
      // Rats will fall through to exploration logic below
    }

    // Score visible enemy rats
    int focusId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    for (int i = enemyLen; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) continue;
      MapLocation enemyLoc = enemy.getLocation();
      int dx = meX - enemyLoc.x;
      int dy = meY - enemyLoc.y;
      int distSq = dx * dx + dy * dy;
      int score = scoreTarget(TARGET_ENEMY_RAT, distSq);

      // Bonus for focus fire target
      if (focusId > 0 && (enemy.getID() & 1023) == focusId) {
        score += FOCUS_FIRE_BONUS * 1000 / (1000 + distSq * DISTANCE_WEIGHT_INT);
      }

      // Reduce enemy rat priority in all-in mode
      if (allInMode) {
        score = (score * 11) >> 5;
      }

      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = enemyLoc;
        cachedBestTargetType = TARGET_ENEMY_RAT;
      }
    }

    // Score cheese (skip if all-in mode)
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
        score >>= 1; // Reduce delivery priority in all-in mode
      }
      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
      }
    }

    // EXPLORATION MODE: Only activate when estimate was checked and found wrong
    // This means a rat went to the estimated position and didn't find the king
    if (!enemyKingConfirmed && estimateCheckedRound > 0) {
      // Estimate was wrong - explore to find the king
      // BUT still collect adjacent cheese (distance ≤ 2) - grab it without detouring
      boolean adjacentCheeseFound = false;
      if (cachedBestTargetType == TARGET_CHEESE && cachedBestTarget != null) {
        int dx = meX - cachedBestTarget.x;
        int dy = meY - cachedBestTarget.y;
        int distSq = dx * dx + dy * dy;
        if (distSq <= 2) { // Adjacent - grab it on the way
          adjacentCheeseFound = true;
        }
      }
      // If carrying cheese, deliver it first (but to exploration target direction)
      // Otherwise, explore to find the king
      if (!adjacentCheeseFound && !cachedCarryingCheese) {
        // No adjacent cheese and not carrying - explore to find the king
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_ENEMY_KING;
      }
      // If carrying cheese, the delivery target is already set - that's fine
    }

    // Fallback: if no target found, explore or deliver
    if (cachedBestTarget == null) {
      if (cachedCarryingCheese && cachedOurKingLoc != null) {
        // Carrying cheese - go to our king
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
        cachedBestScore = 1;
      } else if (enemyKingConfirmed && cachedEnemyKingLoc != null) {
        // Enemy king has been seen - go there
        cachedBestTarget = cachedEnemyKingLoc;
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 1;
      } else {
        // Enemy king NOT confirmed - explore to find it!
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 1;
      }
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

  /** Attack enemies, prioritizing enemy king first, then focus fire target. */
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

      // Bonus for focus fire target
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

  /** King updates the focus fire target. */
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
      rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.getID() & 1023);
      int hp = bestTarget.getHealth();
      rc.writeSharedArray(SLOT_FOCUS_HP, hp < 1023 ? hp : 1023);
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

    // CHARGE MODE: When close to enemy king, ignore traps and attack!
    // It's worth 50 trap damage to kill the king.
    boolean chargeMode =
        cachedEnemyKingLoc != null && me.distanceSquaredTo(cachedEnemyKingLoc) <= 16;

    if (!bug2WallFollowing) {
      // In charge mode, move directly toward enemy king
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

  /** Score movement directions and return the best one. */
  private static Direction getBestMoveDirection(RobotController rc, MapLocation target)
      throws GameActionException {
    Direction facing = rc.getDirection();
    Direction toTarget = myLoc.directionTo(target);
    int targetX = target.x;
    int targetY = target.y;

    Direction bestDir = null;
    int bestScore = Integer.MIN_VALUE;

    for (int i = 8; --i >= 0; ) {
      Direction dir = DIRECTIONS[i];
      if (!canMoveSafely(rc, dir)) continue;

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
        score += FORWARD_MOVE_BONUS >> 1;
      }

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

    // Priority 1: Turn toward enemy king (only if confirmed)
    if (enemyKingConfirmed) {
      MapLocation target = cachedEnemyKingLoc;
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

  /**
   * Search scan - turn aggressively to find the enemy king when location is unknown. Rats turn to
   * scan different directions based on their ID and round number.
   */
  private static void trySearchScan(RobotController rc) throws GameActionException {
    if (!rc.isMovementReady()) return;

    Direction currentFacing = rc.getDirection();
    int round = cachedRound;
    int id = rc.getID();

    // Scan more frequently - every 3 rounds
    if ((round + id) % 3 == 0) {
      // Scan by turning 90 degrees
      Direction scanDir = currentFacing.rotateRight().rotateRight();
      if (rc.canTurn(scanDir)) {
        rc.turn(scanDir);
        return;
      }
    }

    // Otherwise, turn toward the cached best target (already computed in scoreAllTargets)
    if (cachedBestTarget != null && !cachedBestTarget.equals(myLoc)) {
      Direction toTarget = myLoc.directionTo(cachedBestTarget);
      if (toTarget != Direction.CENTER && toTarget != currentFacing) {
        int angleDiff = getAngleDifference(currentFacing, toTarget);
        if (angleDiff > 45 && rc.canTurn(toTarget)) {
          rc.turn(toTarget);
          return;
        }
      }
    }
  }

  /** Returns angle difference in degrees (0-180). */
  private static int getAngleDifference(Direction a, Direction b) {
    int diff = a.ordinal() - b.ordinal();
    if (diff < 0) diff = -diff;
    if (diff > 4) diff = 8 - diff;
    return diff * 45;
  }

  /** Calculate direction from delta coordinates. */
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

  /** Check if any cats are nearby. */
  private static RobotInfo findDangerousCat(RobotInfo[] neutrals) {
    for (int i = neutrals.length; --i >= 0; ) {
      RobotInfo neutral = neutrals[i];
      if (neutral.getType() == UnitType.CAT) {
        return neutral;
      }
    }
    return null;
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
    for (int i = 8; --i >= 0; ) {
      Direction dir = DIRECTIONS[i];

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
    // Fallback: try directions away from cat
    if (rc.canMove(awayFromCat)) {
      rc.move(awayFromCat);
      return true;
    }
    Direction left1 = awayFromCat.rotateLeft();
    if (rc.canMove(left1)) {
      rc.move(left1);
      return true;
    }
    Direction right1 = awayFromCat.rotateRight();
    if (rc.canMove(right1)) {
      rc.move(right1);
      return true;
    }
    if (rc.canMove(left1.rotateLeft())) {
      rc.move(left1.rotateLeft());
      return true;
    }
    if (rc.canMove(right1.rotateRight())) {
      rc.move(right1.rotateRight());
      return true;
    }

    return false;
  }

  // ========================================================================
  // INTERCEPTOR BEHAVIOR
  // ========================================================================

  /** Check if this rat should act as interceptor (defend our king). */
  private static boolean shouldIntercept(RobotController rc) {
    // Only 5% interceptors - almost everyone ATTACKS!
    return rc.getID() % 20 == 0;
  }

  /** Interceptor behavior: patrol near our king, attack enemies, collect cheese. */
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
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
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
    // ========== CACHE RC METHODS (Bytecode Optimization) ==========
    int bcStart = PROFILE ? Clock.getBytecodesLeft() : 0;
    myLoc = rc.getLocation();
    cachedRound = rc.getRoundNum();
    cachedMyID = rc.getID();
    cachedMyDirection = rc.getDirection();
    myLocX = myLoc.x;
    myLocY = myLoc.y;

    // Use cached values
    MapLocation me = myLoc;
    int hp = rc.getHealth();
    int round = cachedRound;

    // Check for cats FIRST - highest priority escape!
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

    // Detect damage BEFORE updating lastKingHP (used for spawn decisions)
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

    // 3. Check for enemies - OPTIMIZATION: Reuse nearbyEnemies from earlier
    RobotInfo[] enemies = nearbyEnemies; // Already sensed at line 1357
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
    // Stop spawning if taking damage and low on cheese
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

    // 8. King-side timeout: If no enemy king confirmed after timeout, mark estimate as wrong
    // This is more reliable than squeak relay since squeaks have limited range
    if (!enemyKingConfirmed && estimateCheckedRound == 0) {
      int mapWidth = rc.getMapWidth();
      int mapHeight = rc.getMapHeight();
      int mapDiagonal = (int) Math.sqrt(mapWidth * mapWidth + mapHeight * mapHeight);
      int timeout = BASE_VERIFICATION_TIMEOUT + (mapDiagonal / 2);

      if (round > timeout) {
        // No confirmation received - estimate is probably wrong
        rc.writeSharedArray(SLOT_ESTIMATE_CHECKED, round);
        estimateCheckedRound = round;
      }
    }

    // 9. Broadcast enemy king if visible
    broadcastEnemyKing(rc, enemies);

    // 10. Update economy mode
    updateEconomyMode(rc);

    // ========== BYTECODE PROFILING (End of turn) ==========
    if (PROFILE && round % 10 == 0) {
      int bcUsed = bcStart - Clock.getBytecodesLeft();
      System.out.println("[ratbot6 KING] Round:" + round + " Bytecode:" + bcUsed);
    }
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

  /** Broadcast enemy king position if visible. */
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

  /** Try to spawn a baby rat TOWARD the enemy king. */
  private static boolean trySpawnRat(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();

    // Get direction TOWARD enemy king - spawn attackers in that direction!
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = kingLoc.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    // Priority spawn directions: toward enemy first, then adjacent, then away
    Direction[] spawnPriority = {
      toEnemy,
      toEnemy.rotateLeft(),
      toEnemy.rotateRight(),
      toEnemy.rotateLeft().rotateLeft(),
      toEnemy.rotateRight().rotateRight(),
      toEnemy.opposite().rotateLeft(),
      toEnemy.opposite().rotateRight(),
      toEnemy.opposite()
    };

    // Try to spawn at distance 2 (backward loop for bytecode efficiency)
    for (int i = 8; --i >= 0; ) {
      Direction dir = spawnPriority[i];
      MapLocation spawnLoc = kingLoc.add(dir).add(dir);
      if (rc.canBuildRat(spawnLoc)) {
        rc.buildRat(spawnLoc);
        return true;
      }
    }

    // Try distance 3 and 4 (backward loops for bytecode efficiency)
    for (int dist = 3; dist <= 4; dist++) {
      for (int i = 8; --i >= 0; ) {
        Direction dir = spawnPriority[i];
        MapLocation spawnLoc = kingLoc.translate(dir.dx * dist, dir.dy * dist);
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

    for (int i = 8; --i >= 0; ) {
      Direction dir = DIRECTIONS[i];
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
   * Find nearby cheese locations by scanning MapInfo. OPTIMIZATION: Already uses backward loop.
   * MapInfo sensing is necessary since no senseNearbyCheese() API exists.
   */
  private static void findNearbyCheese(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, 20);
    cheeseCount = 0;

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

  /** Baby rat main loop. Simple: seek and destroy. */
  private static void runBabyRat(RobotController rc) throws GameActionException {
    // Skip turn if being ratnapped
    if (rc.isBeingThrown() || rc.isBeingCarried()) {
      return;
    }

    // ========== CACHE RC METHODS (Bytecode Optimization) ==========
    int bcStart = PROFILE ? Clock.getBytecodesLeft() : 0;
    myLoc = rc.getLocation();
    cachedRound = rc.getRoundNum();
    cachedMyID = rc.getID();
    cachedMyDirection = rc.getDirection();
    myLocX = myLoc.x;
    myLocY = myLoc.y;

    // Use cached values
    MapLocation me = myLoc;
    int round = cachedRound;
    int id = cachedMyID;

    // 1. Update game state
    updateGameState(rc);

    // 2. Sense enemies and cheese
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    findNearbyCheese(rc);

    // 3. Check if interceptor (small % to defend king)
    if (shouldIntercept(rc)) {
      runInterceptor(rc, enemies);
      return;
    }

    // 4. If close to enemy king location but can't see enemies, turn to look
    // (rats have a vision cone, not 360° vision)
    if (cachedEnemyKingLoc != null && enemies.length == 0) {
      int distToEnemyKing = me.distanceSquaredTo(cachedEnemyKingLoc);
      Direction facing = rc.getDirection();
      Direction toKing = me.directionTo(cachedEnemyKingLoc);

      // Turn to face enemy king location when close
      if (distToEnemyKing <= 8 && toKing != Direction.CENTER && toKing != facing) {
        // Close to king location - turn to face it
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
    //    In SEARCH mode (enemy king not confirmed), turn more aggressively to scan
    if (enemies.length == 0 && cheeseCount == 0) {
      if (!enemyKingConfirmed) {
        // In search mode - scan more aggressively
        trySearchScan(rc);
      } else {
        tryTurnTowardTarget(rc);
      }
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

    // 9. Move toward target
    if (cachedBestTarget != null) {
      bug2MoveTo(rc, cachedBestTarget);
    }

    // 10. Squeak enemy king position when found
    updateEnemyKingFromBabyRat(rc, enemies);

    // PERF: Removed mine squeaking - not worth bytecode

    // ========== BYTECODE PROFILING ==========
    if (PROFILE && cachedRound % 10 == 0) {
      int bcUsed = bcStart - Clock.getBytecodesLeft();
      System.out.println(
          "[ratbot6 BABY] Round:" + cachedRound + " ID:" + cachedMyID + " Bytecode:" + bcUsed);
    }
  }

  /** Baby rat updates cached enemy king position if visible and squeaks to share intel. */
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
          if (hpBits > 15) hpBits = 15;
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

  /** King reads squeaks from baby rats and updates shared array with enemy king position. */
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
        // Baby rat found the enemy king! Update position.
        int y = (content >> 16) & 0xFFF;
        int x = (content >> 4) & 0xFFF;
        int hpBits = content & 0xF;
        int hp = hpBits * 35;

        rc.writeSharedArray(SLOT_ENEMY_KING_X, x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, y);
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, hp < 1023 ? hp : 1023);
        rc.writeSharedArray(SLOT_ENEMY_KING_ROUND, round);

        // Only allocate if position changed
        if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
          cachedEnemyKingLoc = new MapLocation(x, y);
        }
        cachedEnemyKingHP = hp;
        enemyKingConfirmed = true;
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
}
