package com.gielinordailies;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("gielinordailies")
public interface GielinorDailiesConfig extends Config
{
    @ConfigSection(
        name = "Connection",
        description = "Gielinor Dailies server connection settings",
        position = 0
    )
    String connectionSection = "connection";

    @ConfigItem(
        keyName = "apiUrl",
        name = "Site URL",
        description = "Your Gielinor Dailies site URL (e.g. https://gielinordailies.com or your domain)",
        section = connectionSection,
        position = 0
    )
    default String apiUrl()
    {
        return "https://gielinordailies.com";
    }

    @ConfigItem(
        keyName = "apiToken",
        name = "API Token",
        description = "Your Gielinor Dailies API token (generate in Settings > RuneLite Plugin)",
        secret = true,
        section = connectionSection,
        position = 1
    )
    default String apiToken()
    {
        return "";
    }

    @ConfigSection(
        name = "Tracking",
        description = "What to track and push to Gielinor Dailies",
        position = 1
    )
    String trackingSection = "tracking";

    @ConfigItem(
        keyName = "pushStats",
        name = "Push Stats",
        description = "Push skill levels and XP to Gielinor Dailies on login and level-up",
        section = trackingSection,
        position = 0
    )
    default boolean pushStats()
    {
        return true;
    }

    @ConfigItem(
        keyName = "pushBossKc",
        name = "Push Boss KC",
        description = "Push boss kill counts when a kill is detected",
        section = trackingSection,
        position = 1
    )
    default boolean pushBossKc()
    {
        return true;
    }

    @ConfigItem(
        keyName = "pushIntervalSeconds",
        name = "Push Interval (seconds)",
        description = "How often to push accumulated stats (minimum 30s)",
        section = trackingSection,
        position = 2
    )
    default int pushIntervalSeconds()
    {
        return 60;
    }

    @ConfigSection(
        name = "Overlay",
        description = "In-game task overlay settings",
        position = 2
    )
    String overlaySection = "overlay";

    @ConfigItem(
        keyName = "showOverlay",
        name = "Show Task Overlay",
        description = "Display your Gielinor Dailies tasks as an in-game overlay",
        section = overlaySection,
        position = 0
    )
    default boolean showOverlay()
    {
        return true;
    }

    @ConfigItem(
        keyName = "taskRefreshSeconds",
        name = "Task Refresh (seconds)",
        description = "How often to refresh tasks from Gielinor Dailies (minimum 60s)",
        section = overlaySection,
        position = 1
    )
    default int taskRefreshSeconds()
    {
        return 120;
    }

    @ConfigItem(
        keyName = "playCompletionSound",
        name = "Task Completion Sound",
        description = "Play a sound when a task is completed",
        section = overlaySection,
        position = 2
    )
    default boolean playCompletionSound()
    {
        return true;
    }
}
