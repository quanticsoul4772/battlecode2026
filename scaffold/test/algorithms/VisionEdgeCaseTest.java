package algorithms;

import static org.junit.Assert.*;

import battlecode.common.*;
import org.junit.Test;
import ratbot.algorithms.Vision;

public class VisionEdgeCaseTest {

  @Test
  public void testInCone90_ExactlyAt45Degrees() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(11, 11);
    assertTrue(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone90_Perpendicular() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(13, 10);
    assertFalse(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone180_ExactlyAt90Degrees() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(13, 10);
    assertTrue(Vision.inCone180(origin, Direction.NORTH, target));
  }

  @Test
  public void testIsVisible_OutOfRange() {
    MapLocation observer = new MapLocation(10, 10);
    MapLocation target = new MapLocation(30, 30);
    assertFalse(Vision.isVisible(observer, Direction.NORTH, target, 20, 90));
  }

  @Test
  public void testIsVisible_InRangeInCone() {
    MapLocation observer = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 14);
    assertTrue(Vision.isVisible(observer, Direction.NORTH, target, 20, 90));
  }

  @Test
  public void testCanSee_CatVision180() {
    MapLocation observer = new MapLocation(10, 10);
    MapLocation target = new MapLocation(13, 10);
    assertTrue(Vision.canSee(observer, Direction.NORTH, target, UnitType.CAT));
  }

  @Test
  public void testGetVisibleTilesCount_ReturnsSomeResults() {
    int count =
        Vision.getVisibleTilesCount(new MapLocation(15, 15), Direction.NORTH, 20, 90, 30, 30);
    assertTrue(count > 0);
    assertTrue(count < 400);
  }
}
