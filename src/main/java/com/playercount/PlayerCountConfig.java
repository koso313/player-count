package com.playercount;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;
import net.runelite.http.api.worlds.WorldRegion;

@ConfigGroup(PlayerCountConfig.GROUP)
public interface PlayerCountConfig extends Config
{
	String GROUP = "playercount";

	int MIN_REFRESH = 5;
	// RuneLite saves defaults on first load, so a new default needs a new key name.
	String REFRESH_KEY = "updateInterval";
	String SIDEBAR_KEY = "showSidebar";
	String SIZE_KEY = "overlaySizeOption";
	String FAVORITES_KEY = "favoriteWorlds";

	@ConfigSection(name = "Look & size", description = "Frame style, size and when the overlay hides", position = -1)
	String lookSection = "look";

	@ConfigSection(name = "Overlay lines", description = "What the overlay shows", position = 0)
	String linesSection = "lines";

	@ConfigSection(name = "Change & colours", description = "+/- figures and colour coding", position = 1)
	String colorsSection = "colors";

	@ConfigSection(name = "Population graph", description = "Graph of player counts over time", position = 2)
	String graphSection = "graph";

	@ConfigSection(name = "World finder", description = "Find a quiet (or busy) world to hop to", position = 3)
	String finderSection = "finder";

	@ConfigSection(name = "Alerts", description = "Notifications when counts change", position = 4)
	String alertsSection = "alerts";

	@ConfigSection(name = "Sidebar & chat", description = "World list panel and chat command", position = 5)
	String extrasSection = "extras";

	@ConfigSection(name = "Data", description = "How often player counts are refreshed", position = 6)
	String dataSection = "data";

	// ---- Look & size ----

	@ConfigItem(keyName = "frameStyle", name = "Frame style", description = "RuneLite's box, the game's steel interface border, or a RuneHUD-style dark panel", section = lookSection, position = 0)
	default FrameStyle frameStyle()
	{
		return FrameStyle.STEEL;
	}

	@ConfigItem(keyName = "frameOpacity", name = "Background opacity", description = "Background opacity for the steel and dark frames", section = lookSection, position = 1)
	@Range(max = 100)
	@Units(Units.PERCENT)
	default int frameOpacity()
	{
		return 90;
	}

	@ConfigItem(keyName = SIZE_KEY, name = "Size", description = "Full, Medium (the game's small font) or Small (short labels on one line). Each is drawn at native size so text stays sharp. Alt-dragging the overlay's corner also switches size.", section = lookSection, position = 2)
	default OverlaySize overlaySize()
	{
		return OverlaySize.FULL;
	}

	@ConfigItem(keyName = "layout", name = "Layout", description = "Vertical list, or a horizontal strip", section = lookSection, position = 3)
	default Layout layout()
	{
		return Layout.VERTICAL;
	}

	@ConfigItem(keyName = "hideMode", name = "Auto-hide", description = "Hide the overlay in combat, or until you hover over it", section = lookSection, position = 3)
	default HideMode hideMode()
	{
		return HideMode.NEVER;
	}

	@ConfigItem(keyName = "hideInInstances", name = "Hide in instances", description = "Hide the overlay in instanced areas such as boss rooms, raids and minigames", section = lookSection, position = 4)
	default boolean hideInInstances()
	{
		return false;
	}

	// ---- Overlay lines ----

	@ConfigItem(keyName = "showTitle", name = "Title", description = "Show the 'Player Count' title", section = linesSection, position = 0)
	default boolean showTitle()
	{
		return true;
	}

	@ConfigItem(keyName = "showCurrentWorld", name = "Current world", description = "Players in the world you're on", section = linesSection, position = 1)
	default boolean showCurrentWorld()
	{
		return true;
	}

	@ConfigItem(keyName = "showTotal", name = "OSRS total", description = "Total players across all worlds", section = linesSection, position = 2)
	default boolean showTotal()
	{
		return true;
	}

	@ConfigItem(keyName = "showMembersSplit", name = "Members / F2P split", description = "Total split into members and free-to-play worlds", section = linesSection, position = 3)
	default boolean showMembersSplit()
	{
		return false;
	}

	@ConfigItem(keyName = "showRegions", name = "Region breakdown", description = "Total players per region (US, UK, DE, AU...)", section = linesSection, position = 4)
	default boolean showRegions()
	{
		return false;
	}

	@ConfigItem(keyName = "showNearby", name = "Nearby players", description = "Other players within render distance", section = linesSection, position = 5)
	default boolean showNearby()
	{
		return true;
	}

	@ConfigItem(keyName = "showPing", name = "Ping", description = "Your ping to the current world", section = linesSection, position = 5)
	default boolean showPing()
	{
		return true;
	}

	@ConfigItem(keyName = "showSocial", name = "Friends & clan here", description = "How many of your friends and clan members are on your world", section = linesSection, position = 6)
	default boolean showSocial()
	{
		return false;
	}

	@ConfigItem(keyName = "showSuggestion", name = "Suggested world", description = "The world finder's pick", section = linesSection, position = 7)
	default boolean showSuggestion()
	{
		return true;
	}

	@ConfigItem(keyName = "activityWorlds", name = "Activity worlds", description = "Comma-separated activity names (e.g. Wintertodt, Tempoross). Shows the busiest matching world for each.", section = linesSection, position = 8)
	default String activityWorlds()
	{
		return "";
	}

	@ConfigItem(keyName = "showRecords", name = "Daily records", description = "Today's peak and low OSRS total and when they happened (day resets 00:00 UTC)", section = linesSection, position = 9)
	default boolean showRecords()
	{
		return false;
	}

	@ConfigItem(keyName = "showUpdated", name = "Last updated", description = "Show how long ago counts were refreshed", section = linesSection, position = 10)
	default boolean showUpdated()
	{
		return false;
	}

	// ---- Change & colours ----

	@ConfigItem(keyName = "showChange", name = "Show change", description = "Show +/- over the last minute", section = colorsSection, position = 0)
	default boolean showChange()
	{
		return true;
	}

	@ConfigItem(keyName = "colorChanges", name = "Colour changes", description = "Green for gains, red for losses", section = colorsSection, position = 1)
	default boolean colorChanges()
	{
		return true;
	}

	@ConfigItem(keyName = "busyThreshold", name = "Busy world above", description = "Current world count turns red above this (also used by the busy alert)", section = colorsSection, position = 2)
	@Range(min = 1, max = 2000)
	default int busyThreshold()
	{
		return 1000;
	}

	// ---- Graph ----

	@ConfigItem(keyName = "showGraph", name = "Show graph", description = "Draw a population graph in the overlay (history starts when the plugin starts)", section = graphSection, position = 0)
	default boolean showGraph()
	{
		return false;
	}

	@ConfigItem(keyName = "graphSeries", name = "Graph", description = "What to graph", section = graphSection, position = 1)
	default GraphSeries graphSeries()
	{
		return GraphSeries.TOTAL;
	}

	@ConfigItem(keyName = "graphHours", name = "Time span (hours)", description = "How far back the graph goes, in hours", section = graphSection, position = 2)
	@Range(min = 1, max = 6)
	default int graphHours()
	{
		return 1;
	}

	// ---- World finder ----

	@ConfigItem(keyName = "finderMode", name = "Find", description = "What the world finder looks for, or Off to disable it. It never suggests PvP, high-risk, Bounty Hunter, PvP Arena, Deadman, seasonal/Leagues, beta, tournament, Last Man Standing, speedrun or fresh-start worlds, or worlds that are nearly full (Themed worlds is the exception: it can suggest any world whose activity matches, with a red warning on risky ones).", section = finderSection, position = 0)
	default FinderMode finderMode()
	{
		return FinderMode.QUIETEST;
	}

	@ConfigItem(keyName = FAVORITES_KEY, name = "Favourite worlds", description = "Comma-separated world numbers, e.g. 302, 330, 420. Used by Find: Favourites, which cycles through them. Starring worlds in the sidebar edits this list too.", section = finderSection, position = 1)
	default String favoriteWorlds()
	{
		return "";
	}

	@ConfigItem(keyName = "favouriteOrder", name = "Favourites order", description = "For Find: Favourites, which favourite comes first (and the order the Next world hotkey cycles through them). Both weighs ping and player count equally.", section = finderSection, position = 2)
	default FavouriteOrder favouriteOrder()
	{
		return FavouriteOrder.LOWEST_PING;
	}

	@ConfigItem(keyName = "theme", name = "Theme", description = "For Find: Themed worlds. Pick an activity and the finder rotates through every world running it (Next world cycles them). Risky ones such as PvP and Deadman are listed last and warned in red.", section = finderSection, position = 3)
	default WorldTheme theme()
	{
		return WorldTheme.WINTERTODT;
	}

	@ConfigItem(keyName = "themedOrder", name = "Themed order", description = "For Find: Themed worlds, which matching world comes first (and the order Next world rotates through them). Both prefers busy, low-ping worlds.", section = finderSection, position = 4)
	default ThemedOrder themedOrder()
	{
		return ThemedOrder.BUSIEST;
	}

	@ConfigItem(keyName = "warnRisky", name = "Warn on risky worlds", description = "Themed worlds can include risky worlds. PvP and Deadman worlds (attackable almost anywhere) pulse red; High Risk, Bounty Hunter, Wilderness PK, Leagues, beta, speedrun and fresh-start worlds get a steady red outline. Chat warns as you hop.", section = finderSection, position = 5)
	default boolean warnRisky()
	{
		return true;
	}

	@ConfigItem(keyName = "membership", name = "Membership", description = "Members or free-to-play worlds", section = finderSection, position = 6)
	default Membership membership()
	{
		return Membership.SAME_AS_CURRENT;
	}

	@ConfigItem(keyName = "region", name = "Region", description = "Only suggest worlds in this region", section = finderSection, position = 7)
	default RegionFilter region()
	{
		return RegionFilter.ANY;
	}

	@ConfigItem(keyName = "allowSkillTotal", name = "Skill total worlds", description = "Include skill-total worlds you have the level for", section = finderSection, position = 8)
	default boolean allowSkillTotal()
	{
		return true;
	}

	@ConfigItem(keyName = "maxPing", name = "Max ping", description = "Only suggest worlds at or under this ping (0 = any)", section = finderSection, position = 9)
	@Range(max = 1000)
	@Units(Units.MILLISECONDS)
	default int maxPing()
	{
		return 0;
	}

	@ConfigItem(keyName = "preferUsual", name = "Prefer usually quiet", description = "Weigh in how busy each world usually is at this hour (learned over a few days of use)", section = finderSection, position = 10)
	default boolean preferUsual()
	{
		return false;
	}

	@ConfigItem(keyName = "skipRecentMinutes", name = "Skip recent worlds", description = "Don't suggest worlds you were on in the last X minutes (0 = off)", section = finderSection, position = 11)
	@Range(max = 120)
	@Units(Units.MINUTES)
	default int skipRecentMinutes()
	{
		return 0;
	}

	@ConfigItem(keyName = "hopKey", name = "Hop hotkey", description = "Hop to the suggested world. You can also right-click the overlay.", section = finderSection, position = 12)
	default Keybind hopKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(keyName = "nextWorldKey", name = "Next world hotkey", description = "Each press hops to the next world on the finder's list, skipping ones this hotkey already took you to", section = finderSection, position = 13)
	default Keybind nextWorldKey()
	{
		return Keybind.NOT_SET;
	}

	// ---- Alerts ----

	@ConfigItem(keyName = "busyAlert", name = "World gets busy", description = "Notify when your world goes above the 'Busy world above' count", section = alertsSection, position = 0)
	default Notification busyAlert()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "quietAlert", name = "World gets quiet", description = "Notify when your world drops below the count below", section = alertsSection, position = 1)
	default Notification quietAlert()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "quietThreshold", name = "Quiet world below", description = "Count used by the quiet alert", section = alertsSection, position = 2)
	@Range(min = 1, max = 2000)
	default int quietThreshold()
	{
		return 100;
	}

	@ConfigItem(keyName = "nearbyAlert", name = "Player arrives", description = "Notify when someone shows up nearby while you were alone", section = alertsSection, position = 3)
	default Notification nearbyAlert()
	{
		return Notification.OFF;
	}

	// ---- Sidebar & chat ----

	@ConfigItem(keyName = SIDEBAR_KEY, name = "World list sidebar", description = "Sidebar panel listing every world with live counts; double-click a world to hop", section = extrasSection, position = 0)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(keyName = "chatCommand", name = "::players command", description = "Type ::players in chat to print the counts", section = extrasSection, position = 1)
	default boolean chatCommand()
	{
		return true;
	}

	// ---- Data ----

	@ConfigItem(keyName = "pingEnabled", name = "Measure ping", description = "Ping worlds (a quick connection to each world server) for the ping line, sidebar and finder", section = dataSection, position = 1)
	default boolean pingEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "pingInterval", name = "Ping every", description = "How often to re-ping your current world (other worlds: favourites every 20s, all worlds every 2 minutes)", section = dataSection, position = 2)
	@Range(min = 1, max = 60)
	@Units(Units.SECONDS)
	default int pingInterval()
	{
		return 3;
	}

	@ConfigItem(keyName = REFRESH_KEY, name = "Refresh every", description = "How often to download Jagex's live world list", section = dataSection, position = 0)
	@Range(min = MIN_REFRESH, max = 600)
	@Units(Units.SECONDS)
	default int refreshInterval()
	{
		return 30;
	}

	enum ThemedOrder
	{
		BUSIEST("Busiest first"),
		LOWEST_PING("Lowest ping first"),
		BOTH("Both (busy and low ping)");

		private final String label;

		ThemedOrder(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FavouriteOrder
	{
		LOWEST_PING("Lowest ping first"),
		FEWEST_PLAYERS("Fewest players first"),
		BOTH("Both (ping and players)");

		private final String label;

		FavouriteOrder(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum OverlaySize
	{
		FULL("Full"),
		MEDIUM("Medium"),
		SMALL("Small");

		private final String label;

		OverlaySize(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum Layout
	{
		VERTICAL("Vertical"),
		HORIZONTAL("Horizontal");

		private final String label;

		Layout(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum HideMode
	{
		NEVER("Never"),
		IN_COMBAT("In combat"),
		UNTIL_HOVER("Until hovered");

		private final String label;

		HideMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FrameStyle
	{
		RUNELITE("RuneLite box"),
		STEEL("OSRS steel"),
		DARK("Dark panel");

		private final String label;

		FrameStyle(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum GraphSeries
	{
		TOTAL("OSRS total"),
		CURRENT_WORLD("Current world");

		private final String label;

		GraphSeries(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum FinderMode
	{
		OFF("Off"),
		QUIETEST("Quietest"),
		BUSIEST("Busiest"),
		LOWEST_PING("Lowest ping"),
		FAVOURITES("Favourites"),
		THEMED("Themed worlds");

		private final String label;

		FinderMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum Membership
	{
		SAME_AS_CURRENT("Same as current"),
		MEMBERS("Members"),
		FREE("Free-to-play");

		private final String label;

		Membership(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum RegionFilter
	{
		ANY("Any", null),
		SAME_AS_CURRENT("Same as current", null),
		US("United States", WorldRegion.UNITED_STATES_OF_AMERICA),
		UK("United Kingdom", WorldRegion.UNITED_KINGDOM),
		GERMANY("Germany", WorldRegion.GERMANY),
		AUSTRALIA("Australia", WorldRegion.AUSTRALIA);

		private final String label;
		final WorldRegion region;

		RegionFilter(String label, WorldRegion region)
		{
			this.label = label;
			this.region = region;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}
