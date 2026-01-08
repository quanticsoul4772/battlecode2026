package ratbot.logging;

import battlecode.common.*;

/**
 * Bytecode budget tracking and enforcement.
 *
 * <p>Helps prevent bytecode overflows by: - Tracking cumulative usage per turn - Warning when
 * approaching limit - Forcing early yield if necessary
 *
 * <p>Critical for avoiding mid-turn cutoffs.
 */
public class BytecodeBudget {

  private static final int BABY_RAT_LIMIT = 17500;
  private static final int RAT_KING_LIMIT = 20000;

  private static final double WARNING_THRESHOLD = 0.8; // Warn at 80%
  private static final double CRITICAL_THRESHOLD = 0.9; // Critical at 90%

  private static int turnStartBytecode = 0;
  private static int budgetLimit = BABY_RAT_LIMIT;

  /**
   * Initialize budget tracking for this turn.
   *
   * @param unitType Unit type to set appropriate limit
   */
  public static void startTurn(String unitType) {
    turnStartBytecode = Clock.getBytecodeNum();

    // Set limit based on type
    if (unitType.equals("RAT_KING")) {
      budgetLimit = RAT_KING_LIMIT;
    } else {
      budgetLimit = BABY_RAT_LIMIT;
    }
  }

  /**
   * Get bytecodes used this turn.
   *
   * @return Bytecodes used
   */
  public static int used() {
    return Clock.getBytecodeNum() - turnStartBytecode;
  }

  /**
   * Get bytecodes remaining.
   *
   * @return Bytecodes left in budget
   */
  public static int remaining() {
    return budgetLimit - used();
  }

  /**
   * Get percentage of budget used.
   *
   * @return Percentage (0.0 to 1.0)
   */
  public static double percentUsed() {
    return (double) used() / budgetLimit;
  }

  /**
   * Check if approaching bytecode limit.
   *
   * @return true if >80% of budget used
   */
  public static boolean isWarning() {
    return percentUsed() > WARNING_THRESHOLD;
  }

  /**
   * Check if critically close to limit.
   *
   * @return true if >90% of budget used
   */
  public static boolean isCritical() {
    return percentUsed() > CRITICAL_THRESHOLD;
  }

  /**
   * Check if can afford operation.
   *
   * @param estimatedCost Estimated bytecode cost
   * @return true if enough budget remaining
   */
  public static boolean canAfford(int estimatedCost) {
    return remaining() >= estimatedCost;
  }

  /**
   * Reserve bytecodes for end-of-turn operations. Returns true if we should skip optional
   * operations.
   *
   * @param reserveAmount Bytecodes to reserve
   * @return true if should conserve bytecode
   */
  public static boolean shouldConserve(int reserveAmount) {
    return remaining() < reserveAmount;
  }

  /**
   * Force yield if critically low on bytecode. Call before expensive operations.
   *
   * @param operationCost Expected cost of next operation
   */
  public static void checkYield(int operationCost) {
    if (remaining() < operationCost) {
      Clock.yield();
    }
  }

  /**
   * Get budget status string for logging.
   *
   * @return Status string (e.g., "OK", "WARNING", "CRITICAL")
   */
  public static String getStatus() {
    if (isCritical()) return "CRITICAL";
    if (isWarning()) return "WARNING";
    return "OK";
  }

  /**
   * Log bytecode usage summary.
   *
   * @param round Current round
   * @param id Robot ID
   */
  public static void logSummary(int round, int id) {
    int used = used();
    int rem = remaining();
    double pct = percentUsed() * 100;

    sb.setLength(0);
    sb.append("BYTECODE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":used=")
        .append(used)
        .append(":remaining=")
        .append(rem)
        .append(":percent=")
        .append((int) pct)
        .append(":status=")
        .append(getStatus());

    System.out.println(sb.toString());
  }

  // Reusable StringBuilder
  private static StringBuilder sb = new StringBuilder(128);

  /**
   * Estimated bytecode costs for common operations. Based on 2025 experience and method complexity.
   */
  public static class Estimates {
    public static final int SENSE_NEARBY_ROBOTS = 100;
    public static final int SENSE_MAP_INFO = 50;
    public static final int BFS_PATHFINDING = 3000; // Expensive
    public static final int BUG2_PATHFINDING = 300; // Cheaper
    public static final int VISION_CONE_CHECK = 50;
    public static final int ATTACK = 100;
    public static final int MOVE = 50;
    public static final int TURN = 50;
    public static final int SHARED_ARRAY_READ = 20;
    public static final int SHARED_ARRAY_WRITE = 20;
    public static final int SQUEAK = 50;

    // Reserve for end-of-turn
    public static final int END_TURN_RESERVE = 500;
  }
}
