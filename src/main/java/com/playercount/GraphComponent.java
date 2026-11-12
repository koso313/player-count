package com.playercount;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/** Small line graph of player counts over a time window. */
class GraphComponent implements LayoutableRenderableEntity
{
	private static final int HEIGHT = 44;
	private static final Color GRID = new Color(255, 255, 255, 40);
	private static final Color LINE = new Color(0xFF, 0xB0, 0x40);
	private static final Color LABEL = new Color(0xAA, 0xAA, 0xAA);

	private final List<PopulationHistory.Point> points;
	private final Instant now;
	private final Duration window;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);

	GraphComponent(List<PopulationHistory.Point> points, Instant now, Duration window)
	{
		this.points = points;
		this.now = now;
		this.window = window;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		int x0 = preferredLocation.x;
		int y0 = preferredLocation.y + 2;
		int w = preferredSize.width;
		FontMetrics fm = g.getFontMetrics();
		int labelH = fm.getHeight();
		int plotTop = y0 + labelH;
		int plotH = HEIGHT - labelH;

		g.setColor(GRID);
		g.drawRect(x0, plotTop, w - 1, plotH);

		if (points.size() < 2)
		{
			label(g, "Graph: collecting data...", x0, y0 + labelH - 2);
		}
		else
		{
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
			for (PopulationHistory.Point p : points)
			{
				min = Math.min(min, p.getValue());
				max = Math.max(max, p.getValue());
			}
			int range = Math.max(1, max - min);
			long spanMs = window.toMillis();
			Instant start = now.minus(window);

			Path2D path = new Path2D.Float();
			boolean first = true;
			for (PopulationHistory.Point p : points)
			{
				double fx = Duration.between(start, p.getTime()).toMillis() / (double) spanMs;
				double px = x0 + Math.max(0, Math.min(1, fx)) * (w - 1);
				double py = plotTop + plotH - 1 - (p.getValue() - min) / (double) range * (plotH - 2);
				if (first)
				{
					path.moveTo(px, py);
					first = false;
				}
				else
				{
					path.lineTo(px, py);
				}
			}

			Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			Stroke stroke = g.getStroke();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(new BasicStroke(1.5f));
			g.setColor(LINE);
			g.draw(path);
			g.setStroke(stroke);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);

			String hi = PlayerCountOverlay.compact(max);
			String lo = PlayerCountOverlay.compact(min);
			label(g, lo + " - " + hi, x0, y0 + labelH - 2);
			String span = window.toHours() + "h";
			label(g, span, x0 + w - fm.stringWidth(span), y0 + labelH - 2);
		}

		Dimension size = new Dimension(w, HEIGHT + 4);
		bounds.setLocation(preferredLocation);
		bounds.setSize(size);
		return size;
	}

	private static void label(Graphics2D g, String s, int x, int y)
	{
		TextComponent t = new TextComponent();
		t.setText(s);
		t.setColor(LABEL);
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
		this.preferredSize = dimension;
	}
}
