package com.playercount;

import net.runelite.api.coords.WorldPoint;

/**
 * Whether another player can attack you, using the game's combat-level rules: in the Wilderness the
 * allowed level difference is the Wilderness level; PvP worlds add 15 (and allow it outside the
 * Wilderness); on Deadman anyone can. Kept free of client state so it can be tested.
 */
final class Attackable
{
	private Attackable()
	{
	}

	/** Wilderness level at a tile (surface or underground), or 0 outside it. */
	static int wildernessLevel(WorldPoint p)
	{
		if (p == null)
		{
			return 0;
		}
		int x = p.getX();
		int y = p.getY();
		// Surface Wilderness
		if (x >= 2944 && x <= 3391 && y >= 3520 && y <= 3967)
		{
			return (y - 3520) / 8 + 1;
		}
		// Wilderness dungeons sit 6400 tiles north of the surface they're under
		if (x >= 2944 && x <= 3455 && y >= 9920 && y <= 10367)
		{
			return (y - 9920) / 8 + 1;
		}
		return 0;
	}

	static boolean canAttack(int myCombat, WorldPoint me, int theirCombat, WorldPoint them, boolean pvpWorld, boolean deadman)
	{
		if (deadman)
		{
			return true;
		}
		int myWild = wildernessLevel(me);
		int theirWild = wildernessLevel(them);
		int range;
		if (pvpWorld)
		{
			range = 15 + Math.min(myWild, theirWild);
		}
		else if (myWild > 0 && theirWild > 0)
		{
			range = Math.min(myWild, theirWild);
		}
		else
		{
			return false;
		}
		return Math.abs(myCombat - theirCombat) <= range;
	}
}
