package ratbot;

import battlecode.common.*;
import ratbot.algorithms.*;
import ratbot.logging.*;

/**
 * Baby Rat behavior for Battlecode 2026.
 *
 * <p>Primary responsibilities: 1. Collect cheese from mines 2. Transfer cheese to rat kings 3.
 * Avoid cats 4. Basic combat 5. Strategic backstab decisions
 *
 * <p>State machine: EXPLORE → COLLECT → DELIVER → repeat
 */
public class BabyRat {

  // States
  private static enum State {
    EXPLORE,
    COLLECT,
    DELIVER,
    ATTACK_CAT
  }

  private static State currentState = State.EXPLORE;

  // Cheese collection targets
  private static MapLocation targetMine = null;
  private static MapLocation targetKing = null;
  private static int cheeseThreshold = BehaviorConfig.CHEESE_DELIVERY_THRESHOLD;

  // Strategic decision tracking
  private static boolean isBackstabbing = false;
  private static int lastBackstabCheck = 0;

  // Emergency mode tracking
  private static boolean isEmergencyMode = false;

  /** Main behavior loop for baby rats. */
  public static void run(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();
    int id = rc.getID();
    MapLocation me = rc.getLocation();

    // Check for emergency mode FIRST (highest priority)
    State oldState = currentState;
    checkEmergencyMode(rc);

    // Check backstab decision periodically (low bytecode cost)
    checkBackstabDecision(rc);

    // Update state
    updateState(rc);

    // Debug state transitions
    if (DebugConfig.STATE_LOGGING && oldState != currentState) {
      Debug.debugStateChange(rc, oldState.toString(), currentState.toString(), "updateState");
    }

    // Visual: Show current state
    if (DebugConfig.VISUAL_INDICATORS && round % DebugConfig.INDICATOR_INTERVAL == 0) {
      Debug.status(rc, currentState + " cheese=" + rc.getRawCheese());
    }

    // Execute behavior based on state
    switch (currentState) {
      case EXPLORE:
        explore(rc);
        break;
      case COLLECT:
        collect(rc);
        break;
      case DELIVER:
        deliver(rc);
        break;
      case ATTACK_CAT:
        attackCat(rc);
        break;
    }

    // Log state every 20 rounds
    if (round % 20 == 0) {
      Logger.logState(
          round,
          "BABY_RAT",
          id,
          me.x,
          me.y,
          rc.getDirection().toString(),
          rc.getHealth(),
          rc.getRawCheese(),
          currentState.toString());
    }
  }

  /** Update state machine based on current conditions. */
  private static void updateState(RobotController rc) throws GameActionException {
    // Check for cats first - ATTACK them (primary objective: 50% of cooperation score)
    if (RobotUtil.detectCat(rc, 20)) {
      currentState = State.ATTACK_CAT;
      return;
    }

    // Cheese collection logic
    int rawCheese = rc.getRawCheese();

    if (rawCheese >= cheeseThreshold) {
      currentState = State.DELIVER;
    } else if (rawCheese > 0) {
      currentState = State.COLLECT; // Keep collecting
    } else {
      currentState = State.EXPLORE; // Find cheese
    }
  }

  /** EXPLORE: Find cheese or cheese mines. */
  private static void explore(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();

    // Look for cheese in vision
    MapLocation[] nearbyLocs = rc.getAllLocationsWithinRadiusSquared(me, 20);
    MapLocation nearestCheese = null;
    int nearestDist = Integer.MAX_VALUE;

    for (MapLocation loc : nearbyLocs) {
      if (rc.canSenseLocation(loc)) {
        MapInfo info = rc.senseMapInfo(loc);
        if (info.getCheeseAmount() > 0) {
          int dist = me.distanceSquaredTo(loc);
          if (dist < nearestDist) {
            nearestDist = dist;
            nearestCheese = loc;
          }
        }
      }
    }

    if (nearestCheese != null) {
      // Try to pick up if on cheese
      if (rc.canPickUpCheese(nearestCheese)) {
        rc.pickUpCheese(nearestCheese);

        if (DebugConfig.DEBUG_CHEESE) {
          Debug.info(rc, "Picked up cheese at " + nearestCheese);
        }

        Logger.logCheeseCollect(
            rc.getRoundNum(),
            rc.getID(),
            me.x,
            me.y,
            5,
            rc.getRawCheese(),
            nearestCheese.x,
            nearestCheese.y);
      } else {
        // Move toward nearest cheese
        moveToward(rc, nearestCheese);

        if (DebugConfig.DEBUG_CHEESE && DebugConfig.VISUAL_INDICATORS) {
          Debug.markTarget(rc, nearestCheese, "CHEESE");
        }
      }
    } else {
      // No cheese visible - move toward map center to explore
      MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
      moveToward(rc, center);
    }
  }

  /** COLLECT: Move to cheese and pick it up. */
  private static void collect(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();

    // Simplified: look for cheese using canPickUpCheese
    MapLocation[] nearbyLocs = rc.getAllLocationsWithinRadiusSquared(me, 20);
    MapLocation nearestCheese = null;
    int nearestDist = Integer.MAX_VALUE;

    for (MapLocation loc : nearbyLocs) {
      if (rc.canPickUpCheese(loc)) {
        int dist = me.distanceSquaredTo(loc);
        if (dist < nearestDist) {
          nearestDist = dist;
          nearestCheese = loc;
        }
      }
    }

    // Found cheese
    if (nearestCheese != null) {
      // Can we pick it up?
      if (rc.canPickUpCheese(nearestCheese)) {
        rc.pickUpCheese(nearestCheese);

        Logger.logCheeseCollect(
            rc.getRoundNum(),
            rc.getID(),
            me.x,
            me.y,
            5, // amount (constant from specs)
            rc.getRawCheese(),
            nearestCheese.x,
            nearestCheese.y);

        // Keep collecting if below threshold
        if (rc.getRawCheese() < cheeseThreshold) {
          currentState = State.COLLECT;
        } else {
          currentState = State.DELIVER;
        }
      } else {
        // Move toward cheese
        moveToward(rc, nearestCheese);
      }
    } else {
      // No cheese visible, go back to exploring
      currentState = State.EXPLORE;
    }
  }

  /** DELIVER: Return cheese to rat king. */
  private static void deliver(RobotController rc) throws GameActionException {
    // Get king location from shared array (broadcast by king)
    int kingX = rc.readSharedArray(1);
    int kingY = rc.readSharedArray(2);

    // Validate coordinates
    if (kingX == 0 && kingY == 0) {
      // King hasn't broadcast position yet - try to find via vision
      RobotInfo nearestKing = RobotUtil.findNearestAllyKing(rc);
      if (nearestKing == null) {
        Debug.warning(rc, "No king position available");
        currentState = State.EXPLORE;
        return;
      }
      kingX = nearestKing.getLocation().x;
      kingY = nearestKing.getLocation().y;
    }

    MapLocation kingLoc = new MapLocation(kingX, kingY);
    int distance = rc.getLocation().distanceSquaredTo(kingLoc);

    // Debug delivery attempt
    if (DebugConfig.DEBUG_CHEESE && rc.getRoundNum() % 10 == 0) {
      Debug.info(
          rc,
          "DELIVER: King at " + kingLoc + " dist²=" + distance + " cheese=" + rc.getRawCheese());
    }

    // Visual: Show delivery target
    if (DebugConfig.DEBUG_CHEESE && DebugConfig.VISUAL_INDICATORS) {
      Debug.markTarget(rc, kingLoc, "DELIVER");
    }

    // Can we transfer?
    if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
      int amount = rc.getRawCheese();
      rc.transferCheese(kingLoc, amount);

      Logger.logCheeseTransfer(
          rc.getRoundNum(), rc.getID(), amount, kingLoc.x, kingLoc.y, rc.getGlobalCheese());

      if (DebugConfig.DEBUG_CHEESE) {
        Debug.info(rc, "Transferred " + amount + " cheese to king");
      }

      // Done delivering, back to exploring
      currentState = State.EXPLORE;
    } else {
      // Move toward king
      moveToward(rc, kingLoc);

      if (DebugConfig.DEBUG_CHEESE && rc.getRoundNum() % 10 == 0) {
        Debug.info(rc, "Moving toward king at " + kingLoc + " (dist²=" + distance + ")");
      }
    }
  }

  /**
   * ATTACK_CAT: Primary objective in cooperation mode (50% of score). Swarm attack cats to deal
   * damage. Uses shared array cat tracking when cat not in vision.
   */
  private static void attackCat(RobotController rc) throws GameActionException {
    // Try to find cat in vision first (most accurate)
    RobotInfo nearestCat = RobotUtil.findNearestCat(rc);

    if (nearestCat != null) {
      // Cat in vision - attack directly
      MapLocation catLoc = nearestCat.getLocation();
      int distance = rc.getLocation().distanceSquaredTo(catLoc);

      // EMERGENCY: Check if cat threatening king
      int kingX = rc.readSharedArray(1);
      int kingY = rc.readSharedArray(2);
      MapLocation kingLoc = new MapLocation(kingX, kingY);
      int catToKingDist = catLoc.distanceSquaredTo(kingLoc);

      // If cat within 5 tiles of king AND we're close to cat - THROW
      if (catToKingDist <= 25 && distance <= 9) {
        if (emergencyThrowAtCat(rc, catLoc)) {
          return; // Threw, done
        }
      }

      // Visual: Show attack target
      if (DebugConfig.VISUAL_INDICATORS) {
        Debug.markTarget(rc, catLoc, "ATTACK_CAT");
      }

      // Check if in vision cone (required for attack)
      boolean inVision =
          Vision.canSee(rc.getLocation(), rc.getDirection(), catLoc, UnitType.BABY_RAT);

      if (DebugConfig.DEBUG_COMBAT && rc.getRoundNum() % 10 == 0) {
        Debug.verbose(
            rc,
            "Cat check: dist²="
                + distance
                + " inVision="
                + inVision
                + " canAttack="
                + rc.canAttack(catLoc));
      }

      // Can we attack? (adjacent + in vision cone + action ready)
      if (distance <= 2 && inVision && rc.canAttack(catLoc)) {
        rc.attack(catLoc);

        Logger.logCombat(
            rc.getRoundNum(),
            "BABY_RAT",
            rc.getID(),
            rc.getLocation().x,
            rc.getLocation().y,
            catLoc.x,
            catLoc.y,
            10, // base damage
            0, // no cheese spent
            nearestCat.getHealth() - 10);

        if (DebugConfig.DEBUG_COMBAT) {
          Debug.info(rc, "ATTACKED cat! HP now " + (nearestCat.getHealth() - 10));
        }
        return;
      }

      // Need to face cat first or get closer
      if (!inVision || distance > 2) {
        moveToward(rc, catLoc); // Will turn to face + move closer
        return;
      }

      // Adjacent and in vision but can't attack - debug why
      if (DebugConfig.DEBUG_COMBAT) {
        Debug.warning(
            rc,
            "Can't attack cat! dist="
                + distance
                + " inVision="
                + inVision
                + " actionReady="
                + rc.isActionReady());
      }
      moveToward(rc, catLoc);
      return;
    }

    // Can't see cat in vision - check shared array for tracked positions
    MapLocation trackedCat = getTrackedCatPosition(rc);

    if (trackedCat == null) {
      // No cats tracked anywhere - switch to EXPLORE
      currentState = State.EXPLORE;
      return;
    }

    // Navigate to tracked cat position
    moveToward(rc, trackedCat);

    if (DebugConfig.VISUAL_INDICATORS) {
      Debug.markTarget(rc, trackedCat, "TRACKED_CAT");
    }
  }

  /**
   * Emergency defense: throw rat at cat to stun it. Sacrifices one rat to buy 2 turns for king
   * survival.
   */
  private static boolean emergencyThrowAtCat(RobotController rc, MapLocation catLoc)
      throws GameActionException {
    // Find adjacent ally to throw
    RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());

    for (int i = allies.length; --i >= 0; ) {
      if (allies[i].getType() != UnitType.BABY_RAT) continue;

      MapLocation ratLoc = allies[i].getLocation();

      if (rc.canCarryRat(ratLoc)) {
        rc.carryRat(ratLoc);

        // Face cat
        Direction toCat = rc.getLocation().directionTo(catLoc);
        if (rc.getDirection() != toCat && rc.canTurn()) {
          rc.turn(toCat);
          return false; // Throw next turn
        }

        if (rc.canThrowRat()) {
          rc.throwRat();
          System.out.println(
              "THROW:" + rc.getRoundNum() + ":" + rc.getID() + ":Threw rat at cat " + catLoc);
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Get nearest tracked cat position from shared array. Kings write cat positions for baby rats to
   * use.
   *
   * @return Nearest tracked cat location, or null if none tracked
   */
  private static MapLocation getTrackedCatPosition(RobotController rc) throws GameActionException {
    MapLocation me = rc.getLocation();
    MapLocation nearest = null;
    int nearestDist = Integer.MAX_VALUE;

    // Check all 4 cat tracking slots
    for (int i = 0; i < 4; i++) {
      int slotX = 3 + (i * 2);
      int slotY = 4 + (i * 2);

      int catX = rc.readSharedArray(slotX);
      int catY = rc.readSharedArray(slotY);

      if (catX == 0) continue; // No cat in this slot

      MapLocation catLoc = new MapLocation(catX, catY);
      int dist = me.distanceSquaredTo(catLoc);

      if (dist < nearestDist) {
        nearestDist = dist;
        nearest = catLoc;
      }
    }

    return nearest; // null if no cats tracked
  }

  /**
   * Movement toward target with obstacle avoidance. Tries direct path first, then alternatives if
   * blocked.
   */
  private static void moveToward(RobotController rc, MapLocation target)
      throws GameActionException {
    MapLocation me = rc.getLocation();
    Direction toTarget = me.directionTo(target);
    Direction currentFacing = rc.getDirection();

    // Already facing target - try to move
    if (currentFacing == toTarget) {
      if (rc.canMoveForward()) {
        rc.moveForward();
        return;
      }
      // Blocked - try alternatives
    }

    // Need to turn OR blocked - try best available direction
    Direction[] alternatives = DirectionUtil.orderedDirections(toTarget);

    for (Direction dir : alternatives) {
      // If we can move in this direction
      if (dir == currentFacing && rc.canMoveForward()) {
        rc.moveForward();
        return;
      } else if (rc.canMove(dir)) {
        // Turn toward this direction
        if (rc.canTurn()) {
          rc.turn(dir);
          return;
        }
      }
    }

    // Completely stuck - debug
    if (rc.getRoundNum() % 50 == 0) {
      Debug.warning(rc, "STUCK: Can't move any direction from " + me);
    }
  }

  /**
   * Check for emergency mode and override behavior if needed. Emergency mode triggered by kings
   * when cheese critically low.
   */
  private static void checkEmergencyMode(RobotController rc) throws GameActionException {
    int cheeseStatus = rc.readSharedArray(BehaviorConfig.SLOT_CHEESE_STATUS);

    if (cheeseStatus == BehaviorConfig.EMERGENCY_CRITICAL) {
      // CRITICAL EMERGENCY: All rats must prioritize cheese delivery
      if (!isEmergencyMode) {
        isEmergencyMode = true;
        System.out.println(
            "EMERGENCY:" + rc.getRoundNum() + ":RAT_EMERGENCY_MODE:id=" + rc.getID());

        // Visual emergency
        if (DebugConfig.DEBUG_EMERGENCY) {
          Debug.debugEmergency(rc, "RAT_RESPONSE", cheeseStatus);
        }
      }

      // Override state machine - force cheese operations
      if (rc.getRawCheese() > 0) {
        currentState = State.DELIVER; // Deliver NOW
      } else {
        currentState = State.COLLECT; // Collect NOW
      }

      // Lower delivery threshold for more frequent deliveries
      cheeseThreshold = BehaviorConfig.EMERGENCY_DELIVERY_THRESHOLD;
    } else if (cheeseStatus > 0 && cheeseStatus < 200) {
      // WARNING: Low cheese - increase delivery frequency
      if (!isEmergencyMode) {
        System.out.println(
            "WARNING:" + rc.getRoundNum() + ":LOW_CHEESE_MODE:rounds=" + cheeseStatus);
      }
      isEmergencyMode = false;
      cheeseThreshold = BehaviorConfig.WARNING_DELIVERY_THRESHOLD;
    } else {
      // Normal operations
      isEmergencyMode = false;
      cheeseThreshold = BehaviorConfig.CHEESE_DELIVERY_THRESHOLD;
    }
  }

  /**
   * Check if we should backstab (call periodically to save bytecode). Evaluates game state and
   * switches to backstab mode if advantageous.
   */
  private static void checkBackstabDecision(RobotController rc) throws GameActionException {
    int round = rc.getRoundNum();

    // Only check every N rounds to save bytecode
    if (round - lastBackstabCheck < BehaviorConfig.BACKSTAB_CHECK_INTERVAL) return;
    lastBackstabCheck = round;

    // Too early in game
    if (round < BehaviorConfig.BACKSTAB_EARLIEST_ROUND) return;

    // TODO: Implement when shared array communication protocol is defined
    // For now, placeholder implementation with estimated values
    /*
    // Read game state from shared array
    int ourCatDamage = rc.readSharedArray(29);
    int enemyCatDamage = rc.readSharedArray(30);
    int ourKings = RobotUtil.countAllyKings(rc);
    int enemyKings = RobotUtil.countEnemyKings(rc);
    int ourCheese = rc.readSharedArray(31);
    int enemyCheese = rc.readSharedArray(32);

    // Evaluate backstab
    GameTheory.GameState state = new GameTheory.GameState(
        round, ourCatDamage, enemyCatDamage,
        ourKings, enemyKings, ourCheese, enemyCheese
    );

    GameTheory.BackstabRecommendation rec = GameTheory.evaluate(state);

    if (rec.shouldBackstab && !isBackstabbing) {
        isBackstabbing = true;
        Logger.logBackstab(round, ourCatDamage, enemyCatDamage,
                          ourKings, enemyKings, "INITIATED:" + rec.reasoning);
    }
    */
  }
}
