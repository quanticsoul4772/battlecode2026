package ratbot2;

import battlecode.common.*;
import ratbot2.utils.*;

/**
 * Combat-specialized baby rat (70% of army).
 *
 * NEW OBJECTIVE: DISTRACT cats (keep them away from king).
 * Strategy: Patrol between cat and king, attract cat attention, kite cat away.
 */
public class CombatRat {
    public static void run(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int id = rc.getID();
        MapLocation me = rc.getLocation();

        // Periodic status report
        if (round % 50 == 0) {
            int hp = rc.getHealth();
            System.out.println("COMBAT_STATUS:" + round + ":" + id + ":hp=" + hp + ":pos=" + me);
        }

        // Check emergency
        int emergency = rc.readSharedArray(Communications.SLOT_EMERGENCY);
        if (emergency == Communications.EMERGENCY_CRITICAL) {
            // Switch to economy mode temporarily
            EconomyRat.run(rc);
            return;
        }

        // PRIORITY 1: Defend against enemy rats (prevent rushes)
        RobotInfo[] enemies = rc.senseNearbyRobots(20, rc.getTeam().opponent());
        if (enemies.length > 0) {
            defendKing(rc, enemies);
            return;
        }

        // PRIORITY 2: ATTACK ENEMY KING (aggressive strategy)
        // Other teams do this - direct assault on enemy king!
        int enemyKingX = rc.readSharedArray(Communications.SLOT_ENEMY_KING_X);
        int enemyKingY = rc.readSharedArray(Communications.SLOT_ENEMY_KING_Y);

        if (enemyKingX != 0) {
            // Enemy king location known - ATTACK IT!
            MapLocation enemyKing = new MapLocation(enemyKingX, enemyKingY);

            // Check if enemy king in vision
            RobotInfo[] visibleEnemies = rc.senseNearbyRobots(20, rc.getTeam().opponent());
            for (RobotInfo enemy : visibleEnemies) {
                if (enemy.getType() == UnitType.RAT_KING) {
                    // Attack enemy king!
                    MapLocation enemyLoc = enemy.getLocation();
                    if (rc.canAttack(enemyLoc) && Vision.canSee(me, rc.getDirection(), enemyLoc, UnitType.BABY_RAT)) {
                        rc.attack(enemyLoc);
                        System.out.println("KING_ASSAULT:" + rc.getRoundNum() + ":" + rc.getID() + ":attacking enemy king!");
                        return;
                    }
                }
            }

            // Navigate toward enemy king
            Movement.moveToward(rc, enemyKing);
            if (rc.getRoundNum() % 100 == 0) {
                System.out.println("COMBAT_ASSAULT:" + rc.getRoundNum() + ":" + rc.getID() + ":→enemy king at " + enemyKing);
            }
            return;
        }

        // PRIORITY 3: Attack cats (if no enemy king spotted yet)
        int catX = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_X);
        int catY = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_Y);

        if (catX != 0) {
            MapLocation catLoc = new MapLocation(catX, catY);

            // Check if cat in vision
            RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
            for (RobotInfo robot : nearby) {
                if (robot.getType() == UnitType.CAT) {
                    attackCat(rc, robot);
                    return;
                }
            }

            // Navigate toward cat
            Movement.moveToward(rc, catLoc);
            return;
        }

        // PRIORITY 4: Explore map to find enemy king or cats
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
        Movement.moveToward(rc, center);
    }

    /**
     * Attack cat for cooperation score.
     */
    private static void attackCat(RobotController rc, RobotInfo cat) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation catLoc = cat.getLocation();
        int dist = me.distanceSquaredTo(catLoc);

        // Adjacent and in vision - attack
        if (dist <= 2 && Vision.canSee(me, rc.getDirection(), catLoc, UnitType.BABY_RAT)) {
            if (rc.canAttack(catLoc)) {
                rc.attack(catLoc);
                System.out.println("CAT_ATTACK:" + rc.getRoundNum() + ":" + rc.getID() + ":damage=10");
                return;
            }
        }

        // Not adjacent or not facing - move toward cat
        Movement.moveToward(rc, catLoc);
    }

    /**
     * Defend king from enemy rats.
     */
    private static void defendKing(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        MapLocation me = rc.getLocation();

        // Get king position
        int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
        int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);
        MapLocation kingLoc = new MapLocation(kingX, kingY);

        // Position between enemy and king
        RobotInfo nearest = enemies[0];
        for (RobotInfo enemy : enemies) {
            if (enemy.getLocation().distanceSquaredTo(kingLoc) < nearest.getLocation().distanceSquaredTo(kingLoc)) {
                nearest = enemy;
            }
        }

        MapLocation enemyLoc = nearest.getLocation();

        // Attack if adjacent
        if (me.distanceSquaredTo(enemyLoc) <= 2 && Vision.canSee(me, rc.getDirection(), enemyLoc, UnitType.BABY_RAT)) {
            if (rc.canAttack(enemyLoc)) {
                rc.attack(enemyLoc);
                System.out.println("DEFEND:" + rc.getRoundNum() + ":attacked enemy!");
                return;
            }
        }

        // Move to intercept
        Movement.moveToward(rc, enemyLoc);
    }

    /**
     * Distract cats - patrol at center, attract cat, keep it busy away from king.
     */
    private static void distractCats(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        // Simple strategy: Patrol at center (where cats spawn)
        // When cat sees us, it enters Attack mode and chases
        // We keep it busy away from king

        // Check if cat visible
        RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
        RobotInfo visibleCat = null;

        for (RobotInfo robot : nearby) {
            if (robot.getType() == UnitType.CAT) {
                visibleCat = robot;
                break;
            }
        }

        if (visibleCat != null) {
            MapLocation catLoc = visibleCat.getLocation();
            int dist = me.distanceSquaredTo(catLoc);

            // Attack if adjacent (locks cat onto us)
            if (dist <= 2 && Vision.canSee(me, rc.getDirection(), catLoc, UnitType.BABY_RAT)) {
                if (rc.canAttack(catLoc)) {
                    rc.attack(catLoc);
                    Debug.status(rc, "ATTACK!");
                    return;
                }
            }

            // Kite: Stay at 3-4 tile distance (cat can see us but we're mobile)
            if (dist < 9) {
                // Too close - move away
                Direction away = me.directionTo(catLoc);
                Direction flee = DirectionUtil.opposite(away);
                Movement.moveToward(rc, me.add(flee).add(flee));
                Debug.status(rc, "KITE AWAY");
            } else if (dist > 16) {
                // Too far - move closer (keep cat engaged)
                Movement.moveToward(rc, catLoc);
                Debug.status(rc, "KITE CLOSE");
            } else {
                // Perfect range - circle around cat
                Direction toCat = me.directionTo(catLoc);
                Direction circle = DirectionUtil.rotateLeft(toCat);
                Movement.moveToward(rc, me.add(circle).add(circle));
                Debug.status(rc, "KITE CIRCLE");
            }
        } else {
            // No cat visible - patrol at center
            if (rc.getRoundNum() % 20 == 0) {
                System.out.println("DISTRACT:" + rc.getRoundNum() + ":" + rc.getID() + ":patrolling center");
            }
            Movement.moveToward(rc, center);
        }
    }

    /**
     * Attack primary target cat (focus fire).
     * CRITICAL: Fight cats at MAP CENTER, not near king.
     * Attacking near king ATTRACTS cat to king.
     */
    private static void attackPrimaryCat(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        // Get king position
        int kingX = rc.readSharedArray(Communications.SLOT_KING_X);
        int kingY = rc.readSharedArray(Communications.SLOT_KING_Y);
        MapLocation kingLoc = new MapLocation(kingX, kingY);

        // Don't fight near king (attracts cat)
        int distToKing = me.distanceSquaredTo(kingLoc);
        if (distToKing < 64) { // Within 8 tiles of king
            // Move toward center (away from king)
            Movement.moveToward(rc, center);
            Debug.status(rc, "→CENTER");
            return;
        }

        // Get primary target from shared array
        int catX = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_X);
        int catY = rc.readSharedArray(Communications.SLOT_PRIMARY_CAT_Y);

        if (catX == 0) {
            // No cat tracked - patrol at center
            Movement.moveToward(rc, center);
            return;
        }

        MapLocation targetCat = new MapLocation(catX, catY);

        // Check if cat in vision
        RobotInfo[] nearby = rc.senseNearbyRobots(20, Team.NEUTRAL);
        RobotInfo visibleCat = null;

        for (RobotInfo robot : nearby) {
            if (robot.getType() == UnitType.CAT) {
                visibleCat = robot;
                break;
            }
        }

        if (visibleCat != null) {
            // Cat in vision - attack
            MapLocation catLoc = visibleCat.getLocation();
            int distance = me.distanceSquaredTo(catLoc);

            // Adjacent? Try to attack
            if (distance <= 2) {
                // Check vision cone (must be facing cat)
                boolean inVision = Vision.canSee(me, rc.getDirection(), catLoc, UnitType.BABY_RAT);

                if (inVision && rc.canAttack(catLoc)) {
                    rc.attack(catLoc);
                    Debug.status(rc, "ATK CAT!");
                    return;
                }

                // Adjacent but not facing - turn toward cat
                if (!inVision) {
                    Direction toCat = me.directionTo(catLoc);
                    if (rc.canTurn()) {
                        rc.turn(toCat);
                        return;
                    }
                }
            }

            // Move toward cat BUT maintain distance (kiting)
            distance = rc.getLocation().distanceSquaredTo(catLoc);
            if (distance > 4) {
                // Too far - move closer
                Movement.moveToward(rc, catLoc);
            } else {
                // Close enough - circle around cat (don't sit still)
                Direction toCat = me.directionTo(catLoc);
                Direction circle = DirectionUtil.rotateLeft(toCat); // Circle left
                MapLocation circlePos = me.add(circle);
                Movement.moveToward(rc, circlePos);
                Debug.status(rc, "CIRCLE CAT");
            }
        } else {
            // Cat not in vision - patrol toward center (don't sit still)
            Movement.moveToward(rc, center);
            Debug.status(rc, "→CENTER");
        }
    }
}
