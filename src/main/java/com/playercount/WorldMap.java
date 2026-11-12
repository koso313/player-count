package com.playercount;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.Value;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;

/**
 * A dark world map where each region's players appear as glowing dots clustered around the cities its
 * servers serve: more players, more dots. Land outline from Natural Earth (public domain).
 */
final class WorldMap
{
	@Value
	static class RegionStat
	{
		int players;
		int worlds;
	}

	/** Players per region and how many worlds each has. */
	static Map<WorldRegion, RegionStat> stats(List<World> worlds)
	{
		Map<WorldRegion, int[]> acc = new EnumMap<>(WorldRegion.class);
		for (World w : worlds)
		{
			if (w.getRegion() == null || w.getPlayers() < 0)
			{
				continue;
			}
			int[] a = acc.computeIfAbsent(w.getRegion(), k -> new int[2]);
			a[0] += Math.max(0, w.getPlayers());
			a[1]++;
		}
		Map<WorldRegion, RegionStat> out = new EnumMap<>(WorldRegion.class);
		acc.forEach((r, a) -> out.put(r, new RegionStat(a[0], a[1])));
		return out;
	}

	static String regionName(WorldRegion r)
	{
		switch (r)
		{
			case UNITED_STATES_OF_AMERICA:
				return "United States";
			case UNITED_KINGDOM:
				return "United Kingdom";
			case GERMANY:
				return "Germany";
			case AUSTRALIA:
				return "Australia";
			case BRAZIL:
				return "Brazil";
			case JAPAN:
				return "Japan";
			case SINGAPORE:
				return "Singapore";
			case SOUTH_AFRICA:
				return "South Africa";
			default:
				return r.name();
		}
	}

	// Map window: equirectangular, cropped to where people live
	private static final double LON_MIN = -168;
	private static final double LON_MAX = 180;
	private static final double LAT_MAX = 76;
	private static final double LAT_MIN = -50;
	static final double ASPECT = (LAT_MAX - LAT_MIN) / (LON_MAX - LON_MIN);

	/** About one dot per this many players, so the busiest region looks busiest. */
	private static final double PLAYERS_PER_DOT = 300;
	private static final int MAX_DOTS = 360;

	/** Cities each region's servers mostly serve: lon, lat, weight. */
	private static final Map<WorldRegion, double[][]> ANCHORS = new EnumMap<>(WorldRegion.class);
	private static final Map<WorldRegion, Double> SPREAD = new EnumMap<>(WorldRegion.class);

	static
	{
		ANCHORS.put(WorldRegion.UNITED_STATES_OF_AMERICA, new double[][]{
			{-74, 40.7, 3}, {-87.6, 41.9, 2}, {-118.2, 34, 2}, {-122.3, 47.6, 1}, {-96.8, 32.8, 2}, {-84.4, 33.7, 2},
			{-80.2, 25.8, 1}, {-104.9, 39.7, 1}, {-79.4, 43.7, 1.5}, {-73.6, 45.5, 1}, {-123.1, 49.3, 1}, {-99.1, 19.4, 0.7},
			{-77, 38.9, 2}, {-71.1, 42.4, 1.5}, {-93.3, 45, 1}, {-112.1, 33.4, 1}, {-122.4, 37.8, 1.5}, {-95.4, 29.8, 1.5}});
		ANCHORS.put(WorldRegion.UNITED_KINGDOM, new double[][]{
			{-0.1, 51.5, 3}, {-2.2, 53.5, 2}, {-1.9, 52.5, 1.5}, {-4.25, 55.9, 1}, {-6.3, 53.3, 1}, {-3.2, 51.5, 0.7},
			{-1.5, 53.8, 1}, {2.35, 48.85, 1.5}, {4.9, 52.4, 1}, {4.35, 50.85, 0.7}, {-3.7, 40.4, 0.7}, {10.75, 59.9, 0.6},
			{18.1, 59.3, 0.8}, {-9.1, 38.7, 0.5}});
		ANCHORS.put(WorldRegion.GERMANY, new double[][]{
			{13.4, 52.5, 2}, {8.7, 50.1, 2}, {11.6, 48.1, 1.5}, {10, 53.5, 1}, {16.4, 48.2, 1}, {21, 52.2, 1.5}, {14.4, 50.1, 1},
			{12.5, 41.9, 0.7}, {24.9, 60.2, 0.7}, {19, 47.5, 0.7}, {8.5, 47.4, 0.7}, {12.6, 55.7, 0.8}, {6.8, 51.2, 1.2}});
		ANCHORS.put(WorldRegion.AUSTRALIA, new double[][]{
			{151.2, -33.9, 3}, {145, -37.8, 2.5}, {153, -27.5, 1.5}, {115.9, -32, 1}, {138.6, -34.9, 0.8},
			{174.8, -36.8, 1}, {174.8, -41.3, 0.5}, {149.1, -35.3, 0.5}});
		ANCHORS.put(WorldRegion.BRAZIL, new double[][]{
			{-46.6, -23.5, 3}, {-43.2, -22.9, 2}, {-58.4, -34.6, 1.5}, {-70.6, -33.4, 1}, {-47.9, -15.8, 1}, {-77, -12, 0.7},
			{-74.1, 4.7, 0.7}, {-38.5, -12.9, 0.8}, {-49.3, -25.4, 0.8}});
		ANCHORS.put(WorldRegion.JAPAN, new double[][]{
			{139.7, 35.7, 3}, {135.5, 34.7, 1.5}, {127, 37.5, 1.5}, {121.5, 25, 1}, {114.2, 22.3, 1}, {141.3, 43.1, 0.5}});
		ANCHORS.put(WorldRegion.SINGAPORE, new double[][]{
			{103.8, 1.35, 2}, {101.7, 3.1, 1}, {100.5, 13.75, 1}, {121, 14.6, 1.5}, {106.8, -6.2, 1.5}, {106.7, 10.8, 0.7},
			{77.2, 28.6, 0.6}, {72.9, 19, 0.6}, {55.3, 25.2, 0.5}});
		ANCHORS.put(WorldRegion.SOUTH_AFRICA, new double[][]{
			{28, -26.2, 2}, {18.4, -33.9, 1.5}, {31, -29.9, 1}, {36.8, -1.3, 0.5}, {3.4, 6.5, 0.5}, {31.2, 30, 0.5}});

		SPREAD.put(WorldRegion.UNITED_STATES_OF_AMERICA, 3.2);
		SPREAD.put(WorldRegion.UNITED_KINGDOM, 1.6);
		SPREAD.put(WorldRegion.GERMANY, 1.8);
		SPREAD.put(WorldRegion.AUSTRALIA, 2.2);
		SPREAD.put(WorldRegion.BRAZIL, 2.6);
		SPREAD.put(WorldRegion.JAPAN, 1.6);
		SPREAD.put(WorldRegion.SINGAPORE, 2.2);
		SPREAD.put(WorldRegion.SOUTH_AFRICA, 2.0);
	}

	private static final Path2D LAND = loadLand();

	private static final Color LAND_FILL = new Color(0x2B, 0x34, 0x3E, 235);
	private static final Color LAND_EDGE = new Color(0x5E, 0x6E, 0x7C);
	private static final Color HALO = new Color(0x8C, 0xD2, 0xFF, 70);
	private static final Color CORE = new Color(0xEE, 0xF8, 0xFF);
	private static final Color HOT_HALO = new Color(0xFF, 0xD0, 0x60, 110);
	private static final Color HOT_CORE = new Color(0xFF, 0xF0, 0xC0);

	/** Dot positions (lon, lat) per region, generated once and only ever extended, so dots never jump. */
	private final Map<WorldRegion, List<double[]>> dots = new EnumMap<>(WorldRegion.class);
	private final Map<WorldRegion, Random> rngs = new EnumMap<>(WorldRegion.class);

	private BufferedImage landCache;

	/** Where the dots went last paint (screen x, y, region ordinal), for hover. */
	private int[] hitX = new int[0];
	private int[] hitY = new int[0];
	private WorldRegion[] hitRegion = new WorldRegion[0];
	private int hitCount;

	private static Path2D loadLand()
	{
		Path2D p = new Path2D.Double(Path2D.WIND_NON_ZERO);
		try (InputStream in = WorldMap.class.getResourceAsStream("land.txt"))
		{
			if (in == null)
			{
				return p;
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] pts = line.split(" ");
				for (int i = 0; i < pts.length; i++)
				{
					String[] xy = pts[i].split(",");
					double lon = Double.parseDouble(xy[0]);
					double lat = Double.parseDouble(xy[1]);
					if (i == 0)
					{
						p.moveTo(lon, lat);
					}
					else
					{
						p.lineTo(lon, lat);
					}
				}
				p.closePath();
			}
		}
		catch (IOException | RuntimeException e)
		{
			// leave whatever loaded; the map still shows dots
		}
		return p;
	}

	private static AffineTransform toScreen(Rectangle r)
	{
		AffineTransform t = new AffineTransform();
		t.translate(r.x, r.y);
		t.scale(r.width / (LON_MAX - LON_MIN), -r.height / (LAT_MAX - LAT_MIN));
		t.translate(-LON_MIN, -LAT_MAX);
		return t;
	}

	/** Draws the map into {@code r}. {@code hot} (may be null) is drawn highlighted. */
	void paint(Graphics2D g, Rectangle r, Map<WorldRegion, RegionStat> stats, WorldRegion hot)
	{
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (landCache == null || landCache.getWidth() != r.width || landCache.getHeight() != r.height)
		{
			landCache = renderLand(r.width, r.height);
		}
		g.drawImage(landCache, r.x, r.y, null);

		AffineTransform t = toScreen(r);
		int total = 0;
		for (Map.Entry<WorldRegion, RegionStat> e : stats.entrySet())
		{
			total += dotsFor(e.getKey(), e.getValue().getPlayers());
		}
		if (hitX.length < total)
		{
			hitX = new int[total];
			hitY = new int[total];
			hitRegion = new WorldRegion[total];
		}
		hitCount = 0;
		double[] pt = new double[2];
		double size = Math.max(1.0, r.width / 230.0);
		for (Map.Entry<WorldRegion, RegionStat> e : stats.entrySet())
		{
			WorldRegion region = e.getKey();
			int n = dotsFor(region, e.getValue().getPlayers());
			List<double[]> list = positions(region, n);
			boolean isHot = region == hot;
			for (int i = 0; i < n; i++)
			{
				t.transform(list.get(i), 0, pt, 0, 1);
				double halo = (isHot ? 2.6 : 2.0) * size;
				g.setColor(isHot ? HOT_HALO : HALO);
				g.fill(new Ellipse2D.Double(pt[0] - halo, pt[1] - halo, 2 * halo, 2 * halo));
				double core = (isHot ? 0.95 : 0.7) * size;
				g.setColor(isHot ? HOT_CORE : CORE);
				g.fill(new Ellipse2D.Double(pt[0] - core, pt[1] - core, 2 * core, 2 * core));
				hitX[hitCount] = (int) Math.round(pt[0]);
				hitY[hitCount] = (int) Math.round(pt[1]);
				hitRegion[hitCount] = region;
				hitCount++;
			}
		}
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
	}

	/** Region whose nearest dot (from the last paint) is within a few pixels of (x, y), or null. */
	WorldRegion regionAt(int x, int y)
	{
		int best = -1;
		int bestD = 7 * 7;
		for (int i = 0; i < hitCount; i++)
		{
			int dx = hitX[i] - x;
			int dy = hitY[i] - y;
			int d = dx * dx + dy * dy;
			if (d <= bestD)
			{
				bestD = d;
				best = i;
			}
		}
		return best < 0 ? null : hitRegion[best];
	}

	/** Tooltip text for a region. */
	static String describe(WorldRegion r, Map<WorldRegion, RegionStat> stats)
	{
		RegionStat s = stats.get(r);
		if (s == null)
		{
			return regionName(r);
		}
		int total = 0;
		for (RegionStat v : stats.values())
		{
			total += v.getPlayers();
		}
		double pct = total == 0 ? 0 : 100.0 * s.getPlayers() / total;
		return String.format("%s: %,d players (%.1f%%), %d worlds", regionName(r), s.getPlayers(), pct, s.getWorlds());
	}

	private static int dotsFor(WorldRegion r, int players)
	{
		if (players <= 0 || !ANCHORS.containsKey(r))
		{
			return 0;
		}
		return (int) Math.max(3, Math.min(MAX_DOTS, Math.round(players / PLAYERS_PER_DOT)));
	}

	private List<double[]> positions(WorldRegion region, int n)
	{
		List<double[]> list = dots.computeIfAbsent(region, k -> new ArrayList<>());
		Random rng = rngs.computeIfAbsent(region, k -> new Random(0x5EED + k.ordinal() * 7919L));
		double[][] anchors = ANCHORS.get(region);
		double spread = SPREAD.getOrDefault(region, 2.0);
		double weightSum = 0;
		for (double[] a : anchors)
		{
			weightSum += a[2];
		}
		while (list.size() < n)
		{
			double pick = rng.nextDouble() * weightSum;
			double[] a = anchors[0];
			for (double[] c : anchors)
			{
				pick -= c[2];
				if (pick <= 0)
				{
					a = c;
					break;
				}
			}
			double[] p = null;
			for (int tries = 0; tries < 8 && p == null; tries++)
			{
				double lon = a[0] + rng.nextGaussian() * spread * 1.3;
				double lat = a[1] + rng.nextGaussian() * spread;
				if (LAND.contains(lon, lat))
				{
					p = new double[]{lon, lat};
				}
			}
			list.add(p != null ? p : new double[]{a[0], a[1]});
		}
		return list;
	}

	private static BufferedImage renderLand(int w, int h)
	{
		BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		java.awt.Shape land = toScreen(new Rectangle(0, 0, w, h)).createTransformedShape(LAND);
		g.setColor(LAND_FILL);
		g.fill(land);
		g.setColor(LAND_EDGE);
		g.setStroke(new BasicStroke(Math.max(0.6f, w / 400f)));
		g.draw(land);
		g.dispose();
		return img;
	}
}
