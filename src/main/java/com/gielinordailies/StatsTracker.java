package com.gielinordailies;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Skill;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Tracks XP gains and boss kills during a session.
 * Accumulates deltas since login for pushing to Gielinor Dailies.
 */
@Slf4j
@Singleton
public class StatsTracker
{
    private final Client client;

    // Baseline XP at login (or last push)
    private final EnumMap<Skill, Long> baselineXp = new EnumMap<>(Skill.class);

    // Accumulated XP gains this session
    private final EnumMap<Skill, Long> sessionXpGains = new EnumMap<>(Skill.class);

    // Boss KC gains this session (boss name -> kill count delta)
    private final Map<String, Integer> sessionKcGains = new HashMap<>();

    // Track whether we have a baseline
    private boolean initialized = false;

    // Dirty flag — set when new gains are detected
    private boolean dirty = false;

    @Inject
    public StatsTracker(Client client)
    {
        this.client = client;
    }

    /**
     * Capture baseline XP for all skills. Call on login.
     */
    public void captureBaseline()
    {
        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }
            baselineXp.put(skill, (long) client.getSkillExperience(skill));
        }
        sessionXpGains.clear();
        sessionKcGains.clear();
        initialized = true;
        dirty = false;
        log.debug("Gielinor Dailies: Captured XP baseline for {} skills", baselineXp.size());
    }

    /**
     * Called when a stat changes. Calculates the delta from baseline.
     */
    public void onStatChanged(Skill skill)
    {
        if (!initialized || skill == Skill.OVERALL)
        {
            return;
        }

        long currentXp = client.getSkillExperience(skill);
        Long baseline = baselineXp.get(skill);
        if (baseline == null)
        {
            baselineXp.put(skill, currentXp);
            return;
        }

        long gained = currentXp - baseline;
        if (gained > 0)
        {
            sessionXpGains.put(skill, gained);
            dirty = true;
        }
    }

    /**
     * Record a boss kill.
     */
    public void onBossKill(String bossName)
    {
        sessionKcGains.merge(bossName, 1, Integer::sum);
        dirty = true;
        log.debug("Gielinor Dailies: Boss kill recorded — {} (total this session: {})", bossName, sessionKcGains.get(bossName));
    }

    /**
     * Get current skill levels and XP for a full stats push.
     */
    public Map<String, GielinorDailiesApiClient.SkillData> getCurrentStats()
    {
        Map<String, GielinorDailiesApiClient.SkillData> stats = new HashMap<>();

        for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL)
            {
                continue;
            }

            String name = formatSkillName(skill);
            int level = client.getRealSkillLevel(skill);
            long xp = client.getSkillExperience(skill);

            stats.put(name, new GielinorDailiesApiClient.SkillData(level, xp, -1));
        }

        return stats;
    }

    /**
     * Get XP gains accumulated this session.
     */
    public Map<String, Long> getXpGains()
    {
        Map<String, Long> gains = new HashMap<>();
        for (Map.Entry<Skill, Long> entry : sessionXpGains.entrySet())
        {
            if (entry.getValue() > 0)
            {
                gains.put(formatSkillName(entry.getKey()), entry.getValue());
            }
        }
        return gains;
    }

    /**
     * Get boss KC gains this session.
     */
    public Map<String, Integer> getKcGains()
    {
        return new HashMap<>(sessionKcGains);
    }

    /**
     * Get the player's combat level.
     */
    public int getCombatLevel()
    {
        return client.getLocalPlayer() != null ? client.getLocalPlayer().getCombatLevel() : 0;
    }

    public boolean isDirty()
    {
        return dirty;
    }

    public void clearDirty()
    {
        dirty = false;
    }

    public boolean isInitialized()
    {
        return initialized;
    }

    public void reset()
    {
        baselineXp.clear();
        sessionXpGains.clear();
        sessionKcGains.clear();
        initialized = false;
        dirty = false;
    }

    private String formatSkillName(Skill skill)
    {
        // RuneLite uses UPPERCASE enum names, Gielinor Dailies expects Title Case
        String name = skill.getName();
        if (name == null || name.isEmpty())
        {
            return skill.name().charAt(0) + skill.name().substring(1).toLowerCase();
        }
        return name;
    }
}
