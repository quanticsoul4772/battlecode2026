package mock;

import battlecode.common.*;

public class MockRobotController implements RobotController {
  private MapLocation location;
  private Direction facing;
  private UnitType type;
  private Team team;
  private MockGameState gameState;
  private int health;
  private int rawCheese = 0;
  private int movementCooldown = 0;
  private int actionCooldown = 0;
  private int id;
  private static int nextId = 1000;
  private int[] sharedArray = new int[64];

  public MockRobotController(
      MapLocation loc, Direction facing, UnitType type, Team team, MockGameState game) {
    this.location = loc;
    this.facing = facing;
    this.type = type;
    this.team = team;
    this.gameState = game;
    this.id = nextId++;
    this.health = (type == UnitType.RAT_KING) ? 500 : 100;
  }

  public MapLocation getLocation() {
    return location;
  }

  public Direction getDirection() {
    return facing;
  }

  public UnitType getType() {
    return type;
  }

  public Team getTeam() {
    return team;
  }

  public int getHealth() {
    return health;
  }

  public int getRawCheese() {
    return rawCheese;
  }

  public int getGlobalCheese() {
    return gameState.getGlobalCheese(team);
  }

  public int getRoundNum() {
    return gameState.getRound();
  }

  public int getID() {
    return id;
  }

  public int getMapWidth() {
    return gameState.getWidth();
  }

  public int getMapHeight() {
    return gameState.getHeight();
  }

  public void setHealth(int hp) {
    this.health = hp;
  }

  public boolean canMoveForward() {
    return movementCooldown < 10 && gameState.isPassable(location.add(facing));
  }

  public void moveForward() {
    if (canMoveForward()) {
      location = location.add(facing);
      movementCooldown += 10;
    }
  }

  public boolean canMove(Direction dir) {
    return movementCooldown < 10 && gameState.isPassable(location.add(dir));
  }

  public void move(Direction dir) {
    if (canMove(dir)) {
      location = location.add(dir);
      movementCooldown += (dir == facing) ? 10 : 18;
    }
  }

  public boolean canTurn() {
    return movementCooldown < 10;
  }

  public boolean canTurn(Direction dir) {
    return movementCooldown < 10;
  }

  public void turn(Direction dir) {
    if (canTurn()) {
      facing = dir;
      movementCooldown += 10;
    }
  }

  public boolean canPickUpCheese(MapLocation loc) {
    return actionCooldown < 10 && gameState.hasCheeseAt(loc) && location.equals(loc);
  }

  public void pickUpCheese(MapLocation loc) {
    if (canPickUpCheese(loc)) {
      int amount = gameState.getCheeseAt(loc);
      rawCheese += amount;
      gameState.removeCheeseAt(loc);
      actionCooldown += 10;
    }
  }

  public boolean canTransferCheese(MapLocation kingLoc, int amount) {
    return actionCooldown < 10 && rawCheese >= amount && location.distanceSquaredTo(kingLoc) <= 16;
  }

  public void transferCheese(MapLocation kingLoc, int amount) {
    if (canTransferCheese(kingLoc, amount)) {
      rawCheese -= amount;
      gameState.addGlobalCheese(team, amount);
      actionCooldown += 10;
    }
  }

  public RobotInfo[] senseNearbyRobots() {
    return senseNearbyRobotsImpl(location, -1, null);
  }

  public RobotInfo[] senseNearbyRobots(int radiusSquared) {
    return senseNearbyRobotsImpl(location, radiusSquared, null);
  }

  public RobotInfo[] senseNearbyRobots(int radiusSquared, Team targetTeam) {
    return senseNearbyRobotsImpl(location, radiusSquared, targetTeam);
  }

  public RobotInfo[] senseNearbyRobots(MapLocation center, int radiusSquared, Team targetTeam) {
    return senseNearbyRobotsImpl(center, radiusSquared, targetTeam);
  }

  private RobotInfo[] senseNearbyRobotsImpl(
      MapLocation center, int radiusSquared, Team targetTeam) {
    if (radiusSquared == -1) radiusSquared = 10000;

    java.util.List<RobotInfo> result = new java.util.ArrayList<>();
    for (MockRobotController rc : gameState.getAllRobots()) {
      if (targetTeam != null && rc.team != targetTeam) continue;

      int dist = center.distanceSquaredTo(rc.location);
      if (dist <= radiusSquared && rc != this) {
        result.add(
            new RobotInfo(rc.id, rc.team, rc.type, rc.health, rc.location, rc.facing, 0, 0, null));
      }
    }
    return result.toArray(new RobotInfo[0]);
  }

  public MapInfo senseMapInfo(MapLocation loc) throws GameActionException {
    return new MapInfo(loc, false, false, false, gameState.getCheeseAt(loc), TrapType.NONE, false);
  }

  public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared) {
    java.util.List<MapLocation> locs = new java.util.ArrayList<>();
    int radius = (int) Math.ceil(Math.sqrt(radiusSquared));
    for (int x = center.x - radius; x <= center.x + radius; x++) {
      for (int y = center.y - radius; y <= center.y + radius; y++) {
        if (x >= 0 && x < gameState.getWidth() && y >= 0 && y < gameState.getHeight()) {
          MapLocation loc = new MapLocation(x, y);
          if (center.distanceSquaredTo(loc) <= radiusSquared) {
            locs.add(loc);
          }
        }
      }
    }
    return locs.toArray(new MapLocation[0]);
  }

  public boolean canSenseLocation(MapLocation loc) {
    return true;
  }

  public int readSharedArray(int index) {
    return sharedArray[index];
  }

  public void writeSharedArray(int index, int value) {
    sharedArray[index] = value;
  }

  public void stepRound() {
    movementCooldown = Math.max(0, movementCooldown - 10);
    actionCooldown = Math.max(0, actionCooldown - 10);

    if (type == UnitType.RAT_KING) {
      if (gameState.getGlobalCheese(team) >= 3) {
        gameState.spendGlobalCheese(team, 3);
      } else {
        health -= 10;
      }
    }
  }

  // Stub implementations for RobotController interface
  public boolean isMovementReady() {
    return movementCooldown < 10;
  }

  public boolean isTurningReady() {
    return movementCooldown < 10;
  }

  public boolean isActionReady() {
    return actionCooldown < 10;
  }

  public int getMovementCooldownTurns() {
    return Math.max(0, (movementCooldown - 10 + 9) / 10);
  }

  public int getTurningCooldownTurns() {
    return Math.max(0, (movementCooldown - 10 + 9) / 10);
  }

  public int getActionCooldownTurns() {
    return Math.max(0, (actionCooldown - 10 + 9) / 10);
  }

  public void setIndicatorString(String s) {}

  public void setIndicatorDot(MapLocation loc, int r, int g, int b) {}

  public void setIndicatorLine(MapLocation a, MapLocation b, int r, int g, int blue) {}

  public void setTimelineMarker(String msg, int r, int g, int b) {}

  public RobotInfo senseRobotAtLocation(MapLocation loc) {
    return null;
  }

  public boolean canSenseRobotAtLocation(MapLocation loc) {
    return true;
  }

  public boolean canSenseRobot(int id) {
    return false;
  }

  public RobotInfo senseRobot(int id) {
    return null;
  }

  public boolean onTheMap(MapLocation loc) {
    return loc.x >= 0
        && loc.x < gameState.getWidth()
        && loc.y >= 0
        && loc.y < gameState.getHeight();
  }

  public Message[] readSqueaks() {
    return new Message[0];
  }

  public Message[] readSqueaks(int maxLength) {
    return new Message[0];
  }

  public void squeak(String msg) {}

  public boolean squeak(int data) {
    return false;
  }

  public boolean canAttack(MapLocation target) {
    return false;
  }

  public boolean canAttack(MapLocation target, int cheeseAmount) {
    return false;
  }

  public void attack(MapLocation target) {}

  public void attack(MapLocation target, int cheeseAmount) {}

  public RobotInfo getCarrying() {
    return null;
  }

  public boolean isBeingCarried() {
    return false;
  }

  public boolean canCarryRat(RobotInfo target) {
    return false;
  }

  public void carryRat(RobotInfo target) {}

  public boolean canCarryRat(MapLocation loc) {
    return false;
  }

  public void carryRat(MapLocation loc) {}

  public boolean canThrowRat(MapLocation target) {
    return false;
  }

  public void throwRat(MapLocation target) {}

  public boolean canThrowRat() {
    return false;
  }

  public void throwRat() {}

  public boolean canPlaceTrap(MapLocation loc, TrapType trapType) {
    return false;
  }

  public void placeTrap(MapLocation loc, TrapType trapType) {}

  public boolean canPlaceCatTrap(MapLocation loc) {
    return false;
  }

  public void placeCatTrap(MapLocation loc) {}

  public boolean canRemoveCatTrap(MapLocation loc) {
    return false;
  }

  public void removeCatTrap(MapLocation loc) {}

  public boolean canRemoveRatTrap(MapLocation loc) {
    return false;
  }

  public void removeRatTrap(MapLocation loc) {}

  public boolean canPlaceRatTrap(MapLocation loc) {
    return false;
  }

  public void placeRatTrap(MapLocation loc) {}

  public boolean canRemoveDirt(MapLocation loc) {
    return false;
  }

  public void removeDirt(MapLocation loc) {}

  public boolean canPlaceDirt(MapLocation loc) {
    return false;
  }

  public void placeDirt(MapLocation loc) {}

  public void resign() {}

  public void disintegrate() {}

  public boolean canDropRat(Direction dir) {
    return false;
  }

  public void dropRat(Direction dir) {}

  public boolean canSenseCheeseAmount(MapLocation loc) {
    return true;
  }

  public int senseCheeseAmount(MapLocation loc) {
    return gameState.getCheeseAt(loc);
  }

  public boolean canBecomeRatKing() {
    return false;
  }

  public void becomeRatKing() {}

  public boolean canBuildRat(MapLocation loc) {
    return false;
  }

  public void buildRat(MapLocation loc) {}

  public int getCurrentRatCost() {
    return 10;
  }

  public MapLocation adjacentLocation(Direction dir) {
    return location.add(dir);
  }

  public MapInfo[] senseNearbyMapInfos(MapLocation center, int radiusSquared) {
    return new MapInfo[0];
  }

  public MapInfo[] senseNearbyMapInfos(MapLocation center) {
    return new MapInfo[0];
  }

  public MapInfo[] senseNearbyMapInfos(int radiusSquared) {
    return new MapInfo[0];
  }

  public MapInfo[] senseNearbyMapInfos() {
    return new MapInfo[0];
  }

  public int senseTrapType(MapLocation loc) {
    return 0;
  }

  public boolean sensePassability(MapLocation loc) {
    return gameState.isPassable(loc);
  }

  public int senseDirt(MapLocation loc) {
    return 0;
  }

  public boolean isLocationOccupied(MapLocation loc) {
    return false;
  }

  public boolean isBeingThrown() {
    return false;
  }

  public int getAirTimeTurns() {
    return 0;
  }

  public int getDirt() {
    return 0;
  }

  public int getTotalCheeseTransferred() {
    return 0;
  }

  public int getAllCheese() {
    return rawCheese + gameState.getGlobalCheese(team);
  }

  public MapLocation[] getAllPartLocations() {
    return new MapLocation[] {location};
  }

  public TrapType senseTrap(MapLocation loc) {
    return null;
  }

  public boolean isCooperation() {
    return true;
  }
}
