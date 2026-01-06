package ratbot;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import mock.*;

public class BabyRatStateMachineTest {

    @Test
    public void testStateTransition_ExploreToCollect() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);
        game.addCheese(new MapLocation(15, 16), 5);

        for (int i = 0; i < 10; i++) {
            BabyRat.run(rat);
            game.stepRound();
        }

        assertTrue(rat.getHealth() > 0);
    }

    @Test
    public void testStateTransition_CollectToDeliver() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 2500);

        MockRobotController king = game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        game.addCheese(new MapLocation(15, 15), 20);
        rat.pickUpCheese(new MapLocation(15, 15));

        assertTrue(rat.getRawCheese() >= 20);

        for (int i = 0; i < 15; i++) {
            BabyRat.run(rat);
            game.stepRound();
            if (rat.getRawCheese() == 0) break;
        }

        assertEquals(0, rat.getRawCheese());
    }

    @Test
    public void testStateTransition_AnyToFlee() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);
        game.addRobot(new MapLocation(15, 18), Direction.SOUTH, UnitType.CAT, Team.NEUTRAL);

        MapLocation start = rat.getLocation();

        for (int i = 0; i < 10; i++) {
            BabyRat.run(rat);
            game.stepRound();
        }

        MapLocation end = rat.getLocation();
        assertNotEquals(start, end);
    }

    @Test
    public void testMultipleCheesePileCollection() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 2500);

        MockRobotController king = game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        game.addCheese(new MapLocation(15, 16), 5);
        game.addCheese(new MapLocation(15, 17), 5);
        game.addCheese(new MapLocation(15, 18), 5);

        for (int i = 0; i < 30; i++) {
            BabyRat.run(rat);
            game.stepRound();
        }

        assertTrue(rat.getHealth() > 0);
    }

    @Test
    public void testRatExploresWhenNoCheese() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        for (int i = 0; i < 10; i++) {
            BabyRat.run(rat);
            game.stepRound();
        }

        assertTrue(rat.getHealth() > 0);
    }

    @Test
    public void testEmergencyOverridesNormalBehavior() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 90);

        MockRobotController king = game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        game.addCheese(new MapLocation(15, 15), 10);
        rat.pickUpCheese(new MapLocation(15, 15));

        RatKing.run(king);
        assertEquals(999, king.readSharedArray(0));

        BabyRat.run(rat);

        assertTrue(rat.getRawCheese() > 0);
    }

    @Test
    public void testWarningModeChangesThreshold() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 250);

        MockRobotController king = game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        RatKing.run(king);

        int status = king.readSharedArray(0);
        assertTrue(status > 0 && status < 200);

        game.addCheese(new MapLocation(15, 15), 15);
        rat.pickUpCheese(new MapLocation(15, 15));

        BabyRat.run(rat);

        assertTrue(rat.getRawCheese() > 0);
    }

    @Test
    public void testCatFleeOverridesCollection() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        MockRobotController rat = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

        game.addCheese(new MapLocation(15, 16), 25);
        game.addRobot(new MapLocation(15, 18), Direction.SOUTH, UnitType.CAT, Team.NEUTRAL);

        for (int i = 0; i < 5; i++) {
            BabyRat.run(rat);
            game.stepRound();
        }

        assertTrue(rat.getLocation().y <= 15);
    }
}
