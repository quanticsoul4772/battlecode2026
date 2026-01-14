package ratbot8;

import battlecode.common.*;

/**
 * Ratbot8 - "Battle Intelligence"
 *
 * <h2>Philosophy</h2>
 *
 * "We can survive anything. Now we choose WHEN to attack."
 *
 * <h2>EASY TUNING</h2>
 *
 * Change playstyle by editing ONE line in SECTION 1 (Profiles & Tunable Constants):
 *
 * <ul>
 *   <li>BALANCED (default) - Good all-around performance
 *   <li>AGGRESSIVE - Early pressure, fast attacks, risky economy
 *   <li>DEFENSIVE - Strong defense, safe economy, late attacks
 *   <li>RUSH - All-in early aggression
 *   <li>TURTLE - Maximum defense, late game focus
 * </ul>
 *
 * <h2>Core Architecture</h2>
 *
 * - Situational Value Function with state-based weights - 3 Role System: CORE (10%), FLEX (70%),
 * SPECIALIST (20%) - Game State Machine: SURVIVE → PRESSURE → EXECUTE - Profile-based tuning for
 * easy adjustment
 *
 * <h2>Key Features</h2>
 *
 * - Proven defense from ratbot7 (guardian positioning, starvation prevention) - Strategic offense
 * from ratbot5 (kiting, assassins, focus fire) - Value function architecture from ratbot6 -
 * Bytecode optimized (target: 1200-1600 BC/turn)
 *
 * <h2>Bytecode Optimizations (Phase 4)</h2>
 *
 * - Local variable caching in hot paths (runKing, runBabyRat, scoreAllTargets) - Pre-computed
 * profile-adjusted weights (static initializer) - Unrolled direction loops in bug2MoveTo - Bitmask
 * trap avoidance (adjacentTrapMask) - DX_DY_TO_DIR_ORDINAL lookup table - Backward loops (--i >= 0)
 * for array iteration
 */
public class RobotPlayer {

  // ================================================================
  // SECTION 1: PROFILES & TUNABLE CONSTANTS
  // ================================================================

  // Debug flag - set to false for competition (compile-time elimination)
  private static final boolean DEBUG = true;

  // Profiling flag - set to false for competition
  private static final boolean PROFILE = false;
  private static final int PROFILE_INTERVAL = 50; // Log every N rounds

  // ===================================================================
  // EASY TUNING SYSTEM - Change ONE line to shift entire playstyle!
  // ===================================================================
  //
  // HOW TO USE:
  // 1. Uncomment ONE profile block below (and comment out the others)
  // 2. Rebuild: ./gradlew build
  // 3. The bot will automatically adjust all behaviors based on the profile
  //
  // WHAT EACH WEIGHT AFFECTS:
  // - ATTACK_WEIGHT:  Enemy king/rat targeting, starting commitment level
  // - DEFENSE_WEIGHT: Guardian positioning radius, spawn rate limits
  // - ECONOMY_WEIGHT: Cheese collection priority, spawn reserves
  //
  // ===================================================================

  // PROFILE: Balanced (default) - Good all-around performance
  private static final String PROFILE_NAME = "BALANCED";
  private static final int ATTACK_WEIGHT = 100;
  private static final int DEFENSE_WEIGHT = 100;
  private static final int ECONOMY_WEIGHT = 100;

  // PROFILE: Aggressive - Early pressure, fast attacks, risky economy
  // private static final String PROFILE_NAME = "AGGRESSIVE";
  // private static final int ATTACK_WEIGHT = 150;   // +50% attack priority
  // private static final int DEFENSE_WEIGHT = 70;   // -30% defense (tighter guardians)
  // private static final int ECONOMY_WEIGHT = 80;   // -20% economy (lower reserves)

  // PROFILE: Defensive - Strong defense, safe economy, late attacks
  // private static final String PROFILE_NAME = "DEFENSIVE";
  // private static final int ATTACK_WEIGHT = 60;    // -40% attack priority
  // private static final int DEFENSE_WEIGHT = 150;  // +50% defense (wider guardians)
  // private static final int ECONOMY_WEIGHT = 130;  // +30% economy (higher reserves)

  // PROFILE: Rush - All-in early aggression, minimal economy
  // private static final String PROFILE_NAME = "RUSH";
  // private static final int ATTACK_WEIGHT = 180;   // +80% attack priority
  // private static final int DEFENSE_WEIGHT = 50;   // -50% defense
  // private static final int ECONOMY_WEIGHT = 60;   // -40% economy

  // PROFILE: Turtle - Maximum defense, slow buildup, late game focus
  // private static final String PROFILE_NAME = "TURTLE";
  // private static final int ATTACK_WEIGHT = 40;    // -60% attack priority
  // private static final int DEFENSE_WEIGHT = 180;  // +80% defense
  // private static final int ECONOMY_WEIGHT = 150;  // +50% economy

  // Pre-computed profile-adjusted weights (calculated once at class load)
  // Format: {attack, enemyRat, cheese, delivery, explore}
  private static final int[][] PROFILE_ADJUSTED_WEIGHTS = new int[3][5];

  static {
    // Apply profile weights to base STATE_WEIGHTS
    // STATE_SURVIVE: {50, 30, 150, 200, 0}
    PROFILE_ADJUSTED_WEIGHTS[0][0] = 50 * ATTACK_WEIGHT / 100; // attack
    PROFILE_ADJUSTED_WEIGHTS[0][1] = 30 * ATTACK_WEIGHT / 100; // enemyRat
    PROFILE_ADJUSTED_WEIGHTS[0][2] = 150 * ECONOMY_WEIGHT / 100; // cheese
    PROFILE_ADJUSTED_WEIGHTS[0][3] = 200 * ECONOMY_WEIGHT / 100; // delivery
    PROFILE_ADJUSTED_WEIGHTS[0][4] = 0; // explore

    // STATE_PRESSURE: {100, 100, 100, 100, 50} - Balanced attack and economy
    PROFILE_ADJUSTED_WEIGHTS[1][0] = 100 * ATTACK_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[1][1] = 100 * ATTACK_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[1][2] = 100 * ECONOMY_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[1][3] = 100 * ECONOMY_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[1][4] = 50;

    // STATE_EXECUTE: {250, 80, 50, 80, 30}
    PROFILE_ADJUSTED_WEIGHTS[2][0] = 250 * ATTACK_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[2][1] = 80 * ATTACK_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[2][2] = 50 * ECONOMY_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[2][3] = 80 * ECONOMY_WEIGHT / 100;
    PROFILE_ADJUSTED_WEIGHTS[2][4] = 30;
  }

  // ===== GAME STATE CONSTANTS =====
  private static final int STATE_SURVIVE = 0;
  private static final int STATE_PRESSURE = 1;
  private static final int STATE_EXECUTE = 2;

  // HYSTERESIS THRESHOLDS - Different values for entering vs exiting states
  private static final int SURVIVE_ENTER_HP = 300;
  private static final int SURVIVE_EXIT_HP = 400;
  private static final int SURVIVE_ENTER_CHEESE = 200;
  private static final int SURVIVE_EXIT_CHEESE = 400;
  private static final int SURVIVE_ENTER_THREAT = 6;
  private static final int SURVIVE_EXIT_THREAT = 3;

  private static final int EXECUTE_ENTER_ENEMY_HP = 200;
  private static final int EXECUTE_EXIT_ENEMY_HP = 250;
  private static final int EXECUTE_ENTER_ADVANTAGE = 50;
  private static final int EXECUTE_EXIT_ADVANTAGE = 20;

  // ===== ROLE CONSTANTS =====
  private static final int ROLE_CORE = 0; // 5% - King protection (guardians)
  private static final int ROLE_FLEX = 1; // 75% - Value-driven behavior (attackers/gatherers)
  private static final int ROLE_SPECIALIST = 2; // 20% - Scouts/Raiders/Assassins

  // ===== VALUE FUNCTION CONSTANTS =====
  private static final int TARGET_NONE = 0;
  private static final int TARGET_ENEMY_KING = 1;
  private static final int TARGET_ENEMY_RAT = 2;
  private static final int TARGET_CHEESE = 3;
  private static final int TARGET_DELIVERY = 4;
  private static final int TARGET_PATROL = 5;
  private static final int TARGET_EXPLORE = 6;

  // Base values for target scoring
  private static final int ENEMY_KING_BASE = 200;
  private static final int ENEMY_RAT_BASE = 60;
  private static final int CHEESE_BASE_NORMAL = 100;
  private static final int CHEESE_BASE_LOW = 150;
  private static final int CHEESE_BASE_CRITICAL = 200;
  private static final int DELIVERY_BASE = 150;
  // NOTE: GATHERER_PCT and EMERGENCY_GATHERER_PCT removed - value function handles cheese priority
  private static final int DISTANCE_WEIGHT = 15;

  // State-based weight multipliers: {attack, enemyRat, cheese, delivery, explore}
  private static final int[][] STATE_WEIGHTS = {
    {50, 30, 150, 200, 0}, // STATE_SURVIVE: Prioritize defense and economy
    {100, 100, 100, 100, 50}, // STATE_PRESSURE: Balanced attack and economy
    {250, 80, 50, 80, 30} // STATE_EXECUTE: All-out attack
  };

  // ===== ECONOMY CONSTANTS (PROFILE-ADJUSTED) =====
  // Base reserve is scaled by ECONOMY_WEIGHT: higher = more conservative spawning
  private static final int BASE_CHEESE_RESERVE = 300;
  private static final int ABSOLUTE_CHEESE_RESERVE = BASE_CHEESE_RESERVE * ECONOMY_WEIGHT / 100;
  private static final int SPAWN_CAP_CHEESE_THRESHOLD = 400;
  private static final int SPAWN_CAP_MAX = 50; // Was 30 - allow scaling to match ratbot6
  private static final int SPAWN_CAP_INCREASE_START =
      100; // Increased from 50 - be conservative early game
  private static final int SPAWN_CAP_INCREASE_INTERVAL = 20; // Allow +1 spawn every N rounds

  // ===== ANTI-STARVATION SPAWN THROTTLING (PROFILE-ADJUSTED) =====
  // ECONOMY_WEIGHT affects how conservative we are with cheese
  // TUNED AGGRESSIVE: Now that delivery fix works, we can spawn more freely!
  // The virtuous cycle: more rats → more cheese collected → more delivered → more spawns
  // Early game spawn cap adjusted by ATTACK_WEIGHT: higher attack = more early spawns
  // Minimum of 8 rats ensures we have some army even with defensive profiles
  private static final int BASE_EARLY_GAME_SPAWN_CAP = 25; // Was 20 - establish army faster
  private static final int EARLY_GAME_SPAWN_CAP =
      Math.max(8, BASE_EARLY_GAME_SPAWN_CAP * ATTACK_WEIGHT / 100);
  // Cheese floor adjusted by ECONOMY_WEIGHT: higher economy = higher floor (safer)
  // TUNED AGGRESSIVE: Reduced from 500 to 300 - delivery fix enables more spawning
  private static final int BASE_CHEESE_FLOOR = 300; // Was 500
  private static final int CHEESE_FLOOR = BASE_CHEESE_FLOOR * ECONOMY_WEIGHT / 100;
  // TUNED AGGRESSIVE: Reduced from 3 to 2 - spawn more frequently
  private static final int SPAWN_RATE_LIMIT_ROUNDS =
      2; // Min rounds between spawns when cheese < 800 (was 3)
  private static final int CRITICAL_CHEESE = 150;
  private static final int LOW_CHEESE =
      600; // Balanced threshold - too high makes rats gather instead of attack

  // ===== GUARDIAN CONSTANTS (PROFILE-ADJUSTED) =====
  // Guardian radius scaled by DEFENSE_WEIGHT: higher = wider defensive perimeter
  // Minimum bounds prevent guardians from standing on top of king with extreme profiles
  private static final int BASE_GUARDIAN_INNER_DIST_SQ = 5;
  private static final int BASE_GUARDIAN_OUTER_DIST_SQ = 13;
  private static final int GUARDIAN_INNER_DIST_SQ =
      Math.max(2, BASE_GUARDIAN_INNER_DIST_SQ * DEFENSE_WEIGHT / 100);
  private static final int GUARDIAN_OUTER_DIST_SQ =
      Math.max(5, BASE_GUARDIAN_OUTER_DIST_SQ * DEFENSE_WEIGHT / 100);
  private static final int GUARDIAN_ATTACK_RANGE_SQ = 9;

  // ===== DISTANCE CONSTANTS =====
  private static final int DELIVERY_RANGE_SQ = 9;
  private static final int HOME_TERRITORY_RADIUS_SQ = 144;
  private static final int CHEESE_DETOUR_RANGE_SQ =
      25; // 5 tiles - max distance to detour for cheese

  // ===== EXPLORATION BONUS CONSTANTS =====  // NOTE: These bonuses are SUBTRACTED from score
  // (lower = better), so they make targets more
  // attractive
  // Values are intentionally small to avoid over-prioritizing our side when cheese is elsewhere
  private static final int EXPLORE_OWN_SIDE_BONUS =
      0; // Disabled - causes regression (rats cluster)
  private static final int EXPLORE_INTERIOR_BONUS = 0; // Disabled - pure distance works better

  // ===== CAT CONSTANTS =====
  private static final int CAT_DANGER_RADIUS_SQ = 100; // 10 tiles - cat is dangerous
  private static final int CAT_FLEE_RADIUS_SQ = 18; // ~4.2 tiles - must flee immediately
  private static final int CAT_CAUTION_RADIUS_SQ = 169; // 13 tiles - be careful

  // ===== ANTI-CROWDING CONSTANTS =====
  private static final int CROWDING_CHECK_RADIUS_SQ = 4; // 2 tiles - check for nearby friendlies
  private static final int CROWDING_THRESHOLD =
      3; // Spread out if more than this many friendlies nearby

  // ===== STARVATION PREVENTION (Phase 2) =====
  // PORTED FROM RATBOT7: Hysteresis thresholds prevent oscillation
  private static final int STARVATION_THRESHOLD =
      200; // Explore AROUND king when globalCheese < this (enter explore mode)
  // TUNED FOR CORRIDORS: Reduced from 500 to 400 for faster return to attack mode
  private static final int STARVATION_EXPLORE_EXIT =
      400; // Stop exploring when globalCheese > this (exit explore mode - hysteresis)
  private static final int STARVATION_WARNING_ROUNDS =
      50; // Balanced threshold - triggers cheese priority when starvation is concerning
  private static final int STARVATION_CRITICAL_ROUNDS = 15;
  private static final int CHEESE_PER_ROUND_KING = 3;
  private static final int KING_VICINITY_DIST_SQ =
      36; // 6 tiles - check near king first during starvation

  // ===== TRAP BUILDING CONSTANTS =====
  private static final int CAT_TRAP_RING_DIST = 2; // Build cat traps 2 tiles from king
  private static final int RAT_TRAP_RING_DIST = 3; // Build rat traps 3 tiles from king
  private static final int MAX_CAT_TRAPS = 4; // Max cat traps to build
  private static final int MAX_RAT_TRAPS = 8; // Max rat traps to build
  private static final int CAT_TRAP_COST = 10; // Cheese cost per cat trap (from spec)
  private static final int RAT_TRAP_COST = 5; // Cheese cost per rat trap (from spec)
  private static final int EARLY_GAME_ROUND = 50; // Early game ends at round 50
  private static final int EARLY_GAME_CHEESE_RESERVE = 75; // Increased from 50 for safer margin

  // ===== EMERGENCY DEFENSE (Phase 2) =====
  private static final int FULL_EMERGENCY_THRESHOLD = 3;
  private static final int PARTIAL_EMERGENCY_THRESHOLD = 1;
  private static final int FORMATION_TIGHT_RADIUS_SQ = 20;

  // ===== KITING (Phase 2 defensive + Phase 3 offensive) =====
  private static final int KITE_STATE_APPROACH = 0;
  private static final int KITE_STATE_ATTACK = 1;
  private static final int KITE_STATE_RETREAT = 2;
  private static final int KITE_RETREAT_DIST_HEALTHY = 1;
  private static final int KITE_RETREAT_DIST_WOUNDED = 2;
  private static final int KITE_RETREAT_DIST_CRITICAL = 3;
  private static final int KITE_ENGAGE_DIST_SQ = 8; // Start kiting when this close
  private static final int HEALTHY_HP_THRESHOLD = 80; // HP above which rats press attack

  // ===== BODY BLOCKING (Phase 2) =====
  private static final int BLOCKING_LINE_DIST = 3;

  // ===== PHASE 3: ASSASSIN/KING RUSH CONSTANTS =====
  private static final int ASSASSIN_DIVISOR = 5; // 20% of specialists are assassins
  private static final int LARGE_MAP_ASSASSIN_DIVISOR = 2; // 50% on large maps
  private static final int KING_PROXIMITY_BYPASS_DIST_SQ = 144; // Within 12 tiles, bypass baby rats
  private static final int LARGE_MAP_KING_BYPASS_DIST_SQ = 400; // Within 20 tiles on large maps
  private static final int KING_ATTACK_PRIORITY_DIST_SQ = 25; // Within 5 tiles, always attack king

  // ===== PHASE 3: ALL-IN ATTACK CONSTANTS =====
  private static final int ALL_IN_HP_THRESHOLD =
      200; // Trigger all-in when enemy king HP < this (40%)
  private static final int ALL_IN_SIGNAL_DURATION = 100; // All-in lasts this many rounds
  private static final int ALL_IN_MIN_ATTACKERS = 5; // Need at least this many attackers
  private static final int ALL_IN_MIN_ROUND =
      20; // Don't all-in before this round (earlier aggression for exposed-king maps)

  // ===== PHASE 3: RACE MODE CONSTANTS =====
  private static final int RACE_DEFEND_MODE = 1; // Defend when we lose the race
  private static final int RACE_ATTACK_MODE = 2; // Attack when we win the race
  private static final int BOTH_KINGS_LOW_THRESHOLD = 150; // Consider king "low" below this HP

  // ===== PHASE 3: ENEMY KING HP TRACKING =====
  private static final int ENEMY_KING_STARTING_HP = 500;
  private static final int BASE_ATTACK_DAMAGE = 10;

  // ===== PHASE 3: FOCUS FIRE CONSTANTS =====
  private static final int FOCUS_FIRE_BONUS = 80;
  private static final int FOCUS_FIRE_STALE_ROUNDS = 2;

  // ===== PHASE 3: OVERKILL PREVENTION =====
  private static final int OVERKILL_HP_THRESHOLD = 20;

  // ===== PHASE 3: CHARGE MODE =====
  private static final int CHARGE_MODE_DIST_SQ = 16; // Ignore traps within 4 tiles of enemy king

  // ===== PHASE 3: OPPONENT CLASSIFICATION =====
  private static final int OPPONENT_UNKNOWN = 0;
  private static final int OPPONENT_RUSHING = 1;
  private static final int OPPONENT_TURTLING = 2;
  private static final int OPPONENT_BALANCED = 3;
  private static final int OPPONENT_DESPERATE = 4;
  private static final int RUSH_DETECTION_ROUND = 30;
  private static final int RUSH_ENEMY_THRESHOLD = 3;
  private static final int TURTLE_DETECTION_ROUND = 50;
  private static final int TURTLE_ENEMY_THRESHOLD = 2;

  // ===== STRATEGIC ATTACK INTELLIGENCE: COMMITMENT LEVELS =====
  // Graduated attack commitment - controls what % of army attacks vs gathers
  // Escalate based on success, de-escalate when threatened
  private static final int COMMITMENT_DEFEND = 0; // 10% - Minimal offense, protect king
  private static final int COMMITMENT_PROBE = 1; // 20% - Scouts only, gather intel
  private static final int COMMITMENT_RAID = 2; // 40% - Economy harassment
  private static final int COMMITMENT_ASSAULT = 3; // 60% - Serious attack push
  private static final int COMMITMENT_ALL_IN = 4; // 100% - Kill the enemy king

  // ===== STRATEGIC ATTACK INTELLIGENCE: POST-RUSH PHASES =====
  // When we survive a rush, execute counter-attack sequence
  private static final int POST_RUSH_NONE = 0; // No rush survived
  private static final int POST_RUSH_STABILIZE = 1; // Rounds 0-10: Ensure king safe
  private static final int POST_RUSH_ECONOMY_RAID = 2; // Rounds 10-50: Attack their economy
  private static final int POST_RUSH_ASSAULT_PREP = 3; // Rounds 50-80: Build advantage
  private static final int POST_RUSH_KING_ASSAULT = 4; // When conditions met: Kill king

  // Commitment escalation thresholds
  private static final int KILLS_FOR_RAID = 2; // Escalate to RAID after killing 2 enemies
  private static final int ADVANTAGE_FOR_ASSAULT =
      30; // 30% army advantage for ASSAULT (faster escalation)
  private static final int ECONOMY_WINDOW_THRESHOLD = 800; // Cheese for ECONOMY window

  // ===== BYTECODE OPTIMIZATION: CACHE INTERVALS =====
  private static final int KING_POS_CACHE_INTERVAL =
      2; // Re-read king positions every N rounds (reduced from 5 to prevent stale targeting)
  private static final int DEFENSE_CACHE_INTERVAL = 3; // Re-read defense state every N rounds
  private static final int RING_BUFFER_CACHE_INTERVAL =
      5; // Re-read ring buffer every N rounds (reduced from 10 for better predictive targeting)

  // ===== PHASE 3: ATTACK WINDOWS =====
  private static final int WINDOW_NONE = 0;
  private static final int WINDOW_POST_RUSH = 1;
  private static final int WINDOW_ECONOMY = 2;
  private static final int WINDOW_WOUNDED_KING = 3;
  private static final int WINDOW_ARMY_ADVANTAGE = 4;
  private static final int WINDOW_LATE_GAME = 5;
  private static final int WINDOW_TURTLE_PUNISH = 6;
  private static final int POST_RUSH_WINDOW_DURATION = 50;
  private static final int ECONOMY_WINDOW_CHEESE = 800;
  private static final int WOUNDED_KING_HP = 350; // 70% HP - balanced threshold for attack windows
  private static final int ARMY_ADVANTAGE_THRESHOLD = 3;
  private static final int LATE_GAME_ROUND = 250;

  // ================================================================
  // SECTION 2: SHARED ARRAY SLOT CONSTANTS
  // ================================================================
  //
  // IMPORTANT: Battlecode 2026 shared array values are limited to 0-1023 (10 bits).
  // All values written must respect this limit. Round numbers use "& 1023" masking.
  // Coordinates use 5-bit packing with /2 precision for values > 63.
  //
  // Encoding formats used:
  //   - Raw: Direct value (0-1023)
  //   - HP/8: Health divided by 8, capped at 63 (represents 0-504 HP)
  //   - Coord: Raw map coordinate (0-60 typical, max ~63)
  //   - Packed5: ((x>>1) & 0x1F) | (((y>>1) & 0x1F) << 5) - 5 bits each, /2 precision
  //   - Round10: round & 1023 - 10-bit masked round number
  //   - Offset50: value + 50 - allows negative values (-50 to +973)
  //
  // ┌──────┬─────────────────────────┬──────────┬─────────────────────────────────┐
  // │ Slot │ Name                    │ Format   │ Description                     │
  // ├──────┼─────────────────────────┼──────────┼─────────────────────────────────┤
  // │  0   │ OUR_KING_X              │ Coord    │ Our king's X coordinate         │
  // │  1   │ OUR_KING_Y              │ Coord    │ Our king's Y coordinate         │
  // │  2   │ ENEMY_KING_X            │ Coord    │ Enemy king's X (est. or conf.)  │
  // │  3   │ ENEMY_KING_Y            │ Coord    │ Enemy king's Y (est. or conf.)  │
  // │  4   │ ENEMY_KING_HP           │ HP/8     │ Enemy king HP (0-504)           │
  // │  5   │ (reserved)              │ -        │ Future economy mode             │
  // │  6   │ FOCUS_TARGET            │ Raw      │ Focus fire target robot ID      │
  // │  7   │ FOCUS_HP                │ HP/8     │ Focus target HP                 │
  // │  8   │ FOCUS_ROUND             │ Round10  │ When focus target was updated   │
  // │  9   │ GAME_STATE              │ Raw 0-2  │ 0=SURVIVE, 1=PRESSURE, 2=EXECUTE│
  // │ 10   │ SCOUT_COUNT             │ Raw      │ Count of scout specialists      │
  // │ 11   │ RAIDER_COUNT            │ Raw      │ Count of raider specialists     │
  // │ 12   │ ASSASSIN_COUNT          │ Raw      │ Count of assassin specialists   │
  // │ 13   │ ENEMY_KING_CONFIRMED    │ Raw 0-1  │ 1 if enemy king visually seen   │
  // │ 14   │ CORE_COUNT              │ Raw      │ Count of core guardians         │
  // │ 15   │ THREAT_LEVEL            │ Raw      │ Enemy count near our king       │
  // │ 16   │ ARMY_ADVANTAGE          │ Offset50 │ Our rats - enemy rats (+50)     │
  // │ 17   │ RAT_COUNT               │ Raw      │ (unused)                        │
  // │ 18   │ SPAWN_COUNT             │ Raw      │ Total spawns this game          │
  // │ 19   │ (reserved)              │ -        │ Future delivery tracking        │
  // │ 20   │ OUR_KING_HP             │ HP/8     │ Our king HP (0-504)             │
  // │ 21   │ STARVATION_ROUNDS       │ Raw      │ Rounds until king starves       │
  // │ 22   │ EMERGENCY_LEVEL         │ Raw 0-2  │ 0=none, 1=partial, 2=full       │
  // │ 23   │ ENEMIES_NEAR_KING       │ Raw      │ Enemies within 5 tiles of king  │
  // │ 24   │ BLOCKING_LINE_X         │ Coord    │ Body blocking line center X     │
  // │ 25   │ BLOCKING_LINE_Y         │ Coord    │ Body blocking line center Y     │
  // │ 26   │ BLOCKING_LINE_DIR       │ Raw 0-7  │ Direction ordinal               │
  // │ 27   │ ALL_IN_ROUND            │ Round10  │ When all-in was triggered       │
  // │ 28   │ RACE_MODE               │ Raw 0-2  │ 0=none, 1=defend, 2=attack      │
  // │ 29   │ DAMAGE_TO_ENEMY_KING    │ Raw      │ Cumulative damage dealt (0-500) │
  // │ 30   │ CONFIRMED_ENEMY_HP      │ Raw      │ Enemy king HP from sighting     │
  // │ 31   │ ATTACKERS_NEAR_ENEMY    │ Raw      │ Our rats near enemy king        │
  // │ 32   │ OPPONENT_TYPE           │ Raw 0-4  │ 0=unk,1=rush,2=turtle,3=bal,4=des│
  // │ 33   │ ATTACK_WINDOW           │ Raw 0-6  │ Current attack opportunity type │
  // │ 34   │ RUSH_SURVIVED_ROUND     │ Round10  │ When we survived a rush         │
  // │ 35   │ ENEMIES_SEEN_NEAR_KING  │ Raw      │ Cumulative enemies near king    │
  // │ 36   │ TOTAL_ENEMIES_SEEN      │ Raw      │ Cumulative total enemies        │
  // │ 37   │ ENEMY_RING_0            │ Packed5  │ Predictive targeting buffer [0] │
  // │ 38   │ ENEMY_RING_1            │ Packed5  │ Predictive targeting buffer [1] │
  // │ 39   │ ENEMY_RING_2            │ Packed5  │ Predictive targeting buffer [2] │
  // │ 40   │ ENEMY_RING_3            │ Packed5  │ Predictive targeting buffer [3] │
  // │ 41   │ ENEMY_RING_INDEX        │ Raw 0-3  │ Ring buffer write index         │
  // │ 42   │ CHEESE_LOC_1            │ Packed5  │ Shared cheese location 1        │
  // │ 43   │ CHEESE_LOC_2            │ Packed5  │ Shared cheese location 2        │
  // │ 44   │ CHEESE_LOC_3            │ Packed5  │ Shared cheese location 3        │
  // │ 45   │ CHEESE_ROUND            │ Round10  │ When cheese locs were updated   │
  // │ 46   │ ATTACK_COMMITMENT       │ Raw 0-4  │ Strategic attack commitment lvl │
  // │ 47   │ POST_RUSH_PHASE         │ Raw 0-4  │ Post-rush counter-attack phase  │
  // │ 48   │ KILLS_THIS_GAME         │ Raw      │ Cumulative enemy kills (0-255)  │
  // └──────┴─────────────────────────┴──────────┴─────────────────────────────────┘
  // ================================================================

  // === KING POSITION SLOTS (0-4) ===
  /** Our king's X coordinate. Format: Coord (0-60). */
  private static final int SLOT_OUR_KING_X = 0;

  /** Our king's Y coordinate. Format: Coord (0-60). */
  private static final int SLOT_OUR_KING_Y = 1;

  /** Enemy king's X coordinate (confirmed or estimated). Format: Coord (0-60). */
  private static final int SLOT_ENEMY_KING_X = 2;

  /** Enemy king's Y coordinate (confirmed or estimated). Format: Coord (0-60). */
  private static final int SLOT_ENEMY_KING_Y = 3;

  /** Enemy king's HP divided by 8. Format: HP/8 (0-63, represents 0-504 HP). */
  private static final int SLOT_ENEMY_KING_HP = 4;

  // Slot 5 reserved for future economy mode

  // === FOCUS FIRE SLOTS (6-8) ===
  /** Focus fire target robot ID (masked). Format: Raw (robotID & 1023). */
  private static final int SLOT_FOCUS_TARGET = 6;

  /** Focus fire target HP divided by 8. Format: HP/8 (0-63). */
  private static final int SLOT_FOCUS_HP = 7;

  /** Round when focus target was last updated. Format: Round10 (round & 1023). */
  private static final int SLOT_FOCUS_ROUND = 8;

  // === GAME STATE SLOTS (9-20) ===
  /** Current game state. Format: Raw (0=SURVIVE, 1=PRESSURE, 2=EXECUTE). */
  private static final int SLOT_GAME_STATE = 9;

  /** Count of scout-role specialists. Format: Raw (0-255). */
  private static final int SLOT_SCOUT_COUNT = 10;

  /** Count of raider-role specialists. Format: Raw (0-255). */
  private static final int SLOT_RAIDER_COUNT = 11;

  /** Count of assassin-role specialists. Format: Raw (0-255). */
  private static final int SLOT_ASSASSIN_COUNT = 12;

  /** Whether enemy king position is confirmed (visually sighted). Format: Raw (0=no, 1=yes). */
  private static final int SLOT_ENEMY_KING_CONFIRMED = 13;

  /** Count of core-role guardians. Format: Raw (0-255). */
  private static final int SLOT_CORE_COUNT = 14;

  /** Current threat level (enemy count near king). Format: Raw (0-255). */
  private static final int SLOT_THREAT_LEVEL = 15;

  /** Army advantage (our rats - enemy rats). Format: Offset50 (value + 50, range -50 to +50). */
  private static final int SLOT_ARMY_ADVANTAGE = 16;

  /** Total rat count (unused). Format: Raw (0-255). */
  private static final int SLOT_RAT_COUNT = 17;

  /** Total spawn count this game. Format: Raw (0-1023). */
  private static final int SLOT_SPAWN_COUNT = 18;

  // Slot 19 reserved for future delivery tracking
  /** Our king's HP divided by 8. Format: HP/8 (0-63, represents 0-504 HP). */
  private static final int SLOT_OUR_KING_HP = 20;

  // === PHASE 2 DEFENSE SLOTS (21-26) ===
  /** Rounds until king starvation (globalCheese / 3). Format: Raw (0-255, capped). */
  private static final int SLOT_STARVATION_ROUNDS = 21;

  /** Emergency level. Format: Raw (0=none, 1=partial, 2=full). */
  private static final int SLOT_EMERGENCY_LEVEL = 22;

  /** Count of enemies within 5 tiles of our king. Format: Raw (0-255). */
  private static final int SLOT_ENEMIES_NEAR_KING = 23;

  /** Blocking line center X coordinate. Format: Coord (0-60, 0=no line). */
  private static final int SLOT_BLOCKING_LINE_X = 24;

  /** Blocking line center Y coordinate. Format: Coord (0-60). */
  private static final int SLOT_BLOCKING_LINE_Y = 25;

  /** Blocking line direction ordinal. Format: Raw (0-7, Direction.ordinal()). */
  private static final int SLOT_BLOCKING_LINE_DIR = 26;

  // === PHASE 3 OFFENSE SLOTS (27-31) ===
  /** Round when all-in attack was triggered. Format: Round10 (round & 1023, 0=inactive). */
  private static final int SLOT_ALL_IN_ROUND = 27;

  /** Race mode when both kings low HP. Format: Raw (0=none, 1=defend, 2=attack). */
  private static final int SLOT_RACE_MODE = 28;

  /** Cumulative damage dealt to enemy king. Format: Raw (0-500). */
  private static final int SLOT_DAMAGE_TO_ENEMY_KING = 29;

  /** Confirmed enemy king HP (from direct sighting). Format: Raw (0-500). */
  private static final int SLOT_CONFIRMED_ENEMY_HP = 30;

  /** Count of our rats within 10 tiles of enemy king. Format: Raw (0-255). */
  private static final int SLOT_ATTACKERS_NEAR_ENEMY = 31;

  // === PHASE 3 OPPONENT CLASSIFICATION SLOTS (32-36) ===
  /**
   * Opponent behavior type. Format: Raw (0=unknown, 1=rushing, 2=turtling, 3=balanced,
   * 4=desperate).
   */
  private static final int SLOT_OPPONENT_TYPE = 32;

  /** Current attack window type. Format: Raw (0-6, see WINDOW_* constants). */
  private static final int SLOT_ATTACK_WINDOW = 33;

  /** Round when we survived a rush. Format: Round10 (round & 1023, 0=never rushed). */
  private static final int SLOT_RUSH_SURVIVED_ROUND = 34;

  /** Cumulative enemies seen near our king. Format: Raw (0-255, capped). */
  private static final int SLOT_ENEMIES_SEEN_NEAR_KING = 35;

  /** Cumulative total enemies seen. Format: Raw (0-255, capped). */
  private static final int SLOT_TOTAL_ENEMIES_SEEN = 36;

  // === ENEMY RING BUFFER SLOTS (37-41) ===
  // Circular buffer for predictive targeting - stores recent enemy positions.
  /**
   * Enemy position ring buffer slot 0. Format: Packed5 - ((x>>1) & 0x1F) | (((y>>1) & 0x1F) << 5).
   */
  private static final int SLOT_ENEMY_RING_0 = 37;

  /** Enemy position ring buffer slot 1. Format: Packed5. */
  private static final int SLOT_ENEMY_RING_1 = 38;

  /** Enemy position ring buffer slot 2. Format: Packed5. */
  private static final int SLOT_ENEMY_RING_2 = 39;

  /** Enemy position ring buffer slot 3. Format: Packed5. */
  private static final int SLOT_ENEMY_RING_3 = 40;

  /** Current write index for ring buffer (unused - tracked locally). Format: Raw (0-3). */
  private static final int SLOT_ENEMY_RING_INDEX = 41;

  // === CHEESE LOCATION SHARING SLOTS (42-45) ===
  // Stores up to 3 cheese locations shared via squeaks.
  /** Cheese sighting 1 location. Format: Packed5 - ((x>>1) & 0x1F) | (((y>>1) & 0x1F) << 5). */
  private static final int SLOT_CHEESE_LOC_1 = 42;

  /** Cheese sighting 2 location. Format: Packed5. */
  private static final int SLOT_CHEESE_LOC_2 = 43;

  /** Cheese sighting 3 location. Format: Packed5. */
  private static final int SLOT_CHEESE_LOC_3 = 44;

  /** Round when cheese locations were last updated. Format: Round10 (round & 1023). */
  private static final int SLOT_CHEESE_ROUND = 45;

  /** Current attack commitment level. Format: Raw (0-4, see COMMITMENT_* constants). */
  private static final int SLOT_ATTACK_COMMITMENT = 46;

  /** Current post-rush phase. Format: Raw (0-4, see POST_RUSH_* constants). */
  private static final int SLOT_POST_RUSH_PHASE = 47;

  /** Kills this game (cumulative enemy rats killed). Format: Raw (0-255). */
  private static final int SLOT_KILLS_THIS_GAME = 48;

  // === SHARED ARRAY RELATED CONSTANTS (not slots) ===
  /** Cheese location is considered stale after this many rounds. */
  private static final int CHEESE_STALE_ROUNDS = 50;

  // ================================================================
  // SQUEAK MESSAGE FORMAT DOCUMENTATION
  // ================================================================
  //
  // Squeaks are 32-bit integer messages broadcast by baby rats and read by the king.
  // Baby rats cannot write to the shared array, so squeaks are the only way for them
  // to communicate information (like enemy king sightings) back to the team.
  //
  // All squeak formats use a 4-bit type field in the highest bits to distinguish
  // message types. The remaining 28 bits carry the payload.
  //
  // === TYPE 1: ENEMY KING POSITION ===
  // Purpose: Baby rat reports enemy king sighting with location and HP estimate.
  // Sender: babyRatBroadcastEnemyKing()
  // Receiver: kingReadSqueaks()
  //
  // Bit layout (32 bits total):
  //   [31-28] Type = 1 (4 bits)
  //   [27-16] Y coordinate (12 bits, 0-4095, actual range 0-60)
  //   [15-4]  X coordinate (12 bits, 0-4095, actual range 0-60)
  //   [3-0]   HP estimate (4 bits, value = actualHP / 35, range 0-15 = 0-525 HP)
  //
  // Encoding:  (1 << 28) | (y << 16) | (x << 4) | (hp / 35)
  // Decoding:  type = (content >> 28) & 0xF
  //            y = (content >> 16) & 0xFFF
  //            x = (content >> 4) & 0xFFF
  //            hp = (content & 0xF) * 35
  //
  // === TYPE 2: CHEESE LOCATION ===
  // Purpose: Baby rat reports cheese sighting for other rats to collect.
  // Sender: squeakCheeseLocation()
  // Receiver: kingReadSqueaks()
  //
  // Bit layout (32 bits total):
  //   [31-28] Type = 2 (4 bits)
  //   [27-16] Y coordinate (12 bits, 0-4095, actual range 0-60)
  //   [15-4]  X coordinate (12 bits, 0-4095, actual range 0-60)
  //   [3-0]   Reserved (4 bits, unused, set to 0)
  //
  // Encoding:  (2 << 28) | (y << 16) | (x << 4)
  // Decoding:  type = (content >> 28) & 0xF
  //            y = (content >> 16) & 0xFFF
  //            x = (content >> 4) & 0xFFF
  //
  // Note: Cheese locations are stored in shared array with reduced precision
  // (5 bits per coordinate, /2) to fit within the 10-bit value limit.
  // ================================================================

  // ================================================================
  // SECTION 3: DIRECTION ARRAYS (BYTECODE OPTIMIZED)
  // ================================================================

  private static final Direction[] DIRECTIONS = {
    Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
    Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
  };

  // Pre-computed direction deltas for MapLocation-free arithmetic
  private static final int[] DIR_DX = {0, 1, 1, 1, 0, -1, -1, -1};
  private static final int[] DIR_DY = {1, 1, 0, -1, -1, -1, 0, 1};

  // Lookup table: DX_DY_TO_DIR_ORDINAL[dx+1][dy+1] = direction ordinal (-1 for center)
  private static final int[][] DX_DY_TO_DIR_ORDINAL = {
    {5, 6, 7}, // dx=-1: SW(5), W(6), NW(7)
    {4, -1, 0}, // dx=0: S(4), CENTER(-1), N(0)
    {3, 2, 1} // dx=1: SE(3), E(2), NE(1)
  };

  // Pre-computed perpendicular directions
  private static final Direction[] PERP_LEFT_BY_DIR = new Direction[8];
  private static final Direction[] PERP_RIGHT_BY_DIR = new Direction[8];

  // ================================================================
  // SECTION 4: STATIC FIELDS - Cached Game State
  // ================================================================

  // Cached per-turn state
  private static int cachedRound;
  private static MapLocation myLoc;
  private static int myLocX;
  private static int myLocY;
  private static Direction cachedFacing;
  private static boolean cachedActionReady;
  private static boolean cachedMovementReady;
  private static boolean cachedCarryingCheese;

  // Team info
  private static Team cachedOurTeam;
  private static Team cachedEnemyTeam;

  // King info
  private static MapLocation cachedOurKingLoc;
  private static MapLocation cachedEnemyKingLoc;
  private static int cachedEnemyKingHP = 500;
  private static boolean enemyKingConfirmed = false;
  private static int cachedDistToKingSq = -1;
  private static boolean hasOurKingLoc = false;

  // Game state
  private static int currentGameState = STATE_PRESSURE;
  private static int cachedThreatLevel = 0;
  private static int cachedGlobalCheese = 0;
  private static int cachedOurKingHP = 500;
  private static int cachedArmyAdvantage = 0;
  private static int cachedSpawnCount = 0;

  // Map info
  private static int cachedMapWidth;
  private static int cachedMapHeight;
  private static int cachedMapArea;

  // Target scoring results
  private static MapLocation cachedBestTarget;
  private static int cachedBestTargetType;
  private static int cachedBestScore;

  // Bug2 pathfinding state
  private static MapLocation bug2Target;
  private static boolean bug2WallFollowing = false;
  private static Direction bug2WallDir;
  private static MapLocation bug2StartLoc;
  private static int bug2StartDist;

  // Trap avoidance bitmask
  private static int adjacentTrapMask = 0;

  // Cheese buffer
  private static final MapLocation[] cheeseBuffer = new MapLocation[50];
  private static int cheeseCount = 0;

  // Robot buffers (pre-allocated to avoid allocation per turn)
  private static final RobotInfo[] enemyBuffer = new RobotInfo[100];
  private static final RobotInfo[] allyBuffer = new RobotInfo[100];

  // King state
  private static MapLocation kingSpawnPoint;
  private static int spawnCount = 0;
  private static MapLocation estimatedEnemyKingLoc;
  private static int catTrapsBuilt = 0;
  private static int ratTrapsBuilt = 0;

  // Cached target for proactive movement (avoid allocation each turn)
  private static MapLocation cachedProactiveTarget = null;

  // Initialization flag
  private static boolean initialized = false;

  // Squeak throttle
  private static int lastSqueakRound = -100;
  private static int lastSqueakID = -1;
  private static final int SQUEAK_THROTTLE_ROUNDS = 10;

  // Per-cheese squeak throttle - track WHICH cheese location was squeaked to avoid re-squeaking
  // same location
  private static MapLocation lastSqueakedCheeseLoc = null;

  // Phase 2 defense state
  private static int cachedStarvationRounds = 100;
  private static int cachedEmergencyLevel = 0;
  private static boolean cachedInEmergency = false;
  private static int cachedEnemiesNearKing = 0;
  private static MapLocation cachedBlockingLineCenter = null;
  private static Direction cachedBlockingLineDir = Direction.NORTH;
  private static int kiteState = KITE_STATE_APPROACH;
  private static int kiteRetreatCounter = 0;
  private static int lastKiteTargetId = -1; // Track which enemy we're kiting

  // Phase 3 offense state
  private static int cachedAllInRound = 0;
  private static boolean cachedAllInActive = false;
  private static int cachedRaceMode = 0;
  private static int cachedDamageToEnemyKing = 0;
  private static int cachedAttackersNearEnemy = 0;
  private static int lastConfirmedEnemyKingHP = 500;
  private static int lastConfirmedHPRound = -1;

  // Focus fire state
  private static MapLocation cachedFocusTarget = null;
  private static int cachedFocusTargetRound = -100;

  // Phase 3: Opponent classification state
  private static int cachedOpponentType = OPPONENT_UNKNOWN;
  private static int cachedAttackWindow = WINDOW_NONE;
  private static int enemiesSeenNearKingTotal = 0;
  private static int totalEnemiesSeen = 0;
  private static int rushSurvivedRound = 0;
  private static boolean wasRecentlyRushed = false;

  // Enemy ring buffer for predictive targeting (cached from shared array)
  private static final int[] cachedEnemyRingX = new int[4];
  private static final int[] cachedEnemyRingY = new int[4];
  private static int enemyRingWriteIndex = 0;

  // Strategic Attack Intelligence state
  // Starting commitment is profile-adjusted: higher ATTACK_WEIGHT = more aggressive start
  // Note: getProfileStartingCommitment() is called via static initializer block below
  // to ensure safe initialization order (method defined before use).
  private static int currentAttackCommitment;

  // Static initializer block for profile-dependent initialization
  // This ensures getProfileStartingCommitment() is called after COMMITMENT_* constants are defined
  static {
    currentAttackCommitment = getProfileStartingCommitment();
  }

  /** Get starting commitment level based on ATTACK_WEIGHT profile setting. */
  private static int getProfileStartingCommitment() {
    // Original thresholds - aggressive commitment changes caused regression
    if (ATTACK_WEIGHT >= 150) return COMMITMENT_ASSAULT; // Very aggressive profile
    if (ATTACK_WEIGHT >= 120) return COMMITMENT_RAID; // Aggressive profile
    if (ATTACK_WEIGHT >= 80) return COMMITMENT_PROBE; // Balanced profile
    return COMMITMENT_DEFEND; // Defensive profile
  }

  private static int postRushPhase = POST_RUSH_NONE;
  private static int killsThisGame = 0;

  // Static commitment level names for debug logging (avoid allocation per log)
  private static final String[] COMMITMENT_NAMES = {"DEFEND", "PROBE", "RAID", "ASSAULT", "ALL_IN"};

  // Profile weight names for debug logging
  private static final String PROFILE_WEIGHTS_STR =
      "ATK:" + ATTACK_WEIGHT + " DEF:" + DEFENSE_WEIGHT + " ECO:" + ECONOMY_WEIGHT;

  // Predictive targeting reusable location
  private static MapLocation predictedLoc = null;

  // Pre-computed explore targets for cheese exploration (avoids MapLocation allocation in hot path)
  // 16 targets: 8 edge + 8 interior to ensure full map coverage
  private static final MapLocation[] EXPLORE_TARGETS = new MapLocation[16];

  // Cheese location sharing - last known cheese positions
  private static MapLocation lastCheeseSeenLoc = null;
  private static int lastCheeseSeenRound = -100;
  private static final int LAST_CHEESE_STALE_ROUNDS = 100; // Memory expires after this many rounds

  // Shared cheese locations read from shared array
  private static final MapLocation[] sharedCheeseLocations = new MapLocation[3];
  private static int sharedCheeseRound = -100;

  // Cheese squeak throttle (separate from enemy king squeak)
  private static int lastCheeseSqueak = -100;
  private static final int CHEESE_SQUEAK_THROTTLE = 15; // Squeak cheese location every N rounds

  // ===== DECOY SQUEAK CONSTANTS (cat luring) =====
  // Cats hear squeaks but don't decode them - what matters is WHERE the squeak originates
  // Squeaking in enemy territory lures cats there, away from our gatherers!
  private static final int DECOY_SQUEAK_THROTTLE = 8; // Rounds between decoy squeaks
  private static final int DECOY_SQUEAK_MIN_DIST_FROM_OUR_KING_SQ = 225; // 15 tiles from our king
  private static final int DECOY_SQUEAK_MAX_DIST_FROM_ENEMY_KING_SQ =
      400; // Within 20 tiles of enemy king
  private static final int SUPPRESS_SQUEAK_NEAR_KING_DIST_SQ =
      64; // 8 tiles - suppress squeaks near our king

  // Decoy squeak state - NOTE: Using ID-based throttling instead of shared lastDecoySqueakRound
  // to avoid all rats sharing the same cooldown (each rat squeaks independently)

  // Spiral exploration constants
  private static final int SPIRAL_MAX_STEPS =
      8; // Complete one spiral ring before moving to next target
  private static final int SPIRAL_ROUNDS_PER_STEP = 3; // Rounds to spend at each spiral waypoint

  // Spiral exploration state - tracks when rat first arrived at current explore target
  // Note: These are shared across all rats, so each rat resets the timer when switching targets.
  // This is a "best effort" approach - not perfectly per-rat but avoids complex state management.
  private static int spiralArrivalRound = -100;
  private static int spiralTargetQuadrant = -1;

  // Rotation scanning removed - now uses (ratId + round) % 8 for per-rat independent scanning
  // See tryScanForCheese() for the new algorithm that avoids shared state issues

  // Bytecode optimization: Last read rounds for cached values
  private static int lastKingPosReadRound = -100;
  private static int lastDefenseReadRound = -100;
  private static int lastRingBufferReadRound = -100;

  // PORTED FROM RATBOT7: Cheese hunt target caching for starvation mode
  // getCheeseHuntTarget() spreads rats in 8 sectors AROUND our king where cheese spawns
  private static MapLocation cachedCheeseHuntTarget = null;
  private static MapLocation cachedCheeseHuntKingLoc = null;
  private static int cachedCheeseHuntRound = -100;
  private static int cachedCheeseHuntGroup = -1;
  // Hysteresis flag: true when we should explore for cheese (cheese < 500)
  private static boolean shouldExploreForCheese = false;

  // ================================================================
  // BYTECODE PROFILING
  // ================================================================

  // Profiling counters (reset each turn)
  private static int bcTurnStart = 0;
  private static int bcAfterInit = 0;
  private static int bcAfterSense = 0;
  private static int bcAfterScore = 0;
  private static int bcAfterAction = 0;
  private static int bcAfterMove = 0;
  private static int bcTurnEnd = 0;

  // Cumulative stats (for averaging)
  private static int totalTurns = 0;
  private static long totalBytecode = 0;
  private static int maxBytecode = 0;
  private static int minBytecode = Integer.MAX_VALUE;

  // Section totals for hotspot identification
  private static long bcTotalInit = 0;
  private static long bcTotalSense = 0;
  private static long bcTotalScore = 0;
  private static long bcTotalAction = 0;
  private static long bcTotalMove = 0;
  private static long bcTotalOther = 0;

  // ================================================================
  // SECTION 5: ENTRY POINT
  // ================================================================

  @SuppressWarnings("unused")
  public static void run(RobotController rc) throws GameActionException {
    while (true) {
      try {
        // Start bytecode tracking
        if (PROFILE) {
          bcTurnStart = Clock.getBytecodeNum();
        }

        if (!initialized) {
          initializeRobot(rc);
          initialized = true;
        }

        switch (rc.getType()) {
          case RAT_KING:
            runKing(rc);
            break;
          case BABY_RAT:
            runBabyRat(rc);
            break;
          default:
            break;
        }

        // End bytecode tracking and log
        if (PROFILE) {
          bcTurnEnd = Clock.getBytecodeNum();
          recordBytecodeStats(rc);
        }
      } catch (GameActionException e) {
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ================================================================
  // SECTION 6: INITIALIZATION
  // ================================================================

  private static void initializeRobot(RobotController rc) throws GameActionException {
    cachedOurTeam = rc.getTeam();
    cachedEnemyTeam = cachedOurTeam.opponent();
    cachedMapWidth = rc.getMapWidth();
    cachedMapHeight = rc.getMapHeight();
    cachedMapArea = cachedMapWidth * cachedMapHeight;

    // Pre-compute explore targets for cheese exploration (bytecode optimization)
    // 16 targets: 8 edge + 8 interior for FULL map coverage
    // Interior targets are CRITICAL for maps like 'pipes' where cheese spawns in corridors!
    int midX = cachedMapWidth / 2;
    int midY = cachedMapHeight / 2;
    int q1X = cachedMapWidth / 4; // 1/4 position
    int q3X = 3 * cachedMapWidth / 4; // 3/4 position
    int q1Y = cachedMapHeight / 4;
    int q3Y = 3 * cachedMapHeight / 4;

    // Edge targets (original 8)
    EXPLORE_TARGETS[0] = new MapLocation(cachedMapWidth - 5, midY); // East edge
    EXPLORE_TARGETS[1] = new MapLocation(cachedMapWidth - 5, cachedMapHeight - 5); // NE corner
    EXPLORE_TARGETS[2] = new MapLocation(midX, cachedMapHeight - 5); // North edge
    EXPLORE_TARGETS[3] = new MapLocation(5, cachedMapHeight - 5); // NW corner
    EXPLORE_TARGETS[4] = new MapLocation(5, midY); // West edge
    EXPLORE_TARGETS[5] = new MapLocation(5, 5); // SW corner
    EXPLORE_TARGETS[6] = new MapLocation(midX, 5); // South edge
    EXPLORE_TARGETS[7] = new MapLocation(cachedMapWidth - 5, 5); // SE corner

    // Interior targets (NEW 8) - critical for cheese in corridors/center!
    EXPLORE_TARGETS[8] = new MapLocation(q1X, q1Y); // SW interior quadrant
    EXPLORE_TARGETS[9] = new MapLocation(q3X, q1Y); // SE interior quadrant
    EXPLORE_TARGETS[10] = new MapLocation(q1X, q3Y); // NW interior quadrant
    EXPLORE_TARGETS[11] = new MapLocation(q3X, q3Y); // NE interior quadrant
    EXPLORE_TARGETS[12] = new MapLocation(midX, q1Y); // South center
    EXPLORE_TARGETS[13] = new MapLocation(midX, q3Y); // North center
    EXPLORE_TARGETS[14] = new MapLocation(q1X, midY); // West center
    EXPLORE_TARGETS[15] = new MapLocation(q3X, midY); // East center

    // Initialize enemy king HP tracking
    cachedEnemyKingHP = ENEMY_KING_STARTING_HP;
    lastConfirmedEnemyKingHP = ENEMY_KING_STARTING_HP;
    cachedDamageToEnemyKing = 0;

    // Pre-compute perpendicular directions
    for (int i = 0; i < 8; i++) {
      PERP_LEFT_BY_DIR[i] = DIRECTIONS[(i + 6) & 7]; // rotateLeft().rotateLeft()
      PERP_RIGHT_BY_DIR[i] = DIRECTIONS[(i + 2) & 7]; // rotateRight().rotateRight()
    }

    // Initialize hysteresis flag based on current cheese level
    // This ensures rats spawning mid-game have the correct explore mode state
    int initialCheese = rc.getGlobalCheese();
    if (initialCheese < STARVATION_THRESHOLD) {
      shouldExploreForCheese = true;
    } else if (initialCheese > STARVATION_EXPLORE_EXIT) {
      shouldExploreForCheese = false;
    }
    // If in hysteresis zone (200-500), keep default false - conservative start

    if (rc.getType().isRatKingType()) {
      kingSpawnPoint = rc.getLocation();
      cachedOurKingLoc = kingSpawnPoint;

      // Reset trap counts on king init (handles respawn case)
      catTrapsBuilt = 0;
      ratTrapsBuilt = 0;

      rc.writeSharedArray(SLOT_OUR_KING_X, kingSpawnPoint.x);
      rc.writeSharedArray(SLOT_OUR_KING_Y, kingSpawnPoint.y);
      rc.writeSharedArray(SLOT_GAME_STATE, STATE_PRESSURE);

      // Log profile configuration on startup
      if (DEBUG) {
        System.out.println("==================================================");
        System.out.println("[RATBOT8] Profile: " + PROFILE_NAME);
        System.out.println("[RATBOT8] Weights: " + PROFILE_WEIGHTS_STR);
        System.out.println(
            "[RATBOT8] Starting commitment: " + COMMITMENT_NAMES[currentAttackCommitment]);
        System.out.println(
            "[RATBOT8] Guardian radius: inner="
                + GUARDIAN_INNER_DIST_SQ
                + " outer="
                + GUARDIAN_OUTER_DIST_SQ);
        System.out.println("[RATBOT8] Cheese reserve: " + ABSOLUTE_CHEESE_RESERVE);
        System.out.println("==================================================");
      }

      // Calculate estimated enemy king position (rotational symmetry)
      estimatedEnemyKingLoc =
          new MapLocation(
              cachedMapWidth - kingSpawnPoint.x - 1, cachedMapHeight - kingSpawnPoint.y - 1);
      rc.writeSharedArray(SLOT_ENEMY_KING_X, estimatedEnemyKingLoc.x);
      rc.writeSharedArray(SLOT_ENEMY_KING_Y, estimatedEnemyKingLoc.y);
    }
  }

  // ================================================================
  // SECTION 7: GAME STATE MANAGEMENT
  // ================================================================

  private static void updateGameState(RobotController rc) throws GameActionException {
    cachedRound = rc.getRoundNum();
    myLoc = rc.getLocation();
    myLocX = myLoc.x;
    myLocY = myLoc.y;

    cachedFacing = rc.getDirection();
    cachedActionReady = rc.isActionReady();
    cachedMovementReady = rc.isMovementReady();

    // Reset kite state each turn if no target locked
    if (kiteState == KITE_STATE_RETREAT && kiteRetreatCounter <= 0) {
      kiteState = KITE_STATE_APPROACH;
    }
    cachedCarryingCheese = rc.getRawCheese() > 0;
    cachedGlobalCheese = rc.getGlobalCheese();

    // === BYTECODE OPTIMIZATION: Only read king positions every N rounds ===
    // King positions rarely change, so we can cache them
    boolean shouldReadKingPos = (cachedRound - lastKingPosReadRound) >= KING_POS_CACHE_INTERVAL;
    if (shouldReadKingPos) {
      lastKingPosReadRound = cachedRound;

      // Read our king position
      int kingX = rc.readSharedArray(SLOT_OUR_KING_X);
      int kingY = rc.readSharedArray(SLOT_OUR_KING_Y);
      if (kingX > 0 || kingY > 0) {
        if (cachedOurKingLoc == null
            || cachedOurKingLoc.x != kingX
            || cachedOurKingLoc.y != kingY) {
          cachedOurKingLoc = new MapLocation(kingX, kingY);
        }
      }

      // Read enemy king position
      int enemyX = rc.readSharedArray(SLOT_ENEMY_KING_X);
      int enemyY = rc.readSharedArray(SLOT_ENEMY_KING_Y);
      enemyKingConfirmed = rc.readSharedArray(SLOT_ENEMY_KING_CONFIRMED) > 0;
      if (enemyX > 0 || enemyY > 0) {
        if (cachedEnemyKingLoc == null
            || cachedEnemyKingLoc.x != enemyX
            || cachedEnemyKingLoc.y != enemyY) {
          cachedEnemyKingLoc = new MapLocation(enemyX, enemyY);
        }
      }
    }

    // === ESSENTIAL READS: These change frequently, read every turn ===
    // Read enemy king HP (stored as HP/8) - important for attack decisions
    int storedHP = rc.readSharedArray(SLOT_ENEMY_KING_HP);
    cachedEnemyKingHP = (storedHP == 0) ? 500 : (storedHP << 3);

    // Read our king HP (stored as HP/8) - important for survival decisions
    int storedOurHP = rc.readSharedArray(SLOT_OUR_KING_HP);
    cachedOurKingHP = (storedOurHP == 0) ? 500 : (storedOurHP << 3);

    // Game state and threat level - important for behavior
    currentGameState = rc.readSharedArray(SLOT_GAME_STATE);
    if (currentGameState < 0 || currentGameState > 2) currentGameState = STATE_PRESSURE;

    // Phase 3: Read all-in round (important for coordinated attacks)
    // Note: getMaskedRoundDiff() handles 10-bit wraparound (see Section 17: Utilities)
    cachedAllInRound = rc.readSharedArray(SLOT_ALL_IN_ROUND);
    cachedAllInActive =
        (cachedAllInRound > 0 && getMaskedRoundDiff(cachedAllInRound) <= ALL_IN_SIGNAL_DURATION);

    // === LESS FREQUENT READS: Every 3 rounds ===
    boolean shouldReadDefenseState = (cachedRound - lastDefenseReadRound) >= DEFENSE_CACHE_INTERVAL;
    if (shouldReadDefenseState) {
      lastDefenseReadRound = cachedRound;

      cachedThreatLevel = rc.readSharedArray(SLOT_THREAT_LEVEL);
      cachedSpawnCount = rc.readSharedArray(SLOT_SPAWN_COUNT);
      cachedRaceMode = rc.readSharedArray(SLOT_RACE_MODE);

      // Phase 3: Read enemy king damage tracking
      cachedDamageToEnemyKing = rc.readSharedArray(SLOT_DAMAGE_TO_ENEMY_KING);
      int confirmedHP = rc.readSharedArray(SLOT_CONFIRMED_ENEMY_HP);
      if (confirmedHP > 0) {
        cachedEnemyKingHP = confirmedHP;
      } else if (cachedDamageToEnemyKing > 0) {
        cachedEnemyKingHP = ENEMY_KING_STARTING_HP - cachedDamageToEnemyKing;
        if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
      }

      // Phase 3: Read attackers near enemy
      cachedAttackersNearEnemy = rc.readSharedArray(SLOT_ATTACKERS_NEAR_ENEMY);

      // Phase 3: Read opponent classification and attack window
      cachedOpponentType = rc.readSharedArray(SLOT_OPPONENT_TYPE);
      cachedAttackWindow = rc.readSharedArray(SLOT_ATTACK_WINDOW);
      rushSurvivedRound = rc.readSharedArray(SLOT_RUSH_SURVIVED_ROUND);
      wasRecentlyRushed =
          (rushSurvivedRound > 0
              && getMaskedRoundDiff(rushSurvivedRound) <= POST_RUSH_WINDOW_DURATION);

      // Phase 3: Read strategic attack intelligence state
      currentAttackCommitment = rc.readSharedArray(SLOT_ATTACK_COMMITMENT);
      if (currentAttackCommitment < 0 || currentAttackCommitment > 4) {
        currentAttackCommitment = COMMITMENT_PROBE; // Default
      }
      postRushPhase = rc.readSharedArray(SLOT_POST_RUSH_PHASE);
      killsThisGame = rc.readSharedArray(SLOT_KILLS_THIS_GAME);
    }

    // === ESSENTIAL DEFENSE READS: Emergency level and starvation must be fresh for immediate
    // response ===
    // These are critical for emergency response and must be read every turn
    cachedEmergencyLevel = rc.readSharedArray(SLOT_EMERGENCY_LEVEL);
    cachedEnemiesNearKing = rc.readSharedArray(SLOT_ENEMIES_NEAR_KING);
    cachedStarvationRounds = rc.readSharedArray(SLOT_STARVATION_ROUNDS);
    cachedInEmergency = cachedEmergencyLevel > 0;

    // === CACHED DEFENSE READS: Blocking line position (every 3 rounds) ===
    // Blocking line changes less frequently, can be cached
    if (shouldReadDefenseState) {
      int blockX = rc.readSharedArray(SLOT_BLOCKING_LINE_X);
      int blockY = rc.readSharedArray(SLOT_BLOCKING_LINE_Y);
      int blockDirOrd = rc.readSharedArray(SLOT_BLOCKING_LINE_DIR);
      if (blockX > 0 && blockY > 0) {
        if (cachedBlockingLineCenter == null
            || cachedBlockingLineCenter.x != blockX
            || cachedBlockingLineCenter.y != blockY) {
          cachedBlockingLineCenter = new MapLocation(blockX, blockY);
        }
        cachedBlockingLineDir = DIRECTIONS[blockDirOrd & 7];
      } else {
        cachedBlockingLineCenter = null;
      }
    }

    // === SKIP FOCUS FIRE READS: Only read when we have enemies (done in runBabyRat) ===
    // cachedFocusTarget is read lazily when enemies are visible

    // === SKIP ENEMY RING BUFFER: Only read every 5 rounds (predictive targeting) ===
    boolean shouldReadRingBuffer =
        (cachedRound - lastRingBufferReadRound) >= RING_BUFFER_CACHE_INTERVAL;
    if (shouldReadRingBuffer) {
      lastRingBufferReadRound = cachedRound;
      for (int i = 0; i < 4; i++) {
        int packed = rc.readSharedArray(SLOT_ENEMY_RING_0 + i);
        if (packed > 0) {
          // Decode 5-bit packed coords (multiply by 2 to restore precision)
          cachedEnemyRingX[i] = (packed & 0x1F) << 1;
          cachedEnemyRingY[i] = ((packed >> 5) & 0x1F) << 1;
        } else {
          cachedEnemyRingX[i] = -1;
          cachedEnemyRingY[i] = -1;
        }
      }
    }

    // Cache distance to king (uses cached king loc, no shared array read)
    hasOurKingLoc = cachedOurKingLoc != null;
    if (hasOurKingLoc) {
      int dx = myLocX - cachedOurKingLoc.x;
      int dy = myLocY - cachedOurKingLoc.y;
      cachedDistToKingSq = dx * dx + dy * dy;
    } else {
      cachedDistToKingSq = -1;
    }
  }

  /**
   * Read focus fire target lazily - only when enemies are visible. Saves ~60 BC when no enemies.
   */
  private static void readFocusFireTarget(RobotController rc) throws GameActionException {
    int focusRound = rc.readSharedArray(SLOT_FOCUS_ROUND);
    boolean focusNotStale =
        (focusRound > 0) && (getMaskedRoundDiff(focusRound) <= FOCUS_FIRE_STALE_ROUNDS);
    if (focusNotStale) {
      int focusX = rc.readSharedArray(SLOT_FOCUS_TARGET);
      if (focusX > 0) {
        // Decode packed coordinates (x in lower 6 bits, y in upper 6 bits)
        int fx = focusX & 0x3F;
        int fy = (focusX >> 6) & 0x3F;
        if (cachedFocusTarget == null || cachedFocusTarget.x != fx || cachedFocusTarget.y != fy) {
          cachedFocusTarget = new MapLocation(fx, fy);
        }
        cachedFocusTargetRound = focusRound;
      }
    } else {
      cachedFocusTarget = null;
      cachedFocusTargetRound = -100;
    }
  }

  // ================================================================
  // SECTION 8: GAME STATE MACHINE WITH HYSTERESIS
  // ================================================================

  private static int determineGameState(
      int kingHP, int cheese, int threat, int enemyKingHP, int advantage) {
    switch (currentGameState) {
      case STATE_SURVIVE:
        // Exit SURVIVE only when ALL conditions are safe (conservative)
        if (kingHP > SURVIVE_EXIT_HP
            && cheese > SURVIVE_EXIT_CHEESE
            && threat < SURVIVE_EXIT_THREAT) {
          if (enemyKingHP < EXECUTE_ENTER_ENEMY_HP
              || (advantage > EXECUTE_ENTER_ADVANTAGE && cheese > 500)) {
            return STATE_EXECUTE;
          }
          return STATE_PRESSURE;
        }
        return STATE_SURVIVE;

      case STATE_EXECUTE:
        // Drop to SURVIVE if we're in danger
        if (kingHP < SURVIVE_ENTER_HP
            || cheese < SURVIVE_ENTER_CHEESE
            || threat > SURVIVE_ENTER_THREAT) {
          return STATE_SURVIVE;
        }
        // Exit EXECUTE if advantage is lost
        if (enemyKingHP > EXECUTE_EXIT_ENEMY_HP && advantage < EXECUTE_EXIT_ADVANTAGE) {
          return STATE_PRESSURE;
        }
        return STATE_EXECUTE;

      case STATE_PRESSURE:
      default:
        // Enter SURVIVE if threatened
        if (kingHP < SURVIVE_ENTER_HP
            || cheese < SURVIVE_ENTER_CHEESE
            || threat > SURVIVE_ENTER_THREAT) {
          return STATE_SURVIVE;
        }
        // Enter EXECUTE if opportunity
        if (enemyKingHP < EXECUTE_ENTER_ENEMY_HP
            || (advantage > EXECUTE_ENTER_ADVANTAGE && cheese > 500)) {
          return STATE_EXECUTE;
        }
        return STATE_PRESSURE;
    }
  }

  // ================================================================
  // SECTION 9: ROLE ASSIGNMENT
  // ================================================================

  private static int getRatRole(int ratId) {
    int roleSelector = ratId % 100;
    if (roleSelector < 5) {
      return ROLE_CORE; // 0-4: Guardian duty (5%)
    } else if (roleSelector < 80) {
      return ROLE_FLEX; // 5-79: Value function (75%)
    } else {
      return ROLE_SPECIALIST; // 80-99: Scouts/Raiders/Assassins (20%)
    }
  }

  // ================================================================
  // SECTION 10: VALUE FUNCTION
  // ================================================================

  /** Get profile-adjusted weights for current game state (bytecode optimized). */
  private static int[] getStateWeights(int state) {
    // Use pre-computed profile-adjusted weights
    if (state >= 0 && state < 3) {
      return PROFILE_ADJUSTED_WEIGHTS[state];
    }
    return PROFILE_ADJUSTED_WEIGHTS[STATE_PRESSURE];
  }

  private static int getCheeseBaseValue() {
    if (cachedGlobalCheese < CRITICAL_CHEESE) return CHEESE_BASE_CRITICAL;
    if (cachedGlobalCheese < LOW_CHEESE) return CHEESE_BASE_LOW;
    return CHEESE_BASE_NORMAL;
  }

  private static int scoreTarget(int baseValue, int weight, int distSq) {
    int weighted = baseValue * weight / 100;
    return weighted * 1000 / (1000 + distSq * DISTANCE_WEIGHT);
  }

  private static void scoreAllTargets(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    // === BYTECODE OPTIMIZATION: Cache frequently used values ===
    final int[] weights = getStateWeights(currentGameState);
    final int locX = myLocX;
    final int locY = myLocY;
    final MapLocation kingLoc = cachedOurKingLoc;
    final boolean hasKing = hasOurKingLoc;

    // ALL-IN MODE: If active or commitment is ALL_IN, heavily weight enemy king
    boolean allInMode = cachedAllInActive || currentAttackCommitment == COMMITMENT_ALL_IN;

    // Priority 1: Deliver cheese if carrying (reduced in all-in mode)
    if (cachedCarryingCheese && hasKing) {
      int score = scoreTarget(DELIVERY_BASE, weights[3], cachedDistToKingSq);
      if (allInMode) score = score >> 1; // Halve delivery priority in all-in
      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = kingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
      }
    }

    // Priority 2: Attack enemies
    if (hasKing) {
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        MapLocation enemyLoc = enemy.getLocation();
        int ex = enemyLoc.x;
        int ey = enemyLoc.y;

        int dx = locX - ex;
        int dy = locY - ey;
        int distSq = dx * dx + dy * dy;

        int baseValue = ENEMY_RAT_BASE;
        int weightIdx = 1;

        if (enemy.getType() == UnitType.RAT_KING) {
          baseValue = ENEMY_KING_BASE;
          weightIdx = 0;
          // Wounded king bonus - increases priority when king is low HP
          if (cachedEnemyKingHP < 250) {
            baseValue += 100; // Extra priority for wounded king
          }
          // Attack window bonus
          if (cachedAttackWindow >= WINDOW_WOUNDED_KING) {
            baseValue += 150; // Strong bonus when attack window is open
          }
        }

        int score = scoreTarget(baseValue, weights[weightIdx], distSq);

        // Focus fire bonus
        if (cachedFocusTarget != null && enemyLoc.distanceSquaredTo(cachedFocusTarget) <= 2) {
          score += FOCUS_FIRE_BONUS;
        }

        // Commitment-based target bonus (Strategic Attack Intelligence)
        // Skip for gatherers - they won't act on attack targets anyway (bytecode optimization)
        if (!cachedCarryingCheese) {
          score += getCommitmentTargetBonus(enemy, currentAttackCommitment);
        }

        // Bonus for enemies near our king
        int edx = ex - kingLoc.x;
        int edy = ey - kingLoc.y;
        int enemyDistToKing = edx * edx + edy * edy;
        if (enemyDistToKing <= HOME_TERRITORY_RADIUS_SQ) {
          score += 100;
        }

        // All-in mode: reduce baby rat priority, boost king
        if (allInMode) {
          if (enemy.getType() == UnitType.RAT_KING) {
            score += 500;
          } else {
            score = (score * 11) >> 5; // ~34% reduction
          }
        }

        // Race attack mode: boost all enemy targets
        if (cachedRaceMode == RACE_ATTACK_MODE) {
          if (enemy.getType() == UnitType.RAT_KING) {
            score += 300;
          }
        }

        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = enemyLoc;
          cachedBestTargetType =
              enemy.getType() == UnitType.RAT_KING ? TARGET_ENEMY_KING : TARGET_ENEMY_RAT;
        }
      }
    }

    // Priority 3: Collect cheese (skip in all-in mode)
    if (!cachedCarryingCheese && !allInMode) {
      int cheeseBase = getCheeseBaseValue();
      for (int i = cheeseCount; --i >= 0; ) {
        MapLocation cheese = cheeseBuffer[i];
        int dx = locX - cheese.x;
        int dy = locY - cheese.y;
        int distSq = dx * dx + dy * dy;

        int score = scoreTarget(cheeseBase, weights[2], distSq);
        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = cheese;
          cachedBestTargetType = TARGET_CHEESE;
        }
      }
    }

    // Priority 4: Attack enemy king if nothing else to do
    if (cachedBestTarget == null) {
      if (cachedEnemyKingLoc != null) {
        // Go to enemy king (even if not confirmed - trust the estimate)
        cachedBestTarget = cachedEnemyKingLoc;
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 50;
      } else if (hasKing) {
        // Fallback: patrol near our king only if we have no enemy king info
        cachedBestTarget = kingLoc;
        cachedBestTargetType = TARGET_PATROL;
        cachedBestScore = 1;
      }
    }
  }

  // ================================================================
  // SECTION 11: CHEESE SENSING
  // ================================================================

  private static void findNearbyCheese(RobotController rc) throws GameActionException {
    // BUGFIX: Increased radius from 13 to 25 (5 tiles) to see cheese from further away
    // BUGFIX: Increased radius from 13 to 36 (6 tiles) to see cheese from further away
    // This is critical for finding cheese in corridors - rats were walking right past it!
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, 36);
    cheeseCount = 0;

    for (int i = nearbyTiles.length; --i >= 0 && cheeseCount < cheeseBuffer.length; ) {
      MapInfo info = nearbyTiles[i];
      if (info.getCheeseAmount() > 0) {
        MapLocation cheeseLoc = info.getMapLocation();
        cheeseBuffer[cheeseCount++] = cheeseLoc;
        // Update last-known cheese memory (always track most recent sighting)
        lastCheeseSeenLoc = cheeseLoc;
        lastCheeseSeenRound = cachedRound;
      }
    }
  }

  /**
   * Squeak cheese location to share with team. Called when rat sees cheese it can't immediately
   * collect.
   *
   * <p>NOTE: This squeak is SUPPRESSED when near our king to avoid attracting cats to our
   * gatherers. Cats hear squeaks but don't decode them - squeaking near king = cat magnet!
   */
  private static void squeakCheeseLocation(RobotController rc, MapLocation cheeseLoc)
      throws GameActionException {
    // CRITICAL: Suppress squeaks near our king to avoid attracting cats to gatherers!
    // Cats hear squeaks from ~4 tiles away and investigate the sound source.
    if (shouldSuppressSqueaksNearKing()) return;

    // Throttle squeaks to avoid spam
    // CRITICAL FIX: Only throttle if squeaking the SAME location
    // Previously rats wouldn't squeak about NEW cheese for 15 rounds after squeaking any cheese
    boolean sameLocation =
        (lastSqueakedCheeseLoc != null && lastSqueakedCheeseLoc.equals(cheeseLoc));
    if (sameLocation && cachedRound - lastCheeseSqueak < CHEESE_SQUEAK_THROTTLE) return;

    // Format: type(4 bits) | y(12 bits) | x(12 bits) | reserved(4 bits)
    // Type = 2 for cheese location
    int squeak = (2 << 28) | (cheeseLoc.y << 16) | (cheeseLoc.x << 4);
    rc.squeak(squeak);
    lastCheeseSqueak = cachedRound;
    lastSqueakedCheeseLoc = cheeseLoc;
  }

  /** Read shared cheese locations from shared array. */
  private static void readSharedCheeseLocations(RobotController rc) throws GameActionException {
    // Check if shared cheese info is stale
    int cheeseRound = rc.readSharedArray(SLOT_CHEESE_ROUND);
    if (cheeseRound == 0 || getMaskedRoundDiff(cheeseRound) > CHEESE_STALE_ROUNDS) {
      // Stale or no data
      sharedCheeseLocations[0] = null;
      sharedCheeseLocations[1] = null;
      sharedCheeseLocations[2] = null;
      sharedCheeseRound = -100;
      return;
    }

    sharedCheeseRound = cheeseRound;

    // Read up to 3 cheese locations
    int packed1 = rc.readSharedArray(SLOT_CHEESE_LOC_1);
    int packed2 = rc.readSharedArray(SLOT_CHEESE_LOC_2);
    int packed3 = rc.readSharedArray(SLOT_CHEESE_LOC_3);

    // Decode packed coordinates (5 bits x, 5 bits y with *2 to restore precision)
    // Battlecode 2026 shared array values are limited to 0-1023 (10 bits)
    if (packed1 > 0) {
      int x = (packed1 & 0x1F) << 1;
      int y = ((packed1 >> 5) & 0x1F) << 1;
      sharedCheeseLocations[0] = new MapLocation(x, y);
    } else {
      sharedCheeseLocations[0] = null;
    }

    if (packed2 > 0) {
      int x = (packed2 & 0x1F) << 1;
      int y = ((packed2 >> 5) & 0x1F) << 1;
      sharedCheeseLocations[1] = new MapLocation(x, y);
    } else {
      sharedCheeseLocations[1] = null;
    }

    if (packed3 > 0) {
      int x = (packed3 & 0x1F) << 1;
      int y = ((packed3 >> 5) & 0x1F) << 1;
      sharedCheeseLocations[2] = new MapLocation(x, y);
    } else {
      sharedCheeseLocations[2] = null;
    }
  }

  /**
   * Clear a depleted cheese location from the shared array. Called when a rat visits a shared
   * cheese location and finds no cheese there.
   */
  private static void clearDepletedCheeseLocation(RobotController rc, MapLocation depletedLoc)
      throws GameActionException {
    // Baby rats can't write to shared array, but we clear from local cache
    // to avoid re-visiting this location
    for (int i = 0; i < 3; i++) {
      if (sharedCheeseLocations[i] != null && sharedCheeseLocations[i].equals(depletedLoc)) {
        sharedCheeseLocations[i] = null;
      }
    }
    // Also clear last-known memory if it matches
    if (lastCheeseSeenLoc != null && lastCheeseSeenLoc.equals(depletedLoc)) {
      lastCheeseSeenLoc = null;
      lastCheeseSeenRound = -100;
    }
    // Clear squeak throttle if we just depleted that location
    if (lastSqueakedCheeseLoc != null && lastSqueakedCheeseLoc.equals(depletedLoc)) {
      lastSqueakedCheeseLoc = null;
    }
  }

  /**
   * Find shared cheese location for this rat. Distributes rats across different shared locations
   * based on ID to prevent traffic jams where all rats go to the same spot.
   *
   * @param ratId The rat's ID (used to select which shared location to target)
   * @return A shared cheese location, or null if none available
   */
  private static MapLocation findSharedCheeseForRat(int ratId) {
    if (sharedCheeseRound < 0) return null;

    // Count available shared locations
    int availableCount = 0;
    for (int i = 0; i < 3; i++) {
      if (sharedCheeseLocations[i] != null) availableCount++;
    }
    if (availableCount == 0) return null;

    // ANTI-TRAFFIC JAM: Different rats target different shared locations
    // This prevents all rats from crowding at the closest cheese
    int targetSlot = ratId % availableCount;
    int slotIndex = 0;
    for (int i = 0; i < 3; i++) {
      if (sharedCheeseLocations[i] != null) {
        if (slotIndex == targetSlot) {
          return sharedCheeseLocations[i];
        }
        slotIndex++;
      }
    }

    // Fallback to first available (shouldn't reach here)
    for (int i = 0; i < 3; i++) {
      if (sharedCheeseLocations[i] != null) return sharedCheeseLocations[i];
    }
    return null;
  }

  /** Find closest shared cheese location. Returns null if none available. */
  private static MapLocation findClosestSharedCheese() {
    if (sharedCheeseRound < 0) return null;

    MapLocation closest = null;
    int closestDist = Integer.MAX_VALUE;

    for (int i = 0; i < 3; i++) {
      MapLocation loc = sharedCheeseLocations[i];
      if (loc == null) continue;
      int dx = myLocX - loc.x;
      int dy = myLocY - loc.y;
      int dist = dx * dx + dy * dy;
      if (dist < closestDist) {
        closestDist = dist;
        closest = loc;
      }
    }

    return closest;
  }

  /**
   * Find nearest visible cheese and move toward it. Consolidates duplicate cheese-seeking logic.
   *
   * @param rc RobotController
   * @param enemies Enemy array for tryImmediateAction
   * @param enemyCount Number of enemies
   * @param urgent If true, use bug2MoveToUrgent; otherwise bug2MoveTo
   * @param maxDistSq Maximum distance squared to consider (-1 for no limit)
   * @param doImmediateAction If true, call tryImmediateAction before moving
   * @return true if we found cheese within range and initiated movement, false otherwise
   * @throws GameActionException if movement fails
   */
  private static boolean moveTowardNearestCheese(
      RobotController rc,
      RobotInfo[] enemies,
      int enemyCount,
      boolean urgent,
      int maxDistSq,
      boolean doImmediateAction)
      throws GameActionException {
    if (cheeseCount == 0) return false;

    // Find nearest cheese
    MapLocation nearestCheese = null;
    int nearestDist = Integer.MAX_VALUE;
    for (int i = cheeseCount; --i >= 0; ) {
      int dx = myLocX - cheeseBuffer[i].x;
      int dy = myLocY - cheeseBuffer[i].y;
      int dist = dx * dx + dy * dy;
      if (dist < nearestDist) {
        nearestDist = dist;
        nearestCheese = cheeseBuffer[i];
      }
    }

    if (nearestCheese == null) return false;

    // Check max distance if specified
    if (maxDistSq > 0 && nearestDist > maxDistSq) return false;

    // Squeak cheese location to share with team
    squeakCheeseLocation(rc, nearestCheese);

    // Try immediate actions (attack/dig) while moving
    if (doImmediateAction) {
      tryImmediateAction(rc, enemies, enemyCount);
    }

    // Move toward cheese
    if (urgent) {
      bug2MoveToUrgent(rc, nearestCheese);
    } else {
      bug2MoveTo(rc, nearestCheese);
    }
    return true;
  }

  // ================================================================
  // SECTION 12: PHASE 2 DEFENSE SYSTEMS
  // ================================================================

  /** Predict rounds until king starvation based on king's carried cheese */
  private static int predictStarvationRounds(int kingCarriedCheese) {
    if (kingCarriedCheese <= 0) return 0;
    return kingCarriedCheese / CHEESE_PER_ROUND_KING;
  }

  /** Update emergency state based on enemies near king */
  private static void updateEmergencyState(RobotInfo[] enemies, int enemyCount) {
    cachedEnemiesNearKing = 0;
    if (cachedOurKingLoc == null) return;

    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType()) {
        MapLocation enemyLoc = enemy.getLocation();
        int dx = enemyLoc.x - cachedOurKingLoc.x;
        int dy = enemyLoc.y - cachedOurKingLoc.y;
        int distToKing = dx * dx + dy * dy;
        if (distToKing <= 25) { // Within 5 tiles of king
          cachedEnemiesNearKing++;
        }
      }
    }

    // Set emergency level
    if (cachedEnemiesNearKing >= FULL_EMERGENCY_THRESHOLD) {
      cachedEmergencyLevel = 2; // Full emergency
      cachedInEmergency = true;
    } else if (cachedEnemiesNearKing >= PARTIAL_EMERGENCY_THRESHOLD) {
      cachedEmergencyLevel = 1; // Partial emergency
      cachedInEmergency = true;
    } else {
      cachedEmergencyLevel = 0;
      cachedInEmergency = false;
    }
  }

  /** Update blocking line position for body blocking */
  private static void updateBlockingLine(RobotInfo[] enemies, int enemyCount) {
    if (cachedOurKingLoc == null || enemyCount == 0) {
      cachedBlockingLineCenter = null;
      return;
    }

    // Find centroid of nearby enemies
    int sumX = 0, sumY = 0, count = 0;
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType()) {
        MapLocation enemyLoc = enemy.getLocation();
        int dx = enemyLoc.x - cachedOurKingLoc.x;
        int dy = enemyLoc.y - cachedOurKingLoc.y;
        int distToKing = dx * dx + dy * dy;
        if (distToKing <= 100) { // Within 10 tiles
          sumX += enemyLoc.x;
          sumY += enemyLoc.y;
          count++;
        }
      }
    }

    if (count == 0) {
      cachedBlockingLineCenter = null;
      return;
    }

    int enemyCenterX = sumX / count;
    int enemyCenterY = sumY / count;
    MapLocation enemyCenter = new MapLocation(enemyCenterX, enemyCenterY);

    // Blocking line is between king and enemy center
    Direction toEnemy = cachedOurKingLoc.directionTo(enemyCenter);
    if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;

    // Position blocking line BLOCKING_LINE_DIST tiles from king toward enemies
    int lineX = cachedOurKingLoc.x + toEnemy.dx * BLOCKING_LINE_DIST;
    int lineY = cachedOurKingLoc.y + toEnemy.dy * BLOCKING_LINE_DIST;
    // Clamp to valid map coordinates to avoid negative values
    if (lineX < 0) lineX = 0;
    if (lineY < 0) lineY = 0;
    if (lineX >= cachedMapWidth) lineX = cachedMapWidth - 1;
    if (lineY >= cachedMapHeight) lineY = cachedMapHeight - 1;
    cachedBlockingLineCenter = new MapLocation(lineX, lineY);
    cachedBlockingLineDir = toEnemy;
  }

  /** Run defensive kiting behavior - attack then retreat */
  private static boolean runDefensiveKiting(RobotController rc, RobotInfo target)
      throws GameActionException {
    if (target == null) return false;

    MapLocation targetLoc = target.getLocation();
    int dx = myLocX - targetLoc.x;
    int dy = myLocY - targetLoc.y;
    int distSq = dx * dx + dy * dy;
    int myHP = rc.getHealth();

    // Determine retreat distance based on HP
    int retreatDist;
    if (myHP <= 30) {
      retreatDist = KITE_RETREAT_DIST_CRITICAL;
    } else if (myHP <= 60) {
      retreatDist = KITE_RETREAT_DIST_WOUNDED;
    } else {
      retreatDist = KITE_RETREAT_DIST_HEALTHY;
    }

    // State machine
    switch (kiteState) {
      case KITE_STATE_APPROACH:
        if (distSq <= 2) {
          kiteState = KITE_STATE_ATTACK;
        } else {
          bug2MoveTo(rc, targetLoc);
        }
        break;

      case KITE_STATE_ATTACK:
        if (rc.canAttack(targetLoc)) {
          rc.attack(targetLoc);
          kiteState = KITE_STATE_RETREAT;
          kiteRetreatCounter = retreatDist;
        } else if (distSq > 2) {
          kiteState = KITE_STATE_APPROACH;
        }
        break;

      case KITE_STATE_RETREAT:
        if (kiteRetreatCounter <= 0) {
          kiteState = KITE_STATE_APPROACH;
        } else {
          Direction away = targetLoc.directionTo(myLoc);
          if (away == Direction.CENTER) away = DIRECTIONS[cachedRound & 7];
          if (rc.canMove(away)) {
            rc.move(away);
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
          } else if (rc.canMove(away.rotateLeft())) {
            rc.move(away.rotateLeft());
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
          } else if (rc.canMove(away.rotateRight())) {
            rc.move(away.rotateRight());
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
          }
        }
        break;
    }
    return true;
  }

  /** Emergency movement - ignore traps, just get to target ASAP */
  private static void bug2MoveToUrgent(RobotController rc, MapLocation target)
      throws GameActionException {
    if (target == null || !cachedMovementReady) return;
    if (myLocX == target.x && myLocY == target.y) return;

    Direction dir = myLoc.directionTo(target);
    if (dir == Direction.CENTER) return;

    // Try direct, then adjacent directions - IGNORE TRAPS
    if (rc.canMove(dir)) {
      rc.move(dir);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }
    Direction left = dir.rotateLeft();
    if (rc.canMove(left)) {
      rc.move(left);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }
    Direction right = dir.rotateRight();
    if (rc.canMove(right)) {
      rc.move(right);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }
  }

  /** King flees from nearby cat */
  private static boolean kingFleeFromCat(RobotController rc, MapLocation catLoc)
      throws GameActionException {
    if (catLoc == null || !rc.isMovementReady()) return false;
    int dx = myLocX - catLoc.x;
    int dy = myLocY - catLoc.y;
    int distSq = dx * dx + dy * dy;
    if (distSq > CAT_FLEE_RADIUS_SQ) return false;

    Direction awayFromCat = catLoc.directionTo(myLoc);
    if (awayFromCat == Direction.CENTER) {
      awayFromCat = DIRECTIONS[cachedRound & 7];
    }

    if (rc.canMove(awayFromCat)) {
      rc.move(awayFromCat);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      return true;
    }
    if (rc.canMove(awayFromCat.rotateLeft())) {
      rc.move(awayFromCat.rotateLeft());
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      return true;
    }
    if (rc.canMove(awayFromCat.rotateRight())) {
      rc.move(awayFromCat.rotateRight());
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      return true;
    }
    return false;
  }

  /** Find nearest dangerous cat */
  private static MapLocation findDangerousCat(RobotInfo[] allRobots) {
    MapLocation nearestCat = null;
    int nearestDistSq = Integer.MAX_VALUE;
    for (int i = allRobots.length; --i >= 0; ) {
      RobotInfo robot = allRobots[i];
      if (robot.getType() == UnitType.CAT) {
        MapLocation catLoc = robot.getLocation();
        int dx = myLocX - catLoc.x;
        int dy = myLocY - catLoc.y;
        int distSq = dx * dx + dy * dy;
        if (distSq < nearestDistSq) {
          nearestDistSq = distSq;
          nearestCat = catLoc;
        }
      }
    }
    return nearestCat;
  }

  // ================================================================
  // SECTION 12B: PHASE 3 OFFENSIVE SYSTEMS
  // ================================================================

  /** Write enemy position to ring buffer for predictive targeting. */
  private static void writeEnemyToRingBuffer(RobotController rc, MapLocation enemyLoc)
      throws GameActionException {
    // Pack: (x >> 1) (5 bits) | ((y >> 1) << 5) (5 bits) = 10 bits max = 1023
    int x = (enemyLoc.x >> 1) & 0x1F;
    int y = (enemyLoc.y >> 1) & 0x1F;
    int packed = x | (y << 5);
    rc.writeSharedArray(SLOT_ENEMY_RING_0 + enemyRingWriteIndex, packed);
    enemyRingWriteIndex = (enemyRingWriteIndex + 1) & 3; // Circular buffer
    // Note: SLOT_ENEMY_RING_INDEX not written - we track index locally
  }

  /**
   * Predict where an enemy will be based on recent sightings. Uses velocity estimation from ring
   * buffer.
   */
  private static MapLocation predictEnemyPosition(MapLocation currentLoc) {
    // Find two most recent sightings of enemies near this location
    int bestIdx1 = -1, bestIdx2 = -1;
    int bestDist1 = Integer.MAX_VALUE, bestDist2 = Integer.MAX_VALUE;

    for (int i = 0; i < 4; i++) {
      if (cachedEnemyRingX[i] < 0) continue;
      int rx = cachedEnemyRingX[i];
      int ry = cachedEnemyRingY[i];

      // Check if this sighting is near current enemy (within 5 tiles)
      int dx = rx - currentLoc.x;
      if (dx < 0) dx = -dx;
      int dy = ry - currentLoc.y;
      if (dy < 0) dy = -dy;
      int dist = dx + dy; // Manhattan distance

      if (dist <= 5) {
        if (dist < bestDist1) {
          bestDist2 = bestDist1;
          bestIdx2 = bestIdx1;
          bestDist1 = dist;
          bestIdx1 = i;
        } else if (dist < bestDist2) {
          bestDist2 = dist;
          bestIdx2 = i;
        }
      }
    }

    // If we have two sightings, estimate velocity
    if (bestIdx1 >= 0 && bestIdx2 >= 0) {
      int x1 = cachedEnemyRingX[bestIdx2];
      int y1 = cachedEnemyRingY[bestIdx2];
      int x2 = cachedEnemyRingX[bestIdx1];
      int y2 = cachedEnemyRingY[bestIdx1];

      // Velocity per round (approximate)
      int vx = (x2 - x1) >> 1; // Divide by 2 for average
      int vy = (y2 - y1) >> 1;

      // Predict 1 round ahead
      int predictX = currentLoc.x + vx;
      int predictY = currentLoc.y + vy;

      // Clamp to map bounds
      if (predictX < 0) predictX = 0;
      if (predictX >= cachedMapWidth) predictX = cachedMapWidth - 1;
      if (predictY < 0) predictY = 0;
      if (predictY >= cachedMapHeight) predictY = cachedMapHeight - 1;

      // Reuse MapLocation if unchanged
      if (predictedLoc == null || predictedLoc.x != predictX || predictedLoc.y != predictY) {
        predictedLoc = new MapLocation(predictX, predictY);
      }
      return predictedLoc;
    }

    // Fall back to current position
    return currentLoc;
  }

  /** Record damage dealt to enemy king. Updates shared array slot. */
  private static void recordDamageToEnemyKing(RobotController rc, int damage)
      throws GameActionException {
    int newDamage = cachedDamageToEnemyKing + damage;
    if (newDamage > ENEMY_KING_STARTING_HP) newDamage = ENEMY_KING_STARTING_HP;

    // Only king can write to shared array
    if (rc.getType().isRatKingType()) {
      rc.writeSharedArray(SLOT_DAMAGE_TO_ENEMY_KING, newDamage);
    }
    cachedDamageToEnemyKing = newDamage;
    cachedEnemyKingHP = ENEMY_KING_STARTING_HP - newDamage;
    if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
  }

  /**
   * Called when we can see the enemy king directly. Updates confirmed HP. Only king can write to
   * shared array, but all rats update local cache.
   */
  private static void confirmEnemyKingHP(RobotController rc, int actualHP, int round)
      throws GameActionException {
    // Always update local cache
    lastConfirmedEnemyKingHP = actualHP;
    lastConfirmedHPRound = round;
    cachedEnemyKingHP = actualHP;
    enemyKingConfirmed = true;

    // Recalibrate damage tracking based on actual HP
    int actualDamage = ENEMY_KING_STARTING_HP - actualHP;
    if (actualDamage > cachedDamageToEnemyKing) {
      cachedDamageToEnemyKing = actualDamage;
    }

    // Only king can write to shared array
    if (rc.getType().isRatKingType()) {
      if (actualDamage > 0) {
        rc.writeSharedArray(SLOT_DAMAGE_TO_ENEMY_KING, actualDamage);
      }
      rc.writeSharedArray(SLOT_CONFIRMED_ENEMY_HP, actualHP);
    }
  }

  /** Check if we should trigger all-in attack. Also triggers when commitment reaches ALL_IN. */
  private static void checkAndBroadcastAllIn(
      RobotController rc, int round, int cheese, int armySize) throws GameActionException {
    if (cachedAllInActive) return;
    if (round < ALL_IN_MIN_ROUND) return;
    if (cachedEmergencyLevel >= 2) return; // Don't all-in when starving

    boolean shouldAllIn = false;

    // Condition 0: Commitment system already at ALL_IN
    if (currentAttackCommitment == COMMITMENT_ALL_IN) {
      shouldAllIn = true;
    }

    // Condition 1: Enemy king HP is low
    if (!shouldAllIn && cachedEnemyKingHP > 0 && cachedEnemyKingHP <= ALL_IN_HP_THRESHOLD) {
      if (armySize >= ALL_IN_MIN_ATTACKERS) {
        shouldAllIn = true;
      }
    }

    // Condition 2: Late game with army advantage
    if (!shouldAllIn && round > 300 && armySize >= 10) {
      shouldAllIn = true;
    }

    // Condition 3: We've dealt significant damage
    if (!shouldAllIn && cachedDamageToEnemyKing >= (ENEMY_KING_STARTING_HP - ALL_IN_HP_THRESHOLD)) {
      if (armySize >= ALL_IN_MIN_ATTACKERS) {
        shouldAllIn = true;
      }
    }

    // Condition 4: Race mode says attack
    if (!shouldAllIn && cachedRaceMode == RACE_ATTACK_MODE) {
      shouldAllIn = true;
    }

    // Condition 5: Post-rush counter-attack reached king assault phase
    if (!shouldAllIn && postRushPhase == POST_RUSH_KING_ASSAULT) {
      shouldAllIn = true;
    }

    if (shouldAllIn) {
      rc.writeSharedArray(SLOT_ALL_IN_ROUND, round & 1023); // Limit to 10 bits
      cachedAllInActive = true;
      cachedAllInRound = round & 1023; // Store masked value for consistency
      // Also set commitment to ALL_IN
      if (currentAttackCommitment != COMMITMENT_ALL_IN) {
        currentAttackCommitment = COMMITMENT_ALL_IN;
        rc.writeSharedArray(SLOT_ATTACK_COMMITMENT, COMMITMENT_ALL_IN);
      }
    }
  }

  /** Update race mode when both kings are low HP. */
  private static void updateRaceMode(RobotController rc, int round, int ourKingHP, int armySize)
      throws GameActionException {
    int enemyKingHP = cachedEnemyKingHP;

    // Only activate race logic when at least one king is low
    if (ourKingHP >= BOTH_KINGS_LOW_THRESHOLD && enemyKingHP >= BOTH_KINGS_LOW_THRESHOLD) {
      if (cachedRaceMode != 0) {
        rc.writeSharedArray(SLOT_RACE_MODE, 0);
        cachedRaceMode = 0;
      }
      return;
    }

    // At least one king is low - calculate race
    int ourAttackers = Math.max(1, cachedAttackersNearEnemy);
    int enemyAttackers = Math.max(1, armySize / 2); // Estimate

    int roundsToKillEnemy = enemyKingHP / (ourAttackers * 10);
    int roundsToKillUs = ourKingHP / (enemyAttackers * 10);

    int newRaceMode;
    if (roundsToKillEnemy < roundsToKillUs) {
      newRaceMode = RACE_ATTACK_MODE;
    } else if (roundsToKillUs < roundsToKillEnemy - 2) {
      newRaceMode = RACE_DEFEND_MODE;
    } else {
      newRaceMode = RACE_ATTACK_MODE; // Tie-breaker: attack
    }

    if (newRaceMode != cachedRaceMode) {
      rc.writeSharedArray(SLOT_RACE_MODE, newRaceMode);
      cachedRaceMode = newRaceMode;
    }
  }

  /** Get dynamic kite retreat distance based on HP. */
  private static int getKiteRetreatDist(int hp) {
    if (hp < 40) return KITE_RETREAT_DIST_CRITICAL;
    if (hp < 60) return KITE_RETREAT_DIST_WOUNDED;
    return KITE_RETREAT_DIST_HEALTHY;
  }

  /**
   * Run offensive kiting behavior for attackers. Returns true if an action was taken. Uses
   * attack-retreat cycle to maximize damage while minimizing damage taken.
   */
  private static boolean runOffensiveKiting(RobotController rc, RobotInfo target)
      throws GameActionException {
    if (target == null) return false;

    MapLocation targetLoc = target.getLocation();
    int targetId = target.getID();
    int dx = myLocX - targetLoc.x;
    int dy = myLocY - targetLoc.y;
    int distSq = dx * dx + dy * dy;
    int myHP = rc.getHealth();

    // Reset kite state when engaging new target
    if (lastKiteTargetId != targetId) {
      kiteState = KITE_STATE_APPROACH;
      kiteRetreatCounter = 0;
      lastKiteTargetId = targetId;
    }

    // Dynamic retreat distance based on HP
    // Healthy rats don't retreat - press the attack!
    int retreatDist = (myHP >= HEALTHY_HP_THRESHOLD) ? 0 : getKiteRetreatDist(myHP);

    switch (kiteState) {
      case KITE_STATE_APPROACH:
        if (distSq <= 2) {
          kiteState = KITE_STATE_ATTACK;
        } else {
          bug2MoveTo(rc, targetLoc);
          return true;
        }
        // Fall through to attack

      case KITE_STATE_ATTACK:
        if (rc.isActionReady() && rc.canAttack(targetLoc)) {
          rc.attack(targetLoc);
          cachedActionReady = false;
          if (retreatDist > 0) {
            kiteState = KITE_STATE_RETREAT;
            kiteRetreatCounter = retreatDist;
          } else {
            // Healthy - press attack, stay in approach
            kiteState = KITE_STATE_APPROACH;
          }
          return true;
        }
        if (distSq > 2) {
          kiteState = KITE_STATE_APPROACH;
        }
        bug2MoveTo(rc, targetLoc);
        return true;

      case KITE_STATE_RETREAT:
        if (kiteRetreatCounter <= 0) {
          kiteState = KITE_STATE_APPROACH;
          break;
        }
        if (cachedMovementReady) {
          Direction away = targetLoc.directionTo(myLoc);
          if (away == Direction.CENTER) away = DIRECTIONS[cachedRound & 7];
          if (rc.canMove(away)) {
            rc.move(away);
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
            return true;
          }
          // Try adjacent directions
          Direction left = away.rotateLeft();
          Direction right = away.rotateRight();
          if (rc.canMove(left)) {
            rc.move(left);
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
            return true;
          }
          if (rc.canMove(right)) {
            rc.move(right);
            myLoc = rc.getLocation();
            myLocX = myLoc.x;
            myLocY = myLoc.y;
            cachedMovementReady = false;
            kiteRetreatCounter--;
            return true;
          }
        }
        break;
    }
    return false;
  }

  /**
   * Check if attacking this target would be overkill. Returns true if we should NOT attack (let
   * allies finish it).
   *
   * <p>TODO: Currently unused because allyBuffer is not reliably populated in all calling contexts.
   * Could be used in runCoreRat() or runFlexRat() where we have ally data.
   */
  @SuppressWarnings("unused")
  private static boolean isOverkill(RobotController rc, RobotInfo target, RobotInfo[] allies) {
    if (allies == null || allies.length == 0) return false;

    int targetHP = target.getHealth();
    if (targetHP > OVERKILL_HP_THRESHOLD) return false;

    MapLocation targetLoc = target.getLocation();
    int adjacentAllies = 0;
    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getLocation().distanceSquaredTo(targetLoc) <= 2) {
        adjacentAllies++;
      }
    }

    // If expected damage from allies >= target HP, don't attack
    int expectedDamage = adjacentAllies * 10;
    return expectedDamage >= targetHP;
  }

  /** Check if this rat should be an assassin. */
  private static boolean isAssassin(int ratId) {
    int divisor = (cachedMapArea > 2500) ? LARGE_MAP_ASSASSIN_DIVISOR : ASSASSIN_DIVISOR;
    return (ratId % divisor) == 0;
  }

  /** Get bypass distance for king rush based on map size. */
  private static int getKingBypassDistSq() {
    return (cachedMapArea > 2500) ? LARGE_MAP_KING_BYPASS_DIST_SQ : KING_PROXIMITY_BYPASS_DIST_SQ;
  }

  // ================================================================
  // SECTION 12C: PHASE 3 OPPONENT CLASSIFICATION & ATTACK WINDOWS
  // ================================================================

  /**
   * Classify opponent behavior based on observed patterns. Called by king each turn to update
   * shared array.
   */
  private static void classifyOpponentBehavior(
      RobotController rc, int round, int enemiesNearKing, int totalEnemies)
      throws GameActionException {
    // Track cumulative enemy sightings
    enemiesSeenNearKingTotal += enemiesNearKing;
    totalEnemiesSeen += totalEnemies;

    int newOpponentType = cachedOpponentType;

    // Early game rush detection
    if (round <= RUSH_DETECTION_ROUND) {
      if (enemiesSeenNearKingTotal >= RUSH_ENEMY_THRESHOLD) {
        newOpponentType = OPPONENT_RUSHING;
      }
    }
    // Turtle detection - no enemies seen by mid-game
    else if (round >= TURTLE_DETECTION_ROUND && cachedOpponentType == OPPONENT_UNKNOWN) {
      if (totalEnemiesSeen < TURTLE_ENEMY_THRESHOLD) {
        newOpponentType = OPPONENT_TURTLING;
      } else {
        newOpponentType = OPPONENT_BALANCED;
      }
    }

    // Detect opponent desperation (low HP king, erratic behavior)
    if (cachedEnemyKingHP > 0 && cachedEnemyKingHP < 100) {
      newOpponentType = OPPONENT_DESPERATE;
    }

    // Update if rush survived
    if (cachedOpponentType == OPPONENT_RUSHING
        && enemiesNearKing == 0
        && round > RUSH_DETECTION_ROUND) {
      if (rushSurvivedRound == 0) {
        rushSurvivedRound = round & 1023; // Store masked value for consistency
        rc.writeSharedArray(SLOT_RUSH_SURVIVED_ROUND, rushSurvivedRound);
      }
    }

    // Write to shared array if changed
    if (newOpponentType != cachedOpponentType) {
      cachedOpponentType = newOpponentType;
      rc.writeSharedArray(SLOT_OPPONENT_TYPE, newOpponentType);
    }

    // Update tracking slots
    rc.writeSharedArray(SLOT_ENEMIES_SEEN_NEAR_KING, Math.min(enemiesSeenNearKingTotal, 255));
    rc.writeSharedArray(SLOT_TOTAL_ENEMIES_SEEN, Math.min(totalEnemiesSeen, 255));
  }

  /**
   * Detect current attack window based on game state. Returns the type of attack opportunity
   * available.
   */
  private static int detectAttackWindow(
      RobotController rc, int round, int ourKingHP, int cheese, int armyAdvantage)
      throws GameActionException {
    int window = WINDOW_NONE;

    // Priority 1: Wounded enemy king - highest priority attack window
    if (cachedEnemyKingHP > 0 && cachedEnemyKingHP < WOUNDED_KING_HP) {
      window = WINDOW_WOUNDED_KING;
    }
    // Priority 2: Post-rush window - enemy just failed a rush
    else if (wasRecentlyRushed && cachedEmergencyLevel == 0) {
      window = WINDOW_POST_RUSH;
    }
    // Priority 3: Army advantage - we have more rats
    else if (armyAdvantage >= ARMY_ADVANTAGE_THRESHOLD && ourKingHP > 250) {
      window = WINDOW_ARMY_ADVANTAGE;
    }
    // Priority 4: Economy window - we have lots of cheese
    else if (cheese >= ECONOMY_WINDOW_CHEESE && ourKingHP > 300) {
      window = WINDOW_ECONOMY;
    }
    // Priority 5: Turtle punish - enemy is turtling, we should attack
    else if (cachedOpponentType == OPPONENT_TURTLING && round > 100) {
      window = WINDOW_TURTLE_PUNISH;
    }
    // Priority 6: Late game - time to commit
    else if (round >= LATE_GAME_ROUND) {
      window = WINDOW_LATE_GAME;
    }

    // Don't attack if we're in danger
    if (currentGameState == STATE_SURVIVE || cachedEmergencyLevel >= 2) {
      window = WINDOW_NONE;
    }

    // Write to shared array if changed
    if (window != cachedAttackWindow) {
      cachedAttackWindow = window;
      rc.writeSharedArray(SLOT_ATTACK_WINDOW, window);
    }

    return window;
  }

  /** Check if we're in charge mode (close to enemy king, ignore traps). */
  private static boolean isInChargeMode() {
    if (cachedEnemyKingLoc == null) return false;
    int dx = myLocX - cachedEnemyKingLoc.x;
    int dy = myLocY - cachedEnemyKingLoc.y;
    int distSq = dx * dx + dy * dy;
    return distSq <= CHARGE_MODE_DIST_SQ;
  }

  // ================================================================
  // SECTION 12D: STRATEGIC ATTACK INTELLIGENCE SYSTEM
  // ================================================================

  /**
   * Get the attack percentage for a given commitment level. This determines what percentage of the
   * army actively attacks vs gathers.
   *
   * @param commitment The commitment level (COMMITMENT_DEFEND through COMMITMENT_ALL_IN)
   * @return Attack percentage (10-100)
   */
  private static int getAttackPercentage(int commitment) {
    // TUNED: Much higher percentages to match ratbot6's ~95% attack rate
    // With 10% gatherers, effective attack % = 90% × attackPct
    // ratbot6 has 95% attackers - we need to be competitive!
    switch (commitment) {
      case COMMITMENT_DEFEND:
        return 70; // Attack even when defending - defense = faster offense (effective ~63%)
      case COMMITMENT_PROBE:
        return 85; // Aggressive scouting (effective ~76%)
      case COMMITMENT_RAID:
        return 90; // Strong harassment (effective ~81%)
      case COMMITMENT_ASSAULT:
        return 95; // Near full attack (effective ~85%) - restored for aggressive maps
      case COMMITMENT_ALL_IN:
        return 100; // Everyone attacks (effective ~90%)
      default:
        return 90; // Default to RAID level aggression
    }
  }

  /**
   * Get the post-rush counter-attack phase based on time since rush survived. When we survive a
   * rush, we execute a counter-attack sequence: STABILIZE → ECONOMY_RAID → ASSAULT_PREP →
   * KING_ASSAULT
   *
   * @return The current post-rush phase
   */
  private static int getPostRushPhase() {
    if (!wasRecentlyRushed || rushSurvivedRound == 0) return POST_RUSH_NONE;

    int roundsSinceSurvival = getMaskedRoundDiff(rushSurvivedRound);

    // Phase 0: Stabilize (first 10 rounds after surviving rush)
    if (roundsSinceSurvival < 10) {
      if (cachedOurKingHP < 200 || cachedGlobalCheese < 300) {
        return POST_RUSH_STABILIZE;
      }
    }

    // Phase 3: King assault (when conditions met - skip directly to kill)
    if (cachedArmyAdvantage > 40 || cachedEnemyKingHP < 200) {
      return POST_RUSH_KING_ASSAULT;
    }

    // Phase 2: Assault prep (50-80 rounds after surviving)
    if (roundsSinceSurvival > 50) {
      return POST_RUSH_ASSAULT_PREP;
    }

    // Phase 1: Economy raid (default 10-50 rounds after surviving)
    return POST_RUSH_ECONOMY_RAID;
  }

  /**
   * Get the counter-strategy commitment based on opponent behavior. Maps opponent type to
   * appropriate response commitment level.
   *
   * @return Recommended commitment level for current opponent behavior
   */
  private static int getCounterStrategyCommitment() {
    switch (cachedOpponentType) {
      case OPPONENT_RUSHING:
        // After surviving rush, counter-attack their economy
        if (wasRecentlyRushed) {
          int phase = getPostRushPhase();
          if (phase == POST_RUSH_KING_ASSAULT) return COMMITMENT_ALL_IN;
          if (phase == POST_RUSH_ASSAULT_PREP) return COMMITMENT_ASSAULT;
          if (phase == POST_RUSH_ECONOMY_RAID) return COMMITMENT_RAID;
          return COMMITMENT_DEFEND; // Stabilizing
        }
        return COMMITMENT_DEFEND; // Still under rush, defend

      case OPPONENT_TURTLING:
        // Probe their defenses, don't overcommit
        return COMMITMENT_PROBE;

      case OPPONENT_DESPERATE:
        // They're all-in, focus on defense
        return COMMITMENT_DEFEND;

      case OPPONENT_BALANCED:
      default:
        // Opportunistic - use current escalation level
        return currentAttackCommitment;
    }
  }

  /**
   * Update attack commitment based on game conditions. Escalates when we're winning, de-escalates
   * when threatened. Called by king each round.
   *
   * @param rc RobotController for writing to shared array
   * @throws GameActionException if shared array write fails
   */
  private static void updateAttackCommitment(RobotController rc) throws GameActionException {
    int oldCommitment = currentAttackCommitment;

    // DE-ESCALATE first (threat takes priority)
    // BALANCED: Defend early enough to make a difference, but not too cautiously
    // - threatLevel > 3: 4+ enemies near king (was > 5, too late for pipes corridors)
    // - cheese < 150: ~50 rounds of survival (was < 100, too risky)
    // - kingHP < 300: 60% health (was < 200, too late to save)
    //
    // NOTE: Reactive escalation (HP < 350 → ASSAULT) was removed because:
    // 1. It conflicted with de-escalation (HP < 300 → DEFEND), causing oscillation
    // 2. Testing showed it didn't help on exposed-king maps like sittingducks
    // 3. The narrow 50 HP window (300-350) caused unpredictable behavior
    // The value function and attack windows already handle aggressive responses.
    if (cachedThreatLevel > 3 || cachedGlobalCheese < 150 || cachedOurKingHP < 300) {
      currentAttackCommitment = COMMITMENT_DEFEND;
    }
    // ESCALATE based on conditions (check in order of priority)
    else if (cachedEnemyKingHP < 200 && cachedOurKingHP > 200) {
      // Enemy king nearly dead, we're healthy - go for the kill!
      currentAttackCommitment = COMMITMENT_ALL_IN;
    } else if (cachedArmyAdvantage > ADVANTAGE_FOR_ASSAULT && oldCommitment < COMMITMENT_ASSAULT) {
      // Strong army advantage - escalate to ASSAULT
      currentAttackCommitment = COMMITMENT_ASSAULT;
    } else if (killsThisGame >= KILLS_FOR_RAID && oldCommitment < COMMITMENT_RAID) {
      // Killed enough enemies - escalate to RAID
      currentAttackCommitment = COMMITMENT_RAID;
    } else if (enemyKingConfirmed && oldCommitment < COMMITMENT_PROBE) {
      // Found enemy king - escalate to PROBE
      currentAttackCommitment = COMMITMENT_PROBE;
    }

    // Apply counter-strategy commitment (take higher of current and counter)
    int counterCommitment = getCounterStrategyCommitment();
    if (counterCommitment > currentAttackCommitment) {
      currentAttackCommitment = counterCommitment;
    }

    // Apply attack window influence
    if (cachedAttackWindow != WINDOW_NONE) {
      int windowCommitment = getWindowCommitment(cachedAttackWindow);
      if (windowCommitment > currentAttackCommitment) {
        currentAttackCommitment = windowCommitment;
      }
    }

    // Write to shared array if changed
    if (currentAttackCommitment != oldCommitment) {
      rc.writeSharedArray(SLOT_ATTACK_COMMITMENT, currentAttackCommitment);
      if (DEBUG) {
        // Calculate effective attack percentage after accounting for gatherers
        // Assume ~20% gatherers on average (15% base + some emergency)
        int attackPct = getAttackPercentage(currentAttackCommitment);
        int effectiveAttackPct = (80 * attackPct) / 100; // ~80% are non-gatherers
        String commitName =
            (currentAttackCommitment >= 0 && currentAttackCommitment < 5)
                ? COMMITMENT_NAMES[currentAttackCommitment]
                : "UNK";
        System.out.println(
            "[COMMITMENT] R"
                + cachedRound
                + " changed to "
                + commitName
                + " (attackPct:"
                + attackPct
                + "% effectiveAttack:~"
                + effectiveAttackPct
                + "% kills:"
                + killsThisGame
                + " adv:"
                + cachedArmyAdvantage
                + " enemyHP:"
                + cachedEnemyKingHP
                + ")");
      }
    }

    // Write post-rush phase
    int phase = getPostRushPhase();
    if (phase != postRushPhase) {
      postRushPhase = phase;
      rc.writeSharedArray(SLOT_POST_RUSH_PHASE, phase);
    }
  }

  /**
   * Get recommended commitment level for an attack window.
   *
   * @param window The attack window type
   * @return Recommended commitment level
   */
  private static int getWindowCommitment(int window) {
    switch (window) {
      case WINDOW_WOUNDED_KING:
        return COMMITMENT_ALL_IN; // Finish them!
      case WINDOW_POST_RUSH:
        return COMMITMENT_RAID; // Counter-attack economy
      case WINDOW_ARMY_ADVANTAGE:
        return COMMITMENT_ASSAULT; // Press the advantage
      case WINDOW_ECONOMY:
        return COMMITMENT_ASSAULT; // We can afford to attack
      case WINDOW_LATE_GAME:
        return COMMITMENT_ASSAULT; // Time to commit
      case WINDOW_TURTLE_PUNISH:
        return COMMITMENT_RAID; // Don't let them build forever
      default:
        return COMMITMENT_PROBE; // Default scout behavior
    }
  }

  /**
   * Get target scoring bonus based on current commitment level. Different commitments prioritize
   * different targets.
   *
   * @param enemy The enemy robot to score
   * @param commitment Current attack commitment level
   * @return Bonus score to add to this target
   */
  private static int getCommitmentTargetBonus(RobotInfo enemy, int commitment) {
    boolean isKing = enemy.getType() == UnitType.RAT_KING;
    boolean carryingCheese = enemy.getRawCheeseAmount() > 0;
    boolean nearOurKing =
        cachedOurKingLoc != null && enemy.getLocation().distanceSquaredTo(cachedOurKingLoc) <= 36;
    boolean nearEnemyKing =
        cachedEnemyKingLoc != null
            && enemy.getLocation().distanceSquaredTo(cachedEnemyKingLoc) <= 36;
    int hp = enemy.getHealth();

    int bonus = 0;

    switch (commitment) {
      case COMMITMENT_RAID:
        // Economy raid priorities
        if (carryingCheese) bonus += 500; // Deny their economy
        if (nearEnemyKing) bonus += 300; // About to deliver
        if (hp < 30) bonus += 200; // Easy kill
        if (isKing) bonus -= 200; // Don't waste time on king yet
        break;

      case COMMITMENT_ASSAULT:
      case COMMITMENT_ALL_IN:
        // King assault priorities
        if (isKing) bonus += 1000; // PRIMARY TARGET
        if (nearEnemyKing && !isKing) bonus += 400; // Blocking our path
        break;

      case COMMITMENT_DEFEND:
        // Defense priorities
        if (nearOurKing) bonus += 800; // Immediate threat
        if (carryingCheese) bonus += 300; // Deny their economy
        break;

      case COMMITMENT_PROBE:
        // Probing - avoid fights, just observe
        bonus -= 100; // Slight penalty to attacking
        break;
    }

    return bonus;
  }

  /**
   * Record a kill and update the shared array. Called when we confirm killing an enemy rat.
   *
   * @param rc RobotController for writing to shared array
   * @throws GameActionException if shared array write fails
   */
  private static void recordKill(RobotController rc) throws GameActionException {
    killsThisGame++;
    if (rc.getType().isRatKingType()) {
      rc.writeSharedArray(SLOT_KILLS_THIS_GAME, Math.min(killsThisGame, 255));
    }
  }

  // NOTE: isAttackerByCommitment() removed - value function handles attack vs gather priority

  /**
   * Aggressive movement toward enemy king. Used when very close to enemy king. Ignores traps and
   * tries direct movement, prioritizing getting adjacent to king.
   */
  private static void moveToKingAggressive(RobotController rc, MapLocation kingLoc)
      throws GameActionException {
    if (!cachedMovementReady || kingLoc == null) return;

    int dx = kingLoc.x - myLocX;
    int dy = kingLoc.y - myLocY;

    // Already adjacent to king (within 3x3 area)
    if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) return;

    Direction toKing = myLoc.directionTo(kingLoc);
    if (toKing == Direction.CENTER) return;

    // Try direct movement first (ignore traps in aggressive mode)
    if (rc.canMove(toKing)) {
      rc.move(toKing);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }

    // Try adjacent directions
    Direction left = toKing.rotateLeft();
    if (rc.canMove(left)) {
      rc.move(left);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }

    Direction right = toKing.rotateRight();
    if (rc.canMove(right)) {
      rc.move(right);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }

    // Try more rotations
    Direction left2 = left.rotateLeft();
    if (rc.canMove(left2)) {
      rc.move(left2);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
      return;
    }

    Direction right2 = right.rotateRight();
    if (rc.canMove(right2)) {
      rc.move(right2);
      myLoc = rc.getLocation();
      myLocX = myLoc.x;
      myLocY = myLoc.y;
      cachedMovementReady = false;
    }
  }

  // ================================================================
  // SECTION 13: BUG2 PATHFINDING (from ratbot7)
  // ================================================================

  private static void bug2MoveTo(RobotController rc, MapLocation target)
      throws GameActionException {
    if (target == null || !cachedMovementReady) return;

    // === BYTECODE OPTIMIZATION: Cache target coordinates ===
    final int targetX = target.x;
    final int targetY = target.y;

    // CHARGE MODE: When close to enemy king, ignore traps and push forward
    boolean chargeMode = isInChargeMode();

    // Cache adjacent trap info (skip if in charge mode)
    if (!chargeMode) {
      cacheAdjacentTraps(rc);
    } else {
      adjacentTrapMask = 0; // Ignore all traps in charge mode
    }

    if (bug2Target == null || targetX != bug2Target.x || targetY != bug2Target.y) {
      bug2Target = target;
      bug2WallFollowing = false;
    }

    if (myLocX == targetX && myLocY == targetY) return;

    Direction toTarget = myLoc.directionTo(target);
    int toTargetOrd = toTarget.ordinal();

    if (!bug2WallFollowing) {
      // Try direct path first
      if (rc.canMove(toTarget) && ((adjacentTrapMask & (1 << toTargetOrd)) == 0)) {
        rc.move(toTarget);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        return;
      }

      // Try left rotation
      int leftOrd = (toTargetOrd + 7) & 7;
      Direction left = DIRECTIONS[leftOrd];
      if (rc.canMove(left) && ((adjacentTrapMask & (1 << leftOrd)) == 0)) {
        rc.move(left);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        return;
      }

      // Try right rotation
      int rightOrd = (toTargetOrd + 1) & 7;
      Direction right = DIRECTIONS[rightOrd];
      if (rc.canMove(right) && ((adjacentTrapMask & (1 << rightOrd)) == 0)) {
        rc.move(right);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        return;
      }

      bug2WallFollowing = true;
      bug2WallDir = toTarget;
      bug2StartLoc = myLoc;
      int sdx = myLocX - target.x;
      int sdy = myLocY - target.y;
      bug2StartDist = sdx * sdx + sdy * sdy;
    }

    if (bug2WallFollowing) {
      int cdx = myLocX - targetX;
      int cdy = myLocY - targetY;
      int currentDist = cdx * cdx + cdy * cdy;

      if (currentDist < bug2StartDist && (myLocX != bug2StartLoc.x || myLocY != bug2StartLoc.y)) {
        bug2WallFollowing = false;
        if (rc.canMove(toTarget) && ((adjacentTrapMask & (1 << toTargetOrd)) == 0)) {
          rc.move(toTarget);
          myLoc = rc.getLocation();
          myLocX = myLoc.x;
          myLocY = myLoc.y;
          return;
        }
      }

      // Unrolled wall-following loop
      int wallOrd = bug2WallDir.ordinal();

      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }
      wallOrd = (wallOrd + 1) & 7;
      if (rc.canMove(DIRECTIONS[wallOrd]) && ((adjacentTrapMask & (1 << wallOrd)) == 0)) {
        rc.move(DIRECTIONS[wallOrd]);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        bug2WallDir = PERP_LEFT_BY_DIR[wallOrd];
        return;
      }

      bug2WallDir = DIRECTIONS[(wallOrd + 1) & 7];
    }
  }

  private static void cacheAdjacentTraps(RobotController rc) throws GameActionException {
    adjacentTrapMask = 0;
    MapInfo[] nearby = rc.senseNearbyMapInfos(2);
    for (int i = nearby.length; --i >= 0; ) {
      MapInfo info = nearby[i];
      if (info.getTrap() == TrapType.RAT_TRAP) {
        MapLocation loc = info.getMapLocation();
        int dx = loc.x - myLocX;
        int dy = loc.y - myLocY;
        int ordinal = DX_DY_TO_DIR_ORDINAL[dx + 1][dy + 1];
        if (ordinal >= 0) adjacentTrapMask |= (1 << ordinal);
      }
    }
  }

  // ================================================================
  // SECTION 13: IMMEDIATE ACTIONS
  // ================================================================

  private static boolean tryImmediateAction(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    if (!cachedActionReady) return false;

    // Priority 1: Attack
    if (enemyCount > 0) {
      if (tryAttack(rc, enemies, enemyCount)) return true;
    }

    // Priority 2: Deliver
    if (cachedCarryingCheese
        && cachedDistToKingSq >= 0
        && cachedDistToKingSq <= DELIVERY_RANGE_SQ) {
      if (tryDeliverCheese(rc)) return true;
    }

    // Priority 3: Collect
    if (cheeseCount > 0) {
      if (tryCollectCheese(rc)) return true;
    }

    // Priority 4: Dig dirt blocking path
    if (tryDigDirt(rc)) return true;

    return false;
  }

  private static boolean tryAttack(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    if (!cachedActionReady) return false;

    // === BYTECODE OPTIMIZATION: Cache focus target for inner loop ===
    final MapLocation focusTarget = cachedFocusTarget;

    // Always attack enemy king first
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        // Use enhanced attack on enemy king (spend cheese for extra damage)
        int cheeseToSpend = Math.min(rc.getRawCheese(), 100);
        if (rc.canAttack(loc)) {
          if (cheeseToSpend > 10) {
            rc.attack(loc, cheeseToSpend);
          } else {
            rc.attack(loc);
          }
          // Track damage dealt to enemy king (baby rats can't write to shared array,
          // but we update local cache for our own targeting decisions)
          cachedDamageToEnemyKing += BASE_ATTACK_DAMAGE;
          cachedEnemyKingHP -= BASE_ATTACK_DAMAGE;
          if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
          cachedActionReady = false;
          return true;
        }
        // Try adjacent tiles of 3x3 king area
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = loc.translate(dx, dy);
            if (rc.canAttack(tile)) {
              if (cheeseToSpend > 10) {
                rc.attack(tile, cheeseToSpend);
              } else {
                rc.attack(tile);
              }
              cachedDamageToEnemyKing += BASE_ATTACK_DAMAGE;
              cachedEnemyKingHP -= BASE_ATTACK_DAMAGE;
              if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
              cachedActionReady = false;
              return true;
            }
          }
        }
      }
    }

    // Attack lowest HP enemy with focus fire bonus and overkill prevention
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;

    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (!enemy.getType().isBabyRatType()) continue;
      MapLocation loc = enemy.getLocation();
      if (!rc.canAttack(loc)) continue;

      int score = 1000 - enemy.getHealth();

      // Focus fire bonus
      if (focusTarget != null && loc.distanceSquaredTo(focusTarget) <= 2) {
        score += 5000; // Strong focus fire priority
      }

      // Overkill prevention - reduce priority for nearly-dead enemies
      // Note: We use a simple HP check here since allyBuffer may not be populated
      // in all calling contexts. The isOverkill() function is available for
      // contexts where we have ally data.
      if (enemy.getHealth() <= OVERKILL_HP_THRESHOLD) {
        score -= 200; // Slight penalty for nearly-dead enemies
      }

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    if (bestTarget != null) {
      // Use predictive targeting
      MapLocation targetLoc = bestTarget.getLocation();
      MapLocation predictedLoc = predictEnemyPosition(targetLoc);
      int targetHP = bestTarget.getHealth();

      // Try predicted location first, then actual location
      if (rc.canAttack(predictedLoc)) {
        rc.attack(predictedLoc);
        cachedActionReady = false;
        // Track kill for commitment escalation (10 damage per attack)
        if (targetHP <= 10) {
          killsThisGame++; // Local tracking - king will sync to shared array
        }
        return true;
      }
      if (rc.canAttack(targetLoc)) {
        rc.attack(targetLoc);
        cachedActionReady = false;
        if (targetHP <= 10) {
          killsThisGame++;
        }
        return true;
      }
    }
    return false;
  }

  private static boolean tryDeliverCheese(RobotController rc) throws GameActionException {
    if (!cachedCarryingCheese || cachedOurKingLoc == null) return false;

    int amount = rc.getRawCheese();
    if (amount <= 0) return false;

    // Early exit if out of transfer range - don't waste movement cooldown on turning
    if (cachedDistToKingSq > DELIVERY_RANGE_SQ) return false;

    // CRITICAL FIX: Must be FACING the king to transfer cheese (vision cone requirement)
    // Battlecode only allows 90-degree turns per round, so may need multiple turns!
    Direction toKing = myLoc.directionTo(cachedOurKingLoc);
    if (toKing == Direction.CENTER) {
      // We're standing on the king? Shouldn't happen but handle it
      return false;
    }

    // Check if we're facing the king (or close enough - within 45 degrees)
    // Vision cone is 90 degrees, so we need to be within 1 rotation of the target direction
    if (toKing != cachedFacing) {
      // Not facing king - need to turn
      // API: turn(Direction d) turns TOWARD d but only up to 90 degrees per turn!
      // So if we're facing opposite (180°), we need 2 turns to face the king
      if (rc.canTurn()) {
        rc.turn(toKing);
        // CRITICAL: Get the ACTUAL new facing direction after turn
        // Don't assume we're now facing toKing - we may have only turned 90°!
        cachedFacing = rc.getDirection();
        if (DEBUG) {
          System.out.println(
              "[DELIVERY TURN] R"
                  + cachedRound
                  + " ID:"
                  + rc.getID()
                  + " turned toward king, now facing:"
                  + cachedFacing
                  + " need:"
                  + toKing);
        }
        // Check if we're NOW facing the king after the turn
        if (cachedFacing == toKing) {
          // Fully turned - try to transfer immediately
          if (rc.canTransferCheese(cachedOurKingLoc, amount)) {
            rc.transferCheese(cachedOurKingLoc, amount);
            cachedCarryingCheese = false;
            if (DEBUG) {
              System.out.println(
                  "[DELIVERY SUCCESS] R"
                      + cachedRound
                      + " ID:"
                      + rc.getID()
                      + " delivered "
                      + amount
                      + " cheese to king!");
            }
            return true;
          }
        }
        // Either still turning (was 180° away) or transfer failed - try again next turn
        return false;
      } else {
        // Can't turn yet (movement on cooldown) - try again next round
        if (DEBUG && cachedRound % 10 == 0) {
          System.out.println(
              "[DELIVERY WAIT] R"
                  + cachedRound
                  + " ID:"
                  + rc.getID()
                  + " waiting to turn toward king, facing:"
                  + cachedFacing
                  + " need:"
                  + toKing);
        }
        return false;
      }
    }

    // We're facing the king - try to transfer
    if (rc.canTransferCheese(cachedOurKingLoc, amount)) {
      rc.transferCheese(cachedOurKingLoc, amount);
      cachedCarryingCheese = false;
      if (DEBUG) {
        System.out.println(
            "[DELIVERY SUCCESS] R"
                + cachedRound
                + " ID:"
                + rc.getID()
                + " delivered "
                + amount
                + " cheese to king!");
      }
      return true;
    }
    if (DEBUG && cachedRound % 10 == 0) {
      System.out.println(
          "[DELIVERY FAIL] R"
              + cachedRound
              + " ID:"
              + rc.getID()
              + " can't transfer "
              + amount
              + " cheese, distToKing:"
              + cachedDistToKingSq
              + " kingLoc:"
              + cachedOurKingLoc
              + " myLoc:"
              + myLoc
              + " facing:"
              + cachedFacing
              + " dirToKing:"
              + toKing);
    }
    return false;
  }

  private static boolean tryCollectCheese(RobotController rc) throws GameActionException {
    if (!cachedActionReady) return false;

    for (int i = cheeseCount; --i >= 0; ) {
      MapLocation cheese = cheeseBuffer[i];
      int cdx = myLocX - cheese.x;
      int cdy = myLocY - cheese.y;
      if (cdx * cdx + cdy * cdy <= 2) {
        if (rc.canPickUpCheese(cheese)) {
          rc.pickUpCheese(cheese);
          cachedCarryingCheese = true;
          // Clear squeak throttle so we can immediately squeak about new cheese elsewhere
          if (lastSqueakedCheeseLoc != null && lastSqueakedCheeseLoc.equals(cheese)) {
            lastSqueakedCheeseLoc = null;
          }
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Dig dirt blocking our path TOWARD OUR TARGET (not where we're facing). BYTECODE OPT: Uses
   * adjacentLocation.
   */
  private static boolean tryDigDirt(RobotController rc) throws GameActionException {
    if (!cachedActionReady) return false;

    // FIX: Dig toward our TARGET, not where we're currently facing!
    // The rat needs to dig through walls blocking the path to its destination.
    Direction targetDir = Direction.CENTER;

    // If wall-following, use the bug2 target (highest priority - we're stuck!)
    if (bug2WallFollowing && bug2Target != null) {
      targetDir = myLoc.directionTo(bug2Target);
    }
    // Priority 1: Current best target from value function
    else if (cachedBestTarget != null) {
      targetDir = myLoc.directionTo(cachedBestTarget);
    }
    // Priority 2: Enemy king (for attackers)
    else if (cachedEnemyKingLoc != null) {
      targetDir = myLoc.directionTo(cachedEnemyKingLoc);
    }
    // Priority 3: Our king (for delivery/gatherers)
    else if (cachedOurKingLoc != null) {
      targetDir = myLoc.directionTo(cachedOurKingLoc);
    }
    // Fallback: Use facing direction if no target
    else {
      targetDir = cachedFacing;
    }

    if (targetDir == Direction.CENTER) return false;

    // Try target direction first
    MapLocation ahead = rc.adjacentLocation(targetDir);
    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      cachedActionReady = false;
      return true;
    }

    // Try rotations to catch diagonal approaches
    Direction left = targetDir.rotateLeft();
    Direction right = targetDir.rotateRight();

    MapLocation leftAhead = rc.adjacentLocation(left);
    MapLocation rightAhead = rc.adjacentLocation(right);

    if (rc.canRemoveDirt(leftAhead)) {
      rc.removeDirt(leftAhead);
      cachedActionReady = false;
      return true;
    }
    if (rc.canRemoveDirt(rightAhead)) {
      rc.removeDirt(rightAhead);
      cachedActionReady = false;
      return true;
    }

    return false;
  }

  // ================================================================
  // SECTION 14: KING BEHAVIOR
  // ================================================================

  private static void runKing(RobotController rc) throws GameActionException {
    // Check for game over - resign if we're dead (shouldn't happen, but safety check)
    if (rc.getHealth() <= 0) {
      rc.resign();
      return;
    }

    if (PROFILE) bcAfterInit = Clock.getBytecodeNum();

    MapLocation me = rc.getLocation();
    int hp = rc.getHealth();
    cachedOurKingHP = hp;

    // Note: Local caching of me.x, me.y not needed here as they're only used once for broadcast

    // Broadcast position and HP
    rc.writeSharedArray(SLOT_OUR_KING_X, me.x);
    rc.writeSharedArray(SLOT_OUR_KING_Y, me.y);
    rc.writeSharedArray(SLOT_OUR_KING_HP, Math.min(hp >> 3, 63));

    // DEBUG: Log king state every 10 rounds (includes profile name for easy identification)
    if (DEBUG && cachedRound % 10 == 0) {
      int kingCarried = rc.getRawCheese();
      String commitName =
          (currentAttackCommitment >= 0 && currentAttackCommitment < 5)
              ? COMMITMENT_NAMES[currentAttackCommitment]
              : "UNK";
      // Value function handles cheese vs attack priority - no fixed gatherer percentage
      int attackPct = getAttackPercentage(currentAttackCommitment);
      System.out.println(
          "[R8 KING "
              + PROFILE_NAME
              + "] R"
              + cachedRound
              + " HP:"
              + hp
              + " globalCheese:"
              + cachedGlobalCheese
              + " kingCheese:"
              + kingCarried
              + " spawns:"
              + spawnCount
              + " commit:"
              + commitName
              + " attackPct:"
              + attackPct
              + "% starvation:"
              + cachedStarvationRounds);
    }

    updateGameState(rc);

    // Sense all robots (including cats)
    RobotInfo[] allRobots = rc.senseNearbyRobots(-1);
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    int enemyCount = enemies.length;

    // === PHASE 2 DEFENSE SYSTEMS ===
    // Check for cats and flee if necessary
    MapLocation dangerousCat = findDangerousCat(allRobots);
    if (dangerousCat != null) {
      if (kingFleeFromCat(rc, dangerousCat)) {
        // Update location after fleeing
        me = rc.getLocation();
        myLoc = me;
        myLocX = me.x;
        myLocY = me.y;
      }
    }

    // Calculate starvation prediction from GLOBAL cheese (base case)
    cachedStarvationRounds =
        cachedGlobalCheese
            / CHEESE_PER_ROUND_KING; // CONDITIONAL HYBRID: Only trigger urgency when there's a REAL
    // delivery bottleneck
    // i.e., king is actually running low AND there's cheese available on the map
    // CRITICAL FIX: Don't trigger in early game (round <= 50) when king naturally has 0 cheese!
    // The old condition was ALWAYS true early game, causing ALL rats to gather instead of attack.
    int kingCarriedCheese = rc.getRawCheese();
    if (kingCarriedCheese < 30 && cachedGlobalCheese > 300 && cachedRound > 50) {
      // King has < 10 rounds of cheese but plenty on map = delivery bottleneck!
      // Force urgency by capping starvation at 30 rounds (triggers WARNING not CRITICAL)
      int oldStarvation = cachedStarvationRounds;
      cachedStarvationRounds = Math.min(cachedStarvationRounds, 30);
      if (DEBUG) {
        System.out.println(
            "[STARVATION HYBRID] R"
                + cachedRound
                + " kingCheese:"
                + kingCarriedCheese
                + " globalCheese:"
                + cachedGlobalCheese
                + " starvation:"
                + oldStarvation
                + "->"
                + cachedStarvationRounds);
      }
    }
    if (DEBUG && cachedRound % 20 == 0) {
      System.out.println(
          "[KING STARVATION] R"
              + cachedRound
              + " kingCheese:"
              + kingCarriedCheese
              + " globalCheese:"
              + cachedGlobalCheese
              + " starvationRounds:"
              + cachedStarvationRounds);
    }

    // BUG FIX: Set cachedOurKingLoc to current position BEFORE updateEmergencyState
    // Otherwise updateEmergencyState returns early because cachedOurKingLoc is null/stale
    cachedOurKingLoc = me;

    // Update emergency state based on enemies near king
    updateEmergencyState(enemies, enemyCount);

    // Update blocking line for body blocking
    updateBlockingLine(enemies, enemyCount);

    // Write defense state to shared array
    rc.writeSharedArray(SLOT_STARVATION_ROUNDS, Math.min(cachedStarvationRounds, 255));
    rc.writeSharedArray(SLOT_EMERGENCY_LEVEL, cachedEmergencyLevel);
    rc.writeSharedArray(SLOT_ENEMIES_NEAR_KING, cachedEnemiesNearKing);
    if (cachedBlockingLineCenter != null) {
      rc.writeSharedArray(SLOT_BLOCKING_LINE_X, cachedBlockingLineCenter.x);
      rc.writeSharedArray(SLOT_BLOCKING_LINE_Y, cachedBlockingLineCenter.y);
      // Ensure direction ordinal is valid (0-7, not CENTER=8)
      int dirOrd = cachedBlockingLineDir.ordinal();
      if (dirOrd > 7) dirOrd = 0;
      rc.writeSharedArray(SLOT_BLOCKING_LINE_DIR, dirOrd);
    } else {
      rc.writeSharedArray(SLOT_BLOCKING_LINE_X, 0);
      rc.writeSharedArray(SLOT_BLOCKING_LINE_Y, 0);
    }

    // Update threat level and army advantage
    rc.writeSharedArray(SLOT_THREAT_LEVEL, enemyCount);

    // Write enemies to ring buffer for predictive targeting
    for (int i = 0; i < enemyCount && i < 2; i++) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType()) {
        writeEnemyToRingBuffer(rc, enemy.getLocation());
      }
    }

    if (PROFILE) bcAfterSense = Clock.getBytecodeNum();

    // Calculate army advantage (our rats - enemy rats visible)
    RobotInfo[] allies = rc.senseNearbyRobots(-1, cachedOurTeam);
    int allyRatCount = 0;
    int enemyRatCount = 0;
    int attackersNearEnemy = 0;
    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getType().isBabyRatType()) {
        allyRatCount++;
        // Count attackers near enemy king
        if (cachedEnemyKingLoc != null) {
          int distToEnemyKing = allies[i].getLocation().distanceSquaredTo(cachedEnemyKingLoc);
          if (distToEnemyKing <= 100) { // Within 10 tiles
            attackersNearEnemy++;
          }
        }
      }
    }
    for (int i = enemyCount; --i >= 0; ) {
      if (enemies[i].getType().isBabyRatType()) enemyRatCount++;
    }
    cachedArmyAdvantage = allyRatCount - enemyRatCount;
    cachedAttackersNearEnemy = attackersNearEnemy;
    rc.writeSharedArray(
        SLOT_ARMY_ADVANTAGE, cachedArmyAdvantage + 50); // offset by 50 to handle negatives
    rc.writeSharedArray(SLOT_ATTACKERS_NEAR_ENEMY, attackersNearEnemy);

    // Phase 3: Update race mode when kings are low
    updateRaceMode(rc, cachedRound, hp, allyRatCount);

    // Phase 3: Classify opponent behavior
    classifyOpponentBehavior(rc, cachedRound, cachedEnemiesNearKing, enemyCount);

    // Phase 3: Detect attack windows and check for all-in condition
    detectAttackWindow(rc, cachedRound, hp, cachedGlobalCheese, cachedArmyAdvantage);

    // Strategic Attack Intelligence: Update attack commitment level
    updateAttackCommitment(rc);

    checkAndBroadcastAllIn(rc, cachedRound, cachedGlobalCheese, allyRatCount);

    // Update game state with hysteresis
    int newState =
        determineGameState(
            hp, cachedGlobalCheese, enemyCount, cachedEnemyKingHP, cachedArmyAdvantage);
    if (newState != currentGameState) {
      currentGameState = newState;
      rc.writeSharedArray(SLOT_GAME_STATE, newState);
    }

    // Clear role counts for this round
    rc.writeSharedArray(SLOT_CORE_COUNT, 0);
    rc.writeSharedArray(SLOT_SCOUT_COUNT, 0);
    rc.writeSharedArray(SLOT_RAIDER_COUNT, 0);
    rc.writeSharedArray(SLOT_ASSASSIN_COUNT, 0);

    // Update focus fire target
    if (enemyCount > 0) {
      updateFocusFireTarget(rc, enemies);
    }

    if (PROFILE) bcAfterScore = Clock.getBytecodeNum();

    // === KING SELF-DEFENSE: Attack adjacent enemies ===
    if (rc.isActionReady() && enemyCount > 0) {
      // Priority 1: Attack enemy king if adjacent
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType() == UnitType.RAT_KING) {
          MapLocation loc = enemy.getLocation();
          if (rc.canAttack(loc)) {
            int cheeseToSpend = Math.min(rc.getRawCheese(), 100);
            if (cheeseToSpend > 10) {
              rc.attack(loc, cheeseToSpend);
            } else {
              rc.attack(loc);
            }
            if (DEBUG) {
              System.out.println("[R8 KING] R" + cachedRound + " ATTACKED enemy king!");
            }
            break; // Stop checking other enemies - action consumed
          }
        }
      }

      // Priority 2: Attack lowest HP adjacent baby rat
      RobotInfo bestTarget = null;
      int lowestHP = Integer.MAX_VALUE;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        if (!enemy.getType().isBabyRatType()) continue;
        MapLocation loc = enemy.getLocation();
        if (!rc.canAttack(loc)) continue;
        if (enemy.getHealth() < lowestHP) {
          lowestHP = enemy.getHealth();
          bestTarget = enemy;
        }
      }
      if (bestTarget != null && rc.isActionReady()) {
        rc.attack(bestTarget.getLocation());
        if (DEBUG) {
          System.out.println("[R8 KING] R" + cachedRound + " ATTACKED enemy rat HP:" + lowestHP);
        }
      }
    }

    // ===== TRAP BUILDING =====
    // Build traps BEFORE spawning to establish defenses
    if (rc.isActionReady()) {
      tryBuildTraps(rc, me);
    }

    // Spawning logic - BALANCED with anti-starvation throttling
    // CRITICAL FIX: ratbot8 was over-spawning (36 rats in 50 rounds = all cheese drained)
    // This caused king starvation and loss. Now we throttle spawning to maintain economy.
    int spawnCost = rc.getCurrentRatCost();
    int round = cachedRound;

    // SPAWN CAP: Much more conservative in early game
    // Early game (R0-100): Hard cap of 15 rats to establish economy first
    // After R100: Gradually increase cap by +1 every 20 rounds
    int effectiveSpawnCap;
    if (round < 100) {
      effectiveSpawnCap = EARLY_GAME_SPAWN_CAP; // 15 rats max in first 100 rounds
    } else {
      int bonusSpawns = (round - SPAWN_CAP_INCREASE_START) / SPAWN_CAP_INCREASE_INTERVAL;
      effectiveSpawnCap = SPAWN_CAP_MAX + bonusSpawns;
    }
    boolean underSpawnCap = spawnCount < effectiveSpawnCap;

    // CHEESE FLOOR: Never spawn if cheese drops below floor
    // This ensures we always have cheese for king to consume
    boolean aboveCheeseFloor = cachedGlobalCheese >= CHEESE_FLOOR;

    // Reserve calculation: Higher reserve when cheese is low
    int cheeseReserve;
    if (cachedGlobalCheese < 800) {
      cheeseReserve = ABSOLUTE_CHEESE_RESERVE * 2; // 600 reserve when low
    } else if (round < EARLY_GAME_ROUND) {
      cheeseReserve = EARLY_GAME_CHEESE_RESERVE;
    } else {
      cheeseReserve = ABSOLUTE_CHEESE_RESERVE;
    }
    boolean canAfford = cachedGlobalCheese > spawnCost + cheeseReserve;

    // SPAWN RATE LIMITING: When cheese < 800, limit spawn rate
    // This prevents rapid cheese drain that killed us on pipes map
    boolean rateLimited = false;
    if (cachedGlobalCheese < 800 && spawnCount > 8) {
      rateLimited = (round % SPAWN_RATE_LIMIT_ROUNDS != 0);
    }

    // STARVATION PREVENTION: Apply from round 1, not just after early game
    // This is critical - the old code waited until round 50 to check starvation!
    if (cachedStarvationRounds < STARVATION_CRITICAL_ROUNDS) {
      canAfford = false; // Critical - no spawning, save all cheese for king
    } else if (cachedStarvationRounds < STARVATION_WARNING_ROUNDS) {
      // Warning level - only spawn every 10 rounds
      if (round % 10 != 0) {
        canAfford = false;
      }
    }

    // Combine all conditions
    canAfford = canAfford && aboveCheeseFloor && !rateLimited;

    // Emergency override - spawn guardians even when low on cheese
    // BUT only if we're above cheese floor!
    if (cachedInEmergency
        && cachedGlobalCheese >= spawnCost + 50
        && cachedGlobalCheese >= CHEESE_FLOOR) {
      canAfford = true;
    }

    if (canAfford && underSpawnCap && rc.isActionReady()) {
      if (trySpawnRat(rc)) {
        spawnCount++;
        rc.writeSharedArray(SLOT_SPAWN_COUNT, spawnCount);
        if (DEBUG) {
          System.out.println(
              "[R8 SPAWN] R"
                  + cachedRound
                  + " spawn #"
                  + spawnCount
                  + " cost:"
                  + spawnCost
                  + " cheese:"
                  + cachedGlobalCheese
                  + " cap:"
                  + effectiveSpawnCap);
        }
      }
    } else if (DEBUG && cachedRound % 20 == 0) {
      System.out.println(
          "[R8 NO_SPAWN] R"
              + cachedRound
              + " canAfford:"
              + canAfford
              + " underCap:"
              + underSpawnCap
              + " aboveFloor:"
              + aboveCheeseFloor
              + " rateLimited:"
              + rateLimited
              + " actionReady:"
              + rc.isActionReady()
              + " cost:"
              + spawnCost
              + " reserve:"
              + cheeseReserve
              + " cheese:"
              + cachedGlobalCheese
              + " starvation:"
              + cachedStarvationRounds
              + " cap:"
              + effectiveSpawnCap);
    }

    // DEBUG: Log spawn count at key early-game milestones to verify spawning rate
    if (DEBUG && (round == 30 || round == 50)) {
      System.out.println(
          "[R8 SPAWN_CHECK] R"
              + round
              + " spawnCount:"
              + spawnCount
              + " cap:"
              + effectiveSpawnCap
              + " cheese:"
              + cachedGlobalCheese);
    }

    if (PROFILE) bcAfterAction = Clock.getBytecodeNum();

    // King movement - ONLY evade from enemies, stay behind traps otherwise
    if (rc.isMovementReady() && enemyCount > 0) {
      evadeFromEnemies(rc, enemies);
    }

    // Read squeaks from baby rats about enemy king position and cheese locations
    kingReadSqueaks(rc);

    // King also senses and broadcasts nearby cheese locations
    kingBroadcastCheese(rc);

    // Broadcast enemy king if visible
    broadcastEnemyKing(rc, enemies);

    if (PROFILE) bcAfterMove = Clock.getBytecodeNum();
  }

  /**
   * King reads squeaks from baby rats and updates shared array with enemy king position and cheese
   * locations.
   */
  private static void kingReadSqueaks(RobotController rc) throws GameActionException {
    Message[] squeaks = rc.readSqueaks(-1);
    int len = squeaks.length;
    if (len == 0) return;

    int limit = Math.min(len, 8); // Read up to 8 squeaks

    // Track cheese locations found in squeaks (up to 3)
    int cheeseFound = 0;
    int[] cheeseX = new int[3];
    int[] cheeseY = new int[3];

    for (int i = len - 1; i >= len - limit && i >= 0; --i) {
      int content = squeaks[i].getBytes();
      int type = (content >> 28) & 0xF;

      if (type == 1) { // Enemy king position squeak
        int y = (content >> 16) & 0xFFF;
        int x = (content >> 4) & 0xFFF;
        int hpBits = content & 0xF;
        int hp = hpBits * 35;

        // Update shared array
        rc.writeSharedArray(SLOT_ENEMY_KING_X, x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, y);
        rc.writeSharedArray(SLOT_ENEMY_KING_CONFIRMED, 1);

        // Update local cache
        if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
          cachedEnemyKingLoc = new MapLocation(x, y);
        }
        if (hp > 0) {
          cachedEnemyKingHP = hp;
        }
        enemyKingConfirmed = true;
      } else if (type == 2 && cheeseFound < 3) { // Cheese location squeak
        // SQUEAK FORMAT: type(4 bits) | y(12 bits) | x(12 bits) | reserved(4 bits)
        // Extract full 12-bit coordinates from squeak, then pack to 5-bit for shared array
        int y = (content >> 16) & 0xFFF; // 12 bits for y (full precision)
        int x = (content >> 4) & 0xFFF; // 12 bits for x (full precision)
        // Validate coordinates are within map bounds only
        // Note: Coordinates will be packed to 5-bit (/2) for shared array storage
        if (x >= 0 && x < cachedMapWidth && y >= 0 && y < cachedMapHeight) {
          // Store unique cheese locations
          boolean duplicate = false;
          for (int j = 0; j < cheeseFound; j++) {
            if (cheeseX[j] == x && cheeseY[j] == y) {
              duplicate = true;
              break;
            }
          }
          if (!duplicate) {
            cheeseX[cheeseFound] = x;
            cheeseY[cheeseFound] = y;
            cheeseFound++;
          }
        }
      }
    }

    // Write cheese locations to shared array if we found any
    if (cheeseFound > 0) {
      // SHARED ARRAY FORMAT: 10-bit limit (0-1023)
      // Round is stored modulo 1024 - matches rarely exceed 1000 rounds
      rc.writeSharedArray(SLOT_CHEESE_ROUND, cachedRound & 1023);
      // Pack x,y: 5 bits each with /2 precision (max 31*32+31=1023)
      // Precision loss: odd coordinates become even (e.g., 51→50)
      // This ~2-tile precision is acceptable for cheese pathfinding
      if (cheeseFound >= 1) {
        int packed = ((cheeseX[0] >> 1) & 0x1F) | (((cheeseY[0] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_1, packed);
      }
      if (cheeseFound >= 2) {
        int packed = ((cheeseX[1] >> 1) & 0x1F) | (((cheeseY[1] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_2, packed);
      }
      if (cheeseFound >= 3) {
        int packed = ((cheeseX[2] >> 1) & 0x1F) | (((cheeseY[2] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_3, packed);
      }
    }
  }

  private static void updateFocusFireTarget(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;

    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      int score = (enemy.getType() == UnitType.RAT_KING) ? 10000 : 1000;
      score -= enemy.getHealth();

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    if (bestTarget != null) {
      rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.getID() & 1023);
      rc.writeSharedArray(SLOT_FOCUS_HP, Math.min(bestTarget.getHealth() >> 3, 63));
      rc.writeSharedArray(SLOT_FOCUS_ROUND, cachedRound & 1023);
    }
  }

  private static boolean trySpawnRat(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();

    // Spawn TOWARD enemy - attackers should face the enemy immediately!
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = kingLoc.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    Direction[] spawnPriority = new Direction[8];
    spawnPriority[0] = toEnemy;
    spawnPriority[1] = toEnemy.rotateLeft();
    spawnPriority[2] = toEnemy.rotateRight();
    spawnPriority[3] = toEnemy.rotateLeft().rotateLeft();
    spawnPriority[4] = toEnemy.rotateRight().rotateRight();
    spawnPriority[5] = toEnemy.opposite().rotateLeft();
    spawnPriority[6] = toEnemy.opposite().rotateRight();
    spawnPriority[7] = toEnemy.opposite();

    for (int dist = 2; dist <= 4; dist++) {
      for (int i = 0; i < 8; i++) {
        Direction dir = spawnPriority[i];
        MapLocation spawnLoc = kingLoc.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canBuildRat(spawnLoc)) {
          rc.buildRat(spawnLoc);
          return true;
        }
      }
    }
    return false;
  }

  private static void evadeFromEnemies(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    if (!rc.isMovementReady()) return;

    MapLocation me = rc.getLocation();
    int sumX = 0, sumY = 0;
    for (int i = enemies.length; --i >= 0; ) {
      MapLocation loc = enemies[i].getLocation();
      sumX += loc.x;
      sumY += loc.y;
    }
    int centerX = sumX / enemies.length;
    int centerY = sumY / enemies.length;

    int dx = me.x - centerX;
    int dy = me.y - centerY;
    Direction awayDir = directionFromDelta(dx, dy);

    if (awayDir != Direction.CENTER && rc.canMove(awayDir)) {
      rc.move(awayDir);
    }
  }

  /** King senses nearby cheese and broadcasts to shared array. */
  private static void kingBroadcastCheese(RobotController rc) throws GameActionException {
    // Only broadcast every 5 rounds to save bytecode
    if (cachedRound % 5 != 0) return;

    // Sense nearby cheese
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, 20);
    int foundCount = 0;
    int[] foundX = new int[3];
    int[] foundY = new int[3];

    for (int i = nearbyTiles.length; --i >= 0 && foundCount < 3; ) {
      MapInfo info = nearbyTiles[i];
      if (info.getCheeseAmount() > 0) {
        MapLocation loc = info.getMapLocation();
        foundX[foundCount] = loc.x;
        foundY[foundCount] = loc.y;
        foundCount++;
      }
    }

    // Write to shared array if we found any
    if (foundCount > 0) {
      rc.writeSharedArray(SLOT_CHEESE_ROUND, cachedRound & 1023); // Limit to 10 bits
      // Pack x,y into single value (5 bits each with /2 precision, max 1023)
      if (foundCount >= 1) {
        int packed = ((foundX[0] >> 1) & 0x1F) | (((foundY[0] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_1, packed);
      }
      if (foundCount >= 2) {
        int packed = ((foundX[1] >> 1) & 0x1F) | (((foundY[1] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_2, packed);
      }
      if (foundCount >= 3) {
        int packed = ((foundX[2] >> 1) & 0x1F) | (((foundY[2] >> 1) & 0x1F) << 5);
        rc.writeSharedArray(SLOT_CHEESE_LOC_3, packed);
      }
    }
  }

  private static void broadcastEnemyKing(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        int actualHP = enemy.getHealth();
        rc.writeSharedArray(SLOT_ENEMY_KING_X, loc.x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, loc.y);
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, Math.min(actualHP >> 3, 63));
        rc.writeSharedArray(SLOT_ENEMY_KING_CONFIRMED, 1);
        // Confirm enemy king HP with precise tracking
        confirmEnemyKingHP(rc, actualHP, cachedRound);
        return;
      }
    }
  }

  /** Build traps around the king for defense. */
  private static void tryBuildTraps(RobotController rc, MapLocation kingLoc)
      throws GameActionException {
    if (!rc.isActionReady()) return;

    // Priority 1: Build cat traps first (up to MAX_CAT_TRAPS)
    if (catTrapsBuilt < MAX_CAT_TRAPS && cachedGlobalCheese >= CAT_TRAP_COST + 50) {
      if (DEBUG && cachedRound % 20 == 0) {
        System.out.println(
            "[R8 TRAP] R"
                + cachedRound
                + " trying cat trap #"
                + (catTrapsBuilt + 1)
                + " cheese:"
                + cachedGlobalCheese);
      }
      // Try to build cat trap in ring around king
      for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        MapLocation trapLoc =
            kingLoc.translate(dir.dx * CAT_TRAP_RING_DIST, dir.dy * CAT_TRAP_RING_DIST);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapsBuilt++;
          return;
        }
      }
    }

    // Priority 2: Build rat traps after some cat traps (up to MAX_RAT_TRAPS)
    if (catTrapsBuilt >= 2
        && ratTrapsBuilt < MAX_RAT_TRAPS
        && cachedGlobalCheese >= RAT_TRAP_COST + 50) {
      // Try ring 2 first
      for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        MapLocation trapLoc =
            kingLoc.translate(dir.dx * CAT_TRAP_RING_DIST, dir.dy * CAT_TRAP_RING_DIST);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapsBuilt++;
          return;
        }
      }
      // Try ring 3
      for (int i = 0; i < 8; i++) {
        Direction dir = DIRECTIONS[i];
        MapLocation trapLoc =
            kingLoc.translate(dir.dx * RAT_TRAP_RING_DIST, dir.dy * RAT_TRAP_RING_DIST);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapsBuilt++;
          return;
        }
      }
    }
  }

  /** King moves proactively toward center/enemy when safe. */
  private static void proactiveKingMovement(RobotController rc, MapLocation kingLoc)
      throws GameActionException {
    if (!rc.isMovementReady()) return;

    // Calculate target coordinates (avoid MapLocation allocation)
    int targetX, targetY;

    if (cachedEnemyKingLoc != null && enemyKingConfirmed) {
      // If we know enemy king location, move 1/4 of the way toward them
      targetX = kingLoc.x + (cachedEnemyKingLoc.x - kingLoc.x) / 4;
      targetY = kingLoc.y + (cachedEnemyKingLoc.y - kingLoc.y) / 4;
    } else {
      // Move toward map center
      targetX = cachedMapWidth / 2;
      targetY = cachedMapHeight / 2;
    }

    // Only move if we're not already close to target
    int dx = kingLoc.x - targetX;
    int dy = kingLoc.y - targetY;
    int distSq = dx * dx + dy * dy;
    if (distSq <= 16) return; // Already close enough (4 tiles)

    // Cache target only when needed for directionTo
    if (cachedProactiveTarget == null
        || cachedProactiveTarget.x != targetX
        || cachedProactiveTarget.y != targetY) {
      cachedProactiveTarget = new MapLocation(targetX, targetY);
    }

    Direction toTarget = kingLoc.directionTo(cachedProactiveTarget);
    if (toTarget == Direction.CENTER) return;

    // Try to move toward target
    if (rc.canMove(toTarget)) {
      rc.move(toTarget);
      return;
    }
    // Try adjacent directions
    if (rc.canMove(toTarget.rotateLeft())) {
      rc.move(toTarget.rotateLeft());
      return;
    }
    if (rc.canMove(toTarget.rotateRight())) {
      rc.move(toTarget.rotateRight());
    }
  }

  // ================================================================
  // SECTION 15: BABY RAT BEHAVIOR
  // ================================================================

  private static void runBabyRat(RobotController rc) throws GameActionException {
    if (rc.isBeingThrown() || rc.isBeingCarried()) return;

    updateGameState(rc);

    // PORTED FROM RATBOT7: Hysteresis for explore mode - MUST BE EARLY before any returns!
    // Enter explore mode when cheese < 200, exit when cheese > 500
    // This prevents oscillation where gatherers flip between patrol/explore every few rounds
    if (cachedGlobalCheese < STARVATION_THRESHOLD) {
      shouldExploreForCheese = true; // Enter explore mode
    } else if (cachedGlobalCheese > STARVATION_EXPLORE_EXIT) {
      shouldExploreForCheese = false; // Exit explore mode
    }
    // Otherwise keep previous state (hysteresis)

    if (PROFILE) bcAfterInit = Clock.getBytecodeNum();

    // === BYTECODE OPTIMIZATION: Cache frequently used values as locals ===
    final int locX = myLocX;
    final int locY = myLocY;
    final MapLocation ourKing = cachedOurKingLoc;
    final MapLocation enemyKing = cachedEnemyKingLoc;
    final int distToKing = cachedDistToKingSq;

    int id = rc.getID();
    int myHP = rc.getHealth();
    int role = getRatRole(id);

    // Starvation rounds, emergency level, and blocking line are now read in updateGameState

    // Early game: CORE rats attack like FLEX until round 30 BUT only if no enemies are near
    // CRITICAL FIX: Keep at least SOME guardians at all times to defend against rushes!
    // IDs 0-1 (roleSelector < 2) stay as guardians even in early game
    // IDs 2-4 (roleSelector 2-4) become temporary FLEX attackers
    // Note: getRatRole() assigns ROLE_CORE to IDs 0-4, so this keeps 40% of CORE as guardians
    if (role == ROLE_CORE && cachedRound < ALL_IN_MIN_ROUND && cachedEmergencyLevel == 0) {
      int roleSelector = id % 100;
      if (roleSelector >= 2) {
        role = ROLE_FLEX; // IDs 2-4 become temporary attackers in early game
      }
      // IDs 0-1 stay as ROLE_CORE guardians
    }

    // Sense robots
    RobotInfo[] allRobots = rc.senseNearbyRobots(-1);
    int enemyCount = 0;
    int allyCount = 0;
    MapLocation seenEnemyKingLoc = null;

    for (int i = allRobots.length; --i >= 0; ) {
      RobotInfo robot = allRobots[i];
      Team robotTeam = robot.getTeam();
      if (robotTeam == cachedEnemyTeam) {
        enemyBuffer[enemyCount++] = robot;
        // Track enemy king sighting
        if (robot.getType() == UnitType.RAT_KING) {
          seenEnemyKingLoc = robot.getLocation();
        }
      } else if (robotTeam == cachedOurTeam) {
        allyBuffer[allyCount++] = robot;
      }
    }

    // If we saw enemy king, update our local cache immediately
    // (Baby rats can't write to shared array, but local cache helps targeting)
    if (seenEnemyKingLoc != null) {
      if (cachedEnemyKingLoc == null || !cachedEnemyKingLoc.equals(seenEnemyKingLoc)) {
        cachedEnemyKingLoc = seenEnemyKingLoc;
      }
    } else if (cachedEnemyKingLoc != null) {
      // VERIFY-ON-ARRIVAL: If we're close to cached enemy king location but don't see them,
      // force a re-read next turn to get updated position (prevents circling at stale location)
      int dxToEnemy = locX - cachedEnemyKingLoc.x;
      int dyToEnemy = locY - cachedEnemyKingLoc.y;
      int distToEnemyKingSq = dxToEnemy * dxToEnemy + dyToEnemy * dyToEnemy;
      if (distToEnemyKingSq <= 25) { // Within 5 tiles but don't see king
        lastKingPosReadRound = -100; // Force re-read next turn
      }
    }

    if (PROFILE) bcAfterSense = Clock.getBytecodeNum();

    // Cat avoidance using Phase 2 defense system
    MapLocation nearestCatLoc = findDangerousCat(allRobots);
    if (nearestCatLoc != null) {
      int catDx = locX - nearestCatLoc.x;
      int catDy = locY - nearestCatLoc.y;
      int nearestCatDistSq = catDx * catDx + catDy * catDy;
      if (nearestCatDistSq <= CAT_FLEE_RADIUS_SQ) {
        Direction awayFromCat = nearestCatLoc.directionTo(myLoc);
        if (awayFromCat == Direction.CENTER) awayFromCat = Direction.SOUTH;
        if (cachedMovementReady) {
          MapLocation fleeTarget =
              new MapLocation(
                  myLocX + DIR_DX[awayFromCat.ordinal()] * 3,
                  myLocY + DIR_DY[awayFromCat.ordinal()] * 3);
          bug2MoveToUrgent(rc, fleeTarget); // Use urgent movement to flee cat
          return;
        }
      }
    }

    // ANTI-CROWDING: If too many friendlies nearby, spread out before doing anything else
    // This prevents traffic jams that trap us in constrained spaces
    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(CROWDING_CHECK_RADIUS_SQ, cachedOurTeam);
    if (nearbyAllies.length > CROWDING_THRESHOLD) {
      // Calculate centroid of nearby allies and move away
      int sumDx = 0, sumDy = 0;
      for (int i = nearbyAllies.length; --i >= 0; ) {
        MapLocation allyLoc = nearbyAllies[i].getLocation();
        sumDx += allyLoc.x - locX;
        sumDy += allyLoc.y - locY;
      }
      Direction awayFromCrowd = directionFromDelta(-sumDx, -sumDy);
      if (awayFromCrowd != Direction.CENTER && cachedMovementReady && rc.canMove(awayFromCrowd)) {
        rc.move(awayFromCrowd);
        myLoc = rc.getLocation();
        myLocX = myLoc.x;
        myLocY = myLoc.y;
        cachedMovementReady = false;
        if (DEBUG && cachedRound % 20 == 0) {
          System.out.println(
              "[ANTI-CROWD] R"
                  + cachedRound
                  + " ID:"
                  + id
                  + " spreading out, "
                  + nearbyAllies.length
                  + " nearby");
        }
        // Don't return - continue to try other actions after spreading
      }
    }

    // Populate cheese buffer early - needed for both starvation emergency and normal gathering
    findNearbyCheese(rc);

    // VALUE FUNCTION DRIVEN: ALL rats collect cheese - no gatherer percentage gatekeeping
    // The value function's STATE_WEIGHTS handle cheese vs attack priority:
    // - STATE_SURVIVE: cheese=150 (high priority when economy low)
    // - STATE_PRESSURE: cheese=100 (balanced)
    // - STATE_EXECUTE: cheese=50 (low priority, focus attack)

    // CRITICAL FIX: ALL rats collect cheese they can see!
    // Previously only 12% gatherers actively collected, causing king starvation.
    // Now ALL rats will move toward visible cheese within 5 tiles (25 distSq).
    // This matches ratbot6's approach where rats opportunistically collect.
    if (cheeseCount > 0 && !cachedCarryingCheese) {
      // First try to collect adjacent cheese (free action)
      if (cachedActionReady && tryCollectCheese(rc)) {
        cachedCarryingCheese = true;
        // Try to attack enemies while returning (don't waste combat opportunity)
        tryImmediateAction(rc, enemyBuffer, enemyCount);
        // Return to king immediately after collecting
        if (hasOurKingLoc) bug2MoveTo(rc, cachedOurKingLoc);
        return;
      }

      // ALL rats move toward visible cheese within 7 tiles!
      // Exception: Skip during all-in when very close to enemy king
      boolean skipForAllIn = cachedAllInActive && cachedEnemyKingLoc != null;
      if (skipForAllIn) {
        int edx = locX - cachedEnemyKingLoc.x;
        int edy = locY - cachedEnemyKingLoc.y;
        skipForAllIn = (edx * edx + edy * edy) <= 25; // Only skip if within 5 tiles of enemy king
      }

      if (!skipForAllIn) {
        // Find nearest cheese within 7 tiles
        MapLocation nearestCheese = null;
        int nearestDist = Integer.MAX_VALUE;
        for (int i = cheeseCount; --i >= 0; ) {
          int cdx = locX - cheeseBuffer[i].x;
          int cdy = locY - cheeseBuffer[i].y;
          int dist = cdx * cdx + cdy * cdy;
          if (dist < nearestDist) { // No distance limit - ALL rats collect visible cheese
            nearestDist = dist;
            nearestCheese = cheeseBuffer[i];
          }
        }

        if (nearestCheese != null) {
          // Squeak cheese location to share with teammates
          squeakCheeseLocation(rc, nearestCheese);
          // Try immediate actions while moving
          tryImmediateAction(rc, enemyBuffer, enemyCount);
          // Move toward cheese
          bug2MoveTo(rc, nearestCheese);
          return;
        }
      }
    }

    // ALL rats actively seek out DISTANT cheese (beyond 7 tiles) - no gatekeeping!
    // Universal block above handles cheese within 7 tiles for ALL rats
    // Find the nearest DISTANT cheese (beyond 7 tiles)
    MapLocation distantCheese = null;
    int nearestDistantDist = Integer.MAX_VALUE;
    if (cheeseCount > 0 && !cachedCarryingCheese) {
      for (int i = cheeseCount; --i >= 0; ) {
        int cdx = locX - cheeseBuffer[i].x;
        int cdy = locY - cheeseBuffer[i].y;
        int dist = cdx * cdx + cdy * cdy;
        if (dist > 49 && dist < nearestDistantDist) { // Beyond 7 tiles only
          nearestDistantDist = dist;
          distantCheese = cheeseBuffer[i];
        }
      }
    }
    if (distantCheese != null) {
      if (DEBUG && cachedRound % 10 == 0) {
        System.out.println(
            "[CHEESE DEBUG] R"
                + cachedRound
                + " ID:"
                + id
                + " sees distant cheese at "
                + distantCheese
                + " myLoc:"
                + myLoc
                + " actionReady:"
                + cachedActionReady
                + " starvation:"
                + cachedStarvationRounds);
      }

      // Squeak cheese location to share with teammates
      squeakCheeseLocation(rc, distantCheese);

      // Try to collect adjacent cheese (free action)
      if (cachedActionReady && tryCollectCheese(rc)) {
        cachedCarryingCheese = true;
        if (DEBUG) {
          System.out.println(
              "[CHEESE COLLECTED] R" + cachedRound + " ID:" + id + " picked up cheese!");
        }
        // If starvation is critical (< 20 rounds), return immediately
        // But first check if we're already at king and just need to turn to deliver
        if (cachedStarvationRounds < 20 && hasOurKingLoc) {
          if (cachedDistToKingSq <= DELIVERY_RANGE_SQ) {
            // Already at king! Try to deliver (handles turning)
            tryDeliverCheese(rc);
          } else {
            bug2MoveToUrgent(rc, cachedOurKingLoc);
          }
          return;
        }
      }

      // ALWAYS move toward visible cheese within 5 tiles - this is the key fix!
      // Rats were walking past cheese because detouring was conditional on thresholds.
      // Exception: Don't detour during all-in when very close to enemy king
      boolean skipForAttack = cachedAllInActive && cachedEnemyKingLoc != null;
      if (skipForAttack) {
        int edx = locX - cachedEnemyKingLoc.x;
        int edy = locY - cachedEnemyKingLoc.y;
        skipForAttack = (edx * edx + edy * edy) <= 25; // Only skip if within 5 tiles of enemy king
      }
      if (DEBUG && skipForAttack) {
        System.out.println(
            "[CHEESE SKIP] R" + cachedRound + " ID:" + id + " skipping cheese for all-in attack");
      }
      if (!skipForAttack) {
        if (DEBUG && cachedRound % 10 == 0) {
          System.out.println(
              "[CHEESE MOVE] R"
                  + cachedRound
                  + " ID:"
                  + id
                  + " moving toward distant cheese at "
                  + distantCheese);
        }
        // Move directly toward the distant cheese we found
        bug2MoveTo(rc, distantCheese);
        return;
      }
      // skipForAttack is true - prioritizing attack over distant cheese collection
    }

    // STARVATION EMERGENCY: Force ALL rats to become emergency gatherers when king is starving
    // This ensures rats COLLECT cheese before returning, not just return empty-handed!
    // CRITICAL: NO EXEMPTIONS during critical starvation - everyone helps or king dies!
    boolean amAssassin = isAssassin(id);
    // Remove assassin/core exemption during critical starvation (< 15 rounds)
    boolean exemptFromStarvation =
        (cachedStarvationRounds >= 15) && (role == ROLE_CORE || amAssassin);
    if (cachedStarvationRounds < STARVATION_WARNING_ROUNDS
        && !exemptFromStarvation
        && ourKing != null) {
      // CRITICAL FIX: Check king's vicinity FIRST - cheese often spawns near king!
      // Rats were exploring the whole map while cheese sat right next to the starving king
      if (!cachedCarryingCheese && cheeseCount == 0 && distToKing > KING_VICINITY_DIST_SQ) {
        // No cheese visible and far from king - GO TO KING FIRST to check for nearby cheese
        bug2MoveToUrgent(rc, ourKing);
        return;
      }
      // FIX: Collect cheese FIRST, then return to king
      // Without this, rats return empty-handed and king still starves!
      if (!cachedCarryingCheese && cheeseCount > 0) {
        // Try to collect nearby cheese
        if (tryCollectCheese(rc)) {
          // Successfully collected - now return to king
          bug2MoveToUrgent(rc, ourKing);
          return;
        }
        // Move toward nearest cheese (urgent mode for starvation)
        if (moveTowardNearestCheese(rc, enemyBuffer, enemyCount, true, -1, false)) {
          return;
        }
      }
      // FIX: NO CHEESE VISIBLE - Use shared knowledge and smart exploration
      if (!cachedCarryingCheese && cheeseCount == 0) {
        // Try to dig and attack while searching
        tryImmediateAction(rc, enemyBuffer, enemyCount);

        // Priority 1: Check shared cheese locations from other rats
        // ANTI-TRAFFIC JAM: Use ID-based distribution so rats spread across locations
        readSharedCheeseLocations(rc);
        MapLocation sharedCheese = findSharedCheeseForRat(id);
        if (sharedCheese != null) {
          int dx = locX - sharedCheese.x;
          int dy = locY - sharedCheese.y;
          int distToShared = dx * dx + dy * dy;
          if (distToShared > 4) {
            if (DEBUG && cachedRound % 10 == 0) {
              System.out.println(
                  "[STARVATION_SHARED] R"
                      + cachedRound
                      + " ID:"
                      + id
                      + " going to shared cheese at "
                      + sharedCheese);
            }
            bug2MoveToUrgent(rc, sharedCheese);
            return;
          } else {
            // At shared location but no cheese - clear it
            clearDepletedCheeseLocation(rc, sharedCheese);
          }
        }

        // Priority 2: Check last-known cheese memory
        if (lastCheeseSeenLoc != null
            && (cachedRound - lastCheeseSeenRound) < LAST_CHEESE_STALE_ROUNDS) {
          int dx = locX - lastCheeseSeenLoc.x;
          int dy = locY - lastCheeseSeenLoc.y;
          int distToLastCheese = dx * dx + dy * dy;
          if (distToLastCheese > 4) {
            // Go to last-known cheese location
            bug2MoveTo(rc, lastCheeseSeenLoc);
            return;
          } else {
            // We're at the last-known location but no cheese - clear memory
            lastCheeseSeenLoc = null;
          }
        }

        // Priority 3: Use exploreForCheese() which includes INTERIOR targets for corridors
        // CRITICAL FIX: getCheeseHuntTarget() uses sector-based hunting which fails on
        // corridor maps like pipes. exploreForCheese() includes interior targets (indices 8-15)
        // that cover corridor areas where cheese actually spawns!
        MapLocation exploreTarget =
            exploreForCheese(rc, id, true); // emergency mode = faster rotation
        if (exploreTarget != null) {
          if (DEBUG && cachedRound % 10 == 0) {
            System.out.println(
                "[STARVATION_EXPLORE] R"
                    + cachedRound
                    + " ID:"
                    + id
                    + " exploring for cheese at "
                    + exploreTarget
                    + " cheese:"
                    + cachedGlobalCheese);
          }
          bug2MoveToUrgent(rc, exploreTarget);
          return;
        }

        // Priority 4 removed: getCheeseHuntTarget() fallback was dead code
        // since exploreForCheese() always returns a non-null target.
        // The sector-based hunting was failing on corridor maps anyway.

        // Last resort fallback: return to king (should rarely reach here)
        if (ourKing != null) {
          bug2MoveToUrgent(rc, ourKing);
          return;
        }
      }
      // If carrying cheese, return to king IMMEDIATELY - this is critical for survival!
      if (cachedCarryingCheese) {
        // If close enough to deliver, try to deliver (handles turning)
        if (distToKing <= DELIVERY_RANGE_SQ) {
          if (tryDeliverCheese(rc)) {
            if (DEBUG) {
              System.out.println(
                  "[STARVATION_DELIVERY] R"
                      + cachedRound
                      + " ID:"
                      + id
                      + " delivered cheese to king!");
            }
            // Delivered! Now go find more cheese to help with starvation
            cachedCarryingCheese = false; // Update cache after delivery
            // Continue searching for more cheese - use exploreForCheese for corridor coverage
            MapLocation nextCheeseTarget = exploreForCheese(rc, id, true);
            if (nextCheeseTarget != null) {
              bug2MoveToUrgent(rc, nextCheeseTarget);
            }
            return; // Don't fall through to attack logic - stay in gathering mode
          } else {
            // Couldn't deliver yet (need to turn) - stay put and wait
            return;
          }
        } else {
          // CRITICAL: Always return to king when carrying cheese in starvation mode
          // Previous code only returned if distToKing > FORMATION_TIGHT_RADIUS_SQ
          // This caused rats to NOT return when they were "close but not close enough"
          if (DEBUG && cachedRound % 20 == 0) {
            System.out.println(
                "[STARVATION_RETURN] R"
                    + cachedRound
                    + " ID:"
                    + id
                    + " returning to king with cheese, dist:"
                    + distToKing);
          }
          bug2MoveToUrgent(rc, ourKing);
          return;
        }
      }
    }

    // Partial or full emergency - all rats return to king (except CORE who are already there)
    // Changed from == 2 to >= 1 so even 1 enemy near king triggers defense
    // EXCEPTION: Assassins NEVER return - they must reach enemy king
    if (cachedEmergencyLevel >= 1 && role != ROLE_CORE && !amAssassin && ourKing != null) {
      if (distToKing > FORMATION_TIGHT_RADIUS_SQ) {
        bug2MoveToUrgent(rc, ourKing);
        return;
      }
    }

    // Broadcast enemy king if we see it
    babyRatBroadcastEnemyKing(rc, enemyBuffer, enemyCount);

    // Read focus fire target ONLY when we have enemies (saves ~60 BC when no enemies)
    if (enemyCount > 0) {
      readFocusFireTarget(rc);
    }

    if (PROFILE) bcAfterScore = Clock.getBytecodeNum();

    // ALL-IN OVERRIDE: In all-in mode, all rats attack (except CORE defending)
    if (cachedAllInActive && role != ROLE_CORE) {
      int dx = locX - (enemyKing != null ? enemyKing.x : 0);
      int dy = locY - (enemyKing != null ? enemyKing.y : 0);
      int distToEnemyKing = (enemyKing != null) ? (dx * dx + dy * dy) : Integer.MAX_VALUE;
      runAssassinMode(rc, enemyBuffer, enemyCount, distToEnemyKing, myHP);
      return;
    }

    // RACE MODE: In RACE_DEFEND_MODE, non-assassin attackers return to defend king
    if (cachedRaceMode == RACE_DEFEND_MODE && role != ROLE_CORE && !amAssassin) {
      if (distToKing > 64) { // More than 8 tiles from king
        tryImmediateAction(rc, enemyBuffer, enemyCount);
        bug2MoveTo(rc, ourKing);
        return;
      }
    }

    // RACE MODE: In RACE_ATTACK_MODE, everyone (except CORE) rushes enemy king
    if (cachedRaceMode == RACE_ATTACK_MODE && role != ROLE_CORE) {
      if (enemyKing != null) {
        int distToEnemyKing =
            (locX - enemyKing.x) * (locX - enemyKing.x)
                + (locY - enemyKing.y) * (locY - enemyKing.y);

        // Attack enemy king if possible
        for (int i = enemyCount; --i >= 0; ) {
          RobotInfo enemy = enemyBuffer[i];
          if (enemy.getType() == UnitType.RAT_KING) {
            MapLocation kingLoc = enemy.getLocation();
            if (rc.canAttack(kingLoc)) {
              rc.attack(kingLoc);
              cachedDamageToEnemyKing += BASE_ATTACK_DAMAGE;
              cachedActionReady = false;
            }
            bug2MoveTo(rc, kingLoc);
            return;
          }
        }

        // Rush to enemy king location - use aggressive movement when close
        // Reuse distToEnemyKing calculated at start of this block
        if (distToEnemyKing <= 25) {
          moveToKingAggressive(rc, enemyKing);
        } else {
          bug2MoveTo(rc, enemyKing);
        }
        return;
      }
    }

    if (PROFILE) bcAfterAction = Clock.getBytecodeNum();

    // Role-based behavior
    switch (role) {
      case ROLE_CORE:
        runCoreRat(rc, enemyBuffer, enemyCount);
        break;
      case ROLE_SPECIALIST:
        runSpecialist(rc, enemyBuffer, enemyCount);
        break;
      case ROLE_FLEX:
      default:
        runFlexRat(rc, enemyBuffer, enemyCount);
        break;
    }

    // Post-movement cheese collection removed - now handled by universal block at start
    // of runBabyRat() which aggressively collects all visible cheese within 5 tiles.

    if (PROFILE) bcAfterMove = Clock.getBytecodeNum();
  }

  // ================================================================
  // SECTION 16: ROLE BEHAVIORS
  // ================================================================

  private static void runCoreRat(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    // Note: Only kings can write to shared array, so we skip presence reporting here

    // === BYTECODE OPTIMIZATION: Cache frequently used values ===
    final MapLocation kingLoc = cachedOurKingLoc;
    final int locX = myLocX;
    final int locY = myLocY;
    final int distToKing = cachedDistToKingSq;

    if (!hasOurKingLoc) {
      runFlexRat(rc, enemies, enemyCount);
      return;
    }

    // PRIORITY 0: ESCAPE CORRIDOR - Move out of king's way when cat is nearby!
    // This is CRITICAL on constrained maps like sittingducks where guardians trap the king
    RobotInfo[] allRobots = rc.senseNearbyRobots(-1);
    MapLocation catLoc = findDangerousCat(allRobots);
    if (catLoc != null) {
      // Calculate king's escape direction
      Direction kingEscapeDir = catLoc.directionTo(kingLoc);
      if (kingEscapeDir != Direction.CENTER) {
        // Am I blocking the king's escape path?
        Direction kingToMe = kingLoc.directionTo(myLoc);

        // If I'm in the direction the king wants to flee, MOVE OUT OF THE WAY
        boolean blockingEscape =
            (kingToMe == kingEscapeDir
                || kingToMe == kingEscapeDir.rotateLeft()
                || kingToMe == kingEscapeDir.rotateRight());

        if (blockingEscape && distToKing <= 8) { // Within ~3 tiles of king
          // Move perpendicular to king's escape direction
          Direction perpLeft = kingEscapeDir.rotateLeft().rotateLeft();
          Direction perpRight = kingEscapeDir.rotateRight().rotateRight();

          if (rc.canMove(perpLeft)) {
            rc.move(perpLeft);
            if (DEBUG) {
              System.out.println(
                  "[ESCAPE_CORRIDOR] R"
                      + cachedRound
                      + " guardian moving left to clear king's path");
            }
            return;
          } else if (rc.canMove(perpRight)) {
            rc.move(perpRight);
            if (DEBUG) {
              System.out.println(
                  "[ESCAPE_CORRIDOR] R"
                      + cachedRound
                      + " guardian moving right to clear king's path");
            }
            return;
          } else if (rc.canMove(kingEscapeDir)) {
            // Can't go perpendicular, move in same direction as king
            rc.move(kingEscapeDir);
            return;
          }
        }
      }
    }

    // Priority 1: If too far from king, rush back
    if (distToKing > GUARDIAN_OUTER_DIST_SQ) {
      tryImmediateAction(rc, enemies, enemyCount);
      if (cachedInEmergency) {
        bug2MoveToUrgent(rc, kingLoc); // Urgent in emergency
      } else {
        bug2MoveTo(rc, kingLoc);
      }
      return;
    }

    // Priority 1: Find and engage enemies threatening king
    RobotInfo bestThreat = null;
    int bestThreatScore = Integer.MIN_VALUE;
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (!enemy.getType().isBabyRatType()) continue;

      MapLocation enemyLoc = enemy.getLocation();
      int edx = enemyLoc.x - kingLoc.x;
      int edy = enemyLoc.y - kingLoc.y;
      int enemyDistToKing = edx * edx + edy * edy;

      // Score by threat level: closer to king = higher threat
      int score = 1000 - enemyDistToKing - enemy.getHealth();
      if (enemyDistToKing <= 25) score += 500; // Bonus for enemies very close to king

      if (score > bestThreatScore) {
        bestThreatScore = score;
        bestThreat = enemy;
      }
    }

    // Priority 2: Use defensive kiting against threats
    if (bestThreat != null) {
      // CRITICAL FIX: Only reset kite state when engaging a NEW target, not every turn!
      // Before: kiteState was reset every turn, so guardians NEVER retreated after attacking
      int threatId = bestThreat.getID();
      if (lastKiteTargetId != threatId) {
        kiteState = KITE_STATE_APPROACH;
        kiteRetreatCounter = 0;
        lastKiteTargetId = threatId;
      }
      MapLocation threatLoc = bestThreat.getLocation();
      int threatDx = threatLoc.x - kingLoc.x;
      int threatDy = threatLoc.y - kingLoc.y;
      int threatDistToKing = threatDx * threatDx + threatDy * threatDy;

      // Attack if in range
      if (rc.isActionReady() && rc.canAttack(threatLoc)) {
        rc.attack(threatLoc);
      }

      // Use defensive kiting for enemies close to king
      if (threatDistToKing <= GUARDIAN_ATTACK_RANGE_SQ * 2) {
        runDefensiveKiting(rc, bestThreat);
        return;
      } else {
        // Move to intercept
        bug2MoveTo(rc, threatLoc);
        return;
      }
    }

    // Priority 3: Body blocking if we have a blocking line
    if (cachedBlockingLineCenter != null && cachedInEmergency) {
      int distToLine =
          (myLocX - cachedBlockingLineCenter.x) * (myLocX - cachedBlockingLineCenter.x)
              + (myLocY - cachedBlockingLineCenter.y) * (myLocY - cachedBlockingLineCenter.y);
      if (distToLine > 4) {
        bug2MoveTo(rc, cachedBlockingLineCenter);
        return;
      }
    }

    // Priority 4: Maintain optimal guardian position
    // ANTI-CROWDING: Count nearby friendlies and spread out if too crowded
    int nearbyFriendlies = 0;
    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(CROWDING_CHECK_RADIUS_SQ, cachedOurTeam);
    nearbyFriendlies = nearbyAllies.length;

    if (nearbyFriendlies > CROWDING_THRESHOLD) {
      // Too crowded! Move away from densest cluster instead of orbiting
      int sumDx = 0, sumDy = 0;
      for (int i = nearbyAllies.length; --i >= 0; ) {
        MapLocation allyLoc = nearbyAllies[i].getLocation();
        sumDx += allyLoc.x - locX;
        sumDy += allyLoc.y - locY;
      }
      // Move away from centroid of nearby allies
      Direction awayFromCrowd = directionFromDelta(-sumDx, -sumDy);
      if (awayFromCrowd != Direction.CENTER && rc.canMove(awayFromCrowd)) {
        rc.move(awayFromCrowd);
        if (DEBUG && cachedRound % 20 == 0) {
          System.out.println(
              "[ANTI-CROWD] R"
                  + cachedRound
                  + " guardian spreading out, "
                  + nearbyFriendlies
                  + " nearby");
        }
        return;
      }
    }

    if (distToKing < GUARDIAN_INNER_DIST_SQ) {
      // Too close, move away
      Direction awayFromKing = kingLoc.directionTo(myLoc);
      if (awayFromKing != Direction.CENTER) {
        MapLocation patrolSpot =
            new MapLocation(
                locX + DIR_DX[awayFromKing.ordinal()], locY + DIR_DY[awayFromKing.ordinal()]);
        bug2MoveTo(rc, patrolSpot);
      }
    } else {
      // In good position - orbit king to cover angles
      // BUGFIX: Use ID-based orbit direction so different guardians orbit different ways
      // This prevents guardians from clustering and blocking each other
      int id = rc.getID();
      Direction toKing = myLoc.directionTo(kingLoc);
      int orbitOffset = (id + cachedRound) % 4; // 0-3
      Direction orbit;
      switch (orbitOffset) {
        case 0:
          orbit = toKing.rotateLeft();
          break;
        case 1:
          orbit = toKing.rotateRight();
          break;
        case 2:
          orbit = toKing.rotateLeft().rotateLeft();
          break;
        default:
          orbit = toKing.rotateRight().rotateRight();
          break;
      }
      // BUGFIX: Try multiple directions when blocked instead of doing nothing
      if (rc.canMove(orbit)) {
        rc.move(orbit);
      } else {
        // Try adjacent directions when primary orbit direction is blocked
        Direction left = orbit.rotateLeft();
        Direction right = orbit.rotateRight();
        if (rc.canMove(left)) {
          rc.move(left);
        } else if (rc.canMove(right)) {
          rc.move(right);
        } else {
          // All blocked - try moving away from king to spread out
          Direction away = toKing.opposite();
          if (rc.canMove(away)) {
            rc.move(away);
          }
        }
      }
    }

    // Try immediate actions while patrolling
    tryImmediateAction(rc, enemies, enemyCount);
  }

  /**
   * Baby rats detect enemy king and squeak position to share with team.
   *
   * <p>NOTE: Unlike cheese squeaks, enemy king position squeaks are NOT suppressed near our king
   * because knowing enemy king location is critical tactical information that outweighs the cat
   * attraction risk. Also, if we see the enemy king, we're likely already in combat anyway.
   */
  private static void babyRatBroadcastEnemyKing(
      RobotController rc, RobotInfo[] enemies, int enemyCount) throws GameActionException {
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation enemyLoc = enemy.getLocation();
        int hp = enemy.getHealth();

        // Update local cache
        cachedEnemyKingLoc = enemyLoc;
        cachedEnemyKingHP = hp;
        enemyKingConfirmed = true;

        // Squeak enemy king position to share with team (with throttling)
        // NOTE: Not suppressed near our king - enemy king info is too important!
        int id = rc.getID();
        if (cachedRound - lastSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
          // Format: type (4 bits) | y (12 bits) | x (12 bits) | hp/35 (4 bits)
          int hpBits = hp / 35;
          if (hpBits > 15) hpBits = 15;
          int squeak = (1 << 28) | (enemyLoc.y << 16) | (enemyLoc.x << 4) | hpBits;
          rc.squeak(squeak);
          lastSqueakRound = cachedRound;
          lastSqueakID = id;
        }
        return;
      }
    }
  }

  /**
   * FLEX rat behavior - VALUE FUNCTION DRIVEN.
   *
   * <p>Design pattern from RATBOT8_DESIGN.md:
   *
   * <ol>
   *   <li>scoreAllTargets() - Score cheese, enemies, delivery based on STATE_WEIGHTS
   *   <li>tryImmediateAction() - Attack/collect/deliver if adjacent
   *   <li>bug2MoveTo(cachedBestTarget) - Go to highest scoring target
   * </ol>
   *
   * <p>The STATE_WEIGHTS system handles cheese vs attack priority naturally:
   *
   * <ul>
   *   <li>STATE_SURVIVE: cheese=150 (high priority when economy is low)
   *   <li>STATE_PRESSURE: cheese=100 (balanced)
   *   <li>STATE_EXECUTE: cheese=50 (low priority, focus on attack)
   * </ul>
   *
   * <p>NO GATHERER PERCENTAGE GATEKEEPING - the value function handles everything.
   */
  private static void runFlexRat(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    // === BYTECODE OPTIMIZATION: Cache frequently used values ===
    final int locX = myLocX;
    final int locY = myLocY;
    final MapLocation enemyKing = cachedEnemyKingLoc;
    int myHP = rc.getHealth();

    // DECOY SQUEAK: FLEX rats in enemy territory should also lure cats
    // Especially effective during all-in or when attacking
    if (cachedAllInActive || cachedRaceMode == RACE_ATTACK_MODE || enemyCount == 0) {
      tryDecoySqueakInEnemyTerritory(rc);
    }

    // ================================================================
    // PRIORITY 1: DELIVER CHEESE IF CARRYING
    // ================================================================
    // Carrying cheese = return to king immediately (economy is life)
    // Only exception: ALL-IN mode when very close to enemy king
    if (cachedCarryingCheese && hasOurKingLoc) {
      // Try to deliver if in range
      if (cachedDistToKingSq <= DELIVERY_RANGE_SQ) {
        tryDeliverCheese(rc);
      }
      // Skip delivery return ONLY if all-in AND very close to enemy king
      boolean skipForAllIn = cachedAllInActive && enemyKing != null;
      if (skipForAllIn) {
        int edx = locX - enemyKing.x;
        int edy = locY - enemyKing.y;
        skipForAllIn = (edx * edx + edy * edy) <= 16; // Only skip if within 4 tiles
      }
      if (!skipForAllIn || cachedStarvationRounds < 30) {
        // Return to king to deliver
        tryImmediateAction(rc, enemies, enemyCount);
        bug2MoveTo(rc, cachedOurKingLoc);
        return;
      }
      // Fall through to attack logic only if all-in and very close to enemy king
    }

    // ================================================================
    // PRIORITY 2: ALL-IN / RACE MODE - EVERYONE ATTACKS
    // ================================================================
    // When all-in is active or race mode says attack, everyone rushes enemy king
    if ((cachedAllInActive || cachedRaceMode == RACE_ATTACK_MODE) && enemyKing != null) {
      int dx = locX - enemyKing.x;
      int dy = locY - enemyKing.y;
      int distToEnemyKing = dx * dx + dy * dy;
      runAssassinMode(rc, enemies, enemyCount, distToEnemyKing, myHP);
      return;
    }

    // ================================================================
    // PRIORITY 3: RACE DEFEND MODE - RETURN TO KING
    // ================================================================
    if (cachedRaceMode == RACE_DEFEND_MODE && hasOurKingLoc && cachedDistToKingSq > 36) {
      tryImmediateAction(rc, enemies, enemyCount);
      bug2MoveTo(rc, cachedOurKingLoc);
      return;
    }

    // ================================================================
    // PRIORITY 4: VALUE FUNCTION DRIVEN BEHAVIOR
    // ================================================================
    // This is the CORE of FLEX rat behavior - let the value function decide
    // what to prioritize based on game state weights.

    // Step 1: Score all targets (cheese, enemies, delivery)
    // The STATE_WEIGHTS automatically prioritize cheese when economy is low
    scoreAllTargets(rc, enemies, enemyCount);

    // Step 2: Kite if engaged with close enemies
    if (enemyCount > 0) {
      RobotInfo bestEnemyTarget = null;
      int minDist = Integer.MAX_VALUE;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        // Always prioritize enemy king
        if (enemy.getType() == UnitType.RAT_KING) {
          bestEnemyTarget = enemy;
          break;
        }
        MapLocation enemyLoc = enemy.getLocation();
        int dx = locX - enemyLoc.x;
        int dy = locY - enemyLoc.y;
        int distSq = dx * dx + dy * dy;
        if (distSq < minDist) {
          minDist = distSq;
          bestEnemyTarget = enemy;
        }
      }
      // Use offensive kiting when engaged with enemies
      if (bestEnemyTarget != null && minDist <= KITE_ENGAGE_DIST_SQ) {
        runOffensiveKiting(rc, bestEnemyTarget);
        return;
      }
    }

    // Step 3: Try immediate actions (attack, collect, deliver, dig)
    tryImmediateAction(rc, enemies, enemyCount);

    // Step 4: If we just collected cheese, return to king
    if (cachedCarryingCheese && hasOurKingLoc) {
      bug2MoveTo(rc, cachedOurKingLoc);
      return;
    }

    // Step 5: Move toward best target from value function
    if (cachedBestTarget != null) {
      bug2MoveTo(rc, cachedBestTarget);
      return;
    }

    // ================================================================
    // FALLBACK: EXPLORE FOR CHEESE
    // ================================================================
    // No target from value function - explore to find cheese/enemies
    MapLocation exploreTarget;
    if (shouldExploreForCheese) {
      // Low cheese mode: search sectors around our king where cheese spawns
      exploreTarget = getCheeseHuntTarget(rc);
    } else {
      // Normal mode: explore using shared cheese + spiral pattern
      exploreTarget = exploreForCheese(rc, rc.getID(), false);
    }

    if (exploreTarget != null) {
      bug2MoveTo(rc, exploreTarget);
    } else if (enemyKing != null) {
      // No explore target - go to enemy king
      bug2MoveTo(rc, enemyKing);
    } else if (hasOurKingLoc) {
      // Last resort - patrol near our king
      bug2MoveTo(rc, cachedOurKingLoc);
    }
  }

  private static void runSpecialist(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    int round = cachedRound;
    int id = rc.getID();
    int myHP = rc.getHealth();

    // Check if this specialist is an assassin (based on ID)
    boolean isAssassinRole = isAssassin(id);

    // Calculate distance to enemy king
    int distToEnemyKing = Integer.MAX_VALUE;
    if (cachedEnemyKingLoc != null) {
      int dx = myLocX - cachedEnemyKingLoc.x;
      int dy = myLocY - cachedEnemyKingLoc.y;
      distToEnemyKing = dx * dx + dy * dy;
    }

    // Phase transitions based on commitment level and game state:
    // - COMMITMENT_ALL_IN or ASSAULT: All specialists become assassins
    // - COMMITMENT_RAID: Specialists become raiders (harass economy)
    // - COMMITMENT_PROBE or DEFEND: Scouts gather intel
    // Attack windows can trigger early assassin mode
    boolean attackWindowActive =
        (cachedAttackWindow == WINDOW_WOUNDED_KING
            || cachedAttackWindow == WINDOW_POST_RUSH
            || cachedAttackWindow == WINDOW_ARMY_ADVANTAGE);

    // High commitment or kill conditions -> ASSASSIN mode
    if (isAssassinRole
        || currentAttackCommitment >= COMMITMENT_ASSAULT
        || round > 200
        || cachedEnemyKingHP < 200
        || cachedAllInActive
        || attackWindowActive) {
      // ASSASSIN mode - rush enemy king, bypass baby rats
      runAssassinMode(rc, enemies, enemyCount, distToEnemyKing, myHP);
      return;
    }
    // RAID commitment -> RAIDER mode (harass economy)
    else if (currentAttackCommitment == COMMITMENT_RAID
        || postRushPhase == POST_RUSH_ECONOMY_RAID) {
      runRaiderMode(rc, enemies, enemyCount);
      return;
    }
    // Mid-game with known enemy -> RAIDER mode
    else if (round > EARLY_GAME_ROUND || enemyKingConfirmed) {
      runRaiderMode(rc, enemies, enemyCount);
      return;
    }
    // Early game or PROBE/DEFEND commitment -> SCOUT mode
    else {
      runScoutMode(rc, enemies, enemyCount);
      return;
    }
  }

  /** ASSASSIN mode: Rush to enemy king, bypass baby rats, never retreat. */
  private static void runAssassinMode(
      RobotController rc, RobotInfo[] enemies, int enemyCount, int distToEnemyKing, int myHP)
      throws GameActionException {

    // DECOY SQUEAK: Lure cats toward enemy territory while attacking!
    // Assassins are deep in enemy territory - perfect for decoy squeaks
    tryDecoySqueakInEnemyTerritory(rc);

    // Priority 1: Attack enemy king if in range
    if (cachedEnemyKingLoc != null && distToEnemyKing <= 9 && cachedActionReady) {
      // Try to attack king center
      if (rc.canAttack(cachedEnemyKingLoc)) {
        rc.attack(cachedEnemyKingLoc);
        cachedDamageToEnemyKing += BASE_ATTACK_DAMAGE;
        cachedEnemyKingHP -= BASE_ATTACK_DAMAGE;
        cachedActionReady = false;
        return;
      }
      // Try all 9 tiles of the 3x3 king area
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          MapLocation tile = cachedEnemyKingLoc.translate(dx, dy);
          if (rc.canAttack(tile)) {
            rc.attack(tile);
            cachedDamageToEnemyKing += BASE_ATTACK_DAMAGE;
            cachedEnemyKingHP -= BASE_ATTACK_DAMAGE;
            cachedActionReady = false;
            return;
          }
        }
      }
    }

    // Priority 2: Bypass baby rats when close to enemy king
    int bypassDist = getKingBypassDistSq();
    if (distToEnemyKing <= bypassDist) {
      // Close to enemy king - push through, only fight blocking enemies
      RobotInfo blockingEnemy = null;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType() == UnitType.RAT_KING) continue;
        MapLocation enemyLoc = enemy.getLocation();
        int dx = myLocX - enemyLoc.x;
        int dy = myLocY - enemyLoc.y;
        if (dx * dx + dy * dy <= 2) {
          blockingEnemy = enemy;
          break;
        }
      }

      // Attack blocking enemy if present and very close to king
      if (blockingEnemy != null && distToEnemyKing <= KING_ATTACK_PRIORITY_DIST_SQ) {
        if (cachedActionReady && rc.canAttack(blockingEnemy.getLocation())) {
          rc.attack(blockingEnemy.getLocation());
          cachedActionReady = false;
        }
      }

      // Move toward enemy king
      if (cachedEnemyKingLoc != null) {
        bug2MoveTo(rc, cachedEnemyKingLoc);
      }
      return;
    }

    // Priority 3: Use offensive kiting against enemies we encounter
    RobotInfo bestTarget = null;
    int minDist = Integer.MAX_VALUE;
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        // Found enemy king - confirm HP and target
        confirmEnemyKingHP(rc, enemy.getHealth(), cachedRound);
        cachedEnemyKingLoc = enemy.getLocation();
        bestTarget = enemy;
        break;
      }
      MapLocation enemyLoc = enemy.getLocation();
      int dx = myLocX - enemyLoc.x;
      int dy = myLocY - enemyLoc.y;
      int dist = dx * dx + dy * dy;
      if (dist < minDist) {
        minDist = dist;
        bestTarget = enemy;
      }
    }

    // Kite enemies, but prefer pushing to king over retreating
    if (bestTarget != null && myHP >= HEALTHY_HP_THRESHOLD) {
      // Healthy assassin - attack and continue pushing
      if (cachedActionReady && rc.canAttack(bestTarget.getLocation())) {
        rc.attack(bestTarget.getLocation());
        cachedActionReady = false;
      }
    } else if (bestTarget != null) {
      runOffensiveKiting(rc, bestTarget);
      return;
    }

    // Priority 4: Move toward enemy king
    if (cachedEnemyKingLoc != null) {
      bug2MoveTo(rc, cachedEnemyKingLoc);
    } else {
      // No known enemy king location - explore
      runScoutMode(rc, enemies, enemyCount);
    }
  }

  /** RAIDER mode: Harass enemy economy, target collectors near cheese. */
  private static void runRaiderMode(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {

    // DECOY SQUEAK: Raiders patrol enemy territory - good candidates for cat luring
    tryDecoySqueakInEnemyTerritory(rc);

    // Priority 1: Target enemies carrying cheese (most valuable economy denial)
    RobotInfo cheeseCarrier = null;
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) continue;
      if (enemy.getRawCheeseAmount() > 0) {
        cheeseCarrier = enemy;
        break;
      }
    }

    if (cheeseCarrier != null) {
      // Attack cheese carrier if in range
      if (cachedActionReady && rc.canAttack(cheeseCarrier.getLocation())) {
        rc.attack(cheeseCarrier.getLocation());
        cachedActionReady = false;
        // Track kill if we finished them
        if (cheeseCarrier.getHealth() <= 10) {
          killsThisGame++;
        }
      }
      // Chase cheese carrier
      bug2MoveTo(rc, cheeseCarrier.getLocation());
      return;
    }

    // Priority 2: Attack enemies near cheese (likely collectors)
    RobotInfo collectorTarget = null;
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) continue;
      MapLocation enemyLoc = enemy.getLocation();

      // Check if enemy is near cheese (collector behavior)
      for (int j = cheeseCount; --j >= 0; ) {
        int cdx = cheeseBuffer[j].x - enemyLoc.x;
        int cdy = cheeseBuffer[j].y - enemyLoc.y;
        if (cdx * cdx + cdy * cdy <= 9) {
          collectorTarget = enemy;
          break;
        }
      }
      if (collectorTarget != null) break;
    }

    if (collectorTarget != null) {
      // Attack collector if in range
      if (cachedActionReady && rc.canAttack(collectorTarget.getLocation())) {
        rc.attack(collectorTarget.getLocation());
        cachedActionReady = false;
        if (collectorTarget.getHealth() <= 10) {
          killsThisGame++;
        }
      }
      // Chase collector
      bug2MoveTo(rc, collectorTarget.getLocation());
      return;
    }

    // Priority 3: Attack of opportunity (small groups only - don't overcommit)
    if (enemyCount > 0 && enemyCount <= 2) {
      // Find closest enemy
      RobotInfo closest = null;
      int minDist = Integer.MAX_VALUE;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType() == UnitType.RAT_KING) continue;
        MapLocation enemyLoc = enemy.getLocation();
        int dx = myLocX - enemyLoc.x;
        int dy = myLocY - enemyLoc.y;
        int dist = dx * dx + dy * dy;
        if (dist < minDist) {
          minDist = dist;
          closest = enemy;
        }
      }
      if (closest != null) {
        if (cachedActionReady && rc.canAttack(closest.getLocation())) {
          rc.attack(closest.getLocation());
          cachedActionReady = false;
          if (closest.getHealth() <= 10) {
            killsThisGame++;
          }
        }
        bug2MoveTo(rc, closest.getLocation());
        return;
      }
    }

    // Priority 4: Patrol between enemy king and cheese sources (economy routes)
    if (cachedEnemyKingLoc != null) {
      // Patrol midpoint between enemy king and map center
      int patrolX = (cachedEnemyKingLoc.x + cachedMapWidth / 2) / 2;
      int patrolY = (cachedEnemyKingLoc.y + cachedMapHeight / 2) / 2;
      MapLocation patrolPoint = new MapLocation(patrolX, patrolY);
      bug2MoveTo(rc, patrolPoint);
    } else {
      runScoutMode(rc, enemies, enemyCount);
    }
  }

  /** SCOUT mode: Explore to find enemy king. */
  private static void runScoutMode(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {

    // DECOY SQUEAK: Scouts explore enemy territory - squeak to lure cats
    tryDecoySqueakInEnemyTerritory(rc);

    // Priority 1: If we see enemy king, update cache and switch to assassin
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        cachedEnemyKingLoc = enemy.getLocation();
        confirmEnemyKingHP(rc, enemy.getHealth(), cachedRound);
        // Squeak enemy king position (handled in babyRatBroadcastEnemyKing)
        runAssassinMode(
            rc, enemies, enemyCount, myLoc.distanceSquaredTo(cachedEnemyKingLoc), rc.getHealth());
        return;
      }
    }

    // Priority 2: Attack enemies we encounter
    tryImmediateAction(rc, enemies, enemyCount);

    // Priority 3: Explore based on ID to spread scouts across map
    int id = rc.getID();
    int quadrant = id % 4;
    MapLocation exploreTarget;

    if (cachedEnemyKingLoc != null) {
      // Check if we're close to the estimated location but don't see the king
      int dx = myLocX - cachedEnemyKingLoc.x;
      int dy = myLocY - cachedEnemyKingLoc.y;
      int distToEstimate = dx * dx + dy * dy;

      if (distToEstimate <= 9 && enemyKingConfirmed) {
        // We're within 3 tiles of where we thought the king was, but we don't see it
        // The king must have moved! Force re-read of position from shared array next turn
        // NOTE: Don't clear enemyKingConfirmed globally - other rats may still know where king is
        // Instead, just force this rat to re-read the position
        lastKingPosReadRound = -100;
        if (DEBUG) {
          System.out.println(
              "[R8 SCOUT] R" + cachedRound + " king not at expected location, forcing re-read...");
        }
      }

      if (!enemyKingConfirmed || distToEstimate > 25) {
        // Have estimate but not confirmed, or far from estimate - go there first
        exploreTarget = cachedEnemyKingLoc;
      } else {
        // We're at the estimate and king is "confirmed" but not visible
        // This means we need to explore nearby - spiral out
        int offset = (cachedRound / 5) % 8; // Rotate every 5 rounds
        Direction exploreDir = DIRECTIONS[offset];
        exploreTarget = cachedEnemyKingLoc.translate(exploreDir.dx * 5, exploreDir.dy * 5);
      }
    } else {
      // No estimate at all - explore different quadrants based on ID
      int midX = cachedMapWidth / 2;
      int midY = cachedMapHeight / 2;
      switch (quadrant) {
        case 0:
          exploreTarget = new MapLocation(cachedMapWidth - 5, cachedMapHeight - 5);
          break;
        case 1:
          exploreTarget = new MapLocation(cachedMapWidth - 5, 5);
          break;
        case 2:
          exploreTarget = new MapLocation(5, cachedMapHeight - 5);
          break;
        default:
          exploreTarget = new MapLocation(midX, midY);
          break;
      }
    }

    bug2MoveTo(rc, exploreTarget);
  }

  // ================================================================
  // SECTION 17: UTILITIES
  // ================================================================

  /**
   * Try to scan for cheese by turning to look in different directions. Rats have 90-degree forward
   * vision, so they need to turn to see cheese behind them. This function rotates the rat 90
   * degrees each call until all 4 cardinal directions have been checked, then resets for the next
   * scan cycle.
   *
   * @param rc The RobotController
   * @return true if we turned (caller should return and check for cheese next round), false if scan
   *     cycle is complete or we can't turn
   * @throws GameActionException if turn fails
   */
  private static boolean tryScanForCheese(RobotController rc) throws GameActionException {
    // FIXED: Use ID + round to determine scan direction per-rat independently
    // This avoids the shared state issue where all rats shared scanDirectionIndex
    int id = rc.getID();

    // Each rat scans a different direction based on (id + round) % 8
    // Phases 0-3: scan by turning. Phases 4-7: explore instead of scanning in place
    int scanPhase = (id + cachedRound) % 8;

    // Only scan during phases 0-3 (first 4 of every 8 rounds for this rat)
    // REDUCED from 0-5: Scanning 75% of time was causing rats to never collect!
    // Now scan 50% of time - balanced between finding cheese and collecting it.
    if (scanPhase >= 4) {
      return false; // Don't scan this round - explore instead
    }

    // Calculate target direction: rotate 90 degrees * scanPhase from a base direction
    // Base direction offset by ID so different rats check different directions first
    int baseOrd = (id % 8);
    int targetOrd = (baseOrd + scanPhase * 2) & 7; // 2 ordinals = 90 degrees
    Direction targetDir = DIRECTIONS[targetOrd];

    // If already facing target, no need to turn
    if (cachedFacing == targetDir) {
      return false; // Already facing this direction, continue with normal behavior
    }

    // Try to turn toward target direction
    if (rc.canTurn()) {
      rc.turn(targetDir);
      cachedFacing = rc.getDirection();
      if (DEBUG && cachedRound % 10 == 0) {
        System.out.println(
            "[CHEESE SCAN] R"
                + cachedRound
                + " ID:"
                + id
                + " turned to "
                + cachedFacing
                + " phase:"
                + scanPhase);
      }
      return true; // Turned, caller should return and check for cheese next round
    }

    // Can't turn (on cooldown) - continue with current facing
    return false;
  }

  /**
   * Calculate the difference between the current round and a stored round value, handling 10-bit
   * wraparound correctly. Shared array values are limited to 0-1023, so round numbers wrap.
   *
   * <p>This function is small and called frequently (~4 times per turn). JIT should inline it.
   *
   * @param storedRound The round value read from shared array (already masked to 10 bits)
   * @return The number of rounds since storedRound (0-1023 range, handles wraparound)
   */
  private static int getMaskedRoundDiff(int storedRound) {
    int diff = (cachedRound & 1023) - storedRound;
    return diff < 0 ? diff + 1024 : diff;
  }

  /**
   * Check if the rat is in the enemy half of the map. Used for decoy squeak logic - we want to
   * squeak in enemy territory to lure cats there.
   *
   * @return true if we're in the enemy half (closer to enemy king's spawn than our king's spawn)
   */
  private static boolean isInEnemyHalf() {
    // Use rotational symmetry: enemy spawn is at (mapWidth-1-ourKingX, mapHeight-1-ourKingY)
    // We're in enemy half if we're past the midpoint toward their side on BOTH axes
    if (cachedOurKingLoc == null) {
      // Fallback: use map center - require both axes
      return myLocX > cachedMapWidth / 2 && myLocY > cachedMapHeight / 2;
    }
    // Calculate midpoint between our king and enemy king (estimated by symmetry)
    int midX = cachedMapWidth / 2;
    int midY = cachedMapHeight / 2;
    // Check if we're on enemy side of both axes (rotational symmetry)
    boolean pastMidX = (cachedOurKingLoc.x < midX) ? (myLocX > midX) : (myLocX < midX);
    boolean pastMidY = (cachedOurKingLoc.y < midY) ? (myLocY > midY) : (myLocY < midY);
    // In enemy half if past midpoint on BOTH axes (strict definition to avoid false positives)
    return pastMidX && pastMidY;
  }

  /**
   * Check if squeaks should be suppressed near our king to avoid attracting cats. Cats hear squeaks
   * from ~4 tiles away, so squeaking near our king could attract cats to our gatherers!
   *
   * @return true if we should NOT squeak (too close to our king)
   */
  private static boolean shouldSuppressSqueaksNearKing() {
    if (!hasOurKingLoc) return false; // No king location, can't check
    return cachedDistToKingSq <= SUPPRESS_SQUEAK_NEAR_KING_DIST_SQ;
  }

  /**
   * Try to emit a decoy squeak to lure cats toward enemy territory. This exploits the fact that
   * cats hear squeaks but don't decode them - they just investigate the sound source.
   *
   * <p>Conditions for decoy squeak:
   *
   * <ul>
   *   <li>Must be in enemy half of map
   *   <li>Must be far enough from our king (15+ tiles)
   *   <li>Should be reasonably close to enemy king (within 20 tiles) for maximum effect
   *   <li>Throttled to avoid excessive bytecode use
   * </ul>
   *
   * @param rc The RobotController
   * @return true if we squeaked, false otherwise
   * @throws GameActionException if squeak fails
   */
  private static boolean tryDecoySqueakInEnemyTerritory(RobotController rc)
      throws GameActionException {
    // Throttle decoy squeaks using ID-based pattern to avoid shared state issues
    // Each rat squeaks independently every DECOY_SQUEAK_THROTTLE rounds based on their ID
    int id = rc.getID();
    if ((cachedRound + id) % DECOY_SQUEAK_THROTTLE != 0) return false;

    // Must be in enemy half of map
    if (!isInEnemyHalf()) return false;

    // Must be far enough from our king (don't attract cats toward our side)
    if (hasOurKingLoc && cachedDistToKingSq < DECOY_SQUEAK_MIN_DIST_FROM_OUR_KING_SQ) return false;

    // Prefer squeaking when near enemy king for maximum cat harassment
    boolean nearEnemyKing = false;
    if (cachedEnemyKingLoc != null) {
      int dx = myLocX - cachedEnemyKingLoc.x;
      int dy = myLocY - cachedEnemyKingLoc.y;
      int distToEnemyKingSq = dx * dx + dy * dy;
      nearEnemyKing = distToEnemyKingSq <= DECOY_SQUEAK_MAX_DIST_FROM_ENEMY_KING_SQ;
    }

    // Only squeak if near enemy king OR in enemy half and far from ours
    // (but prioritize near enemy king for better effect)
    if (!nearEnemyKing && cachedRound % 2 != 0) {
      // When not near enemy king, only squeak every other round to reduce spam
      return false;
    }

    // Emit decoy squeak - use type 2 (cheese location) with our current position
    // Cats don't decode the content, they just hear the noise and investigate
    // Format: type(4 bits) | y(12 bits) | x(12 bits) | reserved(4 bits)
    int squeak = (2 << 28) | (myLocY << 16) | (myLocX << 4);
    rc.squeak(squeak);

    if (DEBUG && cachedRound % 20 == 0) {
      System.out.println(
          "[DECOY_SQUEAK] R"
              + cachedRound
              + " ID:"
              + id
              + " at ("
              + myLocX
              + ","
              + myLocY
              + ") nearEnemyKing:"
              + nearEnemyKing);
    }
    return true;
  }

  /**
   * Explore for cheese using shared knowledge and spiral pattern. Used by both starvation emergency
   * block and gatherer exploration to reduce code duplication.
   *
   * <p>This function checks shared cheese locations first, then falls back to smart exploration
   * with a spiral pattern around pre-computed explore targets.
   *
   * @param rc The RobotController
   * @param ratId The rat's ID (used for exploration target selection and spiral offset)
   * @param emergencyMode If true, use faster rotation (8 rounds) for starvation emergencies
   * @return A MapLocation to navigate toward, or null if no exploration target found
   * @throws GameActionException if shared array read fails
   */
  private static MapLocation exploreForCheese(RobotController rc, int ratId, boolean emergencyMode)
      throws GameActionException {
    // Priority 1: Check shared cheese locations from other rats
    readSharedCheeseLocations(rc);
    MapLocation sharedCheese = findClosestSharedCheese();
    if (sharedCheese != null) {
      int dx = myLocX - sharedCheese.x;
      int dy = myLocY - sharedCheese.y;
      int distToShared = dx * dx + dy * dy;
      if (distToShared <= 4) {
        // At shared location but no cheese visible - it's depleted, clear it
        if (cheeseCount == 0) {
          clearDepletedCheeseLocation(rc, sharedCheese);
        }
        // Fall through to exploration
      } else {
        // Go to shared cheese location
        return sharedCheese;
      }
    }

    // Priority 2: POSITION-AWARE exploration - prioritize targets on OUR side of the map
    // This helps rats find cheese closer to our king rather than wandering into enemy territory
    // Uses 16 targets (8 edge + 8 interior) for FULL map coverage
    // Rotation offset ensures rats don't all go to the same target
    int rotationPeriod = emergencyMode ? 8 : 12; // Faster rotation for quicker coverage
    int rotationOffset = (cachedRound / rotationPeriod) % 16;

    // Determine which side of the map we're on (fallback to rat's position if king location
    // unknown)
    int mapMidX = cachedMapWidth / 2;
    boolean weAreLeft;
    if (cachedOurKingLoc != null) {
      weAreLeft = cachedOurKingLoc.x < mapMidX;
    } else {
      // Fallback: use rat's own position when king location is unknown
      weAreLeft = myLocX < mapMidX;
    }

    // Find best target with position-aware scoring
    MapLocation exploreTarget = null;
    int bestScore = Integer.MAX_VALUE;
    for (int i = 0; i < 16; i++) {
      int targetIdx =
          (i + ratId + rotationOffset) % 16; // Different rats check different targets first
      MapLocation target = EXPLORE_TARGETS[targetIdx];
      int dx = myLocX - target.x;
      int dy = myLocY - target.y;
      int dist = dx * dx + dy * dy;

      // Base score is distance (lower = better)
      int score = dist;

      // POSITION-AWARE BONUS: Prefer targets on our side of the map
      // This helps find cheese near our king rather than enemy territory
      boolean targetOnOurSide = weAreLeft ? (target.x < mapMidX) : (target.x >= mapMidX);
      if (targetOnOurSide) {
        score -= EXPLORE_OWN_SIDE_BONUS; // Reduce score (better) for targets on our side
      }

      // INTERIOR BONUS: Prefer interior targets (indices 8-15) over edge targets
      // Interior targets cover corridors where cheese often spawns
      if (targetIdx >= 8) {
        score -= EXPLORE_INTERIOR_BONUS; // Reduce score (better) for interior targets
      }

      if (score < bestScore) {
        bestScore = score;
        exploreTarget = target;
      }
    }
    int quadrant = 0; // For spiral tracking, use target index
    for (int i = 0; i < 16; i++) {
      if (EXPLORE_TARGETS[i] == exploreTarget) {
        quadrant = i;
        break;
      }
    }

    // LOCAL SPIRAL: If near explore target but no cheese, spiral outward to check nearby area
    int dxToTarget = myLocX - exploreTarget.x;
    int dyToTarget = myLocY - exploreTarget.y;
    int distToTargetSq = dxToTarget * dxToTarget + dyToTarget * dyToTarget;
    if (distToTargetSq <= 16) { // Within 4 tiles of target
      // Time-based spiral: track when we first arrived and progress through steps over time
      if (spiralTargetQuadrant != quadrant) {
        // New target - reset arrival time
        spiralTargetQuadrant = quadrant;
        spiralArrivalRound = cachedRound;
      }
      int roundsAtTarget = cachedRound - spiralArrivalRound;
      int spiralStep = roundsAtTarget / SPIRAL_ROUNDS_PER_STEP;
      MapLocation spiralWaypoint = getSpiralWaypoint(exploreTarget, ratId, spiralStep);
      if (spiralWaypoint != null) {
        exploreTarget = spiralWaypoint;
      }
      // If spiralWaypoint is null, we've completed the spiral - rotation period will move us
    }

    return exploreTarget;
  }

  /**
   * PORTED FROM RATBOT7: Get a target location for hunting cheese during starvation. CRITICAL:
   * Unlike getExplorationTarget() which goes toward ENEMY, this function returns positions AROUND
   * our king where cheese spawns. Rats are spread in different directions based on their ID.
   *
   * <p>This function always returns a non-null MapLocation after computing the target. The result
   * is cached for 10 rounds or until the rat reaches its target (within 3 tiles), then a new sector
   * is assigned.
   *
   * @param rc The RobotController
   * @return A MapLocation around our king to search for cheese (never null)
   * @throws GameActionException if any game action fails
   */
  private static MapLocation getCheeseHuntTarget(RobotController rc) throws GameActionException {
    // FIX #1: Reduced cache from 20 to 10 rounds for faster sector reassignment
    // FIX #3: Also invalidate if we're near our target (sector searched, try another)
    boolean nearTarget =
        cachedCheeseHuntTarget != null && myLoc.distanceSquaredTo(cachedCheeseHuntTarget) <= 9;
    boolean cacheExpired = (cachedRound - cachedCheeseHuntRound) >= 10;
    boolean cacheValid =
        cachedCheeseHuntTarget != null
            && cachedCheeseHuntKingLoc == cachedOurKingLoc
            && !cacheExpired
            && !nearTarget; // FIX #3: Reassign if we reached our target

    if (cacheValid) {
      return cachedCheeseHuntTarget;
    }

    int id = rc.getID();
    // FIX #3: If cache expired or we reached target, try next sector
    // FIX: Only reassign if we've had an initial assignment (cachedCheeseHuntGroup >= 0)
    int group;
    if ((nearTarget || cacheExpired) && cachedCheeseHuntGroup >= 0) {
      group = (cachedCheeseHuntGroup + 1) % 8; // Rotate to next sector
    } else {
      group = id % 8; // Initial assignment based on ID
    }

    // FIX #2: Dynamic search radius based on map size (larger maps need wider search)
    int searchRadius = Math.max(12, cachedMapWidth / 3);

    int targetX, targetY;

    if (cachedOurKingLoc != null) {
      int kingX = cachedOurKingLoc.x;
      int kingY = cachedOurKingLoc.y;

      // BYTECODE OPT: Use DIR_DX/DIR_DY arrays for direction calculation
      // group maps to: 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW
      // This matches our DIRECTIONS array order exactly!
      int dx = DIR_DX[group] * searchRadius;
      int dy = DIR_DY[group] * searchRadius;
      targetX = kingX + dx;
      targetY = kingY + dy;

      // BYTECODE OPT: Use ternary for bounds clamping instead of Math.max/min
      targetX = targetX < 0 ? 0 : (targetX >= cachedMapWidth ? cachedMapWidth - 1 : targetX);
      targetY = targetY < 0 ? 0 : (targetY >= cachedMapHeight ? cachedMapHeight - 1 : targetY);
    } else {
      // Fallback: search center of map
      targetX = cachedMapWidth / 2;
      targetY = cachedMapHeight / 2;
    }
    cachedCheeseHuntTarget = new MapLocation(targetX, targetY);
    cachedCheeseHuntKingLoc = cachedOurKingLoc;
    cachedCheeseHuntRound = cachedRound;
    cachedCheeseHuntGroup = group; // Track assigned sector for reassignment

    if (DEBUG && cachedRound % 20 == 0) {
      System.out.println(
          "[CHEESE_HUNT] R"
              + cachedRound
              + " ID:"
              + rc.getID()
              + " group:"
              + group
              + " target:"
              + cachedCheeseHuntTarget
              + " kingLoc:"
              + cachedOurKingLoc);
    }

    return cachedCheeseHuntTarget;
  }

  /**
   * Get a spiral exploration waypoint around a center point. Used when a rat reaches its explore
   * target but doesn't find cheese - spirals outward to check the nearby area before moving to next
   * target.
   *
   * <p>The spiral pattern goes: N, NE, E, SE, S, SW, W, NW at radius 4 tiles.
   *
   * @param center The center point to spiral around (typically the explore target)
   * @param ratId The rat's ID (used to offset spiral direction so rats spread out)
   * @param spiralStep Time-based step (0-7), calculated as (cachedRound - arrivalRound) /
   *     SPIRAL_ROUNDS_PER_STEP
   * @return A MapLocation offset from center in the spiral pattern, clamped to map bounds, or null
   *     if spiral is complete (step >= SPIRAL_MAX_STEPS)
   */
  private static MapLocation getSpiralWaypoint(MapLocation center, int ratId, int spiralStep) {
    // Use spiralStep directly (time-based, tracked per-rat via spiralArrivalRound)
    int step = spiralStep;
    if (step >= SPIRAL_MAX_STEPS) {
      return null; // Signal to move to next target
    }

    // Offset direction by rat ID so different rats explore different directions first
    int dirIndex = (step + (ratId % 8)) % 8;
    Direction dir = DIRECTIONS[dirIndex];

    // Fixed radius of 4 tiles for spiral exploration
    int radius = 4;

    // Calculate waypoint
    int wx = center.x + dir.dx * radius;
    int wy = center.y + dir.dy * radius;

    // Clamp to map bounds (with margin)
    wx = Math.max(2, Math.min(cachedMapWidth - 3, wx));
    wy = Math.max(2, Math.min(cachedMapHeight - 3, wy));

    return new MapLocation(wx, wy);
  }

  // Note: shouldContinueSpiral() was removed - spiral state is now deterministic based on
  // roundsAtTarget parameter passed to getSpiralWaypoint(), avoiding shared mutable state issues.

  /** Record bytecode stats for this turn and log periodically. */
  private static void recordBytecodeStats(RobotController rc) {
    int used = bcTurnEnd - bcTurnStart;
    totalTurns++;
    totalBytecode += used;
    if (used > maxBytecode) maxBytecode = used;
    if (used < minBytecode) minBytecode = used;

    // Accumulate section stats
    int initCost = bcAfterInit - bcTurnStart;
    int senseCost = bcAfterSense - bcAfterInit;
    int scoreCost = bcAfterScore - bcAfterSense;
    int actionCost = bcAfterAction - bcAfterScore;
    int moveCost = bcAfterMove - bcAfterAction;
    int otherCost = bcTurnEnd - bcAfterMove;

    bcTotalInit += Math.max(0, initCost);
    bcTotalSense += Math.max(0, senseCost);
    bcTotalScore += Math.max(0, scoreCost);
    bcTotalAction += Math.max(0, actionCost);
    bcTotalMove += Math.max(0, moveCost);
    bcTotalOther += Math.max(0, otherCost);

    // Log every PROFILE_INTERVAL rounds
    int round = rc.getRoundNum();
    if (round % PROFILE_INTERVAL == 0) {
      String type = rc.getType().isRatKingType() ? "KING" : "RAT";
      int limit = rc.getType().isRatKingType() ? 20000 : 17500;
      int avg = totalTurns > 0 ? (int) (totalBytecode / totalTurns) : 0;
      int pct = (avg * 100) / limit;

      System.out.println(
          "[PROFILE "
              + type
              + "] R"
              + round
              + " used:"
              + used
              + "/"
              + limit
              + " ("
              + (used * 100 / limit)
              + "%)"
              + " avg:"
              + avg
              + " ("
              + pct
              + "%)"
              + " min:"
              + minBytecode
              + " max:"
              + maxBytecode);

      // Log section breakdown
      if (totalTurns > 0) {
        System.out.println(
            "[PROFILE "
                + type
                + "] Sections: "
                + "init:"
                + (bcTotalInit / totalTurns)
                + " "
                + "sense:"
                + (bcTotalSense / totalTurns)
                + " "
                + "score:"
                + (bcTotalScore / totalTurns)
                + " "
                + "action:"
                + (bcTotalAction / totalTurns)
                + " "
                + "move:"
                + (bcTotalMove / totalTurns)
                + " "
                + "other:"
                + (bcTotalOther / totalTurns));
      }
    }
  }

  private static Direction directionFromDelta(int dx, int dy) {
    if (dx > 0) {
      if (dy > 0) return Direction.NORTHEAST;
      if (dy < 0) return Direction.SOUTHEAST;
      return Direction.EAST;
    } else if (dx < 0) {
      if (dy > 0) return Direction.NORTHWEST;
      if (dy < 0) return Direction.SOUTHWEST;
      return Direction.WEST;
    } else {
      if (dy > 0) return Direction.NORTH;
      if (dy < 0) return Direction.SOUTH;
      return Direction.CENTER;
    }
  }
}
