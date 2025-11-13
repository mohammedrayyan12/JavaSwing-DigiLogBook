
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import javax.swing.*;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Objects;

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