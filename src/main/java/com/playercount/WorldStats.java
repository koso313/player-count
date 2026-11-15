package com.playercount;

import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Value;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * Pure logic over the world list: population totals and the world finder.
 * No client access, so it is unit-testable.
 */
final class WorldStats
{
	/** Worlds at or above this are treated as full (a hop there usually fails). */
	static final int NEARLY_FULL = 1950;

	/** World types a normal account should never be sent to by the finder. */
	private static final Set<WorldType> SPECIAL = EnumSet.of(
		WorldType.PVP,
		WorldType.BOUNTY,
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.HIGH_RISK,
		WorldType.LAST_MAN_STANDING,
		WorldType.BETA_WORLD,
		WorldType.LEGACY_ONLY,
		WorldType.EOC_ONLY,
		WorldType.NOSAVE_MODE,
		WorldType.TOURNAMENT,
		WorldType.FRESH_START_WORLD,
		WorldType.DEADMAN,
		WorldType.SEASONAL);


	private static final Pattern SKILL_TOTAL = Pattern.compile("(\\d{3,4})");

	private WorldStats()
	{
	}

	@Value
	static class Totals
	{
		int total;
		int members;
		int free;
		int worldsOnline;
	}

	@Value
	@Builder
	static class Criteria
	{
		/** World the player is on now; never suggested. */
		int currentWorld;
		boolean members;
		/** Null means any region. */
		WorldRegion region;
		int totalLevel;
		boolean allowSkillTotal;
		@Builder.Default
		Mode mode = Mode.QUIETEST;
		/** Worlds to leave out (e.g. visited recently). */
		@Builder.Default
		Set<Integer> excluded = Collections.emptySet();
		/** Measured pings in ms; missing or negative = unknown. */
		@Builder.Default
		Map<Integer, Integer> pings = Collections.emptyMap();
		/** 0 = no limit. With a limit, worlds with unknown ping are left out. */
		int maxPing;
		@Builder.Default
		Set<Integer> favorites = Collections.emptySet();
		/** Typical counts at this hour; used when preferUsual is on. */
		@Builder.Default
		Map<Integer, Integer> usual = Collections.emptyMap();
		boolean preferUsual;
		/** For THEMED_* modes: the activity to match. */
		WorldTheme theme;
	}

	enum Mode
	{
		QUIETEST,
		BUSIEST,
		LOWEST_PING,
		/** Worlds whose activity matches a theme, busiest first. */
		THEMED_BUSIEST,
		/** Themed, lowest ping first. */
		THEMED_BY_PING,
		/** Themed, busy and low ping weighed equally. */
		THEMED_BALANCED,
		/** Themed, fewest players first. */
		THEMED_QUIETEST,
		/** Themed, quiet and low ping weighed equally. */
		THEMED_QUIET_BALANCED
	}

	static boolean isThemed(Mode m)
	{
		return m == Mode.THEMED_BUSIEST || m == Mode.THEMED_BY_PING || m == Mode.THEMED_BALANCED
			|| m == Mode.THEMED_QUIETEST || m == Mode.THEMED_QUIET_BALANCED;
	}


	static Totals totals(List<World> worlds)
	{
		int total = 0;
		int members = 0;
		int online = 0;
		for (World w : worlds)
		{
			int p = w.getPlayers();
			if (p <= 0)
			{
				continue;
			}
			online++;
			total += p;
			if (w.getTypes().contains(WorldType.MEMBERS))
			{
				members += p;
			}
		}
		return new Totals(total, members, total - members, online);
	}

	/**
	 * Skill total needed to log in to a world, or 0 if it has none.
	 * Returns {@link Integer#MAX_VALUE} for a skill-total world whose requirement can't be read.
	 */
	static int skillTotalRequirement(World w)
	{
		if (!w.getTypes().contains(WorldType.SKILL_TOTAL))
		{
			return 0;
		}
		String activity = w.getActivity();
		if (activity != null)
		{
			Matcher m = SKILL_TOTAL.matcher(activity);
			if (m.find())
			{
				return Integer.parseInt(m.group(1));
			}
		}
		return Integer.MAX_VALUE;
	}

	static boolean eligible(World w, Criteria c)
	{
		if (w.getId() == c.getCurrentWorld() || c.getExcluded().contains(w.getId()))
		{
			return false;
		}
		int p = w.getPlayers();
		if (p < 0 || p >= NEARLY_FULL)
		{
			return false;
		}
		boolean themed = isThemed(c.getMode());
		boolean favourites = themed && c.getTheme() == WorldTheme.FAVOURITES;
		if (themed && (c.getTheme() == null || (favourites ? !c.getFavorites().contains(w.getId()) : !c.getTheme().matches(w))))
		{
			return false;
		}
		// Themed worlds may be any type (the user named the theme); everywhere else special worlds are skipped.
		if (!themed && isSpecial(w))
		{
			return false;
		}
		// Favourites are worlds you picked yourself, so the membership, region and max-ping filters don't apply
		// (members can join free worlds). Skill totals and full worlds still do: you couldn't get in.
		if (!favourites)
		{
			if (w.getTypes().contains(WorldType.MEMBERS) != c.isMembers())
			{
				return false;
			}
			if (c.getRegion() != null && w.getRegion() != c.getRegion())
			{
				return false;
			}
			int ping = c.getPings().getOrDefault(w.getId(), -1);
			if ((c.getMaxPing() > 0 || c.getMode() == Mode.LOWEST_PING) && ping < 0)
			{
				return false;
			}
			if (c.getMaxPing() > 0 && ping > c.getMaxPing())
			{
				return false;
			}
		}
		int req = skillTotalRequirement(w);
		return req == 0 || (c.isAllowSkillTotal() && c.getTotalLevel() >= req);
	}

	/** Every eligible world, best first for the criteria's mode. */
	static List<World> rank(List<World> worlds, Criteria c)
	{
		Comparator<World> order;
		switch (c.getMode())
		{
			case BUSIEST:
			case THEMED_BUSIEST:
				order = Comparator.comparingInt(World::getPlayers).reversed();
				break;
			case LOWEST_PING:
			case THEMED_BY_PING:
				order = Comparator.<World>comparingInt(w -> unknownLast(c.getPings().getOrDefault(w.getId(), -1)))
					.thenComparingInt(World::getPlayers);
				break;
			case THEMED_QUIETEST:
				order = Comparator.comparingInt(World::getPlayers);
				break;
			default:
				order = Comparator.comparingDouble(w -> quietScore(w, c));
		}
		List<World> eligible = worlds.stream().filter(w -> eligible(w, c)).collect(Collectors.toList());
		if (c.getMode() == Mode.THEMED_QUIET_BALANCED)
		{
			order = balanced(eligible, c.getPings(), false);
		}
		else if (c.getMode() == Mode.THEMED_BALANCED)
		{
			order = balanced(eligible, c.getPings(), true);
		}
		eligible.sort(order.thenComparingInt(World::getId));
		return eligible;
	}

	/**
	 * Ping and players each scaled 0..1 against the worlds being compared, then added: lowest total wins.
	 * A world with no ping yet counts as the slowest. {@code busy} rewards more players instead of fewer.
	 */
	static Comparator<World> balanced(List<World> worlds, Map<Integer, Integer> pings, boolean busy)
	{
		int maxPing = 1;
		int maxPlayers = 1;
		for (World w : worlds)
		{
			maxPing = Math.max(maxPing, pings.getOrDefault(w.getId(), -1));
			maxPlayers = Math.max(maxPlayers, w.getPlayers());
		}
		final double mp = maxPing;
		final double ml = maxPlayers;
		return Comparator.comparingDouble(w ->
		{
			int ping = pings.getOrDefault(w.getId(), -1);
			double crowd = w.getPlayers() / ml;
			return (ping < 0 ? 1.0 : ping / mp) + (busy ? 1.0 - crowd : crowd);
		});
	}

	private static int unknownLast(int ping)
	{
		return ping < 0 ? Integer.MAX_VALUE : ping;
	}

	/** Best eligible world, or null if none match. */
	static World pick(List<World> worlds, Criteria c)
	{
		List<World> ranked = rank(worlds, c);
		return ranked.isEmpty() ? null : ranked.get(0);
	}

	/** Players now, blended half-and-half with the usual count for this hour when that's wanted and known. */
	static double quietScore(World w, Criteria c)
	{
		int usual = c.isPreferUsual() ? c.getUsual().getOrDefault(w.getId(), -1) : -1;
		return usual < 0 ? w.getPlayers() : (w.getPlayers() + usual) / 2.0;
	}

	/** Players per region, busiest first. */
	static Map<WorldRegion, Integer> regionTotals(List<World> worlds)
	{
		Map<WorldRegion, Integer> sums = new EnumMap<>(WorldRegion.class);
		for (World w : worlds)
		{
			if (w.getPlayers() > 0 && w.getRegion() != null)
			{
				sums.merge(w.getRegion(), w.getPlayers(), Integer::sum);
			}
		}
		Map<WorldRegion, Integer> sorted = new LinkedHashMap<>();
		sums.entrySet().stream()
			.sorted(Map.Entry.<WorldRegion, Integer>comparingByValue().reversed())
			.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	/** Busiest world whose activity contains the term (case-insensitive), or null. */
	static World busiestActivity(List<World> worlds, String term)
	{
		String needle = term.trim().toLowerCase(Locale.ROOT);
		if (needle.isEmpty())
		{
			return null;
		}
		return worlds.stream()
			.filter(w -> w.getPlayers() >= 0 && w.getActivity() != null
				&& w.getActivity().toLowerCase(Locale.ROOT).contains(needle))
			.max(Comparator.comparingInt(World::getPlayers).thenComparingInt(w -> -w.getId()))
			.orElse(null);
	}

	/**
	 * Short warning for worlds where you can lose items or that need a separate account/mode,
	 * e.g. "PvP" or "Leagues"; null for ordinary (and safe minigame) worlds.
	 */
	static String risk(World w)
	{
		java.util.EnumSet<WorldType> t = w.getTypes();
		// Attackable almost anywhere first
		if (t.contains(WorldType.DEADMAN))
		{
			return "Deadman";
		}
		if (t.contains(WorldType.PVP))
		{
			return t.contains(WorldType.HIGH_RISK) ? "PvP high risk" : "PvP";
		}
		// Dangerous in the Wilderness
		if (t.contains(WorldType.HIGH_RISK))
		{
			return "High risk";
		}
		if (t.contains(WorldType.BOUNTY))
		{
			return "Bounty Hunter";
		}
		if (t.contains(WorldType.SEASONAL))
		{
			return "Leagues";
		}
		if (t.contains(WorldType.BETA_WORLD))
		{
			return "Beta";
		}
		if (t.contains(WorldType.QUEST_SPEEDRUNNING))
		{
			return "Speedrun";
		}
		if (t.contains(WorldType.FRESH_START_WORLD))
		{
			return "Fresh start";
		}
		if (t.contains(WorldType.TOURNAMENT) || t.contains(WorldType.NOSAVE_MODE))
		{
			return "Tournament";
		}
		// Ordinary worlds set aside for Wilderness PKing: not PvP everywhere, but you're there to fight.
		String activity = w.getActivity() == null ? "" : w.getActivity().toLowerCase(java.util.Locale.ROOT);
		if (activity.contains("wilderness pk"))
		{
			return "Wildy PK";
		}
		// PvP minigames: nothing to lose, but PvP all the same, so flagged orange like the Wilderness worlds
		if (t.contains(WorldType.LAST_MAN_STANDING) || activity.startsWith("lms") || activity.contains("last man standing"))
		{
			return "LMS";
		}
		if (t.contains(WorldType.PVP_ARENA) || activity.contains("pvp arena"))
		{
			return "PvP Arena";
		}
		return null;
	}

	/**
	 * PvP-themed worlds (PvP, Deadman, High Risk, Bounty Hunter, Wilderness PK, LMS, PvP Arena): these get the
	 * pulsing red warning. Separate-account worlds (Leagues, beta, speedrun, fresh start) are orange instead.
	 */
	static boolean pvpThemed(World w)
	{
		String r = risk(w);
		return r != null && !SEPARATE_ACCOUNT.contains(r);
	}

	private static final java.util.Set<String> SEPARATE_ACCOUNT = new java.util.HashSet<>(java.util.Arrays.asList(
		"Leagues", "Beta", "Speedrun", "Fresh start", "Tournament"));

	/** PvP and Deadman worlds: you can be attacked almost anywhere (stronger chat warning). */
	static boolean deadly(World w)
	{
		return w.getTypes().contains(WorldType.PVP) || w.getTypes().contains(WorldType.DEADMAN);
	}

	/** True for PvP, Deadman, seasonal and other worlds the finder never suggests. */
	static boolean isSpecial(World w)
	{
		for (WorldType t : w.getTypes())
		{
			if (SPECIAL.contains(t))
			{
				return true;
			}
		}
		return false;
	}

	static String regionCode(WorldRegion region)
	{
		if (region == null)
		{
			return "";
		}
		String a2 = region.getAlpha2();
		return "GB".equals(a2) ? "UK" : a2;
	}

	/** True if both lists have the same worlds with the same player counts (a repeat of the same update). */
	static boolean sameCounts(List<World> a, List<World> b)
	{
		if (a.size() != b.size())
		{
			return false;
		}
		Map<Integer, Integer> counts = new HashMap<>();
		for (World w : a)
		{
			counts.put(w.getId(), w.getPlayers());
		}
		for (World w : b)
		{
			Integer n = counts.get(w.getId());
			if (n == null || n != w.getPlayers())
			{
				return false;
			}
		}
		return true;
	}
}
