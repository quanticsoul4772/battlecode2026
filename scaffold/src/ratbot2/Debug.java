package ratbot2;

import battlecode.common.*;
import ratbot2.utils.DirectionUtil;

/**
 * Centralized debugging system for Battlecode 2026.
 *
 * Features:
 * - Visual indicators in client (dots, lines, strings)
 * - Debug levels (VERBOSE, INFO, WARNING, ERROR)
 * - Conditional compilation (disable in production)
 * - Zero overhead when disabled
 *
 * Usage:
 *   Debug.setLevel(Debug.Level.VERBOSE);
 *   Debug.info(rc, "State transition: EXPLORE -> COLLECT");
 *   Debug.dot(rc, location, Debug.Color.GREEN);
 *   Debug.line(rc, start, end, Debug.Color.RED);
 */
public class Debug {

    // Debug levels
    public enum Level {
        OFF(0),      // No debug output
        ERROR(1),    // Only critical errors
        WARNING(2),  // Warnings and errors
        INFO(3),     // Important info
        VERBOSE(4);  // Everything

        final int priority;
        Level(int priority) { this.priority = priority; }
    }

    // Current debug level
    private static Level currentLevel = Level.INFO;

    // Enable/disable debug entirely (set false for competition)
    private static final boolean DEBUG_ENABLED = true;

    /**
     * Set debug level for this match.
     */
    public static void setLevel(Level level) {
        currentLevel = level;
    }

    /**
     * Check if debug level is enabled.
     */
    private static boolean isEnabled(Level level) {
        return DEBUG_ENABLED && level.priority <= currentLevel.priority;
    }

    // ===== Logging Methods =====

    /**
     * Log verbose debug message.
     */
    public static void verbose(RobotController rc, String message) {
        if (isEnabled(Level.VERBOSE)) {
            System.out.println("DEBUG:" + rc.getRoundNum() + ":" + rc.getID() + ":VERBOSE:" + message);
        }
    }

    /**
     * Log info message.
     */
    public static void info(RobotController rc, String message) {
        if (isEnabled(Level.INFO)) {
            System.out.println("DEBUG:" + rc.getRoundNum() + ":" + rc.getID() + ":INFO:" + message);
        }
    }

    /**
     * Log warning message.
     */
    public static void warning(RobotController rc, String message) {
        if (isEnabled(Level.WARNING)) {
            System.out.println("DEBUG:" + rc.getRoundNum() + ":" + rc.getID() + ":WARNING:" + message);
        }
    }

    /**
     * Log error message.
     */
    public static void error(RobotController rc, String message) {
        if (isEnabled(Level.ERROR)) {
            System.out.println("DEBUG:" + rc.getRoundNum() + ":" + rc.getID() + ":ERROR:" + message);
        }
    }

    // ===== Visual Indicators =====

    /**
     * Predefined colors for visual debugging.
     */
    public static class Color {
        public static final int RED = rgb(255, 0, 0);
        public static final int GREEN = rgb(0, 255, 0);
        public static final int BLUE = rgb(0, 0, 255);
        public static final int YELLOW = rgb(255, 255, 0);
        public static final int CYAN = rgb(0, 255, 255);
        public static final int MAGENTA = rgb(255, 0, 255);
        public static final int WHITE = rgb(255, 255, 255);
        public static final int ORANGE = rgb(255, 165, 0);
        public static final int PURPLE = rgb(128, 0, 128);
        public static final int GRAY = rgb(128, 128, 128);

        private static int rgb(int r, int g, int b) {
            return (r << 16) | (g << 8) | b;
        }

        public static int[] components(int color) {
            return new int[]{(color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF};
        }
    }

    /**
     * Draw dot at location (visible in client).
     */
    public static void dot(RobotController rc, MapLocation loc, int color) {
        if (!DEBUG_ENABLED) return;

        int[] rgb = Color.components(color);
        try {
            rc.setIndicatorDot(loc, rgb[0], rgb[1], rgb[2]);
        } catch (Exception e) {}
    }

    /**
     * Draw line between locations (visible in client).
     */
    public static void line(RobotController rc, MapLocation from, MapLocation to, int color) {
        if (!DEBUG_ENABLED) return;

        int[] rgb = Color.components(color);
        try {
            rc.setIndicatorLine(from, to, rgb[0], rgb[1], rgb[2]);
        } catch (Exception e) {}
    }

    /**
     * Set indicator string (visible in client).
     */
    public static void status(RobotController rc, String message) {
        if (!DEBUG_ENABLED) return;

        try {
            rc.setIndicatorString(message);
        } catch (Exception e) {}
    }

    // ===== Debugging Helpers =====

    /**
     * Debug spawn attempt with visual feedback.
     */
    public static void debugSpawnAttempt(RobotController rc, MapLocation loc, boolean success) {
        if (!DEBUG_ENABLED) return;

        if (success) {
            dot(rc, loc, Color.GREEN);
            info(rc, "Spawned at " + loc);
        } else {
            dot(rc, loc, Color.RED);
            verbose(rc, "Spawn blocked at " + loc);
        }
    }

    /**
     * Debug pathfinding with visual path.
     */
    public static void debugPath(RobotController rc, MapLocation start, MapLocation target, Direction step) {
        if (!DEBUG_ENABLED) return;

        dot(rc, target, Color.YELLOW);
        line(rc, start, target, Color.CYAN);

        verbose(rc, "Pathfinding: " + start + " -> " + target + " (step: " + step + ")");
    }

    /**
     * Debug vision cone visualization.
     */
    public static void debugVisionCone(RobotController rc, MapLocation[] visibleTiles) {
        if (!DEBUG_ENABLED || !isEnabled(Level.VERBOSE)) return;

        for (MapLocation loc : visibleTiles) {
            dot(rc, loc, Color.CYAN);
        }
    }

    /**
     * Debug cat detection.
     */
    public static void debugCatDetection(RobotController rc, MapLocation catLoc) {
        if (!DEBUG_ENABLED) return;

        dot(rc, catLoc, Color.RED);
        line(rc, rc.getLocation(), catLoc, Color.RED);
        warning(rc, "CAT DETECTED at " + catLoc);
    }

    /**
     * Debug cheese location.
     */
    public static void debugCheese(RobotController rc, MapLocation cheeseLoc, int amount) {
        if (!DEBUG_ENABLED) return;

        dot(rc, cheeseLoc, Color.YELLOW);
        verbose(rc, "Cheese: " + amount + " at " + cheeseLoc);
    }

    /**
     * Debug state transition.
     */
    public static void debugStateChange(RobotController rc, String fromState, String toState, String reason) {
        if (!DEBUG_ENABLED) return;

        info(rc, "State: " + fromState + " -> " + toState + " (" + reason + ")");
    }

    /**
     * Debug emergency mode.
     */
    public static void debugEmergency(RobotController rc, String type, int cheeseStatus) {
        if (!DEBUG_ENABLED) return;

        error(rc, "EMERGENCY: " + type + " (cheese status: " + cheeseStatus + ")");

        // Visual: Red timeline marker
        rc.setTimelineMarker("EMERGENCY:" + type, 255, 0, 0);
    }

    /**
     * Debug bytecode usage.
     */
    public static void debugBytecode(RobotController rc, int used, int limit) {
        if (!DEBUG_ENABLED || !isEnabled(Level.VERBOSE)) return;

        double percent = (double)used / limit * 100;
        String status = percent > 90 ? "CRITICAL" : percent > 80 ? "WARNING" : "OK";

        verbose(rc, "Bytecode: " + used + "/" + limit + " (" + (int)percent + "% - " + status + ")");
    }

    /**
     * Debug backstab decision.
     */
    public static void debugBackstab(RobotController rc, boolean shouldBackstab, String reasoning) {
        if (!DEBUG_ENABLED) return;

        if (shouldBackstab) {
            error(rc, "BACKSTAB TRIGGERED: " + reasoning);
            rc.setTimelineMarker("BACKSTAB", 255, 0, 0);
        } else {
            verbose(rc, "Backstab check: " + reasoning);
        }
    }

    /**
     * Draw vision cone boundaries.
     */
    public static void visualizeVisionCone(RobotController rc) {
        if (!DEBUG_ENABLED || !isEnabled(Level.VERBOSE)) return;

        MapLocation me = rc.getLocation();
        Direction facing = rc.getDirection();

        // Draw lines at 90° cone edges (±45° from facing)
        Direction left45 = DirectionUtil.turn(facing, -1);
        Direction right45 = DirectionUtil.turn(facing, 1);

        MapLocation leftEdge = me.add(left45).add(left45).add(left45);
        MapLocation rightEdge = me.add(right45).add(right45).add(right45);

        line(rc, me, leftEdge, Color.CYAN);
        line(rc, me, rightEdge, Color.CYAN);
    }

    /**
     * Mark target location with color.
     */
    public static void markTarget(RobotController rc, MapLocation target, String label) {
        if (!DEBUG_ENABLED) return;

        dot(rc, target, Color.MAGENTA);
        line(rc, rc.getLocation(), target, Color.MAGENTA);
        status(rc, label + ": " + target);
    }

    /**
     * Debug decision context.
     */
    public static void debugDecision(RobotController rc, String decision, String context) {
        if (!DEBUG_ENABLED) return;

        info(rc, "DECISION: " + decision + " | " + context);
        status(rc, decision);
    }

    /**
     * Visualize all nearby robots with color coding.
     */
    public static void visualizeNearbyRobots(RobotController rc) throws GameActionException {
        if (!DEBUG_ENABLED || !isEnabled(Level.VERBOSE)) return;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo[] cats = rc.senseNearbyRobots(-1, Team.NEUTRAL);

        for (RobotInfo ally : allies) {
            dot(rc, ally.getLocation(), Color.GREEN);
        }

        for (RobotInfo enemy : enemies) {
            dot(rc, enemy.getLocation(), Color.ORANGE);
        }

        for (RobotInfo cat : cats) {
            dot(rc, cat.getLocation(), Color.RED);
            line(rc, rc.getLocation(), cat.getLocation(), Color.RED);
        }
    }

    /**
     * Timeline marker for important events.
     */
    public static void timeline(RobotController rc, String event, int color) {
        if (!DEBUG_ENABLED) return;

        int[] rgb = Color.components(color);
        rc.setTimelineMarker(event, rgb[0], rgb[1], rgb[2]);
    }
}
