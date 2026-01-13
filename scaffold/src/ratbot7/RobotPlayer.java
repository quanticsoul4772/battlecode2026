package ratbot7;

import static ratbot7.Debug7.*;

import battlecode.common.*;
import java.util.Random;

/**
 * Ratbot7 - PURE DEFENSIVE Strategy
 *
 * <h2>Philosophy</h2>
 *
 * DEFENSE FIRST. Protect the king. Keep cheese flowing. Never starve. "Survive at all costs →
 * Counter-strike when safe"
 *
 * <h2>Core Defensive Principles</h2>
 *
 * <ul>
 *   <li>King stays in safe zone, protected by traps and blockers
 *   <li>Gatherers stay CLOSE to king (tight economy leash)
 *   <li>Cheese delivery is ALWAYS top priority when carrying
 *   <li>All rats converge to defend king when threatened
 *   <li>Body blocking forms shield wall between enemies and king
 * </ul>
 *
 * <h2>Three Phases</h2>
 *
 * <ul>
 *   <li>Phase 1 (BUILD): Place traps, spawn rats, build defenses
 *   <li>Phase 2 (DEFENSE): All rats converge when enemies approach
 *   <li>Phase 3 (ATTACK): Only after defenses are solid, counter-attack
 * </ul>
 *
 * <h2>Key Defensive Features</h2>
 *
 * <ul>
 *   <li>Directional trap placement toward enemy
 *   <li>Sentry ring with 60% facing enemy direction
 *   <li>Body blocking shield wall formation
 *   <li>Tight economy leash (5-12 tiles max from king)
 *   <li>Forced cheese delivery when near king
 *   <li>Wounded rats retreat through trap corridors
 * </ul>
 */
public class RobotPlayer {

  // ========================================================================
  // SHARED ARRAY SLOT CONSTANTS
  // ========================================================================
  private static final int SLOT_OUR_KING_X = 0;
  private static final int SLOT_OUR_KING_Y = 1;
  private static final int SLOT_ENEMY_KING_X = 2;
  private static final int SLOT_ENEMY_KING_Y = 3;
  private static final int SLOT_ENEMY_KING_HP = 4;
  private static final int SLOT_SPAWN_COUNT = 5;
  private static final int SLOT_FOCUS_TARGET = 6;
  private static final int SLOT_FOCUS_HP = 7;
  private static final int SLOT_PHASE = 8; // 1=buildup, 2=defense, 3=attack
  private static final int SLOT_ENEMY_KING_ROUND = 9;
  private static final int SLOT_THREAT_LEVEL = 10; // Number of enemies near king
  private static final int SLOT_CAT_X = 11; // Cat location X (for cat baiting)
  private static final int SLOT_ECONOMY_RECOVERY = 19; // Economy recovery mode flag (0 or 1)
  private static final int SLOT_KING_HP = 20; // King HP scaled down (HP / 10)
  private static final int SLOT_CAT_Y = 12; // Cat location Y (for cat baiting)
  private static final int SLOT_CAT_ROUND = 13; // Round when cat was last seen near king
  private static final int SLOT_STARVATION_PREDICT =
      21; // Predicted rounds until starvation (999=safe)
  private static final int SLOT_BLOCK_LINE_X = 14; // Blocking line center X
  private static final int SLOT_BLOCK_LINE_Y = 15; // Blocking line center Y
  private static final int SLOT_BLOCK_PERP = 16; // Perpendicular direction ordinal for line spread

  // ========================================================================
  // SQUEAK CONSTANTS
  // ========================================================================
  private static final int SQUEAK_TYPE_ENEMY_KING = 1;
  private static final int SQUEAK_TYPE_THREAT = 2;
  private static final int SQUEAK_TYPE_CAT_BAIT = 3; // Squeak to lure cat away from king
  private static final int SQUEAK_TYPE_ENEMY_SPOTTED = 4; // Early warning - enemies seen!
  private static final int SQUEAK_THROTTLE_ROUNDS = 10;
  private static final int THREAT_SQUEAK_THROTTLE = 5; // Throttle threat squeaks
  private static final int CAT_BAIT_THROTTLE_ROUNDS = 5; // Throttle cat baiting squeaks
  private static final int MAX_SQUEAKS_TO_READ = 4; // BYTECODE OPT: Reduced from 8 to 4
  private static final int MAX_SQUEAK_THREAT = 10; // Cap on squeak-derived threat level
  private static final int SQUEAK_THREAT_DECAY_ROUNDS =
      3; // Decay squeak threat after this many rounds

  // ========================================================================
  // PHASE CONSTANTS
  // ========================================================================
  private static final int PHASE_BUILDUP = 1;
  private static final int PHASE_DEFENSE = 2;
  private static final int PHASE_ATTACK = 3;

  // ========================================================================
  // PHASE TRANSITION THRESHOLDS
  // ========================================================================
  // Attack phase round is now calculated dynamically based on map size
  // Small maps (30x30): ~50 rounds, Large maps (60x60): ~85 rounds
  private static final int BASE_ATTACK_ROUND =
      50; // Minimum attack round (increased for defensive buildup)
  private static final int ATTACK_PHASE_SPAWN_COUNT = 15; // Or when 15 rats spawned (faster!)
  private static final int DEFENSE_TRIGGER_ENEMIES = 2; // Switch to defense when 2+ enemies near
  // PARTIAL EMERGENCY: Only nearby rats respond to low threat (1-2 enemies)
  // FULL EMERGENCY: All rats defend when 3+ enemies near king
  private static final int PARTIAL_EMERGENCY_THRESHOLD = 1; // Low threat - partial response
  private static final int FULL_EMERGENCY_THRESHOLD = 3; // High threat - all rats defend
  private static final int PARTIAL_EMERGENCY_RADIUS_SQ = 64; // Only rats within 8 tiles respond
  private static final int EMERGENCY_DEFENSE_THRESHOLD = 1; // Legacy alias for vision management

  // DEFENSE-READY CHECK: Minimum defenses before attack phase
  private static final int MIN_TRAPS_FOR_ATTACK = 2; // Need at least 2 traps (reduced from 4)
  private static final int MIN_TRAPS_BEFORE_SPAWN =
      0; // Spawn immediately for early scouting (was 1 - now spawn round 1-2)
  private static final int DEFENSE_TIMEOUT_ROUND =
      20; // Spawn anyway after this round (reduced for faster army)
  private static final int CHEESE_HOARD_FAILSAFE_THRESHOLD =
      1500; // Force spawning if hoarding this much cheese with no army
  private static final int TRAP_RING_FAILURE_THRESHOLD =
      5; // Switch to LINE layout after this many failures (was 10 - faster adaptation)

  // ========================================================================
  // ROLE CONSTANTS (percentage-based)
  // ========================================================================
  private static final int GATHERER_THRESHOLD =
      55; // 55% gatherers (reduced to make room for guardians/interceptors)
  private static final int SENTRY_THRESHOLD = 70; // 15% sentries (55-70)
  private static final int GUARDIAN_THRESHOLD = 85; // 15% guardians (70-85) - inner defense ring
  private static final int INTERCEPTOR_THRESHOLD = 95; // 10% interceptors (85-95) - mid-map patrol
  // Remaining 5% are scouts (95-100)

  // ========================================================================
  // VALUE FUNCTION CONSTANTS
  // ========================================================================
  private static final int CHEESE_VALUE_NORMAL = 100; // HIGH - economy focus!
  private static final int CHEESE_VALUE_ATTACK_PHASE = 30; // Lower in attack phase
  private static final int ENEMY_RAT_VALUE = 60; // Defend against intruders
  private static final int ENEMY_KING_VALUE = 250; // Only in attack phase
  private static final int DELIVERY_PRIORITY = 150;

  // ========================================================================
  // DISTANCE CONSTANTS
  // ========================================================================
  private static final int KING_SAFE_ZONE_RADIUS_SQ = 36; // 6 tiles - stay close!
  private static final int SENTRY_PATROL_RADIUS_SQ = 64; // 8 tiles from king
  private static final int HOME_TERRITORY_RADIUS_SQ = 144; // 12 tiles from king
  private static final int DELIVERY_RANGE_SQ = 9;
  private static final int EMERGENCY_DELIVERY_DIST_SQ = 25; // Within 5 tiles = force delivery
  private static final int DISTANCE_WEIGHT_INT = 15;

  // ========================================================================
  // TRAP CONSTANTS - HEAVY DEFENSE
  // ========================================================================
  private static final int RAT_TRAP_TARGET = 15; // More traps than ratbot6!
  private static final int MAX_TRAP_ATTEMPTS_PER_TURN =
      3; // BYTECODE OPT: Limit canPlaceRatTrap calls (reduced from 6)
  private static final int TRAP_GIVE_UP_THRESHOLD =
      10; // BYTECODE OPT: Stop trying after persistent failures (was 15 - save bytecode earlier)
  private static final int CAT_TRAP_TARGET = 8;
  private static final int TRAP_WINDOW_END = 50; // Place traps early
  // STRATEGIC DIRT CONSTANTS
  private static final int DIRT_STOCKPILE_MAX = 12; // Max dirt to acquire
  private static final int DIRT_STOCKPILE_MIN = 4; // Min dirt before placing
  private static final int FUNNEL_WALL_TARGET = 4; // 2 walls per side of trap line
  private static final int FLANK_WALL_TARGET = 6; // L-shaped close protection
  private static final int REACTIVE_WALL_MAX = 4; // Max reactive walls
  private static final int FUNNEL_OFFSET = 2; // Tiles from center for funnel walls
  private static final int FLANK_DIST = 2; // Distance for flank protection
  private static final int DIRT_DIG_WINDOW_END = 35; // Stop digging after this round
  private static final int REACTIVE_THREAT_THRESHOLD = 3; // Enemies from unexpected dir to trigger
  private static final int SMALL_MAP_THRESHOLD = 30; // BYTECODE OPT: Skip walls on small maps

  // ========================================================================
  // SPAWN CONSTANTS
  // ========================================================================
  private static final int SPAWN_CHEESE_RESERVE = 50; // Lower reserve = more spawning (was 80)
  private static final int ATTACK_SPAWN_RESERVE = 100; // Higher reserve in attack phase (was 150)
  private static final int MIN_KING_CHEESE_AFTER_SPAWN =
      100; // CRITICAL: Don't spawn if king cheese falls below this (increased from 30)

  // ========================================================================
  // ECONOMY ANTI-COLLAPSE CONSTANTS - Prevent starvation death
  // ========================================================================
  // FIX #1 CRITICAL: Cheese reserve - NEVER let cheese drop below this
  private static final int ABSOLUTE_CHEESE_RESERVE =
      300; // Never spawn if cheese would drop below this  // FIX #3 HIGH: Spawn cap - limit spawns
  // when cheese is low
  // FIX #6 DOCUMENTATION: Both thresholds use 500 intentionally.
  // At exactly 500 cheese, neither emergency recall nor spawn cap triggers (both use "< 500").
  // This provides a consistent "safe" threshold - below 500 = emergency measures active.
  private static final int SPAWN_CAP_CHEESE_THRESHOLD = 500; // Apply spawn cap when cheese < this
  private static final int SPAWN_CAP_MAX_SPAWNS = 20; // Max spawns when cheese is low (was 25)
  // Note: LATE_GAME_SPAWN_CAP (15) applies after round 400 for even tighter control
  // FIX #4 HIGH: Emergency recall - force gatherers home when cheese critical
  // FIX #6 NOTE: Both SPAWN_CAP and EMERGENCY_RECALL use "< 500", so at exactly 500, neither
  // triggers.
  // This is intentional - 500 is the "safe" threshold, below it triggers emergency measures.
  private static final int EMERGENCY_RECALL_CHEESE =
      500; // Force all gatherers home when cheese < this
  // FIX #2: Mass emergency threshold - when cheese=0 AND kingHP < this, ALL roles become gatherers
  private static final int MASS_EMERGENCY_KING_HP = 200;

  // ========================================================================
  // PREDICTIVE STARVATION CONSTANTS
  // ========================================================================
  // King consumes 3 cheese per round - if unfed, loses 10 HP/round
  private static final int KING_CONSUMPTION_PER_ROUND = 3;
  // Trigger early warning when predicted starvation is within this many rounds
  private static final int STARVATION_WARNING_ROUNDS = 30;
  // Trigger critical warning when predicted starvation is within this many rounds
  private static final int STARVATION_CRITICAL_ROUNDS = 15;
  // Minimum cheese income rate to consider "safe" (cheese/round averaged over 10 rounds)
  private static final int SAFE_CHEESE_INCOME_RATE = 5; // Need 3 for king + buffer
  // FIX #5 MEDIUM: ATK phase economy - don't let gatherers explore far when cheese is low
  private static final int ATK_PHASE_EXPLORE_CHEESE =
      1500; // Don't explore in ATK phase if cheese < this (was 1000)
  // FIX #2 CRITICAL: Home guard - always keep some gatherers near king
  private static final int HOME_GUARD_COUNT = 3; // Number of gatherers to keep near king ALWAYS
  private static final int HOME_GUARD_RADIUS_SQ = 25; // 5 tiles - home guard must stay within this
  private static final int ECONOMY_RECOVERY_THRESHOLD =
      800; // Pause spawning until cheese > this when recovering (increased to trigger earlier)
  private static final int ECONOMY_RECOVERY_EXIT =
      1000; // Exit recovery mode when cheese > this (hysteresis: enter<800, exit>1000)
  private static final int ECONOMY_RECOVERY_EXIT_HP =
      450; // Exit HP-triggered recovery when kingHP > this
  private static final int KING_CHEESE_SURVIVAL_RESERVE =
      150; // Always reserve this much cheese for king feeding during combat

  // ========================================================================
  // SURVIVAL IMPROVEMENTS - Push survival past 250 rounds
  // ========================================================================
  // #1: Economy recovery fix - trigger on low threat OR critical cheese
  private static final int ECONOMY_RECOVERY_LOW_THREAT =
      3; // Recovery can trigger at threat <= 3 (during sustained combat)
  private static final int CHEESE_CRITICAL_THRESHOLD =
      200; // Immediate recovery when cheese < 200 (trigger earlier)
  private static final int KING_HP_CRITICAL = 200; // Tighten gather radius when kingHP < this
  private static final int CRITICAL_GATHER_RADIUS_SQ = 100; // 10 tiles when king is critical

  // #2: Cheese crisis mode - emergency gathering when king is dying
  private static final int CHEESE_CRISIS_KING_HP = 200; // Crisis when kingHP < 200
  private static final int CHEESE_CRISIS_CHEESE = 50; // Crisis when cheese < 50
  private static final int CHEESE_CRISIS_GATHER_RADIUS_SQ = 100; // 10 tiles during crisis

  // #3: Combat patrol radius - gatherers stay closer during combat
  private static final int COMBAT_PATROL_RADIUS_SQ = 225; // 15 tiles when threat >= 2

  // #4: HP-based spawn throttling
  private static final int SPAWN_PAUSE_KING_HP = 150; // Pause spawning when kingHP < 150
  private static final int SPAWN_THROTTLE_KING_HP = 300; // Slow spawning when kingHP < 300

  // #5: Wounded rat retreat
  private static final int WOUNDED_HP_THRESHOLD = 40; // Retreat when HP < 40
  private static final int WOUNDED_RETREAT_RADIUS_SQ = 25; // Retreat to within 5 tiles of king

  // #6: Adaptive formation tightening
  private static final int TIGHT_FORMATION_CHEESE_THRESHOLD = 100; // Tighten when cheese < 100
  private static final int TIGHT_FORMATION_RADIUS_SQ = 100; // 10 tiles during cheese crisis

  // #7: Delivery priority - closest gatherer delivers first
  private static final int DELIVERY_PRIORITY_BOOST = 200; // Score boost for close delivery

  // #8: Earlier trap placement - faster layout switching
  private static final int FAST_LAYOUT_SWITCH_FAILURES =
      3; // Switch layout after 3 failures (was 5 - even faster adaptation)
  // Emergency trap threshold - place close traps when under attack
  private static final int EMERGENCY_TRAP_THREAT_THRESHOLD = 2; // Trigger emergency trap mode

  // ========================================================================
  // CAT CONSTANTS
  // ========================================================================
  private static final int CAT_DANGER_RADIUS_SQ = 100;
  private static final int CAT_POUNCE_RANGE_SQ = 9;
  private static final int CAT_FLEE_RADIUS_SQ = 18; // Baby rats flee if cat within ~4.2 tiles
  private static final int CAT_BAIT_TRIGGER_DIST_SQ =
      64; // Cat within 8 tiles of king triggers baiting
  private static final int CAT_BAIT_MIN_DIST_FROM_KING_SQ =
      36; // Bait rat must be 6+ tiles from king
  private static final int CAT_BAIT_OPPOSITE_ANGLE = 90; // Must be ~opposite direction from cat
  // FIX: Add CAT_CAUTION_RADIUS_SQ for proactive avoidance (like ratbot6)
  // King should start moving away even before cat is in immediate danger range
  private static final int CAT_CAUTION_RADIUS_SQ = 169; // 13 tiles - proactive avoidance

  // ========================================================================
  // RATNAPPING CONSTANTS
  // ========================================================================
  private static final int RATNAP_RANGE_SQ = 2; // Adjacent only
  private static final int MAX_HOLD_ROUNDS = 5; // Drop rat if held too long without throwing

  // ========================================================================
  // DEFENSIVE CONSTANTS
  // ========================================================================
  private static final int RETREAT_HP_THRESHOLD = 30; // HP below which rats retreat to king
  private static final int ALLY_RESCUE_HP_THRESHOLD = 25; // HP below which we rescue allies
  private static final int KITE_TRIGGER_HP = 50; // HP below which we kite through traps

  // ========================================================================
  // BODY BLOCKING CONSTANTS
  // ========================================================================
  private static final int BODY_BLOCK_TRIGGER_DIST_SQ = 225; // 15 tiles - EARLIER activation!
  private static final int BODY_BLOCK_MIN_ENEMIES =
      1; // Minimum enemies to trigger blocking (was 2)
  private static final int BODY_BLOCK_SLOTS = 7; // Number of positions in blocking line (-3 to +3)
  // FIX 1: Tighter block line - max 4 tiles from king (was 8)
  // This ensures blockers can actually reach positions before being overwhelmed
  private static final int BODY_BLOCK_MAX_DIST = 4; // CAP: Never more than 4 tiles from king
  // FIX 7: Only nearby rats can become blockers - don't pull rats from far away
  private static final int BODY_BLOCK_MAX_RAT_DIST_SQ = 36; // Rats must be within 6 tiles to block
  // Note: Shared array stores coordinates + 1 to allow (0,0) as valid position
  // Value of 0 in array means "not set", value of 1 means coordinate 0, etc.

  // ========================================================================
  // FORMATION TIGHTENING CONSTANTS
  // ========================================================================
  private static final int FORMATION_TIGHT_RADIUS_SQ =
      20; // ~4.5 tiles - balanced formation (middle ground between 16 and 25)
  private static final int COUNTER_RUSH_ROUND = 30; // If enemies before this round = rush detected

  // ========================================================================
  // PROACTIVE SHIELD WALL CONSTANTS
  // ========================================================================
  // FIX 8: Position defenders TOWARD enemy from round 1, don't wait for threat
  private static final int PROACTIVE_SHIELD_PERCENT = 40; // 40% of rats form proactive shield
  private static final int PROACTIVE_SHIELD_DIST = 4; // Shield wall 4 tiles toward enemy

  // FIX 12: Directional sentry distribution - bias toward enemy
  private static final int SENTRY_ENEMY_BIAS_PERCENT = 60; // 60% of sentries face enemy direction

  // ========================================================================
  // FIX 2: STANDING DEFENSE RESERVE CONSTANTS
  // ========================================================================
  // Keep a reserve of rats always near king - they never explore far
  // This provides instant defenders when threats arrive
  private static final int STANDING_DEFENSE_COUNT = 10; // Number of rats to keep near king
  // FIX 5: Use 36 (6 tiles) instead of 25 (5 tiles) to prevent oscillation at boundary
  // This is slightly larger than FORMATION_TIGHT_RADIUS_SQ to avoid edge cases
  private static final int STANDING_DEFENSE_RADIUS_SQ = 36; // Within 6 tiles of king
  private static final int SLOT_DEFENDER_COUNT = 18; // Shared array slot for defender count

  // ========================================================================
  // FIX 3: FORCED DEFENSE PHASE CONSTANTS
  // ========================================================================
  // Force DEF phase when many enemies are close to king
  private static final int FORCE_DEF_ENEMY_COUNT = 5; // 5+ enemies triggers forced DEF
  private static final int FORCE_DEF_ENEMY_DIST_SQ = 100; // Within 10 tiles of king

  // ========================================================================
  // FIX 5: COUNTER-ATTACK SWARM MODE CONSTANTS
  // ========================================================================
  // When outnumbered, focus all damage on single targets for quick kills
  private static final int SWARM_MODE_ENEMY_THRESHOLD = 3; // Activate swarm when 3+ enemies
  private static final int SWARM_FOCUS_BONUS = 300; // Extra priority for swarm target

  // ========================================================================
  // KING ANCHORING CONSTANTS
  // ========================================================================
  private static final int KING_ANCHOR_THREAT_THRESHOLD = 3; // Anchor when 3+ enemies near
  private static final int KING_ANCHOR_DIST_SQ = 16; // Stay within 4 tiles of spawn when anchored
  private static final int KING_EMERGENCY_EVADE_DIST_SQ =
      4; // Evade if enemy within 2 tiles even when anchored

  // ========================================================================
  // GUARDIAN CONSTANTS - Inner defense ring that NEVER leaves king
  // ========================================================================
  // FIX: Balanced for coverage - not too tight (crowding) or too loose (slow intercepts)
  private static final int GUARDIAN_INNER_DIST_SQ = 5; // Stay within ~2.2 tiles of king
  private static final int GUARDIAN_OUTER_DIST_SQ =
      13; // Max ~3.6 tiles from king (spread for multi-angle intercepts)
  private static final int GUARDIAN_ATTACK_RANGE_SQ =
      9; // Attack enemies within 3 tiles of king (inner defense perimeter)

  // ========================================================================
  // INTERCEPTOR CONSTANTS - Mid-map patrol to slow rushes
  // ========================================================================
  private static final int INTERCEPTOR_PATROL_DIST = 15; // Patrol 15 tiles toward enemy
  private static final int INTERCEPTOR_ENGAGE_DIST_SQ = 64; // Engage enemies within 8 tiles
  private static final int INTERCEPTOR_RETURN_THREAT = 4; // Return to defend when threat >= 4

  // ========================================================================
  // DEFENSIVE KITING CONSTANTS - Retreat after attacking for better trades
  // ========================================================================
  private static final int KITE_STATE_APPROACH = 0;
  private static final int KITE_STATE_ATTACK = 1;
  private static final int KITE_STATE_RETREAT = 2;
  private static final int KITE_RETREAT_DIST_HEALTHY = 1; // HP >= 60: retreat 1 tile
  private static final int KITE_RETREAT_DIST_WOUNDED = 2; // HP < 60: retreat 2 tiles
  private static final int KITE_HP_HEALTHY_THRESHOLD = 60;
  private static final int KITE_ENGAGE_DIST_SQ = 8; // Start kiting when this close

  // ========================================================================
  // DEFENDER PURSUIT CONSTANTS - Chase enemies bypassing toward king
  // ========================================================================
  private static final int PURSUIT_BYPASS_DIST_SQ = 16; // Enemy bypassing if > 4 tiles from me
  private static final int PURSUIT_KING_APPROACH_DIST_SQ = 36; // But < 6 tiles from king

  // Adaptive constants - set based on map size in initializeRobot()
  private static int sentryRingDistSq = 36; // Default 6 tiles, smaller on small maps
  private static int sentryRingInnerSq = 25; // Default 5 tiles
  private static int sentryRingDist =
      6; // Cached sqrt of sentryRingDistSq (BYTECODE: avoid Math.sqrt per turn)
  private static int trapWindowEnd = 30; // Default, faster on small maps (was 50)
  private static int dirtWallWindowEnd = 25; // Default (was 40)
  private static int bodyBlockLineDist = 5; // Distance from king to blocking line, adaptive
  private static boolean rushDetected = false; // True if enemies seen before COUNTER_RUSH_ROUND
  private static boolean isSmallMap = false; // FIX 15: Track if map is small for exploration limits
  private static int mapMaxDimension = 60; // FIX 15: Cached max(width, height) for distance limits
  private static int consecutiveRingFailures = 0; // Track ring trap placement failures
  private static int consecutiveLineFailures = 0; // Track LINE layout failures for fallback
  private static boolean useLineLayout = false; // Fallback to LINE layout when ring fails
  private static boolean useFallbackLayout = false; // Final fallback: try ANY valid position
  private static boolean trapPlacementStalled = false; // Fix 2: Track if trap placement is failing
  private static int lastThreatRound = -100; // For threat hysteresis
  private static int lastSqueakThreatRound = -100; // Track when we last received threat squeaks
  private static int cachedSqueakThreat = 0; // Cached squeak threat for decay logic
  private static final int THREAT_HYSTERESIS_ROUNDS =
      3; // Maintain threat for at least this many rounds

  // Fix 5: Track rounds since last threat for safe exploration
  private static int roundsSinceThreat = 0;
  // FIX 10: Reduced from 5 to 3 - faster economic recovery after threat clears
  private static final int SAFE_EXPLORE_THRESHOLD =
      3; // Must be 0 threat for 3 rounds before exploring (was 5, was 20)
  private static final int SLOT_SAFE_ROUNDS = 17; // Shared array slot for safe rounds count

  // FIX 10: Critical cheese threshold - when economy is dying, OVERRIDE all lockouts!
  // Gatherers MUST collect cheese when king is starving, regardless of safety
  // FIX 15: Raised from 100 to 200 for earlier detection - gives gatherers time to return
  // FIX 16: Raised from 200 to 500 for even earlier detection in late game
  private static final int CRITICAL_CHEESE_THRESHOLD = 500; // Emergency economy mode

  // LATE-GAME ECONOMY MODE - after round 400, map cheese is often depleted
  // Keep gatherers much closer to king to ensure timely delivery
  private static final int LATE_GAME_ROUND = 400;
  private static final int LATE_GAME_MAX_DIST_SQ = 100; // 10 tiles max in late game
  private static final int LATE_GAME_SPAWN_CAP = 15; // Reduced spawn cap after round 400

  // ZERO INCOME STARVATION - when cheese income is 0 for N rounds, predict starvation
  private static final int ZERO_INCOME_ROUNDS_THRESHOLD =
      5; // After 5 rounds of 0 income  // STARVATION THRESHOLDS - unified constants for patrol
  // death spiral fix
  // These thresholds control when gatherers switch from patrol to explore mode
  // Using consistent thresholds prevents confusing behavior at boundary values
  private static final int STARVATION_THRESHOLD =
      200; // Explore away from king AND increase scan radius when cheese < this
  private static final int STARVATION_EXPLORE_EXIT =
      500; // Stop exploring when cheese > this (hysteresis)
  private static final int STARVATION_LEASH_REMOVAL =
      150; // Remove distance leash when cheese < this (tighter than explore)

  // DEFENSIVE ECONOMY LEASH - prevent gatherers from exploring too far
  // When cheese is low, gatherers must stay close enough to return in time
  // TIGHTENED for better defense: gatherers stay closer to king at all times
  private static final int ECON_LEASH_CRITICAL_DIST = 5; // Max dist when cheese < 200
  private static final int ECON_LEASH_NORMAL_DIST = 12; // Max dist when cheese >= 200
  private static final int SMALL_MAP_MAX_EXPLORE_DIST = 6; // Cap for small maps (was 10)
  // BYTECODE OPT: Pre-computed squared distances for economy leash comparisons
  private static final int ECON_LEASH_CRITICAL_DIST_SQ = 25; // 5*5
  private static final int ECON_LEASH_NORMAL_DIST_SQ = 144; // 12*12
  private static final int SMALL_MAP_MAX_EXPLORE_DIST_SQ = 36; // 6*6
  private static final int SLOT_KING_CHEESE = 19; // Broadcast king cheese for baby rats

  // FIX 10: Tighter gatherer lockout radius - only lock rats RIGHT NEXT to king
  // 144 (12 tiles) was way too large - locked EVERYONE in late game
  private static final int GATHERER_LOCKOUT_RADIUS_SQ = 25; // 5 tiles (was HOME_TERRITORY = 144)

  // ========================================================================
  // TARGET TYPE CONSTANTS
  // ========================================================================
  private static final int TARGET_NONE = 0;
  private static final int TARGET_ENEMY_KING = 1;
  private static final int TARGET_ENEMY_RAT = 2;
  private static final int TARGET_CHEESE = 3;
  private static final int TARGET_DELIVERY = 4;
  private static final int TARGET_PATROL = 5;
  private static final int TARGET_EXPLORE = 6;

  // ========================================================================
  // DIRECTION ARRAY
  // ========================================================================
  private static final Direction[] DIRECTIONS = {
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST
  };

  // ========================================================================
  // STATIC FIELDS - Cached Game State
  // ========================================================================
  private static int cachedRound;
  private static boolean cachedCarryingCheese;
  private static MapLocation cachedOurKingLoc;
  private static MapLocation cachedEnemyKingLoc;
  private static int cachedEnemyKingHP;
  private static int cachedSpawnCount;
  private static int cachedPhase;
  private static int cachedThreatLevel;
  private static Team cachedOurTeam;
  private static Team cachedEnemyTeam;
  private static boolean enemyKingConfirmed;

  // Current location
  private static MapLocation myLoc;
  private static int myLocX;
  private static int myLocY;

  // Cached per-turn state (bytecode optimization)
  private static Direction cachedFacing;
  private static boolean cachedActionReady;
  private static boolean cachedMovementReady;
  private static int cachedDistToKingSq = -1; // -1 = not calculated
  private static Direction cachedToEnemyDir; // BYTECODE OPT: Cached direction to enemy king

  // BYTECODE OPT: Cached map dimensions (set once in initializeRobot)
  private static int cachedMapWidth;
  private static int cachedMapHeight;

  // BYTECODE OPT: Pre-computed role hashes for baby rats (computed once per turn)
  private static int cachedRolePercent; // id % 100
  private static int cachedShieldHash; // (id >> 4) % 100
  private static int cachedDirSlot; // (id >> 2) % 10
  private static int cachedSlotMod5; // id % 5
  private static int cachedBlockSlot; // (id >> 4) % 10

  // BYTECODE OPT: Cached shared array values (read once per turn in runBabyRat)
  private static int cachedFocusTargetId;
  private static int cachedSafeRounds;
  private static int cachedDefenderCount;
  private static int cachedKingCheese;
  private static int cachedKingHP = 500; // Track king HP for survival improvements
  private static int cachedGlobalCheese; // FIX #1: Cache global cheese to avoid duplicate API calls
  private static boolean cachedIsHomeGuard; // FIX #4: Cache home guard status (ID never changes)

  // Target scoring results
  private static MapLocation cachedBestTarget;
  private static int cachedBestTargetType;
  private static int cachedBestScore;

  // Bug2 pathfinding
  private static MapLocation bug2Target;
  private static boolean bug2WallFollowing = false;
  private static Direction bug2WallDir;
  private static MapLocation bug2StartLoc;
  private static int bug2StartDist;

  // Cached exploration target (bytecode optimization - avoid MapLocation allocation per call)
  private static MapLocation cachedExplorationTarget;
  private static MapLocation cachedExplorationKingLoc; // Invalidate cache when king moves

  // King state
  private static MapLocation kingSpawnPoint;
  private static int ratTrapCount = 0;
  private static int trapAttemptsThisTurn = 0;
  private static int trapPlacementIndex =
      0; // BYTECODE OPT: Resume position for amortized placement
  private static int catTrapCount = 0;
  private static int dirtWallCount = 0;
  private static int funnelWallCount = 0;
  private static boolean economyRecoveryMode =
      false; // ECONOMY RECOVERY: Pause spawning to rebuild cheese
  // Track what triggered recovery mode for proper exit conditions
  // 0 = not in recovery, 1 = low threat, 2 = cheese critical, 3 = sustained combat, 4 = king
  // pressure
  private static int recoveryTriggerReason = 0;
  private static int recoveryStartRound = 0; // Track when recovery started for timeout fallback

  // Predictive starvation tracking (King only)
  private static int lastRoundCheese = 0; // Cheese at start of last round
  private static int cheeseChangeSum = 0; // Sum of cheese changes over tracking window
  private static int cheeseTrackingRounds = 0; // Number of rounds tracked
  private static final int STARVATION_SAFE = 999; // Magic number for "no starvation predicted"
  private static int predictedStarvationRounds =
      STARVATION_SAFE; // Predicted rounds until cheese = 0
  private static boolean starvationWarningActive = false; // True when starvation predicted soon
  private static boolean starvationCriticalActive =
      false; // True when starvation imminent  // Note: wasExploringForCheese removed - static
  // variables are shared across all robots in Battlecode.
  // Instead, we use round-based hysteresis: track when we ENTERED explore mode and stay for N
  // rounds.

  // Baby rat kiting state (per robot)
  private static int kiteState = KITE_STATE_APPROACH;
  private static int lastKiteTargetId = -1;
  private static int kiteRetreatTurns = 0;
  private static int flankWallCount = 0;
  private static int reactiveWallCount = 0;
  private static Direction lastThreatDirection = null;
  private static int spawnCount = 0;
  private static MapLocation estimatedEnemyKingLoc;
  private static int attackPhaseRound = 100; // Calculated based on map size

  // Pre-allocated arrays for bytecode optimization
  private static final Direction[] SPAWN_PRIORITY = new Direction[8];

  // Cheese mine locations (cached)
  private static final MapLocation[] cheeseMineBuffer = new MapLocation[20];
  private static int cheeseMineCount = 0;
  private static int lastMineScanRound = -100;

  // Cat tracking
  private static MapLocation lastKnownCatLoc = null;
  private static int lastCatSeenRound = -100;

  // Cat baiting state
  private static int lastCatBaitRound = -100;

  // RNG
  private static Random rng;

  // Cheese buffer
  private static final MapLocation[] cheeseBuffer = new MapLocation[50];
  private static int cheeseCount = 0;

  // BYTECODE OPTIMIZATION: Pre-allocated arrays for robot filtering
  // Avoids creating new arrays every turn (major bytecode savings)
  private static final RobotInfo[] enemyBuffer = new RobotInfo[100];
  private static final RobotInfo[] allyBuffer = new RobotInfo[100];

  // Map size thresholds for adaptive constants
  // Values BELOW these thresholds are considered small/medium maps
  private static final int SMALL_MAP_DIAGONAL_MAX = 50;
  private static final int MEDIUM_MAP_DIAGONAL_MAX = 70;

  // Squeak state
  private static int lastSqueakRound = -100;
  private static int lastSqueakID = -1;

  // ========================================================================
  // BYTECODE OPTIMIZATION HELPERS
  // ========================================================================

  /**
   * Integer square root - avoids expensive Math.sqrt() and cast overhead. Uses Newton's method for
   * fast approximation. Duplicated from Debug7 for bytecode efficiency (avoid static method call
   * overhead).
   */
  private static int intSqrt(int n) {
    if (n <= 0) return 0;
    if (n == 1) return 1;
    int x = n;
    int y = (x + 1) >> 1;
    while (y < x) {
      x = y;
      y = (x + n / x) >> 1;
    }
    return x;
  }

  // Ratnapping state (per baby rat)
  private static int holdingRatSinceRound = -1; // Round when we started holding a rat

  // Initialization
  private static boolean initialized = false;
  private static boolean cachedIsCooperation = false;
  private static boolean hasOurKingLoc = false; // BYTECODE: cache null check

  // BYTECODE OPT: Pre-cached diagonal directions for each enemy direction (8 possible)
  private static final Direction[][] DIAG_DIRS_BY_ENEMY = new Direction[8][4];
  // BYTECODE OPT: Pre-cached perpendicular directions for rotations
  private static final Direction[] PERP_LEFT_BY_DIR = new Direction[8];
  private static final Direction[] PERP_RIGHT_BY_DIR = new Direction[8];

  // BYTECODE OPT: Cache adjacent rat traps to reduce API calls in canMoveSafely()
  // Indexed by Direction.ordinal() - true if that adjacent tile has a rat trap
  private static final boolean[] ADJACENT_RAT_TRAP = new boolean[8];
  // Lookup table: DX_DY_TO_DIR_ORDINAL[dx+1][dy+1] = direction ordinal (-1 for center)
  private static final int[][] DX_DY_TO_DIR_ORDINAL = {
    {5, 6, 7}, // dx=-1: SW(5), W(6), NW(7)
    {4, -1, 0}, // dx=0: S(4), CENTER(-1), N(0)
    {3, 2, 1} // dx=1: SE(3), E(2), NE(1)
  };

  // FIX: Static arrays for hot path methods (bytecode optimization - avoid array creation per turn)
  private static final Direction[] SENTRY_BACK_DIRS = new Direction[5]; // For scoreAllTargetsSentry
  private static final Direction[] TRAP_PRIORITY_DIRS = new Direction[8]; // For placeRatTraps

  // BYTECODE OPT: Pre-cached trap direction arrays for all 8 enemy directions
  // Avoids recomputing rotations every call - lookup by toEnemy.ordinal()
  private static final Direction[][] TRAP_DIRS_BY_ENEMY = new Direction[8][8];

  // ========================================================================
  // ENTRY POINT
  // ========================================================================

  @SuppressWarnings("unused")
  public static void run(RobotController rc) throws GameActionException {
    rng = new Random(rc.getID());

    while (true) {
      try {
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
      } catch (GameActionException e) {
        // BYTECODE OPTIMIZATION: Avoid string concatenation
        e.printStackTrace();
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ========================================================================
  // INITIALIZATION
  // ========================================================================

  private static void initializeRobot(RobotController rc) throws GameActionException {
    cachedOurTeam = rc.getTeam();
    cachedEnemyTeam = cachedOurTeam.opponent();
    cachedIsCooperation = rc.isCooperation();

    // Calculate attack phase round based on map size
    // Smaller maps = earlier attack (more aggressive)
    int mapWidth = rc.getMapWidth();
    int mapHeight = rc.getMapHeight();
    int mapDiagonal = (int) Math.sqrt(mapWidth * mapWidth + mapHeight * mapHeight);

    // MAP-SIZE ADAPTIVE CONSTANTS
    // Small maps need faster defenses and tighter formations
    if (mapDiagonal < SMALL_MAP_DIAGONAL_MAX) {
      // Small map (30x30, diag ~42)
      sentryRingDistSq = 16; // 4 tiles - tighter ring
      sentryRingInnerSq = 9; // 3 tiles
      sentryRingDist = 4; // BYTECODE: cached sqrt
      trapWindowEnd = 15; // Place traps VERY FAST (was 20)
      dirtWallWindowEnd = 12;
      attackPhaseRound = 40; // Attack earlier
      bodyBlockLineDist = 3; // Closer blocking line on small maps
      isSmallMap = true; // FIX 15: Mark as small map for exploration limits
    } else if (mapDiagonal < MEDIUM_MAP_DIAGONAL_MAX) {
      // Medium map
      sentryRingDistSq = 25; // 5 tiles
      sentryRingInnerSq = 16; // 4 tiles
      sentryRingDist = 5; // BYTECODE: cached sqrt
      trapWindowEnd = 25; // Faster trap placement (was 35)
      dirtWallWindowEnd = 20;
      attackPhaseRound = BASE_ATTACK_ROUND + mapDiagonal / 3;
      bodyBlockLineDist = 4; // Medium distance
      isSmallMap = false; // FIX 15: Reset for medium maps
    } else {
      // Large map (60x60, diag ~85)
      sentryRingDistSq = 36; // 6 tiles
      sentryRingInnerSq = 25; // 5 tiles
      sentryRingDist = 6; // BYTECODE: cached sqrt
      trapWindowEnd = 30; // Faster trap placement (was 50)
      dirtWallWindowEnd = 25;
      attackPhaseRound = BASE_ATTACK_ROUND + mapDiagonal / 2;
      // FIX 1: Reduced from 6 to 4 - block line must be tight even on large maps
      bodyBlockLineDist = 4; // Max distance on big maps (capped by BODY_BLOCK_MAX_DIST)
      isSmallMap = false; // FIX 15: Reset for large maps
    }

    // FIX 15: Cache map size for exploration distance limits
    mapMaxDimension = Math.max(mapWidth, mapHeight);

    // BYTECODE OPT: Cache map dimensions for all units
    cachedMapWidth = mapWidth;
    cachedMapHeight = mapHeight;

    // BYTECODE OPT: Pre-compute trap direction arrays for all 8 possible enemy directions
    // This avoids 8 rotation calls per placeRatTraps() invocation
    for (int e = 0; e < 8; e++) {
      Direction toEnemy = DIRECTIONS[e];
      TRAP_DIRS_BY_ENEMY[e][0] = toEnemy;
      TRAP_DIRS_BY_ENEMY[e][1] = toEnemy.rotateLeft();
      TRAP_DIRS_BY_ENEMY[e][2] = toEnemy.rotateRight();
      // BYTECODE OPT: Pre-compute diagonal directions for placeRatTrapsLine
      DIAG_DIRS_BY_ENEMY[e][0] = toEnemy.rotateLeft();
      DIAG_DIRS_BY_ENEMY[e][1] = toEnemy.rotateRight();
      DIAG_DIRS_BY_ENEMY[e][2] = toEnemy.opposite().rotateLeft();
      DIAG_DIRS_BY_ENEMY[e][3] = toEnemy.opposite().rotateRight();
      // BYTECODE OPT: Pre-compute perpendicular directions (90° rotations)
      PERP_LEFT_BY_DIR[e] = toEnemy.rotateLeft().rotateLeft();
      PERP_RIGHT_BY_DIR[e] = toEnemy.rotateRight().rotateRight();
      TRAP_DIRS_BY_ENEMY[e][3] = toEnemy.rotateLeft().rotateLeft();
      TRAP_DIRS_BY_ENEMY[e][4] = toEnemy.rotateRight().rotateRight();
      TRAP_DIRS_BY_ENEMY[e][5] = toEnemy.opposite().rotateLeft();
      TRAP_DIRS_BY_ENEMY[e][6] = toEnemy.opposite().rotateRight();
      TRAP_DIRS_BY_ENEMY[e][7] = toEnemy.opposite();
    }

    if (rc.getType().isRatKingType()) {
      // Reset king-only static state that could carry over between games (tournament mode)
      consecutiveRingFailures = 0;
      consecutiveLineFailures = 0;
      useLineLayout = false;
      useFallbackLayout = false;
      trapPlacementStalled = false;
      rushDetected = false;
      lastThreatRound = -100;
      lastSqueakThreatRound = -100;
      cachedSqueakThreat = 0;
      roundsSinceThreat = 0;

      kingSpawnPoint = rc.getLocation();
      cachedOurKingLoc = kingSpawnPoint;

      rc.writeSharedArray(SLOT_OUR_KING_X, kingSpawnPoint.x);
      rc.writeSharedArray(SLOT_OUR_KING_Y, kingSpawnPoint.y);
      rc.writeSharedArray(SLOT_PHASE, PHASE_BUILDUP);

      // Calculate estimated enemy king position (rotational symmetry)
      estimatedEnemyKingLoc =
          new MapLocation(mapWidth - kingSpawnPoint.x - 1, mapHeight - kingSpawnPoint.y - 1);
      rc.writeSharedArray(SLOT_ENEMY_KING_X, estimatedEnemyKingLoc.x);
      rc.writeSharedArray(SLOT_ENEMY_KING_Y, estimatedEnemyKingLoc.y);
    }
  }

  // ========================================================================
  // GAME STATE MANAGEMENT
  // ========================================================================

  private static void updateGameState(RobotController rc) throws GameActionException {
    cachedRound = rc.getRoundNum();
    myLoc = rc.getLocation();
    myLocX = myLoc.x;
    myLocY = myLoc.y;

    // Cache frequently called methods (bytecode optimization)
    cachedFacing = rc.getDirection();
    cachedActionReady = rc.isActionReady();
    cachedMovementReady = rc.isMovementReady();

    cachedCarryingCheese = rc.getRawCheese() > 0;

    // FIX #4: Cache home guard status (ID never changes, so only need to calculate once)
    // Note: This selects ~17% of ALL rats. Since gatherers are ~55% of rats (id % 100 < 55),
    // this effectively gives us ~17% of rats that happen to be gatherers as home guards.
    if (cachedRound == 1) {
      cachedIsHomeGuard = (rc.getID() % 18) < HOME_GUARD_COUNT;
    }

    // Read our king position
    int kingX = rc.readSharedArray(SLOT_OUR_KING_X);
    int kingY = rc.readSharedArray(SLOT_OUR_KING_Y);
    if (kingX > 0 || kingY > 0) {
      if (cachedOurKingLoc == null || cachedOurKingLoc.x != kingX || cachedOurKingLoc.y != kingY) {
        cachedOurKingLoc = new MapLocation(kingX, kingY);
      }
    }

    // Read enemy king position
    int enemyX = rc.readSharedArray(SLOT_ENEMY_KING_X);
    int enemyY = rc.readSharedArray(SLOT_ENEMY_KING_Y);
    int enemyKingSeenRound = rc.readSharedArray(SLOT_ENEMY_KING_ROUND);
    enemyKingConfirmed = enemyKingSeenRound > 0;

    if (enemyX > 0 || enemyY > 0) {
      if (cachedEnemyKingLoc == null
          || cachedEnemyKingLoc.x != enemyX
          || cachedEnemyKingLoc.y != enemyY) {
        cachedEnemyKingLoc = new MapLocation(enemyX, enemyY);
      }
    }

    // 6-bit: stored as HP/8, multiply back by 8 to get actual HP
    int storedHP = rc.readSharedArray(SLOT_ENEMY_KING_HP);
    cachedEnemyKingHP = (storedHP == 0) ? 500 : (storedHP << 3);

    cachedSpawnCount = rc.readSharedArray(SLOT_SPAWN_COUNT);
    cachedPhase = rc.readSharedArray(SLOT_PHASE);
    if (cachedPhase == 0) cachedPhase = PHASE_BUILDUP;

    cachedThreatLevel = rc.readSharedArray(SLOT_THREAT_LEVEL);

    // FIX #1: Cache global cheese to avoid duplicate API calls in baby rat logic
    cachedGlobalCheese = rc.getGlobalCheese();

    // Cache distance to our king (used frequently)
    // BYTECODE OPTIMIZATION: cache hasOurKingLoc to avoid repeated null checks
    hasOurKingLoc = cachedOurKingLoc != null;
    if (hasOurKingLoc) {
      int dx = myLocX - cachedOurKingLoc.x;
      int dy = myLocY - cachedOurKingLoc.y;
      cachedDistToKingSq = dx * dx + dy * dy;
    } else {
      cachedDistToKingSq = -1;
    }

    // BYTECODE OPT: Cache direction to enemy king (used in multiple scoring functions)
    if (hasOurKingLoc && cachedEnemyKingLoc != null) {
      cachedToEnemyDir = cachedOurKingLoc.directionTo(cachedEnemyKingLoc);
      if (cachedToEnemyDir == Direction.CENTER) cachedToEnemyDir = Direction.NORTH;
    } else {
      cachedToEnemyDir = Direction.NORTH;
    }
  }

  // ========================================================================
  // ROLE DETERMINATION
  // ========================================================================

  // BYTECODE OPT: getRatRole is now inlined in runBabyRat using cachedRolePercent
  // Kept for reference but no longer called in hot path
  @SuppressWarnings("unused")
  private static int getRatRole(int ratId) {
    int rolePercent = ratId % 100;
    if (rolePercent < GATHERER_THRESHOLD) {
      return 0; // Gatherer
    } else if (rolePercent < SENTRY_THRESHOLD) {
      return 1; // Sentry
    } else if (rolePercent < GUARDIAN_THRESHOLD) {
      return 3; // Guardian
    } else if (rolePercent < INTERCEPTOR_THRESHOLD) {
      return 4; // Interceptor
    } else {
      return 2; // Scout
    }
  }

  // ========================================================================
  // VALUE FUNCTION
  // ========================================================================

  // scoreTarget() and getBaseValue() removed - now inlined in scoring functions for bytecode
  // optimization

  // FIX #5: Pass globalCheese as parameter to avoid expensive API call per gatherer
  private static void scoreAllTargetsGatherer(
      RobotController rc,
      RobotInfo[] enemies,
      int enemyCount,
      boolean economyBoost,
      int globalCheese)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    // FIX #4 HIGH: Emergency recall - when cheese < 500, gatherers must help economy
    // CRITICAL FIX: When cheese is critically low (< 200), DON'T patrol to king!
    // The king's area is depleted - gatherers must SPREAD OUT to find cheese elsewhere.
    // Only patrol to king when cheese is moderate (200-500) to stay in delivery range.
    boolean emergencyRecall = globalCheese < EMERGENCY_RECALL_CHEESE || starvationWarningActive;
    boolean criticallyLow = globalCheese < STARVATION_THRESHOLD; // < 200 = spread out to search
    if (emergencyRecall && hasOurKingLoc) {
      if (criticallyLow) {
        // CRITICAL STARVATION: ALL rats spread out to SEARCH for cheese!
        // Don't patrol to king - that area is already depleted!
        // CRITICAL FIX: Remove distance check - even rats NEAR king must spread out
        // because the area near king is depleted. Only searching outward will find cheese.
        cachedBestTarget = getCheeseHuntTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 1000; // Maximum priority
        if (Debug7.ENABLED && (cachedRound % 10) == 0) {
          System.out.println(
              "STARVATION_SEARCH:"
                  + cachedRound
                  + ":"
                  + rc.getID()
                  + ":cheese="
                  + globalCheese
                  + ":distKing="
                  + cachedDistToKingSq
                  + ":target="
                  + cachedBestTarget);
        }
        return;
      } else if (cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
        // MODERATE EMERGENCY (200-500): Only FAR rats return home
        // Near rats can keep collecting nearby cheese
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_PATROL;
        cachedBestScore = 1000; // Maximum priority
        if (Debug7.ENABLED && (cachedRound % 20) == 0) {
          System.out.println(
              "EMERGENCY_RECALL:"
                  + cachedRound
                  + ":"
                  + rc.getID()
                  + ":cheese="
                  + globalCheese
                  + ":starvationIn="
                  + predictedStarvationRounds
                  + ":distKing="
                  + cachedDistToKingSq);
        }
        return;
      }
      // Near king with moderate cheese (200-500) - fall through to normal behavior
    }

    // FIX #2 CRITICAL: Home guard - some gatherers MUST stay near king ALWAYS
    // cachedIsHomeGuard selects ~17% of ALL rats. Since this code only runs for gatherers,
    // we effectively get ~17% of gatherers as home guards (~3 out of every 18 gatherers).
    // FIX #3: Skip if emergencyRecall is active (it already returns early, but cleaner logic)
    // FIX #4: Use cachedIsHomeGuard (calculated once in updateGameState since ID never changes)
    if (!emergencyRecall
        && cachedIsHomeGuard
        && hasOurKingLoc
        && cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
      // Home guard must return to king - don't explore far!
      cachedBestTarget = cachedOurKingLoc;
      cachedBestTargetType = TARGET_PATROL;
      cachedBestScore = 500; // High priority
      if (Debug7.ENABLED && (cachedRound % 30) == 0) {
        System.out.println("HOME_GUARD_RETURN:" + cachedRound + ":distKing=" + cachedDistToKingSq);
      }
      return;
    }

    // FIX #5 MEDIUM: ATK phase economy - don't explore far if cheese < 1000
    // PREDICTIVE: Also limit exploration when starvation is predicted
    boolean atkPhaseEconomyLimit =
        cachedPhase == PHASE_ATTACK
            && (globalCheese < ATK_PHASE_EXPLORE_CHEESE || starvationWarningActive);

    // Priority 1: Deliver cheese if carrying
    // BYTECODE OPTIMIZATION: Use cached hasOurKingLoc instead of null check
    if (cachedCarryingCheese && hasOurKingLoc) {
      // BYTECODE OPT: Inline scoreTarget - base value is DELIVERY_PRIORITY
      int score = (DELIVERY_PRIORITY * 1000) / (1000 + cachedDistToKingSq * DISTANCE_WEIGHT_INT);
      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
      }
    }

    // Priority 2: Attack enemies in home territory (counter-strike!)
    // BYTECODE OPTIMIZATION: Use enemyCount parameter and cached hasOurKingLoc
    if (hasOurKingLoc) {
      int kingX = cachedOurKingLoc.x;
      int kingY = cachedOurKingLoc.y;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemies[i];
        MapLocation enemyLoc = enemy.getLocation();
        int ex = enemyLoc.x;
        int ey = enemyLoc.y;

        // Only attack if enemy is in our territory OR very close to us
        // BYTECODE OPTIMIZATION: Inline distance calculations
        int edx = ex - kingX;
        int edy = ey - kingY;
        int enemyDistToKing = edx * edx + edy * edy;
        int mdx = myLocX - ex;
        int mdy = myLocY - ey;
        int myDistToEnemy = mdx * mdx + mdy * mdy;

        if (enemyDistToKing <= HOME_TERRITORY_RADIUS_SQ || myDistToEnemy <= 8) {
          // BYTECODE OPT: Inline scoreTarget - base value is ENEMY_RAT_VALUE
          int score = (ENEMY_RAT_VALUE * 1000) / (1000 + myDistToEnemy * DISTANCE_WEIGHT_INT);
          if (enemy.getType() == UnitType.RAT_KING) {
            score += 500; // Always attack enemy king!
            // BYTECODE OPT: Early exit - enemy king is highest priority
            // Note: Don't return here - need to check forced delivery below
            cachedBestScore = score;
            cachedBestTarget = enemyLoc;
            cachedBestTargetType = TARGET_ENEMY_RAT;
            break; // Exit enemy loop but continue to forced delivery check
          }
          if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = enemyLoc;
            cachedBestTargetType = TARGET_ENEMY_RAT;
          }
        }
      }
    }

    // Priority 3: Collect cheese (unless carrying)
    // MINE CAMPING: Give bonus to cheese near mines for consistent income
    // FIX 11: During economy emergency, MASSIVELY boost cheese priority
    if (!cachedCarryingCheese) {
      // BYTECODE OPT: Pre-compute cheese base value (depends on phase)
      int cheeseBaseValue =
          (cachedPhase == PHASE_ATTACK) ? CHEESE_VALUE_ATTACK_PHASE : CHEESE_VALUE_NORMAL;
      for (int i = cheeseCount; --i >= 0; ) {
        MapLocation cheese = cheeseBuffer[i];
        int dx = myLocX - cheese.x;
        int dy = myLocY - cheese.y;
        int distSq = dx * dx + dy * dy;
        // BYTECODE OPT: Inline scoreTarget
        int score = (cheeseBaseValue * 1000) / (1000 + distSq * DISTANCE_WEIGHT_INT);
        // FIX 11: Economy emergency - cheese is TOP PRIORITY!
        if (economyBoost) {
          score += 500; // Massive boost to ensure cheese is chosen over explore
        }

        // Bonus for cheese in home territory (safer)
        // BYTECODE OPTIMIZATION: Use cached hasOurKingLoc and inline distance
        if (hasOurKingLoc) {
          int cdx = cheese.x - cachedOurKingLoc.x;
          int cdy = cheese.y - cachedOurKingLoc.y;
          int cheeseDistToKingSq = cdx * cdx + cdy * cdy;
          if (cheeseDistToKingSq <= HOME_TERRITORY_RADIUS_SQ) {
            score += 50;
          }
        }
        // BYTECODE OPTIMIZATION: Removed mine camping nested loop
        // Was O(cheese × mines) per turn - too expensive

        if (score > cachedBestScore) {
          cachedBestScore = score;
          cachedBestTarget = cheese;
          cachedBestTargetType = TARGET_CHEESE;
        }
      }
    }

    // DEFENSIVE ECONOMY: FORCED DELIVERY - If carrying cheese during economy emergency, ALWAYS
    // deliver
    // This MUST come FIRST to ensure cheese reaches the starving king immediately!
    // STRENGTHENED: Also force delivery if we're close to king and carrying ANY cheese
    // FIX: Also force delivery when king HP is low OR in recovery mode
    if (cachedCarryingCheese && hasOurKingLoc) {
      // Emergency mode: always deliver
      // Non-emergency but close to king: also deliver to keep cheese flowing
      // NEW: Force delivery when king HP < 300 or in recovery mode (prevents starvation)
      boolean kingNeedsDelivery = (cachedKingHP > 0 && cachedKingHP < 300) || economyRecoveryMode;
      boolean shouldForceDelivery =
          economyBoost || kingNeedsDelivery || (cachedDistToKingSq <= EMERGENCY_DELIVERY_DIST_SQ);
      if (shouldForceDelivery) {
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
        // FIX #7: Delivery priority - closer rats get higher priority
        // This ensures the closest rat with cheese delivers first, others keep collecting
        int distBonus = (cachedDistToKingSq < 25) ? DELIVERY_PRIORITY_BOOST : 0;
        cachedBestScore = 500 + distBonus; // Absolute maximum priority - king needs cheese!
        // Skip all other priority checks - delivery is critical
        return;
      }
    }

    // Priority 4: ECONOMY EMERGENCY FALLBACK - if no cheese found, RETURN TO KING
    // A starving king needs gatherers CLOSE so they can deliver as soon as they find cheese
    // Don't explore far away - stay in home territory where cheese is more likely to spawn
    if (cachedBestTarget == null && economyBoost && hasOurKingLoc) {
      // No cheese visible but economy is critical - stay near king, not far exploration
      // Cheese often spawns near cheese mines which are usually near king's starting area
      cachedBestTarget = cachedOurKingLoc;
      cachedBestTargetType = TARGET_PATROL;
      cachedBestScore = 80; // Return to king area to find nearby cheese
    }

    // DEFENSIVE ECONOMY LEASH - Force gatherers to return if too far from king
    // This prevents gatherers from exploring to the opposite corner and starving the king
    // FIX #3: During recovery mode, use tighter radius to gather cheese faster near king
    // STRENGTHENED: Tighter leash distances, always applies (not just outside combat)
    // BYTECODE OPT: Use squared distance comparisons instead of intSqrt
    if (hasOurKingLoc && cachedDistToKingSq >= 0) {
      // Determine max exploration distance squared based on cheese level and king health
      // Use economyBoost flag (already computed) instead of re-reading shared array
      // FIX #3: Recovery mode uses COMBAT_PATROL_RADIUS for tighter gathering
      // FIX #4: Critical king HP uses even tighter radius (10 tiles)
      int maxExploreDistSq;
      boolean kingCritical = cachedKingHP > 0 && cachedKingHP < KING_HP_CRITICAL;

      if (economyBoost || kingCritical) {
        // Economy emergency OR king critically low - stay VERY close
        // Use tighter radius (10 tiles) when king is critical to ensure delivery
        maxExploreDistSq = kingCritical ? CRITICAL_GATHER_RADIUS_SQ : ECON_LEASH_CRITICAL_DIST_SQ;
        if (Debug7.ENABLED && kingCritical && (cachedRound % 50) == 0) {
          // Log every 50 rounds to save bytecode during critical phase
          System.out.println(
              "CRITICAL_KING_RADIUS:"
                  + cachedRound
                  + ":kingHP="
                  + cachedKingHP
                  + ":maxDist="
                  + maxExploreDistSq);
        }
      } else if (economyRecoveryMode) {
        // Recovery mode - use combat patrol radius (15 tiles) to stay close for faster cheese
        // collection
        maxExploreDistSq = COMBAT_PATROL_RADIUS_SQ;
        // Debug log for radius change verification (every 10 rounds)
        if (Debug7.ENABLED && (cachedRound % 10) == 0) {
          System.out.println(
              "RECOVERY_RADIUS:" + cachedRound + ":maxDist=" + maxExploreDistSq + ":mode=RECOVERY");
        }
      } else if (atkPhaseEconomyLimit) {
        // FIX #5: ATK phase but low cheese - tighter leash to ensure cheese delivery
        maxExploreDistSq = COMBAT_PATROL_RADIUS_SQ; // 15 tiles instead of 12
      } else {
        // Normal operation - still stay relatively close (12 tiles)
        maxExploreDistSq = ECON_LEASH_NORMAL_DIST_SQ;
      } // LATE-GAME ECONOMY MODE: After round 400, map cheese is often depleted
      // Keep gatherers closer to ensure timely delivery
      // FIX: BUT when starving, REMOVE the leash so gatherers can search for distant cheese!
      // The patrol death spiral occurs because gatherers can't see cheese beyond their scan radius
      // and keep returning to the depleted area near king.
      // STARVATION_LEASH_REMOVAL (150) is tighter than STARVATION_THRESHOLD (200) for explore mode
      boolean starvingRemoveLeash =
          globalCheese < STARVATION_LEASH_REMOVAL && !cachedCarryingCheese;
      if (starvingRemoveLeash) {
        // CRITICAL: Remove leash when starving - gatherers MUST find cheese!
        maxExploreDistSq = Integer.MAX_VALUE;
        if (Debug7.ENABLED && (cachedRound % 10) == 0) {
          System.out.println(
              "STARVATION_LEASH_REMOVED:"
                  + cachedRound
                  + ":cheese="
                  + globalCheese
                  + ":exploring_far");
        }
      } else if (cachedRound > LATE_GAME_ROUND && maxExploreDistSq > LATE_GAME_MAX_DIST_SQ) {
        maxExploreDistSq = LATE_GAME_MAX_DIST_SQ;
        if (Debug7.ENABLED && (cachedRound % 50) == 0) {
          System.out.println(
              "LATE_GAME_ECONOMY:"
                  + cachedRound
                  + ":maxDist="
                  + maxExploreDistSq
                  + ":round="
                  + cachedRound);
        }
      }

      // Small map awareness - cap exploration distance on small maps
      if (isSmallMap && maxExploreDistSq > SMALL_MAP_MAX_EXPLORE_DIST_SQ) {
        maxExploreDistSq = SMALL_MAP_MAX_EXPLORE_DIST_SQ;
      }

      // Check if we're too far from king (squared comparison)
      if (cachedDistToKingSq > maxExploreDistSq) {
        // Too far - return toward king! This overrides current target
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_PATROL;
        cachedBestScore = 150; // High priority to force return
      }
    }

    // Priority 5: In ATTACK phase with no other targets, go to enemy king
    // FIX #5: But NOT if cheese is low - stay close to gather!
    if (cachedBestTarget == null && cachedPhase == PHASE_ATTACK && !atkPhaseEconomyLimit) {
      if (enemyKingConfirmed && cachedEnemyKingLoc != null) {
        cachedBestTarget = cachedEnemyKingLoc;
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 50;
      } else {
        // Explore to find enemy king
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 50;
      }
    } else if (cachedBestTarget == null && atkPhaseEconomyLimit && hasOurKingLoc) {
      // FIX #5: In ATK phase but cheese is low - stay near king to gather
      cachedBestTarget = cachedOurKingLoc;
      cachedBestTargetType = TARGET_PATROL;
      cachedBestScore = 40;
      if (Debug7.ENABLED && (cachedRound % 30) == 0) {
        System.out.println(
            "ATK_ECON_LIMIT:"
                + cachedRound
                + ":"
                + rc.getID()
                + ":cheese="
                + globalCheese
                + ":staying_near_king");
      }
    }

    // Priority 6: PROACTIVE SHIELD WALL for gatherers too (FIX 8)
    // Position 40% of gatherers toward enemy direction from early rounds
    // This ensures defenders are in place BEFORE enemies arrive
    // FIX 13: Use different hash so all roles can participate
    // BYTECODE OPT: Use pre-computed cachedShieldHash and cachedToEnemyDir
    if (cachedBestTarget == null && hasOurKingLoc && cachedEnemyKingLoc != null) {
      // FIX 13: Use cached hash for different distribution
      boolean isShieldWallRat = cachedShieldHash < PROACTIVE_SHIELD_PERCENT; // 40% form shield

      // Only form shield wall in BUILD phase or early rounds (before round 50)
      // After that, gatherers should be collecting cheese
      if (isShieldWallRat && (cachedPhase == PHASE_BUILDUP || cachedRound < 50)) {
        // BYTECODE OPT: Use cached direction to enemy
        Direction perpLeft = cachedToEnemyDir.rotateLeft().rotateLeft();
        int slot = cachedSlotMod5 - 2; // -2, -1, 0, 1, 2 spread (pre-computed)

        int shieldX =
            cachedOurKingLoc.x + cachedToEnemyDir.dx * PROACTIVE_SHIELD_DIST + perpLeft.dx * slot;
        int shieldY =
            cachedOurKingLoc.y + cachedToEnemyDir.dy * PROACTIVE_SHIELD_DIST + perpLeft.dy * slot;

        // BYTECODE OPT: Use cached map dimensions
        shieldX = Math.max(0, Math.min(cachedMapWidth - 1, shieldX));
        shieldY = Math.max(0, Math.min(cachedMapHeight - 1, shieldY));

        // BYTECODE OPT: Inline distance calculation instead of creating MapLocation
        int dxShield = myLocX - shieldX;
        int dyShield = myLocY - shieldY;
        int distToShieldSq = dxShield * dxShield + dyShield * dyShield;

        if (distToShieldSq > 4) {
          cachedBestTarget = new MapLocation(shieldX, shieldY);
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 55; // Higher than fallback, lower than cheese
        } else {
          // At shield position - can still collect nearby cheese
          cachedBestTarget = myLoc;
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 1;
        }
      }
    }

    // Fallback: When starving, EXPLORE AWAY from king to find cheese!
    // This fixes the patrol death spiral where gatherers clump near king where cheese is depleted.
    if (cachedBestTarget == null && cachedOurKingLoc != null) {
      // FIX: When starving and not carrying cheese, explore OUTWARD to find cheese!
      // The old behavior (patrol to king) created a death spiral where gatherers
      // stayed in depleted areas and never found distant cheese.
      // HYSTERESIS: Enter explore mode at < STARVATION_THRESHOLD (200), exit at >
      // STARVATION_EXPLORE_EXIT (500)
      // This prevents oscillation where gatherers flip between patrol/explore every few rounds.
      // Note: getExplorationTarget() returns symmetry-based targets aimed at finding enemy king,
      // not optimal cheese locations. This is a limitation but still better than patrolling to
      // depleted areas near our king. Future improvement: track cheese mine locations.      //
      // HYSTERESIS FIX: Use cheese threshold only (not per-robot state since statics are shared).
      // Enter explore at < 200, exit at > 500. The 200-500 zone defaults to explore to be safe.
      // This is simpler and more robust than tracking per-robot state.
      boolean shouldExploreForCheese =
          globalCheese < STARVATION_EXPLORE_EXIT && !cachedCarryingCheese;
      boolean inStarvationZone = globalCheese < STARVATION_THRESHOLD; // < 200 = definitely explore
      if (inStarvationZone) {
        // STARVATION: Explore AROUND our king to find cheese!
        // CRITICAL FIX: getExplorationTarget() goes toward ENEMY - wrong direction!
        // Cheese spawns on OUR side of the map, so we need to search around our king
        cachedBestTarget = getCheeseHuntTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 100; // High priority - king is starving!
        if (Debug7.ENABLED && (cachedRound % 5) == 0) {
          System.out.println(
              "STARVATION_CHEESE_HUNT:"
                  + cachedRound
                  + ":"
                  + rc.getID()
                  + ":cheese="
                  + globalCheese
                  + ":target="
                  + cachedBestTarget);
        }
      } else if (shouldExploreForCheese) {
        // In hysteresis zone (200-500): search around our king for cheese
        // This prevents oscillation - we only patrol when cheese is truly healthy (> 500)
        cachedBestTarget = getCheeseHuntTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 50; // Lower priority than starvation zone
        // FIX #4: Add logging for hysteresis zone for debugging consistency
        if (Debug7.ENABLED && (cachedRound % 10) == 0) {
          System.out.println(
              "HYSTERESIS_CHEESE_HUNT:"
                  + cachedRound
                  + ":"
                  + rc.getID()
                  + ":cheese="
                  + globalCheese
                  + ":target="
                  + cachedBestTarget);
        }
      } else {
        // Cheese is healthy (> 500) or carrying cheese: patrol near king
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_PATROL;
        cachedBestScore = 1;
      }
    }
  }

  private static void scoreAllTargetsSentry(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    // Priority 1: Attack ANY enemies we can see
    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      MapLocation enemyLoc = enemy.getLocation();
      int dx = myLocX - enemyLoc.x;
      int dy = myLocY - enemyLoc.y;
      int distSq = dx * dx + dy * dy;
      // BYTECODE OPT: Inline scoreTarget - base value is ENEMY_RAT_VALUE
      int score = (ENEMY_RAT_VALUE * 1000) / (1000 + distSq * DISTANCE_WEIGHT_INT) + 100;

      if (enemy.getType() == UnitType.RAT_KING) {
        score += 1000;
        // BYTECODE OPT: Early exit - enemy king is highest priority
        cachedBestScore = score;
        cachedBestTarget = enemyLoc;
        cachedBestTargetType = TARGET_ENEMY_RAT;
        return; // Nothing beats enemy king!
      }

      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = enemyLoc;
        cachedBestTargetType = TARGET_ENEMY_RAT;
      }
    }

    // Priority 2: Deliver cheese if carrying
    // BYTECODE OPTIMIZATION: Use cached hasOurKingLoc and cachedDistToKingSq
    if (cachedCarryingCheese && hasOurKingLoc) {
      // BYTECODE OPT: Inline scoreTarget - base value is DELIVERY_PRIORITY
      int score = (DELIVERY_PRIORITY * 1000) / (1000 + cachedDistToKingSq * DISTANCE_WEIGHT_INT);
      if (score > cachedBestScore) {
        cachedBestScore = score;
        cachedBestTarget = cachedOurKingLoc;
        cachedBestTargetType = TARGET_DELIVERY;
      }
    }

    // Priority 3: Collect adjacent cheese
    // BYTECODE OPT: Skip if already carrying cheese (can't collect more)
    if (!cachedCarryingCheese && cheeseCount > 0) {
      // BYTECODE OPT: Pre-compute cheese base value
      int cheeseBaseValue =
          (cachedPhase == PHASE_ATTACK) ? CHEESE_VALUE_ATTACK_PHASE : CHEESE_VALUE_NORMAL;
      for (int i = cheeseCount; --i >= 0; ) {
        MapLocation cheese = cheeseBuffer[i];
        int dx = myLocX - cheese.x;
        int dy = myLocY - cheese.y;
        int distSq = dx * dx + dy * dy;
        if (distSq <= 8) { // Only very close cheese
          // BYTECODE OPT: Inline scoreTarget
          int score = (cheeseBaseValue * 1000) / (1000 + distSq * DISTANCE_WEIGHT_INT);
          if (score > cachedBestScore) {
            cachedBestScore = score;
            cachedBestTarget = cheese;
            cachedBestTargetType = TARGET_CHEESE;
          }
        }
      }
    }

    // Priority 4: PROACTIVE SHIELD WALL FORMATION (FIX 8)
    // Instead of 360° ring, position toward ENEMY direction from round 1
    // This ensures defenders are already in place when attack comes
    // BYTECODE OPTIMIZATION: Use cached distance and pre-computed hashes
    // FIX 13: Use different hash so sentries can participate in shield wall
    if (cachedBestTarget == null && cachedDistToKingSq >= 0 && hasOurKingLoc) {
      // FIX 13: Use pre-computed cachedShieldHash
      // This allows sentries (roles 70-89) to participate in shield wall
      boolean isShieldWallRat = cachedShieldHash < PROACTIVE_SHIELD_PERCENT; // 40% form shield

      if (isShieldWallRat && cachedEnemyKingLoc != null) {
        // PROACTIVE SHIELD: Position TOWARD enemy, not in a ring
        // BYTECODE OPT: Use pre-cached perpendicular direction
        Direction perpLeft = PERP_LEFT_BY_DIR[cachedToEnemyDir.ordinal()];
        int slot = cachedSlotMod5 - 2; // -2, -1, 0, 1, 2 spread (pre-computed)

        int shieldX =
            cachedOurKingLoc.x + cachedToEnemyDir.dx * PROACTIVE_SHIELD_DIST + perpLeft.dx * slot;
        int shieldY =
            cachedOurKingLoc.y + cachedToEnemyDir.dy * PROACTIVE_SHIELD_DIST + perpLeft.dy * slot;

        // Clamp to map bounds - BYTECODE OPT: Use ternary instead of Math.max/min
        shieldX = shieldX < 0 ? 0 : (shieldX >= cachedMapWidth ? cachedMapWidth - 1 : shieldX);
        shieldY = shieldY < 0 ? 0 : (shieldY >= cachedMapHeight ? cachedMapHeight - 1 : shieldY);

        // BYTECODE OPT: Inline distance calculation
        int dxShield = myLocX - shieldX;
        int dyShield = myLocY - shieldY;
        int distToShieldSq = dxShield * dxShield + dyShield * dyShield;

        if (distToShieldSq > 4) {
          // Not at shield position - move there
          // BYTECODE OPT: Only allocate MapLocation if needed (we're not at position)
          cachedBestTarget = new MapLocation(shieldX, shieldY);
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 60; // Higher priority than old ring
        } else {
          // At shield position - hold and face enemy
          cachedBestTarget = myLoc;
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 1;
        }
      } else {
        // Non-shield rats: use DIRECTIONAL sentry ring logic (FIX 12)
        // Bias 60% of sentries toward enemy direction, not evenly distributed
        if (cachedDistToKingSq < sentryRingInnerSq || cachedDistToKingSq > sentryRingDistSq) {
          Direction ringDir;
          if (cachedEnemyKingLoc != null) {
            // BYTECODE OPT: Use cached direction to enemy

            // FIX 12: Directional distribution - 60% face enemy direction
            // Use pre-computed cachedDirSlot
            if (cachedDirSlot < (SENTRY_ENEMY_BIAS_PERCENT / 10)) {
              // 60% of sentries: Enemy-facing directions (front 3 directions)
              int frontSlot = cachedDirSlot % 3;
              if (frontSlot == 0) ringDir = cachedToEnemyDir;
              else if (frontSlot == 1) ringDir = cachedToEnemyDir.rotateLeft();
              else ringDir = cachedToEnemyDir.rotateRight();
            } else {
              // 40% of sentries: Other directions (flanks and rear)
              int backSlot = (cachedDirSlot - (SENTRY_ENEMY_BIAS_PERCENT / 10)) % 5;
              // BYTECODE OPT: Use pre-cached perpendicular directions
              SENTRY_BACK_DIRS[0] = PERP_LEFT_BY_DIR[cachedToEnemyDir.ordinal()];
              SENTRY_BACK_DIRS[1] = PERP_RIGHT_BY_DIR[cachedToEnemyDir.ordinal()];
              SENTRY_BACK_DIRS[2] = cachedToEnemyDir.opposite().rotateLeft();
              SENTRY_BACK_DIRS[3] = cachedToEnemyDir.opposite().rotateRight();
              SENTRY_BACK_DIRS[4] = cachedToEnemyDir.opposite();
              ringDir = SENTRY_BACK_DIRS[backSlot];
            }
          } else {
            // Fallback to uniform distribution if no enemy known
            ringDir = DIRECTIONS[cachedRolePercent % 8];
          }

          int targetX = cachedOurKingLoc.x + ringDir.dx * sentryRingDist;
          int targetY = cachedOurKingLoc.y + ringDir.dy * sentryRingDist;
          // BYTECODE OPT: Use cached dimensions
          targetX = Math.max(0, Math.min(cachedMapWidth - 1, targetX));
          targetY = Math.max(0, Math.min(cachedMapHeight - 1, targetY));
          cachedBestTarget = new MapLocation(targetX, targetY);
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 50;
        } else {
          cachedBestTarget = myLoc;
          cachedBestTargetType = TARGET_PATROL;
          cachedBestScore = 1;
        }
      }
    }
  }

  // BYTECODE OPT: isInSentryRingPosition() is now inlined at call sites
  // Original logic: cachedDistToKingSq >= 0 && cachedDistToKingSq >= sentryRingInnerSq &&
  // cachedDistToKingSq <= sentryRingDistSq

  /**
   * Make sentry face OUTWARD (away from king) for optimal vision coverage. This creates a 360°
   * detection ring around the king.
   */
  private static void trySentryFaceOutward(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached values
    if (!cachedMovementReady) return;
    if (!hasOurKingLoc) return;

    // Calculate direction AWAY from king (outward)
    Direction awayFromKing = cachedOurKingLoc.directionTo(myLoc);
    if (awayFromKing == Direction.CENTER) return;

    if (cachedFacing == awayFromKing) return; // Already facing outward

    // Turn to face outward if not already
    if (rc.canTurn(awayFromKing)) {
      rc.turn(awayFromKing);
      cachedMovementReady = false; // Update cache
    }
  }

  private static void scoreAllTargetsScout(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    cachedBestTarget = null;
    cachedBestTargetType = TARGET_NONE;
    cachedBestScore = Integer.MIN_VALUE;

    // Check for visible enemy king
    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        cachedBestTarget = enemy.getLocation();
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 1000;
        return;
      }
    }

    // In attack phase, explore to find enemy king
    if (cachedPhase == PHASE_ATTACK) {
      if (!enemyKingConfirmed) {
        cachedBestTarget = getExplorationTarget(rc);
        cachedBestTargetType = TARGET_EXPLORE;
        cachedBestScore = 100;
      } else if (cachedEnemyKingLoc != null) {
        cachedBestTarget = cachedEnemyKingLoc;
        cachedBestTargetType = TARGET_ENEMY_KING;
        cachedBestScore = 200;
      }
    } else {
      // Before attack phase, scouts act as gatherers
      scoreAllTargetsGatherer(rc, enemies, enemyCount, false, cachedGlobalCheese);
    }

    // Fallback: collect cheese on the way
    if (cachedBestTarget == null && !cachedCarryingCheese && cheeseCount > 0) {
      cachedBestTarget = cheeseBuffer[0];
      cachedBestTargetType = TARGET_CHEESE;
      cachedBestScore = 1;
    }
  }

  // Cache for cheese hunt target to avoid repeated allocation
  private static MapLocation cachedCheeseHuntTarget = null;
  private static MapLocation cachedCheeseHuntKingLoc = null;
  private static int cachedCheeseHuntRound = -1;
  private static int cachedCheeseHuntGroup = -1; // Track assigned sector for reassignment

  /**
   * Get a target location for hunting cheese during starvation. CRITICAL: Unlike
   * getExplorationTarget() which goes toward ENEMY, this function returns positions AROUND our king
   * where cheese spawns. Rats are spread in different directions based on their ID.
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

      // Spread rats in 8 directions around our king
      switch (group) {
        case 0: // North
          targetX = kingX;
          targetY = Math.min(kingY + searchRadius, cachedMapHeight - 1);
          break;
        case 1: // Northeast
          targetX = Math.min(kingX + searchRadius, cachedMapWidth - 1);
          targetY = Math.min(kingY + searchRadius, cachedMapHeight - 1);
          break;
        case 2: // East
          targetX = Math.min(kingX + searchRadius, cachedMapWidth - 1);
          targetY = kingY;
          break;
        case 3: // Southeast
          targetX = Math.min(kingX + searchRadius, cachedMapWidth - 1);
          targetY = Math.max(kingY - searchRadius, 0);
          break;
        case 4: // South
          targetX = kingX;
          targetY = Math.max(kingY - searchRadius, 0);
          break;
        case 5: // Southwest
          targetX = Math.max(kingX - searchRadius, 0);
          targetY = Math.max(kingY - searchRadius, 0);
          break;
        case 6: // West
          targetX = Math.max(kingX - searchRadius, 0);
          targetY = kingY;
          break;
        case 7: // Northwest
        default:
          targetX = Math.max(kingX - searchRadius, 0);
          targetY = Math.min(kingY + searchRadius, cachedMapHeight - 1);
          break;
      }
    } else {
      // Fallback: search center of map
      targetX = cachedMapWidth / 2;
      targetY = cachedMapHeight / 2;
    }
    cachedCheeseHuntTarget = new MapLocation(targetX, targetY);
    cachedCheeseHuntKingLoc = cachedOurKingLoc;
    cachedCheeseHuntRound = cachedRound;
    cachedCheeseHuntGroup = group; // Track assigned sector for reassignment

    return cachedCheeseHuntTarget;
  }

  private static MapLocation getExplorationTarget(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Cache exploration target to avoid MapLocation allocation per call
    // Invalidate cache only when king location changes (which affects target calculation)
    // Use reference equality (==) since we assign cachedExplorationKingLoc = cachedOurKingLoc
    // directly
    boolean cacheValid =
        cachedExplorationTarget != null && cachedExplorationKingLoc == cachedOurKingLoc;

    if (cacheValid) {
      return cachedExplorationTarget;
    }

    int id = rc.getID();
    int group = id % 4;

    // BYTECODE OPT: Use cached map dimensions
    int targetX, targetY;

    if (group == 0) {
      // Rotational symmetry
      if (cachedOurKingLoc != null) {
        targetX = cachedMapWidth - cachedOurKingLoc.x - 1;
        targetY = cachedMapHeight - cachedOurKingLoc.y - 1;
      } else {
        targetX = cachedMapWidth - 1;
        targetY = cachedMapHeight - 1;
      }
    } else if (group == 1) {
      // Horizontal reflection
      if (cachedOurKingLoc != null) {
        targetX = cachedMapWidth - cachedOurKingLoc.x - 1;
        targetY = cachedOurKingLoc.y;
      } else {
        targetX = cachedMapWidth - 1;
        targetY = cachedMapHeight / 2;
      }
    } else if (group == 2) {
      // Vertical reflection
      if (cachedOurKingLoc != null) {
        targetX = cachedOurKingLoc.x;
        targetY = cachedMapHeight - cachedOurKingLoc.y - 1;
      } else {
        targetX = cachedMapWidth / 2;
        targetY = cachedMapHeight - 1;
      }
    } else {
      // Center
      targetX = cachedMapWidth / 2;
      targetY = cachedMapHeight / 2;
    }

    targetX = Math.max(0, Math.min(cachedMapWidth - 1, targetX));
    targetY = Math.max(0, Math.min(cachedMapHeight - 1, targetY));

    // Cache the result and the king location used for calculation
    cachedExplorationTarget = new MapLocation(targetX, targetY);
    cachedExplorationKingLoc = cachedOurKingLoc;

    return cachedExplorationTarget;
  }

  // ========================================================================
  // IMMEDIATE ACTIONS
  // ========================================================================

  private static boolean tryImmediateAction(
      RobotController rc, RobotInfo[] enemies, int enemyCount, int focusTargetId)
      throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached value
    if (!cachedActionReady) return false;

    // Priority 1: Attack
    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    if (enemyCount > 0) {
      if (tryAttack(rc, enemies, enemyCount, focusTargetId)) return true;
    }

    // Priority 2: Deliver
    // BYTECODE OPTIMIZATION: Use cached distance
    if (cachedCarryingCheese && cachedDistToKingSq >= 0) {
      if (cachedDistToKingSq <= DELIVERY_RANGE_SQ) {
        if (tryDeliverCheese(rc)) return true;
      }
    }

    // Priority 3: Collect
    if (cheeseCount > 0) {
      if (tryCollectCheese(rc)) return true;
    }

    // Priority 4: Dig
    if (tryDigDirt(rc)) return true;

    return false;
  }

  private static boolean tryAttack(
      RobotController rc, RobotInfo[] enemies, int enemyCount, int focusTargetId)
      throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached value
    if (!cachedActionReady) return false;

    // Always attack enemy king first - with ENHANCED ATTACK!
    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        if (rc.canAttack(loc)) {
          // Use enhanced attack on enemy king - spend cheese for burst damage!
          // Spending X cheese adds ceil(log X) damage (base 10 + bonus)
          // We spend up to 100 cheese for +5 damage = 15 total (50% boost!)
          int cheeseToSpend = Math.min(rc.getRawCheese(), 100);
          if (cheeseToSpend > 10) {
            // Only use enhanced attack if we have meaningful cheese to spend
            rc.attack(loc, cheeseToSpend);
          } else {
            rc.attack(loc);
          }
          return true;
        }
      }
    }

    // FIX 5: SWARM MODE - When outnumbered, focus ALL damage on single targets
    // This maximizes kill rate to reduce enemy DPS quickly
    boolean swarmMode = enemyCount >= SWARM_MODE_ENEMY_THRESHOLD;

    // FIX 4: Optimized single-pass target selection with swarm mode
    // We track best target by base score, then add swarm bonus at the end
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    // 6-bit: stored as HP/8, multiply back by 8 to get actual HP
    int focusTargetHP = rc.readSharedArray(SLOT_FOCUS_HP) << 3;

    // For swarm mode: track lowest HP attackable enemy
    RobotInfo swarmTarget = null;
    int lowestHP = Integer.MAX_VALUE;

    // Single pass: score all targets and find swarm target simultaneously
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      MapLocation loc = enemy.getLocation();
      if (!rc.canAttack(loc)) continue;

      int enemyHP = enemy.getHealth();

      // Track lowest HP for swarm mode (skip kings - handled above)
      if (swarmMode && enemy.getType() != UnitType.RAT_KING && enemyHP < lowestHP) {
        lowestHP = enemyHP;
        swarmTarget = enemy;
      }

      int score = 1000 - enemyHP; // Prioritize wounded

      // FIX 5: SWARM MODE BONUS - applied only to lowest HP target
      if (swarmMode && swarmTarget != null && enemy.getID() == swarmTarget.getID()) {
        score += SWARM_FOCUS_BONUS; // +300 for swarm target
      }

      // OVERKILL PREVENTION: Skip targets that will already die from focus fire
      // If this is the focus target and HP <= 10, one ally attack will kill it
      // Check if multiple allies are attacking same target
      if (focusTargetId > 0 && (enemy.getID() & 1023) == focusTargetId) {
        // This is the focus target - check if it's about to die anyway
        if (focusTargetHP > 0 && focusTargetHP <= 10) {
          // Target will die from one more hit - skip if we're not the closest
          // (Let the closest ally get the kill, others find new targets)
          score -= 200; // Reduce priority, don't skip entirely
        } else {
          score += 500; // Focus fire bonus for healthy targets
          // DEBUG: Log focus fire targeting
          logFocusFire(cachedRound, rc.getID(), focusTargetId, focusTargetHP, "HEALTHY");
        }
      }

      // Bonus for targets that will die from our attack (efficient kills)
      if (enemyHP <= 10) {
        score += 300; // Prioritize finishing kills
      }

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    if (bestTarget != null) {
      MapLocation targetLoc = bestTarget.getLocation();
      int targetHP = bestTarget.getHealth();
      rc.attack(targetLoc);
      cachedActionReady = false; // Update cache
      // DEBUG: Log attack (include swarm mode indicator)
      logAttack(cachedRound, rc.getID(), targetLoc.x, targetLoc.y, targetHP, 10, swarmMode);
      if (targetHP <= 10) {
        logKill(cachedRound, rc.getID(), targetLoc.x, targetLoc.y, bestTarget.getType().name());
      }
      return true;
    }
    return false;
  }

  private static boolean tryDeliverCheese(RobotController rc) throws GameActionException {
    if (!cachedCarryingCheese || cachedOurKingLoc == null) return false;

    int amount = rc.getRawCheese();
    if (amount <= 0) return false;
    if (rc.canTransferCheese(cachedOurKingLoc, amount)) {
      rc.transferCheese(cachedOurKingLoc, amount);
      cachedCarryingCheese = false;
      // DEBUG: Log cheese delivery (guard against -1 distance)
      logCheeseDeliver(
          cachedRound,
          rc.getID(),
          amount,
          rc.getGlobalCheese(),
          cachedDistToKingSq); // BYTECODE OPT: Pass squared dist, avoid intSqrt
      return true;
    }
    return false;
  }

  private static boolean tryCollectCheese(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached value
    if (!cachedActionReady) return false;

    // BYTECODE OPTIMIZATION: Reverse loop is faster
    for (int i = cheeseCount; --i >= 0; ) {
      MapLocation cheese = cheeseBuffer[i];
      // BYTECODE OPTIMIZATION: Inline distance calculation
      int cdx = myLocX - cheese.x;
      int cdy = myLocY - cheese.y;
      if (cdx * cdx + cdy * cdy <= 2) {
        if (rc.canPickUpCheese(cheese)) {
          rc.pickUpCheese(cheese);
          cachedCarryingCheese = true;
          // DEBUG: Log cheese collection
          logCheeseCollect(cachedRound, rc.getID(), true, cheese.x, cheese.y, rc.getRawCheese());
          return true;
        }
      }
    }
    return false;
  }

  private static boolean tryDigDirt(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached values and avoid MapLocation allocation
    if (!cachedActionReady) return false;

    // BYTECODE OPT: Use adjacentLocation instead of creating new MapLocation
    MapLocation ahead = rc.adjacentLocation(cachedFacing);

    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      return true;
    }
    return false;
  }

  // ========================================================================
  // BUG2 PATHFINDING
  // ========================================================================

  private static void bug2MoveTo(RobotController rc, MapLocation target)
      throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached value
    if (target == null || !cachedMovementReady) return;

    // BYTECODE OPT: Cache adjacent trap info once instead of sensing per-direction
    cacheAdjacentTraps(rc);

    // BYTECODE OPTIMIZATION: Inline equality checks
    if (bug2Target == null || target.x != bug2Target.x || target.y != bug2Target.y) {
      bug2Target = target;
      bug2WallFollowing = false;
    }

    // BYTECODE OPTIMIZATION: Inline equality check
    if (myLocX == target.x && myLocY == target.y) return;

    Direction toTarget = myLoc.directionTo(target);

    if (!bug2WallFollowing) {
      if (canMoveSafely(rc, toTarget)) {
        rc.move(toTarget);
        return;
      }

      Direction left = toTarget.rotateLeft();
      Direction right = toTarget.rotateRight();

      if (canMoveSafely(rc, left)) {
        rc.move(left);
        return;
      }
      if (canMoveSafely(rc, right)) {
        rc.move(right);
        return;
      }

      bug2WallFollowing = true;
      bug2WallDir = toTarget;
      bug2StartLoc = myLoc;
      // BYTECODE OPTIMIZATION: Inline distance calculation
      int sdx = myLocX - target.x;
      int sdy = myLocY - target.y;
      bug2StartDist = sdx * sdx + sdy * sdy;
    }

    if (bug2WallFollowing) {
      // BYTECODE OPTIMIZATION: Inline distance and equality checks
      int cdx = myLocX - target.x;
      int cdy = myLocY - target.y;
      int currentDist = cdx * cdx + cdy * cdy;
      // If we've made progress toward target, exit wall-following and try direct movement
      if (currentDist < bug2StartDist && (myLocX != bug2StartLoc.x || myLocY != bug2StartLoc.y)) {
        bug2WallFollowing = false;
        // Try direct movement again
        if (canMoveSafely(rc, toTarget)) {
          rc.move(toTarget);
          return;
        }
      }

      // BYTECODE OPTIMIZATION: Use backward loop (saves ~24 bytecode)
      for (int i = 8; --i >= 0; ) {
        if (canMoveSafely(rc, bug2WallDir)) {
          rc.move(bug2WallDir);
          bug2WallDir = PERP_LEFT_BY_DIR[bug2WallDir.ordinal()];
          return;
        }
        bug2WallDir = bug2WallDir.rotateRight();
      }
    }
  }

  /**
   * URGENT Bug2 movement that ignores rat traps. Use when king is dying and cheese delivery is
   * critical. The rat trap damage (30 HP) is acceptable if it means saving the king.
   */
  private static void bug2MoveToUrgent(RobotController rc, MapLocation target)
      throws GameActionException {
    if (target == null || !cachedMovementReady) return;

    if (myLocX == target.x && myLocY == target.y) return;

    Direction toTarget = myLoc.directionTo(target);

    // Try direct movement first (ignoring rat traps)
    if (canMoveCrisis(rc, toTarget)) {
      rc.move(toTarget);
      return;
    }

    // Try adjacent directions
    Direction left = toTarget.rotateLeft();
    Direction right = toTarget.rotateRight();

    if (canMoveCrisis(rc, left)) {
      rc.move(left);
      return;
    }
    if (canMoveCrisis(rc, right)) {
      rc.move(right);
      return;
    }

    // Try perpendicular directions
    Direction left2 = left.rotateLeft();
    Direction right2 = right.rotateRight();

    if (canMoveCrisis(rc, left2)) {
      rc.move(left2);
      return;
    }
    if (canMoveCrisis(rc, right2)) {
      rc.move(right2);
      return;
    }
  }

  /**
   * Cache adjacent rat trap locations for efficient canMoveSafely() checks. Call once at start of
   * bug2MoveTo() to batch sense all 8 adjacent tiles instead of sensing per-direction.
   *
   * <p>BYTECODE: ~150-200 bytecode once vs ~130 bytecode × 11 calls = ~980 bytecode savings
   */
  private static void cacheAdjacentTraps(RobotController rc) throws GameActionException {
    // Reset cache (backward loop for bytecode)
    for (int d = 8; --d >= 0; ) ADJACENT_RAT_TRAP[d] = false;

    // Batch sense all adjacent tiles (radius²=2 gets exactly the 8 adjacent tiles)
    MapInfo[] nearby = rc.senseNearbyMapInfos(2);
    for (int i = nearby.length; --i >= 0; ) {
      MapInfo info = nearby[i];
      if (info.getTrap() == TrapType.RAT_TRAP) {
        MapLocation loc = info.getMapLocation();
        int dx = loc.x - myLocX;
        int dy = loc.y - myLocY;
        // Convert dx/dy to direction ordinal using lookup table
        if (dx >= -1 && dx <= 1 && dy >= -1 && dy <= 1) {
          int ordinal = DX_DY_TO_DIR_ORDINAL[dx + 1][dy + 1];
          if (ordinal >= 0) ADJACENT_RAT_TRAP[ordinal] = true;
        }
      }
    }
  }

  /**
   * Check if movement in direction is safe (passable and no rat trap). BYTECODE OPT: Uses cached
   * trap data from cacheAdjacentTraps() - only ~25 bytecode per call vs ~130 bytecode.
   */
  private static boolean canMoveSafely(RobotController rc, Direction dir)
      throws GameActionException {
    return rc.canMove(dir) && !ADJACENT_RAT_TRAP[dir.ordinal()];
  }

  /**
   * Crisis movement check - allows movement through rat traps when king is dying. Use this when
   * cheese delivery is critical and the rat trap damage is acceptable.
   */
  private static boolean canMoveCrisis(RobotController rc, Direction dir)
      throws GameActionException {
    return rc.canMove(dir); // Ignore rat traps - king survival is more important
  }

  // ========================================================================
  // VISION CONE MANAGEMENT
  // ========================================================================

  /** Turn toward the most likely valuable direction when nothing is visible. */
  private static void tryTurnTowardTarget(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached values
    if (!cachedMovementReady) return; // Turn uses movement cooldown

    // Priority 1: Turn toward enemy king (only if confirmed)
    // BYTECODE OPTIMIZATION: Inline equality check
    if (enemyKingConfirmed
        && cachedEnemyKingLoc != null
        && (cachedEnemyKingLoc.x != myLocX || cachedEnemyKingLoc.y != myLocY)) {
      Direction toTarget = myLoc.directionTo(cachedEnemyKingLoc);
      if (toTarget != Direction.CENTER) {
        int angleDiff = getAngleDifference(cachedFacing, toTarget);
        if (angleDiff > 45 && rc.canTurn(toTarget)) {
          rc.turn(toTarget);
          cachedMovementReady = false; // Update cache
          return;
        }
      }
    }

    // Priority 2: Turn toward our king if carrying cheese or in emergency defense
    // BYTECODE OPTIMIZATION: Use cached hasOurKingLoc and inline equality check
    if ((cachedCarryingCheese || cachedThreatLevel >= EMERGENCY_DEFENSE_THRESHOLD)
        && hasOurKingLoc
        && (cachedOurKingLoc.x != myLocX || cachedOurKingLoc.y != myLocY)) {
      Direction toKing = myLoc.directionTo(cachedOurKingLoc);
      if (toKing != Direction.CENTER) {
        int angleDiff = getAngleDifference(cachedFacing, toKing);
        if (angleDiff > 45 && rc.canTurn(toKing)) {
          rc.turn(toKing);
          cachedMovementReady = false; // Update cache
          return;
        }
      }
    }
  }

  /**
   * Search scan - turn aggressively to find enemies. Rats turn to scan different directions based
   * on their ID and round number.
   */
  private static void trySearchScan(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use cached values
    if (!cachedMovementReady) return;

    int id = rc.getID();

    // Scan more frequently - every 3 rounds
    if ((cachedRound + id) % 3 == 0) {
      // Scan by turning 90 degrees
      Direction scanDir = cachedFacing.rotateRight().rotateRight();
      if (rc.canTurn(scanDir)) {
        rc.turn(scanDir);
        cachedMovementReady = false; // Update cache
        return;
      }
    }

    // Otherwise, turn toward the cached best target (already computed in scoring)
    // BYTECODE OPTIMIZATION: Inline equality check
    if (cachedBestTarget != null
        && (cachedBestTarget.x != myLocX || cachedBestTarget.y != myLocY)) {
      Direction toTarget = myLoc.directionTo(cachedBestTarget);
      if (toTarget != Direction.CENTER && toTarget != cachedFacing) {
        int angleDiff = getAngleDifference(cachedFacing, toTarget);
        if (angleDiff > 45 && rc.canTurn(toTarget)) {
          rc.turn(toTarget);
          cachedMovementReady = false; // Update cache
          return;
        }
      }
    }
  }

  /** Returns angle difference in degrees (0-180). */
  private static int getAngleDifference(Direction a, Direction b) {
    int diff = a.ordinal() - b.ordinal();
    if (diff < 0) diff = -diff;
    if (diff > 4) diff = 8 - diff;
    return diff * 45;
  }

  // ========================================================================
  // CHEESE SENSING
  // ========================================================================

  private static void findNearbyCheese(RobotController rc) throws GameActionException {
    // BYTECODE OPTIMIZATION: Reduced sensing radius from 20 to 13
    // Still covers vision cone, but ~35% fewer tiles to process
    // FIX: When starving, increase scan radius to find distant cheese!
    // Normal: 13 (radius ~3.6 tiles), Starving: 25 (radius ~5 tiles, ~80 tiles to process)
    // Note: Radius 25 = sqrt(25) = 5 tiles = ~80 tiles vs 13 = ~50 tiles. Moderate increase.
    int scanRadius = (cachedGlobalCheese < STARVATION_THRESHOLD) ? 25 : 13;
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, scanRadius);
    cheeseCount = 0;

    int bufLen = cheeseBuffer.length;
    for (int i = nearbyTiles.length; --i >= 0 && cheeseCount < bufLen; ) {
      MapInfo info = nearbyTiles[i];
      if (info.getCheeseAmount() > 0) {
        cheeseBuffer[cheeseCount++] = info.getMapLocation();
      }
      // BYTECODE OPTIMIZATION: Removed mine camping nested loop
      // The O(cheese × mines) inner loop was too expensive
      // Mine locations rarely change and marginal strategic value
    }
  }

  // ========================================================================
  // KING BEHAVIOR
  // ========================================================================

  private static int lastKingHP = 500;

  private static void runKing(RobotController rc) throws GameActionException {
    // BYTECODE: Start tracking at beginning of turn
    startBytecodeTracking();
    int id = rc.getID();
    int round = rc.getRoundNum();
    logBytecode(round, id, "START");

    MapLocation me = rc.getLocation();
    // BYTECODE OPT: Cache king coordinates for inline distance calculations
    int meX = me.x;
    int meY = me.y;
    int hp = rc.getHealth();

    // Check for cats - HIGHEST PRIORITY! Cats can one-shot kings with pounce (100 damage)
    // This check happens FIRST and will override even KING_FREEZE if needed
    RobotInfo[] neutrals = rc.senseNearbyRobots(-1, Team.NEUTRAL);
    RobotInfo dangerousCat = findDangerousCat(neutrals);
    int distToCatSq = Integer.MAX_VALUE; // Track for later use in movement section

    if (dangerousCat != null) {
      lastKnownCatLoc = dangerousCat.getLocation();
      lastCatSeenRound = round;
      // BYTECODE OPTIMIZATION: Inline distance calculation
      MapLocation catLoc = dangerousCat.getLocation();
      int catDx = me.x - catLoc.x;
      int catDy = me.y - catLoc.y;
      distToCatSq = catDx * catDx + catDy * catDy;
      if (distToCatSq < CAT_DANGER_RADIUS_SQ) {
        // CAT IN DANGER RANGE - MUST FLEE! This overrides everything.
        if (kingFleeFromCat(rc, me, catLoc)) {
          return; // Successfully fled from cat
        }
        // If flee failed (blocked), we'll try again in movement section
      }
    }

    RobotInfo[] enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
    int enemyCount = enemies.length;

    // BYTECODE: After sensing
    logBytecode(round, id, "SENSE");

    // Detect invisible damage
    boolean takingInvisibleDamage = hp < lastKingHP && enemyCount == 0;
    lastKingHP = hp;

    if (takingInvisibleDamage && rc.isMovementReady()) {
      Direction opposite = rc.getDirection().opposite();
      if (rc.canTurn(opposite)) {
        rc.turn(opposite);
        enemies = rc.senseNearbyRobots(-1, cachedEnemyTeam);
        enemyCount = enemies.length;
      }
    }

    // 1. Broadcast position
    rc.writeSharedArray(SLOT_OUR_KING_X, me.x);
    rc.writeSharedArray(SLOT_OUR_KING_Y, me.y);

    // 2. Update game state
    updateGameState(rc);

    // 2b. Read squeaks FIRST - before threat calculation!
    // FIX: Previously squeaks were read AFTER threat was written, causing squeak-derived
    // threat to be immediately overwritten on the next turn. Now we read squeaks first
    // and combine the threat with direct sensing.
    int squeakThreatCount = kingReadSqueaks(rc, id);

    // 3. Update threat level with HYSTERESIS to prevent rapid oscillation
    // Maintain threat for at least THREAT_HYSTERESIS_ROUNDS to allow defense to form
    int oldThreatLevel = cachedThreatLevel;
    // FIX: Combine direct sensing (enemyCount) with squeak-derived threat
    // BYTECODE OPT: Use ternary instead of Math.max
    int newThreatLevel = enemyCount > squeakThreatCount ? enemyCount : squeakThreatCount;

    // THREAT HYSTERESIS: If we had threat recently, don't immediately drop to 0
    if (newThreatLevel > 0) {
      lastThreatRound = round;
      roundsSinceThreat = 0; // Fix 5: Reset safe counter
    } else if (round - lastThreatRound < THREAT_HYSTERESIS_ROUNDS && oldThreatLevel > 0) {
      // Maintain previous threat level for hysteresis period
      // BYTECODE OPT: Use ternary instead of Math.max
      int decayed = oldThreatLevel - 1;
      newThreatLevel = decayed > 1 ? decayed : 1; // Decay slowly, min 1
      roundsSinceThreat = 0; // Still under threat hysteresis
    } else {
      // Fix 5: No threat - increment safe counter
      roundsSinceThreat++;
    }
    rc.writeSharedArray(SLOT_THREAT_LEVEL, newThreatLevel);
    // Fix 5: Broadcast safe rounds so baby rats know when it's safe to explore
    // BYTECODE OPT: Use ternary instead of Math.min
    rc.writeSharedArray(SLOT_SAFE_ROUNDS, roundsSinceThreat < 63 ? roundsSinceThreat : 63);

    // FIX 10: Broadcast king cheese EARLY so baby rats get fresh data this round
    // FIX 11: Use >> 2 (divide by 4) instead of >> 6 (divide by 64) for better precision!
    // Shared array is 10 bits (0-1023), so >> 2 gives range 0-4092 with 4-cheese granularity
    // Previously: 22 cheese >> 6 = 0 (LOST!) Now: 22 >> 2 = 5 -> 5 << 2 = 20 (good!)
    int kingCheese = rc.getGlobalCheese();
    // BYTECODE OPT: Use ternary instead of Math.min
    int cheeseScaled = kingCheese >> 2;
    rc.writeSharedArray(SLOT_KING_CHEESE, cheeseScaled < 1023 ? cheeseScaled : 1023);

    // BYTECODE: After threat calculation
    logBytecode(round, id, "THREAT");

    // DEBUG: Log threat level changes
    if (newThreatLevel != oldThreatLevel) {
      int nearestDist = Integer.MAX_VALUE;
      for (int i = enemyCount; --i >= 0; ) {
        // BYTECODE OPT: Inline distance calculation
        MapLocation eLoc = enemies[i].getLocation();
        int edx = meX - eLoc.x;
        int edy = meY - eLoc.y;
        int d = edx * edx + edy * edy;
        if (d < nearestDist) nearestDist = d;
      }
      logThreatChange(
          round,
          rc.getID(),
          oldThreatLevel,
          newThreatLevel,
          "king_sense",
          nearestDist); // BYTECODE OPT: Pass squared dist, avoid intSqrt
    }

    // 3b. Update body blocking line if enemies are approaching
    updateBlockingLine(rc, me, enemies, enemyCount);

    // 3c. FIX 3: closeEnemyCount calculation removed - now using newThreatLevel directly
    // since squeaks report enemies the king can't directly see

    // 3d. FIX 2: Count defenders near king and broadcast for standing defense system
    int defenderCount = 0;
    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(STANDING_DEFENSE_RADIUS_SQ, cachedOurTeam);
    defenderCount = nearbyAllies.length;
    rc.writeSharedArray(SLOT_DEFENDER_COUNT, defenderCount);

    // DEBUG: Visualize defensive formations
    // BYTECODE OPT: Guard visualize calls to avoid function call overhead when disabled
    if (Debug7.ENABLED) {
      visualizeSentryRing(rc, me, sentryRingDist);
      visualizeTrapRing(rc, me, 4, COLOR_RAT_TRAP);
      visualizeEnemies(rc, enemies, enemyCount);
    }

    // 4. Update focus fire target
    if (enemyCount > 0) {
      updateFocusFireTarget(rc, enemies);
    }

    // 5. Phase management (pass newThreatLevel for force DEF logic)
    updatePhaseWithThreatLevel(rc, round, enemyCount, newThreatLevel);

    // 6. HEAVY TRAP PLACEMENT (PRIORITY OVER SPAWNING!)
    // FIX: Ensure minimum traps are placed before any spawning
    // This prevents the issue where spawning consumes action cooldown before traps are placed
    // Uses adaptive trapWindowEnd based on map size
    // BYTECODE OPT: Reset trap attempts counter each turn
    trapAttemptsThisTurn = 0;
    // BYTECODE OPT: Give up on traps after persistent failures - saves ~6000 bytecode/turn!
    int totalFailures = consecutiveRingFailures + consecutiveLineFailures;
    boolean gaveUpOnTraps = totalFailures > TRAP_GIVE_UP_THRESHOLD;
    boolean trapPriorityPhase = ratTrapCount < MIN_TRAPS_BEFORE_SPAWN;
    if (!gaveUpOnTraps && (round <= trapWindowEnd || trapPriorityPhase) && rc.isActionReady()) {
      if (ratTrapCount < RAT_TRAP_TARGET) {
        int trapsBeforeAttempt = ratTrapCount;

        // Fix 1: Try layouts in order: EMERGENCY -> RING -> LINE -> FALLBACK
        // EMERGENCY MODE: When under attack, prioritize close traps toward enemy
        if (cachedThreatLevel >= EMERGENCY_TRAP_THREAT_THRESHOLD && ratTrapCount < 10) {
          placeEmergencyTraps(rc, me);
        } else if (useFallbackLayout) {
          placeRatTrapsFallback(rc, me);
        } else if (useLineLayout) {
          placeRatTrapsLine(rc, me);
        } else {
          placeRatTraps(rc, me);
        }

        // Track consecutive failures to switch layouts
        if (ratTrapCount == trapsBeforeAttempt && rc.isActionReady()) {
          // Trap placement failed (no valid position found)
          consecutiveRingFailures++;
          // DEBUG: Log trap failure (guarded for bytecode savings)
          if (Debug7.ENABLED) {
            String layoutName = useFallbackLayout ? "FALLBACK" : (useLineLayout ? "LINE" : "RING");
            logTrapFailed(round, rc.getID(), layoutName, consecutiveRingFailures);
          }

          if (!useLineLayout && consecutiveRingFailures >= TRAP_RING_FAILURE_THRESHOLD) {
            // Switch to LINE layout
            useLineLayout = true;
            if (Debug7.ENABLED) {
              logLayoutSwitch(round, rc.getID(), "RING", "LINE", consecutiveRingFailures);
            }
            consecutiveRingFailures = 0; // Reset counter for line layout
          }
        } else {
          // Trap placed successfully, reset failure counter
          consecutiveRingFailures = 0;
        }
      }
      // Try cat traps after rat traps (only in cooperation mode)
      // BYTECODE OPT: Also skip cat traps if we gave up on rat traps
      if (cachedIsCooperation && catTrapCount < CAT_TRAP_TARGET && rc.isActionReady()) {
        placeCatTraps(rc, me);
      }
    } else if (gaveUpOnTraps && Debug7.ENABLED) {
      // Log that we gave up on traps
      logDecision(round, id, "TRAP_GIVE_UP", "totalFailures=" + totalFailures);
    }

    // 6b. STRATEGIC DIRT PLACEMENT - kill corridors, flank protection, reactive walls
    // Order: 1) Reactive walls (urgent), 2) Funnel walls, 3) Flank protection, 4) Dig for stockpile
    if (rc.isActionReady()) {
      placeStrategicDirt(rc, me, round, newThreatLevel);
    }

    // BYTECODE: After trap/wall placement
    logBytecode(round, id, "TRAPS");

    // 7. Spawn rats (economy focus - spawn more!)
    // FIX: Only spawn if we have minimum traps built OR timeout reached (defense priority)
    // This ensures defenses are established before building army, but prevents deadlock
    boolean trapTimeoutReached = round > DEFENSE_TIMEOUT_ROUND;
    // CRITICAL FIX: Also release spawning when we've given up on traps
    // CHEESE HOARD FAILSAFE: Force spawning if hoarding cheese with no army
    boolean cheeseHoardFailsafe =
        rc.getGlobalCheese() > CHEESE_HOARD_FAILSAFE_THRESHOLD && spawnCount == 0;
    boolean defenseMinimumMet =
        ratTrapCount >= MIN_TRAPS_BEFORE_SPAWN
            || trapTimeoutReached
            || gaveUpOnTraps
            || cheeseHoardFailsafe;
    int cheeseReserve = (cachedPhase == PHASE_ATTACK) ? ATTACK_SPAWN_RESERVE : SPAWN_CHEESE_RESERVE;
    int currentCheese = rc.getGlobalCheese();
    int spawnCost = getSpawnCost(rc);

    // ECONOMY RECOVERY MODE: FIX #1 - trigger on low threat OR critical cheese
    // OLD BUG: Required threat==0 which never happens during sustained assault
    // NEW: Trigger when threat<=2 OR cheese is critically low
    // DEBUG: Log recovery state every 10 rounds for better visibility
    if (Debug7.ENABLED && (round % 10) == 0) {
      System.out.println(
          "RECOVERY_STATE:"
              + round
              + ":mode="
              + economyRecoveryMode
              + ":cheese="
              + currentCheese
              + ":threat="
              + cachedThreatLevel
              + ":spawns="
              + spawnCount);
    }
    if (economyRecoveryMode) {
      // Exit recovery mode based on what triggered it
      boolean shouldExit = false;
      String exitReason = "";

      if (recoveryTriggerReason == 4) {
        // King pressure trigger - exit when king HP recovers OR fallback after 50 rounds
        int roundsInRecovery = round - recoveryStartRound;
        if (cachedKingHP > ECONOMY_RECOVERY_EXIT_HP) {
          shouldExit = true;
          exitReason = "HP_RECOVERED:" + cachedKingHP;
        } else if (roundsInRecovery > 50 && currentCheese >= ECONOMY_RECOVERY_EXIT) {
          // Fallback: after 50 rounds, allow cheese-based exit to prevent infinite loop
          shouldExit = true;
          exitReason = "TIMEOUT_FALLBACK:rounds=" + roundsInRecovery + ":cheese=" + currentCheese;
        }
      } else if (recoveryTriggerReason == 5) {
        // Predicted starvation trigger - exit when starvation no longer imminent
        if (predictedStarvationRounds > STARVATION_WARNING_ROUNDS * 2) {
          shouldExit = true;
          exitReason = "STARVATION_AVERTED:predictedRounds=" + predictedStarvationRounds;
        } else if (currentCheese >= ECONOMY_RECOVERY_EXIT
            && predictedStarvationRounds > STARVATION_WARNING_ROUNDS) {
          // Also exit if cheese is high AND starvation not imminent
          shouldExit = true;
          exitReason =
              "CHEESE_RECOVERED:" + currentCheese + ":predictedRounds=" + predictedStarvationRounds;
        }
      } else {
        // All other triggers - exit when cheese is healthy
        if (currentCheese >= ECONOMY_RECOVERY_EXIT) {
          shouldExit = true;
          exitReason = "CHEESE_HEALTHY:" + currentCheese;
        }
      }

      if (shouldExit) {
        economyRecoveryMode = false;
        recoveryTriggerReason = 0;
        recoveryStartRound = 0; // Reset for next recovery cycle
        if (Debug7.ENABLED) System.out.println("RECOVERY_EXIT:" + round + ":reason=" + exitReason);
      }
    } else {
      // FIX #1: Multiple ways to enter recovery mode:
      // 1. Low threat (<=3) AND low cheese (<800) - normal recovery
      // 2. CRITICAL cheese (<200) - emergency recovery regardless of threat
      // 3. Sustained combat drain - high threat with declining cheese
      // 4. King under pressure - HP < 400
      boolean lowThreatRecovery =
          cachedThreatLevel <= ECONOMY_RECOVERY_LOW_THREAT
              && currentCheese < ECONOMY_RECOVERY_THRESHOLD
              && spawnCount > 5; // Lowered from 10 to trigger earlier
      // Also trigger if we have high threat AND low cheese (sustained combat draining economy)
      boolean sustainedCombatDrain =
          cachedThreatLevel >= 3 && currentCheese < 1000 && spawnCount > 8; // Lowered thresholds
      boolean cheeseCritical =
          currentCheese < CHEESE_CRITICAL_THRESHOLD && spawnCount > 3; // Lowered from 5
      // NEW: Trigger recovery when king is taking damage (HP < 400) regardless of cheese
      boolean kingUnderPressure = cachedKingHP > 0 && cachedKingHP < 400 && spawnCount > 5;

      if (lowThreatRecovery || cheeseCritical || sustainedCombatDrain || kingUnderPressure) {
        economyRecoveryMode = true;
        recoveryStartRound = round; // Track when recovery started
        // Track which condition triggered for proper exit logic
        // Priority: king pressure > sustained combat > cheese critical > low threat
        if (kingUnderPressure) {
          recoveryTriggerReason = 4; // Uses HP-based exit
        } else if (sustainedCombatDrain) {
          recoveryTriggerReason = 3;
        } else if (cheeseCritical) {
          recoveryTriggerReason = 2;
        } else {
          recoveryTriggerReason = 1;
        }
        // Debug: which condition triggered?
        String triggerReason =
            kingUnderPressure
                ? "KING_PRESSURE"
                : (sustainedCombatDrain
                    ? "SUSTAINED_COMBAT"
                    : (cheeseCritical ? "CHEESE_CRITICAL" : "LOW_THREAT"));
        if (Debug7.ENABLED)
          System.out.println(
              "RECOVERY_ENTER:"
                  + round
                  + ":reason="
                  + triggerReason
                  + ":cheese="
                  + currentCheese
                  + ":threat="
                  + cachedThreatLevel
                  + ":kingHP="
                  + cachedKingHP);
      }
    }

    // SPAWN THROTTLING: Check multiple conditions before spawning
    // FIX: When recovery is triggered by king pressure, be extra conservative
    boolean recoveryFromKingPressure = economyRecoveryMode && recoveryTriggerReason == 4;
    // 1. Basic defense minimum met
    // 2. Have enough cheese reserve
    // 3. CRITICAL: King cheese after spawn must be >= MIN_KING_CHEESE_AFTER_SPAWN
    // 4. NOT in economy recovery mode (unless under attack)
    // 5. During combat, reserve extra cheese for king survival
    int effectiveMinCheese = MIN_KING_CHEESE_AFTER_SPAWN;
    if (cachedThreatLevel >= 2) {
      // During combat, reserve more cheese to prevent starvation
      effectiveMinCheese = KING_CHEESE_SURVIVAL_RESERVE;
    }
    boolean canAffordSpawn = currentCheese > cheeseReserve + spawnCost;
    boolean kingWontStarve = (currentCheese - spawnCost) >= effectiveMinCheese;
    boolean notInRecovery =
        !economyRecoveryMode || cachedThreatLevel >= 3; // Override recovery only if high threat

    // FIX #1 CRITICAL: Absolute cheese reserve - NEVER spawn if cheese would drop below 300
    // This prevents the economy death spiral where we spawn too many rats and starve
    boolean maintainsReserve = (currentCheese - spawnCost) >= ABSOLUTE_CHEESE_RESERVE;

    // PREDICTIVE STARVATION: Block spawning if starvation is critical (< 15 rounds)
    // This gives gatherers time to collect and deliver cheese
    boolean starvationBlocksSpawn = starvationCriticalActive && spawnCount > 5;
    if (starvationBlocksSpawn && Debug7.ENABLED && (round % 10) == 0) {
      System.out.println(
          "STARVATION_SPAWN_BLOCK:"
              + round
              + ":predictedRounds="
              + predictedStarvationRounds
              + ":spawns="
              + spawnCount);
    }

    if (!maintainsReserve && Debug7.ENABLED && (round % 10) == 0) {
      System.out.println(
          "SPAWN_RESERVE_BLOCK:"
              + round
              + ":cheese="
              + currentCheese
              + ":spawnCost="
              + spawnCost
              + ":reserve="
              + ABSOLUTE_CHEESE_RESERVE);
    }

    // ========================================================================
    // PREDICTIVE STARVATION DETECTION
    // Calculate rounds until cheese depletion based on income/consumption rate
    // ========================================================================
    predictedStarvationRounds = predictStarvationRounds(currentCheese, round);
    starvationWarningActive = predictedStarvationRounds <= STARVATION_WARNING_ROUNDS;
    starvationCriticalActive = predictedStarvationRounds <= STARVATION_CRITICAL_ROUNDS;

    // Log starvation prediction when warning is active
    if (Debug7.ENABLED && starvationWarningActive && (round % 5) == 0) {
      System.out.println(
          "STARVATION_PREDICT:"
              + round
              + ":cheese="
              + currentCheese
              + ":predictedRounds="
              + predictedStarvationRounds
              + ":warning="
              + starvationWarningActive
              + ":critical="
              + starvationCriticalActive);
    }

    // CRITICAL: If starvation is imminent, force recovery mode
    if (starvationCriticalActive && !economyRecoveryMode) {
      economyRecoveryMode = true;
      recoveryTriggerReason = 5; // 5 = predicted starvation
      recoveryStartRound = round;
      if (Debug7.ENABLED) {
        System.out.println(
            "RECOVERY_ENTER:"
                + round
                + ":reason=PREDICTED_STARVATION:predictedRounds="
                + predictedStarvationRounds);
      }
    }

    // FIX #3 HIGH: Spawn cap - limit spawns when cheese is low
    // Prevent over-spawning that drains economy
    // ENHANCED: Also cap spawns when starvation is predicted
    boolean spawnCapOk = true;
    // Late-game uses tighter spawn cap (15 instead of 20)
    int effectiveSpawnCap = round > LATE_GAME_ROUND ? LATE_GAME_SPAWN_CAP : SPAWN_CAP_MAX_SPAWNS;
    if ((currentCheese < SPAWN_CAP_CHEESE_THRESHOLD || starvationWarningActive)
        && spawnCount >= effectiveSpawnCap) {
      spawnCapOk = false;
      if (Debug7.ENABLED && (round % 20) == 0) {
        System.out.println(
            "SPAWN_CAP_HIT:"
                + round
                + ":spawns="
                + spawnCount
                + ":cheese="
                + currentCheese
                + ":cap="
                + effectiveSpawnCap);
      }
    }

    // FIX #4: HP-based spawn throttling
    // Pause spawning when king HP is critically low
    // Slow spawning when king HP is moderately low
    boolean hpAllowsSpawn =
        true; // FIX: Only pause spawning at critical HP, NOT during king pressure recovery
    // When king is under pressure with plenty of cheese, we need MORE defenders!
    if (cachedKingHP < SPAWN_PAUSE_KING_HP) {
      hpAllowsSpawn = false; // Complete pause only when HP critically low
      if (Debug7.ENABLED && (round % 10) == 0)
        System.out.println("SPAWN_PAUSE:" + round + ":kingHP=" + cachedKingHP);
    } else if (cachedKingHP < SPAWN_THROTTLE_KING_HP) {
      hpAllowsSpawn = (round % 2) == 0; // Spawn every other round
    }

    // Add maintainsReserve and spawnCapOk checks to prevent economy collapse
    if (defenseMinimumMet
        && canAffordSpawn
        && kingWontStarve
        && notInRecovery
        && hpAllowsSpawn
        && maintainsReserve
        && spawnCapOk
        && !starvationBlocksSpawn) {
      if (trySpawnRat(rc)) {
        spawnCount++;
        rc.writeSharedArray(SLOT_SPAWN_COUNT, spawnCount);
      }
    }

    // Broadcast economy recovery mode to baby rats
    rc.writeSharedArray(SLOT_ECONOMY_RECOVERY, economyRecoveryMode ? 1 : 0);

    // Broadcast starvation prediction to baby rats (capped at 1023 for 10-bit slot)
    int starvationBroadcast = predictedStarvationRounds < 1023 ? predictedStarvationRounds : 1023;
    rc.writeSharedArray(SLOT_STARVATION_PREDICT, starvationBroadcast);

    // Broadcast king HP for survival improvements (scaled down to fit in shared array)
    int kingHP = rc.getHealth();
    rc.writeSharedArray(SLOT_KING_HP, kingHP / 10);
    cachedKingHP = kingHP; // Update cache

    // BYTECODE: After spawning
    logBytecode(round, id, "SPAWN");

    // 8. King movement - STAY SAFE!
    // Fix 4: FREEZE when threat >= 3 - absolutely no movement to let defense form
    // This prevents the issue where king keeps moving and rats chase moving target
    if (rc.isMovementReady()) {
      // BYTECODE OPT: Inline distance calculation
      int spawnDx = meX - kingSpawnPoint.x;
      int spawnDy = meY - kingSpawnPoint.y;
      int distToSpawnSq = spawnDx * spawnDx + spawnDy * spawnDy;

      // FIX: CAT EVASION OVERRIDES KING_FREEZE!
      // Cats deal 100 damage (instant kill) vs enemies dealing ~10 per attack
      // If a cat is nearby, we MUST move regardless of threat level
      boolean catNearby = distToCatSq < CAT_CAUTION_RADIUS_SQ;
      boolean catDanger = distToCatSq < CAT_DANGER_RADIUS_SQ;

      if (catDanger && dangerousCat != null) {
        // CAT IN DANGER RANGE - OVERRIDE FREEZE AND FLEE!
        // This is the second attempt if the first one failed (movement wasn't ready)
        logDecision(
            round,
            rc.getID(),
            "CAT_OVERRIDE_FREEZE",
            "catDistSq=" + distToCatSq + ":threat=" + newThreatLevel + ":MUST_FLEE");
        kingFleeFromCat(rc, me, dangerousCat.getLocation());
        // Don't return - let other logic run if flee failed
      } else if (catNearby && dangerousCat != null) {
        // CAT IN CAUTION RANGE - proactive avoidance, override freeze
        logDecision(
            round,
            rc.getID(),
            "CAT_CAUTION_MOVE",
            "catDistSq=" + distToCatSq + ":proactive_avoidance");
        kingFleeFromCat(rc, me, dangerousCat.getLocation());
      } else if (newThreatLevel >= 3) {
        // Fix 4: KING FREEZE - When threat level >= 3, DO NOT MOVE AT ALL
        // This lets blockers and defenders form around a stable position
        // ONLY freeze if no cat nearby!
        logKingFreeze(round, rc.getID(), newThreatLevel, hp, enemyCount, roundsSinceThreat);
        // Skip all movement logic - stay completely still
      } else if (enemyCount >= KING_ANCHOR_THREAT_THRESHOLD) {
        // KING ANCHORING: Under moderate attack - prioritize staying near spawn
        // Only move if we're too far from spawn point
        if (distToSpawnSq > KING_ANCHOR_DIST_SQ) {
          // Move toward spawn point, not away from enemies
          Direction toSpawn = me.directionTo(kingSpawnPoint);
          tryKingMove(rc, toSpawn);
        } else {
          // FIX: Allow minimal evasion even when anchored if enemy is VERY close
          // This prevents the king from just sitting still while being attacked
          int closestEnemyDistSq = Integer.MAX_VALUE;
          for (int i = enemyCount; --i >= 0; ) {
            // BYTECODE OPT: Inline distance calculation
            MapLocation eLoc = enemies[i].getLocation();
            int edx = meX - eLoc.x;
            int edy = meY - eLoc.y;
            int d = edx * edx + edy * edy;
            if (d < closestEnemyDistSq) closestEnemyDistSq = d;
          }
          if (closestEnemyDistSq <= KING_EMERGENCY_EVADE_DIST_SQ) {
            // Enemy within 2 tiles - minimal evasion toward spawn
            evadeFromEnemies(rc, enemies);
          }
          // Otherwise stay still - let the defense form around us!
        }
      } else if (enemyCount > 0) {
        // Low threat - evade but don't go too far from spawn
        if (distToSpawnSq <= KING_SAFE_ZONE_RADIUS_SQ * 2) {
          evadeFromEnemies(rc, enemies);
        } else {
          // Already far from spawn - return instead of evading further
          Direction toSpawn = me.directionTo(kingSpawnPoint);
          tryKingMove(rc, toSpawn);
        }
      } else if (distToSpawnSq > KING_SAFE_ZONE_RADIUS_SQ) {
        // No enemies - return to safe zone
        Direction toSpawn = me.directionTo(kingSpawnPoint);
        tryKingMove(rc, toSpawn);
      }
      // Otherwise stay still - no random wandering!
    }

    // BYTECODE: After king movement
    logBytecode(round, id, "MOVE");

    // 10. Broadcast enemy king if visible
    broadcastEnemyKing(rc, enemies);

    // DEBUG: Log economy summary (kingCheese already computed and broadcast earlier)
    logEconomySummary(round, rc.getID(), kingCheese, spawnCount, roundsSinceThreat, newThreatLevel);

    // DEBUG: Log king summary and set indicator
    // FIX 4: Use newThreatLevel instead of enemyCount for accurate threat reporting
    logKingSummary(
        round,
        rc.getID(),
        hp,
        rc.getGlobalCheese(),
        spawnCount,
        newThreatLevel, // FIX 4: Was enemyCount - now shows actual threat level
        phaseName(cachedPhase),
        ratTrapCount + catTrapCount,
        dirtWallCount,
        rushDetected);
    indicator(
        rc,
        buildKingIndicator(
            phaseName(cachedPhase), hp, rc.getGlobalCheese(), enemyCount, spawnCount));

    // 11. Broadcast cat location if cat is near king (for cat baiting)
    if (dangerousCat != null) {
      MapLocation catLoc = dangerousCat.getLocation();
      // BYTECODE OPT: Inline distance calculation
      int catDx = meX - catLoc.x;
      int catDy = meY - catLoc.y;
      int catDistToKing = catDx * catDx + catDy * catDy;
      if (catDistToKing <= CAT_BAIT_TRIGGER_DIST_SQ) {
        // Cat is close to king - broadcast for baby rats to bait it away
        rc.writeSharedArray(SLOT_CAT_X, catLoc.x);
        rc.writeSharedArray(SLOT_CAT_Y, catLoc.y);
        rc.writeSharedArray(SLOT_CAT_ROUND, round);
      }
    }

    // BYTECODE: End of turn summary
    logBytecode(round, id, "END");
    logBytecodeSummary(round, id, true);
  }

  private static void updatePhase(RobotController rc, int round, int enemyCount)
      throws GameActionException {
    updatePhaseWithThreatLevel(rc, round, enemyCount, 0);
  }

  private static void updatePhaseWithThreatLevel(
      RobotController rc, int round, int enemyCount, int threatLevel) throws GameActionException {
    int currentPhase = cachedPhase;
    int newPhase = currentPhase;

    // COUNTER-RUSH DETECTION: If rush was detected, stay in defense longer
    boolean stayDefensive = rushDetected && round < COUNTER_RUSH_ROUND + 30;

    // Fix 2: DEFENSE-READY CHECK - Only allow attack phase when defenses are built
    // If trap placement is stalling with < 8 traps, stay in BUILD to keep trying
    // Timeout fallback ensures we attack eventually even if trap placement fails
    // CRITICAL FIX: Also consider defense ready when we've given up on traps
    boolean trapsGaveUp =
        (consecutiveRingFailures + consecutiveLineFailures) > TRAP_GIVE_UP_THRESHOLD;
    boolean defenseReady =
        (ratTrapCount >= MIN_TRAPS_FOR_ATTACK) || round >= DEFENSE_TIMEOUT_ROUND || trapsGaveUp;

    // Fix 2: If traps are stalling but we have < 8, force staying in BUILD
    // Only stall BUILD phase if we haven't given up on traps and timeout hasn't been reached
    if (trapPlacementStalled && ratTrapCount < 8 && round < DEFENSE_TIMEOUT_ROUND && !trapsGaveUp) {
      defenseReady = false; // Don't leave BUILD phase yet
      logDecision(
          rc.getRoundNum(),
          rc.getID(),
          "TRAP_STALL_OVERRIDE",
          "traps=" + ratTrapCount + ":stalled=true:staying_in_build");
    }

    // FIX 3: Force DEF phase if threat level is high (from direct sensing OR squeaks)
    // This is checked here so it integrates with normal phase logic
    // BUG FIX: Use threatLevel instead of closeEnemyCount because king may not
    // directly see enemies that baby rats reported via squeaks!
    boolean forceDefense = threatLevel >= FORCE_DEF_ENEMY_COUNT;

    if (currentPhase == PHASE_BUILDUP) {
      // FIX 3: Force defense if under heavy attack
      if (forceDefense) {
        newPhase = PHASE_DEFENSE;
      }
      // Switch to attack phase if conditions met (but not during counter-rush)
      // FIX: Also require defenses to be ready before attacking
      // Use dynamic attack round based on map size
      else if (!stayDefensive
          && defenseReady
          && (round >= attackPhaseRound || spawnCount >= ATTACK_PHASE_SPAWN_COUNT)) {
        newPhase = PHASE_ATTACK;
      }
      // Switch to defense if under attack OR rush detected
      else if (enemyCount >= DEFENSE_TRIGGER_ENEMIES || rushDetected) {
        newPhase = PHASE_DEFENSE;
      }
    } else if (currentPhase == PHASE_DEFENSE) {
      // FIX 3: Stay in defense if under heavy attack
      if (forceDefense) {
        // Stay in PHASE_DEFENSE, don't transition out
        newPhase = PHASE_DEFENSE;
      }
      // Return to buildup only if no enemies AND rush has been handled
      else if (enemyCount == 0 && !stayDefensive) {
        newPhase = PHASE_BUILDUP;
      }
      // Switch to attack if conditions met (after rush is handled)
      // FIX: Also require defenses to be ready
      else if (!stayDefensive
          && defenseReady
          && (round >= attackPhaseRound || spawnCount >= ATTACK_PHASE_SPAWN_COUNT)) {
        newPhase = PHASE_ATTACK;
      }
    } else if (currentPhase == PHASE_ATTACK) {
      // FIX 3: Even in attack phase, revert to defense if under heavy attack
      if (forceDefense) {
        newPhase = PHASE_DEFENSE;
      }
    }

    if (newPhase != currentPhase) {
      // DEBUG: Log phase transition
      String reason = "";
      if (newPhase == PHASE_ATTACK) reason = defenseReady ? "defense_ready" : "spawn_count";
      else if (newPhase == PHASE_DEFENSE) reason = rushDetected ? "rush_detected" : "enemies_near";
      else if (newPhase == PHASE_BUILDUP) reason = "threat_cleared";
      logPhaseChange(
          round, rc.getID(), phaseName(currentPhase), phaseName(newPhase), reason, enemyCount);

      rc.writeSharedArray(SLOT_PHASE, newPhase);
      cachedPhase = newPhase;
    }
  }

  private static void updateFocusFireTarget(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    RobotInfo bestTarget = null;
    int bestScore = Integer.MIN_VALUE;
    int enemyCount = enemies.length;

    // FIX 5: In swarm mode (3+ enemies), prioritize lowest HP for quick kills
    boolean swarmMode = enemyCount >= SWARM_MODE_ENEMY_THRESHOLD;

    // Track lowest HP non-king for swarm mode focus
    int lowestHP = Integer.MAX_VALUE;
    RobotInfo lowestHPEnemy = null;

    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      int score = (enemy.getType() == UnitType.RAT_KING) ? 10000 : 1000;

      int hp = enemy.getHealth();

      // Track lowest HP for swarm mode
      if (swarmMode && enemy.getType() != UnitType.RAT_KING && hp < lowestHP) {
        lowestHP = hp;
        lowestHPEnemy = enemy;
      }

      // Normal scoring: prefer wounded
      score -= hp;

      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }

    // FIX 5: In swarm mode, override with lowest HP target for focus fire
    if (swarmMode && lowestHPEnemy != null) {
      // Swarm focus: everyone attacks the lowest HP enemy
      bestTarget = lowestHPEnemy;
    }

    if (bestTarget != null) {
      rc.writeSharedArray(SLOT_FOCUS_TARGET, bestTarget.getID() & 63);
      int hp = bestTarget.getHealth();
      // 6-bit: divide HP by 8, max 63 -> range 0-504 (king max HP is 500)
      rc.writeSharedArray(SLOT_FOCUS_HP, Math.min(hp >> 3, 63));
    }
  }

  private static int getSpawnCost(RobotController rc) throws GameActionException {
    return rc.getCurrentRatCost();
  }

  /**
   * Predicts how many rounds until cheese runs out based on current consumption rate. Called by
   * king each round to enable proactive starvation prevention.
   *
   * @param currentCheese Current global cheese amount
   * @param round Current round number
   * @return Predicted rounds until starvation (999 if safe)
   */
  private static int predictStarvationRounds(int currentCheese, int round) {
    // Track cheese change from last round
    int cheeseChange = currentCheese - lastRoundCheese;
    lastRoundCheese = currentCheese;

    // Skip first round (no baseline)
    if (round <= 1) {
      return STARVATION_SAFE;
    }

    // Update rolling average (use last 10 rounds for smoothing)
    cheeseChangeSum += cheeseChange;
    cheeseTrackingRounds++;

    // Use exponential moving average instead of reset (preserves history better)
    // Formula: newAvg = oldAvg - (oldAvg / 10) + newValue
    if (cheeseTrackingRounds >= 10) {
      cheeseChangeSum = cheeseChangeSum - (cheeseChangeSum / 10) + cheeseChange;
      cheeseTrackingRounds = 10; // Cap at 10 for consistent averaging
    }

    // Guard against division by zero (should never happen after round 1, but be safe)
    if (cheeseTrackingRounds == 0) {
      return STARVATION_SAFE;
    }

    // Calculate average cheese change per round
    // Negative = losing cheese, Positive = gaining cheese
    int avgCheeseChange = cheeseChangeSum / cheeseTrackingRounds;

    // FIX: When cheese income is 0 for ZERO_INCOME_ROUNDS_THRESHOLD rounds,
    // predict starvation based on current cheese divided by king consumption
    // This catches late-game map exhaustion where gatherers find nothing
    if (avgCheeseChange == 0 && cheeseTrackingRounds >= ZERO_INCOME_ROUNDS_THRESHOLD) {
      // Zero income for a while - predict based on current cheese and king consumption
      int roundsUntilZero = currentCheese / KING_CONSUMPTION_PER_ROUND;
      if (Debug7.ENABLED && (round % 20) == 0) {
        System.out.println(
            "ZERO_INCOME_PREDICT:"
                + round
                + ":cheese="
                + currentCheese
                + ":rounds="
                + roundsUntilZero);
      }
      return roundsUntilZero;
    }

    // Net cheese flow = income - king consumption (3/round)
    // If avgCheeseChange is already negative, we're already losing cheese
    // If avgCheeseChange is positive but < 3, we're still losing net cheese
    int netCheeseFlow = avgCheeseChange; // Already includes king consumption effect

    // If we're gaining cheese or stable, no starvation predicted
    if (netCheeseFlow >= 0) {
      return STARVATION_SAFE;
    }

    // Calculate rounds until cheese = 0
    // roundsUntilZero = currentCheese / |netCheeseFlow|
    int roundsUntilZero = currentCheese / (-netCheeseFlow);

    // Cap at 999 (effectively "safe")
    return roundsUntilZero < 999 ? roundsUntilZero : 999;
  }

  private static boolean trySpawnRat(RobotController rc) throws GameActionException {
    MapLocation kingLoc = rc.getLocation();

    // Spawn away from enemy (safer!)
    Direction awayFromEnemy = Direction.SOUTH;
    if (cachedEnemyKingLoc != null) {
      awayFromEnemy = cachedEnemyKingLoc.directionTo(kingLoc);
      if (awayFromEnemy == Direction.CENTER) awayFromEnemy = Direction.SOUTH;
    }

    // Use pre-allocated array for bytecode optimization
    SPAWN_PRIORITY[0] = awayFromEnemy;
    SPAWN_PRIORITY[1] = awayFromEnemy.rotateLeft();
    SPAWN_PRIORITY[2] = awayFromEnemy.rotateRight();
    SPAWN_PRIORITY[3] = awayFromEnemy.rotateLeft().rotateLeft();
    SPAWN_PRIORITY[4] = awayFromEnemy.rotateRight().rotateRight();
    SPAWN_PRIORITY[5] = awayFromEnemy.opposite().rotateLeft();
    SPAWN_PRIORITY[6] = awayFromEnemy.opposite().rotateRight();
    SPAWN_PRIORITY[7] = awayFromEnemy.opposite();

    // BYTECODE OPT: Use backward loops
    for (int dist = 2; dist <= 4; dist++) {
      for (int i = 8; --i >= 0; ) {
        Direction dir = SPAWN_PRIORITY[i];
        MapLocation spawnLoc = kingLoc.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canBuildRat(spawnLoc)) {
          rc.buildRat(spawnLoc);
          return true;
        }
      }
    }

    return false;
  }

  private static void placeRatTraps(RobotController rc, MapLocation me) throws GameActionException {
    if (!rc.isActionReady()) return;

    // FIX 12: DIRECTIONAL TRAP PLACEMENT - prioritize enemy direction!
    // Place traps toward enemy FIRST, then fill in the ring.
    // This ensures traps actually block enemy approach.

    // BYTECODE OPT: Determine direction toward enemy and lookup pre-cached array
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    // BYTECODE OPT: Use pre-computed direction array instead of 8 rotations per call
    // Priority order: Enemy direction -> Adjacent -> Perpendicular -> Behind
    Direction[] trapDirs = TRAP_DIRS_BY_ENEMY[toEnemy.ordinal()];

    // BYTECODE OPT: Cache king position for bounds checking
    int meX = me.x;
    int meY = me.y;

    // BYTECODE OPT: Amortized placement - resume from last position instead of starting over
    // Total positions: 3 distances × 8 directions = 24
    int totalPositions = 24;
    int startIdx = trapPlacementIndex;

    // Place traps in rings at distance 2, 3, 4 from king (closer for better protection)
    for (int i = 0; i < totalPositions; i++) {
      // BYTECODE OPT: Early exit if too many attempts this turn
      if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) {
        trapPlacementIndex = (startIdx + i) % totalPositions;
        return;
      }
      int idx = (startIdx + i) % totalPositions;
      int dist = 4 - (idx / 8); // 4, 3, 2 (was 5, 4, 3 - now closer to king)
      int d = idx % 8;
      Direction dir = trapDirs[d];
      // BYTECODE OPT: Calculate coordinates first, check bounds before allocation
      int trapX = meX + dir.dx * dist;
      int trapY = meY + dir.dy * dist;
      // Skip out-of-bounds positions without allocating MapLocation
      if (trapX < 0 || trapX >= cachedMapWidth || trapY < 0 || trapY >= cachedMapHeight) continue;
      MapLocation trapLoc = new MapLocation(trapX, trapY);
      trapAttemptsThisTurn++;
      if (rc.canPlaceRatTrap(trapLoc)) {
        rc.placeRatTrap(trapLoc);
        ratTrapCount++;
        trapPlacementIndex = (idx + 1) % totalPositions; // Resume from next position
        // DEBUG: Log trap placement (guarded for bytecode savings)
        if (Debug7.ENABLED) {
          logTrapPlaced(cachedRound, rc.getID(), "RAT_TRAP", trapX, trapY, dist, ratTrapCount);
        }
        return;
      }
    }
    // No trap placed - caller will track consecutive failures
    // BYTECODE OPT: Don't reset index - continue scanning different positions each turn
  }

  /**
   * Fallback LINE trap layout when RING layout fails due to map constraints. Places traps in a line
   * toward the enemy king, which works better on constrained maps.
   */
  private static void placeRatTrapsLine(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!rc.isActionReady()) return;

    // Determine direction toward enemy
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    // BYTECODE OPT: Use pre-cached perpendicular directions
    Direction perpLeft = PERP_LEFT_BY_DIR[toEnemy.ordinal()];
    Direction perpRight = PERP_RIGHT_BY_DIR[toEnemy.ordinal()];

    // BYTECODE OPT: Cache king position
    int meX = me.x;
    int meY = me.y;

    // Try distances 2-5 toward enemy, with perpendicular offset 0-2 (closer traps)
    // BYTECODE OPT: Use backward loops
    for (int dist = 5; dist >= 2; --dist) {
      for (int offset = 2; offset >= 0; --offset) {
        // BYTECODE OPT: Early exit if too many attempts this turn
        if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
        int trapX, trapY;
        // Center position (offset == 0)
        if (offset == 0) {
          trapX = meX + toEnemy.dx * dist;
          trapY = meY + toEnemy.dy * dist;
          if (trapX >= 0 && trapX < cachedMapWidth && trapY >= 0 && trapY < cachedMapHeight) {
            MapLocation trapLoc = new MapLocation(trapX, trapY);
            trapAttemptsThisTurn++;
            if (rc.canPlaceRatTrap(trapLoc)) {
              rc.placeRatTrap(trapLoc);
              ratTrapCount++;
              if (Debug7.ENABLED) {
                logTrapPlaced(
                    cachedRound, rc.getID(), "RAT_TRAP_LINE", trapX, trapY, dist, ratTrapCount);
              }
              return;
            }
          }
        } else {
          // Left offset
          trapX = meX + toEnemy.dx * dist + perpLeft.dx * offset;
          trapY = meY + toEnemy.dy * dist + perpLeft.dy * offset;
          if (trapX >= 0 && trapX < cachedMapWidth && trapY >= 0 && trapY < cachedMapHeight) {
            MapLocation leftLoc = new MapLocation(trapX, trapY);
            trapAttemptsThisTurn++;
            if (rc.canPlaceRatTrap(leftLoc)) {
              rc.placeRatTrap(leftLoc);
              ratTrapCount++;
              if (Debug7.ENABLED) {
                logTrapPlaced(
                    cachedRound, rc.getID(), "RAT_TRAP_LINE", trapX, trapY, dist, ratTrapCount);
              }
              return;
            }
          }
          // BYTECODE OPT: Early exit if too many attempts
          if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
          // Right offset
          trapX = meX + toEnemy.dx * dist + perpRight.dx * offset;
          trapY = meY + toEnemy.dy * dist + perpRight.dy * offset;
          if (trapX >= 0 && trapX < cachedMapWidth && trapY >= 0 && trapY < cachedMapHeight) {
            MapLocation rightLoc = new MapLocation(trapX, trapY);
            trapAttemptsThisTurn++;
            if (rc.canPlaceRatTrap(rightLoc)) {
              rc.placeRatTrap(rightLoc);
              ratTrapCount++;
              if (Debug7.ENABLED) {
                logTrapPlaced(
                    cachedRound, rc.getID(), "RAT_TRAP_LINE", trapX, trapY, dist, ratTrapCount);
              }
              return;
            }
          }
        }
      }
    }

    // Try diagonal directions as last resort
    // BYTECODE OPT: Use pre-cached diagonal directions instead of 4 rotations per call
    Direction[] diagDirs = DIAG_DIRS_BY_ENEMY[toEnemy.ordinal()];
    // BYTECODE OPT: Use backward loop for directions
    for (int di = 4; --di >= 0; ) {
      // BYTECODE OPT: Early exit if too many attempts this turn
      if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
      Direction diagDir = diagDirs[di];
      for (int dist = 2; dist <= 4; dist++) {
        int trapX = meX + diagDir.dx * dist;
        int trapY = meY + diagDir.dy * dist;
        if (trapX < 0 || trapX >= cachedMapWidth || trapY < 0 || trapY >= cachedMapHeight) continue;
        MapLocation trapLoc = new MapLocation(trapX, trapY);
        trapAttemptsThisTurn++;
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          consecutiveLineFailures = 0; // Reset on success
          if (Debug7.ENABLED) {
            logTrapPlaced(
                cachedRound, rc.getID(), "RAT_TRAP_DIAG", trapLoc.x, trapLoc.y, dist, ratTrapCount);
          }
          return;
        }
      }
    }
    // LINE layout failed - track failures and potentially switch to FALLBACK
    consecutiveLineFailures++;
    if (consecutiveLineFailures >= 10 && !useFallbackLayout) {
      useFallbackLayout = true;
      logLayoutSwitch(cachedRound, rc.getID(), "LINE", "FALLBACK", consecutiveLineFailures);
    }
  }

  /**
   * Fix 1: FALLBACK trap placement - try ANY valid position when standard layouts fail. This is the
   * last resort when both RING and LINE layouts are blocked by map terrain. FIX 9: Start from
   * distance 2 (closer traps) for better coverage on constrained maps.
   */
  /**
   * EMERGENCY trap placement when under attack (threat >= 2). Places traps at distance 1-2 directly
   * toward the enemy for immediate protection. Prioritizes blocking the enemy approach path.
   */
  private static void placeEmergencyTraps(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!rc.isActionReady()) return;
    if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;

    int meX = me.x;
    int meY = me.y;

    // Determine direction toward enemy
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    // Emergency: place at distance 1-2 toward enemy and adjacent directions
    Direction[] emergencyDirs = {
      toEnemy,
      toEnemy.rotateLeft(),
      toEnemy.rotateRight(),
      toEnemy.rotateLeft().rotateLeft(),
      toEnemy.rotateRight().rotateRight()
    };

    // Try distance 2 first (immediate blocking), then distance 1
    for (int dist = 2; dist >= 1; --dist) {
      for (Direction dir : emergencyDirs) {
        if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
        int trapX = meX + dir.dx * dist;
        int trapY = meY + dir.dy * dist;
        if (trapX < 0 || trapX >= cachedMapWidth || trapY < 0 || trapY >= cachedMapHeight) continue;
        MapLocation trapLoc = new MapLocation(trapX, trapY);
        trapAttemptsThisTurn++;
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          if (Debug7.ENABLED) {
            System.out.println(
                "EMERGENCY_TRAP:"
                    + cachedRound
                    + ":"
                    + rc.getID()
                    + ":pos="
                    + trapLoc
                    + ":dist="
                    + dist);
          }
          return;
        }
      }
    }

    // Fallback: try any direction at distance 2-3
    for (int dist = 2; dist <= 3; dist++) {
      for (int d = 8; --d >= 0; ) {
        if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
        Direction dir = DIRECTIONS[d];
        int trapX = meX + dir.dx * dist;
        int trapY = meY + dir.dy * dist;
        if (trapX < 0 || trapX >= cachedMapWidth || trapY < 0 || trapY >= cachedMapHeight) continue;
        MapLocation trapLoc = new MapLocation(trapX, trapY);
        trapAttemptsThisTurn++;
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          if (Debug7.ENABLED) {
            System.out.println(
                "EMERGENCY_TRAP:"
                    + cachedRound
                    + ":"
                    + rc.getID()
                    + ":pos="
                    + trapLoc
                    + ":dist="
                    + dist
                    + ":fallback");
          }
          return;
        }
      }
    }
  }

  private static void placeRatTrapsFallback(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!rc.isActionReady()) return;
    // BYTECODE OPT: Early exit if too many attempts this turn
    if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;

    // BYTECODE OPT: Cache king position
    int meX = me.x;
    int meY = me.y;

    // Try ALL positions in a spiral outward from king (distances 2-6)
    // BYTECODE OPT: Use backward loops
    for (int dist = 6; dist >= 2; --dist) {
      // Check all 8 directions at this distance
      for (int d = 7; d >= 0; --d) {
        // BYTECODE OPT: Early exit if too many attempts this turn
        if (trapAttemptsThisTurn >= MAX_TRAP_ATTEMPTS_PER_TURN) return;
        Direction dir = DIRECTIONS[d];
        int trapX = meX + dir.dx * dist;
        int trapY = meY + dir.dy * dist;
        // BYTECODE OPT: Bounds check before allocation
        if (trapX >= 0 && trapX < cachedMapWidth && trapY >= 0 && trapY < cachedMapHeight) {
          MapLocation target = new MapLocation(trapX, trapY);
          trapAttemptsThisTurn++;
          if (rc.canPlaceRatTrap(target)) {
            rc.placeRatTrap(target);
            ratTrapCount++;
            trapPlacementStalled = false; // Success!
            if (Debug7.ENABLED) {
              logTrapPlaced(
                  cachedRound, rc.getID(), "RAT_TRAP_FALLBACK", trapX, trapY, dist, ratTrapCount);
            }
            return;
          }
        }
        // BYTECODE OPT: Simplified offset check - only try 4 cardinal offsets instead of 8
        // This reduces iterations while still providing good coverage
        for (int o = 3; o >= 0; --o) {
          int ox = (o == 0 || o == 2) ? 1 : -1;
          int oy = (o == 0 || o == 1) ? 1 : -1;
          int offX = trapX + ox;
          int offY = trapY + oy;
          if (offX >= 0 && offX < cachedMapWidth && offY >= 0 && offY < cachedMapHeight) {
            MapLocation offset = new MapLocation(offX, offY);
            if (rc.canPlaceRatTrap(offset)) {
              rc.placeRatTrap(offset);
              ratTrapCount++;
              trapPlacementStalled = false;
              logTrapPlaced(
                  cachedRound, rc.getID(), "RAT_TRAP_FALLBACK", offX, offY, dist, ratTrapCount);
              return;
            }
          }
        }
      }
    }
    // If we get here, trap placement is truly stalled - no valid positions anywhere
    trapPlacementStalled = true;
    logTrapFailed(cachedRound, rc.getID(), "FALLBACK", ratTrapCount);
  }

  private static void placeCatTraps(RobotController rc, MapLocation me) throws GameActionException {
    if (!rc.isActionReady()) return;

    // Place cat traps in RING pattern like rat traps
    // Cat traps do more damage (100) - place at distance 4 for middle ring coverage

    // Cat traps at distance 4 (middle ring) for maximum impact
    // Cover all 8 directions - BYTECODE OPT: Use backward loop
    for (int d = 8; --d >= 0; ) {
      Direction dir = DIRECTIONS[d];
      MapLocation trapLoc = me.translate(dir.dx * 4, dir.dy * 4);
      if (rc.canPlaceCatTrap(trapLoc)) {
        rc.placeCatTrap(trapLoc);
        catTrapCount++;
        if (Debug7.ENABLED) {
          logTrapPlaced(cachedRound, rc.getID(), "CAT_TRAP", trapLoc.x, trapLoc.y, 4, catTrapCount);
        }
        return;
      }
    }

    // Fallback: try distance 2 and 4 (closer to king)
    for (int dist = 2; dist <= 4; dist += 2) {
      // BYTECODE OPT: Use backward loop for directions
      for (int d = 8; --d >= 0; ) {
        Direction dir = DIRECTIONS[d];
        MapLocation trapLoc = me.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapCount++;
          if (Debug7.ENABLED) {
            logTrapPlaced(
                cachedRound, rc.getID(), "CAT_TRAP", trapLoc.x, trapLoc.y, dist, catTrapCount);
          }
          return;
        }
      }
    }
  }

  /**
   * Place dirt walls to create defensive corridors that funnel enemies through traps. Walls are
   * placed perpendicular to the enemy direction, creating chokepoints. Note: Walls are placed off
   * to the sides to avoid blocking the direct path to enemy king.
   */
  // ========================================================================
  // STRATEGIC DIRT PLACEMENT SYSTEM
  // Creates kill corridors, flank protection, and reactive defenses
  // ========================================================================

  /**
   * Orchestrates all strategic dirt placement. Priority order: 1. Reactive walls (urgent - respond
   * to unexpected threat direction) 2. Funnel walls (beside traps to create kill corridors) 3.
   * Flank protection (L-shaped close walls) 4. Dig for stockpile (acquire dirt resource)
   */
  private static void placeStrategicDirt(
      RobotController rc, MapLocation me, int round, int threatLevel) throws GameActionException {
    if (!rc.isActionReady()) return;
    // BYTECODE OPT: Skip on small maps where walls are less useful
    if (cachedMapWidth <= SMALL_MAP_THRESHOLD && cachedMapHeight <= SMALL_MAP_THRESHOLD) return;

    // Determine direction toward enemy
    Direction toEnemy = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }

    // 1. REACTIVE WALLS - urgent response to unexpected threats
    if (threatLevel >= REACTIVE_THREAT_THRESHOLD && reactiveWallCount < REACTIVE_WALL_MAX) {
      if (placeReactiveWalls(rc, me, toEnemy)) return;
    }

    // 2. FUNNEL WALLS - create kill corridors beside trap line
    // Only place after we have some traps and enough dirt
    if (funnelWallCount < FUNNEL_WALL_TARGET
        && ratTrapCount >= 2
        && rc.getDirt() >= DIRT_STOCKPILE_MIN) {
      if (placeFunnelWalls(rc, me, toEnemy)) return;
    }

    // 3. FLANK PROTECTION - L-shaped walls close to king
    // Only place after funnels are done and we have enough dirt
    if (flankWallCount < FLANK_WALL_TARGET
        && funnelWallCount >= FUNNEL_WALL_TARGET
        && rc.getDirt() >= DIRT_STOCKPILE_MIN) {
      if (placeFlankProtection(rc, me, toEnemy)) return;
    }

    // 4. DIG FOR STOCKPILE - acquire dirt resource when safe
    // Only dig in early game and when we have low stockpile
    if (round <= DIRT_DIG_WINDOW_END && rc.getDirt() < DIRT_STOCKPILE_MAX && threatLevel == 0) {
      tryDigDirtForStockpile(rc, me);
    }
  }

  /**
   * Dig existing dirt to acquire dirt resource for strategic placement. Scans vision for dirt tiles
   * and digs them.
   */
  private static void tryDigDirtForStockpile(RobotController rc, MapLocation me)
      throws GameActionException {
    if (!rc.isActionReady()) return;
    if (rc.getDirt() >= DIRT_STOCKPILE_MAX) return;

    // Scan nearby tiles for existing dirt to dig
    MapInfo[] nearby = rc.senseNearbyMapInfos(8);
    for (int i = nearby.length; --i >= 0; ) {
      MapInfo info = nearby[i];
      MapLocation loc = info.getMapLocation();
      if (info.isDirt() && rc.canRemoveDirt(loc)) {
        rc.removeDirt(loc);
        if (Debug7.ENABLED) {
          System.out.printf(
              "[R%d][%d] DIRT_DUG at (%d,%d) stockpile=%d%n",
              cachedRound, rc.getID(), loc.x, loc.y, rc.getDirt());
        }
        return;
      }
    }
  }

  /**
   * Place funnel walls beside the trap line to create kill corridors. Enemies are funneled through
   * the trap zone.
   */
  private static boolean placeFunnelWalls(RobotController rc, MapLocation me, Direction toEnemy)
      throws GameActionException {
    if (!rc.isActionReady()) return false;
    if (rc.getDirt() <= 0) return false;

    int enemyOrdinal = toEnemy.ordinal();
    Direction perpLeft = PERP_LEFT_BY_DIR[enemyOrdinal];
    Direction perpRight = PERP_RIGHT_BY_DIR[enemyOrdinal];

    // Place funnel walls at distance 3-4 toward enemy, offset by FUNNEL_OFFSET perpendicular
    for (int dist = 3; dist <= 4; dist++) {
      MapLocation funnelLeft =
          me.translate(
              toEnemy.dx * dist + perpLeft.dx * FUNNEL_OFFSET,
              toEnemy.dy * dist + perpLeft.dy * FUNNEL_OFFSET);
      if (rc.canPlaceDirt(funnelLeft)) {
        rc.placeDirt(funnelLeft);
        funnelWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(
              cachedRound, rc.getID(), funnelLeft.x, funnelLeft.y, "FUNNEL_L", funnelWallCount);
        }
        return true;
      }

      MapLocation funnelRight =
          me.translate(
              toEnemy.dx * dist + perpRight.dx * FUNNEL_OFFSET,
              toEnemy.dy * dist + perpRight.dy * FUNNEL_OFFSET);
      if (rc.canPlaceDirt(funnelRight)) {
        rc.placeDirt(funnelRight);
        funnelWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(
              cachedRound, rc.getID(), funnelRight.x, funnelRight.y, "FUNNEL_R", funnelWallCount);
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Place L-shaped flank protection walls close to king. Prevents enemies from flanking around the
   * trap line.
   */
  private static boolean placeFlankProtection(RobotController rc, MapLocation me, Direction toEnemy)
      throws GameActionException {
    if (!rc.isActionReady()) return false;
    if (rc.getDirt() <= 0) return false;

    int enemyOrdinal = toEnemy.ordinal();
    Direction perpLeft = PERP_LEFT_BY_DIR[enemyOrdinal];
    Direction perpRight = PERP_RIGHT_BY_DIR[enemyOrdinal];
    Direction back = toEnemy.opposite();

    // L-shape positions: side walls at dist 2-3, back corners
    MapLocation[] leftFlank = {
      me.translate(perpLeft.dx * FLANK_DIST, perpLeft.dy * FLANK_DIST),
      me.translate(perpLeft.dx * FLANK_DIST + back.dx, perpLeft.dy * FLANK_DIST + back.dy),
      me.translate(perpLeft.dx * (FLANK_DIST + 1), perpLeft.dy * (FLANK_DIST + 1))
    };

    MapLocation[] rightFlank = {
      me.translate(perpRight.dx * FLANK_DIST, perpRight.dy * FLANK_DIST),
      me.translate(perpRight.dx * FLANK_DIST + back.dx, perpRight.dy * FLANK_DIST + back.dy),
      me.translate(perpRight.dx * (FLANK_DIST + 1), perpRight.dy * (FLANK_DIST + 1))
    };

    for (int i = 0; i < leftFlank.length; i++) {
      MapLocation loc = leftFlank[i];
      if (rc.canPlaceDirt(loc)) {
        rc.placeDirt(loc);
        flankWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(cachedRound, rc.getID(), loc.x, loc.y, "FLANK_L", flankWallCount);
        }
        return true;
      }
    }

    for (int i = 0; i < rightFlank.length; i++) {
      MapLocation loc = rightFlank[i];
      if (rc.canPlaceDirt(loc)) {
        rc.placeDirt(loc);
        flankWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(cachedRound, rc.getID(), loc.x, loc.y, "FLANK_R", flankWallCount);
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Place reactive walls when enemies approach from unexpected direction. Detects threat direction
   * and places blocking dirt if it differs from expected.
   */
  private static boolean placeReactiveWalls(RobotController rc, MapLocation me, Direction toEnemy)
      throws GameActionException {
    if (!rc.isActionReady()) return false;
    if (rc.getDirt() <= 0) return false;
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    if (enemies.length < REACTIVE_THREAT_THRESHOLD) return false;

    // Calculate average enemy direction
    int totalDx = 0, totalDy = 0;
    int threatCount = 0;
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() != UnitType.RAT_KING) { // Only count baby rats
        MapLocation enemyLoc = enemy.getLocation();
        totalDx += enemyLoc.x - me.x;
        totalDy += enemyLoc.y - me.y;
        threatCount++;
      }
    }
    if (threatCount < REACTIVE_THREAT_THRESHOLD) return false;

    // BYTECODE OPT: Use integer-based direction calculation instead of atan2
    // Normalize dx/dy to -1/0/1 range for direction lookup
    int ndx = (totalDx > 0) ? 1 : (totalDx < 0) ? -1 : 0;
    int ndy = (totalDy > 0) ? 1 : (totalDy < 0) ? -1 : 0;
    if (ndx == 0 && ndy == 0) return false;
    Direction threatDir = me.directionTo(me.translate(ndx, ndy));

    // Check if threat direction differs significantly from expected enemy direction
    if (threatDir == toEnemy
        || threatDir == toEnemy.rotateLeft()
        || threatDir == toEnemy.rotateRight()) {
      return false;
    }

    // Place blocking dirt toward the unexpected threat direction
    lastThreatDirection = threatDir;
    Direction perpLeft = threatDir.rotateLeft().rotateLeft();
    Direction perpRight = threatDir.rotateRight().rotateRight();

    for (int dist = 2; dist <= 3; dist++) {
      MapLocation blockLeft =
          me.translate(threatDir.dx * dist + perpLeft.dx, threatDir.dy * dist + perpLeft.dy);
      if (rc.canPlaceDirt(blockLeft)) {
        rc.placeDirt(blockLeft);
        reactiveWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(
              cachedRound, rc.getID(), blockLeft.x, blockLeft.y, "REACTIVE", reactiveWallCount);
        }
        return true;
      }

      MapLocation blockRight =
          me.translate(threatDir.dx * dist + perpRight.dx, threatDir.dy * dist + perpRight.dy);
      if (rc.canPlaceDirt(blockRight)) {
        rc.placeDirt(blockRight);
        reactiveWallCount++;
        dirtWallCount++;
        if (Debug7.ENABLED) {
          logWallPlaced(
              cachedRound, rc.getID(), blockRight.x, blockRight.y, "REACTIVE", reactiveWallCount);
        }
        return true;
      }
    }
    return false;
  }

  /**
   * Check if a location is on or near the direct path between two points. Used to avoid placing
   * dirt walls that block friendly movement.
   */
  private static boolean isOnDirectPath(MapLocation from, MapLocation to, MapLocation check) {
    if (from == null || to == null || check == null) return false;

    // Calculate perpendicular distance from check to the line from->to
    // Using simplified approach: check if the location is within 1 tile of the direct line
    int dx = to.x - from.x;
    int dy = to.y - from.y;

    // Avoid division by zero
    if (dx == 0 && dy == 0) return check.equals(from);

    // Project check point onto the line and see if it's close
    // Simplified: just check if moving from 'from' toward 'to' would pass near 'check'
    Direction toTarget = from.directionTo(to);
    Direction toCheck = from.directionTo(check);

    // If check is in roughly the same direction as target and close, it's on the path
    if (toTarget == toCheck
        || toTarget == toCheck.rotateLeft()
        || toTarget == toCheck.rotateRight()) {
      int distToCheck = from.distanceSquaredTo(check);
      int distToTarget = from.distanceSquaredTo(to);
      // Only consider it "on path" if check is between from and to
      return distToCheck < distToTarget;
    }

    return false;
  }

  private static RobotInfo findDangerousCat(RobotInfo[] neutrals) {
    for (int i = neutrals.length; --i >= 0; ) {
      if (neutrals[i].getType() == UnitType.CAT) {
        return neutrals[i];
      }
    }
    return null;
  }

  private static boolean kingFleeFromCat(RobotController rc, MapLocation me, MapLocation catLoc)
      throws GameActionException {
    if (!rc.isMovementReady()) return false;

    Direction awayFromCat = catLoc.directionTo(me);

    if (rc.canMove(awayFromCat)) {
      rc.move(awayFromCat);
      return true;
    }

    Direction left = awayFromCat.rotateLeft();
    Direction right = awayFromCat.rotateRight();

    if (rc.canMove(left)) {
      rc.move(left);
      return true;
    }
    if (rc.canMove(right)) {
      rc.move(right);
      return true;
    }

    return false;
  }

  private static void evadeFromEnemies(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
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

    tryKingMove(rc, awayDir);
  }

  private static boolean tryKingMove(RobotController rc, Direction dir) throws GameActionException {
    if (!rc.isMovementReady() || dir == null || dir == Direction.CENTER) return false;

    MapLocation me = rc.getLocation();
    MapLocation target = me.add(dir);

    if (rc.canSenseLocation(target)) {
      MapInfo info = rc.senseMapInfo(target);
      if (info.getTrap() == TrapType.RAT_TRAP) {
        return false;
      }
    }

    if (rc.canMove(dir)) {
      rc.move(dir);
      return true;
    }

    Direction left = dir.rotateLeft();
    Direction right = dir.rotateRight();

    if (rc.canMove(left)) {
      rc.move(left);
      return true;
    }
    if (rc.canMove(right)) {
      rc.move(right);
      return true;
    }

    return false;
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

  /**
   * Calculate and broadcast blocking line position for body blocking defense. When enemies approach
   * the king, we calculate a line of positions perpendicular to the attack vector where rats can
   * form a shield wall.
   */
  private static void updateBlockingLine(
      RobotController rc, MapLocation me, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    // Clear blocking line if not enough enemies
    // Note: We store coordinates + 1 so that (0,0) is a valid position
    // Array value 0 = not set, value 1 = coordinate 0, etc.
    if (enemyCount < BODY_BLOCK_MIN_ENEMIES) {
      rc.writeSharedArray(SLOT_BLOCK_LINE_X, 0);
      rc.writeSharedArray(SLOT_BLOCK_LINE_Y, 0);
      rc.writeSharedArray(SLOT_BLOCK_PERP, 0);
      return;
    }

    // Calculate enemy center of mass
    int sumX = 0, sumY = 0;
    int closestDistSq = Integer.MAX_VALUE;
    for (int i = enemyCount; --i >= 0; ) {
      MapLocation loc = enemies[i].getLocation();
      sumX += loc.x;
      sumY += loc.y;
      // BYTECODE OPT: Inline distance calculation
      int edx = me.x - loc.x;
      int edy = me.y - loc.y;
      int distSq = edx * edx + edy * edy;
      if (distSq < closestDistSq) closestDistSq = distSq;
    }

    // Only block if enemies are within trigger distance
    if (closestDistSq > BODY_BLOCK_TRIGGER_DIST_SQ) {
      rc.writeSharedArray(SLOT_BLOCK_LINE_X, 0);
      rc.writeSharedArray(SLOT_BLOCK_LINE_Y, 0);
      rc.writeSharedArray(SLOT_BLOCK_PERP, 0);
      return;
    }

    int enemyCenterX = sumX / enemyCount;
    int enemyCenterY = sumY / enemyCount;

    // Calculate attack vector (king -> enemy center)
    int dx = enemyCenterX - me.x;
    int dy = enemyCenterY - me.y;
    Direction attackDir = directionFromDelta(dx, dy);
    if (attackDir == Direction.CENTER) attackDir = Direction.NORTH;

    // FIX 1: CAP blocking line distance to prevent blockers from traveling too far
    // Block line should be VERY close to king (max 4 tiles) so blockers reach positions quickly
    // BYTECODE OPT: Use squared distance thresholds instead of intSqrt
    // distSq < 36 (dist < 6) -> blockDist = 2
    // distSq < 81 (dist < 9) -> blockDist = 3
    // distSq >= 81 -> blockDist = 4
    int effectiveBlockDist = closestDistSq < 36 ? 2 : (closestDistSq < 81 ? 3 : 4);
    // Also cap at adaptive bodyBlockLineDist
    effectiveBlockDist =
        effectiveBlockDist < bodyBlockLineDist ? effectiveBlockDist : bodyBlockLineDist;

    // Blocking line center is effectiveBlockDist tiles toward enemy
    int lineCenterX = me.x + attackDir.dx * effectiveBlockDist;
    int lineCenterY = me.y + attackDir.dy * effectiveBlockDist;

    // Clamp to map bounds - BYTECODE OPT: Use ternary instead of Math.max/min
    lineCenterX =
        lineCenterX < 0 ? 0 : (lineCenterX >= cachedMapWidth ? cachedMapWidth - 1 : lineCenterX);
    lineCenterY =
        lineCenterY < 0 ? 0 : (lineCenterY >= cachedMapHeight ? cachedMapHeight - 1 : lineCenterY);

    // Perpendicular direction for spreading blockers along the line
    // BYTECODE OPT: Use pre-cached lookup instead of rotateLeft().rotateLeft()
    Direction perpDir = PERP_LEFT_BY_DIR[attackDir.ordinal()];

    // Broadcast blocking line (store coordinates + 1 to allow (0,0) as valid position)
    rc.writeSharedArray(SLOT_BLOCK_LINE_X, lineCenterX + 1);
    rc.writeSharedArray(SLOT_BLOCK_LINE_Y, lineCenterY + 1);
    // Store perpDir ordinal + 1 to distinguish from "not set" (0)
    rc.writeSharedArray(SLOT_BLOCK_PERP, perpDir.ordinal() + 1);

    // DEBUG: Log and visualize block line
    // BYTECODE OPT: Guard MapLocation allocation and logging with ENABLED check
    if (Debug7.ENABLED) {
      MapLocation lineCenter = new MapLocation(lineCenterX, lineCenterY);
      logBlockLine(
          rc.getRoundNum(),
          rc.getID(),
          lineCenterX,
          lineCenterY,
          perpDir.name(),
          enemyCount,
          closestDistSq); // BYTECODE OPT: Pass squared dist, avoid intSqrt
      visualizeBlockLine(rc, lineCenter, perpDir, BODY_BLOCK_SLOTS);
    }
  }

  /**
   * Read squeaks from baby rats and return the threat count. Must be called BEFORE threat level
   * calculation so squeak-derived threat is included.
   *
   * @param rc RobotController
   * @param id King's robot ID (passed to avoid rc.getID() calls in hot path)
   * @return Number of enemies reported via squeaks
   */
  private static int kingReadSqueaks(RobotController rc, int id) throws GameActionException {
    Message[] squeaks = rc.readSqueaks(-1);
    int len = squeaks.length;
    if (len == 0) return 0;

    // BYTECODE OPT: Use ternary instead of Math.min
    int limit = len < MAX_SQUEAKS_TO_READ ? len : MAX_SQUEAKS_TO_READ;
    int round = cachedRound;
    int threatCount = 0; // Count threat squeaks for preemptive defense

    for (int i = len - 1; i >= len - limit; --i) {
      int content = squeaks[i].getBytes();
      int type = (content >> 28) & 0xF;

      if (type == SQUEAK_TYPE_ENEMY_KING) {
        int y = (content >> 16) & 0xFFF;
        int x = (content >> 4) & 0xFFF;
        int hpBits = content & 0xF;
        int hp = hpBits * 35;

        rc.writeSharedArray(SLOT_ENEMY_KING_X, x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, y);
        // 6-bit: divide HP by 8, max 63 -> range 0-504 (king max HP is 500)
        // BYTECODE OPT: Use ternary instead of Math.min
        int hpScaled = hp >> 3;
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, hpScaled < 63 ? hpScaled : 63);
        rc.writeSharedArray(SLOT_ENEMY_KING_ROUND, round);

        if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
          cachedEnemyKingLoc = new MapLocation(x, y);
        }
        enemyKingConfirmed = true;
      } else if (type == SQUEAK_TYPE_ENEMY_SPOTTED) {
        // EARLY WARNING: Baby rat saw enemies!
        // Extract enemy count from squeak (stored in lower bits)
        int enemyCount = content & 0xF;
        threatCount += enemyCount;

        // COUNTER-RUSH DETECTION: Enemies before round 30 = rush detected!
        if (round < COUNTER_RUSH_ROUND && !rushDetected) {
          rushDetected = true;
          // BYTECODE OPT: Guard debug log with ENABLED check
          if (ENABLED) {
            logRushDetected(round, id, enemyCount);
          }
          // Force defense phase immediately
          rc.writeSharedArray(SLOT_PHASE, PHASE_DEFENSE);
          cachedPhase = PHASE_DEFENSE;
        }
        // BYTECODE OPT: Guard debug log with ENABLED check
        // Skip coordinate extraction when not logging
        if (ENABLED) {
          int squeakX = (content >> 4) & 0xFFF;
          int squeakY = (content >> 16) & 0xFFF;
          logSqueakReceived(round, id, "ENEMY_SPOTTED", squeakX, squeakY, enemyCount);
        }
      }
    }

    // Update cached squeak threat with decay logic
    if (threatCount > 0) {
      // Fresh squeak threat received - update cache and timestamp
      cachedSqueakThreat = Math.min(threatCount, MAX_SQUEAK_THREAT);
      lastSqueakThreatRound = round;
    } else if (round - lastSqueakThreatRound >= SQUEAK_THREAT_DECAY_ROUNDS
        && cachedSqueakThreat > 0) {
      // No new squeaks and stale - decay by 1 per round
      cachedSqueakThreat = Math.max(0, cachedSqueakThreat - 1);
    }
    // Return cached squeak threat (with decay applied)
    return cachedSqueakThreat;
  }

  private static void broadcastEnemyKing(RobotController rc, RobotInfo[] enemies)
      throws GameActionException {
    for (int i = enemies.length; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation loc = enemy.getLocation();
        rc.writeSharedArray(SLOT_ENEMY_KING_X, loc.x);
        rc.writeSharedArray(SLOT_ENEMY_KING_Y, loc.y);
        // 6-bit: divide HP by 8, max 63 -> range 0-504 (king max HP is 500)
        rc.writeSharedArray(SLOT_ENEMY_KING_HP, Math.min(enemy.getHealth() >> 3, 63));
        rc.writeSharedArray(SLOT_ENEMY_KING_ROUND, cachedRound);
        enemyKingConfirmed = true;
        return;
      }
    }
  }

  // ========================================================================
  // BABY RAT BEHAVIOR
  // ========================================================================

  private static void runBabyRat(RobotController rc) throws GameActionException {
    if (rc.isBeingThrown() || rc.isBeingCarried()) {
      return;
    }

    // BYTECODE: Start tracking at beginning of turn
    startBytecodeTracking();

    updateGameState(rc);

    int id = rc.getID();
    int myHP = rc.getHealth();

    // BYTECODE: Log after updateGameState (id now available)
    logBytecode(cachedRound, id, "START");

    // BYTECODE OPT: Pre-compute all role hashes once per turn
    cachedRolePercent = id % 100;
    cachedShieldHash = (id >> 4) % 100;
    cachedDirSlot = (id >> 2) % 10;
    cachedSlotMod5 = id % 5;
    cachedBlockSlot = (id >> 4) % 10;

    // Calculate role ONCE here - used for role-based behavior throughout
    // Role 0 = Gatherer (55%), Role 1 = Sentry (15%), Role 2 = Scout (5%)
    // Role 3 = Guardian (15%), Role 4 = Interceptor (10%)
    // BYTECODE OPT: Inline role calculation using pre-computed hash
    int role;
    if (cachedRolePercent < GATHERER_THRESHOLD) {
      role = 0; // Gatherer
    } else if (cachedRolePercent < SENTRY_THRESHOLD) {
      role = 1; // Sentry
    } else if (cachedRolePercent < GUARDIAN_THRESHOLD) {
      role = 3; // Guardian - inner defense ring
    } else if (cachedRolePercent < INTERCEPTOR_THRESHOLD) {
      role = 4; // Interceptor - mid-map patrol
    } else {
      role = 2; // Scout
    }

    // BYTECODE OPT: Batch shared array reads at start of turn
    cachedFocusTargetId = rc.readSharedArray(SLOT_FOCUS_TARGET);
    cachedSafeRounds = rc.readSharedArray(SLOT_SAFE_ROUNDS);
    cachedDefenderCount = rc.readSharedArray(SLOT_DEFENDER_COUNT);
    int kingCheeseScaled = rc.readSharedArray(SLOT_KING_CHEESE);
    cachedKingCheese = kingCheeseScaled << 2; // Scale back up (multiply by 4)
    economyRecoveryMode = rc.readSharedArray(SLOT_ECONOMY_RECOVERY) == 1;
    cachedKingHP = rc.readSharedArray(SLOT_KING_HP) * 10; // Scale back up (multiply by 10)

    // Read starvation prediction from king
    predictedStarvationRounds = rc.readSharedArray(SLOT_STARVATION_PREDICT);
    starvationWarningActive = predictedStarvationRounds <= STARVATION_WARNING_ROUNDS;
    starvationCriticalActive = predictedStarvationRounds <= STARVATION_CRITICAL_ROUNDS;

    // BYTECODE OPTIMIZATION: Sense all robots once, then filter into pre-allocated buffers
    // This avoids creating new arrays every turn (major bytecode savings)
    RobotInfo[] allRobots = rc.senseNearbyRobots(-1);
    int allRobotsLen = allRobots.length;

    // Filter into enemies and allies using pre-allocated buffers, check for cats in single pass
    int enemyCount = 0;
    int allyCount = 0;
    MapLocation nearestCatLoc = null;
    int nearestCatDistSq = Integer.MAX_VALUE;

    for (int i = allRobotsLen; --i >= 0; ) {
      RobotInfo robot = allRobots[i];
      Team robotTeam = robot.getTeam();
      if (robotTeam == cachedEnemyTeam) {
        enemyBuffer[enemyCount++] = robot;
      } else if (robotTeam == cachedOurTeam) {
        allyBuffer[allyCount++] = robot;
      } else if (robot.getType() == UnitType.CAT) {
        // Neutral cat - track nearest for flee logic
        MapLocation catLoc = robot.getLocation();
        int catDx = myLocX - catLoc.x;
        int catDy = myLocY - catLoc.y;
        int distSq = catDx * catDx + catDy * catDy;
        if (distSq < nearestCatDistSq) {
          nearestCatDistSq = distSq;
          nearestCatLoc = catLoc;
        }
      }
    }

    // Use pre-allocated buffers with counts (no array allocation!)

    // BYTECODE: After sensing/filtering robots
    logBytecode(cachedRound, id, "SENSE");

    findNearbyCheese(rc);

    // BYTECODE: After finding cheese
    logBytecode(cachedRound, id, "CHEESE");

    // =====================================================================
    // CAT AVOIDANCE - Baby rats flee from cats within pounce range!
    // This is critical for survival - cats kill rats instantly
    // BYTECODE OPTIMIZATION: Cat detected in single pass above
    // =====================================================================
    if (nearestCatLoc != null && nearestCatDistSq <= CAT_FLEE_RADIUS_SQ) {
      // CAT NEARBY! Flee in opposite direction
      Direction awayFromCat = nearestCatLoc.directionTo(myLoc);
      if (awayFromCat == Direction.CENTER) awayFromCat = Direction.SOUTH;
      // Try to move away - only return early if we can actually move
      if (cachedMovementReady) {
        // BYTECODE OPT: Direct calculation instead of triple-add allocation
        MapLocation fleeTarget =
            new MapLocation(myLocX + awayFromCat.dx * 3, myLocY + awayFromCat.dy * 3);
        bug2MoveTo(rc, fleeTarget);
        return; // Priority is fleeing, skip everything else
      }
    }

    // EMERGENCY DEFENSE MODE: Tiered response based on threat level
    // FIX: PARTIAL EMERGENCY (1-2 enemies) - only nearby rats respond
    // FIX: FULL EMERGENCY (3+ enemies) - all rats defend
    // This prevents ALL rats from abandoning tasks when just 1 enemy appears
    boolean fullEmergency = cachedThreatLevel >= FULL_EMERGENCY_THRESHOLD;
    boolean partialEmergency = cachedThreatLevel >= PARTIAL_EMERGENCY_THRESHOLD && !fullEmergency;

    // For partial emergency, only nearby rats (within 8 tiles) should respond
    boolean shouldRespondToPartialEmergency =
        partialEmergency
            && cachedDistToKingSq >= 0
            && cachedDistToKingSq <= PARTIAL_EMERGENCY_RADIUS_SQ;

    // Final emergency state: full emergency OR (partial emergency AND nearby)
    boolean emergencyDefense = fullEmergency || shouldRespondToPartialEmergency;

    // FIX #5: WOUNDED RAT RETREAT - rats with HP < 40 retreat to king for protection
    if (myHP < WOUNDED_HP_THRESHOLD
        && hasOurKingLoc
        && cachedDistToKingSq > WOUNDED_RETREAT_RADIUS_SQ) {
      // Wounded and far from king - retreat immediately!
      if (Debug7.ENABLED && (cachedRound % 10) == 0)
        System.out.println(
            "WOUNDED_RETREAT:"
                + cachedRound
                + ":"
                + id
                + ":hp="
                + myHP
                + ":distKing="
                + cachedDistToKingSq);
      tryImmediateAction(rc, enemyBuffer, enemyCount, cachedFocusTargetId);
      bug2MoveTo(rc, cachedOurKingLoc);
      return;
    }

    // BYTECODE OPT: Use pre-read cached focus target
    int focusTargetId = cachedFocusTargetId;

    // =====================================================================
    // DEFENSIVE RETREAT SYSTEM
    // Wounded rats (HP < 50) retreat toward king through trap corridors
    // HP < 30 = critical retreat (always run)
    // HP 30-50 = kiting (retreat if far from home territory)
    // This is a DEFENSIVE behavior - protect wounded rats!
    // BYTECODE OPTIMIZATION: Use cached distance to king
    // =====================================================================
    if (myHP < KITE_TRIGGER_HP && enemyCount > 0 && cachedDistToKingSq >= 0) {

      // Critical HP (< 30): Always retreat to king
      // Medium HP (30-50): Only retreat if outside home territory
      boolean shouldRetreat =
          (myHP < RETREAT_HP_THRESHOLD) || (cachedDistToKingSq > HOME_TERRITORY_RADIUS_SQ);

      if (shouldRetreat && cachedDistToKingSq > DELIVERY_RANGE_SQ) {
        // Retreat through trap corridors - enemies will follow and hit traps!
        // DEBUG: Log retreat decision
        String trigger = (myHP < RETREAT_HP_THRESHOLD) ? "HP_CRITICAL" : "OUTSIDE_HOME";
        // BYTECODE OPT: Pass squared distance, avoid intSqrt
        logRetreat(cachedRound, id, myHP, trigger, cachedDistToKingSq, enemyCount);
        visualizeRetreat(rc, myLoc, cachedOurKingLoc);
        bug2MoveTo(rc, cachedOurKingLoc);
        return;
      }

      // If at king with cheese, deliver it before continuing
      if (cachedCarryingCheese && cachedDistToKingSq <= DELIVERY_RANGE_SQ) {
        tryDeliverCheese(rc);
      }
      // If at king, fall through to defensive behavior
    }

    // =====================================================================
    // GUARDIAN ROLE: Inner defense ring that NEVER leaves king
    // CRITICAL: This MUST execute BEFORE emergency CONVERGE so Guardians
    // don't abandon their posts during COMBAT emergencies!
    // EXCEPTION: During STARVATION emergencies, guardians MUST gather cheese.
    // Holding position while the king starves is useless!
    // =====================================================================
    // Check for starvation emergency FIRST - guardians must gather cheese when king is starving
    boolean starvationEmergency =
        cachedGlobalCheese == 0 && cachedKingHP > 0 && cachedKingHP < MASS_EMERGENCY_KING_HP;
    if (role == 3 && hasOurKingLoc && !starvationEmergency) {
      // Guardian behavior: Stay close to king at all times
      if (cachedDistToKingSq > GUARDIAN_OUTER_DIST_SQ) {
        // Too far from king - return immediately
        if (Debug7.ENABLED) {
          System.out.println(
              "GUARDIAN_RETURN:"
                  + cachedRound
                  + ":"
                  + id
                  + ":distKing="
                  + cachedDistToKingSq
                  + ":returning_to_king");
        }
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
        bug2MoveTo(rc, cachedOurKingLoc);
        return;
      }

      // Attack any enemy within 5 tiles of king (priority target)
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemyBuffer[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          int enemyDistToKing = enemyLoc.distanceSquaredTo(cachedOurKingLoc);
          // SMART INTERCEPT: Only intercept if:
          // 1. Enemy is within attack range of king (4 tiles)
          // 2. Enemy is closer to king than we are (actually bypassing us)
          boolean enemyInRange = enemyDistToKing <= GUARDIAN_ATTACK_RANGE_SQ;
          boolean enemyBypassing = enemyDistToKing < cachedDistToKingSq;
          if (enemyInRange && enemyBypassing) {
            // Enemy is close to king AND bypassing us - intercept!
            boolean attacked = false;
            if (rc.isActionReady() && rc.canAttack(enemyLoc)) {
              rc.attack(enemyLoc);
              attacked = true;
            }
            if (Debug7.ENABLED) {
              System.out.println(
                  "GUARDIAN_INTERCEPT:"
                      + cachedRound
                      + ":"
                      + id
                      + ":enemy="
                      + enemy.getID()
                      + ":enemyHP="
                      + enemy.getHealth()
                      + ":enemyDistKing="
                      + enemyDistToKing
                      + ":myDistKing="
                      + cachedDistToKingSq
                      + ":bypassing=true"
                      + ":attacked="
                      + attacked);
            }
            // Move URGENTLY to intercept position - king is dying!
            bug2MoveToUrgent(rc, enemyLoc);
            return;
          }
        }
      }

      // No immediate threat - patrol around king in guardian ring
      if (cachedDistToKingSq < GUARDIAN_INNER_DIST_SQ) {
        // Too close, move outward slightly
        Direction awayFromKing = cachedOurKingLoc.directionTo(myLoc);
        if (awayFromKing != Direction.CENTER) {
          if (Debug7.ENABLED && (cachedRound % 20) == 0) {
            System.out.println(
                "GUARDIAN_PATROL:"
                    + cachedRound
                    + ":"
                    + id
                    + ":distKing="
                    + cachedDistToKingSq
                    + ":moving_outward:dir="
                    + awayFromKing);
          }
          MapLocation patrolSpot = myLoc.translate(awayFromKing.dx, awayFromKing.dy);
          bug2MoveTo(rc, patrolSpot);
        }
      } else {
        // In guardian ring - holding position
        if (Debug7.ENABLED && (cachedRound % 20) == 0) {
          System.out.println(
              "GUARDIAN_HOLD:"
                  + cachedRound
                  + ":"
                  + id
                  + ":distKing="
                  + cachedDistToKingSq
                  + ":enemies="
                  + enemyCount
                  + ":in_position");
        }
      }
      // Guardian stays in position - don't fall through to other behaviors
      return;
    }

    // =====================================================================
    // FORMATION TIGHTENING - In FULL emergency, ALL rats converge to king!
    // In PARTIAL emergency, only nearby rats converge (others keep gathering)
    // This creates a dense defensive formation that's hard to break through
    // Rats outside the tight formation move toward king immediately
    // This is PURE DEFENSE - protect the king!
    // EXCEPTION: Guardians (role==3) NEVER converge - they're already at king!
    // =====================================================================
    if (fullEmergency && cachedDistToKingSq >= 0 && role != 3) {
      // FULL EMERGENCY: All rats (except Guardians) converge to defend king!
      // DEBUG: Log emergency defense activation
      logEmergencyDefense(cachedRound, id, "FULL_EMERGENCY", cachedThreatLevel);

      // If too far from king, converge immediately!
      if (cachedDistToKingSq > FORMATION_TIGHT_RADIUS_SQ) {
        // DEBUG: Log convergence decision
        logConverge(
            cachedRound,
            id,
            cachedDistToKingSq, // BYTECODE OPT: Pass squared dist, avoid intSqrt
            cachedThreatLevel,
            cachedOurKingLoc.x,
            cachedOurKingLoc.y);
        visualizeRetreat(rc, myLoc, cachedOurKingLoc);

        // Try immediate actions first (attack nearby enemies)
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId); // focusTargetId read above
        // Then move toward king
        bug2MoveTo(rc, cachedOurKingLoc);
        // Squeak threat warning if we see enemies
        if (enemyCount > 0) {
          tryThreatSqueak(rc, enemyCount);
        }
        return; // Skip other behaviors - priority is converging
      }
    } else if (shouldRespondToPartialEmergency && role != 3) {
      // PARTIAL EMERGENCY: Only nearby rats respond
      // Don't log as much to reduce spam for low-threat situations
      if (cachedDistToKingSq > FORMATION_TIGHT_RADIUS_SQ) {
        // Nearby rat converging to defend
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
        bug2MoveTo(rc, cachedOurKingLoc);
        if (enemyCount > 0) {
          tryThreatSqueak(rc, enemyCount);
        }
        return;
      }
      // If already in formation, fall through to normal defensive behavior
    }

    // FIX #2 HIGH: MASS EMERGENCY - when cheese=0 AND king HP < 200, ALL roles act as gatherers
    // This overrides role-specific behavior - king survival is the only priority
    boolean massEmergency =
        cachedGlobalCheese == 0 && cachedKingHP > 0 && cachedKingHP < MASS_EMERGENCY_KING_HP;
    if (massEmergency && hasOurKingLoc) {
      // ALL rats must help gather cheese - ignore role
      if (Debug7.ENABLED && (cachedRound % 10) == 0) {
        System.out.println(
            "MASS_EMERGENCY:"
                + cachedRound
                + ":"
                + id
                + ":role="
                + role
                + ":kingHP="
                + cachedKingHP
                + ":converting_to_gatherer");
      }
      // If carrying cheese, rush to king with urgent movement (ignore traps)
      if (cachedCarryingCheese && cachedDistToKingSq > DELIVERY_RANGE_SQ) {
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
        bug2MoveToUrgent(rc, cachedOurKingLoc);
        return;
      }
      // If not carrying cheese, act as gatherer to find cheese
      // Use gatherer scoring to find nearest cheese
      scoreAllTargetsGatherer(rc, enemyBuffer, enemyCount, true, cachedGlobalCheese);
      if (cachedBestTarget != null) {
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
        bug2MoveToUrgent(rc, cachedBestTarget);
        return;
      }
      // No cheese found - rush to king anyway
      tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
      bug2MoveToUrgent(rc, cachedOurKingLoc);
      return;
    }

    // FIX #4 HIGH: EMERGENCY RECALL - when global cheese is critically low, ALL gatherers return
    // home
    // This MUST come before other behaviors to prevent economy collapse!
    // FIX #1: Use cachedGlobalCheese instead of API call (already cached in updateGameState)
    boolean emergencyRecallActive = cachedGlobalCheese < EMERGENCY_RECALL_CHEESE;
    if (emergencyRecallActive
        && role == 0
        && hasOurKingLoc
        && cachedDistToKingSq > HOME_GUARD_RADIUS_SQ) {
      // EMERGENCY: Global cheese critically low - ALL gatherers rush home!
      if (Debug7.ENABLED && (cachedRound % 10) == 0) {
        System.out.println(
            "GLOBAL_EMERGENCY_RECALL:"
                + cachedRound
                + ":"
                + id
                + ":globalCheese="
                + cachedGlobalCheese
                + ":distKing="
                + cachedDistToKingSq);
      }
      tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
      bug2MoveTo(rc, cachedOurKingLoc);
      return;
    }

    // FIX #2: Cheese crisis delivery rush - if carrying cheese and king needs it, rush to king
    // ENHANCED: Rush when king HP < 300, cheese < 200, or global cheese < 500
    // This ensures cheese gets delivered before critical situation develops
    // FIX #4: Also rush when global cheese is low (emergency recall territory)
    boolean cheeseCrisisDelivery =
        cachedCarryingCheese
            && ((cachedKingHP > 0 && cachedKingHP < 300) // Rush when king HP getting low
                || cachedKingCheese < 200 // Rush when king cheese getting low (was 100)
                || emergencyRecallActive // Rush when global cheese low
                || economyRecoveryMode // Always rush in recovery mode
                || starvationWarningActive // PREDICTIVE: Rush when starvation predicted
                || (cachedCarryingCheese
                    && cachedGlobalCheese < 500)); // FIX: Force delivery when carrying + low global
    if (cheeseCrisisDelivery && hasOurKingLoc && cachedDistToKingSq > DELIVERY_RANGE_SQ) {
      // RUSH delivery during cheese crisis - override all other behaviors
      if (Debug7.ENABLED)
        System.out.println(
            "CRISIS_DELIVER:"
                + cachedRound
                + ":"
                + id
                + ":rushing_to_king:distKing="
                + cachedDistToKingSq
                + ":kingHP="
                + cachedKingHP
                + ":starvationIn="
                + predictedStarvationRounds);
      tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
      // FIX #1 CRITICAL: When king HP is critical (<100), use urgent movement that ignores rat
      // traps
      // The 30 HP trap damage is acceptable if it means saving the king from starvation
      if (cachedKingHP > 0 && cachedKingHP < 100) {
        bug2MoveToUrgent(rc, cachedOurKingLoc);
      } else {
        bug2MoveTo(rc, cachedOurKingLoc);
      }
      return;
    }

    // BYTECODE: After emergency defense checks
    logBytecode(cachedRound, id, "EMERGENCY");

    // =====================================================================
    // INTERCEPTOR ROLE: Mid-map patrol to slow enemy rushes
    // Interceptors patrol between our king and mid-map, engaging enemies early
    // =====================================================================
    if (role == 4 && hasOurKingLoc) {
      // Return to defend if threat is high
      if (cachedThreatLevel >= INTERCEPTOR_RETURN_THREAT) {
        tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);
        bug2MoveTo(rc, cachedOurKingLoc);
        return;
      }

      // Engage any enemies within range with defensive kiting
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemyBuffer[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          int distToEnemy = myLoc.distanceSquaredTo(enemyLoc);
          if (distToEnemy <= INTERCEPTOR_ENGAGE_DIST_SQ) {
            if (runDefensiveKiting(rc, enemy, myLoc, myHP)) {
              return;
            }
          }
        }
      }

      // No enemies - patrol toward mid-map
      MapLocation patrolTarget = getInterceptorPatrolPoint();
      if (patrolTarget != null) {
        bug2MoveTo(rc, patrolTarget);
        return;
      }
    }

    // =====================================================================
    // DEFENDER PURSUIT: Chase enemies that bypass BLOCKLINE toward king
    // Any defender (sentry/guardian) should pursue bypassing enemies
    // =====================================================================
    if ((role == 1 || role == 3) && hasOurKingLoc && enemyCount > 0) {
      RobotInfo bypassingEnemy = findBypassingEnemy(enemyBuffer, enemyCount, myLoc);
      if (bypassingEnemy != null) {
        MapLocation enemyLoc = bypassingEnemy.getLocation();
        MapLocation interceptPoint = getInterceptPoint(enemyLoc, cachedOurKingLoc);
        if (rc.isActionReady() && rc.canAttack(enemyLoc)) {
          rc.attack(enemyLoc);
        }
        bug2MoveToUrgent(rc, interceptPoint); // FIX #4: Urgent pathfinding for bypass intercepts
        return;
      }
    }

    // =====================================================================
    // DEFENSIVE KITING: Sentries kite when engaging enemies
    // =====================================================================
    if (role == 1 && enemyCount > 0) {
      RobotInfo closestEnemy = null;
      int closestDist = Integer.MAX_VALUE;
      for (int i = enemyCount; --i >= 0; ) {
        RobotInfo enemy = enemyBuffer[i];
        if (enemy.getType().isBabyRatType()) {
          int dist = myLoc.distanceSquaredTo(enemy.getLocation());
          if (dist < closestDist) {
            closestDist = dist;
            closestEnemy = enemy;
          }
        }
      }
      if (closestEnemy != null && closestDist <= KITE_ENGAGE_DIST_SQ) {
        if (runDefensiveKiting(rc, closestEnemy, myLoc, myHP)) {
          return;
        }
      }
    }

    // =====================================================================
    // ECONOMY RECOVERY: Sentries/Interceptors become temporary gatherers when recovering
    // Guardians stay as guardians (king protection is critical)
    // FIX: Relaxed threat requirement from ==0 to <=2 to match recovery entry condition
    // =====================================================================
    if (economyRecoveryMode && cachedThreatLevel <= 2 && (role == 1 || role == 4)) {
      role = 0; // Temporarily act as gatherer
      if (Debug7.ENABLED && (cachedRound % 30) == 0)
        System.out.println(
            "RECOVERY_ROLE_SWITCH:"
                + cachedRound
                + ":"
                + id
                + ":fromRole="
                + (cachedRolePercent < SENTRY_THRESHOLD ? 1 : 4)
                + ":toGatherer");
    }

    // =====================================================================
    // BODY BLOCKING - Form shield wall between enemies and king!
    // 50% of rats can be blockers (based on ID hash)
    // Blockers position on a line perpendicular to enemy approach
    // This forces enemies to attack blockers or waste turns going around
    // This is PURE DEFENSE - protect the king!
    // BYTECODE OPTIMIZATION: Only check when threat level is high
    // =====================================================================
    // BYTECODE OPT: Inline shouldBodyBlock check using pre-computed cachedBlockSlot
    if (emergencyDefense && cachedBlockSlot < 7 && cachedOurKingLoc != null) {
      if (tryBodyBlock(rc, enemyBuffer, enemyCount)) {
        return;
      }
    }

    // Role already calculated at start of runBabyRat() - used consistently throughout
    // NOTE: role may have been modified above (sentry->gatherer during economy recovery)

    // DEBUG: Log baby rat state every N rounds
    // BYTECODE OPT: Pass squared distance, avoid intSqrt
    String roleStr;
    switch (role) {
      case 0:
        roleStr = "GATHERER";
        break;
      case 1:
        roleStr = "SENTRY";
        break;
      case 2:
        roleStr = "SCOUT";
        break;
      case 3:
        roleStr = "GUARDIAN";
        break;
      case 4:
        roleStr = "INTERCEPTOR";
        break;
      default:
        roleStr = "UNKNOWN";
        break;
    }
    logBabyRatState(
        cachedRound,
        id,
        roleStr,
        myHP,
        myLocX,
        myLocY,
        targetName(cachedBestTargetType),
        cachedDistToKingSq, // Pass squared distance
        emergencyDefense);

    // All rats attack visible enemy king regardless of role!
    // BYTECODE OPTIMIZATION: Use enemyCount instead of enemies.length
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemyBuffer[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        // Found enemy king - attack mode with ENHANCED ATTACK!
        MapLocation kingLoc = enemy.getLocation();
        if (rc.isActionReady() && rc.canAttack(kingLoc)) {
          // Use enhanced attack - spend cheese for burst damage!
          int cheeseToSpend = Math.min(rc.getRawCheese(), 100);
          if (cheeseToSpend > 10) {
            rc.attack(kingLoc, cheeseToSpend);
          } else {
            rc.attack(kingLoc);
          }
        }
        bug2MoveTo(rc, kingLoc);
        updateEnemyKingFromBabyRat(rc, enemyBuffer, enemyCount);
        return;
      }
    }

    // Try ratnapping weak enemies FIRST (offensive priority)
    // Enemy ratnap is more valuable than defensive rescue in most cases
    if (tryRatnapAndThrow(rc, enemyBuffer, enemyCount)) {
      return;
    }

    // =====================================================================
    // DEFENSIVE RATNAPPING - Rescue wounded allies!
    // Grab wounded allies (HP < 25) and throw them toward king for safety
    // Thrown rats are immune to attacks!
    // BYTECODE OPTIMIZATION: Use pre-allocated allyBuffer (already filtered above)
    // =====================================================================
    if (cachedActionReady) {
      if (tryDefensiveRatnap(rc, allyBuffer, allyCount)) {
        return;
      }
    }

    // If we're carrying a rat but couldn't throw, prioritize moving toward appropriate target
    RobotInfo carryingRat = rc.getCarrying();
    if (carryingRat != null) {
      // Check if carrying ally (defensive rescue) or enemy (offensive throw)
      if (carryingRat.getTeam() == cachedOurTeam) {
        // Carrying wounded ally - move toward king to throw to safety
        if (cachedOurKingLoc != null) {
          bug2MoveTo(rc, cachedOurKingLoc);
          return;
        }
      } else if (cachedEnemyKingLoc != null) {
        // Carrying enemy - move toward enemy king to throw at them
        bug2MoveTo(rc, cachedEnemyKingLoc);
        return;
      }
    }

    // Try immediate actions
    tryImmediateAction(rc, enemyBuffer, enemyCount, focusTargetId);

    // BYTECODE: After immediate actions (attack/deliver/collect)
    logBytecode(cachedRound, id, "ACTION");

    // Fix 5: Use pre-read cached safe rounds to determine if exploration is allowed
    // BYTECODE OPT: Use cached values
    boolean safeToExplore = cachedSafeRounds >= SAFE_EXPLORE_THRESHOLD;

    // FIX 2: Standing defense reserve - check if we need more defenders near king
    // BYTECODE OPT: Use cached value
    boolean needMoreDefenders = cachedDefenderCount < STANDING_DEFENSE_COUNT;
    // FIX 1: Use simpler ID check - first 15 rats (by ID % 100) are potential defenders
    // This is closer to STANDING_DEFENSE_COUNT (10) to reduce oscillation spam
    // BYTECODE OPT: Use pre-computed cachedRolePercent
    boolean isStandingDefender = cachedRolePercent < 15; // ~15% are potential standing defenders
    // FIX 5: Check against STANDING_DEFENSE_RADIUS_SQ (now 36 = 6 tiles)
    // FIX 6: ECONOMY FIX - Disable ATK phase defender lockout!
    // Standing defenders should NOT stay near king during ATK phase - they should attack!
    // Only keep standing defenders during BUILD/DEF phases when we need home defense
    boolean atkPhaseDefender = false; // DISABLED - was causing economy collapse
    boolean shouldStayNearKing =
        atkPhaseDefender
            || (needMoreDefenders
                && isStandingDefender
                && cachedDistToKingSq > STANDING_DEFENSE_RADIUS_SQ);

    // FIX 2: Log when potential standing defender is released from duty
    if (isStandingDefender
        && !needMoreDefenders
        && cachedDistToKingSq <= STANDING_DEFENSE_RADIUS_SQ) {
      logDecision(
          cachedRound,
          id,
          "DEFENDER_RELEASED",
          "defenders="
              + cachedDefenderCount
              + ":threshold="
              + STANDING_DEFENSE_COUNT
              + ":can_explore");
    }

    // Role-based target scoring - do this BEFORE vision management so cachedBestTarget is fresh
    // In EMERGENCY DEFENSE mode, ALL rats act as sentries (converge on king, attack enemies)
    // Fix 5: If not safe to explore, gatherers also act as sentries (stay near king)
    // FIX 2: Standing defenders also stay near king
    // FIX 10: CRITICAL ECONOMY OVERRIDE - when cheese is dangerously low, gatherers MUST collect!
    // This prevents the economy death spiral where lockout -> no cheese -> king starves
    // BYTECODE OPT: Use pre-read cached king cheese value
    // FIX 10b: Remove `> 0` check - cheese=0 is the MOST critical emergency!
    // Note: kingCheese=0 on round 1 before king writes, but that's fine - no lockout active then
    // anyway
    boolean economyEmergency = cachedKingCheese < CRITICAL_CHEESE_THRESHOLD;

    // FIX 10: TIGHTER gatherer lockout radius - only lock rats within 5 tiles (was 12!)
    // 12 tiles locked EVERYONE in late game, causing economy collapse
    boolean gathererNearKing =
        (role == 0) && cachedDistToKingSq >= 0 && cachedDistToKingSq <= GATHERER_LOCKOUT_RADIUS_SQ;

    // FIX 10: Economy emergency overrides safety lockout - gatherers MUST collect cheese!
    // FIX 11: Also boost cheese priority during emergency so gatherers actively seek it
    // FIX #1 RECOVERY: Recovery mode ALSO boosts cheese priority for faster recovery
    boolean shouldActAsSentry;
    boolean economyBoostCheese = false; // FIX 11: Flag to boost cheese priority
    if ((economyEmergency || economyRecoveryMode) && role == 0) {
      // ECONOMY EMERGENCY OR RECOVERY: Gatherers ignore lockout and collect cheese!
      shouldActAsSentry = false;
      economyBoostCheese = true; // FIX 11: Boost cheese priority in scoring
      // FIX #2: Reduce log spam - only log every 20 rounds
      if (economyRecoveryMode && !economyEmergency && (cachedRound % 20) == 0) {
        logDecision(
            cachedRound,
            id,
            "RECOVERY_GATHER_BOOST",
            "kingCheese=" + cachedKingCheese + ":BOOSTED_PRIORITY");
      } else if (economyEmergency) {
        logDecision(
            cachedRound,
            id,
            "ECON_EMERGENCY_OVERRIDE",
            "kingCheese=" + cachedKingCheese + ":MUST_COLLECT");
      }
    } else {
      // FIX #4: All other cases (gatherers not in emergency/recovery, or non-gatherers)
      shouldActAsSentry =
          emergencyDefense || ((!safeToExplore && gathererNearKing) || shouldStayNearKing);
    }

    // BYTECODE OPTIMIZATION: Use enemyCount instead of enemies.length
    if (shouldActAsSentry) {
      // Emergency OR (not safe AND near king) OR standing defender - act as sentry!
      scoreAllTargetsSentry(rc, enemyBuffer, enemyCount);
      // DEBUG: Log economy lockout
      if (!emergencyDefense && !safeToExplore && gathererNearKing) {
        logEconomyLockout(
            cachedRound,
            id,
            "SAFE_THRESHOLD",
            cachedSafeRounds,
            SAFE_EXPLORE_THRESHOLD,
            cachedDistToKingSq); // BYTECODE OPT: Pass squared distance, avoid intSqrt
      } else if (shouldStayNearKing) {
        logEconomyLockout(
            cachedRound,
            id,
            "STANDING_DEFENDER",
            cachedSafeRounds,
            STANDING_DEFENSE_COUNT,
            cachedDistToKingSq); // BYTECODE OPT: Pass squared distance, avoid intSqrt
      }
    } else {
      if (role == 0) {
        // Gatherer - safe to explore (or economy emergency forces collection)
        // FIX #5: Pass cached global cheese to avoid duplicate API call
        scoreAllTargetsGatherer(
            rc, enemyBuffer, enemyCount, economyBoostCheese, cachedGlobalCheese);
      } else if (role == 1 || role == 3 || role == 4) {
        // Sentry, Guardian, or Interceptor - all use sentry scoring as fallback
        // (Guardian and Interceptor have specialized behavior above but need fallback scoring)
        scoreAllTargetsSentry(rc, enemyBuffer, enemyCount);
        // Sentry-specific: face OUTWARD for 360° coverage
        // Only do this when in position and no enemies visible
        // BYTECODE OPT: Inline isInSentryRingPosition() check
        if (enemyCount == 0
            && cachedDistToKingSq >= 0
            && cachedDistToKingSq >= sentryRingInnerSq
            && cachedDistToKingSq <= sentryRingDistSq) {
          trySentryFaceOutward(rc);
          // DEBUG: Log sentry status
          // BYTECODE OPT: Pass squared distance, avoid intSqrt
          logSentryStatus(cachedRound, id, true, cachedDistToKingSq, cachedFacing.name());
        }
      } else {
        // Scout
        scoreAllTargetsScout(rc, enemyBuffer, enemyCount);
      }
    }

    // BYTECODE: After target scoring
    logBytecode(cachedRound, id, "SCORE");

    // Vision management - scan even when cheese is visible to spot nearby threats
    // This helps rats find enemies in their blind spots while collecting cheese
    if (enemyCount == 0) {
      if (emergencyDefense || !enemyKingConfirmed) {
        // In emergency or search mode - scan aggressively
        trySearchScan(rc);
      } else {
        tryTurnTowardTarget(rc);
      }
    }

    // Move toward target
    if (cachedBestTarget != null) {
      // DEBUG: Visualize current target (guarded for bytecode savings)
      if (Debug7.ENABLED) visualizeTarget(rc, myLoc, cachedBestTarget);
      bug2MoveTo(rc, cachedBestTarget);
    }

    // BYTECODE: After movement
    logBytecode(cachedRound, id, "MOVE");

    // DEBUG: Set indicator string for baby rat
    String action =
        emergencyDefense ? "DEFEND" : (cachedBestTargetType == TARGET_CHEESE ? "COLLECT" : "MOVE");
    String detail =
        cachedBestTarget != null ? cachedBestTarget.x + "," + cachedBestTarget.y : "none";
    indicator(rc, buildIndicator(roleName(role), phaseName(cachedPhase), myHP, action, detail));

    // Squeak enemy king if found
    updateEnemyKingFromBabyRat(rc, enemyBuffer, enemyCount);

    // =====================================================================
    // CAT REDIRECTION VIA SQUEAKING
    // If a cat is near our king, rats on the OPPOSITE side squeak to lure it away
    // HIGH RISK: Cats hear all squeaks and will chase the source!
    // =====================================================================
    tryCatBaitSqueak(rc);

    // =====================================================================
    // EARLY WARNING THREAT SQUEAK
    // If we see enemies, squeak to warn the king preemptively
    // This allows defense to trigger BEFORE enemies reach the king
    // =====================================================================
    if (enemyCount > 0) {
      tryThreatSqueak(rc, enemyCount);
    }

    // BYTECODE: End of turn summary
    logBytecode(cachedRound, id, "END");
    logBytecodeSummary(cachedRound, id, false);
  }

  // ========================================================================
  // THREAT SQUEAK - Early warning system
  // ========================================================================
  private static int lastThreatSqueakRound = -100;

  /**
   * Broadcast threat squeak to warn king of approaching enemies. This enables preemptive defense
   * before enemies reach the king.
   */
  private static void tryThreatSqueak(RobotController rc, int enemyCount)
      throws GameActionException {
    // Throttle threat squeaks to avoid spam
    if (cachedRound - lastThreatSqueakRound < THREAT_SQUEAK_THROTTLE) return;
    // Don't override important king-location squeaks
    if (cachedRound - lastSqueakRound < 3) return;

    // Only squeak if enemies are approaching (within 15 tiles of our king)
    if (!hasOurKingLoc) return;

    // Encode squeak: type in top 4 bits, enemy count in bottom 4 bits
    // Also encode our position so king knows threat direction
    int squeakContent =
        (SQUEAK_TYPE_ENEMY_SPOTTED << 28) | (myLocY << 16) | (myLocX << 4) | (enemyCount & 0xF);

    rc.squeak(squeakContent);
    lastThreatSqueakRound = cachedRound;
    lastSqueakRound = cachedRound;
    lastSqueakID = rc.getID();

    // DEBUG: Log squeak sent (using individual values to avoid bytecode overhead)
    logSqueakSent(cachedRound, rc.getID(), "ENEMY_SPOTTED", myLocX, myLocY, enemyCount);
    // DEBUG: Log enemy spotted details
    logEnemySpotted(
        cachedRound,
        rc.getID(),
        enemyCount,
        myLocX,
        myLocY,
        cachedDistToKingSq); // BYTECODE OPT: Pass squared dist, avoid intSqrt
  }

  // ========================================================================
  // CAT BAITING - Lure cats away from king via squeaking
  // ========================================================================

  // ========================================================================
  // BODY BLOCKING - Form shield wall to protect king
  // ========================================================================

  // BYTECODE OPT: Inlined shouldBodyBlock check - use cachedBlockSlot < 7
  // Old method kept for reference but not called:
  // private static boolean shouldBodyBlock(int ratId) {
  //   return ((ratId >> 4) % 10) < 7; // ~70% of rats, different selection than role
  // }

  /**
   * Try to position this rat in the blocking line to physically block enemy paths. Returns true if
   * we moved toward blocking position. Does NOT return true for holding position to allow
   * ratnapping opportunities.
   *
   * <p>Shield Wall Formation:
   *
   * <pre>
   *        Enemy Center of Mass
   *              ↓
   *   [B-2][B-1][B0][B+1][B+2]  ← Blocking line (5 rats)
   *              |
   *              | (adaptive distance)
   *              |
   *            KING
   * </pre>
   *
   * <p>FIX 7: Only NEARBY rats (within 6 tiles of king) can become blockers. Don't pull rats from
   * the opposite side of the map - they'll never arrive in time.
   */
  private static boolean tryBodyBlock(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    // FIX 14: Check distance to BLOCKING LINE, not distance to KING
    // This ensures blockers are assigned based on their ability to reach the line quickly

    // Read blocking line from shared array FIRST to check distance properly
    // Note: Values are stored + 1 to distinguish from "not set" (0)
    int lineXStored = rc.readSharedArray(SLOT_BLOCK_LINE_X);
    int lineYStored = rc.readSharedArray(SLOT_BLOCK_LINE_Y);
    int perpOrdinalStored = rc.readSharedArray(SLOT_BLOCK_PERP);

    // No blocking line set (not enough enemies or too far)
    // Check if ANY value is 0 (not set)
    if (lineXStored == 0 || lineYStored == 0 || perpOrdinalStored == 0) return false;

    // Convert back from stored format (subtract 1)
    int lineX = lineXStored - 1;
    int lineY = lineYStored - 1;
    int perpOrdinal = perpOrdinalStored - 1;

    // Validate perpOrdinal is in valid range for Direction (0-7)
    if (perpOrdinal < 0 || perpOrdinal > 7) return false;

    // FIX 14: Check distance to blocking LINE CENTER, not distance to king
    // This ensures only nearby rats become blockers (distant rats won't reach in time)
    int dx = myLocX - lineX;
    int dy = myLocY - lineY;
    int distToLineCenterSq = dx * dx + dy * dy;
    if (distToLineCenterSq > BODY_BLOCK_MAX_RAT_DIST_SQ) {
      return false; // Too far from blocking line to be effective
    }

    MapLocation lineCenter = new MapLocation(lineX, lineY);
    Direction perpDir = Direction.values()[perpOrdinal];

    // Calculate our assigned slot in the blocking line (-3, -2, -1, 0, 1, 2, 3)
    // Use ID to spread rats across the 7 positions
    int slot = (rc.getID() % BODY_BLOCK_SLOTS) - (BODY_BLOCK_SLOTS / 2);

    // Calculate our blocking position
    int blockX = lineX + perpDir.dx * slot;
    int blockY = lineY + perpDir.dy * slot;

    // Clamp to map bounds - BYTECODE OPT: Use cached dimensions
    blockX = Math.max(0, Math.min(cachedMapWidth - 1, blockX));
    blockY = Math.max(0, Math.min(cachedMapHeight - 1, blockY));

    // BYTECODE OPT: Inline distance calculation instead of creating MapLocation for comparison
    int dxLine = myLocX - lineX;
    int dyLine = myLocY - lineY;
    int myDistToLine = dxLine * dxLine + dyLine * dyLine;

    // Check if we're at blocking position (inline distance check)
    int dxBlock = myLocX - blockX;
    int dyBlock = myLocY - blockY;
    boolean atBlockPos = (dxBlock == 0 && dyBlock == 0);
    boolean inPosition = atBlockPos || myDistToLine <= 8;

    if (inPosition) {
      // DEBUG: Log blocker in position
      logBodyBlock(cachedRound, rc.getID(), slot, true, myDistToLine, enemyCount);

      // In position - attack adjacent enemies while blocking
      tryBlockerAttack(rc, enemies, enemyCount);

      // Turn to face enemies while blocking
      if (cachedMovementReady && enemyCount > 0) {
        tryFaceClosestEnemy(rc, enemies, enemyCount);
      }

      // Return FALSE to allow ratnapping opportunities even while blocking!
      // The blocker stays in position but can still grab weak enemies
      return false;
    }

    // DEBUG: Log blocker moving to position
    logBodyBlock(cachedRound, rc.getID(), slot, false, myDistToLine, enemyCount);

    // Move toward our assigned blocking position
    // BYTECODE OPT: Only allocate MapLocation when we actually need to move
    bug2MoveTo(rc, new MapLocation(blockX, blockY));
    return true; // We're moving, skip other behaviors
  }

  // ========================================================================
  // DEFENSIVE KITING - Retreat after attacking for better trade ratio
  // ========================================================================

  /**
   * Execute defensive kiting against a target enemy. State machine: APPROACH -> ATTACK -> RETREAT
   * -> APPROACH Returns true if action was taken, false if no action needed.
   */
  private static boolean runDefensiveKiting(
      RobotController rc, RobotInfo target, MapLocation myLoc, int myHP)
      throws GameActionException {
    if (target == null) return false;

    int targetId = target.getID();
    MapLocation targetLoc = target.getLocation();
    int distToTarget = myLoc.distanceSquaredTo(targetLoc);

    // Reset kite state if target changed
    if (lastKiteTargetId != targetId) {
      kiteState = KITE_STATE_APPROACH;
      kiteRetreatTurns = 0;
      lastKiteTargetId = targetId;
    }

    // Dynamic kite distance based on HP
    int kiteRetreatDist =
        (myHP >= KITE_HP_HEALTHY_THRESHOLD) ? KITE_RETREAT_DIST_HEALTHY : KITE_RETREAT_DIST_WOUNDED;

    switch (kiteState) {
      case KITE_STATE_APPROACH:
        if (distToTarget <= 2) {
          // Adjacent - transition to attack
          kiteState = KITE_STATE_ATTACK;
          // Fall through to attack
        } else if (distToTarget <= KITE_ENGAGE_DIST_SQ) {
          // Move toward enemy
          bug2MoveTo(rc, targetLoc);
          return true;
        } else {
          return false; // Too far, not kiting
        }
        // Fall through to attack state

      case KITE_STATE_ATTACK:
        if (rc.isActionReady() && rc.canAttack(targetLoc)) {
          rc.attack(targetLoc);
          // After attacking, retreat (unless healthy and can press advantage)
          if (myHP < KITE_HP_HEALTHY_THRESHOLD || target.getHealth() > 30) {
            kiteState = KITE_STATE_RETREAT;
            kiteRetreatTurns = kiteRetreatDist;
          }
          return true;
        }
        // Can't attack, try to get closer
        bug2MoveTo(rc, targetLoc);
        return true;

      case KITE_STATE_RETREAT:
        if (kiteRetreatTurns > 0) {
          // Move away from enemy
          Direction awayFromEnemy = targetLoc.directionTo(myLoc);
          if (awayFromEnemy != Direction.CENTER) {
            MapLocation retreatTarget = myLoc.translate(awayFromEnemy.dx * 2, awayFromEnemy.dy * 2);
            bug2MoveTo(rc, retreatTarget);
          }
          kiteRetreatTurns--;
          return true;
        }
        // Done retreating, go back to approach
        kiteState = KITE_STATE_APPROACH;
        return false;
    }
    return false;
  }

  /** Get the patrol point for interceptors - between our king and mid-map. */
  private static MapLocation getInterceptorPatrolPoint() {
    if (!hasOurKingLoc) return null;

    // Patrol toward enemy king or map center
    int targetX, targetY;
    if (cachedEnemyKingLoc != null) {
      targetX = cachedEnemyKingLoc.x;
      targetY = cachedEnemyKingLoc.y;
    } else {
      targetX = cachedMapWidth / 2;
      targetY = cachedMapHeight / 2;
    }

    // Calculate point INTERCEPTOR_PATROL_DIST tiles toward target from our king
    int dx = targetX - cachedOurKingLoc.x;
    int dy = targetY - cachedOurKingLoc.y;
    int dist = (int) Math.sqrt(dx * dx + dy * dy);
    if (dist == 0) dist = 1;

    int patrolX = cachedOurKingLoc.x + (dx * INTERCEPTOR_PATROL_DIST) / dist;
    int patrolY = cachedOurKingLoc.y + (dy * INTERCEPTOR_PATROL_DIST) / dist;

    // Clamp to map bounds
    patrolX = Math.max(0, Math.min(cachedMapWidth - 1, patrolX));
    patrolY = Math.max(0, Math.min(cachedMapHeight - 1, patrolY));

    return new MapLocation(patrolX, patrolY);
  }

  /**
   * Check if an enemy is bypassing defenders toward our king. Returns the bypassing enemy or null.
   */
  private static RobotInfo findBypassingEnemy(
      RobotInfo[] enemies, int enemyCount, MapLocation myLoc) {
    if (!hasOurKingLoc) return null;

    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (!enemy.getType().isBabyRatType()) continue;

      MapLocation enemyLoc = enemy.getLocation();
      int distToMe = myLoc.distanceSquaredTo(enemyLoc);
      int enemyDistToKing = enemyLoc.distanceSquaredTo(cachedOurKingLoc);

      // Enemy is bypassing if:
      // 1. Far from me (> 4 tiles)
      // 2. But close to king (< 6 tiles)
      // 3. Moving toward king (closer than me)
      if (distToMe > PURSUIT_BYPASS_DIST_SQ
          && enemyDistToKing < PURSUIT_KING_APPROACH_DIST_SQ
          && enemyDistToKing < cachedDistToKingSq) {
        return enemy;
      }
    }
    return null;
  }

  /** Calculate intercept point between enemy and king. */
  private static MapLocation getInterceptPoint(MapLocation enemyLoc, MapLocation kingLoc) {
    // Intercept point is halfway between enemy and king
    int interceptX = (enemyLoc.x + kingLoc.x) / 2;
    int interceptY = (enemyLoc.y + kingLoc.y) / 2;
    return new MapLocation(interceptX, interceptY);
  }

  /** Helper: Attack closest enemy while in blocking position. */
  private static void tryBlockerAttack(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    if (!cachedActionReady || enemyCount == 0) return;

    for (int i = enemyCount; --i >= 0; ) {
      MapLocation enemyLoc = enemies[i].getLocation();
      if (rc.canAttack(enemyLoc)) {
        rc.attack(enemyLoc);
        cachedActionReady = false;
        return;
      }
    }
  }

  /** Helper: Turn to face the closest enemy. */
  private static void tryFaceClosestEnemy(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    if (!cachedMovementReady || enemyCount == 0) return;

    int closestDistSq = Integer.MAX_VALUE;
    MapLocation closestEnemy = null;
    for (int i = enemyCount; --i >= 0; ) {
      MapLocation loc = enemies[i].getLocation();
      // BYTECODE OPT: Inline distance calculation
      int edx = myLocX - loc.x;
      int edy = myLocY - loc.y;
      int distSq = edx * edx + edy * edy;
      if (distSq < closestDistSq) {
        closestDistSq = distSq;
        closestEnemy = loc;
      }
    }
    if (closestEnemy != null) {
      Direction toEnemy = myLoc.directionTo(closestEnemy);
      if (toEnemy != Direction.CENTER && toEnemy != cachedFacing && rc.canTurn(toEnemy)) {
        rc.turn(toEnemy);
        cachedMovementReady = false;
      }
    }
  }

  // ========================================================================
  // CAT BAITING - Lure cats away from king via squeaking
  // ========================================================================

  private static void tryCatBaitSqueak(RobotController rc) throws GameActionException {
    // Check if we've already squeaked recently (throttle)
    if (cachedRound - lastCatBaitRound < CAT_BAIT_THROTTLE_ROUNDS) return;
    if (cachedRound - lastSqueakRound < 3) return; // Don't override important squeaks

    // Read cat location from shared array
    int catX = rc.readSharedArray(SLOT_CAT_X);
    int catY = rc.readSharedArray(SLOT_CAT_Y);
    int catRound = rc.readSharedArray(SLOT_CAT_ROUND);

    // Cat info must be recent (within 5 rounds - cats move fast!)
    if (catRound == 0 || cachedRound - catRound > 5) return;

    MapLocation catLoc = new MapLocation(catX, catY);
    if (cachedOurKingLoc == null) return;

    // Check if we're far enough from king to be effective bait
    // BYTECODE OPTIMIZATION: Use cached distance - check our distance first (cheaper)
    if (cachedDistToKingSq < CAT_BAIT_MIN_DIST_FROM_KING_SQ) return;

    // Check if cat is actually close to our king (still a threat)
    // BYTECODE OPTIMIZATION: Inline distance calculation
    int catDx = catX - cachedOurKingLoc.x;
    int catDy = catY - cachedOurKingLoc.y;
    int catDistToKing = catDx * catDx + catDy * catDy;
    if (catDistToKing > CAT_BAIT_TRIGGER_DIST_SQ) return;

    // Check if we're on the OPPOSITE side of king from cat
    // (so our squeak draws cat AWAY from king, not through it)
    Direction kingToCat = cachedOurKingLoc.directionTo(catLoc);
    Direction kingToMe = cachedOurKingLoc.directionTo(myLoc);

    if (kingToCat == Direction.CENTER || kingToMe == Direction.CENTER) return;

    int angleDiff = getAngleDifference(kingToCat, kingToMe);
    if (angleDiff < CAT_BAIT_OPPOSITE_ANGLE) return; // Not opposite enough

    // We're in a good position to bait! Squeak to lure the cat.
    // The cat will hear this and enter Chase mode toward us.
    // Note: Squeak range is sqrt(16)=4, but cat Chase mode will still move toward source
    int squeak = (SQUEAK_TYPE_CAT_BAIT << 28) | (myLocY << 16) | (myLocX << 4);
    rc.squeak(squeak);
    lastCatBaitRound = cachedRound;
    lastSqueakRound = cachedRound;
    lastSqueakID = rc.getID();

    // DEBUG: Log cat bait squeak (using individual values to avoid bytecode overhead)
    // BYTECODE OPT: Pass squared distance, avoid intSqrt
    logSqueakSent(cachedRound, rc.getID(), "CAT_BAIT", myLocX, myLocY, catDistToKing);
  }

  // ========================================================================
  // DEFENSIVE RATNAPPING - Rescue wounded allies!
  // ========================================================================

  /**
   * Try to rescue a wounded ally by grabbing them and throwing toward king. Thrown rats are immune
   * to attacks while airborne - this is a rescue mechanic!
   *
   * <p>Note: Caller should verify rc.isActionReady() before calling to save bytecode.
   */
  private static boolean tryDefensiveRatnap(RobotController rc, RobotInfo[] allies, int allyCount)
      throws GameActionException {
    // First check if we're already carrying a rat
    RobotInfo carrying = rc.getCarrying();
    if (carrying != null) {
      // Already carrying - try to throw toward safety (king)
      return tryThrowTowardKing(rc);
    }

    // Not carrying - look for wounded allies to rescue
    // (Caller already verified isActionReady)
    // BYTECODE OPTIMIZATION: Use allyCount parameter
    for (int i = allyCount; --i >= 0; ) {
      RobotInfo ally = allies[i];
      if (ally.getType() == UnitType.RAT_KING) continue; // Can't ratnap kings

      // Only rescue wounded allies
      if (ally.getHealth() >= ALLY_RESCUE_HP_THRESHOLD) continue;

      MapLocation allyLoc = ally.getLocation();
      // BYTECODE OPT: Inline distance calculation
      int adx = myLocX - allyLoc.x;
      int ady = myLocY - allyLoc.y;
      int distSq = adx * adx + ady * ady;

      // Must be adjacent
      if (distSq > RATNAP_RANGE_SQ) continue;

      // Can always grab allies!
      if (rc.canCarryRat(allyLoc)) {
        rc.carryRat(allyLoc);
        holdingRatSinceRound = cachedRound; // Start tracking hold duration
        // DEBUG: Log defensive ratnap grab (rescue)
        logRatnapGrab(cachedRound, rc.getID(), ally.getID(), ally.getHealth(), true);
        return true;
      }
    }

    return false;
  }

  /** Throw carried ally toward king for safety. Thrown rats are immune to attacks during flight! */
  private static boolean tryThrowTowardKing(RobotController rc) throws GameActionException {
    RobotInfo carrying = rc.getCarrying();
    if (carrying == null) {
      holdingRatSinceRound = -1;
      return false;
    }

    // Only do defensive throw for ALLIES
    if (carrying.getTeam() != cachedOurTeam) return false;

    int holdDuration = (holdingRatSinceRound >= 0) ? (cachedRound - holdingRatSinceRound) : 0;

    // Determine throw direction toward our king (safety)
    // BYTECODE OPTIMIZATION: Use cached hasOurKingLoc
    Direction throwDir = Direction.SOUTH; // Default
    if (hasOurKingLoc) {
      throwDir = myLoc.directionTo(cachedOurKingLoc);
      if (throwDir == Direction.CENTER) throwDir = Direction.SOUTH;
    }

    // BYTECODE OPTIMIZATION: Use cached values

    // If already facing king and can throw, throw immediately!
    if (cachedFacing == throwDir && cachedActionReady && rc.canThrowRat()) {
      rc.throwRat();
      holdingRatSinceRound = -1;
      cachedActionReady = false; // Update cache
      // DEBUG: Log defensive ratnap throw (rescue) and visualize
      logRatnapThrow(cachedRound, rc.getID(), throwDir.name(), true);
      visualizeRescue(rc, myLoc, cachedOurKingLoc);
      return true;
    }

    // Fallback: held too long, throw in any direction
    if (holdDuration >= MAX_HOLD_ROUNDS && cachedActionReady && rc.canThrowRat()) {
      rc.throwRat();
      holdingRatSinceRound = -1;
      cachedActionReady = false; // Update cache
      // DEBUG: Visualize rescue even for fallback throw
      if (hasOurKingLoc) visualizeRescue(rc, myLoc, cachedOurKingLoc);
      return true;
    }

    // Turn to face throw direction
    if (cachedFacing != throwDir && cachedMovementReady && rc.canTurn(throwDir)) {
      rc.turn(throwDir);
      cachedMovementReady = false; // Update cache
    }

    return false;
  }

  private static void updateEnemyKingFromBabyRat(
      RobotController rc, RobotInfo[] enemies, int enemyCount) throws GameActionException {
    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) {
        MapLocation enemyLoc = enemy.getLocation();
        cachedEnemyKingLoc = enemyLoc;
        cachedEnemyKingHP = enemy.getHealth();

        int round = cachedRound;
        int id = rc.getID();
        if (round - lastSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
          int hp = enemy.getHealth();
          int hpBits = Math.min(hp / 35, 15);
          int squeak =
              (SQUEAK_TYPE_ENEMY_KING << 28) | (enemyLoc.y << 16) | (enemyLoc.x << 4) | hpBits;
          rc.squeak(squeak);
          lastSqueakRound = round;
          lastSqueakID = id;
        }
        return;
      }
    }
  }

  // ========================================================================
  // RATNAPPING - Grab weak enemies and throw them!
  // ========================================================================

  /**
   * Try to ratnap a weak enemy rat and throw it at the enemy king. Ratnapping rules: - Must be
   * adjacent and visible - Can grab if: enemy facing away, OR we have more HP, OR it's an ally
   *
   * <p>Throwing deals 5 * (4 - airtime) damage to BOTH the thrown rat AND any target hit!
   */
  private static boolean tryRatnapAndThrow(RobotController rc, RobotInfo[] enemies, int enemyCount)
      throws GameActionException {
    // First check if we're already carrying a rat (use getCarrying() not isCarryingRat())
    RobotInfo carrying = rc.getCarrying();
    if (carrying != null) {
      // We have a rat - try to throw it toward enemy king!
      return tryThrowAtEnemyKing(rc);
    }

    // Not carrying - try to grab a weak enemy
    // BYTECODE OPTIMIZATION: Use cached value
    if (!cachedActionReady) return false;

    int myHealth = rc.getHealth();

    // BYTECODE OPTIMIZATION: Use enemyCount parameter
    for (int i = enemyCount; --i >= 0; ) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType() == UnitType.RAT_KING) continue; // Can't ratnap kings
      if (enemy.getType() == UnitType.CAT) continue; // Can't ratnap cats

      MapLocation enemyLoc = enemy.getLocation();
      // BYTECODE OPTIMIZATION: Inline distance calculation
      int edx = myLocX - enemyLoc.x;
      int edy = myLocY - enemyLoc.y;
      int distSq = edx * edx + edy * edy;

      // Must be adjacent
      if (distSq > RATNAP_RANGE_SQ) continue;

      // BYTECODE OPTIMIZATION: Check canCarryRat early to skip expensive calculations
      if (!rc.canCarryRat(enemyLoc)) continue;

      // Check if we can grab: enemy has less/equal HP OR is facing away
      boolean canGrab = false;

      // We have more or equal HP - can grab!
      if (enemy.getHealth() <= myHealth) {
        canGrab = true;
      } else {
        // Only check facing if we don't have HP advantage
        Direction enemyFacing = enemy.getDirection();
        Direction toUs = enemyLoc.directionTo(myLoc);
        if (enemyFacing != null && toUs != Direction.CENTER) {
          int angleDiff = getAngleDifference(enemyFacing, toUs);
          if (angleDiff >= 90) { // Facing away (90+ degrees from us)
            canGrab = true;
          }
        }
      }

      if (canGrab) {
        rc.carryRat(enemyLoc);
        holdingRatSinceRound = cachedRound; // Start tracking hold duration
        cachedActionReady = false; // Update cache
        // DEBUG: Log ratnap grab
        logRatnapGrab(cachedRound, rc.getID(), enemy.getID(), enemy.getHealth(), false);
        return true;
      }
    }

    return false;
  }

  /**
   * Throw a carried rat toward the enemy king for burst damage. Thrown rats deal 5 * (4 - airtime)
   * damage - max 20 if hitting immediately!
   *
   * <p>Logic: 1) If already facing toward target, throw immediately 2) If not facing but can turn,
   * turn first (uses movement cooldown) 3) If held too long without throwing, throw in any
   * direction 4) Throwing uses ACTION cooldown, turning uses MOVEMENT cooldown - they're separate!
   */
  private static boolean tryThrowAtEnemyKing(RobotController rc) throws GameActionException {
    // Check if carrying a rat using getCarrying()
    RobotInfo carrying = rc.getCarrying();
    if (carrying == null) {
      holdingRatSinceRound = -1; // Reset tracker
      return false;
    }

    // Track how long we've been holding this rat (for fallback throw)
    // holdingRatSinceRound is -1 when not holding, >= 0 when holding
    int holdDuration = (holdingRatSinceRound >= 0) ? (cachedRound - holdingRatSinceRound) : 0;

    // Determine ideal throw direction toward enemy king (confirmed or estimated)
    Direction throwDir = Direction.NORTH;
    if (cachedEnemyKingLoc != null) {
      throwDir = myLoc.directionTo(cachedEnemyKingLoc);
      if (throwDir == Direction.CENTER) throwDir = Direction.NORTH;
    }

    // BYTECODE OPTIMIZATION: Use cached facing

    // PRIORITY 1: If we're already facing the right direction and can throw, throw immediately!
    // Throwing uses ACTION cooldown
    if (cachedFacing == throwDir && cachedActionReady && rc.canThrowRat()) {
      rc.throwRat();
      holdingRatSinceRound = -1; // Reset tracker
      // DEBUG: Log ratnap throw
      logRatnapThrow(cachedRound, rc.getID(), throwDir.name(), false);
      return true;
    }

    // PRIORITY 2: If held too long, throw in whatever direction we're facing (fallback)
    // This prevents getting stuck holding a rat indefinitely
    if (holdDuration >= MAX_HOLD_ROUNDS && cachedActionReady && rc.canThrowRat()) {
      rc.throwRat();
      holdingRatSinceRound = -1; // Reset tracker
      return true;
    }

    // PRIORITY 3: Turn to face the throw direction if needed
    // Turning uses MOVEMENT cooldown (separate from action!)
    if (cachedFacing != throwDir && cachedMovementReady && rc.canTurn(throwDir)) {
      rc.turn(throwDir);
      cachedMovementReady = false; // Update cache
      // DON'T return true here - let the rat continue other actions this turn
      // We've turned, and next iteration will try to throw
    }

    // Return false to let the rat continue with other behaviors
    // The throw will happen next turn once we're facing the right direction
    return false;
  }
}
