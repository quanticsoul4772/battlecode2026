# ratbot3 - Simple Design (Lessons Learned)

## Core Principle: **KEEP IT FUCKING SIMPLE**

### Mistakes Made (ratbot1 & ratbot2):
1. ❌ Added zone territories → complexity
2. ❌ Added delivery queues → broke deliveries
3. ❌ Added collision avoidance → didn't help
4. ❌ Added approach vectors → more complexity
5. ❌ Over-engineered movement → rats can't reach king

**Result**: Bot doesn't work, king starves, we lose.

## ratbot3 Requirements (SIMPLE ONLY)

### King Behavior:
1. **Spawn 10-15 rats** in first 50 rounds
2. **Place 3-5 traps** for defense (rounds 20-40)
3. **That's it** - no tracking, no coordination, nothing fancy

### Baby Rat Behavior:
1. **If carrying ≥10 cheese** → go to king and deliver
2. **Otherwise** → find nearest cheese and collect it
3. **That's it** - no zones, no roles, no states

### Movement:
1. **Face target** (turn if needed)
2. **Move forward** (if can move)
3. **If blocked** → try any other direction
4. **That's it** - no Bug2, no BFS, no collision avoidance

## Implementation (Single File, ~200 Lines Max)

```java
package ratbot3;

import battlecode.common.*;

public class RobotPlayer {
    public static void run(RobotController rc) {
        while (true) {
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    king(rc);
                } else {
                    rat(rc);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    // KING: Spawn rats, place traps
    static int spawned = 0, traps = 0;
    static void king(RobotController rc) throws GameActionException {
        int r = rc.getRoundNum();
        int c = rc.getGlobalCheese();

        // Spawn rats early
        if (r <= 50 && spawned < 12 && c > rc.getCurrentRatCost() + 50) {
            for (Direction d : dirs) {
                MapLocation loc = rc.getLocation().add(d).add(d).add(d).add(d);
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    spawned++;
                    return;
                }
            }
        }

        // Place traps
        if (r >= 20 && r <= 40 && traps < 5 && c > 50) {
            for (Direction d : dirs) {
                MapLocation loc = rc.getLocation().add(d).add(d);
                if (rc.canPlaceCatTrap(loc)) {
                    rc.placeCatTrap(loc);
                    traps++;
                    return;
                }
            }
        }
    }

    // RAT: Collect cheese, deliver to king
    static void rat(RobotController rc) throws GameActionException {
        int cheese = rc.getRawCheese();

        if (cheese >= 10) {
            // DELIVER
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo a : allies) {
                if (a.getType() == UnitType.RAT_KING) {
                    MapLocation k = a.getLocation();
                    if (rc.getLocation().distanceSquaredTo(k) <= 9 && rc.canTransferCheese(k, cheese)) {
                        rc.transferCheese(k, cheese);
                        return;
                    }
                    move(rc, k);
                    return;
                }
            }
        }

        // COLLECT
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = 9999;

        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me, 20)) {
            if (rc.canSenseLocation(loc)) {
                if (rc.senseMapInfo(loc).getCheeseAmount() > 0) {
                    int d = me.distanceSquaredTo(loc);
                    if (d < bestDist) {
                        bestDist = d;
                        best = loc;
                    }
                }
            }
        }

        if (best != null && rc.canPickUpCheese(best)) {
            rc.pickUpCheese(best);
        } else if (best != null) {
            move(rc, best);
        } else {
            // Explore
            move(rc, new MapLocation(rc.getMapWidth()/2, rc.getMapHeight()/2));
        }
    }

    // MOVE: Turn toward target, move forward
    static void move(RobotController rc, MapLocation target) throws GameActionException {
        Direction want = rc.getLocation().directionTo(target);
        Direction face = rc.getDirection();

        if (face == want && rc.canMoveForward()) {
            rc.moveForward();
        } else if (face != want && rc.canTurn()) {
            rc.turn(want);
        } else {
            // Blocked - try anything
            for (Direction d : dirs) {
                if (rc.canMove(d)) {
                    if (rc.getDirection() == d) {
                        if (rc.canMoveForward()) rc.moveForward();
                    } else if (rc.canTurn()) {
                        rc.turn(d);
                    }
                    return;
                }
            }
        }
    }

    static final Direction[] dirs = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };
}
```

## What This Does:

**King**:
- Spawns 12 rats in first 50 rounds (distance 4)
- Places 5 cat traps for defense (rounds 20-40)
- Done

**Baby Rats**:
- If carrying ≥10 cheese: Find king (by sensing), deliver
- Otherwise: Find nearest cheese, collect it
- If no cheese visible: Go to map center

**Movement**:
- Turn to face target
- Move forward
- If blocked: Try any other direction
- No pathfinding algorithms, no coordination

## Why This Will Work:

1. **No zone complexity** - rats go where cheese is
2. **No delivery queues** - deliver when ready
3. **No fancy pathfinding** - just turn and move
4. **Traps for defense** - but simple placement
5. **Sensing for king** - no shared array dependency

## Test Plan:

1. Build ratbot3
2. Run vs examplefuncsplayer
3. Check:
   - Rats spawning
   - Cheese being collected
   - Deliveries happening
   - King surviving

If it works: DONE. Don't add anything.
If it doesn't: Fix the ONE thing that's broken, nothing else.

---

**Philosophy**: Working simple code > broken complex code
