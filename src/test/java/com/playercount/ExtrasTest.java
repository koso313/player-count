package com.playercount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class ExtrasTest
{
	private static World world(int id, int players, WorldRegion region, String activity, WorldType... types)
	{
		EnumSet<WorldType> set = EnumSet.noneOf(WorldType.class);
		set.addAll(Arrays.asList(types));
		return World.builder().id(id).players(players).region(region).activity(activity).address("a").types(set).build();
	}

	private static final List<World> WORLDS = Arrays.asList(
		world(301, 100, WorldRegion.UNITED_STATES_OF_AMERICA, "Trade - Free"),
		world(307, 400, WorldRegion.UNITED_STATES_OF_AMERICA, "Wintertodt", WorldType.MEMBERS),
		world(309, 250, WorldRegion.UNITED_KINGDOM, "Wintertodt", WorldType.MEMBERS),
		world(310, 50, WorldRegion.UNITED_KINGDOM, "-", WorldType.MEMBERS),
		world(311, 60, WorldRegion.GERMANY, "-", WorldType.MEMBERS));

	@Test
	public void regionTotalsBusiestFirst()
	{
		Map<WorldRegion, Integer> r = WorldStats.regionTotals(WORLDS);
		assertEquals(Arrays.asList(WorldRegion.UNITED_STATES_OF_AMERICA, WorldRegion.UNITED_KINGDOM, WorldRegion.GERMANY),
			Arrays.asList(r.keySet().toArray()));
		assertEquals(500, (int) r.get(WorldRegion.UNITED_STATES_OF_AMERICA));
	}

	@Test
	public void activitySearchIsCaseInsensitiveAndPicksBusiest()
	{
		assertEquals(307, WorldStats.busiestActivity(WORLDS, " wintertodt ").getId());
		assertNull(WorldStats.busiestActivity(WORLDS, "Tempoross"));
	}

	@Test
	public void finderSkipsExcludedWorlds()
	{
		WorldStats.Criteria.CriteriaBuilder c = WorldStats.Criteria.builder().currentWorld(1).members(true).totalLevel(2000);
		assertEquals(310, WorldStats.pick(WORLDS, c.build()).getId());
		assertEquals(311, WorldStats.pick(WORLDS, c.excluded(Collections.singleton(310)).build()).getId());
	}

	@Test
	public void dailyRecordsTrackPeakLowAndResetAtUtcMidnight()
	{
		DailyRecords r = new DailyRecords();
		Instant t = Instant.parse("2026-11-11T10:00:00Z");
		assertTrue(r.update(100_000, t));
		assertTrue(r.update(120_000, t.plusSeconds(60)));
		assertTrue(r.update(90_000, t.plusSeconds(120)));
		assertFalse(r.update(110_000, t.plusSeconds(180)));
		assertEquals(120_000, r.getPeak());
		assertEquals(90_000, r.getLow());

		DailyRecords copy = DailyRecords.parse(r.serialize());
		assertEquals(r.getPeak(), copy.getPeak());
		assertEquals(r.getLowAt(), copy.getLowAt());

		Instant nextDay = Instant.parse("2026-11-12T00:00:30Z");
		assertTrue(r.update(80_000, nextDay));
		assertEquals(80_000, r.getPeak());
		assertTrue(r.hasData(nextDay));
		assertFalse(DailyRecords.parse("garbage").hasData(nextDay));
	}

	@Test
	public void historyKeepsTotalsAndPerWorldCountsAtThirtySecondSpacing()
	{
		PopulationHistory h = new PopulationHistory();
		Instant t = Instant.parse("2026-11-11T10:00:00Z");
		h.add(t, WORLDS);
		h.add(t.plusSeconds(5), WORLDS); // too soon, ignored
		h.add(t.plusSeconds(30), Collections.singletonList(world(310, 70, WorldRegion.UNITED_KINGDOM, "-")));

		List<PopulationHistory.Point> total = h.series(t.plusSeconds(30), Duration.ofHours(1), -1);
		assertEquals(2, total.size());
		assertEquals(860, total.get(0).getValue());
		assertEquals(70, total.get(1).getValue());

		List<PopulationHistory.Point> w307 = h.series(t.plusSeconds(30), Duration.ofHours(1), 307);
		assertEquals(1, w307.size()); // 307 missing from the second sample
		assertEquals(400, w307.get(0).getValue());

		h.add(t.plus(Duration.ofHours(7)), WORLDS);
		assertEquals(1, h.series(t.plus(Duration.ofHours(7)), Duration.ofHours(6), -1).size());
	}
}
