import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.util.Arrays;
import java.util.List;



public class AddLogbookManager implements AutoCloseable {

	private static final String JDBC_URL = "jdbc:sqlite::memory:";
	final Connection connection;

	public AddLogbookManager() throws SQLException {
		this.connection = DriverManager.getConnection(JDBC_URL);
		createTable(); // Ensure the table exists when the manager is created
	}

	// create in-memory database
	private void createTable() throws SQLException {
		// Match the order of your INSERT statement for clarity
		String createTableSql = "CREATE TABLE temp (login_time TEXT, usn TEXT, name TEXT, details TEXT, logout_time TEXT, session_id TEXT PRIMARY KEY)";
		try (Statement stmt = this.connection.createStatement()) {
			stmt.executeUpdate(createTableSql);

			// Add the Index for each category (Fast seaching => Binary Search)
			for (String category : DataPlace.configMap.keySet()) {
				// unique name for each index
				String indexName = "idx_details_" + category;
				
				String indexSql = "CREATE INDEX IF NOT EXISTS " + indexName + 
								" ON temp (json_extract(details, '$." + category + "'));";
				
				stmt.executeUpdate(indexSql);
			}
		}
	}

	// Method to import a CSV into the in-memory SQLite table
	public void importCsvToDatabase(String csvFilePath) throws IOException, SQLException {
		String insertSql = "INSERT or REPLACE INTO temp (login_time, usn, name, details, logout_time, session_id) VALUES (?, ?, ?, ?, ?, ?)";

		try (PreparedStatement pstmt = this.connection.prepareStatement(insertSql);
				BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {

			// Read the header and map column names to indices
			String headerLine = reader.readLine(); 
			List<String> headers = Arrays.asList(headerLine.split(","));

			this.connection.setAutoCommit(false); // Begin transaction

			String line;
			while ((line = reader.readLine()) != null) {
				String[] values = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
				
				pstmt.setString(1, values[0]); // login_time
				pstmt.setString(2, values[1]); // usn
				pstmt.setString(3, values[2]); // name

				// DYNAMIC JSON BUILDING
				StringBuilder json = new StringBuilder("{");
				// We start at index 3 and stop before the last two (logout and session)
				for (int i = 3; i < values.length - 2; i++) {
					String key = headers.get(i).trim();
					String val = values[i].trim().replace("\"", "\\\""); // Escape quotes
					
					json.append("\"").append(key).append("\":\"").append(val).append("\"");
					if (i < values.length - 3) json.append(",");
				}
				json.append("}");

				pstmt.setString(4, json.toString()); // details
				pstmt.setString(5, values[values.length - 2]); // logout_time
				pstmt.setString(6, values[values.length - 1]); // session_id
				
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
