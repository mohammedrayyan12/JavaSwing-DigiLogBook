import javax.swing.*;
import java.awt.*;

class DataPlace {
    	DataPlace() {
		JFrame jf = new JFrame("Data Zone");
		jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		jf.setSize(900, 500);
		jf.setLocationRelativeTo(null);
		jf.setVisible(true);
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
