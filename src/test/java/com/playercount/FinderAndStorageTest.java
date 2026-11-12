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
		WorldStats.Criteria byPing = base().favorites(favs).pings(pings).mode(WorldStats.Mode.FAVOURITES_BY_PING).build();
		assertEquals(Arrays.asList(1, 4, 2), ids(WorldStats.rank(WORLDS, byPing))); // unknown ping last, not dropped
		WorldStats.Criteria byPlayers = base().favorites(favs).pings(pings).mode(WorldStats.Mode.FAVOURITES_BY_PLAYERS).build();
		assertEquals(Arrays.asList(2, 4, 1), ids(WorldStats.rank(WORLDS, byPlayers)));
		assertNull(WorldStats.pick(WORLDS, base().mode(WorldStats.Mode.FAVOURITES_BY_PING).build())); // no favourites
	}

	@Test
	public void favouritesBothWeighsPingAndPlayers()
	{
		// 1: 300 players, 30ms   2: 100 players, 120ms   4: 200 players, 40ms
		// scaled: 1 = 1.0 + 0.25 = 1.25   2 = 0.33 + 1.0 = 1.33   4 = 0.67 + 0.33 = 1.0
		java.util.Set<Integer> favs = new HashSet<>(Arrays.asList(1, 2, 4));
		WorldStats.Criteria both = base().favorites(favs).pings(map(1, 30, 2, 120, 4, 40)).mode(WorldStats.Mode.FAVOURITES_BALANCED).build();
		assertEquals(Arrays.asList(4, 1, 2), ids(WorldStats.rank(WORLDS, both)));
	}

	@Test
	public void themedWorldsMatchActivitiesIncludingRiskyOnes()
	{
		List<World> worlds = Arrays.asList(
			themed(307, 400, "Wintertodt"),
			themed(309, 900, "Wintertodt"),
			themed(320, 150, "Tempoross"),
			themed(330, 50, "House Party, Gilded Altar"),
			themed(319, 300, "Last Man Standing", WorldType.LAST_MAN_STANDING),
			themed(325, 500, "PvP world - Wintertodt fans", WorldType.PVP));
		java.util.List<String> themes = Arrays.asList(" wintertodt", "TEMPOROSS", "last man");
		List<World> busiest = WorldStats.rank(worlds, base().themes(themes).mode(WorldStats.Mode.THEMED_BUSIEST).build());
		assertEquals(Arrays.asList(309, 325, 307, 319, 320), ids(busiest)); // risky worlds allowed in Themed (warned)
		assertEquals("PvP", WorldStats.risk(worlds.get(5)));
		assertNull(WorldStats.risk(worlds.get(4))); // LMS is a safe minigame
		assertNull(WorldStats.risk(worlds.get(0)));
		Map<Integer, Integer> pings = map(307, 20, 309, 200, 320, 40, 319, 60);
		assertEquals(307, WorldStats.pick(worlds, base().themes(themes).pings(pings).mode(WorldStats.Mode.THEMED_BY_PING).build()).getId());
		// Both: 307 = 0.1 ping + 0.56 quietness = 0.66 beats 309 = 1.0 + 0 = 1.0
		assertEquals(307, WorldStats.pick(worlds, base().themes(themes).pings(pings).mode(WorldStats.Mode.THEMED_BALANCED).build()).getId());
		assertNull(WorldStats.pick(worlds, base().mode(WorldStats.Mode.THEMED_BUSIEST).build())); // no themes
		// Outside Themed mode special worlds are still never suggested
		assertTrue(WorldStats.rank(worlds, base().build()).stream().noneMatch(w -> w.getId() == 319 || w.getId() == 325));
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
