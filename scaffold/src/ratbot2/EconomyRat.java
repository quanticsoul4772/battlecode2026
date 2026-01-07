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
        int rawCheese = rc.getRawCheese();

        if (rawCheese >= DELIVERY_THRESHOLD) {
            deliverCheese(rc);
        } else {
            collectCheese(rc);
        }
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

        // Can transfer? (distance ≤ 9 = 3 tiles)
        if (distance <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            int amount = rc.getRawCheese();
            rc.transferCheese(kingLoc, amount);
            System.out.println("DELIVER:" + rc.getRoundNum() + ":" + rc.getID() + ":amount=" + amount);
        } else {
            // Move toward king
            Movement.moveToward(rc, kingLoc);
            Debug.status(rc, "→KING");
        }
    }
}
