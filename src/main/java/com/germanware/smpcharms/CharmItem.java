package com.germanware.smpcharms;

public record CharmItem(CharmType type, int level) {
    public boolean isLevel2() {
        return level >= 2;
    }
}
