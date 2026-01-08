package ratbot2.utils;

import battlecode.common.*;

/**
 * Vision cone calculations for Battlecode 2026.
 *
 * <p>Units have directional vision: - Baby Rat: 90° cone, sqrt(20) radius - Cat: 180° cone,
 * sqrt(30) radius - Rat King: 360° (omnidirectional), sqrt(25) radius
 *
 * <p>Standalone module - can integrate into any scaffold.
 */
public class Vision {

  // Static buffer for visible tiles (max ~400 tiles for 20x20 area)
  private static MapLocation[] visibleBuffer = new MapLocation[400];

  /**
   * Check if target is in 90° vision cone. Used by Baby Rats.
   *
   * @param observer Observer location
   * @param facing Direction observer is facing
   * @param target Target location to check
   * @return true if target is in 90° cone centered on facing direction
   */
  public static boolean inCone90(MapLocation observer, Direction facing, MapLocation target) {
    // Get direction from observer to target
    Direction toTarget = observer.directionTo(target);

    // 90° cone = facing direction ± 45° = facing ± 1 direction step
    // Directions are: N(0), NE(1), E(2), SE(3), S(4), SW(5), W(6), NW(7)
    int facingOrd = facing.ordinal();
    int targetOrd = toTarget.ordinal();

    // Compute angular difference (handle wrap-around)
    int diff = Math.abs(facingOrd - targetOrd);
    if (diff > 4) diff = 8 - diff; // Shortest angular distance

    // In 90° cone if within 1 direction step (±45°)
    return diff <= 1;
  }

  /**
   * Check if target is in 180° vision cone. Used by Cats.
   *
   * @param observer Observer location
   * @param facing Direction observer is facing
   * @param target Target location to check
   * @return true if target is in 180° cone (front half)
   */
  public static boolean inCone180(MapLocation observer, Direction facing, MapLocation target) {
    Direction toTarget = observer.directionTo(target);

    int facingOrd = facing.ordinal();
    int targetOrd = toTarget.ordinal();

    int diff = Math.abs(facingOrd - targetOrd);
    if (diff > 4) diff = 8 - diff;

    // 180° cone = ±90° = ±2 direction steps
    return diff <= 2;
  }

  /**
   * Check if target is visible with range check.
   *
   * @param observer Observer location
   * @param facing Direction observer is facing
   * @param target Target location
   * @param radiusSquared Vision radius squared
   * @param coneAngle Cone angle (90, 180, or 360)
   * @return true if target is in vision range and cone
   */
  public static boolean isVisible(
      MapLocation observer,
      Direction facing,
      MapLocation target,
      int radiusSquared,
      int coneAngle) {
    // Check range first (cheaper)
    int distSq = observer.distanceSquaredTo(target);
    if (distSq > radiusSquared) return false;

    // Check cone
    if (coneAngle == 360) return true; // Omnidirectional (Rat King)
    if (coneAngle == 180) return inCone180(observer, facing, target);
    if (coneAngle == 90) return inCone90(observer, facing, target);

    return false;
  }

  /**
   * Get visible tiles into provided buffer (bytecode-optimized).
   *
   * @param buffer Buffer to store visible tiles
   * @param center Observer location
   * @param facing Direction observer is facing
   * @param radiusSquared Vision radius squared
   * @param coneAngle Cone angle (90, 180, or 360)
   * @param mapWidth Map width
   * @param mapHeight Map height
   * @return Count of visible tiles stored in buffer
   */
  public static int getVisibleTilesIntoBuffer(
      MapLocation[] buffer,
      MapLocation center,
      Direction facing,
      int radiusSquared,
      int coneAngle,
      int mapWidth,
      int mapHeight) {
    int count = 0;

    // Bounding box around vision radius
    int radius = (int) Math.ceil(Math.sqrt(radiusSquared));
    int minX = Math.max(0, center.x - radius);
    int maxX = Math.min(mapWidth - 1, center.x + radius);
    int minY = Math.max(0, center.y - radius);
    int maxY = Math.min(mapHeight - 1, center.y + radius);

    // Check all tiles in bounding box
    for (int x = minX; x <= maxX; x++) {
      for (int y = minY; y <= maxY; y++) {
        MapLocation loc = new MapLocation(x, y);

        if (isVisible(center, facing, loc, radiusSquared, coneAngle)) {
          buffer[count++] = loc;
        }
      }
    }

    return count;
  }

  /**
   * Get all tiles visible in vision cone (convenience method using static buffer).
   *
   * <p>WARNING: Results stored in shared static buffer. Copy if needed beyond immediate use. For
   * better performance, use getVisibleTilesIntoBuffer() with your own buffer.
   *
   * @param center Observer location
   * @param facing Direction observer is facing
   * @param radiusSquared Vision radius squared
   * @param coneAngle Cone angle (90, 180, or 360)
   * @param mapWidth Map width
   * @param mapHeight Map height
   * @return Count of visible tiles (access via getVisibleTile(index))
   */
  public static int getVisibleTilesCount(
      MapLocation center,
      Direction facing,
      int radiusSquared,
      int coneAngle,
      int mapWidth,
      int mapHeight) {
    return getVisibleTilesIntoBuffer(
        visibleBuffer, center, facing, radiusSquared, coneAngle, mapWidth, mapHeight);
  }

  /**
   * Access visible tile at index from static buffer. Only valid after calling
   * getVisibleTilesCount().
   *
   * @param index Index of tile (0 to count-1)
   * @return MapLocation at index
   */
  public static MapLocation getVisibleTile(int index) {
    return visibleBuffer[index];
  }

  /**
   * Get all tiles visible in vision cone (DEPRECATED - allocates memory). Use
   * getVisibleTilesCount() + getVisibleTile() for better performance.
   *
   * @param center Observer location
   * @param facing Direction observer is facing
   * @param radiusSquared Vision radius squared
   * @param coneAngle Cone angle (90, 180, or 360)
   * @param mapWidth Map width
   * @param mapHeight Map height
   * @return Array of visible locations
   * @deprecated Use getVisibleTilesCount() + getVisibleTile() for zero-allocation
   */
  @Deprecated
  public static MapLocation[] getVisibleTiles(
      MapLocation center,
      Direction facing,
      int radiusSquared,
      int coneAngle,
      int mapWidth,
      int mapHeight) {
    int count = getVisibleTilesCount(center, facing, radiusSquared, coneAngle, mapWidth, mapHeight);

    // Copy from static buffer
    MapLocation[] result = new MapLocation[count];
    System.arraycopy(visibleBuffer, 0, result, 0, count);
    return result;
  }

  /**
   * Compute optimal facing direction to maximize vision overlap with target area.
   *
   * @param observer Observer location
   * @param targetCenter Center of area to observe
   * @return Best direction to face
   */
  public static Direction optimalFacingFor(MapLocation observer, MapLocation targetCenter) {
    return observer.directionTo(targetCenter);
  }

  /**
   * Check if observer can see target given facing direction. Accounts for vision cone and range.
   *
   * @param observer Observer location
   * @param observerFacing Observer's facing direction
   * @param target Target location
   * @param observerType Unit type (determines cone angle and radius)
   * @return true if observer can see target
   */
  public static boolean canSee(
      MapLocation observer, Direction observerFacing, MapLocation target, UnitType observerType) {
    // Get vision parameters based on type
    int radiusSquared;
    int coneAngle;

    if (observerType == UnitType.BABY_RAT) {
      radiusSquared = 20;
      coneAngle = 90;
    } else if (observerType == UnitType.RAT_KING) {
      radiusSquared = 25;
      coneAngle = 360;
    } else if (observerType == UnitType.CAT) {
      radiusSquared = 30;
      coneAngle = 180;
    } else {
      return false;
    }

    return isVisible(observer, observerFacing, target, radiusSquared, coneAngle);
  }
}
