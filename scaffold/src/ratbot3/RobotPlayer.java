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

        // COUNT VISIBLE BABY RATS
        RobotInfo[] team = rc.senseNearbyRobots(-1, rc.getTeam());
        int visibleRats = 0;
        for (RobotInfo r : team) {
            if (r.getType() == UnitType.BABY_RAT) {
                visibleRats++;
            }
        }

        System.out.println("KING:" + round + ":cheese=" + cheese + ":spawned=" + spawnCount + " visible=" + visibleRats);

        // SPAWNING STRATEGY:
        // 1. Initial: Spawn 10 rats (rounds 1-10)
        // 2. Replacement: Spawn 1 rat every 50 rounds to replace attrition
        boolean shouldSpawn = false;

        if (spawnCount < 10) {
            // Initial spawn phase
            shouldSpawn = true;
        } else if (round % 50 == 0 && spawnCount < 20) {
            // Replacement spawn: 1 rat per 50 rounds (slow replacement)
            shouldSpawn = true;
            System.out.println("REPLACEMENT_INTERVAL:" + round + ":spawning replacement (1 per 50 rounds)");
        }

        if (shouldSpawn) {
            int cost = rc.getCurrentRatCost();
            if (cheese > cost + 100) {
                spawnRat(rc);
            } else if (round % 50 == 0) {
                System.out.println("REPLACEMENT_BLOCKED:" + round + ":need cheese=" + (cost + 100) + " have=" + cheese);
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

        // Scan map for pathfinding (once per rat)
        if (!mapScanned) {
            scanMap(rc);
        }

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

        // FIRST: Look for ANY enemy to attack (baby rats easier than king!)
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // Priority 1: Attack baby rats if we see any (easier targets, triggers backstab)
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == UnitType.BABY_RAT) {
                MapLocation enemyLoc = enemy.getLocation();
                int dist = me.distanceSquaredTo(enemyLoc);

                System.out.println("ENEMY_RAT:" + round + ":" + id + ":dist=" + dist);

                if (rc.canAttack(enemyLoc)) {
                    rc.attack(enemyLoc);
                    System.out.println("ATTACK_RAT:" + round + ":" + id + ":HIT");
                    return;
                }

                // Chase enemy rat
                if (dist <= 20) {
                    System.out.println("CHASE_RAT:" + round + ":" + id);
                    simpleMove(rc, enemyLoc);
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

                System.out.println("KING_SPOTTED:" + round + ":" + id + ":dist=" + dist + " HP=" + enemy.getHealth() + " facing=" + facing + " toKing=" + toKing + " actionReady=" + actionReady + " canAttack=" + canAtk);

                // Try to attack
                if (canAtk) {
                    if (facingKing) {
                        rc.attack(actualKing);
                        System.out.println("ATTACK_KING:" + round + ":" + id + ":HIT dist=" + dist);
                        return;
                    } else if (rc.canTurn()) {
                        rc.turn(toKing);
                        System.out.println("ATTACK_TURN:" + round + ":" + id + ":turning " + facing + "→" + toKing);
                        return;
                    }
                } else if (dist <= 20) {
                    System.out.println("ATTACK_BLOCKED:" + round + ":" + id + ":dist=" + dist + " actionReady=" + actionReady + " facingKing=" + facingKing);
                }

                // ASSAULT: When close, use turn+moveForward (avoid strafe cooldown penalty!)
                if (dist <= 10) {
                    System.out.println("ASSAULT:" + round + ":" + id + ":dist=" + dist);

                    // Turn to face king if not facing
                    if (facing != toKing && rc.canTurn()) {
                        rc.turn(toKing);
                        System.out.println("TURN_KING:" + round + ":" + id + ":→" + toKing);
                        return;
                    }

                    // Move forward toward king (10 cd, not 18)
                    if (facing == toKing && rc.canMoveForward()) {
                        rc.moveForward();
                        System.out.println("PUSH:" + round + ":" + id + ":forward");
                        return;
                    }

                    // Blocked - try alternate directions with turn+move
                    for (Direction d : directions) {
                        if (rc.canTurn() && rc.getDirection() != d) {
                            rc.turn(d);
                            System.out.println("TURN_ALT:" + round + ":" + id + ":→" + d);
                            return;
                        }
                    }

                    System.out.println("ASSAULT_WAIT:" + round + ":" + id + ":cooldown");
                    return;
                }

                // Far away - normal chase
                System.out.println("CHASE:" + round + ":" + id + ":dist=" + dist);
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
                System.out.println("RUSH_FRESH:" + round + ":" + id + ":to updated pos " + sharedPos + " dist=" + dist + " age=" + posAge);
                simpleMove(rc, sharedPos);
                return;
            }

            // Stale position - search area around it
            if (dist <= 50) {
                System.out.println("SEARCH_STALE:" + round + ":" + id + ":pos stale (" + posAge + " rounds old), searching near " + sharedPos);
                // Search in expanding pattern
                int searchDir = (id + round) % 8;
                Direction searchDirection = directions[searchDir];
                MapLocation searchTarget = sharedPos.add(searchDirection).add(searchDirection).add(searchDirection);
                simpleMove(rc, searchTarget);
                return;
            }

            System.out.println("RUSH:" + round + ":" + id + ":to " + sharedPos + " dist=" + dist);
            simpleMove(rc, sharedPos);
            return;
        }

        // No position known - search center
        System.out.println("SEARCH:" + round + ":" + id);
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
            System.out.println("DELIVER_FAIL:" + round + ":" + id + ":no king pos");
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

        System.out.println("DELIVER:" + round + ":" + id + ":dist=" + dist + " cheese=" + rc.getRawCheese() + " delivStuck=" + deliveryStuckCount);

        // Transfer if close
        if (dist <= 9 && rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            int amount = rc.getRawCheese();
            rc.transferCheese(kingLoc, amount);
            System.out.println("TRANSFER:" + round + ":" + id + ":SUCCESS amt=" + amount);
            deliveryStuckCount = 0;
            return;
        }

        // If stuck delivering for 10+ rounds, DROP cheese and resume collecting
        // Let king pick it up, don't waste time navigating impossible paths
        if (deliveryStuckCount >= 10 && dist > 50) {
            System.out.println("DELIVERY_TIMEOUT:" + round + ":" + id + ":stuck " + deliveryStuckCount + " rounds, giving up, resuming collection");
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
        // Use move(Direction) for direct movement (faster than turn+move)
        if (rc.canMove(desired)) {
            rc.move(desired);
            if (facing == desired) {
                System.out.println("ACTION:" + round + ":" + id + ":MOVED " + desired);
            } else {
                System.out.println("ACTION:" + round + ":" + id + ":STRAFED " + desired + " (was facing " + facing + ")");
            }
            return;
        } else {
            // Can't move desired - check why
            if (facing == desired) {
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
            } else {
                // Not facing desired, check if should turn
                System.out.println("NOT_FACING:" + round + ":" + id + ":want " + desired + " facing " + facing);
            }
        }

        // Try moving in ANY available direction (use move() for directional movement)
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                rc.move(dir);
                System.out.println("ACTION:" + round + ":" + id + ":MOVED_ANY " + dir + " (was facing " + facing + ")");
                return;
            }
        }

        // Can't move anywhere - try turning
        if (rc.canTurn() && facing != desired) {
            rc.turn(desired);
            System.out.println("ACTION:" + round + ":" + id + ":TURN " + desired);
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

    // === PATHFINDING (BFS) ===
    private static int[] queueX = new int[900]; // 30x30 max
    private static int[] queueY = new int[900];
    private static boolean[][] visited = new boolean[60][60];
    private static Direction[][] parent = new Direction[60][60];
    private static boolean[][] passable = new boolean[60][60];
    private static boolean mapScanned = false;

    // Scan map for passability (once per rat)
    private static void scanMap(RobotController rc) throws GameActionException {
        if (mapScanned) return;

        // Initialize all as passable
        for (int x = 0; x < 60; x++) {
            for (int y = 0; y < 60; y++) {
                passable[x][y] = true;
            }
        }

        // Mark impassable tiles we can sense
        MapLocation me = rc.getLocation();
        MapLocation[] visible = rc.getAllLocationsWithinRadiusSquared(me, 20);
        for (MapLocation loc : visible) {
            if (rc.canSenseLocation(loc)) {
                passable[loc.x][loc.y] = rc.sensePassability(loc);
            }
        }

        mapScanned = true;
    }

    // BFS pathfinding - returns direction to move
    private static Direction bfs(MapLocation start, MapLocation target, int mapW, int mapH) {
        if (start.equals(target)) return Direction.CENTER;

        // Reset visited
        for (int x = 0; x < mapW; x++) {
            for (int y = 0; y < mapH; y++) {
                visited[x][y] = false;
                parent[x][y] = Direction.CENTER;
            }
        }

        // BFS queue
        int head = 0, tail = 0;
        queueX[tail] = start.x;
        queueY[tail] = start.y;
        tail++;
        visited[start.x][start.y] = true;

        int[] dx = {0, 1, 1, 1, 0, -1, -1, -1};
        int[] dy = {1, 1, 0, -1, -1, -1, 0, 1};

        while (head < tail) {
            int x = queueX[head];
            int y = queueY[head];
            head++;

            for (int i = 0; i < 8; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];

                if (nx < 0 || nx >= mapW || ny < 0 || ny >= mapH) continue;
                if (visited[nx][ny] || !passable[nx][ny]) continue;

                visited[nx][ny] = true;
                parent[nx][ny] = directions[i];

                queueX[tail] = nx;
                queueY[tail] = ny;
                tail++;

                if (nx == target.x && ny == target.y) {
                    // Backtrack to find first step
                    return backtrack(start, target);
                }
            }
        }

        return Direction.CENTER;
    }

    private static Direction backtrack(MapLocation start, MapLocation target) {
        int x = target.x;
        int y = target.y;
        Direction lastDir = Direction.CENTER;

        while (true) {
            Direction dir = parent[x][y];
            if (dir == Direction.CENTER) break;

            lastDir = dir;

            // Move backwards
            Direction rev = opposite(dir);
            x += deltaX(rev);
            y += deltaY(rev);

            if (x == start.x && y == start.y) break;
        }

        return lastDir;
    }

    private static Direction opposite(Direction d) {
        return directions[(d.ordinal() + 4) % 8];
    }

    private static int deltaX(Direction d) {
        switch (d) {
            case EAST: case NORTHEAST: case SOUTHEAST: return 1;
            case WEST: case NORTHWEST: case SOUTHWEST: return -1;
            default: return 0;
        }
    }

    private static int deltaY(Direction d) {
        switch (d) {
            case NORTH: case NORTHEAST: case NORTHWEST: return 1;
            case SOUTH: case SOUTHEAST: case SOUTHWEST: return -1;
            default: return 0;
        }
    }
}
