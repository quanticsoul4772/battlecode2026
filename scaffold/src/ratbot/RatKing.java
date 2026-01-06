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

        // Try to spawn baby rats (when API available)
        // TODO: Uncomment when rc.canBuildRobot() and rc.buildRobot() are added to API
        // trySpawn(rc);
    }

    /**
     * Attempt to spawn a baby rat if conditions are favorable.
     *
     * NOTE: Implementation ready, waiting for API release.
     * Methods rc.canBuildRobot() and rc.buildRobot() not yet available in engine v1.0.3.
     * See scaffold/API_STATUS.md for current API status.
     */
    @SuppressWarnings("unused")
    private static void trySpawn(RobotController rc) throws GameActionException {
        // Safety check: Ensure we have survival buffer
        int globalCheese = rc.getGlobalCheese();
        int kingCount = RobotUtil.countAllyKings(rc);
        int roundsOfCheese = globalCheese / (kingCount * 3);

        // Don't spawn if we're low on cheese
        if (roundsOfCheese < BehaviorConfig.WARNING_CHEESE_ROUNDS) {
            return;  // Survival first
        }

        // Try all 8 adjacent directions (backward loop for bytecode efficiency)
        Direction[] directions = DirectionUtil.ALL_DIRECTIONS;

        for (int i = directions.length; --i >= 0;) {
            MapLocation spawnLoc = rc.getLocation().add(directions[i]);

            // TODO: Uncomment when API available
            /*
            if (rc.canBuildRobot(spawnLoc)) {
                int babyRats = RobotUtil.countAllyBabyRats(rc);
                int spawnCost = Constants.getSpawnCost(babyRats);

                rc.buildRobot(spawnLoc);

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

                return;  // Only spawn once per turn
            }
            */
        }
    }

}
