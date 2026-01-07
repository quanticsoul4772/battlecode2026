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
            if (rc.getRoundNum() % 50 == 0) {
                System.out.println("MOVE_BLOCKED:" + rc.getRoundNum() + ":" + rc.getID() + ":facing=" + facing + " blocked forward");
            }
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

        // Blocked - try alternative directions more aggressively
        Direction[] alternatives = DirectionUtil.ALL_DIRECTIONS;
        for (Direction dir : alternatives) {
            // Try moving in any available direction
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

        // Completely stuck - try random direction
        for (int i = 0; i < 8; i++) {
            Direction random = DirectionUtil.ALL_DIRECTIONS[(rc.getID() + rc.getRoundNum() + i) % 8];
            if (rc.canMove(random)) {
                if (rc.getDirection() == random && rc.canMoveForward()) {
                    rc.moveForward();
                    return;
                } else if (rc.canTurn()) {
                    rc.turn(random);
                    return;
                }
            }
        }
    }
}
