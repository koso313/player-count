package com.playercount;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.worlds.World;

/**
 * Measures ping as the time to open a TCP connection to a world's game port
 * (no special permissions needed, unlike ICMP). The connection is closed straight away.
 * Runs on its own thread so slow connects never hold up the client.
 */
@Slf4j
class PingService
{
	private static final int GAME_PORT = 43594;
	private static final int TIMEOUT_MS = 2000;
	/** How often favourites are re-pinged. */
	private static final long FAVOURITES_EVERY_S = 20;
	/** How often every world is re-pinged (one at a time, spread out). */
	private static final long SWEEP_EVERY_S = 120;
	/** How often to check whether a sweep is due (also covers the world list arriving after start). */
	private static final long SWEEP_CHECK_S = 5;
	private static final long SWEEP_SPACING_MS = 100;

	private final Map<Integer, Integer> pings = new ConcurrentHashMap<>();
	private ScheduledExecutorService thread;
	private volatile long lastSweep;
	private final java.util.Queue<World> sweepQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
	private volatile int sweepDone;
	private volatile int lastCurrent;
	private volatile long lastCurrentAt;

	/**
	 * @param worlds  latest world list (may be empty before the first download)
	 * @param current id of the world the player is on, or a non-positive value if none
	 * @param first   worlds to ping first in each sweep, and more often (favourites)
	 * @param currentEverySeconds how often to re-ping the current world
	 */
	synchronized void start(Supplier<List<World>> worlds, IntSupplier current, Supplier<Set<Integer>> first, int currentEverySeconds)
	{
		if (thread != null)
		{
			return;
		}
		// Two threads, so a long all-worlds sweep never holds up the current world's ping.
		thread = Executors.newScheduledThreadPool(2, r ->
		{
			Thread t = new Thread(r, "player-count-ping");
			t.setDaemon(true);
			return t;
		});
		// Current world: checked every second, pinged straight away when it changes (login or hop),
		// otherwise every currentEverySeconds.
		long everyMs = Math.max(1, currentEverySeconds) * 1000L;
		lastCurrent = 0;
		lastCurrentAt = 0;
		thread.scheduleWithFixedDelay(() -> safely(() ->
		{
			int id = current.getAsInt();
			long now = System.currentTimeMillis();
			if (id <= 0 || (id == lastCurrent && now - lastCurrentAt < everyMs))
			{
				return;
			}
			for (World w : worlds.get())
			{
				if (w.getId() == id)
				{
					lastCurrent = id;
					lastCurrentAt = now;
					measure(w);
					break;
				}
			}
		}), 1, 1, TimeUnit.SECONDS);
		thread.scheduleWithFixedDelay(() -> safely(() ->
		{
			Set<Integer> favs = first.get();
			for (World w : worlds.get())
			{
				if (favs.contains(w.getId()))
				{
					measure(w);
				}
			}
		}), FAVOURITES_EVERY_S, FAVOURITES_EVERY_S, TimeUnit.SECONDS);
		// All-worlds sweep: when one is due, queue the worlds (favourites first), then a separate task pings
		// one queued world every SWEEP_SPACING_MS. Spacing comes from scheduling, never from sleeping.
		lastSweep = 0;
		sweepQueue.clear();
		thread.scheduleWithFixedDelay(() -> safely(() ->
		{
			List<World> list = new ArrayList<>(worlds.get());
			long now = System.currentTimeMillis();
			// Wait for the world list, then sweep straight away and every SWEEP_EVERY_S after.
			if (!sweepQueue.isEmpty() || list.isEmpty() || (lastSweep != 0 && now - lastSweep < SWEEP_EVERY_S * 1000))
			{
				return;
			}
			lastSweep = now;
			sweepDone = 0;
			Set<Integer> priority = first.get();
			Collections.shuffle(list);
			list.sort((a, b) -> Boolean.compare(!priority.contains(a.getId()), !priority.contains(b.getId())));
			for (World w : list)
			{
				if (w.getPlayers() >= 0)
				{
					sweepQueue.add(w);
				}
			}
		}), 1, SWEEP_CHECK_S, TimeUnit.SECONDS);
		thread.scheduleWithFixedDelay(() -> safely(() ->
		{
			World w = sweepQueue.poll();
			if (w == null)
			{
				return;
			}
			measure(w);
			sweepDone++;
			if (sweepQueue.isEmpty())
			{
				log.debug("Pinged {} worlds in {}s ({} reachable)", sweepDone, (System.currentTimeMillis() - lastSweep) / 1000,
					pings.values().stream().filter(p -> p >= 0).count());
			}
		}), SWEEP_SPACING_MS, SWEEP_SPACING_MS, TimeUnit.MILLISECONDS);
	}

	synchronized void stop()
	{
		if (thread != null)
		{
			thread.shutdownNow();
			thread = null;
		}
		sweepQueue.clear();
		pings.clear();
	}

	/** Last measured ping in ms, or -1 if unknown/unreachable. */
	int ping(int world)
	{
		return pings.getOrDefault(world, -1);
	}

	/** Read-only view of every known ping. */
	Map<Integer, Integer> all()
	{
		return Collections.unmodifiableMap(pings);
	}

	private void measure(World w)
	{
		long start = System.nanoTime();
		try (Socket socket = new Socket())
		{
			socket.connect(new InetSocketAddress(w.getAddress(), GAME_PORT), TIMEOUT_MS);
			int ms = (int) Math.max(1, (System.nanoTime() - start) / 1_000_000);
			pings.put(w.getId(), ms);
		}
		catch (IOException e)
		{
			pings.put(w.getId(), -1);
		}
	}

	private static void safely(Runnable r)
	{
		try
		{
			r.run();
		}
		catch (RuntimeException e)
		{
			log.debug("Ping task failed", e);
		}
	}
}
