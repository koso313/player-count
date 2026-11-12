package com.playercount;

import java.awt.Color;
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
	/** Sidebar stars from an earlier build, folded into Favourite worlds on start-up. */
	String STARRED_KEY = "starredWorlds";

	@ConfigSection(name = "Overlay", description = "How the overlay looks and when it hides", position = 0)
	String lookSection = "look";

	@ConfigSection(name = "Overlay lines", description = "What the overlay shows", position = 1)
	String linesSection = "lines";

	@ConfigSection(name = "World finder", description = "Find a world to hop to and the hotkeys for it", position = 2)
	String finderSection = "finder";

	@ConfigSection(name = "Finder filters", description = "Narrow down which worlds the finder suggests", position = 3, closedByDefault = true)
	String filtersSection = "filters";

	@ConfigSection(name = "World alerts", description = "Notifications when your world gets busy or quiet", position = 4)
	String alertsSection = "alerts";

	@ConfigSection(name = "Player arrives", description = "Alert and highlight when players show up near you", position = 5)
	String arrivalSection = "arrival";

	@ConfigSection(name = "Sidebar & data", description = "Sidebar panel, chat command, refresh and ping", position = 6, closedByDefault = true)
	String extrasSection = "extras";

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

	@ConfigItem(keyName = "autoHide", name = "Auto-hide", description = "Hide the overlay in combat, in instances (boss rooms, raids, minigames), either, or until you hover over it", section = lookSection, position = 4)
	default AutoHide autoHide()
	{
		return AutoHide.NEVER;
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

	@ConfigItem(keyName = "changeStyle", name = "Change (+/-)", description = "Show how your world and the total changed over the last minute: off, plain, or green for gains and red for losses", section = linesSection, position = 3)
	default ChangeStyle changeStyle()
	{
		return ChangeStyle.COLOURED;
	}

	@ConfigItem(keyName = "showMembersSplit", name = "Members / F2P split", description = "Total split into members and free-to-play worlds", section = linesSection, position = 4)
	default boolean showMembersSplit()
	{
		return false;
	}

	@ConfigItem(keyName = "regionDisplay", name = "Regions", description = "Players per region: one line each, the top regions on one line, a share bar, or a world map of glowing players (hover a region for its count)", section = linesSection, position = 5)
	default RegionDisplay regionDisplay()
	{
		return RegionDisplay.OFF;
	}

	@ConfigItem(keyName = "showNearby", name = "Nearby players", description = "Other players within render distance", section = linesSection, position = 7)
	default boolean showNearby()
	{
		return true;
	}

	@ConfigItem(keyName = "showPing", name = "Ping", description = "Your ping to the current world", section = linesSection, position = 6)
	default boolean showPing()
	{
		return true;
	}

	@ConfigItem(keyName = "showSocial", name = "Friends & clan here", description = "How many of your friends and clan members are on your world", section = linesSection, position = 8)
	default boolean showSocial()
	{
		return false;
	}

	@ConfigItem(keyName = "showSuggestion", name = "Suggested world", description = "The world finder's pick", section = linesSection, position = 9)
	default boolean showSuggestion()
	{
		return true;
	}

	@ConfigItem(keyName = "showRecords", name = "Daily records", description = "Today's peak and low OSRS total and when they happened (day resets 00:00 UTC)", section = linesSection, position = 11)
	default boolean showRecords()
	{
		return false;
	}

	@ConfigItem(keyName = "showUpdated", name = "Last updated", description = "Show how long ago counts were refreshed", section = linesSection, position = 14)
	default boolean showUpdated()
	{
		return false;
	}

	// ---- Change & colours ----

	@ConfigItem(keyName = "busyThreshold", name = "Busy above", description = "Your world counts as busy above this many players: the busy alert fires and its count turns red in the overlay", section = alertsSection, position = 1)
	@Range(min = 1, max = 2000)
	default int busyThreshold()
	{
		return 1000;
	}

	// ---- Graph ----

	@ConfigItem(keyName = "graphMode", name = "Graph", description = "Graph the OSRS total or your current world over time (history is kept between sessions)", section = linesSection, position = 12)
	default GraphMode graphMode()
	{
		return GraphMode.OFF;
	}

	@ConfigItem(keyName = "graphHours", name = "Graph time span (hours)", description = "How far back the graph goes, in hours", section = linesSection, position = 13)
	@Range(min = 1, max = 6)
	default int graphHours()
	{
		return 1;
	}

	// ---- World finder ----

	@ConfigItem(keyName = "finderMode", name = "Find", description = "What the world finder looks for (Themed worlds also covers your favourites), or Off to disable it. It never suggests PvP, high-risk, Bounty Hunter, PvP Arena, Deadman, seasonal/Leagues, beta, tournament, Last Man Standing, speedrun or fresh-start worlds, or worlds that are nearly full (Themed worlds is the exception: it can suggest any world whose activity matches, with a red warning on risky ones).", section = finderSection, position = 0)
	default FinderMode finderMode()
	{
		return FinderMode.QUIETEST;
	}


	@ConfigItem(keyName = "theme", name = "Theme", description = "For Find: Themed worlds. Your favourites, a skill-total level, or an activity; the finder rotates through every matching world (Next world cycles them). Risky worlds are warned in red or orange.", section = finderSection, position = 1)
	default WorldTheme theme()
	{
		return WorldTheme.WINTERTODT;
	}

	@ConfigItem(keyName = FAVORITES_KEY, name = "Favourite worlds", description = "Comma-separated world numbers, e.g. 302, 330, 420, for Theme: Favourites (the finder filters for membership, region and max ping don't apply to favourites). The sidebar's stars and Favourites row edit this list too (reopen the settings to see their changes); deleting a world here removes it there.", section = finderSection, position = 2)
	default String favoriteWorlds()
	{
		return "";
	}

	@ConfigItem(keyName = STARRED_KEY, name = "", description = "", hidden = true)
	default String starredWorlds()
	{
		return "";
	}

	@ConfigItem(keyName = "themedOrder", name = "Order", description = "For Find: Themed worlds (including Favourites), which world comes first and the order Next world rotates through them.", section = finderSection, position = 3)
	default ThemedOrder themedOrder()
	{
		return ThemedOrder.BUSIEST;
	}

	@ConfigItem(keyName = "warnRisky", name = "Warn on risky worlds", description = "Themed worlds can include risky worlds. PvP-themed worlds (PvP, Deadman, High Risk, Bounty Hunter, Wilderness PK, LMS, PvP Arena) pulse red; separate-account worlds (Leagues, beta, speedrun, fresh start) are orange with an orange underline. Chat warns as you hop.", section = finderSection, position = 4)
	default boolean warnRisky()
	{
		return true;
	}

	@ConfigItem(keyName = "membership", name = "Membership", description = "Members or free-to-play worlds", section = filtersSection, position = 0)
	default Membership membership()
	{
		return Membership.SAME_AS_CURRENT;
	}

	@ConfigItem(keyName = "region", name = "Region", description = "Only suggest worlds in this region", section = filtersSection, position = 1)
	default RegionFilter region()
	{
		return RegionFilter.ANY;
	}

	@ConfigItem(keyName = "allowSkillTotal", name = "Skill total worlds", description = "Include skill-total worlds you have the level for", section = filtersSection, position = 3)
	default boolean allowSkillTotal()
	{
		return true;
	}

	@ConfigItem(keyName = "maxPing", name = "Max ping", description = "Only suggest worlds at or under this ping (0 = any)", section = filtersSection, position = 2)
	@Range(max = 1000)
	@Units(Units.MILLISECONDS)
	default int maxPing()
	{
		return 0;
	}

	@ConfigItem(keyName = "preferUsual", name = "Prefer usually quiet", description = "Weigh in how busy each world usually is at this hour (learned over a few days of use)", section = filtersSection, position = 5)
	default boolean preferUsual()
	{
		return false;
	}

	@ConfigItem(keyName = "skipRecentMinutes", name = "Skip recent worlds", description = "Don't suggest worlds you were on in the last X minutes (0 = off)", section = filtersSection, position = 4)
	@Range(max = 120)
	@Units(Units.MINUTES)
	default int skipRecentMinutes()
	{
		return 0;
	}

	@ConfigItem(keyName = "hopKey", name = "Hop hotkey", description = "Hop to the suggested world. You can also right-click the overlay.", section = finderSection, position = 5)
	default Keybind hopKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(keyName = "nextWorldKey", name = "Next world hotkey", description = "Each press hops to the next world on the finder's list, skipping ones this hotkey already took you to", section = finderSection, position = 6)
	default Keybind nextWorldKey()
	{
		return Keybind.NOT_SET;
	}

	// ---- Alerts ----

	@ConfigItem(keyName = "busyAlert", name = "World gets busy", description = "Notify when your world goes above the Busy above count", section = alertsSection, position = 0)
	default Notification busyAlert()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "quietAlert", name = "World gets quiet", description = "Notify when your world drops below the count below", section = alertsSection, position = 2)
	default Notification quietAlert()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "quietThreshold", name = "Quiet below", description = "Your world counts as quiet below this many players (for the quiet alert)", section = alertsSection, position = 3)
	@Range(min = 1, max = 2000)
	default int quietThreshold()
	{
		return 100;
	}

	@ConfigItem(keyName = "nearbyAlert", name = "Alert", description = "Alert when a player shows up nearby. Click the cog to choose a screen flash (and its colour), a sound, a tray popup or a chat message.", section = arrivalSection, position = 0)
	default Notification nearbyAlert()
	{
		return Notification.OFF;
	}

	@ConfigItem(keyName = "arrivalMode", name = "Alert for", description = "Only the first player to arrive while you were alone, or every new player", section = arrivalSection, position = 1)
	default ArrivalMode arrivalMode()
	{
		return ArrivalMode.WHEN_ALONE;
	}

	@ConfigItem(keyName = "arrivalRange", name = "Within", description = "Only count players within this many tiles (0 = anywhere on screen)", section = arrivalSection, position = 2)
	@Range(max = 32)
	default int arrivalRange()
	{
		return 0;
	}

	@ConfigItem(keyName = "arrivalIgnoreFriends", name = "Ignore friends & clan", description = "Don't alert for friends, clan members or your friends chat", section = arrivalSection, position = 3)
	default boolean arrivalIgnoreFriends()
	{
		return true;
	}

	@ConfigItem(keyName = "arrivalAttackableOnly", name = "Only if they can attack you", description = "Only alert for players in combat range of you: in the Wilderness the level gap must be within the Wilderness level, PvP worlds add 15, and on Deadman anyone counts", section = arrivalSection, position = 4)
	default boolean arrivalAttackableOnly()
	{
		return false;
	}

	@ConfigItem(keyName = "arrivalHighlight", name = "Highlight arrivals", description = "Outline players who set off the alert and show their name and combat level", section = arrivalSection, position = 5)
	default boolean arrivalHighlight()
	{
		return true;
	}

	@ConfigItem(keyName = "arrivalColor", name = "Highlight colour", description = "Colour of the arrival outline and label", section = arrivalSection, position = 6)
	default Color arrivalColor()
	{
		return new Color(0xFF, 0x30, 0x30);
	}

	@ConfigItem(keyName = "arrivalSeconds", name = "Highlight for", description = "How long arrivals stay highlighted", section = arrivalSection, position = 7)
	@Range(min = 1, max = 60)
	@Units(Units.SECONDS)
	default int arrivalSeconds()
	{
		return 6;
	}

	// ---- Sidebar & chat ----

	@ConfigItem(keyName = SIDEBAR_KEY, name = "World list sidebar", description = "Sidebar panel listing every world with live counts, ping and a Hop button", section = extrasSection, position = 0)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(keyName = "sidebarMap", name = "Sidebar world map", description = "Show the glowing player map at the top of the sidebar (hover a region for its count)", section = extrasSection, position = 1)
	default boolean sidebarMap()
	{
		return true;
	}

	@ConfigItem(keyName = "chatCommand", name = "::players command", description = "Type ::players in chat to print the counts", section = extrasSection, position = 2)
	default boolean chatCommand()
	{
		return true;
	}

	// ---- Data ----

	@ConfigItem(keyName = "pingEnabled", name = "Measure ping", description = "Ping worlds (a quick connection to each world server) for the ping line, sidebar and finder", section = extrasSection, position = 4)
	default boolean pingEnabled()
	{
		return true;
	}

	@ConfigItem(keyName = "pingInterval", name = "Ping every", description = "How often to re-ping your current world (other worlds: favourites every 20s, all worlds every 2 minutes)", section = extrasSection, position = 5)
	@Range(min = 1, max = 60)
	@Units(Units.SECONDS)
	default int pingInterval()
	{
		return 3;
	}

	@ConfigItem(keyName = REFRESH_KEY, name = "Refresh every", description = "How often to download Jagex's live world list", section = extrasSection, position = 3)
	@Range(min = MIN_REFRESH, max = 600)
	@Units(Units.SECONDS)
	default int refreshInterval()
	{
		return 30;
	}

	enum ArrivalMode
	{
		WHEN_ALONE("When you were alone"),
		EVERY_PLAYER("Every new player");

		private final String label;

		ArrivalMode(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum RegionDisplay
	{
		OFF("Off"),
		LIST("One line each"),
		TOP("Top regions"),
		BAR("Share bar"),
		MAP("World map");

		private final String label;

		RegionDisplay(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum ThemedOrder
	{
		BUSIEST("Busiest first"),
		QUIETEST("Quietest first"),
		LOWEST_PING("Lowest ping first"),
		BOTH("Busy + low ping"),
		QUIET_AND_FAST("Quiet + low ping");

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

	enum AutoHide
	{
		NEVER("Never"),
		IN_COMBAT("In combat"),
		IN_INSTANCES("In instances"),
		COMBAT_OR_INSTANCES("Combat or instances"),
		UNTIL_HOVER("Until hovered");

		private final String label;

		AutoHide(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	enum ChangeStyle
	{
		OFF("Off"),
		PLAIN("Plain"),
		COLOURED("Coloured");

		private final String label;

		ChangeStyle(String label)
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

	enum GraphMode
	{
		OFF("Off"),
		TOTAL("OSRS total"),
		CURRENT_WORLD("Current world");

		private final String label;

		GraphMode(String label)
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
