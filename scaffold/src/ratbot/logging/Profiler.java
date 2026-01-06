package ratbot.logging;

import battlecode.common.*;
/**
 * Bytecode profiling utilities for Battlecode 2026.
 *
 * Tracks bytecode usage across different code sections to identify:
 * - Hotspots consuming excessive bytecode
 * - Optimization opportunities
 * - Turn-by-turn bytecode trends
 *
 * Uses mock Clock interface for standalone development.
 * Will use actual battlecode.common.Clock when integrated.
 */
public class Profiler {

    private static int profileStartBytecode = 0;
    private static int turnStartBytecode = 0;
    private static int[] sectionBytecodes = new int[20]; // Track up to 20 sections
    private static String[] sectionNames = new String[20];
    private static int sectionCount = 0;

    // Sampling rate (profile every N rounds to reduce overhead)
    private static final int SAMPLE_INTERVAL = 20;

    /**
     * Start profiling a code section.
     * Call before expensive operation.
     */
    public static void start() {
        profileStartBytecode = Clock.getBytecodeNum();
    }

    /**
     * End profiling and log result.
     * Call after expensive operation.
     *
     * @param section Name of code section
     * @param round Current round number
     * @param id Robot ID
     */
    public static void end(String section, int round, int id) {
        int cost = Clock.getBytecodeNum() - profileStartBytecode;

        // Only log every SAMPLE_INTERVAL rounds to reduce spam
        if (round % SAMPLE_INTERVAL == 0) {
            Logger.logProfile(round, id, section, cost);
        }

        // Track for summary
        recordSection(section, cost);
    }

    /**
     * Start tracking at beginning of turn.
     */
    public static void startTurn() {
        turnStartBytecode = Clock.getBytecodeNum();
        sectionCount = 0; // Reset section tracking
    }

    /**
     * End turn and log total bytecode usage.
     *
     * @param round Current round
     * @param id Robot ID
     */
    public static void endTurn(int round, int id) {
        int totalUsed = Clock.getBytecodeNum() - turnStartBytecode;

        if (round % SAMPLE_INTERVAL == 0) {
            Logger.logProfile(round, id, "TOTAL_TURN", totalUsed);
        }
    }

    /**
     * Record section bytecode usage for aggregation.
     */
    private static void recordSection(String section, int bytecodes) {
        // Find existing section or add new
        for (int i = sectionCount; --i >= 0;) {
            if (sectionNames[i].equals(section)) {
                sectionBytecodes[i] += bytecodes;
                return;
            }
        }

        // Add new section
        if (sectionCount < sectionNames.length) {
            sectionNames[sectionCount] = section;
            sectionBytecodes[sectionCount] = bytecodes;
            sectionCount++;
        }
    }

    /**
     * Get bytecode usage for section.
     *
     * @param section Section name
     * @return Total bytecodes used in this section
     */
    public static int getBytecodes(String section) {
        for (int i = sectionCount; --i >= 0;) {
            if (sectionNames[i].equals(section)) {
                return sectionBytecodes[i];
            }
        }
        return 0;
    }

    /**
     * Check if approaching bytecode limit.
     *
     * @param limit Bytecode limit for unit type
     * @return true if >80% of limit used
     */
    public static boolean approachingLimit(int limit) {
        int used = Clock.getBytecodeNum();
        return used > (limit * 0.8);
    }

    /**
     * Get current bytecode usage.
     *
     * @return Bytecodes used this turn
     */
    public static int current() {
        return Clock.getBytecodeNum();
    }

    /**
     * Get remaining bytecode budget.
     *
     * @param limit Bytecode limit for unit type
     * @return Bytecodes remaining
     */
    public static int remaining(int limit) {
        return limit - Clock.getBytecodeNum();
    }

    /**
     * Log summary of all tracked sections.
     * Call at end of game or periodically.
     */
    public static void logSummary(int round, int id) {
        for (int i = sectionCount; --i >= 0;) {
            Logger.logProfile(round, id, sectionNames[i] + "_TOTAL", sectionBytecodes[i]);
        }
    }

    /**
     * Reset profiling data.
     * Call at start of game or when needed.
     */
    public static void reset() {
        sectionCount = 0;
        for (int i = 0; i < sectionBytecodes.length; i++) {
            sectionBytecodes[i] = 0;
            sectionNames[i] = null;
        }
    }
}

