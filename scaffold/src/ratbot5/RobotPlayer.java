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
  private static final int ENHANCED_ATTACK_CHEESE = 8; // RANGE: 4-32
  private static final int ENHANCED_THRESHOLD = 500; // RANGE: 300-1000

  // ========== POPULATION CONFIG ==========
  private static final int INITIAL_SPAWN_COUNT = 12; // RANGE: 8-15
  private static final int MAX_SPAWN_COUNT = 20; // RANGE: 15-25
  private static final int COLLECTOR_MINIMUM = 4; // RANGE: 3-6

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

  private static void runKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int cheese = rc.getGlobalCheese();
    MapLocation me = rc.getLocation();

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
      }
    }
    // Instant replacement if low collectors
    else if (spawnCount < MAX_SPAWN_COUNT) {
      int collectors = countCollectors(rc);
      if (collectors < COLLECTOR_MINIMUM) {
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + KING_CHEESE_RESERVE) {
          spawnRat(rc);
        }
      }
    }
  }

  private static void spawnRat(RobotController rc) throws GameActionException {
    for (Direction dir : Direction.allDirections()) {
      MapLocation loc = rc.getLocation().add(dir).add(dir);
      if (rc.canBuildRat(loc)) {
        rc.buildRat(loc);
        spawnCount++;
        return;
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

    // DEBUG: Show what we're doing
    if (round % 50 == 0) {
      System.out.println(
          "RAT:"
              + round
              + ":"
              + id
              + ":role="
              + (myRole == 0 ? "ATK" : "COL")
              + " moveCd="
              + rc.getMovementCooldownTurns()
              + " actionCd="
              + rc.getActionCooldownTurns());
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

    // Attack king with proper distance
    for (RobotInfo enemy : enemies) {
      if (enemy.getType().isRatKingType()) {
        MapLocation kingCenter = enemy.getLocation();
        int dist = (int) me.bottomLeftDistanceSquaredTo(kingCenter);

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

    // No enemies - rush enemy king
    if (rc.getRoundNum() % 50 == 0) {
      System.out.println("RUSH_KING:" + rc.getRoundNum() + ":" + rc.getID());
    }
    MapLocation enemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
    moveTo(rc, enemyKing);
  }

  // ================================================================
  // COLLECTOR - Phase 4: Economy from javadoc
  // ================================================================

  private static void runCollector(RobotController rc) throws GameActionException {
    int cheese = rc.getRawCheese();

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

    // READ squeaks to learn about cheese mines from other collectors
    try {
      Message[] squeaks = rc.readSqueaks(-1);
      for (Message msg : squeaks) {
        int bytes = msg.getBytes();
        int type = (bytes >> 28) & 0xF;
        if (type == 3) { // Cheese mine squeak
          int x = (bytes >> 4) & 0xFFF;
          int y = (bytes >> 16) & 0xFFF;
          MapLocation mineLoc = new MapLocation(x, y);
          int dist = me.distanceSquaredTo(mineLoc);
          // If closer than current target, go to this mine
          if (nearest == null || dist < nearestDist) {
            nearest = mineLoc;
            nearestDist = dist;
          }
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

    // Normal movement: turn + forward (10 cd, no strafe)
    Direction desired = me.directionTo(target);

    if (desired == Direction.CENTER) {
      return;
    }

    Direction facing = rc.getDirection();

    // PHASE 2: Check for traps before moving
    if (facing == desired && Clock.getBytecodesLeft() > 500) {
      MapLocation nextLoc = rc.adjacentLocation(facing);
      if (rc.canSenseLocation(nextLoc)) {
        MapInfo nextInfo = rc.senseMapInfo(nextLoc);
        if (nextInfo.getTrap() == TrapType.RAT_TRAP) {
          // Avoid rat trap (50 damage!)
          for (Direction alt : new Direction[]{desired.rotateLeft(), desired.rotateRight()}) {
            if (rc.canTurn()) {
              rc.turn(alt);
              return;
            }
          }
        }
      }
    }

    if (facing != desired && rc.canTurn()) {
      rc.turn(desired);
    } else if (facing == desired && rc.canMoveForward()) {
      rc.moveForward();
    }
  }
}
