package ratbot;

/**
 * Configuration constants for bot behavior.
 *
 * Centralizes magic numbers with documentation and rationale.
 * Enables easy tuning based on match data and strategy evolution.
 *
 * All values derived from analysis and testing (see DECISIONS.md).
 */
public class BehaviorConfig {

    // ===== Cheese Collection =====

    /**
     * Cheese delivery threshold (return to king when carrying this much).
     *
     * Rationale: Balance carrying penalty vs trip overhead
     * - Carrying penalty: 0.01 * cheese (at 20 = 1.2x slower movement)
     * - Round-trip overhead: ~30 rounds to mine and back
     * - Optimization space: 15-25 range based on map size
     *
     * Tuning: Revisit after Sprint 1 with match data
     * - If cheese income high: Increase to 30 (fewer trips)
     * - If maps small: Decrease to 15 (less penalty)
     */
    public static final int CHEESE_DELIVERY_THRESHOLD = 20;

    /**
     * Emergency delivery threshold (when global cheese critical).
     *
     * Rationale: Faster deliveries prevent starvation
     * - In emergency: Minimize carrying time
     * - Deliver even small amounts immediately
     */
    public static final int EMERGENCY_DELIVERY_THRESHOLD = 5;

    /**
     * Warning delivery threshold (when global cheese low).
     *
     * Rationale: More frequent deliveries without extreme overhead
     * - Balances urgency with efficiency
     */
    public static final int WARNING_DELIVERY_THRESHOLD = 10;

    // ===== Strategic Decisions =====

    /**
     * Backstab evaluation frequency (rounds between checks).
     *
     * Rationale: GameTheory.evaluate() costs ~100 bytecode
     * - Checking every 50 rounds = 2 bytecode/turn average overhead
     * - Strategic decisions don't change rapidly (50-round granularity acceptable)
     *
     * Tuning: Can reduce to 25 rounds if more responsive backstab needed
     */
    public static final int BACKSTAB_CHECK_INTERVAL = 50;

    /**
     * Minimum round for backstab consideration.
     *
     * Rationale: Early game backstab is suboptimal
     * - Before round 200: Cats still strong, cat damage critical
     * - Cooperation mode values cat damage at 50% vs 30% in backstab
     * - Early backstab sacrifices 20% scoring weight
     *
     * Tuning: May adjust based on cat defeat rate
     * - If cats weak early: Reduce to 100
     * - If cats strong: Increase to 300
     */
    public static final int BACKSTAB_EARLIEST_ROUND = 200;

    /**
     * Backstab score advantage threshold (required points above cooperation).
     *
     * Rationale: Risk mitigation for strategic shift
     * - Require clear advantage before committing
     * - Account for estimation errors in enemy state
     *
     * Tuning: Conservative (10 points) for Sprint 1
     * - Can reduce to 5 if game theory accurate
     * - Can increase to 15 if want higher confidence
     */
    public static final int BACKSTAB_CONFIDENCE_THRESHOLD = 10;

    // ===== King Economy =====

    /**
     * Emergency cheese threshold (rounds of food remaining).
     *
     * Rationale: Circuit breaker trigger point
     * - 33 rounds = minimum survival buffer
     * - Allows time for emergency response
     * - Below this: CRITICAL mode (stop all spawning)
     */
    public static final int CRITICAL_CHEESE_ROUNDS = 33;

    /**
     * Warning cheese threshold (rounds of food remaining).
     *
     * Rationale: Early warning for cheese shortages
     * - 100 rounds = comfortable buffer
     * - Triggers reduced spawning, increased collection priority
     * - Prevents critical emergencies
     */
    public static final int WARNING_CHEESE_ROUNDS = 100;

    /**
     * Minimum global cheese for spawning (absolute value).
     *
     * Rationale: Safety margin beyond rounds calculation
     * - Even with good rounds remaining, need absolute minimum
     * - Accounts for sudden cheese consumption (upgrades, attacks)
     */
    public static final int MIN_CHEESE_FOR_SPAWNING = 500;

    /**
     * King formation cheese requirement (per new king).
     *
     * Rationale: New king sustainability
     * - Formation cost: 50 cheese
     * - Buffer for 500 rounds: 1,500 cheese
     * - Total requirement: 1,550 cheese per new king
     */
    public static final int KING_FORMATION_CHEESE = 1550;

    // ===== Movement & Navigation =====

    /**
     * State logging frequency (rounds between logs).
     *
     * Rationale: Balance visibility with log volume
     * - Every 20 rounds captures key transitions
     * - Reduces log spam in 2,000-round games
     * - Profiler samples at same rate for correlation
     */
    public static final int STATE_LOG_INTERVAL = 20;

    /**
     * Economy logging frequency (rounds between logs).
     *
     * Rationale: Track cheese income trends
     * - Every 10 rounds enables income rate calculation
     * - Frequent enough for early problem detection
     */
    public static final int ECONOMY_LOG_INTERVAL = 10;

    // ===== King Redundancy =====

    /**
     * Minimum spacing between kings (distance).
     *
     * Rationale: Prevent multi-king cat pounce losses
     * - Cat pounce range: sqrt(9) = 3 tiles
     * - Safe spacing: 15 tiles (5x pounce range)
     * - Provides spatial redundancy
     *
     * Tuning: Based on cat behavior analysis
     * - If cats rarely target kings: Reduce to 10
     * - If multi-king losses observed: Increase to 20
     */
    public static final int KING_MIN_SPACING = 15;

    /**
     * Optimal king count for different map sizes.
     *
     * Rationale: Balance spawn rate with consumption
     * - Small maps (<30x30): 1-2 kings sufficient
     * - Medium maps (30x40): 2-3 kings optimal
     * - Large maps (40x60): 3 kings maximum (consumption limit)
     */
    public static int getOptimalKingCount(int mapWidth, int mapHeight) {
        int mapArea = mapWidth * mapHeight;

        if (mapArea < 900) return 2;   // Small: 2 kings
        if (mapArea < 1600) return 2;  // Medium: 2 kings
        return 3;                      // Large: 3 kings
    }

    // ===== Combat =====

    /**
     * King retreat health threshold (percentage).
     *
     * Rationale: Preserve kings at all costs
     * - Below 40% health: High death risk
     * - Kings worth 50% of score in backstab mode
     * - Losing king = instant loss if last one
     */
    public static final double KING_RETREAT_HEALTH_PCT = 0.4;

    /**
     * Cheese spending threshold for bite enhancement.
     *
     * Rationale: Only enhance when guarantees kill
     * - Base damage: 10
     * - Enhancement: ceil(log2(cheese)) bonus
     * - Only spend if: damage >= target.health
     *
     * Use: GameTheory.worthEnhancingBite()
     */
    public static final int MAX_CHEESE_PER_BITE = 32; // Gives +5 damage

    // ===== Performance =====

    /**
     * Profiler sampling interval (rounds between samples).
     *
     * Rationale: Reduce profiling overhead
     * - Full profiling: ~50 bytecode overhead per section
     * - Sampling every 20 rounds: 2.5 bytecode/turn average
     */
    public static final int PROFILER_SAMPLE_INTERVAL = 20;

    /**
     * Bytecode budget warning threshold (percentage).
     *
     * Rationale: Early warning for bytecode pressure
     * - Baby rat limit: 17,500
     * - Warning at 80% = 14,000 bytecode
     * - Allows fallback to cheaper algorithms
     */
    public static final double BYTECODE_WARNING_PCT = 0.8;

    // ===== Shared Array Protocol =====

    /**
     * Shared array slot allocation.
     *
     * Array size: 64 slots × 10 bits (0-1023 per slot)
     */
    public static final int SLOT_CHEESE_STATUS = 0;   // Emergency code or rounds remaining
    public static final int SLOT_KING_X = 1;          // King X position
    public static final int SLOT_KING_Y = 2;          // King Y position
    public static final int SLOT_CAT1_X = 3;          // Cat #1 X position (0 if none)
    public static final int SLOT_CAT1_Y = 4;          // Cat #1 Y position
    public static final int SLOT_CAT2_X = 5;          // Cat #2 X position (0 if none)
    public static final int SLOT_CAT2_Y = 6;          // Cat #2 Y position
    public static final int SLOT_CAT3_X = 7;          // Cat #3 X position (0 if none)
    public static final int SLOT_CAT3_Y = 8;          // Cat #3 Y position
    public static final int SLOT_CAT4_X = 9;          // Cat #4 X position (0 if none)
    public static final int SLOT_CAT4_Y = 10;         // Cat #4 Y position
    public static final int SLOT_OUR_CAT_DAMAGE = 29; // Our team cat damage
    public static final int SLOT_ENEMY_CAT_DAMAGE = 30; // Enemy cat damage
    public static final int SLOT_OUR_CHEESE_TRANSFERRED = 31; // Cheese transferred to kings
    public static final int SLOT_ENEMY_CHEESE_TRANSFERRED = 32; // Enemy cheese (estimated)

    /**
     * Emergency code values.
     */
    public static final int EMERGENCY_CRITICAL = 999; // Critical starvation
    public static final int EMERGENCY_CAT_SWARM = 998; // Multiple cats detected
    public static final int EMERGENCY_KING_UNDER_ATTACK = 997; // King taking damage
}
