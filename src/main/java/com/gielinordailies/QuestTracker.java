package com.gielinordailies;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class QuestTracker
{
    // Varp 101 = total quest points
    private static final int QUEST_POINTS_VARP = 101;

    @Inject
    private Client client;

    // Cache last known states to detect changes
    private final Map<String, String> lastKnownStates = new HashMap<>();
    private boolean initialized = false;

    /**
     * Get total quest points from the client (varp 101).
     * Must be called from the game thread.
     */
    public int getTotalQuestPoints()
    {
        try
        {
            return client.getVarpValue(QUEST_POINTS_VARP);
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Failed to read quest points varp", e);
            return 0;
        }
    }

    /**
     * Capture all quest states. Returns the full list of quests with their states.
     */
    public List<QuestData> captureAllQuests()
    {
        List<QuestData> quests = new ArrayList<>();

        for (Quest quest : Quest.values())
        {
            try
            {
                QuestState state = quest.getState(client);
                String stateName = state != null ? state.name() : "NOT_STARTED";

                quests.add(new QuestData(quest.getName(), stateName));
                lastKnownStates.put(quest.getName(), stateName);
            }
            catch (Exception e)
            {
                // Some quests may not be queryable, skip them
            }
        }

        initialized = true;
        log.debug("Gielinor Dailies: Captured {} quest states, total QP={}", quests.size(), getTotalQuestPoints());
        return quests;
    }

    /**
     * Check if any quest states have changed since last capture.
     * Returns only the changed quests, or empty if nothing changed.
     */
    public List<QuestData> getChangedQuests()
    {
        if (!initialized)
        {
            return captureAllQuests();
        }

        List<QuestData> changed = new ArrayList<>();

        for (Quest quest : Quest.values())
        {
            try
            {
                QuestState state = quest.getState(client);
                String stateName = state != null ? state.name() : "NOT_STARTED";
                String previous = lastKnownStates.get(quest.getName());

                if (!stateName.equals(previous))
                {
                    changed.add(new QuestData(quest.getName(), stateName));
                    lastKnownStates.put(quest.getName(), stateName);
                }
            }
            catch (Exception e)
            {
                // Skip
            }
        }

        return changed;
    }

    public boolean isInitialized()
    {
        return initialized;
    }

    public void reset()
    {
        lastKnownStates.clear();
        initialized = false;
    }

    // Data class
    public static class QuestData
    {
        public final String name;
        public final String state;

        public QuestData(String name, String state)
        {
            this.name = name;
            this.state = state;
        }
    }
}
