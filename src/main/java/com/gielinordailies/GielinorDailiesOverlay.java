package com.gielinordailies;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class GielinorDailiesOverlay extends OverlayPanel
{
    // ── OSRS-style colours ──
    private static final Color BG_NORMAL       = new Color(89, 72, 49, 200);
    private static final Color BG_CELEBRATE    = new Color(35, 80, 35, 220);
    private static final Color BG_ALL_DONE     = new Color(100, 85, 20, 220);

    private static final Color BORDER_NORMAL   = new Color(110, 85, 45);
    private static final Color BORDER_CELEBRATE = new Color(70, 160, 70);
    private static final Color BORDER_ALL_DONE = new Color(200, 170, 40);

    private static final Color TITLE_COLOR     = new Color(255, 215, 100);
    private static final Color COLOR_COMPLETED = new Color(100, 180, 100);
    private static final Color COLOR_HIGH      = new Color(220, 100, 100);
    private static final Color COLOR_MEDIUM    = new Color(220, 180, 80);
    private static final Color COLOR_LOW       = new Color(150, 150, 150);
    private static final Color TEXT_WHITE      = new Color(230, 225, 215);
    private static final Color TEXT_MUTED      = new Color(160, 155, 145);

    private static final long CELEBRATE_DURATION_MS = 4000;
    private static final long ALL_DONE_DURATION_MS  = 6000;

    private static class CelebrationEntry
    {
        final String taskName;
        final long startTime;
        CelebrationEntry(String taskName, long startTime)
        {
            this.taskName = taskName;
            this.startTime = startTime;
        }
    }

    private final CopyOnWriteArrayList<CelebrationEntry> celebrations = new CopyOnWriteArrayList<>();
    private volatile boolean allTasksDone = false;
    private volatile long allDoneStartTime = 0;

    private final GielinorDailiesConfig config;
    private volatile List<GielinorDailiesApiClient.GielinorDailiesTask> tasks;
    private volatile boolean connected = false;

    @Inject
    public GielinorDailiesOverlay(GielinorDailiesConfig config)
    {
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    public void setTasks(List<GielinorDailiesApiClient.GielinorDailiesTask> tasks)   { this.tasks = tasks; }
    public void setConnected(boolean connected)                          { this.connected = connected; }

    public void celebrate(String taskName)
    {
        celebrations.add(new CelebrationEntry(taskName, System.currentTimeMillis()));
    }

    public void celebrateAllDone()
    {
        this.allTasksDone = true;
        this.allDoneStartTime = System.currentTimeMillis();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        long now = System.currentTimeMillis();

        // ── "All tasks done!" celebration ──
        if (allTasksDone && (now - allDoneStartTime) < ALL_DONE_DURATION_MS)
        {
            float progress = (float) (now - allDoneStartTime) / ALL_DONE_DURATION_MS;
            int alpha = fadeAlpha(progress, 0.7f, 220);

            panelComponent.setBackgroundColor(withAlpha(BG_ALL_DONE, alpha));

            panelComponent.getChildren().add(TitleComponent.builder()
                .text("\u2605 Gielinor Dailies \u2605")
                .color(withAlpha(new Color(255, 215, 0), alpha))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("All tasks complete!")
                .leftColor(withAlpha(new Color(255, 255, 200), alpha))
                .build());

            panelComponent.getChildren().add(LineComponent.builder()
                .left("\u2714 Great work today!")
                .leftColor(withAlpha(new Color(255, 215, 0), alpha))
                .build());

            Dimension dim = super.render(graphics);
            drawBorder(graphics, dim, withAlpha(BORDER_ALL_DONE, alpha));
            return dim;
        }
        else if (allTasksDone)
        {
            allTasksDone = false;
        }

        // ── Stacked celebrations ──
        celebrations.removeIf(entry -> (now - entry.startTime) >= CELEBRATE_DURATION_MS);

        if (!celebrations.isEmpty())
        {
            long newestStart = 0;
            for (CelebrationEntry e : celebrations)
                if (e.startTime > newestStart) newestStart = e.startTime;
            float bgProgress = (float) (now - newestStart) / CELEBRATE_DURATION_MS;
            int bgAlpha = fadeAlpha(bgProgress, 0.6f, 220);

            panelComponent.setBackgroundColor(withAlpha(BG_CELEBRATE, bgAlpha));

            panelComponent.getChildren().add(TitleComponent.builder()
                .text("\u2714 Task Complete!")
                .color(withAlpha(COLOR_COMPLETED, bgAlpha))
                .build());

            for (CelebrationEntry entry : celebrations)
            {
                float ep = (float) (now - entry.startTime) / CELEBRATE_DURATION_MS;
                int ea = fadeAlpha(ep, 0.6f, 255);

                panelComponent.getChildren().add(LineComponent.builder()
                    .left("\u2714 " + entry.taskName)
                    .leftColor(withAlpha(new Color(200, 255, 200), ea))
                    .build());
            }

            Dimension dim = super.render(graphics);
            drawBorder(graphics, dim, withAlpha(BORDER_CELEBRATE, bgAlpha));
            return dim;
        }

        // ── Normal rendering — always set background so it never disappears ──
        panelComponent.setBackgroundColor(BG_NORMAL);

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("Gielinor Dailies")
            .color(TITLE_COLOR)
            .build());

        if (!connected)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("Not connected")
                .leftColor(COLOR_HIGH)
                .build());

            Dimension dim = super.render(graphics);
            drawBorder(graphics, dim, BORDER_NORMAL);
            return dim;
        }

        if (tasks == null || tasks.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("No tasks today")
                .leftColor(TEXT_MUTED)
                .build());

            Dimension dim = super.render(graphics);
            drawBorder(graphics, dim, BORDER_NORMAL);
            return dim;
        }

        long completed = tasks.stream().filter(t -> t.completed).count();
        boolean allDone = completed == tasks.size();

        // Hide overlay entirely when all tasks are done
        if (allDone)
        {
            return null;
        }

        panelComponent.getChildren().add(LineComponent.builder()
            .left("Progress")
            .right(completed + "/" + tasks.size())
            .leftColor(TEXT_MUTED)
            .rightColor(TEXT_WHITE)
            .build());

        for (GielinorDailiesApiClient.GielinorDailiesTask task : tasks)
        {
            if (!task.completed)
            {
                String prefix = getTypePrefix(task.taskType);
                panelComponent.getChildren().add(LineComponent.builder()
                    .left(prefix + task.title)
                    .leftColor(getPriorityColor(task.priority))
                    .build());
            }
        }

        if (completed > 0)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                .left("\u2714 " + completed + " done")
                .leftColor(COLOR_COMPLETED)
                .build());
        }

        Dimension dim = super.render(graphics);
        drawBorder(graphics, dim, BORDER_NORMAL);
        return dim;
    }

    // ── Helpers ──

    /**
     * Draws an OSRS-style double border around the already-rendered panel.
     */
    private void drawBorder(Graphics2D g, Dimension dim, Color borderColor)
    {
        if (dim == null) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = dim.width;
        int h = dim.height;

        // Outer gold/bronze border
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(borderColor);
        g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

        // Inner darker border (inset by 2px)
        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(
            Math.max(0, borderColor.getRed() - 50),
            Math.max(0, borderColor.getGreen() - 40),
            Math.max(0, borderColor.getBlue() - 20),
            borderColor.getAlpha()
        ));
        g2.drawRoundRect(2, 2, w - 5, h - 5, 4, 4);

        g2.dispose();
    }

    private static int fadeAlpha(float progress, float fadeStart, int maxAlpha)
    {
        if (progress < fadeStart) return maxAlpha;
        float fade = (progress - fadeStart) / (1.0f - fadeStart);
        return Math.max(0, Math.min(maxAlpha, (int) (maxAlpha * (1.0f - fade))));
    }

    private static Color withAlpha(Color c, int alpha)
    {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private Color getPriorityColor(String priority)
    {
        switch (priority)
        {
            case "high":   return COLOR_HIGH;
            case "medium": return COLOR_MEDIUM;
            default:       return COLOR_LOW;
        }
    }

    private String getTypePrefix(String type)
    {
        switch (type)
        {
            case "daily":  return "[D] ";
            case "weekly": return "[W] ";
            default:       return "";
        }
    }
}
