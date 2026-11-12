package com.playercount;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import net.runelite.client.ui.FontManager;
import net.runelite.http.api.worlds.WorldRegion;

/** The glowing world map at the top of the sidebar; hover a cluster for that region's players. */
class WorldMapView extends JComponent
{
	private static final Color BG = new Color(0x14, 0x18, 0x1D);

	private final WorldMap map = new WorldMap();
	private Map<WorldRegion, WorldMap.RegionStat> stats = Collections.emptyMap();
	private int total;
	private WorldRegion hot;

	WorldMapView()
	{
		setToolTipText("");
		ToolTipManager.sharedInstance().registerComponent(this);
		addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				WorldRegion r = map.regionAt(e.getX(), e.getY());
				if (r != hot)
				{
					hot = r;
					repaint();
				}
			}
		});
		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hot = null;
				repaint();
			}
		});
	}

	void setData(Map<WorldRegion, WorldMap.RegionStat> stats, int total)
	{
		this.stats = stats;
		this.total = total;
		repaint();
	}

	@Override
	public String getToolTipText(MouseEvent e)
	{
		WorldRegion r = map.regionAt(e.getX(), e.getY());
		return r == null ? null : WorldMap.describe(r, stats);
	}

	@Override
	public Dimension getPreferredSize()
	{
		int w = getParent() == null || getParent().getWidth() <= 0 ? 209 : getParent().getWidth();
		return new Dimension(w, (int) Math.round(w * WorldMap.ASPECT) + 22);
	}

	@Override
	public Dimension getMaximumSize()
	{
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics;
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		int w = getWidth();
		int mapH = (int) Math.round(w * WorldMap.ASPECT);
		g.setColor(BG);
		g.fillRect(0, 0, w, getHeight());
		map.paint(g, new Rectangle(0, 2, w, mapH), stats, hot);
		g.setFont(FontManager.getRunescapeBoldFont());
		FontMetrics fm = g.getFontMetrics();
		String text = total > 0 ? String.format("%,d Players Online", total) : "Loading worlds...";
		int tx = (w - fm.stringWidth(text)) / 2;
		int ty = mapH + 4 + fm.getAscent();
		g.setColor(Color.BLACK);
		g.drawString(text, tx + 1, ty + 1);
		g.setColor(FramePainter.OSRS_ORANGE);
		g.drawString(text, tx, ty);
	}
}
