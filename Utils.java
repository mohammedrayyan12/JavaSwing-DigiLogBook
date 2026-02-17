
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.List;
import java.util.Map;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.*;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;


class HelperFunctions {

    public static Map<String, List<String>> loadConfigMap(String JDBC_URL_local) {
		Map<String, List<String>> map = new LinkedHashMap<>(); // preserves the order of categories
		String confTable = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");
		
		try (Connection conn = DriverManager.getConnection(JDBC_URL_local);
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT category, item_value FROM " + confTable + " ORDER BY category, id")) {

			while (rs.next()) {
				String category = rs.getString("category");
				String value = rs.getString("item_value");
				
				// If it's a new category, initialize the list with the category name as the first item (Header)
				map.computeIfAbsent(category, k -> {
					List<String> list = new ArrayList<>();
					list.add(k); // e.g., First item is "Subjects"
					return list;
				});
				
				map.get(category).add(value);
			}
		} catch (SQLException e) {
			System.err.println("Error loading configMap from local DB");
			e.printStackTrace();
		}
		return map;
	}


    public static Map<String, String> getCurrentFilters() {
        Map<String, String> filters = new HashMap<>();
        for (JComboBox<String> combo : DataPlace.dynamicCombos) {
            String category = combo.getName(); 
            String selected = combo.getSelectedItem().toString().trim();
            filters.put(category, selected);
        }
        return filters;
    }
    
    public static Map<String, String> parseJsonToMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) return map;

        // Remove curly braces and split by ","
        String clean = json.substring(1, json.length() - 1);
        String[] pairs = clean.split(",");

        for (String pair : pairs) {
            String[] keyValue = pair.split(":");
            if (keyValue.length == 2) {
                // Strip quotes and whitespace
                String key = keyValue[0].replaceAll("\"", "").trim();
                String value = keyValue[1].replaceAll("\"", "").trim();
                map.put(key, value);
            }
        }
        return map;
    }

    public static void showCategoryManagerDialog(JPanel parent, Runnable onRefresh) {
        Window parentWindow = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(parentWindow, "Manage Categories", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        // 1. Get current categories from the keys of your memory map
        List<String> categories = new ArrayList<>(DataPlace.configMap.keySet());
        final boolean[] dataChanged = {false};

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(Color.WHITE);

        // Initial render of categories
        renderCategoryRows(listPanel, categories, dataChanged);

        // 2. Footer for adding a New Category
        JPanel footer = new JPanel(new BorderLayout(5, 5));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JTextField inputField = new JTextField();
        inputField.setBorder(BorderFactory.createTitledBorder("New Category Name"));
        JButton addBtn = new JButton("+ Create");

        addBtn.addActionListener(e -> {
            String newCat = inputField.getText().trim();
            if (!newCat.isEmpty() && !categories.contains(newCat)) {
                // Initialize in DB with an empty list so it exists as a key
                OptionsManager.saveCategoryItems(newCat, new ArrayList<>(List.of("")));
                categories.add(newCat);
                inputField.setText("");
                dataChanged[0] = true;
                renderCategoryRows(listPanel, categories, dataChanged);
            }
        });

        footer.add(inputField, BorderLayout.CENTER);
        footer.add(addBtn, BorderLayout.EAST);

        dialog.add(new JScrollPane(listPanel), BorderLayout.CENTER);
        dialog.add(footer, BorderLayout.SOUTH);

        // 3. Update UI on close if changed
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (dataChanged[0]) {
                    onRefresh.run(); // Call the UI update trigger
                }
                dialog.dispose();
            }
        });

        dialog.setSize(350, 450);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static void renderCategoryRows(JPanel panel, List<String> categories, boolean[] dataChanged) {
        panel.removeAll();
        for (String cat : categories) {
            JPanel row = new JPanel(new BorderLayout());
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
            row.setBackground(Color.WHITE);
            row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

            JLabel nameLabel = new JLabel("  " + cat);
            nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

            JButton delBtn = new JButton("\uD83D\uDDD1"); // Trash bin icon
            delBtn.setForeground(Color.RED);
            delBtn.setContentAreaFilled(false);
            delBtn.setBorderPainted(false);

            delBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(panel, 
                    "Delete '" + cat + "' and all its items?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    // Send empty list to only delete the category
                    OptionsManager.saveCategoryItems(cat,new ArrayList<>()); 
                    categories.remove(cat);
                    dataChanged[0] = true;
                    renderCategoryRows(panel, categories, dataChanged);
                }
            });

            row.add(nameLabel, BorderLayout.CENTER);
            row.add(delBtn, BorderLayout.EAST);
            panel.add(row);
        }
        panel.revalidate();
        panel.repaint();
    }

    public static void showEditDialog(String category, JPanel parent) {
		Window parentWindow = SwingUtilities.getWindowAncestor(parent);
		JDialog dialog = new JDialog(parentWindow, "Edit " + category, Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setLayout(new BorderLayout());

		// 1. Load initial data into a local list
		List<String> localItems = OptionsManager.getCategoryItems(category);
		final boolean[] isChanged = {false}; // variables accessed inside lambdas or inner classes must be effectively final, hence create an array and change

		// 2. Search Bar (Top)
		JTextField searchField = new JTextField();
		searchField.setBorder(BorderFactory.createTitledBorder("Search " + category));
		
		// 3. Main List Panel (Center)
		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(Color.WHITE);

		// 4. Document Listener for real-time filtering
		searchField.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { updateListUI(listPanel, localItems, searchField.getText(), isChanged); }
			public void removeUpdate(DocumentEvent e) { updateListUI(listPanel, localItems, searchField.getText(), isChanged); }
			public void changedUpdate(DocumentEvent e) { updateListUI(listPanel, localItems, searchField.getText(), isChanged); }
		});

		// 5. Scrollable Area
		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.getViewport().setBackground(Color.WHITE);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());

		// 6. Footer (Input + Save Button)
		JPanel footer = new JPanel(new BorderLayout(5, 5));
		footer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		
		JTextField inputField = new JTextField();
		JButton addBtn = new JButton("+ Add");
		JButton saveBtn = new JButton("Save & Close");
		saveBtn.setBackground(new Color(40, 167, 69)); // Green
		saveBtn.setForeground(Color.WHITE);
		saveBtn.setOpaque(true);
		saveBtn.setBorderPainted(false);

		addBtn.addActionListener(e -> {
			String val = inputField.getText().trim();
			if (!val.isEmpty()) {
				localItems.add(val);
				isChanged[0] = true;
				inputField.setText("");
				updateListUI(listPanel, localItems, searchField.getText(),isChanged);
			}
		});

		saveBtn.addActionListener(e -> {
			OptionsManager.saveCategoryItems(category, localItems);
			isChanged[0] = false;
			dialog.dispose();
		});

		JPanel inputRow = new JPanel(new BorderLayout(5, 0));
		inputRow.add(inputField, BorderLayout.CENTER);
		inputRow.add(addBtn, BorderLayout.EAST);

		footer.add(inputRow, BorderLayout.NORTH);
		footer.add(saveBtn, BorderLayout.SOUTH);

		// 7. Safety Net: Window Listener
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				if (isChanged[0]) {
					int choice = JOptionPane.showConfirmDialog(dialog, 
						"You have unsaved changes. Save before closing?", "Unsaved Changes", 
						JOptionPane.YES_NO_CANCEL_OPTION);
					
					if (choice == JOptionPane.YES_OPTION) {
						OptionsManager.saveCategoryItems(category, localItems);
						dialog.dispose();
					} else if (choice == JOptionPane.NO_OPTION) {
						dialog.dispose();
					}
				} else dialog.dispose();
			}
		});

		// Final Assembly
		dialog.add(searchField, BorderLayout.NORTH);
		dialog.add(scrollPane, BorderLayout.CENTER);
		dialog.add(footer, BorderLayout.SOUTH);
		
		updateListUI(listPanel, localItems, "", isChanged); // Initial render
		
		dialog.setSize(350, 500);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	public static void updateListUI(JPanel listPanel, List<String> localItems, String filter, boolean[] isChanged) {
		listPanel.removeAll();
		String lowerFilter = filter.toLowerCase();

		for (String item : localItems) {
			if (!filter.isEmpty() && !item.toLowerCase().contains(lowerFilter)) continue;

			JPanel row = new JPanel(new BorderLayout());
			row.setBackground(Color.WHITE);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
			row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

			JLabel label = new JLabel("  " + item);
			JButton removeBtn = new JButton("\uD83D\uDDD1");
			removeBtn.setForeground(Color.RED);
			removeBtn.setBorderPainted(false);
			removeBtn.setContentAreaFilled(false);

			removeBtn.addActionListener(e -> {
				localItems.remove(item);
				isChanged[0] = true;  // Mark as changed
				updateListUI(listPanel, localItems, filter, isChanged);
			});

			row.add(label, BorderLayout.CENTER);
			row.add(removeBtn, BorderLayout.EAST);
			listPanel.add(row);
		}
		listPanel.revalidate();
		listPanel.repaint();
	}
}

class CloudDataBaseInfo {

    /**
     * Attempts to verify the database connection using the provided credentials.
     * This method is purely for connection checking and returns the status.
     * It does NOT save the configuration or display dialogs.
     * * @return true if the connection is successful, false otherwise.
     */
    public static boolean verification(String JDBC_URL_cloud, String USERNAME, String PASSWORD) {
        try (Connection conn = DriverManager.getConnection(JDBC_URL_cloud, USERNAME, PASSWORD)) {
            // Connection successful
            JOptionPane.showMessageDialog(
                null, 
                "Connection Validated.\nDatabase info saved.", 
                "Verification Result", 
                JOptionPane.INFORMATION_MESSAGE
            );
            return true; 
        } catch (SQLException e) {
            // Connection failed
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                null, 
                "Could not connect to JDBC Link.\nDatabase info saved.", 
                "Verification Result", 
                JOptionPane.WARNING_MESSAGE
            );
            return false;
        }
    }

    private static String getDatabaseType(Connection conn) throws SQLException {
    String productName = conn.getMetaData().getDatabaseProductName().toLowerCase();
    
    if (productName.contains("postgresql")) return "POSTGRES";
    if (productName.contains("mysql") || productName.contains("mariadb")) return "MYSQL";
    if (productName.contains("sqlite")) return "SQLITE";
    if (productName.contains("microsoft") || productName.contains("sql server")) return "MSSQL";
    if (productName.contains("oracle")) return "ORACLE";
    
    return "UNKNOWN";
}

    public static void createTables(String JDBC_URL_cloud, String USERNAME, String PASSWORD) {
        createConfigurationTableCloud(JDBC_URL_cloud, USERNAME, PASSWORD);
        createRecordsTableCloud(JDBC_URL_cloud, USERNAME, PASSWORD);
    }

    private static void createConfigurationTableCloud(String JDBC_URL_cloud, String USERNAME, String PASSWORD) {
        String confTable = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");

        try (Connection conn = DriverManager.getConnection(JDBC_URL_cloud, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement()) {

            String dbType = getDatabaseType(conn);
            String idType, timeFunc, textType;

            // Dialect Mapping
            switch (dbType) {
                case "POSTGRES":
                    idType = "SERIAL PRIMARY KEY";
                    timeFunc = "TIMESTAMPTZ DEFAULT NOW()";
                    textType = "TEXT";
                    break;
                case "SQLITE":
                    idType = "INTEGER PRIMARY KEY AUTOINCREMENT";
                    timeFunc = "DATETIME DEFAULT CURRENT_TIMESTAMP";
                    textType = "TEXT";
                    break;
                case "MSSQL":
                    idType = "INT IDENTITY(1,1) PRIMARY KEY";
                    timeFunc = "DATETIME DEFAULT GETDATE()";
                    textType = "NVARCHAR(MAX)";
                    break;
                case "ORACLE":
                    idType = "NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
                    timeFunc = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                    textType = "VARCHAR2(255)";
                    break;
                default: // MySQL / MariaDB
                    idType = "INT AUTO_INCREMENT PRIMARY KEY";
                    timeFunc = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP";
                    textType = "VARCHAR(255)";
                    break;
            }

            String sql = String.format(
                            "CREATE TABLE IF NOT EXISTS %s (" +
                            " id %s," + 
                            " category %s NOT NULL," + 
                            " item_value %s NOT NULL," + 
                            " created_at %s," + 
                            " UNIQUE(category, item_value)" + 
                            ");", 
                            confTable, idType, textType, textType, timeFunc
                        );

            stmt.executeUpdate(sql);

            // Sync Local to Cloud 
            for(String cat: DataPlace.configMap.keySet()) OptionsManager.syncConfiguration(cat, OptionsManager.getCategoryItems(cat));
            
        } catch (SQLException e) {
            System.err.println("\u26A0 CRITICAL: FAILED TO CREATE CLOUD CONFIGURATION TABLE");
            e.printStackTrace();
        }
    }

    private static void createRecordsTableCloud(String JDBC_URL_cloud, String USERNAME, String PASSWORD) {
        String recTable = ConfigLoader.config.getProperty("CLOUD_TABLE");

        try (Connection conn = DriverManager.getConnection(JDBC_URL_cloud, USERNAME, PASSWORD);
            Statement stmt = conn.createStatement()) {
            
            String dbType = getDatabaseType(conn);
            String textType;
            String longTextType;

            // Dialect Mapping for Records
            switch (dbType) {
                case "POSTGRES":
                case "SQLITE":
                    textType = "TEXT";
                    longTextType = "TEXT";
                    break;
                case "MSSQL":
                    textType = "NVARCHAR(255)";
                    longTextType = "NVARCHAR(MAX)";
                    break;
                case "ORACLE":
                    textType = "VARCHAR2(255)";
                    longTextType = "CLOB"; // Use Character Large Object for JSON strings
                    break;
                default: // MYSQL / MariaDB
                    textType = "VARCHAR(255)";
                    longTextType = "TEXT";
                    break;
            }

            String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                " session_id %s PRIMARY KEY," + 
                " login_time %s NOT NULL," + 
                " logout_time %s," + 
                " usn %s NOT NULL," +
                " name %s NOT NULL," +
                " details %s" +     // This holds all dynamic attributes in jSON String{Subject, dept, sem}
                ");", 
                recTable, textType, textType, textType, textType, textType, longTextType
            );

            stmt.executeUpdate(sql);
            System.out.println("✓ Cloud Records Table initialized for " + dbType);

        } catch (SQLException e) {
            System.err.println("\u26A0 CRITICAL: FAILED TO CREATE CLOUD RECORDS TABLE");
            e.printStackTrace();
        }
    }
}

class ConfigLoader {

    // A single, static instance of Properties accessible application-wide
    static final Properties config = new Properties();

    // --- Persistence Setup (Safe Location ) ---
    private static final String APP_DIR_NAME = "DigiLogBook";
    private static final String CONFIG_FILE_NAME = "config.properties";

    protected static final Path CONFIG_DIR_PATH = Paths.get(System.getProperty("user.home"), "." + APP_DIR_NAME);
    private static final Path CONFIG_FILE_PATH = CONFIG_DIR_PATH.resolve(CONFIG_FILE_NAME);
    
    // The internal name of the template file inside the JAR
    private static final String CONFIG_TEMPLATE_NAME = "config.properties";

    static {
        File persistentConfigFile = CONFIG_FILE_PATH.toFile(); 

        System.out.println("Checking for persistent config file");

        // --- PHASE 1: Try to read the persistent (user-edited) file ---
        if (persistentConfigFile.exists()) {
            try (FileReader reader = new FileReader(persistentConfigFile)) {
                config.load(reader);
                System.out.println("Persistent configuration loaded successfully.");
            } catch (IOException e) {
                // If we found it but couldn't read it (e.g., permissions issue)
                System.err.println("Error reading existing persistent config file: " + e.getMessage());
            }
        } else {
            // --- PHASE 2: If persistent file is missing, load the default template ---
            System.out.println("Persistent file not found. Loading default template.");
            
            // USE CLASSLOADER for reading the resource from inside the JAR
            try (InputStream templateStream = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_TEMPLATE_NAME)) {
                
                if (templateStream == null) {
                    throw new FileNotFoundException("\u26A0 CRITICAL: Default template file " +  CONFIG_TEMPLATE_NAME + " not found inside the application!");
                } else {
                    config.load(templateStream);
                    System.out.println("Default configuration loaded successfully.");
                    
                    // Immediately create the persistent file based on the template content
                    saveProperties();
                    System.out.println("New persistent config file created from deafult template.");
                }

            } catch (IOException e) {
                System.err.println("Error reading template or creating new file: " + e.getMessage());
            }
        }

        // ---- PHASE 3: Initialise Database if not exist ---
        File dbFile = CONFIG_DIR_PATH.resolve(config.getProperty("JDBC_URL_local")).toFile();
        
        if (!dbFile.exists()) {
            System.out.println("First run detected: Initializing Database...");

            // Initialize the SQLite table structure
            OptionsManager.createConfigurationTableLocal(getLocalDBUrl()); 

            // Use capitalized keys to match your JComboBox headers and JSON extraction logic
            OptionsManager.saveCategoryItems("Subject", new ArrayList<>(List.of("BXLX101","BXLX102","BXXX3L3","BXXX5L5")));
            OptionsManager.saveCategoryItems("Department", new ArrayList<>(List.of("CSE (cs)","ISE (is)","AIML (ai)","DS (cd)","ECE (ec)","MECH (me)","CIVIL (cv)")));
            OptionsManager.saveCategoryItems("Batch", new ArrayList<>(List.of("I", "II")));
            OptionsManager.saveCategoryItems("Sem", new ArrayList<>(List.of("1","2","3","4","5","6","7","8")));
            
            // Refresh the memory map immediately so the UI is ready
            DataPlace.configMap = HelperFunctions.loadConfigMap(getLocalDBUrl());
        } 
    }

    /*
    Delete Application Folder created.
     */
    public static void deleteConfigFolder(Path path) {
        if (!Files.exists(path)) {
            System.out.println("No configuration folder found at: " + path);
            return;
        }
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder()) // Delete children before parents
                .map(Path::toFile)
                .forEach(File::delete);
            System.out.println("\u26A0 CRITICAL: Deleted Application Configuration Folder");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to delete Application Configuration Folder");
        }
    }

    /**
     * Saves the current state of the properties object to the config file.
     */
    public static void saveProperties() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) { // Use try-with-resources
            config.store(writer, "Configuration settings updated by user interface");
        } catch (IOException ioException) {
            ioException.printStackTrace();
            // Use a standard Swing way to notify the user of a critical failure
            JOptionPane.showMessageDialog(null, "Failed to write config file.", "File Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Centralized method to update and save all cloud DB properties.
     */
    public static void saveCloudDbConfig(String url, String user, String password, boolean verified) {
        config.setProperty("JDBC_URL_cloud", url);
        config.setProperty("JDBC_USERNAME_cloud", user);
        config.setProperty("JDBC_PASSWORD_cloud", password);
        config.setProperty("CLOUD_DB_VERIFIED", String.valueOf(verified));
        saveProperties();
    }

    public static LocalDate getLocalLastRunDate() {
        String dateStr = config.getProperty("local.auto.delete.last.run.date");
        if (dateStr == null) {
            return null;
        }
        return LocalDate.parse(dateStr);
    }

    public static String getLocalDBUrl() {
        final String DB_FILE_NAME = config.getProperty("JDBC_URL_local");

        return "jdbc:sqlite:" + CONFIG_DIR_PATH.resolve(DB_FILE_NAME).toString();
    }
    
    public static void setLocalLastRunDateToNow() throws IOException {
        config.setProperty("local.auto.delete.last.run.date", LocalDate.now().toString());
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) {
            config.store(writer, "Configuration settings updated by user interface");
        }
    }

    public static LocalDate getCloudLastRunDate() {
        String dateStr = config.getProperty("cloud.auto.delete.last.run.date");
        if (dateStr == null) {
            return null;
        }
        return LocalDate.parse(dateStr);
    }

    public static void setCloudLastRunDateToNow() throws IOException {
        config.setProperty("cloud.auto.delete.last.run.date", LocalDate.now().toString());
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) {
            config.store(writer, "Configuration settings updated by user interface");
        }
    }
    
    public static void setAutoSaveDirectory(String location) {
        config.setProperty("auto.save.records.directory", location);
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) {
            config.store(writer, "Configuration settings updated by user interface");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setAutoDeleteDuration(String property,String duration) {
        config.setProperty(property, duration);
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) {
            config.store(writer, "Configuration settings updated by user interface");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setAutoSaveFeature(String property, String isSelected) {
        config.setProperty(property, isSelected);
        try (FileWriter writer = new FileWriter(CONFIG_FILE_PATH.toFile())) {
            config.store(writer, "Configuration settings updated by user interface");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

class OptionsManager {

    public static void createConfigurationTableLocal(String JDBC_URL_local) {
        String confTable = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");

        // SQLite uses INTEGER PRIMARY KEY AUTOINCREMENT instead of SERIAL 
        // SQLite uses DATETIME instead of TIMESTAMP WITH TIME ZONE
        // SQLite uses CURRENT_TIMESTAMP instead of NOW()
        String sql = "CREATE TABLE IF NOT EXISTS " + confTable + " (" +
                    " id INTEGER PRIMARY KEY AUTOINCREMENT," + 
                    " category TEXT NOT NULL," + 
                    " item_value TEXT NOT NULL," + 
                    " created_at DATETIME DEFAULT CURRENT_TIMESTAMP," + 
                    " UNIQUE(category, item_value)" + 
                    ");";

        try (Connection conn = DriverManager.getConnection(JDBC_URL_local);
            Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
            System.out.println("✓ Local Configuration Table initialized.");
        } catch (SQLException e) {
            System.err.println("\u26A0 CRITICAL: FAILED TO CREATE LOCAL CONFIGURATION TABLE");
            e.printStackTrace();
        }
    }

    public static void createRecordsTableLocal(Connection localConn) {
        String localTable = ConfigLoader.config.getProperty("LOCAL_TABLE");
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
            for (String category : DataPlace.configMap.keySet()) {
                // unique name for each index
                String indexName = "idx_details_" + category;
                
                String indexSql = "CREATE INDEX IF NOT EXISTS " + indexName + 
                                " ON " + localTable + " (json_extract(details, '$." + category + "'));";
                
                stmt.executeUpdate(indexSql);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*
    Update OptionsData
    */
    public static List<String> getCategoryItems(String category) {
        List<String> items = new ArrayList<>();
        String confTable = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");
        
        // Use PreparedStatement to handle the quotes and prevent SQL injection automatically
        String sql = "SELECT item_value FROM " + confTable + " WHERE category = ?";
        
        try (Connection localCon = DriverManager.getConnection(ConfigLoader.getLocalDBUrl());
            PreparedStatement pstmt = localCon.prepareStatement(sql)) {
            
            pstmt.setString(1, category);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    items.add(rs.getString("item_value"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching items for category: " + category);
            e.printStackTrace();
        }
        return items;
    }    

    public static void saveCategoryItems(String category, List<String> items) {
        String confTable = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");
        try (Connection localCon = DriverManager.getConnection(ConfigLoader.getLocalDBUrl())) {
            localCon.setAutoCommit(false);

            // 1. Delete old
            try (PreparedStatement deleteStmt = localCon.prepareStatement("DELETE FROM " + confTable + " WHERE category = ?")) {
                deleteStmt.setString(1, category);
                deleteStmt.executeUpdate();
            }

            // 2. Insert new
            try (PreparedStatement localStm = localCon.prepareStatement("INSERT INTO " + confTable + " (category, item_value) VALUES (?, ?)")) {
                for (String option : items) {
                    localStm.setString(1, category);
                    localStm.setString(2, option);
                    localStm.addBatch();
                }
                localStm.executeBatch();
            }
            
            localCon.commit(); // Save local changes

            // 3. Cloud Sync
            boolean cloudVerified = Boolean.parseBoolean(ConfigLoader.config.getProperty("CLOUD_DB_VERIFIED", "false"));
            if (cloudVerified) {
                syncConfiguration(category, items);
            } else {
                System.out.println("WARNING: Cloud Config Sync failed");
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to save category items locally.");
            e.printStackTrace();
        }

        // 4. Update configMap
        DataPlace.configMap = HelperFunctions.loadConfigMap(ConfigLoader.getLocalDBUrl());
    }

    public static void syncConfiguration(String category, List<String> options) {
        String JDBC_URL_cloud = ConfigLoader.config.getProperty("JDBC_URL_cloud");
        String USERNAME_cloud = ConfigLoader.config.getProperty("JDBC_USERNAME_cloud");
	    String PASSWORD_cloud = ConfigLoader.config.getProperty("JDBC_PASSWORD_cloud");
        String conf_table = ConfigLoader.config.getProperty("CONFIGURATION_TABLE");

        try(Connection cloudCon = DriverManager.getConnection(JDBC_URL_cloud, USERNAME_cloud, PASSWORD_cloud)) {
            String deleteSQL = "DELETE FROM " + conf_table + " WHERE category = ?";
            try (PreparedStatement deleteStmt = cloudCon.prepareStatement(deleteSQL)) {
                deleteStmt.setString(1, category);
                deleteStmt.executeUpdate();
            }

            String insertStmt = "INSERT INTO " + conf_table + " (category,item_value) VALUES (? ,?)";
            PreparedStatement cloudstm = cloudCon.prepareStatement(insertStmt);
            cloudstm.setString(1, category);
            for(String option: options) {
                cloudstm.setString(2,option);
                cloudstm.addBatch();
            }
            cloudstm.executeBatch();
            System.out.println("Cloud Sync: " + category + " updated.");
        } catch (SQLException e) {
            System.err.println("Cloud Sync Failed for " + category);
            e.printStackTrace();
        }

    }
}

class ExportCsvPdf {
    private static File fileToSave;
    private static JFileChooser fileChooser;
    private ArrayList<SessionGroup> selectedGroups;

    ExportCsvPdf(String type, ArrayList<SessionGroup> selectedGroups) {

        if (selectedGroups.size() < 1) {
            JOptionPane.showMessageDialog(null, "Nothing to export.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        this.selectedGroups = selectedGroups;

        fileChooser = new JFileChooser();
        if (type.equals("CSV")) {
            fileChooser.setDialogTitle("Save as CSV");
        } else if (type.equals("PDF")) {
            fileChooser.setDialogTitle("Save as PDF");
        }

        int userSelection = fileChooser.showSaveDialog(null);

        if (userSelection != JFileChooser.APPROVE_OPTION) {
            return; // User cancelled the operation.
        }

        fileToSave = fileChooser.getSelectedFile();

        if (type.equals("CSV"))
            exportGroupedDataToCSV();
        else if (type.equals("PDF"))
            exportGroupedDataToPdf();

    }

    // This method will generate a CSV from a List<SessionGroup>.
    public void exportGroupedDataToCSV() {
        if (!fileToSave.getName().toLowerCase().endsWith(".csv")) {
            fileToSave = new File(fileToSave.getAbsolutePath() + ".csv");
        }

        try (PrintWriter pw = new PrintWriter(new FileWriter(fileToSave))) {
            // Define headers. 
            pw.print("Login Time,USN,Name,");
            for (String category : DataPlace.configMap.keySet()) {
                pw.print(category + ",");
            }
            pw.println("Logout Time,Session ID");

            for (SessionGroup group : selectedGroups) {
                // Add a line to separate groups
                // pw.println("------------------------- Group:  -------------------------");

                // Write the records for the current group

                for (SessionRecord record : group.records) {
                    pw.print(record.getLoginTime() + ",");
                    pw.print(record.getUsn() + ",");
                    pw.print(record.getName() + ",");

                    // Dynamic Categories (Subjects, Departments, etc.)
                    for (String category : DataPlace.configMap.keySet()) {
                        // Get value from map, use empty string if not found to keep CSV columns aligned
                        String val = record.attributes.getOrDefault(category, "");
                        pw.print(val + ",");
                    }

                    pw.print(record.getLogoutTime() + ",");
                    pw.print(record.getSessionId());
                    pw.println();
                }
            }

            JOptionPane.showMessageDialog(null, "Exported to: " + fileToSave.getAbsolutePath(), "Success",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error exporting file: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // This method generate a PDF from a List<SessionGroup>.
    public void exportGroupedDataToPdf() {

        if (!fileToSave.getName().toLowerCase().endsWith(".pdf")) {
            fileToSave = new File(fileToSave.getAbsolutePath() + ".pdf");
        }

        try (PDDocument document = new PDDocument()) {

            // Define fonts and colors needed for the heading and body
            final PDType1Font BOLD_FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            final int HEADING_FONT_SIZE = 14;
            final int HEADING_SPACE = 20;

            for (SessionGroup group : selectedGroups) {
                // Create a new page for the new group
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                PDPageContentStream contentStream = new PDPageContentStream(document, page);

                int yPosition = 800; // Increased to give more room at the top
                int margin = 50;
                int cellHeight = 20;
                int pageWidth = (int) page.getMediaBox().getWidth();

                // --- Draw Group Heading --

                // Define constants for the heading box (adjust these values as needed)
                final int BOX_HEIGHT = 55;
                final int BOX_WIDTH = pageWidth - 2 * margin;
                final int ROW_HEIGHT = BOX_HEIGHT / 2;
                final int HEADING_PADDING = 3;
                final Color HEADER_BOX_COLOR = new Color(240, 240, 240); // Light gray background
                final Color BORDER_COLOR = Color.DARK_GRAY;

                // Font Sizes for Hierarchy
                final float PRIORITY_FONT_SIZE = 14; // Date, Subject, Dept
                final float SECONDARY_FONT_SIZE = 12; // Slot, Sem, Batch

                // Calculate the box's top-left corner
                int boxY = yPosition - BOX_HEIGHT;
                int boxX = margin;

                // 1. Draw the box background
                contentStream.setNonStrokingColor(HEADER_BOX_COLOR);
                contentStream.addRect(boxX, boxY, BOX_WIDTH, BOX_HEIGHT);
                contentStream.fill();
                contentStream.setNonStrokingColor(Color.BLACK); // Reset for drawing text/lines

                // 2. Draw all internal dividing lines (Borders and Separators)
                contentStream.setStrokingColor(BORDER_COLOR);
                contentStream.setLineWidth(0.5f);

                // --- Horizontal Row Divider (Top Row / Bottom Row) ---
                contentStream.moveTo(boxX, boxY + ROW_HEIGHT);
                contentStream.lineTo(boxX + BOX_WIDTH, boxY + ROW_HEIGHT);

                // --- Vertical Column Dividers ---

                // Define column boundaries based on percentage width (100% total)
                // Row 1: Date (30%) | Subject (50%) | Dept (420%)
                int w1 = (int) (BOX_WIDTH * 0.30);
                int w2 = (int) (BOX_WIDTH * 0.50);
                int w3 = (int) (BOX_WIDTH * 0.20);

                // Row 2: Slot (30%) | Lab No. (30) | Sem (20%) | Batch (20%)
                int w4 = (int) (BOX_WIDTH * 0.30);
                int w5 = (int) (BOX_WIDTH * 0.30);
                int w6 = (int) (BOX_WIDTH * 0.20);
                int w7 = (int) (BOX_WIDTH * 0.20);

                // Column 1 Divider (at 30% width) - Applies to both rows
                contentStream.moveTo(boxX + w1, boxY);
                contentStream.lineTo(boxX + w1, boxY + BOX_HEIGHT);

                // Column 2 Divider (at 30% + 50% = 80% width) - Applies to top row only
                contentStream.moveTo(boxX + w1 + w2, boxY + ROW_HEIGHT);
                contentStream.lineTo(boxX + w1 + w2, boxY + BOX_HEIGHT);

                // Column 3 Divider (at 60% width) - Applies to bottom row only 
                contentStream.moveTo(boxX + w4 + w5, boxY);
                contentStream.lineTo(boxX + w4 + w5, boxY + ROW_HEIGHT);

                // Column 4 Divider (at 60% + 20% = 80% width) - Applies to bottom row only
                contentStream.moveTo(boxX + w4 + w5 + w6, boxY);
                contentStream.lineTo(boxX + w4 + w5 + w6, boxY + ROW_HEIGHT);
                contentStream.stroke();

                // 3. Draw Text (Row 1: Date | Subject | Dept) - HIGH PRIORITY
                float textX;
                float textY1 = boxY + ROW_HEIGHT + HEADING_PADDING;

                // === Column 1: Date ===
                textX = boxX + HEADING_PADDING;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, PRIORITY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.newLineAtOffset(textX, textY1);
                contentStream.showText( group.date);
                contentStream.endText();

                // === Column 2: Subject ===
                textX += w1;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, PRIORITY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.newLineAtOffset(textX+(w2-w1)/2, textY1); //Text Centering 
                contentStream.showText("Subject: " + group.attributes.getOrDefault("Subjects", "-"));
                contentStream.endText();

                // === Column 3: Dept ===
                textX += w2;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, PRIORITY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.newLineAtOffset(textX, textY1);
                contentStream.showText("Dept: " + group.attributes.getOrDefault("Departments", "-"));
                contentStream.endText();

                // 4. Draw Text (Row 2: Slot | Sem | Batch) - SECONDARY PRIORITY
                float textY2 = boxY + HEADING_PADDING;
                textX = boxX; // Reset X position

                // === Column 4: Slot ===
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, SECONDARY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.DARK_GRAY);
                contentStream.newLineAtOffset(textX + HEADING_PADDING, textY2);
                contentStream.showText(group.slot);
                contentStream.endText();

                // === Column 5: Lab Number ===
                textX += w4;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, SECONDARY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.DARK_GRAY);
                contentStream.newLineAtOffset(textX + HEADING_PADDING+ w5/5, textY2); //trying to Center
                contentStream.showText("Lab No: 314");
                contentStream.endText();

                // === Column 6: Sem ===
                textX += w5;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, SECONDARY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.DARK_GRAY);
                contentStream.newLineAtOffset(textX + HEADING_PADDING, textY2);
                contentStream.showText("Sem: " + group.attributes.getOrDefault("Semester", "-"));
                contentStream.endText();

                // === Column 6: Batch ===
                textX += w6;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, SECONDARY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.DARK_GRAY);
                contentStream.newLineAtOffset(textX + HEADING_PADDING, textY2);
                contentStream.showText("Batch: " + group.attributes.getOrDefault("Batches", "-"));
                contentStream.endText();

                // 5. Update yPosition to start the table below the heading box
                yPosition -= BOX_HEIGHT + 10; // Move down past the box, plus a small gap

                // Reset stroking color for the main table lines
                contentStream.setStrokingColor(Color.BLACK);
                contentStream.setNonStrokingColor(Color.BLACK);

                // --- Draw Table Headers for the Group ---
                List<String> headerList = new ArrayList<>();
                headerList.add("Login Time");
                headerList.add("USN");
                headerList.add("Name");

                // Add dynamic categories (Sem, Dept, etc.)
                for (String category : DataPlace.configMap.keySet()) {
                    headerList.add(category);
                }

                headerList.add("Logout Time");

                // HEADERS
                String[] headers = headerList.toArray(new String[0]);

                // Dynamic Width allocation to columns 
                float availableWidth = page.getMediaBox().getWidth() - (2 * margin);
                float[] columnWidths = new float[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    columnWidths[i] = availableWidth / headers.length; // Simple equal distribution
                }
                
                drawRow(contentStream, yPosition, margin, columnWidths, cellHeight, headers,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), Color.GRAY);
                yPosition -= cellHeight;

                // --- Draw Group's Records ---
                for (SessionRecord record : group.records) {
                    List<String> rowList = new ArrayList<>();
    
                    // Standard fields
                    rowList.add(record.getLoginTime().toLocalTime().toString()); 
                    rowList.add(record.getUsn());
                    rowList.add(record.getName());

                    // Dynamic attributes matching the header order
                    for (String category : DataPlace.configMap.keySet()) {
                        rowList.add(record.attributes.getOrDefault(category, "-"));
                    }

                    rowList.add(record.getLogoutTime().toLocalTime().toString());

                    String[] rowData = rowList.toArray(new String[0]);

                    drawRow(contentStream, yPosition, margin, columnWidths, cellHeight, rowData,
                            new PDType1Font(Standard14Fonts.FontName.HELVETICA), Color.BLACK);
                    yPosition -= cellHeight;

                    // Handle pagination within the group if needed
                    if (yPosition < margin) {
                        contentStream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        contentStream = new PDPageContentStream(document, page);
                        yPosition = (int) page.getMediaBox().getHeight() - margin;

                        // Optionally, redraw headers on the new page
                        drawRow(contentStream, yPosition, margin, columnWidths, cellHeight, headers,
                                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), Color.GRAY);
                        yPosition -= cellHeight;
                    }
                }
                contentStream.close();
            }

            // **CODE TO SAVE THE DOCUMENT**
            document.save(fileToSave);
            document.close();

            // Changed 'table' to 'null'
            JOptionPane.showMessageDialog(null, "Exported to: " + fileToSave.getAbsolutePath(), "Success",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null, "Error exporting file: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void drawRow(PDPageContentStream contentStream, int y, int margin, float[] columnWidths, int height,
            String[] cells, PDType1Font font, Color textColor) throws IOException {
        contentStream.setStrokingColor(Color.BLACK);
        float nextX = margin;

        for (int i = 0; i < cells.length; i++) {
            contentStream.setNonStrokingColor(Color.WHITE); // Cell background
            contentStream.addRect(nextX, y - height, columnWidths[i], height);
            contentStream.fill();
            contentStream.setNonStrokingColor(textColor); // Text color

            // Add cell text
            contentStream.beginText();
            contentStream.setFont(font, 10);
            contentStream.newLineAtOffset(nextX + 5, y - height + 5);
            contentStream.showText(cells[i]);
            contentStream.endText();

            // Add cell borders
            contentStream.setNonStrokingColor(Color.BLACK);
            contentStream.addRect(nextX, y - height, columnWidths[i], height);
            contentStream.stroke();
            nextX += columnWidths[i];
        }
    }

    private static int[] calculateColumnWidths(int numColumns, int availableWidth) {
        int[] widths = new int[numColumns];
        int widthPerColumn = availableWidth / numColumns;
        for (int i = 0; i < numColumns; i++) {
            widths[i] = widthPerColumn;
        }
        return widths;
    }
}

// Calendar
class DatePicker {
    private JDialog dialog;
    private Calendar currentCalendar = new GregorianCalendar();
    private JLabel monthLabel;
    private JLabel yearLabel;
    private String day = "";
    private JButton[] dayButtons = new JButton[42];
    private String selectedDate;
    private JTextField targetTextField;

    public DatePicker(JFrame parent, JTextField targetTextField) {
        this.targetTextField = targetTextField;
        dialog = new JDialog(parent, "Select Date", true);
        dialog.setResizable(false);
        dialog.setSize(400, 280);
        dialog.setLocationRelativeTo(parent);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(createControlPanel(), BorderLayout.NORTH);
        panel.add(createCalendarPanel(), BorderLayout.CENTER);
        dialog.getContentPane().add(panel);
    }

    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JPanel monthYearPanel = new JPanel(new FlowLayout());
        monthLabel = new JLabel("", JLabel.CENTER);
        monthLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        monthLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDropdownSelection("month");
            }
        });

        yearLabel = new JLabel("", JLabel.CENTER);
        yearLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        yearLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showDropdownSelection("year");
            }
        });

        monthYearPanel.add(monthLabel);
        monthYearPanel.add(yearLabel);

        JButton prevButton = new JButton("<<");
        JButton nextButton = new JButton(">>");

        prevButton.addActionListener(e -> {
            currentCalendar.add(Calendar.MONTH, -1);
            displayCalendar();
        });

        nextButton.addActionListener(e -> {
            currentCalendar.add(Calendar.MONTH, 1);
            displayCalendar();
        });

        panel.add(prevButton, BorderLayout.WEST);
        panel.add(monthYearPanel, BorderLayout.CENTER);
        panel.add(nextButton, BorderLayout.EAST);
        return panel;
    }

    private void showDropdownSelection(String type) {
        JDialog selectionDialog = new JDialog(dialog, "Select " + type, true);
        selectionDialog.setLayout(new BorderLayout());
        selectionDialog.setSize(200, 250);
        selectionDialog.setLocationRelativeTo(dialog);

        JPanel panel = new JPanel(new GridLayout(0, 1));
        JScrollPane scrollPane = new JScrollPane(panel);

        if (Objects.equals(type, "month")) {
            String[] months = { "January", "February", "March", "April", "May", "June", "July", "August", "September",
                    "October", "November", "December" };
            for (int i = 0; i < 12; i++) {
                final int monthIndex = i;
                JButton monthButton = new JButton(months[i]);
                monthButton.addActionListener(e -> {
                    currentCalendar.set(Calendar.MONTH, monthIndex);
                    displayCalendar();
                    selectionDialog.dispose();
                });
                panel.add(monthButton);
            }
        } else if (Objects.equals(type, "year")) {
            int currentYear = currentCalendar.get(Calendar.YEAR);
            DefaultListModel<String> listModel = new DefaultListModel<>();
            int startYear = currentYear - 50;
            int endYear = currentYear + 50;

            for (int year = startYear; year <= endYear; year++) {
                listModel.addElement(String.valueOf(year));
            }

            JList<String> yearList = new JList<>(listModel);
            yearList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                        boolean isSelected, boolean cellHasFocus) {
                    Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                    if (Integer.parseInt(value.toString()) == currentYear) {
                        c.setForeground(Color.BLUE);
                        c.setFont(c.getFont().deriveFont(Font.BOLD));
                    }
                    return c;
                }
            });

            yearList.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        int selectedYear = Integer.parseInt(yearList.getSelectedValue());
                        currentCalendar.set(Calendar.YEAR, selectedYear);
                        displayCalendar();
                        selectionDialog.dispose();
                    }
                }
            });
            panel.add(yearList);

            // Center the list on the current year
            yearList.ensureIndexIsVisible(listModel.indexOf(String.valueOf(currentYear)) - 5);

        }

        selectionDialog.add(scrollPane, BorderLayout.CENTER);
        selectionDialog.setVisible(true);
    }

    private JPanel createCalendarPanel() {
        JPanel panel = new JPanel(new GridLayout(7, 7, 2, 2));
        panel.setBackground(Color.WHITE);

        String[] headers = { "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat" };
        for (String header : headers) {
            JLabel label = new JLabel(header, JLabel.CENTER);
            label.setForeground(Color.RED);
            panel.add(label);
        }

        for (int i = 0; i < 42; i++) {
            dayButtons[i] = new JButton();
            dayButtons[i].setFocusPainted(false);
            dayButtons[i].setBackground(Color.LIGHT_GRAY);
            dayButtons[i].setBorderPainted(false);
            dayButtons[i].addActionListener(e -> {
                day = e.getActionCommand();
                if (!day.isEmpty()) {
                    int selectedDay = Integer.parseInt(day);
                    currentCalendar.set(Calendar.DAY_OF_MONTH, selectedDay);
                    selectedDate = new java.text.SimpleDateFormat("yyyy-MM-dd, E").format(currentCalendar.getTime());
                    targetTextField.setText(selectedDate);
                    dialog.dispose();
                }
            });
            panel.add(dayButtons[i]);
        }
        displayCalendar();
        return panel;
    }

    private void displayCalendar() {

        // Get the current date for highlighting today
        LocalDate today = LocalDate.now();
        LocalDate selected = null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Safely parse the selected date from the text field
        String textFromTextField = targetTextField.getText();
        if (textFromTextField != null && !textFromTextField.isEmpty()) {
            try {
                // Split by comma and format to ensure consistency
                String datePart = textFromTextField.split(",")[0].trim();
                selected = LocalDate.parse(datePart, formatter);
            } catch (Exception e) {
                // Handle parsing errors if the format is invalid
                selected = null;
            }
        }

        Calendar calendarForDisplay = (Calendar) currentCalendar.clone();
        calendarForDisplay.set(Calendar.DAY_OF_MONTH, 1);
        int dayOfWeek = calendarForDisplay.get(Calendar.DAY_OF_WEEK);
        int daysInMonth = calendarForDisplay.getActualMaximum(Calendar.DAY_OF_MONTH);

        for (int i = 0; i < 42; i++) {
            JButton button = dayButtons[i];
            button.setText("");
            button.setBackground(Color.WHITE); // Reset color
            button.setBorderPainted(true); // Ensure border is visible for highlighting

            if (i >= dayOfWeek - 1 && i < dayOfWeek - 1 + daysInMonth) {
                int dayOfMonth = i - (dayOfWeek - 1) + 1;
                button.setText(String.valueOf(dayOfMonth));

                LocalDate calendarDate = LocalDate.of(
                        currentCalendar.get(Calendar.YEAR),
                        currentCalendar.get(Calendar.MONTH) + 1,
                        dayOfMonth);

                // Highlight the current day
                if (calendarDate.isEqual(today)) {
                    button.setBackground(Color.BLUE);
                }

                // Highlight the selected day
                if (selected != null && calendarDate.isEqual(selected)) {
                    button.setBackground(Color.CYAN);
                }
            }
        }

        monthLabel.setText(new java.text.SimpleDateFormat("MMMM").format(currentCalendar.getTime()));
        yearLabel.setText(new java.text.SimpleDateFormat("yyyy").format(currentCalendar.getTime()));

        if (Objects.equals(
                new java.text.SimpleDateFormat("MMMM yyyy").format(currentCalendar.getTime()),
                new java.text.SimpleDateFormat("MMMM yyyy").format(new GregorianCalendar().getTime()))) {
            monthLabel.setForeground(Color.BLUE);
            yearLabel.setForeground(Color.BLUE);
        } else {
            monthLabel.setForeground(Color.BLACK);
            yearLabel.setForeground(Color.BLACK);
        }
    }

    public void showPicker() {
        dialog.setVisible(true);
    }
}
