package mica.gui;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.meta.ExperimenterWrapper;
import fr.igred.omero.meta.GroupWrapper;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import omero.gateway.Gateway;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.ROIFacility;
import omero.gateway.model.ImageData;
import omero.gateway.model.ROIData;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
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

	// Omero
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
	private final transient Client client;
	// Ces champs peuvent être remplacés par un champ Client
	private final Gateway gate;
	//variables to keep
	private String macro_chosen;
	private String extension_chosen;
	private Long dataset_id_in;
	private String directory_out;
	private String directory_in;
	private Long dataset_id_out;
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
	private ArrayList MROIS;
	private ArrayList ROISL;
	private ArrayList<Long> ima_ids;
	private ArrayList<Long> images_ids;
	private SecurityContext ctx;
	private ExperimenterWrapper exp;


	public BatchWindow(Client client) {
		super("Choice of input files and output location");
		this.setSize(600, 700);
		this.setLocationRelativeTo(null);
		this.client = client;
		gate = client.getGateway();
		try {
			exp = client.getUser(client.getUser().getUserName());
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error(e.getCause().getMessage());
		}
		String username = client.getUser().getUserName();

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


	// for(ROIWrapper roi : image.getROIs(client)) client.delete(roi);
	public void deleteROIs(List<ImageWrapper> images) {
		System.out.println("ROIs deletion from Omero");
		for (ImageWrapper image : images) {
			try {
				List<ROIWrapper> rois = image.getROIs(client);
				for (ROIWrapper roi : rois) {
					client.delete(roi);
				}
			} catch (ExecutionException | OMEROServerError | ServiceException | AccessException exception) {
				IJ.log(exception.getMessage());
			} catch (InterruptedException e) {
				IJ.log(e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}


	public List<String> getImagesFromDirectory(String directory) {
		//""" List all image's paths contained in a directory """
		File dir = new File(directory);
		File[] files = dir.listFiles();
		ArrayList<String> pathsImagesIni = new ArrayList<>();
		for (File value : Objects.requireNonNull(files)) {
			String file = value.getAbsolutePath();
			pathsImagesIni.add(file);
		}
		return pathsImagesIni;
	}


	// Si on utilise une List<ImageWrapper>, on récupère les IDs directement.
	public ArrayList<Long> get_image_ids(Gateway gateway,
										 SecurityContext context,
										 Long dataset_id) {
		//""" List all image's ids contained in a Dataset """
		ArrayList<Long> image_ids = new ArrayList<>();
		try {
			BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
			ArrayList<Long> ids = new ArrayList<>();
			ids.add(dataset_id);
			Collection<ImageData> images = browse
					.getImagesForDatasets(context, ids);
			Iterator<ImageData> j = images.iterator();
			while (j.hasNext()) {
				image_ids.add(j.next().getId());
			}
		} catch (DSOutOfServiceException | DSAccessException | ExecutionException exception) {
			IJ.log(exception.getMessage());
		}
		return image_ids;
	}


	public void save_and_close_with_res(String image, String attach) {
		//IJ.selectWindow("Result") //depends if images are renamed or not in the macro

		IJ.saveAs("TIFF", image);
		//IJ.run("Close")
		IJ.selectWindow("Results");
		IJ.saveAs("Results", attach);
		//IJ.run("Close")
	}


	public void save_and_close_without_res(String image) {
		//IJ.selectWindow("Result") //depends if images are renamed or not in the macro
		IJ.saveAs("TIFF", image);
		//	IJ.run("Close")
	}


	public void save_results_only(String attach) {
		IJ.selectWindow("Results");
		IJ.saveAs("Results", attach);
		//	IJ.run("Close")
	}


	/*
	// Pour ROIs 2D/3D/4D :
	List<ROIWrapper> rois = ROIWrapper.fromImageJ(ijRois, "ROI");
	for(ROIWrapper roi : rois) {
		roi.setImage(image);
	}
	*/
	// Retrieves ROIS of an image, before their deletion and add it in an array
	ArrayList save_ROIS_2D(ArrayList AL_ROIS, Long image_id, ImagePlus imap) {
		System.out.println("saving ROIs");
		/*ROIReader rea = new ROIReader();
		Object roi_list = rea.readImageJROIFromSources(image_id, imap);
        AL_ROIS.add(roi_list);*/
		return AL_ROIS;
	}


	// Retrieves 3D ROIS of an image, before their deletion and add it in an array
	ArrayList save_ROIS_3D(ArrayList AL_ROIS, Long image_id, ImagePlus imap) {
        /*
		System.out.println("saving ROIs");
		ROIReader rea = new ROIReader();
		Object roi_list = rea.readImageJROIFromSources(image_id, imap);
		RoiManager rm = RoiManager.getInstance();
		Roi[] ij_rois;
		if (rm != null) {ij_rois = rm.getRoisAsArray();}
   		int number_4D_rois = 0;
		Map<String,int> roi_4D_id;
		Map<int,Object> final_roi_list;

		if (ij_rois != null) {
			for (Roi ij_roi : ij_rois) {
				if (ij_roi.getProperty("ROI") != null) {
          			int ij_4D_roi_id = Integer.parseInt(ij_roi.getProperty("ROI"));
          			number_4D_rois = Math.max(ij_4D_roi_id, number_4D_rois);
        			roi_4D_id.put(ij_roi.getName() , ij_4D_roi_id);
				}
			}
		}

		if (roi_list) {
			System.out.println("test 1");
        	for(ROIData roidata : roi_list) {
				System.out.println("test 2");
				Iterator<ROIData> i = roidata.getIterator();
            	while (i.hasNext()) {
					System.out.println i;
                	Iterator<ROIData> roi = i.next();
					System.out.println roi;
               		String name = roi[0].getText();
					System.out.println name;
                	if (name) {
                    	int idx_4D_roi = roi_4D_id.get(name)-1;;
						System.out.println idx_4D_roi;
                    	if (final_roi_list.get(idx_4D_roi)) {
                        	final_roi_list.get(idx_4D_roi).addShapeData(roi[0]);
							System.out.println ("oui");
						}
                    	else {
                        	final_roi_list.put(idx_4D_roi, roidata);
							System.out.println("non");
						}
					}
				}
			}
		}

		AL_ROIS.add(final_roi_list);*/
		return AL_ROIS;
	}


	String today_date() {
		SimpleDateFormat formatter = new SimpleDateFormat("HH-mm-ss-dd-MM-yyyy");
		Date date = new Date();
		String text_date = formatter.format(date);
		String[] tab_date = text_date.split("-", 4);
		return tab_date[3] + "_" + tab_date[0] + "h" + tab_date[1];
	}


	ArrayList<ArrayList> run_macro(List<ImageWrapper> images,
								   SecurityContext context,
								   String macro_chosen,
								   String extension_chosen,
								   String dir,
								   Boolean results,
								   Boolean sav_rois,
								   JLabel lab)
	throws ServiceException, AccessException, ExecutionException {
		//""" Run a macro on images and save the result """
		int size = images.size();
		String[] paths_images = new String[size];
		String[] paths_attach = new String[size];
		IJ.run("Close All");
		String appel = "0";
		MROIS = new ArrayList();
		ArrayList<Long> image_ids = new ArrayList<>();
		ArrayList<Boolean> tab_ima_res = new ArrayList<>();
		int index = 0;
		for (ImageWrapper image : images) {
			// Open the image
			lab.setText("image " + (index + 1) + "/" + images.size());
			long id = image.getId();
			image_ids.add(id);
			long gid = context.getGroupID();
			client.switchGroup(gid);
			client.getImage(id).toImagePlus(client);
			ImagePlus imp = IJ.getImage();
			long id_local = imp.getID();
			// Define paths
			String title = imp.getTitle();
			if ((title
					.matches("(.*)qptiff(.*)")))
				title = title.replace(".qptiff", "_");
			else title = FilenameUtils.removeExtension(title);
			String res =
					dir + File.separator + title + extension_chosen + ".tif";
			String attach =
					dir + File.separator + "Res_" +
					title + /* extension_chosen + */ "_" + today_date() +
					".xls";
			// Analyse the images.
			IJ.runMacroFile(macro_chosen, appel);
			appel = "1";
			// Save and Close the various components
			if (sav_rois) {
				// save of ROIs
				RoiManager rm = RoiManager.getInstance2();
				if (checkoutline.isSelected()) {  //  local save
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  today_date() + "_RoiSet.zip");
					if (checkresfile_ima
							.isSelected()) {  // image results expected
						if (results) {
							save_and_close_with_res(res, attach);
						} else {
							save_and_close_without_res(res);
						}
					} else {
						if (results) {
							save_results_only(attach);
						}
					}
				}
				if (checkinline.isSelected()) { // save on Omero
					Roi[] rois = rm.getRoisAsArray();
					boolean verif = false;
					for (Roi roi : rois) {
						if (roi.getProperties() != null) verif = true;
					}
					if (checkresfile_ima
							.isSelected()) { // image results expected
						// sauvegarde 3D
						if (verif) {
							if (results) {
								save_and_close_with_res(res, attach);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							} else {
								save_and_close_without_res(res);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (results) {
								save_and_close_with_res(res, attach);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							} else {
								save_and_close_without_res(res);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							}
						}
					} else {
						// sauvegarde 3D
						if (verif) {
							if (results) {
								save_results_only(attach);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							} else {
								MROIS = save_ROIS_3D(MROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (results) {
								save_results_only(attach);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							} else {
								MROIS = save_ROIS_2D(MROIS, id, imp);
							}
						}

					}
				}
			} else {
				if (checkresfile_ima.isSelected()) { // image results expected
					if (results) {
						save_and_close_with_res(res, attach);
					} else {
						save_and_close_without_res(res);
					}
				} else {
					if (results) {
						save_results_only(attach);
					}
				}
			}
			imp.changes = false; // Prevent "Save Changes?" dialog
			Boolean ima_res = true;
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				ima_res = false;
			} else {
				int new_id = imp
						.getID(); // result image have to be selected in the end of the macro
				if (new_id == id_local) {
					ima_res = false;
				}
			}
			IJ.run("Close All"); //  To do local and Omero saves on the same time
			paths_images[index] = res;
			paths_attach[index] = attach;
			tab_ima_res.add(ima_res);
		}
		ArrayList<ArrayList> tab = new ArrayList(Arrays.asList(paths_images, paths_attach, MROIS, image_ids, tab_ima_res));
		return tab;
	}


	ArrayList<ArrayList> run_macro_on_local_images(List<String> images,
												   String macro_chosen,
												   String extension_chosen,
												   String dir,
												   Boolean results,
												   Boolean sav_rois,
												   JLabel lab) {
		//""" Run a macro on images from local computer and save the result """
		int size = images.size();
		String[] paths_images = new String[size];
		String[] paths_attach = new String[size];
		IJ.run("Close All");
		String appel = "0";
		MROIS = new ArrayList();
		ArrayList<Boolean> tab_ima_res = new ArrayList<>();
		int index = 0;
		for (String Image : images) {
			// Open the image
			//IJ.open(Image);
			//IJ.run("Stack to Images");
			lab.setText("image " + (index + 1) + "/" + images.size());
			ImagePlus imp = IJ.openImage(Image);
			long id = imp.getID();
			IJ.run("Bio-Formats Importer",
				   "open=" + Image +
				   " autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
			int L = imp.getHeight();
			int H = imp.getWidth();
			int B = imp.getBitDepth();
			int F = imp.getFrame();
			int C = imp.getChannel();
			int S = imp.getSlice();
			IJ.createHyperStack(Image, H, L, C, S, F, B);
			//IJ.run("Images to Hyperstack");
			//imp.createHyperStack(Image,C,S,F,B);
			// Define paths
			String title = imp.getTitle();
			if ((title
					.matches("(.*)qptiff(.*)")))
				title = title.replace(".qptiff", "_");
			else title = FilenameUtils.removeExtension(title);
			String res =
					dir + File.separator + title + extension_chosen + ".tif";
			String attach =
					dir + File.separator + "Res_" +
					title + /* extension_chosen + */ "_" + today_date() +
					".xls";
			// Analyse the images
			IJ.runMacroFile(macro_chosen, appel);
			appel = "1";
			// Save and Close the various components
			if (sav_rois) {
				//  save of ROIs
				RoiManager rm = RoiManager.getInstance2();
				if (checkoutline.isSelected()) {  //  local save
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  today_date() + "_RoiSet.zip");
					if (checkresfile_ima.isSelected()) {
						if (results) {
							save_and_close_with_res(res, attach);
						} else {
							save_and_close_without_res(res);
						}
					} else {
						if (results) {
							save_results_only(attach);
						}
					}
				}
				if (checkinline.isSelected()) {  // save on Omero
					Roi[] rois = rm.getRoisAsArray();
					boolean verif = false;
					for (Roi roi : rois) {
						if (roi.getProperties() != null) verif = true;
					}
					if (checkresfile_ima
							.isSelected()) {  // image results expected
						// sauvegarde 3D
						if (verif) {
							if (results) {
								save_and_close_with_res(res, attach);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							} else {
								save_and_close_without_res(res);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (results) {
								save_and_close_with_res(res, attach);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							} else {
								save_and_close_without_res(res);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							}
						}
					} else {
						// sauvegarde 3D
						if (verif) {
							if (results) {
								save_results_only(attach);
								MROIS = save_ROIS_3D(MROIS, id, imp);
							} else {
								MROIS = save_ROIS_3D(MROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (results) {
								save_results_only(attach);
								MROIS = save_ROIS_2D(MROIS, id, imp);
							} else {
								MROIS = save_ROIS_2D(MROIS, id, imp);
							}
						}

					}
				}
			} else {
				if (checkresfile_ima.isSelected()) {  // image results expected
					if (results) {
						save_and_close_with_res(res, attach);
					} else {
						save_and_close_without_res(res);
					}
				} else {
					if (results) {
						save_results_only(attach);
					}
				}
			}
			imp.changes = false; // Prevent "Save Changes?" dialog
			Boolean ima_res = true;
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				ima_res = false;
			} else {
				int new_id = imp
						.getID(); // result image have to be selected in the end of the macro
				if (new_id == id) {
					ima_res = false;
				}
			}
			IJ.run("Close All"); //  To do local and Omero saves on the same time
			paths_images[index] = res;
			paths_attach[index] = attach;
			tab_ima_res.add(ima_res);
		}
		ArrayList<ArrayList> tab = new ArrayList(Arrays.asList(paths_images, paths_attach, MROIS, tab_ima_res));
		return tab;
	}


	// for(ROIWrapper roi : rois) image.save(roi);
	// Read the ROIS array and upload them on omero
	public int upload_ROIS(ArrayList AL_ROIS,
						   SecurityContext ctx,
						   Gateway gat,
						   Long id,
						   int indice)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		exp = client.getUser(client.getUser().getUserName());

		if (AL_ROIS.get(indice) !=
			null) { // && (AL_ROIS.get(indice)).size() > 0) { // Problem
			System.out.println("Importing ROIs");
			ROIFacility roi_facility = gat.getFacility(ROIFacility.class);
			roi_facility.saveROIs(ctx, id, exp
					.getId(), (Collection<ROIData>) AL_ROIS.get(indice));
			return indice + 1;
		}
		return indice;
	}


	// DatasetWrapper :
	// dataset.importImages(client, paths_images);
	public ArrayList<Long> import_images_in_dataset(ArrayList<String> paths_images,
													Long dataset_id,
													Gateway gateway,
													SecurityContext context,
													ArrayList ROISL,
													JLabel lab,
													Boolean sav_rois)
	throws Exception {
		//""" Import images in Omero server from paths_images and return ids of these images in dataset_id """
		//paths_images : String[]
		ArrayList<Long> initial_images_ids = get_image_ids(gateway, context, dataset_id);
		ArrayList<Long> images_ids = new ArrayList<>();

		DatasetWrapper dataset = client.getDataset(dataset_id_out);
		for (String path : paths_images) {
			dataset.importImages(client, path);
		}


		int indice = 0;
		ArrayList<Long> current_images_ids = get_image_ids(gateway, context, dataset_id);
		for (Long id : current_images_ids) {
			if (!initial_images_ids.contains(id)) {
				initial_images_ids.add(id);
				images_ids.add(id);
				if (sav_rois) {
					indice = upload_ROIS(ROISL, context, gateway, id, indice);
				}
			}
		}
		return images_ids;
	}


	public ArrayList<Long> import_Rois_in_image(ArrayList<Long> images_ids,
												Long dataset_id,
												Gateway gateway,
												SecurityContext context,
												ArrayList ROISL,
												JLabel lab)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		//""" Import Rois in Omero server on images and return ids of these images in dataset_id """
		int indice = 0;
		long ind = 1;
		for (Long id : images_ids) {
			indice = upload_ROIS(ROISL, context, gateway, id, indice);
			lab.setText("image " + ind + "/" + images_ids.size());
			ind = ind + 1;
		}
		return images_ids;
	}


	public void uploadTagFiles(List<String> paths, List<Long> imageIds) {
		//""" Attach result files from paths to images in omero """
		for (int i = 0; i < paths.size(); i++) {
			ImageWrapper image;
			try {
				image = client.getImage(imageIds.get(i));
				image.addFile(client, new File(paths.get(i)));
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
			} catch (InterruptedException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}


	public void delete_temp(String tmp_dir) {
		//""" Delete the local copy of temporary files and directory """
		File dir = new File(tmp_dir);
		File[] entries = dir.listFiles();
		for (File entry : entries) {
			entry.delete();
		}
		dir.delete();
	}


	class ComboGroupListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
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
			} else { //local.isSelected()
				panelInput.add(input2b);
				panelInput.remove(input2a);
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

	class BoutonValiderDataListener
			implements ActionListener {
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
				dataset_id_in = idata.get(datasetListIn.getSelectedItem());
				if (dataset_id_in == null) {
					errorWindow("Input: \nNo dataset selected");
				} else {
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
					dataset_id_out = idata
							.get(datasetListOutExist.getSelectedItem());
					if (dataset_id_out == null) {
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
					ProgressThread progress = new ProgressThread();
					progress.start();
				} catch (Exception e2) {
					errorWindow(e2.getMessage());
				}
			}

		}

	}

	class ImageTreatment extends JFrame {
		private final Container cp2 = this.getContentPane();
		private final JPanel Panel0 = new JPanel();
		private final JPanel Panel1 = new JPanel();
		private final JPanel Panel2 = new JPanel();
		private final JPanel Panel3 = new JPanel();
		private final JLabel warn_label = new JLabel("", SwingConstants.CENTER);
		private final JLabel prog_label = new JLabel("", SwingConstants.CENTER);
		private final JLabel state_label = new JLabel("", SwingConstants.CENTER);
		private final JButton InfoBtn = new JButton("OK");


		public ImageTreatment() {
			this.setTitle("Progression");
			this.setLocationRelativeTo(null);
			this.setSize(300, 200);

			Font warnfont = new Font("Arial", Font.PLAIN, 12);
			Font progfont = new Font("Arial", Font.BOLD, 12);
			warn_label
					.setText("<html> <body style='text-align:center;'> Warning: <br>Image processing can take time <br>depending on your network rate </body> </html>");
			warn_label.setFont(warnfont);
			prog_label.setFont(progfont);
			state_label.setFont(progfont);

			cp2.setLayout(new BoxLayout(cp2, BoxLayout.PAGE_AXIS));
			Panel0.add(warn_label);
			Panel1.add(prog_label);
			Panel2.add(state_label);
			Panel3.add(InfoBtn);
			InfoBtn.setEnabled(false);
			InfoBtn.addActionListener(new ProgressListener());
			cp2.add(Panel0);
			cp2.add(Panel1);
			cp2.add(Panel2);
			cp2.add(Panel3);
			this.setVisible(true);
		}


		public void runTreatment() {

			boolean results;
			boolean sav_rois;
			results = checkresfile_res.isSelected();
			sav_rois = checkresfile_roi.isSelected();
			ArrayList<String> paths_images;
			ArrayList<String> pathsAttach;

			try {

				if (!checkoutline.isSelected()) {
					prog_label.setText("Temporary directory creation...");
					Path directory_outf = Files
							.createTempDirectory("Fiji_analyse");
					directory_out = directory_outf.toString();
				}

				if (omero.isSelected()) {
					prog_label.setText("Images recovery from Omero...");
					DatasetWrapper dataset = client.getDataset(dataset_id_in);
					List<ImageWrapper> images = dataset.getImages(client);
					prog_label.setText("Macro running...");
					ArrayList<ArrayList> paths = run_macro(images, ctx, macro_chosen, extension_chosen, directory_out, results, sav_rois, state_label);
					state_label.setText("");
					paths_images = paths.get(0);
					pathsAttach = paths.get(1);
					ROISL = paths.get(2);
					ima_ids = paths.get(3);
					ima_res = (Boolean) paths.get(4).get(0);
					if (checkresfile_del_roi.isSelected()) {
						deleteROIs(images);
					}
				} else {
					prog_label.setText("Images recovery from input folder...");
					List<String> images = getImagesFromDirectory(directory_in);
					prog_label.setText("Macro running...");
					List<ArrayList> paths = run_macro_on_local_images(images, macro_chosen, extension_chosen, directory_out, results, sav_rois, state_label);
					state_label.setText("");
					paths_images = paths.get(0);
					pathsAttach = paths.get(1);
					ROISL = paths.get(2);
					ima_res = (Boolean) paths.get(3).get(1);
				}

				if (diff.isSelected() && !ima_res) {
					prog_label.setText("New dataset creation...");
					ProjectWrapper project = client.getProject(project_id_out);
					DatasetWrapper dataset = project
							.addDataset(client, dataset_name_out, "");
					dataset_id_out = dataset.getId();
				}

				if (checkinline.isSelected()) {
					prog_label.setText("import on omero...");
					if (ima_res && checkresfile_ima.isSelected()) {
						images_ids = import_images_in_dataset(paths_images, dataset_id_out, gate, ctx, ROISL, state_label, sav_rois);
					}
					if (!checkresfile_ima.isSelected() &&
						checkresfile_roi.isSelected()) {
						images_ids = import_Rois_in_image(ima_ids, dataset_id_in, gate, ctx, ROISL, state_label);
					}
					if (results && !checkresfile_ima.isSelected() &&
						!checkresfile_roi.isSelected() &&
						checkresfile_res.isSelected()) {
						prog_label.setText("Attachement of results files...");

						uploadTagFiles(pathsAttach, ima_ids);
					} else if (results && checkresfile_ima.isSelected()) {
						prog_label.setText("Attachement of results files...");
						uploadTagFiles(pathsAttach, images_ids);
					}
					if (!ima_res && checkresfile_ima.isSelected()) {
						errorWindow("Impossible to save: \nOutput image must be different than input image");
					}
				}

				if (!checkoutline.isSelected()) {
					prog_label.setText("Temporary directory deletion...");
					delete_temp(directory_out);
				}

				prog_label.setText("Task completed!");
				InfoBtn.setEnabled(true);

			} catch (Exception e3) {
				if (e3.getMessage().equals("Macro canceled")) {
					this.dispose();
					IJ.run("Close");
				}
				errorWindow(e3.getMessage());
			}
		}


		class ProgressListener
				implements ActionListener {
			public void actionPerformed(ActionEvent e) {
				BatchWindow.this.dispose();
			}

		}

	}

	class ProgressThread extends Thread {
		public void run() {
			ImageTreatment action = new ImageTreatment();
			action.runTreatment();
		}

	}


}