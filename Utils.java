
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Objects;
import java.util.Properties;

import javax.swing.*;


import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

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
        
        // Ensure the application directory exists before checking for the file
        if (!persistentConfigFile.getParentFile().exists()) {
            persistentConfigFile.getParentFile().mkdirs();
        }
        
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
                    throw new FileNotFoundException("CRITICAL: Default template file " +  CONFIG_TEMPLATE_NAME + " not found inside the application!");
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

    // --- Persistence Setup (Safe Location ) ---
    private static final String OPTIONS_FILE_NAME = "optionsData.csv";

    // Absolute Path to the persistent, user-editable CSV file
    public static final Path PERSISTENT_FILE_PATH = ConfigLoader.CONFIG_DIR_PATH.resolve(OPTIONS_FILE_NAME);
    
    // Name of the read-only template inside the JAR
    private static final String OPTIONS_TEMPLATE_NAME = "optionsData.csv";

    // --- Utility to Load Template and Create Persistent File ---
    static {
        File persistentFile = PERSISTENT_FILE_PATH.toFile();
                
        // If the persistent file doesn't exist, create the directory and copy the template
        if (!persistentFile.exists()) {
            try {
                // 1. Create directory if necessary
                if (!persistentFile.getParentFile().exists()) {
                    persistentFile.getParentFile().mkdirs();
                }


                // 2. Load the template from the JAR
                try (InputStream templateStream = OptionsManager.class.getClassLoader().getResourceAsStream(OPTIONS_TEMPLATE_NAME)) {
                    
                    if (templateStream == null) {
                        throw new FileNotFoundException("CRITICAL: Default template file " + OPTIONS_TEMPLATE_NAME +" not found inside the application!");                
                    }
                    
                    // 3. Write the template stream to the new persistent file
                    try (FileOutputStream fos = new FileOutputStream(persistentFile)) {
                        templateStream.transferTo(fos);
                    }
                    System.out.println("New persistent options file created from deafult template.");

                }
            } catch (IOException e) {
                System.err.println("CRITICAL: Failed to initialize persistent options file.");
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Critical error setting up options file. Check logs.", "Setup Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}

class ExportCsvPdf {
    private static File fileToSave;
    private static JFileChooser fileChooser;
    private ArrayList<SessionGroup> selectedGroups;

    ExportCsvPdf(String type, ArrayList<SessionGroup> selectedGroups) {

        if (selectedGroups.size() < 1) {
            // JOptionPane.showMessageDialog(table, "The table is empty and cannot be
            // exported.", "Warning", JOptionPane.WARNING_MESSAGE);
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
            // Define headers. Assuming the same columns as your JTable
            String[] headers = new String[] { "Login Time", "USN", "Name", "Sem", "Dept", "Subject", "Batch",
                    "Logout Time", "Session ID" };

            // Write the main header row
            for (int i = 0; i < headers.length; i++) {
                pw.print(headers[i]);
                if (i < headers.length - 1)
                    pw.print(",");
            }
            pw.println();

            for (SessionGroup group : selectedGroups) {
                // Add a line to separate groups
                // pw.println("------------------------- Group:  -------------------------");

                // Write the records for the current group
                for (SessionRecord record : group.records) {
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

                // --- FIXED COLUMN COUNT ---
                // 8 columns: Login Time, USN, Name, Sem, Dept, Subject, Batch, Logout Time
                int colCount = 8;

                int pageWidth = (int) page.getMediaBox().getWidth();
                // The column widths calculation now uses the fixed count (8)
                int[] columnWidths = calculateColumnWidths(colCount, pageWidth - 2 * margin);

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
                contentStream.showText("Subject: " + group.sub);
                contentStream.endText();

                // === Column 3: Dept ===
                textX += w2;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, PRIORITY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.BLACK);
                contentStream.newLineAtOffset(textX, textY1);
                contentStream.showText("Dept: " + group.dept);
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
                contentStream.showText("Sem: " + group.sem);
                contentStream.endText();

                // === Column 6: Batch ===
                textX += w6;
                contentStream.beginText();
                contentStream.setFont(BOLD_FONT, SECONDARY_FONT_SIZE);
                contentStream.setNonStrokingColor(Color.DARK_GRAY);
                contentStream.newLineAtOffset(textX + HEADING_PADDING, textY2);
                contentStream.showText("Batch: " + group.batch);
                contentStream.endText();

                // 5. Update yPosition to start the table below the heading box
                yPosition -= BOX_HEIGHT + 10; // Move down past the box, plus a small gap

                // Reset stroking color for the main table lines
                contentStream.setStrokingColor(Color.BLACK);
                contentStream.setNonStrokingColor(Color.BLACK);

                // --- Draw Table Headers for the Group ---
                String[] headers = new String[] { "Login Time", "USN", "Name", "Sem", "Dept", "Subject", "Batch",
                        "Logout Time" };
                drawRow(contentStream, yPosition, margin, columnWidths, cellHeight, headers,
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), Color.GRAY);
                yPosition -= cellHeight;

                // --- Draw Group's Records ---
                for (SessionRecord record : group.records) {
                    String[] rowData = new String[] {
                            record.getLoginTime().toLocalTime().toString(), record.getUsn(), record.getName(),
                            record.getSem(), record.getDept(), record.getSub(),
                            record.getBatch(), record.getLogoutTime().toLocalTime().toString()
                    };
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

    private static void drawRow(PDPageContentStream contentStream, int y, int margin, int[] columnWidths, int height,
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