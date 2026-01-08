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

        // Attack enemies
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.BABY_RAT) {
                if (rc.canAttack(enemy.getLocation())) {
                    // Cheese-enhanced if conditions met
                    int globalCheese = rc.getGlobalCheese();
                    if (globalCheese > 500 && enemy.getHealth() < 30 && rc.getRawCheese() >= 8) {
                        rc.attack(enemy.getLocation(), 8);
                    } else {
                        rc.attack(enemy.getLocation());
                    }
                    return;
                }
                // Chase
                move(rc, enemy.getLocation());
                return;
            }
        }

        // No enemies - spread out and search (don't all cluster at enemy king!)
        int myNum = rc.getID() % 8; // Attackers 0-7
        int mapW = rc.getMapWidth();
        int mapH = rc.getMapHeight();

        // Assign search zones to avoid clustering
        MapLocation searchTarget;
        switch (myNum) {
            case 0: searchTarget = new MapLocation(mapW / 4, mapH / 4); break; // NW
            case 1: searchTarget = new MapLocation(3 * mapW / 4, mapH / 4); break; // NE
            case 2: searchTarget = new MapLocation(3 * mapW / 4, 3 * mapH / 4); break; // SE
            case 3: searchTarget = new MapLocation(mapW / 4, 3 * mapH / 4); break; // SW
            case 4: searchTarget = new MapLocation(mapW / 2, mapH / 4); break; // N
            case 5: searchTarget = new MapLocation(3 * mapW / 4, mapH / 2); break; // E
            case 6: searchTarget = new MapLocation(mapW / 2, 3 * mapH / 4); break; // S
            default: searchTarget = new MapLocation(mapW / 4, mapH / 2); break; // W
        }

        move(rc, searchTarget);
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
    private static void move(RobotController rc, MapLocation target) throws GameActionException {
        Direction desired = rc.getLocation().directionTo(target);

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

    private static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };
}
