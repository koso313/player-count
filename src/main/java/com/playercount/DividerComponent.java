package com.playercount;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;

/** The line under the title. Reaches out through the panel padding so a steel divider meets the side edges. */
class DividerComponent implements LayoutableRenderableEntity
{
	private static final int PAD = 2;

	private final FramePainter painter;
	private final PlayerCountConfig.FrameStyle style;
	private final Rectangle insets;

	private final Rectangle bounds = new Rectangle();
	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 0);

	DividerComponent(FramePainter painter, PlayerCountConfig.FrameStyle style, Rectangle insets)
	{
		this.painter = painter;
		this.style = style;
		this.insets = insets;
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		int h = FramePainter.dividerHeight(style) + 2 * PAD;
		int x0 = preferredLocation.x - insets.x;
		int x1 = preferredLocation.x + preferredSize.width + insets.width;
		painter.paintDivider(g, style, x0, x1, preferredLocation.y + PAD);
		Dimension size = new Dimension(preferredSize.width, h);
		bounds.setLocation(preferredLocation);
		bounds.setSize(size);
		return size;
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
