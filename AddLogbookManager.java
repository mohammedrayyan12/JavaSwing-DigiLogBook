import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;



public class AddLogbookManager implements AutoCloseable {

	private static final String JDBC_URL = "jdbc:sqlite::memory:";
	final Connection connection;

	public AddLogbookManager() throws SQLException {
		this.connection = DriverManager.getConnection(JDBC_URL);
		createTable(); // Ensure the table exists when the manager is created
	}

	// create in-memory database
	private void createTable() throws SQLException {
		String createTableSql = "CREATE TABLE temp (login_time TEXT, logout_time TEXT, name TEXT, usn TEXT, sem TEXT, dept TEXT, sub TEXT, batch TEXT, session_id TEXT PRIMARY KEY)";
		try (PreparedStatement createTablePstmt = this.connection.prepareStatement(createTableSql)) {
			createTablePstmt.executeUpdate();
		}
	}

	// Method to import a CSV into the in-memory SQLite table
	public void importCsvToDatabase(String csvFilePath) throws IOException, SQLException {
		String insertSql = "INSERT or REPLACE INTO temp (login_time, usn, name, sem, dept, sub, batch, logout_time, session_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (PreparedStatement pstmt = this.connection.prepareStatement(insertSql);
				BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {

			String line;
			reader.readLine(); // Skip header line
			this.connection.setAutoCommit(false); // Begin transaction for speed

			while ((line = reader.readLine()) != null) {
				String[] values = line.split(",");
				pstmt.setString(1, values[0]);
				pstmt.setString(2, values[1]);
				pstmt.setString(3, values[2]);
				pstmt.setString(4, values[3]);
				pstmt.setString(5, values[4]);
				pstmt.setString(6, values[5]);
				pstmt.setString(7, values[6]);
				pstmt.setString(8, values[7]);
                pstmt.setString(9, values[8]);
				pstmt.addBatch();
			}
			pstmt.executeBatch();
			this.connection.commit(); // Commit all insertions
		} finally {
			this.connection.setAutoCommit(true);
		}
	}

	// Implement AutoCloseable to ensure the connection is closed, in-memort
	// database deletion
	@Override
	public void close() throws SQLException {
		if (this.connection != null && !this.connection.isClosed()) {
			this.connection.close();
			System.out.println("In-memory database connection closed. Data is deleted.");
		}
	}
}