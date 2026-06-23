package com.germanware.smpcharms;

public enum CharmType {
    STRENGTH("Strength Charm", "Berserker DPS"),
    HEALTH("Health Charm", "Tank"),
    FEATHER("Feather Charm", "Mobility"),
    WEALTH("Wealth Charm", "Economy / Grinder"),
    PHANTOM("Phantom Charm", "Stealth Assassin"),
    LUCK("Luck Charm", "Treasure Hunter"),
    WARDEN("Warden Charm", "Guardian"),
    MINER("Miner Charm", "Deep Miner"),
    OCEAN("Ocean Charm", "Tide Walker"),
    AIR("Air Charm", "Sky Runner"),
    STORAGE("Storage Charm", "Portable Vault");

    private final String displayName;
    private final String role;

    CharmType(String displayName, String role) {
        this.displayName = displayName;
        this.role = role;
    }

    public String displayName() {
        return displayName;
    }

    public String role() {
        return role;
    }
}
