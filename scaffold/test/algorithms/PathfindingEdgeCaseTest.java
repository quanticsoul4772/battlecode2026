package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import ratbot.algorithms.Pathfinding;

public class PathfindingEdgeCaseTest {

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
    public void testBFS_SameLocation() {
        boolean[][] passable = createOpenMap(30, 30);
        MapLocation start = new MapLocation(15, 15);

        Direction dir = Pathfinding.bfs(start, start, passable, 30, 30);
        assertEquals(Direction.CENTER, dir);
    }

    @Test
    public void testBFS_DiagonalPath() {
        boolean[][] passable = createOpenMap(30, 30);
        MapLocation start = new MapLocation(10, 10);
        MapLocation target = new MapLocation(15, 15);

        Direction dir = Pathfinding.bfs(start, target, passable, 30, 30);
        assertNotNull(dir);
        assertNotEquals(Direction.CENTER, dir);
    }

    @Test
    public void testBFS_WithObstacle() {
        boolean[][] passable = createOpenMap(30, 30);
        passable[10][12] = false;

        MapLocation start = new MapLocation(10, 10);
        MapLocation target = new MapLocation(10, 15);

        Direction dir = Pathfinding.bfs(start, target, passable, 30, 30);
        assertNotNull(dir);
    }

    @Test
    public void testGreedy_North() {
        MapLocation current = new MapLocation(10, 10);
        MapLocation target = new MapLocation(10, 20);
        assertEquals(Direction.NORTH, Pathfinding.greedy(current, target));
    }

    @Test
    public void testGreedy_Diagonal() {
        MapLocation current = new MapLocation(10, 10);
        MapLocation target = new MapLocation(15, 15);
        assertEquals(Direction.NORTHEAST, Pathfinding.greedy(current, target));
    }

    @Test
    public void testBug2_DirectPath() {
        MapLocation current = new MapLocation(10, 10);
        MapLocation target = new MapLocation(10, 15);

        Direction dir = Pathfinding.bug2(current, target, (d) -> true);
        assertEquals(Direction.NORTH, dir);
    }

    @Test
    public void testBug2_NewTarget() {
        MapLocation target1 = new MapLocation(10, 15);
        Pathfinding.bug2(new MapLocation(10, 10), target1, (d) -> true);

        MapLocation target2 = new MapLocation(20, 20);
        Direction dir = Pathfinding.bug2(new MapLocation(10, 10), target2, (d) -> true);

        assertNotEquals(Direction.CENTER, dir);
    }
}
