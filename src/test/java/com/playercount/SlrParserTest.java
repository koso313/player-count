package com.playercount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;
import org.junit.Test;

public class SlrParserTest
{
	/** A real slr.ws download from 2026-11-11. */
	private static List<World> sample() throws IOException
	{
		try (InputStream in = SlrParserTest.class.getResourceAsStream("slr-sample.bin"))
		{
			return SlrParser.parse(in.readAllBytes());
		}
	}

	private static World find(List<World> worlds, int id)
	{
		return worlds.stream().filter(w -> w.getId() == id).findFirst().orElseThrow(AssertionError::new);
	}

	@Test
	public void parsesEveryWorld() throws IOException
	{
		List<World> worlds = sample();
		assertEquals(352, worlds.size());
		assertEquals(115578, WorldStats.totals(worlds).getTotal());
		assertEquals(107803, WorldStats.totals(worlds).getMembers());
	}

	@Test
	public void parsesFields() throws IOException
	{
		List<World> worlds = sample();
		World w302 = find(worlds, 302);
		assertEquals(1799, w302.getPlayers());
		assertEquals("Trade - Members", w302.getActivity());
		assertEquals("oldschool2a.runescape.com", w302.getAddress());
		assertEquals(WorldRegion.UNITED_KINGDOM, w302.getRegion());
		assertTrue(w302.getTypes().contains(WorldType.MEMBERS));

		World w301 = find(worlds, 301);
		assertEquals(994, w301.getPlayers());
		assertEquals(WorldRegion.UNITED_STATES_OF_AMERICA, w301.getRegion());
		assertFalse(w301.getTypes().contains(WorldType.MEMBERS));
	}

	@Test(expected = IllegalArgumentException.class)
	public void truncatedDataFails()
	{
		SlrParser.parse(new byte[]{0, 0, 0, 10, 0, 5, 1});
	}
}
