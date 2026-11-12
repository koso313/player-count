package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Map;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.http.api.worlds.WorldRegion;

/** The glowing world map as an overlay row (vertical) or cell (horizontal), with a players-online caption. */
class WorldMapComponent implements LayoutableRenderableEntity
{
	private final WorldMap map;
	private final Map<WorldRegion, WorldMap.RegionStat> stats;
	private final WorldRegion hot;
	private final int total;
	/** 0 = height follows width; otherwise the cell height in the horizontal strip. */
	private final int fixedHeight;

	private final Rectangle bounds = new Rectangle();
	private Point location = new Point();
	private Dimension size = new Dimension(190, 0);

	WorldMapComponent(WorldMap map, Map<WorldRegion, WorldMap.RegionStat> stats, WorldRegion hot, int total, int fixedHeight)
	{
		this.map = map;
		this.stats = stats;
		this.hot = hot;
		this.total = total;
		this.fixedHeight = fixedHeight;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		java.awt.Font before = g.getFont();
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		boolean caption = fixedHeight == 0;
		int w = size.width;
		int mapH = fixedHeight > 0 ? fixedHeight : (int) Math.round(w * WorldMap.ASPECT);
		int mapW = fixedHeight > 0 ? Math.min(w, (int) Math.round(mapH / WorldMap.ASPECT)) : w;
		int mx = location.x + (w - mapW) / 2;
		map.paint(g, new Rectangle(mx, location.y + 2, mapW, mapH), stats, hot);
		int h = mapH + 2;
		if (caption)
		{
			String text = String.format("%,d Players Online", total);
			TextComponent t = new TextComponent();
			t.setText(text);
			t.setColor(FramePainter.OSRS_ORANGE);
			t.setPosition(new Point(location.x + (w - fm.stringWidth(text)) / 2, location.y + h + fm.getAscent() + 1));
			t.render(g);
			h += fm.getHeight() + 2;
		}
		g.setFont(before);
		Dimension d = new Dimension(w, h);
		bounds.setBounds(location.x, location.y, d.width, d.height);
		return d;
	}

	/** Region under a point in the overlay's own coordinates, or null. */
	WorldRegion regionAt(int x, int y)
	{
		return bounds.contains(x, y) ? map.regionAt(x, y) : null;
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point p)
	{
		location = p;
	}

	@Override
	public void setPreferredSize(Dimension d)
	{
		size = d;
	}
}
