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
    private static int cheeseThreshold = BehaviorConfig.CHEESE_DELIVERY_THRESHOLD;

    // Strategic decision tracking
    private static boolean isBackstabbing = false;
    private static int lastBackstabCheck = 0;

    // Emergency mode tracking
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
        if (RobotUtil.detectCat(rc, 20)) {
            currentState = State.FLEE;
            return;
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
        // Find nearest rat king
        RobotInfo nearestKing = RobotUtil.findNearestAllyKing(rc);

        if (nearestKing != null) {
            MapLocation kingLoc = nearestKing.getLocation();
            // Can we transfer?
            if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
                int amount = rc.getRawCheese();
                rc.transferCheese(kingLoc, amount);

                Logger.logCheeseTransfer(
                    rc.getRoundNum(),
                    rc.getID(),
                    amount,
                    kingLoc.x, kingLoc.y,
                    rc.getGlobalCheese()
                );

                // Done delivering, back to exploring
                currentState = State.EXPLORE;
            } else {
                // Move toward king
                moveToward(rc, kingLoc);
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
        RobotInfo nearestCat = RobotUtil.findNearestCat(rc);

        if (nearestCat != null) {
            MapLocation catLoc = nearestCat.getLocation();
            // Move away from cat (opposite direction)
            Direction away = me.directionTo(catLoc);
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
        int cheeseStatus = rc.readSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS);

        if (cheeseStatus == BehaviorConfig.EMERGENCY_CRITICAL) {
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
            cheeseThreshold = BehaviorConfig.EMERGENCY_DELIVERY_THRESHOLD;
        } else if (cheeseStatus > 0 && cheeseStatus < 200) {
            // WARNING: Low cheese - increase delivery frequency
            if (!isEmergencyMode) {
                System.out.println("WARNING:" + rc.getRoundNum() + ":LOW_CHEESE_MODE:rounds=" + cheeseStatus);
            }
            isEmergencyMode = false;
            cheeseThreshold = BehaviorConfig.WARNING_DELIVERY_THRESHOLD;
        } else {
            // Normal operations
            isEmergencyMode = false;
            cheeseThreshold = BehaviorConfig.CHEESE_DELIVERY_THRESHOLD;
        }
    }

    /**
     * Check if we should backstab (call periodically to save bytecode).
     * Evaluates game state and switches to backstab mode if advantageous.
     */
    private static void checkBackstabDecision(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        // Only check every N rounds to save bytecode
        if (round - lastBackstabCheck < BehaviorConfig.BACKSTAB_CHECK_INTERVAL) return;
        lastBackstabCheck = round;

        // Too early in game
        if (round < BehaviorConfig.BACKSTAB_EARLIEST_ROUND) return;

        // TODO: Implement when shared array communication protocol is defined
        // For now, placeholder implementation with estimated values
        /*
        // Read game state from shared array
        int ourCatDamage = rc.readSharedArray(29);
        int enemyCatDamage = rc.readSharedArray(30);
        int ourKings = RobotUtil.countAllyKings(rc);
        int enemyKings = RobotUtil.countEnemyKings(rc);
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

}
