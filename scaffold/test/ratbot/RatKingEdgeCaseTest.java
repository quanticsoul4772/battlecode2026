package ratbot;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import mock.*;

public class RatKingEdgeCaseTest {

    @Test
    public void testKingWithZeroCheese() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 0);
        MockRobotController king = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

        RatKing.run(king);

        assertEquals(999, king.readSharedArray(0));
    }

    @Test
    public void testKingNormalOperations() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 5000);
        MockRobotController king = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

        RatKing.run(king);

        int status = king.readSharedArray(0);
        assertTrue(status > 200);
    }

    @Test
    public void testKingEconomyTracking() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 1000);
        MockRobotController king = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

        for (int i = 0; i < 20; i++) {
            RatKing.run(king);
            game.stepRound();
        }

        int remaining = game.getGlobalCheese(Team.A);
        assertEquals(940, remaining);
    }

    @Test
    public void testMultipleKingsCriticalCheese() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 90);

        MockRobotController king1 = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);
        MockRobotController king2 = game.addRobot(new MapLocation(20, 20), Direction.NORTH, UnitType.RAT_KING, Team.A);

        RatKing.run(king1);

        int status = king1.readSharedArray(0);
        assertTrue(status == 999 || status < 100);
    }

    @Test
    public void testKingHealthDecline() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 0);
        MockRobotController king = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

        int initialHealth = king.getHealth();

        for (int i = 0; i < 5; i++) {
            RatKing.run(king);
            game.stepRound();
        }

        assertEquals(initialHealth - 50, king.getHealth());
    }

    @Test
    public void testKingRecoveryFromWarning() throws GameActionException {
        MockGameState game = new MockGameState(30, 30);
        game.addGlobalCheese(Team.A, 250);
        MockRobotController king = game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

        RatKing.run(king);
        int status1 = king.readSharedArray(0);
        assertTrue(status1 < 100);

        game.addGlobalCheese(Team.A, 2000);

        RatKing.run(king);
        int status2 = king.readSharedArray(0);
        assertTrue(status2 > 200);
    }
}
