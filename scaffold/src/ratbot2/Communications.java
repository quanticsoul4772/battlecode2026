package ratbot2;

/**
 * Shared array communication protocol. Kings write, all rats read. 64 slots × 10-bit values
 * (0-1023).
 */
public class Communications {
  // Emergency & King Position
  public static final int SLOT_EMERGENCY = 0; // 999=critical, 0-200=rounds remaining
  public static final int SLOT_KING_X = 1;
  public static final int SLOT_KING_Y = 2;

  // Cat Tracking (up to 4 cats, 2 slots each for X/Y)
  public static final int SLOT_CAT1_X = 3;
  public static final int SLOT_CAT1_Y = 4;
  public static final int SLOT_CAT2_X = 5;
  public static final int SLOT_CAT2_Y = 6;
  public static final int SLOT_CAT3_X = 7;
  public static final int SLOT_CAT3_Y = 8;
  public static final int SLOT_CAT4_X = 9;
  public static final int SLOT_CAT4_Y = 10;

  // Primary Target (focus fire - all combat rats attack this)
  public static final int SLOT_PRIMARY_CAT_X = 11;
  public static final int SLOT_PRIMARY_CAT_Y = 12;

  // Enemy King Tracking
  public static final int SLOT_ENEMY_KING_X = 13;
  public static final int SLOT_ENEMY_KING_Y = 14;

  // Map Boundaries (for zone calculation)
  public static final int SLOT_MAP_WIDTH = 15;
  public static final int SLOT_MAP_HEIGHT = 16;

  // Reserved slots 17-63 available
  public static final int EMERGENCY_CRITICAL = 999;
}
