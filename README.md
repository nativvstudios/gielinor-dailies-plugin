# Gielinor Dailies Plugin

A [RuneLite](https://runelite.net/) plugin that syncs your OSRS stats, XP gains, boss kills, and quest progress with your [Gielinor Dailies](https://gielinordailies.com) dashboard for task tracking and planning.

## Features

- **Stats Sync** - Automatically pushes your skill levels and XP to your Gielinor Dailies account
- **XP Gain Tracking** - Tracks session XP gains per skill
- **Boss KC Tracking** - Detects boss kills from chat messages and syncs kill counts
- **Quest Tracking** - Monitors quest state changes and syncs progress
- **Task Overlay** - In-game overlay showing your daily/weekly tasks with progress
- **Sidebar Panel** - Full task management panel with priority indicators, completion buttons, and announcements
- **Completion Celebrations** - Visual and audio notifications when you complete tasks

## Setup

1. Install the plugin from the RuneLite Plugin Hub
2. Create an account at [Gielinor Dailies](https://gielinordailies.com)
3. Add your OSRS character to your account
4. Generate an API token from your account settings
5. In the plugin config, enter your API token
6. Log into OSRS - the plugin will automatically match your RSN and start syncing

## Configuration

| Setting | Description | Default |
|---------|-------------|---------|
| API Token | Your authentication token | - |
| Push Stats | Enable automatic stat syncing | On |
| Push Boss KC | Enable boss kill count tracking | On |
| Show Overlay | Show the in-game task overlay | On |
| Completion Sound | Play a sound when tasks are completed | On |

## Support

Report issues at https://github.com/nativvstudios/gielinor-dailies-plugin/issues
