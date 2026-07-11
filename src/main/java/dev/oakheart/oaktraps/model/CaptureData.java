package dev.oakheart.oaktraps.model;

/**
 * Immutable snapshot of a captured mob.
 *
 * <p>{@code entityBytes} is the full-fidelity serialized entity (see
 * {@code util.EntityCapture}); the remaining fields are denormalized display
 * stats extracted at capture time so the trap item lore can be rendered without
 * deserializing the blob. {@code version} is an internal schema version so old
 * captures can be migrated rather than becoming unreleasable if the storage
 * format ever changes.
 */
public record CaptureData(
        byte[] entityBytes,
        String typeKey,
        String displayName,
        String variant,
        double health,
        double maxHealth,
        double damage,
        boolean mythic,
        String mythicType,
        int version
) {
    /** Current capture-data schema version. */
    public static final int CURRENT_VERSION = 1;
}
