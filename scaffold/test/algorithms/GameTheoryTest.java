package algorithms;

import static org.junit.Assert.*;
import org.junit.Test;
import ratbot.algorithms.GameTheory;

public class GameTheoryTest {

    @Test
    public void testShouldBackstab_KingAdvantage() {
        boolean result = GameTheory.shouldBackstab(5000, 5000, 3, 1, 500, 500, 10);
        assertTrue(result);
    }

    @Test
    public void testCheeseToExtraDamage() {
        assertEquals(5, GameTheory.cheeseToExtraDamage(32));
        assertEquals(6, GameTheory.cheeseToExtraDamage(64));
        assertEquals(1, GameTheory.cheeseToExtraDamage(2));
    }

    @Test
    public void testWorthEnhancingBite_ForKill() {
        assertTrue(GameTheory.worthEnhancingBite(32, 15, 10));
    }

    @Test
    public void testWorthEnhancingBite_NotWorth() {
        assertFalse(GameTheory.worthEnhancingBite(32, 50, 10));
    }

    @Test
    public void testScoreCooperation_EqualSplit() {
        int score = GameTheory.scoreCooperation(5000, 5000, 2, 2, 1000, 1000);
        assertEquals(50, score);
    }

    @Test
    public void testScoreBackstabbing_AssumeWin() {
        int score = GameTheory.scoreBackstabbing(5000, 5000, 2, 2, 1000, 1000);
        assertTrue(score > 50);
    }
}
