package mica.gui;

import fr.igred.omero.Client;
import fr.igred.omero.exception.ServiceException;

import javax.swing.*;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.concurrent.ExecutionException;

import static javax.swing.JOptionPane.showMessageDialog;


public class ConnectDialog extends JDialog implements ActionListener {

	private final transient Client client;

	private final JTextField hostField = new JTextField("bioimage.france-bioinformatique.fr");
	private final JFormattedTextField portField = new JFormattedTextField(NumberFormat
																				  .getIntegerInstance());
	private final JTextField userField = new JTextField("");
	private final JPasswordField passwordField = new JPasswordField("");
	private final JButton login = new JButton("Login");
	private final JButton cancel = new JButton("Cancel");
	private boolean cancelled;


	public ConnectDialog(Client client) {
		super();
		this.setModal(true);
		this.client = client;
		this.setTitle("Connection to OMERO");
		this.setSize(350, 200);
		this.setLocationRelativeTo(null); // center the window

		Container cp = this.getContentPane();
		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));

		JPanel panelInfo = new JPanel();
		panelInfo.setLayout(new BoxLayout(panelInfo, BoxLayout.LINE_AXIS));

		JPanel panelInfo1 = new JPanel();
		panelInfo1.setLayout(new GridLayout(4, 1, 0, 3));

		JPanel panelInfo2 = new JPanel();
		panelInfo2.setLayout(new GridLayout(4, 1, 0, 3));
		panelInfo1.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));
		panelInfo2.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));

		JLabel hostLabel = new JLabel("Host:");
		panelInfo1.add(hostLabel);
		panelInfo2.add(hostField);

		JLabel portLabel = new JLabel("Port:");
		panelInfo1.add(portLabel);
		panelInfo2.add(portField);
		portField.setValue(4064L);

		JLabel userLabel = new JLabel("User:");
		panelInfo1.add(userLabel);
		panelInfo2.add(userField);

		JLabel passwdLabel = new JLabel("Password:");
		panelInfo1.add(passwdLabel);
		panelInfo2.add(passwordField);

		JPanel buttons = new JPanel();
		buttons.add(cancel);
		buttons.add(login);
		login.addActionListener(this);
		cancel.addActionListener(this);

		this.getRootPane().setDefaultButton(login);
		panelInfo.add(panelInfo1);
		panelInfo.add(panelInfo2);
		cp.add(panelInfo);
		cp.add(buttons);
		this.setVisible(true);
	}


	/**
	 * Invoked when an action occurs.
	 *
	 * @param e the event to be processed
	 */
	@Override
	public void actionPerformed(ActionEvent e) {
		Object source = e.getSource();
		if (source == login) {
			cancelled = false;
			String host = this.hostField.getText();
			Long port = (Long) this.portField.getValue();
			String username = this.userField.getText();
			char[] password = this.passwordField.getPassword();
			try {
				client.connect(host, port.intValue(), username, password);
				dispose();
			} catch (ExecutionException | ServiceException e1) {
				String errorValue = e1.getCause().getMessage();
				String message = e1.getCause().getMessage();
				if (errorValue.equals("Login credentials not valid")) {
					message = "Login credentials not valid";
				} else if (errorValue
						.equals("Can't resolve hostname " + host)) {
					message = "Hostname not valid";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			} catch (RuntimeException e1) {
				String errorValue = e1.getMessage();
				String message = e1.getMessage();
				if (errorValue.equals("Obtained null object proxy")) {
					message = "Port not valid or no internet access";
				}
				showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		} else if (source == cancel) {
			cancelled = true;
			dispose();
		}
	}


	public boolean wasCancelled() {
		return cancelled;
	}

}