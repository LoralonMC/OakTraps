package dev.oakheart.oaktraps.util;

import dev.oakheart.oaktraps.model.CaptureData;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;

import java.util.Base64;
import java.util.Locale;

/**
 * The single boundary around Paper's entity (de)serialization.
 *
 * <p>{@code Bukkit.getUnsafe()} is flagged internal by Paper, but the
 * {@code serializeEntity}/{@code deserializeEntity} methods themselves are not
 * deprecated-for-removal and are the only API able to round-trip a full entity
 * (equipment, AI, trades, tamed state, …). Isolating every call here means a
 * future Paper change is a one-class migration; {@link CaptureData#version}
 * lets stored captures be migrated rather than orphaned.
 */
public final class EntityCapture {

    private EntityCapture() {
    }

    /** Serialize a live entity to a full-fidelity byte blob. */
    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    public static byte[] serialize(Entity entity) {
        return Bukkit.getUnsafe().serializeEntity(entity);
    }

    /**
     * Recreate an entity from a blob into the world (not yet added — Paper adds
     * it). {@code preserveUUID} is intentionally {@code false} for release:
     * a fresh UUID avoids duplicate-entity hazards; traits are what matter.
     */
    @SuppressWarnings({"UnstableApiUsage", "deprecation"})
    public static Entity deserialize(byte[] bytes, World world, boolean preserveUUID) {
        return Bukkit.getUnsafe().deserializeEntity(bytes, world, preserveUUID);
    }

    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static byte[] fromBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    /**
     * Capture a living entity: serialize it and extract denormalized display
     * stats for the trap item lore. Does <b>not</b> remove the entity — the
     * caller decides when, after firing the (cancellable) capture event.
     */
    public static CaptureData capture(LivingEntity entity, boolean mythic, String mythicType) {
        byte[] bytes = serialize(entity);

        AttributeInstance maxHealthAttr = entity.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : entity.getHealth();

        AttributeInstance damageAttr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        double damage = damageAttr != null ? damageAttr.getValue() : 0.0;

        String displayName = entity.customName() != null
                ? PlainTextComponentSerializer.plainText().serialize(entity.customName())
                : null;

        return new CaptureData(
                bytes,
                entity.getType().getKey().asString(),
                displayName,
                variantOf(entity),
                entity.getHealth(),
                maxHealth,
                damage,
                mythic,
                mythicType,
                CaptureData.CURRENT_VERSION
        );
    }

    /** A human-readable variant for the common variant-bearing mobs, or null. */
    public static String variantOf(LivingEntity entity) {
        if (entity instanceof MushroomCow mooshroom) return nice(mooshroom.getVariant());
        if (entity instanceof Pig pig) return nice(pig.getVariant());
        if (entity instanceof Cow cow) return nice(cow.getVariant());
        if (entity instanceof Chicken chicken) return nice(chicken.getVariant());
        if (entity instanceof Frog frog) return nice(frog.getVariant());
        if (entity instanceof Wolf wolf) return nice(wolf.getVariant());
        if (entity instanceof Axolotl axolotl) return nice(axolotl.getVariant());
        if (entity instanceof Cat cat) return nice(cat.getCatType());
        if (entity instanceof Rabbit rabbit) return nice(rabbit.getRabbitType());
        if (entity instanceof Fox fox) return nice(fox.getFoxType());
        if (entity instanceof Villager villager) return nice(villager.getVillagerType());
        if (entity instanceof Sheep sheep) return nice(sheep.getColor());
        if (entity instanceof Horse horse) return nice(horse.getColor());
        return null;
    }

    private static String nice(Object value) {
        if (value == null) return null;
        String raw;
        if (value instanceof Enum<?> e) {
            raw = e.name();
        } else if (value instanceof Keyed k) {
            raw = k.getKey().getKey();
        } else {
            raw = value.toString();
        }
        return prettify(raw);
    }

    private static String prettify(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String word : raw.toLowerCase(Locale.ROOT).split("[_/]")) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}
