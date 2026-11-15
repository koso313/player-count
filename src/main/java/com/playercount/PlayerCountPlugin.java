package com.playercount;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
	/** How long counts read from the game client's own world list override RuneLite's older copy. */
	private static final Duration CLIENT_COUNTS_FOR = Duration.ofMinutes(10);
	private static final int MAX_HOP_ATTEMPTS = 5;
	/** Ticks to ignore nearby-player changes after a login, hop or map load while players stream in. */
	private static final int NEARBY_SETTLE_TICKS = 3;
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
	private OverlayManager overlayManager;

	@Inject
	private PlayerCountOverlay overlay;

	@Inject
	private ArrivalOverlay arrivalOverlay;

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
	private net.runelite.client.game.SpriteManager spriteManager;

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

	// Everything below is only touched on the client thread.
	/** RuneLite's latest world list, before the game client's counts are laid over it. */
	private WorldResult base;
	private net.runelite.api.World[] lastClientList;
	private int lastClientSum = -1;
	private Map<Integer, Integer> clientCounts = Collections.emptyMap();
	private Instant clientCountsAt;
	/** Oldest first; the head is the +/- baseline, the tail is the latest. */
	private final Deque<Sample> history = new ArrayDeque<>();
	@Getter
	private final PopulationHistory history6h = new PopulationHistory();
	@Getter
	private DailyRecords records = new DailyRecords();
	private Map<WorldRegion, Integer> regionTotals = Collections.emptyMap();
	private Map<WorldRegion, WorldMap.RegionStat> regionStats = Collections.emptyMap();
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
	/** Players that counted for the arrival alert last tick (by name). */
	private Set<String> lastQualifying = new HashSet<>();
	/** Players to highlight, until when. Client thread only. */
	@Getter
	private final Map<Player, Instant> arrivals = new HashMap<>();

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
		migrateStars();
		recomputeFavorites();
		Instant now = Instant.now();
		store.load(HISTORY_FILE, in -> history6h.read(in, now));
		store.load(USAGE_FILE, usage::read);
		overlayManager.add(overlay);
		overlayManager.add(arrivalOverlay);
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
			public void setFavorite(int world, boolean on)
			{
				PlayerCountPlugin.this.setFavorite(world, on);
			}

			@Override
			public void refresh()
			{
				refreshNow();
			}

			@Override
			public void saveSetting(String key, String value)
			{
				configManager.setConfiguration(PlayerCountConfig.GROUP, key, value);
			}
		}, WorldListPanel.Filters.builder()
			.sort(config.sidebarSort())
			.membership(config.sidebarMembership())
			.region(config.sidebarRegion())
			.theme(config.sidebarTheme())
			.hidePvp(config.sidebarHidePvp())
			.open(config.sidebarFiltersOpen())
			.build());
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
		// Region flags for the sidebar, from the game's world switcher sprites
		WorldListPanel flagTarget = panel;
		WorldListPanel.FLAG_SPRITES.forEach((region, sprite) ->
			spriteManager.getSpriteAsync(sprite, 0, img -> flagTarget.setFlag(region, img)));

		// Show RuneLite's copy straight away; the scheduled refresh keeps it current.
		executor.execute(() ->
		{
			WorldResult cached = worldService.getWorlds();
			if (cached != null && cached.getWorlds() != null)
			{
				clientThread.invokeLater(() ->
				{
					if (base == null)
					{
						acceptBase(cached);
					}
				});
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
		overlayManager.remove(arrivalOverlay);
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
		clientThread.invokeLater(() ->
		{
			base = null;
			lastClientList = null;
			lastClientSum = -1;
			clientCounts = Collections.emptyMap();
			clientCountsAt = null;
			history.clear();
			history6h.clear();
			regionTotals = Collections.emptyMap();
			visited.clear();
			cycled.clear();
			arrivals.clear();
			lastQualifying.clear();
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
		// RuneLite's WorldService stops refreshing on its own once you're logged in, so ask it to.
		// It answers through WorldsFetch.
		refreshTask = executor.scheduleWithFixedDelay(worldService::refresh, seconds, seconds, TimeUnit.SECONDS);
	}

	void refreshNow()
	{
		worldService.refresh();
	}

	/**
	 * Merges the game client's own world list, if it has refreshed since we last looked. The client only
	 * reloads it now and then (on the login screen, or while the world switcher is open), but when it does,
	 * its counts are current, unlike RuneLite's copy, which can be up to 10 minutes old.
	 */
	private void checkClientList()
	{
		net.runelite.api.World[] list = client.getWorldList();
		if (list == null || list.length == 0)
		{
			return;
		}
		int sum = 0;
		for (net.runelite.api.World w : list)
		{
			sum += Math.max(0, w.getPlayerCount());
		}
		if (list == lastClientList && sum == lastClientSum)
		{
			return;
		}
		boolean first = lastClientList == null;
		lastClientList = list;
		lastClientSum = sum;
		Map<Integer, Integer> counts = new HashMap<>();
		for (net.runelite.api.World w : list)
		{
			counts.put(w.getId(), w.getPlayerCount());
		}
		clientCounts = counts;
		clientCountsAt = Instant.now();
		// A list already sitting there at start-up may be old; only trust ones we see change.
		if (!first && base != null)
		{
			accept(base);
		}
	}

	/** RuneLite's list with any fresh counts from the game client laid over it. */
	private WorldResult withClientCounts(WorldResult result)
	{
		Map<Integer, Integer> counts = clientCounts;
		Instant at = clientCountsAt;
		if (counts.isEmpty() || at == null || Duration.between(at, Instant.now()).compareTo(CLIENT_COUNTS_FOR) > 0)
		{
			return result;
		}
		List<World> merged = new ArrayList<>(result.getWorlds().size());
		for (World w : result.getWorlds())
		{
			Integer n = counts.get(w.getId());
			merged.add(n == null || n == w.getPlayers() ? w : World.builder()
				.id(w.getId())
				.types(w.getTypes())
				.address(w.getAddress())
				.activity(w.getActivity())
				.region(w.getRegion())
				.players(n)
				.build());
		}
		WorldResult out = new WorldResult();
		out.setWorlds(merged);
		return out;
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
			recomputeFavorites();
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

	/** RuneLite's world list, fetched by its WorldService (shared with the World Hopper). */
	@Subscribe
	public void onWorldsFetch(WorldsFetch e)
	{
		WorldResult result = e.getWorldResult();
		if (result != null && result.getWorlds() != null)
		{
			clientThread.invokeLater(() -> acceptBase(result));
		}
	}

	private void acceptBase(WorldResult result)
	{
		base = result;
		accept(result);
	}

	private void accept(WorldResult raw)
	{
		WorldResult result = withClientCounts(raw);
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
		regionStats = WorldMap.stats(worlds);
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

		nearby = countNearby();
		checkArrivals();

		friendsHere = config.showSocial() ? countFriendsHere() : -1;
		clanHere = config.showSocial() ? countClanHere() : -1;

		checkClientList();
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
			arrivals.clear();
			lastQualifying.clear();
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

	Map<WorldRegion, WorldMap.RegionStat> regionStats()
	{
		return regionStats;
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

	/** The world you're on, from the latest list, or null. */
	World currentWorldInfo()
	{
		WorldResult latest = latest();
		return latest == null ? null : latest.findWorld(client.getWorld());
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

	/** Players that count for the arrival alert right now: in range, not friends (if set), attackable (if set). */
	private List<Player> qualifyingPlayers()
	{
		List<Player> out = new ArrayList<>();
		Player me = client.getLocalPlayer();
		WorldView view = client.getTopLevelWorldView();
		if (me == null || view == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return out;
		}
		boolean pvpWorld = client.getWorldType().contains(net.runelite.api.WorldType.PVP);
		boolean deadman = client.getWorldType().contains(net.runelite.api.WorldType.DEADMAN);
		for (Player p : view.players())
		{
			if (p == null || p == me || p.getName() == null)
			{
				continue;
			}
			if (config.arrivalRange() > 0 && me.getWorldLocation().distanceTo(p.getWorldLocation()) > config.arrivalRange())
			{
				continue;
			}
			if (config.arrivalIgnoreFriends() && (p.isFriend() || p.isClanMember() || p.isFriendsChatMember()))
			{
				continue;
			}
			if (config.arrivalAttackableOnly() && !Attackable.canAttack(me.getCombatLevel(), me.getWorldLocation(),
				p.getCombatLevel(), p.getWorldLocation(), pvpWorld, deadman))
			{
				continue;
			}
			out.add(p);
		}
		return out;
	}

	/** Fires the Player arrives alert (and highlight) for newly arrived qualifying players. */
	private void checkArrivals()
	{
		if (!config.nearbyAlert().isEnabled())
		{
			lastQualifying.clear();
			arrivals.clear();
			return;
		}
		List<Player> now = qualifyingPlayers();
		Set<String> names = new HashSet<>();
		for (Player p : now)
		{
			names.add(p.getName());
		}
		if (nearbySettle > 0)
		{
			// Players stream in after a login, hop or loading screen; don't treat them as arrivals.
			nearbySettle--;
			lastQualifying = names;
			return;
		}
		List<Player> arrived = new ArrayList<>();
		for (Player p : now)
		{
			if (!lastQualifying.contains(p.getName()))
			{
				arrived.add(p);
			}
		}
		boolean fire = !arrived.isEmpty()
			&& (config.arrivalMode() == PlayerCountConfig.ArrivalMode.EVERY_PLAYER || lastQualifying.isEmpty());
		lastQualifying = names;
		if (!fire)
		{
			return;
		}
		Instant until = Instant.now().plusSeconds(config.arrivalSeconds());
		for (Player p : arrived)
		{
			arrivals.put(p, until);
		}
		notify(config.nearbyAlert(), arrivalMessage(arrived));
	}

	private String arrivalMessage(List<Player> arrived)
	{
		Player me = client.getLocalPlayer();
		if (arrived.size() == 1)
		{
			Player p = arrived.get(0);
			int tiles = me == null ? -1 : me.getWorldLocation().distanceTo(p.getWorldLocation());
			return describe(p) + " arrived" + (tiles >= 0 ? " " + tiles + (tiles == 1 ? " tile" : " tiles") + " away." : ".");
		}
		StringBuilder sb = new StringBuilder(arrived.size() + " players arrived: ");
		for (int i = 0; i < Math.min(3, arrived.size()); i++)
		{
			sb.append(i == 0 ? "" : ", ").append(describe(arrived.get(i)));
		}
		if (arrived.size() > 3)
		{
			sb.append(" and ").append(arrived.size() - 3).append(" more");
		}
		return sb.append('.').toString();
	}

	private static String describe(Player p)
	{
		String skull = p.getSkullIcon() >= 0 ? ", skulled" : "";
		return p.getName() + " (lvl-" + p.getCombatLevel() + skull + ")";
	}

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
			case THEMED:
				switch (config.themedOrder())
				{
					case QUIETEST:
						mode = WorldStats.Mode.THEMED_QUIETEST;
						break;
					case LOWEST_PING:
						mode = WorldStats.Mode.THEMED_BY_PING;
						break;
					case BOTH:
						mode = WorldStats.Mode.THEMED_BALANCED;
						break;
					case QUIET_AND_FAST:
						mode = WorldStats.Mode.THEMED_QUIET_BALANCED;
						break;
					default:
						mode = WorldStats.Mode.THEMED_BUSIEST;
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
			.theme(config.theme())
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
		String risk = config.warnRisky() ? WorldStats.risk(target) : null;
		if (risk != null)
		{
			chat(WorldStats.deadly(target)
				? String.format("<col=ff4030>Careful: world %d is a %s world - you can be attacked almost anywhere.</col>", target.getId(), risk)
				: WorldStats.pvpThemed(target)
				? String.format("<col=ff4030>Careful: world %d is a %s world.</col>", target.getId(), risk)
				: String.format("<col=ff8c1a>Heads up: world %d is a %s world.</col>", target.getId(), risk));
		}
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
		if (config.finderMode() == PlayerCountConfig.FinderMode.THEMED)
		{
			int need = config.theme().requiredTotal();
			if (need > 0 && client.getTotalLevel() < need)
			{
				return String.format("%s worlds need a total level of %,d (yours is %,d).", config.theme(), need, client.getTotalLevel());
			}
			return "No open " + config.theme() + " worlds right now match your finder settings.";
		}
		if (config.finderMode() == PlayerCountConfig.FinderMode.THEMED && config.theme() == WorldTheme.FAVOURITES && favorites.isEmpty())
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

	/** Favourites = worlds starred in the sidebar + worlds typed into Favourite worlds. */
	/** Favourites are the Favourite worlds list (the sidebar edits it too). */
	private void recomputeFavorites()
	{
		favorites = parseFavorites(config.favoriteWorlds());
	}

	/** An earlier build kept sidebar stars in a hidden list; fold them into Favourite worlds once. */
	private void migrateStars()
	{
		Set<Integer> starred = parseFavorites(config.starredWorlds());
		if (starred.isEmpty())
		{
			return;
		}
		Set<Integer> all = new TreeSet<>(parseFavorites(config.favoriteWorlds()));
		all.addAll(starred);
		configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.FAVORITES_KEY, join(all));
		configManager.unsetConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.STARRED_KEY);
	}

	private static String join(Set<Integer> worlds)
	{
		return worlds.stream().map(String::valueOf).collect(Collectors.joining(", "));
	}

	void toggleFavorite(int world)
	{
		setFavorite(world, !favorites.contains(world));
	}

	/** Adds or removes a favourite (sidebar star, favourites row, or its add box). */
	void setFavorite(int world, boolean on)
	{
		Set<Integer> list = new TreeSet<>(parseFavorites(config.favoriteWorlds()));
		if (on)
		{
			list.add(world);
		}
		else
		{
			list.remove(world);
		}
		configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.FAVORITES_KEY, join(list));
		recomputeFavorites();
		// RuneLite only writes settings to disk every few minutes or on exit; save stars straight away.
		executor.execute(configManager::sendConfig);
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
		PlayerCountConfig.AutoHide hide = config.autoHide();
		boolean instances = hide == PlayerCountConfig.AutoHide.IN_INSTANCES || hide == PlayerCountConfig.AutoHide.COMBAT_OR_INSTANCES;
		boolean combat = hide == PlayerCountConfig.AutoHide.IN_COMBAT || hide == PlayerCountConfig.AutoHide.COMBAT_OR_INSTANCES;
		if (instances && client.getGameState() == GameState.LOGGED_IN
			&& client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance())
		{
			return true;
		}
		return combat && inCombat();
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
			.finderLabel(config.finderMode() == PlayerCountConfig.FinderMode.THEMED ? config.theme().toString() : config.finderMode().toString())
			.finderOff(config.finderMode() == PlayerCountConfig.FinderMode.OFF)
			.warnRisky(config.warnRisky())
			.regionStats(regionStats)
			.showMap(config.sidebarMap())
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
