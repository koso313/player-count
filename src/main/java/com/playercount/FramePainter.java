package com.playercount;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.util.function.IntFunction;
import net.runelite.api.gameval.SpriteID;
import net.runelite.client.ui.overlay.components.ComponentConstants;

/**
 * Backgrounds and borders for the three frame styles. Kept free of client state
 * so the offline preview draws exactly what the overlay draws.
 */
final class FramePainter
{
	/** OSRS interface text orange. */
	static final Color OSRS_ORANGE = new Color(0xFF, 0x98, 0x1F);
	/** Brown backing used behind steel-bordered interfaces. */
	private static final Color STEEL_FILL = new Color(62, 53, 41);

	// RuneHUD's panel colours
	private static final Color DARK_FILL = new Color(20, 20, 24);
	private static final Color DARK_EDGE = new Color(8, 8, 10);
	static final Color DARK_TRIM = new Color(126, 108, 74);
	private static final Color DARK_TEXT = new Color(238, 234, 222);

	private static final int STEEL_EDGE = 6;

	/** The game's steel border pieces, trimmed of transparent padding. Null until loaded. */
	private BufferedImage topLeft, topRight, bottomLeft, bottomRight;
	private BufferedImage top, bottom, left, right, divider;

	/**
	 * Loads the steel sprites through the given lookup (sprite id to image, null if not ready).
	 * Returns true once everything is available.
	 */
	boolean loadSteel(IntFunction<BufferedImage> sprites)
	{
		if (topLeft != null)
		{
			return true;
		}
		BufferedImage tl = sprites.apply(SpriteID.Steelborder.TOP_LEFT);
		BufferedImage tr = sprites.apply(SpriteID.Steelborder.TOP_RIGHT);
		BufferedImage bl = sprites.apply(SpriteID.Steelborder.BOTTOM_LEFT);
		BufferedImage br = sprites.apply(SpriteID.Steelborder.BOTTOM_RIGHT);
		BufferedImage t = sprites.apply(SpriteID.Steelborder2.EDGE_TOP);
		BufferedImage r = sprites.apply(SpriteID.Steelborder2.EDGE_RIGHT);
		BufferedImage d = sprites.apply(SpriteID.SteelborderDivider._0);
		if (tl == null || tr == null || bl == null || br == null || t == null || r == null || d == null)
		{
			return false;
		}
		top = trim(t);
		right = trim(r);
		// The game only ships top and right edges; interfaces mirror them for the other two.
		bottom = flip(top, false);
		left = flip(right, true);
		divider = trim(d);
		topRight = trim(tr);
		bottomLeft = trim(bl);
		bottomRight = trim(br);
		topLeft = trim(tl); // last, since it marks the set as loaded
		return true;
	}

	/** Padding between the panel edge and its text, per style. x/y = left/top, width/height = right/bottom. */
	static Rectangle insets(PlayerCountConfig.FrameStyle style)
	{
		switch (style)
		{
			case STEEL:
				return new Rectangle(STEEL_EDGE + 5, STEEL_EDGE + 4, STEEL_EDGE + 5, STEEL_EDGE + 5);
			case DARK:
				return new Rectangle(8, 6, 8, 7);
			default:
				int b = ComponentConstants.STANDARD_BORDER;
				return new Rectangle(b, b, b, b);
		}
	}

	/** Smallest width that keeps the steel corners from overlapping. */
	static int minWidth(PlayerCountConfig.FrameStyle style)
	{
		return style == PlayerCountConfig.FrameStyle.STEEL ? 60 : ComponentConstants.STANDARD_WIDTH;
	}

	static Color labelColor(PlayerCountConfig.FrameStyle style)
	{
		switch (style)
		{
			case STEEL:
				return OSRS_ORANGE;
			case DARK:
				return DARK_TEXT;
			default:
				return Color.WHITE;
		}
	}

	static Color titleColor(PlayerCountConfig.FrameStyle style)
	{
		switch (style)
		{
			case STEEL:
				return OSRS_ORANGE;
			case DARK:
				return DARK_TRIM.brighter();
			default:
				return Color.WHITE;
		}
	}

	/** Fill behind the text. Drawn before the panel's children. */
	void paintBackground(Graphics2D g, PlayerCountConfig.FrameStyle style, int w, int h, int opacityPercent)
	{
		int alpha = Math.round(Math.max(0, Math.min(100, opacityPercent)) * 2.55f);
		if (style == PlayerCountConfig.FrameStyle.STEEL)
		{
			g.setColor(withAlpha(STEEL_FILL, alpha));
			g.fillRect(STEEL_EDGE / 2, STEEL_EDGE / 2, w - STEEL_EDGE, h - STEEL_EDGE);
		}
		else if (style == PlayerCountConfig.FrameStyle.DARK)
		{
			Shape shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 9, 9);
			g.setPaint(new GradientPaint(0, 0, withAlpha(DARK_FILL.brighter(), alpha), 0, h, withAlpha(DARK_FILL, alpha)));
			g.fill(shape);
		}
	}

	/** Border drawn on top, once the panel's final size is known. */
	void paintBorder(Graphics2D g, PlayerCountConfig.FrameStyle style, int w, int h, int opacityPercent)
	{
		if (style == PlayerCountConfig.FrameStyle.STEEL && topLeft != null)
		{
			paintSteel(g, w, h);
		}
		else if (style == PlayerCountConfig.FrameStyle.DARK)
		{
			int alpha = Math.round(Math.max(0, Math.min(100, opacityPercent)) * 2.55f);
			g.setStroke(new BasicStroke(1f));
			g.setColor(withAlpha(DARK_TRIM, Math.min(255, alpha + 40)));
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 9, 9));
			g.setColor(withAlpha(DARK_EDGE, alpha));
			g.draw(new RoundRectangle2D.Float(1.5f, 1.5f, w - 3, h - 3, 7, 7));
		}
	}

	private void paintSteel(Graphics2D g, int w, int h)
	{
		Shape clip = g.getClip();

		// Edges, tiled between the corners.
		tileH(g, top, topLeft.getWidth(), w - topRight.getWidth(), 0);
		tileH(g, bottom, bottomLeft.getWidth(), w - bottomRight.getWidth(), h - bottom.getHeight());
		tileV(g, left, topLeft.getHeight(), h - bottomLeft.getHeight(), 0);
		tileV(g, right, topRight.getHeight(), h - bottomRight.getHeight(), w - right.getWidth());

		// Corners, each kept to its own quarter so a small panel doesn't overlap them.
		int hw = w / 2;
		int hh = h / 2;
		corner(g, topLeft, 0, 0, new Rectangle(0, 0, hw, hh), clip);
		corner(g, topRight, w - topRight.getWidth(), 0, new Rectangle(hw, 0, w - hw, hh), clip);
		corner(g, bottomLeft, 0, h - bottomLeft.getHeight(), new Rectangle(0, hh, hw, h - hh), clip);
		corner(g, bottomRight, w - bottomRight.getWidth(), h - bottomRight.getHeight(), new Rectangle(hw, hh, w - hw, h - hh), clip);
		g.setClip(clip);
	}

	/** Divider under the title, spanning the full panel width so it meets the side edges. */
	void paintDivider(Graphics2D g, PlayerCountConfig.FrameStyle style, int x0, int x1, int y)
	{
		if (style == PlayerCountConfig.FrameStyle.STEEL && divider != null)
		{
			tileH(g, divider, x0, x1, y);
		}
		else if (style == PlayerCountConfig.FrameStyle.DARK)
		{
			g.setColor(withAlpha(DARK_TRIM, 160));
			g.drawLine(x0 + 6, y + 2, x1 - 7, y + 2);
		}
	}

	static int dividerHeight(PlayerCountConfig.FrameStyle style)
	{
		return style == PlayerCountConfig.FrameStyle.STEEL ? STEEL_EDGE : 5;
	}

	private static void corner(Graphics2D g, BufferedImage img, int x, int y, Rectangle quarter, Shape clip)
	{
		g.setClip(clip);
		g.clip(quarter);
		g.drawImage(img, x, y, null);
	}

	private static void tileH(Graphics2D g, BufferedImage img, int x0, int x1, int y)
	{
		Shape clip = g.getClip();
		g.clipRect(x0, y, Math.max(0, x1 - x0), img.getHeight());
		for (int x = x0; x < x1; x += img.getWidth())
		{
			g.drawImage(img, x, y, null);
		}
		g.setClip(clip);
	}

	private static void tileV(Graphics2D g, BufferedImage img, int y0, int y1, int x)
	{
		Shape clip = g.getClip();
		g.clipRect(x, y0, img.getWidth(), Math.max(0, y1 - y0));
		for (int y = y0; y < y1; y += img.getHeight())
		{
			g.drawImage(img, x, y, null);
		}
		g.setClip(clip);
	}

	/** Crops fully transparent rows and columns from the edges of a sprite. */
	static BufferedImage trim(BufferedImage img)
	{
		int w = img.getWidth();
		int h = img.getHeight();
		int minX = w, minY = h, maxX = -1, maxY = -1;
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				if ((img.getRGB(x, y) >>> 24) != 0)
				{
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0 || (minX == 0 && minY == 0 && maxX == w - 1 && maxY == h - 1))
		{
			return img;
		}
		return img.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	private static BufferedImage flip(BufferedImage img, boolean horizontal)
	{
		AffineTransform t = horizontal
			? new AffineTransform(-1, 0, 0, 1, img.getWidth(), 0)
			: new AffineTransform(1, 0, 0, -1, 0, img.getHeight());
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		new AffineTransformOp(t, AffineTransformOp.TYPE_NEAREST_NEIGHBOR).filter(toArgb(img), out);
		return out;
	}

	private static BufferedImage toArgb(BufferedImage img)
	{
		if (img.getType() == BufferedImage.TYPE_INT_ARGB)
		{
			return img;
		}
		BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();
		return out;
	}

	private static Color withAlpha(Color c, int alpha)
	{
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
	}
}
