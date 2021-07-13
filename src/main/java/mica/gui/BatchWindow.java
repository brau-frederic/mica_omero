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
	private final JCheckBox checkDelROIs = new JCheckBox(" Clear ROIs each time ");
	private final JCheckBox checkLoadROIs = new JCheckBox(" Load ROIs ");

	// choice of the dataSet
	private final JComboBox<String> projectListIn = new JComboBox<>();
	private final JComboBox<String> datasetListIn = new JComboBox<>();
	private final JLabel labelInputProject = new JLabel();
	private final JLabel labelInputDataset = new JLabel();

	// choice of the record
	private final JTextField inputFolder = new JTextField(20);
	private final JTextField macro = new JTextField(20);
	private final JCheckBox checkImage = new JCheckBox(" The macro returns an image ");
	private final JCheckBox checkResults = new JCheckBox(" The macro returns a results file (other than images)");
	private final JCheckBox checkROIs = new JCheckBox(" The macro returns ROIs ");

	// choice of output
	private final JPanel panelOutput = new JPanel();
	private final JPanel output1 = new JPanel();
	private final JTextField suffix = new JTextField(10);

	// Omero or local => checkbox
	private final JCheckBox onlineOutput = new JCheckBox("OMERO");
	private final JCheckBox localOutput = new JCheckBox("Local");

	// existing dataset
	private final JPanel output3a = new JPanel();
	private final JComboBox<String> projectListOut = new JComboBox<>();
	private final JComboBox<String> datasetListOut = new JComboBox<>();
	private final JLabel labelOutputProject = new JLabel();
	private final JLabel labelOutputDataset = new JLabel();

	// local
	private final JPanel output3b = new JPanel();
	private final JTextField directory = new JTextField(20);

	//variables to keep
	private final transient Client client;
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


	public BatchWindow(Client client) {
		super("Choice of input files and output location");
		this.client = client;

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

		// Group selection
		JPanel panelGroup = new JPanel();
		panelGroup.add(group);
		panelGroup.add(groupUsers);
		panelGroup.setBorder(BorderFactory.createTitledBorder("Group"));
		cp.add(panelGroup);

		JPanel input1 = new JPanel();
		JLabel labelEnterType = new JLabel("Where to get files to analyze :");
		input1.add(labelEnterType);
		input1.add(omero);
		ButtonGroup inputData = new ButtonGroup();
		inputData.add(omero);
		omero.addItemListener(this::updateInput);
		input1.add(local);
		inputData.add(local);
		local.addItemListener(this::updateInput);
		JLabel labelProjectIn = new JLabel(projectName);
		input2a.add(labelProjectIn);
		input2a.add(projectListIn);
		projectListIn.addItemListener(this::updateInputProject);
		input2a.add(labelInputProject);
		labelInputProject.setFont(nameFont);
		JLabel labelDatasetIn = new JLabel(datasetName);
		input2a.add(labelDatasetIn);
		input2a.add(datasetListIn);
		datasetListIn.addItemListener(this::updateInputDataset);
		input2a.add(labelInputDataset);
		labelInputDataset.setFont(nameFont);
		input2a.add(checkLoadROIs);
		input2a.add(checkDelROIs);

		input2b.add(inputFolder);
		inputFolder.setMaximumSize(new Dimension(300, 30));
		JButton inputFolderBtn = new JButton("Images directory");
		input2b.add(inputFolderBtn);
		inputFolderBtn.addActionListener(e -> chooseDirectory(inputFolder));
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
		checkImage.addItemListener(this::updateOutput);
		macro2.add(checkImage);
		JPanel macro3 = new JPanel();
		macro3.setLayout(new BoxLayout(macro3, BoxLayout.LINE_AXIS));
		checkResults.addItemListener(this::updateOutput);
		macro3.add(checkResults);
		JPanel macro4 = new JPanel();
		macro4.setLayout(new BoxLayout(macro4, BoxLayout.LINE_AXIS));
		checkROIs.addItemListener(this::updateOutput);
		macro4.add(checkROIs);
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
		output1.add(suffix);
		suffix.setText("_macro");

		JPanel output2 = new JPanel();
		JLabel labelRecordOption = new JLabel("Where to save results :");
		output2.add(labelRecordOption);
		output2.add(onlineOutput);
		onlineOutput.addItemListener(this::updateOutput);
		output2.add(localOutput);
		localOutput.addItemListener(this::updateOutput);

		output3a.add(labelOutputProject);
		output3a.add(projectListOut);
		projectListOut.addItemListener(this::updateOutputProject);
		JLabel labelExistProjectName = new JLabel();
		output3a.add(labelExistProjectName);
		labelExistProjectName.setFont(nameFont);
		output3a.add(labelOutputDataset);
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
		start.addActionListener(this::start);
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


	private String idLabel(long id) {
		return String.format("ID = %d", id);
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
				labelInputDataset.setText(idLabel(dataset.getId()));
			}
		}
	}


	private void updateInputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = userProjects.get(index);
				this.datasets = project.getDatasets();
				labelInputProject.setText(idLabel(project.getId()));
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
				labelOutputDataset.setText(idLabel(dataset.getId()));
			}
		}
	}


	private void updateOutputProject(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			Object source = e.getSource();
			if (source instanceof JComboBox<?>) {
				int index = ((JComboBox<?>) source).getSelectedIndex();
				ProjectWrapper project = myProjects.get(index);
				this.myDatasets = project.getDatasets();
				labelOutputProject.setText(idLabel(project.getId()));
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
		String name = (String) JOptionPane.showInputDialog(this,
														   "New dataset name:",
														   "Create a new dataset",
														   JOptionPane.QUESTION_MESSAGE,
														   null,
														   null,
														   null);
		if (name == null) return;
		try {
			DatasetWrapper newDataset = project.addDataset(client, name, "");
			id = newDataset.getId();
		} catch (ExecutionException | ServiceException | AccessException exception) {
			warningWindow("Could not create dataset: " + exception.getMessage());
		}
		projectListOut.setSelectedIndex(-1);
		projectListOut.setSelectedIndex(index);
		for (int i = 0; i < myDatasets.size(); i++) {
			if (myDatasets.get(i).getId() == id) {
				datasetListOut.setSelectedIndex(i);
				break;
			}
		}
	}


	private void updateUser(ItemEvent e) {
		if (e.getStateChange() == ItemEvent.SELECTED) {
			int index = userList.getSelectedIndex();
			String username = userList.getItemAt(index);
			long userId = -1;
			if (index >= 1) userId = users.get(index - 1).getId();
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

			labelGroupName.setText(idLabel(id));
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
		} else { //local.isSelected()
			panelInput.add(input2b);
			panelInput.remove(input2a);
		}
		BatchWindow.this.setVisible(true);
	}


	private void chooseDirectory(JTextField textField) {
		JFileChooser outputChoice = new JFileChooser();
		outputChoice.setDialogTitle("Choose the directory");
		outputChoice.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = outputChoice.showOpenDialog(null);
		outputChoice.setAcceptAllFileFilterUsed(false); // ????
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absDir = new File(outputChoice.getSelectedFile()
											   .getAbsolutePath());
			if (absDir.exists() && absDir.isDirectory()) {
				textField.setText(absDir.toString());
			} else {
				////find a way to prevent JFileChooser closure?
				errorWindow(String.format("Output:%nThe directory doesn't exist"));
			}
		}
	}


	private void chooseMacro() {
		JFileChooser macroChoice = new JFileChooser();
		macroChoice.setDialogTitle("Choose the macro file");
		macroChoice.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int returnVal = macroChoice.showOpenDialog(null);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File absFile = new File(macroChoice.getSelectedFile().getAbsolutePath());
			if (absFile.exists() && !absFile.isDirectory()) {
				macro.setText(absFile.toString());
			} else {
				//find a way to prevent JFileChooser closure?
				warningWindow(String.format("Macro:%nThe file doesn't exist"));
			}
		}
	}


	@Override
	public void onThreadFinished() {
		start.setEnabled(true);
	}


	public void start(ActionEvent e) {
		ProgressDialog progress = new ProgressDialog();
		BatchRunner runner = new BatchRunner(client, progress);
		runner.addListener(this);

		// initiation of success variables
		boolean checkInput;
		boolean checkMacro = getMacro();
		boolean checkOutput = getOutput();

		// input data
		if (omero.isSelected()) {
			runner.setInputOnOMERO(true);
			int index = datasetListIn.getSelectedIndex();
			DatasetWrapper dataset = datasets.get(index);
			long inputDatasetId = dataset.getId();
			runner.setInputDatasetId(inputDatasetId);
			runner.setOutputDatasetId(inputDatasetId);
			checkInput = true;
		} else { // local.isSelected()
			runner.setInputOnOMERO(false);
			checkInput = getLocalInput();
		}

		// suffix
		runner.setSuffix(suffix.getText());

		if (checkInput && checkMacro && checkOutput) {
			runner.setLoadROIS(checkLoadROIs.isSelected());
			runner.setClearROIS(checkDelROIs.isSelected());
			runner.setSaveImage(checkImage.isSelected());
			runner.setSaveResults(checkResults.isSelected());
			runner.setSaveROIs(checkROIs.isSelected());
			if (onlineOutput.isSelected()) {
				runner.setOutputOnOMERO(true);
				if (checkImage.isSelected()) {
					runner.setOutputDatasetId(outputDatasetId);
				}
			}
			if (localOutput.isSelected()) {
				runner.setOutputOnLocal(true);
				runner.setDirectoryIn(directoryIn);
				runner.setDirectoryOut(directoryOut);
			}

			runner.setMacro(macroChosen);
			start.setEnabled(false);
			try {
				runner.start();
			} catch (Exception exception) {
				errorWindow(exception.getMessage());
			}
		}
	}


	private void updateOutput(ItemEvent e) {
		if (checkImage.isSelected()) {
			panelOutput.remove(output3b);
			panelOutput.add(output1);
			if (onlineOutput.isSelected()) {
				panelOutput.add(output3a);
			} else {
				panelOutput.remove(output3a);
			}
		} else {
			panelOutput.remove(output1);
			panelOutput.remove(output3a);
			panelOutput.remove(output3b);
		}
		if (localOutput.isSelected()) {
			panelOutput.add(output3b);
		} else {
			panelOutput.remove(output3b);
		}
		this.setVisible(true);
	}


	private boolean getLocalInput() {
		boolean check = false;
		if (inputFolder.getText().equals("")) {
			errorWindow(String.format("Input:%nNo directory selected"));
		} else {
			directoryIn = inputFolder.getText();
			File directoryInF = new File(directoryIn);
			if (directoryInF.exists() && directoryInF.isDirectory()) {
				check = true;
			} else {
				String msg = String.format("Input:%n The directory %s does not exist", directoryIn);
				errorWindow(msg);
			}
		}
		return check;
	}


	private boolean getMacro() {
		boolean check = false;
		// macro file (mandatory)
		if (macro.getText().equals("")) {
			errorWindow(String.format("Macro:%nNo macro selected"));
		} else {
			macroChosen = macro.getText();
			File macroFile = new File(macroChosen);
			if (macroFile.exists() && !macroFile.isDirectory()) {
				check = true;
			} else {
				String msg = String.format("Macro:%n The file %s does not exist", macroChosen);
				errorWindow(msg);
			}
		}
		return check;
	}


	private boolean getOMEROOutput() {
		boolean check = false;
		if (onlineOutput.isSelected()) {
			int index = datasetListOut.getSelectedIndex();
			if (index == -1 || index > datasets.size()) {
				errorWindow(String.format("Output:%nNo dataset selected"));
			} else {
				check = true;
				DatasetWrapper dataset = datasets.get(index);
				outputDatasetId = dataset.getId();
			}
		}
		return check;
	}


	private boolean getLocalOutput() {
		boolean check = false;
		if (localOutput.isSelected()) {
			if (directory.getText().equals("")) {
				errorWindow(String.format("Output:%nNo directory selected"));
			} else {
				directoryOut = directory.getText();
				File directoryOutFile = new File(directoryOut);
				if (directoryOutFile.exists() && directoryOutFile.isDirectory()) {
					check = true;
				} else {
					String msg = String.format("Output:%n The directory %s does not exist", directoryOut);
					errorWindow(msg);
				}
			}
		}
		return check;
	}


	private boolean checkDeleteROIs() {
		boolean check = true;
		if (checkDelROIs.isSelected() && (!onlineOutput.isSelected()) || !checkROIs.isSelected()) {
			errorWindow(String.format("ROIs:%nYou can't clear ROIs if you don't save ROIs on OMERO"));
			check = false;
		}
		return check;
	}


	private boolean checkUploadLocalInput() {
		boolean check = true;
		if (local.isSelected() && onlineOutput.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Output:%nYou can't upload results file or ROIs on OMERO if your image isn't in OMERO"));
			check = false;
		}
		return check;
	}


	private boolean checkSelectedOutput() {
		boolean check = true;
		if (!checkResults.isSelected() && !checkROIs.isSelected() && !checkImage.isSelected()) {
			errorWindow(String.format("Macro:%nYou have to choose at least one output"));
			check = false;
		}
		return check;
	}


	private boolean getOutput() {
		boolean omeroCheck = getOMEROOutput();
		boolean localCheck = getLocalOutput();
		boolean check = omeroCheck || localCheck;

		if (!onlineOutput.isSelected() && !localOutput.isSelected()) {
			errorWindow(String.format("Output:%nYou have to choose the localisation to save the results"));
			check = false;
		}

		check &= checkSelectedOutput();
		check &= checkUploadLocalInput();
		check &= checkDeleteROIs();

		return check;
	}

}