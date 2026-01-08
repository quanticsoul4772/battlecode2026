package ratbot4;

import battlecode.common.*;

/**
 * ratbot4 - Rush Defense + Economy
 *
 * Strategy: 12 rats (8 ATK, 4 COL), fast replacement, cheese-enhanced attacks
 */
public class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            try {
                if (rc.getType() == UnitType.RAT_KING) {
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

    // === KING ===
    private static int spawnCount = 0;

    private static void runKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int cheese = rc.getGlobalCheese();
        MapLocation me = rc.getLocation();

        // Write position and HP
        rc.writeSharedArray(0, me.x);
        rc.writeSharedArray(1, me.y);
        rc.writeSharedArray(29, rc.getHealth());

        // Calculate enemy king (round 1)
        if (round == 1) {
            int enemyX = rc.getMapWidth() - 1 - me.x;
            int enemyY = rc.getMapHeight() - 1 - me.y;
            rc.writeSharedArray(2, enemyX);
            rc.writeSharedArray(3, enemyY);
        }

        // SPAWN 12 rats
        if (spawnCount < 12) {
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 100) {
                for (Direction dir : directions) {
                    MapLocation loc = me.add(dir).add(dir);
                    if (rc.canBuildRat(loc)) {
                        int role = (spawnCount < 8) ? 0 : 1; // 0=ATK, 1=COL
                        rc.buildRat(loc);
                        rc.writeSharedArray(4 + spawnCount, role);
                        spawnCount++;
                        break;
                    }
                }
            }
        }

        // Fast replacement
        if (spawnCount >= 12 && spawnCount < 20) {
            RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
            int visATK = 0, visCOL = 0;
            for (RobotInfo r : team) {
                if (r.getType() == UnitType.BABY_RAT) {
                    int rNum = (r.getID() % 20);
                    int rRole = rc.readSharedArray(4 + rNum);
                    if (rRole == 0) visATK++;
                    else visCOL++;
                }
            }

            if (visCOL < 3 || visATK < 6) {
                int cost = rc.getCurrentRatCost();
                if (cheese > cost + 200) {
                    for (Direction dir : directions) {
                        MapLocation loc = me.add(dir).add(dir);
                        if (rc.canBuildRat(loc)) {
                            int role = (visCOL < 3) ? 1 : 0;
                            rc.buildRat(loc);
                            rc.writeSharedArray(4 + spawnCount, role);
                            spawnCount++;
                            break;
                        }
                    }
                }
            }
        }
    }

    // === BABY RATS ===
    private static int myRole = -1;

    private static void runBabyRat(RobotController rc) throws GameActionException {
        // Read role from shared array
        if (myRole == -1) {
            int myNum = rc.getID() % 20;
            myRole = rc.readSharedArray(4 + myNum);
        }

        if (myRole == 0) {
            runAttacker(rc);
        } else {
            runCollector(rc);
        }
    }

    private static void runAttacker(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // RATNAP wounded enemies
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.BABY_RAT && enemy.getHealth() < 50) {
                if (rc.canCarryRat(enemy.getLocation())) {
                    rc.carryRat(enemy.getLocation());
                    return;
                }
            }
        }

        // Throw carried rat
        if (rc.getCarrying() != null && rc.canThrowRat()) {
            MapLocation kingLoc = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
            Direction toKing = me.directionTo(kingLoc);
            if (rc.getDirection() != toKing && rc.canTurn()) {
                rc.turn(toKing);
                return;
            }
            rc.throwRat();
            return;
        }

        // Attack enemies (baby rats AND king!)
        for (RobotInfo enemy : enemies) {
            MapLocation enemyLoc = enemy.getLocation();

            // Attack if we can
            if (rc.canAttack(enemyLoc)) {
                // Cheese-enhanced if conditions met
                int globalCheese = rc.getGlobalCheese();
                if (globalCheese > 500 && enemy.getHealth() < 100 && rc.getRawCheese() >= 8) {
                    rc.attack(enemyLoc, 8);
                } else {
                    rc.attack(enemyLoc);
                }
                return;
            }

            // If it's a king and we're close, try attacking individual tiles (3x3)
            if (enemy.getType() == UnitType.RAT_KING && me.distanceSquaredTo(enemyLoc) <= 10) {
                // Try each king tile
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        MapLocation tile = new MapLocation(enemyLoc.x + dx, enemyLoc.y + dy);
                        if (rc.canAttack(tile)) {
                            rc.attack(tile);
                            return;
                        }
                    }
                }
            }

            // Chase enemy (baby rat or king)
            if (me.distanceSquaredTo(enemyLoc) <= 100) {
                move(rc, enemyLoc);
                return;
            }
        }

        // No enemies visible - RUSH ENEMY KING (like enemy does to us!)
        MapLocation enemyKingLoc = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
        move(rc, enemyKingLoc);
    }

    private static void runCollector(RobotController rc) throws GameActionException {
        int cheese = rc.getRawCheese();
        MapLocation me = rc.getLocation();

        // Emergency delivery
        int kingHP = rc.readSharedArray(29);
        if (kingHP < 100 && cheese > 0) {
            MapLocation kingLoc = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
            if (me.distanceSquaredTo(kingLoc) <= 9 && rc.canTransferCheese(kingLoc, cheese)) {
                rc.transferCheese(kingLoc, cheese);
                return;
            }
            move(rc, kingLoc);
            return;
        }

        // Fight back if attacked
        RobotInfo[] enemies = rc.senseNearbyRobots(2, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                return;
            }
        }

        // Clear traps
        Direction facing = rc.getDirection();
        MapLocation ahead = rc.adjacentLocation(facing);
        if (rc.canRemoveRatTrap(ahead)) {
            rc.removeRatTrap(ahead);
            return;
        }
        if (rc.canRemoveCatTrap(ahead)) {
            rc.removeCatTrap(ahead);
            return;
        }

        // Deliver
        if (cheese >= 10) {
            MapLocation kingLoc = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
            if (me.distanceSquaredTo(kingLoc) <= 9 && rc.canTransferCheese(kingLoc, cheese)) {
                rc.transferCheese(kingLoc, cheese);
                return;
            }
            move(rc, kingLoc);
            return;
        }

        // Collect
        MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapLocation loc : nearby) {
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);
                if (info.getCheeseAmount() > 0) {
                    int dist = me.distanceSquaredTo(loc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = loc;
                    }
                }
            }
        }

        if (nearest != null) {
            if (rc.canPickUpCheese(nearest)) {
                rc.pickUpCheese(nearest);
            } else {
                move(rc, nearest);
            }
        } else {
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            move(rc, center);
        }
    }

    // === MOVEMENT ===
    private static MapLocation lastPos = null;
    private static int stuckCount = 0;

    private static void move(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Stuck detection
        if (me.equals(lastPos)) {
            stuckCount++;
        } else {
            stuckCount = 0;
            lastPos = me;
        }

        // Use Bug2 when stuck
        if (stuckCount >= 3) {
            Direction bug2Dir = bug2(me, target, (d) -> rc.canMove(d));
            if (bug2Dir != Direction.CENTER && rc.canMove(bug2Dir)) {
                rc.move(bug2Dir);
                return;
            }
        }

        // Greedy movement
        Direction desired = me.directionTo(target);
        if (rc.canMove(desired)) {
            rc.move(desired);
        } else if (rc.canTurn() && rc.getDirection() != desired) {
            rc.turn(desired);
        } else {
            for (Direction d : directions) {
                if (rc.canMove(d)) {
                    rc.move(d);
                    return;
                }
            }
        }
    }

    // === BUG2 PATHFINDING ===
    private static MapLocation bugTarget = null;
    private static boolean bugTracing = false;
    private static Direction bugTracingDir = Direction.NORTH;
    private static int bugTurns = 0;

    private static Direction bug2(MapLocation current, MapLocation target, CanMoveFunction canMove) {
        // Reset if target changed
        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            bugTracing = false;
        }

        Direction targetDir = current.directionTo(target);

        // Try direct path
        if (!bugTracing) {
            if (canMove.canMove(targetDir)) {
                return targetDir;
            }
            // Start tracing
            bugTracing = true;
            bugTracingDir = targetDir;
            bugTurns = 0;
        }

        // Trace obstacle
        if (bugTracing) {
            bugTurns++;
            if (bugTurns > 20 || current.distanceSquaredTo(target) < 10) {
                bugTracing = false;
                return targetDir;
            }

            // Try moving along obstacle
            for (int i = 0; i < 8; i++) {
                if (canMove.canMove(bugTracingDir)) {
                    Direction next = rotateRight(bugTracingDir);
                    bugTracingDir = next;
                    return bugTracingDir;
                }
                bugTracingDir = rotateLeft(bugTracingDir);
            }
        }

        return targetDir;
    }

    interface CanMoveFunction {
        boolean canMove(Direction dir);
    }

    private static Direction rotateLeft(Direction d) {
        int idx = 0;
        for (int i = 0; i < 8; i++) {
            if (directions[i] == d) { idx = i; break; }
        }
        return directions[(idx + 7) % 8];
    }

    private static Direction rotateRight(Direction d) {
        int idx = 0;
        for (int i = 0; i < 8; i++) {
            if (directions[i] == d) { idx = i; break; }
        }
        return directions[(idx + 1) % 8];
    }

    private static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };
}
