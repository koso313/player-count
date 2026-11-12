package com.playercount;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Outlines players who just arrived (per the Player arrives alert) with their name and combat level. */
class ArrivalOverlay extends Overlay
{
	private final PlayerCountPlugin plugin;
	private final PlayerCountConfig config;

	@Inject
	ArrivalOverlay(PlayerCountPlugin plugin, PlayerCountConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Map<Player, Instant> arrivals = plugin.getArrivals();
		if (arrivals.isEmpty() || !config.arrivalHighlight())
		{
			return null;
		}
		Instant now = Instant.now();
		Color color = config.arrivalColor();
		Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), 40);
		Iterator<Map.Entry<Player, Instant>> it = arrivals.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Player, Instant> e = it.next();
			if (now.isAfter(e.getValue()))
			{
				it.remove();
				continue;
			}
			Player p = e.getKey();
			Polygon tile = p.getCanvasTilePoly();
			if (tile != null)
			{
				OverlayUtil.renderPolygon(g, tile, color, fill, new BasicStroke(2f));
			}
			String label = p.getName() == null ? "Player" : p.getName() + " (" + p.getCombatLevel() + ")";
			Point at = p.getCanvasTextLocation(g, label, p.getLogicalHeight() + 40);
			if (at != null)
			{
				OverlayUtil.renderTextLocation(g, at, label, color);
			}
		}
		return null;
	}
}
