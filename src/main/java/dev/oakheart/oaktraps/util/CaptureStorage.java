package dev.oakheart.oaktraps.util;

import dev.oakheart.oaktraps.model.CaptureData;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * The one place CaptureData is read from / written to a PersistentDataContainer.
 *
 * <p>PDC is never copied automatically between holders, so every transfer
 * (trap item ↔ barrel TileState ↔ released entity) routes through here instead
 * of duplicating key handling at each call site. Works for both item-meta and
 * block (TileState) containers — the caller is responsible for committing
 * (e.g. {@code item.setItemMeta(...)} / {@code tileState.update()}).
 */
public final class CaptureStorage {

    private final NamespacedKey trapKey;
    private final NamespacedKey filledKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey entityKey;
    private final NamespacedKey typeKey;
    private final NamespacedKey nameKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey healthKey;
    private final NamespacedKey maxHealthKey;
    private final NamespacedKey damageKey;
    private final NamespacedKey mythicKey;
    private final NamespacedKey mythicTypeKey;

    public CaptureStorage(Plugin plugin) {
        this.trapKey = new NamespacedKey(plugin, "trap");
        this.filledKey = new NamespacedKey(plugin, "filled");
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.versionKey = new NamespacedKey(plugin, "capture_version");
        this.entityKey = new NamespacedKey(plugin, "entity");
        this.typeKey = new NamespacedKey(plugin, "type");
        this.nameKey = new NamespacedKey(plugin, "name");
        this.variantKey = new NamespacedKey(plugin, "variant");
        this.healthKey = new NamespacedKey(plugin, "health");
        this.maxHealthKey = new NamespacedKey(plugin, "max_health");
        this.damageKey = new NamespacedKey(plugin, "damage");
        this.mythicKey = new NamespacedKey(plugin, "mythic");
        this.mythicTypeKey = new NamespacedKey(plugin, "mythic_type");
    }

    // --- Identity ---

    /** Mark a container as an OakTraps trap. */
    public void markTrap(PersistentDataContainer pdc, boolean filled) {
        pdc.set(trapKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(filledKey, PersistentDataType.BYTE, (byte) (filled ? 1 : 0));
    }

    public boolean isTrap(PersistentDataContainer pdc) {
        return pdc.has(trapKey, PersistentDataType.BYTE);
    }

    public boolean isFilled(PersistentDataContainer pdc) {
        return pdc.getOrDefault(filledKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    /** True if the container actually carries a captured mob (single source of truth). */
    public boolean hasCapture(PersistentDataContainer pdc) {
        return pdc.has(entityKey, PersistentDataType.STRING);
    }

    public void setFilled(PersistentDataContainer pdc, boolean filled) {
        pdc.set(filledKey, PersistentDataType.BYTE, (byte) (filled ? 1 : 0));
    }

    public void setOwner(PersistentDataContainer pdc, UUID owner) {
        if (owner != null) {
            pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        }
    }

    public UUID getOwner(PersistentDataContainer pdc) {
        String raw = pdc.get(ownerKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // --- Capture payload ---

    /** Write a captured mob's data and flip the container to filled. */
    public void writeCapture(PersistentDataContainer pdc, CaptureData data) {
        pdc.set(versionKey, PersistentDataType.INTEGER, data.version());
        pdc.set(entityKey, PersistentDataType.STRING, EntityCapture.toBase64(data.entityBytes()));
        pdc.set(typeKey, PersistentDataType.STRING, data.typeKey());
        if (data.displayName() != null) {
            pdc.set(nameKey, PersistentDataType.STRING, data.displayName());
        } else {
            pdc.remove(nameKey);
        }
        if (data.variant() != null) {
            pdc.set(variantKey, PersistentDataType.STRING, data.variant());
        } else {
            pdc.remove(variantKey);
        }
        pdc.set(healthKey, PersistentDataType.DOUBLE, data.health());
        pdc.set(maxHealthKey, PersistentDataType.DOUBLE, data.maxHealth());
        pdc.set(damageKey, PersistentDataType.DOUBLE, data.damage());
        pdc.set(mythicKey, PersistentDataType.BYTE, (byte) (data.mythic() ? 1 : 0));
        if (data.mythicType() != null) {
            pdc.set(mythicTypeKey, PersistentDataType.STRING, data.mythicType());
        } else {
            pdc.remove(mythicTypeKey);
        }
        setFilled(pdc, true);
    }

    /** Read capture data, or {@code null} if the container holds no capture. */
    public CaptureData readCapture(PersistentDataContainer pdc) {
        String base64 = pdc.get(entityKey, PersistentDataType.STRING);
        if (base64 == null) return null;
        byte[] bytes;
        try {
            bytes = EntityCapture.fromBase64(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new CaptureData(
                bytes,
                pdc.getOrDefault(typeKey, PersistentDataType.STRING, "minecraft:pig"),
                pdc.get(nameKey, PersistentDataType.STRING),
                pdc.get(variantKey, PersistentDataType.STRING),
                pdc.getOrDefault(healthKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(maxHealthKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(damageKey, PersistentDataType.DOUBLE, 0.0),
                pdc.getOrDefault(mythicKey, PersistentDataType.BYTE, (byte) 0) == 1,
                pdc.get(mythicTypeKey, PersistentDataType.STRING),
                pdc.getOrDefault(versionKey, PersistentDataType.INTEGER, CaptureData.CURRENT_VERSION)
        );
    }

    /** Remove the capture payload (keeps the trap marker) and flip to empty. */
    public void clearCapture(PersistentDataContainer pdc) {
        pdc.remove(entityKey);
        pdc.remove(typeKey);
        pdc.remove(nameKey);
        pdc.remove(variantKey);
        pdc.remove(healthKey);
        pdc.remove(maxHealthKey);
        pdc.remove(damageKey);
        pdc.remove(mythicKey);
        pdc.remove(mythicTypeKey);
        pdc.remove(versionKey);
        setFilled(pdc, false);
    }
}
