import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

class autoDelete {
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static void performLocalAutoDeleteAndExport(JFrame parentFrame) {
        LocalDate lastRun = ConfigLoader.getLocalLastRunDate();
        LocalDate now = LocalDate.now();
        String durationLocal = ConfigLoader.config.getProperty("local.auto.delete.duration");

        if (lastRun != null && ChronoUnit.MONTHS.between(lastRun, now) < Integer.parseInt(durationLocal)) {
            // It has already ran within duration, skip it.
            System.out.println("Already done (LOCAL DB): " + (Integer.parseInt(durationLocal) - ChronoUnit.WEEKS.between(lastRun, now)) + " Month Left");

            return; 
        }
        
        String showQuery = "Select * from " + ConfigLoader.config.getProperty("LOCAL_TABLE");
        String deleteQuery = "Delete from " + ConfigLoader.config.getProperty("LOCAL_TABLE"); 

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        String fileName = "Session_Records_" + now.format(formatter) + ".csv";
    
        // target file 
        String AUTOSAVE_DIR_PATH = ConfigLoader.config.getProperty("auto.save.records.directory");
        if (AUTOSAVE_DIR_PATH.equals("autoSaved_Session_Records"))
            AUTOSAVE_DIR_PATH = Paths.get(System.getProperty("user.home"), "autoSaved_Session_Records").toString();

        File saveFile = new File(AUTOSAVE_DIR_PATH, fileName);

        try (
            Connection conn = DriverManager.getConnection(ConfigLoader.getLocalDBUrl());
            )  {

            // Setup local database table if it doesn't exist
			try (Statement stmt = conn.createStatement()) {
				stmt.executeUpdate("CREATE TABLE IF NOT EXISTS " +  ConfigLoader.config.getProperty("LOCAL_TABLE") + " (" +
						"session_id TEXT PRIMARY KEY, " +
						"login_time TEXT, " +
						"logout_time TEXT, " +
						"usn TEXT, " +
						"name TEXT, " +
						"details TEXT" +
						");");
			}

            
            // Disable auto-commit for transaction safety
            conn.setAutoCommit(false);

            List<SessionRecord> records = new ArrayList<>();

            // --- EXPORT TO CSV
            try (
                PreparedStatement preparedStatement = conn.prepareStatement(showQuery); 
            ) {

                ResultSet resultSet = preparedStatement.executeQuery();

                while (resultSet.next()) {
                    String jsonDetails = resultSet.getString("details");
    
					
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
                
            }
            if (records.isEmpty()) {
                ConfigLoader.setLocalLastRunDateToNow(); 
                System.out.println("Local DB auto-delete and export complete.");
                return;
            }

                    
            // Ensure the directory exists
            File parentDir = saveFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    System.err.println("ERROR: Failed to create directory: " + parentDir.getAbsolutePath());
                    // Exit the method if the directory can't be created
                    return; 
                }
            }

            PrintWriter pw = new PrintWriter(new FileWriter(saveFile));

            // Define headers.
            pw.print("Login Time,USN,Name,");
            for (String category : DataPlace.configMap.keySet()) {
                pw.print(category + ",");
            }
            pw.println("Logout Time,Session ID");
    
            for (SessionRecord record : records) {
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

            //close writer
            pw.close();
            // ----

            // --- DELETION --
            try (PreparedStatement deleteStatement = conn.prepareStatement(deleteQuery); ) {

                boolean skipDelete = Boolean.parseBoolean(
                    ConfigLoader.config.getProperty("testing.skip.delete", "false")
                );
                
                if (skipDelete) {
                    System.out.println("\u26A0 TESTING MODE: Skipping delete operation");
                    System.out.println("   (Set testing.skip.delete=false to enable deletion)");
                } else {
                    int deletedRows = deleteStatement.executeUpdate();
                    System.out.println("✓ Deleted " + deletedRows + " records from LOCAL DB");
                }

            }
            // ---

            conn.commit(); // Commit the changes
                
            
            //Record that the task ran successfully in the config file
            ConfigLoader.setAutoSaveDirectory(parentDir.getAbsolutePath());
            ConfigLoader.setLocalLastRunDateToNow(); 

            System.out.println("Local DB auto-delete and export complete.");

        } catch (IOException| SQLException e) {
            e.printStackTrace(); 
            System.out.println("Failed to Auto Delete and Export Local DB");
        }
    }

    private static void performCloudAutoDeleteAndSync(JFrame parentFrame) {
        LocalDate lastRun = ConfigLoader.getCloudLastRunDate();
        LocalDate now = LocalDate.now();
        String durationCloud = ConfigLoader.config.getProperty("cloud.auto.delete.duration");
 
        if (lastRun != null && ChronoUnit.WEEKS.between(lastRun, now) < Integer.parseInt(durationCloud)) {
            // It has already ran within duration, skip it.
            System.out.println("Already done (CLOUD DB): " + (Integer.parseInt(durationCloud) - ChronoUnit.WEEKS.between(lastRun, now)) + " Week Left");
            return; 
        }

        String deleteQuery = "Delete from " + ConfigLoader.config.getProperty("CLOUD_TABLE"); 

        try(
            Connection conn = DriverManager.getConnection(
                ConfigLoader.config.getProperty("JDBC_URL_cloud"),
                ConfigLoader.config.getProperty("JDBC_USERNAME_cloud"),
                ConfigLoader.config.getProperty("JDBC_PASSWORD_cloud")
                );
            PreparedStatement deleteStatement = conn.prepareStatement(deleteQuery);
            ) {

            // Sync Databases 
            DataPlace.syncDatabases();

            boolean skipDelete = Boolean.parseBoolean(
                ConfigLoader.config.getProperty("testing.skip.delete", "false")
            );
            
            if (skipDelete) {
                System.out.println("\u26A0 TESTING MODE: Skipping cloud delete operation");
                System.out.println("   (Set testing.skip.delete=false to enable deletion)");
            } else {
                if (lastRun != null) { // Don't delete on first run
                    int deletedRows = deleteStatement.executeUpdate();
                    System.out.println("✓ Deleted " + deletedRows + " records from CLOUD DB");
                }
            }
            //delete the DB ( UNCOMMENT WHILE RUNNING ) // does the same as above else statement
            // if (lastRun != null) deleteStatement.executeUpdate();  //condition for not deleting DB the first time application runs

            // Record it in config file
            ConfigLoader.setCloudLastRunDateToNow();

            System.out.println("Cloud DB auto-delete and sync complete.");

        } catch (SQLException| IOException e) {
            e.printStackTrace();
            System.out.println("Failed to Auto Delete and Sync Cloud DB");
        }
    }

    public static void scheduleAutoDeleteTask() {
        final Runnable autoDeleteTask = () -> {
            // Because this runs on a background thread, we must use SwingUtilities.invokeLater
            // to interact with UI components like JDialog or JOptionPane
            SwingUtilities.invokeLater(() -> {
                if (Boolean.parseBoolean(ConfigLoader.config.getProperty("auto.save"))) {
                    System.out.println("Scheduled auto-delete running...");

                    //Cloud Cleaning
                    performCloudAutoDeleteAndSync(null);

                    //Local Cleaning
                    performLocalAutoDeleteAndExport(null); 
                } else {
                    System.out.println("auto-delete is disabled");
                }
            });
        };

        scheduler.scheduleAtFixedRate(
            autoDeleteTask, 
            0, 
            1, 
            TimeUnit.DAYS // Check daily
        );
    }

}
