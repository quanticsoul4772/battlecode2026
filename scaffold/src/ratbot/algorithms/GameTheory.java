package ratbot.algorithms;

import battlecode.common.*;

/**
 * Game theory and strategic decision-making for Battlecode 2026.
 *
 * <p>Core mechanic: Cooperation vs Backstabbing - Cooperation scoring: 50% cat damage + 30% kings +
 * 20% cheese - Backstabbing scoring: 30% cat damage + 50% kings + 20% cheese
 *
 * <p>Optimal backstab timing is a key strategic decision. Standalone module for pre-scaffold
 * analysis.
 */
public class GameTheory {

  /**
   * Calculate cooperation mode score.
   *
   * @param ourCatDamage Damage we did to cats
   * @param enemyCatDamage Damage enemy did to cats
   * @param ourKings Number of our living rat kings
   * @param enemyKings Number of enemy living rat kings
   * @param ourCheese Cheese we transferred to kings
   * @param enemyCheese Cheese enemy transferred to kings
   * @return Our score in cooperation mode
   */
  public static int scoreCooperation(
      int ourCatDamage,
      int enemyCatDamage,
      int ourKings,
      int enemyKings,
      int ourCheese,
      int enemyCheese) {
    // Avoid division by zero
    int totalDamage = ourCatDamage + enemyCatDamage;
    int totalKings = ourKings + enemyKings;
    int totalCheese = ourCheese + enemyCheese;

    double damagePct = totalDamage > 0 ? (double) ourCatDamage / totalDamage : 0.0;
    double kingsPct = totalKings > 0 ? (double) ourKings / totalKings : 0.0;
    double cheesePct = totalCheese > 0 ? (double) ourCheese / totalCheese : 0.0;

    // Equation 1: 0.5 * cat + 0.3 * kings + 0.2 * cheese
    return (int) Math.round(50.0 * damagePct + 30.0 * kingsPct + 20.0 * cheesePct);
  }

  /**
   * Calculate backstabbing mode score. Assumes we eventually win the king battle (optimistic).
   *
   * @param ourCatDamage Damage we did to cats
   * @param enemyCatDamage Damage enemy did to cats
   * @param ourKings Number of our living rat kings
   * @param enemyKings Number of enemy living rat kings
   * @param ourCheese Cheese we transferred to kings
   * @param enemyCheese Cheese enemy transferred to kings
   * @return Our estimated score in backstabbing mode
   */
  public static int scoreBackstabbing(
      int ourCatDamage,
      int enemyCatDamage,
      int ourKings,
      int enemyKings,
      int ourCheese,
      int enemyCheese) {
    int totalDamage = ourCatDamage + enemyCatDamage;
    int totalCheese = ourCheese + enemyCheese;

    double damagePct = totalDamage > 0 ? (double) ourCatDamage / totalDamage : 0.0;
    double cheesePct = totalCheese > 0 ? (double) ourCheese / totalCheese : 0.0;

    // Equation 2: 0.3 * cat + 0.5 * kings + 0.2 * cheese
    // Assume we win kings battle (100% kings for us)
    double kingsPct = 1.0; // Optimistic assumption

    return (int) Math.round(30.0 * damagePct + 50.0 * kingsPct + 20.0 * cheesePct);
  }

  /**
   * Decide if we should backstab now.
   *
   * @param ourCatDamage Our cat damage
   * @param enemyCatDamage Enemy cat damage
   * @param ourKings Our living kings
   * @param enemyKings Enemy living kings
   * @param ourCheese Our transferred cheese
   * @param enemyCheese Enemy transferred cheese
   * @param confidenceThreshold How confident we need to be (points above coop score)
   * @return true if backstabbing expected to score higher
   */
  public static boolean shouldBackstab(
      int ourCatDamage,
      int enemyCatDamage,
      int ourKings,
      int enemyKings,
      int ourCheese,
      int enemyCheese,
      int confidenceThreshold) {
    int coopScore =
        scoreCooperation(
            ourCatDamage, enemyCatDamage,
            ourKings, enemyKings,
            ourCheese, enemyCheese);

    int backstabScore =
        scoreBackstabbing(
            ourCatDamage, enemyCatDamage,
            ourKings, enemyKings,
            ourCheese, enemyCheese);

    // Only backstab if significantly better
    return backstabScore > coopScore + confidenceThreshold;
  }

  /**
   * Evaluate backstab decision with safety checks.
   *
   * @param state Current game state
   * @return Recommendation with confidence level
   */
  public static BackstabRecommendation evaluate(GameState state) {
    // Safety checks first
    if (state.ourKings == 0) {
      return new BackstabRecommendation(false, 0, "We have no kings");
    }

    if (state.ourKings < state.enemyKings) {
      return new BackstabRecommendation(false, 0, "Enemy has more kings");
    }

    if (state.round < 200) {
      return new BackstabRecommendation(false, 0, "Too early - cats still strong");
    }

    // Compute scores
    int coopScore =
        scoreCooperation(
            state.ourCatDamage, state.enemyCatDamage,
            state.ourKings, state.enemyKings,
            state.ourCheese, state.enemyCheese);

    int backstabScore =
        scoreBackstabbing(
            state.ourCatDamage, state.enemyCatDamage,
            state.ourKings, state.enemyKings,
            state.ourCheese, state.enemyCheese);

    int delta = backstabScore - coopScore;

    // Decision thresholds
    if (delta > 20) {
      return new BackstabRecommendation(true, delta, "Strong advantage (" + delta + " points)");
    } else if (delta > 10) {
      return new BackstabRecommendation(true, delta, "Moderate advantage (" + delta + " points)");
    } else if (delta > 5 && state.ourKings > state.enemyKings) {
      return new BackstabRecommendation(
          true, delta, "King advantage (" + state.ourKings + " vs " + state.enemyKings + ")");
    } else {
      return new BackstabRecommendation(
          false, delta, "Not enough advantage (" + delta + " points)");
    }
  }

  /**
   * Simulate optimal backstab timing over game. Returns round with maximum expected score delta.
   *
   * @param catHP Cat HP at various rounds
   * @param kingCounts King counts over time
   * @return Optimal round to backstab
   */
  public static int optimalBackstabRound(int[] catHP, int[] kingCounts) {
    int bestRound = -1;
    int bestDelta = Integer.MIN_VALUE;

    // Simulate each possible backstab round
    for (int round = 200; round < 2000; round += 50) {
      // Estimate state at that round
      int catDmg = 10000 - catHP[round];
      int kings = kingCounts[round];

      // Assume 50/50 split on cat damage
      int ourDmg = catDmg / 2;
      int enemyDmg = catDmg / 2;

      int coop = scoreCooperation(ourDmg, enemyDmg, kings, kings, 1000, 1000);
      int backstab = scoreBackstabbing(ourDmg, enemyDmg, kings, 0, 1000, 1000);

      int delta = backstab - coop;
      if (delta > bestDelta) {
        bestDelta = delta;
        bestRound = round;
      }
    }

    return bestRound;
  }

  /** Backstab recommendation with reasoning. */
  public static class BackstabRecommendation {
    public final boolean shouldBackstab;
    public final int scoreDelta;
    public final String reasoning;

    public BackstabRecommendation(boolean shouldBackstab, int scoreDelta, String reasoning) {
      this.shouldBackstab = shouldBackstab;
      this.scoreDelta = scoreDelta;
      this.reasoning = reasoning;
    }
  }

  /** Game state snapshot for decision-making. */
  public static class GameState {
    public int round;
    public int ourCatDamage;
    public int enemyCatDamage;
    public int ourKings;
    public int enemyKings;
    public int ourCheese;
    public int enemyCheese;

    public GameState(
        int round,
        int ourCatDmg,
        int enemyCatDmg,
        int ourKings,
        int enemyKings,
        int ourCheese,
        int enemyCheese) {
      this.round = round;
      this.ourCatDamage = ourCatDmg;
      this.enemyCatDamage = enemyCatDmg;
      this.ourKings = ourKings;
      this.enemyKings = enemyKings;
      this.ourCheese = ourCheese;
      this.enemyCheese = enemyCheese;
    }
  }

  /**
   * Estimate cheese value per damage to cat. Helps decide if spending cheese on bite enhancement is
   * worth it.
   *
   * @param cheeseAmount Cheese to spend
   * @return Additional damage gained
   */
  public static int cheeseToExtraDamage(int cheeseAmount) {
    if (cheeseAmount <= 0) return 0;
    // Formula: ceil(log(X))
    return (int) Math.ceil(Math.log(cheeseAmount) / Math.log(2));
  }

  /**
   * Evaluate if spending cheese on bite is worth it.
   *
   * @param cheeseAmount Cheese to spend
   * @param targetHP Target's current HP
   * @param baseDamage Base damage (10 for bite)
   * @return true if it would finish the kill
   */
  public static boolean worthEnhancingBite(int cheeseAmount, int targetHP, int baseDamage) {
    int totalDamage = baseDamage + cheeseToExtraDamage(cheeseAmount);
    return totalDamage >= targetHP; // Finish kill
  }
}
