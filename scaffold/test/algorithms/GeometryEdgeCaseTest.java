package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import ratbot.algorithms.Geometry;

public class GeometryEdgeCaseTest {

    @Test
    public void testClosest_EmptyArray() {
        MapLocation ref = new MapLocation(0, 0);
        MapLocation[] locs = {};
        assertNull(Geometry.closest(ref, locs));
    }

    @Test
    public void testFarthest_EmptyArray() {
        MapLocation ref = new MapLocation(0, 0);
        MapLocation[] locs = {};
        assertNull(Geometry.farthest(ref, locs));
    }

    @Test
    public void testDistanceSquared() {
        MapLocation a = new MapLocation(0, 0);
        MapLocation b = new MapLocation(3, 4);
        assertEquals(25, Geometry.distanceSquared(a, b));
    }

    @Test
    public void testChebyshevDistance() {
        MapLocation a = new MapLocation(0, 0);
        MapLocation b = new MapLocation(3, 4);
        assertEquals(4, Geometry.chebyshevDistance(a, b));
    }

    @Test
    public void testApproximateDistance() {
        int approx = Geometry.approximateDistance(3, 4);
        assertTrue(approx >= 4 && approx <= 6);
    }

    @Test
    public void testWithinRange_Boundary() {
        MapLocation center = new MapLocation(10, 10);
        MapLocation target = new MapLocation(10, 14);
        assertTrue(Geometry.withinRange(center, target, 16));
        assertFalse(Geometry.withinRange(center, target, 15));
    }

    @Test
    public void testIsLineOfSightClear_DirectPath() {
        boolean[][] passable = new boolean[30][30];
        for (int x = 0; x < 30; x++) {
            for (int y = 0; y < 30; y++) {
                passable[x][y] = true;
            }
        }

        assertTrue(Geometry.isLineOfSightClear(
            new MapLocation(10, 10),
            new MapLocation(15, 15),
            passable
        ));
    }

    @Test
    public void testLocationsWithinRadiusCount() {
        int count = Geometry.locationsWithinRadiusCount(
            new MapLocation(15, 15), 20, 30, 30
        );
        assertTrue(count > 0);
    }
}
