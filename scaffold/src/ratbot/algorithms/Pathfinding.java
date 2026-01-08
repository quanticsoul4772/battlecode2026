package ratbot.algorithms;

import battlecode.common.*;

/**
 * Pathfinding algorithms for Battlecode 2026.
 *
 * <p>Supports: - BFS (breadth-first search) for shortest path - Directional Bug2 for obstacle
 * avoidance - A* for weighted pathfinding (future)
 *
 * <p>Optimized for bytecode efficiency. Standalone module - integrates into any scaffold.
 */
public class Pathfinding {

  // BFS queue (reuse to avoid allocation)
  private static int[] queueX = new int[3600]; // 60x60 max
  private static int[] queueY = new int[3600];
  private static boolean[][] visited = new boolean[60][60];
  private static Direction[][] parent = new Direction[60][60];

  /**
   * BFS to find shortest path to target. Returns first direction to take, or CENTER if unreachable.
   *
   * @param start Starting location
   * @param target Target location
   * @param passable Passability map (true = can move)
   * @param mapWidth Map width
   * @param mapHeight Map height
   * @return First direction on shortest path, or CENTER if no path
   */
  public static Direction bfs(
      MapLocation start, MapLocation target, boolean[][] passable, int mapWidth, int mapHeight) {
    if (start.equals(target)) return Direction.CENTER;

    // Reset visited array
    for (int x = mapWidth; --x >= 0; ) {
      for (int y = mapHeight; --y >= 0; ) {
        visited[x][y] = false;
        parent[x][y] = Direction.CENTER;
      }
    }

    // BFS queue
    int head = 0, tail = 0;
    queueX[tail] = start.x;
    queueY[tail] = start.y;
    tail++;
    visited[start.x][start.y] = true;

    // 8 directions
    int[] dx = {0, 1, 1, 1, 0, -1, -1, -1};
    int[] dy = {1, 1, 0, -1, -1, -1, 0, 1};
    Direction[] dirs = ALL_DIRECTIONS;

    // BFS
    while (head < tail) {
      int x = queueX[head];
      int y = queueY[head];
      head++;

      // Check all 8 neighbors
      for (int i = 8; --i >= 0; ) {
        int nx = x + dx[i];
        int ny = y + dy[i];

        // Bounds check
        if (nx < 0 || nx >= mapWidth || ny < 0 || ny >= mapHeight) continue;

        // Already visited or impassable
        if (visited[nx][ny] || !passable[nx][ny]) continue;

        // Mark visited and record parent direction
        visited[nx][ny] = true;
        parent[nx][ny] = dirs[i];

        // Add to queue
        queueX[tail] = nx;
        queueY[tail] = ny;
        tail++;

        // Found target?
        if (nx == target.x && ny == target.y) {
          // Backtrack to find first step
          return backtrackFirstStep(start, target);
        }
      }
    }

    // No path found
    return Direction.CENTER;
  }

  /**
   * Backtrack from target to start using parent array. Returns the first direction to take from
   * start.
   */
  private static Direction backtrackFirstStep(MapLocation start, MapLocation target) {
    int x = target.x;
    int y = target.y;

    Direction lastDir = Direction.CENTER;

    // Backtrack until we reach start
    while (true) {
      Direction dir = parent[x][y];
      if (dir == Direction.CENTER) break; // Reached start

      lastDir = dir;

      // Move backwards along parent chain
      Direction reverse = opposite(dir);
      x += deltaX(reverse);
      y += deltaY(reverse);

      if (x == start.x && y == start.y) break;
    }

    return lastDir;
  }

  /**
   * Directional Bug2 pathfinding. Handles obstacles with tracing behavior, respects facing
   * direction.
   *
   * <p>Simpler than BFS, lower bytecode, good for real-time pathfinding.
   */
  private static MapLocation bugTarget = null;

  private static boolean bugTracing = false;
  private static Direction bugTracingDir = Direction.NORTH;
  private static MapLocation bugStartLoc = null;
  private static int bugStartDist = 0;
  private static boolean bugRotateRight = false;
  private static int bugTurns = 0;

  /**
   * Bug2 algorithm for pathfinding with obstacles.
   *
   * @param current Current location
   * @param target Target location
   * @param canMove Function to check if can move in direction
   * @return Direction to move
   */
  public static Direction bug2(MapLocation current, MapLocation target, CanMoveFunction canMove) {
    // Reset if target changed
    if (bugTarget == null || !bugTarget.equals(target)) {
      bugTarget = target;
      bugTracing = false;
    }

    Direction targetDir = current.directionTo(target);

    // If not tracing, try direct path
    if (!bugTracing) {
      if (canMove.canMove(targetDir)) {
        return targetDir;
      }

      // Start tracing obstacle
      bugTracing = true;
      bugStartLoc = current;
      bugStartDist = current.distanceSquaredTo(target);
      bugTracingDir = targetDir;
      // Use target location hash for deterministic but varied direction choice
      bugRotateRight = ((target.x * 31 + target.y) & 1) == 0;
      bugTurns = 0;
    }

    // Tracing - follow obstacle
    if (bugTracing) {
      // Check if can leave trace (closer than start)
      int curDist = current.distanceSquaredTo(target);
      if (curDist < bugStartDist && canMove.canMove(targetDir)) {
        bugTracing = false;
        return targetDir;
      }

      // Timeout after 20 turns
      bugTurns++;
      if (bugTurns > 20) {
        bugTracing = false;
        return targetDir;
      }

      // Rotate around obstacle
      Direction dir = bugTracingDir;
      for (int i = 8; --i >= 0; ) {
        if (canMove.canMove(dir)) {
          bugTracingDir = bugRotateRight ? rotateLeft(dir) : rotateRight(dir);
          return dir;
        }
        dir = bugRotateRight ? rotateRight(dir) : rotateLeft(dir);
      }
    }

    return targetDir;
  }

  /**
   * Functional interface for movement checking. Allows bug2 to work with any passability function.
   */
  public interface CanMoveFunction {
    boolean canMove(Direction dir);
  }

  /**
   * Simple greedy pathfinding (move toward target). Cheapest bytecode, but can get stuck.
   *
   * @param current Current location
   * @param target Target location
   * @return Direction toward target
   */
  public static Direction greedy(MapLocation current, MapLocation target) {
    return current.directionTo(target);
  }

  /** Get delta X for direction. */
  private static int deltaX(Direction dir) {
    switch (dir) {
      case EAST:
      case NORTHEAST:
      case SOUTHEAST:
        return 1;
      case WEST:
      case NORTHWEST:
      case SOUTHWEST:
        return -1;
      default:
        return 0;
    }
  }

  /** Get delta Y for direction. */
  private static int deltaY(Direction dir) {
    switch (dir) {
      case NORTH:
      case NORTHEAST:
      case NORTHWEST:
        return 1;
      case SOUTH:
      case SOUTHEAST:
      case SOUTHWEST:
        return -1;
      default:
        return 0;
    }
  }

  /** Get opposite direction. */
  private static Direction opposite(Direction dir) {
    return ALL_DIRECTIONS[(dir.ordinal() + 4) % 8];
  }

  /** Rotate direction clockwise. */
  private static Direction rotateRight(Direction dir) {
    return ALL_DIRECTIONS[(dir.ordinal() + 1) % 8];
  }

  /** Rotate direction counter-clockwise. */
  private static Direction rotateLeft(Direction dir) {
    int ord = dir.ordinal() - 1;
    if (ord < 0) ord = 7;
    return ALL_DIRECTIONS[ord];
  }

  // Pre-computed directions
  private static final Direction[] ALL_DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
  };
}
