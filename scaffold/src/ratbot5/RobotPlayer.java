package ratbot5;

import battlecode.common.*;

/**
 * ratbot5 - Built from scratch using Battlecode javadoc
 *
 * <p>Focus: Intelligent movement (no freeze in late rounds) Built with: Proper API usage, grouped
 * configuration, javadoc as reference
 */
public class RobotPlayer {
  // ================================================================
  // CONFIGURATION - Grouped tunable parameters
  // ================================================================

  // ========== COMBAT CONFIG ==========
  // TUNED: More aggressive combat
  private static final int ENHANCED_ATTACK_CHEESE = 16; // Was 8, now 16 for 14 damage
  private static final int ENHANCED_THRESHOLD = 300; // Was 500, now 300 for more enhanced attacks

  // ========== POPULATION CONFIG ==========
  // TUNED: More combat power
  private static final int INITIAL_SPAWN_COUNT = 15; // Was 12, now 15
  private static final int MAX_SPAWN_COUNT = 25; // Was 20, now 25
  private static final int COLLECTOR_MINIMUM = 5; // Was 4, now 5

  // ========== MOVEMENT CONFIG ==========
  private static final int POSITION_HISTORY_SIZE = 5; // RANGE: 3-7
  private static final int FORCED_MOVEMENT_THRESHOLD = 3; // RANGE: 2-5

  // ========== ECONOMY CONFIG ==========
  private static final int DELIVERY_THRESHOLD = 10; // RANGE: 5-15
  private static final int KING_CHEESE_RESERVE = 100; // RANGE: 50-200

  // ================================================================
  // MAIN LOOP
  // ================================================================

  public static void run(RobotController rc) throws GameActionException {
    while (true) {
      try {
        if (rc.getType().isRatKingType()) {
          runKing(rc);
        } else {
          runBabyRat(rc);
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ================================================================
  // KING BEHAVIOR
  // ================================================================

  private static int spawnCount = 0;
  private static int trapCount = 0;

  private static void runKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int cheese = rc.getGlobalCheese();
    MapLocation me = rc.getLocation();

    if (round % 50 == 0) {
      System.out.println("KING:" + round + ":cheese=" + cheese + " spawned=" + spawnCount);
    }

    // Write position to shared array
    rc.writeSharedArray(0, me.x);
    rc.writeSharedArray(1, me.y);

    // Calculate enemy king (round 1 only)
    if (round == 1) {
      rc.writeSharedArray(2, rc.getMapWidth() - 1 - me.x);
      rc.writeSharedArray(3, rc.getMapHeight() - 1 - me.y);
    }

    // Spawn initial rats
    if (spawnCount < INITIAL_SPAWN_COUNT) {
      int cost = rc.getCurrentRatCost();
      if (cheese > cost + KING_CHEESE_RESERVE) {
        spawnRat(rc);
        System.out.println("SPAWN:" + round + ":rat#" + spawnCount);
      }
    }
    // Instant replacement if low collectors
    else if (spawnCount < MAX_SPAWN_COUNT) {
      int collectors = countCollectors(rc);
      if (collectors < COLLECTOR_MINIMUM) {
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + KING_CHEESE_RESERVE) {
          spawnRat(rc);
          System.out.println("REPLACE:" + round + ":rat#" + spawnCount);
        }
      }
    }

    // Place defensive traps
    if (spawnCount >= 10 && trapCount < 5 && cheese > 300) {
      for (Direction dir : Direction.allDirections()) {
        MapLocation loc = me.add(dir).add(dir);
        if (rc.canPlaceCatTrap(loc)) {
          rc.placeCatTrap(loc);
          trapCount++;
          return;
        }
      }
    }

    // King movement - mobile king harder to kill
    RobotInfo[] threats = rc.senseNearbyRobots(25, Team.NEUTRAL);
    RobotInfo[] enemies = rc.senseNearbyRobots(25, rc.getTeam().opponent());

    // Only move if safe
    if (threats.length == 0 && enemies.length == 0) {
      MapLocation ahead = rc.adjacentLocation(rc.getDirection());

      // Clear obstacles
      if (rc.canRemoveDirt(ahead)) {
        rc.removeDirt(ahead);
      }

      // Move slowly
      if (round % 2 == 0 && rc.canMoveForward()) {
        rc.moveForward();
      } else if (rc.canTurn() && round % 10 == 0) {
        Direction newDir = Direction.allDirections()[(round / 10) % 8];
        rc.turn(newDir);
      }
    }
  }

  private static void spawnRat(RobotController rc) throws GameActionException {
    // Try distance 2, then 3, then 4 to find open spots
    for (Direction dir : Direction.allDirections()) {
      for (int dist = 2; dist <= 4; dist++) {
        MapLocation loc = rc.getLocation();
        for (int i = 0; i < dist; i++) {
          loc = loc.add(dir);
        }
        if (rc.canBuildRat(loc)) {
          rc.buildRat(loc);
          spawnCount++;
          return;
        }
      }
    }
  }

  private static int countCollectors(RobotController rc) throws GameActionException {
    int count = 0;
    RobotInfo[] team = rc.senseNearbyRobots(rc.getType().getVisionRadiusSquared(), rc.getTeam());
    for (RobotInfo r : team) {
      if (r.getType().isBabyRatType() && r.getID() % 2 == 1) {
        count++;
      }
    }
    return count;
  }

  // ================================================================
  // BABY RAT BEHAVIOR
  // ================================================================

  private static int myRole = -1;

  private static void runBabyRat(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();

    // Assign role once
    if (myRole == -1) {
      myRole = rc.getID() % 2; // 0=attacker, 1=collector
    }

    // Visual debugging in client
    rc.setIndicatorString((myRole == 0 ? "ATK" : "COL"));

    // Check for 2nd king formation signal
    if (round > 50 && myRole == 1 && rc.canBecomeRatKing()) {
      rc.becomeRatKing(); // Form 2nd king!
      return;
    }

    if (myRole == 0) {
      runAttacker(rc);
    } else {
      runCollector(rc);
    }
  }

  // ================================================================
  // ATTACKER - Phase 2: Combat from javadoc
  // ================================================================

  private static void runAttacker(RobotController rc) throws GameActionException {
    if (!rc.isActionReady()) return;

    // Bytecode check
    if (Clock.getBytecodesLeft() < rc.getType().getBytecodeLimit() * 0.1) {
      MapLocation enemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
      moveTo(rc, enemyKing);
      return;
    }

    MapLocation me = rc.getLocation();

    // Ratnapping - throw if carrying
    RobotInfo carrying = rc.getCarrying();
    if (carrying != null && rc.canThrowRat()) {
      MapLocation ourKing = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
      Direction toKing = me.directionTo(ourKing);
      if (rc.getDirection() != toKing && rc.canTurn()) {
        rc.turn(toKing);
      } else {
        rc.throwRat();
      }
      return;
    }

    int visionRange = rc.getType().getVisionRadiusSquared();
    RobotInfo[] enemies = rc.senseNearbyRobots(visionRange, rc.getTeam().opponent());

    // Find targets: wounded to ratnap, best to attack
    RobotInfo bestTarget = null;
    RobotInfo ratnap = null;
    int maxCheese = 0;

    for (RobotInfo enemy : enemies) {
      if (enemy.getType().isBabyRatType()) {
        if (enemy.getHealth() < 50 && ratnap == null) {
          ratnap = enemy;
        }

        int cheese = enemy.getRawCheeseAmount();
        if (cheese > maxCheese || bestTarget == null) {
          maxCheese = cheese;
          bestTarget = enemy;
        }
      }
    }

    // Ratnap wounded
    if (ratnap != null && carrying == null && rc.canCarryRat(ratnap.getLocation())) {
      rc.carryRat(ratnap.getLocation());
      return;
    }

    // Attack with game mode adaptation
    boolean coop = rc.isCooperation();

    if (bestTarget != null && rc.canAttack(bestTarget.getLocation())) {
      if (!coop && rc.getGlobalCheese() > ENHANCED_THRESHOLD) {
        rc.attack(bestTarget.getLocation(), ENHANCED_ATTACK_CHEESE);
      } else {
        rc.attack(bestTarget.getLocation());
      }
      return;
    }

    // Attack king with proper distance AND squeak location
    for (RobotInfo enemy : enemies) {
      if (enemy.getType().isRatKingType()) {
        MapLocation kingCenter = enemy.getLocation();
        int dist = (int) me.bottomLeftDistanceSquaredTo(kingCenter);

        // SQUEAK enemy king location for coordination
        try {
          int squeak = (1 << 28) | (kingCenter.y << 16) | (kingCenter.x << 4);
          rc.squeak(squeak);
        } catch (Exception e) {
          // Squeak failed
        }

        // Attack all 9 king tiles
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = new MapLocation(kingCenter.x + dx, kingCenter.y + dy);
            if (rc.canAttack(tile)) {
              rc.attack(tile);
              return;
            }
          }
        }
      }
    }

    // No enemies visible - check squeaks for enemy king (COORDINATION!)
    try {
      Message[] squeaks = rc.readSqueaks(-1);
      for (Message msg : squeaks) {
        if (msg.getSenderID() != rc.getID()) {
          int bytes = msg.getBytes();
          int type = (bytes >> 28) & 0xF;
          if (type == 1) { // Enemy king squeak
            int x = (bytes >> 4) & 0xFFF;
            int y = (bytes >> 16) & 0xFFF;
            MapLocation squeakedKing = new MapLocation(x, y);
            moveTo(rc, squeakedKing);
            return;
          }
        }
      }
    } catch (Exception e) {
      // Squeak failed
    }

    // Rush enemy king from shared array
    MapLocation enemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
    moveTo(rc, enemyKing);
  }

  // ================================================================
  // COLLECTOR - Phase 4: Economy from javadoc
  // ================================================================

  private static void runCollector(RobotController rc) throws GameActionException {
    int cheese = rc.getRawCheese();

    // Check bytecode budget
    if (Clock.getBytecodesLeft() < rc.getType().getBytecodeLimit() * 0.1) {
      // Emergency mode - just deliver what we have
      if (cheese > 0) {
        MapLocation king = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
        moveTo(rc, king);
      }
      return;
    }

    if (cheese >= DELIVERY_THRESHOLD) {
      deliver(rc);
    } else {
      collect(rc);
    }
  }

  private static void collect(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    int visionRange = rc.getType().getVisionRadiusSquared();

    // Scan for cheese AND cheese mines
    MapInfo[] nearbyInfo = rc.senseNearbyMapInfos(me, visionRange);
    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;
    int cheeseFound = 0;

    for (MapInfo info : nearbyInfo) {
      // SQUEAK cheese mine locations (from javadoc/lectureplayer)
      if (info.hasCheeseMine()) {
        try {
          MapLocation mineLoc = info.getMapLocation();
          int squeak = (3 << 28) | (mineLoc.y << 16) | (mineLoc.x << 4);
          rc.squeak(squeak);
          System.out.println(
              "MINE_SQUEAK:" + rc.getRoundNum() + ":" + rc.getID() + ":loc=" + mineLoc);
        } catch (Exception e) {
          // Squeak failed
        }
      }

      if (info.getCheeseAmount() > 0) {
        cheeseFound++;
        int dist = me.distanceSquaredTo(info.getMapLocation());
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = info.getMapLocation();
        }
      }
    }

    // READ squeaks with full metadata
    try {
      Message[] squeaks = rc.readSqueaks(-1);
      Message freshestMine = null;
      int newestRound = 0;

      for (Message msg : squeaks) {
        // Skip own squeaks
        if (msg.getSenderID() == rc.getID()) continue;

        // Find freshest mine squeak
        if (msg.getRound() > newestRound) {
          int bytes = msg.getBytes();
          int type = (bytes >> 28) & 0xF;
          if (type == 3) {
            freshestMine = msg;
            newestRound = msg.getRound();
          }
        }
      }

      // Use freshest mine location
      if (freshestMine != null) {
        // Go to source (where ally found mine)
        MapLocation mineSource = freshestMine.getSource();
        int dist = me.distanceSquaredTo(mineSource);
        if (nearest == null || dist < nearestDist) {
          nearest = mineSource;
          nearestDist = dist;
        }
      }
    } catch (Exception e) {
      // Squeak reading failed
    }

    if (rc.getRoundNum() % 50 == 0) {
      System.out.println("SCAN:" + rc.getRoundNum() + ":" + rc.getID() + ":found=" + cheeseFound);
    }

    // Collect cheese
    if (nearest != null) {
      if (rc.canPickUpCheese(nearest)) {
        rc.pickUpCheese(nearest);
        System.out.println(
            "PICKUP:" + rc.getRoundNum() + ":" + rc.getID() + ":now=" + rc.getRawCheese());
      } else {
        if (rc.getRoundNum() % 50 == 0) {
          System.out.println(
              "MOVE_TO_CHEESE:" + rc.getRoundNum() + ":" + rc.getID() + ":dist=" + nearestDist);
        }
        moveTo(rc, nearest);
      }
    } else {
      if (rc.getRoundNum() % 50 == 0) {
        System.out.println(
            "NO_CHEESE_EXPLORE:"
                + rc.getRoundNum()
                + ":"
                + rc.getID()
                + ":scanned="
                + nearbyInfo.length);
      }
      // Explore toward center
      MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
      moveTo(rc, center);
    }
  }

  private static void deliver(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation king = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));

    int dist = me.distanceSquaredTo(king);

    // Transfer if in range
    if (dist <= 9 && rc.canTransferCheese(king, rc.getRawCheese())) {
      rc.transferCheese(king, rc.getRawCheese());
      System.out.println(
          "TRANSFER:" + rc.getRoundNum() + ":" + rc.getID() + ":amt=" + rc.getRawCheese());
      return;
    }

    // Move toward king
    if (rc.getRoundNum() % 50 == 0) {
      System.out.println("DELIVERING:" + rc.getRoundNum() + ":" + rc.getID() + ":dist=" + dist);
    }
    moveTo(rc, king);
  }

  // ================================================================
  // MOVEMENT - Phase 1: Intelligent movement from javadoc
  // ================================================================

  private static MapLocation[] positionHistory = new MapLocation[POSITION_HISTORY_SIZE];
  private static int historyIndex = 0;
  private static int stuckRounds = 0;

  private static void moveTo(RobotController rc, MapLocation target) throws GameActionException {
    if (!rc.isMovementReady()) return;

    MapLocation me = rc.getLocation();

    // PHASE 2: Clear obstacles ahead
    MapLocation ahead = rc.adjacentLocation(rc.getDirection());
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

    // Update position history
    positionHistory[historyIndex] = me;
    historyIndex = (historyIndex + 1) % POSITION_HISTORY_SIZE;

    // Check if stuck (all positions same)
    boolean stuck = true;
    for (MapLocation pos : positionHistory) {
      if (pos != null && !pos.equals(me)) {
        stuck = false;
        break;
      }
    }

    if (stuck) {
      stuckRounds++;
    } else {
      stuckRounds = 0;
    }

    // FORCE movement if stuck too long
    if (stuckRounds >= FORCED_MOVEMENT_THRESHOLD) {
      int dx = target.x - me.x;
      int dy = target.y - me.y;
      int clampedDx = Math.max(-1, Math.min(1, dx));
      int clampedDy = Math.max(-1, Math.min(1, dy));

      Direction exact = Direction.fromDelta(clampedDx, clampedDy);
      if (exact != Direction.CENTER && rc.canTurn()) {
        rc.turn(exact);
        stuckRounds = 0;
        return;
      }

      // Try any direction
      for (Direction dir : Direction.allDirections()) {
        if (rc.canTurn()) {
          rc.turn(dir);
          stuckRounds = 0;
          return;
        }
      }
    }

    // Normal movement with javadoc methods
    Direction desired = me.directionTo(target);

    if (desired == Direction.CENTER) {
      return;
    }

    Direction facing = rc.getDirection();

    // Check ahead before moving
    if (facing == desired) {
      MapLocation nextLoc = rc.adjacentLocation(facing);

      // Validate position is on map
      if (!rc.onTheMap(nextLoc)) {
        // Off map - try different direction
        for (Direction alt : Direction.allDirections()) {
          if (rc.canTurn()) {
            rc.turn(alt);
            return;
          }
        }
      }

      // Check passability
      if (rc.canSenseLocation(nextLoc)) {
        // Check for obstacles
        if (!rc.sensePassability(nextLoc)) {
          // Wall or dirt - try perpendicular
          for (Direction alt : new Direction[] {desired.rotateLeft(), desired.rotateRight()}) {
            if (rc.canTurn()) {
              rc.turn(alt);
              return;
            }
          }
        }

        // Check for rats blocking
        if (rc.isLocationOccupied(nextLoc)) {
          RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);
          if (blocker != null && blocker.getTeam() == rc.getTeam()) {
            // Friendly rat - try going around
            for (Direction alt : new Direction[] {desired.rotateLeft(), desired.rotateRight()}) {
              if (rc.canTurn()) {
                rc.turn(alt);
                return;
              }
            }
          }
        }

        // Check for traps
        MapInfo nextInfo = rc.senseMapInfo(nextLoc);
        if (nextInfo.getTrap() == TrapType.RAT_TRAP) {
          // Rat trap - avoid!
          for (Direction alt : new Direction[] {desired.rotateLeft(), desired.rotateRight()}) {
            if (rc.canTurn()) {
              rc.turn(alt);
              return;
            }
          }
        }
      }

      // Path is clear - move forward
      if (rc.canMoveForward()) {
        rc.moveForward();
        return;
      }
    }

    // Not facing target - turn toward it
    if (facing != desired && rc.canTurn()) {
      rc.turn(desired);
    }
  }
}
