package com.gielinordailies;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Slf4j
public class GielinorDailiesPanel extends PluginPanel
{
    private static final Color COLOR_BG = new Color(30, 30, 30);
    private static final Color COLOR_CARD = new Color(43, 43, 43);
    private static final Color COLOR_CARD_HOVER = new Color(53, 53, 53);
    private static final Color COLOR_CARD_COMPLETED = new Color(35, 45, 35);
    private static final Color COLOR_TITLE = new Color(210, 175, 125);
    private static final Color COLOR_ACCENT = new Color(220, 155, 90);
    private static final Color COLOR_TEXT = new Color(200, 200, 200);
    private static final Color COLOR_MUTED = new Color(130, 130, 130);
    private static final Color COLOR_DONE = new Color(100, 180, 100);
    private static final Color COLOR_HIGH = new Color(220, 100, 100);
    private static final Color COLOR_MEDIUM = new Color(220, 180, 80);
    private static final Color COLOR_LOW = new Color(150, 150, 150);
    private static final Color COLOR_PROGRESS_BG = new Color(60, 60, 60);
    private static final Color COLOR_PROGRESS_FILL = new Color(100, 180, 100);
    private static final Color COLOR_BTN_BG = new Color(55, 55, 55);
    private static final Color COLOR_BTN_HOVER = new Color(70, 70, 70);
    private static final Color COLOR_ANN_INFO = new Color(100, 160, 220);
    private static final Color COLOR_ANN_WARNING = new Color(220, 180, 80);
    private static final Color COLOR_ANN_UPDATE = new Color(100, 180, 100);
    private static final Color COLOR_ANN_EVENT = new Color(220, 155, 90);

    private static final int MAX_W = Integer.MAX_VALUE;

    private final JPanel contentPanel;
    private final JLabel statusLabel;
    private final JLabel progressLabel;
    private final ProgressBar progressBar;
    private final JPanel announcementsPanel;

    private List<GielinorDailiesApiClient.GielinorDailiesTask> currentTasks = new ArrayList<>();
    private List<GielinorDailiesApiClient.GielinorDailiesAnnouncement> currentAnnouncements = new ArrayList<>();
    private boolean completedCollapsed = true;
    private ExecutorService executor;
    private GielinorDailiesApiClient apiClient;
    private Runnable onRefreshRequest;

    public GielinorDailiesPanel()
    {
        super(false); // false = no wrapping, we handle our own layout
        setBackground(COLOR_BG);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(0, 0, 0, 0));

        // ── Everything in one vertical scroll ──
        contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(COLOR_BG);
        contentPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Title row
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(COLOR_BG);
        titleRow.setMaximumSize(new Dimension(MAX_W, 28));
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel("Gielinor Dailies");
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
        titleLabel.setForeground(COLOR_TITLE);
        titleRow.add(titleLabel, BorderLayout.WEST);

        JButton refreshBtn = new JButton("\u21BB");
        refreshBtn.setToolTipText("Refresh tasks");
        refreshBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        refreshBtn.setForeground(COLOR_ACCENT);
        refreshBtn.setBackground(COLOR_CARD);
        refreshBtn.setBorderPainted(false);
        refreshBtn.setFocusPainted(false);
        refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshBtn.setPreferredSize(new Dimension(24, 24));
        refreshBtn.setMargin(new Insets(0, 0, 0, 0));
        refreshBtn.addActionListener(e -> {
            if (onRefreshRequest != null) onRefreshRequest.run();
        });
        titleRow.add(refreshBtn, BorderLayout.EAST);
        contentPanel.add(titleRow);

        contentPanel.add(Box.createVerticalStrut(4));

        // Status
        statusLabel = new JLabel("Connecting...");
        statusLabel.setFont(FontManager.getRunescapeSmallFont());
        statusLabel.setForeground(COLOR_MUTED);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(statusLabel);

        contentPanel.add(Box.createVerticalStrut(6));

        // Announcements section (dynamic, initially hidden)
        announcementsPanel = new JPanel();
        announcementsPanel.setLayout(new BoxLayout(announcementsPanel, BoxLayout.Y_AXIS));
        announcementsPanel.setBackground(COLOR_BG);
        announcementsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        announcementsPanel.setVisible(false);
        contentPanel.add(announcementsPanel);

        // Progress label
        progressLabel = new JLabel("0 / 0 tasks done");
        progressLabel.setFont(FontManager.getRunescapeSmallFont());
        progressLabel.setForeground(COLOR_TEXT);
        progressLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(progressLabel);

        contentPanel.add(Box.createVerticalStrut(3));

        // Progress bar
        progressBar = new ProgressBar();
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(progressBar);

        contentPanel.add(Box.createVerticalStrut(8));

        // Divider
        contentPanel.add(createDivider());
        contentPanel.add(Box.createVerticalStrut(6));

        // Task cards get appended here dynamically by rebuildTaskList()

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBackground(COLOR_BG);
        scrollPane.getViewport().setBackground(COLOR_BG);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void init(GielinorDailiesApiClient apiClient, ExecutorService executor, Runnable onRefreshRequest)
    {
        this.apiClient = apiClient;
        this.executor = executor;
        this.onRefreshRequest = onRefreshRequest;
    }

    public void setConnected(boolean connected)
    {
        SwingUtilities.invokeLater(() ->
        {
            statusLabel.setText(connected ? "Connected" : "Not connected");
            statusLabel.setForeground(connected ? COLOR_DONE : COLOR_HIGH);
        });
    }

    public void updateTasks(List<GielinorDailiesApiClient.GielinorDailiesTask> tasks)
    {
        this.currentTasks = tasks != null ? tasks : new ArrayList<>();
        SwingUtilities.invokeLater(this::rebuildTaskList);
    }

    public void updateAnnouncements(List<GielinorDailiesApiClient.GielinorDailiesAnnouncement> announcements)
    {
        this.currentAnnouncements = announcements != null ? announcements : new ArrayList<>();
        SwingUtilities.invokeLater(this::rebuildAnnouncements);
    }

    private void rebuildAnnouncements()
    {
        announcementsPanel.removeAll();

        if (currentAnnouncements.isEmpty())
        {
            announcementsPanel.setVisible(false);
            return;
        }

        for (GielinorDailiesApiClient.GielinorDailiesAnnouncement ann : currentAnnouncements)
        {
            announcementsPanel.add(createAnnouncementCard(ann));
            announcementsPanel.add(Box.createVerticalStrut(4));
        }

        announcementsPanel.setVisible(true);
        announcementsPanel.revalidate();
        announcementsPanel.repaint();
    }

    private JPanel createAnnouncementCard(GielinorDailiesApiClient.GielinorDailiesAnnouncement ann)
    {
        Color accentColor = getAnnouncementColor(ann.type);

        JPanel card = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Left accent border
                g2d.setColor(accentColor);
                g2d.fillRoundRect(0, 0, 3, getHeight(), 2, 2);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(38, 38, 38));
        card.setBorder(new EmptyBorder(5, 10, 5, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Title row: title + dismiss button
        JPanel titleRow = new JPanel(new BorderLayout(4, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(MAX_W, 18));

        String titleText = (ann.pinned ? "\u2709 " : "") + ann.title;
        JLabel titleLabel = new JLabel(titleText);
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(13f));
        titleLabel.setForeground(accentColor);
        titleRow.add(titleLabel, BorderLayout.CENTER);

        JButton dismissBtn = new JButton("\u2715");
        dismissBtn.setToolTipText("Dismiss");
        dismissBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        dismissBtn.setForeground(COLOR_MUTED);
        dismissBtn.setBackground(new Color(38, 38, 38));
        dismissBtn.setBorderPainted(false);
        dismissBtn.setFocusPainted(false);
        dismissBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dismissBtn.setPreferredSize(new Dimension(18, 18));
        dismissBtn.setMargin(new Insets(0, 0, 0, 0));
        dismissBtn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { dismissBtn.setForeground(COLOR_HIGH); }

            @Override
            public void mouseExited(MouseEvent e) { dismissBtn.setForeground(COLOR_MUTED); }
        });
        dismissBtn.addActionListener(e -> dismissAnnouncementAction(ann));
        titleRow.add(dismissBtn, BorderLayout.EAST);

        card.add(titleRow);

        card.add(Box.createVerticalStrut(2));

        // Body text — truncate long messages to 2 lines max
        String bodyText = ann.body.length() > 120 ? ann.body.substring(0, 117) + "..." : ann.body;
        bodyText = bodyText.replace("\n", " ").replace("\r", "");
        JLabel bodyLabel = new JLabel("<html>" + escapeHtml(bodyText) + "</html>");
        bodyLabel.setFont(FontManager.getRunescapeSmallFont());
        bodyLabel.setForeground(COLOR_MUTED);
        bodyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bodyLabel.setMaximumSize(new Dimension(MAX_W, 32));
        card.add(bodyLabel);

        // Type tag
        card.add(Box.createVerticalStrut(2));
        JPanel tagRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tagRow.setOpaque(false);
        tagRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagRow.setMaximumSize(new Dimension(MAX_W, 18));
        addTag(tagRow, ann.type, accentColor);
        card.add(tagRow);

        return card;
    }

    private Color getAnnouncementColor(String type)
    {
        switch (type)
        {
            case "warning": return COLOR_ANN_WARNING;
            case "update": return COLOR_ANN_UPDATE;
            case "event": return COLOR_ANN_EVENT;
            default: return COLOR_ANN_INFO;
        }
    }

    private void dismissAnnouncementAction(GielinorDailiesApiClient.GielinorDailiesAnnouncement ann)
    {
        // Remove from local list immediately
        currentAnnouncements.removeIf(a -> a.id == ann.id);
        SwingUtilities.invokeLater(this::rebuildAnnouncements);

        // Persist dismiss on the server
        if (apiClient != null && executor != null)
        {
            executor.submit(() ->
            {
                try
                {
                    boolean success = apiClient.dismissAnnouncement(ann.id);
                    if (!success)
                    {
                        log.warn("Gielinor Dailies Panel: Failed to dismiss announcement '{}'", ann.title);
                    }
                }
                catch (Exception e)
                {
                    log.warn("Gielinor Dailies Panel: Error dismissing announcement", e);
                }
            });
        }
    }

    private int getCompletedCount()
    {
        return (int) currentTasks.stream().filter(t -> t.completed).count();
    }

    private void rebuildTaskList()
    {
        // Remove all dynamic components (everything after the static header)
        // Static: titleRow, strut, status, strut, announcementsPanel, progressLabel, strut, progressBar, strut, divider, strut = 11
        int staticCount = 11;
        while (contentPanel.getComponentCount() > staticCount)
        {
            contentPanel.remove(staticCount);
        }

        int total = currentTasks.size();
        int completed = getCompletedCount();
        int pending = total - completed;

        // Update progress
        progressLabel.setText(completed + " / " + total + " tasks done");
        boolean allDone = total > 0 && completed == total;
        progressLabel.setForeground(allDone ? new Color(255, 215, 0) : COLOR_TEXT);
        progressBar.setProgress(total > 0 ? (float) completed / total : 0, allDone);

        if (currentTasks.isEmpty())
        {
            JLabel emptyLabel = new JLabel("No tasks for today");
            emptyLabel.setFont(FontManager.getRunescapeSmallFont());
            emptyLabel.setForeground(COLOR_MUTED);
            emptyLabel.setBorder(new EmptyBorder(15, 0, 15, 0));
            emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            contentPanel.add(emptyLabel);
        }
        else
        {
            // Pending tasks
            if (pending > 0)
            {
                for (GielinorDailiesApiClient.GielinorDailiesTask task : currentTasks)
                {
                    if (!task.completed)
                    {
                        contentPanel.add(createTaskCard(task));
                        contentPanel.add(Box.createVerticalStrut(4));
                    }
                }
            }

            // Completed section
            if (completed > 0)
            {
                contentPanel.add(Box.createVerticalStrut(4));

                // Collapsible header
                JPanel header = new JPanel(new BorderLayout());
                header.setBackground(COLOR_BG);
                header.setMaximumSize(new Dimension(MAX_W, 26));
                header.setAlignmentX(Component.LEFT_ALIGNMENT);
                header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                header.setBorder(new EmptyBorder(2, 2, 2, 0));

                JLabel headerLabel = new JLabel((completedCollapsed ? "\u25B6" : "\u25BC") + "  Completed (" + completed + ")");
                headerLabel.setFont(FontManager.getRunescapeSmallFont().deriveFont(14f));
                headerLabel.setForeground(COLOR_MUTED);
                header.add(headerLabel, BorderLayout.WEST);

                JPanel completedCards = new JPanel();
                completedCards.setLayout(new BoxLayout(completedCards, BoxLayout.Y_AXIS));
                completedCards.setBackground(COLOR_BG);
                completedCards.setAlignmentX(Component.LEFT_ALIGNMENT);
                completedCards.setVisible(!completedCollapsed);

                header.addMouseListener(new MouseAdapter()
                {
                    @Override
                    public void mouseClicked(MouseEvent e)
                    {
                        completedCollapsed = !completedCollapsed;
                        completedCards.setVisible(!completedCollapsed);
                        headerLabel.setText((completedCollapsed ? "\u25B6" : "\u25BC") + "  Completed (" + completed + ")");
                        contentPanel.revalidate();
                    }
                });

                contentPanel.add(header);

                for (GielinorDailiesApiClient.GielinorDailiesTask task : currentTasks)
                {
                    if (task.completed)
                    {
                        completedCards.add(Box.createVerticalStrut(4));
                        completedCards.add(createTaskCard(task));
                    }
                }

                contentPanel.add(completedCards);
            }
        }

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createTaskCard(GielinorDailiesApiClient.GielinorDailiesTask task)
    {
        Color normalBg = task.completed ? COLOR_CARD_COMPLETED : COLOR_CARD;
        Color hoverBg = task.completed ? new Color(40, 55, 40) : COLOR_CARD_HOVER;

        // Card: vertical BoxLayout — title row, bottom row (tags + button)
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(normalBg);
        card.setBorder(new EmptyBorder(5, 8, 5, 8));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { card.setBackground(hoverBg); }

            @Override
            public void mouseExited(MouseEvent e) { card.setBackground(normalBg); }
        });

        // Row 1: priority dot + title
        JPanel titleRow = new JPanel();
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Priority dot
        JPanel dot = new JPanel()
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(task.completed ? COLOR_DONE : getPriorityColor(task.priority));
                g2d.fillOval(0, 3, 8, 8);
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(12, 14));
        dot.setMinimumSize(new Dimension(12, 14));
        dot.setMaximumSize(new Dimension(12, 14));
        titleRow.add(dot);

        // Title text
        String displayTitle = task.completed
            ? "<html><s>" + escapeHtml(task.title) + "</s></html>"
            : "<html>" + escapeHtml(task.title) + "</html>";
        JLabel titleLabel = new JLabel(displayTitle);
        titleLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        titleLabel.setForeground(task.completed ? COLOR_MUTED : COLOR_TEXT);
        titleLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        titleRow.add(titleLabel);

        card.add(titleRow);
        card.add(Box.createVerticalStrut(3));

        // Row 2: tags (left) + action button (right)
        JPanel bottomRow = new JPanel(new BorderLayout(4, 0));
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomRow.setMaximumSize(new Dimension(MAX_W, 22));

        JPanel tagsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        tagsRow.setOpaque(false);
        addTag(tagsRow, getTypeLabel(task.taskType), getTypeColor(task.taskType));
        addTag(tagsRow, task.priority, getPriorityColor(task.priority));
        bottomRow.add(tagsRow, BorderLayout.CENTER);

        JButton actionBtn;
        if (task.completed)
        {
            actionBtn = createActionButton("Undo", COLOR_ACCENT, e -> uncompleteTaskAction(task));
        }
        else
        {
            actionBtn = createActionButton("Done \u2713", COLOR_DONE, e -> completeTaskAction(task));
        }
        actionBtn.setPreferredSize(new Dimension(task.completed ? 46 : 52, 20));
        actionBtn.setMaximumSize(new Dimension(task.completed ? 46 : 52, 20));
        bottomRow.add(actionBtn, BorderLayout.EAST);

        card.add(bottomRow);

        return card;
    }

    private JButton createActionButton(String text, Color fg, java.awt.event.ActionListener action)
    {
        JButton btn = new JButton(text);
        btn.setFont(FontManager.getRunescapeBoldFont().deriveFont(14f));
        btn.setForeground(fg);
        btn.setBackground(COLOR_BTN_BG);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMargin(new Insets(2, 4, 2, 4));

        btn.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent e) { btn.setBackground(COLOR_BTN_HOVER); }

            @Override
            public void mouseExited(MouseEvent e) { btn.setBackground(COLOR_BTN_BG); }
        });

        btn.addActionListener(action);
        return btn;
    }

    private void completeTaskAction(GielinorDailiesApiClient.GielinorDailiesTask task)
    {
        if (apiClient == null || executor == null) return;

        executor.submit(() ->
        {
            try
            {
                boolean success = apiClient.completeTask(task.id);
                if (success)
                {
                    log.info("Gielinor Dailies Panel: Completed task '{}'", task.title);
                    if (onRefreshRequest != null) onRefreshRequest.run();
                }
                else
                {
                    log.warn("Gielinor Dailies Panel: Failed to complete task '{}'", task.title);
                }
            }
            catch (Exception e)
            {
                log.warn("Gielinor Dailies Panel: Error completing task", e);
            }
        });
    }

    private void uncompleteTaskAction(GielinorDailiesApiClient.GielinorDailiesTask task)
    {
        if (apiClient == null || executor == null) return;

        executor.submit(() ->
        {
            try
            {
                boolean success = apiClient.uncompleteTask(task.id);
                if (success)
                {
                    log.info("Gielinor Dailies Panel: Uncompleted task '{}'", task.title);
                    if (onRefreshRequest != null) onRefreshRequest.run();
                }
                else
                {
                    log.warn("Gielinor Dailies Panel: Failed to uncomplete task '{}'", task.title);
                }
            }
            catch (Exception e)
            {
                log.warn("Gielinor Dailies Panel: Error uncompleting task", e);
            }
        });
    }

    // ── UI helpers ──

    private void addTag(JPanel parent, String text, Color color)
    {
        JLabel tag = new JLabel(text)
        {
            @Override
            protected void paintComponent(Graphics g)
            {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 35));
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                super.paintComponent(g);
            }
        };
        tag.setFont(FontManager.getRunescapeSmallFont().deriveFont(12f));
        tag.setForeground(color);
        tag.setBorder(new EmptyBorder(2, 5, 2, 5));
        tag.setOpaque(false);
        parent.add(tag);
    }

    private JPanel createDivider()
    {
        JPanel div = new JPanel();
        div.setBackground(new Color(55, 55, 55));
        div.setMaximumSize(new Dimension(MAX_W, 1));
        div.setPreferredSize(new Dimension(100, 1));
        div.setAlignmentX(Component.LEFT_ALIGNMENT);
        return div;
    }

    private Color getPriorityColor(String priority)
    {
        switch (priority)
        {
            case "high": return COLOR_HIGH;
            case "medium": return COLOR_MEDIUM;
            default: return COLOR_LOW;
        }
    }

    private String getTypeLabel(String type)
    {
        switch (type)
        {
            case "daily": return "Daily";
            case "weekly": return "Weekly";
            case "onetime": return "One-time";
            default: return type;
        }
    }

    private Color getTypeColor(String type)
    {
        switch (type)
        {
            case "daily": return new Color(100, 160, 220);
            case "weekly": return new Color(180, 130, 220);
            default: return COLOR_MUTED;
        }
    }

    private static String escapeHtml(String text)
    {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    // ── Custom progress bar ──
    private static class ProgressBar extends JPanel
    {
        private float progress = 0;
        private boolean allDone = false;

        ProgressBar()
        {
            setPreferredSize(new Dimension(100, 6));
            setMaximumSize(new Dimension(MAX_W, 6));
            setMinimumSize(new Dimension(50, 6));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setOpaque(false);
        }

        void setProgress(float progress, boolean allDone)
        {
            this.progress = Math.max(0, Math.min(1, progress));
            this.allDone = allDone;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Background
            g2d.setColor(COLOR_PROGRESS_BG);
            g2d.fillRoundRect(0, 0, w, h, 4, 4);

            // Fill
            int fillW = (int) (w * progress);
            if (fillW > 0)
            {
                g2d.setColor(allDone ? new Color(255, 215, 0) : COLOR_PROGRESS_FILL);
                g2d.fillRoundRect(0, 0, fillW, h, 4, 4);
            }
        }
    }
}
