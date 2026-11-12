package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldResult;

/**
 * Renders README screenshots without the game client: the overlay in each frame style and the
 * sidebar, using a real world-list download and RuneLite's own look and feel. Ping and "usual"
 * figures are made up for the picture.
 * Usage: ./gradlew showcase -Psprites=dir/with/310_0.png... -Pout=docs
 */
public class Showcase
{
	public static void main(String[] args) throws Exception
	{
		File spriteDir = new File(args[0]);
		File out = new File(args[1]);
		out.mkdirs();

		List<World> worlds = sampleWorlds();
		Map<Integer, Integer> pings = new HashMap<>();
		Map<Integer, Integer> usual = new HashMap<>();
		Map<Integer, Integer> changes = new HashMap<>();
		Random rnd = new Random(7);
		for (World w : worlds)
		{
			pings.put(w.getId(), fakePing(w.getRegion(), rnd));
			usual.put(w.getId(), Math.max(0, (int) (w.getPlayers() * (0.7 + rnd.nextDouble() * 0.6))));
			changes.put(w.getId(), rnd.nextInt(41) - 20);
		}

		FramePainter painter = new FramePainter();
		painter.loadSteel(id -> read(new File(spriteDir, id + "_0.png")));

		// Overlay: all three styles, the steel one with more lines and the graph, and both layouts
		Instant graphEnd = Instant.parse("2026-11-11T23:00:00Z");
		List<PopulationHistory.Point> series = fakeSeries(rnd);
		List<BufferedImage> styles = new ArrayList<>();
		for (PlayerCountConfig.FrameStyle style : PlayerCountConfig.FrameStyle.values())
		{
			styles.add(overlay(painter, style, PlayerCountConfig.Layout.VERTICAL, basicItems()));
		}
		ImageIO.write(onGrass(styles, 2), "png", new File(out, "overlay-styles.png"));

		List<OverlayLayout.Item> full = basicItems();
		full.add(4, OverlayLayout.Item.line("Ping").add("34 ms", PlayerCountOverlay.UP));
		full.add(OverlayLayout.Item.line("Wintertodt").add("W307 (812)", Color.WHITE));
		full.add(OverlayLayout.Item.line("Today peak").add("126.4k at 20:14", Color.WHITE));
		full.add(OverlayLayout.Item.line("Today low").add("88.1k at 07:52", Color.WHITE));
		full.add(OverlayLayout.Item.graph(series, graphEnd, Duration.ofHours(1)));
		ImageIO.write(onGrass(Arrays.asList(overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.VERTICAL, full)), 2),
			"png", new File(out, "overlay-steel.png"));

		List<OverlayLayout.Item> strip = basicItems();
		strip.add(4, OverlayLayout.Item.line("Ping").add("34 ms", PlayerCountOverlay.UP));
		List<BufferedImage> horizontal = new ArrayList<>();
		for (PlayerCountConfig.FrameStyle style : PlayerCountConfig.FrameStyle.values())
		{
			horizontal.add(overlay(painter, style, PlayerCountConfig.Layout.HORIZONTAL, strip));
		}
		List<OverlayLayout.Item> stripGraph = new ArrayList<>(strip);
		stripGraph.add(OverlayLayout.Item.graph(series, graphEnd, Duration.ofHours(1)));
		horizontal.add(overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.HORIZONTAL, stripGraph));
		ImageIO.write(stacked(horizontal, 2), "png", new File(out, "overlay-horizontal.png"));
		ImageIO.write(onGrass(Arrays.asList(
			overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.VERTICAL, strip),
			overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.HORIZONTAL, strip)), 2),
			"png", new File(out, "overlay-layouts.png"));

		// Sizes: the old way (whole image shrunk to 75%) next to the compact layout used below 90% now
		List<BufferedImage> sizes = new ArrayList<>();
		sizes.add(shrunk(overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.VERTICAL, strip), 0.75));
		sizes.add(overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.VERTICAL, strip, true));
		sizes.add(overlay(painter, PlayerCountConfig.FrameStyle.DARK, PlayerCountConfig.Layout.VERTICAL, strip, true));
		sizes.add(overlay(painter, PlayerCountConfig.FrameStyle.RUNELITE, PlayerCountConfig.Layout.VERTICAL, strip, true));
		ImageIO.write(onGrass(sizes, 2), "png", new File(out, "overlay-compact-vertical.png"));
		List<BufferedImage> hsizes = new ArrayList<>();
		hsizes.add(shrunk(overlay(painter, PlayerCountConfig.FrameStyle.STEEL, PlayerCountConfig.Layout.HORIZONTAL, strip), 0.75));
		for (PlayerCountConfig.FrameStyle style : PlayerCountConfig.FrameStyle.values())
		{
			hsizes.add(overlay(painter, style, PlayerCountConfig.Layout.HORIZONTAL, strip, true));
		}
		ImageIO.write(stacked(hsizes, 2), "png", new File(out, "overlay-compact-horizontal.png"));

		// Sizes at 1:1 (exactly as drawn in game) and an integer 3x zoom: Full, Medium, Small
		List<OverlayLayout.Item> miniItems = new ArrayList<>();
		miniItems.add(OverlayLayout.Item.line("W302").add("1,799", Color.WHITE).add(" (-12)", PlayerCountOverlay.DOWN));
		miniItems.add(OverlayLayout.Item.line("OSRS").add("115.2k", Color.WHITE));
		miniItems.add(OverlayLayout.Item.line("Ping").add("34", PlayerCountOverlay.UP));
		miniItems.add(OverlayLayout.Item.line("Near").add("14", Color.WHITE));
		miniItems.add(OverlayLayout.Item.line("Quiet").add("W674", PlayerCountOverlay.UP));
		for (PlayerCountConfig.Layout layout : PlayerCountConfig.Layout.values())
		{
			List<BufferedImage> row = Arrays.asList(
				overlay(painter, PlayerCountConfig.FrameStyle.STEEL, layout, strip, false, false),
				overlay(painter, PlayerCountConfig.FrameStyle.STEEL, layout, strip, true, false),
				overlay(painter, PlayerCountConfig.FrameStyle.STEEL, layout, miniItems, true, true),
				overlay(painter, PlayerCountConfig.FrameStyle.DARK, layout, miniItems, true, true),
				overlay(painter, PlayerCountConfig.FrameStyle.RUNELITE, layout, miniItems, true, true));
			String name = layout.name().toLowerCase();
			BufferedImage one = layout == PlayerCountConfig.Layout.VERTICAL ? onGrass(row, 1) : stacked(row, 1);
			ImageIO.write(one, "png", new File(out, "sizes-" + name + "-1x.png"));
			ImageIO.write(layout == PlayerCountConfig.Layout.VERTICAL ? onGrass(row, 3) : stacked(row, 3), "png", new File(out, "sizes-" + name + "-3x.png"));
		}

		// Sidebar
		WorldResult result = new WorldResult();
		result.setWorlds(worlds);
		World current = result.findWorld(302);
		World suggestion = WorldStats.pick(worlds, WorldStats.Criteria.builder().currentWorld(302).members(true).totalLevel(2000).build());
		WorldListPanel.State state = WorldListPanel.State.builder()
			.worlds(worlds)
			.changes(changes)
			.pings(pings)
			.usual(usual)
			.favorites(new HashSet<>(Arrays.asList(302, 330, 420)))
			.currentWorld(current.getId())
			.total(WorldStats.totals(worlds).getTotal())
			.totalChange(-158)
			.suggestion(suggestion)
			.finderLabel("Quietest")
			.finderOff(false)
			.updated(Instant.now().minusSeconds(12))
			.build();
		ImageIO.write(sidebar(state), "png", new File(out, "sidebar.png"));

		ImageIO.write(Icons.pluginIcon(48), "png", new File(out, "icon.png"));
		System.out.println("Wrote screenshots to " + out.getAbsolutePath());
		System.exit(0);
	}

	private static List<OverlayLayout.Item> basicItems()
	{
		List<OverlayLayout.Item> l = new ArrayList<>();
		l.add(OverlayLayout.Item.line("World 302").add("1,799", Color.WHITE).add(" (-12)", PlayerCountOverlay.DOWN));
		l.add(OverlayLayout.Item.line("Total").add("115,214", Color.WHITE).add(" (+158)", PlayerCountOverlay.UP));
		l.add(OverlayLayout.Item.line("P2P / F2P").add("107.5k / 7.7k", Color.WHITE));
		l.add(OverlayLayout.Item.line("Nearby").add("14", Color.WHITE));
		l.add(OverlayLayout.Item.line("Quietest").add("W674 (121) SG", PlayerCountOverlay.UP));
		return l;
	}

	/** Same steps as the overlay: populate, measure, background, panel, border. */
	private static BufferedImage overlay(FramePainter painter, PlayerCountConfig.FrameStyle style, PlayerCountConfig.Layout layout,
		List<OverlayLayout.Item> items)
	{
		return overlay(painter, style, layout, items, false);
	}

	private static BufferedImage overlay(FramePainter painter, PlayerCountConfig.FrameStyle style, PlayerCountConfig.Layout layout,
		List<OverlayLayout.Item> items, boolean compact)
	{
		return overlay(painter, style, layout, items, compact, false);
	}

	private static BufferedImage overlay(FramePainter painter, PlayerCountConfig.FrameStyle style, PlayerCountConfig.Layout layout,
		List<OverlayLayout.Item> items, boolean compact, boolean mini)
	{
		compact = compact || mini;
		BufferedImage img = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setFont(compact ? FontManager.getRunescapeSmallFont() : FontManager.getRunescapeFont());
		Rectangle insets = FramePainter.insets(style, compact);
		boolean custom = style != PlayerCountConfig.FrameStyle.RUNELITE;

		PanelComponent panel = new PanelComponent();
		int content = OverlayLayout.populate(panel, items, style, layout, true, painter, g, compact, mini);
		panel.setBorder(insets);
		panel.setBackgroundColor(custom ? null : ComponentConstants.STANDARD_BACKGROUND_COLOR);
		panel.setPreferredSize(new Dimension(Math.max(OverlayLayout.minWidth(style, layout, compact), content + insets.x + insets.width + 2), 0));

		Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		probe.setFont(g.getFont());
		panel.render(probe);
		Dimension size = panel.render(probe);
		if (custom)
		{
			painter.paintBackground(g, style, size.width, size.height, 90);
		}
		panel.render(g);
		if (custom)
		{
			painter.paintBorder(g, style, size.width, size.height, 90);
		}
		g.dispose();
		return img.getSubimage(0, 0, size.width, size.height);
	}

	/** What scaling the finished image down looks like (the old behaviour), nearest-neighbour like the game. */
	private static BufferedImage shrunk(BufferedImage img, double f)
	{
		int w = (int) Math.round(img.getWidth() * f);
		int h = (int) Math.round(img.getHeight() * f);
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.drawImage(img, 0, 0, w, h, null);
		g.dispose();
		return out;
	}

	private static BufferedImage stacked(List<BufferedImage> panels, int scale)
	{
		int w = 0;
		int h = 16;
		for (BufferedImage p : panels)
		{
			w = Math.max(w, p.getWidth());
			h += p.getHeight() + 16;
		}
		BufferedImage canvas = new BufferedImage((w + 32) * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(74, 90, 53));
		g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		int y = 16;
		for (BufferedImage p : panels)
		{
			g.drawImage(p, 16 * scale, y * scale, p.getWidth() * scale, p.getHeight() * scale, null);
			y += p.getHeight() + 16;
		}
		g.dispose();
		return canvas;
	}

	private static BufferedImage onGrass(List<BufferedImage> panels, int scale)
	{
		int w = 16;
		int h = 0;
		for (BufferedImage p : panels)
		{
			w += p.getWidth() + 16;
			h = Math.max(h, p.getHeight());
		}
		BufferedImage canvas = new BufferedImage(w * scale, (h + 32) * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(74, 90, 53));
		g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		int x = 16;
		for (BufferedImage p : panels)
		{
			g.drawImage(p, x * scale, 16 * scale, p.getWidth() * scale, p.getHeight() * scale, null);
			x += p.getWidth() + 16;
		}
		g.dispose();
		return canvas;
	}

	private static BufferedImage sidebar(WorldListPanel.State state) throws Exception
	{
		AtomicReference<BufferedImage> img = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> RuneLiteLAF.setup());
		AtomicReference<WorldListPanel> panel = new AtomicReference<>();
		AtomicReference<JFrame> frame = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() ->
		{
			WorldListPanel p = new WorldListPanel(new WorldListPanel.Actions()
			{
				@Override
				public void hop(World world)
				{
				}

				@Override
				public void hopSuggested()
				{
				}

				@Override
				public void hopNext()
				{
				}

				@Override
				public void toggleFavorite(int world)
				{
				}

				@Override
				public void refresh()
				{
				}
			});
			JFrame f = new JFrame();
			f.add(p);
			f.pack();
			f.setSize(PluginPanel.PANEL_WIDTH + 16, 720);
			panel.set(p);
			frame.set(f);
			p.update(state);
		});
		SwingUtilities.invokeAndWait(() -> { }); // let update() run
		SwingUtilities.invokeAndWait(() ->
		{
			WorldListPanel p = panel.get();
			frame.get().validate();
			p.previewHover(4);
			BufferedImage i = new BufferedImage(p.getWidth(), p.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = i.createGraphics();
			p.paint(g);
			g.dispose();
			img.set(i);
			frame.get().dispose();
		});
		return img.get();
	}

	private static List<PopulationHistory.Point> fakeSeries(Random rnd)
	{
		List<PopulationHistory.Point> pts = new ArrayList<>();
		Instant end = Instant.parse("2026-11-11T23:00:00Z");
		double v = 112_000;
		for (int i = 120; i >= 0; i--)
		{
			v += rnd.nextGaussian() * 250 + 25;
			pts.add(new PopulationHistory.Point(end.minusSeconds(i * 30L), (int) v));
		}
		return pts;
	}

	private static int fakePing(WorldRegion r, Random rnd)
	{
		int base;
		switch (r == null ? WorldRegion.UNITED_STATES_OF_AMERICA : r)
		{
			case UNITED_KINGDOM:
				base = 95;
				break;
			case GERMANY:
				base = 105;
				break;
			case AUSTRALIA:
				base = 230;
				break;
			default:
				base = 35;
		}
		return base + rnd.nextInt(25);
	}

	private static List<World> sampleWorlds() throws IOException
	{
		try (InputStream in = Showcase.class.getResourceAsStream("slr-sample.bin"))
		{
			return SlrParser.parse(in.readAllBytes());
		}
	}

	private static BufferedImage read(File f)
	{
		try
		{
			return ImageIO.read(f);
		}
		catch (IOException e)
		{
			return null;
		}
	}
}
