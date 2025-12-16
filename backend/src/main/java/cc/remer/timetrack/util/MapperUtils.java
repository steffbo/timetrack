package cc.remer.timetrack.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Utility class for common mapper operations.
 * Centralizes timestamp conversions and audit field mappings.
 */
public final class MapperUtils {

    private MapperUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Convert LocalDateTime to OffsetDateTime in UTC.
     * Returns null if input is null.
     *
     * @param timestamp the local timestamp
     * @return the offset timestamp in UTC, or null
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.atOffset(ZoneOffset.UTC);
    }

    /**
     * Convert LocalDateTime to OffsetDateTime using the specified ZoneOffset.
     * Returns null if input is null.
     *
     * @param timestamp the local timestamp
     * @param zoneOffset the zone offset to use
     * @return the offset timestamp with the specified zone, or null
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime timestamp, ZoneOffset zoneOffset) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.atOffset(zoneOffset);
    }
}
