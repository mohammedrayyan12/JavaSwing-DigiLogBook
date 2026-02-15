import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;
import java.awt.event.*;

import java.io.File;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

class DataPlace {
	boolean isTestingPhase = Boolean.parseBoolean(ConfigLoader.config.getProperty("testing.phase","false"));
    private JFrame jf;
    private JPanel view;
    private JPanel mainContent;
    static JTextField dateFrom, dateTo;
	static JComboBox<String> startTime, endTime;
	static JCheckBox everything;
	private JCheckBox matchD, matchT;
	static JButton exportButton;
    private JPanel datePanel;


	// Store the generated combos to retrieve values for filtering
	static List<JComboBox<String>> dynamicCombos = new ArrayList<>();

    private static String table = ConfigLoader.config.getProperty("LOCAL_TABLE"); 
    static boolean matchSlotConditon = false;

    private static String JDBC_URL_cloud = ConfigLoader.config.getProperty("JDBC_URL_cloud");
    private final static String JDBC_URL_local = ConfigLoader.getLocalDBUrl(); 
	private static String USERNAME_cloud = ConfigLoader.config.getProperty("JDBC_USERNAME_cloud");
	private static String PASSWORD_cloud = ConfigLoader.config.getProperty("JDBC_PASSWORD_cloud");

	// Store categories and their options
	static Map<String, List<String>> configMap = HelperFunctions.loadConfigMap(JDBC_URL_local);


    DataPlace() {
		jf = new JFrame("Data Zone");
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setSize(900, 500);
		jf.setLocationRelativeTo(null);

        createInitialView();
		jf.setVisible(true);
	}

	
	static void syncDatabases() {
		
		if (table.equals("temp")) return;

		String localTable = ConfigLoader.config.getProperty("LOCAL_TABLE");
		String cloudTable = ConfigLoader.config.getProperty("CLOUD_TABLE");

		try (Connection cloudConn = DriverManager.getConnection(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud);
			Connection localConn = DriverManager.getConnection(JDBC_URL_local)) {

			// 1. Setup local table to match cloud structure
			try (Statement stmt = localConn.createStatement()) {
				stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " + localTable + " (" +
						"session_id TEXT PRIMARY KEY, " +
						"login_time TEXT, " +
						"logout_time TEXT, " +
						"usn TEXT, " +
						"name TEXT, " +
						"details TEXT" + // JSON stored as TEXT in SQLite
						");");

				// 2. Add the Index for each category (Fast seaching => Binary Search)
				for (String category : configMap.keySet()) {
					// unique name for each index
					String indexName = "idx_details_" + category;
					
					String indexSql = "CREATE INDEX IF NOT EXISTS " + indexName + 
									" ON " + localTable + " (json_extract(details, '$." + category + "'));";
					
					stmt.executeUpdate(indexSql);
				}
			}
		

			// 2. Fetch all from Cloud
			String selectSql = "SELECT session_id, login_time, logout_time, usn, details FROM " + cloudTable;
			
			localConn.setAutoCommit(false);
			String insertSql = "INSERT OR REPLACE INTO " + localTable + 
							" (session_id, login_time, logout_time, usn, name, details) VALUES (?, ?, ?, ?, ?, ?)";
			
			try (PreparedStatement cloudStmt = cloudConn.prepareStatement(selectSql);
				ResultSet rs = cloudStmt.executeQuery();
				PreparedStatement localStmt = localConn.prepareStatement(insertSql)) {

				while (rs.next()) {
					localStmt.setString(1, rs.getString("session_id"));
					localStmt.setString(2, rs.getString("login_time"));
					localStmt.setString(3, rs.getString("logout_time"));
					localStmt.setString(4, rs.getString("usn"));
					localStmt.setString(5, rs.getString("name"));
					localStmt.setString(6, rs.getString("details")); // Just move the entire string => parse the string to behave like json
					localStmt.addBatch();
				}
				localStmt.executeBatch();
			}
			localConn.commit();
			localConn.setAutoCommit(true);
			System.out.println("✓ Mirror Sync Complete.");
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
    
	public List<SessionGroup> getDatafromDataBase(String table, JPanel mainContent, Map<String, String> currentFilters) {

        // configure which database to connect
        Connection connection = null;
		if (table.equals("temp")) {
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
			StringBuilder showQueryBuilder = new StringBuilder("SELECT * FROM " + table + " WHERE 1=1");
			List<String> params = new ArrayList<>();

			// Conditionally append WHERE clauses
			if (currentFilters != null) {
				for (Map.Entry<String, String> filter : currentFilters.entrySet()) {
					String category = filter.getKey();
					String value = filter.getValue();
					
					// Check if user selected something other than the default header (e.g., "Subjects")
					if (!value.equalsIgnoreCase(category)) {
						showQueryBuilder.append(" AND json_extract(details, '$.")
										.append(category)
										.append("') = ?");
						params.add(value);
					}
				}
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
					String jsonDetails = resultSet.getString("details");
    
					// Convert the JSON string into a Map of ALL available attributes
					Map<String, String> attrMap = HelperFunctions.parseJsonToMap(jsonDetails);

					records.add(new SessionRecord(
							resultSet.getString("login_time"),
							resultSet.getString("logout_time"),
							resultSet.getString("usn"),
							resultSet.getString("name"),
							attrMap,
							resultSet.getString("session_id")
					));
				}
                System.out.println("Data Retrived/Reloaded");

				if (!table.equals("temp")) {
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

    void showData(String table, JPanel mainContent, Map<String, String> currentFilters) {

		// remove previously present content
		if (mainContent.getComponentCount() > 1)
			mainContent.remove(1);

		// Grouped records
		List<SessionGroup> groups = getDatafromDataBase(table, mainContent, currentFilters);

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
            showData(table, mainContent, HelperFunctions.getCurrentFilters());
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

        // Top Panel (North) using BorderLayout for title (Center) and settings (East)
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        
        // Heading Panel at the Top (North) - Keep it in the CENTER of topPanel
        JPanel headingPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        headingPanel.setOpaque(false);
        JLabel title = new JLabel("Log book");
        title.setFont(new Font("Arial", Font.BOLD, 48));
        title.setForeground(Color.WHITE);
        headingPanel.add(title);
        
        // Settings Button Panel (East)
        JPanel settingsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        settingsPanel.setOpaque(false);
        
        // Settings Button ⚙️
        JButton settingsButton = new JButton("\u2699"); 
        settingsButton.setFont(new Font("Arial", Font.PLAIN, 18));
        settingsButton.setForeground(Color.WHITE);
        settingsButton.setBackground(new Color(44, 44, 46)); // Dark background
        settingsButton.setFocusPainted(false);
        settingsButton.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Padding

        settingsButton.addActionListener(e -> {
            System.out.println("Settings button clicked!");

            JDialog settingsDialog = new JDialog(jf, "Settings", true);
            settingsDialog.setMinimumSize(new Dimension(300,180));; 
            settingsDialog.setLocationRelativeTo(jf);

			Callable<JPanel> CloudDBConfig = () -> {
				// main panel with padding
				JPanel mainPanel = new JPanel(new GridBagLayout());
				GridBagConstraints gbc = new GridBagConstraints();
				gbc.anchor = GridBagConstraints.WEST;
				gbc.insets = new Insets(5, 10, 5, 10); // padding
				gbc.fill = GridBagConstraints.HORIZONTAL;
				gbc.weightx = 1.0;

				JLabel cloudDatabaseLabel = new JLabel("Cloud Database Configuration");
				JLabel cloudDatabaseLinkLabel = new JLabel("Cloud Database Link"); cloudDatabaseLinkLabel.setVisible(false);
				JLabel cloudDatabaseUserLabel = new JLabel("Cloud Database Username:"); cloudDatabaseUserLabel.setVisible(false);
				JLabel cloudDatabasePsswdLabel = new JLabel("Cloud Database Password:"); cloudDatabasePsswdLabel.setVisible(false);

				gbc.gridx = 0;
				gbc.gridy = 0;
				mainPanel.add(cloudDatabaseLabel, gbc);


				JPanel sensitivePanel = new JPanel(new GridBagLayout());
				GridBagConstraints gbcs = new GridBagConstraints();
				gbcs.anchor = GridBagConstraints.WEST;
				gbcs.fill = GridBagConstraints.HORIZONTAL;
				gbcs.weightx = 1.0;

				boolean isVerified = Boolean.parseBoolean(ConfigLoader.config.getProperty("CLOUD_DB_VERIFIED", "false"));
				JLabel verifiedorNot = (isVerified) ? new JLabel("Verfied"): new JLabel("Not Verified");
				verifiedorNot.setFont(new Font("Arial", Font.BOLD, 14));
				if (isVerified) verifiedorNot.setForeground(new Color(52, 199, 89));
				else verifiedorNot.setForeground(Color.RED);

				JDBC_URL_cloud = ConfigLoader.config.getProperty("JDBC_URL_cloud");

				if (JDBC_URL_cloud != null && !JDBC_URL_cloud.isEmpty()) {
					JTextField cloudDatabaseLinkField = new JTextField(JDBC_URL_cloud, 25);
					cloudDatabaseLinkField.setEditable(false);
					JTextField cloudDatabaseUserField = new JTextField(USERNAME_cloud,25); cloudDatabaseUserField.setVisible(false);
					JPasswordField cloudDatabasePsswdField = new JPasswordField(PASSWORD_cloud,25); cloudDatabasePsswdField.setVisible(false);

					JButton editButton = new JButton("Edit ✎");
					
					editButton.addActionListener(ee -> {
						cloudDatabaseLinkField.requestFocus();  //Input Focus
						boolean isVisible = !cloudDatabaseUserField.isVisible();
						verifiedorNot.setVisible(!isVisible); //Verified Label
						cloudDatabaseLinkLabel.setVisible(isVisible); //Link Label
						cloudDatabaseUserLabel.setVisible(isVisible); //Username Label
						cloudDatabasePsswdLabel.setVisible(isVisible); //Password Label
						cloudDatabaseLinkField.setEditable(!cloudDatabaseLinkField.isEditable()); //Link field
						cloudDatabaseUserField.setVisible(!cloudDatabaseUserField.isVisible()); //Username field
						cloudDatabasePsswdField.setVisible(!cloudDatabasePsswdField.isVisible()); //Password field

						editButton.setText(cloudDatabaseLinkField.isEditable() ? "Verify" : "Edit ✎"); //Edit Button
						if (!cloudDatabaseLinkField.isEditable()) {        
							JDBC_URL_cloud = cloudDatabaseLinkField.getText().trim();
							USERNAME_cloud = cloudDatabaseUserField.getText().trim();
							PASSWORD_cloud = new String(cloudDatabasePsswdField.getPassword()).trim();

							boolean verification = CloudDataBaseInfo.verification(JDBC_URL_cloud,USERNAME_cloud,PASSWORD_cloud);

							ConfigLoader.saveCloudDbConfig(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud, verification);
							
							if(!verification) {
								verifiedorNot.setText("Not Verified");
								verifiedorNot.setForeground(Color.RED); 
							} else {
								CloudDataBaseInfo.createTables(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud);
								verifiedorNot.setText("Verified");
								verifiedorNot.setForeground(new Color(52, 199, 89)); // Green
							}
						}
						settingsDialog.pack();
					});
					int iX = 0, iY=0;
					gbcs.gridx = iX; gbcs.gridy=iY++; sensitivePanel.add(cloudDatabaseLinkLabel,gbcs);
					gbcs.gridx = iX; gbcs.gridy=iY++; sensitivePanel.add(cloudDatabaseLinkField,gbcs);
					gbcs.gridx = ++iX; sensitivePanel.add(editButton,gbcs);
					gbcs.gridx = --iX; gbcs.gridy=iY++; sensitivePanel.add(cloudDatabaseUserLabel,gbcs);
					gbcs.gridy = iY++; sensitivePanel.add(cloudDatabaseUserField,gbcs);
					gbcs.gridy = iY++; sensitivePanel.add(cloudDatabasePsswdLabel,gbcs);
					gbcs.gridy = iY++; sensitivePanel.add(cloudDatabasePsswdField,gbcs);


				} else {
					verifiedorNot.setVisible(false);
					JButton addCloudDatabaseButton = new JButton("Add Cloud Database Info");
					addCloudDatabaseButton.addActionListener(ee -> {
						// --- Inner Dialog (Add Info) ---
						JDialog addInfoDialog = new JDialog(jf, "Add Cloud Database Info", true);
						addInfoDialog.setSize(350, 300);
						addInfoDialog.setLocationRelativeTo(settingsDialog); 
						addInfoDialog.setLayout(new GridBagLayout());
						
						GridBagConstraints innerGbc = new GridBagConstraints();
						innerGbc.fill = GridBagConstraints.HORIZONTAL;
						innerGbc.insets = new Insets(5, 10, 5, 10);
						innerGbc.weightx = 1.0;

						JTextField cloudDatabaseLinkField = new JTextField(20);
						JTextField cloudDatabaseUserField = new JTextField(20);
						JPasswordField cloudDatabasePsswdField = new JPasswordField(20); // JPasswordField
						JButton saveButton = new JButton("Verify and Save");

						innerGbc.gridx = 0; innerGbc.gridy = 0; addInfoDialog.add(new JLabel("<html>Enter JDBC Link <font color='red'>*</font></html>"), innerGbc);
						innerGbc.gridy = 1; addInfoDialog.add(cloudDatabaseLinkField, innerGbc);
						innerGbc.gridy = 2; addInfoDialog.add(new JLabel("Enter Username (if any)"), innerGbc);    
						innerGbc.gridy = 3; addInfoDialog.add(cloudDatabaseUserField, innerGbc);                
						innerGbc.gridy = 4; addInfoDialog.add(new JLabel("Enter password (if any)"), innerGbc);
						innerGbc.gridy = 5; addInfoDialog.add(cloudDatabasePsswdField, innerGbc);
						innerGbc.gridy = 6; addInfoDialog.add(saveButton, innerGbc);
						
						saveButton.addActionListener(eee -> {
							if (cloudDatabaseLinkField.getText().trim().isEmpty()) {
								JOptionPane.showMessageDialog(addInfoDialog, "JDBC Link cannot be blank.", "Invalid Input", JOptionPane.ERROR_MESSAGE);
								return;
							}                            
							JDBC_URL_cloud = cloudDatabaseLinkField.getText().trim();
							USERNAME_cloud = cloudDatabaseUserField.getText().trim();
							PASSWORD_cloud = new String(cloudDatabasePsswdField.getPassword()).trim();


							boolean verification = CloudDataBaseInfo.verification(JDBC_URL_cloud,USERNAME_cloud,PASSWORD_cloud);

							ConfigLoader.saveCloudDbConfig(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud, verification );

							if(!verification) {
								verifiedorNot.setText("Not Verified");
								verifiedorNot.setForeground(Color.RED); 
							} else {
								CloudDataBaseInfo.createTables(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud);
								verifiedorNot.setText("Verified");
								verifiedorNot.setForeground(new Color(52, 199, 89)); // Green
							}

							addInfoDialog.dispose();
							settingsDialog.dispose();
							settingsButton.putClientProperty("targetTab", 2);
							settingsButton.doClick(); // to repaint Settings Dialog
						});

						addInfoDialog.setVisible(true);
					});
					sensitivePanel.add(addCloudDatabaseButton);
				}

				gbc.gridy = 1; // Put sensitive panel on the next row visually
				mainPanel.add(sensitivePanel, gbc);

				gbc.gridy = 2;
				gbc.anchor = GridBagConstraints.CENTER;   // Center the component within its cell
				gbc.fill = GridBagConstraints.NONE; 
				mainPanel.add(verifiedorNot,gbc); 

				return mainPanel;
			};

			Callable<JPanel> AutoDeleteConfig = () -> {
				JPanel mainPanel = new JPanel();
				mainPanel.setLayout(new GridBagLayout());
				GridBagConstraints gbc = new GridBagConstraints();
				gbc.insets = new Insets(5, 10, 5, 10); 
				gbc.fill = GridBagConstraints.HORIZONTAL; 
				int iX = 0, iY = 0; // for gbc 
				gbc.weightx = 1.0; 

				if (Boolean.parseBoolean(ConfigLoader.config.getProperty("testing.skip.delete", "false"))) {
					JLabel warningLabel = new JLabel("\u26A0 Testing Mode: Deletions are disabled");
					warningLabel.setForeground(Color.RED);
					warningLabel.setFont(new Font("Arial", Font.BOLD, 12));
					gbc.gridy = iY++;
					mainPanel.add(warningLabel, gbc);
				}

				boolean autoSavedState = Boolean.parseBoolean(ConfigLoader.config.getProperty("auto.save"));

				JToggleButton enableAutoSave =  new JToggleButton("Enable Auto Save"); enableAutoSave.setSelected(autoSavedState);


				// --- AutoSave Label ---
				JLabel autoSaveLabel = new JLabel("Auto Save Directory");
				gbc.gridx = iX;
				gbc.gridy = iY++;
				gbc.weighty = 0.0; 
				mainPanel.add(autoSaveLabel, gbc);

				// --- Path to save file ---
				JTextArea autoSaveDir = new JTextArea();
				autoSaveDir.setEditable(false);
				autoSaveDir.setLineWrap(true);
				autoSaveDir.setWrapStyleWord(true);
				autoSaveDir.setText(ConfigLoader.config.getProperty("auto.save.records.directory"));

				JScrollPane scrollPane = new JScrollPane(autoSaveDir);
				gbc.gridx = 0;
				gbc.gridy = iY++;
				gbc.weighty = 1.0; 
				mainPanel.add(scrollPane, gbc);
 
				//Mouse Listener to Set AutoSave Directory
				autoSaveDir.addMouseListener(new MouseAdapter() {
					public void mouseClicked(MouseEvent e) {
						if (!autoSaveDir.isEnabled()) return;

						System.out.println("Text Area Clicked");

						JFileChooser fileChooser = new JFileChooser();

						// Configure the file chooser to select directories only
						fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
						
						// Set a default starting directory as current working directory
						fileChooser.setCurrentDirectory(new File(ConfigLoader.config.getProperty("auto.save.records.directory")));

						int result = fileChooser.showSaveDialog(autoSaveDir);

						if (result == JFileChooser.APPROVE_OPTION) {
							File selectedDirectory = fileChooser.getSelectedFile();
							String absolutePath = selectedDirectory.getAbsolutePath();
							
							String oldPathString = ConfigLoader.config.getProperty("auto.save.records.directory");

							Path oldPath = Paths.get(oldPathString);
							Path newPathDir = Paths.get(absolutePath);

							if (! (oldPath.equals(newPathDir) || !Files.isDirectory(oldPath)) ) {
								try {
									// Iterate through all files in the old directory
									try (Stream<Path> files = Files.list(oldPath)) {
										files.forEach(source -> {

											// Construct the destination path
											Path destination = newPathDir.resolve(source.getFileName());

											try {
												// Move the file. ATOMIC_MOVE ensures the file is moved in one operation, 
												// and REPLACE_EXISTING prevents errors if a file with the same name exists.
												Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

											} catch (IOException ee) {
												// Handle failure for individual file move (e.g., permission denied)
												System.err.println("Failed to move file " + source.getFileName() + ": " + ee.getMessage());

											}
										});
									}
									//delete the previous directory
									Files.delete(oldPath); 
									System.out.println("Previous autoSave directory");

								} catch (IOException ee) {
									// Handle critical errors like inability to create the new directory or list files
									System.err.println("Critical error during directory transfer: " + ee.getMessage());
								}
							}

							ConfigLoader.setAutoSaveDirectory(absolutePath);
							autoSaveDir.setText(absolutePath);

							JOptionPane.showMessageDialog(jf, 
								"AutoSave directory set to: \n" + absolutePath + "\n(Exported and deleted deleted file in: " + oldPathString + " )", 
								"Location Selected", 
								JOptionPane.INFORMATION_MESSAGE);	
						} else if (result == JFileChooser.CANCEL_OPTION) {
							// User cancelled the dialog
							System.out.println("Folder selection cancelled by user.");
						}
						settingsDialog.pack();
					}
				});
				

				// --- Recent Cleanup ---
				JLabel cleanUpL = new JLabel("Recent Cleanup (Local DB): " + ConfigLoader.config.getProperty("local.auto.delete.last.run.date"));  
				gbc.gridy = iY++; mainPanel.add(cleanUpL,gbc);

				JLabel cleanUpC = new JLabel("Recent Cleanup (Cloud DB): " + ConfigLoader.config.getProperty("cloud.auto.delete.last.run.date"));  
				gbc.gridy = iY++; mainPanel.add(cleanUpC,gbc);

				// --- Time Duration to delete (Local) ---
				JPanel timeDuration = new JPanel(new GridLayout(2, 2));
				JLabel timeDurationLocalLabel = new JLabel("Duration of cleanup (Local DB):  "); timeDuration.add(timeDurationLocalLabel); 

				JComboBox timeDurationLocal = new JComboBox<>(new String[] {"1 Month", "3 Months", "6 Months", "9 Months", "12 Months"}); timeDuration.add(timeDurationLocal);

				// --- Time Duration to delete (Cloud) ---
				JLabel timeDurationCloudLabel = new JLabel("Duration of cleanup (Cloud DB):  "); timeDuration.add(timeDurationCloudLabel); 

				JComboBox timeDurationCloud = new JComboBox<>(new String[] {"1 Week", "2 Weeks", "3 Weeks", "4 Weeks"}); timeDuration.add(timeDurationCloud);


				gbc.gridy = iY++; mainPanel.add(timeDuration,gbc);

				gbc.gridy = iY++; mainPanel.add(enableAutoSave,gbc);
				enableAutoSave.addActionListener(ee -> {
					boolean enabled = enableAutoSave.isSelected();
					String text =  (enabled) ?  "Disable Auto Save" : "Enable Auto Save";
					enableAutoSave.setText(text);
					timeDurationCloud.setEnabled(enabled);
					timeDurationLocal.setEnabled(enabled);
					cleanUpC.setVisible(enabled);
					cleanUpL.setVisible(enabled);
        			autoSaveDir.setEnabled(enabled);

					//save updated property
					ConfigLoader.setAutoSaveFeature("auto.save", String.valueOf(enabled));
					if (enabled) {
						String AUTOSAVE_DIR_PATH = ConfigLoader.config.getProperty("auto.save.records.directory");
						if (AUTOSAVE_DIR_PATH.equals("autoSaved_Session_Records"))
							AUTOSAVE_DIR_PATH = Paths.get(System.getProperty("user.home"), "autoSaved_Session_Records").toString();
						if (!Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().exists()) {
							Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().mkdirs();
						}
						ConfigLoader.setAutoSaveDirectory(AUTOSAVE_DIR_PATH);
					} else {
						String AUTOSAVE_DIR_PATH = ConfigLoader.config.getProperty("auto.save.records.directory");
						if (AUTOSAVE_DIR_PATH.equals("autoSaved_Session_Records"))
							AUTOSAVE_DIR_PATH = Paths.get(System.getProperty("user.home"), "autoSaved_Session_Records").toString();
						if (Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().exists()) {
							Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().delete();
						}
					}
					autoSaveDir.setText(!enabled ? "(Auto Save Disabled)" : ConfigLoader.config.getProperty("auto.save.records.directory")); 

					settingsDialog.pack();

				});
				
				boolean enabled = enableAutoSave.isSelected();
				
				if (enabled) {
					String AUTOSAVE_DIR_PATH = ConfigLoader.config.getProperty("auto.save.records.directory");
					if (AUTOSAVE_DIR_PATH.equals("autoSaved_Session_Records"))
						AUTOSAVE_DIR_PATH = Paths.get(System.getProperty("user.home"), "autoSaved_Session_Records").toString();
					if (!Paths.get(AUTOSAVE_DIR_PATH).toFile().getParentFile().exists()) {
						Paths.get(AUTOSAVE_DIR_PATH).toFile().getParentFile().mkdirs();
					}
					ConfigLoader.setAutoSaveDirectory(AUTOSAVE_DIR_PATH);
				} else {
					String AUTOSAVE_DIR_PATH = ConfigLoader.config.getProperty("auto.save.records.directory");
					if (AUTOSAVE_DIR_PATH.equals("autoSaved_Session_Records"))
						AUTOSAVE_DIR_PATH = Paths.get(System.getProperty("user.home"), "autoSaved_Session_Records").toString();
					if (Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().exists()) {
						Paths.get(AUTOSAVE_DIR_PATH).toFile().getAbsoluteFile().delete();
					}
				}

				String text = (enabled) ? "Disable Auto Save" : "Enable Auto Save";
				enableAutoSave.setText(text);
				
				// Explicitly set the state of dependent components
				timeDurationCloud.setEnabled(enabled);
				timeDurationLocal.setEnabled(enabled);
				cleanUpC.setVisible(enabled);
				cleanUpL.setVisible(enabled);
				
				// Update the directory field
				autoSaveDir.setText(!enabled ? "(Auto Save Disabled)" : ConfigLoader.config.getProperty("auto.save.records.directory")); 
				autoSaveDir.setEnabled(enabled);

				// Calculate index to set 
				timeDurationLocal.setSelectedIndex(Integer.parseInt(ConfigLoader.config.getProperty("local.auto.delete.duration")) / 3);
				timeDurationCloud.setSelectedIndex(Integer.parseInt(ConfigLoader.config.getProperty("cloud.auto.delete.duration")) - 1);

				//Action Listeners
				timeDurationLocal.addActionListener(ee -> {
					String selectedDuration = (String) timeDurationLocal.getSelectedItem();

					String[] parts = selectedDuration.split(" ");
					int duration = Integer.parseInt(parts[0]);

					ConfigLoader.setAutoDeleteDuration("local.auto.delete.duration", String.valueOf(duration));
					System.out.println("(LOCAL DB) Auto Delete Duration set to: " + duration);
				});

				timeDurationCloud.addActionListener(ee -> {
					String selectedDuration = (String) timeDurationCloud.getSelectedItem();

					String[] parts = selectedDuration.split(" ");
					int duration = Integer.parseInt(parts[0]);
					
					ConfigLoader.setAutoDeleteDuration("cloud.auto.delete.duration", String.valueOf(duration));
					System.out.println("(CLOUD DB) Auto Delete Duration set to: " + duration);
				});
				return mainPanel;
			};

			Callable<JPanel> ConfigurationFolder = () -> {
				JPanel mainPanel = new JPanel(new GridBagLayout());

				final Runnable[] refreshConfigurationFolder = new Runnable[1];

				// As Callable can only be called once, create a helper method (runnable) that can be called anytime to refresh the UI 
				refreshConfigurationFolder[0] = () -> {
					mainPanel.removeAll(); // Clear existing buttons
					GridBagConstraints gbc = new GridBagConstraints();
					gbc.insets = new Insets(5, 10, 5, 10);
					gbc.fill = GridBagConstraints.HORIZONTAL;
					gbc.gridx = 0;
					gbc.gridy = 0;

					// --- SECTION: Edit Options ---
					JLabel label = new JLabel("\u2699 Edit Application References");
					label.setFont(new Font("SansSerif", Font.BOLD, 14));
					mainPanel.add(label, gbc);

					// Dynamic Options to manage
					gbc.gridy++;
					JButton manageCatsBtn = new JButton("+ Add / Remove Categories");
					manageCatsBtn.setFont(new Font("SansSerif", Font.ITALIC, 12));
					// This helper would handle adding/deleting keys from the config table
					manageCatsBtn.addActionListener(ee -> HelperFunctions.showCategoryManagerDialog(mainPanel,refreshConfigurationFolder[0]));
					mainPanel.add(manageCatsBtn, gbc);

					Set<String> categories = DataPlace.configMap.keySet(); 
					
					for (String cat : categories) {
						gbc.gridy++;
						JButton btn = new JButton("Manage " + cat);
						btn.addActionListener(ee -> HelperFunctions.showEditDialog(cat, mainPanel));
						mainPanel.add(btn, gbc);
					}

					// --- SECTION: Danger Zone ---
					gbc.gridy++;
					gbc.insets = new Insets(30, 10, 10, 10);
					JButton deleteConfigurations = new JButton("\uD83D\uDDD1 Delete Configuration");
					deleteConfigurations.setBackground(new Color(220, 53, 69)); 
					deleteConfigurations.setForeground(Color.WHITE); 
					deleteConfigurations.setFocusPainted(false);
					deleteConfigurations.setFont(new Font("SansSerif", Font.BOLD, 12));

					// Makes the background color visible on MacOS and some Windows themes
					deleteConfigurations.setContentAreaFilled(true);
					deleteConfigurations.setOpaque(true);
					deleteConfigurations.setBorderPainted(false);
					mainPanel.add(deleteConfigurations, gbc);

					deleteConfigurations.addActionListener(ee -> {
						String message = "Are you sure you want to delete all configurations?\n" +
							"This action is permanent and will terminate the application.";
						String heading = "Confirm Critical Deletion";

						int response = JOptionPane.showConfirmDialog(
							mainPanel, 
							message, 
							heading, 
							JOptionPane.YES_NO_OPTION, 
							JOptionPane.WARNING_MESSAGE
						);

						if (response == JOptionPane.YES_OPTION) {
							Path CONFIG_DIR_PATH = Paths.get(System.getProperty("user.home"), ".DigiLogBook");
							ConfigLoader.deleteConfigFolder(CONFIG_DIR_PATH);
							
							// Terminate the app
							System.out.println("Terminating Application...");
							System.exit(0);
						}
					});

					
					mainPanel.revalidate();
        			mainPanel.repaint();

					settingsDialog.pack();
				};

				refreshConfigurationFolder[0].run();
				return mainPanel;

			};
			
			JTabbedPane ooptions = new JTabbedPane();

			try {
				ooptions.addTab("Configuration Details", ConfigurationFolder.call());
				ooptions.addTab("Auto Save/Delete", AutoDeleteConfig.call());
				ooptions.addTab("Cloud Database", CloudDBConfig.call());
			} catch (Exception e1) {
				System.err.println("ERROR: While Adding Tabs to Settings Dialog");
				e1.printStackTrace();
			}
			ooptions.addChangeListener(ee -> {
				settingsDialog.pack();
			});

			Object target = settingsButton.getClientProperty("targetTab");
			if (target instanceof Integer) {
				ooptions.setSelectedIndex((Integer) target);
				// Clear the property so it doesn't open that tab every time thereafter
				settingsButton.putClientProperty("targetTab", null);
			}
			
			// Add main panel to the center of the settings dialog
            settingsDialog.add(ooptions, BorderLayout.CENTER); 

			settingsDialog.pack();
            settingsDialog.setVisible(true);
        });

        settingsPanel.add(settingsButton);

        // Add components to the topPanel
        topPanel.add(headingPanel, BorderLayout.CENTER);
        topPanel.add(settingsPanel, BorderLayout.EAST);
        
        // Add the combined topPanel to the main view
        view.add(topPanel, BorderLayout.NORTH);

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
            table = "temp";
            if (importFromUserSelection())
                showAddViewLogBookPanel();
        });

        viewLogBook.addActionListener(e -> {
            table = ConfigLoader.config.getProperty("LOCAL_TABLE"); 
            
            boolean isVerified = Boolean.parseBoolean(
                ConfigLoader.config.getProperty("CLOUD_DB_VERIFIED", "false")
            );

            if (isVerified) {
                showAddViewLogBookPanel(); 
			} else {
                Object[] options = {"Verify","Maybe Later"};
                int choice = JOptionPane.showOptionDialog(
                    null, 
                    "Cloud Database is not Configured or Verified.", 
                    "Verification Required", 
                    JOptionPane.OK_CANCEL_OPTION, 
                    JOptionPane.WARNING_MESSAGE,
                    null, 
                    options, 
                    options[0]
                );
                
                if (choice == JOptionPane.OK_OPTION) {
					settingsButton.putClientProperty("targetTab", 2);
                    settingsButton.doClick();
                }
            }
        });
   
    }



	boolean importFromUserSelection() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Select CSV files to import");
		
		//filter to show only folders/CSV files in current directory
		FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv"); 
		fileChooser.setFileFilter(filter);
		fileChooser.setMultiSelectionEnabled(true);

		int userSelection = fileChooser.showOpenDialog(null); 
		if (userSelection == JFileChooser.APPROVE_OPTION) {

			File[] selectedFiles = fileChooser.getSelectedFiles();
			
			try {
				logBookData.manager = new AddLogbookManager();
				for (File file: selectedFiles) {
					String csvFilePath = file.getAbsolutePath();
					logBookData.manager.importCsvToDatabase(csvFilePath);
				}
				JOptionPane.showMessageDialog(null, "Successfully imported data from " + selectedFiles.length + " File",
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
        showData(table, mainContent, HelperFunctions.getCurrentFilters());

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
        JButton refresh = (table.equals("temp")) ? new JButton("+") : new JButton("⟳");
		optionsPanel.add(refresh);

		optionsPanel.add(new JLabel("  ")); //Spacer

		dynamicCombos.clear();

		// Logic to get values for filtering
		ActionListener actionListener = e -> {
			Map<String, String> currentFilters = new HashMap<>();
			for (JComboBox<String> combo : dynamicCombos) {
				String category = combo.getName();
				String selected = combo.getSelectedItem().toString().trim();
				currentFilters.put(category, selected);
			}
			showData(table, mainContent, currentFilters); 
		};

		// Loop through your stored configuration to build the UI
		for (Map.Entry<String, List<String>> entry : configMap.entrySet()) {
			String categoryName = entry.getKey();
			List<String> options = entry.getValue();
			
			JComboBox<String> combo = new JComboBox<>(options.toArray(new String[0]));
			combo.setName(categoryName); 
			
			combo.addActionListener(e -> {
				((JComboBox) e.getSource()).setPopupVisible(false);
				actionListener.actionPerformed(e);
			});
			
			dynamicCombos.add(combo);
			optionsPanel.add(combo);
		}

		optionsPanel.add(new JLabel("  ")); // Spacer

		JButton selectDateTime = new JButton("Select Date/Time");
		optionsPanel.add(selectDateTime);

        exportButton = new JButton("Export");
		optionsPanel.add(exportButton);

		ActionListener actionListenerCombo = e ->{ 
			((JComboBox) e.getSource()).setPopupVisible(false);	
			actionListener.actionPerformed(e);
		};

        startTime = new JComboBox<>(
				new String[] { "08:30", "09:20", "10:10", "11:15", "12:15", "13:30", "14:20", "15:10" });
		endTime = new JComboBox<>(
				new String[] { "Ongoing", "09:20", "10:10", "11:00", "12:15", "12:55", "14:20", "15:10", "16:00" });
		endTime.setSelectedItem(endTime.getItemAt(endTime.getItemCount() - 1));

        startTime.addActionListener(actionListenerCombo);
		endTime.addActionListener(actionListenerCombo);

		refresh.addActionListener(e -> {
			if (refresh.getText().contentEquals("+")) {
				JFileChooser fileChooser = new JFileChooser();
				fileChooser.setDialogTitle("Select CSV files to import");
				
				//filter to show only folders/CSV files in current directory
				FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv"); 
				fileChooser.setFileFilter(filter);
				fileChooser.setMultiSelectionEnabled(true);

				int userSelection = fileChooser.showOpenDialog(null); 
				if (userSelection == JFileChooser.APPROVE_OPTION) {

					File[] selectedFiles = fileChooser.getSelectedFiles();
					
					try {
						for (File file: selectedFiles) {
							// Call the import method with the user-selected file path
							String csvFilePath = file.getAbsolutePath();
							logBookData.manager.importCsvToDatabase(csvFilePath);
						}
						JOptionPane.showMessageDialog(null, "Successfully imported data from " + selectedFiles.length + " File",
								"Import Complete", JOptionPane.INFORMATION_MESSAGE);
					} catch (IOException | SQLException ee) {
						JOptionPane.showMessageDialog(null, "Error importing data: " + ee.getMessage(), "Import Error",
								JOptionPane.ERROR_MESSAGE);
						ee.printStackTrace();
					}
					actionListener.actionPerformed(e);
				}

			} else {
				syncDatabases();;
            	actionListener.actionPerformed(e);
			}
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

		// Create Application Folder if not exist
		final String APP_DIR_NAME = "DigiLogBook";

		final Path CONFIG_DIR_PATH = Paths.get(System.getProperty("user.home"), "." + APP_DIR_NAME);
		
		if (!CONFIG_DIR_PATH.toFile().exists()) {
			CONFIG_DIR_PATH.toFile().mkdirs();
			System.out.println("Created Application Folder at " + CONFIG_DIR_PATH);
		}

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new DataPlace();
                autoDelete.scheduleAutoDeleteTask();
            }
        });
    }
}
