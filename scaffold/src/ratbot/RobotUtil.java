package ratbot;

import battlecode.common.*;

/**
 * Shared utility methods for robot sensing and counting. Eliminates code duplication between
 * BabyRat and RatKing.
 */
public class RobotUtil {

  /**
   * Count units of specific type for specific team.
   *
   * @param rc Robot controller
   * @param team Team to count (use rc.getTeam() for allies, rc.getTeam().opponent() for enemies)
   * @param type Unit type to count
   * @return Count of matching units
   */
  public static int countUnits(RobotController rc, Team team, UnitType type)
      throws GameActionException {
    RobotInfo[] robots = rc.senseNearbyRobots(-1, team);
    int count = 0;

    for (int i = robots.length; --i >= 0; ) {
      if (robots[i].getType() == type) {
        count++;
      }
    }

    return count;
  }

  /** Count ally baby rats. */
  public static int countAllyBabyRats(RobotController rc) throws GameActionException {
    return countUnits(rc, rc.getTeam(), UnitType.BABY_RAT);
  }

  /** Count ally rat kings. */
  public static int countAllyKings(RobotController rc) throws GameActionException {
    return countUnits(rc, rc.getTeam(), UnitType.RAT_KING);
  }

  /** Count enemy rat kings. */
  public static int countEnemyKings(RobotController rc) throws GameActionException {
    return countUnits(rc, rc.getTeam().opponent(), UnitType.RAT_KING);
  }

  /** Count enemy baby rats. */
  public static int countEnemyBabyRats(RobotController rc) throws GameActionException {
    return countUnits(rc, rc.getTeam().opponent(), UnitType.BABY_RAT);
  }

  /**
   * Find nearest unit of specific type.
   *
   * @param rc Robot controller
   * @param team Team to search
   * @param type Unit type to find
   * @return Nearest robot info, or null if none found
   */
  public static RobotInfo findNearestUnit(RobotController rc, Team team, UnitType type)
      throws GameActionException {
    RobotInfo[] robots = rc.senseNearbyRobots(-1, team);
    MapLocation me = rc.getLocation();

    RobotInfo nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    for (int i = robots.length; --i >= 0; ) {
      if (robots[i].getType() == type) {
        int dist = me.distanceSquaredTo(robots[i].getLocation());
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = robots[i];
        }
      }
    }

    return nearest;
  }

  /** Find nearest ally rat king. */
  public static RobotInfo findNearestAllyKing(RobotController rc) throws GameActionException {
    return findNearestUnit(rc, rc.getTeam(), UnitType.RAT_KING);
  }

  /** Find nearest cat. */
  public static RobotInfo findNearestCat(RobotController rc) throws GameActionException {
    return findNearestUnit(rc, Team.NEUTRAL, UnitType.CAT);
  }

  /**
   * Check if any cats are nearby.
   *
   * @param rc Robot controller
   * @param radiusSquared Search radius squared
   * @return true if cat detected within radius
   */
  public static boolean detectCat(RobotController rc, int radiusSquared)
      throws GameActionException {
    RobotInfo[] nearby = rc.senseNearbyRobots(radiusSquared, Team.NEUTRAL);

    for (int i = nearby.length; --i >= 0; ) {
      if (nearby[i].getType() == UnitType.CAT) {
        return true;
      }
    }

    return false;
  }
}
