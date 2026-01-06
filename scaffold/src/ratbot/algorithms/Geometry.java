package ratbot.algorithms;


import battlecode.common.*;
/**
 * Geometry and distance utilities for Battlecode 2026.
 *
 * Optimized for bytecode efficiency:
 * - Backward loops (30% savings)
 * - Avoid allocations in hot paths
 * - Integer math (no floating point)
 *
 * Standalone module - integrates into any scaffold.
 */
public class Geometry {

    // Static buffer for locations within radius (max ~400 for 20x20 area)
    private static MapLocation[] radiusBuffer = new MapLocation[400];

    /**
     * Manhattan distance (taxicab distance).
     * Cheaper than Euclidean, useful for pathfinding heuristics.
     *
     * @param a First location
     * @param b Second location
     * @return |dx| + |dy|
     */
    public static int manhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    /**
     * Chebyshev distance (chessboard distance).
     * max(|dx|, |dy|) - useful for square vision ranges.
     *
     * @param a First location
     * @param b Second location
     * @return max(|dx|, |dy|)
     */
    public static int chebyshevDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    /**
     * Euclidean distance squared.
     * Standard distance metric, avoids sqrt (expensive).
     *
     * @param a First location
     * @param b Second location
     * @return dx² + dy²
     */
    public static int distanceSquared(MapLocation a, MapLocation b) {
        int dx = a.x - b.x;
        int dy = a.y - b.y;
        return dx * dx + dy * dy;
    }

    /**
     * Find closest location to reference point.
     * Uses backward loop for bytecode efficiency.
     *
     * @param reference Reference point
     * @param locations Array of candidate locations
     * @return Closest location, or null if array empty
     */
    public static MapLocation closest(MapLocation reference, MapLocation[] locations) {
        if (locations.length == 0) return null;

        MapLocation best = locations[0];
        int bestDist = reference.distanceSquaredTo(locations[0]);

        // Backward loop (bytecode optimization)
        for (int i = locations.length; --i > 0;) {
            int dist = reference.distanceSquaredTo(locations[i]);
            if (dist < bestDist) {
                bestDist = dist;
                best = locations[i];
            }
        }

        return best;
    }

    /**
     * Find farthest location from reference point.
     *
     * @param reference Reference point
     * @param locations Array of candidate locations
     * @return Farthest location, or null if array empty
     */
    public static MapLocation farthest(MapLocation reference, MapLocation[] locations) {
        if (locations.length == 0) return null;

        MapLocation best = locations[0];
        int bestDist = reference.distanceSquaredTo(locations[0]);

        for (int i = locations.length; --i > 0;) {
            int dist = reference.distanceSquaredTo(locations[i]);
            if (dist > bestDist) {
                bestDist = dist;
                best = locations[i];
            }
        }

        return best;
    }

    /**
     * Sort locations by distance from reference (in-place).
     * Uses simple insertion sort (good for small arrays).
     *
     * @param reference Reference point
     * @param locations Array to sort (modified in-place)
     */
    public static void sortByDistance(MapLocation reference, MapLocation[] locations) {
        // Insertion sort (efficient for small n, simple, low bytecode)
        for (int i = 1; i < locations.length; i++) {
            MapLocation key = locations[i];
            int keyDist = reference.distanceSquaredTo(key);
            int j = i - 1;

            while (j >= 0 && reference.distanceSquaredTo(locations[j]) > keyDist) {
                locations[j + 1] = locations[j];
                j--;
            }
            locations[j + 1] = key;
        }
    }

    /**
     * Check if location is within square radius.
     * Faster than distanceSquaredTo for range checks.
     *
     * @param center Center point
     * @param target Target to check
     * @param radiusSquared Radius squared
     * @return true if within range
     */
    public static boolean withinRange(MapLocation center, MapLocation target, int radiusSquared) {
        return center.distanceSquaredTo(target) <= radiusSquared;
    }

    /**
     * Get locations within radius into provided buffer (bytecode-optimized).
     * @param buffer Buffer to store locations
     * @param center Center point
     * @param radiusSquared Radius squared
     * @param mapWidth Map width
     * @param mapHeight Map height
     * @return Count of locations stored in buffer
     */
    public static int locationsWithinRadiusIntoBuffer(
        MapLocation[] buffer,
        MapLocation center,
        int radiusSquared,
        int mapWidth,
        int mapHeight
    ) {
        // Bounding box
        int radius = (int)Math.ceil(Math.sqrt(radiusSquared));
        int minX = Math.max(0, center.x - radius);
        int maxX = Math.min(mapWidth - 1, center.x + radius);
        int minY = Math.max(0, center.y - radius);
        int maxY = Math.min(mapHeight - 1, center.y + radius);

        int count = 0;

        // Iterate bounding box
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                MapLocation loc = new MapLocation(x, y);
                if (withinRange(center, loc, radiusSquared)) {
                    buffer[count++] = loc;
                }
            }
        }

        return count;
    }

    /**
     * Get locations within radius (convenience method using static buffer).
     *
     * WARNING: Results stored in shared static buffer. Copy if needed beyond immediate use.
     * For better performance, use locationsWithinRadiusIntoBuffer() with your own buffer.
     *
     * @param center Center point
     * @param radiusSquared Radius squared
     * @param mapWidth Map width
     * @param mapHeight Map height
     * @return Count of locations (access via getLocationInRadius(index))
     */
    public static int locationsWithinRadiusCount(
        MapLocation center,
        int radiusSquared,
        int mapWidth,
        int mapHeight
    ) {
        return locationsWithinRadiusIntoBuffer(
            radiusBuffer, center, radiusSquared, mapWidth, mapHeight
        );
    }

    /**
     * Access location at index from static buffer.
     * Only valid after calling locationsWithinRadiusCount().
     *
     * @param index Index of location (0 to count-1)
     * @return MapLocation at index
     */
    public static MapLocation getLocationInRadius(int index) {
        return radiusBuffer[index];
    }

    /**
     * Get all locations within radius (DEPRECATED - allocates memory).
     * Use locationsWithinRadiusCount() + getLocationInRadius() for better performance.
     *
     * @param center Center point
     * @param radiusSquared Radius squared
     * @param mapWidth Map width
     * @param mapHeight Map height
     * @return Array of locations within radius
     * @deprecated Use locationsWithinRadiusCount() + getLocationInRadius() for zero-allocation
     */
    @Deprecated
    public static MapLocation[] locationsWithinRadius(
        MapLocation center,
        int radiusSquared,
        int mapWidth,
        int mapHeight
    ) {
        int count = locationsWithinRadiusCount(center, radiusSquared, mapWidth, mapHeight);

        // Copy from static buffer
        MapLocation[] result = new MapLocation[count];
        System.arraycopy(radiusBuffer, 0, result, 0, count);
        return result;
    }

    /**
     * Fast approximate distance (avoids sqrt).
     * Octagonal approximation: max + 0.414*min
     * Accurate within ~8%.
     *
     * @param dx Delta X
     * @param dy Delta Y
     * @return Approximate distance
     */
    public static int approximateDistance(int dx, int dy) {
        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);
        int max = Math.max(absDx, absDy);
        int min = Math.min(absDx, absDy);

        // Approximation: max + min/2 (shift for speed)
        // More accurate: max + min * 0.414 ≈ max + (min * 13) >> 5
        return max + ((min * 13) >> 5);
    }

    /**
     * Check if path from A to B is clear (no obstacles).
     * Simple ray-casting for line-of-sight.
     *
     * @param from Start location
     * @param to End location
     * @param passable Passability map
     * @return true if clear line of sight
     */
    public static boolean isLineOfSightClear(
        MapLocation from,
        MapLocation to,
        boolean[][] passable
    ) {
        // Bresenham's line algorithm
        int dx = Math.abs(to.x - from.x);
        int dy = Math.abs(to.y - from.y);
        int sx = from.x < to.x ? 1 : -1;
        int sy = from.y < to.y ? 1 : -1;
        int err = dx - dy;

        int x = from.x;
        int y = from.y;

        while (true) {
            // Check passability
            if (!passable[x][y]) return false;

            // Reached target
            if (x == to.x && y == to.y) return true;

            // Step
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
}

