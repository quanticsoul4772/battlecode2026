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
  private static final int KITE_RETREAT_DIST = 2; // Retreat 2 tiles after attacking
  private static final int KITE_ENGAGE_DIST_SQ = 8; // Start kiting when this close

  // ========== OVERKILL PREVENTION ==========
  private static final int OVERKILL_HP_THRESHOLD =
      20; // Don't attack if HP < this and allies nearby

  // ========== RETREAT COORDINATION ==========
  private static final int RETREAT_HP_RATIO_THRESHOLD = 40; // Retreat if our HP < 40% of enemy
  private static final int RETREAT_SIGNAL_STALE = 5; // Retreat signal expires after 5 rounds

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
  private static final int MEDIUM_MAP_CONTINUOUS_RESERVE = 200; // Balanced buffer for medium maps
  private static final int MEDIUM_MAP_SPAWN_COOLDOWN = 2; // Spawn every 2 rounds on medium maps
  private static final int MEDIUM_MAP_ALL_ATTACK_ROUNDS = 15; // Short attack burst, then balance
  private static final int MEDIUM_MAP_EARLY_ATTACKER_RATIO = 6; // 60% attackers on medium maps
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
  private static final int FORCED_MOVEMENT_THRESHOLD = 3;
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
  private static final int SENSE_THROTTLE_ROUNDS = 3;
  private static final int MAX_SQUEAKS_TO_READ = 12;

  // ========== CAT DEFENSE CONFIG ==========
  private static final int CAT_FLEE_DIST_SQ = 16;
  private static final int CAT_KING_FLEE_DIST_SQ = 49;
  private static final int CAT_KITE_MIN_DIST_SQ = 10;
  private static final int CAT_KITE_MAX_DIST_SQ = 25;
  private static final int CAT_TRAP_TARGET = 10;
  private static final int CAT_TRAP_END_ROUND = 40;

  // ========== RAT DEFENSE CONFIG ==========
  private static final int ALLY_MIN_DIST_SQ = 4; // Prevent clumping
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

  // ========== ENEMY KING HP TRACKING ==========
  private static final int ENEMY_KING_STARTING_HP = 500; // Rat kings start with 500 HP
  private static final int BASE_ATTACK_DAMAGE = 10; // Base bite damage

  // ========== RESOURCE DENIAL CONFIG ==========
  private static final int DENIAL_PATROL_DIVISOR = 7; // id % 7 == 0 becomes denial patrol
  private static final int DENIAL_PATROL_START_ROUND = 100; // Start denial after round 100
  private static final int CHEESE_CARRIER_PRIORITY_BONUS =
      100; // Extra priority for cheese carriers
  private static final int NEAR_KING_PRIORITY_BONUS = 50; // Extra priority if near enemy king

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

  // Cheese mine tracking
  private static MapLocation[] cachedMineLocations = new MapLocation[MAX_TRACKED_MINES];
  private static int cachedNumMines = 0;
  private static int lastMineReadRound = -1;

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
      if (cachedEnemyKingLoc == null
          || cachedEnemyKingLoc.x != cachedEnemyX
          || cachedEnemyKingLoc.y != cachedEnemyY) {
        cachedEnemyKingLoc = new MapLocation(cachedEnemyX, cachedEnemyY);
      }
      lastKingLocRound = r;
    }

    // Focus fire target (slots 5-6) - check staleness FIRST to skip read if stale
    int focusRound = rc.readSharedArray(6);
    if ((r - focusRound) <= FOCUS_FIRE_STALE_ROUNDS) {
      int focusPacked = rc.readSharedArray(5);
      if (focusPacked != 0) {
        int fx = focusPacked & 0x3F;
        int fy = (focusPacked >> 6) & 0x3F;
        int fhp = ((focusPacked >> 12) & 0xF) * 10;
        if (cachedFocusTarget == null || cachedFocusTarget.x != fx || cachedFocusTarget.y != fy) {
          cachedFocusTarget = new MapLocation(fx, fy);
        }
        cachedFocusTargetHP = fhp;
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

    // Enemy ring buffer (slots 8-11)
    for (int i = 0; i < 4; i++) {
      int packed = rc.readSharedArray(8 + i);
      if (packed != 0) {
        cachedEnemyRingX[i] = packed & 0x3F;
        cachedEnemyRingY[i] = (packed >> 6) & 0x3F;
        cachedEnemyRingRound[i] = r - ((packed >> 12) & 0xF); // Decode round delta
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

    // Rally point (slot 35)
    int rallyPacked = rc.readSharedArray(35);
    if (rallyPacked > 0) {
      int rx = rallyPacked & 0x3F;
      int ry = (rallyPacked >> 6) & 0x3F;
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

    // Read mine locations (slots 22-25) - only refresh every 20 rounds
    if (r - lastMineReadRound >= 20) {
      cachedNumMines = rc.readSharedArray(40);
      if (cachedNumMines > MAX_TRACKED_MINES) cachedNumMines = MAX_TRACKED_MINES;
      for (int i = 0; i < cachedNumMines; i++) {
        int packed = rc.readSharedArray(22 + i);
        if (packed > 0) {
          int mx = packed & 0x3F;
          int my = (packed >> 6) & 0x3F;
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
    // Pack: bits 0-5 = X, bits 6-11 = Y, bits 12-15 = round delta (capped at 15)
    int roundDelta = 0; // Current round = 0 delta
    int packed = (enemyLoc.x & 0x3F) | ((enemyLoc.y & 0x3F) << 6) | ((roundDelta & 0xF) << 12);
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

    if (DEBUG && (round & 63) == 0) {
      System.out.println(
          "KING:"
              + round
              + ":cheese="
              + cheese
              + " HP="
              + kingHP
              + " spawned="
              + spawnCount
              + " strategy="
              + cachedDetectedStrategy
              + " enemyKingHP="
              + cachedEnemyKingHP
              + " dmgDealt="
              + cachedDamageToEnemyKing);
    }

    // Write position
    if (me.x != lastKingX || me.y != lastKingY) {
      rc.writeSharedArray(0, me.x);
      rc.writeSharedArray(1, me.y);
      lastKingX = me.x;
      lastKingY = me.y;
    }

    // Calculate enemy king (round 1)
    if (round == 1) {
      rc.writeSharedArray(2, mapWidth - 1 - me.x);
      rc.writeSharedArray(3, mapHeight - 1 - me.y);
    }

    boolean smallMap = mapSize == 0;
    int initialSpawnTarget = smallMap ? SMALL_MAP_INITIAL_SPAWN : INITIAL_SPAWN_COUNT;
    int cheeseReserve = smallMap ? SMALL_MAP_KING_RESERVE : KING_CHEESE_RESERVE;
    int collectorMin = smallMap ? SMALL_MAP_COLLECTOR_MIN : COLLECTOR_MINIMUM;
    int trapTarget = smallMap ? SMALL_MAP_RAT_TRAP_TARGET : RAT_TRAP_EARLY_TARGET;

    // Spawning logic (same as before but with strategy adaptation)
    if (smallMap) {
      if (spawnCount < initialSpawnTarget) {
        int cost = rc.getCurrentRatCost();
        if (cheese > cost + cheeseReserve) {
          spawnRat(rc, me);
          if (DEBUG) System.out.println("RUSH_SPAWN:" + round + ":rat#" + spawnCount);
        }
      }
      if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget && spawnCount >= 3) {
        placeDefensiveTrapsTowardEnemy(rc, me);
      }
    } else {
      if (round <= RAT_TRAP_EARLY_WINDOW && ratTrapCount < trapTarget) {
        placeDefensiveTrapsTowardEnemy(rc, me);
      }
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

    if (DEBUG && (round & 31) == 0) {
      System.out.println(
          "FLANK:"
              + round
              + ":center="
              + centerCount
              + " left="
              + leftCount
              + " right="
              + rightCount);
    }

    // Spawn initial rats
    if (!smallMap && spawnCount < initialSpawnTarget) {
      int cost = rc.getCurrentRatCost();
      if (cheese > cost + cheeseReserve) {
        spawnRat(rc, me);
      }
    } else {
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
        if (DEBUG)
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
      int rallyPacked = (rallyX & 0x3F) | ((rallyY & 0x3F) << 6);
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

    // FLEE FROM CATS
    if (nearbyCat != null) {
      MapLocation catLoc = nearbyCat.getLocation();
      Direction awayFromCat = catLoc.directionTo(me);
      if (awayFromCat == Direction.CENTER) awayFromCat = DIRS[round & 7];
      if (kingFleeMove(rc, awayFromCat)) return;
    }

    // Normal movement
    if ((round & 1) == 0) {
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
    int hp = target.getHealth();
    int packed = (loc.x & 0x3F) | ((loc.y & 0x3F) << 6) | (((hp / 10) & 0xF) << 12);
    rc.writeSharedArray(5, packed);
    rc.writeSharedArray(6, round);
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
          int packed = (mineLoc.x & 0x3F) | ((mineLoc.y & 0x3F) << 6);
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
   * Updates slot 41 (cumulative damage).
   */
  private static void recordDamageToEnemyKing(RobotController rc, int damage)
      throws GameActionException {
    int currentDamage = rc.readSharedArray(41);
    int newDamage = currentDamage + damage;
    // Cap at enemy king starting HP (can't deal more damage than they have)
    if (newDamage > ENEMY_KING_STARTING_HP) newDamage = ENEMY_KING_STARTING_HP;
    rc.writeSharedArray(41, newDamage);
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

  /** Called when we can see the enemy king directly. Updates confirmed HP. */
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
        rc.writeSharedArray(41, actualDamage);
      }

      // Store confirmed HP in slot 42
      rc.writeSharedArray(42, actualHP);

      if (DEBUG) {
        System.out.println(
            "CONFIRMED_KING_HP:" + round + ":actual=" + actualHP + ":damage=" + actualDamage);
      }
    }
  }

  private static boolean kingFleeMove(RobotController rc, Direction awayDir)
      throws GameActionException {
    if (awayDir == Direction.CENTER) return false;
    MapLocation ahead = rc.adjacentLocation(awayDir);
    if (rc.canSenseLocation(ahead) && rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      return true;
    }
    Direction facing = rc.getDirection();
    if (facing != awayDir && rc.canTurn()) {
      rc.turn(awayDir);
      return true;
    }
    if (rc.canMove(awayDir)) {
      rc.move(awayDir);
      return true;
    }
    Direction left = awayDir.rotateLeft();
    if (rc.canMove(left)) {
      rc.move(left);
      return true;
    }
    Direction right = awayDir.rotateRight();
    if (rc.canMove(right)) {
      rc.move(right);
      return true;
    }
    Direction left2 = left.rotateLeft();
    if (rc.canMove(left2)) {
      rc.move(left2);
      return true;
    }
    Direction right2 = right.rotateRight();
    if (rc.canMove(right2)) {
      rc.move(right2);
      return true;
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
    Direction towardCenter = me.directionTo(cachedMapCenter);
    CAT_TRAP_PRIORITIES[0] = towardCenter;
    CAT_TRAP_PRIORITIES[1] = towardCenter.rotateLeft();
    CAT_TRAP_PRIORITIES[2] = towardCenter.rotateRight();
    CAT_TRAP_PRIORITIES[3] = towardCenter.rotateLeft().rotateLeft();
    CAT_TRAP_PRIORITIES[4] = towardCenter.rotateRight().rotateRight();

    for (int p = 0; p < 5; p++) {
      Direction dir = CAT_TRAP_PRIORITIES[p];
      if (dir == Direction.CENTER) continue;
      int dx = dir.dx;
      int dy = dir.dy;
      for (int dist = 2; dist <= 3; dist++) {
        MapLocation trapLoc = me.translate(dx * dist, dy * dist);
        if (rc.canPlaceCatTrap(trapLoc)) {
          rc.placeCatTrap(trapLoc);
          catTrapCount++;
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

  private static MapLocation lastBabyPos = null;
  private static int lastBabyID = -1;
  private static int samePosRounds = 0;
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

    if (allInActive) {
      // ALL-IN MODE: Everyone attacks, no exceptions
      myRole = 0;
      if (DEBUG && (round % 20) == 0) System.out.println("ALL_IN_ATTACK:" + round + ":" + id);
    } else if (emergencyLevel >= 3) {
      // CRITICAL: All rats become collectors except those already in combat
      myRole = 1;
    } else if (mapSize == 0) {
      // Small map: all attack for first 25 rounds
      if (round < SMALL_MAP_ALL_ATTACK_ROUNDS && emergencyLevel == 0) {
        myRole = 0;
      } else {
        myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
      }
    } else if (mapSize == 1) {
      // Medium map: all attack for first 40 rounds (unless emergency)
      if (round < MEDIUM_MAP_ALL_ATTACK_ROUNDS && emergencyLevel == 0) {
        myRole = 0;
      } else {
        myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
      }
    } else {
      myRole = ((id % 10) < cachedAttackerRatio) ? 0 : 1;
    }

    int health = rc.getHealth();
    MapLocation me = rc.getLocation();

    bcBefore = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo nearbyCat = (mapSize == 0) ? null : findNearbyCat(rc);
    if (PROFILE) profSenseNeutral += Clock.getBytecodeNum() - bcBefore;

    boolean kingNeedsHelp = rc.readSharedArray(4) == 1;
    int distToKing = me.distanceSquaredTo(cachedOurKingLoc);

    // RETREAT CHECK: If retreat signal active, fall back to rally point (not king)
    if (cachedShouldRetreat && myRole == 0 && distToKing > 16) {
      if (DEBUG) System.out.println("RETREAT:" + round + ":" + id + ":falling back");
      // Use rally point if available, otherwise fall back to king
      MapLocation retreatTarget = (cachedRallyPoint != null) ? cachedRallyPoint : cachedOurKingLoc;
      moveTo(rc, me, retreatTarget);
      if (PROFILE) profEndTurn(round, id, false);
      return;
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

    if (myRole == 0) {
      runAttacker(rc, nearbyCat, round, id, me, kingNeedsHelp, distToKing);
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

  private static MapLocation getSwarmTarget(MapLocation me, int swarmRole, int round) {
    int distToEnemy = me.distanceSquaredTo(cachedEnemyKingLoc);
    if (distToEnemy <= SWARM_ENGAGE_DIST_SQ) return cachedEnemyKingLoc;
    if (swarmRole == 0) return cachedEnemyKingLoc;

    // Cache swarm direction to avoid repeated directionTo() calls
    if (lastSwarmDirRound != round) {
      cachedSwarmDir = cachedOurKingLoc.directionTo(cachedEnemyKingLoc);
      lastSwarmDirRound = round;
    }
    Direction toEnemy = cachedSwarmDir;
    if (toEnemy == Direction.CENTER) return cachedEnemyKingLoc;

    // DYNAMIC SWARM REBALANCING: Check if our flank is understaffed
    boolean isLeftFlank = (swarmRole <= 2);
    int myFlankCount = isLeftFlank ? cachedLeftCount : cachedRightCount;

    // If our flank has 0 rats (truly orphaned), redirect to center
    if (myFlankCount < MIN_FLANK_COUNT) {
      return cachedEnemyKingLoc;
    }

    // Update cached flank locations periodically to avoid allocation
    if (lastFlankLocRound != round) {
      Direction leftDir = toEnemy.rotateLeft().rotateLeft();
      Direction rightDir = toEnemy.rotateRight().rotateRight();

      int leftX = cachedEnemyKingLoc.x + leftDir.dx * SWARM_FLANK_DIST;
      int leftY = cachedEnemyKingLoc.y + leftDir.dy * SWARM_FLANK_DIST;
      if (leftX < 0) leftX = 0;
      if (leftX >= mapWidth) leftX = mapWidth - 1;
      if (leftY < 0) leftY = 0;
      if (leftY >= mapHeight) leftY = mapHeight - 1;

      int rightX = cachedEnemyKingLoc.x + rightDir.dx * SWARM_FLANK_DIST;
      int rightY = cachedEnemyKingLoc.y + rightDir.dy * SWARM_FLANK_DIST;
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

  private static void runAttacker(
      RobotController rc,
      RobotInfo nearbyCat,
      int round,
      int id,
      MapLocation me,
      boolean kingNeedsHelp,
      int distToKing)
      throws GameActionException {

    int swarmRole = getSwarmRole(id);
    MapLocation swarmTarget = getSwarmTarget(me, swarmRole, round);

    // Sense enemies
    int bcSense = PROFILE ? Clock.getBytecodeNum() : 0;
    RobotInfo[] enemies = rc.senseNearbyRobots(BABY_RAT_VISION_SQ, cachedEnemyTeam);
    if (PROFILE) profSenseEnemy += Clock.getBytecodeNum() - bcSense;

    // Sense allies for overkill prevention
    RobotInfo[] allies = rc.senseNearbyRobots(BABY_RAT_VISION_SQ, cachedOurTeam);

    // Find best target AND focus target in single pass (bytecode optimization)
    RobotInfo bestTarget = null;
    RobotInfo focusTarget = null;
    int minDist = Integer.MAX_VALUE;

    for (int i = enemies.length - 1; i >= 0; i--) {
      RobotInfo enemy = enemies[i];
      if (!enemy.getType().isBabyRatType()) continue;
      MapLocation enemyLoc = enemy.getLocation();
      int dist = me.distanceSquaredTo(enemyLoc);
      if (dist < minDist) {
        minDist = dist;
        bestTarget = enemy;
      }
      // Check focus target in same loop
      if (cachedFocusTarget != null && enemyLoc.equals(cachedFocusTarget)) {
        focusTarget = enemy;
      }
    }

    // Prioritize focus target
    if (focusTarget != null) bestTarget = focusTarget;

    // KITING STATE MACHINE
    if (bestTarget != null && minDist <= KITE_ENGAGE_DIST_SQ) {
      MapLocation targetLoc = bestTarget.getLocation();

      // Predictive targeting: aim where enemy will be
      MapLocation predictedLoc = predictEnemyPosition(targetLoc, round);

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
              kiteState = KITE_STATE_RETREAT;
              kiteRetreatTurns = KITE_RETREAT_DIST;
              if (DEBUG) System.out.println("KITE_ATTACK:" + round + ":" + id);
              return;
            }
          }
          // Can't attack, try to get closer
          moveTo(rc, me, targetLoc);
          return;

        case KITE_STATE_RETREAT:
          // Retreat after attacking
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

    // Move to swarm target
    moveTo(rc, me, swarmTarget);
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
          if (enemy.getType().isBabyRatType() && enemy.getLocation().equals(cachedFocusTarget)) {
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

    Direction facing = rc.getDirection();
    MapLocation ahead = rc.adjacentLocation(facing);

    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead);
      return;
    }
    if (rc.canRemoveRatTrap(ahead)) {
      rc.removeRatTrap(ahead);
      return;
    }
    if (rc.canRemoveCatTrap(ahead)) {
      rc.removeCatTrap(ahead);
      return;
    }

    // Use cached ID from runBabyRat instead of calling rc.getID()
    int id = moveToLastID;
    if (lastBabyID != id) {
      lastBabyPos = null;
      samePosRounds = 0;
      lastBabyID = id;
    }
    if (lastBabyPos != null && lastBabyPos.equals(me)) {
      samePosRounds++;
    } else {
      samePosRounds = 0;
    }
    lastBabyPos = me;

    if (samePosRounds >= FORCED_MOVEMENT_THRESHOLD) {
      Direction bug2Dir = bug2(rc, target, me, id);
      if (bug2Dir != Direction.CENTER) {
        if (facing != bug2Dir && rc.canTurn()) {
          rc.turn(bug2Dir);
          return;
        }
        if (rc.canMoveForward()) {
          rc.moveForward();
          return;
        }
      }
    }

    Direction desired = me.directionTo(target);
    if (desired == Direction.CENTER) return;

    if (facing == desired) {
      MapLocation nextLoc = ahead;
      if (!rc.onTheMap(nextLoc)) {
        Direction left = desired.rotateLeft();
        if (left != Direction.CENTER && rc.canTurn()) {
          rc.turn(left);
          return;
        }
        Direction right = desired.rotateRight();
        if (right != Direction.CENTER && rc.canTurn()) {
          rc.turn(right);
          return;
        }
        return;
      }
      if (rc.canSenseLocation(nextLoc)) {
        MapInfo nextInfo = rc.senseMapInfo(nextLoc);
        if (!nextInfo.isPassable()) {
          Direction left = desired.rotateLeft();
          if (rc.canTurn()) {
            rc.turn(left);
            return;
          }
          Direction right = desired.rotateRight();
          if (rc.canTurn()) {
            rc.turn(right);
            return;
          }
        }
        if (nextInfo.getTrap() == TrapType.RAT_TRAP) {
          Direction left = desired.rotateLeft();
          if (rc.canTurn()) {
            rc.turn(left);
            return;
          }
          Direction right = desired.rotateRight();
          if (rc.canTurn()) {
            rc.turn(right);
            return;
          }
        }
        if (rc.isLocationOccupied(nextLoc)) {
          RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);
          if (blocker != null && blocker.getTeam() == cachedOurTeam) {
            Direction left = desired.rotateLeft();
            if (rc.canTurn()) {
              rc.turn(left);
              return;
            }
            Direction right = desired.rotateRight();
            if (rc.canTurn()) {
              rc.turn(right);
              return;
            }
          }
        }
      }
      if (rc.canMoveForward()) {
        rc.moveForward();
        return;
      }
    }

    if (facing != desired && rc.isTurningReady() && rc.canTurn()) {
      rc.turn(desired);
      return;
    }

    if (!rc.canMoveForward() && rc.getMovementCooldownTurns() < 10) {
      for (int i = 0; i < DIRS_LEN; i++) {
        Direction dir = DIRS[i];
        if (rc.canMove(dir)) {
          // Ally spacing check: avoid clumping
          MapLocation dest = me.add(dir);
          boolean tooClose = false;
          RobotInfo[] nearbyAllies = rc.senseNearbyRobots(dest, ALLY_MIN_DIST_SQ, cachedOurTeam);
          if (nearbyAllies.length > 2) tooClose = true; // Too many allies nearby
          if (!tooClose) {
            rc.move(dir);
            return;
          }
        }
      }
      // If all dirs have clumping, move anyway to avoid being stuck
      for (int i = 0; i < DIRS_LEN; i++) {
        Direction dir = DIRS[i];
        if (rc.canMove(dir)) {
          rc.move(dir);
          return;
        }
      }
    }
  }

  // ================================================================
  // BUG2 PATHFINDING
  // ================================================================

  private static MapLocation bugTarget = null;
  private static boolean bugTracing = false;
  private static Direction bugTracingDir = Direction.NORTH;
  private static int bugStartDist = 0;
  private static int lastBugID = -1;

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

  private static Direction bug2(RobotController rc, MapLocation target, MapLocation me, int id)
      throws GameActionException {
    if (lastBugID != id || bugTarget == null || !bugTarget.equals(target)) {
      bugTarget = target;
      bugTracing = false;
      lastBugID = id;
    }

    Direction toTarget = me.directionTo(target);
    if (toTarget == Direction.CENTER) return Direction.CENTER;

    // Use safe move check that avoids rat traps
    if (!bugTracing && canMoveSafely(rc, me, toTarget)) return toTarget;

    if (!bugTracing) {
      bugTracing = true;
      bugTracingDir = toTarget;
      bugStartDist = me.distanceSquaredTo(target);
    }

    if (bugTracing) {
      int curDist = me.distanceSquaredTo(target);
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
