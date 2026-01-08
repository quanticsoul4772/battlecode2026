package ratbot.logging;

import battlecode.common.*;

/**
 * Zero-allocation logging system for Battlecode 2026.
 *
 * <p>Provides structured logging for match analysis without bytecode overhead. Uses System.out for
 * visibility in match replays.
 *
 * <p>Log Categories: - STATE: Per-robot state tracking - ECONOMY: Team cheese management - SPAWN:
 * Unit creation events - COMBAT: Attack and damage tracking - CAT: Cat position and behavior
 * tracking - BACKSTAB: Game state transition - CHEESE: Cheese collection and transfer - TACTICAL:
 * Decision-making context - PROFILE: Bytecode usage profiling
 *
 * <p>Format: {CATEGORY}:{round}:{type}:{id}:{key1}={value1}:{key2}={value2}:...
 *
 * <p>Designed for zero allocation - reuses StringBuilder.
 */
public class Logger {

  // Reusable StringBuilder for zero allocation
  private static StringBuilder sb = new StringBuilder(256);

  /**
   * Log robot state snapshot.
   *
   * <p>Example: STATE:100:BABY_RAT:12345:pos=[15,15]:facing=NORTH:hp=85:rawCheese=12:mode=COLLECT
   */
  public static void logState(
      int round,
      String unitType,
      int id,
      int x,
      int y,
      String facing,
      int hp,
      int rawCheese,
      String mode) {
    sb.setLength(0);
    sb.append("STATE:")
        .append(round)
        .append(":")
        .append(unitType)
        .append(":")
        .append(id)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":facing=")
        .append(facing)
        .append(":hp=")
        .append(hp)
        .append(":rawCheese=")
        .append(rawCheese)
        .append(":mode=")
        .append(mode);
    System.out.println(sb.toString());
  }

  /**
   * Log team economy state.
   *
   * <p>Example: ECONOMY:100:globalCheese=2340:cheeseIncome=8:kings=2:babyRats=15:transferred=450
   */
  public static void logEconomy(
      int round,
      int globalCheese,
      int cheeseIncome,
      int kingCount,
      int babyRatCount,
      int cheeseTransferred) {
    sb.setLength(0);
    sb.append("ECONOMY:")
        .append(round)
        .append(":globalCheese=")
        .append(globalCheese)
        .append(":cheeseIncome=")
        .append(cheeseIncome)
        .append(":kings=")
        .append(kingCount)
        .append(":babyRats=")
        .append(babyRatCount)
        .append(":transferred=")
        .append(cheeseTransferred);
    System.out.println(sb.toString());
  }

  /**
   * Log unit spawning.
   *
   * <p>Example: SPAWN:100:KING:12345:pos=[15,15]:cost=20:totalRats=8
   */
  public static void logSpawn(
      int round, String spawnerType, int spawnerId, int x, int y, int cost, int totalBabyRats) {
    sb.setLength(0);
    sb.append("SPAWN:")
        .append(round)
        .append(":")
        .append(spawnerType)
        .append(":")
        .append(spawnerId)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":cost=")
        .append(cost)
        .append(":totalRats=")
        .append(totalBabyRats);
    System.out.println(sb.toString());
  }

  /**
   * Log combat action.
   *
   * <p>Example:
   * COMBAT:100:BABY_RAT:12345:from=[10,10]:target=[11,11]:damage=10:cheeseSpent=0:targetHP=90
   */
  public static void logCombat(
      int round,
      String attackerType,
      int attackerId,
      int fromX,
      int fromY,
      int targetX,
      int targetY,
      int damage,
      int cheeseSpent,
      int targetHP) {
    sb.setLength(0);
    sb.append("COMBAT:")
        .append(round)
        .append(":")
        .append(attackerType)
        .append(":")
        .append(attackerId)
        .append(":from=[")
        .append(fromX)
        .append(",")
        .append(fromY)
        .append("]")
        .append(":target=[")
        .append(targetX)
        .append(",")
        .append(targetY)
        .append("]")
        .append(":damage=")
        .append(damage)
        .append(":cheeseSpent=")
        .append(cheeseSpent)
        .append(":targetHP=")
        .append(targetHP);
    System.out.println(sb.toString());
  }

  /**
   * Log cat tracking.
   *
   * <p>Example: CAT:100:id=1:pos=[15,15]:hp=9500:mode=ATTACK:target=[12,13]
   */
  public static void logCat(
      int round, int catId, int x, int y, int hp, String mode, int targetX, int targetY) {
    sb.setLength(0);
    sb.append("CAT:")
        .append(round)
        .append(":id=")
        .append(catId)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":hp=")
        .append(hp)
        .append(":mode=")
        .append(mode)
        .append(":target=[")
        .append(targetX)
        .append(",")
        .append(targetY)
        .append("]");
    System.out.println(sb.toString());
  }

  /**
   * Log backstab trigger.
   *
   * <p>Example:
   * BACKSTAB:350:our_catDmg=6500:enemy_catDmg=3500:our_kings=3:enemy_kings=2:decision=BACKSTAB
   */
  public static void logBackstab(
      int round,
      int ourCatDamage,
      int enemyCatDamage,
      int ourKings,
      int enemyKings,
      String decision) {
    sb.setLength(0);
    sb.append("BACKSTAB:")
        .append(round)
        .append(":our_catDmg=")
        .append(ourCatDamage)
        .append(":enemy_catDmg=")
        .append(enemyCatDamage)
        .append(":our_kings=")
        .append(ourKings)
        .append(":enemy_kings=")
        .append(enemyKings)
        .append(":decision=")
        .append(decision);
    System.out.println(sb.toString());
  }

  /**
   * Log cheese collection event.
   *
   * <p>Example: CHEESE:100:COLLECT:12345:pos=[10,12]:amount=5:total=17:mine=[10,10]
   */
  public static void logCheeseCollect(
      int round, int ratId, int x, int y, int amount, int totalRawCheese, int mineX, int mineY) {
    sb.setLength(0);
    sb.append("CHEESE:")
        .append(round)
        .append(":COLLECT:")
        .append(ratId)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":amount=")
        .append(amount)
        .append(":total=")
        .append(totalRawCheese)
        .append(":mine=[")
        .append(mineX)
        .append(",")
        .append(mineY)
        .append("]");
    System.out.println(sb.toString());
  }

  /**
   * Log cheese transfer to king.
   *
   * <p>Example: CHEESE:100:TRANSFER:12345:amount=25:king=[15,15]:globalCheese=2550
   */
  public static void logCheeseTransfer(
      int round, int ratId, int amount, int kingX, int kingY, int globalCheeseAfter) {
    sb.setLength(0);
    sb.append("CHEESE:")
        .append(round)
        .append(":TRANSFER:")
        .append(ratId)
        .append(":amount=")
        .append(amount)
        .append(":king=[")
        .append(kingX)
        .append(",")
        .append(kingY)
        .append("]")
        .append(":globalCheese=")
        .append(globalCheeseAfter);
    System.out.println(sb.toString());
  }

  /**
   * Log tactical decision context.
   *
   * <p>Example:
   * TACTICAL:100:BABY_RAT:12345:visibleEnemies=2:visibleCats=1:nearestThreat=3:decision=FLEE
   */
  public static void logTactical(
      int round,
      String unitType,
      int id,
      int visibleEnemies,
      int visibleCats,
      int nearestThreatDist,
      String decision) {
    sb.setLength(0);
    sb.append("TACTICAL:")
        .append(round)
        .append(":")
        .append(unitType)
        .append(":")
        .append(id)
        .append(":visibleEnemies=")
        .append(visibleEnemies)
        .append(":visibleCats=")
        .append(visibleCats)
        .append(":nearestThreat=")
        .append(nearestThreatDist)
        .append(":decision=")
        .append(decision);
    System.out.println(sb.toString());
  }

  /**
   * Log bytecode profiling.
   *
   * <p>Example: PROFILE:100:12345:pathfinding:2450
   */
  public static void logProfile(int round, int id, String section, int bytecodeUsed) {
    sb.setLength(0);
    sb.append("PROFILE:")
        .append(round)
        .append(":")
        .append(id)
        .append(":")
        .append(section)
        .append(":")
        .append(bytecodeUsed);
    System.out.println(sb.toString());
  }

  /**
   * Log ratnapping event.
   *
   * <p>Example: RATNAP:100:carrier=12345:target=12346:pos=[10,10]:action=GRAB
   */
  public static void logRatnap(
      int round, int carrierId, int targetId, int x, int y, String action) {
    sb.setLength(0);
    sb.append("RATNAP:")
        .append(round)
        .append(":carrier=")
        .append(carrierId)
        .append(":target=")
        .append(targetId)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":action=")
        .append(action);
    System.out.println(sb.toString());
  }

  /**
   * Log king starvation warning.
   *
   * <p>Example: WARNING:100:KING:12345:globalCheese=8:roundsLeft=2:hp=480
   */
  public static void logKingWarning(
      int round, int kingId, int globalCheese, int roundsOfCheeseLeft, int kingHP) {
    sb.setLength(0);
    sb.append("WARNING:")
        .append(round)
        .append(":KING:")
        .append(kingId)
        .append(":globalCheese=")
        .append(globalCheese)
        .append(":roundsLeft=")
        .append(roundsOfCheeseLeft)
        .append(":hp=")
        .append(kingHP);
    System.out.println(sb.toString());
  }

  /**
   * Log trap placement.
   *
   * <p>Example: TRAP:100:RAT_TRAP:12345:pos=[12,14]:cost=5:total=12
   */
  public static void logTrap(
      int round, String trapType, int placerId, int x, int y, int cost, int totalTraps) {
    sb.setLength(0);
    sb.append("TRAP:")
        .append(round)
        .append(":")
        .append(trapType)
        .append(":")
        .append(placerId)
        .append(":pos=[")
        .append(x)
        .append(",")
        .append(y)
        .append("]")
        .append(":cost=")
        .append(cost)
        .append(":total=")
        .append(totalTraps);
    System.out.println(sb.toString());
  }

  /**
   * Log error or exceptional condition.
   *
   * <p>Example: ERROR:100:BABY_RAT:12345:type=PATHFINDING_FAILED:context=target=[20,20]
   */
  public static void logError(
      int round, String unitType, int id, String errorType, String context) {
    sb.setLength(0);
    sb.append("ERROR:")
        .append(round)
        .append(":")
        .append(unitType)
        .append(":")
        .append(id)
        .append(":type=")
        .append(errorType)
        .append(":context=")
        .append(context);
    System.out.println(sb.toString());
  }
}
