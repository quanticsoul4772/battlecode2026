# Battlecode 2026 - Quick Reference Card

## Unit Stats Table

| Unit | HP | Size | Move CD | Turn CD | Vision | Bytecode |
|------|-----|------|---------|---------|--------|----------|
| Baby Rat | 100 | 1x1 | 10 | 10 | √20, 90° | 17,500 |
| Rat King | 500 | 3x3 | 40 | 10 | √25, 360° | 20,000 |
| Cat | 10,000 | 2x2 | 10 | - | √30, 180° | N/A |

## Action Costs

| Action | Cheese Cost | Cooldown |
|--------|-------------|----------|
| Spawn rat | 10 + 10*floor(n/4) | 10 |
| Upgrade to king | 50 | 0 |
| Rat trap | 5 | 5 |
| Cat trap | 10 | 10 |
| Dig dirt | 10 | 25 |
| Place dirt | 10 | 25 |
| King consumption | 3/round | - |

## Damage Table

| Attack | Damage | Notes |
|--------|--------|-------|
| Rat bite (base) | 10 | +ceil(log X) if spend X cheese |
| Cat scratch | 50 | Range: vision cone |
| Cat pounce | Instant kill | Baby rats only, up to 3 tiles |
| Throw impact | 5*(4-airtime) | To thrown + hit target |
| Rat trap | 50 + stun | Stun = +20 move cooldown |
| Cat trap | 100 + stun | Stun = +20 move cooldown |

## Scoring Formulas

**Cooperation**: 0.5 * cat_dmg + 0.3 * kings + 0.2 * cheese
**Backstabbing**: 0.3 * cat_dmg + 0.5 * kings + 0.2 * cheese

## Communication

**Shared Array**:
- 64 slots × 10 bits (0-1023)
- Read: Anyone
- Write: Rat kings only

**Squeaking**:
- Range: 4 tiles
- Duration: 5 rounds
- Limit: 1/robot/turn
- **WARNING**: Cats hear squeaks!

## Cat AI Cheat Sheet

```
EXPLORE → (see rat) → ATTACK
        → (hear squeak) → CHASE → SEARCH
SEARCH → (see rat) → ATTACK
       → (no rats) → EXPLORE
ATTACK → (lose sight) → EXPLORE
```

**Attack Priority**: Scratch > Pounce > Move toward

## Cheese Spawn Probability

| Rounds Since Last | Probability |
|-------------------|-------------|
| 1 | ~1% |
| 10 | ~10% |
| 69 | ~50% |
| 100 | ~63% |
| 230 | ~90% |

Formula: 1 - (1 - 0.01)^r

## Ratnapping Rules

**Can grab if**:
- Adjacent + visible
- AND (facing away OR less HP OR ally)

**Effects**:
- Carrier: Normal speed
- Carried: Stunned, immune to attacks (except cat pounce)
- Auto-drop: 10 rounds
- Can throw: 2 tiles/turn for 4 turns

## King Formation

**Requirements**:
- 7+ allied rats in 3x3
- Center rat upgrades
- 3x3 passable, no kings/cats
- Cost: 50 cheese

**Result**:
- HP = sum of rats (max 500)
- Surrounding rats destroyed
- Raw cheese → global
- Center retains cooldowns

## Critical Limits

- Max 5 rat kings/team
- Max 25 rat traps/team
- Max 10 cat traps/team
- Max 10 rounds carrying
- Max 8 MB heap/robot

## Map Constraints

- Size: 30x30 to 60x60 (even)
- Walls: ≤20%
- Dirt: ≤50%
- Cheese mines: Even number, ≥√5 spacing
- Symmetry: Rotation or reflection

## Starting Conditions

- 1 rat king per team
- 2,500 global cheese per team
- Even number of cats at center
- All units face map center
- IDs ≥ 10,000

## Cooldown Mechanics

**Movement/Turning/Action ready when**: cooldown < 10
**Per-round reduction**: -10 to all cooldowns
**Cheese carrying penalty**: 1% per cheese multiplier

## Quick Win/Loss Checks

**Instant Win**: Enemy loses all kings
**Instant Loss**: Lose all kings
**Point Win**: Higher score at round 2,000 or cat defeat
**Tiebreak**: Global cheese → Total rats → Random
