package mock;

import battlecode.common.*;
import java.util.*;

public class MockGameState {
  private int width;
  private int height;
  private int round = 0;
  private List<MockRobotController> robots = new ArrayList<>();
  private Map<Team, Integer> globalCheese = new EnumMap<>(Team.class);
  private Map<MapLocation, Integer> cheeseLocations = new HashMap<>();
  private boolean[][] walls;

  public MockGameState(int width, int height) {
    this.width = width;
    this.height = height;
    this.walls = new boolean[width][height];
    globalCheese.put(Team.A, 0);
    globalCheese.put(Team.B, 0);
  }

  public MockRobotController addRobot(MapLocation loc, Direction facing, UnitType type, Team team) {
    MockRobotController rc = new MockRobotController(loc, facing, type, team, this);
    robots.add(rc);
    return rc;
  }

  public void addCheese(MapLocation loc, int amount) {
    cheeseLocations.put(loc, amount);
  }

  public void addGlobalCheese(Team team, int amount) {
    globalCheese.put(team, globalCheese.get(team) + amount);
  }

  public int getGlobalCheese(Team team) {
    return globalCheese.get(team);
  }

  public void spendGlobalCheese(Team team, int amount) {
    globalCheese.put(team, globalCheese.get(team) - amount);
  }

  public boolean hasCheeseAt(MapLocation loc) {
    return cheeseLocations.containsKey(loc);
  }

  public int getCheeseAt(MapLocation loc) {
    return cheeseLocations.getOrDefault(loc, 0);
  }

  public void removeCheeseAt(MapLocation loc) {
    cheeseLocations.remove(loc);
  }

  public void setWall(int x, int y, boolean isWall) {
    walls[x][y] = isWall;
  }

  public boolean isPassable(MapLocation loc) {
    return !walls[loc.x][loc.y];
  }

  public void stepRound() {
    round++;
    for (MockRobotController rc : robots) {
      rc.stepRound();
    }
  }

  public int getRound() {
    return round;
  }

  public List<MockRobotController> getAllRobots() {
    return robots;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }
}
