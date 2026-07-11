package dev.oakheart.oaktraps.model;

/**
 * Filter mode for entity / mob lists. {@link #BLACKLIST} denies listed entries
 * and allows everything else; {@link #WHITELIST} allows only listed entries.
 */
public enum ListMode {
    BLACKLIST,
    WHITELIST;

    public static ListMode fromString(String value, ListMode fallback) {
        if (value == null) return fallback;
        try {
            return ListMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * @param inList whether the candidate is present in the configured list
     * @return whether the candidate is permitted under this mode
     */
    public boolean allows(boolean inList) {
        return this == BLACKLIST ? !inList : inList;
    }
}
