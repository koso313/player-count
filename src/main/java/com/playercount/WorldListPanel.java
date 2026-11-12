package com.playercount;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.Builder;
import lombok.Value;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldRegion;
import net.runelite.http.api.worlds.WorldType;

/**
 * Sidebar: your world at a glance, quick hop buttons, and every world with live counts,
 * ping and how busy it usually is. Double-click (or Enter) hops; click the star to favourite;
 * right-click for more.
 */
class WorldListPanel extends PluginPanel
{
	interface Actions
	{
		void hop(World world);

		void hopSuggested();

		void hopNext();

		void toggleFavorite(int world);

		void refresh();
	}

	@Value
	@Builder
	static class State
	{
		List<World> worlds;
		Map<Integer, Integer> changes;
		Map<Integer, Integer> pings;
		Map<Integer, Integer> usual;
		Set<Integer> favorites;
		/** -1 when logged out. */
		int currentWorld;
		int total;
		Integer totalChange;
		World suggestion;
		String finderLabel;
		boolean finderOff;
		Instant updated;
	}

	private enum Sort
	{
		QUIETEST("Quietest"),
		BUSIEST("Busiest"),
		PING("Lowest ping"),
		USUAL("Usually quiet"),
		NUMBER("World number");

		private final String label;

		Sort(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	private static final Color GOLD = new Color(0xFF, 0xC8, 0x3C);
	private static final Color SPECIAL = new Color(0xE0, 0x70, 0x70);
	private static final Color SUBTLE = new Color(0x9A, 0x9A, 0x9A);
	private static final Color ROW_A = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color ROW_B = new Color(0x26, 0x26, 0x26);
	private static final Color ROW_HOVER = ColorScheme.DARK_GRAY_HOVER_COLOR;
	private static final Color ROW_SELECTED = new Color(0x3A, 0x3A, 0x3A);
	private static final int ROW_HEIGHT = 36;
	private static final int STAR_AREA = 22;
	private static final javax.swing.ImageIcon STAR_ICON = starIcon();
	/** The Hop button shown on the hovered row. */
	private static final int HOP_W = 38;
	private static final int HOP_H = 20;

	private final Actions actions;

	// Header
	private final JLabel worldTitle = label(FontManager.getRunescapeBoldFont(), Color.WHITE);
	private final JLabel worldPing = label(FontManager.getRunescapeFont(), SUBTLE);
	private final JLabel worldPlayers = label(FontManager.getRunescapeFont(), Color.WHITE);
	private final JLabel worldInfo = label(FontManager.getRunescapeSmallFont(), SUBTLE);
	private final JLabel totalLabel = label(FontManager.getRunescapeFont(), Color.WHITE);
	private final JLabel updatedLabel = label(FontManager.getRunescapeSmallFont(), SUBTLE);
	private final JButton hopButton = button("Hop to suggested");
	private final JButton nextButton = button("Next world");

	// Filters
	private final JTextField search = new JTextField();
	private final JComboBox<String> membership = new JComboBox<>(new String[]{"All worlds", "Members", "Free-to-play"});
	private final JComboBox<String> region = new JComboBox<>();
	private final JComboBox<Sort> sort = new JComboBox<>(Sort.values());
	private final JCheckBox favouritesOnly = checkbox("Favourites", false);
	private final JCheckBox hideSpecial = checkbox("Hide PvP", true);
	private final JLabel summary = label(FontManager.getRunescapeSmallFont(), SUBTLE);

	// List
	private final DefaultListModel<World> model = new DefaultListModel<>();
	private final JList<World> list = new JList<>(model);
	private int hoverIndex = -1;

	private State state;

	WorldListPanel(Actions actions)
	{
		super(false);
		this.actions = actions;
		setLayout(new BorderLayout(0, 6));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);
		top.add(headerCard());
		top.add(Box.createVerticalStrut(6));
		top.add(actionRow());
		top.add(Box.createVerticalStrut(8));
		top.add(filters());
		add(top, BorderLayout.NORTH);

		list.setCellRenderer(new RowRenderer());
		list.setFixedCellHeight(ROW_HEIGHT);
		list.setBackground(ROW_A);
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setToolTipText("");
		list.addMouseMotionListener(new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int i = indexAt(e);
				if (i != hoverIndex)
				{
					hoverIndex = i;
					list.repaint();
				}
				boolean clickable = i >= 0 && (e.getX() < STAR_AREA || overHop(e));
				list.setCursor(clickable ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
			}
		});
		list.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseExited(MouseEvent e)
			{
				hoverIndex = -1;
				list.repaint();
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				int i = indexAt(e);
				if (i < 0)
				{
					return;
				}
				list.setSelectedIndex(i);
				World w = model.get(i);
				if (SwingUtilities.isRightMouseButton(e))
				{
					popup(w).show(list, e.getX(), e.getY());
				}
				else if (e.getX() < STAR_AREA)
				{
					actions.toggleFavorite(w.getId());
				}
				else if (overHop(e) && !isCurrent(w))
				{
					actions.hop(w);
				}
				else if (e.getClickCount() == 2)
				{
					actions.hop(w);
				}
			}
		});
		list.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER && list.getSelectedValue() != null)
				{
					actions.hop(list.getSelectedValue());
				}
			}
		});

		JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getVerticalScrollBar().setUnitIncrement(ROW_HEIGHT / 2);
		add(scroll, BorderLayout.CENTER);
	}

	// ---- Layout pieces ----

	private JComponent headerCard()
	{
		JPanel card = new JPanel(new GridLayout(0, 1, 0, 1));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, ColorScheme.BRAND_ORANGE),
			BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		JPanel row1 = new JPanel(new BorderLayout());
		row1.setOpaque(false);
		row1.add(worldTitle, BorderLayout.WEST);
		worldPing.setHorizontalAlignment(SwingConstants.RIGHT);
		row1.add(worldPing, BorderLayout.EAST);
		card.add(row1);
		card.add(worldPlayers);
		card.add(worldInfo);

		JPanel row4 = new JPanel(new BorderLayout());
		row4.setOpaque(false);
		row4.add(totalLabel, BorderLayout.WEST);
		JButton refresh = button("Refresh");
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.setMargin(new Insets(0, 4, 0, 4));
		refresh.setToolTipText("Download player counts now");
		refresh.addActionListener(e -> actions.refresh());
		row4.add(refresh, BorderLayout.EAST);
		card.add(row4);
		card.add(updatedLabel);

		worldTitle.setText("Not logged in");
		return card;
	}

	private JComponent actionRow()
	{
		JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
		row.setOpaque(false);
		hopButton.addActionListener(e -> actions.hopSuggested());
		nextButton.addActionListener(e -> actions.hopNext());
		nextButton.setToolTipText("Each click hops to the next world on the finder's list");
		row.add(hopButton);
		row.add(nextButton);
		return row;
	}

	private JComponent filters()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 4));
		p.setOpaque(false);

		search.setToolTipText("Search by world number or activity (e.g. 302, Wintertodt)");
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setForeground(Color.WHITE);
		search.setCaretColor(Color.WHITE);
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 5, 3, 5)));
		JPanel searchRow = new JPanel(new BorderLayout(6, 0));
		searchRow.setOpaque(false);
		searchRow.add(label(FontManager.getRunescapeSmallFont(), SUBTLE, "Search"), BorderLayout.WEST);
		searchRow.add(search, BorderLayout.CENTER);
		p.add(searchRow);

		region.addItem("All regions");
		for (WorldRegion r : WorldRegion.values())
		{
			region.addItem(WorldStats.regionCode(r));
		}
		JPanel combos = new JPanel(new GridLayout(1, 2, 6, 0));
		combos.setOpaque(false);
		combos.add(membership);
		combos.add(region);
		p.add(combos);

		JPanel sortRow = new JPanel(new BorderLayout(6, 0));
		sortRow.setOpaque(false);
		sortRow.add(label(FontManager.getRunescapeSmallFont(), SUBTLE, "Sort"), BorderLayout.WEST);
		sortRow.add(sort, BorderLayout.CENTER);
		p.add(sortRow);

		JPanel checks = new JPanel(new GridLayout(1, 2, 6, 0));
		checks.setOpaque(false);
		checks.add(favouritesOnly);
		checks.add(hideSpecial);
		p.add(checks);
		p.add(summary);

		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				rebuild();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				rebuild();
			}
		});
		membership.addActionListener(e -> rebuild());
		region.addActionListener(e -> rebuild());
		sort.addActionListener(e -> rebuild());
		favouritesOnly.setToolTipText("Only show starred worlds");
		hideSpecial.setToolTipText("Hide PvP, Deadman, seasonal and other special worlds");
		favouritesOnly.addActionListener(e -> rebuild());
		hideSpecial.addActionListener(e -> rebuild());
		return p;
	}

	private JPopupMenu popup(World w)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem hop = new JMenuItem("Hop to world " + w.getId());
		hop.addActionListener(e -> actions.hop(w));
		menu.add(hop);
		boolean fav = state != null && state.getFavorites().contains(w.getId());
		JMenuItem star = new JMenuItem(fav ? "Remove from favourites" : "Add to favourites");
		star.addActionListener(e -> actions.toggleFavorite(w.getId()));
		menu.add(star);
		return menu;
	}

	// ---- Data ----

	/** Safe to call from any thread. */
	void update(State next)
	{
		SwingUtilities.invokeLater(() ->
		{
			state = next;
			refreshHeader();
			rebuild();
		});
	}

	private void refreshHeader()
	{
		State s = state;
		World current = s.getCurrentWorld() > 0 ? find(s.getCurrentWorld()) : null;
		if (current == null)
		{
			worldTitle.setText(s.getCurrentWorld() > 0 ? "World " + s.getCurrentWorld() : "Not logged in");
			worldTitle.setIcon(null);
			worldPlayers.setText(" ");
			worldInfo.setText(" ");
			worldPing.setText("");
		}
		else
		{
			worldTitle.setText("World " + current.getId());
			boolean fav = s.getFavorites().contains(current.getId());
			worldTitle.setIcon(fav ? STAR_ICON : null);
			worldTitle.setToolTipText(fav ? "One of your favourite worlds" : null);
			worldPlayers.setText(html(String.format("%,d players", current.getPlayers()), s.getChanges().get(current.getId())));
			worldInfo.setText(activity(current) + "  |  " + WorldStats.regionCode(current.getRegion()) + usualText(current.getId(), "  |  usually "));
			int ping = s.getPings().getOrDefault(current.getId(), -1);
			worldPing.setText(ping < 0 ? "" : ping + " ms");
			worldPing.setForeground(PlayerCountOverlay.pingColor(ping));
		}
		totalLabel.setText(s.getTotal() < 0 ? "OSRS total ..." : html(String.format("OSRS %,d", s.getTotal()), s.getTotalChange()));
		if (s.getUpdated() != null)
		{
			long secs = Duration.between(s.getUpdated(), Instant.now()).getSeconds();
			updatedLabel.setText("Updated " + (secs < 60 ? secs + "s" : secs / 60 + "m") + " ago");
			updatedLabel.setToolTipText("+/- figures are the change over the last minute");
		}

		if (s.isFinderOff())
		{
			hopButton.setText("Finder off");
			hopButton.setEnabled(false);
			nextButton.setEnabled(false);
		}
		else
		{
			World sug = s.getSuggestion();
			hopButton.setText(sug == null ? "No match" : "Hop W" + sug.getId() + " (" + sug.getPlayers() + ")");
			hopButton.setToolTipText(s.getFinderLabel() + " world matching your finder settings");
			hopButton.setEnabled(sug != null);
			nextButton.setEnabled(true);
		}
	}

	private void rebuild()
	{
		if (state == null)
		{
			return;
		}
		State s = state;
		World selected = list.getSelectedValue();

		String text = search.getText().trim().toLowerCase(Locale.ROOT);
		int members = membership.getSelectedIndex();
		String regionCode = region.getSelectedIndex() <= 0 ? null : (String) region.getSelectedItem();
		List<World> rows = new ArrayList<>();
		int players = 0;
		for (World w : s.getWorlds())
		{
			if (w.getPlayers() < 0)
			{
				continue;
			}
			boolean isMembers = w.getTypes().contains(WorldType.MEMBERS);
			if ((members == 1 && !isMembers) || (members == 2 && isMembers))
			{
				continue;
			}
			if (regionCode != null && !regionCode.equals(WorldStats.regionCode(w.getRegion())))
			{
				continue;
			}
			if (hideSpecial.isSelected() && WorldStats.isSpecial(w))
			{
				continue;
			}
			if (favouritesOnly.isSelected() && !s.getFavorites().contains(w.getId()))
			{
				continue;
			}
			if (!text.isEmpty())
			{
				String act = w.getActivity() == null ? "" : w.getActivity().toLowerCase(Locale.ROOT);
				if (!act.contains(text) && !Integer.toString(w.getId()).startsWith(text))
				{
					continue;
				}
			}
			rows.add(w);
			players += w.getPlayers();
		}

		Comparator<World> order;
		switch ((Sort) sort.getSelectedItem())
		{
			case BUSIEST:
				order = Comparator.comparingInt(World::getPlayers).reversed();
				break;
			case PING:
				order = Comparator.comparingInt(w -> unknownLast(s.getPings().getOrDefault(w.getId(), -1)));
				break;
			case USUAL:
				order = Comparator.comparingInt(w -> unknownLast(s.getUsual().getOrDefault(w.getId(), -1)));
				break;
			case NUMBER:
				order = Comparator.comparingInt(World::getId);
				break;
			default:
				order = Comparator.comparingInt(World::getPlayers);
		}
		// Favourites always float to the top.
		rows.sort(Comparator.<World, Boolean>comparing(w -> !s.getFavorites().contains(w.getId()))
			.thenComparing(order)
			.thenComparingInt(World::getId));

		model.clear();
		for (World w : rows)
		{
			model.addElement(w);
		}
		if (selected != null)
		{
			for (int i = 0; i < model.size(); i++)
			{
				if (model.get(i).getId() == selected.getId())
				{
					list.setSelectedIndex(i);
					break;
				}
			}
		}
		summary.setText(String.format("%d worlds  |  %,d players", rows.size(), players));
		summary.setToolTipText("Double-click a world to hop, click its star to favourite, right-click for more");
	}

	private static int unknownLast(int v)
	{
		return v < 0 ? Integer.MAX_VALUE : v;
	}

	private World find(int id)
	{
		for (World w : state.getWorlds())
		{
			if (w.getId() == id)
			{
				return w;
			}
		}
		return null;
	}

	/** True when the mouse is over the Hop button of the row under it. */
	private boolean overHop(MouseEvent e)
	{
		int i = indexAt(e);
		if (i < 0)
		{
			return false;
		}
		java.awt.Rectangle cell = list.getCellBounds(i, i);
		return e.getX() >= cell.width - HOP_W - 6 && e.getX() <= cell.width - 6;
	}

	private boolean isCurrent(World w)
	{
		return state != null && w.getId() == state.getCurrentWorld();
	}

	private int indexAt(MouseEvent e)
	{
		int i = list.locationToIndex(e.getPoint());
		return i >= 0 && list.getCellBounds(i, i).contains(e.getPoint()) ? i : -1;
	}

	private String usualText(int world, String prefix)
	{
		int u = state.getUsual().getOrDefault(world, -1);
		return u < 0 ? "" : prefix + PlayerCountOverlay.compact(u);
	}

	private static String activity(World w)
	{
		String a = w.getActivity();
		return a == null || a.isEmpty() || "-".equals(a) ? "Normal world" : a;
	}

	private static String html(String value, Integer change)
	{
		if (change == null || change == 0)
		{
			return value;
		}
		String colour = change > 0 ? "#66dd66" : "#ee6666";
		return "<html>" + value + " <font color='" + colour + "'>(" + String.format("%+,d", change) + ")</font></html>";
	}

	// ---- Widgets ----

	private static JLabel label(Font font, Color colour)
	{
		return label(font, colour, " ");
	}

	private static JLabel label(Font font, Color colour, String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(colour);
		return l;
	}

	private static JButton button(String text)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		b.setForeground(Color.WHITE);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));
		b.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (b.isEnabled())
				{
					b.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				b.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}
		});
		return b;
	}

	private static JCheckBox checkbox(String text, boolean selected)
	{
		JCheckBox c = new JCheckBox(text, selected);
		c.setOpaque(false);
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setForeground(Color.LIGHT_GRAY);
		return c;
	}

	/** Paints one world as a two-line row. */
	private final class RowRenderer extends JComponent implements ListCellRenderer<World>
	{
		private World world;
		private boolean selected;
		private boolean hover;
		private boolean odd;

		@Override
		public Component getListCellRendererComponent(JList<? extends World> l, World value, int index, boolean isSelected, boolean focus)
		{
			world = value;
			selected = isSelected;
			hover = index == hoverIndex;
			odd = index % 2 == 1;
			setToolTipText(tooltip(value));
			return this;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(0, ROW_HEIGHT); // fit the viewport, never wider
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics;
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int w = getWidth();
			State s = state;
			boolean current = s != null && world.getId() == s.getCurrentWorld();
			boolean fav = s != null && s.getFavorites().contains(world.getId());

			g.setColor(selected ? ROW_SELECTED : hover ? ROW_HOVER : odd ? ROW_B : ROW_A);
			g.fillRect(0, 0, w, ROW_HEIGHT);
			if (current)
			{
				g.setColor(ColorScheme.BRAND_ORANGE);
				g.fillRect(0, 0, 3, ROW_HEIGHT);
			}

			star(g, 11, 12, fav);

			// Line 1: world number and region, players and change on the right
			g.setFont(FontManager.getRunescapeBoldFont());
			FontMetrics bold = g.getFontMetrics();
			g.setColor(current ? ColorScheme.BRAND_ORANGE : WorldStats.isSpecial(world) ? SPECIAL : Color.WHITE);
			String name = "W" + world.getId();
			g.drawString(name, STAR_AREA, 15);
			g.setFont(FontManager.getRunescapeSmallFont());
			g.setColor(SUBTLE);
			g.drawString(WorldStats.regionCode(world.getRegion()) + (world.getTypes().contains(WorldType.MEMBERS) ? "" : "  F2P"),
				STAR_AREA + bold.stringWidth(name) + 6, 15);

			Integer change = s == null ? null : s.getChanges().get(world.getId());
			String changeText = change == null || change == 0 ? "" : String.format(" %+d", change);
			g.setFont(FontManager.getRunescapeFont());
			FontMetrics regular = g.getFontMetrics();
			String count = String.format("%,d", world.getPlayers());
			int right = w - 8;
			if (!changeText.isEmpty())
			{
				g.setColor(change > 0 ? PlayerCountOverlay.UP : PlayerCountOverlay.DOWN);
				g.drawString(changeText, right - regular.stringWidth(changeText), 15);
				right -= regular.stringWidth(changeText);
			}
			g.setColor(world.getPlayers() >= WorldStats.NEARLY_FULL ? SPECIAL : Color.WHITE);
			g.drawString(count, right - regular.stringWidth(count), 15);

			// Line 2: activity on the left, usual count and ping on the right
			g.setFont(FontManager.getRunescapeSmallFont());
			FontMetrics small = g.getFontMetrics();
			int ping = s == null ? -1 : s.getPings().getOrDefault(world.getId(), -1);
			String pingText = ping < 0 ? "" : ping + "ms";
			String usual = s == null ? "" : usualText(world.getId(), "~");
			int rx = w - 8;
			if (!pingText.isEmpty())
			{
				g.setColor(PlayerCountOverlay.pingColor(ping));
				g.drawString(pingText, rx - small.stringWidth(pingText), 30);
				rx -= small.stringWidth(pingText) + 8;
			}
			if (!usual.isEmpty())
			{
				g.setColor(SUBTLE);
				g.drawString(usual, rx - small.stringWidth(usual), 30);
				rx -= small.stringWidth(usual) + 8;
			}
			g.setColor(SUBTLE);
			g.drawString(ellipsize(activity(world), small, rx - STAR_AREA), STAR_AREA, 30);

			if (hover && !current)
			{
				// Hop button over the right-hand figures while the row is hovered
				int bx = w - HOP_W - 6;
				int by = (ROW_HEIGHT - HOP_H) / 2;
				g.setColor(ROW_HOVER);
				g.fillRect(bx - 8, 0, HOP_W + 14, ROW_HEIGHT - 1);
				g.setColor(new Color(0x2E, 0x5E, 0x2A));
				g.fillRoundRect(bx, by, HOP_W, HOP_H, 6, 6);
				g.setColor(new Color(0x6F, 0xC0, 0x5A));
				g.drawRoundRect(bx, by, HOP_W - 1, HOP_H - 1, 6, 6);
				g.setFont(FontManager.getRunescapeBoldFont());
				FontMetrics hb = g.getFontMetrics();
				g.setColor(Color.WHITE);
				g.drawString("Hop", bx + (HOP_W - hb.stringWidth("Hop")) / 2, by + (HOP_H + hb.getAscent()) / 2 - 2);
			}

			g.setColor(ColorScheme.DARK_GRAY_COLOR);
			g.drawLine(0, ROW_HEIGHT - 1, w, ROW_HEIGHT - 1);
		}

		private void star(Graphics2D g, int cx, int cy, boolean filled)
		{
			Polygon p = new Polygon();
			for (int i = 0; i < 10; i++)
			{
				double r = i % 2 == 0 ? 6 : 2.6;
				double a = Math.PI / 2 + i * Math.PI / 5;
				p.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy - r * Math.sin(a)));
			}
			if (filled)
			{
				g.setColor(GOLD);
				g.fillPolygon(p);
			}
			else
			{
				g.setColor(new Color(0x60, 0x60, 0x60));
				g.setStroke(new BasicStroke(1f));
				g.drawPolygon(p);
			}
		}

		private String tooltip(World w)
		{
			State s = state;
			if (s == null)
			{
				return null;
			}
			StringBuilder t = new StringBuilder("<html><b>World ").append(w.getId()).append("</b> &nbsp;")
				.append(WorldStats.regionCode(w.getRegion())).append(" &nbsp;").append(activity(w)).append("<br>")
				.append(String.format("%,d players", w.getPlayers()));
			Integer change = s.getChanges().get(w.getId());
			if (change != null && change != 0)
			{
				t.append(String.format(" (%+d in the last minute)", change));
			}
			int usual = s.getUsual().getOrDefault(w.getId(), -1);
			if (usual >= 0)
			{
				t.append(String.format("<br>Usually about %,d at this hour", usual));
			}
			int ping = s.getPings().getOrDefault(w.getId(), -1);
			if (ping >= 0)
			{
				t.append("<br>Ping ").append(ping).append(" ms");
			}
			t.append("<br><i>Click Hop (or double-click) to hop, click the star to favourite, right-click for more</i></html>");
			return t.toString();
		}
	}

	private static String ellipsize(String s, FontMetrics fm, int width)
	{
		if (fm.stringWidth(s) <= width)
		{
			return s;
		}
		String dots = "...";
		int end = s.length();
		while (end > 0 && fm.stringWidth(s.substring(0, end) + dots) > width)
		{
			end--;
		}
		return end <= 0 ? "" : s.substring(0, end) + dots;
	}

	private static javax.swing.ImageIcon starIcon()
	{
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(14, 14, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		OverlayLayout.star(g, 1, 7, 6);
		g.dispose();
		return new javax.swing.ImageIcon(img);
	}

	/** For screenshots: show row {@code index} as hovered (with its Hop button). */
	void previewHover(int index)
	{
		hoverIndex = index;
		list.repaint();
	}

	/** For tests and screenshots: the rows currently shown. */
	List<World> visibleWorlds()
	{
		List<World> out = new ArrayList<>();
		for (int i = 0; i < model.size(); i++)
		{
			out.add(model.get(i));
		}
		return Collections.unmodifiableList(out);
	}
}
