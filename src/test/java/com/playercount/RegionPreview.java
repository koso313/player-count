package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;

/** Renders the region-breakdown options side by side, for choosing between them. */
public class RegionPreview
{
	public static void main(String[] args) throws Exception
	{
		File spriteDir = new File(args[0]);
		File out = new File(args[1]);
		List<World> worlds;
		try (InputStream in = RegionPreview.class.getResourceAsStream("slr-sample.bin"))
		{
			worlds = SlrParser.parse(in.readAllBytes());
		}
		Map<WorldRegion, Integer> totals = WorldStats.regionTotals(worlds);
		FramePainter painter = new FramePainter();
		painter.loadSteel(id ->
		{
			try
			{
				return ImageIO.read(new File(spriteDir, id + "_0.png"));
			}
			catch (Exception e)
			{
				return null;
			}
		});

		StringBuilder top = new StringBuilder();
		int shown = 0;
		for (Map.Entry<WorldRegion, Integer> e : totals.entrySet())
		{
			if (shown++ < 3)
			{
				top.append(top.length() == 0 ? "" : "  ").append(WorldStats.regionCode(e.getKey())).append(' ').append(PlayerCountOverlay.compact(e.getValue()));
			}
		}
		String topLine = top + (totals.size() > 3 ? "  +" + (totals.size() - 3) : "");

		List<List<OverlayLayout.Item>> options = new ArrayList<>();
		options.add(withBase(OverlayLayout.Item.line("Regions").add(topLine, Color.WHITE)));
		options.add(withBase(OverlayLayout.Item.custom("regions-bar", () -> new RegionGraphics.Bar(totals), 130)));
		Map<WorldRegion, WorldMap.RegionStat> stats = WorldMap.stats(worlds);
		int total = WorldStats.totals(worlds).getTotal();
		WorldMap map = new WorldMap();
		options.add(withBase(OverlayLayout.Item.custom("regions-map", () -> new WorldMapComponent(map, stats, WorldRegion.UNITED_KINGDOM, total, 0), 200)));

		List<BufferedImage> vertical = new ArrayList<>();
		List<BufferedImage> horizontal = new ArrayList<>();
		for (List<OverlayLayout.Item> items : options)
		{
			vertical.add(render(painter, items, PlayerCountConfig.Layout.VERTICAL));
		}
		horizontal.add(render(painter, options.get(0), PlayerCountConfig.Layout.HORIZONTAL));
		horizontal.add(render(painter, options.get(1), PlayerCountConfig.Layout.HORIZONTAL));
		OverlayLayout.Item mapCell = OverlayLayout.Item.custom("regions-map", () -> new WorldMapComponent(map, stats, null, total, 44), 108);
		mapCell.componentHeight = 46;
		List<OverlayLayout.Item> mapStrip = withBase(mapCell);
		horizontal.add(render(painter, mapStrip, PlayerCountConfig.Layout.HORIZONTAL));

		ImageIO.write(sheet(vertical, true, 3), "png", new File(out, "regions-vertical.png"));
		ImageIO.write(sheet(horizontal, false, 3), "png", new File(out, "regions-horizontal.png"));
		System.out.println("Wrote region previews to " + out.getAbsolutePath());
		System.exit(0);
	}

	private static List<OverlayLayout.Item> withBase(OverlayLayout.Item regions)
	{
		Color label = FramePainter.OSRS_ORANGE;
		List<OverlayLayout.Item> l = new ArrayList<>();
		l.add(OverlayLayout.Item.line("World 302").add("1,799", Color.WHITE).add(" (-12)", PlayerCountOverlay.DOWN));
		l.add(OverlayLayout.Item.line("Total").add("115,214", Color.WHITE).add(" (+158)", PlayerCountOverlay.UP));
		l.add(regions);
		l.add(OverlayLayout.Item.line("Ping").add("34 ms", PlayerCountOverlay.UP));
		return l;
	}

	private static BufferedImage render(FramePainter painter, List<OverlayLayout.Item> items, PlayerCountConfig.Layout layout)
	{
		PlayerCountConfig.FrameStyle style = PlayerCountConfig.FrameStyle.STEEL;
		BufferedImage img = new BufferedImage(1000, 500, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setFont(FontManager.getRunescapeFont());
		Rectangle insets = FramePainter.insets(style, false);
		PanelComponent panel = new PanelComponent();
		int content = OverlayLayout.populate(panel, items, style, layout, true, painter, g, false);
		panel.setBorder(insets);
		panel.setBackgroundColor(null);
		panel.setPreferredSize(new Dimension(Math.max(OverlayLayout.minWidth(style, layout, false), Math.max(content, layout == PlayerCountConfig.Layout.VERTICAL ? 150 : 0) + insets.x + insets.width + 2), 0));
		Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
		probe.setFont(g.getFont());
		panel.render(probe);
		Dimension size = panel.render(probe);
		painter.paintBackground(g, style, size.width, size.height, 90);
		panel.render(g);
		painter.paintBorder(g, style, size.width, size.height, 90);
		g.dispose();
		return img.getSubimage(0, 0, size.width, size.height);
	}

	private static BufferedImage sheet(List<BufferedImage> panels, boolean sideBySide, int scale)
	{
		int w = 16;
		int h = 16;
		for (BufferedImage p : panels)
		{
			if (sideBySide)
			{
				w += p.getWidth() + 16;
				h = Math.max(h, p.getHeight() + 32);
			}
			else
			{
				w = Math.max(w, p.getWidth() + 32);
				h += p.getHeight() + 16;
			}
		}
		BufferedImage canvas = new BufferedImage(w * scale, h * scale, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		g.setColor(new Color(74, 90, 53));
		g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
		int x = 16;
		int y = 16;
		for (BufferedImage p : panels)
		{
			g.drawImage(p, x * scale, y * scale, p.getWidth() * scale, p.getHeight() * scale, null);
			if (sideBySide)
			{
				x += p.getWidth() + 16;
			}
			else
			{
				y += p.getHeight() + 16;
			}
		}
		g.dispose();
		return canvas;
	}
}
