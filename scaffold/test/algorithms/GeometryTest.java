package algorithms;

import static org.junit.Assert.*;

import battlecode.common.*;
import org.junit.Test;
import ratbot.algorithms.Geometry;

public class GeometryTest {

  @Test
  public void testManhattanDistance() {
    MapLocation a = new MapLocation(0, 0);
    MapLocation b = new MapLocation(3, 4);
    assertEquals(7, Geometry.manhattanDistance(a, b));
  }

  @Test
  public void testClosest_SingleLocation() {
    MapLocation ref = new MapLocation(0, 0);
    MapLocation[] locs = {new MapLocation(5, 0)};
    assertEquals(new MapLocation(5, 0), Geometry.closest(ref, locs));
  }

  @Test
  public void testClosest_MultipleLocations() {
    MapLocation ref = new MapLocation(0, 0);
    MapLocation[] locs = {new MapLocation(10, 0), new MapLocation(3, 0), new MapLocation(7, 0)};
    assertEquals(new MapLocation(3, 0), Geometry.closest(ref, locs));
  }

  @Test
  public void testFarthest() {
    MapLocation ref = new MapLocation(0, 0);
    MapLocation[] locs = {new MapLocation(5, 0), new MapLocation(10, 0), new MapLocation(3, 0)};
    assertEquals(new MapLocation(10, 0), Geometry.farthest(ref, locs));
  }

  @Test
  public void testWithinRange_Inside() {
    MapLocation center = new MapLocation(10, 10);
    MapLocation target = new MapLocation(12, 12);
    assertTrue(Geometry.withinRange(center, target, 20));
  }

  @Test
  public void testWithinRange_Outside() {
    MapLocation center = new MapLocation(10, 10);
    MapLocation target = new MapLocation(20, 20);
    assertFalse(Geometry.withinRange(center, target, 20));
  }
}
