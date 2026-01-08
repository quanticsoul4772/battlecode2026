package algorithms;

import static org.junit.Assert.*;

import battlecode.common.*;
import org.junit.Test;
import ratbot.algorithms.Vision;

public class VisionTest {

  @Test
  public void testInCone90_DirectlyAhead() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 13);
    assertTrue(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone90_OutsideCone() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(5, 10);
    assertFalse(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone90_DiagonalInCone() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(11, 11);
    assertTrue(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone90_Behind() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 7);
    assertFalse(Vision.inCone90(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone180_FrontHalf() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 15);
    assertTrue(Vision.inCone180(origin, Direction.NORTH, target));
  }

  @Test
  public void testInCone180_BackHalf() {
    MapLocation origin = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 5);
    assertFalse(Vision.inCone180(origin, Direction.NORTH, target));
  }

  @Test
  public void testCanSee_BabyRat() {
    MapLocation observer = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 13);
    assertTrue(Vision.canSee(observer, Direction.NORTH, target, UnitType.BABY_RAT));
  }

  @Test
  public void testCanSee_RatKingOmnidirectional() {
    MapLocation observer = new MapLocation(10, 10);
    MapLocation target = new MapLocation(10, 5);
    assertTrue(Vision.canSee(observer, Direction.NORTH, target, UnitType.RAT_KING));
  }
}
