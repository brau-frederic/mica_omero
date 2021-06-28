package mica.process;

import fr.igred.omero.Client;
import fr.igred.omero.GenericObjectWrapper;
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
import mica.BatchData;
import mica.BatchResults;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.ROIFacility;
import omero.gateway.model.ROIData;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class BatchRunner extends Thread {

	private final BatchData data;
	private final ProcessingProgress progress;
	private final BatchResults bResults;


	public BatchRunner(BatchData data) {
		super();
		this.data = data;
		this.progress = new ProgressLog(Logger.getLogger(getClass().getName()));
		this.bResults = null;
	}


	public BatchRunner(BatchData data, ProcessingProgress progress) {
		super();
		this.data = data;
		this.progress = progress;
		this.bResults = null;
	}


	private void setProgress(String text) {
		if (progress != null) progress.setProgress(text);
	}


	private void setState(String text) {
		if (progress != null) progress.setState(text);
	}


	private void setDone() {
		if (progress != null) progress.setDone();
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
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	@Override
	public void run() {
		Client client = data.getClient();
		boolean results = data.shouldSaveResults();
		boolean saveROIs = data.shouldSaveROIs();
		boolean saveImage = data.shouldSaveImage();
		boolean saveOnOmero = data.isOutputOnOMERO();
		boolean saveOnLocal = data.isOutputOnLocal();
		long inputDatasetId = data.getInputDatasetId();
		String macro = data.getMacro();
		String extension = data.getExtension();
		String directoryOut = data.getDirectoryOut();
		List<String> pathsImages;
		List<String> pathsAttach;
		List<Collection<ROIData>> roisL;
		List<Long> imaIds;
		List<Long> imagesIds;
		boolean imaRes;

		try {
			if (!saveOnOmero) {
				setProgress("Temporary directory creation...");
				Path directoryOutf = Files.createTempDirectory("Fiji_analyse");
				data.setDirectoryOut(directoryOutf.toString());
			}

			if (saveOnOmero) {
				setProgress("Images recovery from Omero...");
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				setProgress("Macro running...");
				runMacro(images, macro, extension, directoryOut, results, saveROIs);
				setState("");
				if (data.shouldClearROIs()) {
					deleteROIs(images);
				}
			} else {
				setProgress("Images recovery from input folder...");
				List<String> images = getImagesFromDirectory(data.getDirectoryIn());
				setProgress("Macro running...");
				runMacroOnLocalImages(images, macro, extension, directoryOut, results, saveROIs);
				setState("");
			}
			pathsImages = bResults.getPathImages();
			pathsAttach = bResults.getPathAttach();
			roisL = bResults.getmROIS();
			imaIds = bResults.getImageIds();
			imaRes = bResults.getImaRes();

			if (data.shouldnewDataSet() && !imaRes) {
				setProgress("New dataset creation...");
				ProjectWrapper project = client.getProject(data.getProjectIdOut());
				DatasetWrapper dataset = project.addDataset(client, data.getNameNewDataSet(), "");
				data.setOutputDatasetId(dataset.getId());
			}

			if (saveOnOmero) {
				setProgress("import on omero...");
				// Default imageIds = input images
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				imagesIds = images.stream().map(GenericObjectWrapper::getId).collect(Collectors.toList());
				if (imaRes && saveImage) {
					imagesIds = importImagesInDataset(pathsImages, roisL, saveROIs);
				}
				if (saveImage && saveROIs) {
					imagesIds = importRoisInImage(imaIds, roisL);
				}
				if (results && !saveImage && !saveROIs) {
					setProgress("Attachment of results files...");
					uploadTagFiles(pathsAttach, imaIds);
				} else if (results && saveImage) {
					setProgress("Attachment of results files...");
					uploadTagFiles(pathsAttach, imagesIds);
				}
				if (imaRes && saveImage) {
					IJ.error("Impossible to save: \nOutput image must be different than input image");
				}
			}

			if (saveOnLocal) {
				setProgress("Temporary directory deletion...");
				deleteTemp(directoryOut);
			}
			setDone();
		} catch (Exception e3) {
			if (e3.getMessage().equals("Macro cancelled")) {
				setDone();
				setState("Macro cancelled");
				IJ.run("Close");
			}
			IJ.error(e3.getMessage());
		}
	}


	public List<Long> importImagesInDataset(List<String> pathsImages,
											List<Collection<ROIData>> roisL,
											Boolean saveRois)
	throws Exception {
		//""" Import images in Omero server from pathsImages and return ids of these images in datasetId """
		//pathsImages : String[]
		List<Long> imageIds = new ArrayList<>();

		Client client = data.getClient();
		Long datasetId = data.getOutputDatasetId();
		DatasetWrapper dataset = client.getDataset(datasetId);
		for (String path : pathsImages) {
			List<Long> newIds = dataset.importImage(client, path);
			imageIds.addAll(newIds);
		}

		int indice = 0;
		for (Long id : imageIds) {
			if (Boolean.TRUE.equals(saveRois)) {
				indice = uploadROIS(roisL, id, indice);
			}
		}
		return imageIds;
	}


	// for(ROIWrapper roi : image.getROIs(client)) client.delete(roi);
	public void deleteROIs(List<ImageWrapper> images) {
		setProgress("ROIs deletion from Omero");
		Client client = data.getClient();
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
		List<String> pathsImagesIni = new ArrayList<>();
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


	public void saveAndCloseWithRes(String image, String attach) {
		//IJ.selectWindow("Result") //depends if images are renamed or not in the macro

		IJ.saveAs("TIFF", image);
		//IJ.run("Close")
		IJ.selectWindow("Results");
		IJ.saveAs("Results", attach);
		//IJ.run("Close")
	}


	public void saveAndCloseWithoutRes(String image) {
		//IJ.selectWindow("Result") //depends if images are renamed or not in the macro
		IJ.saveAs("TIFF", image);
		//	IJ.run("Close")
	}


	public void saveResultsOnly(String attach) {
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
	List<Collection<ROIData>> saveROIS2D(List<Collection<ROIData>> AlRois, Long image_id, ImagePlus imap) {
		System.out.println("saving ROIs");
		/*ROIReader rea = new ROIReader();
		Object roi_list = rea.readImageJROIFromSources(image_id, imap);
        AlRois.add(roi_list);*/
		return AlRois;
	}


	// Retrieves 3D ROIS of an image, before their deletion and add it in an array
	List<Collection<ROIData>> save_ROIS_3D(List<Collection<ROIData>> alROIS, Long image_id, ImagePlus imap) {
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

		alROIS.add(final_roi_list);*/
		return alROIS;
	}


	String todayDate() {
		SimpleDateFormat formatter = new SimpleDateFormat("HH-mm-ss-dd-MM-yyyy");
		Date date = new Date();
		String textDate = formatter.format(date);
		String[] tabDate = textDate.split("-", 4);
		return tabDate[3] + "_" + tabDate[0] + "h" + tabDate[1];
	}


	void runMacro(List<ImageWrapper> images,
				  String macroChosen,
				  String extensionChosen,
				  String dir,
				  Boolean results,
				  Boolean savRois)
	throws ServiceException, AccessException, ExecutionException {
		//""" Run a macro on images and save the result """
		Client client = data.getClient();
		int size = images.size();
		boolean imaRes = true;
		String[] pathsImages = new String[size];
		String[] pathsAttach = new String[size];
		boolean saveImage = data.shouldSaveImage();
		boolean saveOnOmero = data.isOutputOnOMERO();
		IJ.run("Close All");
		String appel = "0";
		List<Collection<ROIData>> mROIS = new ArrayList<>();
		List<Long> imageIds = new ArrayList<>();
		int index = 0;
		for (ImageWrapper image : images) {
			// Open the image
			setState("image " + (index + 1) + "/" + images.size());
			long id = image.getId();
			imageIds.add(id);
			long gid = client.getCurrentGroupId();
			client.switchGroup(gid);
			client.getImage(id).toImagePlus(client);
			ImagePlus imp = IJ.getImage();
			long idocal = imp.getID();
			// Define paths
			String title = imp.getTitle();
			if ((title
					.matches("(.*)qptiff(.*)")))
				title = title.replace(".qptiff", "_");
			else title = FilenameUtils.removeExtension(title);
			String res =
					dir + File.separator + title + extensionChosen + ".tif";
			String attach =
					dir + File.separator + "Res_" +
					title + /* extensionChosen + */ "_" + todayDate() +
					".xls";
			// Analyse the images.
			IJ.runMacroFile(macroChosen, appel);
			appel = "1";
			// Save and Close the various components
			if (Boolean.TRUE.equals(savRois)) {
				// save of ROIs
				RoiManager rm = RoiManager.getInstance2();
				if (Boolean.TRUE.equals(data.isOutputOnLocal())) {  //  local save
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  todayDate() + "_RoiSet.zip");
					if (Boolean.TRUE.equals(saveImage)
					) {  // image results expected
						if (Boolean.TRUE.equals(results)) {
							saveAndCloseWithRes(res, attach);
						} else {
							saveAndCloseWithoutRes(res);
						}
					} else {
						if (Boolean.TRUE.equals(results)) {
							saveResultsOnly(attach);
						}
					}
				}
				if (Boolean.TRUE.equals(saveOnOmero)) { // save on Omero
					Roi[] rois = rm.getRoisAsArray();
					boolean verif = false;
					for (Roi roi : rois) {
						if (roi.getProperties() != null) verif = true;
					}
					if (Boolean.TRUE.equals(saveImage)
					) { // image results expected
						// sauvegarde 3D
						if (Boolean.TRUE.equals(verif)) {
							if (Boolean.TRUE.equals(results)) {
								saveAndCloseWithRes(res, attach);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							} else {
								saveAndCloseWithoutRes(res);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (Boolean.TRUE.equals(results)) {
								saveAndCloseWithRes(res, attach);
								mROIS = saveROIS2D(mROIS, id, imp);
							} else {
								saveAndCloseWithoutRes(res);
								mROIS = saveROIS2D(mROIS, id, imp);
							}
						}
					} else {
						// sauvegarde 3D
						if (Boolean.TRUE.equals(verif)) {
							if (Boolean.TRUE.equals(results)) {
								saveResultsOnly(attach);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							} else {
								mROIS = save_ROIS_3D(mROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (Boolean.TRUE.equals(results)) {
								saveResultsOnly(attach);
								mROIS = saveROIS2D(mROIS, id, imp);
							} else {
								mROIS = saveROIS2D(mROIS, id, imp);
							}
						}

					}
				}
			} else {
				if (Boolean.TRUE.equals(saveImage)) { // image results expected
					if (Boolean.TRUE.equals(results)) {
						saveAndCloseWithRes(res, attach);
					} else {
						saveAndCloseWithoutRes(res);
					}
				} else {
					if (Boolean.TRUE.equals(results)) {
						saveResultsOnly(attach);
					}
				}
			}
			imp.changes = false; // Prevent "Save Changes?" dialog
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				imaRes = false;
			} else {
				int newId = imp
						.getID(); // result image have to be selected in the end of the macro
				if (newId == idocal) {
					imaRes = false;
				}
			}
			IJ.run("Close All"); //  To do local and Omero saves on the same time
			pathsImages[index] = res;
			pathsAttach[index] = attach;
		}

		bResults.setPathImages((Arrays.asList(pathsImages)));
		bResults.setPathAttach((Arrays.asList(pathsAttach)));
		bResults.setmROIS(mROIS);
		bResults.setImageIds(imageIds);
		bResults.setImaRes(imaRes);
	}


	void runMacroOnLocalImages(List<String> images,
							   String macroChosen,
							   String extensionChosen,
							   String dir,
							   Boolean results,
							   Boolean savRois) {
		//""" Run a macro on images from local computer and save the result """
		int size = images.size();
		String[] pathsImages = new String[size];
		String[] pathsAttach = new String[size];
		boolean saveImage = data.shouldSaveImage();
		boolean saveOnOmero = data.isOutputOnOMERO();
		boolean saveOnLocal = data.isOutputOnLocal();
		IJ.run("Close All");
		String appel = "0";
		List<Collection<ROIData>> mROIS = new ArrayList<>();
		Boolean imaRes = true;
		int index = 0;
		for (String Image : images) {
			// Open the image
			//IJ.open(Image);
			//IJ.run("Stack to Images");
			setState("image " + (index + 1) + "/" + images.size());
			ImagePlus imp = IJ.openImage(Image);
			long id = imp.getID();
			IJ.run("Bio-Formats Importer",
				   "open=" + Image +
				   " autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");
			int l = imp.getHeight();
			int h = imp.getWidth();
			int b = imp.getBitDepth();
			int f = imp.getFrame();
			int c = imp.getChannel();
			int s = imp.getSlice();
			IJ.createHyperStack(Image, h, l, c, s, f, b);
			//IJ.run("Images to Hyperstack");
			//imp.createHyperStack(Image,C,S,F,B);
			// Define paths
			String title = imp.getTitle();
			if ((title
					.matches("(.*)qptiff(.*)")))
				title = title.replace(".qptiff", "_");
			else title = FilenameUtils.removeExtension(title);
			String res =
					dir + File.separator + title + extensionChosen + ".tif";
			String attach =
					dir + File.separator + "Res_" +
					title + /* extensionChosen + */ "_" + todayDate() +
					".xls";
			// Analyse the images
			IJ.runMacroFile(macroChosen, appel);
			appel = "1";
			// Save and Close the various components
			if (Boolean.TRUE.equals(savRois)) {
				//  save of ROIs
				RoiManager rm = RoiManager.getInstance2();
				if (Boolean.TRUE.equals(saveOnLocal)) {  //  local save
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  todayDate() + "_RoiSet.zip");
					if (Boolean.TRUE.equals(saveImage)) {
						if (Boolean.TRUE.equals(results)) {
							saveAndCloseWithRes(res, attach);
						} else {
							saveAndCloseWithoutRes(res);
						}
					} else {
						if (Boolean.TRUE.equals(results)) {
							saveResultsOnly(attach);
						}
					}
				}
				if (Boolean.TRUE.equals(saveOnOmero)) {  // save on Omero
					Roi[] rois = rm.getRoisAsArray();
					boolean verif = false;
					for (Roi roi : rois) {
						if (roi.getProperties() != null) verif = true;
					}
					if (Boolean.TRUE.equals(saveImage)
					) {  // image results expected
						// sauvegarde 3D
						if (Boolean.TRUE.equals(verif)) {
							if (Boolean.TRUE.equals(results)) {
								saveAndCloseWithRes(res, attach);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							} else {
								saveAndCloseWithoutRes(res);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (Boolean.TRUE.equals(results)) {
								saveAndCloseWithRes(res, attach);
								mROIS = saveROIS2D(mROIS, id, imp);
							} else {
								saveAndCloseWithoutRes(res);
								mROIS = saveROIS2D(mROIS, id, imp);
							}
						}
					} else {
						// sauvegarde 3D
						if (Boolean.TRUE.equals(verif)) {
							if (Boolean.TRUE.equals(results)) {
								saveResultsOnly(attach);
								mROIS = save_ROIS_3D(mROIS, id, imp);
							} else {
								mROIS = save_ROIS_3D(mROIS, id, imp);
							}
						} else {
							// sauvegarde 2D
							if (Boolean.TRUE.equals(results)) {
								saveResultsOnly(attach);
								mROIS = saveROIS2D(mROIS, id, imp);
							} else {
								mROIS = saveROIS2D(mROIS, id, imp);
							}
						}

					}
				}
			} else {
				if (Boolean.TRUE.equals(saveImage)) {  // image results expected
					if (Boolean.TRUE.equals(results)) {
						saveAndCloseWithRes(res, attach);
					} else {
						saveAndCloseWithoutRes(res);
					}
				} else {
					if (Boolean.TRUE.equals(results)) {
						saveResultsOnly(attach);
					}
				}
			}
			imp.changes = false; // Prevent "Save Changes?" dialog
			imaRes = true;
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				imaRes = false;
			} else {
				int newId = imp
						.getID(); // result image have to be selected in the end of the macro
				if (newId == id) {
					imaRes = false;
				}
			}
			IJ.run("Close All"); //  To do local and Omero saves on the same time
			pathsImages[index] = res;
			pathsAttach[index] = attach;
		}
		bResults.setPathImages((Arrays.asList(pathsImages)));
		bResults.setPathAttach((Arrays.asList(pathsAttach)));
		bResults.setmROIS(mROIS);
		bResults.setImaRes(imaRes);
	}


	// for(ROIWrapper roi : rois) image.save(roi);
	// Read the ROIS array and upload them on omero
	public int uploadROIS(List<Collection<ROIData>> alROIS, Long id, int indice)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		Client client = data.getClient();

		if (alROIS.get(indice) != null) { // && (alROIS.get(indice)).size() > 0) { // Problem
			System.out.println("Importing ROIs");

			ROIFacility roi_facility = client.getGateway().getFacility(ROIFacility.class);
			roi_facility.saveROIs(client.getCtx(), id, client.getUser().getId(),
								  alROIS.get(indice));
			return indice + 1;
		}
		return indice;
	}


	public List<Long> importRoisInImage(List<Long> imagesIds,
										List<Collection<ROIData>> roisL)
	throws ExecutionException, DSAccessException, DSOutOfServiceException {
		//""" Import Rois in Omero server on images and return ids of these images in datasetId """
		int indice = 0;
		long ind = 1;
		for (Long id : imagesIds) {
			indice = uploadROIS(roisL, id, indice);
			setState("image " + ind + "/" + imagesIds.size());
			ind = ind + 1;
		}
		return imagesIds;
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


	public void deleteTemp(String tmp_dir) {
		//""" Delete the local copy of temporary files and directory """
		File dir = new File(tmp_dir);
		File[] entries = dir.listFiles();
		for (File entry : entries) {
			entry.delete();
		}
		dir.delete();
	}

}
