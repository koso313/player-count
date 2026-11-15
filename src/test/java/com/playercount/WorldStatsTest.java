package com.playercount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class WorldStatsTest
{
	private static final WorldRegion US = WorldRegion.UNITED_STATES_OF_AMERICA, UK = WorldRegion.UNITED_KINGDOM, DE = WorldRegion.GERMANY;
	private static World world(int id, int players, WorldRegion region, String activity, WorldType... types)
	{
		EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
		set.addAll(Arrays.asList(types));
		return World.builder()
			.id(id)
			.players(players)
			.region(region)
			.activity(activity)
			.address("oldschool" + id + ".runescape.com")
			.types(set)
			.build();
	}

	private static final List<World> WORLDS = Arrays.asList(
		world(301, 900, US, "Trade - Free", WorldType.PVP_ARENA),
		world(302, 1500, UK, "Trade - Members", WorldType.MEMBERS),
		world(303, 40, DE, "-", WorldType.MEMBERS),
		world(308, 30, UK, "-"),
		world(325, 10, UK, "PvP", WorldType.MEMBERS, WorldType.PVP),
		world(353, 5, UK, "1250 skill total", WorldType.MEMBERS, WorldType.SKILL_TOTAL),
		world(416, 1990, US, "-", WorldType.MEMBERS),
		world(420, 60, US, "-", WorldType.MEMBERS),
		world(500, -1, US, "-", WorldType.MEMBERS));

	private static WorldStats.Criteria.CriteriaBuilder members()
	{
		return WorldStats.Criteria.builder().currentWorld(302).members(true).totalLevel(2000).allowSkillTotal(true);
	}

	@Test
	public void totalsSkipOfflineWorldsAndSplitMembership()
	{
		WorldStats.Totals t = WorldStats.totals(WORLDS);
		assertEquals(900 + 1500 + 40 + 30 + 10 + 5 + 1990 + 60, t.getTotal());
		assertEquals(900 + 30, t.getFree());
		assertEquals(8, t.getWorldsOnline());
	}

	@Test
	public void quietestSkipsPvpAndPicksSkillTotalWhenQualified()
	{
		assertEquals(353, WorldStats.pick(WORLDS, members().build()).getId());
	}

	@Test
	public void skillTotalWorldSkippedWhenUnderLevel()
	{
		assertEquals(303, WorldStats.pick(WORLDS, members().totalLevel(1000).build()).getId());
		assertEquals(303, WorldStats.pick(WORLDS, members().allowSkillTotal(false).build()).getId());
	}

	@Test
	public void freeToPlaySkipsSpecialWorlds()
	{
		assertEquals(308, WorldStats.pick(WORLDS, members().members(false).build()).getId());
	}

	@Test
	public void regionFilter()
	{
		assertEquals(420, WorldStats.pick(WORLDS, members().region(WorldRegion.UNITED_STATES_OF_AMERICA).build()).getId());
		assertNull(WorldStats.pick(WORLDS, members().region(WorldRegion.AUSTRALIA).build()));
	}

	@Test
	public void busiestSkipsCurrentAndNearlyFullWorlds()
	{
		assertEquals(420, WorldStats.pick(WORLDS, members().mode(WorldStats.Mode.BUSIEST).build()).getId());
		assertEquals(302, WorldStats.pick(WORLDS, members().currentWorld(420).mode(WorldStats.Mode.BUSIEST).build()).getId());
	}

	@Test
	public void skillTotalRequirementParsing()
	{
		assertEquals(0, WorldStats.skillTotalRequirement(world(1, 1, US, "2000 skill total")));
		assertEquals(2200, WorldStats.skillTotalRequirement(world(1, 1, US, "2200 skill total", WorldType.SKILL_TOTAL)));
		assertEquals(Integer.MAX_VALUE, WorldStats.skillTotalRequirement(world(1, 1, US, "-", WorldType.SKILL_TOTAL)));
	}

	@Test
	public void compactNumbers()
	{
		assertEquals("950", PlayerCountOverlay.compact(950));
		assertEquals("71.2k", PlayerCountOverlay.compact(71234));
	}

	@Test
	public void sameCountsSpotsARepeatedUpdate()
	{
		List<World> a = Arrays.asList(world(301, 500, WorldRegion.UNITED_STATES_OF_AMERICA, "-"), world(302, 900, WorldRegion.UNITED_KINGDOM, "-"));
		List<World> same = Arrays.asList(world(302, 900, WorldRegion.UNITED_KINGDOM, "-"), world(301, 500, WorldRegion.UNITED_STATES_OF_AMERICA, "-"));
		List<World> changed = Arrays.asList(world(301, 501, WorldRegion.UNITED_STATES_OF_AMERICA, "-"), world(302, 900, WorldRegion.UNITED_KINGDOM, "-"));
		List<World> fewer = Arrays.asList(world(301, 500, WorldRegion.UNITED_STATES_OF_AMERICA, "-"));
		assertEquals(true, WorldStats.sameCounts(a, same));
		assertEquals(false, WorldStats.sameCounts(a, changed));
		assertEquals(false, WorldStats.sameCounts(a, fewer));
	}
}
