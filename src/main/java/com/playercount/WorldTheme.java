package com.playercount;

import java.util.Locale;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * What Find: Themed worlds rotates through. Favourites first, then each skill-total level (highest first),
 * then every activity Jagex labels worlds with, A-Z (checked against the live list by a test).
 * Each activity theme matches worlds whose activity contains any of its terms, or that have its world type.
 */
public enum WorldTheme
{
	/** Your favourite worlds (matched by the finder from the Favourite worlds list, not by activity). */
	FAVOURITES("Favourites"),

	SKILL_TOTAL_2350("Skill total 2350", 2350),
	SKILL_TOTAL_2200("Skill total 2200", 2200),
	SKILL_TOTAL_2000("Skill total 2000", 2000),
	SKILL_TOTAL_1750("Skill total 1750", 1750),
	SKILL_TOTAL_1500("Skill total 1500", 1500),
	SKILL_TOTAL_1250("Skill total 1250", 1250),
	SKILL_TOTAL_750("Skill total 750", 750),
	SKILL_TOTAL_500("Skill total 500", 500),
	SKILL_TOTAL("Skill total (any)", "skill total"),

	AGILITY("Agility training", "agility training"),
	BARBARIAN_ASSAULT("Barbarian Assault", "barbarian assault"),
	BLAST_FURNACE("Blast Furnace", "blast furnace"),
	BOUNTY_HUNTER("Bounty Hunter", WorldType.BOUNTY, "bounty hunter"),
	BRIMHAVEN_AGILITY("Brimhaven Agility Arena", "brimhaven agility"),
	GAMES_ROOM("Burthorpe Games Room", "games room"),
	CASTLE_WARS("Castle Wars", "castle wars"),
	CLAN_RECRUITMENT("Clan recruitment", "clan recruitment"),
	CLAN_WARS("Clan Wars", "clan wars"),
	DEADMAN("Deadman", WorldType.DEADMAN, "deadman"),
	DOG_WALKING("Dog walking", "dog walking"),
	FALADOR_PARTY_ROOM("Falador Party Room", "party room"),
	FISHING_TRAWLER("Fishing Trawler", "fishing trawler"),
	FORESTRY("Forestry", "forestry"),
	FRESH_START("Fresh Start", WorldType.FRESH_START_WORLD, "fresh start"),
	GROUP_PVM("Group PvM", "group pvm"),
	GROUP_SKILLING("Group skilling", "group skilling"),
	GUARDIANS_OF_THE_RIFT("Guardians of the Rift", "guardians of the rift"),
	HARDCORE("Hardcore worlds", "hardcore"),
	HIGH_RISK("High risk", WorldType.HIGH_RISK, "high risk"),
	HOUSE_PARTY("House party / Gilded altar", "house party", "gilded altar"),
	LAST_MAN_STANDING("Last Man Standing", "lms", "last man standing"),
	LEAGUES("Leagues / seasonal", WorldType.SEASONAL, "leagues"),
	MASTERING_MIXOLOGY("Mastering Mixology", "mixology"),
	MORTTON("Mort'ton / Rat Pits", "mort'ton", "rat pits"),
	NEX("Nex", "nex"),
	NIGHTMARE("Nightmare", "nightmare"),
	OURANIA("Ourania Altar", "ourania"),
	PEST_CONTROL("Pest Control", "pest control"),
	PVP_ARENA("PvP Arena", "pvp arena"),
	PVP("PvP worlds", WorldType.PVP, "pvp world"),
	PYRAMID_PLUNDER("Pyramid Plunder", "pyramid plunder"),
	ROLE_PLAYING("Role-playing", "role-playing", "role playing"),
	ROYAL_TITANS("Royal Titans", "royal titans"),
	SALVAGING("Salvaging", "salvaging"),
	SORCERESS_GARDEN("Sorceress's Garden", "sorceress"),
	SOUL_WARS("Soul Wars", "soul wars"),
	SPEEDRUNNING("Speedrunning", WorldType.QUEST_SPEEDRUNNING, "speedrunning"),
	SULLIUSCEP("Sulliuscep cutting", "sulliuscep"),
	TEMPOROSS("Tempoross", "tempoross"),
	FRACTURED_ARCHIVE("The Fractured Archive", "fractured archive"),
	THEATRE_OF_BLOOD("Theatre of Blood", "theatre of blood"),
	TOMBS_OF_AMASCUT("Tombs of Amascut", "tombs of amascut", "toa ffa"),
	TRADE("Trade", "trade"),
	TROUBLE_BREWING("Trouble Brewing", "trouble brewing"),
	TZHAAR_FIGHT_PIT("TzHaar Fight Pit", "fight pit"),
	VARLAMORE_PVM("Varlamore PvM", "varlamore pvm"),
	VOLCANIC_MINE("Volcanic Mine", "volcanic mine"),
	WILDERNESS_PK("Wilderness PK", "wilderness pk"),
	WINTERTODT("Wintertodt", "wintertodt"),
	YAMA("Yama", "yama"),
	ZALCANO("Zalcano", "zalcano"),
	ZEAH_RUNECRAFTING("Zeah Runecrafting", "zeah runecrafting");

	private final String label;
	private final WorldType type;
	private final String[] terms;
	/** For a specific skill-total theme, the total it needs (0 otherwise). */
	private final int total;

	WorldTheme(String label, String... terms)
	{
		this(label, null, terms);
	}

	WorldTheme(String label, WorldType type, String... terms)
	{
		this.label = label;
		this.type = type;
		this.terms = terms;
		this.total = 0;
	}

	/** A specific skill-total level, matched exactly (so 500 doesn't also match 1500). */
	WorldTheme(String label, int total)
	{
		this.label = label;
		this.type = null;
		this.terms = new String[0];
		this.total = total;
	}

	/** Total level a specific skill-total theme needs, or 0. */
	int requiredTotal()
	{
		return total;
	}

	/** Short form for the Small overlay size. */
	String shortLabel()
	{
		if (this == FAVOURITES)
		{
			return "Fav";
		}
		if (total > 0)
		{
			return "Total " + total;
		}
		String first = label.split("[ /]")[0];
		return first.length() > 9 ? first.substring(0, 9) : first;
	}

	/** Activity match. Favourites are matched by the finder (it knows the list), so this is false for them. */
	boolean matches(World w)
	{
		if (this == FAVOURITES)
		{
			return false;
		}
		if (total > 0)
		{
			return w.getActivity() != null && w.getActivity().trim().toLowerCase(Locale.ROOT).startsWith(total + " skill total");
		}
		if (type != null && w.getTypes().contains(type))
		{
			return true;
		}
		String activity = w.getActivity() == null ? "" : w.getActivity().toLowerCase(Locale.ROOT);
		if (activity.isEmpty() || "-".equals(activity))
		{
			return false;
		}
		// Hardcore worlds are for hardcore accounts; only the Hardcore theme sends you there.
		if (this != HARDCORE && activity.contains("hardcore"))
		{
			return false;
		}
		for (String t : terms)
		{
			if (activity.contains(t))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
