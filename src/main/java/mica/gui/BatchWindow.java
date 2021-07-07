package mica.gui;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import ij.IJ;
import mica.process.BatchListener;
import mica.process.BatchRunner;

import javax.swing.*;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static javax.swing.JOptionPane.showMessageDialog;

public class BatchWindow extends JFrame implements BatchListener {
	private final JComboBox<String> groupList = new JComboBox<>();
	private final JComboBox<String> userList = new JComboBox<>();
	private final JLabel labelGroupName = new JLabel();
	private final JButton start = new JButton("Start");

	// choices of input images
	private final JPanel panelInput = new JPanel();
	private final JPanel input2a = new JPanel();
	private final JPanel input2b = new JPanel();

	private final JRadioButton omero = new JRadioButton("OMERO");
	private final JRadioButton local = new JRadioButton("Local");
	private final JCheckBox checkresfileDelRoi = new JCheckBox(" Clear ROIs each time ");
	private final JCheckBox checkresfileLoadRoi = new JCheckBox(" Load ROIs ");

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JLabel labelProjectInName = new JLabel();
	private final JLabel labelInputDataset = new JLabel();

	// choice of the record
	private final JTextField inputfolder = new JTextField(20);
	private final JTextField macro = new JTextField(20);
	private final JCheckBox checkresfileIma = new JCheckBox(" The macro returns an image ");
	private final JCheckBox checkresfileRes = new JCheckBox(" The macro returns a results file (other than images)");
	private final JCheckBox checkresfileRoi = new JCheckBox(" The macro returns ROIs ");

	// choice of output
	private final JPanel panelOutput = new JPanel();
	private final JPanel output1 = new JPanel();
	private final JTextField extension = new JTextField(10);

	// Omero or local => checkbox
	private final JCheckBox checkinline = new JCheckBox("Omero");
	private final JCheckBox checkoutline = new JCheckBox("Local");

	// existing dataset
	private final JPanel output3a = new JPanel();
	private final JComboBox<String> projectListOut = new JComboBox<>();
	private final JComboBox<String> datasetListOut = new JComboBox<>();
	private final JLabel labelExistproject = new JLabel();
	private final JLabel labelExistdataset = new JLabel();

	// local
	private final JPanel output3b = new JPanel();
	private final JTextField directory = new JTextField(20);

	//variables to keep
	private final transient Client client;
	private final transient BatchRunner runner;
	private final transient List<GroupWrapper> groups;
	private String macroChosen;
	private String directoryOut;
	private String directoryIn;
	private Long outputDatasetId;
	private transient List<ProjectWrapper> groupProjects;
	private transient List<ProjectWrapper> userProjects;
	private transient List<DatasetWrapper> datasets;
	private transient List<ProjectWrapper> myProjects;
	private transient List<DatasetWrapper> myDatasets;
	private transient List<ExperimenterWrapper> users;
	private transient ExperimenterWrapper exp;


	public BatchWindow(BatchRunner runner) {
		super("Choice of input files and output location");
		this.runner = runner;
		this.client = runner.getClient();
		this.runner.addListener(this);

		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				super.windowClosing(e);
				client.disconnect();
			}
		});

		final String projectName = "Project Name: ";
		final String datasetName = "Dataset Name: ";
		this.setSize(600, 700);
		this.setLocationRelativeTo(null);

		try {
			exp = client.getUser(client.getUser().getUserName());
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error(e.getCause().getMessage());
		}
		groups = exp.getGroups();
		groups.removeIf(g -> g.getId() <= 2);

		for (GroupWrapper group : groups) {
			groupList.addItem(group.getName());
		}

		Font nameFont = new Font("Arial", Font.ITALIC, 10);
		Container cp = this.getContentPane();
		cp.setLayout(new BoxLayout(this.getContentPane(), BoxLayout.PAGE_AXIS));

		JPanel group = new JPanel();
		JLabel labelGroup = new JLabel("Group Name: ");
		group.add(labelGroup);
		group.add(groupList);
		groupList.addItemListener(this::updateGroup);

		group.add(labelGroupName);
		labelGroupName.setFont(nameFont);
		JPanel groupUsers = new JPanel();
		JLabel labelUser = new JLabel("User Name: ");
		groupUsers.add(labelUser);
		groupUsers.add(userList);
		userList.addItemListener(this::updateUser);

		// choix du groupe
		JPanel panelGroup = new JPanel();
		panelGroup.add(group);
		panelGroup.add(groupUsers);
		panelGroup.setBorder(BorderFactory.createTitledBorder("Group"));
		cp.add(panelGroup);

		JPanel input1 = new JPanel();
		JLabel labelEntertype = new JLabel("Where to get files to analyze :");
		input1.add(labelEntertype);
		input1.add(omero);
		ButtonGroup indata = new ButtonGroup();
		indata.add(omero);
		omero.addItemListener(this::updateInput);
		input1.add(local);
		indata.add(local);
		local.addItemListener(this::updateInput);
		JLabel labelProjectIn = new JLabel(projectName);
		input2a.add(labelProjectIn);
		input2a.add(projectListIn);
		projectListIn.addItemListener(this::updateInputProject);
		input2a.add(labelProjectInName);
		labelProjectInName.setFont(nameFont);
		JLabel labelDatasetIn = new JLabel(datasetName);
		input2a.add(labelDatasetIn);
		input2a.add(datasetListIn);
		datasetListIn.addItemListener(this::updateInputDataset);
		input2a.add(labelInputDataset);
		labelInputDataset.setFont(nameFont);
		input2a.add(checkresfileLoadRoi);
		input2a.add(checkresfileDelRoi);

		input2b.add(inputfolder);
		inputfolder.setMaximumSize(new Dimension(300, 30));
		JButton inputfolderBtn = new JButton("Images directory");
		input2b.add(inputfolderBtn);
		inputfolderBtn.addActionListener(e -> chooseDirectory(inputfolder));
		panelInput.add(input1);
		omero.setSelected(true);
		panelInput.setLayout(new BoxLayout(panelInput, BoxLayout.PAGE_AXIS));
		panelInput.setBorder(BorderFactory.createTitledBorder("Input"));
		cp.add(panelInput);

		JPanel macro1 = new JPanel();
		macro1.add(macro);
		macro.setMaximumSize(new Dimension(300, 30));
		JButton macroBtn = new JButton("Macro file");
		macro1.add(macroBtn);
		macroBtn.addActionListener(e -> chooseMacro());
		JPanel macro2 = new JPanel();
		macro2.setLayout(new BoxLayout(macro2, BoxLayout.LINE_AXIS));
		macro2.add(checkresfileIma);
		JPanel macro3 = new JPanel();
		macro3.setLayout(new BoxLayout(macro3, BoxLayout.LINE_AXIS));
		macro3.add(checkresfileRes);
		JPanel macro4 = new JPanel();
		macro4.setLayout(new BoxLayout(macro4, BoxLayout.LINE_AXIS));
		macro4.add(checkresfileRoi);
		//choice of the macro
		JPanel panelMacro = new JPanel();
		panelMacro.add(macro1);
		panelMacro.add(macro2);
		panelMacro.add(macro3);
		panelMacro.add(macro4);
		panelMacro.setLayout(new BoxLayout(panelMacro, BoxLayout.PAGE_AXIS));
		panelMacro.setBorder(BorderFactory.createTitledBorder("Macro"));
		cp.add(panelMacro);

		JLabel labelExtension = new JLabel("Suffix of output files :");
		output1.add(labelExtension);
		output1.add(extension);
		extension.setText("_macro");

		JPanel output2 = new JPanel();
		JLabel labelRecordoption = new JLabel("Where to save results :");
		output2.add(labelRecordoption);
		output2.add(checkinline);
		checkinline.addItemListener(new CheckInOutListener());
		output2.add(checkoutline);
		checkoutline.addItemListener(new CheckInOutListener());

		output3a.add(labelExistproject);
		output3a.add(projectListOut);
		projectListOut.addItemListener(this::updateOutputProject);
		JLabel labelExistProjectName = new JLabel();
		output3a.add(labelExistProjectName);
		labelExistProjectName.setFont(nameFont);
		output3a.add(labelExistdataset);
		output3a.add(datasetListOut);
		datasetListOut.addItemListener(this::updateOutputDataset);
		JLabel labelExistDatasetName = new JLabel();
		output3a.add(labelExistDatasetName);
		labelExistDatasetName.setFont(nameFont);
		JButton newDataset = new JButton("New");
		newDataset.addActionListener(this::createNewDataset);
		output3a.add(newDataset);

		output3b.add(directory);
		directory.setMaximumSize(new Dimension(300, 30));
		JButton directoryBtn = new JButton("Output directory");
		output3b.add(directoryBtn);
		directoryBtn.addActionListener(e -> chooseDirectory(directory));
		//
		panelOutput.add(output2);
		panelOutput.setLayout(new BoxLayout(panelOutput, BoxLayout.PAGE_AXIS));
		panelOutput.setBorder(BorderFactory.createTitledBorder("Output"));
		cp.add(panelOutput);

		// validation button
		JPanel panelBtn = new JPanel();
		panelBtn.add(start);
		start.addActionListener(e -> start());
		cp.add(panelBtn);

		long groupId = client.getCurrentGroupId();
		int index;
		for (index = 0; index < groups.size(); index++) {
			if (groups.get(index).getId() == groupId) break;
		}
		groupList.setSelectedIndex(-1);
		groupList.setSelectedIndex(index);

		this.setVisible(true);
	}


	public void errorWindow(String message) {
		showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
	}


	public void warningWindow(String message) {
		showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
	}


	public void userProjectsAndDatasets(String username, long userId) {
		if (username.equals("All members")) {
			userProjects = groupProjects;
		} else {
			userProjects = groupProjects.stream()
										.filter(project -> project.getOwner().getId() == userId)
										.collect(Collectors.toList());
		}
		myProjects = groupProjects.stream()
								  .filter(project -> project.getOwner().getId() == exp.getId())
								  .collect(Collectors.toList());
	}


	private void updateInputDataset(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int datasetId = ((JComboBox<?>) source).getSelectedIndex();
				DatasetWrapper dataset = datasets.get(datasetId);
				labelInputDataset.setText("ID = " + dataset.getId());
			}
		}
	}


	private void updateInputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int projectIdIn = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(projectIdIn);
				this.datasets = project.getDatasets();
				labelProjectInName.setText("ID = " + project.getId());
				datasetListIn.removeAllItems();
				for (DatasetWrapper dataset : project.getDatasets()) {
					datasetListIn.addItem(dataset.getName());
				}
				if (!this.datasets.isEmpty()) datasetListIn.setSelectedIndex(0);
			}
		}
	}


	private void updateOutputDataset(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int datasetId = ((JComboBox<?>) source).getSelectedIndex();
				DatasetWrapper dataset = myDatasets.get(datasetId);
				labelExistdataset.setText("ID = " + dataset.getId());
			}
		}
	}


	private void updateOutputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int projectIdIn = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(projectIdIn);
				this.myDatasets = project.getDatasets();
				labelExistproject.setText("ID = " + project.getId());
				datasetListOut.removeAllItems();
				for (DatasetWrapper dataset : project.getDatasets()) {
					datasetListOut.addItem(dataset.getName());
				}
				if (!this.datasets.isEmpty()) datasetListOut.setSelectedIndex(0);
			}
		}
	}


	private void createNewDataset(ActionEvent e) {
		int index = projectListOut.getSelectedIndex();
		ProjectWrapper project = myProjects.get(index);
		long id = -1;
		String name = (String)JOptionPane.showInputDialog(this,
														  "New dataset name:",
														  "Create a new dataset",
														  JOptionPane.QUESTION_MESSAGE,
														  null,
														  null,
														  null);
		try {
			DatasetWrapper newDataset = project.addDataset(client, name, "");
			id = newDataset.getId();
		} catch (ExecutionException | ServiceException | AccessException exception) {
			warningWindow("Could not create dataset: " + exception.getMessage());
		}
		projectListOut.setSelectedIndex(-1);
		projectListOut.setSelectedIndex(index);
		for(int i=0; i<myDatasets.size(); i++) {
			if(myDatasets.get(i).getId() == id){
				datasetListOut.setSelectedIndex(i);
				break;
			}
		}
	}


	private void updateUser(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			String username = (String) userList.getSelectedItem();
			int index = userList.getSelectedIndex() - 1;
			long userId = -1;
			if (index >= 0) userId = users.get(index).getId();
			userProjectsAndDatasets(username, userId);
			projectListIn.removeAllItems();
			projectListOut.removeAllItems();
			datasetListIn.removeAllItems();
			datasetListOut.removeAllItems();
			for (ProjectWrapper project : userProjects) {
				projectListIn.addItem(project.getName());
			}
			for (ProjectWrapper project : myProjects) {
				projectListOut.addItem(project.getName());
			}
			if (!userProjects.isEmpty()) projectListIn.setSelectedIndex(0);
			if (!myProjects.isEmpty()) projectListOut.setSelectedIndex(0);
		}
	}


	private void updateGroup(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = groupList.getSelectedIndex();
			long id = groups.get(index).getId();
			String groupName = groups.get(index).getName();
			client.switchGroup(id);

			groupProjects = new ArrayList<>();
			try {
				groupProjects = client.getProjects();
			} catch (ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			}

			labelGroupName.setText("ID = " + id);
			try {
				GroupWrapper group = client.getGroup(groupName);
				users = group.getExperimenters();
			} catch (ExecutionException | ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			}
			userList.removeAllItems();

			userList.addItem("All members");
			int selected = 0;
			for (ExperimenterWrapper user : users) {
				userList.addItem(user.getUserName());
				if (user.getId() == exp.getId()) {
					selected = users.indexOf(user) + 1;
				}
			}
			userList.setSelectedIndex(selected);
		}
	}


	private void updateInput(ItemEvent e) {
		if (omero.isSelected()) {
			panelInput.add(input2a);
			panelInput.remove(input2b);
			runner.setInputOnOMERO(true);
		} else { //local.isSelected()
			panelInput.add(input2b);
			panelInput.remove(input2a);
			runner.setInputOnOMERO(false);
		}
		BatchWindow.this.setVisible(true);
	}


	private void chooseDirectory(JTextField textField) {
		JFileChooser outputchoice = new JFileChooser();
		outputchoice.setDialogTitle("Choose the directory");
		outputchoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = outputchoice.showOpenDialog(null);
		outputchoice.setAcceptAllFileFilterUsed(false); // ????
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absdir = new File(outputchoice.getSelectedFile()
											   .getAbsolutePath());
			if (absdir.exists() && absdir.isDirectory()) {
				textField.setText(absdir.toString());
			} else {
				////find a way to prevent JFileChooser closure?
				errorWindow("Output: \nThe directory doesn't exist");
			}
		}
		if (returnVal == JFileChooser.CANCEL_OPTION &&
			textField.getText().equals("")) {
			warningWindow("Output: \nNo directory selected");
		}
	}


	private void chooseMacro() {
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
				warningWindow("Macro: \nThe file doesn't exist");
			}
		}
		if (returnVal == JFileChooser.CANCEL_OPTION &&
			macro.getText().equals("")) {
			// warn user if macro selection canceled without any previous choice
			warningWindow("Macro: \nNo macro selected");
		}
	}


	@Override
	public void onThreadFinished() {
		start.setEnabled(true);
	}


	public void start() {

		// initiation of success variables
		boolean inputdata = false;
		boolean macrodata = false;
		boolean omerorecord = false;
		boolean localrecord = false;
		boolean recordtype = false;
		boolean sens;
		int index;

		// input data
		if (omero.isSelected()) {
			index = datasetListIn.getSelectedIndex();
			DatasetWrapper dataset = datasets.get(index);
			Long inputDatasetId = dataset.getId();
			runner.setInputDatasetId(inputDatasetId);
			inputdata = true;
		} else { // local.isSelected()
			if (inputfolder.getText().equals("")) {
				errorWindow("Input: \nNo directory selected");
			} else {
				directoryIn = inputfolder.getText();
				File directoryInf = new File(directoryIn);
				if (directoryInf.exists() && directoryInf.isDirectory()) {
					inputdata = true;
				} else {
					errorWindow("Input: \nThe directory " + directoryIn + " doesn't exist");
				}
			}
		}

		// macro file (mandatory)
		if (macro.getText().equals("")) {
			errorWindow("Macro: \nNo macro selected");
		} else {
			macroChosen = macro.getText();
			File macrof = new File(macroChosen);
			if (macrof.exists() && !macrof.isDirectory()) {
				macrodata = true;
			} else {
				errorWindow("Macro: \nThe file " + macroChosen + " doesn't exist");
			}
		}

		// suffix
		runner.setExtension(extension.getText());

		// record type
		if (checkinline.isSelected()) { // inline record
			index = datasetListOut.getSelectedIndex();
			if (index == -1 || index > datasets.size()) {
				errorWindow("Output: \nNo dataset selected");
				omerorecord = false;
			} else {
				omerorecord = true;
			}
			DatasetWrapper dataset = datasets.get(index);
			outputDatasetId = dataset.getId();
		}
		if (checkoutline.isSelected()) { // local record
			if (directory.getText().equals("")) {
				errorWindow("Output: \nNo directory selected");
				localrecord = false;
			} else {
				directoryOut = directory.getText();
				File directoryOutf = new File(directoryOut);
				if (directoryOutf.exists() &&
					directoryOutf.isDirectory()) {
					localrecord = true;
				} else {
					errorWindow("Output: \nThe directory " + directoryOut +
								" doesn't exist");
					localrecord = false;
				}
			}
		}

		if (!checkinline.isSelected() && !checkoutline.isSelected()) { // omerorecord == null && localrecord = null
			errorWindow("Output: \nYou have to choose the localisation to save the results");
		} else if ((omerorecord) ||
				   (localrecord)) { // true means selected and ok, null means not selected, false means selected but pb
			recordtype = true;
		}

		if (!checkresfileRes.isSelected() &&
			!checkresfileRoi.isSelected() &&
			!checkresfileIma.isSelected()) { // No query
			errorWindow("Macro: \nYou have to choose almost one output");
		} else {
			sens = true;
		}

		if (local.isSelected() && checkinline.isSelected() &&
			!checkresfileIma.isSelected()) { // Impossible to upload
			errorWindow("Output: \nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO");
			sens = false;
		} else {
			sens = true;
		}

		//
		if (inputdata && macrodata && recordtype && sens) {
			try {
				runner.setLoadROIS(checkresfileLoadRoi.isSelected());
				runner.setClearROIS(checkresfileDelRoi.isSelected());
				runner.setSaveImage(checkresfileIma.isSelected());
				runner.setSaveResults(checkresfileRes.isSelected());
				runner.setSaveROIs(checkresfileRoi.isSelected());
				if (checkinline.isSelected()) {
					runner.setOutputOnOMERO(true);
					runner.setOutputDatasetId(outputDatasetId);
				}
				if (checkoutline.isSelected()) {
					runner.setOutputOnLocal(true);
					runner.setDirectoryIn(directoryIn);
					runner.setDirectoryOut(directoryOut);
				}

				runner.setMacro(macroChosen);
				start.setEnabled(false);
				runner.start();
			} catch (Exception e2) {
				errorWindow(e2.getMessage());
			}
		}
	}


	class CheckInOutListener implements ItemListener {
		public void itemStateChanged(ItemEvent e) {
			if (checkresfileIma.isSelected()) {
				panelOutput.remove(output3b);
				panelOutput.add(output1);
				if (checkinline.isSelected()) {
					panelOutput.add(output3a);
				} else {
					panelOutput.remove(output3a);
				}
			} else {
				panelOutput.remove(output1);
				panelOutput.remove(output3a);
				panelOutput.remove(output3b);
			}
			if (checkoutline.isSelected()) {
				panelOutput.add(output3b);
			} else {
				panelOutput.remove(output3b);
			}
			BatchWindow.this.setVisible(true);
		}

	}


}