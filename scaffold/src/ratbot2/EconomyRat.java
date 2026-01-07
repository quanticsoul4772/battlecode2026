package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Economy-specialized baby rat (30% of army).
 *
 * SOLE OBJECTIVE: Collect cheese and deliver to king.
 * Sustain king's 3 cheese/round consumption.
 */
public class EconomyRat {
    private static final int DELIVERY_THRESHOLD = 15;  // Deliver when carrying this much

    public static void run(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() % 50 == 0) {
            System.out.println("ECON_RAT:" + rc.getRoundNum() + ":" + rc.getID() + ":running, cheese=" + rc.getRawCheese());
        }

        // EVASIVE: Check if cat nearby - flee if too close
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

        int rawCheese = rc.getRawCheese();

        if (rawCheese >= DELIVERY_THRESHOLD) {
            deliverCheese(rc);
        } else {
            collectCheese(rc);
        }
    }

    /**
     * Flee from cat - drop cheese if necessary, get away.
     */
    private static void fleeCat(RobotController rc, MapLocation catLoc) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction away = me.directionTo(catLoc);
        Direction flee = DirectionUtil.opposite(away);

        // Move away from cat (toward king for safety)
        int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
        int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);
        MapLocation kingLoc = new MapLocation(kingX, kingY);

        Movement.moveToward(rc, kingLoc);
        Debug.status(rc, "FLEE CAT!");
    }

    /**
     * Collect cheese from mines.
     */
    private static void collectCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Find nearest cheese
        MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
        MapLocation nearestCheese = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapLocation loc : nearby) {
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);
                if (info.getCheeseAmount() > 0) {
                    int dist = me.distanceSquaredTo(loc);
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
            } else {
                // Move toward cheese
                Movement.moveToward(rc, nearestCheese);
            }
        } else {
            // No cheese visible - explore toward center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            Movement.moveToward(rc, center);
        }
    }

    /**
     * Deliver cheese to king (within 3 tiles).
     */
    private static void deliverCheese(RobotController rc) throws GameActionException {
        // Get king position from shared array
        int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
        int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);

        if (kingX == 0 && kingY == 0) {
            return; // No king position yet
        }

        MapLocation kingLoc = new MapLocation(kingX, kingY);
        int distance = rc.getLocation().distanceSquaredTo(kingLoc);

        // Debug delivery attempts
        if (rc.getRoundNum() % 50 == 0) {
            System.out.println("DELIVERY_ATTEMPT:" + rc.getRoundNum() + ":" + rc.getID() + ":dist=" + distance + " cheese=" + rc.getRawCheese());
        }

        // Can transfer? (distance ≤ 9 = 3 tiles)
        if (distance <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            int amount = rc.getRawCheese();
            rc.transferCheese(kingLoc, amount);
            System.out.println("DELIVER:" + rc.getRoundNum() + ":" + rc.getID() + ":amount=" + amount);
        } else {
            // Move toward king
            if (rc.getRoundNum() % 20 == 0 && distance > 9) {
                System.out.println("STUCK:" + rc.getRoundNum() + ":" + rc.getID() + ":dist=" + distance + " pos=" + rc.getLocation());
            }
            Movement.moveToward(rc, kingLoc);
        }
    }
}
