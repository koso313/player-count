package com.playercount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class FinderAndStorageTest
{
	private static World world(int id, int players)
	{
		return World.builder().id(id).players(players).region(WorldRegion.UNITED_KINGDOM).activity("-")
			.address("a").types(EnumSet.of(WorldType.MEMBERS)).build();
	}

	private static final List<World> WORLDS = Arrays.asList(world(1, 300), world(2, 100), world(3, 50), world(4, 200));

	private static Map<Integer, Integer> map(int... kv)
	{
		Map<Integer, Integer> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put(kv[i], kv[i + 1]);
		}
		return m;
	}

	private static WorldStats.Criteria.CriteriaBuilder base()
	{
		return WorldStats.Criteria.builder().currentWorld(99).members(true).totalLevel(2000);
	}

	private static List<Integer> ids(List<World> worlds)
	{
		return worlds.stream().map(World::getId).collect(Collectors.toList());
	}

	@Test
	public void lowestPingSkipsUnknownAndBreaksTiesByPlayers()
	{
		Map<Integer, Integer> pings = map(1, 20, 2, 40, 4, 20); // world 3 unknown
		List<World> ranked = WorldStats.rank(WORLDS, base().mode(WorldStats.Mode.LOWEST_PING).pings(pings).build());
		assertEquals(Arrays.asList(4, 1, 2), ids(ranked));
	}

	@Test
	public void maxPingFiltersQuietestToo()
	{
		Map<Integer, Integer> pings = map(1, 20, 2, 140, 3, 200, 4, 60);
		assertEquals(4, WorldStats.pick(WORLDS, base().pings(pings).maxPing(100).build()).getId());
		assertEquals(3, WorldStats.pick(WORLDS, base().pings(pings).build()).getId()); // no limit
	}

	@Test
	public void favouritesModeCyclesInChosenOrder()
	{
		java.util.Set<Integer> favs = new HashSet<>(Arrays.asList(1, 2, 4));
		Map<Integer, Integer> pings = map(1, 30, 4, 80); // world 2 not pinged yet
		WorldStats.Criteria byPing = base().favorites(favs).pings(pings).theme(WorldTheme.FAVOURITES).mode(WorldStats.Mode.THEMED_BY_PING).build();
		assertEquals(Arrays.asList(1, 4, 2), ids(WorldStats.rank(WORLDS, byPing))); // unknown ping last, not dropped
		WorldStats.Criteria byPlayers = base().favorites(favs).pings(pings).theme(WorldTheme.FAVOURITES).mode(WorldStats.Mode.THEMED_QUIETEST).build();
		assertEquals(Arrays.asList(2, 4, 1), ids(WorldStats.rank(WORLDS, byPlayers)));
		assertNull(WorldStats.pick(WORLDS, base().theme(WorldTheme.FAVOURITES).mode(WorldStats.Mode.THEMED_BY_PING).build())); // no favourites
	}

	@Test
	public void favouritesBothWeighsPingAndPlayers()
	{
		// 1: 300 players, 30ms   2: 100 players, 120ms   4: 200 players, 40ms
		// scaled: 1 = 1.0 + 0.25 = 1.25   2 = 0.33 + 1.0 = 1.33   4 = 0.67 + 0.33 = 1.0
		java.util.Set<Integer> favs = new HashSet<>(Arrays.asList(1, 2, 4));
		WorldStats.Criteria both = base().favorites(favs).pings(map(1, 30, 2, 120, 4, 40)).theme(WorldTheme.FAVOURITES).mode(WorldStats.Mode.THEMED_QUIET_BALANCED).build();
		assertEquals(Arrays.asList(4, 1, 2), ids(WorldStats.rank(WORLDS, both)));
	}

	@Test
	public void themedWorldsRotateThroughTheChosenActivity()
	{
		List<World> worlds = Arrays.asList(
			themed(307, 400, "Wintertodt"),
			themed(309, 900, "Wintertodt"),
			themed(395, 600, "Hardcore World - Wintertodt"),
			themed(320, 150, "Tempoross"),
			themed(330, 50, "House Party, Gilded Altar"),
			themed(319, 300, "LMS Casual", WorldType.LAST_MAN_STANDING),
			themed(325, 500, "PvP World", WorldType.PVP));
		WorldStats.Criteria.CriteriaBuilder wt = base().theme(WorldTheme.WINTERTODT).mode(WorldStats.Mode.THEMED_BUSIEST);
		assertEquals(Arrays.asList(309, 307), ids(WorldStats.rank(worlds, wt.build()))); // hardcore world skipped
		Map<Integer, Integer> pings = map(307, 20, 309, 200);
		assertEquals(307, WorldStats.pick(worlds, wt.pings(pings).mode(WorldStats.Mode.THEMED_BY_PING).build()).getId());
		assertEquals(307, WorldStats.pick(worlds, wt.pings(pings).mode(WorldStats.Mode.THEMED_BALANCED).build()).getId());
		assertEquals(319, WorldStats.pick(worlds, base().theme(WorldTheme.LAST_MAN_STANDING).mode(WorldStats.Mode.THEMED_BUSIEST).build()).getId());
		assertEquals(330, WorldStats.pick(worlds, base().theme(WorldTheme.HOUSE_PARTY).mode(WorldStats.Mode.THEMED_BUSIEST).build()).getId());
		// Risky worlds only through their theme, and flagged
		World pvp = WorldStats.pick(worlds, base().theme(WorldTheme.PVP).mode(WorldStats.Mode.THEMED_BUSIEST).build());
		assertEquals(325, pvp.getId());
		assertEquals("PvP", WorldStats.risk(pvp));
		assertEquals("LMS", WorldStats.risk(worlds.get(5))); // PvP-themed: orange, not deadly
		assertTrue(!WorldStats.deadly(worlds.get(5)));
		assertTrue(WorldStats.pvpThemed(worlds.get(5))); // LMS pulses red
		World wildy = themed(369, 699, "Wilderness PK - Members");
		assertEquals("Wildy PK", WorldStats.risk(wildy));
		assertTrue(!WorldStats.deadly(wildy)); // only the Wilderness is dangerous
		assertTrue(WorldStats.pvpThemed(wildy));
		assertTrue(WorldStats.deadly(pvp));
		assertTrue(WorldTheme.WILDERNESS_PK.matches(wildy));
		assertTrue(!WorldTheme.PVP.matches(wildy));
		assertTrue(WorldStats.rank(worlds, base().build()).stream().noneMatch(w -> w.getId() == 319 || w.getId() == 325));
		assertNull(WorldStats.pick(worlds, base().mode(WorldStats.Mode.THEMED_BUSIEST).build())); // no theme chosen
	}

	@Test
	public void everyLiveActivityHasATheme() throws java.io.IOException
	{
		List<World> live;
		try (java.io.InputStream in = getClass().getResourceAsStream("slr-sample.bin"))
		{
			live = SlrParser.parse(in.readAllBytes());
		}
		java.util.Set<String> uncovered = new java.util.TreeSet<>();
		for (World w : live)
		{
			String a = w.getActivity();
			if (a == null || a.trim().isEmpty() || "-".equals(a.trim()))
			{
				continue;
			}
			if (Arrays.stream(WorldTheme.values()).noneMatch(t -> t.matches(w)))
			{
				uncovered.add(a);
			}
		}
		assertTrue("Activities no theme covers: " + uncovered, uncovered.isEmpty());
	}

	@Test
	public void skillTotalThemesMatchExactly()
	{
		World w500 = themed(301, 10, "500 skill total", WorldType.SKILL_TOTAL);
		World w1500 = themed(302, 10, "1500 skill total", WorldType.SKILL_TOTAL);
		assertTrue(WorldTheme.SKILL_TOTAL_500.matches(w500));
		assertTrue(!WorldTheme.SKILL_TOTAL_500.matches(w1500)); // 500 is inside "1500"
		assertTrue(WorldTheme.SKILL_TOTAL_1500.matches(w1500));
		assertTrue(WorldTheme.SKILL_TOTAL.matches(w500) && WorldTheme.SKILL_TOTAL.matches(w1500));
		assertEquals(2200, WorldTheme.SKILL_TOTAL_2200.requiredTotal());
		// The finder still respects your total level
		World w2200 = themed(303, 10, "2200 skill total", WorldType.SKILL_TOTAL);
		WorldStats.Criteria.CriteriaBuilder c = base().theme(WorldTheme.SKILL_TOTAL_2200).mode(WorldStats.Mode.THEMED_BUSIEST).allowSkillTotal(true);
		assertNull(WorldStats.pick(Arrays.asList(w2200), c.totalLevel(2100).build()));
		assertEquals(303, WorldStats.pick(Arrays.asList(w2200), c.totalLevel(2250).build()).getId());
	}

	@Test
	public void pvpHighRiskIsDeadlyAndNamedSo()
	{
		World w = themed(579, 62, "PvP World - High Risk", WorldType.PVP, WorldType.HIGH_RISK);
		assertEquals("PvP high risk", WorldStats.risk(w));
		assertTrue(WorldStats.deadly(w));
		World arena = themed(348, 300, "PvP Arena (Legacy Duels)");
		assertEquals("PvP Arena", WorldStats.risk(arena)); // PvP-themed: orange
		assertTrue(!WorldStats.deadly(arena));
		assertTrue(WorldStats.pvpThemed(arena));
		World leagues = themed(400, 900, "Leagues", WorldType.SEASONAL);
		assertTrue(!WorldStats.pvpThemed(leagues)); // separate account: orange
	}

	@Test
	public void themesListFavouritesThenTotalsDescendingThenAToZ()
	{
		WorldTheme[] all = WorldTheme.values();
		assertEquals(WorldTheme.FAVOURITES, all[0]);
		int i = 1;
		int last = Integer.MAX_VALUE;
		while (all[i].requiredTotal() > 0)
		{
			assertTrue(all[i].requiredTotal() < last);
			last = all[i].requiredTotal();
			i++;
		}
		assertEquals(WorldTheme.SKILL_TOTAL, all[i++]);
		for (; i + 1 < all.length; i++)
		{
			assertTrue(all[i] + " should come before " + all[i + 1],
				all[i].toString().compareToIgnoreCase(all[i + 1].toString()) < 0);
		}
	}

	@Test
	public void everyThemeMatchesAtLeastOneLiveWorld() throws java.io.IOException
	{
		// Guards against typos in theme terms: the sample is a real world list from 2026-11-11.
		List<World> live;
		try (java.io.InputStream in = getClass().getResourceAsStream("slr-sample.bin"))
		{
			live = SlrParser.parse(in.readAllBytes());
		}
		java.util.Set<WorldTheme> unseen = EnumSet.noneOf(WorldTheme.class);
		for (WorldTheme t : WorldTheme.values())
		{
			if (live.stream().noneMatch(t::matches))
			{
				unseen.add(t);
			}
		}
		// A few are seasonal or rotate; everything else should be present
		unseen.remove(WorldTheme.LEAGUES);
		unseen.remove(WorldTheme.FAVOURITES); // not an activity
		assertTrue("Themes with no matching world: " + unseen, unseen.isEmpty());
	}

	private static World themed(int id, int players, String activity, WorldType... extra)
	{
		EnumSet<WorldType> types = EnumSet.of(WorldType.MEMBERS);
		types.addAll(Arrays.asList(extra));
		return World.builder().id(id).players(players).region(WorldRegion.UNITED_KINGDOM).activity(activity)
			.address("a").types(types).build();
	}

	@Test
	public void widthsIgnoreWhichDigitsAndOnlyGrow()
	{
		java.awt.FontMetrics fm = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)
			.createGraphics().getFontMetrics(net.runelite.client.ui.FontManager.getRunescapeFont());
		assertEquals(OverlayLayout.textWidth(fm, "1,111"), OverlayLayout.textWidth(fm, "8,808"));
		OverlayLayout.WidthMemory m = new OverlayLayout.WidthMemory();
		m.begin("a");
		assertEquals(50, m.stable("x", 50));
		assertEquals(50, m.stable("x", 40)); // never shrinks
		assertEquals(60, m.stable("x", 60));
		m.begin("b"); // something else shown: start over
		assertEquals(40, m.stable("x", 40));
	}

	@Test
	public void preferUsualBlendsTypicalCount()
	{
		// World 3 is quiet now (50) but usually packed (1000); world 2 is usually quiet.
		Map<Integer, Integer> usual = map(3, 1000, 2, 80);
		assertEquals(3, WorldStats.pick(WORLDS, base().usual(usual).build()).getId());
		assertEquals(2, WorldStats.pick(WORLDS, base().usual(usual).preferUsual(true).build()).getId());
	}

	@Test
	public void usageStatsNeedSamplesAndSpacing() throws IOException
	{
		UsageStats u = new UsageStats();
		Instant t = Instant.parse("2026-11-11T20:00:00Z");
		u.add(t, WORLDS);
		u.add(t.plusSeconds(60), Collections.singletonList(world(1, 900))); // too soon, ignored
		assertEquals(-1, u.usual(1, t));
		u.add(t.plusSeconds(UsageStats.SAMPLE_GAP_S), Collections.singletonList(world(1, 400)));
		u.add(t.plusSeconds(2 * UsageStats.SAMPLE_GAP_S), Collections.singletonList(world(1, 500)));
		assertEquals(400, u.usual(1, t));
		assertEquals(-1, u.usual(1, t.plus(Duration.ofHours(1)))); // different hour

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		u.write(new DataOutputStream(bytes));
		UsageStats copy = new UsageStats();
		copy.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
		assertEquals(400, copy.usual(1, t));
		assertEquals(Collections.singletonMap(1, 400), copy.usualAll(t));
	}

	@Test
	public void historySurvivesSaveAndDropsOldPoints() throws IOException
	{
		PopulationHistory h = new PopulationHistory();
		Instant t = Instant.parse("2026-11-11T10:00:00Z");
		h.add(t, WORLDS);
		h.add(t.plus(Duration.ofHours(5)), WORLDS);

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		h.write(new DataOutputStream(bytes));
		PopulationHistory loaded = new PopulationHistory();
		Instant later = t.plus(Duration.ofHours(7)); // first point is now over 6h old
		loaded.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), later);
		List<PopulationHistory.Point> pts = loaded.series(later, Duration.ofHours(6), -1);
		assertEquals(1, pts.size());
		assertEquals(650, pts.get(0).getValue());
		assertEquals(50, loaded.series(later, Duration.ofHours(6), 3).get(0).getValue());
	}

	@Test
	public void favouritesParseIgnoresJunk2()
	{
		// Stars and typed worlds are merged; spacing and order in the box don't matter
		java.util.Set<Integer> merged = new java.util.TreeSet<>(PlayerCountPlugin.parseFavorites("427"));
		merged.addAll(PlayerCountPlugin.parseFavorites(" 330 ,302"));
		assertEquals(Arrays.asList(302, 330, 427), new java.util.ArrayList<>(merged));
	}

	@Test
	public void favouritesParseIgnoresJunk()
	{
		assertEquals(new HashSet<>(Arrays.asList(302, 330)), PlayerCountPlugin.parseFavorites("302, x,330,,"));
		assertTrue(PlayerCountPlugin.parseFavorites(null).isEmpty());
	}

	@Test
	public void globeIconIsTheRequestedSize()
	{
		assertEquals(16, Icons.globe(16).getWidth());
		assertEquals(48, Icons.globe(48).getHeight());
	}
}
