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
    private static int trapCount = 0;

    private static void runKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int cheese = rc.getGlobalCheese();
        MapLocation me = rc.getLocation();

        // Write position and HP
        rc.writeSharedArray(0, me.x);
        rc.writeSharedArray(1, me.y);
        rc.writeSharedArray(29, rc.getHealth());

        // Debug spawning
        if (round % 50 == 0) {
            System.out.println("KING:" + round + ":cheese=" + cheese + ":spawned=" + spawnCount);
        }

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
            if (cheese > cost + 50) {
                boolean spawned = false;
                // Try all 8 directions at distance 2, 3, and 4
                for (int dist = 2; dist <= 4 && !spawned; dist++) {
                    for (Direction dir : directions) {
                        MapLocation loc = me;
                        for (int i = 0; i < dist; i++) {
                            loc = loc.add(dir);
                        }
                        if (rc.canBuildRat(loc)) {
                            int role = (spawnCount < 8) ? 0 : 1; // 0=ATK, 1=COL
                            rc.buildRat(loc);
                            rc.writeSharedArray(4 + spawnCount, role);
                            spawnCount++;
                            System.out.println("SPAWN:" + round + ":rat#" + spawnCount + " dist=" + dist);
                            spawned = true;
                            break;
                        }
                    }
                }
                if (!spawned && round % 10 == 0) {
                    System.out.println("SPAWN_BLOCKED:" + round + ":all locations occupied, need=" + spawnCount);
                }
            }
        }

        // Slow replacement (time-based, not visibility-based)
        // Spawn 1 rat every 50 rounds to replace attrition
        if (spawnCount >= 12 && spawnCount < 20 && round % 50 == 0) {
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 200) {
                for (Direction dir : directions) {
                    MapLocation loc = me.add(dir).add(dir);
                    if (rc.canBuildRat(loc)) {
                        // Alternate roles for replacement
                        int role = (spawnCount % 2 == 0) ? 0 : 1;
                        rc.buildRat(loc);
                        rc.writeSharedArray(4 + spawnCount, role);
                        spawnCount++;
                        System.out.println("REPLACEMENT:" + round + ":spawned #" + spawnCount);
                        break;
                    }
                }
            }
        }

        // Place rat traps after spawning (hidden 50 damage!)
        // Spawn only reaches 11 (spawn locations occupied), so trigger at 11
        if (spawnCount >= 11 && trapCount < 10 && cheese > 50) {
            for (Direction dir : directions) {
                MapLocation loc = me.add(dir).add(dir);
                if (rc.canPlaceRatTrap(loc)) {
                    rc.placeRatTrap(loc);
                    trapCount++;
                    System.out.println("TRAP:" + round + ":placed #" + trapCount);
                    return;
                }
            }
        }
    }

    // === BABY RATS ===
    private static int myRole = -1;

    private static void runBabyRat(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();

        // Simple role: even ID = ATK, odd ID = COL
        if (myRole == -1) {
            myRole = (id % 2 == 0) ? 0 : 1;
            System.out.println("ROLE:" + round + ":" + id + ":" + (myRole == 0 ? "ATK" : "COL"));
        }

        if (round % 50 == 0) {
            System.out.println("TICK:" + round + ":" + id + ":" + (myRole == 0 ? "ATK" : "COL") + " pos=" + rc.getLocation());
        }

        if (myRole == 0) {
            runAttacker(rc);
        } else {
            runCollector(rc);
        }
    }

    private static void runAttacker(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        int id = rc.getID();

        if (round % 50 == 0) {
            System.out.println("ATK:" + round + ":" + id + ":running attacker");
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Attack ALL adjacent tiles with CHEESE-ENHANCED ATTACKS
        for (Direction dir : directions) {
            MapLocation loc = me.add(dir);
            if (rc.canAttack(loc)) {
                int globalCheese = rc.getGlobalCheese();
                // Use 8 cheese for 13 damage (kill in 8 hits vs 10)
                if (globalCheese > 500 && rc.canAttack(loc, 8)) {
                    rc.attack(loc, 8);
                } else {
                    rc.attack(loc);
                }
                return;
            }
        }

        // RUSH ENEMY KING - go straight there!
        MapLocation enemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
        move(rc, enemyKing);
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
    // Instance state per rat (not static!)
    private static java.util.HashMap<Integer, MapLocation> ratLastPos = new java.util.HashMap<>();
    private static java.util.HashMap<Integer, Integer> ratStuckCount = new java.util.HashMap<>();

    private static void move(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        int id = rc.getID();
        int round = rc.getRoundNum();

        // Stuck detection (per-rat state)
        MapLocation lastPos = ratLastPos.get(id);
        int stuckCount = ratStuckCount.getOrDefault(id, 0);

        if (me.equals(lastPos)) {
            stuckCount++;
        } else {
            stuckCount = 0;
        }

        ratLastPos.put(id, me);
        ratStuckCount.put(id, stuckCount);

        if (stuckCount >= 2 && round % 50 == 0) {
            System.out.println("STUCK:" + round + ":" + id + ":count=" + stuckCount + " pos=" + me);
        }

        // Movement: Use turn+forward to avoid strafe penalty
        Direction desired = me.directionTo(target);
        Direction facing = rc.getDirection();

        // Turn to face target
        if (facing != desired && rc.canTurn()) {
            rc.turn(desired);
            return;
        }

        // Remove dirt if blocking
        MapLocation ahead = rc.adjacentLocation(facing);
        if (rc.canRemoveDirt(ahead)) {
            rc.removeDirt(ahead);
            return;
        }

        // Move forward (10 cd, not 18 strafe penalty)
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }

        // Blocked - try turning to any open direction
        for (Direction d : directions) {
            if (rc.canMove(d)) {
                if (rc.getDirection() == d && rc.canMoveForward()) {
                    rc.moveForward();
                    return;
                } else if (rc.canTurn()) {
                    rc.turn(d);
                    return;
                }
            }
        }

        if (round % 50 == 0) {
            System.out.println("BLOCKED:" + round + ":" + id + ":completely stuck");
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
