package ratbot5;

import battlecode.common.*;

/**
 * ratbot5 - Professional-level competitive bot
 *
 * <p>Features: - Kiting state machine (attack-retreat-approach cycle) - Enemy ring buffer (track
 * last 4 enemy positions for prediction) - Overkill prevention (don't waste damage on nearly-dead
 * enemies) - Retreat coordination (broadcast retreat signal when army HP is low) - Predictive
 * targeting (aim where enemies will be) - Game theory integration (optimal backstab timing) - Army
 * strength estimation (track relative army sizes) - Dynamic swarm rebalancing (redirect orphaned
 * flankers) - Adaptive strategy detection (counter enemy patterns)
 */
public class RobotPlayer {
  // ================================================================
  // CONFIGURATION - Grouped tunable parameters
  // ================================================================

  // ========== DEBUG CONFIG ==========
  private static final boolean DEBUG = false;
  private static final boolean PROFILE = false;
  private static final int PROFILE_INTERVAL = 10;

  // ========== MAP SIZE THRESHOLDS ==========
  private static final int SMALL_MAP_AREA = 40 * 40;
  private static final int MEDIUM_MAP_AREA = 50 * 50;

  // ========== COMBAT CONFIG ==========
  private static final int ENHANCED_ATTACK_CHEESE = 16;
  private static final int ENHANCED_THRESHOLD = 300;
  private static final int SMALL_MAP_ENHANCED_THRESHOLD = 100;
  private static final int FOCUS_FIRE_STALE_ROUNDS = 2;

  // ========== KITING CONFIG ==========
  private static final int KITE_STATE_APPROACH = 0;
  private static final int KITE_STATE_ATTACK = 1;
  private static final int KITE_STATE_RETREAT = 2;
  // DYNAMIC KITE DISTANCE: Now calculated based on HP in getKiteRetreatDist()
  private static final int KITE_RETREAT_DIST_HEALTHY = 1; // HP >= 60
  private static final int KITE_RETREAT_DIST_WOUNDED = 2; // HP 40-59
  private static final int KITE_RETREAT_DIST_CRITICAL = 3; // HP < 40
  private static final int KITE_ENGAGE_DIST_SQ = 8; // Start kiting when this close

  // ========== OVERKILL PREVENTION ==========
  private static final int OVERKILL_HP_THRESHOLD =
      20; // Don't attack if HP < this and allies nearby

  // ========== RETREAT COORDINATION ==========
  private static final int RETREAT_HP_RATIO_THRESHOLD = 40; // Retreat if our HP < 40% of enemy
  private static final int RETREAT_SIGNAL_STALE = 5; // Retreat signal expires after 5 rounds
  private static final int WOUNDED_HP_THRESHOLD = 40; // HP below which rats should retreat
  private static final int WOUNDED_RETREAT_EXCEPTION_DIST_SQ =
      16; // Don't retreat if this close to enemy king

  // ========== ADAPTIVE STRATEGY ==========
  private static final int RUSH_DETECTION_ROUND = 30;
  private static final int RUSH_ENEMY_THRESHOLD = 3; // >3 enemies = rush
  private static final int TURTLE_DETECTION_ROUND = 50;
  private static final int TURTLE_ENEMY_THRESHOLD = 2; // <2 enemies = turtle
  private static final int STRATEGY_NORMAL = 0;
  private static final int STRATEGY_ANTI_RUSH = 1;
  private static final int STRATEGY_ANTI_TURTLE = 2;

  // ========== PHASE-BASED STRATEGY CONFIG ==========
  private static final int PHASE_EARLY = 0;
  private static final int PHASE_MID = 1;
  private static final int PHASE_LATE = 2;
  private static final int EARLY_PHASE_END_ROUND = 50;
  private static final int MEDIUM_MAP_EARLY_PHASE_END_ROUND =
      40; // Faster transition to balanced phase on medium maps
  private static final int MID_PHASE_END_ROUND = 200;
  private static final int LOW_CHEESE_THRESHOLD = 300;
  private static final int HIGH_CHEESE_THRESHOLD = 800;
  private static final int EARLY_ATTACKER_RATIO = 8;
  private static final int MID_ATTACKER_RATIO = 6;
  private static final int LATE_RICH_ATTACKER_RATIO = 7;
  private static final int LATE_POOR_ATTACKER_RATIO = 4;
  private static final int LATE_NORMAL_ATTACKER_RATIO = 5;

  // ========== SWARMING CONFIG ==========
  private static final int SWARM_FLANK_DIST = 6;
  private static final int SWARM_ENGAGE_DIST_SQ = 64;
  private static final int MIN_FLANK_COUNT = 1; // Rebalance if flank has 0 rats

  // ========== POPULATION CONFIG ==========
  private static final int INITIAL_SPAWN_COUNT = 10;
  private static final int CONTINUOUS_SPAWN_RESERVE = 400;
  // STAGGERED WAVE SPAWNING: Spawn rats in waves to prevent bunching
  // On small/medium maps, spawn faster since games are decided quickly
  private static final int WAVE_SIZE = 3; // Rats per wave (large maps)
  private static final int WAVE_COOLDOWN_ROUNDS = 5; // Rounds between waves (large maps)
  private static final int SMALL_MAP_WAVE_SIZE = 5; // Larger waves on small maps
  private static final int SMALL_MAP_WAVE_COOLDOWN = 2; // Faster spawning on small maps
  private static final int MEDIUM_MAP_WAVE_SIZE = 4; // Medium waves on medium maps
  private static final int MEDIUM_MAP_WAVE_COOLDOWN = 3; // Moderate spawning speed on medium maps
  // LESSON LEARNED: Don't try to be "balanced" - commit to a strategy!
  // Medium maps should be AGGRESSIVE like small maps, not a middle-ground.
  // The "balanced" approach creates a "worst of both worlds" where Team B loses.
  private static final int MEDIUM_MAP_CONTINUOUS_RESERVE =
      150; // Aggressive but above emergency threshold
  private static final int MEDIUM_MAP_SPAWN_COOLDOWN = 1; // Spawn every round (was 2)
  private static final int MEDIUM_MAP_ALL_ATTACK_ROUNDS =
      50; // Extended from 30 to give Team A time to reach enemy king
  private static final int MEDIUM_MAP_EARLY_ATTACKER_RATIO =
      8; // 80% attackers like small maps (was 6)
  private static final int COLLECTOR_MINIMUM = 3;
  private static final int SMALL_MAP_INITIAL_SPAWN = 10;
  private static final int SMALL_MAP_COLLECTOR_MIN = 2;
  private static final int SMALL_MAP_ALL_ATTACK_ROUNDS = 25;
  private static final int SMALL_MAP_CONTINUOUS_RESERVE = 100;
  private static final int SPAWN_COOLDOWN_ROUNDS = 3;

  // ========== ECONOMY EMERGENCY CONFIG ==========
  private static final int EMERGENCY_LEVEL_3_THRESHOLD = 50; // ALL become collectors
  private static final int EMERGENCY_LEVEL_2_THRESHOLD = 100; // 80% collectors
  private static final int EMERGENCY_LEVEL_1_THRESHOLD = 200; // 60% collectors
  private static final int EMERGENCY_SPAWN_STOP_THRESHOLD = 80; // Stop spawning below this
  private static final int EMERGENCY_DELIVERY_THRESHOLD = 1; // Deliver immediately
  private static final int CRITICAL_DELIVERY_THRESHOLD = 3;
  private static final int WARNING_DELIVERY_THRESHOLD = 5;
  private static final int LARGE_MAP_COLLECTOR_RATIO = 5; // 50% collectors on large maps
  private static final int INCOME_CHECK_INTERVAL = 10; // Check income every 10 rounds
  private static final int MIN_INCOME_WARNING = 5; // Warn if income < 5 cheese per 10 rounds

  // ========== ARMY SIZE CONFIG ==========
  private static final int MIN_ARMY_SIZE = 6;
  private static final int EMERGENCY_SPAWN_RESERVE = 80;
  private static final int HEALTHY_ARMY_SIZE = 12;

  // ========== MOVEMENT CONFIG ==========
  private static final int LOW_BYTECODE_THRESHOLD = 800;

  // ========== ECONOMY CONFIG ==========
  private static final int DELIVERY_THRESHOLD = 12;
  private static final int MEDIUM_MAP_DELIVERY_THRESHOLD =
      8; // Faster cheese delivery on medium maps
  private static final int KING_CHEESE_RESERVE = 100;
  private static final int SMALL_MAP_KING_RESERVE = 50;
  private static final int CHEESE_SENSE_RADIUS_SQ = 20;
  private static final int GOOD_ENOUGH_CHEESE_DIST_SQ = 8;

  // ========== THROTTLE CONFIG ==========
  private static final int SQUEAK_THROTTLE_ROUNDS = 15;
  private static final int COLLECTOR_RELAY_THROTTLE_ROUNDS =
      5; // Collectors relay faster than attackers
  private static final int SENSE_THROTTLE_ROUNDS = 3;
  private static final int MAX_SQUEAKS_TO_READ = 12;

  // ========== CAT DEFENSE CONFIG ==========
  private static final int CAT_FLEE_DIST_SQ = 16;
  private static final int CAT_KING_FLEE_DIST_SQ =
      100; // Increased from 49 for earlier detection (10 tiles)
  private static final int CAT_KITE_MIN_DIST_SQ = 10;
  private static final int CAT_KITE_MAX_DIST_SQ = 25;
  private static final int CAT_TRAP_TARGET = 10;
  private static final int CAT_TRAP_END_ROUND = 40;

  // ========== RAT DEFENSE CONFIG ==========
  private static final int ALLY_MIN_DIST_SQ = 4; // Prevent clumping
  private static final int CLUSTER_ALLY_THRESHOLD = 3; // Max allies before triggering spread
  private static final int CLUSTER_SENSE_RADIUS_SQ = 9; // 3 tiles radius for cluster detection
  private static final int SPREAD_TRIGGER_ALLIES =
      4; // Trigger proactive spread when this many allies nearby
  private static final int RAT_TRAP_EARLY_TARGET = 10;
  private static final int SMALL_MAP_RAT_TRAP_TARGET = 3;
  private static final int RAT_TRAP_EARLY_WINDOW = 15;
  private static final int DEFEND_KING_DIST_SQ = 36;
  private static final int BABY_RAT_TRAP_COOLDOWN = 20;
  private static final int REACTIVE_TRAP_DIST_SQ = 25;

  // ========== CHOKEPOINT CONFIG ==========
  private static final int CHOKEPOINT_MIN_BLOCKED =
      3; // Min impassable neighbors to be a chokepoint
  private static final int CHOKEPOINT_SCAN_RADIUS_SQ =
      16; // How far from king to scan for chokepoints
  private static final int CHOKEPOINT_TRAP_INTERVAL =
      5; // Only check for chokepoints every N rounds
  private static final int CHOKEPOINT_TRAP_MAX = 5; // Max chokepoint traps to place

  // ========== GAME THEORY CONFIG ==========
  private static final int BACKSTAB_CHECK_INTERVAL = 50;
  private static final int BACKSTAB_CONFIDENCE_THRESHOLD = 10;
  private static final int BACKSTAB_MINIMUM_ROUND = 200;

  // ========== CHEESE MINE CAMPING CONFIG ==========
  private static final int MAX_TRACKED_MINES = 4;
  private static final int MINE_CAMP_RADIUS_SQ = 16; // Stay within 4 tiles of mine
  private static final int MINE_CAMP_START_ROUND = 50; // Start camping after round 50

  // ========== ALL-IN ATTACK CONFIG ==========
  private static final int ALL_IN_HP_THRESHOLD = 150; // Trigger all-in when enemy king HP < this
  private static final int ALL_IN_SIGNAL_DURATION = 100; // All-in lasts this many rounds
  private static final int ALL_IN_MIN_ATTACKERS = 5; // Need at least this many attackers
  private static final int ALL_IN_MIN_ROUND = 80; // Don't all-in before this round

  // ========== COORDINATED ATTACK CONFIG ==========
  private static final int COORDINATED_ATTACK_THRESHOLD =
      2; // Min attackers near enemy to engage (reduced from 3)
  private static final int NEAR_ENEMY_KING_DIST_SQ =
      100; // "Near" enemy king = within 10 tiles (increased)
  private static final int BOTH_KINGS_LOW_THRESHOLD = 150; // Consider king "low" below this HP
  private static final int RACE_DEFEND_MODE = 1; // Defend when we lose the race
  private static final int RACE_ATTACK_MODE = 2; // Attack when we win the race

  // ========== VISION CONE AWARENESS CONFIG ==========
  // Rats have directional vision (visionConeAngle), not 360° - must check behind periodically
  private static final int VISION_CHECK_BEHIND_INTERVAL = 3; // Check behind every N rounds
  private static final int VISION_CONE_DANGER_DIST_SQ =
      16; // Distance to consider "danger zone" behind

  // ========== ENEMY KING HP TRACKING ==========
  private static final int ENEMY_KING_STARTING_HP = 500; // Rat kings start with 500 HP
  private static final int BASE_ATTACK_DAMAGE = 10; // Base bite damage

  // ========== RESOURCE DENIAL CONFIG ==========
  private static final int DENIAL_PATROL_DIVISOR = 7; // id % 7 == 0 becomes denial patrol
  private static final int DENIAL_PATROL_START_ROUND = 100; // Start denial after round 100
  private static final int CHEESE_CARRIER_PRIORITY_BONUS =
      100; // Extra priority for cheese carriers
  private static final int NEAR_KING_PRIORITY_BONUS = 50; // Extra priority if near enemy king

  // ========== KING RUSH CONFIG ==========
  // FIX FOR TEAM B LOSS: Attackers were fighting baby rats instead of pushing to enemy king
  private static final int KING_PROXIMITY_BYPASS_DIST_SQ = 144; // Within 12 tiles, bypass baby rats
  private static final int LARGE_MAP_KING_BYPASS_DIST_SQ = 400; // Within 20 tiles on large maps
  private static final int ASSASSIN_DIVISOR = 5; // 20% of attackers are "assassins" that rush king
  private static final int LARGE_MAP_ASSASSIN_DIVISOR =
      2; // 50% assassins on large maps (more aggressive)
  private static final int HEALTHY_HP_THRESHOLD = 80; // HP above which rats don't kite retreat
  private static final int KING_ATTACK_PRIORITY_DIST_SQ = 25; // Within 5 tiles, always attack king

  // ========== INTERCEPTOR CONFIG ==========
  // Interceptors patrol between our king and mid-map to slow enemy rushes
  private static final int INTERCEPTOR_DIVISOR =
      4; // 25% of non-assassin attackers are interceptors
  private static final int INTERCEPTOR_ENGAGE_DIST_SQ = 64; // Engage enemies within 8 tiles
  private static final int INTERCEPTOR_PATROL_OFFSET = 10; // How many tiles toward enemy to patrol

  // ========== TERRAIN CONFIG ==========
  // SIMPLIFIED: Dirt avoidance removed - going straight through is faster than confusion
  // Keeping constants for reference but they're no longer actively used
  private static final int DIRT_AVOIDANCE_DIST = 50; // UNUSED - kept for reference
  private static final int DIRT_PENALTY_WEIGHT = 10; // UNUSED - kept for reference

  // ================================================================
  // SHARED ARRAY SLOT LAYOUT
  // ================================================================
  // Slot 0-1: Our king position (X, Y)
  // Slot 2-3: Enemy king position (X, Y)
  // Slot 4: King under attack flag
  // Slot 5-6: Focus fire target (packed loc + HP, round)
  // Slot 7: Phase + attacker ratio
  // Slot 8-11: Enemy ring buffer (4 most recent sightings)
  // Slot 12: Army strength ratio (our HP / enemy HP scaled to 0-100)
  // Slot 13: Retreat signal (round when issued)
  // Slot 14: Detected strategy (0=normal, 1=anti-rush, 2=anti-turtle)
  // Slot 15: Total enemies seen this game (for adaptive strategy)
  // Slot 16-19: Flank counts (center, left, right, reserve)
  // Slot 20: Cat damage accumulated (for game theory)
  // Slot 21: Chokepoint traps placed
  // Slot 34: Enemy king estimated HP
  // Slot 35: Rally point for retreat (packed X,Y)
  // Slot 36: Emergency level (0=normal, 1=warning, 2=critical, 3=emergency)
  // Slot 37: Last cheese amount (for income tracking)
  // Slot 38: Cheese income per 10 rounds
  // Slot 39: All-in signal (round triggered, 0 = inactive)
  // Slots 22-25: Known cheese mine locations (packed X,Y)
  // Slot 40: Number of known mines (0-4)
  // Slot 41: Total damage dealt to enemy king (cumulative)
  // Slot 42: Last confirmed enemy king HP (from direct sensing)
  // Slot 43: Attackers near enemy king count
  // Slot 44: Race mode (0=normal, 1=defend, 2=attack)

  // ================================================================
  // BYTECODE-OPTIMIZED CONSTANTS
  // ================================================================
  private static final Direction[] DIRS = Direction.allDirections();
  private static final int DIRS_LEN = 9;
  private static final int[] DIR_DX = {0, 1, 1, 1, 0, -1, -1, -1, 0};
  private static final int[] DIR_DY = {1, 1, 0, -1, -1, -1, 0, 1, 0};
  private static final Direction[] CAT_TRAP_PRIORITIES = new Direction[5];
  private static final int BABY_RAT_BYTECODE_LIMIT = 2500;
  private static final int KING_BYTECODE_LIMIT = 5000;
  private static final int BABY_RAT_VISION_SQ = 20;
  private static final int KING_VISION_SQ = 25;
  private static final Direction[] BABY_TRAP_DIRS = new Direction[3];

  // ================================================================
  // PROFILING
  // ================================================================
  private static int profTurnStart = 0;
  private static int profSenseNeutral = 0;
  private static int profSenseEnemy = 0;
  private static int profMovement = 0;
  private static int profCombat = 0;
  private static int profCheese = 0;
  private static int profCache = 0;
  private static int profOther = 0;
  private static int profTurnTotal = 0;
  private static int profMaxTurn = 0;
  private static int profSampleCount = 0;
  private static long profAccumSenseNeutral = 0;
  private static long profAccumSenseEnemy = 0;
  private static long profAccumMovement = 0;
  private static long profAccumCombat = 0;
  private static long profAccumCheese = 0;
  private static long profAccumCache = 0;
  private static long profAccumTotal = 0;

  private static void profReset() {
    profSenseNeutral = 0;
    profSenseEnemy = 0;
    profMovement = 0;
    profCombat = 0;
    profCheese = 0;
    profCache = 0;
    profOther = 0;
  }

  private static void profStartTurn() {
    profTurnStart = Clock.getBytecodeNum();
    profReset();
  }

  private static void profEndTurn(int round, int id, boolean isKing) {
    profTurnTotal = Clock.getBytecodeNum() - profTurnStart;
    profOther =
        profTurnTotal
            - profSenseNeutral
            - profSenseEnemy
            - profMovement
            - profCombat
            - profCheese
            - profCache;
    if (profOther < 0) profOther = 0;
    if (profTurnTotal > profMaxTurn) profMaxTurn = profTurnTotal;
    profAccumSenseNeutral += profSenseNeutral;
    profAccumSenseEnemy += profSenseEnemy;
    profAccumMovement += profMovement;
    profAccumCombat += profCombat;
    profAccumCheese += profCheese;
    profAccumCache += profCache;
    profAccumTotal += profTurnTotal;
    profSampleCount++;

    if (round % PROFILE_INTERVAL == 0) {
      int limit = isKing ? KING_BYTECODE_LIMIT : BABY_RAT_BYTECODE_LIMIT;
      int pct = (profTurnTotal * 100) / limit;
      System.out.println(
          "PROF:"
              + round
              + ":"
              + id
              + ":"
              + (isKing ? "KING" : "BABY")
              + " "
              + profTurnTotal
              + "/"
              + limit
              + " ("
              + pct
              + "%)"
              + " | sense:"
              + (profSenseNeutral + profSenseEnemy)
              + " move:"
              + profMovement
              + " combat:"
              + profCombat
              + " | max:"
              + profMaxTurn);
    }
  }

  // ================================================================
  // CACHED STATE
  // ================================================================
  private static int cachedKingX, cachedKingY, cachedEnemyX, cachedEnemyY;
  private static int lastCacheRound = -1;
  private static Team cachedOurTeam = null;
  private static Team cachedEnemyTeam = null;
  private static boolean cachedIsCooperation = false;

  // Focus fire target
  private static MapLocation cachedFocusTarget = null;
  private static int cachedFocusTargetHP = 0;
  private static int cachedFocusTargetRound = -100;

  // Phase-based strategy
  private static int cachedGamePhase = PHASE_EARLY;
  private static int cachedAttackerRatio = EARLY_ATTACKER_RATIO;

  // Cached locations
  private static MapLocation cachedOurKingLoc = null;
  private static MapLocation cachedEnemyKingLoc = null;
  private static MapLocation cachedMapCenter = null;
  private static int lastKingLocRound = -1;

  // Map size
  private static int mapSize = -1;
  private static boolean mapSizeComputed = false;
  private static int mapWidth = 0;
  private static int mapHeight = 0;

  // Enemy ring buffer cache (read from shared array)
  private static int[] cachedEnemyRingX = new int[4];
  private static int[] cachedEnemyRingY = new int[4];
  private static int[] cachedEnemyRingRound = new int[4];
  private static int cachedEnemyRingIndex = 0;

  // Retreat coordination
  private static int cachedRetreatRound = -100;
  private static boolean cachedShouldRetreat = false;

  // Adaptive strategy
  private static int cachedDetectedStrategy = STRATEGY_NORMAL;

  // Swarm role caching
  private static int cachedSwarmRole = -1;
  private static int lastSwarmRoleID = -1;

  // Flank counts (read from shared array slots 16-18)
  private static int cachedCenterCount = 0;
  private static int cachedLeftCount = 0;
  private static int cachedRightCount = 0;

  // Cached swarm flank locations (avoid allocation)
  private static MapLocation cachedLeftFlankLoc = null;
  private static MapLocation cachedRightFlankLoc = null;
  private static int lastFlankLocRound = -1;

  // Cached swarm direction (avoid repeated directionTo calls)
  private static Direction cachedSwarmDir = null;
  private static int lastSwarmDirRound = -1;

  // Cached squeak target (avoid allocation)
  private static MapLocation cachedSqueakTarget = null;

  // Rally point for retreat
  private static MapLocation cachedRallyPoint = null;
  private static int cachedEnemyKingHP = ENEMY_KING_STARTING_HP; // Estimated enemy king HP
  private static int cachedDamageToEnemyKing = 0; // Cumulative damage dealt
  private static int lastConfirmedEnemyKingHP = -1; // -1 = never seen, otherwise actual sensed HP
  private static int lastConfirmedHPRound = -1; // Round when we last saw enemy king

  // Emergency economy state
  private static int cachedEmergencyLevel = 0;
  private static int lastCheeseAmount = -1; // -1 = not initialized yet

  // All-in state
  private static int cachedAllInRound = 0;
  private static boolean cachedAllInActive = false;

  // Coordinated attack state
  private static int cachedAttackersNearEnemy = 0;
  private static int cachedRaceMode = 0; // 0=normal, 1=defend, 2=attack
  private static int cachedOurKingHP = 500;

  // Cheese mine tracking
  private static MapLocation[] cachedMineLocations = new MapLocation[MAX_TRACKED_MINES];
  private static int cachedNumMines = 0;
  private static int lastMineReadRound = -1;

  // Vision cone awareness - track when we last looked behind
  private static int lastVisionCheckBehindRound = -100;
  private static int lastVisionCheckBehindID = -1;

  // Collector relay for enemy king position - collectors store heard positions and relay on
  // delivery.
  // NOTE: These static variables are safe in Battlecode because only ONE robot runs per turn
  // (single-threaded execution). The heardEnemyKingID check ensures state resets when a different
  // collector runs. Multiple collectors in the same turn would overwrite each other, but that's
  // fine since we want the most recent heard position anyway.
  private static MapLocation heardEnemyKingLoc = null;
  private static int heardEnemyKingRound = -100;
  private static int heardEnemyKingID = -1;

  // Kiting state (per robot)
  private static int kiteState = KITE_STATE_APPROACH;
  private static int lastKiteID = -1;
  private static int kiteRetreatTurns = 0;
  private static MapLocation lastAttackedLoc = null;

  // ================================================================
  // INITIALIZATION
  // ================================================================

  private static void computeMapSize(RobotController rc) {
    if (!mapSizeComputed) {
      mapWidth = rc.getMapWidth();
      mapHeight = rc.getMapHeight();
      int area = mapWidth * mapHeight;
      if (area <= SMALL_MAP_AREA) {
        mapSize = 0;
      } else if (area <= MEDIUM_MAP_AREA) {
        mapSize = 1;
      } else {
        mapSize = 2;
      }
      cachedMapCenter = new MapLocation(mapWidth >> 1, mapHeight >> 1);
      cachedOurTeam = rc.getTeam();
      cachedEnemyTeam = cachedOurTeam.opponent();
      cachedIsCooperation = rc.isCooperation();
      mapSizeComputed = true;
    }
  }

  // REMOVED isSmallMap() - use mapSize == 0 directly for bytecode savings

  // ================================================================
  // CACHE REFRESH
  // ================================================================

  private static void refreshCache(RobotController rc) throws GameActionException {
    int r = rc.getRoundNum();
    if (r == lastCacheRound) return;
    lastCacheRound = r;

    // Basic king positions
    cachedKingX = rc.readSharedArray(0);
    cachedKingY = rc.readSharedArray(1);
    cachedEnemyX = rc.readSharedArray(2);
    cachedEnemyY = rc.readSharedArray(3);

    if (lastKingLocRound != r) {
      if (cachedOurKingLoc == null
          || cachedOurKingLoc.x != cachedKingX
          || cachedOurKingLoc.y != cachedKingY) {
        cachedOurKingLoc = new MapLocation(cachedKingX, cachedKingY);
      }
      // GLOBAL (0,0) PROTECTION: If enemy position is (0,0), it's not set yet
      // Use map center as fallback to prevent asymmetric behavior for Team B
      if (cachedEnemyX == 0 && cachedEnemyY == 0 && cachedMapCenter != null) {
        // Enemy position not set yet - use map center as fallback
        if (cachedEnemyKingLoc == null || !cachedEnemyKingLoc.equals(cachedMapCenter)) {
          cachedEnemyKingLoc = cachedMapCenter;
        }
      } else if (cachedEnemyKingLoc == null
          || cachedEnemyKingLoc.x != cachedEnemyX
          || cachedEnemyKingLoc.y != cachedEnemyY) {
        cachedEnemyKingLoc = new MapLocation(cachedEnemyX, cachedEnemyY);
      }
      lastKingLocRound = r;
    }

    // Focus fire target (slots 5-6) - check staleness FIRST to skip read if stale
    int focusRound = rc.readSharedArray(6);
    // Handle uninitialized case (focusRound == 0) and wraparound
    // If focusRound is 0 and r > FOCUS_FIRE_STALE_ROUNDS, treat as stale
    boolean focusNotStale =
        (focusRound > 0) && (((r & 0x3FF) - focusRound + 1024) % 1024 <= FOCUS_FIRE_STALE_ROUNDS);
    if (focusNotStale) {
      int focusPacked = rc.readSharedArray(5);
      if (focusPacked != 0) {
        // Decode 5-bit packed coords (multiply by 2 to restore precision)
        int fx = (focusPacked & 0x1F) << 1;
        int fy = ((focusPacked >> 5) & 0x1F) << 1;
        // HP no longer packed - will be sensed when needed
        if (cachedFocusTarget == null || cachedFocusTarget.x != fx || cachedFocusTarget.y != fy) {
          cachedFocusTarget = new MapLocation(fx, fy);
        }
        cachedFocusTargetHP = 0; // Unknown until sensed
        cachedFocusTargetRound = focusRound;
      } else {
        cachedFocusTarget = null;
        cachedFocusTargetHP = 0;
        cachedFocusTargetRound = -100;
      }
    } else {
      cachedFocusTarget = null;
      cachedFocusTargetHP = 0;
      cachedFocusTargetRound = -100;
    }

    // Phase (slot 7)
    int phasePacked = rc.readSharedArray(7);
    cachedGamePhase = phasePacked & 0x3;
    cachedAttackerRatio = (phasePacked >> 2) & 0xF;
    if (cachedAttackerRatio == 0) {
      cachedAttackerRatio = EARLY_ATTACKER_RATIO;
    }

    // Enemy ring buffer (slots 8-11) - decode 5-bit packed coords
    for (int i = 0; i < 4; i++) {
      int packed = rc.readSharedArray(8 + i);
      if (packed != 0) {
        // Decode 5-bit packed coords (multiply by 2 to restore precision)
        cachedEnemyRingX[i] = (packed & 0x1F) << 1;
        cachedEnemyRingY[i] = ((packed >> 5) & 0x1F) << 1;
        cachedEnemyRingRound[i] = r; // Assume recent
      } else {
        cachedEnemyRingX[i] = -1;
        cachedEnemyRingY[i] = -1;
        cachedEnemyRingRound[i] = -100;
      }
    }

    // Retreat signal (slot 13)
    int retreatRound = rc.readSharedArray(13);
    cachedRetreatRound = retreatRound;
    cachedShouldRetreat = (retreatRound > 0 && (r - retreatRound) <= RETREAT_SIGNAL_STALE);

    // Detected strategy (slot 14)
    cachedDetectedStrategy = rc.readSharedArray(14) & 0x3;

    // Flank counts (slots 16-18)
    cachedCenterCount = rc.readSharedArray(16);
    cachedLeftCount = rc.readSharedArray(17);
    cachedRightCount = rc.readSharedArray(18);

    // Enemy king HP (slot 34)
    int enemyHP = rc.readSharedArray(34);
    if (enemyHP > 0) cachedEnemyKingHP = enemyHP;

    // Rally point (slot 35) - decode 5-bit packed coords
    int rallyPacked = rc.readSharedArray(35);
    if (rallyPacked > 0) {
      // Decode 5-bit packed coords (multiply by 2 to restore precision)
      int rx = (rallyPacked & 0x1F) << 1;
      int ry = ((rallyPacked >> 5) & 0x1F) << 1;
      if (cachedRallyPoint == null || cachedRallyPoint.x != rx || cachedRallyPoint.y != ry) {
        cachedRallyPoint = new MapLocation(rx, ry);
      }
    }

    // Emergency level (slot 36)
    cachedEmergencyLevel = rc.readSharedArray(36) & 0x3;

    // All-in signal (slot 39)
    cachedAllInRound = rc.readSharedArray(39);
    cachedAllInActive = (cachedAllInRound > 0 && (r - cachedAllInRound) <= ALL_IN_SIGNAL_DURATION);

    // Enemy king damage tracking (slot 41-42)
    cachedDamageToEnemyKing = rc.readSharedArray(41);
    int confirmedHP = rc.readSharedArray(42);
    if (confirmedHP > 0) {
      // Use confirmed HP if available, otherwise estimate from damage
      lastConfirmedEnemyKingHP = confirmedHP;
      cachedEnemyKingHP = confirmedHP;
    } else {
      // Estimate HP from total damage dealt
      cachedEnemyKingHP = ENEMY_KING_STARTING_HP - cachedDamageToEnemyKing;
      if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
    }

    // Coordinated attack tracking (slot 43)
    cachedAttackersNearEnemy = rc.readSharedArray(43);

    // Race mode (slot 44)
    cachedRaceMode = rc.readSharedArray(44);

    // Read mine locations (slots 22-25) - only refresh every 20 rounds
    if (r - lastMineReadRound >= 20) {
      cachedNumMines = rc.readSharedArray(40);
      if (cachedNumMines > MAX_TRACKED_MINES) cachedNumMines = MAX_TRACKED_MINES;
      for (int i = 0; i < cachedNumMines; i++) {
        int packed = rc.readSharedArray(22 + i);
        if (packed > 0) {
          // Decode 5-bit packed coords (multiply by 2 to restore precision)
          int mx = (packed & 0x1F) << 1;
          int my = ((packed >> 5) & 0x1F) << 1;
          if (cachedMineLocations[i] == null
              || cachedMineLocations[i].x != mx
              || cachedMineLocations[i].y != my) {
            cachedMineLocations[i] = new MapLocation(mx, my);
          }
        }
      }
      lastMineReadRound = r;
    }
  }

  // ================================================================
  // ENEMY RING BUFFER (slots 8-11)
  // ================================================================

  private static int enemyRingWriteIndex = 0;

  private static void writeEnemyToRingBuffer(RobotController rc, MapLocation enemyLoc, int round)
      throws GameActionException {
    // BATTLECODE 2026: Shared array max value is 1023 (10 bits)
    // Pack: (x >> 1) (5 bits) | ((y >> 1) << 5) (5 bits) = 10 bits max = 1023 max value
    int x = (enemyLoc.x >> 1) & 0x1F; // 5 bits (0-31)
    int y = (enemyLoc.y >> 1) & 0x1F; // 5 bits (0-31)
    int packed = x | (y << 5);
    // Max value: 31 + (31 << 5) = 31 + 992 = 1023 ✓ (exactly at limit)
    rc.writeSharedArray(8 + enemyRingWriteIndex, packed);
    enemyRingWriteIndex = (enemyRingWriteIndex + 1) & 3; // Circular buffer
  }

  // ================================================================
  // PREDICTIVE TARGETING
  // ================================================================

  /**
   * Predict where an enemy will be based on recent sightings. Uses velocity estimation from ring
   * buffer.
   */
  // Reusable MapLocation for prediction to avoid allocation
  private static MapLocation predictedLoc = null;

  private static MapLocation predictEnemyPosition(MapLocation currentLoc, int currentRound) {
    // Find two most recent sightings of enemies near this location
    int bestIdx1 = -1, bestIdx2 = -1;
    int bestRound1 = -100, bestRound2 = -100;

    for (int i = 0; i < 4; i++) {
      if (cachedEnemyRingX[i] < 0) continue;
      int rx = cachedEnemyRingX[i];
      int ry = cachedEnemyRingY[i];
      int rr = cachedEnemyRingRound[i];

      // Check if this sighting is near current enemy (within 5 tiles)
      int dx = Math.abs(rx - currentLoc.x);
      int dy = Math.abs(ry - currentLoc.y);
      if (dx <= 5 && dy <= 5) {
        if (rr > bestRound1) {
          bestRound2 = bestRound1;
          bestIdx2 = bestIdx1;
          bestRound1 = rr;
          bestIdx1 = i;
        } else if (rr > bestRound2) {
          bestRound2 = rr;
          bestIdx2 = i;
        }
      }
    }

    // If we have two sightings, estimate velocity
    if (bestIdx1 >= 0 && bestIdx2 >= 0 && bestRound1 != bestRound2) {
      int x1 = cachedEnemyRingX[bestIdx2];
      int y1 = cachedEnemyRingY[bestIdx2];
      int x2 = cachedEnemyRingX[bestIdx1];
      int y2 = cachedEnemyRingY[bestIdx1];
      int dt = bestRound1 - bestRound2;

      if (dt > 0) {
        // Velocity per round
        int vx = (x2 - x1) / dt;
        int vy = (y2 - y1) / dt;

        // Predict 1-2 rounds ahead
        int predictX = currentLoc.x + vx;
        int predictY = currentLoc.y + vy;

        // Clamp to map bounds
        if (predictX < 0) predictX = 0;
        if (predictX >= mapWidth) predictX = mapWidth - 1;
        if (predictY < 0) predictY = 0;
        if (predictY >= mapHeight) predictY = mapHeight - 1;

        // Only allocate if position changed
        if (predictedLoc == null || predictedLoc.x != predictX || predictedLoc.y != predictY) {
          predictedLoc = new MapLocation(predictX, predictY);
        }
        return predictedLoc;
      }
    }

    // Fall back to current position (avoid allocation)
    return currentLoc;
  }

  // ================================================================
  // OVERKILL PREVENTION
  // ================================================================

  /**
   * Check if attacking this target would be overkill. Returns true if we should NOT attack (let
   * allies finish it).
   */
  private static boolean isOverkill(RobotController rc, RobotInfo target, RobotInfo[] allies) {
    // Early exit if no allies
    if (allies.length == 0) return false;

    int targetHP = target.getHealth();
    if (targetHP > OVERKILL_HP_THRESHOLD) {
      return false; // Not low enough HP to worry about overkill
    }

    // Count allies adjacent to this target
    MapLocation targetLoc = target.getLocation();
    int adjacentAllies = 0;
    for (int i = allies.length - 1; i >= 0; i--) {
      if (allies[i].getLocation().distanceSquaredTo(targetLoc) <= 2) {
        adjacentAllies++;
      }
    }

    // If expected damage from allies >= target HP, don't attack
    int expectedDamage = adjacentAllies * 10;
    return expectedDamage >= targetHP;
  }

  // ================================================================
  // MAIN LOOP
  // ================================================================

  public static void run(RobotController rc) throws GameActionException {
    computeMapSize(rc);

    while (true) {
      try {
        if (rc.getType().isRatKingType()) {
          runKing(rc);
        } else {
          runBabyRat(rc);
        }
      } catch (Exception e) {
        if (DEBUG) e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ================================================================
  // KING BEHAVIOR
  // ================================================================

  private static int spawnCount = 0;
  private static int trapCount = 0;
  private static int catTrapCount = 0;
  private static int ratTrapCount = 0;
  private static int chokepointTrapCount = 0;
  private static int lastKingX = -1, lastKingY = -1;
  private static int lastSpawnRound = 0;
  // Wave spawning tracking
  private static int lastWaveStartRound = 0; // Round when current wave started
  private static int waveSpawnedCount = 0; // Rats spawned in current wave

  /**
   * Helper method for staggered wave spawning. Returns true if we can spawn a rat in the current
   * wave.
   */
  private static boolean canSpawnInWave(int round, int waveSize, int waveCooldown) {
    if (round == 1) {
      // First round - start first wave
      lastWaveStartRound = round;
      waveSpawnedCount = 0;
      if (DEBUG) System.out.println("NEW_WAVE:" + round + ":starting wave 1");
      return true;
    } else if (waveSpawnedCount < waveSize) {
      // Current wave not complete - continue spawning
      return true;
    } else if (round - lastWaveStartRound >= waveCooldown) {
      // Wave cooldown elapsed - start new wave
      lastWaveStartRound = round;
      waveSpawnedCount = 0;
      if (DEBUG) System.out.println("NEW_WAVE:" + round + ":starting new wave");
      return true;
    }
    return false;
  }

  private static int totalEnemiesSeen = 0;
  private static int catDamageDealt = 0;
  private static int numKnownMines = 0;
  private static MapLocation[] knownMineLocations = new MapLocation[MAX_TRACKED_MINES];

  private static void runKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    if (PROFILE) profStartTurn();
    int cheese = rc.getGlobalCheese();
    MapLocation me = rc.getLocation();
    int kingHP = rc.getHealth();

    // DETAILED DEBUG: Log EVERY round for Team B analysis
    if (DEBUG) {
      System.out.println(
          "KING:"
              + round
              + ":cheese="
              + cheese
              + ":HP="
              + kingHP
              + ":spawned="
              + spawnCount
              + ":emergency="
              + cachedEmergencyLevel
              + ":phase="
              + cachedGamePhase
              + ":atkRatio="
              + cachedAttackerRatio
              + ":enemyKingHP="
              + cachedEnemyKingHP
              + ":myPos="
              + me
              + ":enemyPos="
              + cachedEnemyKingLoc);
    }

    // DEBUG: Log map size and team on round 1
    if (DEBUG && round == 1) {
      System.out.println(
          "INIT:"
              + round
              + ":mapSize="
              + mapSize
              + ":mapW="
              + mapWidth
              + ":mapH="
              + mapHeight
              + ":myPos="
              + me
              + ":enemyPos="
              + (mapWidth - 1 - me.x)
              + ","
              + (mapHeight - 1 - me.y));
    }

    // DEBUG: Log every round for first 30 rounds to track early game fully
    if (DEBUG && round <= 30) {
      System.out.println(
          "EARLY:"
              + round
              + ":cheese="
              + cheese
              + ":spawned="
              + spawnCount
              + ":traps="
              + ratTrapCount
              + ":cost="
              + rc.getCurrentRatCost());
    }

    // Write our position
    if (me.x != lastKingX || me.y != lastKingY) {
      rc.writeSharedArray(0, me.x);
      rc.writeSharedArray(1, me.y);
      lastKingX = me.x;
      lastKingY = me.y;
    }

    // Calculate enemy king position FIRST (before trap placement)
    // This must happen before any code that uses cachedEnemyKingLoc
    if (round == 1) {
      int enemyX = mapWidth - 1 - me.x;
      int enemyY = mapHeight - 1 - me.y;
      rc.writeSharedArray(2, enemyX);
      rc.writeSharedArray(3, enemyY);
      // Update local cache immediately so trap placement uses correct position
      cachedEnemyX = enemyX;
      cachedEnemyY = enemyY;
      cachedEnemyKingLoc = new MapLocation(enemyX, enemyY);
    }

    boolean smallMap = mapSize == 0;
    int initialSpawnTarget = smallMap ? SMALL_MAP_INITIAL_SPAWN : INITIAL_SPAWN_COUNT;
    int cheeseReserve = smallMap ? SMALL_MAP_KING_RESERVE : KING_CHEESE_RESERVE;
    int collectorMin = smallMap ? SMALL_MAP_COLLECTOR_MIN : COLLECTOR_MINIMUM;
    int trapTarget = smallMap ? SMALL_MAP_RAT_TRAP_TARGET : RAT_TRAP_EARLY_TARGET;

    // Spawning logic with STAGGERED WAVE SPAWNING to prevent bunching
    // SMALL MAPS: Spawn ALL initial rats IMMEDIATELY on round 1 - games are decided quickly!
    // MEDIUM/LARGE MAPS: Spawn rats in waves with map-specific sizes and cooldowns
    int waveSize;
    int waveCooldown;
    if (smallMap) {
      waveSize = SMALL_MAP_WAVE_SIZE; // 5 rats per wave
      waveCooldown = SMALL_MAP_WAVE_COOLDOWN; // 2 rounds between waves
    } else if (mapSize == 1) {
      waveSize = MEDIUM_MAP_WAVE_SIZE; // 4 rats per wave
      waveCooldown = MEDIUM_MAP_WAVE_COOLDOWN; // 3 rounds between waves
    } else {
      waveSize = WAVE_SIZE; // 3 rats per wave
      waveCooldown = WAVE_COOLDOWN_ROUNDS; // 5 rounds between waves
    }

    if (spawnCount < initialSpawnTarget) {
      // SMALL/MEDIUM MAP OPTIMIZATION: Spawn ALL initial rats immediately (no wave system)
      // Games are decided quickly on small/medium maps, so we need rats out ASAP
      if (smallMap || mapSize == 1) {
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + cheeseReserve) {
          if (spawnRat(rc, me)) {
            if (DEBUG && spawnCount == 1) {
              // Only log wave params on first spawn to reduce verbosity
              System.out.println(
                  "INSTANT_SPAWN:" + round + ":rat#" + spawnCount + ":mapSize=" + mapSize);
            } else if (DEBUG) {
              System.out.println("SPAWN:" + round + ":rat#" + spawnCount);
            }
          }
        }
      } else if (canSpawnInWave(round, waveSize, waveCooldown)) {
        // LARGE MAPS ONLY: Use wave spawning to prevent bunching
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + cheeseReserve) {
          if (spawnRat(rc, me)) {
            waveSpawnedCount++;
            if (DEBUG && spawnCount == 1) {
              // Only log wave params on first spawn to reduce verbosity
              System.out.println(
                  "WAVE_SPAWN:"
                      + round
                      + ":rat#"
                      + spawnCount
                      + ":waveSize="
                      + waveSize
                      + ":cooldown="
                      + waveCooldown);
            } else if (DEBUG) {
              System.out.println("SPAWN:" + round + ":rat#" + spawnCount);
            }
          }
        }
      }
    }
    // Only place traps after spawning some rats
    if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget && spawnCount >= 3) {
      placeDefensiveTrapsTowardEnemy(rc, me);
    }

    // Cat traps (cooperation)
    if (!smallMap
        && cachedIsCooperation
        && round >= 5
        && round <= CAT_TRAP_END_ROUND
        && catTrapCount < CAT_TRAP_TARGET) {
      placeCatTraps(rc, me);
    }

    // CHOKEPOINT TRAPS: Place traps at narrow passages toward enemy
    if (!smallMap
        && round > RAT_TRAP_EARLY_WINDOW
        && (round % CHOKEPOINT_TRAP_INTERVAL) == 0
        && chokepointTrapCount < CHOKEPOINT_TRAP_MAX
        && rc.isActionReady()) {
      placeChokepointTrap(rc, me);
    }

    // Count army once for both spawning, retreat coordination, and flank tracking
    int[] armyCounts = countArmyAndCollectors(rc);
    int armySize = armyCounts[0];
    int collectors = armyCounts[1];
    int ourTotalHP = armyCounts[2];
    int centerCount = armyCounts[3];
    int leftCount = armyCounts[4];
    int rightCount = armyCounts[5];

    // Write flank counts to shared array (slots 16-18)
    rc.writeSharedArray(16, centerCount);
    rc.writeSharedArray(17, leftCount);
    rc.writeSharedArray(18, rightCount);

    // Track our king HP for race logic
    cachedOurKingHP = kingHP;

    // DEBUG: Log army composition more frequently
    if (DEBUG && (round & 15) == 0) {
      System.out.println(
          "ARMY:"
              + round
              + ":total="
              + armySize
              + ":collectors="
              + collectors
              + ":HP="
              + ourTotalHP
              + ":center="
              + centerCount
              + ":left="
              + leftCount
              + ":right="
              + rightCount);
    }

    // NOTE: Initial spawn logic moved to staggered wave section above
    // Continuous spawning after initial phase
    if (spawnCount >= initialSpawnTarget) {
      // Continuous spawning with army health awareness
      int cost = rc.getCurrentRatCost();
      boolean emergencyMode = armySize < MIN_ARMY_SIZE;
      boolean healthyArmy =
          armySize
              >= HEALTHY_ARMY_SIZE; // EMERGENCY SPAWN LIMITING: Don't spawn if cheese critically
      // low
      int emergencyLevel = cachedEmergencyLevel;
      boolean spawnBlocked = (cheese < EMERGENCY_SPAWN_STOP_THRESHOLD && emergencyLevel >= 2);

      // ALL-IN MODE: Stop spawning to commit everything to the attack
      boolean allInActive = cachedAllInActive;
      if (allInActive) {
        if (DEBUG) System.out.println("ALL_IN_NO_SPAWN:" + round);
      } else if (spawnBlocked) {
        // Don't spawn - need to save cheese for king survival
        if (DEBUG && (round % 5) == 0)
          System.out.println(
              "SPAWN_BLOCKED:" + round + ":cheese=" + cheese + ":emergency=" + emergencyLevel);
      } else {
        int continuousReserve;
        if (emergencyMode) {
          continuousReserve = EMERGENCY_SPAWN_RESERVE;
        } else if (emergencyLevel >= 1) {
          // Reduce spawning in economy warning
          continuousReserve = 300;
        } else if (smallMap) {
          continuousReserve = SMALL_MAP_CONTINUOUS_RESERVE;
        } else if (mapSize == 1) {
          continuousReserve = MEDIUM_MAP_CONTINUOUS_RESERVE;
        } else {
          continuousReserve = CONTINUOUS_SPAWN_RESERVE;
        }

        int spawnCooldown;
        if (emergencyMode) {
          spawnCooldown = 1;
        } else if (smallMap) {
          spawnCooldown = 1;
        } else if (mapSize == 1) {
          spawnCooldown = MEDIUM_MAP_SPAWN_COOLDOWN;
        } else if (healthyArmy) {
          spawnCooldown = SPAWN_COOLDOWN_ROUNDS + 1;
        } else {
          spawnCooldown = SPAWN_COOLDOWN_ROUNDS;
        }
        boolean cooldownReady = (round - lastSpawnRound) >= spawnCooldown;

        if (cheese > cost + continuousReserve && cooldownReady) {
          if (spawnRat(rc, me)) {
            lastSpawnRound = round;
          }
        }
      }
    }

    // Defensive structures
    if (spawnCount >= 10 && cheese > 300) {
      for (int i = 0; i < DIRS_LEN; i++) {
        Direction dir = DIRS[i];
        int dx = DIR_DX[i] << 1;
        int dy = DIR_DY[i] << 1;
        MapLocation loc = me.translate(dx, dy);
        if (rc.getDirt() < 4 && rc.canPlaceDirt(loc)) {
          rc.placeDirt(loc);
          return;
        }
        if (trapCount < 5 && rc.canPlaceRatTrap(loc)) {
          rc.placeRatTrap(loc);
          trapCount++;
          return;
        }
      }
    }

    // SENSE THREATS
    int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] neutrals = rc.senseNearbyRobots(CAT_KING_FLEE_DIST_SQ, Team.NEUTRAL);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcBefore;
    RobotInfo nearbyCat = null;
    for (int i = neutrals.length - 1; i >= 0; i--) {
      if (neutrals[i].getType() == UnitType.CAT) {
        nearbyCat = neutrals[i];
        break;
      }
    } // PRIORITY CAT EVASION: If cat is close, flee FIRST before anything else!
    // This ensures king survives by prioritizing evasion over attacking
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);

      // REACTIVE CAT TRAP: Place ONE trap between us and the approaching cat
      if (distToCat <= 64 && rc.isActionReady()) { // Cat within 8 tiles
        Direction toCat = me.directionTo(catLoc);
        if (toCat != Direction.CENTER) {
          boolean trapPlaced = false;
          // Try direct path first, then adjacent directions
          Direction[] trapDirs = {toCat, toCat.rotateLeft(), toCat.rotateRight()};
          for (int d = 0; d < 3 && !trapPlaced; d++) {
            Direction dir = trapDirs[d];
            for (int dist = 2; dist <= 4 && !trapPlaced; dist++) {
              MapLocation trapLoc = me.translate(dir.dx * dist, dir.dy * dist);
              if (rc.canPlaceCatTrap(trapLoc)) {
                rc.placeCatTrap(trapLoc);
                catTrapCount++;
                trapPlaced = true;
                if (DEBUG) {
                  System.out.println(
                      "CAT_TRAP_REACTIVE:" + round + ":loc=" + trapLoc + ":catDist=" + distToCat);
                }
              }
            }
          }
        }
      }

      // If cat is very close (within 6 tiles), flee IMMEDIATELY - skip attacking!
      if (distToCat <= 36) {
        Direction awayFromCat = catLoc.directionTo(me);
        if (awayFromCat == Direction.CENTER) awayFromCat = DIRS[round & 7];
        if (DEBUG) {
          System.out.println(
              "CAT_PRIORITY_FLEE:" + round + ":catDist=" + distToCat + ":dir=" + awayFromCat);
        }
        if (kingFleeMove(rc, awayFromCat)) {
          if (PROFILE) profEndTurn(round, rc.getID(), true);
          return;
        }
      }
    }

    bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(25, cachedEnemyTeam);
    if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcBefore;

    int enemyCount = 0;
    int totalEnemyHP = 0;
    RobotInfo closestEnemy = null;
    int closestDist = Integer.MAX_VALUE;
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType()) {
        enemyCount++;
        totalEnemyHP += enemy.getHealth();
        int dist = me.distanceSquaredTo(enemy.getLocation());
        if (dist < closestDist) {
          closestDist = dist;
          closestEnemy = enemy;
        }
        // Write to enemy ring buffer
        writeEnemyToRingBuffer(rc, enemy.getLocation(), round);
        totalEnemiesSeen++;
      } else if (enemy.getType().isRatKingType()) {
        // ENEMY KING SPOTTED - confirm HP and update tracking
        int actualEnemyKingHP = enemy.getHealth();
        confirmEnemyKingHP(rc, actualEnemyKingHP, round);

        // Update enemy king position
        MapLocation enemyKingLoc = enemy.getLocation();
        if (cachedEnemyKingLoc == null || !cachedEnemyKingLoc.equals(enemyKingLoc)) {
          rc.writeSharedArray(2, enemyKingLoc.x);
          rc.writeSharedArray(3, enemyKingLoc.y);
          cachedEnemyKingLoc = enemyKingLoc;
        }

        if (DEBUG) {
          System.out.println(
              "ENEMY_KING_SPOTTED:" + round + ":HP=" + actualEnemyKingHP + ":loc=" + enemyKingLoc);
        }
      }
    }

    // Update total enemies seen (slot 15)
    rc.writeSharedArray(15, totalEnemiesSeen);

    // ADAPTIVE STRATEGY DETECTION
    if (round == RUSH_DETECTION_ROUND) {
      if (totalEnemiesSeen >= RUSH_ENEMY_THRESHOLD) {
        rc.writeSharedArray(14, STRATEGY_ANTI_RUSH);
        cachedDetectedStrategy = STRATEGY_ANTI_RUSH;
        if (DEBUG) System.out.println("STRATEGY_DETECTED:" + round + ":ANTI_RUSH");
      }
    }
    if (round == TURTLE_DETECTION_ROUND && cachedDetectedStrategy == STRATEGY_NORMAL) {
      if (totalEnemiesSeen < TURTLE_ENEMY_THRESHOLD) {
        rc.writeSharedArray(14, STRATEGY_ANTI_TURTLE);
        cachedDetectedStrategy = STRATEGY_ANTI_TURTLE;
        if (DEBUG) System.out.println("STRATEGY_DETECTED:" + round + ":ANTI_TURTLE");
      }
    }

    // King under attack flag
    boolean kingUnderAttack = (enemyCount >= 2 || closestDist <= 9);
    rc.writeSharedArray(4, kingUnderAttack ? 1 : 0);

    // ARMY STRENGTH & RETREAT COORDINATION (reuse armyCounts from above)
    if (enemyCount > 0 && totalEnemyHP > 0) {
      int hpRatio = (ourTotalHP * 100) / (ourTotalHP + totalEnemyHP);
      rc.writeSharedArray(12, hpRatio);

      // Retreat signal if our HP ratio < threshold
      if (hpRatio < RETREAT_HP_RATIO_THRESHOLD) {
        rc.writeSharedArray(13, round);
        if (DEBUG) System.out.println("RETREAT_SIGNAL:" + round + ":ratio=" + hpRatio);
      }
    }

    // FOCUS FIRE
    if (enemyCount > 0) {
      RobotInfo focusTarget = selectFocusTarget(enemies, me);
      if (focusTarget != null) {
        writeFocusTarget(rc, focusTarget, round);
      }
    } else {
      rc.writeSharedArray(5, 0);
    }

    // ECONOMY EMERGENCY DETECTION
    updateEmergencyLevel(rc, round, cheese);

    // CHEESE MINE TRACKING - scan for mines and broadcast
    updateMineBroadcast(rc, me, round);

    // LISTEN FOR ENEMY KING POSITION SQUEAKS FROM BABY RATS
    // Baby rats squeak type 1 (enemy king position) when they see the enemy king.
    // The king must listen for these squeaks and update slots 2-3 so all rats
    // have the FRESH enemy king position, not the stale round-1 cached position.
    // This is critical for Team B which may have stale cached position.
    listenForEnemyKingPositionSqueaks(rc, round);

    // COUNT ATTACKERS NEAR ENEMY KING - using direct vision only
    // (Self-reporting squeaks removed - SQUEAK_RADIUS_SQUARED=25 is too short range)
    int attackersNearEnemy = countAttackersNearEnemy(rc, round);
    rc.writeSharedArray(43, attackersNearEnemy);
    cachedAttackersNearEnemy = attackersNearEnemy;

    // BOTH-KINGS-LOW RACE LOGIC
    updateRaceMode(rc, round, kingHP, armySize);

    // ALL-IN DETECTION - check if we should trigger all-in attack
    checkAndBroadcastAllIn(rc, round, cheese, armySize);

    // PHASE-BASED STRATEGY
    updateAndBroadcastPhase(rc, round, cheese);

    // GAME THEORY: Backstab decision (cooperation mode only)
    if (cachedIsCooperation
        && round >= BACKSTAB_MINIMUM_ROUND
        && (round % BACKSTAB_CHECK_INTERVAL) == 0) {
      // Simple backstab check: if we've done significant cat damage and have army advantage
      int coopScore = (catDamageDealt * 50) / (catDamageDealt + 1000); // Simplified
      int backstabScore = 50; // Assume we win king battle
      if (backstabScore > coopScore + BACKSTAB_CONFIDENCE_THRESHOLD) {
        // Could switch to backstab mode here - for now just log
        if (DEBUG)
          System.out.println(
              "BACKSTAB_RECOMMENDED:"
                  + round
                  + ":coop="
                  + coopScore
                  + " backstab="
                  + backstabScore);
      }
    }

    // REACTIVE TRAP PLACEMENT
    if (closestEnemy != null && closestDist <= REACTIVE_TRAP_DIST_SQ && rc.isActionReady()) {
      MapLocation enemyLoc = closestEnemy.getLocation();
      Direction toEnemy = me.directionTo(enemyLoc);
      if (toEnemy != Direction.CENTER) {
        for (int dist = 2; dist <= 3; dist++) {
          MapLocation trapLoc = me.translate(toEnemy.dx * dist, toEnemy.dy * dist);
          if (rc.canPlaceRatTrap(trapLoc)) {
            rc.placeRatTrap(trapLoc);
            ratTrapCount++;
            break;
          }
        }
      }
    }

    // UPDATE RALLY POINT: Strategic retreat position (between king and center)
    if ((round & 15) == 0) { // Every 16 rounds
      int rallyX = (me.x + cachedMapCenter.x) / 2;
      int rallyY = (me.y + cachedMapCenter.y) / 2;
      // BATTLECODE 2026: max value is 1023 (10 bits)
      // Pack: (x >> 1) (5 bits) | ((y >> 1) << 5) (5 bits) = 10 bits max = 1023
      int rallyPacked = ((rallyX >> 1) & 0x1F) | (((rallyY >> 1) & 0x1F) << 5);
      rc.writeSharedArray(35, rallyPacked);
    }

    // KING ATTACKS
    if (closestEnemy != null && rc.isActionReady()) {
      MapLocation enemyLoc = closestEnemy.getLocation();
      if (rc.canAttack(enemyLoc)) {
        rc.attack(enemyLoc);
        return;
      }
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation loc = enemy.getLocation();
          if (rc.canAttack(loc)) {
            rc.attack(loc);
            return;
          }
        } else if (enemy.getType().isRatKingType()) {
          // Track enemy king HP when we can see it
          confirmEnemyKingHP(rc, enemy.getHealth(), round);
          // Try to attack enemy king
          MapLocation kingCenter = enemy.getLocation();
          if (rc.canAttack(kingCenter)) {
            rc.attack(kingCenter);
            recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
            return;
          }
          // Try adjacent tiles
          for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
              if (dx == 0 && dy == 0) continue;
              MapLocation tile = kingCenter.translate(dx, dy);
              if (rc.canAttack(tile)) {
                rc.attack(tile);
                recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
                return;
              }
            }
          }
        }
      }
    }

    // CHASE OR FLEE
    if (closestEnemy != null) {
      MapLocation enemyLoc = closestEnemy.getLocation();
      if (enemyCount >= 3) {
        Direction awayFromEnemy = enemyLoc.directionTo(me);
        if (awayFromEnemy == Direction.CENTER) awayFromEnemy = DIRS[round & 7];
        if (kingFleeMove(rc, awayFromEnemy)) return;
      } else if (closestDist > 2) {
        Direction towardEnemy = me.directionTo(enemyLoc);
        if (towardEnemy != Direction.CENTER) {
          if (kingFleeMove(rc, towardEnemy)) return;
        }
      }
    }

    // FLEE FROM CATS (secondary flee - if cat is medium distance)
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      Direction awayFromCat = catLoc.directionTo(me);
      if (awayFromCat == Direction.CENTER) awayFromCat = DIRS[round & 7];
      if (kingFleeMove(rc, awayFromCat)) {
        if (PROFILE) profEndTurn(round, rc.getID(), true);
        return;
      }
    }

    // Normal movement - ALWAYS move when cat is near, otherwise only even rounds
    // This ensures the king keeps evading even if kingFleeMove() failed above
    boolean shouldMove = (round & 1) == 0 || nearbyCat != null;
    if (shouldMove) {
      if (enemyCount == 0 && nearbyCat == null) {
        if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget) {
          Direction awayFromEnemy;
          if (cachedEnemyKingLoc != null) {
            awayFromEnemy = cachedEnemyKingLoc.directionTo(me);
          } else {
            awayFromEnemy = cachedMapCenter.directionTo(me);
          }
          if (awayFromEnemy != Direction.CENTER && kingFleeMove(rc, awayFromEnemy)) return;
        }
        Direction facing = rc.getDirection();
        MapLocation ahead = rc.adjacentLocation(facing);
        if (rc.canRemoveDirt(ahead)) {
          rc.removeDirt(ahead);
          return;
        }
        if (rc.canMoveForward()) {
          rc.moveForward();
        }
      }
    } else if (rc.canTurn() && (round % 10) == 0) {
      Direction newDir = DIRS[(round / 10) & 7];
      rc.turn(newDir);
    }

    if (PROFILE) profEndTurn(round, rc.getID(), true);
  }

  private static boolean spawnRat(RobotController rc, MapLocation kingLoc)
      throws GameActionException {
    for (int i = 0; i < DIRS_LEN; i++) {
      Direction dir = DIRS[i];
      if (dir == Direction.CENTER) continue;
      int dx = DIR_DX[i];
      int dy = DIR_DY[i];
      for (int dist = 2; dist <= 4; dist++) {
        MapLocation loc = kingLoc.translate(dx * dist, dy * dist);
        if (rc.canBuildRat(loc)) {
          rc.buildRat(loc);
          spawnCount++;
          return true;
        }
      }
    }
    return false;
  }

  private static final int[] armyCountResult =
      new int[6]; // [army, collectors, totalHP, center, left, right]

  private static int[] countArmyAndCollectors(RobotController rc) throws GameActionException {
    int armyCount = 0;
    int collectorCount = 0;
    int totalHP = 0;
    int centerCount = 0;
    int leftCount = 0;
    int rightCount = 0;
    boolean small = mapSize == 0;
    RobotInfo[] team = rc.senseNearbyRobots(KING_VISION_SQ, cachedOurTeam);
    for (int i = team.length - 1; i >= 0; i--) {
      RobotInfo r = team[i];
      if (r.getType().isBabyRatType()) {
        armyCount++;
        totalHP += r.getHealth();
        int rid = r.getID();
        boolean isCollector = small ? (rid % 3 == 0) : ((rid & 1) == 1);
        if (isCollector) {
          collectorCount++;
        } else {
          // Count attackers by swarm role
          int swarmRole = rid % 5;
          if (swarmRole == 0) {
            centerCount++;
          } else if (swarmRole <= 2) {
            leftCount++;
          } else {
            rightCount++;
          }
        }
      }
    }
    armyCountResult[0] = armyCount;
    armyCountResult[1] = collectorCount;
    armyCountResult[2] = totalHP;
    armyCountResult[3] = centerCount;
    armyCountResult[4] = leftCount;
    armyCountResult[5] = rightCount;
    return armyCountResult;
  }

  private static RobotInfo selectFocusTarget(RobotInfo[] enemies, MapLocation kingLoc) {
    RobotInfo bestTarget = null;
    int bestScore = -1;
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (!enemy.getType().isBabyRatType()) continue;
      MapLocation enemyLoc = enemy.getLocation();
      int distToKing = kingLoc.distanceSquaredTo(enemyLoc);
      int hp = enemy.getHealth();
      int damage = 10;
      int cheese = enemy.getRawCheeseAmount();
      if (cheese > 0) damage += 5;
      int score = (damage * 100) / ((distToKing + 1) * (hp + 1));
      if (hp <= 30) score += 50;
      if (hp <= 15) score += 100;
      // Attack range bonus: prefer targets we can hit immediately
      if (distToKing <= 2) score += 40;
      else if (distToKing <= 5) score += 20;
      // RESOURCE DENIAL: High priority for enemies carrying cheese
      if (cheese > 0) score += CHEESE_CARRIER_PRIORITY_BONUS;
      // High priority if enemy near their king (about to deliver)
      if (cachedEnemyKingLoc != null) {
        int distToEnemyKing = enemyLoc.distanceSquaredTo(cachedEnemyKingLoc);
        if (distToEnemyKing <= 16) score += NEAR_KING_PRIORITY_BONUS;
      }
      if (score > bestScore) {
        bestScore = score;
        bestTarget = enemy;
      }
    }
    return bestTarget;
  }

  private static void writeFocusTarget(RobotController rc, RobotInfo target, int round)
      throws GameActionException {
    MapLocation loc = target.getLocation();
    // BATTLECODE 2026: Shared array max value is 1023 (10 bits)
    // Pack: (x >> 1) (5 bits) | ((y >> 1) << 5) (5 bits) = 10 bits max = 1023 max value
    int x = (loc.x >> 1) & 0x1F; // 5 bits (0-31)
    int y = (loc.y >> 1) & 0x1F; // 5 bits (0-31)
    int packed = x | (y << 5);
    // Max value: 31 + (31 << 5) = 31 + 992 = 1023 ✓ (exactly at limit)
    rc.writeSharedArray(5, packed);
    rc.writeSharedArray(6, round & 0x3FF); // Round number mod 1024 to fit in 10 bits
  }

  private static void updateEmergencyLevel(RobotController rc, int round, int cheese)
      throws GameActionException {
    // Calculate emergency level based on current cheese
    int emergencyLevel = 0;
    if (cheese < EMERGENCY_LEVEL_3_THRESHOLD) {
      emergencyLevel = 3; // CRITICAL: All collectors
    } else if (cheese < EMERGENCY_LEVEL_2_THRESHOLD) {
      emergencyLevel = 2; // HIGH: 80% collectors
    } else if (cheese < EMERGENCY_LEVEL_1_THRESHOLD) {
      emergencyLevel = 1; // WARNING: 60% collectors
    } // Track cheese income every INCOME_CHECK_INTERVAL rounds
    if ((round % INCOME_CHECK_INTERVAL) == 0 && round > 0) {
      if (lastCheeseAmount >= 0) { // Skip first check (not initialized)
        int income = cheese - lastCheeseAmount;

        // If income is very low but cheese seems okay, raise early warning
        if (income < MIN_INCOME_WARNING && emergencyLevel == 0 && cheese < 400) {
          emergencyLevel = 1; // Early warning - income is dropping
        }

        if (DEBUG && (round % 50) == 0) {
          System.out.println(
              "ECONOMY:"
                  + round
                  + ":cheese="
                  + cheese
                  + ":income="
                  + income
                  + ":emergency="
                  + emergencyLevel);
        }
      }
      lastCheeseAmount = cheese;
    }

    // Broadcast emergency level
    rc.writeSharedArray(36, emergencyLevel);
    cachedEmergencyLevel = emergencyLevel;
  }

  private static void updateAndBroadcastPhase(RobotController rc, int round, int cheese)
      throws GameActionException {
    int phase;
    int attackerRatio;

    // ECONOMY OVERRIDE: If emergency level is high, prioritize collectors
    int emergencyLevel = cachedEmergencyLevel;
    if (emergencyLevel >= 3) {
      // CRITICAL: All rats become collectors
      phase = PHASE_LATE;
      attackerRatio = 1; // 10% attackers, 90% collectors
      if (DEBUG) System.out.println("EMERGENCY_MODE:" + round + ":level=3:ALL_COLLECTORS");
    } else if (emergencyLevel >= 2) {
      // HIGH: 80% collectors
      phase = PHASE_LATE;
      attackerRatio = 2; // 20% attackers, 80% collectors
    } else if (emergencyLevel >= 1) {
      // WARNING: 60% collectors
      phase = PHASE_MID;
      attackerRatio = 4; // 40% attackers, 60% collectors
    } else if (cachedDetectedStrategy == STRATEGY_ANTI_RUSH) {
      // More defensive - fewer attackers, more collectors
      phase = PHASE_MID;
      attackerRatio = 5; // 50% attackers
    } else if (cachedDetectedStrategy == STRATEGY_ANTI_TURTLE) {
      // More aggressive - more attackers
      phase = PHASE_MID;
      attackerRatio = 8; // 80% attackers
    } else {
      // Use extended early phase for medium maps
      int earlyPhaseEnd = (mapSize == 1) ? MEDIUM_MAP_EARLY_PHASE_END_ROUND : EARLY_PHASE_END_ROUND;
      if (round < earlyPhaseEnd) {
        phase = PHASE_EARLY;
        // MAP-SPECIFIC ATTACKER RATIOS
        if (mapSize == 2) {
          attackerRatio = LARGE_MAP_COLLECTOR_RATIO; // 50% attackers on large maps
        } else if (mapSize == 1) {
          attackerRatio = MEDIUM_MAP_EARLY_ATTACKER_RATIO; // 60% attackers on medium maps
        } else {
          attackerRatio = EARLY_ATTACKER_RATIO; // 80% attackers on small maps
        }
      } else if (round < MID_PHASE_END_ROUND) {
        phase = PHASE_MID;
        attackerRatio = MID_ATTACKER_RATIO;
      } else {
        phase = PHASE_LATE;
        if (cheese < LOW_CHEESE_THRESHOLD) {
          attackerRatio = LATE_POOR_ATTACKER_RATIO;
        } else if (cheese > HIGH_CHEESE_THRESHOLD) {
          attackerRatio = LATE_RICH_ATTACKER_RATIO;
        } else {
          attackerRatio = LATE_NORMAL_ATTACKER_RATIO;
        }
      }
    }

    int packed = (phase & 0x3) | ((attackerRatio & 0xF) << 2);
    rc.writeSharedArray(7, packed);
  }

  // ================================================================
  // CHEESE MINE TRACKING
  // ================================================================

  private static void updateMineBroadcast(RobotController rc, MapLocation me, int round)
      throws GameActionException {
    if (round < MINE_CAMP_START_ROUND) return;
    if ((round % 10) != 0) return; // Only update every 10 rounds

    // Scan for cheese mines in vision
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(me, KING_VISION_SQ);
    for (int i = nearbyTiles.length - 1; i >= 0; i--) {
      if (nearbyTiles[i].hasCheeseMine()) {
        MapLocation mineLoc = nearbyTiles[i].getMapLocation();
        // Check if already tracked
        boolean alreadyTracked = false;
        for (int j = 0; j < numKnownMines; j++) {
          if (knownMineLocations[j] != null && knownMineLocations[j].equals(mineLoc)) {
            alreadyTracked = true;
            break;
          }
        }
        // Add new mine
        if (!alreadyTracked && numKnownMines < MAX_TRACKED_MINES) {
          knownMineLocations[numKnownMines] = mineLoc;
          // BATTLECODE 2026: max value is 1023 (10 bits)
          // Pack: (x >> 1) (5 bits) | ((y >> 1) << 5) (5 bits) = 10 bits max = 1023
          int packed = ((mineLoc.x >> 1) & 0x1F) | (((mineLoc.y >> 1) & 0x1F) << 5);
          rc.writeSharedArray(22 + numKnownMines, packed);
          numKnownMines++;
          rc.writeSharedArray(40, numKnownMines);
          if (DEBUG) System.out.println("MINE_DISCOVERED:" + round + ":" + mineLoc);
        }
      }
    }
  }

  // ================================================================
  // ALL-IN ATTACK COORDINATION
  // ================================================================

  // Reusable result array for parseEnemyKingSqueak to avoid allocation.
  // [0] = x coordinate, [1] = y coordinate, [2] = sender ID (0 if no valid squeak found)
  // WARNING: This array is returned directly and reused - callers must process the result
  // immediately before calling parseEnemyKingSqueak again!
  private static final int[] parsedSqueakResult = new int[3];

  /**
   * Parse enemy king position from squeaks. Shared helper used by both king and collectors.
   *
   * @param rc RobotController
   * @param excludeID ID to exclude from processing (typically self), or -1 to include all
   * @return parsedSqueakResult array: [x, y, senderID] if found, [0, 0, 0] if not found
   */
  private static int[] parseEnemyKingSqueak(RobotController rc, int excludeID)
      throws GameActionException {
    parsedSqueakResult[0] = 0;
    parsedSqueakResult[1] = 0;
    parsedSqueakResult[2] = 0;

    Message[] squeaks = rc.readSqueaks(-1);
    int len = squeaks.length;
    int limit = len < MAX_SQUEAKS_TO_READ ? len : MAX_SQUEAKS_TO_READ;

    for (int i = len - 1; i >= len - limit && i >= 0; i--) {
      Message msg = squeaks[i];
      // Skip our own squeaks if excludeID is set (use -1 to include all squeaks)
      // Note: Robot IDs in Battlecode are always positive (>0), so excludeID==0 won't match
      if (excludeID > 0 && msg.getSenderID() == excludeID) continue;

      int bytes = msg.getBytes();
      int type = (bytes >>> 28) & 0xF;

      // Type 1 = enemy king position squeak
      if (type == 1) {
        int x = (bytes >> 4) & 0xFFF;
        int y = (bytes >> 16) & 0xFFF;

        // Validate coordinates are on the map
        if (x >= 0 && x < mapWidth && y >= 0 && y < mapHeight) {
          parsedSqueakResult[0] = x;
          parsedSqueakResult[1] = y;
          parsedSqueakResult[2] = msg.getSenderID();
          return parsedSqueakResult;
        }
      }
    }
    return parsedSqueakResult;
  }

  /**
   * Listen for enemy king position squeaks from baby rats. When any rat sees the enemy king, they
   * squeak type 1 with the fresh position. The king listens for these squeaks and updates slots 2-3
   * so all rats have the current enemy king position.
   */
  private static void listenForEnemyKingPositionSqueaks(RobotController rc, int round)
      throws GameActionException {
    int[] parsed = parseEnemyKingSqueak(rc, -1); // King listens to all squeaks
    int x = parsed[0];
    int y = parsed[1];
    int senderID = parsed[2];

    if (senderID > 0) {
      // Valid squeak found - check if position is different from cached
      if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
        // Update shared array for all rats
        rc.writeSharedArray(2, x);
        rc.writeSharedArray(3, y);

        // Update local cache
        cachedEnemyX = x;
        cachedEnemyY = y;
        cachedEnemyKingLoc = new MapLocation(x, y);

        if (DEBUG) {
          System.out.println(
              "KING_RECEIVED_ENEMY_POS:"
                  + round
                  + ":from="
                  + senderID
                  + ":newPos="
                  + cachedEnemyKingLoc);
        }
      }
    }
  }

  /**
   * Collector version of squeak listening - stores heard enemy king positions for relay. Collectors
   * roam the map and may hear squeaks from assassins near the enemy king. They store the position
   * and relay it when they deliver cheese to our king.
   */
  private static void listenForEnemyKingPositionSqueaksCollector(
      RobotController rc, int round, int id) throws GameActionException {
    int[] parsed = parseEnemyKingSqueak(rc, id); // Exclude our own squeaks
    int x = parsed[0];
    int y = parsed[1];
    int senderID = parsed[2];

    if (senderID > 0) {
      // Valid squeak found - store for relay and update local cache
      if (heardEnemyKingLoc == null || heardEnemyKingLoc.x != x || heardEnemyKingLoc.y != y) {
        heardEnemyKingLoc = new MapLocation(x, y);
        heardEnemyKingRound = round;

        // ALSO update our local cache immediately - helps this collector too!
        if (cachedEnemyKingLoc == null || cachedEnemyKingLoc.x != x || cachedEnemyKingLoc.y != y) {
          cachedEnemyKingLoc = heardEnemyKingLoc;
          cachedEnemyX = x;
          cachedEnemyY = y;
        }

        if (DEBUG) {
          System.out.println(
              "COLLECTOR_HEARD_ENEMY_POS:"
                  + round
                  + ":"
                  + id
                  + ":from="
                  + senderID
                  + ":loc="
                  + heardEnemyKingLoc);
        }
      }
    }
  }

  /**
   * Count how many of our attackers are near the enemy king using direct vision. Note:
   * Self-reporting squeaks were removed because SQUEAK_RADIUS_SQUARED=25 (~5 tiles) is too short
   * range - attackers near enemy king can't squeak back to our king.
   */
  private static int countAttackersNearEnemy(RobotController rc, int round)
      throws GameActionException {
    int count = 0;

    // Count attackers we can directly see near enemy king
    if (cachedEnemyKingLoc != null) {
      RobotInfo[] allies = rc.senseNearbyRobots(KING_VISION_SQ, cachedOurTeam);
      for (int i = allies.length - 1; i >= 0; i--) {
        RobotInfo ally = allies[i];
        if (ally.getType().isBabyRatType()) {
          int aid = ally.getID();
          boolean isAttacker = (mapSize == 0) ? (aid % 3 != 0) : ((aid & 1) == 0);
          if (isAttacker) {
            int distToEnemy = ally.getLocation().distanceSquaredTo(cachedEnemyKingLoc);
            if (distToEnemy <= NEAR_ENEMY_KING_DIST_SQ) {
              count++;
            }
          }
        }
      }
    }

    if (DEBUG && count > 0) {
      System.out.println("ATTACKERS_NEAR_ENEMY:" + round + ":count=" + count);
    }
    return count;
  }

  /**
   * Update race mode when both kings are low HP. Calculates who wins the HP race and sets strategy
   * accordingly.
   */
  private static void updateRaceMode(RobotController rc, int round, int ourKingHP, int armySize)
      throws GameActionException {
    int enemyKingHP = cachedEnemyKingHP;

    // Only activate race logic when both kings are low
    if (ourKingHP >= BOTH_KINGS_LOW_THRESHOLD && enemyKingHP >= BOTH_KINGS_LOW_THRESHOLD) {
      // Both kings healthy - normal mode
      if (cachedRaceMode != 0) {
        rc.writeSharedArray(44, 0);
        cachedRaceMode = 0;
      }
      return;
    }

    // At least one king is low - calculate race
    // Estimate rounds to kill enemy king: enemyHP / (ourAttackers * 10)
    int ourAttackers = cachedAttackersNearEnemy;
    if (ourAttackers < 1) ourAttackers = 1;

    // Estimate enemy attackers from army ratio (rough estimate)
    int enemyAttackers = Math.max(1, armySize / 2); // Assume enemy has similar army

    int roundsToKillEnemy = enemyKingHP / (ourAttackers * 10);
    int roundsToKillUs = ourKingHP / (enemyAttackers * 10);

    int newRaceMode;
    if (roundsToKillEnemy < roundsToKillUs) {
      // We kill them first - go all-in attack mode
      newRaceMode = RACE_ATTACK_MODE;
      if (DEBUG)
        System.out.println(
            "RACE_ATTACK:"
                + round
                + ":ourHP="
                + ourKingHP
                + ":enemyHP="
                + enemyKingHP
                + ":killEnemy="
                + roundsToKillEnemy
                + ":killUs="
                + roundsToKillUs);
    } else if (roundsToKillUs < roundsToKillEnemy - 2) {
      // They kill us first (with margin) - full defense
      newRaceMode = RACE_DEFEND_MODE;
      if (DEBUG)
        System.out.println(
            "RACE_DEFEND:"
                + round
                + ":ourHP="
                + ourKingHP
                + ":enemyHP="
                + enemyKingHP
                + ":killEnemy="
                + roundsToKillEnemy
                + ":killUs="
                + roundsToKillUs);
    } else {
      // Close race - be aggressive (tie-breaker: attack)
      newRaceMode = RACE_ATTACK_MODE;
    }

    if (newRaceMode != cachedRaceMode) {
      rc.writeSharedArray(44, newRaceMode);
      cachedRaceMode = newRaceMode;
    }
  }

  private static void checkAndBroadcastAllIn(
      RobotController rc, int round, int cheese, int armySize) throws GameActionException {
    // Don't trigger all-in if already active or too early
    if (cachedAllInActive) return;
    if (round < ALL_IN_MIN_ROUND) return;
    if (cachedEmergencyLevel >= 2) return; // Don't all-in when starving

    // Check conditions for all-in
    boolean shouldAllIn = false;

    // Condition 1: Enemy king HP is low (using sophisticated tracking)
    int estimatedEnemyHP = cachedEnemyKingHP;
    if (estimatedEnemyHP > 0 && estimatedEnemyHP <= ALL_IN_HP_THRESHOLD) {
      if (armySize >= ALL_IN_MIN_ATTACKERS) {
        shouldAllIn = true;
        if (DEBUG)
          System.out.println(
              "ALL_IN_TRIGGER:"
                  + round
                  + ":enemyHP="
                  + estimatedEnemyHP
                  + ":damage="
                  + cachedDamageToEnemyKing);
      }
    }

    // Condition 2: Late game with army advantage (backup trigger)
    if (!shouldAllIn && round > 300 && armySize >= 10) {
      shouldAllIn = true;
      if (DEBUG) System.out.println("ALL_IN_TRIGGER:" + round + ":lateGame");
    }

    // Condition 3: We've dealt significant damage (>350 = enemy at <150 HP)
    if (!shouldAllIn && cachedDamageToEnemyKing >= (ENEMY_KING_STARTING_HP - ALL_IN_HP_THRESHOLD)) {
      if (armySize >= ALL_IN_MIN_ATTACKERS) {
        shouldAllIn = true;
        if (DEBUG)
          System.out.println(
              "ALL_IN_TRIGGER:" + round + ":damageThreshold=" + cachedDamageToEnemyKing);
      }
    }

    // Condition 4: Race mode says attack (both kings low, we win race)
    if (!shouldAllIn && cachedRaceMode == RACE_ATTACK_MODE) {
      shouldAllIn = true;
      if (DEBUG) System.out.println("ALL_IN_TRIGGER:" + round + ":raceMode");
    }

    if (shouldAllIn) {
      rc.writeSharedArray(39, round);
      cachedAllInActive = true;
      cachedAllInRound = round;
    }
  }

  // ================================================================
  // ENEMY KING HP TRACKING
  // ================================================================

  /**
   * Record damage dealt to enemy king. Called when a rat successfully attacks the enemy king.
   * Updates slot 41 (cumulative damage). ONLY KING CAN WRITE TO SHARED ARRAY.
   */
  private static void recordDamageToEnemyKing(RobotController rc, int damage)
      throws GameActionException {
    int currentDamage = cachedDamageToEnemyKing;
    int newDamage = currentDamage + damage;
    // Cap at enemy king starting HP (can't deal more damage than they have)
    if (newDamage > ENEMY_KING_STARTING_HP) newDamage = ENEMY_KING_STARTING_HP;

    // Only king can write to shared array
    if (rc.getType().isRatKingType()) {
      rc.writeSharedArray(41, newDamage);
    }
    // Update local cache for all rats
    cachedDamageToEnemyKing = newDamage;
    cachedEnemyKingHP = ENEMY_KING_STARTING_HP - newDamage;
    if (cachedEnemyKingHP < 0) cachedEnemyKingHP = 0;
    if (DEBUG) {
      System.out.println(
          "DAMAGE_TO_KING:"
              + rc.getRoundNum()
              + ":dealt="
              + damage
              + ":total="
              + newDamage
              + ":estHP="
              + cachedEnemyKingHP);
    }
  }

  /**
   * Called when we can see the enemy king directly. Updates confirmed HP. ONLY KING CAN WRITE TO
   * SHARED ARRAY.
   */
  private static void confirmEnemyKingHP(RobotController rc, int actualHP, int round)
      throws GameActionException {
    // Only update if this is newer information
    if (round > lastConfirmedHPRound) {
      lastConfirmedEnemyKingHP = actualHP;
      lastConfirmedHPRound = round;
      cachedEnemyKingHP = actualHP;

      // Recalibrate damage tracking based on actual HP
      int actualDamage = ENEMY_KING_STARTING_HP - actualHP;
      if (actualDamage > cachedDamageToEnemyKing) {
        // Enemy took more damage than we tracked (maybe from cat or other sources)
        cachedDamageToEnemyKing = actualDamage;
        // Only king can write to shared array
        if (rc.getType().isRatKingType()) {
          rc.writeSharedArray(41, actualDamage);
        }
      }

      // Store confirmed HP in slot 42 - only king can write
      if (rc.getType().isRatKingType()) {
        rc.writeSharedArray(42, actualHP);
      }

      if (DEBUG) {
        System.out.println(
            "CONFIRMED_KING_HP:" + round + ":actual=" + actualHP + ":damage=" + actualDamage);
      }
    }
  }

  /**
   * King flee movement - tries ALL 8 directions to escape, prioritizing directions that move away
   * from the threat. Only returns true after actually MOVING.
   *
   * <p>FIX: Previous version returned true after turning without moving, which caused the king to
   * get stuck rotating in place while the cat approached.
   */
  private static boolean kingFleeMove(RobotController rc, Direction awayDir)
      throws GameActionException {
    if (awayDir == Direction.CENTER) return false;

    // PRIORITY 1: Try to actually MOVE in order of best escape direction
    // Try all 8 directions, ordered by how well they move us away from threat
    Direction[] fleeDirs = {
      awayDir, // Best: directly away
      awayDir.rotateLeft(), // Good: 45° left of away
      awayDir.rotateRight(), // Good: 45° right of away
      awayDir.rotateLeft().rotateLeft(), // Okay: 90° left (perpendicular)
      awayDir.rotateRight().rotateRight(), // Okay: 90° right (perpendicular)
      awayDir.rotateLeft().rotateLeft().rotateLeft(), // Poor: 135° (slightly toward)
      awayDir.rotateRight().rotateRight().rotateRight(), // Poor: 135° (slightly toward)
      awayDir.opposite() // Last resort: toward threat (but still moving)
    };

    // Try each direction - prioritize MOVEMENT over everything
    for (Direction dir : fleeDirs) {
      if (dir == Direction.CENTER) continue;
      if (rc.canMove(dir)) {
        rc.move(dir);
        if (DEBUG) {
          System.out.println("KING_FLEE_MOVE:" + rc.getRoundNum() + ":dir=" + dir);
        }
        return true;
      }
    }

    // PRIORITY 2: If completely blocked, try removing obstacles in flee direction
    // Only do this if we couldn't move at all
    MapLocation ahead = rc.getLocation().add(awayDir);
    if (rc.canSenseLocation(ahead)) {
      // Try to remove dirt blocking escape route
      if (rc.canRemoveDirt(ahead)) {
        rc.removeDirt(ahead);
        if (DEBUG) {
          System.out.println("KING_FLEE_REMOVE_DIRT:" + rc.getRoundNum() + ":loc=" + ahead);
        }
        return true;
      }
      // Try to remove rat trap blocking escape route
      if (rc.canRemoveRatTrap(ahead)) {
        rc.removeRatTrap(ahead);
        if (DEBUG) {
          System.out.println("KING_FLEE_REMOVE_TRAP:" + rc.getRoundNum() + ":loc=" + ahead);
        }
        return true;
      }
    }

    // PRIORITY 3: Try removing obstacles in adjacent directions too
    for (int i = 1; i <= 2; i++) {
      Direction leftDir = awayDir;
      Direction rightDir = awayDir;
      for (int j = 0; j < i; j++) {
        leftDir = leftDir.rotateLeft();
        rightDir = rightDir.rotateRight();
      }

      MapLocation leftLoc = rc.getLocation().add(leftDir);
      if (rc.canSenseLocation(leftLoc) && rc.canRemoveDirt(leftLoc)) {
        rc.removeDirt(leftLoc);
        return true;
      }

      MapLocation rightLoc = rc.getLocation().add(rightDir);
      if (rc.canSenseLocation(rightLoc) && rc.canRemoveDirt(rightLoc)) {
        rc.removeDirt(rightLoc);
        return true;
      }
    }

    // PRIORITY 4 (LAST RESORT): Turn toward flee direction for next round
    // NOTE: This does NOT return true because we didn't actually escape!
    // The caller should know we failed to move.
    Direction facing = rc.getDirection();
    if (facing != awayDir && rc.canTurn()) {
      rc.turn(awayDir);
      if (DEBUG) {
        System.out.println("KING_FLEE_TURN_ONLY:" + rc.getRoundNum() + ":newFacing=" + awayDir);
      }
      // Return false - we turned but didn't escape!
      // This signals to caller that flee failed and they should try other options
    }

    if (DEBUG) {
      System.out.println("KING_FLEE_FAILED:" + rc.getRoundNum() + ":blocked in all directions");
    }
    return false;
  }

  private static final Direction[] TRAP_PRIORITY_DIRS = new Direction[8];

  private static void placeDefensiveTrapsTowardEnemy(RobotController rc, MapLocation me)
      throws GameActionException {
    Direction toEnemy;
    if (cachedEnemyKingLoc == null) {
      toEnemy = me.directionTo(cachedMapCenter);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    } else {
      toEnemy = me.directionTo(cachedEnemyKingLoc);
      if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    }
    TRAP_PRIORITY_DIRS[0] = toEnemy;
    TRAP_PRIORITY_DIRS[1] = toEnemy.rotateLeft();
    TRAP_PRIORITY_DIRS[2] = toEnemy.rotateRight();
    TRAP_PRIORITY_DIRS[3] = toEnemy.rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[4] = toEnemy.rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[5] = toEnemy.rotateLeft().rotateLeft().rotateLeft();
    TRAP_PRIORITY_DIRS[6] = toEnemy.rotateRight().rotateRight().rotateRight();
    TRAP_PRIORITY_DIRS[7] = toEnemy.opposite();

    for (int p = 0; p < 8; p++) {
      Direction dir = TRAP_PRIORITY_DIRS[p];
      if (dir == Direction.CENTER) continue;
      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 3; dist <= 4; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          ratTrapCount++;
          return;
        }
      }
    }
  }

  private static void placeCatTraps(RobotController rc, MapLocation me) throws GameActionException {
    // FIX: Place cat traps toward ENEMY, not center!
    // The cat comes from the enemy side, so we need traps between us and the enemy
    Direction towardEnemy;
    if (cachedEnemyKingLoc != null) {
      towardEnemy = me.directionTo(cachedEnemyKingLoc);
    } else {
      towardEnemy = me.directionTo(cachedMapCenter);
    }
    if (towardEnemy == Direction.CENTER) towardEnemy = Direction.NORTH;

    CAT_TRAP_PRIORITIES[0] = towardEnemy;
    CAT_TRAP_PRIORITIES[1] = towardEnemy.rotateLeft();
    CAT_TRAP_PRIORITIES[2] = towardEnemy.rotateRight();
    CAT_TRAP_PRIORITIES[3] = towardEnemy.rotateLeft().rotateLeft();
    CAT_TRAP_PRIORITIES[4] = towardEnemy.rotateRight().rotateRight();

    // Place cat traps at further distances (3-5 tiles) to catch approaching cats
    for (int p = 0; p < 5; p++) {
      Direction dir = CAT_TRAP_PRIORITIES[p];
      if (dir == Direction.CENTER) continue;
      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 3; dist <= 5; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapCount++;
          if (DEBUG) {
            System.out.println(
                "CAT_TRAP_PLACED:" + rc.getRoundNum() + ":loc=" + trapLoc + ":dir=" + dir);
          }
          return;
        }
      }
    }
  }

  // ================================================================
  // CHOKEPOINT ANALYSIS
  // ================================================================

  /**
   * Check if a location is a chokepoint (narrow passage). A chokepoint has CHOKEPOINT_MIN_BLOCKED
   * or more impassable neighbors, forcing enemies to pass through a limited area.
   */
  /**
   * Check if a location is a chokepoint using pre-sensed MapInfo. A chokepoint has
   * CHOKEPOINT_MIN_BLOCKED or more impassable neighbors.
   */
  private static boolean isChokepoint(RobotController rc, MapInfo info) throws GameActionException {
    if (!info.isPassable()) return false; // Can't place trap on impassable tile
    if (info.getTrap() != TrapType.NONE) return false; // Already has a trap

    MapLocation loc = info.getMapLocation();
    int blockedCount = 0;
    for (int i = 0; i < 8; i++) { // Skip CENTER (index 8)
      Direction dir = DIRS[i];
      MapLocation neighbor = loc.add(dir);
      if (!rc.onTheMap(neighbor)) {
        blockedCount++; // Off-map counts as blocked
      } else if (rc.canSenseLocation(neighbor)) {
        MapInfo neighborInfo = rc.senseMapInfo(neighbor);
        if (!neighborInfo.isPassable()) {
          blockedCount++;
        }
      }
    }
    return blockedCount >= CHOKEPOINT_MIN_BLOCKED;
  }

  /**
   * Find the best chokepoint to place a trap at. Prioritizes chokepoints that are: 1. On the path
   * between us and enemy king 2. Closer to the enemy approach direction
   */
  private static void placeChokepointTrap(RobotController rc, MapLocation me)
      throws GameActionException {
    if (cachedEnemyKingLoc == null) return;

    Direction toEnemy = me.directionTo(cachedEnemyKingLoc);
    if (toEnemy == Direction.CENTER) return;

    MapLocation bestChokepoint = null;
    int bestScore = -1;

    // Scan tiles in the direction toward enemy
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(me, CHOKEPOINT_SCAN_RADIUS_SQ);
    for (int i = nearbyTiles.length - 1; i >= 0; i--) {
      MapLocation tileLoc = nearbyTiles[i].getMapLocation();

      // Only consider tiles generally toward enemy (not behind us)
      Direction toTile = me.directionTo(tileLoc);
      if (toTile == Direction.CENTER) continue;
      if (toTile == toEnemy.opposite()) continue; // Skip tiles behind us

      // Check if it's a chokepoint (pass MapInfo to avoid re-sensing)
      if (!isChokepoint(rc, nearbyTiles[i])) continue;

      // Score: prefer tiles closer to enemy direction, not too close to king
      int distFromKing = me.distanceSquaredTo(tileLoc);
      if (distFromKing < 4) continue; // Don't place too close to king

      // Higher score for tiles aligned with enemy direction
      int alignmentBonus = 0;
      if (toTile == toEnemy) alignmentBonus = 30;
      else if (toTile == toEnemy.rotateLeft() || toTile == toEnemy.rotateRight())
        alignmentBonus = 20;
      else if (toTile == toEnemy.rotateLeft().rotateLeft()
          || toTile == toEnemy.rotateRight().rotateRight()) alignmentBonus = 10;

      // Score = alignment bonus + distance bonus (prefer mid-range)
      int score = alignmentBonus + (16 - Math.abs(distFromKing - 9)); // Sweet spot around dist 9

      if (score > bestScore) {
        bestScore = score;
        bestChokepoint = tileLoc;
      }
    }

    // Place trap at best chokepoint found
    if (bestChokepoint != null && rc.canPlaceRatTrap(bestChokepoint)) {
      rc.placeRatTrap(bestChokepoint);
      chokepointTrapCount++;
      if (DEBUG) {
        System.out.println(
            "CHOKEPOINT_TRAP:" + rc.getRoundNum() + ":" + bestChokepoint + ":score=" + bestScore);
      }
    }
  }

  // ================================================================
  // BABY RAT BEHAVIOR
  // ================================================================

  private static MapLocation targetCheese = null;
  private static int targetCheeseRound = -100;
  private static int lastTargetID = -1;
  private static int lastMineSqueakRound = -100;
  private static int lastSqueakID = -1;
  private static int lastBabyTrapRound = -100;
  private static int lastBabyTrapID = -1;

  private static void runBabyRat(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    if (PROFILE) profStartTurn();

    int bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    refreshCache(rc);
    if (PROFILE) profCache += Clock.getBytecodeNum() - bcBefore;

    if (rc.isBeingThrown() || rc.isBeingCarried()) {
      if (PROFILE) profEndTurn(round, id, false);
      return;
    }

    // Cache ID for moveTo to avoid repeated rc.getID() calls
    moveToLastID = id;

    // Reset kite state if different robot
    if (lastKiteID != id) {
      kiteState = KITE_STATE_APPROACH;
      kiteRetreatTurns = 0;
      lastAttackedLoc = null;
      lastKiteID = id;
    }

    // Role assignment with strategy adaptation
    // ALL-IN OVERRIDE: In all-in mode, everyone attacks
    // EMERGENCY OVERRIDE: In critical emergency, everyone collects
    int myRole;
    int emergencyLevel = cachedEmergencyLevel;
    boolean allInActive = cachedAllInActive;

    // Pre-compute isAssassin for role assignment (assassins NEVER become collectors)
    int assassinDivForRole = (mapSize == 2) ? LARGE_MAP_ASSASSIN_DIVISOR : ASSASSIN_DIVISOR;
    boolean isAssassinForRole = (id % assassinDivForRole) == 0;

    if (allInActive) {
      // ALL-IN MODE: Everyone attacks, no exceptions
      myRole = 0;
      if (DEBUG && (round % 20) == 0) System.out.println("ALL_IN_ATTACK:" + round + ":" + id);
    } else if (emergencyLevel >= 3 && !isAssassinForRole) {
      // CRITICAL: All rats become collectors EXCEPT assassins - they must reach enemy king!
      // FIX: Assassins never become collectors, even in emergency mode.
      // Without attackers reaching the enemy king, victory is impossible.
      myRole = 1;
      if (DEBUG && (round % 10) == 0)
        System.out.println("EMERGENCY_COLLECTOR:" + round + ":" + id + ":emerg=" + emergencyLevel);
    } else if (isAssassinForRole) {
      // ASSASSINS ALWAYS ATTACK - they are our only hope to damage the enemy king
      myRole = 0;
      if (DEBUG && emergencyLevel >= 2)
        System.out.println(
            "ASSASSIN_STAYS_ATTACKER:" + round + ":" + id + ":emerg=" + emergencyLevel);
    } else if (mapSize == 0) {
      // Small map: all attack for first 25 rounds
      // Allow all-attack during emergency levels 0-1, only stop at level 2+ (critical)
      if (round < SMALL_MAP_ALL_ATTACK_ROUNDS && emergencyLevel < 2) {
        myRole = 0;
        if (DEBUG && round == 1) System.out.println("SMALL_ALL_ATTACK:" + round + ":" + id);
      } else {
        myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
      }
    } else if (mapSize == 1) {
      // Medium map: all attack for first 30 rounds
      // LESSON: Emergency system was conflicting with all-attack phase!
      // emergencyLevel < 2 allows attack during mild warnings, stops only at critical
      if (round < MEDIUM_MAP_ALL_ATTACK_ROUNDS && emergencyLevel < 2) {
        myRole = 0;
        if (DEBUG && (round % 5) == 0)
          System.out.println("MED_ALL_ATTACK:" + round + ":" + id + ":emerg=" + emergencyLevel);
      } else {
        myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
        if (DEBUG && round == MEDIUM_MAP_ALL_ATTACK_ROUNDS) {
          System.out.println(
              "MED_PHASE_END:"
                  + round
                  + ":"
                  + id
                  + ":role="
                  + (myRole == 0 ? "ATK" : "COL")
                  + ":emerg="
                  + emergencyLevel);
        }
      }
    } else {
      myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
    }

    int health = rc.getHealth();
    MapLocation me = rc.getLocation();

    // DEBUG: Log role assignment for all rats in early game
    if (DEBUG && round <= 30) {
      System.out.println(
          "ROLE:"
              + round
              + ":"
              + id
              + ":"
              + (myRole == 0 ? "ATK" : "COL")
              + ":mapSize="
              + mapSize
              + ":emerg="
              + emergencyLevel
              + ":pos="
              + me
              + ":distToEnemy="
              + (cachedEnemyKingLoc != null ? me.distanceSquaredTo(cachedEnemyKingLoc) : -1));
    }

    bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo nearbyCat = (mapSize == 0) ? null : findNearbyCat(rc);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcBefore;

    boolean kingNeedsHelp = rc.readSharedArray(4) == 1;
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);

    // RETREAT CHECK: If retreat signal active, fall back to rally point (not king)
    // EXCEPTION: Assassins NEVER retreat - they always push to enemy king!
    if (cachedShouldRetreat && myRole == 0 && distToKing > 16 && !isAssassinForRole) {
      if (DEBUG) System.out.println("RETREAT:" + round + ":" + id + ":falling back");
      // Use rally point if available, otherwise fall back to king
      MapLocation retreatTarget = (cachedRallyPoint != null) ? cachedRallyPoint : cachedOurKingLoc;
      moveTo(rc, me, retreatTarget);
      if (PROFILE) profEndTurn(round, id, false);
      return;
    }

    // HP-BASED RETREAT: Wounded attackers (HP < 40) retreat to rally point
    // Exception: Don't retreat if defending king or very close to enemy king
    // EXCEPTION: Assassins NEVER retreat, even when wounded - they must reach enemy king!
    int distToEnemyKing =
        (cachedEnemyKingLoc != null) ? me.distanceSquaredTo(cachedEnemyKingLoc) : Integer.MAX_VALUE;
    if (myRole == 0
        && health < WOUNDED_HP_THRESHOLD
        && !kingNeedsHelp
        && distToEnemyKing > WOUNDED_RETREAT_EXCEPTION_DIST_SQ
        && !isAssassinForRole) {
      // RACE MODE OVERRIDE: In attack race mode, wounded rats still attack
      if (cachedRaceMode != RACE_ATTACK_MODE) {
        if (DEBUG) System.out.println("WOUNDED_RETREAT:" + round + ":" + id + ":HP=" + health);
        MapLocation retreatTarget =
            (cachedRallyPoint != null) ? cachedRallyPoint : cachedOurKingLoc;
        moveTo(rc, me, retreatTarget);
        if (PROFILE) profEndTurn(round, id, false);
        return;
      }
    } // Pre-compute isAssassin once for reuse (avoid redundant calculation)
    int assassinDiv = (mapSize == 2) ? LARGE_MAP_ASSASSIN_DIVISOR : ASSASSIN_DIVISOR;
    boolean isAssassin = (myRole == 0) && ((id % assassinDiv) == 0);

    // RACE DEFEND MODE: Attackers return to defend king
    // EXCEPTION: Assassins NEVER respond to defend recall - they always push to enemy king!
    // This is critical for winning on large maps where distance is a disadvantage.
    if (cachedRaceMode == RACE_DEFEND_MODE && myRole == 0 && distToKing > 25) {
      if (isAssassin) {
        // Assassins NEVER retreat - they're our best chance to kill the enemy king!
        if (DEBUG) System.out.println("ASSASSIN_IGNORES_RECALL:" + round + ":" + id);
        // Don't return - let assassin continue to attack logic below
      } else {
        if (DEBUG) System.out.println("RACE_DEFEND_RECALL:" + round + ":" + id);
        moveTo(rc, me, cachedOurKingLoc);
        if (PROFILE) profEndTurn(round, id, false);
        return;
      }
    }

    // Emergency cat sacrifice
    if (nearbyCat != null && health < 40) {
      MapLocation catLoc = nearbyCat.getLocation();
      int catToKingDist = catLoc.distanceSquaredTo(cachedOurKingLoc);
      if (catToKingDist <= 36) {
        if (me.distanceSquaredTo(catLoc) <= 2) return;
        moveTo(rc, me, catLoc);
        return;
      }
    }

    // Recall for king defense
    if (kingNeedsHelp && distToKing > DEFEND_KING_DIST_SQ) {
      moveTo(rc, me, cachedOurKingLoc);
      return;
    }

    // Low HP collector disintegrate
    if (health < 30 && myRole == 1 && distToKing <= 9) {
      rc.disintegrate();
      return;
    }

    // Determine if this attacker is an interceptor (patrol mid-map to slow enemy rush)
    // Interceptors are non-assassin attackers assigned by ID
    boolean isInterceptor = (myRole == 0) && !isAssassin && ((id % INTERCEPTOR_DIVISOR) == 0);

    if (myRole == 0) {
      runAttacker(
          rc, nearbyCat, round, id, me, kingNeedsHelp, distToKing, isAssassin, isInterceptor);
    } else {
      runCollector(rc, nearbyCat, round, id, me, kingNeedsHelp, distToKing);
    }

    if (PROFILE) profEndTurn(round, id, false);
  }

  private static RobotInfo findNearbyCat(RobotController rc) throws GameActionException {
    RobotInfo[] neutrals = rc.senseNearbyRobots(25, Team.NEUTRAL);
    for (int i = neutrals.length - 1; i >= 0; i--) {
      if (neutrals[i].getType() == UnitType.CAT) return neutrals[i];
    }
    return null;
  }

  private static int getSwarmRole(int id) {
    if (lastSwarmRoleID != id) {
      cachedSwarmRole = id % 5;
      lastSwarmRoleID = id;
    }
    return cachedSwarmRole;
  }

  private static MapLocation getSwarmTarget(
      MapLocation me, int swarmRole, int round, MapLocation targetKingLoc) {
    if (targetKingLoc == null) targetKingLoc = cachedMapCenter;

    // LARGE MAP FIX: Disable swarm flanking on large maps - send everyone directly to king
    // Flanking causes rats to get stuck going to offset positions instead of the actual king
    if (mapSize == 2) return targetKingLoc;

    int distToEnemy = me.distanceSquaredTo(targetKingLoc);
    // ANTI-CLUMPING: Add slight offset based on swarm role to spread attackers
    // Only when far from target - when close, converge on king
    if (distToEnemy <= SWARM_ENGAGE_DIST_SQ) return targetKingLoc;
    if (swarmRole == 0) {
      // Center swarm still goes direct, but add micro-variation based on ID
      // This is handled in movement logic now
      return targetKingLoc;
    }

    // Cache swarm direction to avoid repeated directionTo() calls
    if (lastSwarmDirRound != round) {
      cachedSwarmDir = cachedOurKingLoc.directionTo(targetKingLoc);
      lastSwarmDirRound = round;
    }
    Direction toEnemy = cachedSwarmDir;
    if (toEnemy == Direction.CENTER) return targetKingLoc;

    // DYNAMIC SWARM REBALANCING: Check if our flank is understaffed
    boolean isLeftFlank = (swarmRole <= 2);
    int myFlankCount = isLeftFlank ? cachedLeftCount : cachedRightCount;

    // If our flank has 0 rats (truly orphaned), redirect to center
    if (myFlankCount < MIN_FLANK_COUNT) {
      return targetKingLoc;
    }

    // Update cached flank locations periodically to avoid allocation
    if (lastFlankLocRound != round) {
      Direction leftDir = toEnemy.rotateLeft().rotateLeft();
      Direction rightDir = toEnemy.rotateRight().rotateRight();

      int leftX = targetKingLoc.x + leftDir.dx * SWARM_FLANK_DIST;
      int leftY = targetKingLoc.y + leftDir.dy * SWARM_FLANK_DIST;
      if (leftX < 0) leftX = 0;
      if (leftX >= mapWidth) leftX = mapWidth - 1;
      if (leftY < 0) leftY = 0;
      if (leftY >= mapHeight) leftY = mapHeight - 1;

      int rightX = targetKingLoc.x + rightDir.dx * SWARM_FLANK_DIST;
      int rightY = targetKingLoc.y + rightDir.dy * SWARM_FLANK_DIST;
      if (rightX < 0) rightX = 0;
      if (rightX >= mapWidth) rightX = mapWidth - 1;
      if (rightY < 0) rightY = 0;
      if (rightY >= mapHeight) rightY = mapHeight - 1;

      if (cachedLeftFlankLoc == null
          || cachedLeftFlankLoc.x != leftX
          || cachedLeftFlankLoc.y != leftY) {
        cachedLeftFlankLoc = new MapLocation(leftX, leftY);
      }
      if (cachedRightFlankLoc == null
          || cachedRightFlankLoc.x != rightX
          || cachedRightFlankLoc.y != rightY) {
        cachedRightFlankLoc = new MapLocation(rightX, rightY);
      }
      lastFlankLocRound = round;
    }

    return isLeftFlank ? cachedLeftFlankLoc : cachedRightFlankLoc;
  }

  // ================================================================
  // ATTACKER with KITING STATE MACHINE
  // ================================================================

  /** Get dynamic kite retreat distance based on current HP. */
  private static int getKiteRetreatDist(int hp) {
    if (hp < 40) return KITE_RETREAT_DIST_CRITICAL; // 3 tiles
    if (hp < 60) return KITE_RETREAT_DIST_WOUNDED; // 2 tiles
    return KITE_RETREAT_DIST_HEALTHY; // 1 tile
  }

  private static void runAttacker(
      RobotController rc,
      RobotInfo nearbyCat,
      int round,
      int id,
      MapLocation me,
      boolean kingNeedsHelp,
      int distToKing,
      boolean isAssassin,
      boolean isInterceptor)
      throws GameActionException {

    int swarmRole =
        getSwarmRole(
            id); // cachedEnemyKingLoc is now protected globally in refreshCache() against (0,0)
    MapLocation swarmTarget = getSwarmTarget(me, swarmRole, round, cachedEnemyKingLoc);
    int myHP = rc.getHealth();

    // Sense enemies
    int bcSense = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(BABY_RAT_VISION_SQ, cachedEnemyTeam);
    if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcSense;

    // Sense allies for overkill prevention
    RobotInfo[] allies = rc.senseNearbyRobots(BABY_RAT_VISION_SQ, cachedOurTeam);

    // Find best target AND focus target in single pass (bytecode optimization)
    // ALSO: Check for enemy king and squeak position if found!
    RobotInfo bestTarget = null;
    RobotInfo focusTarget = null;
    int minDist = Integer.MAX_VALUE;
    MapLocation seenEnemyKingLoc = null;

    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isRatKingType()) {
        // ENEMY KING SPOTTED! Record position and confirm HP
        seenEnemyKingLoc = enemy.getLocation();
        confirmEnemyKingHP(rc, enemy.getHealth(), round);
        continue;
      }
      if (!enemy.getType().isBabyRatType()) continue;
      MapLocation enemyLoc = enemy.getLocation();
      int dist = me.distanceSquaredTo(enemyLoc);
      if (dist < minDist) {
        minDist = dist;
        bestTarget = enemy;
      }
      // Check focus target - use distance <= 4 for 5-bit precision (lost 1 tile from packing)
      // Distance check for 5-bit precision: max error is 1 tile per axis, so max dist^2 = 2
      if (cachedFocusTarget != null && enemyLoc.distanceSquaredTo(cachedFocusTarget) <= 2) {
        focusTarget = enemy;
      }
    }

    // SQUEAK enemy king position if we saw it - this is how baby rats tell the king!
    // The king will listen for type 1 squeaks and update the cached position for all rats.
    if (seenEnemyKingLoc != null) {
      // LOCAL CACHE UPDATE: Update our own cached position immediately!
      // This ensures THIS rat uses the fresh position even if squeak doesn't reach king.
      if (cachedEnemyKingLoc == null || !cachedEnemyKingLoc.equals(seenEnemyKingLoc)) {
        cachedEnemyKingLoc = seenEnemyKingLoc;
        cachedEnemyX = seenEnemyKingLoc.x;
        cachedEnemyY = seenEnemyKingLoc.y;
        if (DEBUG) {
          System.out.println(
              "ATK_LOCAL_CACHE_UPDATE:" + round + ":" + id + ":newLoc=" + seenEnemyKingLoc);
        }
      }

      if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
        int squeak = (1 << 28) | (seenEnemyKingLoc.y << 16) | (seenEnemyKingLoc.x << 4);
        rc.squeak(squeak);
        lastMineSqueakRound = round;
        lastSqueakID = id;
        if (DEBUG) {
          System.out.println(
              "ATK_SQUEAK_ENEMY_KING:" + round + ":" + id + ":loc=" + seenEnemyKingLoc);
        }
      }
    }

    // Prioritize focus target
    if (focusTarget != null) bestTarget = focusTarget;

    // VISION CONE AWARENESS: Rats have directional vision, check behind periodically
    // This helps detect enemies that might be flanking us
    // FIX #1: Only turn if no immediate targets in front (bestTarget == null)
    // FIX #3: Add bytecode check before vision cone logic
    if (bestTarget == null && Clock.getBytecodesLeft() > LOW_BYTECODE_THRESHOLD) {
      if (lastVisionCheckBehindID != id) {
        lastVisionCheckBehindRound = -100;
        lastVisionCheckBehindID = id;
      }
      if (round - lastVisionCheckBehindRound >= VISION_CHECK_BEHIND_INTERVAL) {
        lastVisionCheckBehindRound = round;
        // Check if there might be enemies behind us by sensing in opposite direction
        Direction facing = rc.getDirection();
        Direction behind = facing.opposite();
        MapLocation behindLoc = me.add(behind).add(behind); // 2 tiles behind
        if (rc.canSenseLocation(behindLoc)) {
          // We can see behind us, check for enemies
          RobotInfo[] behindEnemies =
              rc.senseNearbyRobots(behindLoc, VISION_CONE_DANGER_DIST_SQ, cachedEnemyTeam);
          if (behindEnemies.length > 0) {
            if (DEBUG) {
              System.out.println(
                  "ENEMY_BEHIND:" + round + ":" + id + ":count=" + behindEnemies.length);
            }
            // If enemies behind and we're healthy, turn to face them
            if (myHP >= HEALTHY_HP_THRESHOLD && rc.canTurn()) {
              rc.turn(behind);
              return;
            }
          }
        }
      }
    }

    // ========== UNIVERSAL ENEMY KING ATTACK ==========
    // FIX: Rats have DIRECTIONAL attacks - they can only attack in the direction they're facing!
    // This block ensures ALL attackers near the enemy king turn to face it and attack.
    // Previously, rats would arrive at the king, fail canAttack() because they weren't facing it,
    // then call moveToKingAggressive() which would turn them but RETURN without attacking again.
    int distToEnemyKing =
        (cachedEnemyKingLoc != null) ? me.distanceSquaredTo(cachedEnemyKingLoc) : Integer.MAX_VALUE;

    // CRITICAL FIX: Skip universal attack block for medium map assassins!
    // Medium map assassins have specialized tile-targeting logic in their own section.
    // If we don't skip this, the universal block turns toward CENTER while the medium
    // block turns toward a specific TILE, causing infinite oscillation between directions.
    boolean isMedMapAssassin = isAssassin && mapSize == 1;

    if (cachedEnemyKingLoc != null
        && distToEnemyKing <= 9
        && rc.isActionReady()
        && !isMedMapAssassin) {
      // Within 3 tiles of enemy king - TURN TO FACE IT FIRST, then attack
      // Note: Turning is FREE (no cooldown) in Battlecode, so we can turn and attack same turn
      Direction toKing = me.directionTo(cachedEnemyKingLoc);
      if (toKing != Direction.CENTER) {
        Direction facing = rc.getDirection();
        // Simplified: canTurn() already checks if turning is ready
        if (facing != toKing && rc.canTurn()) {
          rc.turn(toKing);
          if (DEBUG) {
            System.out.println(
                "TURN_TO_KING:" + round + ":" + id + ":from=" + facing + ":to=" + toKing);
          }
        }
      }

      // Now try to attack the king (after potentially turning)
      // Try attacking center first
      if (rc.canAttack(cachedEnemyKingLoc)) {
        rc.attack(cachedEnemyKingLoc);
        recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
        if (DEBUG) System.out.println("KING_HIT_CENTER:" + round + ":" + id);
        return;
      }
      // Try all 8 adjacent tiles of the king's 3x3 area
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          if (dx == 0 && dy == 0) continue;
          MapLocation tile = cachedEnemyKingLoc.translate(dx, dy);
          if (rc.canAttack(tile)) {
            rc.attack(tile);
            recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
            if (DEBUG) System.out.println("KING_HIT_ADJ:" + round + ":" + id + ":tile=" + tile);
            return;
          }
        }
      }
    }

    // ========== KING RUSH LOGIC ==========
    // FIX FOR TEAM B LOSS: Push to enemy king instead of fighting baby rats

    // Dynamic bypass distance based on map size - larger maps need more aggressive rushing
    int bypassDist = (mapSize == 2) ? LARGE_MAP_KING_BYPASS_DIST_SQ : KING_PROXIMITY_BYPASS_DIST_SQ;
    // isAssassin is now passed as a parameter (pre-computed in runBabyRat to avoid redundant
    // calculation)

    // MEDIUM MAP ASSASSIN FIX: Use FRESH sensed king position like ratbot4!
    // The key insight: ratbot4 successfully attacks because it uses enemy.getLocation()
    // from the sensed enemies array, not a stale cached position from round 1.
    if (isAssassin && mapSize == 1) {
      // STEP 1: Check if we can SEE the enemy king (get FRESH position!)
      MapLocation freshKingLoc = null;
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isRatKingType()) {
          freshKingLoc = enemy.getLocation();
          // Also confirm HP when we see it
          confirmEnemyKingHP(rc, enemy.getHealth(), round);

          // LOCAL CACHE UPDATE: Update our own cached position immediately!
          // This ensures THIS assassin uses the fresh position even if squeak doesn't reach king.
          if (cachedEnemyKingLoc == null || !cachedEnemyKingLoc.equals(freshKingLoc)) {
            cachedEnemyKingLoc = freshKingLoc;
            cachedEnemyX = freshKingLoc.x;
            cachedEnemyY = freshKingLoc.y;
            if (DEBUG) {
              System.out.println(
                  "MED_ASSASSIN_LOCAL_CACHE_UPDATE:"
                      + round
                      + ":"
                      + id
                      + ":newLoc="
                      + freshKingLoc);
            }
          }

          // SQUEAK the fresh position so king can update cached position for all rats!
          // This is critical for Team B which may have stale cached position
          if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
            int squeak = (1 << 28) | (freshKingLoc.y << 16) | (freshKingLoc.x << 4);
            rc.squeak(squeak);
            lastMineSqueakRound = round;
            lastSqueakID = id;
            if (DEBUG) {
              System.out.println(
                  "MED_ASSASSIN_SQUEAK_KING:" + round + ":" + id + ":loc=" + freshKingLoc);
            }
          }

          if (DEBUG) {
            System.out.println(
                "MED_ASSASSIN_SEE_KING:"
                    + round
                    + ":"
                    + id
                    + ":freshLoc="
                    + freshKingLoc
                    + ":cachedLoc="
                    + cachedEnemyKingLoc);
          }
          break;
        }
      }

      // STEP 2: Use fresh position if available, otherwise fall back to cached
      MapLocation attackKingLoc = (freshKingLoc != null) ? freshKingLoc : cachedEnemyKingLoc;

      // STEP 3: SIMPLE ATTACK LOOP like ratbot4 - try all 9 tiles!
      // This is the key fix: ratbot4 just loops through all tiles and calls canAttack().
      // No complex orthogonal vs diagonal logic, no distance checks - just try everything.
      if (attackKingLoc != null && rc.isActionReady()) {
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            MapLocation tile = attackKingLoc.translate(dx, dy);
            if (rc.canAttack(tile)) {
              rc.attack(tile);
              recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
              if (DEBUG) {
                System.out.println(
                    "MED_ASSASSIN_HIT:"
                        + round
                        + ":"
                        + id
                        + ":tile="
                        + tile
                        + ":fresh="
                        + (freshKingLoc != null));
              }
              return;
            }
          }
        }
      }

      // STEP 4: Can't attack king - try to fight blocking baby rats if close
      // This is important: if we're close but can't attack, there might be blockers
      if (attackKingLoc != null) {
        int distToAttackKing = me.distanceSquaredTo(attackKingLoc);
        if (distToAttackKing <= 100 && bestTarget != null && rc.isActionReady()) {
          // Within 10 tiles of enemy king - fight any blocking baby rats
          MapLocation targetLoc = bestTarget.getLocation();
          if (rc.canAttack(targetLoc)) {
            rc.attack(targetLoc);
            if (DEBUG) {
              System.out.println(
                  "MED_ASSASSIN_CLEAR_PATH:" + round + ":" + id + ":target=" + targetLoc);
            }
            return;
          }
        }
      }

      // STEP 5: Move toward king - use aggressive movement
      MapLocation moveTarget = (attackKingLoc != null) ? attackKingLoc : cachedMapCenter;
      if (DEBUG) {
        int distToMove = me.distanceSquaredTo(moveTarget);
        System.out.println(
            "MED_ASSASSIN_PUSH:"
                + round
                + ":"
                + id
                + ":distKing="
                + distToMove
                + ":fresh="
                + (freshKingLoc != null));
      }
      moveToKingAggressive(rc, me, moveTarget);
      return;
    }

    // KING PROXIMITY BYPASS: When close to enemy king, ignore baby rats and push!
    if (distToEnemyKing <= bypassDist) {
      // We're close to enemy king - check if we should bypass baby rats
      boolean shouldBypassBabyRats = false;

      if (bestTarget == null) {
        // No enemies in sight - push to king
        shouldBypassBabyRats = true;
      } else if (!bestTarget.getType().isRatKingType()) {
        // Target is a baby rat, not the king
        if (isAssassin) {
          // Assassins always bypass baby rats to rush king
          shouldBypassBabyRats = true;
          if (DEBUG)
            System.out.println(
                "ASSASSIN_BYPASS:" + round + ":" + id + ":distKing=" + distToEnemyKing);
        } else if (distToEnemyKing <= KING_ATTACK_PRIORITY_DIST_SQ) {
          // Very close to king - everyone rushes
          shouldBypassBabyRats = true;
          if (DEBUG)
            System.out.println("KING_RUSH:" + round + ":" + id + ":distKing=" + distToEnemyKing);
        } else if (myHP >= HEALTHY_HP_THRESHOLD && enemies.length <= 2) {
          // Healthy and not too many enemies - push through
          shouldBypassBabyRats = true;
        }
      }

      if (shouldBypassBabyRats) {
        // Attack enemy king - try all 9 tiles of the 3x3 king area
        // Kings occupy a 3x3 area, so we need to check multiple tiles
        if (cachedEnemyKingLoc != null && rc.isActionReady()) {
          if (DEBUG && distToEnemyKing <= 25) {
            System.out.println(
                "KING_ATTACK_ATTEMPT:"
                    + round
                    + ":"
                    + id
                    + ":distKing="
                    + distToEnemyKing
                    + ":myPos="
                    + me
                    + ":kingPos="
                    + cachedEnemyKingLoc
                    + ":canAttackCenter="
                    + rc.canAttack(cachedEnemyKingLoc));
          }
          // Try attacking center first
          if (rc.canAttack(cachedEnemyKingLoc)) {
            rc.attack(cachedEnemyKingLoc);
            recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
            if (DEBUG) System.out.println("KING_HIT_CENTER:" + round + ":" + id);
            return;
          }
          // Try all 8 adjacent tiles of the king's 3x3 area
          for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
              if (dx == 0 && dy == 0) continue;
              MapLocation tile = cachedEnemyKingLoc.translate(dx, dy);
              if (rc.canAttack(tile)) {
                rc.attack(tile);
                recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
                if (DEBUG)
                  System.out.println("KING_HIT_TILE:" + round + ":" + id + ":tile=" + tile);
                return;
              }
            }
          }
          // Still can't attack - need to get closer using AGGRESSIVE movement
          if (DEBUG && distToEnemyKing <= 16) {
            System.out.println("KING_TOO_FAR:" + round + ":" + id + ":dist=" + distToEnemyKing);
          }
        }
        // Also try attacking any king we can see in enemies array
        for (int i = enemies.length - 1; i >= 0; i--) {
          RobotInfo enemy = enemies[i];
          if (enemy.getType().isRatKingType()) {
            // Confirm enemy king HP when we see it
            confirmEnemyKingHP(rc, enemy.getHealth(), round);
            MapLocation kingCenter = enemy.getLocation();
            if (rc.canAttack(kingCenter)) {
              rc.attack(kingCenter);
              recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
              if (DEBUG) System.out.println("KING_HIT:" + round + ":" + id);
              return;
            }
            // Try adjacent tiles
            for (int dx = -1; dx <= 1; dx++) {
              for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation tile = kingCenter.translate(dx, dy);
                if (rc.canAttack(tile)) {
                  rc.attack(tile);
                  recordDamageToEnemyKing(rc, BASE_ATTACK_DAMAGE);
                  if (DEBUG) System.out.println("KING_HIT_ADJ:" + round + ":" + id);
                  return;
                }
              }
            }
          }
        }
        // Push toward enemy king using AGGRESSIVE close-range movement
        // This tries all 8 directions to find one that gets closer, instead of
        // waiting 3 rounds stuck before using Bug2
        if (distToEnemyKing <= 25) {
          // Very close - use aggressive movement that tries all directions
          moveToKingAggressive(rc, me, cachedEnemyKingLoc);
        } else {
          // Further away - use normal movement
          moveTo(rc, me, cachedEnemyKingLoc);
        }
        return;
      }
    }

    // ASSASSIN ROLE: Always rush king regardless of distance
    if (isAssassin && (bestTarget == null || !bestTarget.getType().isRatKingType())) {
      // Assassin with no king target - rush to king location
      if (DEBUG)
        System.out.println("ASSASSIN_RUSH:" + round + ":" + id + ":distKing=" + distToEnemyKing);
      moveTo(rc, me, cachedEnemyKingLoc != null ? cachedEnemyKingLoc : swarmTarget);
      return;
    }

    // LARGE MAP AGGRESSIVE MODE: On large maps, always prioritize pushing to enemy king
    if (mapSize == 2 && enemies.length == 0 && distToEnemyKing > KING_ATTACK_PRIORITY_DIST_SQ) {
      // No enemies visible on large map - rush toward enemy king
      moveTo(rc, me, cachedEnemyKingLoc != null ? cachedEnemyKingLoc : swarmTarget);
      return;
    }

    // ========== INTERCEPTOR LOGIC ==========
    // Interceptors patrol mid-map to engage enemies and slow their rush to our king
    // This creates a defensive buffer while assassins push to enemy king
    if (isInterceptor && !kingNeedsHelp) {
      // Calculate patrol point: between our king and map center, offset toward enemy
      MapLocation patrolTarget = getInterceptorPatrolPoint(round);

      // If we see enemies, engage them!
      if (bestTarget != null) {
        int distToEnemy = me.distanceSquaredTo(bestTarget.getLocation());
        if (distToEnemy <= INTERCEPTOR_ENGAGE_DIST_SQ) {
          // Engage the enemy - this slows their rush
          if (DEBUG)
            System.out.println("INTERCEPTOR_ENGAGE:" + round + ":" + id + ":dist=" + distToEnemy);
          // Fall through to normal combat logic below
        }
      } else {
        // No enemies - patrol to intercept position
        if (DEBUG && (round % 20) == 0) {
          System.out.println("INTERCEPTOR_PATROL:" + round + ":" + id + ":target=" + patrolTarget);
        }
        moveTo(rc, me, patrolTarget);
        return;
      }
    }

    // KITING STATE MACHINE
    if (bestTarget != null && minDist <= KITE_ENGAGE_DIST_SQ) {
      MapLocation targetLoc = bestTarget.getLocation();

      // Predictive targeting: aim where enemy will be
      MapLocation predictedLoc = predictEnemyPosition(targetLoc, round);

      // DYNAMIC KITE DISTANCE based on HP
      // FIX: Healthy rats don't retreat - press the attack!
      int kiteRetreatDist = (myHP >= HEALTHY_HP_THRESHOLD) ? 0 : getKiteRetreatDist(myHP);

      switch (kiteState) {
        case KITE_STATE_APPROACH:
          // Move toward enemy until adjacent
          if (minDist <= 2) {
            kiteState = KITE_STATE_ATTACK;
          } else {
            moveTo(rc, me, predictedLoc);
            return;
          }
          // Fall through to attack

        case KITE_STATE_ATTACK:
          if (rc.isActionReady()) {
            // OVERKILL PREVENTION: Check if this would be wasted damage
            if (isOverkill(rc, bestTarget, allies)) {
              // Find another target or just move
              if (DEBUG) System.out.println("OVERKILL_SKIP:" + round + ":" + id);
              kiteState = KITE_STATE_APPROACH;
              moveTo(rc, me, swarmTarget);
              return;
            }

            if (rc.canAttack(targetLoc)) {
              boolean coop = cachedIsCooperation;
              int enhancedThreshold =
                  (mapSize == 0) ? SMALL_MAP_ENHANCED_THRESHOLD : ENHANCED_THRESHOLD;
              if (!coop && rc.getGlobalCheese() > enhancedThreshold) {
                rc.attack(targetLoc, ENHANCED_ATTACK_CHEESE);
              } else {
                rc.attack(targetLoc);
              }
              lastAttackedLoc = targetLoc;
              // Only retreat if not healthy
              if (kiteRetreatDist > 0) {
                kiteState = KITE_STATE_RETREAT;
                kiteRetreatTurns = kiteRetreatDist;
                if (DEBUG)
                  System.out.println(
                      "KITE_ATTACK:" + round + ":" + id + ":retreatDist=" + kiteRetreatDist);
              } else {
                // Healthy rat - stay and fight, push forward!
                kiteState = KITE_STATE_APPROACH;
                if (DEBUG) System.out.println("PRESS_ATTACK:" + round + ":" + id + ":HP=" + myHP);
              }
              return;
            }
          }
          // Can't attack, try to get closer
          moveTo(rc, me, targetLoc);
          return;

        case KITE_STATE_RETREAT:
          // Retreat after attacking - use dynamic distance
          if (kiteRetreatTurns > 0 && rc.isMovementReady()) {
            Direction awayFromEnemy = targetLoc.directionTo(me);
            if (awayFromEnemy != Direction.CENTER) {
              MapLocation retreatTarget = me.translate(awayFromEnemy.dx * 2, awayFromEnemy.dy * 2);
              moveTo(rc, me, retreatTarget);
              kiteRetreatTurns--;
              if (DEBUG) System.out.println("KITE_RETREAT:" + round + ":" + id);
              return;
            }
          }
          // Done retreating, go back to approach
          kiteState = KITE_STATE_APPROACH;
          break;
      }
    }

    // No enemy in kiting range, reset state
    kiteState = KITE_STATE_APPROACH;

    // King defense - reuse enemies array instead of re-sensing (bytecode optimization)
    if (kingNeedsHelp && distToKing <= DEFEND_KING_DIST_SQ) {
      if (enemies.length > 0) {
        babyRatPlaceTrap(rc, me, round, id);
      }
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          if (rc.canAttack(enemyLoc)) {
            rc.attack(enemyLoc);
            return;
          }
          moveTo(rc, me, enemyLoc);
          return;
        }
      }
    }

    // Cat defense
    if (nearbyCat != null && cachedIsCooperation) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);
      if (distToCat <= 2 && rc.canAttack(catLoc)) {
        rc.attack(catLoc);
        catDamageDealt += 10;
        return;
      }
      if (distToCat < CAT_KITE_MIN_DIST_SQ) {
        Direction away = me.directionTo(catLoc).opposite();
        moveTo(rc, me, me.translate(away.dx * 3, away.dy * 3));
        return;
      } else if (distToCat > CAT_KITE_MAX_DIST_SQ) {
        moveTo(rc, me, catLoc);
        return;
      } else {
        Direction toCat = me.directionTo(catLoc);
        Direction circle = toCat.rotateLeft().rotateLeft();
        moveTo(rc, me, me.translate(circle.dx << 1, circle.dy << 1));
        return;
      }
    }

    // Ratnapping
    RobotInfo carrying = rc.getCarrying();
    if (carrying != null) {
      int distToKingRatnap = me.distanceSquaredTo(cachedOurKingLoc);
      if (distToKingRatnap <= 4) {
        Direction toKing = me.directionTo(cachedOurKingLoc);
        if (rc.canDropRat(toKing)) {
          rc.dropRat(toKing);
          return;
        }
      }
      if (rc.canThrowRat()) {
        Direction toKing = me.directionTo(cachedOurKingLoc);
        Direction facing = rc.getDirection();
        if (facing != toKing && rc.canTurn()) {
          rc.turn(toKing);
        } else {
          rc.throwRat();
        }
        return;
      }
    }

    // Try to pick up low HP enemy
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isBabyRatType() && enemy.getHealth() < 50) {
        if (carrying == null && rc.canCarryRat(enemy.getLocation())) {
          rc.carryRat(enemy.getLocation());
          return;
        }
      }
    }

    // Attack if can
    if (bestTarget != null && rc.canAttack(bestTarget.getLocation())) {
      boolean coop = cachedIsCooperation;
      int enhancedThreshold = (mapSize == 0) ? SMALL_MAP_ENHANCED_THRESHOLD : ENHANCED_THRESHOLD;
      if (!coop && rc.getGlobalCheese() > enhancedThreshold) {
        rc.attack(bestTarget.getLocation(), ENHANCED_ATTACK_CHEESE);
      } else {
        rc.attack(bestTarget.getLocation());
      }
      return;
    }

    // Attack enemy king
    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (enemy.getType().isRatKingType()) {
        MapLocation kingCenter = enemy.getLocation();
        if (rc.canAttack(kingCenter)) {
          rc.attack(kingCenter);
          return;
        }
        for (int dx = -1; dx <= 1; dx++) {
          for (int dy = -1; dy <= 1; dy++) {
            if (dx == 0 && dy == 0) continue;
            MapLocation tile = kingCenter.translate(dx, dy);
            if (rc.canAttack(tile)) {
              rc.attack(tile);
              return;
            }
          }
        } // Squeak enemy king location
        if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
          int squeak = (1 << 28) | (kingCenter.y << 16) | (kingCenter.x << 4);
          rc.squeak(squeak);
          lastMineSqueakRound = round;
          lastSqueakID = id;
        }
        // Confirm enemy king HP when we can see it
        int actualKingHP = enemy.getHealth();
        confirmEnemyKingHP(rc, actualKingHP, round);

        // Track actual damage to enemy king when we attack
        if (rc.canAttack(kingCenter)) {
          int damageToDeal = BASE_ATTACK_DAMAGE;
          // Check if we're using enhanced attack
          boolean coop = cachedIsCooperation;
          int enhancedThreshold =
              (mapSize == 0) ? SMALL_MAP_ENHANCED_THRESHOLD : ENHANCED_THRESHOLD;
          if (!coop && rc.getGlobalCheese() > enhancedThreshold) {
            damageToDeal = BASE_ATTACK_DAMAGE + (int) Math.ceil(Math.log(ENHANCED_ATTACK_CHEESE));
            rc.attack(kingCenter, ENHANCED_ATTACK_CHEESE);
          } else {
            rc.attack(kingCenter);
          }
          // Update damage tracker
          recordDamageToEnemyKing(rc, damageToDeal);
          return;
        }
      }
    }

    // Check squeaks (skip on small maps)
    if (mapSize != 0) {
      Message[] squeaks = rc.readSqueaks(-1);
      int len = squeaks.length;
      int limit = len < MAX_SQUEAKS_TO_READ ? len : MAX_SQUEAKS_TO_READ;
      for (int i = len - 1; i >= len - limit && i >= 0; i--) {
        Message msg = squeaks[i];
        if (msg.getSenderID() != id) {
          int bytes = msg.getBytes();
          int type = (bytes >>> 28) & 0xF;
          if (type == 1) {
            int x = (bytes >> 4) & 0xFFF;
            int y = (bytes >> 16) & 0xFFF;
            // Cache squeak target to avoid allocation
            if (cachedSqueakTarget == null
                || cachedSqueakTarget.x != x
                || cachedSqueakTarget.y != y) {
              cachedSqueakTarget = new MapLocation(x, y);
            }
            moveTo(rc, me, cachedSqueakTarget);
            return;
          }
        }
      }
    }

    // Self-reporting squeaks removed - SQUEAK_RADIUS_SQUARED=25 (~5 tiles) is too short
    // for attackers near enemy king to report back to our king

    // DENIAL PATROL: Some attackers target enemy-side mines
    if (round >= DENIAL_PATROL_START_ROUND
        && (id % DENIAL_PATROL_DIVISOR) == 0
        && !cachedAllInActive) {
      // Denial patrol - go to enemy side of map to disrupt collectors
      MapLocation denialTarget = getDenialPatrolTarget(me, round);
      if (denialTarget != null) {
        moveTo(rc, me, denialTarget);
        return;
      }
    }

    // DEBUG: Log attacker position EVERY round to track movement
    if (DEBUG) {
      int distToEnemy =
          (cachedEnemyKingLoc != null) ? me.distanceSquaredTo(cachedEnemyKingLoc) : -1;
      System.out.println(
          "ATK:"
              + round
              + ":"
              + id
              + ":pos="
              + me
              + ":distEnemy="
              + distToEnemy
              + ":HP="
              + myHP
              + ":enemies="
              + enemies.length
              + ":kiteState="
              + kiteState);
    }

    // Move to swarm target
    moveTo(rc, me, swarmTarget);
  }

  /**
   * Get the patrol point for interceptors - between our king and mid-map, positioned to intercept
   * enemies rushing to our king.
   */
  // Cached interceptor patrol point to avoid allocation in hot path
  private static MapLocation cachedInterceptorPatrolPoint = null;

  private static int lastInterceptorPatrolRound = -1;

  private static MapLocation getInterceptorPatrolPoint(int round) {
    // Only recalculate every 10 rounds to save bytecode
    if (cachedInterceptorPatrolPoint != null && (round - lastInterceptorPatrolRound) < 10) {
      return cachedInterceptorPatrolPoint;
    }

    if (cachedOurKingLoc == null || cachedMapCenter == null) {
      return cachedMapCenter;
    }
    // Calculate direction from our king toward enemy/center
    MapLocation targetDir = (cachedEnemyKingLoc != null) ? cachedEnemyKingLoc : cachedMapCenter;
    Direction towardEnemy = cachedOurKingLoc.directionTo(targetDir);
    if (towardEnemy == Direction.CENTER) towardEnemy = Direction.NORTH;

    // Patrol point is INTERCEPTOR_PATROL_OFFSET tiles from our king toward enemy
    int patrolX = cachedOurKingLoc.x + towardEnemy.dx * INTERCEPTOR_PATROL_OFFSET;
    int patrolY = cachedOurKingLoc.y + towardEnemy.dy * INTERCEPTOR_PATROL_OFFSET;

    // Clamp to map bounds
    if (patrolX < 0) patrolX = 0;
    if (patrolX >= mapWidth) patrolX = mapWidth - 1;
    if (patrolY < 0) patrolY = 0;
    if (patrolY >= mapHeight) patrolY = mapHeight - 1;

    cachedInterceptorPatrolPoint = new MapLocation(patrolX, patrolY);
    lastInterceptorPatrolRound = round;
    return cachedInterceptorPatrolPoint;
  }

  private static MapLocation getDenialPatrolTarget(MapLocation me, int round) {
    // Target enemy-side mines if known
    if (cachedNumMines > 0 && cachedEnemyKingLoc != null) {
      // Find mine closest to enemy king
      MapLocation bestMine = null;
      int bestDist = Integer.MAX_VALUE;
      for (int i = 0; i < cachedNumMines; i++) {
        if (cachedMineLocations[i] != null) {
          int dist = cachedMineLocations[i].distanceSquaredTo(cachedEnemyKingLoc);
          if (dist < bestDist) {
            bestDist = dist;
            bestMine = cachedMineLocations[i];
          }
        }
      }
      if (bestMine != null) return bestMine;
    }
    // Fall back to enemy side of map
    if (cachedEnemyKingLoc != null && cachedMapCenter != null) {
      // Go to midpoint between center and enemy king
      int midX = (cachedMapCenter.x + cachedEnemyKingLoc.x) / 2;
      int midY = (cachedMapCenter.y + cachedEnemyKingLoc.y) / 2;
      return new MapLocation(midX, midY);
    }
    return null;
  }

  // ================================================================
  // COLLECTOR
  // ================================================================

  private static void runCollector(
      RobotController rc,
      RobotInfo nearbyCat,
      int round,
      int id,
      MapLocation me,
      boolean kingNeedsHelp,
      int distToKing)
      throws GameActionException {
    int cheese = rc.getRawCheese();

    if (Clock.getBytecodesLeft() < BABY_RAT_BYTECODE_LIMIT / 10) {
      if (cheese > 0) moveTo(rc, me, cachedOurKingLoc);
      return;
    }

    // COLLECTOR RELAY: Listen for enemy king position squeaks from attackers!
    // Collectors roam the map and may hear squeaks from assassins near the enemy king.
    // When collectors deliver cheese (within 4 tiles of our king), they relay the position.
    // This creates a reliable communication chain: Assassins -> Collectors -> King -> All Rats
    if (heardEnemyKingID != id) {
      // Reset for new collector
      heardEnemyKingLoc = null;
      heardEnemyKingRound = -100;
      heardEnemyKingID = id;
    }
    listenForEnemyKingPositionSqueaksCollector(rc, round, id);

    // COLLECTOR SURVIVAL PRIORITY: In emergency, collectors flee from all combat
    int emergencyLevel = cachedEmergencyLevel;
    boolean survivalMode = (emergencyLevel >= 2);

    // Sense enemies
    RobotInfo[] enemies = rc.senseNearbyRobots(20, cachedEnemyTeam);

    // SURVIVAL MODE: Flee from enemies if carrying cheese or in emergency
    if (survivalMode || cheese > 0) {
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          int distToEnemy = me.distanceSquaredTo(enemy.getLocation());
          if (distToEnemy <= 8) {
            // Enemy too close - flee toward king
            if (DEBUG && survivalMode) System.out.println("COLLECTOR_FLEE:" + round + ":" + id);
            moveTo(rc, me, cachedOurKingLoc);
            return;
          }
        }
      }
    }

    // Combat with focus fire (only if not in survival mode and king needs help)
    if (!survivalMode && kingNeedsHelp && distToKing <= DEFEND_KING_DIST_SQ) {
      RobotInfo focusTarget = null;
      if (cachedFocusTarget != null) {
        for (int i = enemies.length - 1; i >= 0; i--) {
          RobotInfo enemy = enemies[i];
          // Distance check for 5-bit precision (lost 1 tile from packing)
          if (enemy.getType().isBabyRatType()
              && enemy.getLocation().distanceSquaredTo(cachedFocusTarget) <= 2) {
            focusTarget = enemy;
            break;
          }
        }
      }
      if (focusTarget != null) {
        MapLocation focusLoc = focusTarget.getLocation();
        if (rc.canAttack(focusLoc)) {
          rc.attack(focusLoc);
          return;
        }
        moveTo(rc, me, focusLoc);
        return;
      }
      for (int i = enemies.length - 1; i >= 0; i--) {
        RobotInfo enemy = enemies[i];
        if (enemy.getType().isBabyRatType()) {
          MapLocation enemyLoc = enemy.getLocation();
          if (rc.canAttack(enemyLoc)) {
            rc.attack(enemyLoc);
            return;
          }
          if (enemyLoc.distanceSquaredTo(cachedOurKingLoc) < distToKing) {
            moveTo(rc, me, enemyLoc);
            return;
          }
        }
      }
    }

    // Recall for king defense (only if not in survival mode)
    if (!survivalMode && kingNeedsHelp && distToKing > DEFEND_KING_DIST_SQ) {
      moveTo(rc, me, cachedOurKingLoc);
      return;
    }

    // Cat flee
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      int distToCat = me.distanceSquaredTo(catLoc);
      if (distToCat <= CAT_FLEE_DIST_SQ) {
        Direction away = me.directionTo(catLoc).opposite();
        moveTo(rc, me, me.translate(away.dx << 2, away.dy << 2));
        return;
      }
    }

    // DYNAMIC DELIVERY THRESHOLD based on emergency level
    int deliveryThreshold;
    if (emergencyLevel >= 3) {
      deliveryThreshold = EMERGENCY_DELIVERY_THRESHOLD; // Deliver with 1+ cheese
    } else if (emergencyLevel >= 2) {
      deliveryThreshold = CRITICAL_DELIVERY_THRESHOLD; // Deliver with 3+ cheese
    } else if (emergencyLevel >= 1) {
      deliveryThreshold = WARNING_DELIVERY_THRESHOLD; // Deliver with 5+ cheese
    } else if (mapSize == 1) {
      deliveryThreshold = MEDIUM_MAP_DELIVERY_THRESHOLD;
    } else {
      deliveryThreshold = DELIVERY_THRESHOLD;
    }

    if (cheese >= deliveryThreshold) {
      deliver(rc, me, round, id);
    } else {
      collect(rc, me, round, id);
    }
  }

  private static void collect(RobotController rc, MapLocation me, int round, int id)
      throws GameActionException {

    if (lastTargetID != id) {
      targetCheese = null;
      lastTargetID = id;
    }

    if (targetCheese != null) {
      if (rc.canPickUpCheese(targetCheese)) {
        rc.pickUpCheese(targetCheese);
        targetCheese = null;
        return;
      }
      if (me.distanceSquaredTo(targetCheese) <= 2 && rc.canSenseLocation(targetCheese)) {
        MapInfo info = rc.senseMapInfo(targetCheese);
        if (info.getCheeseAmount() == 0) targetCheese = null;
      }
    }

    if (targetCheese == null || (round - targetCheeseRound) >= SENSE_THROTTLE_ROUNDS) {
      targetCheese = findNearestCheese(rc, me, round, id);
      targetCheeseRound = round;
    }

    if (targetCheese != null) {
      moveTo(rc, me, targetCheese);
    } else {
      // MINE CAMPING: Go to assigned mine instead of map center
      MapLocation campTarget = getAssignedMine(id, round);
      if (campTarget != null) {
        moveTo(rc, me, campTarget);
      } else {
        moveTo(rc, me, cachedMapCenter);
      }
    }
  }

  private static MapLocation getAssignedMine(int id, int round) {
    if (round < MINE_CAMP_START_ROUND) return null;
    if (cachedNumMines == 0) return null;
    // Assign collector to a mine based on ID
    int myMineIndex = id % cachedNumMines;
    return cachedMineLocations[myMineIndex];
  }

  private static MapLocation findNearestCheese(
      RobotController rc, MapLocation me, int round, int id) throws GameActionException {
    MapInfo[] nearbyInfo = rc.senseNearbyMapInfos(me, CHEESE_SENSE_RADIUS_SQ);
    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    for (int i = nearbyInfo.length - 1; i >= 0; i--) {
      int cheeseAmt = nearbyInfo[i].getCheeseAmount();
      if (cheeseAmt > 0) {
        MapLocation loc = nearbyInfo[i].getMapLocation();
        int dist = me.distanceSquaredTo(loc);
        if (dist <= GOOD_ENOUGH_CHEESE_DIST_SQ) return loc;
        if (dist < nearestDist) {
          nearestDist = dist;
          nearest = loc;
        }
      }
    }

    if ((round & 7) == 0 && nearest == null) {
      for (int i = nearbyInfo.length - 1; i >= 0; i--) {
        if (nearbyInfo[i].hasCheeseMine()) {
          MapLocation mineLoc = nearbyInfo[i].getMapLocation();
          if (round - lastMineSqueakRound >= SQUEAK_THROTTLE_ROUNDS || lastSqueakID != id) {
            int squeak = (3 << 28) | (mineLoc.y << 16) | (mineLoc.x << 4);
            rc.squeak(squeak);
            lastMineSqueakRound = round;
            lastSqueakID = id;
          }
          nearest = mineLoc;
          break;
        }
      }
    }
    return nearest;
  }

  private static void deliver(RobotController rc, MapLocation me, int round, int id)
      throws GameActionException {
    int dist = me.distanceSquaredTo(cachedOurKingLoc);

    if (dist <= 9) {
      int amt = rc.getRawCheese();
      if (rc.canTransferCheese(cachedOurKingLoc, amt)) {
        rc.transferCheese(cachedOurKingLoc, amt);

        // COLLECTOR RELAY: When delivering cheese (within 4 tiles of king),
        // relay any enemy king position we heard from attackers!
        // Squeak range is only 4 tiles, but we're guaranteed to be close to king now.
        if (heardEnemyKingLoc != null && (round - heardEnemyKingRound) <= 30) {
          // Only relay if we heard it recently (within 30 rounds)
          if (round - lastMineSqueakRound >= COLLECTOR_RELAY_THROTTLE_ROUNDS
              || lastSqueakID != id) {
            int squeak = (1 << 28) | (heardEnemyKingLoc.y << 16) | (heardEnemyKingLoc.x << 4);
            rc.squeak(squeak);
            lastMineSqueakRound = round;
            lastSqueakID = id;
            if (DEBUG) {
              System.out.println(
                  "COLLECTOR_RELAY_ENEMY_POS:" + round + ":" + id + ":loc=" + heardEnemyKingLoc);
            }
            // Clear after relaying to avoid spamming same position
            heardEnemyKingLoc = null;
          }
        }

        babyRatPlaceTrap(rc, me, round, id);
        return;
      }
    }
    moveTo(rc, me, cachedOurKingLoc);
  }

  private static void babyRatPlaceTrap(RobotController rc, MapLocation me, int round, int id)
      throws GameActionException {
    if (lastBabyTrapID != id) {
      lastBabyTrapRound = -100;
      lastBabyTrapID = id;
    }
    if (round - lastBabyTrapRound < BABY_RAT_TRAP_COOLDOWN) return;
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);
    if (distToKing > 16) return;

    Direction toEnemy = me.directionTo(cachedEnemyKingLoc);
    if (toEnemy == Direction.CENTER) toEnemy = Direction.NORTH;
    BABY_TRAP_DIRS[0] = toEnemy;
    BABY_TRAP_DIRS[1] = toEnemy.rotateLeft();
    BABY_TRAP_DIRS[2] = toEnemy.rotateRight();

    for (int d = 0; d < 3; d++) {
      Direction dir = BABY_TRAP_DIRS[d];
      for (int dist = 1; dist <= 2; dist++) {
        MapLocation trapLoc = me.translate(dir.dx * dist, dir.dy * dist);
        if (rc.canPlaceRatTrap(trapLoc)) {
          rc.placeRatTrap(trapLoc);
          lastBabyTrapRound = round;
          return;
        }
      }
    }
  }

  // ================================================================
  // MOVEMENT
  // ================================================================

  // Cached ID for moveTo to avoid rc.getID() call
  private static int moveToLastID = -1;

  // Pre-allocated arrays for adjacent directions to avoid allocation in hot path
  // Two arrays for ID-based variation (even vs odd)
  private static final Direction[] ADJACENT_DIRS_EVEN = new Direction[4];
  private static final Direction[] ADJACENT_DIRS_ODD = new Direction[4];
  // Pre-allocated arrays for aggressive king movement (6 directions each)
  private static final Direction[] AGGRESSIVE_DIRS_EVEN = new Direction[6];
  private static final Direction[] AGGRESSIVE_DIRS_ODD = new Direction[6];

  // Static offsets for king tile iteration (avoid allocation in hot path)
  // Orthogonal offsets: [0,1], [1,0], [0,-1], [-1,0]
  private static final int[][] ORTHO_OFFSETS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
  // Diagonal offsets: [-1,-1], [-1,1], [1,-1], [1,1]
  private static final int[][] DIAG_OFFSETS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

  /**
   * Count how many allies are near a destination tile. Used for anti-clumping logic - prefer
   * destinations with fewer allies.
   */
  private static int countAlliesNearDest(RobotController rc, MapLocation dest)
      throws GameActionException {
    if (!rc.canSenseLocation(dest)) return 0;
    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(dest, CLUSTER_SENSE_RADIUS_SQ, cachedOurTeam);
    return nearbyAllies.length;
  }

  /**
   * Check if a location is a hazard (rat trap or ally blocking). Used to reduce duplicate hazard
   * checking code in moveTo() and moveToKingAggressive().
   *
   * @return true if the location has a hazard (should not move there), false if safe
   */
  private static boolean isHazardAt(RobotController rc, MapLocation loc)
      throws GameActionException {
    if (!rc.canSenseLocation(loc)) return false; // Assume safe if can't sense
    MapInfo info = rc.senseMapInfo(loc);
    // Check for rat trap
    if (info.getTrap() == TrapType.RAT_TRAP) return true;
    // Check for impassable terrain
    if (!info.isPassable()) return true;
    // Check for ally blocking
    if (rc.isLocationOccupied(loc)) {
      RobotInfo occupant = rc.senseRobotAtLocation(loc);
      if (occupant != null && occupant.getTeam() == cachedOurTeam) {
        return true;
      }
    }
    return false;
  }

  /**
   * Populate the adjacent directions arrays based on the desired direction. This avoids allocating
   * new arrays every moveTo() call.
   */
  private static void populateAdjacentDirs(Direction desired) {
    // Even ID array: left first
    ADJACENT_DIRS_EVEN[0] = desired.rotateLeft();
    ADJACENT_DIRS_EVEN[1] = desired.rotateRight();
    ADJACENT_DIRS_EVEN[2] = desired.rotateLeft().rotateLeft();
    ADJACENT_DIRS_EVEN[3] = desired.rotateRight().rotateRight();
    // Odd ID array: right first
    ADJACENT_DIRS_ODD[0] = desired.rotateRight();
    ADJACENT_DIRS_ODD[1] = desired.rotateLeft();
    ADJACENT_DIRS_ODD[2] = desired.rotateRight().rotateRight();
    ADJACENT_DIRS_ODD[3] = desired.rotateLeft().rotateLeft();
  }

  /**
   * Populate the aggressive directions arrays for king rush movement. These include 6 directions (3
   * rotations each way) for more thorough path finding.
   */
  private static void populateAdjacentDirsAggressive(Direction toKing) {
    // Even ID array: left first
    AGGRESSIVE_DIRS_EVEN[0] = toKing.rotateLeft();
    AGGRESSIVE_DIRS_EVEN[1] = toKing.rotateRight();
    AGGRESSIVE_DIRS_EVEN[2] = toKing.rotateLeft().rotateLeft();
    AGGRESSIVE_DIRS_EVEN[3] = toKing.rotateRight().rotateRight();
    AGGRESSIVE_DIRS_EVEN[4] = toKing.rotateLeft().rotateLeft().rotateLeft();
    AGGRESSIVE_DIRS_EVEN[5] = toKing.rotateRight().rotateRight().rotateRight();
    // Odd ID array: right first
    AGGRESSIVE_DIRS_ODD[0] = toKing.rotateRight();
    AGGRESSIVE_DIRS_ODD[1] = toKing.rotateLeft();
    AGGRESSIVE_DIRS_ODD[2] = toKing.rotateRight().rotateRight();
    AGGRESSIVE_DIRS_ODD[3] = toKing.rotateLeft().rotateLeft();
    AGGRESSIVE_DIRS_ODD[4] = toKing.rotateRight().rotateRight().rotateRight();
    AGGRESSIVE_DIRS_ODD[5] = toKing.rotateLeft().rotateLeft().rotateLeft();
  }

  /**
   * Check if we're in a cluster and should spread out. Returns true if we have too many allies
   * nearby.
   */
  private static boolean shouldSpreadOut(RobotController rc, MapLocation me)
      throws GameActionException {
    RobotInfo[] nearbyAllies = rc.senseNearbyRobots(me, CLUSTER_SENSE_RADIUS_SQ, cachedOurTeam);
    return nearbyAllies.length >= SPREAD_TRIGGER_ALLIES;
  }

  /**
   * Get a spread direction - perpendicular to target direction, varied by ID. This helps rats
   * spread out when clustered.
   */
  private static Direction getSpreadDirection(Direction toTarget, int id) {
    if (toTarget == Direction.CENTER) return Direction.NORTH;
    // Alternate left/right based on ID to create spread
    if ((id & 1) == 0) {
      return toTarget.rotateLeft().rotateLeft(); // 90° left
    } else {
      return toTarget.rotateRight().rotateRight(); // 90° right
    }
  }

  private static void moveTo(RobotController rc, MapLocation me, MapLocation target)
      throws GameActionException {
    if (!rc.isMovementReady()) return;
    if (Clock.getBytecodesLeft() < LOW_BYTECODE_THRESHOLD) {
      Direction d = me.directionTo(target);
      if (d != Direction.CENTER) {
        Direction facing = rc.getDirection();
        if (facing != d && rc.canTurn()) {
          rc.turn(d);
        } else if (rc.canMoveForward()) {
          rc.moveForward();
        }
      }
      return;
    }

    int id = moveToLastID;
    int round = rc.getRoundNum();
    Direction facing = rc.getDirection();
    MapLocation ahead = rc.adjacentLocation(facing);
    Direction desired = me.directionTo(target);
    if (desired == Direction.CENTER) return;

    // Define startIdx early so it's available for all strategies
    int startIdx = id % DIRS_LEN;

    // ANTI-CLUMPING: If too many allies nearby, try to spread out
    // This prevents rats from bunching up and blocking each other
    // ASSASSIN BYPASS: Assassins skip spread logic - they must go straight to king
    int assassinDiv = (mapSize == 2) ? LARGE_MAP_ASSASSIN_DIVISOR : ASSASSIN_DIVISOR;
    boolean isAssassinInMoveTo = (id % assassinDiv) == 0;

    // Also skip spread on large maps entirely - it causes rats to get stuck
    boolean skipSpread = isAssassinInMoveTo || (mapSize == 2);

    if (!skipSpread && Clock.getBytecodesLeft() > 1500 && shouldSpreadOut(rc, me)) {
      Direction spreadDir = getSpreadDirection(desired, id);

      // Try spreading perpendicular, but only if it doesn't take us too far from target
      if (rc.canMove(spreadDir)) {
        MapLocation spreadDest = me.add(spreadDir);
        int currentDist = me.distanceSquaredTo(target);
        int spreadDist = spreadDest.distanceSquaredTo(target);
        // Allow spread if it doesn't increase distance too much
        if (spreadDist <= currentDist + 8) {
          int alliesAtSpread = countAlliesNearDest(rc, spreadDest);
          if (alliesAtSpread < CLUSTER_ALLY_THRESHOLD) {
            if (DEBUG) {
              System.out.println("SPREAD_MOVE:" + round + ":" + id + ":dir=" + spreadDir);
            }
            rc.move(spreadDir);
            return;
          }
        }
      }
    }

    // STRATEGY 1: Try direct movement toward target
    if (facing == desired && rc.canMoveForward() && !isHazardAt(rc, ahead)) {
      rc.moveForward();
      return;
    }

    // STRATEGY 2: Try moving in desired direction (handles case where not facing that way)
    if (rc.canMove(desired)) {
      MapLocation destLoc = me.add(desired);
      if (!isHazardAt(rc, destLoc)) {
        rc.move(desired);
        return;
      }
    }

    // STRATEGY 3: Try adjacent directions (rotateLeft/Right) - TRY ALL, not just turn
    // This is critical: instead of just turning and returning, we try to actually MOVE
    // Use pre-allocated arrays to avoid allocation in hot path
    populateAdjacentDirs(desired);
    Direction[] adjacentDirs = ((id & 1) == 0) ? ADJACENT_DIRS_EVEN : ADJACENT_DIRS_ODD;

    for (int k = 0; k < 4; k++) {
      Direction altDir = adjacentDirs[k];
      if (altDir == Direction.CENTER) continue;
      if (rc.canMove(altDir)) {
        MapLocation destLoc = me.add(altDir);
        if (!isHazardAt(rc, destLoc)) {
          rc.move(altDir);
          return;
        }
      }
    }

    // STRATEGY 4: Remove obstacles blocking our path
    // Try removing obstacles in both 'ahead' direction and 'desired' direction
    boolean removedObstacle = false;
    // First try the direction we're facing
    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      removedObstacle = true;
    } else if (rc.canRemoveRatTrap(ahead)) {
      rc.removeRatTrap(ahead);
      removedObstacle = true;
    } else if (rc.canRemoveCatTrap(ahead)) {
      rc.removeCatTrap(ahead);
      removedObstacle = true;
    }
    // If facing != desired, also try removing obstacles in desired direction
    if (!removedObstacle && facing != desired) {
      MapLocation desiredLoc = me.add(desired);
      if (rc.canRemoveDirt(desiredLoc)) {
        rc.removeDirt(desiredLoc);
        removedObstacle = true;
      } else if (rc.canRemoveRatTrap(desiredLoc)) {
        rc.removeRatTrap(desiredLoc);
        removedObstacle = true;
      } else if (rc.canRemoveCatTrap(desiredLoc)) {
        rc.removeCatTrap(desiredLoc);
        removedObstacle = true;
      }
    }
    // If we removed an obstacle but can't move (used action), return
    if (removedObstacle) return;

    // STRATEGY 5: Use Bug2 pathfinding - ALWAYS try it when direct paths fail
    // Don't rely on samePosRounds counter (it doesn't work with static variables)
    // Removed '&& bug2Dir != desired' check - Bug2 may return desired if it's actually valid now
    Direction bug2Dir = bug2(rc, target, me, id, round);
    if (bug2Dir != Direction.CENTER) {
      if (rc.canMove(bug2Dir)) {
        if (DEBUG) {
          System.out.println("BUG2_MOVE:" + round + ":" + id + ":dir=" + bug2Dir);
        }
        rc.move(bug2Dir);
        return;
      }
    }

    // STRATEGY 6: All 8 directions fallback with ally density consideration
    Direction bestFallbackDir = null;
    int lowestDensity = Integer.MAX_VALUE;

    for (int j = 0; j < DIRS_LEN; j++) {
      int i = (startIdx + j) % DIRS_LEN;
      Direction dir = DIRS[i];
      if (dir == Direction.CENTER) continue;
      if (rc.canMove(dir)) {
        MapLocation dest = me.add(dir);
        // Check for hazards using helper method (only check trap, not ally - we want any move)
        if (rc.canSenseLocation(dest)) {
          MapInfo info = rc.senseMapInfo(dest);
          if (info.getTrap() == TrapType.RAT_TRAP) continue;
        }

        int density = countAlliesNearDest(rc, dest);
        if (density < lowestDensity) {
          lowestDensity = density;
          bestFallbackDir = dir;
          // If we found a direction with no allies, take it immediately
          if (density == 0) break;
        }
      }
    }

    if (bestFallbackDir != null) {
      if (DEBUG) {
        System.out.println(
            "FALLBACK_MOVE:"
                + round
                + ":"
                + id
                + ":dir="
                + bestFallbackDir
                + ":density="
                + lowestDensity);
      }
      rc.move(bestFallbackDir);
      return;
    }

    // STRATEGY 7: If still can't move, try turning toward target for next round
    if (facing != desired && rc.isTurningReady() && rc.canTurn()) {
      rc.turn(desired);
      return;
    }

    // STRATEGY 8: Last resort - any movable direction at all (ignore all preferences)
    for (int j = 0; j < DIRS_LEN; j++) {
      int i = (startIdx + j) % DIRS_LEN;
      Direction dir = DIRS[i];
      if (dir != Direction.CENTER && rc.canMove(dir)) {
        rc.move(dir);
        return;
      }
    }
  }

  // ================================================================
  // CLOSE-RANGE KING RUSH MOVEMENT
  // ================================================================

  /**
   * Aggressive close-range movement for when we're near the enemy king. Unlike normal moveTo, this:
   * 1. Tries ALL 8 directions to find one that gets us closer 2. Doesn't wait to be stuck before
   * trying alternates 3. Prioritizes any movement over turning Returns true if we moved or turned,
   * false if completely blocked.
   */
  private static boolean moveToKingAggressive(
      RobotController rc, MapLocation me, MapLocation kingLoc) throws GameActionException {
    // FIX #2: Cache round and id at start for consistency and bytecode savings
    int round = rc.getRoundNum();
    int id = rc.getID();

    if (!rc.isMovementReady()) {
      if (DEBUG) {
        System.out.println("KING_MOVE_NOT_READY:" + round + ":" + id + ":pos=" + me);
      }
      return false;
    }

    int currentDist = me.distanceSquaredTo(kingLoc);
    Direction toKing = me.directionTo(kingLoc);
    if (toKing == Direction.CENTER) return false;

    Direction facing = rc.getDirection();

    // Strategy 1: If facing king and can move forward, do it
    if (facing == toKing && rc.canMoveForward()) {
      if (DEBUG) {
        System.out.println("KING_MOVE_FORWARD:" + round + ":" + id + ":dir=" + toKing);
      }
      rc.moveForward();
      return true;
    }

    // Strategy 2: Try direct move toward king
    // SIMPLIFIED: No dirt avoidance - go straight through
    if (rc.canMove(toKing)) {
      MapLocation directDest = me.add(toKing);
      if (!isHazardAt(rc, directDest)) {
        if (DEBUG) {
          System.out.println("KING_MOVE_DIRECT:" + round + ":" + id + ":dir=" + toKing);
        }
        rc.move(toKing);
        return true;
      }
    }

    // Strategy 3: Try adjacent directions (rotateLeft/Right of toKing)
    // SIMPLIFIED: No dirt preference - just find any direction that gets us closer
    // ID-BASED VARIATION: Use pre-populated arrays based on ID to spread rats across paths
    populateAdjacentDirsAggressive(toKing);
    Direction[] tryDirs = ((id & 1) == 0) ? AGGRESSIVE_DIRS_EVEN : AGGRESSIVE_DIRS_ODD;

    // Find best direction that gets us closer (or at least same distance)
    // ANTI-CLUMPING: Factor in ally density at destination
    Direction bestDir = null;
    int bestScore = Integer.MAX_VALUE; // Lower is better

    for (Direction dir : tryDirs) {
      if (dir == Direction.CENTER) continue;
      if (!rc.canMove(dir)) continue;

      // Check if this direction gets us closer or same distance
      MapLocation dest = me.add(dir);
      int newDist = dest.distanceSquaredTo(kingLoc);

      // Use helper method to check for hazards
      if (isHazardAt(rc, dest)) continue;

      // ANTI-CLUMPING: Count allies near destination
      int allyDensity = countAlliesNearDest(rc, dest);

      // Calculate score: distance + ally density penalty
      int score = newDist + (allyDensity * 5);

      if (score < bestScore) {
        bestScore = score;
        bestDir = dir;
      }
    }

    if (bestDir != null) {
      if (DEBUG) {
        System.out.println(
            "KING_MOVE_ALT:" + round + ":" + id + ":dir=" + bestDir + ":score=" + bestScore);
      }
      rc.move(bestDir);
      return true;
    }

    // Strategy 4: Can't move - try turning toward king for next round
    if (facing != toKing && rc.canTurn()) {
      if (DEBUG) {
        System.out.println("KING_MOVE_TURN:" + round + ":" + id + ":toDir=" + toKing);
      }
      rc.turn(toKing);
      return true;
    }

    // Strategy 5: Try to remove obstacles in front
    MapLocation ahead = rc.adjacentLocation(facing);
    if (rc.canRemoveDirt(ahead)) {
      if (DEBUG) {
        System.out.println("KING_MOVE_REMOVE_DIRT:" + round + ":" + id);
      }
      rc.removeDirt(ahead);
      return true;
    }
    if (rc.canRemoveRatTrap(ahead)) {
      if (DEBUG) {
        System.out.println("KING_MOVE_REMOVE_TRAP:" + round + ":" + id);
      }
      rc.removeRatTrap(ahead);
      return true;
    }

    // FIX #4 & #5: Use Bug2 as fallback - use rc.canMove(bug2Dir) instead of canMoveForward()
    // This correctly handles the direction returned by Bug2
    Direction bug2Dir = bug2(rc, kingLoc, me, id, round);
    if (bug2Dir != Direction.CENTER && rc.canMove(bug2Dir)) {
      if (DEBUG) {
        System.out.println("KING_MOVE_BUG2:" + round + ":" + id + ":dir=" + bug2Dir);
      }
      rc.move(bug2Dir);
      return true;
    }

    // FIX #1: Log when completely blocked for diagnosis
    if (DEBUG) {
      System.out.println(
          "KING_MOVE_BLOCKED:"
              + round
              + ":"
              + id
              + ":pos="
              + me
              + ":facing="
              + facing
              + ":toKing="
              + toKing
              + ":dist="
              + currentDist);
    }
    return false;
  }

  // ================================================================
  // BUG2 PATHFINDING
  // ================================================================

  private static MapLocation bugTarget = null;
  private static boolean bugTracing = false;
  private static Direction bugTracingDir = Direction.NORTH;
  private static int bugStartDist = 0;
  private static int lastBugID = -1;

  // Tolerance for Bug2 target changes - don't reset if target moved < this distance squared
  // This prevents mid-trace resets when enemy king position updates slightly
  // INCREASED from 25 to 100 to prevent constant target oscillation on large maps
  private static final int BUG2_TARGET_TOLERANCE_SQ = 100; // 10 tiles

  /** Check if we can safely move in a direction (avoids rat traps). */
  private static boolean canMoveSafely(RobotController rc, MapLocation me, Direction dir)
      throws GameActionException {
    if (!rc.canMove(dir)) return false;
    MapLocation dest = me.add(dir);
    if (rc.canSenseLocation(dest)) {
      MapInfo info = rc.senseMapInfo(dest);
      if (info.getTrap() == TrapType.RAT_TRAP) return false;
    }
    return true;
  }

  /**
   * Check if we can safely move in a direction (avoids rat traps only). SIMPLIFIED: No longer
   * prefers non-dirt paths - just checks if move is possible.
   */
  private static int getMoveSafetyScore(RobotController rc, MapLocation me, Direction dir)
      throws GameActionException {
    if (!rc.canMove(dir)) return 0;
    MapLocation dest = me.add(dir);
    if (!rc.canSenseLocation(dest)) return 1; // Assume passable if can't sense

    MapInfo info = rc.senseMapInfo(dest);
    if (info.getTrap() == TrapType.RAT_TRAP) return 0; // Avoid traps completely

    return 1; // Can move - don't distinguish dirt vs non-dirt
  }

  private static Direction bug2(
      RobotController rc, MapLocation target, MapLocation me, int id, int round)
      throws GameActionException {
    // FIX: Use tolerance-based check instead of exact equality
    // This prevents Bug2 state reset when enemy king position updates slightly mid-trace
    boolean targetChanged = false;
    if (lastBugID != id || bugTarget == null) {
      targetChanged = true;
    } else {
      // Only reset if target moved significantly (> 5 tiles)
      int targetMoveDist = bugTarget.distanceSquaredTo(target);
      if (targetMoveDist > BUG2_TARGET_TOLERANCE_SQ) {
        targetChanged = true;
        if (DEBUG) {
          System.out.println(
              "BUG2_TARGET_CHANGE:"
                  + round
                  + ":"
                  + id
                  + ":old="
                  + bugTarget
                  + ":new="
                  + target
                  + ":dist="
                  + targetMoveDist);
        }
      }
    }

    if (targetChanged) {
      bugTarget = target;
      bugTracing = false;
      lastBugID = id;
    }

    Direction toTarget = me.directionTo(target);
    if (toTarget == Direction.CENTER) return Direction.CENTER;

    // SIMPLIFIED: No dirt avoidance - go straight through
    if (!bugTracing) {
      if (canMoveSafely(rc, me, toTarget)) return toTarget;
    }

    if (!bugTracing) {
      bugTracing = true;
      bugTracingDir = toTarget;
      bugStartDist = me.distanceSquaredTo(target);
    }

    if (bugTracing) {
      int curDist = me.distanceSquaredTo(target);

      // SIMPLIFIED: No dirt avoidance - simple tracing
      if (curDist < bugStartDist && canMoveSafely(rc, me, toTarget)) {
        bugTracing = false;
        return toTarget;
      }
      for (int i = 0; i < 8; i++) {
        if (canMoveSafely(rc, me, bugTracingDir)) {
          Direction result = bugTracingDir;
          bugTracingDir = bugTracingDir.rotateLeft();
          return result;
        }
        bugTracingDir = bugTracingDir.rotateRight();
      }
    }
    return toTarget;
  }
}
