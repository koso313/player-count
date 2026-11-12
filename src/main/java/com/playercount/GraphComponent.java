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
	private static final int DEFAULT_HEIGHT = 44;
	private static final Color GRID = new Color(255, 255, 255, 40);
	private static final Color LINE = new Color(0xFF, 0xB0, 0x40);
	private static final Color LABEL = new Color(0xAA, 0xAA, 0xAA);
	private static final Color FILL = new Color(0xFF, 0xB0, 0x40, 55);
	/** Each column also averages this many columns either side, to calm a jumpy line. */
	private static final int SMOOTH = 2;

	private final List<PopulationHistory.Point> points;
	private final Instant now;
	private final Duration window;
	private final int height;
	private final boolean labels;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);

	GraphComponent(List<PopulationHistory.Point> points, Instant now, Duration window)
	{
		this(points, now, window, DEFAULT_HEIGHT, true);
	}

	/** @param labels false for a compact graph with no range/time labels (used in the horizontal strip) */
	GraphComponent(List<PopulationHistory.Point> points, Instant now, Duration window, int height, boolean labels)
	{
		this.points = points;
		this.now = now;
		this.window = window;
		this.height = height;
		this.labels = labels;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		int x0 = preferredLocation.x;
		int y0 = preferredLocation.y + (labels ? 2 : 0);
		int w = preferredSize.width;
		FontMetrics fm = g.getFontMetrics();
		int labelH = labels ? fm.getHeight() : 0;
		int plotTop = y0 + labelH;
		int plotH = height - labelH - (labels ? 0 : 1);

		// Labelled graphs get a faint frame; the compact one just a baseline, so it doesn't look boxed in.
		g.setColor(GRID);
		if (labels)
		{
			g.drawRect(x0, plotTop, w - 1, plotH);
		}
		else
		{
			g.drawLine(x0, plotTop + plotH, x0 + w - 1, plotTop + plotH);
		}

		double[] columns = points.size() < 2 ? null : columns(w);
		if (columns == null)
		{
			if (labels)
			{
				label(g, "Graph: collecting data...", x0, y0 + labelH - 2);
			}
		}
		else
		{
			double min = Double.MAX_VALUE;
			double max = -Double.MAX_VALUE;
			for (double v : columns)
			{
				if (!Double.isNaN(v))
				{
					min = Math.min(min, v);
					max = Math.max(max, v);
				}
			}
			double range = Math.max(1, max - min);
			int top = plotTop + 1;
			int bottom = plotTop + plotH - 1;

			// One smoothed value per pixel column, joined into a line with a soft fill underneath.
			Path2D line = new Path2D.Float();
			Path2D area = new Path2D.Float();
			boolean started = false;
			double lastX = x0;
			for (int i = 0; i < columns.length; i++)
			{
				if (Double.isNaN(columns[i]))
				{
					continue;
				}
				double px = x0 + i + 0.5;
				double py = bottom - (columns[i] - min) / range * (bottom - top);
				if (!started)
				{
					line.moveTo(px, py);
					area.moveTo(px, bottom + 0.5);
					area.lineTo(px, py);
					started = true;
				}
				else
				{
					line.lineTo(px, py);
					area.lineTo(px, py);
				}
				lastX = px;
			}
			area.lineTo(lastX, bottom + 0.5);
			area.closePath();

			Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
			Stroke stroke = g.getStroke();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(FILL);
			g.fill(area);
			g.setStroke(new BasicStroke(labels ? 1.5f : 1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(LINE);
			g.draw(line);
			g.setStroke(stroke);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
			int lo = (int) Math.round(min);
			int hi = (int) Math.round(max);

			if (!labels)
			{
				return finish(w);
			}
			label(g, PlayerCountOverlay.compact(lo) + " - " + PlayerCountOverlay.compact(hi), x0, y0 + labelH - 2);
			String span = window.toHours() + "h";
			label(g, span, x0 + w - fm.stringWidth(span), y0 + labelH - 2);
		}

		return finish(w);
	}

	/**
	 * Averages the points into one value per pixel column (then a small moving average), so a narrow graph
	 * shows the trend instead of every wobble. Columns with no data are NaN. Null if under two columns have data.
	 */
	private double[] columns(int w)
	{
		double[] sum = new double[Math.max(1, w)];
		int[] count = new int[sum.length];
		long spanMs = Math.max(1, window.toMillis());
		Instant start = now.minus(window);
		for (PopulationHistory.Point p : points)
		{
			double fx = Duration.between(start, p.getTime()).toMillis() / (double) spanMs;
			int col = (int) Math.max(0, Math.min(sum.length - 1, Math.floor(fx * sum.length)));
			sum[col] += p.getValue();
			count[col]++;
		}
		double[] raw = new double[sum.length];
		int filled = 0;
		for (int i = 0; i < raw.length; i++)
		{
			raw[i] = count[i] == 0 ? Double.NaN : sum[i] / count[i];
			if (count[i] > 0)
			{
				filled++;
			}
		}
		if (filled < 2)
		{
			return null;
		}
		double[] out = new double[raw.length];
		for (int i = 0; i < raw.length; i++)
		{
			if (Double.isNaN(raw[i]))
			{
				out[i] = Double.NaN;
				continue;
			}
			double t = 0;
			int n = 0;
			for (int j = Math.max(0, i - SMOOTH); j <= Math.min(raw.length - 1, i + SMOOTH); j++)
			{
				if (!Double.isNaN(raw[j]))
				{
					t += raw[j];
					n++;
				}
			}
			out[i] = t / n;
		}
		return out;
	}

	private Dimension finish(int w)
	{
		Dimension size = new Dimension(w, labels ? height + 4 : height);
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
