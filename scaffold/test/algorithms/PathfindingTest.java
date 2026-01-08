package algorithms;

import static org.junit.Assert.*;

import battlecode.common.*;
import org.junit.Test;
import ratbot.algorithms.Pathfinding;

public class PathfindingTest {

  private boolean[][] createOpenMap(int w, int h) {
    boolean[][] map = new boolean[w][h];
    for (int x = 0; x < w; x++) {
      for (int y = 0; y < h; y++) {
        map[x][y] = true;
      }
    }
    return map;
  }

  @Test
  public void testBFS_DirectPath() {
    boolean[][] passable = createOpenMap(30, 30);
    MapLocation start = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 15);

    Direction dir = Pathfinding.bfs(start, target, passable, 30, 30);

    assertNotNull("BFS should find path", dir);
    assertNotEquals("Should not be CENTER", Direction.CENTER, dir);
  }

  @Test
  public void testBFS_AtTarget() {
    boolean[][] passable = createOpenMap(30, 30);
    MapLocation start = new MapLocation(10, 10);

    Direction dir = Pathfinding.bfs(start, start, passable, 30, 30);

    assertEquals(Direction.CENTER, dir);
  }

  @Test
  public void testGreedy() {
    MapLocation current = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 15);

    Direction dir = Pathfinding.greedy(current, target);

    assertEquals(Direction.NORTH, dir);
  }
}
