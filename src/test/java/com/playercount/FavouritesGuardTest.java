package com.playercount;

import static org.junit.Assert.assertEquals;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

public class FavouritesGuardTest
{
	private static Set<Integer> set(Integer... v)
	{
		return new TreeSet<>(Arrays.asList(v));
	}

	@Test
	public void staleSettingsPanelCantUndoAFreshStar()
	{
		FavouritesGuard g = new FavouritesGuard();
		Instant t = Instant.parse("2026-11-12T09:01:12Z");
		g.sidebarChanged(427, true, t);
		// the open panel writes back its old, empty list 6 seconds later
		assertEquals(set(427), g.reconcile(set(), t.plusSeconds(6)));
	}

	@Test
	public void deletingInTheSettingsBoxSticksOnceTheStarIsOld()
	{
		FavouritesGuard g = new FavouritesGuard();
		Instant t = Instant.parse("2026-11-12T09:01:12Z");
		g.sidebarChanged(427, true, t);
		// a minute and more later the user deletes 427 in the settings box
		assertEquals(set(302), g.reconcile(set(302), t.plusSeconds(90)));
	}

	@Test
	public void freshSidebarRemovalAlsoSurvivesAStalePanel()
	{
		FavouritesGuard g = new FavouritesGuard();
		Instant t = Instant.parse("2026-11-12T09:05:00Z");
		g.sidebarChanged(330, false, t);
		assertEquals(set(302), g.reconcile(set(302, 330), t.plusSeconds(10)));
	}
}
