package com.playercount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

public class AttackableTest
{
	private static final WorldPoint LUMBRIDGE = new WorldPoint(3222, 3218, 0);
	private static final WorldPoint WILD_1 = new WorldPoint(3100, 3523, 0);   // level 1
	private static final WorldPoint WILD_20 = new WorldPoint(3100, 3675, 0);  // level 20
	private static final WorldPoint WILD_DUNGEON_10 = new WorldPoint(3100, 9995, 0);

	@Test
	public void wildernessLevels()
	{
		assertEquals(0, Attackable.wildernessLevel(LUMBRIDGE));
		assertEquals(1, Attackable.wildernessLevel(WILD_1));
		assertEquals(20, Attackable.wildernessLevel(WILD_20));
		assertEquals(10, Attackable.wildernessLevel(WILD_DUNGEON_10));
	}

	@Test
	public void wildernessRangeUsesTheLowerLevel()
	{
		assertTrue(Attackable.canAttack(100, WILD_20, 120, WILD_20, false, false));
		assertFalse(Attackable.canAttack(100, WILD_20, 121, WILD_20, false, false));
		assertFalse(Attackable.canAttack(100, WILD_20, 105, WILD_1, false, false)); // they're in level 1
		assertFalse(Attackable.canAttack(100, LUMBRIDGE, 100, LUMBRIDGE, false, false)); // not in the Wilderness
	}

	@Test
	public void pvpWorldsAddFifteenAndDeadmanIsEveryone()
	{
		assertTrue(Attackable.canAttack(100, LUMBRIDGE, 115, LUMBRIDGE, true, false));
		assertFalse(Attackable.canAttack(100, LUMBRIDGE, 116, LUMBRIDGE, true, false));
		assertTrue(Attackable.canAttack(100, WILD_20, 135, WILD_20, true, false));
		assertTrue(Attackable.canAttack(3, LUMBRIDGE, 126, LUMBRIDGE, false, true));
	}
}
