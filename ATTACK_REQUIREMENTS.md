# Attack Requirements - Key Finding

From spec: **Attack range = Adjacent (8 neighbors)**

Adjacent means distanceSquared <= 2:
- dist=1: Cardinal adjacent (N/S/E/W), 1 tile away
- dist=2: Diagonal adjacent (NE/SE/SW/NW), 1 tile away  
- dist=4: 2 tiles away (NOT ADJACENT - NO ATTACK!)

Current problem:
- Rats stuck at dist=4-5 (2 tiles away)
- canAttack() returns false (not adjacent)
- Need to close from dist=4 to dist<=2

King is 3x3 (9 tiles):
- Center at [X,Y]
- Occupies [X-1 to X+1, Y-1 to Y+1]
- Rats targeting center [X,Y]
- dist=4 from center = 2 tiles from edge

Rats need to get within 1 tile of king EDGE to attack (dist<=2 from CENTER).
