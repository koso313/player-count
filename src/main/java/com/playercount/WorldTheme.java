package com.playercount;

import java.util.Locale;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

/**
 * Themed worlds, as Jagex labels them in the world list's activity column (checked against the live list).
 * Each theme matches worlds whose activity contains any of its terms, or that have its world type.
 * Risky ones (PvP, Deadman...) come last and are warned about in red.
 */
public enum WorldTheme
{
	AGILITY("Agility training", "agility training"),
	BARBARIAN_ASSAULT("Barbarian Assault", "barbarian assault"),
	BLAST_FURNACE("Blast Furnace", "blast furnace"),
	BRIMHAVEN_AGILITY("Brimhaven Agility Arena", "brimhaven agility"),
	CASTLE_WARS("Castle Wars", "castle wars"),
	CLAN_RECRUITMENT("Clan recruitment", "clan recruitment"),
	CLAN_WARS("Clan Wars", "clan wars"),
	DOG_WALKING("Dog walking", "dog walking"),
	FALADOR_PARTY_ROOM("Falador Party Room", "party room"),
	FISHING_TRAWLER("Fishing Trawler", "fishing trawler"),
	FORESTRY("Forestry", "forestry"),
	FRACTURED_ARCHIVE("The Fractured Archive", "fractured archive"),
	GAMES_ROOM("Burthorpe Games Room", "games room"),
	GROUP_PVM("Group PvM", "group pvm"),
	GROUP_SKILLING("Group skilling", "group skilling"),
	GUARDIANS_OF_THE_RIFT("Guardians of the Rift", "guardians of the rift"),
	HOUSE_PARTY("House party / Gilded altar", "house party", "gilded altar"),
	MASTERING_MIXOLOGY("Mastering Mixology", "mixology"),
	MORTTON("Mort'ton / Rat Pits", "mort'ton", "rat pits"),
	NEX("Nex", "nex"),
	NIGHTMARE("Nightmare", "nightmare"),
	OURANIA("Ourania Altar", "ourania"),
	PEST_CONTROL("Pest Control", "pest control"),
	PYRAMID_PLUNDER("Pyramid Plunder", "pyramid plunder"),
	ROLE_PLAYING("Role-playing", "role-playing", "role playing"),
	ROYAL_TITANS("Royal Titans", "royal titans"),
	SALVAGING("Salvaging", "salvaging"),
	SORCERESS_GARDEN("Sorceress's Garden", "sorceress"),
	SOUL_WARS("Soul Wars", "soul wars"),
	SULLIUSCEP("Sulliuscep cutting", "sulliuscep"),
	TEMPOROSS("Tempoross", "tempoross"),
	THEATRE_OF_BLOOD("Theatre of Blood", "theatre of blood"),
	TOMBS_OF_AMASCUT("Tombs of Amascut", "tombs of amascut", "toa ffa"),
	TRADE("Trade", "trade"),
	TROUBLE_BREWING("Trouble Brewing", "trouble brewing"),
	TZHAAR_FIGHT_PIT("TzHaar Fight Pit", "fight pit"),
	VARLAMORE_PVM("Varlamore PvM", "varlamore pvm"),
	VOLCANIC_MINE("Volcanic Mine", "volcanic mine"),
	WINTERTODT("Wintertodt", "wintertodt"),
	YAMA("Yama", "yama"),
	ZALCANO("Zalcano", "zalcano"),
	ZEAH_RUNECRAFTING("Zeah Runecrafting", "zeah runecrafting"),
	SKILL_TOTAL("Skill total worlds", "skill total"),
	LAST_MAN_STANDING("Last Man Standing", "lms", "last man standing"),
	PVP_ARENA("PvP Arena", "pvp arena"),
	HARDCORE("Hardcore worlds", "hardcore"),
	// Risky or separate-account: warned in red
	WILDERNESS_PK("Wilderness PK", "wilderness pk"),
	PVP("PvP worlds", WorldType.PVP, "pvp world"),
	HIGH_RISK("High risk", WorldType.HIGH_RISK, "high risk"),
	BOUNTY_HUNTER("Bounty Hunter", WorldType.BOUNTY, "bounty hunter"),
	DEADMAN("Deadman", WorldType.DEADMAN, "deadman"),
	LEAGUES("Leagues / seasonal", WorldType.SEASONAL, "leagues"),
	SPEEDRUNNING("Speedrunning", WorldType.QUEST_SPEEDRUNNING, "speedrunning"),
	FRESH_START("Fresh Start", WorldType.FRESH_START_WORLD, "fresh start");

	private final String label;
	private final WorldType type;
	private final String[] terms;

	WorldTheme(String label, String... terms)
	{
		this(label, null, terms);
	}

	WorldTheme(String label, WorldType type, String... terms)
	{
		this.label = label;
		this.type = type;
		this.terms = terms;
	}

	/** Short form for the Small overlay size. */
	String shortLabel()
	{
		String first = label.split("[ /]")[0];
		return first.length() > 9 ? first.substring(0, 9) : first;
	}

	boolean matches(World w)
	{
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
