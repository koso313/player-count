package com.playercount;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a just-made sidebar change from being undone by a RuneLite settings panel that was opened earlier
 * and writes its old Favourite worlds text back. Only changes from the last minute are protected; anything
 * else typed in the settings box is taken as the user's edit (removals included).
 */
final class FavouritesGuard
{
	static final Duration WINDOW = Duration.ofSeconds(60);

	private final Map<Integer, Instant> added = new ConcurrentHashMap<>();
	private final Map<Integer, Instant> removed = new ConcurrentHashMap<>();

	void sidebarChanged(int world, boolean on, Instant now)
	{
		(on ? added : removed).put(world, now);
		(on ? removed : added).remove(world);
	}

	/** The list as it should be after a settings-box write; equal to {@code written} unless it undid a fresh change. */
	Set<Integer> reconcile(Set<Integer> written, Instant now)
	{
		Instant cutoff = now.minus(WINDOW);
		Set<Integer> out = new TreeSet<>(written);
		added.forEach((w, t) ->
		{
			if (t.isAfter(cutoff))
			{
				out.add(w);
			}
		});
		removed.forEach((w, t) ->
		{
			if (t.isAfter(cutoff))
			{
				out.remove(w);
			}
		});
		return out;
	}
}
