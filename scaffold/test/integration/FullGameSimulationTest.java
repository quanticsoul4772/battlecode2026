package integration;

import static org.junit.Assert.*;

import battlecode.common.*;
import mock.*;
import org.junit.Test;
import ratbot.*;

public class FullGameSimulationTest {

  @Test
  public void testBasicGameLoop() throws GameActionException {
    MockGameState game = new MockGameState(30, 30);
    game.addGlobalCheese(Team.A, 2500);

    MockRobotController king =
        game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
    MockRobotController rat1 =
        game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);
    MockRobotController rat2 =
        game.addRobot(new MapLocation(16, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

    game.addCheese(new MapLocation(15, 20), 10);
    game.addCheese(new MapLocation(16, 20), 10);

    for (int i = 0; i < 100; i++) {
      RatKing.run(king);
      BabyRat.run(rat1);
      BabyRat.run(rat2);
      game.stepRound();
    }

    assertTrue(king.getHealth() > 0);
    assertTrue(rat1.getHealth() > 0);
    assertTrue(rat2.getHealth() > 0);
  }

  @Test
  public void testSurvivalWithLowCheese() throws GameActionException {
    MockGameState game = new MockGameState(30, 30);
    game.addGlobalCheese(Team.A, 500);

    MockRobotController king =
        game.addRobot(new MapLocation(15, 10), Direction.NORTH, UnitType.RAT_KING, Team.A);
    MockRobotController rat =
        game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.BABY_RAT, Team.A);

    for (int i = 0; i < 50; i++) {
      RatKing.run(king);
      BabyRat.run(rat);
      game.stepRound();
    }

    assertTrue(king.getHealth() > 0);
  }

  @Test
  public void testMultiRatCoordination() throws GameActionException {
    MockGameState game = new MockGameState(30, 30);
    game.addGlobalCheese(Team.A, 3000);

    MockRobotController king =
        game.addRobot(new MapLocation(15, 15), Direction.NORTH, UnitType.RAT_KING, Team.A);

    for (int i = 0; i < 5; i++) {
      game.addRobot(new MapLocation(10 + i, 10 + i), Direction.NORTH, UnitType.BABY_RAT, Team.A);
    }

    for (int i = 0; i < 30; i++) {
      RatKing.run(king);
      for (MockRobotController rat : game.getAllRobots()) {
        if (rat.getType() == UnitType.BABY_RAT) {
          BabyRat.run(rat);
        }
      }
      game.stepRound();
    }

    int aliveRats = 0;
    for (MockRobotController rc : game.getAllRobots()) {
      if (rc.getHealth() > 0) aliveRats++;
    }

    assertTrue(aliveRats >= 5);
  }
}
