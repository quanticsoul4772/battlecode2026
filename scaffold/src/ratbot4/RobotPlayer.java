package ratbot4;

import battlecode.common.*;

/**
 * ratbot3 - SIMPLE VERSION
 *
 * Keep it simple: spawn, collect, deliver, survive.
 * No zones, no queues, no fancy coordination.
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
        }

        // COUNT VISIBLE BABY RATS
        RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
        int visibleRats = 0;
        for (RobotInfo r : team) {
            if (r.getType() == UnitType.BABY_RAT) {
                visibleRats++;
            }
        }


        // SPAWNING STRATEGY:
        // 1. Initial: Spawn 10 rats (rounds 1-10)
        // 2. Replacement: Spawn 1 rat every 50 rounds to replace attrition
        boolean shouldSpawn = false;

        if (spawnCount < 12) {
            // Initial spawn: 12 rats (not 10)
            shouldSpawn = true;
        } else if (round % 50 == 0 && spawnCount < 20) {
            // Replacement: 1 per 50 rounds
            shouldSpawn = true;
        }

        if (shouldSpawn) {
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 100) {
                spawnRat(rc);
            } else if (round % 50 == 0) {
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
        }

        if (round % 50 == 0) {
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

        // FIRST: Look for ANY enemy to attack (baby rats easier than king!)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Attack ALL adjacent tiles with CHEESE-ENHANCED attacks
        for (Direction dir : directions) {
            MapLocation loc = me.add(dir);
            if (rc.canAttack(loc)) {
                int globalCheese = rc.getGlobalCheese();
                if (globalCheese > 500 && rc.canAttack(loc, 8)) {
                    rc.attack(loc, 8); // 13 damage
                } else {
                    rc.attack(loc); // 10 damage
                }
                return;
            }
        }

        // Attack enemy baby rats we can see
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.BABY_RAT) {
                MapLocation enemyLoc = enemy.getLocation();
                if (rc.canAttack(enemyLoc)) {
                    rc.attack(enemyLoc);
                    return;
                }
            }
        }

        // Priority 2: If no baby rats, go for king
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.RAT_KING) {
                // FOUND ENEMY KING!
                MapLocation actualKing = enemy.getLocation();
                int dist = me.distanceSquaredTo(actualKing);
                Direction toKing = me.directionTo(actualKing);
                Direction facing = rc.getDirection();

                // Debug attack capability
                boolean actionReady = rc.isActionReady();
                boolean canAtk = rc.canAttack(actualKing);
                boolean facingKing = (facing == toKing);


                // Try to attack
                if (canAtk) {
                    if (facingKing) {
                        rc.attack(actualKing);
                        return;
                    } else if (rc.canTurn()) {
                        rc.turn(toKing);
                        return;
                    }
                } else if (dist <= 20) {
                }

                // ASSAULT: When close, use turn+moveForward (avoid strafe cooldown penalty!)
                if (dist <= 10) {

                    // Turn to face king if not facing
                    if (facing != toKing && rc.canTurn()) {
                        rc.turn(toKing);
                        return;
                    }

                    // Move forward toward king (10 cd, not 18)
                    if (facing == toKing && rc.canMoveForward()) {
                        rc.moveForward();
                        return;
                    }

                    // Blocked - try alternate directions with turn+move
                    for (Direction d : directions) {
                        if (rc.canTurn() && rc.getDirection() != d) {
                            rc.turn(d);
                            return;
                        }
                    }

                    return;
                }

                // Far away - normal chase
                simpleMove(rc, actualKing);
                return;
            }
        }

        // Can't see king - use shared array position
        int lastX = rc.readSharedArray(2);
        int lastY = rc.readSharedArray(3);
        int posTimestamp = rc.readSharedArray(4);

        if (lastX != 0) {
            MapLocation sharedPos = new MapLocation(lastX, lastY);
            int dist = me.distanceSquaredTo(sharedPos);
            int posAge = (round - posTimestamp + 1000) % 1000;

            // Check if position is fresh (updated recently by our king or another attacker)
            if (posAge < 10) {
                simpleMove(rc, sharedPos);
                return;
            }

            // Stale position - search area around it
            if (dist <= 50) {
                // Search in expanding pattern
                int searchDir = (id + round) % 8;
                Direction searchDirection = directions[searchDir];
                MapLocation searchTarget = sharedPos.add(searchDirection).add(searchDirection).add(searchDirection);
                simpleMove(rc, searchTarget);
                return;
            }

            simpleMove(rc, sharedPos);
            return;
        }

        // No position known - search center
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        simpleMove(rc, center);
    }

    private static void collect(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        int id = rc.getID();


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


        if (nearest != null) {
            // Cheese found - go get it
            if (rc.canPickUpCheese(nearest)) {
                rc.pickUpCheese(nearest);
            } else {
                simpleMove(rc, nearest);
            }
        } else {
            // No cheese - explore toward center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            simpleMove(rc, center);
        }
    }

    private static int deliveryStuckCount = 0;
    private static MapLocation lastDeliveryTarget = null;

    private static void deliver(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int round = rc.getRoundNum();
        int id = rc.getID();

        // Get king position
        int kingX = rc.readSharedArray(0);
        int kingY = rc.readSharedArray(1);

        if (kingX == 0) {
            return;
        }

        MapLocation kingLoc = new MapLocation(kingX, kingY);
        int dist = me.distanceSquaredTo(kingLoc);

        // Track if we're making progress toward king
        if (kingLoc.equals(lastDeliveryTarget)) {
            deliveryStuckCount++;
        } else {
            deliveryStuckCount = 0;
            lastDeliveryTarget = kingLoc;
        }


        // Transfer if close
        if (dist <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            int amount = rc.getRawCheese();
            rc.transferCheese(kingLoc, amount);
            deliveryStuckCount = 0;
            return;
        }

        // If stuck delivering for 10+ rounds, DROP cheese and resume collecting
        // Let king pick it up, don't waste time navigating impossible paths
        if (deliveryStuckCount >= 10 && dist > 50) {
            deliveryStuckCount = 0;
            // Resume collecting (will drop cheese naturally or find closer path later)
            collect(rc);
            return;
        }

        // Navigate to king - use simple movement (works better than distance-reducing)
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


        // Check nearby friendlies (traffic detection)
        RobotInfo[] friendlies = rc.senseNearbyRobots(2, rc.getTeam());

        // Log friendly positions when crowded
        if (friendlies.length > 0) {
            StringBuilder friendlyPos = new StringBuilder();
            for (RobotInfo f : friendlies) {
                friendlyPos.append(f.getLocation()).append(",");
            }
        }

        // Analyze directions when stuck (every round when stuck, not just round 3)
        if (stuckRounds >= 1 && round % 10 == 0) {
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
                    }
                }
            }
        }

        // STUCK RECOVERY: If stuck 2+ rounds, immediately escape
        // Don't wait 3 rounds - move NOW!
        if (stuckRounds >= 2) {

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
                            return;
                        }
                    } else if (rc.canTurn()) {
                        rc.turn(d);
                        return;
                    }
                }
            }

            return;
        }

        // TRAFFIC: If too crowded, yield (but only if not stuck long-term)
        if (friendlies.length >= 4 && stuckRounds < 5) {
            int yieldSlot = round % 10;
            int mySlot = id % 10;

            if (mySlot != yieldSlot) {
                return;
            } else {
            }
        }

        // Try to move in desired direction
        // Use move(Direction) for direct movement (faster than turn+move)
        if (rc.canMove(desired)) {
            rc.move(desired);
            if (facing == desired) {
            } else {
            }
            return;
        } else {
            // Can't move desired - check why
            if (facing == desired) {
                // Can't move forward - diagnose why
                MapLocation nextLoc = me.add(desired);

                if (rc.canSenseLocation(nextLoc)) {
                    MapInfo info = rc.senseMapInfo(nextLoc);
                    RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);

                    String blockType = "unknown";
                    if (!info.isPassable()) blockType = "WALL";
                    else if (blocker != null && blocker.getTeam() == rc.getTeam()) blockType = "FRIENDLY_RAT";
                    else if (blocker != null && blocker.getTeam() == rc.getTeam().opponent()) blockType = "ENEMY_RAT";
                    else if (blocker != null && blocker.getTeam() == Team.NEUTRAL) blockType = "CAT";
                    else blockType = "COOLDOWN";


                    // If wall, try perpendicular
                    if (!info.isPassable()) {
                        Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
                        for (Direction p : perp) {
                            if (rc.canMove(p) && rc.canTurn()) {
                                rc.turn(p);
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
                                return;
                            }
                        }
                    }
                }
            } else {
                // Not facing desired, check if should turn
            }
        }

        // Try moving in ANY available direction (use move() for directional movement)
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
        }

        // Can't move anywhere - try turning
        if (rc.canTurn() && facing != desired) {
            rc.turn(desired);
            return;
        }

        // Completely blocked - try ANY passable direction
        for (Direction dir : directions) {
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
