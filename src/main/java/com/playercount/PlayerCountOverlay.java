package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;

/**
 * The movable overlay. Always drawn 1:1 so the game's pixel fonts stay sharp; the Size setting
 * (Full, Medium, Small) changes the layout instead, and Alt-dragging the corner switches between them.
 */
class PlayerCountOverlay extends Overlay
{
	static final Color UP = new Color(0x66, 0xDD, 0x66);
	static final Color DOWN = new Color(0xEE, 0x66, 0x66);
	static final Color AMBER = new Color(0xFF, 0xD0, 0x40);
	private static final Color MUTED = new Color(0xAA, 0xAA, 0xAA);
	private static final Color HIDDEN_HINT = new Color(255, 255, 255, 40);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private final PlayerCountPlugin plugin;
	private final PlayerCountConfig config;
	private final Client client;
	private final SpriteManager spriteManager;
	private final ConfigManager configManager;
	private final OverlayManager overlayManager;
	private final ClientThread clientThread;
	private final TooltipManager tooltipManager;
	private final FramePainter painter = new FramePainter();
	private final PanelComponent panelComponent = new PanelComponent();
	private final OverlayLayout.WidthMemory widths = new OverlayLayout.WidthMemory();
	private final WorldMap worldMap = new WorldMap();
	/** The map drawn last frame (for hover) and the region under the mouse. */
	private WorldMapComponent lastMap;
	private WorldRegion hoverRegion;

	/** Unscaled size last frame; PanelComponent itself sizes from the previous frame too. */
	private Dimension lastNatural;
	/** Size on screen last frame, kept while hidden-until-hover so the hover area stays put. */
	private Dimension lastSize;
	/**
	 * A preferred size already saved when the overlay first draws isn't a drag from this session
	 * (older builds saved one), so it's discarded rather than turned into a Size.
	 */
	private boolean firstFrame = true;
	/** Lines drawn last frame (with their hit boxes), for clicks and hover. */
	private List<OverlayLayout.Item> lastItems = new ArrayList<>();
	/** Mini size this frame: short labels and compact numbers. */
	private boolean mini;

	@Inject
	PlayerCountOverlay(PlayerCountPlugin plugin, PlayerCountConfig config, Client client, SpriteManager spriteManager,
		ConfigManager configManager, OverlayManager overlayManager, ClientThread clientThread, TooltipManager tooltipManager)
	{
		super(plugin);
		this.clientThread = clientThread;
		this.tooltipManager = tooltipManager;
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.spriteManager = spriteManager;
		this.configManager = configManager;
		this.overlayManager = overlayManager;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
		setResizable(true);
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Hop to", "suggested world", e -> plugin.hopToSuggestion());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Next", "world", e -> plugin.hopNext());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Refresh", "player counts", e -> plugin.refreshNow());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Full size", "Player Count overlay",
			e -> configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.SIZE_KEY, PlayerCountConfig.OverlaySize.FULL));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Player Count overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.hiddenBySituation())
		{
			lastItems = new ArrayList<>();
			return null;
		}
		if (config.hideMode() == PlayerCountConfig.HideMode.UNTIL_HOVER && !hovered() && lastSize != null)
		{
			// Keep the space (so hovering it brings the overlay back) with just a faint outline.
			graphics.setColor(HIDDEN_HINT);
			graphics.drawRect(0, 0, lastSize.width - 1, lastSize.height - 1);
			return lastSize;
		}

		PlayerCountConfig.FrameStyle style = config.frameStyle();
		if (style == PlayerCountConfig.FrameStyle.STEEL && !painter.loadSteel(id -> spriteManager.getSprite(id, 0)))
		{
			style = PlayerCountConfig.FrameStyle.RUNELITE; // sprites not loaded yet
		}
		PlayerCountConfig.Layout layout = config.layout();

		// Sizes are layouts, never scaling: pixel fonts and sprites smear when scaled, so each size is
		// drawn 1:1 (Medium uses the game's small font, Small also shortens labels onto one line).
		PlayerCountConfig.OverlaySize size = config.overlaySize();
		boolean compact = size != PlayerCountConfig.OverlaySize.FULL;
		mini = size == PlayerCountConfig.OverlaySize.SMALL;
		Font font = graphics.getFont();
		if (compact)
		{
			graphics.setFont(FontManager.getRunescapeSmallFont());
		}

		hoverRegion = mapHover();
		List<OverlayLayout.Item> items = items();
		markHover(items);
		if (hoverRegion != null)
		{
			tooltipManager.add(new Tooltip(WorldMap.describe(hoverRegion, plugin.regionStats())));
		}

		panelComponent.getChildren().clear();
		StringBuilder shown = new StringBuilder().append(style).append('|').append(layout).append('|').append(size).append('|').append(config.showTitle());
		for (OverlayLayout.Item item : items)
		{
			shown.append('|').append(item.label);
		}
		widths.begin(shown.toString());
		int content = OverlayLayout.populate(panelComponent, items, style, layout, config.showTitle(), painter, graphics, compact, mini, widths);

		Rectangle insets = FramePainter.insets(style, compact);
		int naturalWidth = Math.max(OverlayLayout.minWidth(style, layout, compact), content + insets.x + insets.width + 2);
		snapDrag(naturalWidth, size);

		panelComponent.setPreferredSize(new Dimension(naturalWidth, 0));
		panelComponent.setBorder(insets);
		boolean custom = style != PlayerCountConfig.FrameStyle.RUNELITE;
		panelComponent.setBackgroundColor(custom ? null : ComponentConstants.STANDARD_BACKGROUND_COLOR);

		Dimension natural;
		try
		{
			if (custom && lastNatural != null)
			{
				painter.paintBackground(graphics, style, naturalWidth, lastNatural.height, config.frameOpacity());
			}
			natural = panelComponent.render(graphics);
			if (custom && natural != null)
			{
				painter.paintBorder(graphics, style, natural.width, natural.height, config.frameOpacity());
			}
		}
		finally
		{
			panelComponent.getChildren().clear();
			graphics.setFont(font);
		}

		lastNatural = natural;
		lastItems = items;
		if (natural == null)
		{
			return null;
		}
		lastSize = natural;
		return lastSize;
	}

	/**
	 * Alt-dragging the corner makes RuneLite set a preferred size. Instead of stretching, that picks the
	 * nearest of the three sizes (by how much wider or narrower than now the drag made it) and is cleared.
	 */
	private void snapDrag(int naturalWidth, PlayerCountConfig.OverlaySize current)
	{
		Dimension dragged = getPreferredSize();
		if (dragged == null)
		{
			firstFrame = false;
			return;
		}
		setPreferredSize(null);
		overlayManager.saveOverlay(this);
		if (firstFrame || dragged.width <= 0)
		{
			firstFrame = false; // saved by an older build, not a drag from this session
			return;
		}
		double ratio = dragged.width / (double) naturalWidth;
		PlayerCountConfig.OverlaySize[] sizes = PlayerCountConfig.OverlaySize.values(); // FULL, MEDIUM, SMALL
		int i = current.ordinal();
		if (ratio < 0.85 && i < sizes.length - 1)
		{
			i++;
		}
		else if (ratio > 1.15 && i > 0)
		{
			i--;
		}
		if (sizes[i] != current)
		{
			configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.SIZE_KEY, sizes[i]);
		}
	}

	/** Region under the mouse on last frame's world map, or null. */
	private WorldRegion mapHover()
	{
		Point mouse = client.getMouseCanvasPosition();
		Rectangle b = getBounds();
		if (lastMap == null || mouse == null || b == null || !b.contains(mouse.getX(), mouse.getY()))
		{
			return null;
		}
		return lastMap.regionAt(mouse.getX() - b.x, mouse.getY() - b.y);
	}

	/** Underlines the clickable line under the mouse (using last frame's positions) and shows a tooltip. */
	private void markHover(List<OverlayLayout.Item> items)
	{
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null)
		{
			return;
		}
		OverlayLayout.Item over = itemAt(mouse.getX(), mouse.getY());
		if (over == null)
		{
			return;
		}
		for (OverlayLayout.Item item : items)
		{
			if (item.target != null && item.label.equals(over.label))
			{
				item.hovered = true;
				tooltipManager.add(new Tooltip("Click to hop to world " + item.target.getId()));
			}
		}
	}

	/** The clickable line drawn last frame at this canvas position, or null. */
	private OverlayLayout.Item itemAt(int x, int y)
	{
		Rectangle b = getBounds();
		if (b == null || !b.contains(x, y))
		{
			return null;
		}
		double localX = x - b.x;
		double localY = y - b.y;
		for (OverlayLayout.Item item : lastItems)
		{
			if (item.target != null && item.hitBox.contains(localX, localY))
			{
				return item;
			}
		}
		return null;
	}

	/**
	 * Left-click on a world line hops there. Alt is left alone (that's RuneLite's move/resize),
	 * and the click is consumed so the game doesn't also walk there.
	 */
	MouseEvent handleClick(MouseEvent e)
	{
		if (e.getButton() != MouseEvent.BUTTON1 || e.isAltDown())
		{
			return e;
		}
		OverlayLayout.Item item = itemAt(e.getX(), e.getY());
		if (item != null)
		{
			World target = item.target;
			e.consume();
			clientThread.invoke(() -> plugin.hopTo(target));
		}
		return e;
	}

	private boolean hovered()
	{
		Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = getBounds();
		return mouse != null && bounds != null && bounds.contains(mouse.getX(), mouse.getY());
	}

	/** The overlay's lines, in order. Layout (vertical or horizontal) is applied by {@link OverlayLayout}. */
	private List<OverlayLayout.Item> items()
	{
		List<OverlayLayout.Item> items = new ArrayList<>();
		if (!plugin.hasData())
		{
			items.add(OverlayLayout.Item.line("Loading worlds..."));
			return items;
		}

		if (config.showCurrentWorld())
		{
			int players = plugin.currentWorldPlayers();
			Color color = players > config.busyThreshold() ? DOWN : Color.WHITE;
			String count = players < 0 ? "?" : String.format("%,d", players);
			OverlayLayout.Item here = change(OverlayLayout.Item.line((mini ? "W" : "World ") + plugin.currentWorld()).add(count, color), color, plugin.currentWorldChange())
				.star(plugin.isFavourite(plugin.currentWorld()));
			items.add(flagRisk(here, plugin.currentWorldInfo(), !mini));
		}

		WorldStats.Totals totals = plugin.getTotals();
		if (config.showTotal() && totals != null)
		{
			String total = mini ? compact(totals.getTotal()) : String.format("%,d", totals.getTotal());
			items.add(change(OverlayLayout.Item.line(mini ? "OSRS" : "Total").add(total, Color.WHITE), Color.WHITE, mini ? null : plugin.totalChange()));
		}

		if (config.showMembersSplit() && totals != null)
		{
			items.add(OverlayLayout.Item.line(mini ? "P2P" : "P2P / F2P").add(compact(totals.getMembers()) + " / " + compact(totals.getFree()), Color.WHITE));
		}

		lastMap = null;
		switch (config.regionDisplay())
		{
			case LIST:
				for (Map.Entry<WorldRegion, Integer> e : plugin.regionTotals().entrySet())
				{
					items.add(OverlayLayout.Item.line("  " + WorldStats.regionCode(e.getKey())).add(compact(e.getValue()), Color.WHITE));
				}
				break;
			case TOP:
				items.add(OverlayLayout.Item.line("Regions").add(topRegions(), Color.WHITE));
				break;
			case BAR:
			{
				Map<WorldRegion, Integer> totalsByRegion = plugin.regionTotals();
				items.add(OverlayLayout.Item.custom("regions-bar", () -> new RegionGraphics.Bar(totalsByRegion), mini ? 90 : 130));
				break;
			}
			case MAP:
			{
				boolean strip = config.layout() == PlayerCountConfig.Layout.HORIZONTAL;
				int total = totals == null ? 0 : totals.getTotal();
				WorldMapComponent map = new WorldMapComponent(worldMap, plugin.regionStats(), hoverRegion, total, strip ? (mini ? 28 : 44) : 0);
				lastMap = map;
				OverlayLayout.Item item = OverlayLayout.Item.custom("regions-map", () -> map, strip ? (mini ? 70 : 108) : 200);
				item.componentHeight = strip ? (mini ? 28 : 46) : 0;
				items.add(item);
				break;
			}
			default:
				break;
		}

		if (config.showPing() && config.pingEnabled())
		{
			int ping = plugin.currentPing();
			items.add(OverlayLayout.Item.line("Ping").add(ping < 0 ? "..." : ping + (mini ? "" : " ms"), pingColor(ping)));
		}

		if (config.showNearby() && plugin.getNearby() >= 0)
		{
			items.add(OverlayLayout.Item.line(mini ? "Near" : "Nearby").add(Integer.toString(plugin.getNearby()), Color.WHITE));
		}

		if (config.showSocial())
		{
			if (plugin.getFriendsHere() >= 0)
			{
				items.add(OverlayLayout.Item.line("Friends here").add(Integer.toString(plugin.getFriendsHere()), Color.WHITE));
			}
			if (plugin.getClanHere() >= 0)
			{
				items.add(OverlayLayout.Item.line("Clan here").add(Integer.toString(plugin.getClanHere()), Color.WHITE));
			}
		}

		if (config.showSuggestion() && config.finderMode() != PlayerCountConfig.FinderMode.OFF)
		{
			World s = plugin.getSuggestion();
			String label = config.finderMode() == PlayerCountConfig.FinderMode.THEMED
				? (mini ? config.theme().shortLabel() : config.theme().toString())
				: (mini ? shortFinder() : config.finderMode().toString());
			if (s == null)
			{
				items.add(OverlayLayout.Item.line(label).add("none", MUTED));
			}
			else
			{
				int ping = config.pingEnabled() ? plugin.pingOf(s.getId()) : -1;
				String risk = config.warnRisky() ? WorldStats.risk(s) : null;
				boolean deadly = risk != null && WorldStats.deadly(s);
				Color worldColor = risk == null ? UP : deadly ? OverlayLayout.RISK : OverlayLayout.CAUTION;
				items.add(OverlayLayout.Item.line(label).add(mini ? "W" + s.getId() : worldText(s, true), worldColor)
					.add(ping < 0 || mini ? "" : " " + ping + "ms", pingColor(ping))
					.add(risk == null ? "" : " " + risk, worldColor)
					.target(s).warn(deadly).pulse(deadly).caution(risk != null && !deadly));
			}
		}

		for (Map.Entry<String, World> e : plugin.activityWorlds().entrySet())
		{
			World w = e.getValue();
			items.add(flagRisk(OverlayLayout.Item.line(e.getKey()).add(w == null ? "none" : worldText(w, false), w == null ? MUTED : Color.WHITE).target(w), w, !mini));
		}

		if (config.showRecords())
		{
			DailyRecords r = plugin.getRecords();
			if (r.hasData(Instant.now()))
			{
				items.add(OverlayLayout.Item.line("Today peak").add(compact(r.getPeak()) + " at " + CLOCK.format(r.getPeakAt()), Color.WHITE));
				items.add(OverlayLayout.Item.line("Today low").add(compact(r.getLow()) + " at " + CLOCK.format(r.getLowAt()), Color.WHITE));
			}
		}

		Instant updated = plugin.getLastUpdate();
		if (config.showUpdated() && updated != null)
		{
			long secs = Duration.between(updated, Instant.now()).getSeconds();
			items.add(OverlayLayout.Item.line("Updated").add(secs < 60 ? secs + "s ago" : (secs / 60) + "m ago", MUTED));
		}

		if (config.showGraph())
		{
			Duration window = Duration.ofHours(Math.max(1, config.graphHours()));
			int world = config.graphSeries() == PlayerCountConfig.GraphSeries.CURRENT_WORLD ? plugin.currentWorld() : -1;
			Instant now = Instant.now();
			items.add(OverlayLayout.Item.graph(plugin.getHistory().series(now, window, world), now, window));
		}
		return items;
	}

	/** "US 74.0k  UK 18.3k  DE 13.1k  +5" */
	private String topRegions()
	{
		StringBuilder sb = new StringBuilder();
		int shown = 0;
		Map<WorldRegion, Integer> all = plugin.regionTotals();
		for (Map.Entry<WorldRegion, Integer> e : all.entrySet())
		{
			if (shown++ == 3)
			{
				break;
			}
			sb.append(sb.length() == 0 ? "" : "  ").append(WorldStats.regionCode(e.getKey())).append(' ').append(compact(e.getValue()));
		}
		if (all.size() > 3)
		{
			sb.append("  +").append(all.size() - 3);
		}
		return sb.toString();
	}

	private String shortFinder()
	{
		switch (config.finderMode())
		{
			case BUSIEST:
				return "Busy";
			case LOWEST_PING:
				return "Fast";
			case THEMED:
				return "Theme";
			default:
				return "Quiet";
		}
	}

	/**
	 * Marks a line red (pulsing) for worlds where you can be attacked almost anywhere, or orange (underlined)
	 * for other PvP-themed and separate-account worlds, and adds a short tag like "High risk".
	 */
	private OverlayLayout.Item flagRisk(OverlayLayout.Item item, World w, boolean tag)
	{
		String risk = w == null || !config.warnRisky() ? null : WorldStats.risk(w);
		if (risk == null)
		{
			return item;
		}
		boolean deadly = WorldStats.deadly(w);
		Color c = deadly ? OverlayLayout.RISK : OverlayLayout.CAUTION;
		if (tag)
		{
			item.add(" " + risk, c);
		}
		return item.warn(deadly).pulse(deadly).caution(!deadly);
	}

	/** Appends the +/- in green/red (or the value's colour if colouring is off). */
	private OverlayLayout.Item change(OverlayLayout.Item item, Color valueColor, Integer change)
	{
		if (config.showChange() && change != null && change != 0)
		{
			Color color = !config.colorChanges() ? valueColor : change > 0 ? UP : DOWN;
			item.add(String.format(" (%+,d)", change), color);
		}
		return item;
	}

	private String worldText(World w, boolean withRegion)
	{
		StringBuilder s = new StringBuilder(String.format("W%d (%,d)", w.getId(), w.getPlayers()));
		if (withRegion)
		{
			s.append(' ').append(WorldStats.regionCode(w.getRegion()));
		}
		return s.toString().trim();
	}

	static Color pingColor(int ping)
	{
		if (ping < 0)
		{
			return MUTED;
		}
		return ping < 80 ? UP : ping < 150 ? AMBER : DOWN;
	}

	static String compact(int n)
	{
		if (n >= 1000)
		{
			return String.format("%.1fk", n / 1000.0);
		}
		return Integer.toString(n);
	}
}
