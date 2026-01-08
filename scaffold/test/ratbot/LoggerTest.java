package ratbot;

import static org.junit.Assert.*;

import org.junit.Test;
import ratbot.logging.Logger;

public class LoggerTest {

  @Test
  public void testLogState() {
    Logger.logState(100, "BABY_RAT", 12345, 15, 15, "NORTH", 85, 12, "COLLECT");
    assertTrue(true);
  }

  @Test
  public void testLogEconomy() {
    Logger.logEconomy(100, 2340, 8, 2, 15, 450);
    assertTrue(true);
  }

  @Test
  public void testLogSpawn() {
    Logger.logSpawn(100, "RAT_KING", 12345, 15, 15, 20, 8);
    assertTrue(true);
  }

  @Test
  public void testLogCombat() {
    Logger.logCombat(100, "BABY_RAT", 12345, 10, 10, 11, 11, 10, 0, 90);
    assertTrue(true);
  }

  @Test
  public void testLogCheeseCollect() {
    Logger.logCheeseCollect(100, 12345, 10, 12, 5, 17, 10, 10);
    assertTrue(true);
  }

  @Test
  public void testLogCheeseTransfer() {
    Logger.logCheeseTransfer(100, 12345, 25, 15, 15, 2550);
    assertTrue(true);
  }
}
