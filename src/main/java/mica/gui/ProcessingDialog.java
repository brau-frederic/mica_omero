package mica.gui;

import javax.swing.*;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class ProcessingDialog extends JFrame {
	private final JLabel progLabel = new JLabel("", SwingConstants.CENTER);
	private final JLabel stateLabel = new JLabel("", SwingConstants.CENTER);
	private final JButton ok = new JButton("OK");

	public ProcessingDialog() {
		this.setTitle("Progression");
		this.setLocationRelativeTo(null);
		this.setSize(300, 200);

		Font warnfont = new Font("Arial", Font.PLAIN, 12);
		Font progfont = new Font("Arial", Font.BOLD, 12);
		JLabel warnLabel = new JLabel("", SwingConstants.CENTER);
		warnLabel
				.setText("<html> <body style='text-align:center;'> Warning: <br>Image processing can take time <br>depending on your network rate </body> </html>");
		warnLabel.setFont(warnfont);
		progLabel.setFont(progfont);
		stateLabel.setFont(progfont);

		Container cp2 = this.getContentPane();
		cp2.setLayout(new BoxLayout(cp2, BoxLayout.PAGE_AXIS));
		JPanel panel0 = new JPanel();
		panel0.add(warnLabel);
		JPanel panel1 = new JPanel();
		panel1.add(progLabel);
		JPanel panel2 = new JPanel();
		panel2.add(stateLabel);
		JPanel panel3 = new JPanel();
		panel3.add(ok);
		ok.setEnabled(false);
		ok.addActionListener(new ProgressListener());
		cp2.add(panel0);
		cp2.add(panel1);
		cp2.add(panel2);
		cp2.add(panel3);
		this.setVisible(true);
	}


	public void setProg(String text) {
		progLabel.setText(text);
	}


	public void setState(String text) {
		stateLabel.setText(text);
	}


	public void setButtonState(boolean state) {
		ok.setEnabled(state);
	}

	class ProgressListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			ProcessingDialog.this.dispose();
		}

	}

}
