package net.krona.politicsmod.politics;

public enum CountryRole {
    LEADER(3),  // Founder
    MAYOR(2),   // Manages citizens
    CITIZEN(1), // Can build and break
    GUEST(0);   // Outsider, no permissions

    private final int level;

    CountryRole(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean canManageCitizens() {
        return this.level >= MAYOR.getLevel();
    }

    public boolean canBuild() {
        return this.level >= CITIZEN.getLevel();
    }
}