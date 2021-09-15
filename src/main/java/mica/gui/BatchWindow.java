package mica.gui;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import ij.IJ;
import mica.BatchData;
import mica.BatchRunner;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;

import javax.swing.*;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static javax.swing.JOptionPane.showMessageDialog;

public class BatchWindow extends JFrame {
	private final JComboBox<String> groupList = new JComboBox<>();
	private final JComboBox<String> userList = new JComboBox<>();
	private final JLabel labelGroupName = new JLabel();

	// choices of input images
	private final JPanel panelInput = new JPanel();
	private final JPanel input2a = new JPanel();
	private final JPanel input2b = new JPanel();

	private final JRadioButton omero = new JRadioButton("OMERO");
	private final JRadioButton local = new JRadioButton("Local");
	private final JCheckBox checkresfile_del_roi = new JCheckBox(" Clear ROIs each time ");

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JLabel labelProjectinname = new JLabel();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JLabel labelDatasetinname = new JLabel();

	// choice of the record
	private final JTextField inputfolder = new JTextField(20);
	private final JButton inputfolder_btn = new JButton("Images directory");
	private final JTextField macro = new JTextField(20);
	private final JButton macro_btn = new JButton("Macro file");
	private final JCheckBox checkresfile_ima = new JCheckBox(" The macro returns an image ");
	private final JCheckBox checkresfile_res = new JCheckBox(" The macro returns a results file (other than images)");
	private final JCheckBox checkresfile_roi = new JCheckBox(" The macro returns ROIs ");

	// choice of output
	private final JPanel panelOutput = new JPanel();
	private final JPanel output1 = new JPanel();
	private final JTextField extension = new JTextField(10);

	// Omero or local => checkbox
	private final JCheckBox checkinline = new JCheckBox("Omero");
	private final JCheckBox checkoutline = new JCheckBox("Local");

	// OMERO
	private final JPanel output3a = new JPanel();
	private final JRadioButton exist = new JRadioButton("Existing dataset");
	private final JRadioButton diff = new JRadioButton("New dataset");

	// existing dataset
	private final JPanel output3a1 = new JPanel();
	private final JComboBox<String> projectListOutExist = new JComboBox<>();
	private final JLabel labelExistprojectname = new JLabel();
	private final JComboBox<String> datasetListOutExist = new JComboBox<>();
	private final JLabel labelExistdatasetname = new JLabel();

	// new dataSet
	private final JPanel output3a2 = new JPanel();
	private final JComboBox<String> projectListOutNew = new JComboBox<>();
	private final JLabel labelNewprojectname = new JLabel();
	private final JTextField newdataset = new JTextField(10);

	// local
	private final JPanel output3b = new JPanel();
	private final JTextField directory = new JTextField(20);
	private final JButton directory_btn = new JButton("Output directory");

	private final JButton start = new JButton("start");
	// Ces champs peuvent être remplacés par un champ Client
	private final Gateway gate;
	//variables to keep
	private BatchData data;
	private String macro_chosen;
	private String extension_chosen;
	private String directory_out;
	private String directory_in;
	private Long outputDatasetId;
	private Long project_id_out;
	private String dataset_name_out;
	private Boolean ima_res;
	private Map<Long, ArrayList<Long>> idmap;
	private Map<Long, String> projname;
	private Map<Long, String> dataname;
	private Map<String, Long> idgroup = new HashMap<>();
	private Map<String, Long> idproj = new HashMap<>();
	private Map<String, Long> idata = new HashMap<>();
	private Map<String, Long> userIds;
	private Set<String> project_ids;
	private List MROIS;
	private List ROISL;
	private List<Long> ima_ids;
	private List<Long> images_ids;
	private SecurityContext ctx;
	private ExperimenterWrapper exp;


	public BatchWindow(BatchData data) {
		super("Choice of input files and output location");
		this.setSize(600, 700);
		this.setLocationRelativeTo(null);
		this.data = data;
		Client client = data.getClient();
		gate = client.getGateway();
		try {
			exp = client.getUser(client.getUser().getUserName());
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error(e.getCause().getMessage());
		}

		Map<Long, String> groupmap = myGroups(exp);
		idgroup = hashToMap(groupmap, idgroup);
		Set<String> groupIds = idgroup.keySet();
		for (String groupId : groupIds) {
			groupList.addItem(groupId);
		}
		Font namefont = new Font("Arial", Font.ITALIC, 10);
		Container cp = this.getContentPane();
		cp.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

		JPanel group = new JPanel();
		JLabel labelGroup = new JLabel("Group Name: ");
		group.add(labelGroup);
		group.add(groupList);
		groupList.addActionListener(new ComboGroupListener());
		group.add(labelGroupName);
		labelGroupName.setFont(namefont);
		JPanel groupUsers = new JPanel();
		JLabel labelUser = new JLabel("User Name: ");
		groupUsers.add(labelUser);
		groupUsers.add(userList);
		userList.addActionListener(new ComboUserListener());
		// choix du groupe
		JPanel panelGroup = new JPanel();
		panelGroup.add(group);
		panelGroup.add(groupUsers);
		panelGroup.setBorder(BorderFactory.createTitledBorder("Group"));
		cp.add(panelGroup);

		//input1.setLayout(new BoxLayout(input1, BoxLayout.LINE_AXIS));
		JPanel input1 = new JPanel();
		JLabel labelEntertype = new JLabel("Where to get files to analyze :");
		input1.add(labelEntertype);
		input1.add(omero);
		ButtonGroup indata = new ButtonGroup();
		indata.add(omero);
		omero.addItemListener(new EnterInOutListener());
		input1.add(local);
		indata.add(local);
		local.addItemListener(new EnterInOutListener());
		//input2a.setLayout(new BoxLayout(input2a, BoxLayout.LINE_AXIS));
		JLabel labelProjectin = new JLabel("Project Name: ");
		input2a.add(labelProjectin);
		input2a.add(projectListIn);
		projectListIn.addActionListener(new ComboInListener());
		input2a.add(labelProjectinname);
		labelProjectinname.setFont(namefont);
		JLabel labelDatasetin = new JLabel("Dataset Name: ");
		input2a.add(labelDatasetin);
		input2a.add(datasetListIn);
		datasetListIn.addActionListener(new ComboDataInListener());
		input2a.add(labelDatasetinname);
		labelDatasetinname.setFont(namefont);
		JCheckBox checkresfile_load_roi = new JCheckBox(" Load ROIs ");
		input2a.add(checkresfile_load_roi);
		input2a.add(checkresfile_del_roi);

		//input2b.setLayout(new BoxLayout(input2b, BoxLayout.LINE_AXIS));
		input2b.add(inputfolder);
		inputfolder.setMaximumSize(new Dimension(300, 30));
		input2b.add(inputfolder_btn);
		inputfolder_btn.addActionListener(new BoutonInputFolderListener());
		panelInput.add(input1);
		omero.setSelected(true);
		panelInput.setLayout(new BoxLayout(panelInput, BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
		cp.add(panelInput);

		//macro1.setLayout(new BoxLayout(macro1, BoxLayout.LINE_AXIS));
		JPanel macro1 = new JPanel();
		macro1.add(macro);
		macro.setMaximumSize(new Dimension(300, 30));
		macro1.add(macro_btn);
		macro_btn.addActionListener(new BoutonMacroListener());
		JPanel macro2 = new JPanel();
		macro2.setLayout(new BoxLayout(macro2, BoxLayout.LINE_AXIS));
		macro2.add(checkresfile_ima);
		JPanel macro3 = new JPanel();
		macro3.setLayout(new BoxLayout(macro3, BoxLayout.LINE_AXIS));
		macro3.add(checkresfile_res);
		JPanel macro4 = new JPanel();
		macro4.setLayout(new BoxLayout(macro4, BoxLayout.LINE_AXIS));
		macro4.add(checkresfile_roi);
		//choice of the macro
		JPanel panelMacro = new JPanel();
		panelMacro.add(macro1);
		panelMacro.add(macro2);
		panelMacro.add(macro3);
		panelMacro.add(macro4);
		panelMacro.setLayout(new BoxLayout(panelMacro, BoxLayout.PAGE_AXIS));
		panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
		cp.add(panelMacro);

		//output1.setLayout(new BoxLayout(output1, BoxLayout.LINE_AXIS));
		JLabel labelExtension = new JLabel("Suffix of output files :");
		output1.add(labelExtension);
		output1.add(extension);
		extension
				.setText("_macro"); //extension.setMaximumSize(new Dimension(300, 30));

		//output2.setLayout(new BoxLayout(output2, BoxLayout.LINE_AXIS));
		JPanel output2 = new JPanel();
		JLabel labelRecordoption = new JLabel("Where to save results :");
		output2.add(labelRecordoption);
		output2.add(checkinline);
		checkinline.addItemListener(new CheckInOutListener());
		output2.add(checkoutline);
		checkoutline.addItemListener(new CheckInOutListener());
		//
		//output3a.setLayout(new BoxLayout(output3a, BoxLayout.LINE_AXIS));
		JLabel labelOutdata = new JLabel("Choose an output dataset :");
		output3a.add(labelOutdata);
		output3a.add(exist);
		ButtonGroup outdata = new ButtonGroup();
		outdata.add(exist);
		exist.addItemListener(new CheckInOutListener());
		exist.setSelected(true);
		output3a.add(diff);
		outdata.add(diff);
		diff.addItemListener(new CheckInOutListener());
		// exist
		//output3a1.setLayout(new BoxLayout(output3a1, BoxLayout.LINE_AXIS));
		JLabel labelExistproject = new JLabel("Project Name: ");
		output3a1.add(labelExistproject);
		output3a1.add(projectListOutExist);
		projectListOutExist.addActionListener(new ComboOutExistListener());
		output3a1.add(labelExistprojectname);
		labelExistprojectname.setFont(namefont);
		JLabel labelExistdataset = new JLabel("Dataset Name: ");
		output3a1.add(labelExistdataset);
		output3a1.add(datasetListOutExist);
		datasetListOutExist.addActionListener(new ComboDataOutExistListener());
		output3a1.add(labelExistdatasetname);
		labelExistdatasetname.setFont(namefont);
		// diff
		//output3a2.setLayout(new BoxLayout(output3a2, BoxLayout.LINE_AXIS));
		JLabel labelNewproject = new JLabel("Project Name: ");
		output3a2.add(labelNewproject);
		output3a2.add(projectListOutNew);
		projectListOutNew.addActionListener(new ComboOutNewListener());
		output3a2.add(labelNewprojectname);
		labelNewprojectname.setFont(namefont);
		JLabel labelNewdataset = new JLabel("Dataset Name: ");
		output3a2.add(labelNewdataset);
		output3a2.add(newdataset);
		//
		//output3b.setLayout(new BoxLayout(output3b, BoxLayout.LINE_AXIS));
		output3b.add(directory);
		directory.setMaximumSize(new Dimension(300, 30));
		output3b.add(directory_btn);
		directory_btn.addActionListener(new BoutonDirectoryListener());
		//
		panelOutput.add(output2);
		panelOutput.setLayout(new BoxLayout(panelOutput, BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		cp.add(panelOutput);

		// validation button
		JPanel panelBtn = new JPanel();
		panelBtn.add(start);
		start.addActionListener(new BoutonValiderDataListener());
		cp.add(panelBtn);

		this.setVisible(true);
	}


	// Transform a LinkedHashMap in Map where keys are names and values are ids
	public Map<String, Long> hashToMap(Map<Long, String> hash,
									   Map<String, Long> M) {
		Set<Long> cles = hash.keySet();
		for (Long cle : cles) {
			M.put(hash.get(cle), cle);
		}
		return M;
	}


	public void errorWindow(String message) {
		showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}


	public void warningWindow(String message) {
		showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}


	public Map<Long, String> myGroups(ExperimenterWrapper experimenter) {
		List<GroupWrapper> groups = experimenter.getGroups();
		Map<Long, String> groupmap = new LinkedHashMap<>();
		for (GroupWrapper group : groups) {
			long groupId = group.getGroupId();
			String groupName = group.getName();
			if (groupId != 1) { // exclude the "user" group
				groupmap.put(groupId, groupName);
			}
		}
		return groupmap;
	}


	public List userProjectsAndDatasets(String username,
										Map<String, Long> userId) {
		Client client = data.getClient();
		List<ProjectWrapper> projects = new ArrayList<>();
		try {
			projects = client.getProjects();
		} catch (ServiceException | AccessException exception) {
			IJ.log(exception.getMessage());
		}

		Map<Long, ArrayList<Long>> idmap = new HashMap<>();
		Map<Long, String> projname = new HashMap<>();
		Map<Long, String> dataname = new HashMap<>();

		if (!username.equals("All members")) {
			projects.removeIf(project -> project.getOwner().getId() !=
										 userId.get(username));
		}

		for (ProjectWrapper project : projects) {
			Long projectId = project.getId();
			idmap.put(projectId, new ArrayList<>());
			String projectName = project.getName();
			projname.put(projectId, projectName);
			List<DatasetWrapper> datasets = project.getDatasets();
			for (DatasetWrapper dataset : datasets) {
				Long datasetId = dataset.getId();
				String datasetName = dataset.getName();
				idmap.get(projectId).add(datasetId);
				dataname.put(datasetId, datasetName);
			}
		}
		return new ArrayList<>(Arrays.asList(idmap, projname, dataname));
	}


	class ComboGroupListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			Client client = data.getClient();
			String groupName = (String) groupList.getSelectedItem();
			labelGroupName.setText("ID = " + idgroup.get(groupName));
			List<ExperimenterWrapper> members = new ArrayList<>();
			try {
				GroupWrapper fullGroup = client.getGroup(groupName);
				members = fullGroup.getExperimenters();
			} catch (ExecutionException | ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			}
			userIds = new HashMap<>();
			userList.removeAllItems();
			userList.addItem("All members");
			for (ExperimenterWrapper member : members) {
				userList.addItem(member.getUserName());
				userIds.put(member.getUserName(), member.getId());
			}
		}

	}

	class ComboUserListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String username = (String) userList.getSelectedItem();
			List maps = userProjectsAndDatasets(username, userIds);
			idmap = (Map<Long, ArrayList<Long>>) maps.get(0);
			projname = (Map<Long, String>) maps.get(1);
			idproj = hashToMap(projname, idproj);
			dataname = (Map<Long, String>) maps.get(2);
			idata = hashToMap(dataname, idata);
			project_ids = idproj.keySet();
			projectListIn.removeAllItems();
			projectListOutNew.removeAllItems();
			projectListOutExist.removeAllItems();
			for (String project_id : project_ids) {
				projectListIn.addItem(project_id);
				projectListOutNew.addItem(project_id);
				projectListOutExist.addItem(project_id);
			}
		}

	}

	class EnterInOutListener implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (omero.isSelected()) {
				panelInput.add(input2a);
				panelInput.remove(input2b);
				data.setInputOnOMERO(true);
			} else { //local.isSelected()
				panelInput.add(input2b);
				panelInput.remove(input2a);
				data.setInputOnOMERO(false);
			}
			BatchWindow.this.setVisible(true);
		}

	}

	class ComboInListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			int index = Math.max(0, projectListIn.getSelectedIndex());
			String projectIdIn = projectListIn.getItemAt(index);
			labelProjectinname.setText("ID = " + idproj.get(projectIdIn));
			datasetListIn.removeAllItems();
			for (Long datasetId : idmap.get(idproj.get(projectIdIn))) {
				datasetListIn.addItem(dataname.get(datasetId));
			}
		}

	}

	class ComboDataInListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String dataset_id_in = (String) datasetListIn.getSelectedItem();
			labelDatasetinname.setText(
					"ID = " + idata.get(dataset_id_in));
		}

	}

	class ComboOutExistListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			int index = Math.max(0, projectListOutExist.getSelectedIndex());
			String project_id_out = projectListOutExist.getItemAt(index);
			labelExistprojectname.setText("ID = " + idproj.get(project_id_out));
			datasetListOutExist.removeAllItems();
			for (Long dataset_id : idmap.get(idproj.get(project_id_out))) {
				datasetListOutExist.addItem(dataname.get(dataset_id));
			}
		}

	}

	class ComboDataOutExistListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String dataset_id_out = (String) datasetListOutExist
					.getSelectedItem();
			labelExistdatasetname.setText(
					"ID = " + idata.get(dataset_id_out));
		}

	}

	class ComboOutNewListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String project_id_out = (String) projectListOutNew
					.getSelectedItem();
			labelNewprojectname.setText(
					"ID = " + idproj.get(project_id_out));
		}

	}

	class BoutonInputFolderListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			JFileChooser inputchoice = new JFileChooser();
			inputchoice.setDialogTitle("Choose the input directory");
			inputchoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int returnVal = inputchoice.showOpenDialog(null);
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File absindir = new File(inputchoice.getSelectedFile()
													.getAbsolutePath());
				// verify if record exist (handwritten case)
				if (absindir.exists() && absindir.isDirectory()) {
					inputfolder.setText(absindir.toString());
				} else {
					//find a way to prevent JFileChooser closure?
					errorWindow("Input: \nThe directory doesn't exist"); // new JOptionPane(inputchoice);
				}
			}
			if (returnVal == JFileChooser.CANCEL_OPTION &&
				inputfolder.getText() == "") {
				// warn user if macro selection canceled without any previous choice
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
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File absfile = new File(macrochoice.getSelectedFile()
												   .getAbsolutePath());
				if (absfile.exists() && !absfile.isDirectory()) {
					macro.setText(absfile.toString());
				} else {
					//find a way to prevent JFileChooser closure?
					warningWindow("Macro: \nThe file doesn't exist"); //new JOptionPane(macrochoice);
				}
			}
			if (returnVal == JFileChooser.CANCEL_OPTION &&
				macro.getText() == "") {
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
				File absdir = new File(outputchoice.getSelectedFile()
												   .getAbsolutePath());
				if (absdir.exists() && absdir.isDirectory()) {
					directory.setText(absdir.toString());
				} else {
					////find a way to prevent JFileChooser closure?
					errorWindow("Output: \nThe directory doesn't exist");
				}
			}
			if (returnVal == JFileChooser.CANCEL_OPTION &&
				directory.getText() == "") {
				warningWindow("Output: \nNo directory selected");
			}
		}

	}

	class CheckInOutListener implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (checkresfile_ima.isSelected()) {
				panelOutput.remove(output3b);
				panelOutput.add(output1);
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
			} else {
				panelOutput.remove(output1);
				panelOutput.remove(output3a1);
				panelOutput.remove(output3a);
				panelOutput.remove(output3a1);
				panelOutput.remove(output3a2);
				panelOutput.remove(output3b);
				if (checkoutline.isSelected()) {
					panelOutput.add(output3b);
				} else {
					panelOutput.remove(output3b);
				}
			}
			BatchWindow.this.setVisible(true);
		}

	}

	class BoutonValiderDataListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {

			// initiation of success variables
			Boolean inputdata = null;
			Boolean macrodata = null;
			Boolean omerorecord = null;
			Boolean localrecord = null;
			Boolean recordtype = null;
			Boolean sens = null;

			// input data
			if (omero.isSelected()) {
				String inputDatasetId = (String) datasetListIn.getSelectedItem();
				if (inputDatasetId == null) {
					errorWindow("Input: \nNo dataset selected");
				} else {
					data.setInputDatasetId(idata.get(inputDatasetId));
					inputdata = true;
				}
			} else { // local.isSelected()
				if (inputfolder.getText().equals("")) {
					errorWindow("Input: \nNo directory selected");
				} else {
					directory_in = inputfolder.getText();
					File directory_inf = new File(directory_in);
					if (directory_inf.exists() && directory_inf.isDirectory()) {
						inputdata = true;
					} else {
						errorWindow("Input: \nThe directory " + directory_in +
									" doesn't exist");
					}
				}
			}

			// macro file (mandatory)
			if (macro.getText().equals("")) {
				errorWindow("Macro: \nNo macro selected");
			} else {
				macro_chosen = macro.getText();
				File macrof = new File(macro_chosen);
				if (macrof.exists() && !macrof.isDirectory()) {
					macrodata = true;
				} else {
					errorWindow("Macro: \nThe file " + macro_chosen +
								" doesn't exist");
				}
			}

			// suffix
			extension_chosen = extension.getText();

			// record type
			if (checkinline.isSelected()) { // inline record
				if (exist.isSelected()) { // existing dataset
					outputDatasetId = idata
							.get(datasetListOutExist.getSelectedItem());
					if (outputDatasetId == null) {
						errorWindow("Output: \nNo dataset selected");
						omerorecord = false;
					} else {
						omerorecord = true;
					}
				} else { // new dataset
					project_id_out = idproj
							.get(projectListOutNew.getSelectedItem());
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
					if (directory_outf.exists() &&
						directory_outf.isDirectory()) {
						localrecord = true;
					} else {
						errorWindow("Output: \nThe directory " + directory_out +
									" doesn't exist");
						localrecord = false;
					}
				}
			}

			if (!checkinline.isSelected() && !checkoutline
					.isSelected()) { // omerorecord == null && localrecord = null
				errorWindow("Output: \nYou have to choose the localisation to save the results");
			} else if ((omerorecord || omerorecord == null) &&
					   (localrecord || localrecord ==
									   null)) { // true means selected and ok, null means not selected, false means selected but pb
				recordtype = true;
			}

			if (!checkresfile_res.isSelected() &&
				!checkresfile_roi.isSelected() &&
				!checkresfile_ima.isSelected()) { // No query
				errorWindow("Macro: \nYou have to choose almost one output");
			} else {
				sens = true;
			}

			if (local.isSelected() && checkinline.isSelected() &&
				!checkresfile_ima.isSelected()) { // Impossible to upload
				errorWindow("Output: \nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO");
				sens = false;
			} else {
				sens = true;
			}

			//
			if (inputdata && macrodata && recordtype && sens) {
				try {
					data.setSaveResults(checkresfile_res.isSelected());
					data.setSaveROIs(checkresfile_roi.isSelected());
					data.setOutputDatasetId(outputDatasetId);
					ProcessingDialog processingDialog = new ProcessingDialog();
					BatchRunner progress = new BatchRunner(data, processingDialog);
					progress.start();
				} catch (Exception e2) {
					errorWindow(e2.getMessage());
				}
			}

		}

	}

}