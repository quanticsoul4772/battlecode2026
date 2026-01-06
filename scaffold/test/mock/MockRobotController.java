package mock;

import battlecode.common.*;

public class MockRobotController {
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

    public MockRobotController(MapLocation loc, Direction facing, UnitType type, Team team, MockGameState game) {
        this.location = loc;
        this.facing = facing;
        this.type = type;
        this.team = team;
        this.gameState = game;
        this.id = nextId++;
        this.health = (type == UnitType.RAT_KING) ? 500 : 100;
    }

    public MapLocation getLocation() { return location; }
    public Direction getDirection() { return facing; }
    public UnitType getType() { return type; }
    public Team getTeam() { return team; }
    public int getHealth() { return health; }
    public int getRawCheese() { return rawCheese; }
    public int getGlobalCheese() { return gameState.getGlobalCheese(team); }
    public int getRoundNum() { return gameState.getRound(); }
    public int getID() { return id; }
    public int getMapWidth() { return gameState.getWidth(); }
    public int getMapHeight() { return gameState.getHeight(); }

    public void setHealth(int hp) { this.health = hp; }

    public boolean canMoveForward() {
        return movementCooldown < 10 && gameState.isPassable(location.add(facing));
    }

    public void moveForward() {
        if (canMoveForward()) {
            location = location.add(facing);
            movementCooldown += 10;
        }
    }

    public boolean canTurn() {
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

    public RobotInfo[] senseNearbyRobots(int radiusSquared, Team targetTeam) {
        if (radiusSquared == -1) radiusSquared = 10000;

        java.util.List<RobotInfo> result = new java.util.ArrayList<>();
        for (MockRobotController rc : gameState.getAllRobots()) {
            if (targetTeam != null && rc.team != targetTeam) continue;

            int dist = location.distanceSquaredTo(rc.location);
            if (dist <= radiusSquared && rc != this) {
                result.add(new RobotInfo(rc.id, rc.team, rc.type, rc.health, rc.location, rc.facing, 0, 0, null));
            }
        }
        return result.toArray(new RobotInfo[0]);
    }

    public MapInfo senseMapInfo(MapLocation loc) throws GameActionException {
        return new MapInfo(loc, false, false, false, gameState.getCheeseAt(loc), null, false);
    }

    public MapLocation[] getAllLocationsWithinRadiusSquared(MapLocation center, int radiusSquared) {
        java.util.List<MapLocation> locs = new java.util.ArrayList<>();
        int radius = (int)Math.ceil(Math.sqrt(radiusSquared));
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
}
