package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import battlecode.common.*;
import ratbot.algorithms.DirectionUtil;

public class DirectionUtilTest {

    @Test
    public void testTurnsToFace_SameDirection() {
        assertEquals(0, DirectionUtil.turnsToFace(Direction.NORTH, Direction.NORTH));
    }

    @Test
    public void testTurnsToFace_Adjacent() {
        assertEquals(1, DirectionUtil.turnsToFace(Direction.NORTH, Direction.NORTHEAST));
    }

    @Test
    public void testTurnsToFace_Opposite() {
        assertEquals(2, DirectionUtil.turnsToFace(Direction.NORTH, Direction.SOUTH));
    }

    @Test
    public void testOpposite() {
        assertEquals(Direction.SOUTH, DirectionUtil.opposite(Direction.NORTH));
        assertEquals(Direction.WEST, DirectionUtil.opposite(Direction.EAST));
    }

    @Test
    public void testRotateRight() {
        assertEquals(Direction.NORTHEAST, DirectionUtil.rotateRight(Direction.NORTH));
    }

    @Test
    public void testRotateLeft() {
        assertEquals(Direction.NORTHWEST, DirectionUtil.rotateLeft(Direction.NORTH));
    }
}
