package ratbot2;

import battlecode.common.*;

/**
 * RobotPlayer - Entry point for ratbot2
 *
 * <p>Role Assignment: - 70% combat rats (IDs % 10 in 0-6) - Attack cats - 30% economy rats (IDs %
 * 10 in 7-9) - Collect cheese
 */
public class RobotPlayer {
  private static int myRole = -1; // 0=combat, 1=economy

  public static void run(RobotController rc) throws GameActionException {
    // Assign role at spawn (based on ID)
    if (myRole == -1) {
      // 50% combat (defend king), 50% economy (collect cheese)
      myRole = (rc.getID() % 2 == 0) ? 0 : 1;

      String roleStr = (myRole == 0) ? "COMBAT" : "ECONOMY";
      System.out.println("SPAWN:" + rc.getRoundNum() + ":" + rc.getID() + ":role=" + roleStr);
    }

    while (true) {
      try {
        if (rc.getType() == UnitType.RAT_KING) {
          RatKing.run(rc);
        } else if (myRole == 0) {
          // Combat role: DISTRACT cats (keep them away from king)
          CombatRat.run(rc);
        } else {
          // Economy role: Collect cheese (flee from cats)
          EconomyRat.run(rc);
        }
      } catch (GameActionException e) {
        System.out.println("GameActionException");
        e.printStackTrace();
      } catch (Exception e) {
        System.out.println("Exception");
        e.printStackTrace();
      } finally {
        Clock.yield();
      }
    }
  }
}
