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

    // Emergency thresholds (Nygard: Circuit breaker pattern)
    private static final int EMERGENCY_THRESHOLD = 100; // rounds of cheese
    private static final int CRITICAL_THRESHOLD = 33;   // rounds of cheese
    private static final int EMERGENCY_CODE = 999;      // Shared array emergency signal
    private static final int CHEESE_STATUS_SLOT = 0;    // Shared array slot for cheese status

    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        int globalCheese = rc.getGlobalCheese();

        // Calculate survival metrics
        int kingCount = Math.max(1, countAllyKings(rc));
        int roundsLeft = globalCheese / (kingCount * 3);

        // CRITICAL: Emergency circuit breaker
        if (roundsLeft < CRITICAL_THRESHOLD) {
            // Broadcast CRITICAL emergency to all units
            rc.writeSharedArray(CHEESE_STATUS_SLOT, EMERGENCY_CODE);
            System.out.println("EMERGENCY:" + round + ":CRITICAL_STARVATION:rounds=" + roundsLeft + ":cheese=" + globalCheese);
            // STOP ALL SPAWNING - survival mode
            return;
        }

        // WARNING: Low cheese - reduce spawning
        if (roundsLeft < EMERGENCY_THRESHOLD) {
            rc.writeSharedArray(CHEESE_STATUS_SLOT, roundsLeft);
            System.out.println("WARNING:" + round + ":LOW_CHEESE:rounds=" + roundsLeft + ":cheese=" + globalCheese);
            // Only spawn if significant surplus
            if (globalCheese > 500) {
                trySpawn(rc);
            }
            return;
        }

        // Normal operations - broadcast status
        rc.writeSharedArray(CHEESE_STATUS_SLOT, Math.min(roundsLeft, 1023)); // 10-bit limit

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
        int kingCount = countAllyKings(rc);
        int roundsOfCheese = globalCheese / (kingCount * 3);

        // Don't spawn if we're low on cheese (< 100 rounds of food)
        if (roundsOfCheese < 100) {
            return;  // Survival first
        }

        // Try all 8 adjacent directions (backward loop for bytecode efficiency)
        Direction[] directions = DirectionUtil.ALL_DIRECTIONS;

        for (int i = directions.length; --i >= 0;) {
            MapLocation spawnLoc = rc.getLocation().add(directions[i]);

            // TODO: Uncomment when API available
            /*
            if (rc.canBuildRobot(spawnLoc)) {
                int babyRats = countBabyRats(rc);
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

    /**
     * Count ally baby rats (for spawn cost calculation).
     */
    private static int countBabyRats(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        int count = 0;

        for (int i = allies.length; --i >= 0;) {
            if (allies[i].getType() == UnitType.BABY_RAT) {
                count++;
            }
        }

        return count;
    }

    /**
     * Count ally kings for consumption calculation.
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
}
