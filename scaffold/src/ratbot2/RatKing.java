package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Rat King behavior - Battlecode 2026
 *
 * <p>PRIMARY OBJECTIVES: 1. Spawn baby rats aggressively (build army for cat damage race) 2. Place
 * 10 cat traps (1,000 damage = 10% cat HP) 3. Track cats for baby rats (360° vision) 4. Survive
 * (consume 3 cheese/round)
 */
public class RatKing {
  private static int lastGlobalCheese = 2500;
  private static boolean trapsPlaced = false;
  private static int trapCount = 0;

  public static void run(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int globalCheese = rc.getGlobalCheese();

    // EMERGENCY: Critical starvation
    if (globalCheese < 50) {
      rc.writeSharedArray(Communications.SLOT_EMERGENCY, Communications.EMERGENCY_CRITICAL);
      System.out.println("EMERGENCY:" + round + ":CRITICAL:cheese=" + globalCheese);
      return;
    }

    // Clear emergency if recovered
    rc.writeSharedArray(Communications.SLOT_EMERGENCY, 0);

    // Broadcast king position (for cheese delivery)
    MapLocation myLoc = rc.getLocation();
    rc.writeSharedArray(Communications.SLOT_KING_X, myLoc.x);
    rc.writeSharedArray(Communications.SLOT_KING_Y, myLoc.y);

    // Write map dimensions (for zone calculation)
    if (round == 1) {
      rc.writeSharedArray(Communications.SLOT_MAP_WIDTH, rc.getMapWidth());
      rc.writeSharedArray(Communications.SLOT_MAP_HEIGHT, rc.getMapHeight());
    }

    // Track enemy king position
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    for (RobotInfo enemy : enemies) {
      if (enemy.getType() == UnitType.RAT_KING) {
        rc.writeSharedArray(Communications.SLOT_ENEMY_KING_X, enemy.getLocation().x);
        rc.writeSharedArray(Communications.SLOT_ENEMY_KING_Y, enemy.getLocation().y);
        if (round % 100 == 0) {
          System.out.println("ENEMY_KING_SPOTTED:" + round + ":" + enemy.getLocation());
        }
        break;
      }
    }

    // PRIORITY 1: Spawn baby rats FIRST (before traps) so they can escape
    // CRITICAL: Must spawn rats before building trap perimeter!
    if (round <= 40) {
      spawnBabyRat(rc);
    }

    // PRIORITY 2: Place rat traps for enemy defense (rounds 20-30, AFTER spawning)
    if (!ratTrapsPlaced && round >= 20 && round <= 30 && globalCheese >= 50) {
      placeRatTraps(rc);
    }

    // PRIORITY 3: Place cat traps (rounds 30-50, AFTER spawning)
    // Place traps FAR from king so rats aren't trapped
    if (!trapsPlaced && round >= 30 && round <= 50 && globalCheese >= 50 && trapCount < 10) {
      if (round % 20 == 0) {
        System.out.println(
            "TRAP_CHECK:"
                + round
                + ":attempting placement, cheese="
                + globalCheese
                + ", traps="
                + trapCount);
      }
      placeDefensiveTraps(rc);
      return; // Skip spawning this round to allow action cooldown for traps
    }

    // PRIORITY 4: Continue spawning throughout game
    spawnBabyRat(rc);

    // PRIORITY 3: Track cats (kings have 360° vision)
    trackCats(rc);

    // PRIORITY 4: Collect cheese (strategic)
    collectCheese(rc);

    // Logging
    if (round % 20 == 0) {
      int income = (globalCheese - lastGlobalCheese + 60) / 2;
      System.out.println(
          "KING:"
              + round
              + ":cheese="
              + globalCheese
              + ":income="
              + income
              + ":traps="
              + trapCount);
      lastGlobalCheese = globalCheese;
    }
  }

  private static int spawnCount = 0;
  private static boolean ratTrapsPlaced = false;
  private static int ratTrapCount = 0;

  private static int lastSpawnRound = 0;

  /**
   * Spawn baby rat at distance=2 (outside 3×3 king footprint). CONTINUOUS SPAWNING: Maintain army
   * throughout game.
   */
  private static void spawnBabyRat(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int cost = rc.getCurrentRatCost();
    int cheese = rc.getGlobalCheese();

    // Calculate spawn strategy based on game phase
    boolean shouldSpawn = false;
    int cheeseReserve = 100; // Default reserve

    // PHASE 1: Early defense (rounds 1-50) - aggressive spawning
    if (round <= 50) {
      shouldSpawn = spawnCount < 15;
      cheeseReserve = 50;
    }
    // PHASE 2: Mid game (rounds 51-300) - maintain army
    else if (round <= 300) {
      // Spawn every 50 rounds to replace losses
      shouldSpawn = (round - lastSpawnRound) >= 50;
      cheeseReserve = 150;
    }
    // PHASE 3: Late game (rounds 301+) - survival mode
    else {
      // Spawn more frequently as rats die
      shouldSpawn = (round - lastSpawnRound) >= 30;
      cheeseReserve = 200;
    }

    // Check if we have enough cheese
    if (!shouldSpawn || cheese < cost + cheeseReserve) {
      if (round % 100 == 0) {
        System.out.println(
            "SPAWN_SKIP:"
                + round
                + ":cheese="
                + cheese
                + ":cost="
                + cost
                + ":reserve="
                + cheeseReserve
                + ":lastSpawn="
                + lastSpawnRound);
      }
      return;
    }

    // Try 8 directions at distance 3-4 (FARTHER from king to avoid clustering)
    // CRITICAL: Distance=2 creates traffic jam, rats block each other!
    for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
      // Try distance 3 first, then 4
      for (int dist = 3; dist <= 4; dist++) {
        MapLocation spawnLoc = rc.getLocation();
        for (int i = 0; i < dist; i++) {
          spawnLoc = spawnLoc.add(dir);
        }

        if (rc.canBuildRat(spawnLoc)) {
          rc.buildRat(spawnLoc);
          spawnCount++;
          lastSpawnRound = round;
          System.out.println(
              "SPAWN:"
                  + round
                  + ":rat #"
                  + spawnCount
                  + " at "
                  + spawnLoc
                  + " dist="
                  + dist
                  + " cheese="
                  + cheese);
          return;
        }
      }
    }

    // Failed to spawn - log why
    if (round % 100 == 0) {
      System.out.println("SPAWN_BLOCKED:" + round + ":no valid spawn location");
    }
  }

  /** Track cat positions in shared array. Kings have 360° vision, always see cats. */
  private static void trackCats(RobotController rc) throws GameActionException {
    RobotInfo[] neutral = rc.senseNearbyRobots(-1, Team.NEUTRAL);

    int catIndex = 0;
    MapLocation closestCat = null;
    int closestDist = Integer.MAX_VALUE;

    for (RobotInfo robot : neutral) {
      if (robot.getType() == UnitType.CAT && catIndex < 4) {
        MapLocation catLoc = robot.getLocation();

        // Write to cat tracking slots
        int slotX = Communications.SLOT_CAT1_X + (catIndex * 2);
        int slotY = Communications.SLOT_CAT1_Y + (catIndex * 2);
        rc.writeSharedArray(slotX, catLoc.x);
        rc.writeSharedArray(slotY, catLoc.y);

        if (rc.getRoundNum() % 100 == 0) {
          System.out.println("CAT_TRACKED:" + rc.getRoundNum() + ":#" + catIndex + " at " + catLoc);
        }

        // Track closest for primary target
        int dist = rc.getLocation().distanceSquaredTo(catLoc);
        if (dist < closestDist) {
          closestDist = dist;
          closestCat = catLoc;
        }

        catIndex++;
      }
    }

    // Set primary target (all combat rats attack this)
    if (closestCat != null) {
      rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_X, closestCat.x);
      rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_Y, closestCat.y);
    }

    // Clear unused slots
    for (int i = catIndex; i < 4; i++) {
      int slotX = Communications.SLOT_CAT1_X + (i * 2);
      rc.writeSharedArray(slotX, 0);
    }
  }

  /**
   * Place cat traps ADJACENT to king (BUILD_DISTANCE_SQUARED = 2). King is 3×3, so traps go just
   * outside the footprint. Rats spawn at distance 3-4, so they won't be trapped!
   */
  private static void placeDefensiveTraps(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();
    MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

    // Direction from king TOWARD center (where cats come from)
    Direction towardCenter = kingLoc.directionTo(center);

    boolean isCooperation = rc.isCooperation();
    if (rc.getRoundNum() % 20 == 0) {
      System.out.println(
          "TRAP_ATTEMPT:"
              + rc.getRoundNum()
              + ":cooperation="
              + isCooperation
              + " kingLoc="
              + kingLoc
              + " dir="
              + towardCenter);
    }

    // Place traps ADJACENT to king (distance squared ≤ 2)
    // King is 3×3, so this means just outside the king's footprint
    for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
      if (trapCount >= 10) break;

      // Try distance 2 (adjacent, BUILD_DISTANCE_SQUARED = 2)
      MapLocation trapLoc = kingLoc.add(dir).add(dir);

      if (rc.canPlaceCatTrap(trapLoc)) {
        rc.placeCatTrap(trapLoc);
        trapCount++;
        System.out.println("TRAP:" + rc.getRoundNum() + ":" + trapLoc + " (" + trapCount + "/10)");
      }
    }

    // Stop trying after round 60 or if all placed
    if (rc.getRoundNum() > 60) {
      trapsPlaced = true;
      if (trapCount > 0) {
        System.out.println(
            "DEFENSE:" + rc.getRoundNum() + ":Finished, placed " + trapCount + "/10 traps");
      }
    } else if (trapCount >= 10) {
      trapsPlaced = true;
      System.out.println("DEFENSE:" + rc.getRoundNum() + ":SUCCESS! Placed all 10 traps");
    }
  }

  /**
   * King collects cheese from nearby tiles. Strategic: Only when cheese visible and king action
   * ready.
   */
  private static void collectCheese(RobotController rc) throws GameActionException {
    // Debug cheese collection attempts
    if (rc.getRoundNum() % 100 == 0) {
      System.out.println(
          "KING_COLLECT_CHECK:" + rc.getRoundNum() + ":actionReady=" + rc.isActionReady());
    }

    if (!rc.isActionReady()) {
      return; // Need action cooldown for pickup
    }

    // Look for cheese within king's 360° vision (5 tile radius)
    MapLocation me = rc.getLocation();
    MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 25);

    int cheeseFound = 0;
    MapLocation nearestCheese = null;
    int nearestDist = Integer.MAX_VALUE;

    for (MapLocation loc : nearby) {
      if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        if (info.getCheeseAmount() > 0) {
          cheeseFound++;
          if (rc.canPickUpCheese(loc)) {
            int dist = me.distanceSquaredTo(loc);
            if (dist < nearestDist) {
              nearestDist = dist;
              nearestCheese = loc;
            }
          }
        }
      }
    }

    if (rc.getRoundNum() % 100 == 0 && cheeseFound > 0) {
      System.out.println(
          "KING_CHEESE_VISIBLE:"
              + rc.getRoundNum()
              + ":found="
              + cheeseFound
              + " canPickup="
              + (nearestCheese != null));
    }

    if (nearestCheese != null && rc.canPickUpCheese(nearestCheese)) {
      rc.pickUpCheese(nearestCheese);
      System.out.println(
          "KING_COLLECT:"
              + rc.getRoundNum()
              + ":"
              + nearestCheese
              + " cheese="
              + rc.getGlobalCheese());
    }
  }

  /**
   * Place rat traps ADJACENT to king for enemy rat defense. BUILD_DISTANCE_SQUARED = 2, so must be
   * adjacent.
   */
  private static void placeRatTraps(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();

    // Place rat traps ADJACENT to king (distance squared ≤ 2)
    for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
      if (ratTrapCount >= 5) break;

      MapLocation trapLoc = kingLoc.add(dir).add(dir);

      if (rc.canPlaceRatTrap(trapLoc)) {
        rc.placeRatTrap(trapLoc);
        ratTrapCount++;
        System.out.println(
            "RAT_TRAP:" + rc.getRoundNum() + ":" + trapLoc + " (" + ratTrapCount + "/5)");
      }
    }

    if (ratTrapCount >= 5 || rc.getRoundNum() > 40) {
      ratTrapsPlaced = true;
      System.out.println(
          "RAT_DEFENSE:" + rc.getRoundNum() + ":Placed " + ratTrapCount + " rat traps");
    }
  }
}
