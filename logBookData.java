import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;
import java.io.File;
import java.sql.*;

class DataPlace {
    JFrame jf;
    JPanel view;
    JPanel mainContent;

    private final String JDBC_URL_cloud = System.getenv("JDBC_URL_cloud");
    private final String JDBC_URL_local = System.getenv("JDBC_URL_local"); // for now sqlite
	private final String USERNAME = System.getenv("JDBC_USERNAME_local");
	private final String PASSWORD = System.getenv("JDBC_PASSWORD_local");

    void syncDatabases() {
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
	

    DataPlace() {
		jf = new JFrame("Data Zone");
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setSize(900, 500);
		jf.setLocationRelativeTo(null);

        createInitialView();
		jf.setVisible(true);
	}

    void createInitialView() {
        view = new JPanel(new FlowLayout());
        JButton addLogBook = new JButton("Add"); view.add(addLogBook);
        JButton viewLogBook = new JButton("View"); view.add(viewLogBook);

        addLogBook.addActionListener(e -> {
            showAddLogBookPanel();
        });

        viewLogBook.addActionListener(e -> {
            showViewLogBookPanel();
        });
        jf.add(view);
    }

    void showAddLogBookPanel() {
        importFromUserSelection();
        showViewLogBookPanel();
    }

    void showViewLogBookPanel() {
		mainContent = new JPanel(new BorderLayout(12, 12));

		mainContent.add(createOptionsPanel(), BorderLayout.NORTH);
        syncDatabases();

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

        String[] labSubjects = {"Subjects","Sub1","Sub2","Sub3","Sub4","Others"};
        JComboBox sub = new JComboBox<>(labSubjects); 
        optionsPanel.add(sub);

        String[] departments = {"Departments","Dept1","Dept2","Dept3","Dept4"};
        JComboBox department = new JComboBox<>(departments); 
        optionsPanel.add(department);

		String[] batches = {"Batches","I","II"};
        JComboBox batch = new JComboBox<>(batches); 
        optionsPanel.add(batch);

		String[] sems = {"Semesters", "1", "2", "3", "4", "5", "6", "7", "8"};
        JComboBox sem = new JComboBox<>(sems); 
        optionsPanel.add(sem);

		backButton.addActionListener(e -> {
			jf.remove(mainContent);
			createInitialView();
			jf.revalidate();
			jf.repaint();
        });

		refresh.addActionListener(e -> {
			syncDatabases();;
		});

        return optionsPanel;
    }

    void importFromUserSelection() {
        JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Select a CSV file to import");
		fileChooser.setCurrentDirectory(new File("./") );

        //filter to show only folders/CSV files in current directory
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv"); 
		fileChooser.setFileFilter(filter);

		int userSelection = fileChooser.showOpenDialog(null); 
        if (userSelection == JFileChooser.APPROVE_OPTION) {

			File selectedFile = fileChooser.getSelectedFile();

            JOptionPane.showMessageDialog(null, "Successfully imported data from " + selectedFile.getName(),
						"Import Complete", JOptionPane.INFORMATION_MESSAGE);
        }

    }
}

public class logBookData {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new DataPlace();
            }
        });
    }
}
