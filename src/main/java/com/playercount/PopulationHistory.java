package com.playercount;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import lombok.Value;
import net.runelite.http.api.worlds.World;

/**
 * Player counts over time for the graph: the OSRS total plus every world's count,
 * so the "current world" graph still works right after a hop. Saved to disk between sessions.
 */
final class PopulationHistory
{
	static final Duration MAX_AGE = Duration.ofHours(6);
	/** At most one stored point per this gap, so fast refreshes don't bloat memory (~720 points max). */
	static final Duration MIN_GAP = Duration.ofSeconds(30);

	@Value
	static class Point
	{
		Instant time;
		int value;
	}

	private static final class Entry
	{
		final Instant time;
		final int total;
		final short[] byWorld; // index = world id, -1 = unknown

		Entry(Instant time, int total, short[] byWorld)
		{
			this.time = time;
			this.total = total;
			this.byWorld = byWorld;
		}

		int players(int world)
		{
			return world >= 0 && world < byWorld.length ? byWorld[world] : -1;
		}
	}

	private static final int VERSION = 1;

	private final Deque<Entry> entries = new ArrayDeque<>();

	synchronized void add(Instant time, List<World> worlds)
	{
		Entry last = entries.peekLast();
		if (last != null && Duration.between(last.time, time).compareTo(MIN_GAP) < 0)
		{
			return;
		}

		int maxId = 0;
		for (World w : worlds)
		{
			maxId = Math.max(maxId, w.getId());
		}
		short[] byWorld = new short[maxId + 1];
		Arrays.fill(byWorld, (short) -1);
		int total = 0;
		for (World w : worlds)
		{
			int p = w.getPlayers();
			byWorld[w.getId()] = (short) Math.max(-1, Math.min(p, Short.MAX_VALUE));
			if (p > 0)
			{
				total += p;
			}
		}
		entries.addLast(new Entry(time, total, byWorld));

		Instant cutoff = time.minus(MAX_AGE);
		while (!entries.isEmpty() && entries.peekFirst().time.isBefore(cutoff))
		{
			entries.removeFirst();
		}
	}

	/** Points within the window, oldest first. worldId -1 means the OSRS total. */
	synchronized List<Point> series(Instant now, Duration window, int worldId)
	{
		Instant from = now.minus(window);
		List<Point> out = new ArrayList<>();
		for (Entry e : entries)
		{
			if (e.time.isBefore(from))
			{
				continue;
			}
			int v = worldId < 0 ? e.total : e.players(worldId);
			if (v >= 0)
			{
				out.add(new Point(e.time, v));
			}
		}
		return out;
	}

	synchronized void clear()
	{
		entries.clear();
	}

	synchronized void write(DataOutputStream out) throws IOException
	{
		out.writeInt(VERSION);
		out.writeInt(entries.size());
		for (Entry e : entries)
		{
			out.writeLong(e.time.getEpochSecond());
			out.writeInt(e.total);
			out.writeShort(e.byWorld.length);
			for (short v : e.byWorld)
			{
				out.writeShort(v);
			}
		}
	}

	/** Replaces the contents with saved history, dropping anything older than {@link #MAX_AGE}. */
	synchronized void read(DataInputStream in, Instant now) throws IOException
	{
		entries.clear();
		if (in.readInt() != VERSION)
		{
			return;
		}
		Instant cutoff = now.minus(MAX_AGE);
		int n = in.readInt();
		for (int i = 0; i < n; i++)
		{
			Instant time = Instant.ofEpochSecond(in.readLong());
			int total = in.readInt();
			short[] byWorld = new short[in.readUnsignedShort()];
			for (int j = 0; j < byWorld.length; j++)
			{
				byWorld[j] = in.readShort();
			}
			if (!time.isBefore(cutoff) && !time.isAfter(now))
			{
				entries.addLast(new Entry(time, total, byWorld));
			}
		}
	}
}
