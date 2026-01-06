package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;
import ratbot.logging.*;

/**
 * Battlecode 2026 - Ratbot
 *
 * Built on pre-developed algorithm modules:
 * - Vision: Cone-based visibility
 * - Pathfinding: BFS + Bug2
 * - GameTheory: Backstab decisions
 * - Geometry: Distance calculations
 * - DirectionUtil: Turn optimization
 */
public class RobotPlayer {

    /**
     * run() is called when a robot is instantiated.
     * This is the main entry point for all our robots.
     */
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        System.out.println("Ratbot initialized: " + rc.getType() + " at " + rc.getLocation());

        while (true) {
            turnCount++;

            try {
                // Start bytecode tracking
                BytecodeBudget.startTurn(rc.getType().toString());

                // Dispatch based on type
                if (rc.getType() == UnitType.BABY_RAT) {
                    runBabyRat(rc);
                } else if (rc.getType() == UnitType.RAT_KING) {
                    runRatKing(rc);
                }

            } catch (GameActionException e) {
                System.out.println("GameActionException: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Always yield at end of turn
                Clock.yield();
            }
        }
    }

    // ===== Shared State =====

    static int turnCount = 0;

    // ===== Baby Rat Behavior =====

    private static void runBabyRat(RobotController rc) throws GameActionException {
        BabyRat.run(rc);
    }

    // ===== Rat King Behavior =====

    private static void runRatKing(RobotController rc) throws GameActionException {
        RatKing.run(rc);
    }
}
