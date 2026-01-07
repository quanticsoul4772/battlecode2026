package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Combat-specialized baby rat (70% of army).
 *
 * SOLE OBJECTIVE: Attack cats for 50% of cooperation score.
 * Damage race: Out-damage enemy team on cats.
 */
public class CombatRat {
    public static void run(RobotController rc) throws GameActionException {
        // Check emergency
        int emergency = rc.readSharedArray(Communications.SLOT_EMERGENCY);
        if (emergency == Communications.EMERGENCY_CRITICAL) {
            // Switch to economy mode temporarily
            EconomyRat.run(rc);
            return;
        }

        // PRIMARY MISSION: Attack cats
        attackPrimaryCat(rc);
    }

    /**
     * Attack primary target cat (focus fire).
     * All combat rats attack same cat for faster kills.
     */
    private static void attackPrimaryCat(RobotController rc) throws GameActionException {
        // Get primary target from shared array
        int catX = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_X);
        int catY = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_Y);

        if (catX == 0) {
            // No cat tracked - patrol toward center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            Movement.moveToward(rc, center);
            return;
        }

        MapLocation targetCat = new MapLocation(catX, catY);
        MapLocation me = rc.getLocation();

        // Check if cat in vision
        RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
        RobotInfo visibleCat = null;

        for (RobotInfo robot : nearby) {
            if (robot.getType() == UnitType.CAT) {
                visibleCat = robot;
                break;
            }
        }

        if (visibleCat != null) {
            // Cat in vision - attack
            MapLocation catLoc = visibleCat.getLocation();
            int distance = me.distanceSquaredTo(catLoc);

            // Adjacent? Try to attack
            if (distance <= 2) {
                // Check vision cone (must be facing cat)
                boolean inVision = Vision.canSee(me, rc.getDirection(), catLoc, UnitType.BABY_RAT);

                if (inVision && rc.canAttack(catLoc)) {
                    rc.attack(catLoc);
                    Debug.status(rc, "ATK CAT!");
                    return;
                }

                // Adjacent but not facing - turn toward cat
                if (!inVision) {
                    Direction toCat = me.directionTo(catLoc);
                    if (rc.canTurn()) {
                        rc.turn(toCat);
                        return;
                    }
                }
            }

            // Move toward cat
            Movement.moveToward(rc, catLoc);
        } else {
            // Cat not in vision - navigate to tracked position
            Movement.moveToward(rc, targetCat);
            Debug.status(rc, "→CAT");
        }
    }
}
