package ratbot3;

import battlecode.common.*;

/**
 * ratbot3 - SIMPLE VERSION
 *
 * Keep it simple: spawn, collect, deliver, survive.
 * No zones, no queues, no fancy coordination.
 */
public class RobotPlayer {
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("INIT:" + rc.getRoundNum() + ":" + rc.getID() + ":type=" + rc.getType());

        while (true) {
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    System.out.println("KING_TICK:" + rc.getRoundNum());
                    runKing(rc);
                } else {
                    if (rc.getRoundNum() % 10 == 0) {
                        System.out.println("RAT_TICK:" + rc.getRoundNum() + ":" + rc.getID() + ":cheese=" + rc.getRawCheese());
                    }
                    runBabyRat(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException:" + rc.getRoundNum() + ":" + rc.getID());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception:" + rc.getRoundNum() + ":" + rc.getID());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // === KING BEHAVIOR ===
    private static int spawnCount = 0;
    private static int trapCount = 0;
    private static int ratTrapCount = 0;

    private static void runKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int cheese = rc.getGlobalCheese();
        MapLocation me = rc.getLocation();

        // Write positions to shared array
        rc.writeSharedArray(0, me.x);
        rc.writeSharedArray(1, me.y);

        // Calculate enemy king position (round 1)
        if (round == 1) {
            int enemyX = rc.getMapWidth() - 1 - me.x;
            int enemyY = rc.getMapHeight() - 1 - me.y;
            rc.writeSharedArray(2, enemyX);
            rc.writeSharedArray(3, enemyY);
            System.out.println("ENEMY:" + round + ":[" + enemyX + "," + enemyY + "]");
        }

        // COUNT VISIBLE BABY RATS (limited by vision range)
        RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
        int visibleRats = 0;
        for (RobotInfo r : team) {
            if (r.getType() == UnitType.BABY_RAT) {
                visibleRats++;
            }
        }

        System.out.println("KING:" + round + ":cheese=" + cheese + ":spawned=" + spawnCount + " visible=" + visibleRats);

        // SPAWN STRATEGY: Initial 10, then replacements up to max 20
        // Can't track exact deaths (rats leave vision), but can spawn replacements periodically
        boolean shouldSpawn = false;

        if (spawnCount < 10) {
            // Initial spawn phase - get to 10 rats
            shouldSpawn = true;
        } else if (spawnCount < 20 && visibleRats < 8) {
            // Replacement phase - if many rats not visible, likely died
            shouldSpawn = true;
            System.out.println("REPLACEMENT:" + round + ":visible=" + visibleRats + " (likely deaths, spawning replacement)");
        } else if (spawnCount >= 20 && round % 100 == 0) {
            System.out.println("MAX_SPAWNED:" + round + ":spawned " + spawnCount + " total, no more spawns");
        }

        if (shouldSpawn) {
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 150) {
                spawnRat(rc);
            } else if (round % 50 == 0) {
                System.out.println("SPAWN_PAUSED:" + round + ":need=" + (cost + 150) + " have=" + cheese);
            }
        }

        // Minimal traps (after initial spawn complete)
        if (spawnCount >= 10 && trapCount < 3 && cheese > 300) {
            placeCatTrap(rc);
        }
    }

    private static void spawnRat(RobotController rc) throws GameActionException {
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            if (rc.canBuildRat(loc)) {
                // Alternate roles: even=attack, odd=collect
                boolean isAttacker = (spawnCount % 2 == 0);
                String role = isAttacker ? "ATK" : "COL";

                rc.buildRat(loc);
                System.out.println("SPAWN:" + rc.getRoundNum() + ":#" + spawnCount + " " + role + " at " + loc);
                spawnCount++;
                return;
            }
        }
    }

    private static void placeRatTrap(RobotController rc) throws GameActionException {
        // Place at distance 2 (adjacent to king)
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            if (rc.canPlaceRatTrap(loc)) {
                rc.placeRatTrap(loc);
                ratTrapCount++;
                System.out.println("RAT_TRAP:" + rc.getRoundNum());
                return;
            }
        }
    }

    private static void placeCatTrap(RobotController rc) throws GameActionException {
        // Place at distance 2 (adjacent to king)
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            if (rc.canPlaceCatTrap(loc)) {
                rc.placeCatTrap(loc);
                trapCount++;
                System.out.println("CAT_TRAP:" + rc.getRoundNum());
                return;
            }
        }
    }

    // === BABY RAT BEHAVIOR ===
    private static int myRole = -1;

    private static void runBabyRat(RobotController rc) throws GameActionException {
        int cheese = rc.getRawCheese();
        int round = rc.getRoundNum();
        MapLocation me = rc.getLocation();
        int id = rc.getID();

        // SIMPLE: Role based on ID (50/50 split)
        if (myRole == -1) {
            myRole = (id % 2 == 0) ? 0 : 1;  // Even ID=attack, odd ID=collect
            String role = (myRole == 0) ? "ATTACK" : "COLLECT";
            System.out.println("ROLE:" + round + ":" + id + ":" + role);
        }

        if (round % 50 == 0) {
            System.out.println("STATUS:" + round + ":" + id + ":role=" + (myRole == 0 ? "ATK" : "COL") + ":pos=" + me + ":cheese=" + cheese + ":hp=" + rc.getHealth());
        }

        if (myRole == 0) {
            // ATTACK
            attackEnemyKing(rc);
        } else {
            // COLLECT
            if (cheese >= 10) {
                deliver(rc);
            } else {
                collect(rc);
            }
        }
    }

    private static void attackEnemyKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        MapLocation me = rc.getLocation();

        // FIRST: Check if we can SEE enemy king (vision-based tracking)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.RAT_KING) {
                // FOUND ENEMY KING IN VISION!
                MapLocation actualKing = enemy.getLocation();
                int dist = me.distanceSquaredTo(actualKing);

                System.out.println("KING_SPOTTED:" + round + ":" + id + ":at=" + actualKing + " dist=" + dist + " HP=" + enemy.getHealth());

                // Attack if adjacent
                if (dist <= 2 && rc.canAttack(actualKing)) {
                    rc.attack(actualKing);
                    System.out.println("ATTACK_KING:" + round + ":" + id + ":DAMAGE=10 HP_LEFT=" + (enemy.getHealth() - 10));
                    return;
                }

                // Chase the king
                System.out.println("CHASE_KING:" + round + ":" + id + ":pursuing to " + actualKing);
                simpleMove(rc, actualKing);
                return;
            }
        }

        // Can't see enemy king - use calculated position from shared array
        int enemyKingX = rc.readSharedArray(2);
        int enemyKingY = rc.readSharedArray(3);

        if (enemyKingX != 0) {
            MapLocation calculatedKing = new MapLocation(enemyKingX, enemyKingY);
            int dist = me.distanceSquaredTo(calculatedKing);

            System.out.println("RUSH_CALC:" + round + ":" + id + ":target=" + calculatedKing + " dist=" + dist);

            // Navigate to calculated position
            simpleMove(rc, calculatedKing);
            return;
        }

        // No target - search center
        System.out.println("SEARCH_CENTER:" + round + ":" + id);
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        simpleMove(rc, center);
    }

    private static void collect(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        int id = rc.getID();

        System.out.println("COLLECT:" + round + ":" + id + ":pos=" + me);

        // Find nearest cheese
        MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
        MapLocation nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        int cheeseCount = 0;

        for (MapLocation loc : nearby) {
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);
                if (info.getCheeseAmount() > 0) {
                    cheeseCount++;
                    int dist = me.distanceSquaredTo(loc);
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = loc;
                    }
                }
            }
        }

        System.out.println("CHEESE_VISIBLE:" + round + ":" + id + ":count=" + cheeseCount + " nearest=" + nearest + " dist=" + nearestDist);

        if (nearest != null) {
            // Cheese found - go get it
            if (rc.canPickUpCheese(nearest)) {
                rc.pickUpCheese(nearest);
                System.out.println("PICKUP_SUCCESS:" + round + ":" + id + ":from=" + nearest);
            } else {
                System.out.println("MOVING_TO_CHEESE:" + round + ":" + id + ":target=" + nearest);
                simpleMove(rc, nearest);
            }
        } else {
            // No cheese - explore toward center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            System.out.println("EXPLORE_CENTER:" + round + ":" + id + ":target=" + center);
            simpleMove(rc, center);
        }
    }

    private static void deliver(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        int id = rc.getID();

        // Get king position from shared array
        int kingX = rc.readSharedArray(0);
        int kingY = rc.readSharedArray(1);

        if (kingX == 0) {
            System.out.println("DELIVER_FAIL:" + round + ":" + id + ":no king pos in array");
            return;
        }

        MapLocation kingLoc = new MapLocation(kingX, kingY);
        int dist = me.distanceSquaredTo(kingLoc);

        // Log every delivery attempt
        System.out.println("DELIVER_TRY:" + round + ":" + id + ":pos=" + me + " king=" + kingLoc + " dist=" + dist + " cheese=" + rc.getRawCheese());

        // Close enough to transfer?
        if (dist <= 9) {
            if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
                int amount = rc.getRawCheese();
                rc.transferCheese(kingLoc, amount);
                System.out.println("TRANSFER_SUCCESS:" + round + ":" + id + ":amt=" + amount);
                return;
            } else {
                System.out.println("TRANSFER_BLOCKED:" + round + ":" + id + ":close enough but transfer failed");
            }
        }

        // Move toward king
        System.out.println("MOVING_TO_KING:" + round + ":" + id + ":need to get closer");
        simpleMove(rc, kingLoc);
    }

    // === MOVEMENT WITH COMPREHENSIVE DEBUGGING ===
    private static MapLocation lastPosition = null;
    private static int stuckRounds = 0;

    private static void simpleMove(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction desired = me.directionTo(target);
        Direction facing = rc.getDirection();
        int round = rc.getRoundNum();
        int id = rc.getID();

        // STUCK DETECTION
        if (me.equals(lastPosition)) {
            stuckRounds++;
        } else {
            stuckRounds = 0;
            lastPosition = me;
        }

        System.out.println("MOVE:" + round + ":" + id + ":pos=" + me + " target=" + target + " dist=" + me.distanceSquaredTo(target) + " want=" + desired + " facing=" + facing + " stuckRounds=" + stuckRounds);

        // Check nearby friendlies (traffic detection)
        RobotInfo[] friendlies = rc.senseNearbyRobots(2, rc.getTeam());
        System.out.println("NEARBY:" + round + ":" + id + ":friendlies=" + friendlies.length);

        // Log friendly positions when crowded
        if (friendlies.length > 0) {
            StringBuilder friendlyPos = new StringBuilder();
            for (RobotInfo f : friendlies) {
                friendlyPos.append(f.getLocation()).append(",");
            }
            System.out.println("FRIENDLY_POS:" + round + ":" + id + ":" + friendlyPos);
        }

        // Analyze directions when stuck (every round when stuck, not just round 3)
        if (stuckRounds >= 1 && round % 10 == 0) {
            System.out.println("STUCK_ANALYSIS:" + round + ":" + id + ":stuck=" + stuckRounds + " analyzing");
            int passableCount = 0;
            for (Direction d : directions) {
                MapLocation checkLoc = me.add(d);
                if (rc.canSenseLocation(checkLoc)) {
                    MapInfo info = rc.senseMapInfo(checkLoc);
                    RobotInfo robot = rc.senseRobotAtLocation(checkLoc);
                    boolean canMove = rc.canMove(d);
                    if (canMove) passableCount++;
                    if (!canMove || stuckRounds >= 5) {
                        // Only log blocked directions, or all if very stuck
                        System.out.println("DIR:" + round + ":" + id + ":" + d + " passable=" + info.isPassable() + " robot=" + (robot != null) + " canMove=" + canMove);
                    }
                }
            }
            System.out.println("ESCAPE_OPTIONS:" + round + ":" + id + ":passable=" + passableCount + "/8 directions");
        }

        // STUCK RECOVERY: If stuck 2+ rounds, immediately escape
        // Don't wait 3 rounds - move NOW!
        if (stuckRounds >= 2) {
            System.out.println("STUCK_RECOVERY:" + round + ":" + id + ":stuck " + stuckRounds + " rounds, ESCAPING NOW");

            // Prioritize perpendicular to desired direction (likely less congested)
            Direction[] escapeOrder = {
                rotateLeft(desired),
                rotateRight(desired),
                rotateLeft(rotateLeft(desired)),
                rotateRight(rotateRight(desired)),
                directions[0], directions[1], directions[2], directions[3],
                directions[4], directions[5], directions[6], directions[7]
            };

            for (Direction d : escapeOrder) {
                if (rc.canMove(d)) {
                    if (rc.getDirection() == d) {
                        if (rc.canMoveForward()) {
                            rc.moveForward();
                            System.out.println("ACTION:" + round + ":" + id + ":ESCAPED by moving " + d);
                            return;
                        }
                    } else if (rc.canTurn()) {
                        rc.turn(d);
                        System.out.println("ACTION:" + round + ":" + id + ":ESCAPE_TURN to " + d);
                        return;
                    }
                }
            }

            System.out.println("ACTION:" + round + ":" + id + ":TRAPPED completely surrounded");
            return;
        }

        // TRAFFIC: If too crowded, yield (but only if not stuck long-term)
        if (friendlies.length >= 4 && stuckRounds < 5) {
            int yieldSlot = round % 10;
            int mySlot = id % 10;

            if (mySlot != yieldSlot) {
                System.out.println("YIELD:" + round + ":" + id + ":crowded=" + friendlies.length + " waiting for slot");
                return;
            } else {
                System.out.println("MY_TURN:" + round + ":" + id + ":crowded=" + friendlies.length + " attempting move");
            }
        }

        // Try to move in desired direction
        if (facing == desired) {
            if (rc.canMoveForward()) {
                rc.moveForward();
                System.out.println("ACTION:" + round + ":" + id + ":MOVED_FORWARD to " + rc.getLocation());
                return;
            } else {
                // Can't move forward - diagnose why
                MapLocation nextLoc = me.add(desired);
                System.out.println("BLOCKED_FORWARD:" + round + ":" + id + ":target_tile=" + nextLoc);

                if (rc.canSenseLocation(nextLoc)) {
                    MapInfo info = rc.senseMapInfo(nextLoc);
                    RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);

                    String blockType = "unknown";
                    if (!info.isPassable()) blockType = "WALL";
                    else if (blocker != null && blocker.getTeam() == rc.getTeam()) blockType = "FRIENDLY_RAT";
                    else if (blocker != null && blocker.getTeam() == rc.getTeam().opponent()) blockType = "ENEMY_RAT";
                    else if (blocker != null && blocker.getTeam() == Team.NEUTRAL) blockType = "CAT";
                    else blockType = "COOLDOWN";

                    System.out.println("BLOCK_REASON:" + round + ":" + id + ":" + blockType + " at " + nextLoc);

                    // If wall, try perpendicular
                    if (!info.isPassable()) {
                        Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
                        for (Direction p : perp) {
                            if (rc.canMove(p) && rc.canTurn()) {
                                rc.turn(p);
                                System.out.println("ACTION:" + round + ":" + id + ":WALL_DETOUR turned " + p);
                                return;
                            }
                        }
                    }

                    // If friendly rat, try to back up or go around
                    if (blocker != null && blocker.getTeam() == rc.getTeam()) {
                        // Try perpendicular first
                        Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
                        for (Direction p : perp) {
                            if (rc.canMove(p) && rc.canTurn()) {
                                rc.turn(p);
                                System.out.println("ACTION:" + round + ":" + id + ":AVOID_FRIENDLY turned " + p);
                                return;
                            }
                        }
                    }
                }
            }
        }

        // Turn toward target if not facing
        if (facing != desired && rc.canTurn()) {
            rc.turn(desired);
            System.out.println("ACTION:" + round + ":" + id + ":TURNED from " + facing + " to " + desired);
            return;
        }

        // Can't turn - try moving current direction
        if (rc.canMoveForward()) {
            rc.moveForward();
            System.out.println("ACTION:" + round + ":" + id + ":MOVED_CURRENT_DIR " + facing);
            return;
        }

        // Completely blocked - try ANY passable direction
        System.out.println("TRYING_ANY:" + round + ":" + id + ":main path blocked");
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                if (rc.getDirection() == dir) {
                    if (rc.canMoveForward()) {
                        rc.moveForward();
                        System.out.println("ACTION:" + round + ":" + id + ":EMERGENCY_MOVE " + dir);
                        return;
                    }
                } else if (rc.canTurn()) {
                    rc.turn(dir);
                    System.out.println("ACTION:" + round + ":" + id + ":EMERGENCY_TURN to " + dir);
                    return;
                }
            }
        }

        System.out.println("ACTION:" + round + ":" + id + ":STUCK - NO VALID MOVES");
    }

    // Helper for perpendicular movement
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
