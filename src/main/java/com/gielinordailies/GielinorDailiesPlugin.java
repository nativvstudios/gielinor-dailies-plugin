package com.gielinordailies;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
    name = "Gielinor Dailies",
    description = "Syncs your OSRS stats, XP gains, and boss kills with Gielinor Dailies for task tracking and planning",
    tags = {"planner", "tasks", "xp", "tracker", "stats", "boss"}
)
public class GielinorDailiesPlugin extends Plugin
{
    // Boss kill chat patterns
    private static final Pattern KC_PATTERN = Pattern.compile("Your (.+) kill count is: (\\d+)");
    private static final Pattern PERSONAL_BEST_PATTERN = Pattern.compile("Your (.+) personal best is");

    @Inject
    private Client client;

    @Inject
    private GielinorDailiesConfig config;

    @Inject
    private GielinorDailiesApiClient apiClient;

    @Inject
    private StatsTracker statsTracker;

    @Inject
    private QuestTracker questTracker;

    @Inject
    private GielinorDailiesOverlay overlay;

    @Inject
    private SoundPlayer soundPlayer;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ScheduledExecutorService executor;

    private GielinorDailiesPanel panel;
    private NavigationButton navButton;

    private boolean connected = false;
    private int matchedCharacterId = -1;
    private int ticksSinceLastPush = 0;
    private int ticksSinceLastTaskFetch = 0;
    private int ticksSinceLastQuestCheck = 0;
    private int ticksSinceLastAnnouncementFetch = 0;
    private boolean loggedIn = false;
    private boolean questPushPending = false;
    private boolean initialTaskFetchDone = false;
    private String lastTaskHash = "";
    private final Set<Integer> previouslyCompletedIds = new HashSet<>();

    @Override
    protected void startUp()
    {
        overlayManager.add(overlay);

        // Create the sidebar panel
        panel = new GielinorDailiesPanel();
        panel.init(apiClient, executor, this::fetchTasksAsync);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");

        navButton = NavigationButton.builder()
            .tooltip("Gielinor Dailies")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);

        log.info("Gielinor Dailies plugin started");
    }

    @Override
    protected void shutDown()
    {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        soundPlayer.shutdown();
        statsTracker.reset();
        questTracker.reset();
        connected = false;
        loggedIn = false;
        initialTaskFetchDone = false;
        previouslyCompletedIds.clear();
        matchedCharacterId = -1;
        log.info("Gielinor Dailies plugin stopped");
    }

    @Provides
    GielinorDailiesConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(GielinorDailiesConfig.class);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN && !loggedIn)
        {
            loggedIn = true;
            // Delay initialization slightly so client data is available
            ticksSinceLastPush = 0;
            ticksSinceLastTaskFetch = 0;
        }
        else if (event.getGameState() == GameState.LOGIN_SCREEN)
        {
            if (loggedIn)
            {
                // Push any remaining data before logout
                pushStatsAsync();
            }
            loggedIn = false;
            connected = false;
            matchedCharacterId = -1;
            initialTaskFetchDone = false;
            previouslyCompletedIds.clear();
            statsTracker.reset();
            questTracker.reset();
            overlay.setConnected(false);
            panel.setConnected(false);
        }
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!loggedIn)
        {
            return;
        }

        // Initialize on first tick after login (client data is ready)
        if (!statsTracker.isInitialized())
        {
            initializeAfterLogin();
            return;
        }

        // If initial quest push is pending, capture on game thread and push async
        if (questPushPending && connected)
        {
            questPushPending = false;
            List<QuestTracker.QuestData> allQuests = questTracker.captureAllQuests();
            int totalQp = questTracker.getTotalQuestPoints();
            if (!allQuests.isEmpty())
            {
                executor.submit(() -> {
                    try
                    {
                        boolean success = apiClient.pushQuests(matchedCharacterId, allQuests, totalQp);
                        log.info("Gielinor Dailies: Pushed {} quest states, {} QP (success={})", allQuests.size(), totalQp, success);
                    }
                    catch (Exception e)
                    {
                        log.warn("Gielinor Dailies: Quest push error", e);
                    }
                });
            }
        }

        ticksSinceLastPush++;
        ticksSinceLastTaskFetch++;
        ticksSinceLastQuestCheck++;
        ticksSinceLastAnnouncementFetch++;

        // Push stats every 60 seconds (~100 ticks)
        if (ticksSinceLastPush >= 100 && statsTracker.isDirty())
        {
            pushStatsAsync();
            pushGainsAsync();
            ticksSinceLastPush = 0;
        }

        // Check for task changes every 120 seconds (~200 ticks)
        if (ticksSinceLastTaskFetch >= 200)
        {
            checkAndFetchTasksAsync();
            ticksSinceLastTaskFetch = 0;
        }

        // Check for quest state changes every ~10 seconds (~17 ticks)
        if (ticksSinceLastQuestCheck >= 17)
        {
            checkQuestChangesAsync();
            ticksSinceLastQuestCheck = 0;
        }

        // Refresh announcements every 5 minutes (~500 ticks)
        if (ticksSinceLastAnnouncementFetch >= 500)
        {
            fetchAnnouncementsAsync();
            ticksSinceLastAnnouncementFetch = 0;
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event)
    {
        if (!loggedIn || !config.pushStats())
        {
            return;
        }

        Skill skill = event.getSkill();
        statsTracker.onStatChanged(skill);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        if (!loggedIn || !config.pushBossKc())
        {
            return;
        }

        if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
        {
            return;
        }

        String message = event.getMessage().replaceAll("<[^>]+>", ""); // strip color tags

        // Boss kill count message
        Matcher kcMatcher = KC_PATTERN.matcher(message);
        if (kcMatcher.find())
        {
            String bossName = kcMatcher.group(1);
            statsTracker.onBossKill(bossName);
            return;
        }
    }

    /**
     * Called once after login when client data is available.
     * Matches RSN to a Gielinor Dailies character and captures baseline XP.
     */
    private void initializeAfterLogin()
    {
        String token = config.apiToken();
        if (token == null || token.isEmpty())
        {
            log.info("Gielinor Dailies: No API token configured");
            overlay.setConnected(false);
            panel.setConnected(false);
            return;
        }

        String rsn = client.getLocalPlayer() != null ? client.getLocalPlayer().getName() : null;
        if (rsn == null || rsn.isEmpty())
        {
            return; // Not ready yet
        }

        log.info("Gielinor Dailies: Logged in as {}, connecting...", rsn);

        // Capture XP baseline immediately
        statsTracker.captureBaseline();

        // Connect and match character in background
        executor.submit(() ->
        {
            try
            {
                List<GielinorDailiesApiClient.GielinorDailiesCharacter> characters = apiClient.getCharacters();
                if (characters.isEmpty())
                {
                    log.warn("Gielinor Dailies: No characters found or connection failed");
                    overlay.setConnected(false);
                    panel.setConnected(false);
                    return;
                }

                // Match RSN to a character (case-insensitive)
                GielinorDailiesApiClient.GielinorDailiesCharacter matched = null;
                for (GielinorDailiesApiClient.GielinorDailiesCharacter c : characters)
                {
                    if (c.name.equalsIgnoreCase(rsn))
                    {
                        matched = c;
                        break;
                    }
                }

                if (matched == null)
                {
                    log.warn("Gielinor Dailies: No character matching RSN '{}' found", rsn);
                    overlay.setConnected(false);
                    panel.setConnected(false);
                    return;
                }

                matchedCharacterId = matched.id;
                connected = true;
                overlay.setConnected(true);
                panel.setConnected(true);
                log.debug("Gielinor Dailies: Matched to character '{}' (id={})", matched.name, matched.id);

                // Do an initial stats push
                if (config.pushStats() && "plugin".equals(matched.dataSource))
                {
                    pushStats();
                }

                // Fetch tasks for overlay
                fetchTasks();

                // Fetch announcements
                fetchAnnouncements();

                // Quest push happens on next game tick (needs game thread for varbit access)
                questPushPending = true;
            }
            catch (Throwable t)
            {
                log.error("Gielinor Dailies: Init failed with {}: {}", t.getClass().getName(), t.getMessage(), t);
                overlay.setConnected(false);
                panel.setConnected(false);
            }
        });
    }

    private void pushStatsAsync()
    {
        if (!connected || matchedCharacterId < 0 || !config.pushStats())
        {
            return;
        }

        executor.submit(this::pushStats);
    }

    private void pushStats()
    {
        try
        {
            Map<String, GielinorDailiesApiClient.SkillData> stats = statsTracker.getCurrentStats();
            Map<String, Integer> kcGains = statsTracker.getKcGains();
            int combatLevel = statsTracker.getCombatLevel();

            boolean success = apiClient.pushStats(matchedCharacterId, stats, kcGains, combatLevel);
            if (success)
            {
                log.debug("Gielinor Dailies: Stats pushed ({} skills, {} bosses)", stats.size(), kcGains.size());
                statsTracker.clearDirty();
            }
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Push stats error", e);
        }
    }

    private void pushGainsAsync()
    {
        if (!connected || matchedCharacterId < 0)
        {
            return;
        }

        executor.submit(() ->
        {
            try
            {
                Map<String, Long> xpGains = statsTracker.getXpGains();
                Map<String, Integer> kcGains = statsTracker.getKcGains();

                if (xpGains.isEmpty() && kcGains.isEmpty())
                {
                    return;
                }

                apiClient.pushGains(matchedCharacterId, xpGains, kcGains);
                log.debug("Gielinor Dailies: Gains pushed ({} skills, {} bosses)", xpGains.size(), kcGains.size());
            }
            catch (Exception e)
            {
                log.warn("Gielinor Dailies: Push gains error", e);
            }
        });
    }

    /**
     * Lightweight check-then-fetch: polls the /api/tasks/check endpoint first.
     * Only does a full fetch if the hash has changed (tasks were added/removed/completed).
     */
    private void checkAndFetchTasksAsync()
    {
        if (!connected)
        {
            return;
        }

        executor.submit(() -> {
            try
            {
                GielinorDailiesApiClient.TaskCheckResult check = apiClient.checkTasks(matchedCharacterId);
                if (check == null)
                {
                    // API error — fall back to full fetch
                    fetchTasks();
                    return;
                }

                if (!check.hash.equals(lastTaskHash))
                {
                    // Something changed — do full fetch
                    log.debug("Gielinor Dailies: Task state changed (hash {} -> {}), fetching", lastTaskHash, check.hash);
                    lastTaskHash = check.hash;
                    fetchTasks();
                }
                else
                {
                    log.debug("Gielinor Dailies: No task changes (hash {})", check.hash);
                }
            }
            catch (Exception e)
            {
                log.warn("Gielinor Dailies: Check tasks error, falling back to full fetch", e);
                fetchTasks();
            }
        });
    }

    private void fetchTasksAsync()
    {
        if (!connected)
        {
            return;
        }

        executor.submit(this::fetchTasks);
    }

    private void fetchTasks()
    {
        try
        {
            List<GielinorDailiesApiClient.GielinorDailiesTask> tasks = apiClient.getTodayTasks(matchedCharacterId);
            overlay.setTasks(tasks);
            panel.updateTasks(tasks);
            log.debug("Gielinor Dailies: Fetched {} tasks", tasks.size());

            // Detect newly completed tasks (skip on first fetch to avoid false positives)
            if (initialTaskFetchDone && !tasks.isEmpty())
            {
                List<String> newlyCompletedNames = new ArrayList<>();

                for (GielinorDailiesApiClient.GielinorDailiesTask task : tasks)
                {
                    if (task.completed && !previouslyCompletedIds.contains(task.id))
                    {
                        newlyCompletedNames.add(task.title);
                    }
                }

                if (!newlyCompletedNames.isEmpty())
                {
                    // Check if ALL tasks are now done
                    boolean allDone = tasks.stream().allMatch(t -> t.completed);
                    if (allDone)
                    {
                        overlay.celebrateAllDone();
                    }
                    else
                    {
                        // Each task gets its own stacked celebration notification
                        for (String name : newlyCompletedNames)
                        {
                            overlay.celebrate(name);
                        }
                    }
                    if (config.playCompletionSound())
                    {
                        soundPlayer.playTaskComplete();
                    }
                }
            }

            // Update the tracked set for next comparison
            previouslyCompletedIds.clear();
            for (GielinorDailiesApiClient.GielinorDailiesTask task : tasks)
            {
                if (task.completed)
                {
                    previouslyCompletedIds.add(task.id);
                }
            }
            initialTaskFetchDone = true;
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Fetch tasks error", e);
        }
    }

    private void fetchAnnouncementsAsync()
    {
        if (!connected)
        {
            return;
        }

        executor.submit(this::fetchAnnouncements);
    }

    private void fetchAnnouncements()
    {
        try
        {
            List<GielinorDailiesApiClient.GielinorDailiesAnnouncement> announcements = apiClient.getAnnouncements();
            log.debug("Gielinor Dailies: Fetched {} announcements", announcements.size());
            panel.updateAnnouncements(announcements);
        }
        catch (Exception e)
        {
            log.warn("Gielinor Dailies: Fetch announcements error", e);
        }
    }

    /**
     * Check for quest state changes and push only the diffs.
     * Called from onGameTick (game thread), so varbit reads are safe.
     */
    private void checkQuestChangesAsync()
    {
        if (!connected || matchedCharacterId < 0)
        {
            return;
        }

        // Capture changed quests on game thread
        List<QuestTracker.QuestData> changed = questTracker.getChangedQuests();
        if (!changed.isEmpty())
        {
            int totalQp = questTracker.getTotalQuestPoints();
            // Push changes on background thread, then immediately refresh tasks
            executor.submit(() ->
            {
                try
                {
                    boolean success = apiClient.pushQuests(matchedCharacterId, changed, totalQp);
                    log.info("Gielinor Dailies: Pushed {} quest changes, {} QP (success={})", changed.size(), totalQp, success);

                    // Immediately fetch tasks so quest-based task completion shows up right away
                    if (success)
                    {
                        fetchTasks();
                        ticksSinceLastTaskFetch = 0;
                    }
                }
                catch (Exception e)
                {
                    log.warn("Gielinor Dailies: Quest check error", e);
                }
            });
        }
    }
}
