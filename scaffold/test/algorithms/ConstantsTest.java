package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import ratbot.algorithms.Constants;

public class ConstantsTest {

    @Test
    public void testGetSpawnCost_FirstTier() {
        assertEquals(10, Constants.getSpawnCost(0));
        assertEquals(10, Constants.getSpawnCost(1));
        assertEquals(10, Constants.getSpawnCost(3));
    }

    @Test
    public void testGetSpawnCost_SecondTier() {
        assertEquals(20, Constants.getSpawnCost(4));
        assertEquals(20, Constants.getSpawnCost(7));
    }

    @Test
    public void testGetSpawnCost_ThirdTier() {
        assertEquals(30, Constants.getSpawnCost(8));
        assertEquals(30, Constants.getSpawnCost(11));
    }

    @Test
    public void testGetSpawnCost_HighPopulation() {
        assertEquals(70, Constants.getSpawnCost(25));
        assertEquals(260, Constants.getSpawnCost(100));
    }

    @Test
    public void testRoundsOfCheeseSupply_SingleKing() {
        assertEquals(100, Constants.roundsOfCheeseSupply(300, 1));
        assertEquals(666, Constants.roundsOfCheeseSupply(2000, 1));
    }

    @Test
    public void testRoundsOfCheeseSupply_MultipleKings() {
        assertEquals(50, Constants.roundsOfCheeseSupply(300, 2));
        assertEquals(33, Constants.roundsOfCheeseSupply(300, 3));
    }

    @Test
    public void testRoundsOfCheeseSupply_LowCheese() {
        assertEquals(10, Constants.roundsOfCheeseSupply(30, 1));
        assertEquals(0, Constants.roundsOfCheeseSupply(0, 1));
    }
}
