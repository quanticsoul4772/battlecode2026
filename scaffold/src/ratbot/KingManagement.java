package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;

/**
 * King redundancy and spatial distribution management.
 *
 * <p>Prevents multi-king losses from: - Clustered kings (single cat pounce kills multiple) - Lack
 * of spatial redundancy - Uncoordinated king formation
 *
 * <p>Implements Nygard's redundancy pattern for high-availability.
 */
public class KingManagement {

  // Minimum spacing between kings (prevent multi-kill)
  private static final int KING_SPACING = 15;
  private static final int KING_SPACING_SQUARED = KING_SPACING * KING_SPACING; // 225

  /**
   * Check if safe to form new king at this location. Validates spatial distribution to prevent
   * clustering.
   *
   * @param rc Robot controller
   * @param candidateLoc Proposed king formation location
   * @return true if location provides good redundancy
   */
  public static boolean isSafeKingLocation(RobotController rc, MapLocation candidateLoc)
      throws GameActionException {

    // Find all existing ally kings
    RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getType() == UnitType.RAT_KING) {
        MapLocation kingLoc = allies[i].getLocation();

        // Check distance
        int distSquared = candidateLoc.distanceSquaredTo(kingLoc);

        if (distSquared < KING_SPACING_SQUARED) {
          // Too close - risk of multi-king cat pounce
          return false;
        }
      }
    }

    // No nearby kings - safe location
    return true;
  }

  /**
   * Evaluate if we should form additional king. Considers: - Current king count - Cheese economy
   * (can we sustain another king?) - Map coverage (do we need more spawn points?)
   *
   * @param rc Robot controller
   * @return true if should consider king formation
   */
  public static boolean shouldFormAdditionalKing(RobotController rc) throws GameActionException {

    int globalCheese = rc.getGlobalCheese();
    RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());

    // Count existing kings
    int kingCount = 0;
    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getType() == UnitType.RAT_KING) {
        kingCount++;
      }
    }

    // Max 5 kings (game limit)
    if (kingCount >= 5) {
      return false;
    }

    // Economic check: Can we sustain another king?
    // Each king consumes 3 cheese/round
    int currentConsumption = kingCount * 3;
    int newConsumption = (kingCount + 1) * 3;
    int additionalCost = newConsumption - currentConsumption; // +3 per round

    // Need 500 rounds buffer for new king = 1,500 cheese minimum
    int cheeseRequired = 1500;

    if (globalCheese < cheeseRequired) {
      return false; // Cannot afford
    }

    // Strategic check: Do we need more kings?
    // 1 king: Baseline (always have)
    // 2 kings: 2x spawn rate, 2x coverage
    // 3 kings: 3x spawn rate, 3x coverage
    // 4-5 kings: High consumption, only if cheese income very high

    // Estimate cheese income based on current economy
    // This is placeholder - should track actual income rate
    int estimatedIncome = 10; // cheese/round (rough estimate)

    // Can we sustain new king with positive income?
    boolean sustainable = estimatedIncome > newConsumption;

    if (!sustainable) {
      return false;
    }

    // Recommended king counts:
    // - Early game (round < 500): 1-2 kings
    // - Mid game (500-1500): 2-3 kings
    // - Late game (1500+): 2-3 kings (efficiency over spawning)

    int round = rc.getRoundNum();

    if (round < 500 && kingCount >= 2) {
      return false; // Enough for early game
    }

    if (round >= 500 && kingCount >= 3) {
      return false; // Optimal for mid-late game
    }

    return true; // Form king if location safe
  }

  /**
   * Find optimal location for new king formation. Maximizes spacing from existing kings and cheese
   * mine coverage.
   *
   * @param rc Robot controller
   * @return Recommended king location, or null if none suitable
   */
  public static MapLocation findOptimalKingLocation(RobotController rc) throws GameActionException {

    MapLocation currentLoc = rc.getLocation();

    // Get all existing king locations
    RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
    MapLocation[] kingLocations = new MapLocation[5]; // Max 5 kings
    int kingCount = 0;

    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getType() == UnitType.RAT_KING) {
        kingLocations[kingCount++] = allies[i].getLocation();
      }
    }

    // Evaluate current location
    boolean currentLocationSafe = true;

    for (int i = kingCount; --i >= 0; ) {
      int distSquared = currentLoc.distanceSquaredTo(kingLocations[i]);
      if (distSquared < KING_SPACING_SQUARED) {
        currentLocationSafe = false;
        break;
      }
    }

    if (currentLocationSafe) {
      return currentLoc; // Current location is good
    }

    // TODO: Implement search for optimal location
    // For now, return null if current location unsafe
    return null;
  }

  /**
   * Calculate optimal number of kings for current game state. Based on cheese income, map size, and
   * game phase.
   *
   * @param globalCheese Current global cheese
   * @param cheeseIncome Estimated cheese income per round
   * @param round Current round number
   * @param mapWidth Map width
   * @param mapHeight Map height
   * @return Recommended king count
   */
  public static int optimalKingCount(
      int globalCheese, int cheeseIncome, int round, int mapWidth, int mapHeight) {
    // King costs: 3 cheese/round consumption

    // Economic constraint
    int maxSustainableKings = cheeseIncome / 3;

    // Map size consideration (larger maps benefit from more spawn points)
    int mapArea = mapWidth * mapHeight;
    int recommendedByMap = mapArea < 900 ? 1 : (mapArea < 1600 ? 2 : 3);

    // Game phase consideration
    int recommendedByPhase;
    if (round < 500) {
      recommendedByPhase = 2; // Build army early
    } else if (round < 1500) {
      recommendedByPhase = 3; // Peak production
    } else {
      recommendedByPhase = 2; // Efficiency over growth
    }

    // Take minimum of constraints
    int optimal = Math.min(maxSustainableKings, recommendedByMap);
    optimal = Math.min(optimal, recommendedByPhase);
    optimal = Math.max(1, optimal); // At least 1 king
    optimal = Math.min(5, optimal); // Game maximum

    return optimal;
  }

  /**
   * Check if king should retreat due to health threat.
   *
   * @param rc Robot controller
   * @return true if king should retreat
   */
  public static boolean shouldKingRetreat(RobotController rc) throws GameActionException {
    int health = rc.getHealth();
    int maxHealth = 500; // King max HP

    // Retreat if below 40% health
    if (health < (maxHealth * 0.4)) {
      // Check for nearby threats
      RobotInfo[] enemies = rc.senseNearbyRobots(25, rc.getTeam().opponent());

      for (int i = enemies.length; --i >= 0; ) {
        if (enemies[i].getType() == UnitType.CAT) {
          return true; // Cat nearby + low health = retreat
        }
      }
    }

    return false;
  }

  /**
   * Get safe retreat direction away from threats.
   *
   * @param rc Robot controller
   * @return Direction to retreat, or null if no threats
   */
  public static Direction getRetreatDirection(RobotController rc) throws GameActionException {
    RobotInfo[] enemies = rc.senseNearbyRobots(25, rc.getTeam().opponent());

    MapLocation me = rc.getLocation();
    int sumDx = 0;
    int sumDy = 0;

    // Calculate average threat direction
    for (int i = enemies.length; --i >= 0; ) {
      MapLocation threat = enemies[i].getLocation();
      sumDx += (me.x - threat.x);
      sumDy += (me.y - threat.y);
    }

    if (sumDx == 0 && sumDy == 0) {
      return null; // No clear retreat direction
    }

    // Direction away from threats
    return DirectionUtil.fromVector(sumDx, sumDy);
  }
}
