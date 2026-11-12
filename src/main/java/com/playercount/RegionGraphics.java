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

/** Compact ways to show players per region: a share bar, and a dot-matrix world map with bubbles. */
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

	/** Where each region's servers are, as longitude/latitude, for the map bubbles. */
	private static final Map<WorldRegion, double[]> PLACES = new EnumMap<>(WorldRegion.class);

	static
	{
		PLACES.put(WorldRegion.UNITED_STATES_OF_AMERICA, new double[]{-97, 39});
		PLACES.put(WorldRegion.UNITED_KINGDOM, new double[]{-3, 54});
		PLACES.put(WorldRegion.GERMANY, new double[]{12, 50});
		PLACES.put(WorldRegion.AUSTRALIA, new double[]{134, -25});
		PLACES.put(WorldRegion.BRAZIL, new double[]{-50, -12});
		PLACES.put(WorldRegion.JAPAN, new double[]{139, 37});
		PLACES.put(WorldRegion.SINGAPORE, new double[]{104, 1});
		PLACES.put(WorldRegion.SOUTH_AFRICA, new double[]{25, -29});
	}

	/** Very rough continent outlines (longitude, latitude pairs), only meant to read as a world map. */
	private static final double[][] LAND = {
		// North America
		{-168, 66, -140, 70, -95, 72, -80, 73, -62, 60, -55, 52, -66, 45, -75, 35, -81, 25, -97, 26, -97, 17, -87, 14, -78, 8,
			-90, 15, -105, 20, -117, 32, -125, 40, -124, 48, -135, 58, -150, 60, -165, 60},
		// Greenland
		{-52, 82, -20, 82, -20, 70, -44, 60, -54, 68},
		// South America
		{-80, 10, -60, 10, -50, 0, -35, -7, -40, -22, -48, -28, -58, -38, -66, -55, -72, -50, -72, -30, -70, -18, -81, -5},
		// Europe
		{-10, 36, -9, 43, -1, 46, -4, 48, 0, 50, 5, 53, 8, 57, 5, 62, 15, 69, 28, 71, 40, 67, 40, 55, 30, 45, 28, 41, 20, 40, 15, 38, 10, 44, 3, 43},
		// Great Britain and Ireland
		{-6, 50, 2, 51, 0, 55, -3, 58, -6, 56},
		{-10, 52, -6, 52, -6, 55, -9, 55},
		// Africa
		{-17, 21, -10, 30, -5, 36, 10, 37, 20, 32, 32, 31, 35, 28, 43, 12, 51, 12, 40, -5, 40, -15, 33, -25, 20, -35, 17, -30, 12, -17, 9, -2, 5, 5,
			-8, 5, -17, 14},
		// Madagascar
		{44, -13, 50, -15, 47, -25, 44, -24},
		// Asia
		{28, 41, 40, 55, 40, 67, 60, 70, 80, 73, 110, 76, 140, 72, 170, 66, 180, 65, 160, 58, 140, 50, 130, 42, 122, 40, 122, 30, 110, 20, 108, 12,
			100, 13, 100, 5, 95, 15, 88, 22, 80, 15, 77, 8, 72, 20, 66, 25, 57, 25, 52, 15, 43, 12, 35, 28, 35, 36},
		// Japan
		{130, 31, 141, 35, 142, 42, 140, 45, 136, 37},
		// Indonesia / New Guinea
		{95, 5, 105, -6, 120, -9, 140, -8, 150, -10, 140, -3, 130, -2, 118, 3, 108, 2},
		// Australia
		{114, -22, 122, -17, 130, -12, 137, -12, 142, -11, 146, -19, 153, -27, 150, -37, 140, -38, 131, -31, 115, -34},
		// New Zealand
		{172, -35, 178, -38, 172, -46, 166, -46},
	};

	private static final double LAT_TOP = 78;
	private static final double LAT_BOTTOM = -56;

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

	/** Dot-matrix world map with a bubble per region, sized by its players and labelled with the count. */
	static final class Map2D implements LayoutableRenderableEntity
	{
		private static final int DOT = 3;

		private final Map<WorldRegion, Integer> totals;
		private final int height;
		private final boolean labels;
		private final Rectangle bounds = new Rectangle();
		private Point location = new Point();
		private Dimension size = new Dimension(160, 0);

		Map2D(Map<WorldRegion, Integer> totals, int height, boolean labels)
		{
			this.totals = totals;
			this.height = height;
			this.labels = labels;
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			int x0 = location.x;
			int y0 = location.y + 1;
			int w = size.width;
			int h = height;

			// Land as dots
			Polygon[] land = new Polygon[LAND.length];
			for (int i = 0; i < LAND.length; i++)
			{
				Polygon p = new Polygon();
				for (int j = 0; j < LAND[i].length; j += 2)
				{
					p.addPoint((int) Math.round(px(LAND[i][j], w)), (int) Math.round(py(LAND[i][j + 1], h)));
				}
				land[i] = p;
			}
			g.setColor(new Color(0xC8, 0xBC, 0x9A, 120));
			for (int y = DOT / 2; y < h; y += DOT)
			{
				for (int x = DOT / 2; x < w; x += DOT)
				{
					for (Polygon p : land)
					{
						if (p.contains(x, y))
						{
							g.fillRect(x0 + x - 1, y0 + y - 1, 2, 2);
							break;
						}
					}
				}
			}

			// Bubbles, biggest first so small ones sit on top
			int max = 1;
			for (int v : totals.values())
			{
				max = Math.max(max, v);
			}
			Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			java.awt.Font before = g.getFont();
			g.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics fm = g.getFontMetrics();
			double maxR = Math.max(4, h / 6.0);
			for (Map.Entry<WorldRegion, Integer> e : totals.entrySet())
			{
				double[] at = PLACES.get(e.getKey());
				if (at == null)
				{
					continue;
				}
				double r = Math.max(2.5, maxR * Math.sqrt(e.getValue() / (double) max));
				double cx = x0 + px(at[0], w);
				double cy = y0 + py(at[1], h);
				Color c = color(e.getKey());
				g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 90));
				g.fill(new java.awt.geom.Ellipse2D.Double(cx - r - 1.5, cy - r - 1.5, 2 * r + 3, 2 * r + 3));
				g.setColor(c);
				g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
			if (labels)
			{
				// Biggest first; each label tries right, left, above, below its bubble and is skipped if all overlap.
				java.util.List<Rectangle> taken = new java.util.ArrayList<>();
				Rectangle area = new Rectangle(x0, y0, w, h);
				for (Map.Entry<WorldRegion, Integer> e : totals.entrySet())
				{
					double[] at = PLACES.get(e.getKey());
					if (at == null)
					{
						continue;
					}
					int r = (int) Math.ceil(Math.max(2.5, maxR * Math.sqrt(e.getValue() / (double) max)));
					int cx = (int) Math.round(x0 + px(at[0], w));
					int cy = (int) Math.round(y0 + py(at[1], h));
					taken.add(new Rectangle(cx - r, cy - r, 2 * r, 2 * r));
				}
				for (Map.Entry<WorldRegion, Integer> e : totals.entrySet())
				{
					double[] at = PLACES.get(e.getKey());
					if (at == null)
					{
						continue;
					}
					String s = WorldStats.regionCode(e.getKey()) + " " + PlayerCountOverlay.compact(e.getValue());
					int r = (int) Math.ceil(Math.max(2.5, maxR * Math.sqrt(e.getValue() / (double) max)));
					int cx = (int) Math.round(x0 + px(at[0], w));
					int cy = (int) Math.round(y0 + py(at[1], h));
					int tw = fm.stringWidth(s);
					int th = fm.getAscent();
					Rectangle[] spots = {
						new Rectangle(cx + r + 2, cy - th / 2, tw, th),
						new Rectangle(cx - r - 2 - tw, cy - th / 2, tw, th),
						new Rectangle(cx - tw / 2, cy - r - 2 - th, tw, th),
						new Rectangle(cx - tw / 2, cy + r + 2, tw, th),
					};
					for (Rectangle spot : spots)
					{
						if (!area.contains(spot))
						{
							continue;
						}
						boolean clear = true;
						for (Rectangle t : taken)
						{
							if (t.intersects(spot) && !t.contains(cx, cy))
							{
								clear = false;
								break;
							}
						}
						if (clear)
						{
							taken.add(spot);
							text(g, s, Color.WHITE, spot.x, spot.y + th, true);
							break;
						}
					}
				}
			}
			g.setFont(before);
			Dimension d = new Dimension(w, h + 2);
			bounds.setBounds(location.x, location.y, d.width, d.height);
			return d;
		}

		private static double px(double lon, int w)
		{
			return (lon + 170) / 350.0 * w;
		}

		private static double py(double lat, int h)
		{
			return (LAT_TOP - lat) / (LAT_TOP - LAT_BOTTOM) * h;
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
