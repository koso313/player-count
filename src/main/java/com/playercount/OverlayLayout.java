package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.time.Duration;
import java.time.Instant;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.http.api.worlds.World;

/**
 * Turns the overlay's lines into components, as a vertical list or a horizontal strip.
 * Shared by the overlay and the offline screenshot renderer so both draw the same thing.
 */
final class OverlayLayout
{
	/** Space between a line's label and its value (vertical). */
	static final int GAP = 10;
	private static final int COMPACT_GAP = 6;
	private static final int COMPACT_GRAPH_HEIGHT = 30;
	/** Width of the favourite star drawn after a label, including the space before it. */
	static final int STAR_W = 12;
	static final Color GOLD = new Color(0xFF, 0xC8, 0x3C);

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
		/** World to hop to when this line is clicked, or null. */
		World target;
		/** Set before layout when the mouse is over this line, to underline it. */
		boolean hovered;
		/** Draw a gold star after the label (your current world is a favourite). */
		boolean star;
		/** Glow red: this world is risky (PvP, Deadman, separate-account...). */
		boolean warn;
		/** Pulse the glow: you can die almost anywhere on this world (PvP, Deadman). */
		boolean pulse;
		/** Orange underline: risky, but not deadly everywhere. */
		boolean caution;
		/** A custom graphic (e.g. region map) instead of text; made fresh each frame. */
		java.util.function.Supplier<LayoutableRenderableEntity> component;
		/** Width of the custom graphic's cell in the horizontal strip (and its minimum width in the vertical list). */
		int componentWidth;
		/** Height the custom graphic wants in the horizontal strip (0 = the text height). */
		int componentHeight;
		/** Where the line was drawn last, in the overlay's unscaled coordinates (filled in while rendering). */
		final Rectangle hitBox = new Rectangle();

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

		/** A custom graphic: its own row in the vertical layout, a {@code stripWidth}-wide cell in the horizontal one. */
		static Item custom(String key, java.util.function.Supplier<LayoutableRenderableEntity> make, int stripWidth)
		{
			Item i = new Item(key, null, null, null);
			i.component = make;
			i.componentWidth = stripWidth;
			return i;
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

		Item target(World world)
		{
			this.target = world;
			return this;
		}

		Item star(boolean on)
		{
			this.star = on;
			return this;
		}

		Item warn(boolean on)
		{
			this.warn = on;
			return this;
		}

		Item pulse(boolean on)
		{
			this.pulse = on;
			return this;
		}

		Item caution(boolean on)
		{
			this.caution = on;
			return this;
		}
	}

	/**
	 * Fills the panel and returns the content width it needs (excluding the panel's insets).
	 * Compact uses tighter spacing; the caller sets the game's small font on {@code g} for it.
	 */
	static int populate(PanelComponent panel, List<Item> items, PlayerCountConfig.FrameStyle style,
		PlayerCountConfig.Layout layout, boolean title, FramePainter painter, Graphics2D g, boolean compact)
	{
		return populate(panel, items, style, layout, title, painter, g, compact, false, null);
	}

	/** Mini: one line per cell (label then value), for the smallest sizes. Implies compact spacing. */
	static int populate(PanelComponent panel, List<Item> items, PlayerCountConfig.FrameStyle style,
		PlayerCountConfig.Layout layout, boolean title, FramePainter painter, Graphics2D g, boolean compact, boolean mini)
	{
		return populate(panel, items, style, layout, title, painter, g, compact, mini, null);
	}

	/**
	 * @param widths remembers the widest each line/cell has been, so the overlay's edges don't move as numbers
	 *               tick over (null = measure fresh, for screenshots)
	 */
	static int populate(PanelComponent panel, List<Item> items, PlayerCountConfig.FrameStyle style,
		PlayerCountConfig.Layout layout, boolean title, FramePainter painter, Graphics2D g, boolean compact, boolean mini,
		WidthMemory widths)
	{
		compact = compact || mini;
		title = title && !mini;
		FontMetrics fm = g.getFontMetrics();
		Color label = FramePainter.labelColor(style);
		Rectangle insets = FramePainter.insets(style, compact);
		int gap = compact ? COMPACT_GAP : GAP;

		if (layout == PlayerCountConfig.Layout.HORIZONTAL)
		{
			Strip strip = new Strip(items, style, label, painter, insets, compact, mini, widths);
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
			if (item.component != null)
			{
				panel.getChildren().add(item.component.get());
				width = Math.max(width, item.componentWidth);
				continue;
			}
			if (item.isGraph())
			{
				panel.getChildren().add(compact
					? new GraphComponent(item.graph, item.now, item.window, COMPACT_GRAPH_HEIGHT, true)
					: new GraphComponent(item.graph, item.now, item.window));
				continue;
			}
			RichLineComponent c = new RichLineComponent(item.label, label).track(item.hitBox).underline(item.hovered || item.caution).star(item.star).glow(item.warn, item.pulse);
			for (RichLineComponent.Part p : item.parts)
			{
				c.add(p.getText(), p.getColor());
			}
			width = Math.max(width, c.neededWidth(fm, gap));
			panel.getChildren().add(c);
		}
		return widths == null ? width : widths.stable("vertical", width);
	}

	/** Every item side by side: label on top, value underneath, divided like the game's interfaces. */
	static final class Strip implements LayoutableRenderableEntity
	{

		private final List<Item> items;
		private final PlayerCountConfig.FrameStyle style;
		private final Color labelColor;
		private final FramePainter painter;
		private final Rectangle insets;
		private final int cellPad;
		private final int graphWidth;
		private final boolean oneLine;
		private final WidthMemory widths;

		private final Rectangle bounds = new Rectangle();
		private Point preferredLocation = new Point();

		Strip(List<Item> items, PlayerCountConfig.FrameStyle style, Color labelColor, FramePainter painter, Rectangle insets, boolean compact, boolean oneLine,
			WidthMemory widths)
		{
			this.widths = widths;
			this.oneLine = oneLine;
			this.cellPad = oneLine ? 3 : compact ? 4 : 7;
			this.graphWidth = oneLine ? 76 : compact ? 80 : 96;
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

		private int cellWidth(Item item, FontMetrics small, FontMetrics value)
		{
			if (item.component != null)
			{
				return item.componentWidth;
			}
			if (item.isGraph())
			{
				return graphWidth;
			}
			int v = 0;
			for (RichLineComponent.Part p : item.parts)
			{
				v += textWidth(value, p.getText());
			}
			int star = item.star ? STAR_W : 0;
			int w = oneLine
				? small.stringWidth(item.label) + star + (item.label.isEmpty() ? 0 : 4) + v + 2 * cellPad
				: Math.max(small.stringWidth(item.label) + star, v) + 2 * cellPad;
			return widths == null ? w : widths.stable("cell:" + item.label, w);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			FontMetrics small = g.getFontMetrics(FontManager.getRunescapeSmallFont());
			FontMetrics value = g.getFontMetrics();
			int textH = oneLine ? value.getHeight() : small.getHeight() + value.getHeight();
			int h = textH;
			for (Item it : items)
			{
				h = Math.max(h, it.componentHeight);
			}
			// Text sits vertically centred when a taller graphic sets the strip height
			int textY = (h - textH) / 2;
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
				if (item.warn)
				{
					glow(g, x + 1, y - 1, cw - 2, h + 2, item.pulse);
				}
				if (item.component != null)
				{
					LayoutableRenderableEntity c = item.component.get();
					c.setPreferredLocation(new Point(x + 3, y));
					c.setPreferredSize(new Dimension(cw - 6, h));
					c.render(g);
				}
				else if (item.isGraph())
				{
					GraphComponent graph = new GraphComponent(item.graph, item.now, item.window, h, false);
					graph.setPreferredLocation(new Point(x + 3, y));
					graph.setPreferredSize(new Dimension(cw - 6, h));
					graph.render(g);
				}
				else if (oneLine)
				{
					int lx = x + cellPad;
					int base = y + textY + value.getAscent();
					text(g, item.label, labelColor, lx, base);
					int vx = lx + value.stringWidth(item.label) + (item.label.isEmpty() ? 0 : 4);
					if (item.star)
					{
						star(g, vx - 1, base - value.getAscent() / 2 - 1, 4);
						vx += STAR_W;
					}
					item.hitBox.setBounds(x, y, cw, h);
					int vw = 0;
					for (RichLineComponent.Part p : item.parts)
					{
						text(g, p.getText(), p.getColor(), vx + vw, base);
						vw += value.stringWidth(p.getText());
					}
					if ((item.hovered || item.caution) && vw > 0)
					{
						g.setColor(item.parts.get(0).getColor());
						g.drawLine(vx, base + 2, vx + vw - 1, base + 2);
					}
				}
				else
				{
					java.awt.Font valueFont = g.getFont();
					g.setFont(FontManager.getRunescapeSmallFont());
					int ty = y + textY;
					int lw = small.stringWidth(item.label) + (item.star ? STAR_W : 0);
					int lx = x + (cw - lw) / 2;
					text(g, item.label, labelColor, lx, ty + small.getAscent());
					if (item.star)
					{
						star(g, lx + small.stringWidth(item.label) + 3, ty + small.getAscent() / 2 + 1, 4);
					}
					g.setFont(valueFont);
					int vw = 0;
					for (RichLineComponent.Part p : item.parts)
					{
						vw += value.stringWidth(p.getText());
					}
					int vx = x + (cw - vw) / 2;
					item.hitBox.setBounds(x, y, cw, h);
					if ((item.hovered || item.caution) && vw > 0)
					{
						int uy = ty + small.getHeight() + value.getAscent() + 2;
						g.setColor(item.parts.get(0).getColor());
						g.drawLine(vx, uy, vx + vw - 1, uy);
					}
					for (RichLineComponent.Part p : item.parts)
					{
						text(g, p.getText(), p.getColor(), vx, ty + small.getHeight() + value.getAscent());
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

	/**
	 * Text width with every digit measured as the widest digit, so a number's width only changes when it
	 * gains or loses a digit, not every time a digit changes.
	 */
	static int textWidth(FontMetrics fm, String s)
	{
		int widest = 0;
		for (char d = '0'; d <= '9'; d++)
		{
			widest = Math.max(widest, fm.charWidth(d));
		}
		int w = 0;
		for (int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			w += Character.isDigit(c) ? widest : fm.charWidth(c);
		}
		return w;
	}

	static final Color RISK = new Color(0xFF, 0x40, 0x30);
	/** Risky but not deadly everywhere (Wilderness-only danger, separate-account worlds). */
	static final Color CAUTION = new Color(0xFF, 0x8C, 0x1A);

	/** A red glow around a box to flag a risky world; it pulses only for worlds where you can die anywhere. */
	static void glow(Graphics2D g, int x, int y, int w, int h, boolean pulsing)
	{
		double pulse = pulsing ? 0.55 + 0.45 * Math.sin(System.currentTimeMillis() / 220.0) : 0.8;
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(255, 40, 30, (int) (40 * pulse)));
		g.fillRoundRect(x, y, w, h, 6, 6);
		for (int i = 0; i < 3; i++)
		{
			g.setColor(new Color(255, 60, 40, (int) ((170 - i * 55) * pulse)));
			g.drawRoundRect(x - i, y - i, w - 1 + 2 * i, h - 1 + 2 * i, 6 + 2 * i, 6 + 2 * i);
		}
		if (aa != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		}
	}

	/** A small gold five-pointed star with a dark outline, centred on (x + r, cy). */
	static void star(Graphics2D g, int x, int cy, int r)
	{
		double cx = x + r;
		Polygon p = new Polygon();
		for (int i = 0; i < 10; i++)
		{
			double rad = i % 2 == 0 ? r : r * 0.45;
			double a = Math.PI / 2 + i * Math.PI / 5;
			p.addPoint((int) Math.round(cx + rad * Math.cos(a)), (int) Math.round(cy - rad * Math.sin(a)));
		}
		Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0x40, 0x2A, 0x00));
		p.translate(1, 1);
		g.fillPolygon(p);
		p.translate(-1, -1);
		g.setColor(GOLD);
		g.fillPolygon(p);
		if (aa != null)
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
		}
	}

	/** Remembers the widest each line/cell has needed; forgets when what's shown changes. */
	static final class WidthMemory
	{
		private final Map<String, Integer> widest = new HashMap<>();
		private String signature = "";

		/** Call once per frame with a key describing what's shown (lines, style, layout, size). */
		void begin(String signature)
		{
			if (!signature.equals(this.signature))
			{
				widest.clear();
				this.signature = signature;
			}
		}

		int stable(String key, int measured)
		{
			int w = Math.max(widest.getOrDefault(key, 0), measured);
			widest.put(key, w);
			return w;
		}
	}

	/** Smallest panel width for the style and layout. */
	static int minWidth(PlayerCountConfig.FrameStyle style, PlayerCountConfig.Layout layout, boolean compact)
	{
		return layout == PlayerCountConfig.Layout.HORIZONTAL || compact ? 60 : FramePainter.minWidth(style);
	}
}
