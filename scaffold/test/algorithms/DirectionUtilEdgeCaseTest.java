package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import ratbot.algorithms.DirectionUtil;

public class DirectionUtilEdgeCaseTest {

    @Test
    public void testTurn_Positive() {
        assertEquals(Direction.EAST, DirectionUtil.turn(Direction.NORTH, 2));
    }

    @Test
    public void testTurn_Negative() {
        assertEquals(Direction.WEST, DirectionUtil.turn(Direction.NORTH, -2));
    }

    @Test
    public void testTurn_Wraparound() {
        assertEquals(Direction.NORTH, DirectionUtil.turn(Direction.NORTHWEST, 1));
    }

    @Test
    public void testIsAdjacent_True() {
        assertTrue(DirectionUtil.isAdjacent(Direction.NORTH, Direction.NORTHEAST));
    }

    @Test
    public void testIsAdjacent_False() {
        assertFalse(DirectionUtil.isAdjacent(Direction.NORTH, Direction.SOUTH));
    }

    @Test
    public void testFromVector_Cardinal() {
        assertEquals(Direction.NORTH, DirectionUtil.fromVector(0, 1));
        assertEquals(Direction.EAST, DirectionUtil.fromVector(1, 0));
        assertEquals(Direction.SOUTH, DirectionUtil.fromVector(0, -1));
        assertEquals(Direction.WEST, DirectionUtil.fromVector(-1, 0));
    }

    @Test
    public void testFromVector_Diagonal() {
        assertEquals(Direction.NORTHEAST, DirectionUtil.fromVector(1, 1));
        assertEquals(Direction.SOUTHWEST, DirectionUtil.fromVector(-1, -1));
    }

    @Test
    public void testFromVector_Zero() {
        assertEquals(Direction.CENTER, DirectionUtil.fromVector(0, 0));
    }

    @Test
    public void testIsDiagonal() {
        assertTrue(DirectionUtil.isDiagonal(Direction.NORTHEAST));
        assertTrue(DirectionUtil.isDiagonal(Direction.SOUTHWEST));
        assertFalse(DirectionUtil.isDiagonal(Direction.NORTH));
    }

    @Test
    public void testIsCardinal() {
        assertTrue(DirectionUtil.isCardinal(Direction.NORTH));
        assertTrue(DirectionUtil.isCardinal(Direction.EAST));
        assertFalse(DirectionUtil.isCardinal(Direction.NORTHEAST));
    }

    @Test
    public void testOptimalMovement_AlreadyFacing() {
        DirectionUtil.MovementPlan plan = DirectionUtil.optimalMovement(Direction.NORTH, Direction.NORTH);
        assertEquals(Direction.NORTH, plan.targetFacing);
        assertFalse(plan.needsTurn);
    }

    @Test
    public void testOptimalMovement_OneTurnAway() {
        DirectionUtil.MovementPlan plan = DirectionUtil.optimalMovement(Direction.NORTH, Direction.NORTHEAST);
        assertEquals(Direction.NORTHEAST, plan.targetFacing);
        assertTrue(plan.needsTurn);
    }

    @Test
    public void testOptimalFacingForTwo() {
        Direction optimal = DirectionUtil.optimalFacingForTwo(
            new MapLocation(10, 10),
            new MapLocation(10, 15),
            new MapLocation(11, 15)
        );
        assertNotNull(optimal);
    }

    @Test
    public void testOrderedDirections_StartsWithPreferred() {
        Direction[] ordered = DirectionUtil.orderedDirections(Direction.NORTH);
        assertEquals(Direction.NORTH, ordered[0]);
    }
}
