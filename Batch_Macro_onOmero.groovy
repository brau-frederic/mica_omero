// Script Groovy to batch your local macro Fiji or ImageJ on a dataset of images in Omero
// Connect to Omero, choose your group, project, dataset 
// Execute your macro which is localy on your PC for all the images contained in the dataset
// It imports your image localy and treats it with the macro.
// This macro needs to send an image of the results an can give a table of results that could be linked to the image in Omero
// Maxence Delannoy, Manon Carvalho, Clemence Belle GB5 students from Polytech Nice Sophia. 
// Mentored project by C. Rovere and F. Brau (brau@ipmc.cnrs.fr) January 2020 
// Script done from the Omero team groovy files

import java.util.ArrayList
import java.lang.StringBuffer
import java.nio.ByteBuffer
import java.io.File
import java.io.FileInputStream
import java.io.PrintWriter

import java.nio.file.Path
import java.nio.file.Files

import org.apache.commons.io.FilenameUtils

// OMERO Dependencies
import omero.gateway.Gateway
import omero.gateway.LoginCredentials
import omero.gateway.SecurityContext
import omero.gateway.facility.BrowseFacility
import omero.gateway.facility.DataManagerFacility
import omero.gateway.facility.ROIFacility
import omero.gateway.facility.TablesFacility
import omero.gateway.exception.DSOutOfServiceException
import omero.log.SimpleLogger
import omero.model.ChecksumAlgorithmI
import omero.model.FileAnnotationI
import omero.model.OriginalFileI
import omero.model.enums.ChecksumAlgorithmSHA1160

import static omero.rtypes.rlong
import static omero.rtypes.rstring

import omero.gateway.model.DataObject
import omero.gateway.model.DatasetData
import omero.gateway.model.ProjectData
import omero.gateway.model.GroupData
import omero.gateway.model.FileAnnotationData
import omero.gateway.model.ImageData
import omero.gateway.model.ExperimenterData
import omero.gateway.model.TableData
import omero.gateway.model.TableDataColumn
import omero.model.DatasetI
import omero.model.ProjectI

import org.openmicroscopy.shoola.util.roi.io.ROIReader

import loci.formats.FormatTools
import loci.formats.ImageTools
import loci.common.DataTools

import ome.formats.importer.ImportConfig
import ome.formats.OMEROMetadataStoreClient
import ome.formats.importer.OMEROWrapper
import ome.formats.importer.ImportLibrary 
//import ome.formats.importer.util.ErrorHandler
import ome.formats.importer.cli.ErrorHandler
import ome.formats.importer.cli.LoggingImportMonitor
import ome.formats.importer.ImportCandidates
import loci.formats.in.DefaultMetadataOptions
import loci.formats.in.MetadataLevel
import loci.plugins.in.ImporterOptions
import loci.plugins.LociImporter
import loci.plugins.out.Exporter
import loci.plugins.LociExporter

import omero.model.ChecksumAlgorithm
import ome.model.core.OriginalFile
import omero.api.RawFileStorePrx
import ome.model.annotations.ImageAnnotationLink
import omero.model.ImageAnnotationLinkI

import ij.IJ
import ij.ImagePlus
import ij.ImageStack
import ij.process.ByteProcessor
import ij.process.ShortProcessor
import ij.plugin.frame.RoiManager
import ij.measure.ResultsTable

// affichage
import java.awt.*;
import javax.swing.*;
import java.text.NumberFormat;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;




public class Connexion extends JFrame {
	private Container cp = this.getContentPane();
	private JPanel panelInfo = new JPanel();
	private JPanel panelInfo1 = new JPanel();
	private JPanel panelInfo2 = new JPanel();
	private JPanel panelBtn = new JPanel();
	private JLabel port_label = new JLabel("PORT:");
    private JFormattedTextField port = new JFormattedTextField(NumberFormat.getIntegerInstance()); 
    private int PORT; 
	private JLabel host_label = new JLabel("HOST:");
    private JTextField host = new JTextField("omero.france-bioinformatique.fr");
    private String HOST = new String();
	private JLabel user_label = new JLabel("User:");
    private JTextField user = new JTextField("");
    private String User = new String();
	private JLabel passwd_label = new JLabel("Password:");
	private JPasswordField passwd = new JPasswordField("");
    private String Password = new String();
    private JButton Valider = new JButton("Login");
    

	public Connexion() {
		///
		super("Connection to Omero");
		this.setSize(350,200);
		//this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null); // centre la fenetre à l'écran 
		///
		cp.setLayout(new BoxLayout(cp,BoxLayout.PAGE_AXIS));
		panelInfo.setLayout(new BoxLayout(panelInfo,BoxLayout.LINE_AXIS));
		panelInfo1.setLayout(new GridLayout(0,1,0,3));  // 0: as many rows as necessary
		panelInfo2.setLayout(new GridLayout(0,1,0,3));
		panelInfo1.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10)); 
		panelInfo2.setBorder(BorderFactory.createEmptyBorder(15, 10, 5, 10));
		panelInfo1.add(host_label);
        panelInfo2.add(host);
        panelInfo1.add(port_label);
        panelInfo2.add(port); port.setValue(4064);
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

	def connect_to_omero(hst,prt,usr,psw) {
		LoginCredentials credentials = new LoginCredentials()
		credentials.getServer().setHostname(hst.trim())
		credentials.getServer().setPort(prt)
		credentials.getUser().setUsername(usr.trim())
		credentials.getUser().setPassword(psw.trim())
		SimpleLogger simpleLogger = new SimpleLogger()
		Gateway gateway = new Gateway(simpleLogger)
		ExperimenterData experimenter = gateway.connect(credentials)
		return [gateway, experimenter, credentials]
	}
	
	class BoutonValiderCoListener implements ActionListener{
		public void actionPerformed(ActionEvent arg0) {
			HOST = host.getText();
			PORT = port.getValue();
			User = user.getText();
			Password = passwd.getText();
			try {
				ArrayList conn = connect_to_omero(HOST,PORT,User,Password);
				Gateway gate = conn[0];
				ExperimenterData exp = conn[1];
				LoginCredentials cred = conn[2];
				this.dispose();
				new Getinfos(gate,exp,cred);
			}
			catch(DSOutOfServiceException | omero.ClientError e1) { //DSOutOfServiceException e1 //omero.ClientError
				String errorValue = new String(e1.getMessage());
				JOptionPane errorPane = new JOptionPane();
				String message = new String();
				if (errorValue == "Login credentials not valid") {
					message = "Login credentials not valid";
				} else if (errorValue == "Can't resolve hostname " + HOST) {
					message = "Hostname not valid";
				} else if (errorValue == "Obtained null object proxy") {
					message = "Port not valid or no internet access";
				} else {
					message = e1.getMessage();
				}
				errorPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}

public class Getinfos extends JFrame {
	private Container cp = this.getContentPane();
	// choix du groupe
	private JPanel panelGroup = new JPanel();
	private JPanel group = new JPanel(); 
	private JLabel label_group = new JLabel("Group ID: ");
	private JComboBox groupList = new JComboBox();
	private JLabel label_groupname = new JLabel();
	
	// choix des images d'entrées
	private JPanel panelInput = new JPanel();
	private JPanel input1 = new JPanel(); 
	private JPanel input2a = new JPanel(); 
	private JPanel input2b = new JPanel(); 

	private JLabel label_entertype = new JLabel("Where to get files to analyze :");
	private ButtonGroup indata = new ButtonGroup();
	private JRadioButton omero = new JRadioButton("Omero");
	private JRadioButton local = new JRadioButton("Local");
    	// choix du dataSet
	private JLabel label_projectin = new JLabel("Project ID: ");
	private JComboBox projectListIn = new JComboBox();
	private JLabel label_projectinname = new JLabel();
	private JLabel label_datasetin = new JLabel("Dataset ID: ");
	private JComboBox datasetListIn = new JComboBox();
	private JLabel label_datasetinname = new JLabel();
		// choix du dossier
	private JTextField inputfolder = new JTextField(20);
    private JButton inputfolder_btn = new JButton("Images directory");
    //choix de la macro
    private JPanel panelMacro = new JPanel();
    private JPanel macro1 = new JPanel(); 
    private JPanel macro2 = new JPanel(); 
    private JTextField macro = new JTextField(20);
    private JButton macro_btn = new JButton("Macro file");
    private JCheckBox checkresfile = new JCheckBox(" The macro returns a results file (other than images)");
    // choix de la sortie
    private JPanel panelOutput = new JPanel();
    private JPanel output1 = new JPanel(); 
    private JLabel label_extension = new JLabel("Suffix of output files :");
    private JTextField extension = new JTextField(10); 
    	// en ligne ou en local => checkbox
    private JPanel output2 = new JPanel(); 
    private JLabel label_recordoption = new JLabel("Where to save results :");
    private JCheckBox checkinline = new JCheckBox("Omero");
    private JCheckBox checkoutline = new JCheckBox("Local");
			// en ligne
	private JPanel output3a = new JPanel(); 
	private JLabel label_outdata = new JLabel("Choose an output dataset :"); 
	private ButtonGroup outdata = new ButtonGroup();
	private JRadioButton exist = new JRadioButton("Existing dataset");
	private JRadioButton diff = new JRadioButton("New dataset");
				// Dataset existant
	private JPanel output3a1 = new JPanel(); 
	private JLabel label_existproject = new JLabel("project ID: ");
	private JComboBox projectListOutExist = new JComboBox();
	private JLabel label_existprojectname = new JLabel();
	private JLabel label_existdataset = new JLabel("Dataset ID: ");
	private JComboBox datasetListOutExist = new JComboBox();
	private JLabel label_existdatasetname = new JLabel();
				// Nouveau dataSet
	private JPanel output3a2 = new JPanel(); 
	private JLabel label_newproject = new JLabel("Project ID: ");	
	private JComboBox projectListOutNew = new JComboBox();
	private JLabel label_newprojectname = new JLabel();
	private JLabel label_newdataset = new JLabel("Dataset name: ");
	private JTextField newdataset = new JTextField(10);
			// en local
	private JPanel output3b = new JPanel(); 
    private JTextField directory = new JTextField(20);
	private JButton directory_btn = new JButton("Output directory");
	
	// Bouton de validation
	private JPanel panelBtn = new JPanel();
    private JButton valider = new JButton("Submit");

    //variable à garder
    private String macro_chosen;
    private String extension_chosen;
	private Long dataset_id_in;
	private String directory_out;
	private String directory_in;
	private Long dataset_id_out;
	private Long project_id_out;
	private String dataset_name_out;

    private LinkedHashMap<Long,Long> groupmap;
    private LinkedHashMap<Long,Long> idmap;
    private LinkedHashMap<Long,Long> projname;
    private LinkedHashMap<Long,Long> dataname;
    private Set<Long> project_ids;
    private Set<Long> group_ids;

    private Gateway gate;
	private SecurityContext ctx;
	private ExperimenterData exp;
	private LoginCredentials cred;


	public Getinfos(Gateway gte,ExperimenterData expm,LoginCredentials crd) {
		///
		super("Choice of input files and output location");
		this.setSize(600,550);
		this.setLocationRelativeTo(null);
        ///
		gate = gte;
		exp = expm;
		cred = crd;
		///
		groupmap = my_groups(exp);
		group_ids = groupmap.keySet();
		group_ids.each { group_id ->
			groupList.addItem(group_id);
		}
		
        ///
		Font namefont = new Font("Arial",Font.ITALIC,10);
        ///
        cp.setLayout(new BoxLayout(this.getContentPane(),BoxLayout.PAGE_AXIS));

		group.add(label_group);
		group.add(groupList); groupList.addActionListener(new ComboGroupListener());
		group.add(label_groupname); label_groupname.setFont(namefont);
		panelGroup.add(group);
		panelGroup.setBorder(BorderFactory.createTitledBorder("Group"));
		cp.add(panelGroup);
		
		//input1.setLayout(new BoxLayout(input1, BoxLayout.LINE_AXIS)); 
		input1.add(label_entertype);
		input1.add(omero); indata.add(omero); omero.addItemListener(new EnterInOutListener());
		input1.add(local); indata.add(local); local.addItemListener(new EnterInOutListener());
		//input2a.setLayout(new BoxLayout(input2a, BoxLayout.LINE_AXIS));
		input2a.add(label_projectin);
		input2a.add(projectListIn); projectListIn.addActionListener(new ComboInListener()); 
		input2a.add(label_projectinname); label_projectinname.setFont(namefont);
		input2a.add(label_datasetin); 
		input2a.add(datasetListIn); datasetListIn.addActionListener(new ComboDataInListener());
		input2a.add(label_datasetinname); label_datasetinname.setFont(namefont);
		//input2b.setLayout(new BoxLayout(input2b, BoxLayout.LINE_AXIS));
		input2b.add(inputfolder); inputfolder.setMaximumSize(new Dimension(300, 30));
		input2b.add(inputfolder_btn); inputfolder_btn.addActionListener(new BoutonInputFolderListener());
		panelInput.add(input1); 
		omero.setSelected(true);
		panelInput.setLayout(new BoxLayout(panelInput,BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
        cp.add(panelInput);

		//macro1.setLayout(new BoxLayout(macro1, BoxLayout.LINE_AXIS));
		macro1.add(macro); macro.setMaximumSize(new Dimension(300, 30));
        macro1.add(macro_btn); macro_btn.addActionListener(new BoutonMacroListener());
        macro2.setLayout(new BoxLayout(macro2, BoxLayout.LINE_AXIS));
        macro2.add(checkresfile);
        panelMacro.add(macro1);
        panelMacro.add(macro2);
        panelMacro.setLayout(new BoxLayout(panelMacro,BoxLayout.PAGE_AXIS));
        panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
        cp.add(panelMacro);

        //output1.setLayout(new BoxLayout(output1, BoxLayout.LINE_AXIS));
        output1.add(label_extension);
        output1.add(extension); extension.setText("_macro"); //extension.setMaximumSize(new Dimension(300, 30));
		
        //output2.setLayout(new BoxLayout(output2, BoxLayout.LINE_AXIS));
        output2.add(label_recordoption);
		output2.add(checkinline); checkinline.addItemListener(new CheckInOutListener());
		output2.add(checkoutline); checkoutline.addItemListener(new CheckInOutListener());
		//
		//output3a.setLayout(new BoxLayout(output3a, BoxLayout.LINE_AXIS));
		output3a.add(label_outdata);
		output3a.add(exist); outdata.add(exist); exist.addItemListener(new CheckInOutListener()); exist.setSelected(true);
		output3a.add(diff); outdata.add(diff); diff.addItemListener(new CheckInOutListener());
		// exist
		//output3a1.setLayout(new BoxLayout(output3a1, BoxLayout.LINE_AXIS));
		output3a1.add(label_existproject);
		output3a1.add(projectListOutExist); projectListOutExist.addActionListener(new ComboOutExistListener());
		output3a1.add(label_existprojectname); label_existprojectname.setFont(namefont);
		output3a1.add(label_existdataset);
		output3a1.add(datasetListOutExist); datasetListOutExist.addActionListener(new ComboDataOutExistListener());
		output3a1.add(label_existdatasetname); label_existdatasetname.setFont(namefont);
		// diff
		//output3a2.setLayout(new BoxLayout(output3a2, BoxLayout.LINE_AXIS));
		output3a2.add(label_newproject); 
		output3a2.add(projectListOutNew); projectListOutNew.addActionListener(new ComboOutNewListener());
		output3a2.add(label_newprojectname); label_newprojectname.setFont(namefont);
		output3a2.add(label_newdataset);
		output3a2.add(newdataset);
		//
		//output3b.setLayout(new BoxLayout(output3b, BoxLayout.LINE_AXIS));
		output3b.add(directory); directory.setMaximumSize(new Dimension(300, 30));
		output3b.add(directory_btn); directory_btn.addActionListener(new BoutonDirectoryListener());
		//
		panelOutput.add(output1);
		panelOutput.add(output2);
		panelOutput.setLayout(new BoxLayout(panelOutput,BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		cp.add(panelOutput);

		panelBtn.add(valider); valider.addActionListener(new BoutonValiderDataListener());
		cp.add(panelBtn);
        
        this.setVisible(true);
	}

	class ComboGroupListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long group_id = groupList.getSelectedItem()
			label_groupname.setText(groupmap.get(group_id))
			ctx = new SecurityContext(group_id);
			ArrayList maps = my_projects_and_datasets(gate,ctx)
			idmap = maps[0]
			projname = maps[1]
			dataname = maps[2]
			project_ids = idmap.keySet()
			projectListIn.removeAllItems()
			projectListOutNew.removeAllItems()
			projectListOutExist.removeAllItems()
			project_ids.each { project_id ->
				projectListIn.addItem(project_id)
				projectListOutNew.addItem(project_id)
				projectListOutExist.addItem(project_id)
			}
		}
	}

	class EnterInOutListener implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (omero.isSelected()) {
				panelInput.add(input2a);
				panelInput.remove(input2b);
			} 
			else { //local.isSelected()
				panelInput.add(input2b);
				panelInput.remove(input2a);
			}
			this.setVisible(true)
		}
	}

	class ComboInListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long project_id_in = projectListIn.getSelectedItem()
			label_projectinname.setText(projname.get(project_id_in))
			datasetListIn.removeAllItems()
			idmap.get(project_id_in).eachWithIndex { dataset_id, index ->
				datasetListIn.addItem(dataset_id)
			}
		}
	}

	class ComboDataInListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long dataset_id_in = datasetListIn.getSelectedItem()
			label_datasetinname.setText(dataname.get(dataset_id_in))
		}
	}

	class ComboOutExistListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long project_id_out = projectListOutExist.getSelectedItem()
			label_existprojectname.setText(projname.get(project_id_out))
			datasetListOutExist.removeAllItems()
			idmap.get(project_id_out).eachWithIndex { dataset_id, index ->
				datasetListOutExist.addItem(dataset_id)
			}
		}
	}

	class ComboDataOutExistListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long dataset_id_out = datasetListOutExist.getSelectedItem()
			label_existdatasetname.setText(dataname.get(dataset_id_out))
		}
	}

	class ComboOutNewListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Long project_id_out = projectListOutNew.getSelectedItem()
			label_newprojectname.setText(projname.get(project_id_out))
		}
	}
	
	class BoutonInputFolderListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser inputchoice = new JFileChooser();
			inputchoice.setDialogTitle("Choose the input directory");
			inputchoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = inputchoice.showOpenDialog(null);
			if(returnVal==JFileChooser.APPROVE_OPTION){
				File absindir = new File(inputchoice.getSelectedFile().getAbsolutePath());
				// teste si le dossier existe (cas où on écrit à la main)
				if(absindir.exists() && absindir.isDirectory()) { 
					inputfolder.setText(absindir.toString());
				} else {
					//trouver une façon d'empêcher la fermeture du JFileChooser?
					errorWindow("Input: \nThe directory doesn't exist"); // new JOptionPane(inputchoice);
				}
            }
            if (returnVal==JFileChooser.CANCEL_OPTION && inputfolder.getText()=="") {
            	// prévient l'utilisateur si on annule la sélection du dossier sans avoir rien choisi au préalable
            	warningWindow("Input: \nNo directory selected");
            }
		}
	}
	
	class BoutonMacroListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser macrochoice = new JFileChooser();
			macrochoice.setDialogTitle("Choose the macro file");
			macrochoice.setFileSelectionMode(JFileChooser.FILES_ONLY);
			int returnVal = macrochoice.showOpenDialog(null);
			if(returnVal==JFileChooser.APPROVE_OPTION){
				File absfile = new File(macrochoice.getSelectedFile().getAbsolutePath());
				if(absfile.exists() && !absfile.isDirectory()) { 
					macro.setText(absfile.toString());
				} else {
					//trouver une façon d'empêcher la fermeture du JFileChooser?
					warningWindow("Macro: \nThe file doesn't exist"); //new JOptionPane(macrochoice);
				}
            }
            if (returnVal==JFileChooser.CANCEL_OPTION && macro.getText()=="") {
            	// warn user if macro selection canceled without any previous choice
            	warningWindow("Macro: \nNo macro selected");
            }
		}
	}

	class BoutonDirectoryListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser outputchoice = new JFileChooser(); 
			outputchoice.setDialogTitle("Choose the output directory");
			outputchoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = outputchoice.showOpenDialog(null);
			outputchoice.setAcceptAllFileFilterUsed(false); // ????
			if (returnVal == JFileChooser.APPROVE_OPTION) { 
				File absdir = new File(outputchoice.getSelectedFile().getAbsolutePath());
				if(absdir.exists() && absdir.isDirectory()) { 
					directory.setText(absdir.toString());
				} else {
					//trouver une façon d'empêcher la fermeture du JFileChooser?
					errorWindow("Output: \nThe directory doesn't exist");
				}
			}
			if (returnVal == JFileChooser.CANCEL_OPTION && directory.getText()=="") {
				warningWindow("Output: \nNo directory selected");
			}
		}
	}

	class CheckInOutListener implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (checkinline.isSelected()) {
				panelOutput.add(output3a);
				if (diff.isSelected()) {
					panelOutput.add(output3a2);
					panelOutput.remove(output3a1);
				} else if (exist.isSelected()) {
					panelOutput.add(output3a1);
					panelOutput.remove(output3a2);
				}
			} else {
				panelOutput.remove(output3a);
				panelOutput.remove(output3a1);
				panelOutput.remove(output3a2);
			}
			if (checkoutline.isSelected()) {
				panelOutput.add(output3b);
			} else {
				panelOutput.remove(output3b);
			}
			this.setVisible(true)
		}
	}

	class BoutonValiderDataListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			
			// initiation of success variables
			Boolean inputdata;
			Boolean macrodata;
			Boolean omerorecord;
			Boolean localrecord;
			Boolean recordtype;
			
			// input data
			if (omero.isSelected()) {
				dataset_id_in = datasetListIn.getSelectedItem();
				if (dataset_id_in == null) {
					errorWindow("Input: \nNo dataset selected");
				} else {
					inputdata = true;
				}
			} else { // local.isSelected()
				if (inputfolder.getText() == "") {
					errorWindow("Input: \nNo directory selected");
				} else {
					directory_in = inputfolder.getText()
					File directory_inf = new File(directory_in);
					if(directory_inf.exists() && directory_inf.isDirectory()) { 
						inputdata = true;
					} else {
						errorWindow("Input: \nThe directory "+ directory_in +" doesn't exist");
					}
				}
			}

			// macro file (mandatory)
			if (macro.getText() == "") {
				errorWindow("Macro: \nNo macro selected");
			} else {
				macro_chosen = macro.getText();
				File macrof = new File(macro_chosen);
				if(macrof.exists() && !macrof.isDirectory()) { 
					macrodata = true;
				} else {
					errorWindow("Macro: \nThe file "+ macro_chosen +" doesn't exist");
				}
			}

			// suffixe
			extension_chosen = extension.getText();
			
			// record type
			if (checkinline.isSelected()) { // inline record
				if (exist.isSelected()) { // existing dataset
					dataset_id_out = datasetListOutExist.getSelectedItem();
					if (dataset_id_out == null) {
						errorWindow("Output: \nNo dataset selected");
						omerorecord = false;
					}
					else {
						omerorecord = true;
					}
				} 
				else { // new dataset
					project_id_out = projectListOutNew.getSelectedItem();
					dataset_name_out = newdataset.getText();
					if (project_id_out == null || dataset_name_out == "") {
						errorWindow("Output: \nNo project selected or name written");
						omerorecord = false;
					} else {
						omerorecord = true;
					}
				}
			}
			if (checkoutline.isSelected()) { // local record
				if (directory.getText() == "") {
					errorWindow("Output: \nNo directory selected");
					localrecord = false;
				} else {
					directory_out = directory.getText();
					File directory_outf = new File(directory_out);
					if (directory_outf.exists() && directory_outf.isDirectory()) {
						localrecord = true;
					} else {
						errorWindow("Output: \nThe directory "+ directory_out +" doesn't exist");
						localrecord = false;
					}
				}
			}
			
			if (!checkinline.isSelected() && !checkoutline.isSelected()) { // omerorecord == null && localrecord = null
				errorWindow("Output: \nYou have to choose the localisation to save the results");
			} else if ((omerorecord==true || omerorecord==null) && (localrecord==true || localrecord==null)) { // true means selected and ok, null means not selected, false means selected but pb
				recordtype = true;
			}
			

			// 
			if (inputdata && macrodata && recordtype) {
				try {
					ProgressThread progress = new ProgressThread();
					progress.start();
				} catch(Exception e2) {
					 errorWindow(e2.getMessage())
				}
			}
			
		}
	}

	class ImageTreatment extends JFrame{
		private Container cp2 = this.getContentPane();
		private JPanel Panel0 = new JPanel();
		private JPanel Panel1 = new JPanel();
		private JPanel Panel2 = new JPanel();
		private JLabel warn_label = new JLabel("", SwingConstants.CENTER);
		private JLabel prog_label = new JLabel("", SwingConstants.CENTER);
		private JButton InfoBtn = new JButton("OK");
		
		public ImageTreatment() {
			
			this.setTitle("Progression");
			this.setLocationRelativeTo(null);
			this.setSize(300, 200);
			
			Font warnfont = new Font("Arial",Font.PLAIN,12);
			Font progfont = new Font("Arial",Font.BOLD,12);
			warn_label.setText("<html> <body style='text-align:center;'> Warning: <br>Image processing can take time <br>depending on your network rate </body> </html>");
			warn_label.setFont(warnfont);
			prog_label.setFont(progfont);
			
			cp2.setLayout(new BoxLayout(cp2, BoxLayout.PAGE_AXIS));
			Panel0.add(warn_label);
			Panel1.add(prog_label);
			Panel2.add(InfoBtn); 
			InfoBtn.setEnabled(false);
			InfoBtn.addActionListener(new ProgressListener());
			cp2.add(Panel0);
			cp2.add(Panel1);
			cp2.add(Panel2);
            this.setVisible(true);
		}

		public void runTreatment() {
			
			Boolean results;
			if (checkresfile.isSelected()) {results = true;} 
			else {results = false;}
			ArrayList<String> paths_images;
			ArrayList<String> paths_attach;

		try {
	
			ArrayList creds = get_credentials_info(cred)
			if (!checkoutline.isSelected()) {
				prog_label.setText("Temporary directory creation...");
				Path directory_outf = Files.createTempDirectory("Fiji_analyse"); 
				directory_out = directory_outf.toString();
			}
			
			if (omero.isSelected()) {
				prog_label.setText("Images recovery from Omero...");
				Collection<ImageData> images = get_images(gate, ctx, dataset_id_in);
				prog_label.setText("Macro running...");
				ArrayList<ArrayList<String>> paths = run_macro(images,creds[0],creds[1],creds[2],creds[3],ctx,macro_chosen,extension_chosen,directory_out,results);
				paths_images = paths[0];
				paths_attach = paths[1];
			} else {
				prog_label.setText("Images recovery from input folder...");
				ArrayList<String> images = get_images_from_directory(directory_in);
				prog_label.setText("Macro running...");
				ArrayList<ArrayList<String>> paths = run_macro_on_local_images(images,macro_chosen,extension_chosen,directory_out,results);
				paths_images = paths[0];
				paths_attach = paths[1];
			}

			if (diff.isSelected()) {
				prog_label.setText("New dataset creation...");
				dataset_id_out = create_dataset_in_project(gate, ctx, dataset_name_out, project_id_out);
			}

			if (checkinline.isSelected()) {
				prog_label.setText("Images import on omero...");
				ArrayList<Long> images_ids = import_images_in_dataset(paths_images,creds[0],creds[1],creds[2],creds[3],dataset_id_out,gate,ctx)
				if (results == true) { 
					prog_label.setText("Attachement of results files...");
					upload_tag_files(gate,ctx,paths_attach,images_ids)
				}	
			}

			if (!checkoutline.isSelected()) {
				prog_label.setText("Temporary directory deletion...");
				delete_temp(directory_out)
			}
			
			prog_label.setText("Task completed!");
			InfoBtn.setEnabled(true);
			
			} catch (Exception e3) {
				if (e3.getMessage() == "Macro canceled") {
					this.dispose();
					IJ.run("Close");
				}
				errorWindow(e3.getMessage())
			}
		}

		class ProgressListener implements ActionListener {
			public void actionPerformed(ActionEvent e) {
				this.dispose();
			}
		}
	}

	class ProgressThread extends Thread {
		public void run() {
			ImageTreatment action = new ImageTreatment();
			action.runTreatment();
		}
	}


	def errorWindow(message) {
		JOptionPane CancelWarningPane = new JOptionPane();
		CancelWarningPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}

	def warningWindow(message) {
		JOptionPane CancelWarningPane = new JOptionPane();
		CancelWarningPane.showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}

	def my_groups(experimenter) {
		ArrayList<GroupData> groups = experimenter.getGroups()
		def groupmap = [:]
		for (group in groups) {
			Long group_id = group.getGroupId()
			String group_name = group.getName()
			if (group_id != 1) { // exclude the "user" group
				groupmap[group_id] = group_name
			}
		}
		return groupmap
	}
	
	def my_projects_and_datasets(gateway,context) {
		BrowseFacility browse = gateway.getFacility(BrowseFacility)
		Collection<ProjectData> projects = browse.getProjects(context)
		def idmap = [:]
		def projname = [:]
		def dataname = [:]
		for (project in projects) {
			Long project_id = project.getId()
			String project_name = project.getName()
			idmap[project_id] = []
			projname[project_id] = project_name
			Set<DatasetData> datasets = project.getDatasets()
			for (dataset in datasets) {
				Long dataset_id = dataset.getId()
				String dataset_name = dataset.getName()
				idmap[project_id].add(dataset_id)
				dataname[dataset_id] = dataset_name
			}
		}
		return [idmap,projname,dataname]
	}

	def get_credentials_info(credentials) {
		String Host = credentials.getServer()["hostname"]
		int Port = credentials.getServer()["port"]
		String User = credentials.getUser().getUsername()
		String Pwd = credentials.getUser().getPassword()
		return [Host, Port, User, Pwd]
	}

	def get_images(gateway, context, dataset_id) {
		""" List all images contained in a Dataset """
		BrowseFacility browse = gateway.getFacility(BrowseFacility)
		ArrayList ids = new ArrayList(1)
		ids.add(new Long(dataset_id))
		Collection<ImageData> images = browse.getImagesForDatasets(context, ids)
		return images
	}

	def get_images_from_directory(directory){
		""" List all image's paths contained in a directory """
		File dir = new File(directory);
		File[] files= dir.listFiles();
		ArrayList<String> paths_images_ini = new ArrayList<String>();
		for(int i =0; i<files.length ; i++){
			String file = files[i];
			paths_images_ini.add(file);
		}
		return paths_images_ini
	}

	def get_image_ids(gateway, context, dataset_id) {
	    """ List all image's ids contained in a Dataset """
	    BrowseFacility browse = gateway.getFacility(BrowseFacility)
	    ArrayList<Long> ids = new ArrayList<Long>()
	    ids.add(new Long(dataset_id))
	    Collection<ImageData> images = browse.getImagesForDatasets(context, ids)
	    Iterator<ImageData> j = images.iterator()
	    ArrayList<Long> image_ids = new ArrayList<Long>()
	    while (j.hasNext()) {
	        image_ids.add(j.next().getId())
	    }
	    return image_ids
	}

	def create_dataset_in_project(gateway, context, newname, projectId) {
		""" Add a dataset to an existing project """
		projectId = new Long(projectId)
		DataManagerFacility dm = gateway.getFacility(DataManagerFacility)
		DatasetData datasetData = new DatasetData()
		datasetData.setName(newname)
		BrowseFacility browse = gateway.getFacility(BrowseFacility)
		ProjectData projectData = browse.getProjects(context, Collections.singleton(projectId)).iterator().next()
		datasetData.setProjects(Collections.singleton(projectData))
		DataObject newdataset = dm.saveAndReturnObject(context, datasetData)
		return newdataset.getId()
	}

	def open_image_plus(HOST, PORT, USERNAME, PASSWORD, group_id, image_id) {
	    """ Open the image using the Bio-Formats Importer """
	    String location = "location=[OMERO]"
	    String open = "open=[omero:server=${HOST}\nuser=${USERNAME.trim()}\nport=${PORT}\npass=${PASSWORD.trim()}\ngroupID=${group_id}\niid=${image_id}]"
	    String windowless = "windowless=true"
	    String view = "view=Hyperstack"
	    String options = "$location $open $windowless $view"
	    IJ.runPlugIn("loci.plugins.LociImporter", options)
	}
	
	def save_and_close_with_res(image,attach) {
		//IJ.selectWindow("Result") //dépend si images renomées ou non dans la macro
		
		IJ.saveAs("TIFF",image)
		IJ.run("Close")
		IJ.selectWindow("Results")
		IJ.saveAs("Results",attach)
		IJ.run("Close")
	}

	def save_and_close_without_res(image) {
		//IJ.selectWindow("Result") //dépend si images renomées ou non dans la macro
		IJ.saveAs("TIFF",image)
		IJ.run("Close")
		IJ.run("Close") // nécessaire ? selon macro ? (sinon une fenetre reste ouverte)
	}

	def run_macro(images,Host,Port,User,Password,context,macro_chosen,extension_chosen,dir,results){
		""" Run a macro on images and save the result """
		int size = images.size()
		String[] paths_images = new String[size];
		String[] paths_attach = new String[size];
		IJ.run("Close All");
		String appel='0';
		images.eachWithIndex { image, index ->
			// Open the image
			long id = image.getId();
			long gid = context.getGroupID();
			open_image_plus(Host, Port, User, Password, String.valueOf(gid), String.valueOf(id));
			ImagePlus imp = IJ.getImage(); 
			// Define paths
			String title = imp.getTitle();
			title = FilenameUtils.removeExtension(title);
			String res = dir + "\\" + title + extension_chosen + ".tif";
			String attach = dir + "\\" + "Res_" + title + extension_chosen + ".xls";
			// Analyse the images.
			IJ.runMacroFile(macro_chosen,appel);
			appel='1';
			// Save and Close the various components
			if (results == true) {
				save_and_close_with_res(res,attach)
			} else {
				save_and_close_without_res(res)
			}
			imp.changes = false; // Prevent "Save Changes?" dialog
			imp.close();
			paths_images[index] = res;
			paths_attach[index] = attach;
		}
		return [paths_images,paths_attach];
	}
	
	def run_macro_on_local_images(images,macro_chosen,extension_chosen,dir,results){
		""" Run a macro on images from local computer and save the result """
		int size = images.size()
		String[] paths_images = new String[size];
		String[] paths_attach = new String[size];
		IJ.run("Close All")
		String appel='0';
		images.eachWithIndex { Image, index ->
			// Open the image
			//IJ.open(Image);
			//IJ.run("Stack to Images");
			ImagePlus imp = IJ.openImage(Image);
			IJ.run("Bio-Formats Importer", "open=" + Image + " autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
			int L= imp.getHeight();
			int H = imp.getWidth();
			int B = imp.getBitDepth();
			int F = imp.getFrame();
			int C = imp.getChannel();
			int S = imp.getSlice();
			IJ.createHyperStack(Image,H,L,C,S,F,B);
			//IJ.run("Images to Hyperstack");
			//imp.createHyperStack(Image,C,S,F,B);
			// Define paths
			String title = imp.getTitle();
			title = FilenameUtils.removeExtension(title);
			String res = dir + "\\" + title + extension_chosen + ".tif";
			String attach = dir + "\\" + "Res_" + title + extension_chosen + ".xls";
			// Analyse the images
			IJ.runMacroFile(macro_chosen,appel);
			appel='1';
			// Save and Close the various components
			if (results == true) {
				save_and_close_with_res(res,attach)
			} else {
				save_and_close_without_res(res)
			}			
			imp.changes = false; // Prevent "Save Changes?" dialog
			imp.close();
			paths_images[index] = res;
			paths_attach[index] = attach;
		}
		return [paths_images,paths_attach];
	}
	
	def import_images_in_dataset(paths_images,Host,Port,User,Password,dataset_id,gateway,context) {
		""" Import images in Omero server from paths_images and return ids of these images in dataset_id """
		//paths_images : String[]
		ArrayList<Long> initial_images_ids = get_image_ids(gateway, context, dataset_id)
		ArrayList<Long> images_ids = new ArrayList<Long>()
		
		ImportConfig config = new ImportConfig();
		
		config.email.set("");
		config.sendFiles.set(true);
		config.sendReport.set(false);
		config.contOnError.set(false);
		config.debug.set(false);
		config.hostname.set(Host); //
		config.port.set(Port); //
		config.username.set(User); //
		config.password.set(Password); //
		config.targetClass.set("omero.model.Dataset");
		config.targetId.set(Long.valueOf(dataset_id));
		
		OMEROMetadataStoreClient store = new OMEROMetadataStoreClient();
		
		Iterator<String> iter = paths_images.iterator();
		while (iter.hasNext()) {
			String value = iter.next();
			String[] path = [value] as String[]
			try {
				store = config.createStore();
				store.logVersionInfo(config.getIniVersionNumber());
				OMEROWrapper reader = new OMEROWrapper(config);
				ImportLibrary library = new ImportLibrary(store, reader);
		
				ErrorHandler handler = new ErrorHandler(config);
				library.addObserver(new LoggingImportMonitor());
							
				ImportCandidates candidates = new ImportCandidates(reader, path, handler);
				reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL));
				library.importCandidates(config, candidates);
				
				store.logout();
	
				ArrayList<Long> current_images_ids = get_image_ids(gateway, context, dataset_id)
				current_images_ids.each { id ->
					if (!initial_images_ids.contains(id)) {
						initial_images_ids.add(id)
						images_ids.add(id)
					}
				}
			} catch (Exception e) {
				println(e.getMessage());
				println(e.getCause());
				//e.printStackTrace();
			}
		}
		return images_ids
	}

	def upload_tag_files(gateway,context,paths_attach,Images_Id){
		""" Attach result files from paths_attach to images in omero """
		BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
		DataManagerFacility dm = gateway.getFacility(DataManagerFacility.class);
		for (int i = 0; i < paths_attach.size(); i++) {
			int INC = 262144;
			String absolutepath= paths_attach[i]
			//ImageData image = browse.getImage(ctx, Images_Id[i]);
			File file = new File(absolutepath);
			String name = file.getName();
			ImageData image = browse.getImage(context, Images_Id[i])
			String path = absolutepath.substring(0,absolutepath.length()-name.length());
			//create the original file object.
			OriginalFileI originalFile = new OriginalFileI();
			originalFile.setName(rstring(name));
			originalFile.setPath(rstring(path));
			originalFile.setSize(rlong(file.length()));
			ChecksumAlgorithmI checksumAlgorithm = new ChecksumAlgorithmI();
			checksumAlgorithm.setValue(rstring(ChecksumAlgorithmSHA1160.value));
			originalFile.setHasher(checksumAlgorithm);
			originalFile.setMimetype(rstring("application/octet-stream"));
			//Now we save the originalFile object
			originalFile = (OriginalFileI) dm.saveAndReturnObject(context, originalFile);
			//Initialize the service to load the raw data
			RawFileStorePrx rawFileStore = gateway.getRawFileService(context);
			long pos = 0;
			int rlen;
			byte[] buf = new byte[INC];
			ByteBuffer bbuf;
			//Open file and read stream
			try { 
				FileInputStream stream = new FileInputStream(file) ;
			    rawFileStore.setFileId(originalFile.getId().getValue());
			    while ((rlen = stream.read(buf)) > 0) {
			        rawFileStore.write(buf, pos, rlen);
			        pos += rlen;
			        bbuf = ByteBuffer.wrap(buf);
			        bbuf.limit(rlen);
			    }
			    originalFile = rawFileStore.save();
			    stream.close();
			} finally {
			   rawFileStore.close();
			}
			FileAnnotationI fa = new FileAnnotationI();
			fa.setFile(originalFile);
			fa.setDescription(rstring("Results")); // The description set above e.g. PointsModel
			fa.setNs(rstring("Results")); // The name space you have set to identify the file annotation.
			
			//save the file annotation.
			fa = (FileAnnotationI) dm.saveAndReturnObject(context, fa);
			//now link the image and the annotation
			ImageAnnotationLinkI link = new ImageAnnotationLinkI();
			link.setChild(fa);
			link.setParent(image.asImage());
			//save the link back to the server.
			link = (ImageAnnotationLinkI) dm.saveAndReturnObject(context, link);
			// o attach to a Dataset use DatasetAnnotationLink;
		}
	}
	
	def delete_temp(tmp_dir) {
		""" Delete the local copy of temporary files and directory """
		File dir = new File(tmp_dir)
		File[] entries = dir.listFiles()
		for (int i = 0; i < entries.length; i++) {
		    entries[i].delete()
		}
		dir.delete()
	}


}

public static void main(String[] args) {
        new Connexion();
}
