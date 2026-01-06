package ratbot;

import battlecode.common.*;

/**
 * Debug configuration flags.
 * Toggle features on/off for different debugging scenarios.
 */
public class DebugConfig {

    // ===== Global Debug Control =====

    /**
     * Master debug switch.
     * Set to false for competition submission (removes all debug overhead).
     */
    public static final boolean ENABLED = true;

    /**
     * Debug level (OFF, ERROR, WARNING, INFO, VERBOSE).
     */
    public static final Debug.Level LEVEL = Debug.Level.VERBOSE;

    // ===== Feature Flags =====

    /**
     * Enable visual indicators in client.
     * Dots, lines, and strings visible in replay.
     */
    public static final boolean VISUAL_INDICATORS = true;

    /**
     * Enable timeline markers for major events.
     */
    public static final boolean TIMELINE_MARKERS = true;

    /**
     * Enable detailed state logging.
     */
    public static final boolean STATE_LOGGING = true;

    /**
     * Enable bytecode profiling.
     */
    public static final boolean BYTECODE_PROFILING = false;

    /**
     * Enable spawn debugging.
     */
    public static final boolean DEBUG_SPAWNING = true;

    /**
     * Enable pathfinding visualization.
     */
    public static final boolean DEBUG_PATHFINDING = false;

    /**
     * Enable vision cone visualization.
     */
    public static final boolean DEBUG_VISION = false;

    /**
     * Enable cheese collection debugging.
     */
    public static final boolean DEBUG_CHEESE = true;

    /**
     * Enable combat debugging.
     */
    public static final boolean DEBUG_COMBAT = false;

    /**
     * Enable emergency mode debugging.
     */
    public static final boolean DEBUG_EMERGENCY = true;

    /**
     * Enable backstab decision debugging.
     */
    public static final boolean DEBUG_BACKSTAB = true;

    // ===== Performance Tuning =====

    /**
     * Logging frequency (rounds between verbose logs).
     * Higher = less spam, lower = more detail.
     */
    public static final int LOG_INTERVAL = 20;

    /**
     * Visual indicator frequency (rounds between indicator updates).
     * Higher = less visual clutter, lower = more real-time feedback.
     */
    public static final int INDICATOR_INTERVAL = 5;

    /**
     * Max indicator string length (prevent truncation).
     */
    public static final int MAX_INDICATOR_LENGTH = 64;

    // ===== Conditional Debugging =====

    /**
     * Only debug specific robot IDs (empty = all robots).
     * Useful for tracking specific units.
     */
    public static final int[] DEBUG_ROBOT_IDS = {};

    /**
     * Only debug after specific round (0 = always).
     * Useful for mid/late game debugging.
     */
    public static final int DEBUG_AFTER_ROUND = 0;

    /**
     * Only debug before specific round (9999 = always).
     * Useful for early game debugging.
     */
    public static final int DEBUG_BEFORE_ROUND = 9999;

    /**
     * Check if should debug this robot at this round.
     */
    public static boolean shouldDebug(RobotController rc) {
        if (!ENABLED) return false;

        int round = rc.getRoundNum();
        if (round < DEBUG_AFTER_ROUND || round > DEBUG_BEFORE_ROUND) return false;

        if (DEBUG_ROBOT_IDS.length > 0) {
            int id = rc.getID();
            for (int debugId : DEBUG_ROBOT_IDS) {
                if (id == debugId) return true;
            }
            return false;
        }

        return true;
    }

    // ===== Quick Profiles =====

    /**
     * Development profile: All debugging enabled.
     */
    public static void enableDevelopmentMode() {
        // Can't change final fields at runtime, but this documents the pattern
        // In practice: Change final field values above
    }

    /**
     * Competition profile: Minimal debugging.
     */
    public static void enableCompetitionMode() {
        // Set ENABLED = false
        // Set LEVEL = Level.ERROR
        // Set all DEBUG_* = false
    }

    /**
     * Performance testing profile: Bytecode profiling only.
     */
    public static void enableProfilingMode() {
        // Set BYTECODE_PROFILING = true
        // Set VISUAL_INDICATORS = false
        // Set LEVEL = Level.WARNING
    }
}
