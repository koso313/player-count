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
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
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

		// Overlay: all three styles, and the steel one with more lines and the graph
		List<BufferedImage> styles = new ArrayList<>();
		for (PlayerCountConfig.FrameStyle style : PlayerCountConfig.FrameStyle.values())
		{
			styles.add(overlay(painter, style, basicLines(FramePainter.labelColor(style))));
		}
		ImageIO.write(onGrass(styles, 2), "png", new File(out, "overlay-styles.png"));

		List<LayoutableRenderableEntity> full = basicLines(FramePainter.OSRS_ORANGE);
		Color label = FramePainter.OSRS_ORANGE;
		full.add(4, new RichLineComponent("Ping", label).add("34 ms", PlayerCountOverlay.UP));
		full.add(new RichLineComponent("Wintertodt", label).add("W307 (812)", Color.WHITE));
		full.add(new RichLineComponent("Today peak", label).add("126.4k at 20:14", Color.WHITE));
		full.add(new RichLineComponent("Today low", label).add("88.1k at 07:52", Color.WHITE));
		full.add(new GraphComponent(fakeSeries(rnd), Instant.parse("2026-11-11T23:00:00Z"), Duration.ofHours(1)));
		BufferedImage steelFull = overlay(painter, PlayerCountConfig.FrameStyle.STEEL, full);
		ImageIO.write(onGrass(Arrays.asList(steelFull), 2), "png", new File(out, "overlay-steel.png"));

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

		ImageIO.write(Icons.globe(48), "png", new File(out, "icon.png"));
		System.out.println("Wrote screenshots to " + out.getAbsolutePath());
		System.exit(0);
	}

	private static List<LayoutableRenderableEntity> basicLines(Color label)
	{
		List<LayoutableRenderableEntity> l = new ArrayList<>();
		l.add(new RichLineComponent("World 302", label).add("1,799", Color.WHITE).add(" (-12)", PlayerCountOverlay.DOWN));
		l.add(new RichLineComponent("Total", label).add("115,214", Color.WHITE).add(" (+158)", PlayerCountOverlay.UP));
		l.add(new RichLineComponent("P2P / F2P", label).add("107.5k / 7.7k", Color.WHITE));
		l.add(new RichLineComponent("Nearby", label).add("14", Color.WHITE));
		l.add(new RichLineComponent("Quietest", label).add("W674 (121) SG", PlayerCountOverlay.UP));
		return l;
	}

	private static BufferedImage overlay(FramePainter painter, PlayerCountConfig.FrameStyle style, List<LayoutableRenderableEntity> lines)
	{
		BufferedImage img = new BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setFont(FontManager.getRunescapeFont());
		Rectangle insets = FramePainter.insets(style);
		boolean custom = style != PlayerCountConfig.FrameStyle.RUNELITE;

		PanelComponent panel = new PanelComponent();
		panel.setBorder(insets);
		panel.setBackgroundColor(custom ? null : ComponentConstants.STANDARD_BACKGROUND_COLOR);
		panel.getChildren().add(TitleComponent.builder().text("Player Count").color(FramePainter.titleColor(style)).build());
		if (custom)
		{
			panel.getChildren().add(new DividerComponent(painter, style, insets));
		}
		int content = g.getFontMetrics().stringWidth("Player Count");
		for (LayoutableRenderableEntity l : lines)
		{
			if (l instanceof RichLineComponent)
			{
				content = Math.max(content, ((RichLineComponent) l).neededWidth(g.getFontMetrics(), 10));
			}
			panel.getChildren().add(l);
		}
		panel.setPreferredSize(new Dimension(Math.max(FramePainter.minWidth(style), content + insets.x + insets.width + 2), 0));

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
