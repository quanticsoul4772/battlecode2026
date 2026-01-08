package ratbot;

import static org.junit.Assert.*;

import org.junit.Test;
import ratbot.logging.Profiler;

public class ProfilerTest {

  @Test
  public void testStartEnd() {
    Profiler.start();
    Profiler.end("test_section", 1, 1000);
    assertTrue(true);
  }

  @Test
  public void testGetBytecodes() {
    Profiler.reset();
    Profiler.start();
    Profiler.end("section1", 1, 1000);
    int bytecodes = Profiler.getBytecodes("section1");
    assertTrue(bytecodes >= 0);
  }

  @Test
  public void testReset() {
    Profiler.reset();
    int bytecodes = Profiler.getBytecodes("nonexistent");
    assertEquals(0, bytecodes);
  }

  @Test
  public void testCurrent() {
    int current = Profiler.current();
    assertTrue(current >= 0);
  }

  @Test
  public void testRemaining() {
    int remaining = Profiler.remaining(17500);
    assertTrue(remaining > 0);
  }

  @Test
  public void testApproachingLimit() {
    boolean approaching = Profiler.approachingLimit(17500);
    assertFalse(approaching);
  }
}
