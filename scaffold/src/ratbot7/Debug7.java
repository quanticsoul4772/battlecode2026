package ratbot7;

import battlecode.common.*;

/**
 * Comprehensive debugging system for ratbot7 defensive analysis.
 *
 * <p>Provides detailed logging of all defensive systems to learn from each game: - Trap placement
 * and effectiveness - Body blocking formation and decisions - Retreat/kiting behavior - Threat
 * detection and response - Phase transitions - Combat outcomes - Formation integrity
 *
 * <p>Log Format: {CATEGORY}:{round}:{id}:{key=value}:...
 *
 * <p>Indicator Format: {ROLE}|{PHASE}|HP:{hp}|{ACTION}:{detail}
 */
public class Debug7 {

  // ========================================================================
  // MASTER DEBUG SWITCHES
  // ========================================================================

  /** Master switch - set false for competition to eliminate all overhead. */
  public static final boolean ENABLED = true;

  /** Enable visual indicators in client (dots, lines). */
  public static final boolean SHOW_VISUALS = true;

  /** Enable indicator strings (status bar in client). */
  public static final boolean SHOW_INDICATOR = true;

  // ========================================================================
  // CATEGORY SWITCHES - Toggle individual debug categories
  // ========================================================================

  /** Log per-turn robot state. */
  public static final boolean LOG_STATE = true;

  /** Log defensive decisions (retreat, block, converge). */
  public static final boolean LOG_DECISIONS = true;

  /** Log trap placement events. */
  public static final boolean LOG_TRAPS = true;

  /** Log threat detection and level changes. */
  public static final boolean LOG_THREATS = true;

  /** Log formation status (sentry ring, block line). */
  public static final boolean LOG_FORMATION = true;

  /** Log combat outcomes (attacks, damage, kills). */
  public static final boolean LOG_COMBAT = true;

  /** Log phase transitions. */
  public static final boolean LOG_PHASES = true;

  /** Log king summary each turn. */
  public static final boolean LOG_SUMMARY = true;

  /** Log ratnapping events. */
  public static final boolean LOG_RATNAP = true;

  /** Log squeak communications. */
  public static final boolean LOG_SQUEAKS = true;

  /** Log economy events (cheese collection, deliveries, gatherer decisions). */
  public static final boolean LOG_ECONOMY = true;

  /** Log bytecode usage at key checkpoints (for performance analysis). */
  public static final boolean LOG_BYTECODE = true;

  /** Log bytecode every N rounds (reduces spam). */
  public static final int BYTECODE_LOG_INTERVAL = 10;

  // ========================================================================
  // BYTECODE MEASUREMENT
  // ========================================================================

  /** Bytecode budget for Baby Rat per turn. */
  public static final int BABY_RAT_BYTECODE_LIMIT = 17500;

  /** Bytecode budget for King per turn. */
  public static final int KING_BYTECODE_LIMIT = 20000;

  // Track bytecode at start of turn for delta calculations
  private static int turnStartBytecode = 0;
  private static int lastCheckpointBytecode = 0;

  /**
   * Call at the very start of a turn to establish baseline. Must be called before any other
   * bytecode logging.
   */
  public static void startBytecodeTracking() {
    turnStartBytecode = Clock.getBytecodesLeft();
    lastCheckpointBytecode = turnStartBytecode;
  }

  /**
   * Log bytecode usage at a checkpoint. Reports both delta from last checkpoint and total used this
   * turn.
   *
   * <p>NOTE: The delta values include ~50 bytecode overhead from the previous logBytecode() call
   * (StringBuilder ops, System.out.println). For accurate measurements, account for this overhead.
   *
   * <p>Example: BYTECODE:50:12345:checkpoint=SENSE:delta=450:total=1200:remaining=16300
   */
  public static void logBytecode(int round, int id, String checkpoint) {
    if (!ENABLED || !LOG_BYTECODE) return;
    // Throttle to reduce log spam
    if (BYTECODE_LOG_INTERVAL > 0 && round % BYTECODE_LOG_INTERVAL != 0) return;

    int currentBytecode = Clock.getBytecodesLeft();
    int delta = lastCheckpointBytecode - currentBytecode;
    int totalUsed = turnStartBytecode - currentBytecode;
    lastCheckpointBytecode = currentBytecode;

    sb.setLength(0);
    sb.append("BYTECODE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":checkpoint=")
        .append(checkpoint)
        .append(":delta=")
        .append(delta)
        .append(":total=")
        .append(totalUsed)
        .append(":remaining=")
        .append(currentBytecode);
    System.out.println(sb.toString());
  }

  /**
   * Log bytecode summary at end of turn. Reports total bytecode used and percentage of budget.
   *
   * <p>Example: BYTECODE_SUMMARY:50:12345:type=BABY_RAT:used=2500:budget=17500:pct=14
   */
  public static void logBytecodeSummary(int round, int id, boolean isKing) {
    if (!ENABLED || !LOG_BYTECODE) return;
    // Throttle to reduce log spam
    if (BYTECODE_LOG_INTERVAL > 0 && round % BYTECODE_LOG_INTERVAL != 0) return;

    int currentBytecode = Clock.getBytecodesLeft();
    int totalUsed = turnStartBytecode - currentBytecode;
    int budget = isKing ? KING_BYTECODE_LIMIT : BABY_RAT_BYTECODE_LIMIT;
    int pct = (totalUsed * 100) / budget;
    String type = isKing ? "KING" : "BABY_RAT";

    sb.setLength(0);
    sb.append("BYTECODE_SUMMARY:")
        .append(round)
        .append(":")
        .append(id)
        .append(":type=")
        .append(type)
        .append(":used=")
        .append(totalUsed)
        .append(":budget=")
        .append(budget)
        .append(":pct=")
        .append(pct);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // LOGGING INTERVALS - Reduce spam
  // ========================================================================

  /** Log state every N rounds (0 = every round). */
  public static final int STATE_LOG_INTERVAL = 5;

  /** Log summary every N rounds. */
  public static final int SUMMARY_LOG_INTERVAL = 1;

  // ========================================================================
  // COLORS FOR VISUAL INDICATORS
  // ========================================================================

  // Threat colors
  public static final int COLOR_ENEMY = 0xFF0000; // Red - enemies
  public static final int COLOR_THREAT = 0xFF6600; // Orange - threat direction
  public static final int COLOR_CAT = 0xFF00FF; // Magenta - cats

  // Defensive colors
  public static final int COLOR_BLOCK_LINE = 0x00FFFF; // Cyan - body block line
  public static final int COLOR_SENTRY_RING = 0x0066FF; // Blue - sentry positions
  public static final int COLOR_FORMATION = 0x00FF00; // Green - formation positions
  public static final int COLOR_KING_ZONE = 0xFFFF00; // Yellow - king safe zone

  // Trap colors
  public static final int COLOR_RAT_TRAP = 0xFF9900; // Orange - rat traps
  public static final int COLOR_CAT_TRAP = 0x9900FF; // Purple - cat traps
  public static final int COLOR_DIRT_WALL = 0x996633; // Brown - dirt walls

  // Action colors
  public static final int COLOR_RETREAT = 0xFFFF99; // Light yellow - retreating
  public static final int COLOR_ATTACK = 0xFF3333; // Bright red - attacking
  public static final int COLOR_TARGET = 0xFF00FF; // Magenta - current target

  // Reusable StringBuilder for zero allocation logging
  // NOTE: This StringBuilder is reused across all logging methods. Since logging
  // happens sequentially within a single turn, this is safe. Each method calls
  // sb.setLength(0) before use to reset the buffer.
  private static final StringBuilder sb = new StringBuilder(256);

  /** Log emergency defense activation interval (rounds) - reduces spam. */
  public static final int EMERGENCY_LOG_INTERVAL = 10;

  // ========================================================================
  // PHASE LOGGING
  // ========================================================================

  /**
   * Log phase transition with reason. Example:
   * PHASE:100:12345:BUILDUP->DEFENSE:reason=enemies_detected:count=3
   */
  public static void logPhaseChange(
      int round, int id, String fromPhase, String toPhase, String reason, int context) {
    if (!ENABLED || !LOG_PHASES) return;

    sb.setLength(0);
    sb.append("PHASE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":")
        .append(fromPhase)
        .append("->")
        .append(toPhase)
        .append(":reason=")
        .append(reason)
        .append(":context=")
        .append(context);
    System.out.println(sb.toString());
  }

  /** Log rush detection. Example: RUSH:25:12345:detected=true:enemyCount=4:triggerRound=25 */
  public static void logRushDetected(int round, int id, int enemyCount) {
    if (!ENABLED || !LOG_THREATS) return;

    sb.setLength(0);
    sb.append("RUSH:")
        .append(round)
        .append(":")
        .append(id)
        .append(":detected=true")
        .append(":enemyCount=")
        .append(enemyCount)
        .append(":triggerRound=")
        .append(round);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // TRAP LOGGING
  // ========================================================================

  /** Log trap placement. Example: TRAP:15:12345:type=RAT_TRAP:pos=[10,12]:ring=3:total=5 */
  public static void logTrapPlaced(
      int round, int id, String trapType, int x, int y, int ringDist, int totalTraps) {
    if (!ENABLED || !LOG_TRAPS) return;

    sb.setLength(0);
    sb.append("TRAP:")
        .append(round)
        .append(":")
        .append(id)
        .append(":type=")
        .append(trapType)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":ring=")
        .append(ringDist)
        .append(":total=")
        .append(totalTraps);
    System.out.println(sb.toString());
  }

  /** Log dirt wall placement. Example: WALL:20:12345:pos=[8,15]:toward=NORTH:total=3 */
  public static void logWallPlaced(
      int round, int id, int x, int y, String towardEnemy, int totalWalls) {
    if (!ENABLED || !LOG_TRAPS) return;

    sb.setLength(0);
    sb.append("WALL:")
        .append(round)
        .append(":")
        .append(id)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":toward=")
        .append(towardEnemy)
        .append(":total=")
        .append(totalWalls);
    System.out.println(sb.toString());
  }

  /**
   * Log trap placement failure. Example: TRAP_FAIL:25:12345:layout=RING:failures=5
   *
   * <p>This helps diagnose when trap placement is blocked by map geometry.
   */
  public static void logTrapFailed(int round, int id, String layout, int consecutiveFailures) {
    if (!ENABLED || !LOG_TRAPS) return;
    // Only log every 5th failure to reduce spam
    if (consecutiveFailures % 5 != 0 && consecutiveFailures < 10) return;

    sb.setLength(0);
    sb.append("TRAP_FAIL:")
        .append(round)
        .append(":")
        .append(id)
        .append(":layout=")
        .append(layout)
        .append(":failures=")
        .append(consecutiveFailures);
    System.out.println(sb.toString());
  }

  /** Log trap layout switch. Example: LAYOUT_SWITCH:30:12345:from=RING:to=LINE:afterFailures=10 */
  public static void logLayoutSwitch(
      int round, int id, String fromLayout, String toLayout, int afterFailures) {
    if (!ENABLED || !LOG_TRAPS) return;

    sb.setLength(0);
    sb.append("LAYOUT_SWITCH:")
        .append(round)
        .append(":")
        .append(id)
        .append(":from=")
        .append(fromLayout)
        .append(":to=")
        .append(toLayout)
        .append(":afterFailures=")
        .append(afterFailures);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // THREAT LOGGING
  // ========================================================================

  /** Log threat level change. Example: THREAT:50:12345:level=3->5:source=king_sense:enemyDist=8 */
  public static void logThreatChange(
      int round, int id, int oldLevel, int newLevel, String source, int nearestEnemyDist) {
    if (!ENABLED || !LOG_THREATS) return;

    sb.setLength(0);
    sb.append("THREAT:")
        .append(round)
        .append(":")
        .append(id)
        .append(":level=")
        .append(oldLevel)
        .append("->")
        .append(newLevel)
        .append(":source=")
        .append(source)
        .append(":nearestDist=")
        .append(nearestEnemyDist);
    System.out.println(sb.toString());
  }

  /**
   * Log enemy spotted by baby rat. Example:
   * SPOTTED:45:12346:enemies=2:nearest=[15,20]:distToKing=12
   */
  public static void logEnemySpotted(
      int round, int id, int enemyCount, int nearestX, int nearestY, int distToKing) {
    if (!ENABLED || !LOG_THREATS) return;

    sb.setLength(0);
    sb.append("SPOTTED:")
        .append(round)
        .append(":")
        .append(id)
        .append(":enemies=")
        .append(enemyCount)
        .append(":nearest=[")
        .append(nearestX)
        .append(",")
        .append(nearestY)
        .append("]")
        .append(":distToKing=")
        .append(distToKing);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // DEFENSIVE DECISION LOGGING
  // ========================================================================

  /**
   * Log retreat decision. Example:
   * RETREAT:60:12346:hp=28:trigger=HP_CRITICAL:distToKing=15:enemies=2
   */
  public static void logRetreat(
      int round, int id, int hp, String trigger, int distToKing, int enemyCount) {
    if (!ENABLED || !LOG_DECISIONS) return;

    sb.setLength(0);
    sb.append("RETREAT:")
        .append(round)
        .append(":")
        .append(id)
        .append(":hp=")
        .append(hp)
        .append(":trigger=")
        .append(trigger)
        .append(":distToKing=")
        .append(distToKing)
        .append(":enemies=")
        .append(enemyCount);
    System.out.println(sb.toString());
  }

  /**
   * Log kiting decision (retreat through traps). Example:
   * KITE:62:12346:hp=42:enemies=3:direction=SOUTH:throughTraps=true
   */
  public static void logKite(int round, int id, int hp, int enemyCount, String direction) {
    if (!ENABLED || !LOG_DECISIONS) return;

    sb.setLength(0);
    sb.append("KITE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":hp=")
        .append(hp)
        .append(":enemies=")
        .append(enemyCount)
        .append(":direction=")
        .append(direction);
    System.out.println(sb.toString());
  }

  /**
   * Log body block decision. Example: BLOCK:55:12346:slot=2:inPosition=true:distToLine=0:enemies=3
   */
  public static void logBodyBlock(
      int round, int id, int slot, boolean inPosition, int distToLine, int enemyCount) {
    if (!ENABLED || !LOG_DECISIONS) return;

    sb.setLength(0);
    sb.append("BLOCK:")
        .append(round)
        .append(":")
        .append(id)
        .append(":slot=")
        .append(slot)
        .append(":inPosition=")
        .append(inPosition)
        .append(":distToLine=")
        .append(distToLine)
        .append(":enemies=")
        .append(enemyCount);
    System.out.println(sb.toString());
  }

  /**
   * Log formation convergence decision. Example:
   * CONVERGE:58:12346:distToKing=12:threat=4:target=[15,15]
   */
  public static void logConverge(
      int round, int id, int distToKing, int threatLevel, int kingX, int kingY) {
    if (!ENABLED || !LOG_DECISIONS) return;

    sb.setLength(0);
    sb.append("CONVERGE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":distToKing=")
        .append(distToKing)
        .append(":threat=")
        .append(threatLevel)
        .append(":target=[")
        .append(kingX)
        .append(",")
        .append(kingY)
        .append("]");
    System.out.println(sb.toString());
  }

  // Track last emergency log round (static - shared across all robots in same team)
  private static int lastEmergencyLogRound = -100;

  /**
   * Log emergency defense activation. Example:
   * EMERGENCY:52:12345:trigger=THREAT_LEVEL:level=3:allDefending=true
   *
   * <p>Throttled to every EMERGENCY_LOG_INTERVAL rounds to prevent spam.
   */
  public static void logEmergencyDefense(int round, int id, String trigger, int threatLevel) {
    if (!ENABLED || !LOG_DECISIONS) return;
    // Throttle emergency logs to reduce spam during prolonged defense
    if (round - lastEmergencyLogRound < EMERGENCY_LOG_INTERVAL) return;
    lastEmergencyLogRound = round;

    sb.setLength(0);
    sb.append("EMERGENCY:")
        .append(round)
        .append(":")
        .append(id)
        .append(":trigger=")
        .append(trigger)
        .append(":level=")
        .append(threatLevel);
    System.out.println(sb.toString());
  }

  // Track last decision log round per decision type to reduce spam
  private static int lastDecisionLogRound = -100;
  private static final int DECISION_LOG_INTERVAL = 10;

  // Track last ECON_EMERGENCY_OVERRIDE log (separate from generic decisions to avoid spam)
  private static int lastEconEmergencyLogRound = -100;
  private static final int ECON_EMERGENCY_LOG_INTERVAL = 10;

  /**
   * Log generic decision with context. Example:
   * DECISION:100:12345:type=KING_FREEZE:context=threat=3:NO_MOVEMENT:defenders_converging
   *
   * <p>Throttled to reduce spam during prolonged states.
   */
  public static void logDecision(int round, int id, String decisionType, String context) {
    if (!ENABLED || !LOG_DECISIONS) return;

    // ECON_EMERGENCY_OVERRIDE has its own throttle to prevent flooding during economy crisis
    if ("ECON_EMERGENCY_OVERRIDE".equals(decisionType)) {
      if (round - lastEconEmergencyLogRound < ECON_EMERGENCY_LOG_INTERVAL) return;
      lastEconEmergencyLogRound = round;
    } else {
      // Generic decision throttle
      if (round - lastDecisionLogRound < DECISION_LOG_INTERVAL) return;
      lastDecisionLogRound = round;
    }

    sb.setLength(0);
    sb.append("DECISION:")
        .append(round)
        .append(":")
        .append(id)
        .append(":type=")
        .append(decisionType)
        .append(":")
        .append(context);
    System.out.println(sb.toString());
  }

  // Track last KING_FREEZE log to avoid spamming every round
  private static int lastKingFreezeLogRound = -100;
  private static final int KING_FREEZE_LOG_INTERVAL = 5;

  /**
   * Log KING_FREEZE activation - dedicated method without heavy throttling. This logs when king
   * freezes due to high threat level, helping verify the defensive system is triggering at correct
   * threat levels.
   *
   * <p>Example: KING_FREEZE:50:12345:threat=5:hp=350:enemies=3:safeRounds=0
   */
  public static void logKingFreeze(
      int round, int id, int threatLevel, int hp, int enemyCount, int safeRounds) {
    if (!ENABLED || !LOG_DECISIONS) return;
    // Light throttle - log every 5 rounds during freeze
    if (round - lastKingFreezeLogRound < KING_FREEZE_LOG_INTERVAL) return;
    lastKingFreezeLogRound = round;

    sb.setLength(0);
    sb.append("KING_FREEZE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":threat=")
        .append(threatLevel)
        .append(":hp=")
        .append(hp)
        .append(":enemies=")
        .append(enemyCount)
        .append(":safeRounds=")
        .append(safeRounds);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // FORMATION LOGGING
  // ========================================================================

  /**
   * Log sentry ring position status. Example:
   * SENTRY:40:12346:inRing=true:dist=5:facing=EAST:scanning=true
   */
  public static void logSentryStatus(
      int round, int id, boolean inRing, int distToKing, String facing) {
    if (!ENABLED || !LOG_FORMATION) return;

    sb.setLength(0);
    sb.append("SENTRY:")
        .append(round)
        .append(":")
        .append(id)
        .append(":inRing=")
        .append(inRing)
        .append(":dist=")
        .append(distToKing)
        .append(":facing=")
        .append(facing);
    System.out.println(sb.toString());
  }

  /**
   * Log block line status from king perspective. Example:
   * BLOCKLINE:55:12345:center=[12,18]:perp=EAST:enemies=4:closest=8
   */
  public static void logBlockLine(
      int round,
      int id,
      int centerX,
      int centerY,
      String perpDir,
      int enemyCount,
      int closestDist) {
    if (!ENABLED || !LOG_FORMATION) return;

    sb.setLength(0);
    sb.append("BLOCKLINE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":center=[")
        .append(centerX)
        .append(",")
        .append(centerY)
        .append("]")
        .append(":perp=")
        .append(perpDir)
        .append(":enemies=")
        .append(enemyCount)
        .append(":closest=")
        .append(closestDist);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // COMBAT LOGGING
  // ========================================================================

  /**
   * Log attack action. Example: ATTACK:60:12346:target=[15,18]:targetHP=45:damage=10:enhanced=false
   */
  public static void logAttack(
      int round, int id, int targetX, int targetY, int targetHP, int damage, boolean enhanced) {
    if (!ENABLED || !LOG_COMBAT) return;

    sb.setLength(0);
    sb.append("ATTACK:")
        .append(round)
        .append(":")
        .append(id)
        .append(":target=[")
        .append(targetX)
        .append(",")
        .append(targetY)
        .append("]")
        .append(":targetHP=")
        .append(targetHP)
        .append(":damage=")
        .append(damage)
        .append(":enhanced=")
        .append(enhanced);
    System.out.println(sb.toString());
  }

  /** Log kill (target HP went to 0). Example: KILL:62:12346:target=[15,18]:targetType=BABY_RAT */
  public static void logKill(int round, int id, int targetX, int targetY, String targetType) {
    if (!ENABLED || !LOG_COMBAT) return;

    sb.setLength(0);
    sb.append("KILL:")
        .append(round)
        .append(":")
        .append(id)
        .append(":target=[")
        .append(targetX)
        .append(",")
        .append(targetY)
        .append("]")
        .append(":targetType=")
        .append(targetType);
    System.out.println(sb.toString());
  }

  /**
   * Log focus fire target selection. Example:
   * FOCUS:55:12345:target=12399:targetHP=35:priority=WOUNDED
   */
  public static void logFocusFire(int round, int id, int targetId, int targetHP, String priority) {
    if (!ENABLED || !LOG_COMBAT) return;

    sb.setLength(0);
    sb.append("FOCUS:")
        .append(round)
        .append(":")
        .append(id)
        .append(":target=")
        .append(targetId)
        .append(":targetHP=")
        .append(targetHP)
        .append(":priority=")
        .append(priority);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // RATNAPPING LOGGING
  // ========================================================================

  /** Log ratnap grab. Example: RATNAP:65:12346:action=GRAB:target=12399:targetHP=20:isAlly=false */
  public static void logRatnapGrab(int round, int id, int targetId, int targetHP, boolean isAlly) {
    if (!ENABLED || !LOG_RATNAP) return;

    sb.setLength(0);
    sb.append("RATNAP:")
        .append(round)
        .append(":")
        .append(id)
        .append(":action=GRAB")
        .append(":target=")
        .append(targetId)
        .append(":targetHP=")
        .append(targetHP)
        .append(":isAlly=")
        .append(isAlly);
    System.out.println(sb.toString());
  }

  /** Log ratnap throw. Example: RATNAP:67:12346:action=THROW:direction=NORTH:isRescue=true */
  public static void logRatnapThrow(int round, int id, String direction, boolean isRescue) {
    if (!ENABLED || !LOG_RATNAP) return;

    sb.setLength(0);
    sb.append("RATNAP:")
        .append(round)
        .append(":")
        .append(id)
        .append(":action=THROW")
        .append(":direction=")
        .append(direction)
        .append(":isRescue=")
        .append(isRescue);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // SQUEAK LOGGING
  // ========================================================================

  /**
   * Log squeak sent with position and context data. Example:
   * SQUEAK:50:12346:type=ENEMY_SPOTTED:pos=[10,15]:data=3
   *
   * <p>Note: Takes individual values instead of pre-concatenated string to avoid bytecode overhead
   * from string building when debugging is disabled.
   */
  public static void logSqueakSent(
      int round, int id, String squeakType, int posX, int posY, int data) {
    if (!ENABLED || !LOG_SQUEAKS) return;

    sb.setLength(0);
    sb.append("SQUEAK:")
        .append(round)
        .append(":")
        .append(id)
        .append(":type=")
        .append(squeakType)
        .append(":pos=[")
        .append(posX)
        .append(",")
        .append(posY)
        .append("]")
        .append(":data=")
        .append(data);
    System.out.println(sb.toString());
  }

  /**
   * Log squeak received by king. Example:
   * SQUEAK_RX:50:12345:type=ENEMY_SPOTTED:from=[20,15]:count=3
   */
  public static void logSqueakReceived(
      int round, int id, String squeakType, int fromX, int fromY, int data) {
    if (!ENABLED || !LOG_SQUEAKS) return;

    sb.setLength(0);
    sb.append("SQUEAK_RX:")
        .append(round)
        .append(":")
        .append(id)
        .append(":type=")
        .append(squeakType)
        .append(":from=[")
        .append(fromX)
        .append(",")
        .append(fromY)
        .append("]")
        .append(":data=")
        .append(data);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // KING SUMMARY LOGGING
  // ========================================================================

  /**
   * Log king turn summary - comprehensive defensive status. Example:
   * SUMMARY:100:12345:hp=450:cheese=2500:spawns=15:threat=2:
   * phase=DEFENSE:traps=12:walls=4:rushDetected=false
   */
  public static void logKingSummary(
      int round,
      int id,
      int hp,
      int cheese,
      int spawns,
      int threatLevel,
      String phase,
      int trapCount,
      int wallCount,
      boolean rushDetected) {
    if (!ENABLED || !LOG_SUMMARY) return;
    if (round % SUMMARY_LOG_INTERVAL != 0) return;

    sb.setLength(0);
    sb.append("SUMMARY:")
        .append(round)
        .append(":")
        .append(id)
        .append(":hp=")
        .append(hp)
        .append(":cheese=")
        .append(cheese)
        .append(":spawns=")
        .append(spawns)
        .append(":threat=")
        .append(threatLevel)
        .append(":phase=")
        .append(phase)
        .append(":traps=")
        .append(trapCount)
        .append(":walls=")
        .append(wallCount)
        .append(":rush=")
        .append(rushDetected);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // BABY RAT STATE LOGGING
  // ========================================================================

  /**
   * Log baby rat per-turn state. Example:
   * STATE:100:12346:role=SENTRY:hp=85:pos=[15,18]:target=PATROL:distKing=6
   */
  public static void logBabyRatState(
      int round,
      int id,
      String role,
      int hp,
      int x,
      int y,
      String targetType,
      int distToKing,
      boolean emergency) {
    if (!ENABLED || !LOG_STATE) return;
    if (STATE_LOG_INTERVAL > 0 && round % STATE_LOG_INTERVAL != 0) return;

    sb.setLength(0);
    sb.append("STATE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":role=")
        .append(role)
        .append(":hp=")
        .append(hp)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":target=")
        .append(targetType)
        .append(":distKing=")
        .append(distToKing)
        .append(":emergency=")
        .append(emergency);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // VISUAL INDICATORS
  // ========================================================================

  /** Draw dot at location with color. */
  public static void dot(RobotController rc, MapLocation loc, int color) {
    if (!ENABLED || !SHOW_VISUALS) return;
    if (loc == null) return;

    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    try {
      rc.setIndicatorDot(loc, r, g, b);
    } catch (Exception e) {
    }
  }

  /** Draw line between locations. */
  public static void line(RobotController rc, MapLocation from, MapLocation to, int color) {
    if (!ENABLED || !SHOW_VISUALS) return;
    if (from == null || to == null) return;

    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    try {
      rc.setIndicatorLine(from, to, r, g, b);
    } catch (Exception e) {
    }
  }

  /**
   * Set indicator string (status bar in client). Format: {ROLE}|{PHASE}|HP:{hp}|{ACTION}:{detail}
   */
  public static void indicator(RobotController rc, String status) {
    if (!ENABLED || !SHOW_INDICATOR) return;

    try {
      rc.setIndicatorString(status);
    } catch (Exception e) {
    }
  }

  /** Build indicator string for baby rat. */
  public static String buildIndicator(
      String role, String phase, int hp, String action, String detail) {
    sb.setLength(0);
    sb.append(role)
        .append("|")
        .append(phase)
        .append("|HP:")
        .append(hp)
        .append("|")
        .append(action)
        .append(":")
        .append(detail);
    return sb.toString();
  }

  /** Build indicator string for king. */
  public static String buildKingIndicator(
      String phase, int hp, int cheese, int threat, int spawns) {
    sb.setLength(0);
    sb.append("KING|")
        .append(phase)
        .append("|HP:")
        .append(hp)
        .append("|$")
        .append(cheese)
        .append("|T")
        .append(threat)
        .append("|#")
        .append(spawns);
    return sb.toString();
  }

  // ========================================================================
  // VISUAL HELPERS FOR FORMATIONS
  // ========================================================================

  /** Visualize sentry ring around king. */
  public static void visualizeSentryRing(RobotController rc, MapLocation kingLoc, int ringDist) {
    if (!ENABLED || !SHOW_VISUALS) return;
    if (kingLoc == null) return;

    // Draw ring at 8 cardinal directions
    Direction[] dirs = Direction.values();
    for (Direction dir : dirs) {
      if (dir == Direction.CENTER) continue;
      MapLocation ringPos = kingLoc.translate(dir.dx * ringDist, dir.dy * ringDist);
      dot(rc, ringPos, COLOR_SENTRY_RING);
    }
  }

  /** Visualize body blocking line. */
  public static void visualizeBlockLine(
      RobotController rc, MapLocation center, Direction perpDir, int slots) {
    if (!ENABLED || !SHOW_VISUALS) return;
    if (center == null || perpDir == null) return;

    // Draw block line positions
    for (int i = -slots / 2; i <= slots / 2; i++) {
      MapLocation pos = center.translate(perpDir.dx * i, perpDir.dy * i);
      dot(rc, pos, COLOR_BLOCK_LINE);
    }
  }

  /** Visualize trap ring. */
  public static void visualizeTrapRing(
      RobotController rc, MapLocation kingLoc, int dist, int color) {
    if (!ENABLED || !SHOW_VISUALS) return;
    if (kingLoc == null) return;

    Direction[] dirs = Direction.values();
    for (Direction dir : dirs) {
      if (dir == Direction.CENTER) continue;
      MapLocation trapPos = kingLoc.translate(dir.dx * dist, dir.dy * dist);
      dot(rc, trapPos, color);
    }
  }

  /** Visualize enemy positions. */
  public static void visualizeEnemies(RobotController rc, RobotInfo[] enemies, int count) {
    if (!ENABLED || !SHOW_VISUALS) return;

    for (int i = count; --i >= 0; ) {
      MapLocation loc = enemies[i].getLocation();
      dot(rc, loc, COLOR_ENEMY);
    }
  }

  /** Visualize retreat path. */
  public static void visualizeRetreat(RobotController rc, MapLocation from, MapLocation to) {
    if (!ENABLED || !SHOW_VISUALS) return;

    line(rc, from, to, COLOR_RETREAT);
    dot(rc, to, COLOR_RETREAT);
  }

  /** Visualize ally rescue (defensive ratnap throw toward king). */
  public static void visualizeRescue(RobotController rc, MapLocation from, MapLocation kingLoc) {
    if (!ENABLED || !SHOW_VISUALS) return;

    // Use green to indicate rescue (ally being saved)
    line(rc, from, kingLoc, COLOR_FORMATION);
    dot(rc, kingLoc, COLOR_FORMATION);
  }

  /** Visualize current target. */
  public static void visualizeTarget(RobotController rc, MapLocation from, MapLocation target) {
    if (!ENABLED || !SHOW_VISUALS) return;

    line(rc, from, target, COLOR_TARGET);
    dot(rc, target, COLOR_TARGET);
  }

  // ========================================================================
  // ECONOMY LOGGING
  // ========================================================================

  /**
   * Log gatherer becoming sentry due to safety check. Example:
   * ECON_LOCKOUT:50:12346:reason=SAFE_THRESHOLD:safeRounds=5:threshold=20:distToKing=12
   */
  public static void logEconomyLockout(
      int round, int id, String reason, int safeRounds, int threshold, int distToKing) {
    if (!ENABLED || !LOG_ECONOMY) return;

    sb.setLength(0);
    sb.append("ECON_LOCKOUT:")
        .append(round)
        .append(":")
        .append(id)
        .append(":reason=")
        .append(reason)
        .append(":safeRounds=")
        .append(safeRounds)
        .append(":threshold=")
        .append(threshold)
        .append(":distToKing=")
        .append(distToKing);
    System.out.println(sb.toString());
  }

  /**
   * Log cheese collection attempt. Example:
   * ECON_COLLECT:55:12346:success=true:amount=1:pos=[10,15]:totalCarrying=1
   */
  // Track last cheese collect/deliver log to reduce spam
  private static int lastCheeseCollectRound = -100;

  private static int lastCheeseDeliverRound = -100;
  private static final int CHEESE_LOG_INTERVAL = 5;

  public static void logCheeseCollect(
      int round, int id, boolean success, int x, int y, int carrying) {
    if (!ENABLED || !LOG_ECONOMY) return;
    if (round - lastCheeseCollectRound < CHEESE_LOG_INTERVAL) return;
    lastCheeseCollectRound = round;

    sb.setLength(0);
    sb.append("ECON_COLLECT:")
        .append(round)
        .append(":")
        .append(id)
        .append(":success=")
        .append(success)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":carrying=")
        .append(carrying);
    System.out.println(sb.toString());
  }

  /** Log cheese delivery. Example: ECON_DELIVER:60:12346:amount=1:kingCheese=150:distToKing=2 */
  public static void logCheeseDeliver(
      int round, int id, int amount, int kingCheese, int distToKing) {
    if (!ENABLED || !LOG_ECONOMY) return;
    if (round - lastCheeseDeliverRound < CHEESE_LOG_INTERVAL) return;
    lastCheeseDeliverRound = round;

    sb.setLength(0);
    sb.append("ECON_DELIVER:")
        .append(round)
        .append(":")
        .append(id)
        .append(":amount=")
        .append(amount)
        .append(":kingCheese=")
        .append(kingCheese)
        .append(":distToKing=")
        .append(distToKing);
    System.out.println(sb.toString());
  }

  // Track last economy summary log
  private static int lastEconSummaryRound = -100;
  private static final int ECON_SUMMARY_INTERVAL = 10;

  /**
   * Log king economy summary. Example:
   * ECON_SUMMARY:100:12345:cheese=2500:spawns=15:cheesePerRat=166:safeRounds=25:threat=0
   */
  public static void logEconomySummary(
      int round, int id, int cheese, int spawns, int safeRounds, int threat) {
    if (!ENABLED || !LOG_ECONOMY) return;
    if (round - lastEconSummaryRound < ECON_SUMMARY_INTERVAL) return;
    lastEconSummaryRound = round;

    int cheesePerRat = (spawns > 0) ? (cheese / spawns) : cheese;
    sb.setLength(0);
    sb.append("ECON_SUMMARY:")
        .append(round)
        .append(":")
        .append(id)
        .append(":cheese=")
        .append(cheese)
        .append(":spawns=")
        .append(spawns)
        .append(":cheesePerRat=")
        .append(cheesePerRat)
        .append(":safeRounds=")
        .append(safeRounds)
        .append(":threat=")
        .append(threat);
    System.out.println(sb.toString());
  }

  /**
   * Log gatherer target decision. Example:
   * ECON_TARGET:50:12346:role=GATHER:target=CHEESE:dist=8:cheeseVisible=3:acting_as=GATHER
   */
  public static void logGathererTarget(
      int round, int id, String targetType, int distToTarget, int cheeseVisible, String actingAs) {
    if (!ENABLED || !LOG_ECONOMY) return;

    sb.setLength(0);
    sb.append("ECON_TARGET:")
        .append(round)
        .append(":")
        .append(id)
        .append(":target=")
        .append(targetType)
        .append(":dist=")
        .append(distToTarget)
        .append(":cheeseVisible=")
        .append(cheeseVisible)
        .append(":acting_as=")
        .append(actingAs);
    System.out.println(sb.toString());
  }

  // ========================================================================
  // PHASE NAME HELPERS
  // ========================================================================

  public static String phaseName(int phase) {
    switch (phase) {
      case 1:
        return "BUILD";
      case 2:
        return "DEF";
      case 3:
        return "ATK";
      default:
        return "???";
    }
  }

  public static String roleName(int role) {
    switch (role) {
      case 0:
        return "GATHER";
      case 1:
        return "SENTRY";
      case 2:
        return "SCOUT";
      default:
        return "???";
    }
  }

  public static String targetName(int targetType) {
    switch (targetType) {
      case 0:
        return "NONE";
      case 1:
        return "E_KING";
      case 2:
        return "E_RAT";
      case 3:
        return "CHEESE";
      case 4:
        return "DELIVER";
      case 5:
        return "PATROL";
      case 6:
        return "EXPLORE";
      default:
        return "???";
    }
  }

  // ========================================================================
  // BYTECODE OPTIMIZATION HELPERS
  // ========================================================================

  /**
   * Integer square root - avoids expensive Math.sqrt() and cast overhead. Uses Newton's method for
   * fast approximation.
   *
   * @param n The value to take square root of (squared distance)
   * @return Integer approximation of sqrt(n)
   */
  public static int intSqrt(int n) {
    if (n <= 0) return 0;
    if (n == 1) return 1;

    // Newton's method with integer arithmetic
    int x = n;
    int y = (x + 1) >> 1; // Initial guess: (n+1)/2
    while (y < x) {
      x = y;
      y = (x + n / x) >> 1;
    }
    return x;
  }
}
