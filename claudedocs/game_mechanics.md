# Battlecode 2026 Game Mechanics

## Game Overview
**Theme**: Rats vs Cats - Cheese collection strategy game

### Win Condition
- Maximize cheese collection over match duration
- Destroy all enemy units
- Match ends at `GAME_MAX_NUMBER_OF_ROUNDS`

## Unit Types

### BABY_RAT (Basic Unit)
- **Role**: Cheese collection, combat, exploration
- **Special**: Can be carried/thrown
- **Combat**: Bite attack (`RAT_BITE_DAMAGE`)
- **Upgrade**: Can become RAT_KING with cheese

### RAT_KING (Command Unit)
- **Role**: Unit spawning, coordination
- **Abilities**:
  - Build robots (costs cheese)
  - Share cheese with team
  - Write to shared array for communication
- **Limitations**:
  - Consumes cheese per round (`RATKING_CHEESE_CONSUMPTION`)
  - Takes health damage if unfed (`RATKING_HEALTH_LOSS`)
  - Maximum allowed: `MAX_NUMBER_OF_RAT_KINGS`
- **Upgrade Cost**: `RAT_KING_UPGRADE_CHEESE_COST`

### CAT (Enemy Unit)
- **Role**: Rat hunting, territory control
- **Abilities**:
  - Scratch attack (`CAT_SCRATCH_DAMAGE`)
  - Pounce (ranged attack up to `CAT_POUNCE_MAX_DISTANCE_SQUARED`)
  - Reduced damage when pouncing adjacent targets
- **Special**: Sleeps after being fed (`CAT_SLEEP_TIME`)

## Core Mechanics

### Cheese System
- **Starting Amount**: `INITIAL_TEAM_CHEESE` per team
- **Collection**: Pick up from cheese mines on map
- **Spawn Rate**: Mines spawn cheese at `CHEESE_SPAWN_PROBABILITY`
- **Spawn Amount**: `CHEESE_SPAWN_AMOUNT` per spawn
- **Transfer**: Rats can transfer cheese to kings (range `CHEESE_DROP_RADIUS_SQUARED`)
- **Carrying Penalty**: `CHEESE_COOLDOWN_PENALTY` per cheese unit

### Movement
- **Forward**: Standard cooldown
- **Strafe**: Extra cooldown (`MOVE_STRAFE_COOLDOWN`)
- **Turning**: `TURNING_COOLDOWN` added
- **Carrying**: `CARRY_COOLDOWN_MULTIPLIER` when carrying rats
- **Direction**: Rats have facing direction, affects movement cost

### Building & Spawning
- **Spawn Cost**: `BUILD_ROBOT_BASE_COST` + increases
  - Cost increases by `BUILD_ROBOT_COST_INCREASE` per `NUM_ROBOTS_FOR_COST_INCREASE` units
- **Range**: `BUILD_ROBOT_RADIUS_SQUARED`
- **Cooldown**: `BUILD_ROBOT_COOLDOWN`

### Combat
- **Rat Bite**: `RAT_BITE_DAMAGE`
- **Cat Scratch**: `CAT_SCRATCH_DAMAGE`
- **Cat Pounce**: Variable damage based on distance
- **Throw Damage**: `THROW_DAMAGE` + `THROW_DAMAGE_PER_TILE`

### Throwing Mechanics
- **Grab**: Rats can carry other rats (health threshold: `HEALTH_GRAB_THRESHOLD`)
- **Throw Duration**: `THROW_DURATION` turns in air
- **Impact Stun**: 
  - Ground: `HIT_GROUND_COOLDOWN`
  - Target: `HIT_TARGET_COOLDOWN`
- **Limits**:
  - Max tower height: `MAX_CARRY_TOWER_HEIGHT`
  - Max carry duration: `MAX_CARRY_DURATION`

### Traps & Terrain
- **Dirt**:
  - Place cost: `PLACE_DIRT_CHEESE_COST`
  - Dig cost: `DIG_DIRT_CHEESE_COST`
  - Blocks movement (walls)
- **Rat Traps**: Target rats
- **Cat Traps**: Target cats
- **Range**: `BUILD_DISTANCE_SQUARED`
- **Dig Cooldown**: `DIG_COOLDOWN`

### Communication
- **Squeak**: Broadcast messages
  - Range: `SQUEAK_RADIUS_SQUARED`
  - Limit: `MAX_MESSAGES_SENT_ROBOT` per turn
  - Duration: `MESSAGE_ROUND_DURATION` rounds
- **Shared Array**: 
  - Size: `SHARED_ARRAY_SIZE`
  - Max value: `COMM_ARRAY_MAX_VALUE`
  - Only Rat Kings can write

### Vision
- **Vision Cone**: Each unit has:
  - Radius: `visionConeRadiusSquared`
  - Angle: `visionConeAngle`
- **Direction-based**: Vision in facing direction

## Map Features
- **Cheese Mines**: Spawn cheese periodically
- **Walls**: Block movement
- **Dirt**: Placeable/removable obstacles
- **Spacing**: `MIN_CHEESE_MINE_SPACING_SQUARED` between mines

## Bytecode & Performance
- **Limit**: Per-unit `bytecodeLimit`
- **Team Limit**: `MAX_TEAM_EXECUTION_TIME`
- **Penalty**: `EXCEPTION_BYTECODE_PENALTY` for errors
- **Cooldown**: `COOLDOWNS_PER_TURN` reduction

## Game Modes
- **Cooperation**: Teams work together
- **Backstabbing**: Competitive mode
- Check with: `isCooperation()`

## Key Strategic Considerations
1. **Cheese Economy**: Balance collection vs spending
2. **King Management**: Feed kings or they die
3. **Vision Control**: Facing direction critical
4. **Throwing**: Offensive and mobility tool
5. **Trap Placement**: Zone control
6. **Communication**: Limited but powerful
