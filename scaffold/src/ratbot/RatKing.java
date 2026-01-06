package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;
import ratbot.logging.*;

/**
 * Rat King behavior for Battlecode 2026.
 *
 * Primary responsibilities:
 * 1. Spawn baby rats
 * 2. Track global cheese
 * 3. Prevent starvation (consumes 3 cheese/round)
 */
public class RatKing {

    private static int lastGlobalCheese = 2500;

    // Use BehaviorConfig for all constants

    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        int globalCheese = rc.getGlobalCheese();

        // Calculate survival metrics
        int kingCount = Math.max(1, RobotUtil.countAllyKings(rc));
        int roundsLeft = globalCheese / (kingCount * 3);

        // CRITICAL: Emergency circuit breaker
        if (roundsLeft < BehaviorConfig.CRITICAL_CHEESE_ROUNDS) {
            // Broadcast CRITICAL emergency to all units
            rc.writeSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS, BehaviorConfig.EMERGENCY_CRITICAL);
            System.out.println("EMERGENCY:" + round + ":CRITICAL_STARVATION:rounds=" + roundsLeft + ":cheese=" + globalCheese);

            // Visual emergency indicator
            if (DebugConfig.DEBUG_EMERGENCY) {
                Debug.debugEmergency(rc, "CRITICAL_STARVATION", roundsLeft);
            }

            // STOP ALL SPAWNING - survival mode
            return;
        }

        // WARNING: Low cheese - reduce spawning
        if (roundsLeft < BehaviorConfig.WARNING_CHEESE_ROUNDS) {
            rc.writeSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS, roundsLeft);
            System.out.println("WARNING:" + round + ":LOW_CHEESE:rounds=" + roundsLeft + ":cheese=" + globalCheese);
            // Only spawn if significant surplus
            if (globalCheese > BehaviorConfig.MIN_CHEESE_FOR_SPAWNING) {
                trySpawn(rc);
            }
            return;
        }

        // Normal operations - broadcast status
        rc.writeSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS, Math.min(roundsLeft, 1023)); // 10-bit limit

        // Economy logging every 10 rounds
        if (round % 10 == 0) {
            int income = globalCheese - lastGlobalCheese + 30; // +30 = 10 rounds × 3 consumption
            System.out.println("ECONOMY:" + round + ":cheese=" + globalCheese + ":income=" + (income/10));
            lastGlobalCheese = globalCheese;
        }

        // Reposition if stuck in bad spawn location (BEFORE trying to spawn)
        repositionIfNeeded(rc);

        // Try to spawn baby rats
        trySpawn(rc);
    }

    /**
     * Attempt to spawn a baby rat if conditions are favorable.
     */
    private static void trySpawn(RobotController rc) throws GameActionException {
        // Safety check: Ensure we have survival buffer
        int globalCheese = rc.getGlobalCheese();
        int kingCount = Math.max(1, RobotUtil.countAllyKings(rc));
        int roundsOfCheese = globalCheese / (kingCount * 3);

        // Debug
        if (rc.getRoundNum() % 50 == 0) {
            System.out.println("SPAWN_CHECK:" + rc.getRoundNum() + ":rounds=" + roundsOfCheese + ":threshold=" + BehaviorConfig.WARNING_CHEESE_ROUNDS);
        }

        // Don't spawn if we're low on cheese
        if (roundsOfCheese < BehaviorConfig.WARNING_CHEESE_ROUNDS) {
            return;  // Survival first
        }

        // Try spawn locations at distance=2 (outside 3x3 king footprint)
        // King is 3x3, so adjacent tiles (distance=1) are part of king itself!
        Direction[] directions = DirectionUtil.ALL_DIRECTIONS;

        // Debug spawn cost vs available cheese
        if (DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 50 == 0) {
            int babyRats = RobotUtil.countAllyBabyRats(rc);
            int spawnCost = Constants.getSpawnCost(babyRats);
            int currentCost = rc.getCurrentRatCost();
            boolean actionReady = rc.isActionReady();
            int actionCooldown = rc.getActionCooldownTurns();

            Debug.info(rc, "Spawn economics: cost=" + spawnCost + " apiCost=" + currentCost + " cheese=" + globalCheese + " rats=" + babyRats + " actionReady=" + actionReady + " cd=" + actionCooldown);
        }

        int attemptCount = 0;
        for (int i = directions.length; --i >= 0;) {
            // Spawn at distance=2 to avoid king's 3x3 footprint
            MapLocation spawnLoc = rc.getLocation().add(directions[i]).add(directions[i]);
            attemptCount++;

            boolean canSpawn = rc.canBuildRat(spawnLoc);

            // Visual debug each spawn attempt
            if (DebugConfig.DEBUG_SPAWNING) {
                Debug.debugSpawnAttempt(rc, spawnLoc, canSpawn);
            }

            if (canSpawn) {
                int babyRats = RobotUtil.countAllyBabyRats(rc);
                int spawnCost = Constants.getSpawnCost(babyRats);

                rc.buildRat(spawnLoc);

                // Log spawn for analysis
                Logger.logSpawn(
                    rc.getRoundNum(),
                    "RAT_KING",
                    rc.getID(),
                    spawnLoc.x,
                    spawnLoc.y,
                    spawnCost,
                    babyRats + 1  // After spawning
                );

                // Visual success indicator
                if (DebugConfig.DEBUG_SPAWNING) {
                    Debug.timeline(rc, "SPAWN:" + (babyRats + 1), Debug.Color.GREEN);
                }

                return;  // Only spawn once per turn
            }
        }

        // Debug: No valid spawn location found
        if (DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 10 == 0) {
            Debug.warning(rc, "Spawn blocked - checked " + attemptCount + " locations");
            Debug.status(rc, "SPAWN_BLOCKED x" + attemptCount);
        }
    }

    /**
     * Count open spawn locations around king.
     * @return Number of adjacent tiles where baby rats can spawn (0-8)
     */
    private static int countOpenSpawnLocations(RobotController rc) throws GameActionException {
        Direction[] directions = DirectionUtil.ALL_DIRECTIONS;
        int openCount = 0;

        // Detailed debugging every 20 rounds
        boolean detailedDebug = DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 20 == 0;

        for (int i = directions.length; --i >= 0;) {
            // Spawn at distance=2 to avoid king's 3x3 footprint
            MapLocation spawnLoc = rc.getLocation().add(directions[i]).add(directions[i]);
            boolean canSpawn = rc.canBuildRat(spawnLoc);

            if (canSpawn) {
                openCount++;
            }

            // Debug WHY each location fails
            if (detailedDebug && !canSpawn) {
                boolean onMap = rc.onTheMap(spawnLoc);
                boolean passable = onMap ? rc.sensePassability(spawnLoc) : false;
                boolean occupied = onMap ? rc.isLocationOccupied(spawnLoc) : false;
                int cheese = -1;
                if (onMap && rc.canSenseLocation(spawnLoc)) {
                    MapInfo info = rc.senseMapInfo(spawnLoc);
                    cheese = info.getCheeseAmount();
                }

                // Force print for debugging (bypass Debug.verbose)
                System.out.println("DEBUG:" + rc.getRoundNum() + ":" + rc.getID() + ":BLOCKED:" +
                    directions[i] + "@" + spawnLoc +
                    ":onMap=" + onMap +
                    ":pass=" + passable +
                    ":occ=" + occupied +
                    ":cheese=" + cheese);
            }
        }

        // Summary
        if (detailedDebug) {
            Debug.info(rc, "Spawn capacity: " + openCount + "/8 at " + rc.getLocation());
        }

        return openCount;
    }

    /**
     * Check if current position is good for spawning.
     * @return true if at least 4 spawn locations are open (50% capacity)
     */
    private static boolean isPositionGoodForSpawning(RobotController rc) throws GameActionException {
        int openLocations = countOpenSpawnLocations(rc);
        return openLocations >= 4; // Need at least 50% spawn capacity
    }

    /**
     * Find better position for king to move to.
     * Evaluates all 8 adjacent directions and picks best one.
     * @return Direction to move, or null if no better position found
     */
    private static Direction findBetterPosition(RobotController rc) throws GameActionException {
        MapLocation current = rc.getLocation();
        int currentScore = scoreKingPosition(rc, current);

        Direction bestDir = null;
        int bestScore = currentScore;

        for (int i = DirectionUtil.ALL_DIRECTIONS.length; --i >= 0;) {
            Direction dir = DirectionUtil.ALL_DIRECTIONS[i];
            MapLocation candidate = current.add(dir);

            // Can king move there?
            if (!rc.canMove(dir)) continue;

            // Would it be better?
            int score = scoreKingPosition(rc, candidate);

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;

                // Visual debug - show best candidate
                if (DebugConfig.DEBUG_SPAWNING) {
                    Debug.dot(rc, candidate, Debug.Color.GREEN);
                    Debug.verbose(rc, "Better position: " + candidate + " score=" + score + " (current=" + currentScore + ")");
                }
            }
        }

        return bestDir; // null if no improvement found
    }

    /**
     * Score a king position based on spawn capacity and strategic factors.
     * Higher score = better position.
     */
    private static int scoreKingPosition(RobotController rc, MapLocation pos) throws GameActionException {
        int score = 0;

        // Factor 1: ACTUAL spawn capacity (MOST IMPORTANT)
        // Use canBuildRat() to check if can truly spawn (includes cheese check)
        // Check at distance=2 because king is 3x3 (distance=1 is king's own footprint)
        for (int i = DirectionUtil.ALL_DIRECTIONS.length; --i >= 0;) {
            Direction dir = DirectionUtil.ALL_DIRECTIONS[i];
            MapLocation spawnLoc = pos.add(dir).add(dir);  // Distance=2

            // Check if can ACTUALLY spawn here (not just on map)
            if (rc.canBuildRat(spawnLoc)) {
                score += 20; // Doubled weight - spawnable tiles are critical
            }
        }

        // Factor 2: Distance from edges (reduced weight)
        // Center often has cheese clusters - reduce priority
        int edgeDist = Math.min(
            Math.min(pos.x, rc.getMapWidth() - pos.x),
            Math.min(pos.y, rc.getMapHeight() - pos.y)
        );
        score += edgeDist / 2;  // Half weight - don't over-prioritize center

        // Factor 3: Safe spacing from other kings
        if (KingManagement.isSafeKingLocation(rc, pos)) {
            score += 50; // Significant bonus for safe positioning
        }

        // Factor 4: Avoid nearby cats
        if (RobotUtil.detectCat(rc, 100)) {  // Within 10 tiles
            RobotInfo nearestCat = RobotUtil.findNearestCat(rc);
            if (nearestCat != null) {
                int dist = pos.distanceSquaredTo(nearestCat.getLocation());
                if (dist < 100) {  // Within 10 tiles
                    score -= 30;  // Penalty for cat proximity
                }
            }
        }

        return score;
    }

    /**
     * Reposition king if current location is bad for spawning.
     * Only moves when <4 spawn locations available.
     */
    private static void repositionIfNeeded(RobotController rc) throws GameActionException {
        // Check if current position is adequate
        if (isPositionGoodForSpawning(rc)) {
            return; // Position is fine, don't move
        }

        // Position is bad - find better location
        Direction moveDir = findBetterPosition(rc);

        if (moveDir == null) {
            // No better position found
            if (DebugConfig.DEBUG_SPAWNING) {
                Debug.warning(rc, "Stuck - no better position available");
                Debug.status(rc, "STUCK");
            }
            return;
        }

        // Execute movement (turn + move pattern)
        Direction currentFacing = rc.getDirection();

        // Turn toward target
        if (rc.canTurn() && currentFacing != moveDir) {
            rc.turn(moveDir);

            if (DebugConfig.DEBUG_SPAWNING) {
                Debug.info(rc, "Repositioning: turning to " + moveDir);
            }
            return; // Turn this round, move next round
        }

        // Move forward (40 cd for kings)
        if (rc.canMoveForward()) {
            MapLocation oldPos = rc.getLocation();
            rc.moveForward();
            MapLocation newPos = rc.getLocation();

            if (DebugConfig.DEBUG_SPAWNING) {
                Debug.info(rc, "Repositioned: " + oldPos + " -> " + newPos);
                Debug.timeline(rc, "KING_MOVE", Debug.Color.BLUE);
                Debug.line(rc, oldPos, newPos, Debug.Color.BLUE);
            }
        } else {
            // Can't move forward - debug why
            if (DebugConfig.DEBUG_SPAWNING && rc.getRoundNum() % 10 == 0) {
                Debug.verbose(rc, "Can't move: cooldown or blocked");
            }
        }
    }

}
