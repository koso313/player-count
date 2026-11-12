package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Turns the overlay's lines into components, as a vertical list or a horizontal strip.
 * Shared by the overlay and the offline screenshot renderer so both draw the same thing.
 */
final class OverlayLayout
{
	/** Space between a line's label and its value (vertical). */
	static final int GAP = 10;

	private OverlayLayout()
	{
	}

	/** One line of the overlay: a label and a value made of coloured parts, or a graph. */
	static final class Item
	{
		final String label;
		final List<RichLineComponent.Part> parts = new ArrayList<>();
		final List<PopulationHistory.Point> graph;
		final Instant now;
		final Duration window;

		private Item(String label, List<PopulationHistory.Point> graph, Instant now, Duration window)
		{
			this.label = label;
			this.graph = graph;
			this.now = now;
			this.window = window;
		}

		static Item line(String label)
		{
			return new Item(label, null, null, null);
		}

		static Item graph(List<PopulationHistory.Point> points, Instant now, Duration window)
		{
			return new Item("", points, now, window);
		}

		Item add(String text, Color color)
		{
			if (text != null && !text.isEmpty())
			{
				parts.add(new RichLineComponent.Part(text, color));
			}
			return this;
		}

		boolean isGraph()
		{
			return graph != null;
		}
	}

	/**
	 * Fills the panel and returns the content width it needs (excluding the panel's insets).
	 */
	static int populate(PanelComponent panel, List<Item> items, PlayerCountConfig.FrameStyle style,
		PlayerCountConfig.Layout layout, boolean title, FramePainter painter, Graphics2D g)
	{
		FontMetrics fm = g.getFontMetrics();
		Color label = FramePainter.labelColor(style);
		Rectangle insets = FramePainter.insets(style);

		if (layout == PlayerCountConfig.Layout.HORIZONTAL)
		{
			Strip strip = new Strip(items, style, label, painter, insets);
			panel.getChildren().add(strip);
			return strip.neededWidth(g);
		}

		int width = 0;
		if (title)
		{
			String text = "Player Count";
			width = fm.stringWidth(text);
			panel.getChildren().add(TitleComponent.builder().text(text).color(FramePainter.titleColor(style)).build());
			if (style != PlayerCountConfig.FrameStyle.RUNELITE)
			{
				panel.getChildren().add(new DividerComponent(painter, style, insets));
			}
		}
		for (Item item : items)
		{
			if (item.isGraph())
			{
				panel.getChildren().add(new GraphComponent(item.graph, item.now, item.window));
				continue;
			}
			RichLineComponent c = new RichLineComponent(item.label, label);
			for (RichLineComponent.Part p : item.parts)
			{
				c.add(p.getText(), p.getColor());
			}
			width = Math.max(width, c.neededWidth(fm, GAP));
			panel.getChildren().add(c);
		}
		return width;
	}

	/** Every item side by side: label on top, value underneath, divided like the game's interfaces. */
	static final class Strip implements LayoutableRenderableEntity
	{
		private static final int CELL_PAD = 7;
		private static final int GRAPH_WIDTH = 90;

		private final List<Item> items;
		private final PlayerCountConfig.FrameStyle style;
		private final Color labelColor;
		private final FramePainter painter;
		private final Rectangle insets;

		private final Rectangle bounds = new Rectangle();
		private Point preferredLocation = new Point();

		Strip(List<Item> items, PlayerCountConfig.FrameStyle style, Color labelColor, FramePainter painter, Rectangle insets)
		{
			this.items = items;
			this.style = style;
			this.labelColor = labelColor;
			this.painter = painter;
			this.insets = insets;
		}

		int neededWidth(Graphics2D g)
		{
			FontMetrics small = g.getFontMetrics(FontManager.getRunescapeSmallFont());
			FontMetrics value = g.getFontMetrics();
			int w = 0;
			for (int i = 0; i < items.size(); i++)
			{
				w += cellWidth(items.get(i), small, value) + (i > 0 ? divider() : 0);
			}
			return w;
		}

		private int divider()
		{
			return style == PlayerCountConfig.FrameStyle.STEEL ? FramePainter.dividerHeight(style) + 2 : 5;
		}

		private static int cellWidth(Item item, FontMetrics small, FontMetrics value)
		{
			if (item.isGraph())
			{
				return GRAPH_WIDTH;
			}
			int v = 0;
			for (RichLineComponent.Part p : item.parts)
			{
				v += value.stringWidth(p.getText());
			}
			return Math.max(small.stringWidth(item.label), v) + 2 * CELL_PAD;
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			FontMetrics small = g.getFontMetrics(FontManager.getRunescapeSmallFont());
			FontMetrics value = g.getFontMetrics();
			int h = small.getHeight() + value.getHeight();
			int x = preferredLocation.x;
			int y = preferredLocation.y;

			for (int i = 0; i < items.size(); i++)
			{
				Item item = items.get(i);
				if (i > 0)
				{
					int d = divider();
					// Run the divider through the padding so a steel one meets the top and bottom edges.
					painter.paintVerticalDivider(g, style, x + (d - FramePainter.dividerHeight(style)) / 2,
						y - insets.y, y + h + insets.height);
					x += d;
				}
				int cw = cellWidth(item, small, value);
				if (item.isGraph())
				{
					GraphComponent graph = new GraphComponent(item.graph, item.now, item.window, h, false);
					graph.setPreferredLocation(new Point(x + 3, y));
					graph.setPreferredSize(new Dimension(cw - 6, h));
					graph.render(g);
				}
				else
				{
					java.awt.Font valueFont = g.getFont();
					g.setFont(FontManager.getRunescapeSmallFont());
					text(g, item.label, labelColor, x + (cw - small.stringWidth(item.label)) / 2, y + small.getAscent());
					g.setFont(valueFont);
					int vw = 0;
					for (RichLineComponent.Part p : item.parts)
					{
						vw += value.stringWidth(p.getText());
					}
					int vx = x + (cw - vw) / 2;
					for (RichLineComponent.Part p : item.parts)
					{
						text(g, p.getText(), p.getColor(), vx, y + small.getHeight() + value.getAscent());
						vx += value.stringWidth(p.getText());
					}
				}
				x += cw;
			}

			Dimension size = new Dimension(x - preferredLocation.x, h);
			bounds.setLocation(preferredLocation);
			bounds.setSize(size);
			return size;
		}

		private static void text(Graphics2D g, String s, Color c, int x, int y)
		{
			TextComponent t = new TextComponent();
			t.setText(s);
			t.setColor(c);
			t.setPosition(new Point(x, y));
			t.render(g);
		}

		@Override
		public Rectangle getBounds()
		{
			return bounds;
		}

		@Override
		public void setPreferredLocation(Point position)
		{
			this.preferredLocation = position;
		}

		@Override
		public void setPreferredSize(Dimension dimension)
		{
			// Sizes itself from its content.
		}
	}

	/** Smallest panel width for the style and layout. */
	static int minWidth(PlayerCountConfig.FrameStyle style, PlayerCountConfig.Layout layout)
	{
		return layout == PlayerCountConfig.Layout.HORIZONTAL ? 60 : FramePainter.minWidth(style);
	}
}
