package mica;

import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.model.ExperimenterData;
import omero.log.SimpleLogger;

import javax.swing.*;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;


public class Connexion extends JFrame {
	private final Container cp = this.getContentPane();
	private final JPanel panelInfo = new JPanel();
	private final JPanel panelInfo1 = new JPanel();
	private final JPanel panelInfo2 = new JPanel();
	private final JPanel panelBtn = new JPanel();
	private final JLabel port_label = new JLabel("PORT:");
	private final JFormattedTextField port = new JFormattedTextField(NumberFormat.getIntegerInstance());
	private int PORT;
	private final JLabel host_label = new JLabel("HOST:");
	private final JTextField host = new JTextField("bioimage.france-bioinformatique.fr");
	private String HOST = "";
	private final JLabel user_label = new JLabel("User:");
	private final JTextField user = new JTextField("");
	private String User = "";
	private final JLabel passwd_label = new JLabel("Password:");
	private final JPasswordField passwd = new JPasswordField("");
	private String Password = "";
	private final JButton Valider = new JButton("Login");


	public Connexion() {
		///
		super("Connection to Omero");
		this.setSize(350, 200);
		//this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null); // center the window
		///
		cp.setLayout(new BoxLayout(cp, BoxLayout.PAGE_AXIS));
		panelInfo.setLayout(new BoxLayout(panelInfo, BoxLayout.LINE_AXIS));
		panelInfo1.setLayout(new GridLayout(0, 1, 0, 3));  // 0: as many rows as necessary
		panelInfo2.setLayout(new GridLayout(0, 1, 0, 3));
		panelInfo1.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));
		panelInfo2.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));
		panelInfo1.add(host_label);
		panelInfo2.add(host);
		panelInfo1.add(port_label);
		panelInfo2.add(port);
		port.setValue(4064);
		panelInfo1.add(user_label);
		panelInfo2.add(user);
		panelInfo1.add(passwd_label);
		panelInfo2.add(passwd);
		panelBtn.add(Valider);
		Valider.addActionListener(new BoutonValiderCoListener());
		this.getRootPane().setDefaultButton(Valider);
		panelInfo.add(panelInfo1);
		panelInfo.add(panelInfo2);
		cp.add(panelInfo);
		cp.add(panelBtn);
		this.setVisible(true);
	}


	// Fonction équivalente (de la classe Client) : client.connect(hst, prt, usr, psw);
	ArrayList<Object> connect_to_omero(String hst, int prt, String usr, String psw)
	throws DSOutOfServiceException {
		LoginCredentials credentials = new LoginCredentials();
		credentials.getServer().setHost(hst.trim());
		credentials.getServer().setPort(prt);
		credentials.getUser().setUsername(usr.trim());
		credentials.getUser().setPassword(psw.trim());
		SimpleLogger simpleLogger = new SimpleLogger();
		Gateway gateway = new Gateway(simpleLogger);
		ExperimenterData experimenter = gateway.connect(credentials);
		ArrayList<Object> tab = new ArrayList<>(Arrays.asList(gateway, experimenter, credentials));
		return tab;
	}


	class BoutonValiderCoListener implements ActionListener /*, ActionListener */ {
		@Override
		public void actionPerformed(ActionEvent arg0) {
			HOST = String.valueOf(host.getText());
			PORT = (int) port.getValue();
			User = user.getText();
			Password = passwd.getText();
			try {
				// Les lignes 108 à 111 peuvent être remplacées par :
                /* Client client = new Client();
                client.connect(HOST, PORT, User, Password); */
				// Mais il faut aussi mettre à jour les exceptions.
				ArrayList<Object> conn = connect_to_omero(HOST, PORT, User, Password);
				Gateway gate = (Gateway) conn.get(0);
				ExperimenterData exp = (ExperimenterData) conn.get(1);
				LoginCredentials cred = (LoginCredentials) conn.get(2);
				Connexion.this.dispose();
				new Getinfos(gate, exp, cred, User);
			} catch (DSOutOfServiceException | omero.ClientError e1) { //DSOutOfServiceException e1 //omero.ClientError
				String errorValue = e1.getMessage();
				String message;
				if (errorValue.equals("Login credentials not valid")) {
					message = "Login credentials not valid";
				} else if (errorValue.equals("Can't resolve hostname " + HOST)) {
					message = "Hostname not valid";
				} else if (errorValue.equals("Obtained null object proxy")) {
					message = "Port not valid or no internet access";
				} else {
					message = e1.getMessage();
				}
				JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}

	}

}