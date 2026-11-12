package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * Like LineComponent, but the right side can mix colours,
 * e.g. a white count followed by a green "(+12)".
 */
class RichLineComponent implements LayoutableRenderableEntity
{
	@Value
	static class Part
	{
		String text;
		Color color;
	}

	private final String left;
	private final Color leftColor;
	private final List<Part> right = new ArrayList<>();
	private Rectangle tracked;
	private boolean underline;
	private boolean star;
	private boolean glow;
	private boolean pulse;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);

	RichLineComponent(String left, Color leftColor)
	{
		this.left = left;
		this.leftColor = leftColor;
	}

	RichLineComponent add(String text, Color color)
	{
		if (text != null && !text.isEmpty())
		{
			right.add(new Part(text, color));
		}
		return this;
	}

	/** Copies where this line is drawn into the given rectangle on every render (for click targets). */
	RichLineComponent track(Rectangle into)
	{
		this.tracked = into;
		return this;
	}

	/** Underlines the value, to show it can be clicked. */
	RichLineComponent underline(boolean on)
	{
		this.underline = on;
		return this;
	}

	/** Red glow around the line (risky world), pulsing if you can die anywhere there. */
	RichLineComponent glow(boolean on, boolean pulsing)
	{
		this.glow = on;
		this.pulse = pulsing;
		return this;
	}

	/** Gold star after the label. */
	RichLineComponent star(boolean on)
	{
		this.star = on;
		return this;
	}

	/** Width the line needs with the given gap between label and value (digits counted at their widest). */
	int neededWidth(FontMetrics fm, int gap)
	{
		int w = fm.stringWidth(left) + (star ? OverlayLayout.STAR_W : 0);
		int r = stableRightWidth(fm);
		return r == 0 ? w : w + gap + r;
	}

	private int rightWidth(FontMetrics fm)
	{
		int w = 0;
		for (Part p : right)
		{
			w += fm.stringWidth(p.getText());
		}
		return w;
	}

	/** Right side measured with digits at their widest, for sizing (drawing uses the real width). */
	private int stableRightWidth(FontMetrics fm)
	{
		int w = 0;
		for (Part p : right)
		{
			w += OverlayLayout.textWidth(fm, p.getText());
		}
		return w;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		FontMetrics fm = graphics.getFontMetrics();
		int x = preferredLocation.x;
		int baseline = preferredLocation.y + fm.getHeight();

		if (glow)
		{
			OverlayLayout.glow(graphics, x - 2, preferredLocation.y, preferredSize.width + 4, fm.getHeight() + 2, pulse);
		}
		text(graphics, left, leftColor, x, baseline);
		if (star)
		{
			OverlayLayout.star(graphics, x + fm.stringWidth(left) + 3, baseline - fm.getAscent() / 2 + 1, 4);
		}

		int rx = x + preferredSize.width - rightWidth(fm);
		if (underline && !right.isEmpty())
		{
			g2(graphics, right.get(0).getColor(), rx, baseline + 2, rightWidth(fm));
		}
		for (Part p : right)
		{
			text(graphics, p.getText(), p.getColor(), rx, baseline);
			rx += fm.stringWidth(p.getText());
		}

		Dimension size = new Dimension(preferredSize.width, fm.getHeight());
		bounds.setLocation(preferredLocation);
		bounds.setSize(size);
		if (tracked != null)
		{
			tracked.setBounds(bounds);
		}
		return size;
	}

	private static void g2(Graphics2D g, Color c, int x, int y, int w)
	{
		g.setColor(c);
		g.drawLine(x, y, x + w - 1, y);
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
		this.preferredSize = dimension;
	}
}
