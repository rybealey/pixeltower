package org.pixeltower.rp.stats;

/**
 * In-memory view of a single {@code rp_player_stats} row. All mutable
 * fields are volatile; setters are package-private because only
 * {@link StatsManager} is supposed to touch them (the DB write-through
 * belongs there).
 *
 * {@code on_duty_corp_id} and {@code is_on_duty} deliberately live on
 * the shift subsystem rather than here — they describe corp shift state,
 * not stats.
 */
public final class PlayerStats {

    private final int habboId;
    private volatile int hp;
    private volatile int maxHp;
    private volatile int energy;
    private volatile int maxEnergy;
    private volatile int level;
    private volatile int xp;
    private volatile int skillPointsUnspent;
    private volatile int skillHit;
    private volatile int skillEndurance;
    private volatile int skillStamina;

    PlayerStats(int habboId,
                int hp, int maxHp,
                int energy, int maxEnergy,
                int level, int xp,
                int skillPointsUnspent,
                int skillHit, int skillEndurance, int skillStamina) {
        this.habboId = habboId;
        this.hp = hp;
        this.maxHp = maxHp;
        this.energy = energy;
        this.maxEnergy = maxEnergy;
        this.level = level;
        this.xp = xp;
        this.skillPointsUnspent = skillPointsUnspent;
        this.skillHit = skillHit;
        this.skillEndurance = skillEndurance;
        this.skillStamina = skillStamina;
    }

    public int getHabboId() { return this.habboId; }
    public int getHp() { return this.hp; }
    public int getMaxHp() { return this.maxHp; }
    public int getEnergy() { return this.energy; }
    public int getMaxEnergy() { return this.maxEnergy; }
    public int getLevel() { return this.level; }
    public int getXp() { return this.xp; }
    public int getSkillPointsUnspent() { return this.skillPointsUnspent; }
    public int getSkillHit() { return this.skillHit; }
    public int getSkillEndurance() { return this.skillEndurance; }
    public int getSkillStamina() { return this.skillStamina; }

    void setHp(int hp) { this.hp = hp; }
    void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    void setEnergy(int energy) { this.energy = energy; }
    void setMaxEnergy(int maxEnergy) { this.maxEnergy = maxEnergy; }
    void setLevel(int level) { this.level = level; }
    void setXp(int xp) { this.xp = xp; }
    void setSkillPointsUnspent(int skillPointsUnspent) { this.skillPointsUnspent = skillPointsUnspent; }
    void setSkillHit(int skillHit) { this.skillHit = skillHit; }
    void setSkillEndurance(int skillEndurance) { this.skillEndurance = skillEndurance; }
    void setSkillStamina(int skillStamina) { this.skillStamina = skillStamina; }
}
