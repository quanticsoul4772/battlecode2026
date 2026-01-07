package ratbot2;

import battlecode.common.*;
import ratbot2.utils.DirectionUtil;

/**
 * Movement utilities with obstacle avoidance.
 * CRITICAL: Separates turn and move to avoid cooldown conflicts.
 */
public class Movement {
    /**
     * Move toward target with obstacle avoidance.
     * Returns after turning - move happens next turn (cooldown fix).
     */
    public static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction toTarget = me.directionTo(target);
        Direction facing = rc.getDirection();

        // Already facing target - try to move
        if (facing == toTarget) {
            if (rc.canMoveForward()) {
                rc.moveForward();
                return;
            }
            // Blocked forward - try alternatives below
        }

        // Need to turn toward target
        if (facing != toTarget && rc.canTurn()) {
            rc.turn(toTarget);
            return;  // CRITICAL: Move next turn, not same turn
        }

        // Can't turn but can move current direction
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }

        // Blocked - try alternative directions
        Direction[] alternatives = DirectionUtil.orderedDirections(toTarget);
        for (Direction dir : alternatives) {
            if (rc.canMove(dir)) {
                if (rc.getDirection() == dir) {
                    if (rc.canMoveForward()) {
                        rc.moveForward();
                        return;
                    }
                } else if (rc.canTurn()) {
                    rc.turn(dir);
                    return;
                }
            }
        }

        // Completely stuck - do nothing this turn
    }
}
