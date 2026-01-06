package ratbot;

import static org.junit.Assert.*;
import org.junit.Test;
import ratbot.logging.BytecodeBudget;

public class BytecodeBudgetTest {

    @Test
    public void testStartTurn_BabyRat() {
        BytecodeBudget.startTurn("BABY_RAT");
        assertTrue(true);
    }

    @Test
    public void testStartTurn_RatKing() {
        BytecodeBudget.startTurn("RAT_KING");
        assertTrue(true);
    }

    @Test
    public void testUsedAndRemaining() {
        BytecodeBudget.startTurn("BABY_RAT");
        int used = BytecodeBudget.used();
        int remaining = BytecodeBudget.remaining();
        assertTrue(used >= 0);
        assertTrue(remaining > 0);
    }

    @Test
    public void testPercentUsed() {
        BytecodeBudget.startTurn("BABY_RAT");
        double percent = BytecodeBudget.percentUsed();
        assertTrue(percent >= 0.0 && percent <= 1.0);
    }

    @Test
    public void testCanAfford() {
        BytecodeBudget.startTurn("BABY_RAT");
        assertTrue(BytecodeBudget.canAfford(100));
    }

    @Test
    public void testGetStatus() {
        BytecodeBudget.startTurn("BABY_RAT");
        String status = BytecodeBudget.getStatus();
        assertTrue(status.equals("OK") || status.equals("WARNING") || status.equals("CRITICAL"));
    }

    @Test
    public void testEstimates_SenseNearbyRobots() {
        assertEquals(100, BytecodeBudget.Estimates.SENSE_NEARBY_ROBOTS);
    }

    @Test
    public void testEstimates_Pathfinding() {
        assertTrue(BytecodeBudget.Estimates.BFS_PATHFINDING > BytecodeBudget.Estimates.BUG2_PATHFINDING);
    }

    @Test
    public void testEstimates_Move() {
        assertTrue(BytecodeBudget.Estimates.MOVE > 0);
        assertTrue(BytecodeBudget.Estimates.MOVE < 1000);
    }
}

