package com.geprofit;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * The side panel: a scrolling list of flips, plus the states that are easy to
 * forget -- loading, empty, and error.
 *
 * <p>Everything here runs on the Swing thread. The plugin marshals every
 * callback onto it before calling in, so no method in this class needs to
 * think about threading.
 */
class GeProfitPanel extends PluginPanel
{
	private static final NumberFormat NUMBER = NumberFormat.getInstance(Locale.UK);

	private final JPanel list = new JPanel();
	private final JLabel status = new JLabel("Loading flips...", SwingConstants.CENTER);
	private final Runnable onRefresh;

	GeProfitPanel(Runnable onRefresh)
	{
		this.onRefresh = onRefresh;

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		add(header(), BorderLayout.NORTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(list,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		add(scroll, BorderLayout.CENTER);

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setBorder(BorderFactory.createEmptyBorder(8, 4, 4, 4));
		add(status, BorderLayout.SOUTH);
	}

	private JPanel header()
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		JLabel title = new JLabel("Best flips");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		header.add(title, BorderLayout.WEST);

		JButton refresh = new JButton("Refresh");
		refresh.setFont(FontManager.getRunescapeSmallFont());
		refresh.setFocusPainted(false);
		refresh.setToolTipText("Fetch prices again now");
		refresh.addActionListener(e -> onRefresh.run());
		header.add(refresh, BorderLayout.EAST);

		return header;
	}

	void setLoading(boolean loading)
	{
		if (loading)
		{
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			status.setText("Refreshing...");
		}
	}

	void setError(String message)
	{
		list.removeAll();
		status.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		status.setText("<html><body style='width:150px'>" + escape(message) + "</body></html>");
		revalidate();
		repaint();
	}

	void setFlips(List<FlipItem> flips)
	{
		list.removeAll();

		if (flips.isEmpty())
		{
			status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			status.setText("<html><body style='width:150px'>Nothing matches your filters. "
				+ "Try lowering the minimum margin.</body></html>");
			revalidate();
			repaint();
			return;
		}

		// The list is already ranked by the source; showing a slice keeps the
		// panel usable, since nobody scrolls a hundred rows in a side panel.
		int shown = Math.min(flips.size(), 25);
		for (int i = 0; i < shown; i++)
		{
			list.add(row(flips.get(i)));
		}

		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setText("Showing " + shown + " of " + flips.size());
		revalidate();
		repaint();
	}

	private JPanel row(FlipItem flip)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel name = new JLabel(flip.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.NORTH);

		JPanel numbers = new JPanel(new GridLayout(2, 2, 4, 0));
		numbers.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		numbers.add(small("Buy " + gp(flip.getBuyPrice()), ColorScheme.LIGHT_GRAY_COLOR));
		numbers.add(small("Sell " + gp(flip.getSellPrice()), ColorScheme.LIGHT_GRAY_COLOR));
		numbers.add(small("Margin " + gp(flip.getMargin()),
			flip.getMargin() > 0 ? ColorScheme.PROGRESS_COMPLETE_COLOR : ColorScheme.PROGRESS_ERROR_COLOR));
		// The number that actually matters: margin across a whole buy limit.
		numbers.add(small("Limit " + gp(flip.marginPerLimit()), ColorScheme.BRAND_ORANGE));
		row.add(numbers, BorderLayout.CENTER);

		row.setToolTipText(String.format(
			"<html>%s<br>Buy %s, sell %s<br>Tax %s, margin %s each<br>"
				+ "Buy limit %s per 4h &rarr; %s potential<br>Volume %s</html>",
			flip.getName(), exact(flip.getBuyPrice()), exact(flip.getSellPrice()),
			exact(flip.getTax()), exact(flip.getMargin()),
			exact(flip.getBuyLimit()), exact(flip.marginPerLimit()),
			exact(flip.getHourlyVolume())));

		return row;
	}

	private static JLabel small(String text, Color colour)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(colour);
		return label;
	}

	/** Coins in the k/m/b shorthand players read faster than raw digits. */
	private static String gp(long value)
	{
		long n = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (n >= 1_000_000_000L)
		{
			return sign + trim(n / 1_000_000_000.0) + "b";
		}
		if (n >= 1_000_000L)
		{
			return sign + trim(n / 1_000_000.0) + "m";
		}
		if (n >= 1_000L)
		{
			return sign + trim(n / 1_000.0) + "k";
		}
		return sign + n;
	}

	private static String trim(double v)
	{
		String s = String.format(Locale.UK, "%.1f", v);
		return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
	}

	private static String exact(long value)
	{
		return NUMBER.format(value);
	}

	/** The message can come from a server response, so it is never trusted as HTML. */
	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
