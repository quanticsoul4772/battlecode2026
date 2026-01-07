package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Movement utilities with advanced pathfinding.
 * Multi-tier: Greedy → Bug2 → BFS (escalating complexity).
 */
public class Movement {
    // Stuck detection
    private static MapLocation lastPos = null;
    private static int stuckCount = 0;
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

    /**
     * Advanced pathfinding with tiered algorithms.
     * Greedy → Bug2 → BFS (escalates if stuck).
     */
    public static void moveTowardAdvanced(RobotController rc, MapLocation target, boolean[][] passable) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Update stuck detection
        if (me.equals(lastPos)) {
            stuckCount++;
        } else {
            stuckCount = 0;
            lastPos = me;
        }

        // Tier 1: Greedy (fast - always try first)
        if (stuckCount < 5) {
            moveToward(rc, target);
            return;
        }

        // Tier 2: Bug2 (obstacle avoidance)
        if (stuckCount < 20) {
            Direction bug = Pathfinding.bug2(me, target, (d) -> rc.canMove(d));
            if (bug != Direction.CENTER) {
                // Turn toward bug direction
                if (rc.getDirection() != bug && rc.canTurn()) {
                    rc.turn(bug);
                    return;
                }
                // Move in bug direction
                if (rc.canMoveForward()) {
                    rc.moveForward();
                    return;
                }
            }
        }

        // Tier 3: BFS (expensive but guaranteed)
        Direction bfs = Pathfinding.bfs(me, target, passable, 30, 30); // Assume max 30x30
        if (bfs != Direction.CENTER) {
            // Turn toward BFS direction
            if (rc.getDirection() != bfs && rc.canTurn()) {
                rc.turn(bfs);
                return;
            }
            // Move in BFS direction
            if (rc.canMoveForward()) {
                rc.moveForward();
                stuckCount = 0; // Reset if BFS worked
                return;
            }
        }

        // Completely stuck - random walk
        stuckCount = 0; // Reset for next attempt
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            if (rc.canMove(dir)) {
                if (rc.getDirection() == dir && rc.canMoveForward()) {
                    rc.moveForward();
                    return;
                } else if (rc.canTurn()) {
                    rc.turn(dir);
                    return;
                }
            }
        }
    }
}
