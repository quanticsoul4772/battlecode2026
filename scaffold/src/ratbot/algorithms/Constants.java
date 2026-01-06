package ratbot.algorithms;


import battlecode.common.*;
/**
 * Battlecode 2026 Game Constants.
 * Source: Official javadoc constant-values.html + specs.pdf
 *
 * Use these exact values for calculations.
 */
public class Constants {

    // ===== Map Parameters =====
    public static final int MAP_MIN_WIDTH = 20;
    public static final int MAP_MAX_WIDTH = 60;
    public static final int MAP_MIN_HEIGHT = 20;
    public static final int MAP_MAX_HEIGHT = 60;
    public static final int MAX_WALL_PERCENTAGE = 20;
    public static final int MAX_DIRT_PERCENTAGE = 50;

    // ===== Game Rules =====
    public static final int GAME_MAX_NUMBER_OF_ROUNDS = 2000;
    public static final int GAME_DEFAULT_SEED = 6370;
    public static final long MAX_TEAM_EXECUTION_TIME = 1200000000000L; // nanoseconds

    // ===== Initial Resources =====
    public static final int INITIAL_TEAM_CHEESE = 2500;

    // ===== Cheese Mechanics =====
    public static final double CHEESE_COOLDOWN_PENALTY = 0.01; // Per cheese carried
    public static final int CHEESE_SPAWN_AMOUNT = 5;
    public static final double CHEESE_MINE_SPAWN_PROBABILITY = 0.01; // Base probability
    public static final int SQ_CHEESE_SPAWN_RADIUS = 4; // Spawn radius from mine
    public static final int MIN_CHEESE_MINE_SPACING_SQUARED = 25; // sqrt(25) = 5
    public static final int CHEESE_DROP_RADIUS_SQUARED = 9; // sqrt(9) = 3 for transfer
    public static final int CHEESE_TRANSFER_COOLDOWN = 10;

    // ===== Movement & Cooldowns =====
    public static final int COOLDOWNS_PER_TURN = 10; // Reduction per round
    public static final int COOLDOWN_LIMIT = 10; // Threshold for action readiness
    public static final int MOVE_STRAFE_COOLDOWN = 18; // Extra for non-forward movement
    public static final int TURNING_COOLDOWN = 10;
    public static final double CARRY_COOLDOWN_MULTIPLIER = 1.5;

    // ===== Building & Spawning =====
    public static final int BUILD_ROBOT_BASE_COST = 10;
    public static final int BUILD_ROBOT_COST_INCREASE = 10;
    public static final int NUM_ROBOTS_FOR_COST_INCREASE = 4;
    public static final int BUILD_ROBOT_RADIUS_SQUARED = 4; // Adjacent to king
    public static final int BUILD_ROBOT_COOLDOWN = 10;
    public static final int BUILD_DISTANCE_SQUARED = 2; // For traps/dirt

    // ===== Rat Kings =====
    public static final int NUMBER_INITIAL_RAT_KINGS = 1;
    public static final int MAX_NUMBER_OF_RAT_KINGS = 5;
    public static final int RAT_KING_UPGRADE_CHEESE_COST = 50;
    public static final int RATKING_CHEESE_CONSUMPTION = 3; // Per round
    public static final int RATKING_HEALTH_LOSS = 10; // When unfed

    // ===== Combat Damage =====
    public static final int RAT_BITE_DAMAGE = 10;
    public static final int CAT_SCRATCH_DAMAGE = 50;
    public static final int THROW_DAMAGE = 20; // Base throw damage
    public static final int THROW_DAMAGE_PER_TILE = 5; // Additional per tile traveled
    public static final int CAT_POUNCE_ADJACENT_DAMAGE_PERCENT = 50;
    public static final int CAT_POUNCE_MAX_DISTANCE_SQUARED = 9; // sqrt(9) = 3

    // ===== Throwing Mechanics =====
    public static final int THROW_DURATION = 4; // Turns in air
    public static final int HIT_GROUND_COOLDOWN = 10; // Stun from normal landing
    public static final int HIT_TARGET_COOLDOWN = 30; // Stun from hitting target
    public static final int HEALTH_GRAB_THRESHOLD = 0; // Health difference to grab
    public static final int MAX_CARRY_DURATION = 10; // Auto-drop after this
    public static final int MAX_CARRY_TOWER_HEIGHT = 2; // Max stacking

    // ===== Dirt Management =====
    public static final int DIG_DIRT_CHEESE_COST = 10;
    public static final int PLACE_DIRT_CHEESE_COST = 10;
    public static final int DIG_COOLDOWN = 25;
    public static final int CAT_DIG_ADDITIONAL_COOLDOWN = 5;

    // ===== Communication =====
    public static final int SHARED_ARRAY_SIZE = 64;
    public static final int COMM_ARRAY_MAX_VALUE = 1023; // 10 bits
    public static final int SQUEAK_RADIUS_SQUARED = 16; // sqrt(16) = 4
    public static final int MAX_MESSAGES_SENT_ROBOT = 1; // Per turn
    public static final int MESSAGE_ROUND_DURATION = 5; // Rounds before disappearing

    // ===== Cats =====
    public static final int CAT_SLEEP_TIME = 2; // When fed

    // ===== Misc =====
    public static final int INDICATOR_STRING_MAX_LENGTH = 256;
    public static final int TIMELINE_LABEL_MAX_LENGTH = 64;
    public static final int EXCEPTION_BYTECODE_PENALTY = 500;
    public static final String SPEC_VERSION = "1";

    // ===== Unit Stats (from specs.pdf) =====

    // Baby Rat
    public static final int BABY_RAT_HEALTH = 100;
    public static final int BABY_RAT_MOVEMENT_COOLDOWN = 10;
    public static final int BABY_RAT_TURNING_COOLDOWN = 10;
    public static final int BABY_RAT_VISION_RADIUS_SQUARED = 20; // sqrt(20) ~= 4.47
    public static final int BABY_RAT_VISION_CONE_ANGLE = 90;
    public static final int BABY_RAT_BYTECODE_LIMIT = 17500;
    public static final int BABY_RAT_SIZE = 1;

    // Rat King
    public static final int RAT_KING_HEALTH = 500;
    public static final int RAT_KING_MOVEMENT_COOLDOWN = 40;
    public static final int RAT_KING_TURNING_COOLDOWN = 10;
    public static final int RAT_KING_VISION_RADIUS_SQUARED = 25; // sqrt(25) = 5
    public static final int RAT_KING_VISION_CONE_ANGLE = 360;
    public static final int RAT_KING_BYTECODE_LIMIT = 20000;
    public static final int RAT_KING_SIZE = 3; // 3x3

    // Cat
    public static final int CAT_HEALTH = 10000;
    public static final int CAT_MOVEMENT_COOLDOWN = 10;
    public static final int CAT_VISION_RADIUS_SQUARED = 30; // sqrt(30) ~= 5.48
    public static final int CAT_VISION_CONE_ANGLE = 180;
    public static final int CAT_SIZE = 2; // 2x2

    // ===== Calculated Values =====

    /**
     * Calculate spawn cost based on alive baby rats.
     * Formula: 10 + 10 * floor(baby_rats_alive / 4)
     */
    public static int getSpawnCost(int babyRatsAlive) {
        return BUILD_ROBOT_BASE_COST + BUILD_ROBOT_COST_INCREASE * (babyRatsAlive / NUM_ROBOTS_FOR_COST_INCREASE);
    }

    /**
     * Calculate cheese spawn probability for a mine.
     * Formula: 1 - (1 - 0.01)^r where r = rounds since last spawn
     */
    public static double getCheeseSpawnProbability(int roundsSinceLastSpawn) {
        return 1.0 - Math.pow(1.0 - CHEESE_MINE_SPAWN_PROBABILITY, roundsSinceLastSpawn);
    }

    /**
     * Calculate bite damage with cheese enhancement.
     * Formula: 10 + ceil(log2(cheese))
     */
    public static int getBiteDamage(int cheeseSpent) {
        if (cheeseSpent == 0) return RAT_BITE_DAMAGE;
        return RAT_BITE_DAMAGE + (int)Math.ceil(Math.log(cheeseSpent) / Math.log(2));
    }

    /**
     * Calculate throw damage based on airtime.
     * Formula: 5 * (4 - airtime)
     * Note: This is per the PDF spec, but javadoc shows THROW_DAMAGE = 20 (max)
     */
    public static int getThrowDamage(int airtime) {
        return THROW_DAMAGE_PER_TILE * (THROW_DURATION - airtime);
    }

    /**
     * Calculate movement cooldown with cheese carrying penalty.
     * Formula: baseCooldown * (1 + 0.01 * cheeseCarried)
     */
    public static int getMovementCooldownWithCheese(int baseCooldown, int cheeseCarried) {
        return baseCooldown + (int)(baseCooldown * CHEESE_COOLDOWN_PENALTY * cheeseCarried);
    }

    /**
     * Estimate king sustainability.
     * Returns rounds of cheese supply for given king count.
     */
    public static int roundsOfCheeseSupply(int globalCheese, int kingCount) {
        int consumptionPerRound = kingCount * RATKING_CHEESE_CONSUMPTION;
        if (consumptionPerRound == 0) return Integer.MAX_VALUE;
        return globalCheese / consumptionPerRound;
    }
}
