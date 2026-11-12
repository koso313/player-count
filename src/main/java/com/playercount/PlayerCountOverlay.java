package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;

/**
 * The movable overlay. Drawn at its natural size and then scaled, so it can be resized
 * from the Size setting or by Alt-dragging its corner (the drag is turned into a new Size).
 */
class PlayerCountOverlay extends Overlay
{
	static final Color UP = new Color(0x66, 0xDD, 0x66);
	static final Color DOWN = new Color(0xEE, 0x66, 0x66);
	static final Color AMBER = new Color(0xFF, 0xD0, 0x40);
	private static final Color MUTED = new Color(0xAA, 0xAA, 0xAA);
	private static final Color HIDDEN_HINT = new Color(255, 255, 255, 40);
	/** Space between a line's label and its value. */
	private static final int GAP = 10;
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

	private final PlayerCountPlugin plugin;
	private final PlayerCountConfig config;
	private final Client client;
	private final SpriteManager spriteManager;
	private final ConfigManager configManager;
	private final OverlayManager overlayManager;
	private final FramePainter painter = new FramePainter();
	private final PanelComponent panelComponent = new PanelComponent();

	// Per-frame layout state
	private FontMetrics metrics;
	private int contentWidth;
	private PlayerCountConfig.FrameStyle style = PlayerCountConfig.FrameStyle.RUNELITE;
	private Color labelColor = Color.WHITE;
	/** Unscaled size last frame; PanelComponent itself sizes from the previous frame too. */
	private Dimension lastNatural;
	/** Size on screen last frame, kept while hidden-until-hover so the hover area stays put. */
	private Dimension lastScaled;

	@Inject
	PlayerCountOverlay(PlayerCountPlugin plugin, PlayerCountConfig config, Client client, SpriteManager spriteManager,
		ConfigManager configManager, OverlayManager overlayManager)
	{
		super(plugin);
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
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset size", "Player Count overlay",
			e -> configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.SCALE_KEY, 100));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "Player Count overlay");
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (plugin.hiddenBySituation())
		{
			return null;
		}
		if (config.hideMode() == PlayerCountConfig.HideMode.UNTIL_HOVER && !hovered() && lastScaled != null)
		{
			// Keep the space (so hovering it brings the overlay back) with just a faint outline.
			graphics.setColor(HIDDEN_HINT);
			graphics.drawRect(0, 0, lastScaled.width - 1, lastScaled.height - 1);
			return lastScaled;
		}

		metrics = graphics.getFontMetrics();
		contentWidth = 0;
		style = config.frameStyle();
		if (style == PlayerCountConfig.FrameStyle.STEEL && !painter.loadSteel(id -> spriteManager.getSprite(id, 0)))
		{
			style = PlayerCountConfig.FrameStyle.RUNELITE; // sprites not loaded yet
		}
		labelColor = FramePainter.labelColor(style);
		panelComponent.getChildren().clear();

		buildLines();

		Rectangle insets = FramePainter.insets(style);
		int naturalWidth = Math.max(FramePainter.minWidth(style), contentWidth + insets.x + insets.width + 2);
		double scale = scale(naturalWidth);

		panelComponent.setPreferredSize(new Dimension(naturalWidth, 0));
		panelComponent.setBorder(insets);
		boolean custom = style != PlayerCountConfig.FrameStyle.RUNELITE;
		panelComponent.setBackgroundColor(custom ? null : ComponentConstants.STANDARD_BACKGROUND_COLOR);

		AffineTransform before = graphics.getTransform();
		Object interpolation = graphics.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
		graphics.scale(scale, scale);
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
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
			graphics.setTransform(before);
			if (interpolation != null)
			{
				graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
			}
			panelComponent.getChildren().clear();
		}

		lastNatural = natural;
		if (natural == null)
		{
			return null;
		}
		lastScaled = new Dimension((int) Math.ceil(natural.width * scale), (int) Math.ceil(natural.height * scale));
		return lastScaled;
	}

	/**
	 * The Size setting as a factor. If the user has just Alt-dragged the corner, RuneLite will have set a
	 * preferred size; that's converted into a new Size and cleared, so the drag scales instead of stretching.
	 */
	private double scale(int naturalWidth)
	{
		Dimension dragged = getPreferredSize();
		if (dragged != null)
		{
			setPreferredSize(null);
			int pct = (int) Math.round(dragged.width * 100.0 / naturalWidth);
			pct = Math.max(PlayerCountConfig.MIN_SCALE, Math.min(PlayerCountConfig.MAX_SCALE, pct));
			if (pct != config.overlayScale())
			{
				configManager.setConfiguration(PlayerCountConfig.GROUP, PlayerCountConfig.SCALE_KEY, pct);
			}
			overlayManager.saveOverlay(this);
			return pct / 100.0;
		}
		return Math.max(PlayerCountConfig.MIN_SCALE, Math.min(PlayerCountConfig.MAX_SCALE, config.overlayScale())) / 100.0;
	}

	private boolean hovered()
	{
		Point mouse = client.getMouseCanvasPosition();
		Rectangle bounds = getBounds();
		return mouse != null && bounds != null && bounds.contains(mouse.getX(), mouse.getY());
	}

	private void buildLines()
	{
		if (config.showTitle())
		{
			String title = "Player Count";
			contentWidth = metrics.stringWidth(title);
			panelComponent.getChildren().add(TitleComponent.builder().text(title).color(FramePainter.titleColor(style)).build());
			if (style != PlayerCountConfig.FrameStyle.RUNELITE)
			{
				panelComponent.getChildren().add(new DividerComponent(painter, style, FramePainter.insets(style)));
			}
		}

		if (!plugin.hasData())
		{
			line("Loading worlds...", "", Color.WHITE);
			return;
		}

		if (config.showCurrentWorld())
		{
			int players = plugin.currentWorldPlayers();
			Color color = players > config.busyThreshold() ? DOWN : Color.WHITE;
			String count = players < 0 ? "?" : String.format("%,d", players);
			changeLine("World " + plugin.currentWorld(), count, color, plugin.currentWorldChange());
		}

		WorldStats.Totals totals = plugin.getTotals();
		if (config.showTotal() && totals != null)
		{
			changeLine("Total", String.format("%,d", totals.getTotal()), Color.WHITE, plugin.totalChange());
		}

		if (config.showMembersSplit() && totals != null)
		{
			line("P2P / F2P", compact(totals.getMembers()) + " / " + compact(totals.getFree()), Color.WHITE);
		}

		if (config.showRegions())
		{
			for (Map.Entry<WorldRegion, Integer> e : plugin.regionTotals().entrySet())
			{
				line("  " + WorldStats.regionCode(e.getKey()), compact(e.getValue()), Color.WHITE);
			}
		}

		if (config.showPing() && config.pingEnabled())
		{
			int ping = plugin.currentPing();
			line("Ping", ping < 0 ? "..." : ping + " ms", pingColor(ping));
		}

		if (config.showNearby() && plugin.getNearby() >= 0)
		{
			line("Nearby", Integer.toString(plugin.getNearby()), Color.WHITE);
		}

		if (config.showSocial())
		{
			if (plugin.getFriendsHere() >= 0)
			{
				line("Friends here", Integer.toString(plugin.getFriendsHere()), Color.WHITE);
			}
			if (plugin.getClanHere() >= 0)
			{
				line("Clan here", Integer.toString(plugin.getClanHere()), Color.WHITE);
			}
		}

		if (config.showSuggestion() && config.finderMode() != PlayerCountConfig.FinderMode.OFF)
		{
			World s = plugin.getSuggestion();
			String label = config.finderMode().toString();
			if (s == null)
			{
				line(label, "none", MUTED);
			}
			else
			{
				line(label, worldText(s, true), UP);
			}
		}

		for (Map.Entry<String, World> e : plugin.activityWorlds().entrySet())
		{
			World w = e.getValue();
			line(e.getKey(), w == null ? "none" : worldText(w, false), w == null ? MUTED : Color.WHITE);
		}

		if (config.showRecords())
		{
			DailyRecords r = plugin.getRecords();
			if (r.hasData(Instant.now()))
			{
				line("Today peak", compact(r.getPeak()) + " at " + CLOCK.format(r.getPeakAt()), Color.WHITE);
				line("Today low", compact(r.getLow()) + " at " + CLOCK.format(r.getLowAt()), Color.WHITE);
			}
		}

		Instant updated = plugin.getLastUpdate();
		if (config.showUpdated() && updated != null)
		{
			long secs = Duration.between(updated, Instant.now()).getSeconds();
			line("Updated", secs < 60 ? secs + "s ago" : (secs / 60) + "m ago", MUTED);
		}

		if (config.showGraph())
		{
			Duration window = Duration.ofHours(Math.max(1, config.graphHours()));
			int world = config.graphSeries() == PlayerCountConfig.GraphSeries.CURRENT_WORLD ? plugin.currentWorld() : -1;
			Instant now = Instant.now();
			panelComponent.getChildren().add(new GraphComponent(plugin.getHistory().series(now, window, world), now, window));
		}
	}

	private void add(RichLineComponent c)
	{
		contentWidth = Math.max(contentWidth, c.neededWidth(metrics, GAP));
		panelComponent.getChildren().add(c);
	}

	private void line(String left, String right, Color rightColor)
	{
		add(new RichLineComponent(left, labelColor).add(right, rightColor));
	}

	/** A count followed by its +/- in green/red (or the count's colour if colouring is off). */
	private void changeLine(String left, String value, Color valueColor, Integer change)
	{
		RichLineComponent c = new RichLineComponent(left, labelColor).add(value, valueColor);
		if (config.showChange() && change != null && change != 0)
		{
			Color color = !config.colorChanges() ? valueColor : change > 0 ? UP : DOWN;
			c.add(String.format(" (%+,d)", change), color);
		}
		add(c);
	}

	private String worldText(World w, boolean withRegion)
	{
		StringBuilder s = new StringBuilder(String.format("W%d (%,d)", w.getId(), w.getPlayers()));
		if (withRegion)
		{
			s.append(' ').append(WorldStats.regionCode(w.getRegion()));
			int ping = config.pingEnabled() ? plugin.pingOf(w.getId()) : -1;
			if (ping >= 0)
			{
				s.append(' ').append(ping).append("ms");
			}
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
