package com.playercount;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Friend;
import net.runelite.api.FriendContainer;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.Notifier;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Notification;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.WorldsFetch;
import net.runelite.client.game.WorldService;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.Text;
import net.runelite.client.util.WorldUtil;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "Player Count",
	description = "Live player counts for your world and all of OSRS, a world finder with ping, a sidebar world list, alerts and population history",
	tags = {"player", "count", "population", "world", "hop", "hopper", "ping", "online", "quiet", "busy", "favourite"}
)
public class PlayerCountPlugin extends Plugin
{
	/** The +/- figures compare against the sample from about this long ago. */
	static final Duration CHANGE_WINDOW = Duration.ofMinutes(1);
	/** RuneLite's (10-minute cached) list is only used if the live list hasn't loaded for this long. */
	private static final Duration LIVE_STALE_AFTER = Duration.ofMinutes(2);
	private static final int MAX_HOP_ATTEMPTS = 5;
	/** Ticks to ignore nearby-player changes after a login, hop or map load while players stream in. */
	private static final int NEARBY_SETTLE_TICKS = 3;
	private static final int MAX_ACTIVITY_TERMS = 6;
	private static final String RECORDS_KEY = "dailyRecords";
	/** Seconds after the last hit (either way) that you still count as in combat. */
	private static final long COMBAT_LINGER_S = 8;
	/** The next-world hotkey forgets where it's been after this long without a press. */
	private static final Duration CYCLE_RESET = Duration.ofMinutes(20);
	private static final long SAVE_EVERY_S = 300;
	private static final String HISTORY_FILE = "history.dat";
	private static final String USAGE_FILE = "usage.dat";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private WorldService worldService;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private PlayerCountOverlay overlay;

	@Inject
	private PlayerCountConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private MouseManager mouseManager;

	/** Left-clicks on the overlay's world lines hop there. */
	private final MouseAdapter overlayClicks = new MouseAdapter()
	{
		@Override
		public java.awt.event.MouseEvent mousePressed(java.awt.event.MouseEvent e)
		{
			return overlay.handleClick(e);
		}
	};

	@Value
	private static class Sample
	{
		Instant time;
		WorldResult result;
		WorldStats.Totals totals;
	}

	// Set from the HTTP thread, read anywhere.
	private volatile Instant lastLiveFetch;
	private final AtomicBoolean fetchInFlight = new AtomicBoolean();

	// Everything below is only touched on the client thread.
	/** Oldest first; the head is the +/- baseline, the tail is the latest. */
	private final Deque<Sample> history = new ArrayDeque<>();
	@Getter
	private final PopulationHistory history6h = new PopulationHistory();
	@Getter
	private DailyRecords records = new DailyRecords();
	private Map<WorldRegion, Integer> regionTotals = Collections.emptyMap();
	private final Map<Integer, Instant> visited = new HashMap<>();

	@Getter
	private int nearby = -1;
	@Getter
	private int friendsHere = -1;
	@Getter
	private int clanHere = -1;
	@Getter
	private World suggestion;

	// Alert state
	private int alertWorld = -1;
	private boolean wasBusy;
	private boolean wasQuiet;
	private int nearbySettle = NEARBY_SETTLE_TICKS;

	private net.runelite.api.World hopTarget;
	private int hopAttempts;
	private ScheduledFuture<?> refreshTask;

	private WorldListPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> saveTask;
	private int panelTicks;

	private final PingService pingService = new PingService();
	private final UsageStats usage = new UsageStats();
	private final DataStore store = new DataStore(new File(RuneLite.RUNELITE_DIR, "player-count"));
	/** Worlds the user starred. Read on any thread, replaced (not mutated) on change. */
	private volatile Set<Integer> favorites = Collections.emptySet();
	private final Set<Integer> cycled = new HashSet<>();
	private Instant lastCycle;
	private Instant lastCombat;
	/** Latest worlds for the ping thread. */
	private volatile List<World> pingWorlds = Collections.emptyList();
	/** Current world for the ping thread; 0 when not logged in. */
	private volatile int pingCurrent;

	private final HotkeyListener nextHotkey = new HotkeyListener(() -> config.nextWorldKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(PlayerCountPlugin.this::hopNext);
		}
	};

	private final HotkeyListener hopHotkey = new HotkeyListener(() -> config.hopKey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(PlayerCountPlugin.this::hopToSuggestion);
		}
	};

	@Provides
	PlayerCountConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PlayerCountConfig.class);
	}

	@Override
	protected void startUp()
	{
		records = DailyRecords.parse(configManager.getConfiguration(PlayerCountConfig.GROUP, RECORDS_KEY));
		favorites = parseFavorites(config.favoriteWorlds());
		Instant now = Instant.now();
		store.load(HISTORY_FILE, in -> history6h.read(in, now));
		store.load(USAGE_FILE, usage::read);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(hopHotkey);
		keyManager.registerKeyListener(nextHotkey);
		mouseManager.registerMouseListener(overlayClicks);
		panel = new WorldListPanel(new WorldListPanel.Actions()
		{
			@Override
			public void hop(World world)
			{
				clientThread.invoke(() -> hopTo(world));
			}

			@Override
			public void hopSuggested()
			{
				clientThread.invoke(PlayerCountPlugin.this::hopToSuggestion);
			}

			@Override
			public void hopNext()
			{
				clientThread.invoke(PlayerCountPlugin.this::hopNext);
			}

			@Override
			public void toggleFavorite(int world)
			{
				PlayerCountPlugin.this.toggleFavorite(world);
			}

			@Override
			public void refresh()
			{
				refreshNow();
			}
		});
		navButton = NavigationButton.builder()
			.tooltip("Player Count")
			.icon(Icons.globe(16))
			.priority(7)
			.panel(panel)
			.build();
		if (config.showSidebar())
		{
			clientToolbar.addNavigation(navButton);
		}

		// Show RuneLite's copy straight away; the live list replaces it a moment later.
		executor.execute(() ->
		{
			WorldResult cached = worldService.getWorlds();
			if (cached != null && cached.getWorlds() != null && !liveIsFresh())
			{
				clientThread.invokeLater(() -> accept(cached));
			}
		});
		scheduleRefresh();
		if (config.pingEnabled())
		{
			startPing();
		}
		saveTask = executor.scheduleWithFixedDelay(this::saveData, SAVE_EVERY_S, SAVE_EVERY_S, TimeUnit.SECONDS);
	}

	private void startPing()
	{
		pingService.start(() -> pingWorlds, () -> pingCurrent, () -> favorites, config.pingInterval());
	}

	private void saveData()
	{
		store.save(HISTORY_FILE, history6h::write);
		store.save(USAGE_FILE, usage::write);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(hopHotkey);
		keyManager.unregisterKeyListener(nextHotkey);
		mouseManager.unregisterMouseListener(overlayClicks);
		pingService.stop();
		if (saveTask != null)
		{
			saveTask.cancel(false);
			saveTask = null;
		}
		saveData();
		clientToolbar.removeNavigation(navButton);
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
			refreshTask = null;
		}
		lastLiveFetch = null;
		clientThread.invokeLater(() ->
		{
			history.clear();
			history6h.clear();
			regionTotals = Collections.emptyMap();
			visited.clear();
			cycled.clear();
			lastCombat = null;
			pingWorlds = Collections.emptyList();
			suggestion = null;
			hopTarget = null;
			nearby = friendsHere = clanHere = -1;
			alertWorld = -1;
		});
	}

	private void scheduleRefresh()
	{
		if (refreshTask != null)
		{
			refreshTask.cancel(false);
		}
		int seconds = Math.max(PlayerCountConfig.MIN_REFRESH, config.refreshInterval());
		refreshTask = executor.scheduleWithFixedDelay(this::fetchLive, 0, seconds, TimeUnit.SECONDS);
	}

	void refreshNow()
	{
		executor.execute(this::fetchLive);
	}

	/** Downloads Jagex's live world list (async, so the shared executor is never blocked). */
	private void fetchLive()
	{
		if (!fetchInFlight.compareAndSet(false, true))
		{
			return;
		}
		Request request = new Request.Builder().url(SlrParser.URL).build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				fetchInFlight.set(false);
				log.debug("Live world list fetch failed", e);
				fallBackIfStale();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Live world list returned HTTP {}", response.code());
						fallBackIfStale();
						return;
					}
					List<World> worlds = SlrParser.parse(body.bytes());
					WorldResult result = new WorldResult();
					result.setWorlds(worlds);
					lastLiveFetch = Instant.now();
					log.debug("Live world list: {} worlds, {} players", worlds.size(), WorldStats.totals(worlds).getTotal());
					clientThread.invokeLater(() -> accept(result));
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Couldn't read live world list", e);
					fallBackIfStale();
				}
				finally
				{
					fetchInFlight.set(false);
				}
			}
		});
	}

	private boolean liveIsFresh()
	{
		Instant t = lastLiveFetch;
		return t != null && Duration.between(t, Instant.now()).compareTo(LIVE_STALE_AFTER) < 0;
	}

	private void fallBackIfStale()
	{
		if (!liveIsFresh())
		{
			worldService.refresh(); // answers via WorldsFetch
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!PlayerCountConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		if (PlayerCountConfig.REFRESH_KEY.equals(e.getKey()))
		{
			scheduleRefresh();
		}
		if ("pingEnabled".equals(e.getKey()) || "pingInterval".equals(e.getKey()))
		{
			pingService.stop();
			if (config.pingEnabled())
			{
				startPing();
			}
		}
		if (PlayerCountConfig.FAVORITES_KEY.equals(e.getKey()))
		{
			favorites = parseFavorites(config.favoriteWorlds());
		}
		if (PlayerCountConfig.SIDEBAR_KEY.equals(e.getKey()))
		{
			SwingUtilities.invokeLater(() ->
			{
				clientToolbar.removeNavigation(navButton);
				if (config.showSidebar())
				{
					clientToolbar.addNavigation(navButton);
				}
			});
		}
		clientThread.invokeLater(() ->
		{
			updateSuggestion();
			pushPanel();
		});
	}

	/** RuneLite's own list. Only used while the live list is unavailable, since it lags by up to 10 minutes. */
	@Subscribe
	public void onWorldsFetch(WorldsFetch e)
	{
		WorldResult result = e.getWorldResult();
		if (result != null && result.getWorlds() != null && !liveIsFresh())
		{
			clientThread.invokeLater(() -> accept(result));
		}
	}

	private void accept(WorldResult result)
	{
		Instant now = Instant.now();
		List<World> worlds = result.getWorlds();
		WorldStats.Totals totals = WorldStats.totals(worlds);
		history.addLast(new Sample(now, result, totals));
		// Keep exactly one sample at least CHANGE_WINDOW old as the baseline.
		while (history.size() > 2)
		{
			Iterator<Sample> it = history.iterator();
			it.next();
			if (Duration.between(it.next().getTime(), now).compareTo(CHANGE_WINDOW) >= 0)
			{
				history.removeFirst();
			}
			else
			{
				break;
			}
		}

		history6h.add(now, worlds);
		usage.add(now, worlds);
		pingWorlds = worlds;
		regionTotals = WorldStats.regionTotals(worlds);
		if (records.update(totals.getTotal(), now))
		{
			configManager.setConfiguration(PlayerCountConfig.GROUP, RECORDS_KEY, records.serialize());
		}

		updateSuggestion();
		checkWorldAlerts();
		pushPanel();
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		pingCurrent = loggedIn ? client.getWorld() : 0;
		if (loggedIn)
		{
			visited.put(client.getWorld(), Instant.now());
		}

		int n = countNearby();
		if (nearbySettle > 0)
		{
			nearbySettle--;
		}
		else if (nearby == 0 && n > 0)
		{
			notify(config.nearbyAlert(), n == 1 ? "A player arrived nearby." : n + " players arrived nearby.");
		}
		nearby = n;

		friendsHere = config.showSocial() ? countFriendsHere() : -1;
		clanHere = config.showSocial() ? countClanHere() : -1;

		updateSuggestion();
		tickHop();
		// Pings and "updated Xs ago" change between downloads, so refresh the sidebar every ~3s too.
		if (++panelTicks % 5 == 0)
		{
			pushPanel();
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		Actor actor = e.getActor();
		if (actor == client.getLocalPlayer() || e.getHitsplat().isMine())
		{
			lastCombat = Instant.now();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState s = e.getGameState();
		if (s == GameState.HOPPING || s == GameState.LOGIN_SCREEN)
		{
			hopTarget = null;
			nearby = -1;
		}
		if (s == GameState.LOADING || s == GameState.HOPPING || s == GameState.LOGGED_IN)
		{
			nearbySettle = NEARBY_SETTLE_TICKS;
		}
		if (s == GameState.LOGGED_IN || s == GameState.LOGIN_SCREEN)
		{
			updateSuggestion();
			pushPanel();
		}
	}

	/** Closing RuneLite doesn't stop plugins, so save here too (and make the client wait for it). */
	@Subscribe
	public void onClientShutdown(ClientShutdown e)
	{
		e.waitFor(executor.submit(this::saveData));
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted e)
	{
		if (config.chatCommand() && "players".equalsIgnoreCase(e.getCommand()))
		{
			chat(summary());
		}
	}

	// ---- Values for the overlay ----

	private WorldResult latest()
	{
		Sample s = history.peekLast();
		return s == null ? null : s.getResult();
	}

	/** The sample the +/- figures compare against, or null if there's only one. */
	private Sample baseline()
	{
		return history.size() < 2 ? null : history.peekFirst();
	}

	boolean hasData()
	{
		return !history.isEmpty();
	}

	PopulationHistory getHistory()
	{
		return history6h;
	}

	Instant getLastUpdate()
	{
		Sample s = history.peekLast();
		return s == null ? null : s.getTime();
	}

	WorldStats.Totals getTotals()
	{
		Sample s = history.peekLast();
		return s == null ? null : s.getTotals();
	}

	Map<WorldRegion, Integer> regionTotals()
	{
		return regionTotals;
	}

	/** Busiest world per configured activity term, in the order typed (null value = none found). */
	Map<String, World> activityWorlds()
	{
		WorldResult latest = latest();
		String raw = config.activityWorlds();
		if (latest == null || raw == null || raw.trim().isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, World> out = new LinkedHashMap<>();
		for (String term : raw.split(","))
		{
			String t = term.trim();
			if (!t.isEmpty() && out.size() < MAX_ACTIVITY_TERMS)
			{
				out.put(t, WorldStats.busiestActivity(latest.getWorlds(), t));
			}
		}
		return out;
	}

	/** Change in the total since the baseline, or null if there's no baseline yet. */
	Integer totalChange()
	{
		Sample base = baseline();
		WorldStats.Totals now = getTotals();
		return base == null || now == null ? null : now.getTotal() - base.getTotals().getTotal();
	}

	/** Player count of the world we're on, or -1 if unknown. */
	int currentWorldPlayers()
	{
		return playersOn(latest(), client.getWorld());
	}

	/** Change in this world's count since the baseline, or null if unknown. */
	Integer currentWorldChange()
	{
		Sample base = baseline();
		if (base == null)
		{
			return null;
		}
		int now = playersOn(latest(), client.getWorld());
		int before = playersOn(base.getResult(), client.getWorld());
		return now < 0 || before < 0 ? null : now - before;
	}

	/** Per-world change since the baseline. */
	private Map<Integer, Integer> worldChanges()
	{
		Sample base = baseline();
		WorldResult latest = latest();
		if (base == null || latest == null)
		{
			return Collections.emptyMap();
		}
		Map<Integer, Integer> before = new HashMap<>();
		for (World w : base.getResult().getWorlds())
		{
			before.put(w.getId(), w.getPlayers());
		}
		Map<Integer, Integer> out = new HashMap<>();
		for (World w : latest.getWorlds())
		{
			Integer b = before.get(w.getId());
			if (b != null && b >= 0 && w.getPlayers() >= 0)
			{
				out.put(w.getId(), w.getPlayers() - b);
			}
		}
		return out;
	}

	int currentWorld()
	{
		return client.getWorld();
	}

	private static int playersOn(WorldResult result, int id)
	{
		if (result == null)
		{
			return -1;
		}
		World w = result.findWorld(id);
		return w == null ? -1 : w.getPlayers();
	}

	private int countNearby()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return -1;
		}
		return countPlayers(client.getTopLevelWorldView(), client.getLocalPlayer());
	}

	/** Players in a world view and any nested ones (e.g. boats), minus ourselves. */
	private static int countPlayers(WorldView view, Player me)
	{
		if (view == null)
		{
			return 0;
		}
		int n = 0;
		for (Player p : view.players())
		{
			if (p != null && p != me)
			{
				n++;
			}
		}
		for (WorldView sub : view.worldViews())
		{
			n += countPlayers(sub, me);
		}
		return n;
	}

	private int countFriendsHere()
	{
		FriendContainer friends = client.getGameState() == GameState.LOGGED_IN ? client.getFriendContainer() : null;
		if (friends == null)
		{
			return -1;
		}
		int world = client.getWorld();
		int n = 0;
		for (Friend f : friends.getMembers())
		{
			if (f != null && f.getWorld() == world)
			{
				n++;
			}
		}
		return n;
	}

	private int countClanHere()
	{
		ClanChannel clan = client.getGameState() == GameState.LOGGED_IN ? client.getClanChannel() : null;
		if (clan == null)
		{
			return -1;
		}
		Player me = client.getLocalPlayer();
		String myName = me == null || me.getName() == null ? "" : Text.toJagexName(me.getName());
		int world = client.getWorld();
		int n = 0;
		for (ClanChannelMember m : clan.getMembers())
		{
			if (m.getWorld() == world && !Text.toJagexName(m.getName()).equalsIgnoreCase(myName))
			{
				n++;
			}
		}
		return n;
	}

	// ---- Alerts ----

	private void checkWorldAlerts()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int world = client.getWorld();
		int players = currentWorldPlayers();
		if (players < 0)
		{
			return;
		}
		boolean busy = players > config.busyThreshold();
		boolean quiet = players < config.quietThreshold();
		if (world == alertWorld)
		{
			if (busy && !wasBusy)
			{
				notify(config.busyAlert(), String.format("World %d is getting busy: %,d players.", world, players));
			}
			if (quiet && !wasQuiet)
			{
				notify(config.quietAlert(), String.format("World %d is quiet: %,d players.", world, players));
			}
		}
		// Arriving on a world just records its state; alerts fire only on later changes.
		alertWorld = world;
		wasBusy = busy;
		wasQuiet = quiet;
	}

	private void notify(Notification notification, String message)
	{
		if (notification.isEnabled())
		{
			notifier.notify(notification, message);
		}
	}

	// ---- Chat command ----

	private String summary()
	{
		if (!hasData())
		{
			return "Player Count: world list not loaded yet.";
		}
		StringBuilder sb = new StringBuilder();
		int players = currentWorldPlayers();
		sb.append(String.format("World %d: %s", client.getWorld(), players < 0 ? "?" : String.format("%,d", players)));
		Integer wc = currentWorldChange();
		if (wc != null && wc != 0)
		{
			sb.append(String.format(" (%+,d/min)", wc));
		}
		WorldStats.Totals t = getTotals();
		sb.append(String.format(" | OSRS: %,d (P2P %s, F2P %s)", t.getTotal(),
			PlayerCountOverlay.compact(t.getMembers()), PlayerCountOverlay.compact(t.getFree())));
		if (nearby >= 0)
		{
			sb.append(" | Nearby: ").append(nearby);
		}
		World s = suggestion;
		if (s != null)
		{
			sb.append(String.format(" | %s: W%d (%,d)", config.finderMode(), s.getId(), s.getPlayers()));
		}
		return sb.toString();
	}

	// ---- World finder ----

	private Set<Integer> recentWorlds()
	{
		int minutes = config.skipRecentMinutes();
		if (minutes <= 0)
		{
			return Collections.emptySet();
		}
		Instant cutoff = Instant.now().minus(Duration.ofMinutes(minutes));
		Set<Integer> out = new HashSet<>();
		visited.forEach((world, last) ->
		{
			if (last.isAfter(cutoff))
			{
				out.add(world);
			}
		});
		return out;
	}

	private void updateSuggestion()
	{
		WorldResult latest = latest();
		if (latest == null || latest.getWorlds() == null || config.finderMode() == PlayerCountConfig.FinderMode.OFF)
		{
			suggestion = null;
			return;
		}
		suggestion = WorldStats.pick(latest.getWorlds(), criteria(latest, recentWorlds()));
	}

	/** The finder's settings as criteria. Caller checks the finder isn't off. */
	private WorldStats.Criteria criteria(WorldResult latest, Set<Integer> excluded)
	{
		int current = client.getWorld();
		World currentWorld = latest.findWorld(current);

		boolean members;
		switch (config.membership())
		{
			case MEMBERS:
				members = true;
				break;
			case FREE:
				members = false;
				break;
			default:
				members = client.getWorldType().contains(net.runelite.api.WorldType.MEMBERS);
		}

		WorldRegion region;
		switch (config.region())
		{
			case ANY:
				region = null;
				break;
			case SAME_AS_CURRENT:
				region = currentWorld == null ? null : currentWorld.getRegion();
				break;
			default:
				region = config.region().region;
		}

		WorldStats.Mode mode;
		switch (config.finderMode())
		{
			case BUSIEST:
				mode = WorldStats.Mode.BUSIEST;
				break;
			case LOWEST_PING:
				mode = WorldStats.Mode.LOWEST_PING;
				break;
			case FAVOURITES:
				switch (config.favouriteOrder())
				{
					case FEWEST_PLAYERS:
						mode = WorldStats.Mode.FAVOURITES_BY_PLAYERS;
						break;
					case BOTH:
						mode = WorldStats.Mode.FAVOURITES_BALANCED;
						break;
					default:
						mode = WorldStats.Mode.FAVOURITES_BY_PING;
				}
				break;
			default:
				mode = WorldStats.Mode.QUIETEST;
		}

		return WorldStats.Criteria.builder()
			.currentWorld(current)
			.members(members)
			.region(region)
			.totalLevel(client.getTotalLevel())
			.allowSkillTotal(config.allowSkillTotal())
			.mode(mode)
			.excluded(excluded)
			.pings(config.pingEnabled() ? pingService.all() : Collections.emptyMap())
			.maxPing(config.pingEnabled() ? config.maxPing() : 0)
			.favorites(favorites)
			.usual(config.preferUsual() ? usage.usualAll(Instant.now()) : Collections.emptyMap())
			.preferUsual(config.preferUsual())
			.build();
	}

	/** Must run on the client thread. */
	void hopToSuggestion()
	{
		if (config.finderMode() == PlayerCountConfig.FinderMode.OFF)
		{
			chat("The world finder is turned off in Player Count's settings.");
			return;
		}
		if (suggestion == null)
		{
			chat(noMatchMessage());
			return;
		}
		hopTo(suggestion);
	}

	/** Must run on the client thread. */
	void hopTo(World target)
	{
		if (target.getId() == client.getWorld() && client.getGameState() == GameState.LOGGED_IN)
		{
			chat("You're already on world " + target.getId() + ".");
			return;
		}

		net.runelite.api.World rs = client.createWorld();
		rs.setActivity(target.getActivity());
		rs.setAddress(target.getAddress());
		rs.setId(target.getId());
		rs.setPlayerCount(target.getPlayers());
		if (target.getRegion() != null)
		{
			rs.setLocation(target.getRegion().getId());
		}
		rs.setTypes(WorldUtil.toWorldTypes(target.getTypes()));

		if (client.getGameState() == GameState.LOGIN_SCREEN)
		{
			client.changeWorld(rs);
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		hopTarget = rs;
		hopAttempts = 0;
		chat(String.format("Hopping to world %d (%,d players).", target.getId(), target.getPlayers()));
	}

	/** Must run on the client thread. Hops to the next world on the finder's list that this hotkey hasn't visited. */
	void hopNext()
	{
		WorldResult latest = latest();
		if (config.finderMode() == PlayerCountConfig.FinderMode.OFF)
		{
			chat("The world finder is turned off in Player Count's settings.");
			return;
		}
		if (latest == null)
		{
			return;
		}
		Instant now = Instant.now();
		if (lastCycle == null || Duration.between(lastCycle, now).compareTo(CYCLE_RESET) > 0)
		{
			cycled.clear();
		}
		Set<Integer> skip = new HashSet<>(recentWorlds());
		skip.addAll(cycled);
		List<World> ranked = WorldStats.rank(latest.getWorlds(), criteria(latest, skip));
		if (ranked.isEmpty() && !cycled.isEmpty())
		{
			// Been through the whole list: start over. Skip-recent is ignored here, since every world on the
			// list was just visited and would otherwise stall the cycle.
			cycled.clear();
			ranked = WorldStats.rank(latest.getWorlds(), criteria(latest, Collections.emptySet()));
		}
		if (ranked.isEmpty())
		{
			chat(noMatchMessage());
			return;
		}
		World next = ranked.get(0);
		cycled.add(client.getWorld());
		cycled.add(next.getId());
		lastCycle = now;
		hopTo(next);
	}

	// ---- Favourites ----

	private String noMatchMessage()
	{
		if (config.finderMode() == PlayerCountConfig.FinderMode.FAVOURITES && favorites.isEmpty())
		{
			return "You have no favourite worlds yet. Add some under World finder > Favourite worlds, or star them in the sidebar.";
		}
		return "No world matches your world finder settings.";
	}

	static Set<Integer> parseFavorites(String raw)
	{
		Set<Integer> out = new TreeSet<>();
		if (raw != null)
		{
			for (String part : raw.split(","))
			{
				try
				{
					out.add(Integer.parseInt(part.trim()));
				}
				catch (NumberFormatException ignored)
				{
					// skip junk
				}
			}
		}
		return Collections.unmodifiableSet(out);
	}

	boolean isFavourite(int world)
	{
		return favorites.contains(world);
	}

	void toggleFavorite(int world)
	{
		Set<Integer> next = new TreeSet<>(favorites);
		if (!next.remove(world))
		{
			next.add(world);
		}
		favorites = Collections.unmodifiableSet(next);
		configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.FAVORITES_KEY,
			next.stream().map(String::valueOf).collect(Collectors.joining(",")));
		clientThread.invokeLater(() ->
		{
			updateSuggestion();
			pushPanel();
		});
	}

	// ---- Auto-hide ----

	boolean inCombat()
	{
		Player me = client.getLocalPlayer();
		if (me != null && me.getInteracting() instanceof NPC && ((NPC) me.getInteracting()).getCombatLevel() > 0)
		{
			return true;
		}
		return lastCombat != null && Duration.between(lastCombat, Instant.now()).getSeconds() < COMBAT_LINGER_S;
	}

	/** True when the overlay should be hidden for reasons other than hover. */
	boolean hiddenBySituation()
	{
		if (config.hideInInstances() && client.getGameState() == GameState.LOGGED_IN
			&& client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance())
		{
			return true;
		}
		return config.hideMode() == PlayerCountConfig.HideMode.IN_COMBAT && inCombat();
	}

	/** Last measured ping to a world in ms, or -1. */
	int pingOf(int world)
	{
		return pingService.ping(world);
	}

	/** Ping to the world we're on in ms, or -1. */
	int currentPing()
	{
		return config.pingEnabled() ? pingService.ping(client.getWorld()) : -1;
	}

	// ---- Sidebar ----

	private void pushPanel()
	{
		WorldResult latest = latest();
		if (panel == null || latest == null)
		{
			return;
		}
		Instant now = Instant.now();
		WorldStats.Totals t = getTotals();
		panel.update(WorldListPanel.State.builder()
			.worlds(latest.getWorlds())
			.changes(worldChanges())
			.pings(config.pingEnabled() ? new HashMap<>(pingService.all()) : Collections.emptyMap())
			.usual(usage.usualAll(now))
			.favorites(favorites)
			.currentWorld(client.getGameState() == GameState.LOGGED_IN ? client.getWorld() : -1)
			.total(t == null ? -1 : t.getTotal())
			.totalChange(totalChange())
			.suggestion(suggestion)
			.finderLabel(config.finderMode().toString())
			.finderOff(config.finderMode() == PlayerCountConfig.FinderMode.OFF)
			.updated(getLastUpdate())
			.build());
	}

	/** Same approach as the World Hopper plugin: open the switcher, then hop once it's loaded. */
	private void tickHop()
	{
		if (hopTarget == null)
		{
			return;
		}
		if (client.getWidget(InterfaceID.Worldswitcher.BUTTONS) == null)
		{
			client.openWorldHopper();
			if (++hopAttempts >= MAX_HOP_ATTEMPTS)
			{
				chat("Couldn't open the world switcher, hop cancelled.");
				hopTarget = null;
			}
			return;
		}
		client.hopToWorld(hopTarget);
		hopTarget = null;
	}

	private void chat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(message)
			.build());
	}
}
