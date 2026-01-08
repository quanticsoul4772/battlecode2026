package ratbot4;

import battlecode.common.*;

/**
 * ratbot4 - Rush Defense + Sustainable Economy
 *
 * STRATEGY:
 * - Spawn 12 rats (6 attackers rush enemy, 6 collectors feed king)
 * - Cheese-enhanced attacks (13 damage kills in 8 hits vs 10)
 * - Simple movement (turn+forward to avoid strafe cooldown)
 * - Replacement spawning (1 per 50 rounds)
 *
 * KEY MECHANICS:
 * - Attack range: Adjacent only (distanceSquared <= 2)
 * - Vision: 90° cone, must face target to attack
 * - Damage formula: 10 base + ceil(log2(cheese)) enhanced
 * - King consumption: 3 cheese/round (will starve if no deliveries)
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

    // ================================================================
    // KING BEHAVIOR
    // ================================================================
    // King spawns rats, places traps, tracks enemies
    // Kings have 360° vision (can see all around) but only 5 tile radius

    private static int spawnCount = 0;
    private static int trapCount = 0;

    private static void runKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int cheese = rc.getGlobalCheese();
        MapLocation me = rc.getLocation();

        // SHARED ARRAY COMMUNICATION
        // Slot 0-1: Our king position (for baby rats to find us)
        rc.writeSharedArray(0, me.x);
        rc.writeSharedArray(1, me.y);

        // Slot 2-3: Enemy king position (for attackers to rush)
        // Maps are symmetric (rotation 180°), so enemy is mirrored
        if (round == 1) {
            int enemyX = rc.getMapWidth() - 1 - me.x;
            int enemyY = rc.getMapHeight() - 1 - me.y;
            rc.writeSharedArray(2, enemyX);
            rc.writeSharedArray(3, enemyY);
        }

        // SPAWNING: 12 rats initially, then 1 every 50 rounds
        // Cost increases: 10 cheese for first 4, 20 for next 4, 30 for next 4, etc.
        if (spawnCount < 12) {
            int cost = rc.getCurrentRatCost();
            // Keep 100 cheese reserve for king survival (33 rounds)
            if (cheese > cost + 100) {
                spawnRat(rc);
            }
        } else if (round % 50 == 0 && spawnCount < 20) {
            // Replacement spawning: assume some rats died, spawn 1 every 50 rounds
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 100) {
                spawnRat(rc);
            }
        }

        // TRAPS: Place 3 cat traps for defense (after spawning done)
        // Cat traps deal 100 damage to cats, help with cooperation score
        if (spawnCount >= 10 && trapCount < 3 && cheese > 300) {
            placeCatTrap(rc);
        }
    }

    private static void spawnRat(RobotController rc) throws GameActionException {
        // Try all 8 directions at distance 2 from king center
        // King is 3x3, so distance 2 is just outside king footprint
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            if (rc.canBuildRat(loc)) {
                rc.buildRat(loc);
                spawnCount++;
                return;
            }
        }
    }

    private static void placeCatTrap(RobotController rc) throws GameActionException {
        // Place traps at distance 2 (BUILD_DISTANCE_SQUARED constraint)
        // Traps are HIDDEN from enemies - they don't know where they are
        for (Direction dir : directions) {
            MapLocation loc = rc.getLocation().add(dir).add(dir);
            if (rc.canPlaceCatTrap(loc)) {
                rc.placeCatTrap(loc);
                trapCount++;
                return;
            }
        }
    }

    // ================================================================
    // BABY RAT BEHAVIOR
    // ================================================================
    // Baby rats are either ATTACKERS (rush enemy) or COLLECTORS (feed king)
    // Role is determined by ID: even = attacker, odd = collector

    private static int myRole = -1; // 0=ATTACK, 1=COLLECT

    private static void runBabyRat(RobotController rc) throws GameActionException {
        int cheese = rc.getRawCheese();
        int round = rc.getRoundNum();
        MapLocation me = rc.getLocation();
        int id = rc.getID();

        // ROLE ASSIGNMENT: Simple 50/50 split based on ID
        // Even IDs (10000, 10002, 10004...) = attackers
        // Odd IDs (10001, 10003, 10005...) = collectors
        if (myRole == -1) {
            myRole = (id % 2 == 0) ? 0 : 1;
            String role = (myRole == 0) ? "ATTACK" : "COLLECT";
            System.out.println("ROLE:" + round + ":" + id + ":" + role);
        }

        // Periodic status logging
        if (round % 10 == 0) {
            System.out.println("RAT:" + round + ":" + id + ":pos=" + me + " role=" + (myRole == 0 ? "ATK" : "COL"));
        }

        // Execute role behavior
        if (myRole == 0) {
            attackEnemyKing(rc);
        } else {
            // Collectors: deliver when carrying enough, otherwise collect
            if (cheese >= 10) {
                deliver(rc);
            } else {
                collect(rc);
            }
        }
    }

    // ================================================================
    // ATTACKER BEHAVIOR
    // ================================================================
    // Attackers rush toward enemy king and attack any enemy rats they encounter
    // Goal: Kill enemy rats to reduce their economy and trigger backstab mode

    private static void attackEnemyKing(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        MapLocation me = rc.getLocation();

        System.out.println("ATTACK:" + round + ":" + id + ":executing");

        // SCAN FOR ENEMIES
        // -1 radius means entire vision cone (90° in facing direction)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        if (round % 10 == 0) {
            System.out.println("ENEMIES:" + round + ":" + id + ":count=" + enemies.length);
        }

        // ATTACK ENEMY BABY RATS (Priority 1)
        // Baby rats are 1x1 (easy to hit) vs kings 3x3 (hard to hit)
        // Killing enemy rats reduces their economy
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.BABY_RAT) {
                MapLocation enemyLoc = enemy.getLocation();

                // Can only attack if adjacent (distSquared <= 2) AND in vision cone
                if (rc.canAttack(enemyLoc)) {
                    int globalCheese = rc.getGlobalCheese();

                    // CHEESE-ENHANCED ATTACKS
                    // Spending 8 cheese adds ceil(log2(8)) = 3 damage
                    // 10 base + 3 = 13 damage (kills in 8 hits vs 10)
                    if (globalCheese > 500 && rc.canAttack(enemyLoc, 8)) {
                        rc.attack(enemyLoc, 8);
                    } else {
                        rc.attack(enemyLoc);
                    }
                    System.out.println("ATTACK_HIT:" + round + ":" + id);
                    return; // Attacked, done for this round
                }
            }
        }

        // ATTACK ENEMY KING (Priority 2)
        // Only if no baby rats nearby
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.RAT_KING) {
                MapLocation kingLoc = enemy.getLocation();
                int dist = me.distanceSquaredTo(kingLoc);

                // If adjacent and can attack, do it
                if (rc.canAttack(kingLoc)) {
                    rc.attack(kingLoc);
                    return;
                }

                // Close to king (within 10 tiles) - move toward it
                if (dist <= 10) {
                    Direction toKing = me.directionTo(kingLoc);

                    // Turn to face king
                    if (rc.getDirection() != toKing && rc.canTurn()) {
                        rc.turn(toKing);
                        return;
                    }

                    // Move forward
                    if (rc.canMoveForward()) {
                        rc.moveForward();
                        return;
                    }
                }

                // Far from king - navigate toward it
                simpleMove(rc, kingLoc);
                return;
            }
        }

        // NO ENEMIES VISIBLE: RUSH ENEMY KING POSITION
        // Use calculated position from shared array (set on round 1)
        MapLocation enemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
        simpleMove(rc, enemyKing);
    }

    // ================================================================
    // COLLECTOR BEHAVIOR
    // ================================================================
    // Collectors find cheese and deliver it to our king
    // King consumes 3 cheese/round - without deliveries, king starves and dies

    private static void collect(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // FIND NEAREST CHEESE
        // Scan vision radius (20 tiles squared ≈ 4.5 tiles)
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

        // COLLECT CHEESE
        if (nearest != null) {
            if (rc.canPickUpCheese(nearest)) {
                rc.pickUpCheese(nearest);
            } else {
                simpleMove(rc, nearest);
            }
        } else {
            // No cheese visible - explore toward map center
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            simpleMove(rc, center);
        }
    }

    private static int deliveryStuckCount = 0;
    private static MapLocation lastDeliveryTarget = null;

    private static void deliver(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // READ KING POSITION from shared array
        int kingX = rc.readSharedArray(0);
        int kingY = rc.readSharedArray(1);

        if (kingX == 0) return; // No king position yet

        MapLocation kingLoc = new MapLocation(kingX, kingY);
        int dist = me.distanceSquaredTo(kingLoc);

        // TRACK DELIVERY PROGRESS
        // If stuck trying to deliver for too long, give up and resume collecting
        if (kingLoc.equals(lastDeliveryTarget)) {
            deliveryStuckCount++;
        } else {
            deliveryStuckCount = 0;
            lastDeliveryTarget = kingLoc;
        }

        // TRANSFER CHEESE to king
        // Range: distSquared <= 9 (3 tiles)
        if (dist <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            rc.transferCheese(kingLoc, rc.getRawCheese());
            deliveryStuckCount = 0;
            return;
        }

        // DELIVERY TIMEOUT: If stuck for 10+ rounds and far from king, give up
        // This prevents infinite navigation on impossible paths
        if (deliveryStuckCount >= 10 && dist > 50) {
            deliveryStuckCount = 0;
            collect(rc); // Resume collecting
            return;
        }

        // NAVIGATE toward king
        simpleMove(rc, kingLoc);
    }

    // ================================================================
    // MOVEMENT SYSTEM
    // ================================================================
    // Simple greedy movement with stuck recovery
    // Uses turn+moveForward (10 cd) instead of move(Direction) (18 cd strafe penalty)

    private static MapLocation lastPosition = null;
    private static int stuckRounds = 0;

    private static void simpleMove(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction desired = me.directionTo(target);
        Direction facing = rc.getDirection();
        int round = rc.getRoundNum();
        int id = rc.getID();

        // STUCK DETECTION
        // Track if we're making progress or spinning in place
        if (me.equals(lastPosition)) {
            stuckRounds++;
        } else {
            stuckRounds = 0;
            lastPosition = me;
        }

        // STUCK RECOVERY: After 2 rounds in same position, escape aggressively
        if (stuckRounds >= 2) {
            System.out.println("STUCK:" + round + ":" + id + ":rounds=" + stuckRounds + " pos=" + me);

            // Try perpendicular directions first (likely less congested)
            Direction[] escapeOrder = {
                rotateLeft(desired), rotateRight(desired),
                rotateLeft(rotateLeft(desired)), rotateRight(rotateRight(desired)),
                directions[0], directions[1], directions[2], directions[3],
                directions[4], directions[5], directions[6], directions[7]
            };

            for (Direction d : escapeOrder) {
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
            return; // Completely blocked, wait for next round
        }

        // TRAFFIC MANAGEMENT: If surrounded by 4+ friendlies, take turns moving
        // This prevents all rats pushing into same space
        RobotInfo[] friendlies = rc.senseNearbyRobots(2, rc.getTeam());
        if (friendlies.length >= 4) {
            int yieldSlot = round % 10;
            int mySlot = id % 10;
            if (mySlot != yieldSlot) {
                return; // Not my turn, wait
            }
        }

        // GREEDY MOVEMENT: Turn toward target, then move forward
        // Using move(Direction) costs 18 cd (strafe), turn+forward costs 10 cd
        if (rc.canMove(desired)) {
            rc.move(desired);
            return;
        }

        // OBSTACLE HANDLING: Blocked by wall or rat
        if (facing == desired) {
            // We're facing target but can't move - something is blocking
            MapLocation nextLoc = me.add(desired);

            if (rc.canSenseLocation(nextLoc)) {
                MapInfo info = rc.senseMapInfo(nextLoc);

                // If wall ahead, try perpendicular (go around)
                if (!info.isPassable()) {
                    Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
                    for (Direction p : perp) {
                        if (rc.canMove(p) && rc.canTurn()) {
                            rc.turn(p);
                            return;
                        }
                    }
                }

                // If friendly rat ahead, try going around
                RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);
                if (blocker != null && blocker.getTeam() == rc.getTeam()) {
                    Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
                    for (Direction p : perp) {
                        if (rc.canMove(p) && rc.canTurn()) {
                            rc.turn(p);
                            return;
                        }
                    }
                }
            }
        }

        // EMERGENCY UNSTUCK: Try ANY direction to keep moving
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                return;
            }
        }

        // Can't move - try turning at least
        if (rc.canTurn() && facing != desired) {
            rc.turn(desired);
        }
    }

    // Helper functions for direction manipulation
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
