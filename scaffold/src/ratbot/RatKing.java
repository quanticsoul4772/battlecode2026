package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;
import ratbot.logging.*;

/**
 * Rat King behavior for Battlecode 2026.
 *
 * <p>Primary responsibilities: 1. Spawn baby rats 2. Track global cheese 3. Prevent starvation
 * (consumes 3 cheese/round)
 */
public class RatKing {

  private static int lastGlobalCheese = 2500;
  private static int lastSpawnRound = 0; // Track when we last spawned
  private static boolean defensiveTrapsPlaced = false; // Track if we placed cat traps

  public static void run(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    int globalCheese = rc.getGlobalCheese();

    // Calculate survival metrics
    int kingCount = Math.max(1, RobotUtil.countAllyKings(rc));
    int roundsLeft = globalCheese / (kingCount * 3);

    // CRITICAL: Emergency circuit breaker
    if (roundsLeft < BehaviorConfig.CRITICAL_CHEESE_ROUNDS) {
      // Broadcast CRITICAL emergency to all units
      rc.writeSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS, BehaviorConfig.EMERGENCY_CRITICAL);
      System.out.println(
          "EMERGENCY:"
              + round
              + ":CRITICAL_STARVATION:rounds="
              + roundsLeft
              + ":cheese="
              + globalCheese);

      // Visual emergency indicator
      if (DebugConfig.DEBUG_EMERGENCY) {
        Debug.debugEmergency(rc, "CRITICAL_STARVATION", roundsLeft);
      }

      // STOP ALL SPAWNING - survival mode
      return;
    }

    // WARNING: Low cheese - reduce spawning
    if (roundsLeft < BehaviorConfig.WARNING_CHEESE_ROUNDS) {
      rc.writeSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS, roundsLeft);
      System.out.println(
          "WARNING:" + round + ":LOW_CHEESE:rounds=" + roundsLeft + ":cheese=" + globalCheese);
      // Only spawn if significant surplus
      if (globalCheese > BehaviorConfig.MIN_CHEESE_FOR_SPAWNING) {
        trySpawn(rc);
      }
      return;
    }

    // Normal operations - broadcast status
    rc.writeSharedArray(
        BehaviorConfig.SLOT_CHEESE_STATUS, Math.min(roundsLeft, 1023)); // 10-bit limit

    // Broadcast king position for baby rats to find (use slots 1-2)
    MapLocation myLoc = rc.getLocation();
    rc.writeSharedArray(1, myLoc.x); // King X coordinate
    rc.writeSharedArray(2, myLoc.y); // King Y coordinate

    // Economy logging every 10 rounds
    if (round % 10 == 0) {
      int income = globalCheese - lastGlobalCheese + 30; // +30 = 10 rounds × 3 consumption
      System.out.println(
          "ECONOMY:" + round + ":cheese=" + globalCheese + ":income=" + (income / 10));
      lastGlobalCheese = globalCheese;
    }

    // Try to spawn baby rats (PRIORITY 1 - don't waste turns repositioning first!)
    trySpawn(rc);

    // Track cats for baby rats (kings have 360° vision)
    trackCats(rc);

    // Place defensive cat traps (one-time, round 15-20)
    if (!defensiveTrapsPlaced && round >= 15 && globalCheese >= 200) {
      placeCatTraps(rc);
    }

    // Reposition if stuck (ONLY after spawn attempt)
    repositionIfNeeded(rc);
  }

  /**
   * Place cat traps in defensive perimeter around king. Creates barrier to protect king from cat
   * attacks.
   */
  private static void placeCatTraps(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();
    int placed = 0;

    // Place traps in ring around king (distance 3-5)
    for (int d = 0; d < DirectionUtil.ALL_DIRECTIONS.length && placed < 10; d++) {
      Direction dir = DirectionUtil.ALL_DIRECTIONS[d];

      // Try distance 3, 4, 5 from king
      for (int dist = 3; dist <= 5 && placed < 10; dist++) {
        MapLocation trapLoc = kingLoc;
        for (int i = 0; i < dist; i++) {
          trapLoc = trapLoc.add(dir);
        }

        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          placed++;
          System.out.println("TRAP:" + rc.getRoundNum() + ":Placed cat trap at " + trapLoc);
          break;
        }
      }
    }

    defensiveTrapsPlaced = true;
    System.out.println("DEFENSE:" + rc.getRoundNum() + ":Placed " + placed + " cat traps total");
  }

  /**
   * Track cat positions in shared array for baby rats. Kings have 360° vision and can always see
   * cats within range.
   */
  private static void trackCats(RobotController rc) throws GameActionException {
    // Sense all nearby units
    RobotInfo[] nearby = rc.senseNearbyRobots(-1, Team.NEUTRAL);

    int catIndex = 0;
    for (int i = nearby.length; --i >= 0; ) {
      if (nearby[i].getType() == UnitType.CAT && catIndex < 4) {
        MapLocation catLoc = nearby[i].getLocation();

        // Write to shared array (slots 3-10, 2 slots per cat)
        int slotX = 3 + (catIndex * 2);
        int slotY = 4 + (catIndex * 2);

        rc.writeSharedArray(slotX, catLoc.x);
        rc.writeSharedArray(slotY, catLoc.y);

        if (DebugConfig.DEBUG_COMBAT && rc.getRoundNum() % 50 == 0) {
          Debug.info(rc, "Tracking cat #" + catIndex + " at " + catLoc);
        }

        catIndex++;
      }
    }

    // Clear unused cat slots (mark as no cat)
    for (int i = catIndex; i < 4; i++) {
      int slotX = 3 + (i * 2);
      rc.writeSharedArray(slotX, 0); // 0 = no cat
    }
  }

  /**
   * Attempt to spawn a baby rat. AGGRESSIVE: Spawn every turn to build army for cat combat (50% of
   * cooperation score).
   */
  private static void trySpawn(RobotController rc) throws GameActionException {
    int globalCheese = rc.getGlobalCheese();

    // Only gate: True emergency (prevent instant death)
    if (globalCheese < 100) {
      return; // Critical survival mode only
    }

    // Try spawn locations at distance=2 (outside 3x3 king footprint)
    // King is 3x3, so adjacent tiles (distance=1) are part of king itself!
    Direction[] directions = DirectionUtil.ALL_DIRECTIONS;

    int attemptCount = 0;
    for (int i = directions.length; --i >= 0; ) {
      // Spawn at distance=2 to avoid king's 3x3 footprint
      MapLocation spawnLoc = rc.getLocation().add(directions[i]).add(directions[i]);
      attemptCount++;

      boolean canSpawn = rc.canBuildRat(spawnLoc);

      // Visual debug each spawn attempt
      if (DebugConfig.DEBUG_SPAWNING) {
        Debug.debugSpawnAttempt(rc, spawnLoc, canSpawn);
      }

      if (canSpawn) {
        int babyRats = RobotUtil.countAllyBabyRats(rc);
        int spawnCost = Constants.getSpawnCost(babyRats);

        rc.buildRat(spawnLoc);

        // Log spawn for analysis
        Logger.logSpawn(
            rc.getRoundNum(),
            "RAT_KING",
            rc.getID(),
            spawnLoc.x,
            spawnLoc.y,
            spawnCost,
            babyRats + 1 // After spawning
            );

        // Visual success indicator
        if (DebugConfig.DEBUG_SPAWNING) {
          Debug.timeline(rc, "SPAWN:" + (babyRats + 1), Debug.Color.GREEN);
        }

        // Update last spawn round for rate limiting
        lastSpawnRound = rc.getRoundNum();

        return; // Only spawn once per turn
      }
    }

    // Debug: No valid spawn location found
    if (DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 10 == 0) {
      Debug.warning(rc, "Spawn blocked - checked " + attemptCount + " locations");
      Debug.status(rc, "SPAWN_BLOCKED x" + attemptCount);
    }
  }

  /**
   * Count open spawn locations around king.
   *
   * @return Number of adjacent tiles where baby rats can spawn (0-8)
   */
  private static int countOpenSpawnLocations(RobotController rc) throws GameActionException {
    Direction[] directions = DirectionUtil.ALL_DIRECTIONS;
    int openCount = 0;

    // Detailed debugging every 20 rounds
    boolean detailedDebug = DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 20 == 0;

    for (int i = directions.length; --i >= 0; ) {
      // Spawn at distance=2 to avoid king's 3x3 footprint
      MapLocation spawnLoc = rc.getLocation().add(directions[i]).add(directions[i]);
      boolean canSpawn = rc.canBuildRat(spawnLoc);

      if (canSpawn) {
        openCount++;
      }

      // Debug WHY each location fails
      if (detailedDebug && !canSpawn) {
        boolean onMap = rc.onTheMap(spawnLoc);
        boolean passable = onMap ? rc.sensePassability(spawnLoc) : false;
        boolean occupied = onMap ? rc.isLocationOccupied(spawnLoc) : false;
        int cheese = -1;
        if (onMap && rc.canSenseLocation(spawnLoc)) {
          MapInfo info = rc.senseMapInfo(spawnLoc);
          cheese = info.getCheeseAmount();
        }

        // Force print for debugging (bypass Debug.verbose)
        System.out.println(
            "DEBUG:"
                + rc.getRoundNum()
                + ":"
                + rc.getID()
                + ":BLOCKED:"
                + directions[i]
                + "@"
                + spawnLoc
                + ":onMap="
                + onMap
                + ":pass="
                + passable
                + ":occ="
                + occupied
                + ":cheese="
                + cheese);
      }
    }

    // Summary
    if (detailedDebug) {
      Debug.info(rc, "Spawn capacity: " + openCount + "/8 at " + rc.getLocation());
    }

    return openCount;
  }

  /**
   * Check if current position is good for spawning.
   *
   * @return true if at least 4 spawn locations are open (50% capacity)
   */
  private static boolean isPositionGoodForSpawning(RobotController rc) throws GameActionException {
    int openLocations = countOpenSpawnLocations(rc);
    return openLocations >= 4; // Need at least 50% spawn capacity
  }

  /**
   * Find better position for king to move to. Evaluates all 8 adjacent directions and picks best
   * one.
   *
   * @return Direction to move, or null if no better position found
   */
  private static Direction findBetterPosition(RobotController rc) throws GameActionException {
    MapLocation current = rc.getLocation();
    int currentScore = scoreKingPosition(rc, current);

    Direction bestDir = null;
    int bestScore = currentScore;

    for (int i = DirectionUtil.ALL_DIRECTIONS.length; --i >= 0; ) {
      Direction dir = DirectionUtil.ALL_DIRECTIONS[i];
      MapLocation candidate = current.add(dir);

      // Can king move there?
      if (!rc.canMove(dir)) continue;

      // Would it be better?
      int score = scoreKingPosition(rc, candidate);

      if (score > bestScore) {
        bestScore = score;
        bestDir = dir;

        // Visual debug - show best candidate
        if (DebugConfig.DEBUG_SPAWNING) {
          Debug.dot(rc, candidate, Debug.Color.GREEN);
          Debug.verbose(
              rc,
              "Better position: "
                  + candidate
                  + " score="
                  + score
                  + " (current="
                  + currentScore
                  + ")");
        }
      }
    }

    return bestDir; // null if no improvement found
  }

  /**
   * Score a king position based on spawn capacity and strategic factors. Higher score = better
   * position.
   */
  private static int scoreKingPosition(RobotController rc, MapLocation pos)
      throws GameActionException {
    int score = 0;

    // Factor 1: ACTUAL spawn capacity (MOST IMPORTANT)
    // Use canBuildRat() to check if can truly spawn (includes cheese check)
    // Check at distance=2 because king is 3x3 (distance=1 is king's own footprint)
    for (int i = DirectionUtil.ALL_DIRECTIONS.length; --i >= 0; ) {
      Direction dir = DirectionUtil.ALL_DIRECTIONS[i];
      MapLocation spawnLoc = pos.add(dir).add(dir); // Distance=2

      // Check if can ACTUALLY spawn here (not just on map)
      if (rc.canBuildRat(spawnLoc)) {
        score += 20; // Doubled weight - spawnable tiles are critical
      }
    }

    // Factor 2: Distance from edges (reduced weight)
    // Center often has cheese clusters - reduce priority
    int edgeDist =
        Math.min(
            Math.min(pos.x, rc.getMapWidth() - pos.x), Math.min(pos.y, rc.getMapHeight() - pos.y));
    score += edgeDist / 2; // Half weight - don't over-prioritize center

    // Factor 3: Safe spacing from other kings
    if (KingManagement.isSafeKingLocation(rc, pos)) {
      score += 50; // Significant bonus for safe positioning
    }

    // Factor 4: Avoid nearby cats
    if (RobotUtil.detectCat(rc, 100)) { // Within 10 tiles
      RobotInfo nearestCat = RobotUtil.findNearestCat(rc);
      if (nearestCat != null) {
        int dist = pos.distanceSquaredTo(nearestCat.getLocation());
        if (dist < 100) { // Within 10 tiles
          score -= 30; // Penalty for cat proximity
        }
      }
    }

    return score;
  }

  private static boolean hasReachedGoodPosition = false;

  /**
   * Reposition king if COMPLETELY stuck (all 8 spawn locations blocked). Only runs after spawn
   * attempt - don't waste early game on repositioning.
   */
  private static void repositionIfNeeded(RobotController rc) throws GameActionException {
    // Check if ALL spawn locations blocked (rare)
    int openLocations = countOpenSpawnLocations(rc);

    if (openLocations > 0) {
      return; // Can spawn, don't waste time repositioning
    }

    // All blocked - this is rare, try to move
    if (DebugConfig.DEBUG_SPAWNING) {
      Debug.warning(rc, "FULLY BLOCKED - all 8 spawn locations unavailable");
    }

    // Position is bad - find better location
    Direction moveDir = findBetterPosition(rc);

    if (moveDir == null) {
      // No better position found
      if (DebugConfig.DEBUG_SPAWNING) {
        Debug.warning(rc, "Stuck - no better position available");
        Debug.status(rc, "STUCK");
      }
      return;
    }

    // Execute movement (turn + move pattern)
    Direction currentFacing = rc.getDirection();

    // Turn toward target
    if (rc.canTurn() && currentFacing != moveDir) {
      rc.turn(moveDir);

      if (DebugConfig.DEBUG_SPAWNING) {
        Debug.info(rc, "Repositioning: turning to " + moveDir);
      }
      return; // Turn this round, move next round
    }

    // Move forward (40 cd for kings)
    if (rc.canMoveForward()) {
      MapLocation oldPos = rc.getLocation();
      rc.moveForward();
      MapLocation newPos = rc.getLocation();

      if (DebugConfig.DEBUG_SPAWNING) {
        Debug.info(rc, "Repositioned: " + oldPos + " -> " + newPos);
        Debug.timeline(rc, "KING_MOVE", Debug.Color.BLUE);
        Debug.line(rc, oldPos, newPos, Debug.Color.BLUE);
      }
    } else {
      // Can't move forward - debug why
      if (DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 10 == 0) {
        Debug.verbose(rc, "Can't move: cooldown or blocked");
      }
    }
  }
}
