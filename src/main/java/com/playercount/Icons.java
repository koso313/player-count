package com.playercount;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/** A small globe, drawn in code (large, then scaled down so the edges stay smooth). */
final class Icons
{
	private Icons()
	{
	}

	static BufferedImage globe(int size)
	{
		int big = size * 8;
		BufferedImage img = new BufferedImage(big, big, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

		float pad = big * 0.06f;
		float d = big - 2 * pad;
		Shape sphere = new Ellipse2D.Float(pad, pad, d, d);

		// Ocean, lit from the top left
		g.setPaint(new RadialGradientPaint(pad + d * 0.35f, pad + d * 0.3f, d * 0.75f,
			new float[]{0f, 1f}, new Color[]{new Color(0x6F, 0xB8, 0xF0), new Color(0x1B, 0x4C, 0x8C)}));
		g.fill(sphere);

		// Land, clipped to the sphere
		g.setClip(sphere);
		g.setPaint(new RadialGradientPaint(pad + d * 0.35f, pad + d * 0.3f, d * 0.8f,
			new float[]{0f, 1f}, new Color[]{new Color(0x8C, 0xD0, 0x5C), new Color(0x3C, 0x7A, 0x2A)}));
		g.fill(blob(pad, d, new double[][]{{0.18, 0.22}, {0.42, 0.14}, {0.50, 0.30}, {0.40, 0.46}, {0.30, 0.62}, {0.20, 0.48}, {0.10, 0.36}}));
		g.fill(blob(pad, d, new double[][]{{0.60, 0.50}, {0.80, 0.44}, {0.92, 0.58}, {0.84, 0.78}, {0.66, 0.84}, {0.56, 0.68}}));
		g.fill(blob(pad, d, new double[][]{{0.64, 0.16}, {0.80, 0.18}, {0.84, 0.30}, {0.70, 0.32}}));

		// Grid lines
		g.setColor(new Color(255, 255, 255, 70));
		g.setStroke(new BasicStroke(big * 0.025f));
		g.draw(new Ellipse2D.Float(pad + d * 0.3f, pad, d * 0.4f, d));
		g.drawLine((int) (pad + d / 2), (int) pad, (int) (pad + d / 2), (int) (pad + d));
		g.drawLine((int) pad, (int) (pad + d / 2), (int) (pad + d), (int) (pad + d / 2));
		g.draw(new Ellipse2D.Float(pad, pad + d * 0.22f, d, d * 0.56f));
		g.setClip(null);

		// Rim
		g.setColor(new Color(0x0C, 0x24, 0x44));
		g.setStroke(new BasicStroke(big * 0.05f));
		g.draw(sphere);
		g.dispose();

		return scaleDown(img, size);
	}

	private static Shape blob(float pad, float d, double[][] pts)
	{
		Path2D p = new Path2D.Double();
		int n = pts.length;
		for (int i = 0; i < n; i++)
		{
			double[] a = pts[i];
			double[] b = pts[(i + 1) % n];
			double mx = (a[0] + b[0]) / 2;
			double my = (a[1] + b[1]) / 2;
			if (i == 0)
			{
				double[] last = pts[n - 1];
				p.moveTo(pad + d * (last[0] + a[0]) / 2, pad + d * (last[1] + a[1]) / 2);
			}
			p.quadTo(pad + d * a[0], pad + d * a[1], pad + d * mx, pad + d * my);
		}
		p.closePath();
		return p;
	}

	/** Halves repeatedly with bilinear filtering, which keeps small icons crisp without jaggies. */
	private static BufferedImage scaleDown(BufferedImage src, int size)
	{
		BufferedImage cur = src;
		while (cur.getWidth() / 2 >= size)
		{
			int w = cur.getWidth() / 2;
			BufferedImage next = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = next.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(cur, 0, 0, w, w, null);
			g.dispose();
			cur = next;
		}
		return cur;
	}
}
