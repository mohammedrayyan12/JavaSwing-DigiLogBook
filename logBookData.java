import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

class DataPlace {
    private JFrame jf;
    private JPanel view;
    private JPanel mainContent;
    static JTextField dateFrom, dateTo;
	static JComboBox<String> startTime, endTime;
	static JCheckBox everything;
	private JCheckBox matchD, matchT;
	static JButton exportButton;
    private JPanel datePanel;
    private JComboBox<String> sub, department, batch, sem;
    private static String base = System.getenv("DATABASE");
    static boolean matchSlotConditon = false;

    private final String JDBC_URL_cloud = System.getenv("JDBC_URL_cloud");
    private final String JDBC_URL_local = System.getenv("JDBC_URL_local"); // for now sqlite
	private final String USERNAME = System.getenv("JDBC_USERNAME_local");
	private final String PASSWORD = System.getenv("JDBC_PASSWORD_local");

    DataPlace() {
		jf = new JFrame("Data Zone");
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setSize(900, 500);
		jf.setLocationRelativeTo(null);

        createInitialView();
		jf.setVisible(true);
	}

    void syncDatabases() {
        if (base.equals("temp")) return;

		try (Connection cloudConn = DriverManager.getConnection(JDBC_URL_cloud, USERNAME, PASSWORD);
				Connection localConn = DriverManager.getConnection(JDBC_URL_local)) {

			// Setup local database table if it doesn't exist
			try (Statement stmt = localConn.createStatement()) {
				stmt.executeUpdate("CREATE TABLE IF NOT EXISTS student_log (" +
						"session_id TEXT PRIMARY KEY, " +
						"login_time TEXT, " +
						"logout_time TEXT, " +
						"usn TEXT, " +
						"name TEXT, " +
						"sem TEXT, " +
						"dept TEXT, " +
						"sub TEXT, " +
						"batch TEXT" +
						");");
			}

			// Fetch all data from cloud
			PreparedStatement cloudStmt = cloudConn.prepareStatement("SELECT * FROM dummyData");
			ResultSet cloudRs = cloudStmt.executeQuery();

			// Insert into local database
			localConn.setAutoCommit(false);
			String insertSql = "INSERT or REPLACE INTO student_log(login_time, logout_time, usn, name, sem, dept, sub, batch, session_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
			PreparedStatement localStmt = localConn.prepareStatement(insertSql);
			while (cloudRs.next()) {
				localStmt.setString(1, cloudRs.getString("login_time"));
				localStmt.setString(2, cloudRs.getString("logout_time"));
				localStmt.setString(3, cloudRs.getString("usn"));
				localStmt.setString(4, cloudRs.getString("name"));
				localStmt.setString(5, cloudRs.getString("sem"));
				localStmt.setString(6, cloudRs.getString("dept"));
				localStmt.setString(7, cloudRs.getString("sub"));
				localStmt.setString(8, cloudRs.getString("batch"));
				localStmt.setString(9, cloudRs.getString("session_id"));
				localStmt.addBatch();
			}
			localStmt.executeBatch();
			localConn.commit();

			System.out.println("Sync complete.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
    public List<SessionGroup> getDatafromDataBase(String database, JPanel mainContent, String sSub, String sDept,
    String sSem, String sBatch) {

        // configure which database to connect
        Connection connection = null;
		if (database.equals("temp")) {
			connection = logBookData.manager.connection;
		} else {
			try {
				connection = DriverManager.getConnection(JDBC_URL_local);
			} catch (SQLException e) {
				System.out.println("Connection to sqlite failed: " + e.getMessage());
			}
		}

		// each entry is stored in records (EVERYTHING)
		List<SessionGroup> groups = new ArrayList<>();

        try {

			// Create a StringBuilder to construct the query
			StringBuilder showQueryBuilder = new StringBuilder("SELECT * FROM " + database + " WHERE 1=1");
			List<String> params = new ArrayList<>();

			// Conditionally append WHERE clauses
			if (!sDept.equals("Departments")) {
				showQueryBuilder.append(" AND dept=?");
				params.add(sDept);
			}

			if (!sBatch.equalsIgnoreCase("Batches")) {
				showQueryBuilder.append(" AND batch=?");
				params.add(sBatch);
			}

			if (!sSem.equals("Semester")) {
				showQueryBuilder.append(" AND sem=?");
				params.add(sSem);
			}

			// Add the new sSub parameter
			if (!sSub.equalsIgnoreCase("Subjects")) {
				showQueryBuilder.append(" AND sub=?"); 
				params.add(sSub);
			}

			// Add the ORDER BY clause
			showQueryBuilder.append(" ORDER BY USN");

			String showQuery = showQueryBuilder.toString();
			try (PreparedStatement preparedStatement = connection.prepareStatement(showQuery)) {

				// Loop through the collected parameters and set them
				for (int i = 0; i < params.size(); i++) {
					preparedStatement.setString(i + 1, params.get(i));
				}

				ResultSet resultSet = preparedStatement.executeQuery();

                // each entry is stored in records (EVERYTHING)
				List<SessionRecord> records = new ArrayList<>();


				while (resultSet.next()) {
					records.add(new SessionRecord(
							resultSet.getString("login_time"),
							resultSet.getString("logout_time"),
							resultSet.getString("usn"),
							resultSet.getString("name"),
							resultSet.getString("sem"),
							resultSet.getString("dept"),
							resultSet.getString("sub"),
							resultSet.getString("batch"),
							resultSet.getString("session_id")

					));
				}
                System.out.println("Data Retrived/Reloaded");

				if (!database.equals("temp")) {
					connection.close();
				}

                // Group the records
				groups = SessionGrouper.groupSessions(records);

                return groups;
			}
		} catch (SQLException e) {
			System.out.println("Database not connected");
			System.out.println(e.getMessage());
            return groups;
		}

    }

    void showData(String database, JPanel mainContent, String sSub, String sDept, String sSem, String sBatch) {

		// remove previously present content
		if (mainContent.getComponentCount() > 1)
			mainContent.remove(1);

		// Grouped records
		List<SessionGroup> groups = getDatafromDataBase(database, mainContent, sSub, sDept, sSem, sBatch);

        JScrollPane scroll = new JScrollPane(new TimeGroupPanel(groups));

		// Add the content
        mainContent.add(scroll, BorderLayout.CENTER);
		mainContent.revalidate();
		mainContent.repaint();

		// Add action Listener to "Export Button"
		ActionListener[] listeners = exportButton.getActionListeners();

		// Remove each listener
		for (ActionListener listener : listeners) {
			exportButton.removeActionListener(listener);
		}

		DataPlace.exportButton.addActionListener(e -> {
			JFrame frame = new JFrame();
			JDialog dialog = new JDialog(frame, "Select Groups to Export", true);
			dialog.setLayout(new BorderLayout());

			JPanel listPanel = new JPanel();
			listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
			List<JCheckBox> checkBoxes = new ArrayList<>();
			Integer num = 0;
			for (SessionGroup group : groups) {
				JCheckBox cb = new JCheckBox((++num).toString());
				checkBoxes.add(cb);
				listPanel.add(cb);
			}

			JScrollPane scrollPane = new JScrollPane(listPanel);
			dialog.add(scrollPane, BorderLayout.CENTER);

			JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
			JLabel selectedLabel = new JLabel("0 selected");
			JButton selectAllBtn = new JButton("Select All");
			JButton exportBtn = new JButton("Export");
			String[] pdf_csv = { "PDF", "CSV" };
			JComboBox<String> format = new JComboBox<>(pdf_csv);

			selectAllBtn.addActionListener(ev -> {
				boolean isCancel = selectAllBtn.getText().equals("Cancel");
				selectAllBtn.setText(isCancel ? "Select All" : "Cancel");
				for (JCheckBox cb : checkBoxes)
					cb.setSelected(!isCancel);

				long count = checkBoxes.stream().filter(AbstractButton::isSelected).count();
				selectedLabel.setText(count + " selected");
			});

			for (JCheckBox cb : checkBoxes) {
				cb.addActionListener(ev -> {
					long count = checkBoxes.stream().filter(AbstractButton::isSelected).count();
					selectedLabel.setText(count + " selected");
				});
			}
			exportBtn.addActionListener(ev -> {
				ArrayList<SessionGroup> selectedGroups = new ArrayList<>();
				for (int i = 0; i < checkBoxes.size(); i++) {
					if (checkBoxes.get(i).isSelected()) {
						selectedGroups.add(groups.get(i));
					}
				}
				String selected = (String) format.getSelectedItem();

				new ExportCsvPdf(selected,selectedGroups);
			
			});

			bottomPanel.add(selectedLabel);
			bottomPanel.add(selectAllBtn);
			bottomPanel.add(format);
			bottomPanel.add(exportBtn);
			dialog.add(bottomPanel, BorderLayout.SOUTH);

			dialog.setSize(400, 280);
			dialog.setLocationRelativeTo(mainContent);
			dialog.setVisible(true);
		});
    }

    void createInitialView() {
		try {
			for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
				if ("Nimbus".equals(info.getName())) {
					UIManager.setLookAndFeel(info.getClassName());
					break;
				}
			}
		} catch (Exception e) {
			// Use default if Nimbus is not available
			System.out.println("Nimbus not there");
		}
        everything = new JCheckBox("All");
		matchD = new JCheckBox("Match Date");
		matchT = new JCheckBox("Match Time");

		String date = new java.text.SimpleDateFormat("yyyy-MM-dd, E").format(Calendar.getInstance().getTime());
		dateFrom = new JTextField(date, 12);
		dateFrom.setEditable(false);
		dateTo = new JTextField(date, 12);
		dateTo.setEditable(false);

		// Add action listeners for dialog components
		ActionListener dialogActionListener = e -> {
			if (e.getSource() == everything) {
				dateFrom.setEnabled(!everything.isSelected());
				dateTo.setEnabled(!everything.isSelected());
				matchD.setEnabled(!everything.isSelected());
			}
			if (e.getSource() == matchD) {
				dateTo.setVisible(!matchD.isSelected());
				datePanel.getComponent(2).setVisible(dateTo.isVisible());
			}
			if (e.getSource() == matchT) {
				matchSlotConditon = !matchSlotConditon;
			}
			// Trigger data refresh
            showData(base, mainContent, sub.getSelectedItem().toString().trim(), department.getSelectedItem().toString().trim(),
				sem.getSelectedItem().toString().trim(), batch.getSelectedItem().toString().trim());
		};

        everything.addActionListener(dialogActionListener);
		matchD.addActionListener(dialogActionListener);
		matchT.addActionListener(dialogActionListener);

		dateFrom.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (dateFrom.isEnabled()) {
					DatePicker datePicker = new DatePicker(jf, dateFrom);
					datePicker.showPicker();
					dialogActionListener.actionPerformed(new ActionEvent(dateFrom, 0, ""));
				}
			}
		});
		dateTo.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (dateTo.isEnabled()) {
					DatePicker datePicker = new DatePicker(jf, dateTo);
					datePicker.showPicker();
					dialogActionListener.actionPerformed(new ActionEvent(dateTo, 0, ""));
				}
			}
		});

        view = new JPanel(new BorderLayout());
		// view.setBackground(new Color(24, 25, 26));
		view.setBorder(BorderFactory.createEmptyBorder(50, 50, 50, 50));

		// Heading Panel at the Top (North)
		JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		headingPanel.setOpaque(false);
		JLabel title = new JLabel("Log book");
		title.setFont(new Font("Arial", Font.BOLD, 48));
		title.setForeground(Color.WHITE);
		headingPanel.add(title);
		view.add(headingPanel, BorderLayout.NORTH);

		// Content Panel in the Center
		JPanel contentPanel = new JPanel(new GridBagLayout());
		contentPanel.setOpaque(false);

		// Buttons Panel with Descriptions
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 50, 0));
		buttonPanel.setOpaque(false);

		// "Add" Button with multiline HTML text
		JButton addLogBook = new JButton(
				"<html><center><b>+ Add</b><br><font size=\"3\">Add csv file</font></center></html>");
		addLogBook.setFont(new Font("Arial", Font.BOLD, 24));
		addLogBook.setForeground(Color.WHITE);
		addLogBook.setBackground(new Color(52, 199, 89));
		addLogBook.setBorder(BorderFactory.createLineBorder(new Color(52, 199, 89), 2, true));
		addLogBook.setPreferredSize(new Dimension(200, 80));
		addLogBook.setFocusPainted(false);
		buttonPanel.add(addLogBook);

		// "View" Button with multiline HTML text
		JButton viewLogBook = new JButton(
				"<html><center><b>→ View</b><br><font size=\"3\">View existing data</font></center></html>");
		viewLogBook.setFont(new Font("Arial", Font.BOLD, 24));
		viewLogBook.setForeground(Color.WHITE);
		viewLogBook.setBackground(new Color(88, 86, 214));
		viewLogBook.setBorder(BorderFactory.createLineBorder(new Color(88, 86, 214), 2, true));
		viewLogBook.setPreferredSize(new Dimension(200, 80));
		viewLogBook.setFocusPainted(false);
		buttonPanel.add(viewLogBook);

		contentPanel.add(buttonPanel);
		view.add(contentPanel, BorderLayout.CENTER);

		jf.add(view);

        addLogBook.addActionListener(e -> {
            base = "temp";
            if (importFromUserSelection())
                showAddViewLogBookPanel();
        });

        viewLogBook.addActionListener(e -> {
            base = System.getenv("DATABASE");
            showAddViewLogBookPanel();
        });
    }

    boolean importFromUserSelection() {
        JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Select a CSV file to import");
		fileChooser.setCurrentDirectory(new File("./") );

        //filter to show only folders/CSV files in current directory
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv"); 
		fileChooser.setFileFilter(filter);

		int userSelection = fileChooser.showOpenDialog(null); 
        if (userSelection == JFileChooser.APPROVE_OPTION) {

			File selectedFile = fileChooser.getSelectedFile();
            String csvFilePath = selectedFile.getAbsolutePath();

			try {
				logBookData.manager = new AddLogbookManager();
				// Call the import method with the user-selected file path
				logBookData.manager.importCsvToDatabase(csvFilePath);
				JOptionPane.showMessageDialog(null, "Successfully imported data from " + selectedFile.getName(),
						"Import Complete", JOptionPane.INFORMATION_MESSAGE);
				return true;
			} catch (IOException | SQLException e) {
				JOptionPane.showMessageDialog(null, "Error importing data: " + e.getMessage(), "Import Error",
						JOptionPane.ERROR_MESSAGE);
				e.printStackTrace();
			}

        }
        return false;

    }

    void showAddViewLogBookPanel() {
		mainContent = new JPanel(new BorderLayout(12, 12));
        mainContent.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		mainContent.add(createOptionsPanel(), BorderLayout.NORTH);

        syncDatabases();
        showData(base, mainContent, sub.getSelectedItem().toString().trim(), department.getSelectedItem().toString().trim(),
				sem.getSelectedItem().toString().trim(), batch.getSelectedItem().toString().trim());

		jf.remove(view);
		jf.add(mainContent);
		jf.revalidate();
		jf.repaint();
	}

    JPanel createOptionsPanel() {

		JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));

		// Add "Back" button
		JButton backButton = new JButton("Back");
		optionsPanel.add(backButton);

        //Add "Refresh" button
        JButton refresh = new JButton("⟳");
		optionsPanel.add(refresh);

		optionsPanel.add(new JLabel("  ")); //Spacer

		try {
			BufferedReader reader = new BufferedReader(new FileReader("./optionsData.csv")); 
			String line;

			int linesCount = (int) Files.lines(Paths.get("./optionsData.csv")).count();

			String[] labSubjects = new String[linesCount];
			String[] departments = new String[linesCount];
			String[] batches = new String[linesCount];
			String[] sems = new String[linesCount];

			while ( (line = reader.readLine()) != null) {
				if (line.contains("Subjects")) labSubjects = line.split(",");
				
				else if (line.contains("Departments")) departments = line.split(",");
				
				else if (line.contains("Batches")) batches = line.split(",");
				else if (line.contains("Semester")) sems = line.split(",");
			}
		
			sub = new JComboBox<>(labSubjects);
			optionsPanel.add(sub);

			department = new JComboBox<>(departments);
			optionsPanel.add(department);

			batch = new JComboBox<>(batches);
			optionsPanel.add(batch);

			sem = new JComboBox<>(sems);
			optionsPanel.add(sem);
		} catch (IOException e) {
			e.printStackTrace();
		}

        optionsPanel.add(new JLabel("  ")); // Spacer

		JButton selectDateTime = new JButton("Select Date/Time");
		optionsPanel.add(selectDateTime);

        exportButton = new JButton("Export");
		optionsPanel.add(exportButton);

        ActionListener actionListener = e -> 
 			showData(base, mainContent, sub.getSelectedItem().toString().trim(), department.getSelectedItem().toString().trim(),
			sem.getSelectedItem().toString().trim(), batch.getSelectedItem().toString().trim());

		ActionListener actionListenerCombo = e ->{ 
			((JComboBox) e.getSource()).setPopupVisible(false);	
			actionListener.actionPerformed(e);
		};

        startTime = new JComboBox<>(
				new String[] { "08:30", "09:20", "10:10", "11:15", "12:15", "13:30", "14:20", "15:10" });
		endTime = new JComboBox<>(
				new String[] { "Ongoing", "09:20", "10:10", "11:00", "12:15", "12:55", "14:20", "15:10", "16:00" });
		endTime.setSelectedItem(endTime.getItemAt(endTime.getItemCount() - 1));

		sub.addActionListener(actionListenerCombo);
		department.addActionListener(actionListenerCombo);
		batch.addActionListener(actionListenerCombo);
		sem.addActionListener(actionListenerCombo);
        startTime.addActionListener(actionListenerCombo);
		endTime.addActionListener(actionListenerCombo);

		refresh.addActionListener(e -> {
			syncDatabases();;
            actionListener.actionPerformed(e);
		});

		backButton.addActionListener(e -> {
			jf.remove(mainContent);
			createInitialView();
			jf.revalidate();
			jf.repaint();
			if (logBookData.manager != null) {
				try {
					logBookData.manager.close();
					logBookData.manager = null;
				} catch (SQLException ee) {
					System.err.println("Error closing database connection: " + ee.getMessage());
				}
			} else {
				System.out.println("Manger is null");
			}
		});

        selectDateTime.addActionListener(e -> showDateTimeDialog());

        return optionsPanel;
    }

	void showDateTimeDialog() {
		JDialog dialog = new JDialog(jf, "Select Date/Time", true);
		dialog.setResizable(false);
		dialog.setSize(400, 280);
		dialog.setLocationRelativeTo(jf);

		JPanel dialogPanel = new JPanel(new GridLayout(0, 1, 5, 5));
		dialogPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		datePanel.add(new JLabel("Date:"));
		datePanel.add(dateFrom);
		datePanel.add(new JLabel("to"));
		datePanel.add(dateTo);

		JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		timePanel.add(new JLabel("Slot:"));
		timePanel.add(startTime);
		timePanel.add(new JLabel(" - "));
		timePanel.add(endTime);

		dialogPanel.add(everything);
		dialogPanel.add(matchD);
		dialogPanel.add(datePanel);
		dialogPanel.add(matchT);
		dialogPanel.add(timePanel);

		dialog.add(dialogPanel);
		dialog.setVisible(true);
	}


}

public class logBookData {

    static Integer count = 1;
    static AddLogbookManager manager;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new DataPlace();
            }
        });
    }
}
