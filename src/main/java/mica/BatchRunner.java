package mica;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.repository.ProjectWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import mica.gui.ProcessingDialog;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.ROIFacility;
import omero.gateway.model.ROIData;
import org.apache.commons.io.FilenameUtils;

import javax.swing.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class BatchRunner extends Thread {

	private final BatchData data;
	private final ProcessingDialog dialog;


	public BatchRunner(BatchData data) {
		this.data = data;
		this.dialog = null;
	}


	public BatchRunner(BatchData data, ProcessingDialog dialog) {
		this.data = data;
		this.dialog = dialog;
	}


	private void setDialogProg(String text) {
		if (dialog != null) dialog.setProg(text);
	}


	private void setDialogState(String text) {
		if (dialog != null) dialog.setState(text);
	}


	private void setDialogButtonState(boolean state) {
		if (dialog != null) dialog.setButtonState(state);
	}


	/**
	 * If this thread was constructed using a separate
	 * {@code Runnable} run object, then that
	 * {@code Runnable} object's {@code run} method is called;
	 * otherwise, this method does nothing and returns.
	 * <p>
	 * Subclasses of {@code Thread} should override this method.
	 *
	 * @see #start()
	 * @see #stop()
	 * @see #Thread(ThreadGroup, Runnable, String)
	 */
	@Override
	public void run() {
		Client client = data.getClient();
		boolean results = data.shouldSaveResults();
		boolean saveROIs = data.shouldSaveROIs();
		long inputDatasetId = data.getInputDatasetId();
		long outputDatasetId = data.getOutputDatasetId();
		List<String> pathsImages;
		List<String> pathsAttach;

		try {
			if (!checkoutline.isSelected()) {
				setDialogProg("Temporary directory creation...");
				Path directory_outf = Files
						.createTempDirectory("Fiji_analyse");
				directory_out = directory_outf.toString();
			}

			if (data.isInputOnOMERO()) {
				setDialogProg("Images recovery from Omero...");
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				setDialogProg("Macro running...");
				ArrayList<ArrayList> paths = run_macro(images, ctx, macro_chosen, extension_chosen, directory_out, results, saveROIs, stateLabel);
				setDialogState("");
				pathsImages = paths.get(0);
				pathsAttach = paths.get(1);
				ROISL = paths.get(2);
				ima_ids = paths.get(3);
				ima_res = (Boolean) paths.get(4).get(0);
				if (checkresfile_del_roi.isSelected()) {
					deleteROIs(images);
				}
			} else {
				setDialogProg("Images recovery from input folder...");
				List<String> images = getImagesFromDirectory(directory_in);
				setDialogProg("Macro running...");
				List<ArrayList> paths = run_macro_on_local_images(images, macro_chosen, extension_chosen, directory_out, results, saveROIs, stateLabel);
				setDialogState("");
				pathsImages = paths.get(0);
				pathsAttach = paths.get(1);
				ROISL = paths.get(2);
				ima_res = (Boolean) paths.get(3).get(1);
			}

			if (diff.isSelected() && !ima_res) {
				setDialogProg("New dataset creation...");
				ProjectWrapper project = client.getProject(project_id_out);
				DatasetWrapper dataset = project
						.addDataset(client, dataset_name_out, "");
				outputDatasetId = dataset.getId();
			}

			if (checkinline.isSelected()) {
				setDialogProg("import on omero...");
				if (ima_res && checkresfile_ima.isSelected()) {
					images_ids = importImagesInDataset(pathsImages, outputDatasetId, ROISL, saveROIs);
				}
				if (!checkresfile_ima.isSelected() &&
					checkresfile_roi.isSelected()) {
					images_ids = importRoisInImage(ima_ids, inputDatasetId, ROISL);
				}
				if (results && !checkresfile_ima.isSelected() &&
					!checkresfile_roi.isSelected() &&
					checkresfile_res.isSelected()) {
					setDialogProg("Attachement of results files...");

					uploadTagFiles(pathsAttach, ima_ids);
				} else if (results && checkresfile_ima.isSelected()) {
					setDialogProg("Attachement of results files...");
					uploadTagFiles(pathsAttach, images_ids);
				}
				if (!ima_res && checkresfile_ima.isSelected()) {
					errorWindow("Impossible to save: \nOutput image must be different than input image");
				}
			}

			if (!checkoutline.isSelected()) {
				setDialogProg("Temporary directory deletion...");
				delete_temp(directory_out);
			}

			setDialogProg("Task completed!");
			setDialogButtonState(true);

		} catch (Exception e3) {
			if (e3.getMessage().equals("Macro canceled")) {
				this.dispose();
				IJ.run("Close");
			}
			errorWindow(e3.getMessage());
		}
	}


	// DatasetWrapper :
	// dataset.importImages(client, pathsImages);
	public List<Long> importImagesInDataset(List<String> pathsImages,
											Long dataset_id,
											List ROISL,
											Boolean sav_rois)
	throws Exception {
		//""" Import images in Omero server from pathsImages and return ids of these images in dataset_id """
		//pathsImages : String[]
		List<Long> imageIds = new ArrayList<>();

		Client client = data.getClient();
		DatasetWrapper dataset = client.getDataset(dataset_id);
		for (String path : pathsImages) {
			List<ImageWrapper> newImages = dataset.importImages(client, path);
			newImages.forEach(image -> imageIds.add(image.getId()));
		}

		int indice = 0;
		for (Long id : imageIds) {
			indice = upload_ROIS(ROISL, id, indice);
		}
		return imageIds;
	}


	// for(ROIWrapper roi : image.getROIs(client)) client.delete(roi);
	public void deleteROIs(List<ImageWrapper> images) {
		Client client = data.getClient();
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
	public List<Long> getImageIds(Long datasetId) {
		//""" List all image's ids contained in a Dataset """
		Client client = data.getClient();
		List<Long> imageIds = new ArrayList<>();
		try {
			DatasetWrapper dataset = client.getDataset(datasetId);
			List<ImageWrapper> images = dataset.getImages(client);
			images.forEach(image -> imageIds.add(image.getId()));
		} catch (DSOutOfServiceException | DSAccessException exception) {
			IJ.log(exception.getMessage());
		}
		return imageIds;
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
		Client client = data.getClient();
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
	public int upload_ROIS(List AL_ROIS, Long id, int indice)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		Client client = data.getClient();

		if (AL_ROIS.get(indice) != null) { // && (AL_ROIS.get(indice)).size() > 0) { // Problem
			System.out.println("Importing ROIs");

			ROIFacility roi_facility = client.getGateway().getFacility(ROIFacility.class);
			roi_facility.saveROIs(client.getCtx(), id, client.getUser().getId(),
								  (Collection<ROIData>) AL_ROIS.get(indice));
			return indice + 1;
		}
		return indice;
	}


	public List<Long> importRoisInImage(List<Long> images_ids,
										Long dataset_id,
										List ROISL)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		//""" Import Rois in Omero server on images and return ids of these images in dataset_id """
		int indice = 0;
		long ind = 1;
		for (Long id : images_ids) {
			indice = upload_ROIS(ROISL, id, indice);
			setDialogState("image " + ind + "/" + images_ids.size());
			ind = ind + 1;
		}
		return images_ids;
	}


	public void uploadTagFiles(List<String> paths, List<Long> imageIds) {
		//""" Attach result files from paths to images in omero """
		Client client = data.getClient();
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

}
