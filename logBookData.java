import javax.swing.*;

import java.awt.*;
import java.io.File;

class DataPlace {
    JFrame jf;

    DataPlace() {
		jf = new JFrame("Data Zone");
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setSize(900, 500);
		jf.setLocationRelativeTo(null);

        createInitialView();
		jf.setVisible(true);
	}

    void createInitialView() {
        JPanel view = new JPanel(new FlowLayout());
        JButton addLogBook = new JButton("Add"); view.add(addLogBook);
        JButton viewLogBook = new JButton("View"); view.add(viewLogBook);

        addLogBook.addActionListener(e -> {
            showAddLogBookPanel();
        });
        jf.add(view);
    }

    void showAddLogBookPanel() {
        importFromUserSelection();
    }

    void importFromUserSelection() {
        JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("Select a file to import");
		fileChooser.setCurrentDirectory(new File("./") );


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
