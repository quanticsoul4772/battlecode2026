package ratbot5;

import battlecode.common.*;

/**
 * ratbot5 - Extreme bytecode optimized version
 *
 * <p>Optimizations applied: - Static cached MapLocations (avoid new allocations) - Bitwise
 * operations instead of division/modulo - Cached array lengths and loop bounds - Inlined method
 * calls in hot paths - Direction dx/dy lookup tables - No enhanced for-loops (iterator allocation)
 * - Ternary instead of Math.min/max
 */
public class RobotPlayer {
  // ================================================================
  // CONFIGURATION - Grouped tunable parameters
  // ================================================================

  // ========== DEBUG CONFIG ==========
  private static final boolean DEBUG = false; // Set true for testing only
  private static final boolean PROFILE = false; // Set true to enable bytecode profiling
  private static final int PROFILE_INTERVAL = 10; // Profile every N rounds

  // ========== MAP SIZE THRESHOLDS ==========
  private static final int SMALL_MAP_AREA = 40 * 40; // 1600 tiles
  private static final int MEDIUM_MAP_AREA = 50 * 50; // 2500 tiles

  // ========== COMBAT CONFIG ==========
  private static final int ENHANCED_ATTACK_CHEESE = 16;
  private static final int ENHANCED_THRESHOLD = 300;
  private static final int SMALL_MAP_ENHANCED_THRESHOLD = 100;

  // ========== POPULATION CONFIG ==========
  private static final int INITIAL_SPAWN_COUNT = 10;
  private static final int CONTINUOUS_SPAWN_RESERVE =
      400; // Higher reserve to prevent economy collapse
  private static final int COLLECTOR_MINIMUM = 3;
  private static final int SMALL_MAP_INITIAL_SPAWN = 10; // Aggressive: max attackers early
  private static final int SMALL_MAP_COLLECTOR_MIN = 2;
  private static final int SMALL_MAP_ALL_ATTACK_ROUNDS = 25; // All rats attack for first N rounds
  private static final int SMALL_MAP_CONTINUOUS_RESERVE =
      100; // Lower reserve for aggressive small maps
  private static final int SPAWN_COOLDOWN_ROUNDS =
      3; // Only spawn 1 rat every N rounds after initial burst

  // ========== ARMY SIZE CONFIG ==========
  private static final int MIN_ARMY_SIZE = 6; // Below this triggers emergency spawning
  private static final int EMERGENCY_SPAWN_RESERVE = 80; // Lower reserve during emergency
  private static final int HEALTHY_ARMY_SIZE = 12; // Above this, use normal cooldown

  // ========== MOVEMENT CONFIG ==========
  private static final int FORCED_MOVEMENT_THRESHOLD = 3;
  private static final int LOW_BYTECODE_THRESHOLD = 800;

  // ========== ECONOMY CONFIG ==========
  private static final int DELIVERY_THRESHOLD = 5;
  private static final int KING_CHEESE_RESERVE = 100;
  private static final int SMALL_MAP_KING_RESERVE = 50;
  private static final int CHEESE_SENSE_RADIUS_SQ = 10; // ~30 tiles instead of ~60
  private static final int GOOD_ENOUGH_CHEESE_DIST_SQ = 8; // Early return threshold

  // ========== THROTTLE CONFIG ==========
  private static final int SQUEAK_THROTTLE_ROUNDS = 15;
  private static final int SENSE_THROTTLE_ROUNDS = 3;
  private static final int MAX_SQUEAKS_TO_READ = 12;

  // ========== CAT DEFENSE CONFIG ==========
  private static final int CAT_FLEE_DIST_SQ = 16;
  private static final int CAT_KING_FLEE_DIST_SQ = 49;
  private static final int CAT_KITE_MIN_DIST_SQ = 10;
  private static final int CAT_KITE_MAX_DIST_SQ = 25;
  private static final int CAT_TRAP_TARGET = 10;
  private static final int CAT_TRAP_END_ROUND = 40;

  // ========== RAT DEFENSE CONFIG ==========
  private static final int RAT_TRAP_EARLY_TARGET = 10; // For medium/large maps
  private static final int SMALL_MAP_RAT_TRAP_TARGET =
      3; // Minimal traps on small maps - invest in attackers
  private static final int RAT_TRAP_EARLY_WINDOW = 15; // Extend window from 5 to 15 rounds
  private static final int DEFEND_KING_DIST_SQ = 36;
  private static final int BABY_RAT_TRAP_COOLDOWN = 20; // Rounds between baby rat trap placements
  private static final int REACTIVE_TRAP_DIST_SQ = 25; // Place trap when enemy this close

  // ================================================================
  // BYTECODE-OPTIMIZED CONSTANTS (avoid repeated allocations)
  // ================================================================

  // Direction array cached with length constant (saves ~1 bytecode per loop iteration)
  private static final Direction[] DIRS = Direction.allDirections();
  private static final int DIRS_LEN = 9; // DIRS.length cached

  // Direction delta lookup tables (avoid Direction.dx/dy method calls)
  // Order: NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST, CENTER
  private static final int[] DIR_DX = {0, 1, 1, 1, 0, -1, -1, -1, 0};
  private static final int[] DIR_DY = {1, 1, 0, -1, -1, -1, 0, 1, 0};

  // Cat trap placement priorities (static to avoid allocation each call)
  private static final Direction[] CAT_TRAP_PRIORITIES = new Direction[5];

  // Baby rat bytecode limit (constant, avoids rc.getType().getBytecodeLimit())
  private static final int BABY_RAT_BYTECODE_LIMIT = 2500;
  private static final int KING_BYTECODE_LIMIT = 5000;

  // Baby rat vision radius squared (avoids rc.getType().getVisionRadiusSquared() chain)
  private static final int BABY_RAT_VISION_SQ = 20;

  // ================================================================
  // BYTECODE PROFILING (track usage per section)
  // ================================================================
  private static int profTurnStart = 0;
  private static int profSenseNeutral = 0;
  private static int profSenseEnemy = 0;
  private static int profMovement = 0;
  private static int profCombat = 0;
  private static int profCheese = 0;
  private static int profCache = 0;
  private static int profOther = 0;
  private static int profTurnTotal = 0;
  private static int profMaxTurn = 0;
  private static int profSampleCount = 0;

  // Per-section accumulators for averaging
  private static long profAccumSenseNeutral = 0;
  private static long profAccumSenseEnemy = 0;
  private static long profAccumMovement = 0;
  private static long profAccumCombat = 0;
  private static long profAccumCheese = 0;
  private static long profAccumCache = 0;
  private static long profAccumTotal = 0;

  private static void profReset() {
    profSenseNeutral = 0;
    profSenseEnemy = 0;
    profMovement = 0;
    profCombat = 0;
    profCheese = 0;
    profCache = 0;
    profOther = 0;
  }

  private static void profStartTurn() {
    profTurnStart = Clock.getBytecodeNum();
    profReset();
  }

  private static void profEndTurn(int round, int id, boolean isKing) {
    profTurnTotal = Clock.getBytecodeNum() - profTurnStart;
    profOther =
        profTurnTotal
            - profSenseNeutral
            - profSenseEnemy
            - profMovement
            - profCombat
            - profCheese
            - profCache;
    if (profOther < 0) profOther = 0;

    // Track max
    if (profTurnTotal > profMaxTurn) profMaxTurn = profTurnTotal;

    // Accumulate for averaging
    profAccumSenseNeutral += profSenseNeutral;
    profAccumSenseEnemy += profSenseEnemy;
    profAccumMovement += profMovement;
    profAccumCombat += profCombat;
    profAccumCheese += profCheese;
    profAccumCache += profCache;
    profAccumTotal += profTurnTotal;
    profSampleCount++;

    // Print profile every PROFILE_INTERVAL rounds
    if (round % PROFILE_INTERVAL == 0) {
      int limit = isKing ? KING_BYTECODE_LIMIT : BABY_RAT_BYTECODE_LIMIT;
      int pct = (profTurnTotal * 100) / limit;
      System.out.println(
          "PROF:"
              + round
              + ":"
              + id
              + ":"
              + (isKing ? "KING" : "BABY")
              + " "
              + profTurnTotal
              + "/"
              + limit
              + " ("
              + pct
              + "%)"
              + " | sense:"
              + (profSenseNeutral + profSenseEnemy)
              + " (N:"
              + profSenseNeutral
              + " E:"
              + profSenseEnemy
              + ")"
              + " move:"
              + profMovement
              + " combat:"
              + profCombat
              + " cheese:"
              + profCheese
              + " cache:"
              + profCache
              + " other:"
              + profOther
              + " | max:"
              + profMaxTurn);
    }

    // Print summary every 100 rounds
    if (round % 100 == 0 && profSampleCount > 0) {
      System.out.println(
          "PROF_AVG:"
              + round
              + ":"
              + id
              + ":"
              + " total="
              + (profAccumTotal / profSampleCount)
              + " sense="
              + ((profAccumSenseNeutral + profAccumSenseEnemy) / profSampleCount)
              + " move="
              + (profAccumMovement / profSampleCount)
              + " combat="
              + (profAccumCombat / profSampleCount)
              + " cheese="
              + (profAccumCheese / profSampleCount)
              + " samples="
              + profSampleCount);
    }
  }

  // ================================================================
  // CACHED SHARED ARRAY (read once per turn)
  // ================================================================
  private static int cachedKingX, cachedKingY, cachedEnemyX, cachedEnemyY;
  private static int lastCacheRound = -1;

  // Cached team references (avoids rc.getTeam().opponent() calls ~10 bc each)
  private static Team cachedOurTeam = null;
  private static Team cachedEnemyTeam = null;

  // ================================================================
  // CACHED MAPLOCATIONS (avoid repeated 'new MapLocation' allocations)
  // Each 'new MapLocation' costs ~5-10 bytecodes
  // ================================================================
  private static MapLocation cachedOurKingLoc = null;
  private static MapLocation cachedEnemyKingLoc = null;
  private static MapLocation cachedMapCenter = null;
  private static int lastKingLocRound = -1;

  // ================================================================
  // MAP SIZE DETECTION (computed once)
  // ================================================================
  private static int mapSize = -1; // 0=small, 1=medium, 2=large
  private static boolean mapSizeComputed = false;
  private static int mapWidth = 0;
  private static int mapHeight = 0;

  private static void computeMapSize(RobotController rc) {
    if (!mapSizeComputed) {
      mapWidth = rc.getMapWidth();
      mapHeight = rc.getMapHeight();
      int area = mapWidth * mapHeight;
      if (area <= SMALL_MAP_AREA) {
        mapSize = 0; // Small
      } else if (area <= MEDIUM_MAP_AREA) {
        mapSize = 1; // Medium
      } else {
        mapSize = 2; // Large
      }
      // Cache map center (bitwise shift instead of division)
      cachedMapCenter = new MapLocation(mapWidth >> 1, mapHeight >> 1);
      // Cache team references (saves ~10 bc per rc.getTeam().opponent() call)
      cachedOurTeam = rc.getTeam();
      cachedEnemyTeam = cachedOurTeam.opponent();
      mapSizeComputed = true;
    }
  }

  // Inline check avoids method call overhead
  private static boolean isSmallMap() {
    return mapSize == 0;
  }

  private static void refreshCache(RobotController rc) throws GameActionException {
    int r = rc.getRoundNum();
    if (r == lastCacheRound) return;
    lastCacheRound = r;
    cachedKingX = rc.readSharedArray(0);
    cachedKingY = rc.readSharedArray(1);
    cachedEnemyX = rc.readSharedArray(2);
    cachedEnemyY = rc.readSharedArray(3);

    // Update cached locations only if coordinates changed
    if (lastKingLocRound != r) {
      if (cachedOurKingLoc == null
          || cachedOurKingLoc.x != cachedKingX
          || cachedOurKingLoc.y != cachedKingY) {
        cachedOurKingLoc = new MapLocation(cachedKingX, cachedKingY);
      }
      if (cachedEnemyKingLoc == null
          || cachedEnemyKingLoc.x != cachedEnemyX
          || cachedEnemyKingLoc.y != cachedEnemyY) {
        cachedEnemyKingLoc = new MapLocation(cachedEnemyX, cachedEnemyY);
      }
      lastKingLocRound = r;
    }
  }

  // ================================================================
  // MAIN LOOP
  // ================================================================

  public static void run(RobotController rc) throws GameActionException {
    // One-time initialization
    computeMapSize(rc);

    while (true) {
      try {
        if (rc.getType().isRatKingType()) {
          runKing(rc);
        } else {
          runBabyRat(rc);
        }
      } catch (Exception e) {
        if (DEBUG) e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ================================================================
  // KING BEHAVIOR (King statics are OK - only one king per team)
  // ================================================================

  private static int spawnCount = 0;
  private static int trapCount = 0;
  private static int catTrapCount = 0;
  private static int ratTrapCount = 0;
  private static int lastKingX = -1, lastKingY = -1;
  private static int lastSpawnRound = 0; // Track last spawn for cooldown

  private static void runKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    if (PROFILE) profStartTurn();
    int cheese = rc.getGlobalCheese();
    MapLocation me = rc.getLocation();
    int kingHP = rc.getHealth();

    if (DEBUG && (round & 63) == 0) { // Bitwise AND instead of modulo
      System.out.println(
          "KING:" + round + ":cheese=" + cheese + " HP=" + kingHP + " spawned=" + spawnCount);
    }

    // Write position to shared array ONLY if changed
    if (me.x != lastKingX || me.y != lastKingY) {
      rc.writeSharedArray(0, me.x);
      rc.writeSharedArray(1, me.y);
      lastKingX = me.x;
      lastKingY = me.y;
    }

    // Calculate enemy king (round 1 only)
    if (round == 1) {
      rc.writeSharedArray(2, mapWidth - 1 - me.x);
      rc.writeSharedArray(3, mapHeight - 1 - me.y);
    }

    // Determine map-size-based parameters (inlined)
    boolean smallMap = mapSize == 0;
    int initialSpawnTarget = smallMap ? SMALL_MAP_INITIAL_SPAWN : INITIAL_SPAWN_COUNT;
    int cheeseReserve = smallMap ? SMALL_MAP_KING_RESERVE : KING_CHEESE_RESERVE;
    int collectorMin = smallMap ? SMALL_MAP_COLLECTOR_MIN : COLLECTOR_MINIMUM;

    // Get map-specific trap target
    int trapTarget = smallMap ? SMALL_MAP_RAT_TRAP_TARGET : RAT_TRAP_EARLY_TARGET;

    // SMALL MAP STRATEGY: Spawn attackers FIRST, then minimal traps
    // MEDIUM/LARGE MAP: Traps first, then spawn
    if (smallMap) {
      // PRIORITY 0A (Small): Spawn attackers ASAP
      if (spawnCount < initialSpawnTarget) {
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + cheeseReserve) {
          spawnRat(rc, me);
          if (DEBUG) System.out.println("RUSH_SPAWN:" + round + ":rat#" + spawnCount);
        }
      }
      // PRIORITY 0B (Small): Place minimal traps after spawning started
      if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget && spawnCount >= 3) {
        placeDefensiveTrapsTowardEnemy(rc, me);
      }
    } else {
      // PRIORITY 0 (Medium/Large): Early rat trap perimeter
      if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget) {
        placeDefensiveTrapsTowardEnemy(rc, me);
      }
    }

    // PRIORITY 1: Place cat traps early (cooperation only, rounds 5-40)
    if (!smallMap
        && rc.isCooperation()
        && round >= 5
        && round <= CAT_TRAP_END_ROUND
        && catTrapCount < CAT_TRAP_TARGET) {
      placeCatTraps(rc, me);
    }

    // Spawn initial rats (skip for small maps - already handled above)
    if (!smallMap && spawnCount < initialSpawnTarget) {
      int cost = rc.getCurrentRatCost();
      if (cheese > cost + cheeseReserve) {
        spawnRat(rc, me);
        if (DEBUG) System.out.println("SPAWN:" + round + ":rat#" + spawnCount);
      }
    } else {
      // CONTINUOUS SPAWNING: Keep deploying rats as resources allow!
      // Dynamic spawning based on army health
      int cost = rc.getCurrentRatCost();

      // Count our army size and collectors in ONE sense call (saves ~100 bytecodes)
      int[] armyCounts = countArmyAndCollectors(rc);
      int armySize = armyCounts[0];
      int collectors = armyCounts[1];
      boolean emergencyMode = armySize < MIN_ARMY_SIZE;
      boolean healthyArmy = armySize >= HEALTHY_ARMY_SIZE;

      // Dynamic reserve: lower when army is small, higher when healthy
      int continuousReserve;
      if (emergencyMode) {
        continuousReserve = EMERGENCY_SPAWN_RESERVE; // Desperate - spawn with minimal reserve
      } else if (smallMap) {
        continuousReserve = SMALL_MAP_CONTINUOUS_RESERVE;
      } else {
        continuousReserve = CONTINUOUS_SPAWN_RESERVE;
      }

      // Dynamic cooldown: no cooldown in emergency, normal otherwise
      int spawnCooldown;
      if (emergencyMode) {
        spawnCooldown = 1; // Emergency - spawn every round
      } else if (smallMap) {
        spawnCooldown = 1;
      } else if (healthyArmy) {
        spawnCooldown = SPAWN_COOLDOWN_ROUNDS + 1; // Healthy - save cheese
      } else {
        spawnCooldown = SPAWN_COOLDOWN_ROUNDS;
      }
      boolean cooldownReady = (round - lastSpawnRound) >= spawnCooldown;

      // Spawn if we have enough cheese above reserve AND cooldown is ready
      if (cheese > cost + continuousReserve && cooldownReady) {
        // Check if we need more collectors (maintain minimum)
        boolean needCollectors = collectors < collectorMin;

        // Always try to spawn - the role is determined by robot ID at birth
        // We just need to keep spawning to maintain army strength
        if (spawnRat(rc, me)) {
          lastSpawnRound = round;
          if (DEBUG) {
            String reason =
                emergencyMode ? "EMERGENCY" : (needCollectors ? "REPLACE_COL" : "CONTINUOUS");
            System.out.println(
                reason
                    + ":"
                    + round
                    + ":rat#"
                    + spawnCount
                    + " cheese="
                    + cheese
                    + " army="
                    + armySize);
          }
        }
      } else if (DEBUG && (round & 31) == 0) {
        // Debug why we're not spawning
        boolean canAfford = cheese > cost + continuousReserve;
        System.out.println(
            "NO_SPAWN:"
                + round
                + ":cheese="
                + cheese
                + " cost="
                + cost
                + " reserve="
                + continuousReserve
                + " afford="
                + canAfford
                + " cooldown="
                + cooldownReady
                + " army="
                + armySize);
      }
    }

    // Place defensive rat traps and dirt walls
    if (spawnCount >= 10 && cheese > 300) {
      for (int i = 0; i < DIRS_LEN; i++) {
        Direction dir = DIRS[i];
        // Use translate instead of add().add() - saves ~5 bytecodes
        int dx = DIR_DX[i] << 1; // *2 via shift
        int dy = DIR_DY[i] << 1;
        MapLocation loc = me.translate(dx, dy);

        if (rc.getDirt() < 4 && rc.canPlaceDirt(loc)) {
          rc.placeDirt(loc);
          return;
        }

        if (trapCount < 5 && rc.canPlaceRatTrap(loc)) {
          rc.placeRatTrap(loc);
          trapCount++;
          return;
        }
      }
    }

    // PRIORITY 2: SENSE THREATS
    int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] neutrals = rc.senseNearbyRobots(CAT_KING_FLEE_DIST_SQ, Team.NEUTRAL);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcBefore;
    RobotInfo nearbyCat = null;
    for (int i = neutrals.length - 1; i >= 0; i--) {
      if (neutrals[i].getType() == UnitType.CAT) {
        nearbyCat = neutrals[i];
        break;
      }
    }

    // Sense enemy rats
    bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(25, cachedEnemyTeam);
    if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcBefore;
    int enemyCount = 0;
    RobotInfo closestEnemy = null;
    int closestDist = Integer.MAX_VALUE;
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType()) {
        enemyCount++;
        int dist = me.distanceSquaredTo(enemy.getLocation());
        if (dist < closestDist) {
          closestDist = dist;
          closestEnemy = enemy;
        }
      }
    }

    // Update king under attack flag
    boolean kingUnderAttack = (enemyCount >= 2 || closestDist <= 9);
    rc.writeSharedArray(4, kingUnderAttack ? 1 : 0);

    // PRIORITY 2A: REACTIVE TRAP PLACEMENT - place trap in enemy direction
    if (closestEnemy != null && closestDist <= REACTIVE_TRAP_DIST_SQ && rc.isActionReady()) {
      MapLocation enemyLoc = closestEnemy.getLocation();
      Direction toEnemy = me.directionTo(enemyLoc);
      if (toEnemy != Direction.CENTER) {
        // Try to place trap between king and approaching enemy
        for (int dist = 2; dist <= 3; dist++) {
          MapLocation trapLoc = me.translate(toEnemy.dx * dist, toEnemy.dy * dist);
          if (rc.canPlaceRatTrap(trapLoc)) {
            rc.placeRatTrap(trapLoc);
            ratTrapCount++;
            if (DEBUG)
              System.out.println("REACTIVE_TRAP:" + round + ":" + trapLoc + " toward enemy");
            break; // Only place one trap per turn
          }
        }
      }
    }

    // PRIORITY 2B: KING ATTACKS ADJACENT ENEMIES
    if (closestEnemy != null && rc.isActionReady()) {
      MapLocation enemyLoc = closestEnemy.getLocation();
      if (rc.canAttack(enemyLoc)) {
        rc.attack(enemyLoc);
        if (DEBUG) System.out.println("KING_ATTACK:" + round + ":hit enemy at " + enemyLoc);
        return;
      }
      // Try ALL enemies
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation loc = enemy.getLocation();
          if (rc.canAttack(loc)) {
            rc.attack(loc);
            if (DEBUG) System.out.println("KING_ATTACK:" + round + ":hit enemy at " + loc);
            return;
          }
        }
      }
    }

    // PRIORITY 2C: CHASE OR FLEE based on enemy count
    if (closestEnemy != null) {
      MapLocation enemyLoc = closestEnemy.getLocation();

      if (enemyCount >= 3) {
        // Outnumbered - FLEE!
        Direction awayFromEnemy = enemyLoc.directionTo(me);
        if (awayFromEnemy == Direction.CENTER) {
          awayFromEnemy = DIRS[round & 7]; // Bitwise AND instead of modulo
        }
        if (kingFleeMove(rc, awayFromEnemy)) {
          if (DEBUG)
            System.out.println(
                "KING_FLEE_ENEMY:" + round + ":fled from " + enemyCount + " enemies");
          return;
        }
      } else if (closestDist > 2) {
        // CHASE to get in range
        Direction towardEnemy = me.directionTo(enemyLoc);
        if (towardEnemy != Direction.CENTER) {
          if (kingFleeMove(rc, towardEnemy)) {
            if (DEBUG)
              System.out.println(
                  "KING_CHASE:"
                      + round
                      + ":chasing "
                      + enemyCount
                      + " enemy at dist="
                      + closestDist);
            return;
          }
        }
      }
    }

    // PRIORITY 2D: FLEE FROM CATS
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);

      if (DEBUG) {
        System.out.println(
            "KING_CAT_DETECTED:" + round + ":cat at " + catLoc + " dist=" + distToCat);
      }

      Direction awayFromCat = catLoc.directionTo(me);
      if (awayFromCat == Direction.CENTER) {
        awayFromCat = DIRS[round & 7];
      }

      if (kingFleeMove(rc, awayFromCat)) {
        if (DEBUG) System.out.println("KING_FLEE:" + round + ":moved away from cat");
        return;
      }
    }

    // Normal king movement
    // STRATEGY: During trap placement phase, move AWAY from enemy
    // This positions traps BETWEEN king and incoming attackers
    if ((round & 1) == 0) { // Bitwise AND instead of modulo
      if (enemyCount == 0 && nearbyCat == null) {
        // During trap placement phase, retreat from enemy to position traps in front
        if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget) {
          Direction awayFromEnemy;
          if (cachedEnemyKingLoc != null) {
            awayFromEnemy = cachedEnemyKingLoc.directionTo(me);
          } else {
            awayFromEnemy = cachedMapCenter.directionTo(me);
          }
          if (awayFromEnemy != Direction.CENTER && kingFleeMove(rc, awayFromEnemy)) {
            if (DEBUG) System.out.println("KING_RETREAT:" + round + ":positioning for traps");
            return;
          }
        }

        // Normal exploration movement
        Direction facing = rc.getDirection(); // Cache direction
        MapLocation ahead = rc.adjacentLocation(facing);

        if (rc.canRemoveDirt(ahead)) {
          rc.removeDirt(ahead);
          return;
        }

        if (rc.canMoveForward()) {
          rc.moveForward();
        }
      }
    } else if (rc.canTurn() && (round % 10) == 0) {
      Direction newDir = DIRS[(round / 10) & 7];
      rc.turn(newDir);
    }

    if (PROFILE) profEndTurn(round, rc.getID(), true);
  }

  private static boolean spawnRat(RobotController rc, MapLocation kingLoc)
      throws GameActionException {
    for (int i = 0; i < DIRS_LEN; i++) {
      Direction dir = DIRS[i];
      if (dir == Direction.CENTER) continue;
      int dx = DIR_DX[i];
      int dy = DIR_DY[i];
      // Use translate for distance 2,3,4 - avoids chained .add() calls
      for (int dist = 2; dist <= 4; dist++) {
        MapLocation loc = kingLoc.translate(dx * dist, dy * dist);
        if (rc.canBuildRat(loc)) {
          rc.buildRat(loc);
          spawnCount++;
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Count army size AND collectors in a single senseNearbyRobots call. Returns int[2]: [0] = total
   * army size, [1] = collector count. This saves ~100 bytecodes vs calling countArmySize() and
   * countCollectors() separately.
   */
  private static final int[] armyCountResult = new int[2]; // Reusable to avoid allocation

  private static int[] countArmyAndCollectors(RobotController rc) throws GameActionException {
    int armyCount = 0;
    int collectorCount = 0;
    boolean small = mapSize == 0;
    RobotInfo[] team = rc.senseNearbyRobots(rc.getType().getVisionRadiusSquared(), cachedOurTeam);
    for (int i = team.length - 1; i >= 0; i--) {
      RobotInfo r = team[i];
      if (r.getType().isBabyRatType()) {
        armyCount++;
        int rid = r.getID();
        // Bitwise AND for modulo 2, integer modulo 3 for small
        boolean isCollector = small ? (rid % 3 == 0) : ((rid & 1) == 1);
        if (isCollector) collectorCount++;
      }
    }
    armyCountResult[0] = armyCount;
    armyCountResult[1] = collectorCount;
    return armyCountResult;
  }

  private static boolean kingFleeMove(RobotController rc, Direction awayDir)
      throws GameActionException {
    if (awayDir == Direction.CENTER) return false;

    MapLocation ahead = rc.adjacentLocation(awayDir);
    if (rc.canSenseLocation(ahead)) {
      if (rc.canRemoveDirt(ahead)) {
        rc.removeDirt(ahead);
        return true;
      }
    }

    Direction facing = rc.getDirection(); // Cache
    if (facing != awayDir && rc.canTurn()) {
      rc.turn(awayDir);
      return true;
    }

    if (rc.canMove(awayDir)) {
      rc.move(awayDir);
      return true;
    }

    // Try rotations
    Direction left = awayDir.rotateLeft();
    if (rc.canMove(left)) {
      rc.move(left);
      return true;
    }

    Direction right = awayDir.rotateRight();
    if (rc.canMove(right)) {
      rc.move(right);
      return true;
    }

    Direction left2 = left.rotateLeft();
    if (rc.canMove(left2)) {
      rc.move(left2);
      return true;
    }

    Direction right2 = right.rotateRight();
    if (rc.canMove(right2)) {
      rc.move(right2);
      return true;
    }

    return false;
  }

  // Direction priorities for focused trap placement toward enemy
  private static final Direction[] TRAP_PRIORITY_DIRS = new Direction[8];

  private static void placeDefensiveTrapsTowardEnemy(RobotController rc, MapLocation me)
      throws GameActionException {
    // Build trap direction priorities: focus toward enemy king
    // Guard against null cachedEnemyKingLoc (not set until round 1 writeSharedArray)
    Direction toEnemy;
    if (cachedEnemyKingLoc == null) {
      // Fallback: enemy is likely in opposite corner from us
      toEnemy = me.directionTo(cachedMapCenter);
      if (toEnemy == Direction.CENTER) {
        toEnemy = Direction.NORTH;
      }
    } else {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) {
        toEnemy = Direction.NORTH; // Fallback
      }
    }

    // Priority order: direct, flanks, then wider angles (funnel pattern)
    TRAP_PRIORITY_DIRS[0] = toEnemy;
    TRAP_PRIORITY_DIRS[1] = toEnemy.rotateLeft();
    TRAP_PRIORITY_DIRS[2] = toEnemy.rotateRight();
    TRAP_PRIORITY_DIRS[3] = toEnemy.rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[4] = toEnemy.rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[5] = toEnemy.rotateLeft().rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[6] = toEnemy.rotateRight().rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[7] = toEnemy.opposite();

    // Place traps in priority order at distances 3-4 (funnel shape)
    for (int p = 0; p < 8; p++) {
      Direction dir = TRAP_PRIORITY_DIRS[p];
      if (dir == Direction.CENTER) continue;

      int dx = dir.dx;
      int dy = dir.dy;
      // Distance 3-4 creates a defensive perimeter
      for (int dist = 3; dist <= 4; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          if (DEBUG)
            System.out.println(
                "RAT_TRAP:"
                    + rc.getRoundNum()
                    + ":"
                    + trapLoc
                    + " ("
                    + ratTrapCount
                    + "/"
                    + RAT_TRAP_EARLY_TARGET
                    + ") toward enemy");
          return;
        }
      }
    }
  }

  private static void placeCatTraps(RobotController rc, MapLocation me) throws GameActionException {
    // Build priorities array once at start (reuses static array)
    Direction towardCenter = me.directionTo(cachedMapCenter);
    CAT_TRAP_PRIORITIES[0] = towardCenter;
    CAT_TRAP_PRIORITIES[1] = towardCenter.rotateLeft();
    CAT_TRAP_PRIORITIES[2] = towardCenter.rotateRight();
    CAT_TRAP_PRIORITIES[3] = towardCenter.rotateLeft().rotateLeft();
    CAT_TRAP_PRIORITIES[4] = towardCenter.rotateRight().rotateRight();

    // Indexed loop instead of enhanced for-loop (saves ~20 bytecodes)
    for (int p = 0; p < 5; p++) {
      Direction dir = CAT_TRAP_PRIORITIES[p];
      if (dir == Direction.CENTER) continue;

      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 2; dist <= 3; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapCount++;
          if (DEBUG)
            System.out.println(
                "CAT_TRAP:"
                    + rc.getRoundNum()
                    + ":"
                    + trapLoc
                    + " ("
                    + catTrapCount
                    + "/"
                    + CAT_TRAP_TARGET
                    + ")");
          return;
        }
      }
    }
  }

  // ================================================================
  // BABY RAT BEHAVIOR
  // ================================================================

  private static MapLocation lastBabyPos = null;
  private static int lastBabyID = -1;
  private static int samePosRounds = 0;

  private static MapLocation targetCheese = null;
  private static int targetCheeseRound = -100;
  private static int lastTargetID = -1;

  private static int lastMineSqueakRound = -100;
  private static int lastSqueakID = -1;

  // Baby rat trap placement tracking
  private static int lastBabyTrapRound = -100;
  private static int lastBabyTrapID = -1;

  private static void runBabyRat(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    if (PROFILE) profStartTurn();

    // Refresh cached shared array
    int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    refreshCache(rc);
    if (PROFILE) profCache += Clock.getBytecodeNum() - bcBefore;

    // Early exit for thrown/carried rats (combined check)
    if (rc.isBeingThrown() || rc.isBeingCarried()) {
      if (PROFILE) profEndTurn(round, id, false);
      return;
    }

    // Compute role from ID - bitwise AND for modulo 2
    // SMALL MAP EARLY GAME: ALL rats attack for first SMALL_MAP_ALL_ATTACK_ROUNDS
    int myRole;
    if (mapSize == 0) {
      if (round < SMALL_MAP_ALL_ATTACK_ROUNDS) {
        myRole = 0; // ALL ATTACK during early rush
      } else {
        myRole = (id % 3 == 0) ? 1 : 0;
      }
    } else {
      myRole = id & 1; // 0=attacker, 1=collector
    }

    int health = rc.getHealth();
    MapLocation me = rc.getLocation();

    // Check for nearby cat
    bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo nearbyCat = findNearbyCat(rc);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcBefore;

    // Read kingNeedsHelp ONCE here, pass to sub-methods (saves ~10 bc/turn)
    boolean kingNeedsHelp = rc.readSharedArray(4) == 1;
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);

    // EMERGENCY: Sacrifice wounded rat to cat
    if (nearbyCat != null && health < 40) {
      MapLocation catLoc = nearbyCat.getLocation();
      int catToKingDist = catLoc.distanceSquaredTo(cachedOurKingLoc);

      if (catToKingDist <= 36) {
        if (me.distanceSquaredTo(catLoc) <= 2) {
          if (DEBUG)
            System.out.println("SACRIFICE:" + round + ":" + id + ":feeding cat to protect king");
          return;
        }
        moveTo(rc, catLoc);
        return;
      }
    }

    // RECALL: If king needs help AND we're FAR from king, move toward king
    // Rats already near king (distToKing <= DEFEND_KING_DIST_SQ) fall through to
    // runAttacker()/runCollector() which have combat code!
    if (kingNeedsHelp && distToKing > DEFEND_KING_DIST_SQ) {
      if (DEBUG) System.out.println("RECALL:" + round + ":" + id + ":returning to defend!");
      moveTo(rc, cachedOurKingLoc);
      return;
    }

    if (health < 30 && myRole == 1) {
      if (distToKing <= 9) {
        rc.disintegrate();
        return;
      }
    }

    // Visual debugging
    if (DEBUG) {
      String catStatus = nearbyCat != null ? " CAT!" : "";
      rc.setIndicatorString((myRole == 0 ? "ATK" : "COL") + " HP:" + health + catStatus);
      rc.setIndicatorDot(me, health > 50 ? 0 : 255, health > 50 ? 255 : 0, 0);

      if ((round & 127) == 0) { // round % 128 == 0
        rc.setTimelineMarker("Round " + round, 100, 100, 255);
      }

      if (myRole == 0) {
        rc.setIndicatorLine(me, cachedEnemyKingLoc, 255, 0, 0);
      } else if (rc.getRawCheese() >= DELIVERY_THRESHOLD) {
        rc.setIndicatorLine(me, cachedOurKingLoc, 0, 255, 0);
      }
    }

    // DISABLED: Don't create new kings - they tend to starve
    // The single original king is more effective

    if (myRole == 0) {
      runAttacker(rc, nearbyCat, round, id, me, kingNeedsHelp);
    } else {
      runCollector(rc, nearbyCat, round, id, me, kingNeedsHelp);
    }

    if (PROFILE) profEndTurn(round, id, false);
  }

  private static RobotInfo findNearbyCat(RobotController rc) throws GameActionException {
    RobotInfo[] neutrals = rc.senseNearbyRobots(25, Team.NEUTRAL);
    for (int i = neutrals.length - 1; i >= 0; i--) {
      if (neutrals[i].getType() == UnitType.CAT) {
        return neutrals[i];
      }
    }
    return null;
  }

  // ================================================================
  // ATTACKER
  // ================================================================

  private static void runAttacker(
      RobotController rc,
      RobotInfo nearbyCat,
      int round,
      int id,
      MapLocation me,
      boolean kingNeedsHelp)
      throws GameActionException {
    if (DEBUG && (round & 63) == 0) {
      System.out.println("ATK_START:" + round + ":" + id);
    }

    if (!rc.isActionReady()) {
      return;
    }

    // Bytecode guard - use cached constant instead of rc.getType().getBytecodeLimit()
    if (Clock.getBytecodesLeft() < BABY_RAT_BYTECODE_LIMIT / 10) {
      moveTo(rc, cachedEnemyKingLoc);
      return;
    }

    int distToOurKing = me.distanceSquaredTo(cachedOurKingLoc);

    // DEFENSE: King under attack
    if (kingNeedsHelp && distToOurKing <= DEFEND_KING_DIST_SQ) {
      int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
      RobotInfo[] nearKing = rc.senseNearbyRobots(20, cachedEnemyTeam);
      if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcBefore;

      // Try to place trap in enemy path when defending
      if (nearKing.length > 0) {
        babyRatPlaceTrap(rc, me, round, id);
      }

      for (int i = nearKing.length - 1; i >= 0; i--) {
        RobotInfo enemy = nearKing[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          if (rc.canAttack(enemyLoc)) {
            rc.attack(enemyLoc);
            if (DEBUG)
              System.out.println("DEFEND_KING:" + round + ":" + id + ":attacked enemy near king");
            return;
          }
          moveTo(rc, enemyLoc);
          return;
        }
      }
    }

    // CAT DEFENSE: Skip on small maps
    boolean smallMap = mapSize == 0;
    if (nearbyCat != null && rc.isCooperation() && !smallMap) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);

      if (distToCat <= 2) {
        if (rc.canAttack(catLoc)) {
          rc.attack(catLoc);
          if (DEBUG) System.out.println("CAT_ATTACK:" + round + ":" + id + ":damage=10");
          return;
        }
      }

      if (distToCat < CAT_KITE_MIN_DIST_SQ) {
        // Use translate instead of chained .add() - saves ~15 bytecodes
        Direction away = me.directionTo(catLoc).opposite();
        MapLocation fleeTarget = me.translate(away.dx * 3, away.dy * 3);
        moveTo(rc, fleeTarget);
        if (DEBUG) System.out.println("CAT_FLEE:" + round + ":" + id + ":too close");
        return;
      } else if (distToCat > CAT_KITE_MAX_DIST_SQ) {
        moveTo(rc, catLoc);
        if (DEBUG) System.out.println("CAT_ENGAGE:" + round + ":" + id + ":approaching");
        return;
      } else {
        Direction toCat = me.directionTo(catLoc);
        Direction circle = toCat.rotateLeft().rotateLeft();
        MapLocation circleTarget = me.translate(circle.dx << 1, circle.dy << 1);
        moveTo(rc, circleTarget);
        if (DEBUG) System.out.println("CAT_KITE:" + round + ":" + id + ":circling");
        return;
      }
    } else if (nearbyCat != null && smallMap) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);
      if (distToCat < CAT_KITE_MIN_DIST_SQ) {
        Direction away = me.directionTo(catLoc).opposite();
        MapLocation fleeTarget = me.translate(away.dx << 1, away.dy << 1);
        moveTo(rc, fleeTarget);
        if (DEBUG) System.out.println("SMALL_MAP_CAT_DODGE:" + round + ":" + id);
        return;
      }
    }

    // Ratnapping
    RobotInfo carrying = rc.getCarrying();
    if (carrying != null) {
      int distToKingRatnap = me.distanceSquaredTo(cachedOurKingLoc);
      if (distToKingRatnap <= 4) {
        Direction toKing = me.directionTo(cachedOurKingLoc);
        if (rc.canDropRat(toKing)) {
          rc.dropRat(toKing);
          return;
        }
      }

      if (rc.canThrowRat()) {
        Direction toKing = me.directionTo(cachedOurKingLoc);
        Direction facing = rc.getDirection();
        if (facing != toKing && rc.canTurn()) {
          rc.turn(toKing);
        } else {
          rc.throwRat();
        }
        return;
      }
    }

    int bcSense = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(BABY_RAT_VISION_SQ, cachedEnemyTeam);
    if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcSense;

    RobotInfo bestTarget = null;
    RobotInfo ratnap = null;
    int maxCheese = 0;

    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      UnitType t = enemy.getType(); // Cache type
      if (t.isBabyRatType()) {
        int hp = enemy.getHealth();
        if (hp < 50 && ratnap == null) {
          ratnap = enemy;
        }

        int cheese = enemy.getRawCheeseAmount();
        if (cheese > maxCheese || bestTarget == null) {
          maxCheese = cheese;
          bestTarget = enemy;
        }
      }
    }

    if (ratnap != null && carrying == null && rc.canCarryRat(ratnap.getLocation())) {
      rc.carryRat(ratnap.getLocation());
      return;
    }

    boolean coop = rc.isCooperation();
    // Use lower threshold for small maps to get damage boost earlier
    int enhancedThreshold = (mapSize == 0) ? SMALL_MAP_ENHANCED_THRESHOLD : ENHANCED_THRESHOLD;

    if (bestTarget != null) {
      MapLocation targetLoc = bestTarget.getLocation();
      if (rc.canAttack(targetLoc)) {
        if (!coop && rc.getGlobalCheese() > enhancedThreshold) {
          rc.attack(targetLoc, ENHANCED_ATTACK_CHEESE);
        } else {
          rc.attack(targetLoc);
        }
        if (DEBUG) System.out.println("ATTACK:" + round + ":" + id);
        return;
      }
    }

    // Attack king
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isRatKingType()) {
        MapLocation kingCenter = enemy.getLocation();

        if (rc.canAttack(kingCenter)) {
          rc.attack(kingCenter);
          return;
        }

        // Use translate instead of new MapLocation - saves ~5 bytecodes per iteration
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            MapLocation tile = kingCenter.translate(dx, dy);
            if (rc.canAttack(tile)) {
              rc.attack(tile);
              return;
            }
          }
        }

        // Squeak king location (throttled)
        if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
          int squeak = (1 << 28) | (kingCenter.y << 16) | (kingCenter.x << 4);
          rc.squeak(squeak);
          lastMineSqueakRound = round;
          lastSqueakID = id;
        }
      }
    }

    // Check squeaks
    Message[] squeaks = rc.readSqueaks(-1);
    int len = squeaks.length;
    // Ternary instead of Math.min - saves ~3 bytecodes
    int limit = len < MAX_SQUEAKS_TO_READ ? len : MAX_SQUEAKS_TO_READ;
    for (int i = len - 1; i >= len - limit && i >= 0; i--) {
      Message msg = squeaks[i];
      if (msg.getSenderID() != id) {
        int bytes = msg.getBytes();
        int type = (bytes >>> 28) & 0xF;
        if (type == 1) {
          int x = (bytes >> 4) & 0xFFF;
          int y = (bytes >> 16) & 0xFFF;
          MapLocation squeakedKing = new MapLocation(x, y);
          moveTo(rc, squeakedKing);
          return;
        }
      }
    }

    // Rush enemy king from cached location
    int bcMove = PROFILE ? Clock.getBytecodeNum() : 0;
    moveTo(rc, cachedEnemyKingLoc);
    if (PROFILE) profMovement += Clock.getBytecodeNum() - bcMove;
  }

  // ================================================================
  // COLLECTOR
  // ================================================================

  private static void runCollector(
      RobotController rc,
      RobotInfo nearbyCat,
      int round,
      int id,
      MapLocation me,
      boolean kingNeedsHelp)
      throws GameActionException {
    int cheese = rc.getRawCheese();

    if (DEBUG && (round & 63) == 0) {
      System.out.println("COL:" + round + ":" + id + ":cheese=" + cheese);
    }

    // Bytecode check
    if (Clock.getBytecodesLeft() < BABY_RAT_BYTECODE_LIMIT / 10) {
      if (cheese > 0) {
        moveTo(rc, cachedOurKingLoc);
      }
      return;
    }

    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);

    // Combat awareness
    if (kingNeedsHelp && distToKing <= DEFEND_KING_DIST_SQ) {
      int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
      RobotInfo[] enemies = rc.senseNearbyRobots(20, cachedEnemyTeam);
      if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcBefore;
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          if (rc.canAttack(enemyLoc)) {
            rc.attack(enemyLoc);
            if (DEBUG) System.out.println("COL_DEFEND:" + round + ":" + id + ":attacked enemy");
            return;
          }
          if (enemyLoc.distanceSquaredTo(cachedOurKingLoc) < distToKing) {
            moveTo(rc, enemyLoc);
            return;
          }
        }
      }
    }

    if (kingNeedsHelp && distToKing > DEFEND_KING_DIST_SQ) {
      if (DEBUG) System.out.println("COL_RECALL:" + round + ":" + id + ":returning to king");
      moveTo(rc, cachedOurKingLoc);
      return;
    }

    // CAT DEFENSE: Flee
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);

      if (distToCat <= CAT_FLEE_DIST_SQ) {
        Direction away = me.directionTo(catLoc).opposite();
        // Use translate instead of chained .add() - saves ~20 bytecodes
        MapLocation fleeTarget = me.translate(away.dx << 2, away.dy << 2);
        moveTo(rc, fleeTarget);
        if (DEBUG) System.out.println("COL_FLEE:" + round + ":" + id + ":cat at " + catLoc);
        return;
      }
    }

    if (cheese >= DELIVERY_THRESHOLD) {
      if (DEBUG) System.out.println("DELIVER_MODE:" + round + ":" + id);
      int bcMove = PROFILE ? Clock.getBytecodeNum() : 0;
      deliver(rc, me);
      if (PROFILE) profMovement += Clock.getBytecodeNum() - bcMove;
    } else {
      int bcCheese = PROFILE ? Clock.getBytecodeNum() : 0;
      collect(rc, me);
      if (PROFILE) profCheese += Clock.getBytecodeNum() - bcCheese;
    }
  }

  private static void collect(RobotController rc, MapLocation me) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();

    // Reset target cache if different robot
    if (lastTargetID != id) {
      targetCheese = null;
      lastTargetID = id;
    }

    // Check current target
    if (targetCheese != null) {
      if (rc.canPickUpCheese(targetCheese)) {
        rc.pickUpCheese(targetCheese);
        if (DEBUG) System.out.println("PICKUP:" + round + ":" + id + ":now=" + rc.getRawCheese());
        targetCheese = null;
        return;
      }
      if (me.distanceSquaredTo(targetCheese) <= 2 && rc.canSenseLocation(targetCheese)) {
        MapInfo info = rc.senseMapInfo(targetCheese);
        if (info.getCheeseAmount() == 0) {
          targetCheese = null;
        }
      }
    }

    // Rescan if no target (throttled)
    if (targetCheese == null || (round - targetCheeseRound) >= SENSE_THROTTLE_ROUNDS) {
      targetCheese = findNearestCheese(rc, me, round, id);
      targetCheeseRound = round;
    }

    // Move to target or explore
    if (targetCheese != null) {
      if (DEBUG && (round & 63) == 0) {
        System.out.println("MOVE_TO_CHEESE:" + round + ":" + id);
      }
      moveTo(rc, targetCheese);
    } else {
      moveTo(rc, cachedMapCenter);
    }
  }

  private static MapLocation findNearestCheese(
      RobotController rc, MapLocation me, int round, int id) throws GameActionException {
    int bcSense = PROFILE ? Clock.getBytecodeNum() : 0;
    // OPTIMIZATION 1: Use smaller radius (10 vs 20) - cuts tiles scanned by 50%
    MapInfo[] nearbyInfo = rc.senseNearbyMapInfos(me, CHEESE_SENSE_RADIUS_SQ);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcSense;

    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    // OPTIMIZATION 2: Simplified loop - no mine tracking overhead
    // OPTIMIZATION 3: Early return for "good enough" cheese
    for (int i = nearbyInfo.length - 1; i >= 0; i--) {
      int cheeseAmt = nearbyInfo[i].getCheeseAmount();
      if (cheeseAmt > 0) {
        MapLocation loc = nearbyInfo[i].getMapLocation();
        int dist = me.distanceSquaredTo(loc);
        // Early return for close enough cheese - saves scanning remaining tiles
        if (dist <= GOOD_ENOUGH_CHEESE_DIST_SQ) {
          return loc;
        }
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = loc;
        }
      }
    }

    // OPTIMIZATION 4: Only squeak mines occasionally (every 8 rounds)
    // This avoids the expensive hasCheeseMine() check every turn
    if ((round & 7) == 0 && nearest == null) {
      // Only scan for mines when we have no cheese target AND it's the right round
      for (int i = nearbyInfo.length - 1; i >= 0; i--) {
        if (nearbyInfo[i].hasCheeseMine()) {
          MapLocation mineLoc = nearbyInfo[i].getMapLocation();
          if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
            int squeak = (3 << 28) | (mineLoc.y << 16) | (mineLoc.x << 4);
            rc.squeak(squeak);
            lastMineSqueakRound = round;
            lastSqueakID = id;
          }
          nearest = mineLoc; // Head toward mine
          break;
        }
      }
    }

    // OPTIMIZATION 5: Skip squeak reading - just explore toward center
    // The squeak reading was expensive and rarely helps
    // If no cheese found, collect() will moveTo(cachedMapCenter)

    return nearest;
  }

  private static void deliver(RobotController rc, MapLocation me) throws GameActionException {
    int dist = me.distanceSquaredTo(cachedOurKingLoc);
    int round = rc.getRoundNum();
    int id = rc.getID();

    if (DEBUG && (round & 15) == 0) {
      System.out.println("DELIVER:" + round + ":" + id + ":dist=" + dist);
    }

    if (dist <= 9) {
      int amt = rc.getRawCheese();
      if (rc.canTransferCheese(cachedOurKingLoc, amt)) {
        rc.transferCheese(cachedOurKingLoc, amt);
        if (DEBUG) System.out.println("TRANSFER:" + round + ":" + id + ":amt=" + amt);

        // After successful delivery, try to place a defensive trap
        babyRatPlaceTrap(rc, me, round, id);
        return;
      }
    }

    moveTo(rc, cachedOurKingLoc);
  }

  /**
   * Baby rats help build defensive perimeter by placing traps near king after delivery. Cooldown
   * prevents spamming and saves cheese for spawning.
   */
  private static void babyRatPlaceTrap(RobotController rc, MapLocation me, int round, int id)
      throws GameActionException {
    // Reset cooldown tracker if different robot
    if (lastBabyTrapID != id) {
      lastBabyTrapRound = -100;
      lastBabyTrapID = id;
    }

    // Check cooldown
    if (round - lastBabyTrapRound < BABY_RAT_TRAP_COOLDOWN) {
      return;
    }

    // Only place traps if near king (dist <= 16)
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
    if (distToKing > 16) {
      return;
    }

    // Place trap toward enemy direction from current position
    Direction toEnemy = me.directionTo(cachedEnemyKingLoc);
    if (toEnemy == Direction.CENTER) {
      toEnemy = Direction.NORTH;
    }

    // Try placing trap in enemy direction at distance 1-2
    Direction[] tryDirs = {toEnemy, toEnemy.rotateLeft(), toEnemy.rotateRight()};

    for (int d = 0; d < 3; d++) {
      Direction dir = tryDirs[d];
      for (int dist = 1; dist <= 2; dist++) {
        MapLocation trapLoc = me.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          lastBabyTrapRound = round;
          if (DEBUG) System.out.println("BABY_TRAP:" + round + ":" + id + ":" + trapLoc);
          return;
        }
      }
    }
  }

  // ================================================================
  // MOVEMENT - Bytecode optimized
  // ================================================================

  private static void moveTo(RobotController rc, MapLocation target) throws GameActionException {
    if (!rc.isMovementReady()) {
      return;
    }

    MapLocation me = rc.getLocation();
    // Bytecode guard
    if (Clock.getBytecodesLeft() < LOW_BYTECODE_THRESHOLD) {
      Direction d = me.directionTo(target);
      if (d != Direction.CENTER) {
        Direction facing = rc.getDirection();
        if (facing != d && rc.canTurn()) {
          rc.turn(d);
        } else if (rc.canMoveForward()) {
          rc.moveForward();
        }
      }
      return;
    }

    Direction facing = rc.getDirection(); // Cache early
    MapLocation ahead = rc.adjacentLocation(facing);

    // Clear obstacles
    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      return;
    }
    if (rc.canRemoveRatTrap(ahead)) {
      rc.removeRatTrap(ahead);
      return;
    }
    if (rc.canRemoveCatTrap(ahead)) {
      rc.removeCatTrap(ahead);
      return;
    }

    // Stuck detection
    int id = rc.getID();
    if (lastBabyID != id) {
      lastBabyPos = null;
      samePosRounds = 0;
      lastBabyID = id;
    }

    if (lastBabyPos != null && lastBabyPos.equals(me)) {
      samePosRounds++;
    } else {
      samePosRounds = 0;
    }
    lastBabyPos = me;

    // Bug2 if stuck
    if (samePosRounds >= FORCED_MOVEMENT_THRESHOLD) {
      Direction bug2Dir = bug2(rc, target, me, id);
      if (bug2Dir != Direction.CENTER) {
        if (facing != bug2Dir && rc.canTurn()) {
          rc.turn(bug2Dir);
          return;
        }
        if (rc.canMoveForward()) {
          rc.moveForward();
          return;
        }
      }
    }

    // Normal movement
    Direction desired = me.directionTo(target);
    if (desired == Direction.CENTER) {
      return;
    }

    if (facing == desired) {
      MapLocation nextLoc = rc.adjacentLocation(facing);

      if (!rc.onTheMap(nextLoc)) {
        Direction left = desired.rotateLeft();
        if (left != Direction.CENTER && rc.canTurn()) {
          rc.turn(left);
          return;
        }
        Direction right = desired.rotateRight();
        if (right != Direction.CENTER && rc.canTurn()) {
          rc.turn(right);
          return;
        }
        return;
      }

      if (rc.canSenseLocation(nextLoc)) {
        MapInfo nextInfo = rc.senseMapInfo(nextLoc);

        if (!nextInfo.isPassable()) {
          Direction left = desired.rotateLeft();
          if (rc.canTurn()) {
            rc.turn(left);
            return;
          }
          Direction right = desired.rotateRight();
          if (rc.canTurn()) {
            rc.turn(right);
            return;
          }
        }

        if (nextInfo.getTrap() == TrapType.RAT_TRAP) {
          Direction left = desired.rotateLeft();
          if (rc.canTurn()) {
            rc.turn(left);
            return;
          }
          Direction right = desired.rotateRight();
          if (rc.canTurn()) {
            rc.turn(right);
            return;
          }
        }

        if (rc.isLocationOccupied(nextLoc)) {
          RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);
          if (blocker != null && blocker.getTeam() == cachedOurTeam) {
            Direction left = desired.rotateLeft();
            if (rc.canTurn()) {
              rc.turn(left);
              return;
            }
            Direction right = desired.rotateRight();
            if (rc.canTurn()) {
              rc.turn(right);
              return;
            }
          }
        }
      }

      if (rc.canMoveForward()) {
        rc.moveForward();
        return;
      }
    }

    // Turn toward target
    if (facing != desired && rc.isTurningReady() && rc.canTurn()) {
      rc.turn(desired);
      return;
    }

    // Emergency strafe
    if (!rc.canMoveForward() && rc.getMovementCooldownTurns() < 10) {
      for (int i = 0; i < DIRS_LEN; i++) {
        Direction dir = DIRS[i];
        if (rc.canMove(dir)) {
          rc.move(dir);
          return;
        }
      }
    }
  }

  // ================================================================
  // BUG2 PATHFINDING
  // ================================================================

  private static MapLocation bugTarget = null;
  private static boolean bugTracing = false;
  private static Direction bugTracingDir = Direction.NORTH;
  private static int bugStartDist = 0;
  private static int lastBugID = -1;

  private static Direction bug2(RobotController rc, MapLocation target, MapLocation me, int id)
      throws GameActionException {
    // Reset if different robot or target changed
    if (lastBugID != id || bugTarget == null || !bugTarget.equals(target)) {
      bugTarget = target;
      bugTracing = false;
      lastBugID = id;
    }

    Direction toTarget = me.directionTo(target);

    if (toTarget == Direction.CENTER) {
      return Direction.CENTER;
    }

    // Try direct path
    if (!bugTracing && rc.canMove(toTarget)) {
      return toTarget;
    }

    // Start tracing
    if (!bugTracing) {
      bugTracing = true;
      bugTracingDir = toTarget;
      bugStartDist = me.distanceSquaredTo(target);
    }

    // Follow obstacle
    if (bugTracing) {
      int curDist = me.distanceSquaredTo(target);

      if (curDist < bugStartDist && rc.canMove(toTarget)) {
        bugTracing = false;
        return toTarget;
      }

      for (int i = 0; i < 8; i++) {
        if (rc.canMove(bugTracingDir)) {
          Direction result = bugTracingDir;
          bugTracingDir = bugTracingDir.rotateLeft();
          return result;
        }
        bugTracingDir = bugTracingDir.rotateRight();
      }
    }

    return toTarget;
  }
}
