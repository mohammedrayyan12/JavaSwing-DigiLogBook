import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import java.util.ArrayList;
import java.util.List;
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
        File saveFile = new File(ConfigLoader.config.getProperty("auto.save.records.directory"), fileName);

    
        try (
            Connection conn = DriverManager.getConnection(ConfigLoader.config.getProperty("JDBC_URL_local"));
            PreparedStatement preparedStatement = conn.prepareStatement(showQuery);
            PreparedStatement deleteStatement = conn.prepareStatement(deleteQuery);
            )  {
            
            // Disable auto-commit for transaction safety
            conn.setAutoCommit(false); 
            ResultSet resultSet = preparedStatement.executeQuery();

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
            
        
            // Define headers. Assuming the same columns as your JTable
            String[] headers = new String[] { "Login Time", "USN", "Name", "Sem", "Dept", "Subject", "Batch",
                    "Logout Time", "Session ID" };
                    
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

            // Write the main header row
            for (int i = 0; i < headers.length; i++) {
                pw.print(headers[i]);
                if (i < headers.length - 1)
                    pw.print(",");
            }
            pw.println();
    
            for (SessionRecord record : records) {
                pw.print(record.getLoginTime() + ",");
                pw.print(record.getUsn() + ",");
                pw.print(record.getName() + ",");
                pw.print(record.getSem() + ",");
                pw.print(record.getDept() + ",");
                pw.print(record.getSub() + ",");
                pw.print(record.getBatch() + ",");
                pw.print(record.getLogoutTime() + ",");
                pw.print(record.getSessionId());
                pw.println();
            }

            //close writer
            pw.close();

            //delete the DB ( //UNCOMMET WHILE RUNNING )
            // deleteStatement.executeQuery(); 

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

            //delete the DB ( UNCOMMENT WHILE RUNNING )
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
                System.out.println("Scheduled auto-delete running...");

                //Cloud Cleaning
                performCloudAutoDeleteAndSync(null);

                //Local Cleaning
                performLocalAutoDeleteAndExport(null); 
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
