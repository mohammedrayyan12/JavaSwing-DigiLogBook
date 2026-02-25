
import java.awt.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.util.List;

class TimeGroupPanel extends JPanel {
	public TimeGroupPanel(List<SessionGroup> grouped) {

		JPanel contentPanel = new JPanel();
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.PAGE_AXIS));
		// contentPanel.setBackground(new Color(255, 255, 255));

		if (grouped.size() == 0) {
			JLabel noRecords = new JLabel("No Records Found");
			noRecords.setAlignmentX(JLabel.CENTER_ALIGNMENT);
			contentPanel.add(Box.createVerticalGlue());
			contentPanel.add(noRecords);
			contentPanel.add(Box.createVerticalGlue());
		} else {
			// Loop through the groups and add each SingleGroupPanel to the content panel
			for (var entry : grouped) {
				// To prevent the SingleGroupPanel from stretching, wrap it in a container
				// that respects its maximum size.
				JPanel wrapperPanel = new JPanel();
				wrapperPanel.setLayout(new BorderLayout()); // Use BorderLayout for the wrapper
				wrapperPanel.add(new SingleGroupPanel(entry.slot, entry), BorderLayout.NORTH);
				wrapperPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrapperPanel.getPreferredSize().height));
				wrapperPanel.setOpaque(false);

				contentPanel.add(wrapperPanel);

				// Add fixed vertical space between each group panel
				contentPanel.add(Box.createVerticalStrut(10)); // 10 pixels of space
			}
			// Add vertical glue at the end to push the components to the top
			contentPanel.add(Box.createVerticalGlue());
		}

		// Wrap the contentPanel in a JScrollPane to handle scrolling
		JScrollPane scrollPane = new JScrollPane(contentPanel);
		scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scrollPane.setBorder(null); 
		// Add the JScrollPane to the TimeGroupPanel
		setLayout(new BorderLayout());
		add(scrollPane, BorderLayout.CENTER);
		logBookData.count = 1;
	}
}

class SingleGroupPanel extends JPanel {
	private JPanel tablePanel;

	public SingleGroupPanel(String slot, SessionGroup grp) {
		List<SessionRecord> records = grp.records;

		// Set a modern, clean look
		setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEmptyBorder(10, 10, 10, 10), // Padding around the panel
				BorderFactory.createLineBorder(new Color(200, 200, 200), 1) // A subtle border to distinguish each group
		));
		// setBackground(new Color(245, 245, 245)); // A light background for the group block

		// Use a GridBagLayout for precise control over component placement and spacing
		setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 5, 2, 5); // Add spacing between components
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST; // Align components to the left

		// Create a panel for the heading content to allow for two-line layout
		JPanel headingContentPanel = new JPanel(new GridLayout(3, 1));
		headingContentPanel.setOpaque(false); // Make the panel transparent

		// First line of the heading
		JLabel headingLine1 = new JLabel(
				"<html><p style='margin: 0; padding-bottom: 5;'><b>" + logBookData.count++ + ".</b> Date: <b>" + grp.date
						+ "</b> &emsp; Slot: <b>" + slot + "</b></p></html>");
		headingLine1.setFont(new Font("SansSerif", Font.PLAIN, 14));
		headingLine1.setForeground(new Color(30, 30, 30));
		headingContentPanel.add(headingLine1);

		// Second line of the heading
		StringBuilder attrText = new StringBuilder("<html><p style='margin: 0; padding-left: 19; padding-bottom: 5;'>");
		grp.attributes.forEach((key, value) -> {
			attrText.append(key).append(": <b>").append(value).append("</b> &emsp; ");
		});
		attrText.append("</p></html>");

		JLabel headingLine2 = new JLabel(attrText.toString());
		headingLine2.setFont(new Font("SansSerif", Font.PLAIN, 14));
		headingLine2.setForeground(new Color(60, 60, 60));
		headingContentPanel.add(headingLine2);

		JLabel headingLine3 = new JLabel(
				"<html><p style='margin: 0; padding-left: 19;'><b>" + "(" + records.size()
						+ " entries)</b></p></html>");
		headingLine3.setFont(new Font("SansSerif", Font.PLAIN, 14));
		headingLine3.setForeground(new Color(60, 60, 60));
		headingContentPanel.add(headingLine3);

		// The toggle button, styled to look more like a link
		JButton toggle = new JButton("Expand");
		toggle.setFont(new Font("SansSerif", Font.BOLD, 12));
		toggle.setForeground(new Color(0, 120, 215)); // A pleasant blue color
		toggle.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Change cursor to a hand on hover

		// Use an action listener to toggle visibility and change button text
		toggle.addActionListener(e -> {
			tablePanel.setVisible(!tablePanel.isVisible());
			toggle.setText(tablePanel.isVisible() ? "Collapse" : "Expand");
		});

		// Create a header panel using GridBagLayout
		JPanel headerPanel = new JPanel(new GridBagLayout());
		headerPanel.setOpaque(false);
		GridBagConstraints headerGBC = new GridBagConstraints();

		// Add the heading content to the header panel
		headerGBC.weightx = 1.0; // Give extra horizontal space to the heading
		headerGBC.fill = GridBagConstraints.HORIZONTAL;
		headerGBC.anchor = GridBagConstraints.WEST;
		headerGBC.insets = new Insets(5, 5, 5, 5); // Add spacing
		headerPanel.add(headingContentPanel, headerGBC);
		//
		headerGBC.weightx = 0.0; // Don't give extra horizontal space
		headerGBC.fill = GridBagConstraints.NONE;
		headerGBC.anchor = GridBagConstraints.EAST;
		headerGBC.insets = new Insets(5, 5, 5, 5);
		headerPanel.add(toggle, headerGBC);

		// Add the header panel to the main SingleGroupPanel
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.anchor = GridBagConstraints.NORTHWEST;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		add(headerPanel, gbc);

		// Create the table panel
		tablePanel = makeTable(records);
		tablePanel.setVisible(false); // Initially hidden

		// Add table panel below the header
		gbc.gridy = 1;
		gbc.weighty = 1.0; 
		gbc.anchor = GridBagConstraints.NORTH;
		gbc.fill = GridBagConstraints.BOTH;
		add(tablePanel, gbc);
	}

	private JPanel makeTable(List<SessionRecord> records) {

		JTable table = getTable(records);
		table.setAutoCreateRowSorter(true);

		JPanel p = new JPanel(new BorderLayout());
		p.add(new JScrollPane(table), BorderLayout.CENTER);
		return p;
	}

	public static JTable getTable(List<SessionRecord> records) {
		if (records.isEmpty()) return new JTable(new DefaultTableModel());

		// 1. Identify all unique attribute keys present in this record set
		// This ensures that if one record has "Lab Number" and others don't, the column still exists.
		java.util.Set<String> dynamicColumns = new java.util.LinkedHashSet<>();
		for (SessionRecord r : records) {
			dynamicColumns.addAll(r.attributes.keySet());
		}

		// 2. Build Column Headers
		java.util.List<String> columnNames = new java.util.ArrayList<>();
		columnNames.add("Login Time");
		columnNames.add("USN");
		columnNames.add("Name"); 
		
		// Add all dynamic categories
		columnNames.addAll(dynamicColumns);
		
		columnNames.add("Logout Time");
		columnNames.add("Session ID");

		DefaultTableModel model = new DefaultTableModel(columnNames.toArray(), 0);

		// 3. Fill Rows
		for (SessionRecord r : records) {
			java.util.List<Object> row = new java.util.ArrayList<>();
			row.add(r.loginTime);
			row.add(r.usn);
			row.add(r.name);

			// Add values for each dynamic column
			for (String col : dynamicColumns) {
				row.add(r.attributes.getOrDefault(col, "-"));
			}

			row.add(r.logoutTime);
			row.add(r.sessionId);
			model.addRow(row.toArray());
		}

		return new JTable(model);
	}
}