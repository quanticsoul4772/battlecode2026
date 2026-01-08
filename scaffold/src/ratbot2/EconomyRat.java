package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Economy-specialized baby rat (30% of army).
 *
 * <p>SOLE OBJECTIVE: Collect cheese and deliver to king. Sustain king's 3 cheese/round consumption.
 */
public class EconomyRat {
  private static final int DELIVERY_THRESHOLD =
      10; // Deliver when carrying this much (lower = more frequent)

  // Passability map (each rat builds its own)
  private static boolean[][] localPassable = new boolean[60][60];
  private static boolean mapInitialized = false;

  // Zone assignment (4 quadrants to prevent clustering)
  private static int myZone = -1;
  private static MapLocation zoneCenter = null;

  public static void run(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    MapLocation me = rc.getLocation();

    // Build passability map and assign zone on first run
    if (!mapInitialized) {
      scanPassability(rc);
      mapInitialized = true;

      // Assign zone based on ID (4 quadrants)
      myZone = id % 4;
      int mapW = rc.getMapWidth();
      int mapH = rc.getMapHeight();

      // Calculate zone center
      int zx = (myZone % 2 == 0) ? mapW / 4 : 3 * mapW / 4;
      int zy = (myZone < 2) ? mapH / 4 : 3 * mapH / 4;
      zoneCenter = new MapLocation(zx, zy);

      String zoneName = (myZone == 0) ? "NW" : (myZone == 1) ? "NE" : (myZone == 2) ? "SE" : "SW";
      System.out.println(
          "ECON_INIT:" + round + ":" + id + ":zone=" + zoneName + " center=" + zoneCenter);
    }

    // Periodic status report
    if (round % 50 == 0) {
      int rawCheese = rc.getRawCheese();
      int hp = rc.getHealth();
      System.out.println(
          "ECON_STATUS:" + round + ":" + id + ":cheese=" + rawCheese + ":hp=" + hp + ":pos=" + me);
    }

    // Get king position
    int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
    int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);
    MapLocation kingLoc = new MapLocation(kingX, kingY);
    int distToKing = me.distanceSquaredTo(kingLoc);

    // PRIORITY 0: DISPERSE to assigned zone (first 30 rounds)
    // Each rat goes to their ZONE CENTER to spread out across map
    if (round <= 30) {
      Movement.moveToward(rc, zoneCenter);

      if (round % 10 == 0) {
        System.out.println(
            "ECON_DISPERSE:"
                + round
                + ":"
                + id
                + ":→zone="
                + myZone
                + " target="
                + zoneCenter
                + " dist="
                + me.distanceSquaredTo(zoneCenter));
      }
      return;
    }

    // PRIORITY 1: EVASIVE - Check if cat nearby, flee if too close
    RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
    for (RobotInfo robot : nearby) {
      if (robot.getType() == UnitType.CAT) {
        int distToCat = rc.getLocation().distanceSquaredTo(robot.getLocation());

        // If cat within 4 tiles (16 squared) - FLEE
        if (distToCat <= 16) {
          fleeCat(rc, robot.getLocation());
          return;
        }
      }
    }

    // PRIORITY 2: Collection/Delivery cycle
    int rawCheese = rc.getRawCheese();

    if (round % 100 == 0 && rawCheese > 0) {
      System.out.println(
          "ECON_DECISION:"
              + round
              + ":"
              + id
              + ":cheese="
              + rawCheese
              + " threshold="
              + DELIVERY_THRESHOLD
              + " decision="
              + (rawCheese >= DELIVERY_THRESHOLD ? "DELIVER" : "COLLECT"));
    }

    if (rawCheese >= DELIVERY_THRESHOLD) {
      // Deliver when carrying threshold
      // Zone-based approach vectors (in deliverCheese) prevent clustering
      deliverCheese(rc);
    } else {
      collectCheese(rc);
    }
  }

  /** Flee from cat - drop cheese if necessary, get away. */
  private static void fleeCat(RobotController rc, MapLocation catLoc) throws GameActionException {
    MapLocation me = rc.getLocation();
    int distToCat = me.distanceSquaredTo(catLoc);

    // Log flee decision
    if (rc.getRoundNum() % 20 == 0) {
      System.out.println(
          "ECON_FLEE:"
              + rc.getRoundNum()
              + ":"
              + rc.getID()
              + ":cat at "
              + catLoc
              + " dist="
              + distToCat);
    }

    Direction away = me.directionTo(catLoc);
    Direction flee = DirectionUtil.opposite(away);

    // Move away from cat (toward king for safety)
    int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
    int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);
    MapLocation kingLoc = new MapLocation(kingX, kingY);

    Movement.moveToward(rc, kingLoc);
    Debug.status(rc, "FLEE CAT!");
  }

  /** Collect cheese from mines. FREE ROAM - go anywhere on map to find cheese! */
  private static void collectCheese(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();

    // Find nearest cheese ANYWHERE (no distance restrictions!)
    MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
    MapLocation nearestCheese = null;
    int nearestDist = Integer.MAX_VALUE;

    for (MapLocation loc : nearby) {
      if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        if (info.getCheeseAmount() > 0) {
          int dist = me.distanceSquaredTo(loc);
          // CRITICAL: NO distance restrictions - collect ANY cheese we see!
          if (dist < nearestDist) {
            nearestDist = dist;
            nearestCheese = loc;
          }
        }
      }
    }

    if (nearestCheese != null) {
      // Pick up if possible
      if (rc.canPickUpCheese(nearestCheese)) {
        rc.pickUpCheese(nearestCheese);
        if (rc.getRoundNum() % 20 == 0) {
          System.out.println(
              "ECON_PICKUP:"
                  + rc.getRoundNum()
                  + ":"
                  + rc.getID()
                  + ":cheese at "
                  + nearestCheese
                  + " now carrying="
                  + rc.getRawCheese());
        }
      } else {
        // Move toward cheese
        Movement.moveToward(rc, nearestCheese);
        if (rc.getRoundNum() % 50 == 0) {
          System.out.println(
              "ECON_SEEKING:"
                  + rc.getRoundNum()
                  + ":"
                  + rc.getID()
                  + ":moving to "
                  + nearestCheese);
        }
      }
    } else {
      // No cheese visible - PATROL assigned zone
      if (rc.getRoundNum() % 100 == 0) {
        System.out.println(
            "ECON_PATROL:"
                + rc.getRoundNum()
                + ":"
                + rc.getID()
                + ":zone="
                + myZone
                + " patrolling "
                + zoneCenter);
      }
      // Move toward zone center (stay in assigned quadrant)
      Movement.moveToward(rc, zoneCenter);
    }
  }

  /**
   * Deliver cheese to king (within 3 tiles). Uses zone-based approach vectors to prevent clustering
   * at king.
   */
  private static void deliverCheese(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    int round = rc.getRoundNum();
    int id = rc.getID();

    // Get king position from shared array
    int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
    int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);

    if (kingX == 0 && kingY == 0) {
      System.out.println("DELIVERY_FAIL:" + round + ":" + id + ":no king position");
      return; // No king position yet
    }

    MapLocation kingLoc = new MapLocation(kingX, kingY);
    int distance = me.distanceSquaredTo(kingLoc);

    // Debug delivery attempts
    if (round % 20 == 0) {
      System.out.println(
          "DELIVERY_ATTEMPT:"
              + round
              + ":"
              + id
              + ":dist="
              + distance
              + " cheese="
              + rc.getRawCheese());
    }

    // Can transfer? (distance ≤ 9 = 3 tiles)
    if (distance <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
      int amount = rc.getRawCheese();
      rc.transferCheese(kingLoc, amount);
      System.out.println("DELIVER:" + round + ":" + id + ":amount=" + amount);
      return;
    }

    // TRAFFIC MANAGEMENT: Use zone-based approach to king
    // Instead of all rats converging from same direction, approach from zone side
    // This creates 4 approach lanes instead of 1 congestion point

    if (distance > 25) {
      // Far from king - navigate toward zone-specific approach point
      // Approach point is 5 tiles from king in direction of our zone
      Direction fromZone = Direction.NORTH;
      switch (myZone) {
        case 0:
          fromZone = Direction.NORTHWEST;
          break; // NW zone approaches from NW
        case 1:
          fromZone = Direction.NORTHEAST;
          break; // NE zone approaches from NE
        case 2:
          fromZone = Direction.SOUTHEAST;
          break; // SE zone approaches from SE
        case 3:
          fromZone = Direction.SOUTHWEST;
          break; // SW zone approaches from SW
      }

      MapLocation approachPoint = kingLoc;
      for (int i = 0; i < 5; i++) {
        approachPoint = approachPoint.add(fromZone);
      }

      Movement.moveToward(rc, approachPoint);
    } else {
      // Close to king - final approach
      Movement.moveToward(rc, kingLoc);
    }
  }

  /** Scan visible area and build passability map. */
  private static void scanPassability(RobotController rc) throws GameActionException {
    // Initialize all as passable (optimistic)
    for (int x = 0; x < 60; x++) {
      for (int y = 0; y < 60; y++) {
        localPassable[x][y] = true;
      }
    }

    // Mark known impassable
    MapLocation me = rc.getLocation();
    MapLocation[] visible = rc.getAllLocationsWithinRadiusSquared(me, 20);

    for (MapLocation loc : visible) {
      if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        localPassable[loc.x][loc.y] = info.isPassable();
      }
    }
  }
}
