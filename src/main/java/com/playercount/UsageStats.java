package com.playercount;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.http.api.worlds.World;

/**
 * Learns how busy each world usually is at each hour of the day (UTC), so the finder
 * can prefer worlds that are reliably quiet rather than just quiet this minute.
 */
final class UsageStats
{
	static final int HOURS = 24;
	/** Each world-hour bucket takes at most one sample per this many seconds. */
	static final long SAMPLE_GAP_S = 600;
	/** Buckets need this many samples before they're trusted. */
	static final int MIN_SAMPLES = 3;
	/** Past this many samples a bucket is halved, so older days fade out. */
	static final int MAX_SAMPLES = 60;
	private static final int VERSION = 1;

	private static final class Bucket
	{
		double sum;
		int count;
		long lastSample;
	}

	private final Map<Integer, Bucket[]> worlds = new HashMap<>();

	synchronized void add(Instant now, List<World> list)
	{
		int hour = now.atZone(ZoneOffset.UTC).getHour();
		long t = now.getEpochSecond();
		for (World w : list)
		{
			if (w.getPlayers() < 0)
			{
				continue;
			}
			Bucket[] hours = worlds.computeIfAbsent(w.getId(), k -> new Bucket[HOURS]);
			Bucket b = hours[hour];
			if (b == null)
			{
				b = new Bucket();
				hours[hour] = b;
			}
			if (b.count > 0 && t - b.lastSample < SAMPLE_GAP_S)
			{
				continue;
			}
			b.sum += w.getPlayers();
			b.count++;
			b.lastSample = t;
			if (b.count > MAX_SAMPLES)
			{
				b.sum /= 2;
				b.count /= 2;
			}
		}
	}

	/** Typical player count for the world at this hour, or -1 if not learned yet. */
	synchronized int usual(int world, Instant now)
	{
		Bucket[] hours = worlds.get(world);
		if (hours == null)
		{
			return -1;
		}
		Bucket b = hours[now.atZone(ZoneOffset.UTC).getHour()];
		return b == null || b.count < MIN_SAMPLES ? -1 : (int) Math.round(b.sum / b.count);
	}

	/** Typical counts for every world at this hour (only learned ones). */
	synchronized Map<Integer, Integer> usualAll(Instant now)
	{
		Map<Integer, Integer> out = new HashMap<>();
		for (Integer id : worlds.keySet())
		{
			int u = usual(id, now);
			if (u >= 0)
			{
				out.put(id, u);
			}
		}
		return out;
	}

	synchronized void write(DataOutputStream out) throws IOException
	{
		out.writeInt(VERSION);
		out.writeInt(worlds.size());
		for (Map.Entry<Integer, Bucket[]> e : worlds.entrySet())
		{
			out.writeShort(e.getKey());
			for (Bucket b : e.getValue())
			{
				out.writeFloat(b == null ? 0 : (float) b.sum);
				out.writeShort(b == null ? 0 : b.count);
				out.writeLong(b == null ? 0 : b.lastSample);
			}
		}
	}

	synchronized void read(DataInputStream in) throws IOException
	{
		worlds.clear();
		if (in.readInt() != VERSION)
		{
			return;
		}
		int n = in.readInt();
		for (int i = 0; i < n; i++)
		{
			int id = in.readUnsignedShort();
			Bucket[] hours = new Bucket[HOURS];
			for (int h = 0; h < HOURS; h++)
			{
				float sum = in.readFloat();
				int count = in.readUnsignedShort();
				long last = in.readLong();
				if (count > 0)
				{
					Bucket b = new Bucket();
					b.sum = sum;
					b.count = count;
					b.lastSample = last;
					hours[h] = b;
				}
			}
			worlds.put(id, hours);
		}
	}
}
