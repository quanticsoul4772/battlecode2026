package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;
import ratbot.logging.*;

/**
 * Baby Rat behavior for Battlecode 2026.
 *
 * Primary responsibilities:
 * 1. Collect cheese from mines
 * 2. Transfer cheese to rat kings
 * 3. Avoid cats
 * 4. Basic combat
 * 5. Strategic backstab decisions
 *
 * State machine: EXPLORE → COLLECT → DELIVER → repeat
 */
public class BabyRat {

    // States
    private static enum State { EXPLORE, COLLECT, DELIVER, FLEE }
    private static State currentState = State.EXPLORE;

    // Cheese collection targets
    private static MapLocation targetMine = null;
    private static MapLocation targetKing = null;
    private static int cheeseThreshold = 20; // Return to king when carrying this much

    // Strategic decision tracking
    private static boolean isBackstabbing = false;
    private static int lastBackstabCheck = 0;

    // Emergency mode (Nygard: Circuit breaker pattern)
    private static final int EMERGENCY_CODE = 999;
    private static final int CHEESE_STATUS_SLOT = 0;
    private static boolean isEmergencyMode = false;

    /**
     * Main behavior loop for baby rats.
     */
    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        MapLocation me = rc.getLocation();

        // Check for emergency mode FIRST (highest priority)
        checkEmergencyMode(rc);

        // Check backstab decision periodically (low bytecode cost)
        checkBackstabDecision(rc);

        // Update state
        updateState(rc);

        // Execute behavior based on state
        switch (currentState) {
            case EXPLORE:
                explore(rc);
                break;
            case COLLECT:
                collect(rc);
                break;
            case DELIVER:
                deliver(rc);
                break;
            case FLEE:
                flee(rc);
                break;
        }

        // Log state every 20 rounds
        if (round % 20 == 0) {
            Logger.logState(
                round,
                "BABY_RAT",
                id,
                me.x, me.y,
                rc.getDirection().toString(),
                rc.getHealth(),
                rc.getRawCheese(),
                currentState.toString()
            );
        }
    }

    /**
     * Update state machine based on current conditions.
     */
    private static void updateState(RobotController rc) throws GameActionException {
        // Check for cats first (highest priority)
        RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
        if (nearby.length > 0) {
            // Cat detected
            for (RobotInfo robot : nearby) {
                if (robot.getType() == UnitType.CAT) {
                    currentState = State.FLEE;
                    return;
                }
            }
        }

        // Cheese collection logic
        int rawCheese = rc.getRawCheese();

        if (rawCheese >= cheeseThreshold) {
            currentState = State.DELIVER;
        } else if (rawCheese > 0) {
            currentState = State.COLLECT; // Keep collecting
        } else {
            currentState = State.EXPLORE; // Find cheese
        }
    }

    /**
     * EXPLORE: Find cheese or cheese mines.
     */
    private static void explore(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Look for cheese in vision
        MapLocation[] nearbyLocs = rc.getAllLocationsWithinRadiusSquared(me, 20);

        for (MapLocation loc : nearbyLocs) {
            if (rc.canSenseLocation(loc)) {
                MapInfo info = rc.senseMapInfo(loc);
                if (info.getCheeseAmount() > 0) {
                    moveToward(rc, loc);
                    return;
                }
            }
        }

        // No cheese visible - move toward map center to explore
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        moveToward(rc, center);
    }

    /**
     * COLLECT: Move to cheese and pick it up.
     */
    private static void collect(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Simplified: look for cheese using canPickUpCheese
        MapLocation[] nearbyLocs = rc.getAllLocationsWithinRadiusSquared(me, 20);
        MapLocation nearestCheese = null;
        int nearestDist = Integer.MAX_VALUE;

        for (MapLocation loc : nearbyLocs) {
            if (rc.canPickUpCheese(loc)) {
                int dist = me.distanceSquaredTo(loc);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCheese = loc;
                }
            }
        }

        // Found cheese
        if (nearestCheese != null) {
            // Can we pick it up?
            if (rc.canPickUpCheese(nearestCheese)) {
                rc.pickUpCheese(nearestCheese);

                Logger.logCheeseCollect(
                    rc.getRoundNum(),
                    rc.getID(),
                    me.x, me.y,
                    5, // amount (constant from specs)
                    rc.getRawCheese(),
                    nearestCheese.x, nearestCheese.y
                );

                // Keep collecting if below threshold
                if (rc.getRawCheese() < cheeseThreshold) {
                    currentState = State.COLLECT;
                } else {
                    currentState = State.DELIVER;
                }
            } else {
                // Move toward cheese
                moveToward(rc, nearestCheese);
            }
        } else {
            // No cheese visible, go back to exploring
            currentState = State.EXPLORE;
        }
    }

    /**
     * DELIVER: Return cheese to rat king.
     */
    private static void deliver(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Find nearest rat king
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation nearestKing = null;
        int nearestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType() == UnitType.RAT_KING) {
                int dist = me.distanceSquaredTo(ally.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestKing = ally.getLocation();
                }
            }
        }

        if (nearestKing != null) {
            // Can we transfer?
            if (rc.canTransferCheese(nearestKing, rc.getRawCheese())) {
                int amount = rc.getRawCheese();
                rc.transferCheese(nearestKing, amount);

                Logger.logCheeseTransfer(
                    rc.getRoundNum(),
                    rc.getID(),
                    amount,
                    nearestKing.x, nearestKing.y,
                    rc.getGlobalCheese()
                );

                // Done delivering, back to exploring
                currentState = State.EXPLORE;
            } else {
                // Move toward king
                moveToward(rc, nearestKing);
            }
        } else {
            // No king visible - keep exploring (they might be far away)
            currentState = State.EXPLORE;
        }
    }

    /**
     * FLEE: Avoid cats.
     */
    private static void flee(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Find nearest cat
        RobotInfo[] cats = rc.senseNearbyRobots(-1, Team.NEUTRAL);
        MapLocation nearestCat = null;
        int nearestDist = Integer.MAX_VALUE;

        for (RobotInfo cat : cats) {
            if (cat.getType() == UnitType.CAT) {
                int dist = me.distanceSquaredTo(cat.getLocation());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestCat = cat.getLocation();
                }
            }
        }

        if (nearestCat != null) {
            // Move away from cat (opposite direction)
            Direction away = me.directionTo(nearestCat);
            Direction flee = DirectionUtil.opposite(away);

            // Turn to face away
            if (rc.canTurn() && rc.getDirection() != flee) {
                rc.turn(flee);
            }

            // Move away
            if (rc.canMoveForward()) {
                rc.moveForward();
            }
        } else {
            // Cat not visible anymore, return to collection
            currentState = State.EXPLORE;
        }
    }

    /**
     * Simple movement toward target.
     * Uses turn + move forward pattern.
     */
    private static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        MapLocation me = rc.getLocation();
        Direction toTarget = me.directionTo(target);

        // Turn to face target
        if (rc.canTurn() && rc.getDirection() != toTarget) {
            rc.turn(toTarget);
        }

        // Move forward
        if (rc.canMoveForward()) {
            rc.moveForward();
        }
    }

    /**
     * Check for emergency mode and override behavior if needed.
     * Emergency mode triggered by kings when cheese critically low.
     */
    private static void checkEmergencyMode(RobotController rc) throws GameActionException {
        int cheeseStatus = rc.readSharedArray(CHEESE_STATUS_SLOT);

        if (cheeseStatus == EMERGENCY_CODE) {
            // CRITICAL EMERGENCY: All rats must prioritize cheese delivery
            if (!isEmergencyMode) {
                isEmergencyMode = true;
                System.out.println("EMERGENCY:" + rc.getRoundNum() + ":RAT_EMERGENCY_MODE:id=" + rc.getID());
            }

            // Override state machine - force cheese operations
            if (rc.getRawCheese() > 0) {
                currentState = State.DELIVER; // Deliver NOW
            } else {
                currentState = State.COLLECT; // Collect NOW
            }

            // Lower delivery threshold for more frequent deliveries
            cheeseThreshold = 5;
        } else if (cheeseStatus > 0 && cheeseStatus < 200) {
            // WARNING: Low cheese - increase delivery frequency
            if (!isEmergencyMode) {
                System.out.println("WARNING:" + rc.getRoundNum() + ":LOW_CHEESE_MODE:rounds=" + cheeseStatus);
            }
            isEmergencyMode = false;
            cheeseThreshold = 10; // Deliver more frequently
        } else {
            // Normal operations
            isEmergencyMode = false;
            cheeseThreshold = 20; // Normal threshold
        }
    }

    /**
     * Check if we should backstab (call periodically to save bytecode).
     * Evaluates game state and switches to backstab mode if advantageous.
     */
    private static void checkBackstabDecision(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        // Only check every 50 rounds to save bytecode
        if (round - lastBackstabCheck < 50) return;
        lastBackstabCheck = round;

        // Too early in game
        if (round < 200) return;

        // TODO: Implement when shared array communication protocol is defined
        // For now, placeholder implementation with estimated values
        /*
        // Read game state from shared array
        int ourCatDamage = rc.readSharedArray(29);
        int enemyCatDamage = rc.readSharedArray(30);
        int ourKings = countAllyKings(rc);
        int enemyKings = countEnemyKings(rc);
        int ourCheese = rc.readSharedArray(31);
        int enemyCheese = rc.readSharedArray(32);

        // Evaluate backstab
        GameTheory.GameState state = new GameTheory.GameState(
            round, ourCatDamage, enemyCatDamage,
            ourKings, enemyKings, ourCheese, enemyCheese
        );

        GameTheory.BackstabRecommendation rec = GameTheory.evaluate(state);

        if (rec.shouldBackstab && !isBackstabbing) {
            isBackstabbing = true;
            Logger.logBackstab(round, ourCatDamage, enemyCatDamage,
                              ourKings, enemyKings, "INITIATED:" + rec.reasoning);
        }
        */
    }

    /**
     * Count ally kings (for strategic decisions).
     */
    private static int countAllyKings(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int count = 0;

        for (int i = allies.length; --i >= 0;) {
            if (allies[i].getType() == UnitType.RAT_KING) {
                count++;
            }
        }

        return count;
    }

    /**
     * Count enemy kings (for strategic decisions).
     */
    private static int countEnemyKings(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        int count = 0;

        for (int i = enemies.length; --i >= 0;) {
            if (enemies[i].getType() == UnitType.RAT_KING) {
                count++;
            }
        }

        return count;
    }
}
