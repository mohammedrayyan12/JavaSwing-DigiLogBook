import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import java.awt.*;
import java.io.File;

class DataPlace {
    JFrame jf;
    JPanel view;
    JPanel mainContent;

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

		backButton.addActionListener(e -> {
			jf.remove(mainContent);
			createInitialView();
			jf.revalidate();
			jf.repaint();
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
