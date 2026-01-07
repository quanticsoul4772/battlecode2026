package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Rat King behavior - Battlecode 2026
 *
 * PRIMARY OBJECTIVES:
 * 1. Spawn baby rats aggressively (build army for cat damage race)
 * 2. Place 10 cat traps (1,000 damage = 10% cat HP)
 * 3. Track cats for baby rats (360° vision)
 * 4. Survive (consume 3 cheese/round)
 */
public class RatKing {
    private static int lastGlobalCheese = 2500;
    private static boolean trapsPlaced = false;
    private static int trapCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int globalCheese = rc.getGlobalCheese();

        // EMERGENCY: Critical starvation
        if (globalCheese < 50) {
            rc.writeSharedArray(Communications.SLOT_EMERGENCY, Communications.EMERGENCY_CRITICAL);
            System.out.println("EMERGENCY:" + round + ":CRITICAL:cheese=" + globalCheese);
            return;
        }

        // Clear emergency if recovered
        rc.writeSharedArray(Communications.SLOT_EMERGENCY, 0);

        // Broadcast king position (for cheese delivery)
        MapLocation myLoc = rc.getLocation();
        rc.writeSharedArray(Communications.SLOT_KING_X, myLoc.x);
        rc.writeSharedArray(Communications.SLOT_KING_Y, myLoc.y);

        // PRIORITY 1: Place cat traps FIRST (rounds 5-20)
        // CRITICAL: Traps require action cooldown, so must pause spawning
        if (!trapsPlaced && round >= 5 && round <= 20 && globalCheese >= 30 && trapCount < 10) {
            if (round % 20 == 0) {
                System.out.println("TRAP_CHECK:" + round + ":attempting placement, cheese=" + globalCheese + ", traps=" + trapCount);
            }
            placeDefensiveTraps(rc);
            return; // Skip spawning this round to allow action cooldown for traps
        }

        // PRIORITY 2: Spawn baby rats aggressively (after traps placed)
        spawnBabyRat(rc);

        // PRIORITY 3: Track cats (kings have 360° vision)
        trackCats(rc);

        // Logging
        if (round % 20 == 0) {
            int income = (globalCheese - lastGlobalCheese + 60) / 2;
            System.out.println("KING:" + round + ":cheese=" + globalCheese + ":income=" + income + ":traps=" + trapCount);
            lastGlobalCheese = globalCheese;
        }
    }

    /**
     * Spawn baby rat at distance=2 (outside 3×3 king footprint).
     * AGGRESSIVE: Spawn every turn to build army.
     */
    private static void spawnBabyRat(RobotController rc) throws GameActionException {
        int cost = rc.getCurrentRatCost();
        int cheese = rc.getGlobalCheese();

        // Only check: Can afford + small reserve
        if (cheese < cost + 50) {
            return;
        }

        // Try 8 directions at distance=2
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            MapLocation spawnLoc = rc.getLocation().add(dir).add(dir);

            if (rc.canBuildRat(spawnLoc)) {
                rc.buildRat(spawnLoc);
                return;
            }
        }
    }

    /**
     * Track cat positions in shared array.
     * Kings have 360° vision, always see cats.
     */
    private static void trackCats(RobotController rc) throws GameActionException {
        RobotInfo[] neutral = rc.senseNearbyRobots(-1, Team.NEUTRAL);

        int catIndex = 0;
        MapLocation closestCat = null;
        int closestDist = Integer.MAX_VALUE;

        for (RobotInfo robot : neutral) {
            if (robot.getType() == UnitType.CAT && catIndex < 4) {
                MapLocation catLoc = robot.getLocation();

                // Write to cat tracking slots
                int slotX = Communications.SLOT_CAT1_X + (catIndex * 2);
                int slotY = Communications.SLOT_CAT1_Y + (catIndex * 2);
                rc.writeSharedArray(slotX, catLoc.x);
                rc.writeSharedArray(slotY, catLoc.y);

                // Track closest for primary target
                int dist = rc.getLocation().distanceSquaredTo(catLoc);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestCat = catLoc;
                }

                catIndex++;
            }
        }

        // Set primary target (all combat rats attack this)
        if (closestCat != null) {
            rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_X, closestCat.x);
            rc.writeSharedArray(Communications.SLOT_PRIMARY_CAT_Y, closestCat.y);
        }

        // Clear unused slots
        for (int i = catIndex; i < 4; i++) {
            int slotX = Communications.SLOT_CAT1_X + (i * 2);
            rc.writeSharedArray(slotX, 0);
        }
    }

    /**
     * Place cat traps IN FRONT of king (between cat spawn and king).
     * Simple strategy: Line of traps facing toward map center.
     */
    private static void placeDefensiveTraps(RobotController rc) throws GameActionException {
        MapLocation kingLoc = rc.getLocation();
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        // Direction from king TOWARD center (where cats come from)
        Direction towardCenter = kingLoc.directionTo(center);

        boolean isCooperation = rc.isCooperation();
        System.out.println("TRAP_ATTEMPT:" + rc.getRoundNum() + ":cooperation=" + isCooperation + " kingLoc=" + kingLoc + " dir=" + towardCenter);

        // Place traps ADJACENT to king (distance 1-2 max, since king is 3x3)
        // Try all 8 directions around king
        for (Direction dir : DirectionUtil.ALL_DIRECTIONS) {
            if (trapCount >= 10) break;

            // Try distance 2-4 (distance 1 is king footprint)
            for (int dist = 2; dist <= 4; dist++) {
                if (trapCount >= 10) break;

                MapLocation trapLoc = kingLoc;
                for (int i = 0; i < dist; i++) {
                    trapLoc = trapLoc.add(dir);
                }

            boolean canPlace = rc.canPlaceCatTrap(trapLoc);
            if (rc.getRoundNum() <= 10) {
                boolean actionReady = rc.isActionReady();
                boolean onMap = rc.onTheMap(trapLoc);
                boolean occupied = rc.isLocationOccupied(trapLoc);
                System.out.println("TRAP_TRY:" + trapLoc + " canPlace=" + canPlace + " actionReady=" + actionReady + " onMap=" + onMap + " occupied=" + occupied);
            }

                if (canPlace) {
                    rc.placeCatTrap(trapLoc);
                    trapCount++;
                    System.out.println("TRAP:" + rc.getRoundNum() + ":" + trapLoc + " (ring:" + trapCount + ")");
                    break; // Found a spot, move to next direction
                }
            } // Close inner distance loop
        } // Close outer direction loop

        // Only stop trying after round 50 (keep trying if not all placed)
        if (rc.getRoundNum() > 50) {
            trapsPlaced = true;
            if (trapCount > 0) {
                System.out.println("DEFENSE:" + rc.getRoundNum() + ":Gave up, placed " + trapCount + "/10 traps");
            }
        } else if (trapCount >= 10) {
            trapsPlaced = true;
            System.out.println("DEFENSE:" + rc.getRoundNum() + ":SUCCESS! Placed all 10 traps");
        }
    }
}
