package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.http.api.worlds.WorldRegion;

/** The region share bar (the world map lives in {@link WorldMap}). */
final class RegionGraphics
{
	private RegionGraphics()
	{
	}

	static final Map<WorldRegion, Color> COLORS = new EnumMap<>(WorldRegion.class);

	static
	{
		COLORS.put(WorldRegion.UNITED_STATES_OF_AMERICA, new Color(0x4F, 0x9D, 0xF0));
		COLORS.put(WorldRegion.UNITED_KINGDOM, new Color(0xF0, 0x6A, 0x5A));
		COLORS.put(WorldRegion.GERMANY, new Color(0xF2, 0xC9, 0x4C));
		COLORS.put(WorldRegion.AUSTRALIA, new Color(0x6C, 0xC9, 0x5A));
		COLORS.put(WorldRegion.BRAZIL, new Color(0x3F, 0xC8, 0xB4));
		COLORS.put(WorldRegion.JAPAN, new Color(0xE8, 0x7C, 0xC4));
		COLORS.put(WorldRegion.SINGAPORE, new Color(0xA8, 0x8C, 0xF0));
		COLORS.put(WorldRegion.SOUTH_AFRICA, new Color(0xF0, 0x9A, 0x48));
	}

	static Color color(WorldRegion r)
	{
		return COLORS.getOrDefault(r, Color.LIGHT_GRAY);
	}

	/** One bar split by region share, region codes written in segments wide enough to hold them. */
	static final class Bar implements LayoutableRenderableEntity
	{
		private final Map<WorldRegion, Integer> totals;
		private final Rectangle bounds = new Rectangle();
		private Point location = new Point();
		private Dimension size = new Dimension(129, 0);

		Bar(Map<WorldRegion, Integer> totals)
		{
			this.totals = totals;
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			java.awt.Font before = g.getFont();
			g.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics fm = g.getFontMetrics();
			int h = fm.getHeight() + 3;
			int sum = 0;
			for (int v : totals.values())
			{
				sum += v;
			}
			int x = location.x;
			int y = location.y + 1 + Math.max(0, (size.height - h - 3) / 2);
			int w = size.width;
			int drawn = 0;
			int i = 0;
			for (Map.Entry<WorldRegion, Integer> e : totals.entrySet())
			{
				i++;
				int segW = i == totals.size() ? w - drawn : (int) Math.round(w * (e.getValue() / (double) Math.max(1, sum)));
				Color c = color(e.getKey());
				g.setColor(c.darker());
				g.fillRect(x + drawn, y, segW, h);
				g.setColor(c);
				g.fillRect(x + drawn, y, segW, h - 2);
				String code = WorldStats.regionCode(e.getKey());
				if (fm.stringWidth(code) + 4 <= segW)
				{
					text(g, code, new Color(0x10, 0x10, 0x10), x + drawn + (segW - fm.stringWidth(code)) / 2, y + fm.getAscent() + 1, false);
				}
				drawn += segW;
			}
			g.setColor(new Color(0, 0, 0, 160));
			g.drawRect(x, y, w - 1, h - 1);
			g.setFont(before);
			Dimension d = new Dimension(w, Math.max(h + 3, size.height));
			bounds.setBounds(location.x, location.y, d.width, d.height);
			return d;
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

	private static void text(Graphics2D g, String s, Color c, int x, int y, boolean shadow)
	{
		if (shadow)
		{
			TextComponent t = new TextComponent();
			t.setText(s);
			t.setColor(c);
			t.setPosition(new Point(x, y));
			t.render(g);
		}
		else
		{
			g.setColor(c);
			g.drawString(s, x, y);
		}
	}
}
