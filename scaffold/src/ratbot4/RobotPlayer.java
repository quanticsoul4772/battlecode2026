package ratbot4;

import battlecode.common.*;

/**
 * ratbot4 - Battlecode 2026 Competition Bot
 *
 * <p>LEARNING GUIDE FOR JUNIOR DEVELOPERS: This bot demonstrates core game concepts and Java
 * patterns. Comments explain WHAT the code does, WHY we do it, and HOW you can modify it.
 *
 * <p>==================== STRATEGY OVERVIEW ====================
 *
 * <p>POPULATION: 12 rats total - 6 Attackers (rush enemy king, kill enemy rats) - 6 Collectors
 * (gather cheese, feed our king) - WHY 12? Balance between combat power and economic sustainability
 * - TRY CHANGING: spawnCount < 12 to spawn more/fewer rats
 *
 * <p>COMBAT: Cheese-enhanced attacks - Base damage: 10 HP per hit (takes 10 hits to kill enemy rat)
 * - Enhanced: Spend 8 cheese → 13 damage (takes 8 hits to kill) - WHY 8 cheese? Formula is 10 +
 * ceil(log2(cheese)), 8 gives +3 damage - TRY CHANGING: Use 16 cheese for 14 damage, or 32 for 15
 * damage
 *
 * <p>ECONOMY: King needs 3 cheese/round to survive - Without deliveries, king loses 10 HP/round and
 * dies - 6 collectors × ~5 cheese/min = 30 cheese/min income - King consumption: 3 cheese/round =
 * sustainable - TRY CHANGING: More collectors if king starving, fewer if economy strong
 *
 * <p>==================== GAME MECHANICS ====================
 *
 * <p>ATTACKING: - Range: Adjacent only (must be 1 tile away, distanceSquared <= 2) - Vision: 90°
 * cone in facing direction (must face target to see it!) - Action: rc.attack(location) or
 * rc.attack(location, cheeseAmount)
 *
 * <p>MOVEMENT: - Forward move: 10 cooldown (cheap) - Strafe move: 18 cooldown (expensive! avoid
 * when possible) - Turn: 10 cooldown - Cooldown reduces by 10 per round (can't move if cooldown >=
 * 10)
 *
 * <p>SPAWNING: - Cost: 10 cheese for first 4 rats, 20 for next 4, 30 for next 4... - Formula: 10 +
 * 10 × floor(livingRats / 4) - Range: distanceSquared <= 4 (max 2 tiles from king center)
 *
 * <p>==================== JAVA CONCEPTS ====================
 *
 * <p>STATIC VARIABLES: Persist across function calls - private static int spawnCount = 0; - Keeps
 * count even after function returns - WARNING: In Battlecode, static vars are per-robot, not
 * shared!
 *
 * <p>SHARED ARRAY: Team communication (64 slots, 10-bit values 0-1023) - Only kings can WRITE - All
 * robots can READ - Use for: king position, enemy position, coordination
 *
 * <p>EXCEPTIONS: try/catch handles errors gracefully - GameActionException: Invalid game action -
 * Catch prevents crash, logs error, continues next round
 */
public class RobotPlayer {
  public static void run(RobotController rc) throws GameActionException {
    while (true) {
      try {
        if (rc.getType() == UnitType.RAT_KING) {
          runKing(rc);
        } else {
          runBabyRat(rc);
        }
      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }

  // ================================================================
  // KING BEHAVIOR
  // ================================================================
  // King spawns rats, places traps, tracks enemies
  // Kings have 360° vision (can see all around) but only 5 tile radius
  //
  // JAVA LEARNING: Static variables
  // These variables persist across all function calls for this king
  // They're NOT shared between different robots - each king has its own copy

  private static int spawnCount = 0;
  private static int trapCount = 0;

  private static void runKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int cheese = rc.getGlobalCheese();
    MapLocation me = rc.getLocation();

    // PHASE 1: Use GameConstants (king consumes 3 cheese per round)
    int cheeseReserve = 100; // 33 rounds survival buffer

    // SHARED ARRAY COMMUNICATION
    rc.writeSharedArray(0, me.x);
    rc.writeSharedArray(1, me.y);

    // Calculate enemy king position once
    if (round == 1) {
      rc.writeSharedArray(2, rc.getMapWidth() - 1 - me.x);
      rc.writeSharedArray(3, rc.getMapHeight() - 1 - me.y);
    }

    // ==================== SPAWNING LOGIC ====================
    // Phase 1: Spawn 12 rats as fast as possible (rounds 1-12)
    // Phase 2: Spawn 1 rat every 50 rounds to replace losses
    //
    // TUNABLE PARAMETERS:
    // - spawnCount < 12: Change to 8 for fewer rats, 15 for more (affects combat/economy balance)
    // - cheese > cost + 100: Reserve amount (100 = 33 rounds king survival, increase if starving)
    // - round % 50: Replacement frequency (50 = slow, 25 = fast, 100 = very slow)
    // - spawnCount < 20: Maximum total rats (cap to prevent over-spending)
    //
    // JAVA LEARNING: Conditional logic (if/else)
    // First condition checked (spawnCount < 12) takes priority
    // "else if" only executes if first condition is false
    if (spawnCount < 12) {
      int cost = rc.getCurrentRatCost();
      if (cheese > cost + cheeseReserve) {
        spawnRat(rc);
      }
    } else if (round % 50 == 0 && spawnCount < 20) {
      int cost = rc.getCurrentRatCost();
      if (cheese > cost + cheeseReserve) {
        spawnRat(rc);
      }
    }

    // TRAPS: Place 3 cat traps for defense (after spawning done)
    // Cat traps deal 100 damage to cats, help with cooperation score
    if (spawnCount >= 10 && trapCount < 3 && cheese > 300) {
      placeCatTrap(rc);
    }

    // ==================== KING MOVEMENT ====================
    // Cache sensing: scan once, check for both cats and enemy rats
    RobotInfo[] nearbyRobots = rc.senseNearbyRobots(25, null); // All robots within 5 tiles
    boolean hasCats = false;
    boolean hasEnemies = false;

    for (RobotInfo robot : nearbyRobots) {
      if (robot.getTeam() == Team.NEUTRAL) {
        hasCats = true;
      } else if (robot.getTeam() == rc.getTeam().opponent()) {
        hasEnemies = true;
      }
    }

    // STAY STILL if any threats nearby
    if (!hasCats && !hasEnemies) {
      MapLocation ahead = rc.adjacentLocation(rc.getDirection());

      // Clear obstacles
      if (rc.canRemoveDirt(ahead)) {
        rc.removeDirt(ahead);
      }

      // Move forward slowly (every other round)
      if (round % 2 == 0 && rc.canMoveForward()) {
        rc.moveForward();
      } else if (rc.canTurn()) {
        // Turn occasionally to explore
        if (round % 10 == 0) {
          Direction newDir = directions[(round / 10) % 8];
          rc.turn(newDir);
        }
      }
    }
  }

  private static void spawnRat(RobotController rc) throws GameActionException {
    // ==================== SPAWN LOCATION LOGIC ====================
    // Try all 8 directions at distance 2 from king center
    // King is 3x3 (occupies 9 tiles), so distance 2 is just outside footprint
    //
    // JAVA LEARNING: Enhanced for loop
    // "for (Direction dir : directions)" means: for each direction in the array
    // This iterates through all 8 directions (N, NE, E, SE, S, SW, W, NW)
    //
    // TUNABLE: Change spawn distance
    // .add(dir).add(dir) = distance 2
    // .add(dir).add(dir).add(dir) = distance 3 (further from king, less clustering)
    for (Direction dir : directions) {
      MapLocation loc = rc.getLocation().add(dir).add(dir);

      // JAVA LEARNING: API method call with conditional
      // rc.canBuildRat(loc) checks if spawn is valid (passable, in range, etc.)
      // Always check "can" before doing action to avoid exceptions
      if (rc.canBuildRat(loc)) {
        rc.buildRat(loc); // Spawn the rat at this location
        spawnCount++; // Increment counter (JAVA: ++ means add 1)
        return; // Exit function immediately after spawning one rat
      }
    }
    // If we reach here, all 8 directions were blocked (couldn't spawn)
  }

  private static void placeCatTrap(RobotController rc) throws GameActionException {
    // Place traps at distance 2 (BUILD_DISTANCE_SQUARED constraint)
    // Traps are HIDDEN from enemies - they don't know where they are
    for (Direction dir : directions) {
      MapLocation loc = rc.getLocation().add(dir).add(dir);
      if (rc.canPlaceCatTrap(loc)) {
        rc.placeCatTrap(loc);
        trapCount++;
        return;
      }
    }
  }

  // ================================================================
  // BABY RAT BEHAVIOR
  // ================================================================
  // Baby rats are either ATTACKERS (rush enemy) or COLLECTORS (feed king)
  // Role is determined by ID: even = attacker, odd = collector

  // ==================== ROLE ASSIGNMENT ====================
  // JAVA LEARNING: Static variable as instance state
  // Each baby rat has its own myRole value (persists across rounds)
  // -1 means unassigned, 0 = attacker, 1 = collector
  private static int myRole = -1;

  private static void runBabyRat(RobotController rc) throws GameActionException {
    // OPTIMIZATION: Cache all API calls at start
    int cheese = rc.getRawCheese();
    int round = rc.getRoundNum();
    MapLocation me = rc.getLocation();
    int id = rc.getID();

    // ==================== ROLE ASSIGNMENT ====================
    // Assign role once on first execution
    //
    // STRATEGY: 50/50 split between attackers and collectors
    // Even IDs (10000, 10002, 10004...) = attackers
    // Odd IDs (10001, 10003, 10005...) = collectors
    //
    // JAVA LEARNING: Ternary operator (condition ? trueValue : falseValue)
    // (id % 2 == 0) ? 0 : 1 means:
    //   - If id is even, assign 0 (attacker)
    //   - If id is odd, assign 1 (collector)
    //
    // TRY CHANGING:
    // - (id % 3 == 0) ? 0 : 1 for 33% attackers, 66% collectors
    // - (id % 3 < 2) ? 0 : 1 for 66% attackers, 33% collectors
    if (myRole == -1) {
      myRole = (id % 2 == 0) ? 0 : 1;
    }

    // VISUAL DEBUGGING: Show role above rat in client
    rc.setIndicatorString((myRole == 0 ? "ATK" : "COL"));

    // PHASE 4: Check if should form 2nd king
    if (round > SECOND_KING_FORMATION_ROUND
        && cheese > SECOND_KING_CHEESE_REQUIREMENT
        && spawnCount >= 15) {
      RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
      if (allies.length >= 6 && rc.canBecomeRatKing()) {
        rc.becomeRatKing();
        return;
      }
    }

    // Execute role behavior
    if (myRole == 0) {
      attackEnemyKing(rc);
    } else {
      // Collectors: deliver when carrying enough, otherwise collect
      if (cheese >= 10) {
        deliver(rc);
      } else {
        collect(rc);
      }
    }
  }

  // ================================================================
  // ATTACKER BEHAVIOR
  // ================================================================
  // Attackers rush toward enemy king and attack any enemy rats they encounter
  // Goal: Kill enemy rats to reduce their economy and trigger backstab mode

  private static int roundsSinceLastAttack = 0;

  // MAGIC NUMBERS: Extract to named constants for clarity
  private static final int ATTACKER_IDLE_SUICIDE_THRESHOLD = 100; // Rounds before suicide
  private static final int DELIVERY_TIMEOUT_THRESHOLD = 10; // Rounds stuck before giving up
  private static final int STUCK_RECOVERY_THRESHOLD = 2; // Rounds before unstuck
  private static final int CHEESE_ENHANCED_THRESHOLD = 500; // Global cheese for enhanced attacks
  private static final int SECOND_KING_FORMATION_ROUND = 75; // When to consider forming 2nd king
  private static final int SECOND_KING_CHEESE_REQUIREMENT = 1000; // Cheese needed for 2nd king

  // Cache shared array reads (reused multiple times)
  private static MapLocation cachedOurKing = null;
  private static MapLocation cachedEnemyKing = null;
  private static int lastCacheRound = -1;

  private static void attackEnemyKing(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    MapLocation me = rc.getLocation();

    // PHASE 2: Skip if not ready (cleaner than cooldown check)
    if (!rc.isActionReady()) {
      return;
    }

    // BYTECODE MONITORING: Check budget before expensive operations
    int bytecodeLeft = Clock.getBytecodesLeft();

    // Cache shared array reads
    if (lastCacheRound != round) {
      cachedOurKing = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
      cachedEnemyKing = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
      lastCacheRound = round;
    }

    // Suicide if idle too long
    roundsSinceLastAttack++;
    if (roundsSinceLastAttack > ATTACKER_IDLE_SUICIDE_THRESHOLD) {
      rc.disintegrate();
      return;
    }

    // SCAN FOR ENEMIES (single pass)
    RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
    RobotInfo carrying = rc.getCarrying();

    // SINGLE LOOP: Process enemies with smart prioritization
    RobotInfo woundedEnemy = null;
    RobotInfo babyRat = null;
    RobotInfo enemyKing = null;
    int mostCheese = 0;

    for (RobotInfo enemy : enemies) {
      if (enemy.getType() == UnitType.BABY_RAT) {
        // Ratnap wounded enemies
        if (enemy.getHealth() < 50 && woundedEnemy == null) {
          woundedEnemy = enemy;
        }

        // TARGET PRIORITIZATION: Attack collectors carrying cheese!
        // Disrupt enemy economy by killing collectors with cargo
        int enemyCheese = enemy.getRawCheeseAmount();
        if (enemyCheese > mostCheese || babyRat == null) {
          mostCheese = enemyCheese;
          babyRat = enemy; // Prefer collectors over empty attackers
        }
      } else if (enemy.getType() == UnitType.RAT_KING && enemyKing == null) {
        enemyKing = enemy;
      }
    }

    // ==================== RATNAPPING ====================
    if (carrying == null && woundedEnemy != null) {
      if (rc.canCarryRat(woundedEnemy.getLocation())) {
        rc.carryRat(woundedEnemy.getLocation());
        return;
      }
    }

    // Throw carried enemy (reuse cached king position)
    if (carrying != null && rc.canThrowRat()) {
      Direction toKing = me.directionTo(cachedOurKing);

      if (rc.getDirection() != toKing && rc.canTurn()) {
        rc.turn(toKing);
        return;
      }

      rc.throwRat();
      return;
    }

    // ==================== ATTACK BABY RATS ====================
    if (babyRat != null) {
      MapLocation enemyLoc = babyRat.getLocation();
      if (rc.canAttack(enemyLoc)) {
        // OPTIMIZATION: Cache globalCheese, remove duplicate canAttack check
        int globalCheese = rc.getGlobalCheese();
        if (globalCheese > CHEESE_ENHANCED_THRESHOLD) {
          rc.attack(enemyLoc, 8); // 13 damage (assumes we have 8 cheese)
        } else {
          rc.attack(enemyLoc); // 10 damage
        }
        roundsSinceLastAttack = 0;
        return;
      }
    }

    // ==================== ATTACK KING ====================
    if (enemyKing != null) {
      MapLocation kingLoc = enemyKing.getLocation();
      int dist = (int) me.bottomLeftDistanceSquaredTo(kingLoc);

      // Squeak king location
      try {
        int squeak = encodeSqueak(SqueakType.ENEMY_RAT_KING, kingLoc.x, kingLoc.y, 0);
        rc.squeak(squeak);
      } catch (Exception e) {
        // Squeak failed
      }

      // CRITICAL FIX: Attack ALL king tiles!
      // King is 3x3 - try all 9 tiles to find adjacent one
      for (int dx = -1; dx <= 1; dx++) {
        for (int dy = -1; dy <= 1; dy++) {
          MapLocation tile = new MapLocation(kingLoc.x + dx, kingLoc.y + dy);
          if (rc.canAttack(tile)) {
            // Check game mode for strategy
            if (rc.isCooperation()) {
              // Cooperation: conserve cheese
              rc.attack(tile);
            } else {
              // Backstab: use enhanced for points
              int globalCheese = rc.getGlobalCheese();
              if (globalCheese > CHEESE_ENHANCED_THRESHOLD) {
                rc.attack(tile, 8);
              } else {
                rc.attack(tile);
              }
            }
            roundsSinceLastAttack = 0;
            return;
          }
        }
      }

      // Move toward king
      if (dist <= 10) {
        Direction toKing = me.directionTo(kingLoc);
        if (rc.getDirection() != toKing && rc.canTurn()) {
          rc.turn(toKing);
          return;
        }
        if (rc.canMoveForward()) {
          rc.moveForward();
          return;
        }
      }

      simpleMove(rc, kingLoc);
      return;
    }

    // PHASE 2: Read squeaks with metadata filtering
    if (bytecodeLeft > 1000) {
      try {
        Message[] squeaks = rc.readSqueaks(-1);
        Message freshestKingSqueak = null;
        int newestRound = 0;

        for (Message msg : squeaks) {
          // Skip our own squeaks (no point processing)
          if (msg.getSenderID() == id) continue;

          // Prioritize fresh messages (newer = more accurate)
          if (msg.getRound() > newestRound) {
            int rawSqueak = msg.getBytes();
            if (getSqueakType(rawSqueak) == SqueakType.ENEMY_RAT_KING) {
              freshestKingSqueak = msg;
              newestRound = msg.getRound();
            }
          }
        }

        // Use freshest squeak
        if (freshestKingSqueak != null) {
          // Move toward squeak SOURCE (where ally saw king, likely still combat zone)
          MapLocation combatZone = freshestKingSqueak.getSource();
          simpleMove(rc, combatZone);
          return;
        }
      } catch (Exception e) {
        // Squeak reading failed
      }
    }

    // NO ENEMIES VISIBLE: SEARCH FOR KING (don't all cluster at same spot!)
    // Spread out search pattern - each rat searches different area
    MapLocation enemyKingPos = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));

    // Expanding spiral search: different direction per rat
    int searchDir = (id + round / 10) % 8;
    Direction searchDirection = directions[searchDir];
    MapLocation searchTarget =
        enemyKingPos.add(searchDirection).add(searchDirection).add(searchDirection);

    simpleMove(rc, searchTarget);
  }

  // ================================================================
  // COLLECTOR BEHAVIOR
  // ================================================================
  // Collectors find cheese and deliver it to our king
  // King consumes 3 cheese/round - without deliveries, king starves and dies

  private static void collect(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();

    // VISUAL DEBUG: Show collector status
    rc.setIndicatorString("COL cheese:" + rc.getRawCheese());

    // CLEAR OBSTACLES: Remove enemy traps and dirt blocking path
    Direction facing = rc.getDirection();
    MapLocation ahead = rc.adjacentLocation(facing);

    if (rc.canRemoveRatTrap(ahead)) {
      rc.removeRatTrap(ahead); // Clear enemy rat trap (50 damage saved!)
      return;
    }
    if (rc.canRemoveCatTrap(ahead)) {
      rc.removeCatTrap(ahead); // Clear enemy cat trap
      return;
    }
    if (rc.canRemoveDirt(ahead)) {
      rc.removeDirt(ahead); // Clear dirt wall
      return;
    }

    // FIND NEAREST CHEESE
    // Scan vision radius (20 tiles squared ≈ 4.5 tiles)
    MapLocation[] nearby = rc.getAllLocationsWithinRadiusSquared(me, 20);
    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    for (MapLocation loc : nearby) {
      if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);

        // PHASE 3: Squeak cheese mine location
        if (info.hasCheeseMine()) {
          try {
            int squeak = encodeSqueak(SqueakType.CHEESE_MINE, loc.x, loc.y, 0);
            rc.squeak(squeak);
          } catch (Exception e) {
            // Squeak failed, continue
          }
        }

        if (info.getCheeseAmount() > 0) {
          int dist = me.distanceSquaredTo(loc);
          if (dist < nearestDist) {
            nearestDist = dist;
            nearest = loc;
          }
        }
      }
    }

    // COLLECT CHEESE
    if (nearest != null) {
      if (rc.canPickUpCheese(nearest)) {
        rc.pickUpCheese(nearest);
      } else {
        simpleMove(rc, nearest);
      }
    } else {
      // No cheese visible - explore toward map center
      MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
      simpleMove(rc, center);
    }
  }

  private static int deliveryStuckCount = 0;
  private static MapLocation lastDeliveryTarget = null;

  private static void deliver(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();

    // OPTIMIZATION: Reuse cached king position
    if (cachedOurKing == null) {
      cachedOurKing = new MapLocation(rc.readSharedArray(0), rc.readSharedArray(1));
    }

    int dist = me.distanceSquaredTo(cachedOurKing);

    // TRACK DELIVERY PROGRESS
    if (cachedOurKing.equals(lastDeliveryTarget)) {
      deliveryStuckCount++;
    } else {
      deliveryStuckCount = 0;
      lastDeliveryTarget = cachedOurKing;
    }

    // TRANSFER CHEESE
    if (dist <= 9 && rc.canTransferCheese(cachedOurKing, rc.getRawCheese())) {
      rc.transferCheese(cachedOurKing, rc.getRawCheese());
      deliveryStuckCount = 0;
      return;
    }

    // DELIVERY TIMEOUT
    if (deliveryStuckCount >= DELIVERY_TIMEOUT_THRESHOLD && dist > 50) {
      deliveryStuckCount = 0;
      collect(rc);
      return;
    }

    // NAVIGATE toward king
    simpleMove(rc, cachedOurKing);
  }

  // ================================================================
  // MOVEMENT SYSTEM
  // ================================================================
  // Simple greedy movement with stuck recovery
  // Uses turn+moveForward (10 cd) instead of move(Direction) (18 cd strafe penalty)

  private static MapLocation lastPosition = null;
  private static int stuckRounds = 0;

  private static void simpleMove(RobotController rc, MapLocation target)
      throws GameActionException {
    // PHASE 2: Skip if not ready
    if (!rc.isMovementReady()) {
      return;
    }

    MapLocation me = rc.getLocation();
    Direction desired = me.directionTo(target);
    Direction facing = rc.getDirection();
    int round = rc.getRoundNum();
    int id = rc.getID();

    // STUCK DETECTION
    if (me.equals(lastPosition)) {
      stuckRounds++;
    } else {
      stuckRounds = 0;
      lastPosition = me;
    }

    // ==================== STUCK RECOVERY ====================
    // After 2 rounds in same position, escape aggressively
    //
    // STRATEGY: If not making progress, try different approach
    // Perpendicular directions often less congested than straight ahead
    //
    // TUNABLE:
    // - stuckRounds >= 2: How patient to be (1 = aggressive, 3 = patient)
    if (stuckRounds >= STUCK_RECOVERY_THRESHOLD) {
      // JAVA LEARNING: Array initialization
      // Create array of directions to try in specific order
      // Try perpendicular first (likely less congested)
      Direction[] escapeOrder = {
        rotateLeft(desired),
        rotateRight(desired),
        rotateLeft(rotateLeft(desired)),
        rotateRight(rotateRight(desired)),
        directions[0],
        directions[1],
        directions[2],
        directions[3],
        directions[4],
        directions[5],
        directions[6],
        directions[7]
      };

      for (Direction d : escapeOrder) {
        if (rc.canMove(d)) {
          if (rc.getDirection() == d && rc.canMoveForward()) {
            rc.moveForward();
            return;
          } else if (rc.canTurn()) {
            rc.turn(d);
            return;
          }
        }
      }
      return; // Completely blocked, wait for next round
    }

    // ==================== TRAFFIC MANAGEMENT ====================
    // If surrounded by 4+ friendlies, take turns moving
    // This prevents all rats pushing into same space
    //
    // STRATEGY: Round-robin turn-taking to reduce congestion
    // Each rat gets 1 out of every 10 rounds to move when crowded
    //
    // TUNABLE:
    // - friendlies.length >= 4: Crowding threshold (3 = more yielding, 5 = less)
    // - round % 10: Slot count (% 5 = faster turns, % 20 = slower)
    //
    // JAVA LEARNING: .length property
    // Arrays in Java have .length property (not a method, no parentheses)
    RobotInfo[] friendlies = rc.senseNearbyRobots(2, rc.getTeam());
    if (friendlies.length >= 4) {
      int yieldSlot = round % 10; // Which slot is active this round (0-9)
      int mySlot = id % 10; // Which slot this rat owns (based on ID)
      if (mySlot != yieldSlot) {
        return; // Not my turn, wait
      }
    }

    // GREEDY MOVEMENT
    if (rc.canMove(desired)) {
      // PHASE 3: Check for traps (only if enough bytecode)
      if (Clock.getBytecodesLeft() > 500) {
        MapLocation nextLoc = me.add(desired);
        if (rc.canSenseLocation(nextLoc)) {
          MapInfo nextInfo = rc.senseMapInfo(nextLoc);
          if (nextInfo.getTrap() != TrapType.NONE) {
            // Enemy trap! Try perpendicular
            Direction[] avoidTrap = {rotateLeft(desired), rotateRight(desired)};
            for (Direction alt : avoidTrap) {
              if (rc.canMove(alt)) {
                rc.move(alt);
                return;
              }
            }
          }
        }
      }

      rc.move(desired);
      return;
    }

    // OBSTACLE HANDLING
    if (facing == desired) {
      MapLocation nextLoc = me.add(desired);

      if (rc.canSenseLocation(nextLoc)) {
        MapInfo info = rc.senseMapInfo(nextLoc);

        // If wall ahead, try perpendicular (go around)
        if (!info.isPassable()) {
          Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
          for (Direction p : perp) {
            if (rc.canMove(p) && rc.canTurn()) {
              rc.turn(p);
              return;
            }
          }
        }

        // If friendly rat ahead, try going around
        RobotInfo blocker = rc.senseRobotAtLocation(nextLoc);
        if (blocker != null && blocker.getTeam() == rc.getTeam()) {
          Direction[] perp = {rotateLeft(desired), rotateRight(desired)};
          for (Direction p : perp) {
            if (rc.canMove(p) && rc.canTurn()) {
              rc.turn(p);
              return;
            }
          }
        }
      }
    }

    // EMERGENCY UNSTUCK: Try ANY direction to keep moving
    for (Direction dir : directions) {
      if (rc.canMove(dir)) {
        rc.move(dir);
        return;
      }
    }

    // Can't move - try turning at least
    if (rc.canTurn() && facing != desired) {
      rc.turn(desired);
    }
  }

  // Use Direction built-in methods (saves 90 bytecode per call vs our loop)
  private static Direction rotateLeft(Direction d) {
    return d.rotateLeft();
  }

  private static Direction rotateRight(Direction d) {
    return d.rotateRight();
  }

  private static final Direction[] directions = Direction.allDirections();

  // ================================================================
  // SQUEAK COMMUNICATION (Phase 1: Infrastructure)
  // ================================================================
  // Squeaks are 32-bit messages sent to nearby rats (16 tile radius)
  // Format: [4 bits type][12 bits Y][12 bits X][4 bits extra]

  private enum SqueakType {
    INVALID, // 0
    ENEMY_RAT_KING, // 1 - Enemy king location
    ENEMY_BABY_RAT, // 2 - Enemy rat location
    CHEESE_MINE, // 3 - Cheese mine location
    DANGER, // 4 - Under attack
    HELP_KING // 5 - King being attacked
  }

  // Encode squeak message
  private static int encodeSqueak(SqueakType type, int x, int y, int extra) {
    return (type.ordinal() << 28) | (y << 16) | (x << 4) | (extra & 0xF);
  }

  // Decode squeak type
  private static SqueakType getSqueakType(int squeak) {
    int typeOrdinal = (squeak >> 28) & 0xF;
    if (typeOrdinal < SqueakType.values().length) {
      return SqueakType.values()[typeOrdinal];
    }
    return SqueakType.INVALID;
  }

  // Decode squeak location
  private static MapLocation getSqueakLocation(int squeak) {
    int x = (squeak >> 4) & 0xFFF;
    int y = (squeak >> 16) & 0xFFF;
    return new MapLocation(x, y);
  }

  // Decode squeak extra data
  private static int getSqueakExtra(int squeak) {
    return squeak & 0xF;
  }
}
