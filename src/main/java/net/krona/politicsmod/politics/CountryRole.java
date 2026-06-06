package net.krona.politicsmod.politics;

public enum CountryRole {
    LEADER(3),  // Владелец (Создатель)
    MAYOR(2),   // Мэр (Управляет жителями)
    CITIZEN(1), // Гражданин (Может строить/ломать)
    GUEST(0);   // Чужак / Гость (Ничего не может)

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