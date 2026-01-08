package ratbot2.utils;

import battlecode.common.*;

/**
 * Direction utilities for Battlecode 2026.
 *
 * <p>Critical for 2026: Facing direction affects: - Vision (cone centered on facing) - Movement
 * cost (forward cheaper than strafing) - Combat effectiveness (flanking bonus)
 *
 * <p>Standalone module for pre-scaffold development.
 */
public class DirectionUtil {

  // Pre-computed direction array for efficiency
  public static final Direction[] ALL_DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
  };

  public static final Direction[] CARDINAL_DIRECTIONS = {
    Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
  };

  /**
   * Compute minimum number of turns to face target direction. Each turn can rotate up to 90° (±2
   * direction steps).
   *
   * @param current Current facing direction
   * @param target Desired facing direction
   * @return Number of turns needed (0, 1, or 2)
   */
  public static int turnsToFace(Direction current, Direction target) {
    if (current == target) return 0;

    int currentOrd = current.ordinal();
    int targetOrd = target.ordinal();

    // Angular difference (handle wrap-around)
    int diff = Math.abs(currentOrd - targetOrd);
    if (diff > 4) diff = 8 - diff;

    // 1 turn = ±2 steps (90°), 2 turns = 3-4 steps (135°-180°)
    if (diff <= 2) return 1;
    return 2;
  }

  /**
   * Get direction after turning specified amount. Positive = clockwise, negative =
   * counter-clockwise.
   *
   * @param from Starting direction
   * @param steps Number of 45° steps (positive = CW, negative = CCW)
   * @return New direction after turn
   */
  public static Direction turn(Direction from, int steps) {
    int ord = from.ordinal();
    int newOrd = (ord + steps) % 8;
    if (newOrd < 0) newOrd += 8; // Handle negative modulo
    return ALL_DIRECTIONS[newOrd];
  }

  /**
   * Get optimal facing direction to see both targets. Useful for multi-target awareness.
   *
   * @param observer Observer location
   * @param target1 First target
   * @param target2 Second target
   * @return Direction that maximizes visibility of both
   */
  public static Direction optimalFacingForTwo(
      MapLocation observer, MapLocation target1, MapLocation target2) {
    Direction d1 = observer.directionTo(target1);
    Direction d2 = observer.directionTo(target2);

    // If same or adjacent, either works
    int diff = Math.abs(d1.ordinal() - d2.ordinal());
    if (diff <= 1 || diff >= 7) return d1;

    // Face between them (average direction)
    int avgOrd = (d1.ordinal() + d2.ordinal()) / 2;
    return ALL_DIRECTIONS[avgOrd % 8];
  }

  /**
   * Get direction rotated clockwise.
   *
   * @param dir Direction to rotate
   * @return Direction rotated 45° clockwise
   */
  public static Direction rotateRight(Direction dir) {
    return ALL_DIRECTIONS[(dir.ordinal() + 1) % 8];
  }

  /**
   * Get direction rotated counter-clockwise.
   *
   * @param dir Direction to rotate
   * @return Direction rotated 45° counter-clockwise
   */
  public static Direction rotateLeft(Direction dir) {
    int ord = dir.ordinal() - 1;
    if (ord < 0) ord = 7;
    return ALL_DIRECTIONS[ord];
  }

  /**
   * Get opposite direction (180°).
   *
   * @param dir Direction to reverse
   * @return Opposite direction
   */
  public static Direction opposite(Direction dir) {
    return ALL_DIRECTIONS[(dir.ordinal() + 4) % 8];
  }

  /**
   * Check if two directions are adjacent (45° apart).
   *
   * @param a First direction
   * @param b Second direction
   * @return true if adjacent
   */
  public static boolean isAdjacent(Direction a, Direction b) {
    int diff = Math.abs(a.ordinal() - b.ordinal());
    return diff == 1 || diff == 7;
  }

  /**
   * Compute direction from vector components. Uses switch for O(1) lookup.
   *
   * @param dx Delta X
   * @param dy Delta Y
   * @return Direction closest to vector, or CENTER if (0,0)
   */
  public static Direction fromVector(int dx, int dy) {
    if (dx == 0 && dy == 0) return Direction.CENTER;

    // Normalize to -1, 0, 1
    int sx = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
    int sy = dy > 0 ? 1 : (dy < 0 ? -1 : 0);

    // Combined lookup (efficient)
    int key = (sx + 1) * 3 + (sy + 1);
    switch (key) {
      case 0:
        return Direction.SOUTHWEST;
      case 1:
        return Direction.WEST;
      case 2:
        return Direction.NORTHWEST;
      case 3:
        return Direction.SOUTH;
      case 4:
        return Direction.CENTER;
      case 5:
        return Direction.NORTH;
      case 6:
        return Direction.SOUTHEAST;
      case 7:
        return Direction.EAST;
      case 8:
        return Direction.NORTHEAST;
      default:
        return Direction.CENTER;
    }
  }

  // Static buffer for direction ordering (reuse to avoid allocation)
  private static Direction[] orderedBuffer = new Direction[8];

  /**
   * Get all 8 adjacent directions in optimal order (zero-allocation). Order: specified direction
   * first, then spiral outward.
   *
   * <p>WARNING: Returns shared static buffer. Do not modify or store reference. Buffer contents are
   * overwritten on next call.
   *
   * @param preferred Preferred direction to start with
   * @return Array of 8 directions in preference order
   */
  public static Direction[] orderedDirections(Direction preferred) {
    int idx = 0;

    // Start with preferred
    orderedBuffer[idx++] = preferred;

    // Then adjacent directions
    orderedBuffer[idx++] = rotateLeft(preferred);
    orderedBuffer[idx++] = rotateRight(preferred);

    // Then 90° turns
    orderedBuffer[idx++] = turn(preferred, 2);
    orderedBuffer[idx++] = turn(preferred, -2);

    // Then backwards directions
    orderedBuffer[idx++] = turn(preferred, 3);
    orderedBuffer[idx++] = turn(preferred, -3);

    // Finally opposite
    orderedBuffer[idx++] = opposite(preferred);

    return orderedBuffer;
  }

  /**
   * Compute optimal movement considering facing direction. Forward movement is cheaper than
   * strafing.
   *
   * @param currentFacing Current facing direction
   * @param desiredDirection Where we want to move
   * @return Movement plan: direction to turn to, then move forward
   */
  public static MovementPlan optimalMovement(Direction currentFacing, Direction desiredDirection) {
    int turnCost = turnsToFace(currentFacing, desiredDirection);

    // If already facing right way or close, just move
    if (turnCost == 0) {
      return new MovementPlan(currentFacing, false);
    }

    // If 1 turn away, turn then move
    if (turnCost == 1) {
      return new MovementPlan(desiredDirection, true);
    }

    // If 2 turns (opposite direction), check if strafing is better
    // Strafe has extra cooldown but no turn cost
    // For now: prefer turning (more general)
    return new MovementPlan(desiredDirection, true);
  }

  /** Simple movement plan: turn to direction, then move. */
  public static class MovementPlan {
    public final Direction targetFacing;
    public final boolean needsTurn;

    public MovementPlan(Direction targetFacing, boolean needsTurn) {
      this.targetFacing = targetFacing;
      this.needsTurn = needsTurn;
    }
  }

  /**
   * Check if direction is diagonal.
   *
   * @param dir Direction to check
   * @return true if NE, SE, SW, or NW
   */
  public static boolean isDiagonal(Direction dir) {
    int ord = dir.ordinal();
    return ord == 1 || ord == 3 || ord == 5 || ord == 7;
  }

  /**
   * Check if direction is cardinal.
   *
   * @param dir Direction to check
   * @return true if N, E, S, or W
   */
  public static boolean isCardinal(Direction dir) {
    int ord = dir.ordinal();
    return ord == 0 || ord == 2 || ord == 4 || ord == 6;
  }
}
