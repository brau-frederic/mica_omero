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
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import mica.BatchResults;
import mica.gui.ProgressDialog;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class BatchRunner extends Thread {

	private final Client client;
	private final ProcessingProgress progress;
	private final BatchResults bResults;

	private boolean inputOnOMERO;
	private boolean newDataSet;
	private boolean saveImage;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean loadROIs;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long inputDatasetId;
	private long outputDatasetId;
	private long inputProjectId;
	private long outputProjectId;
	private long projectIdOut;
	private String directoryIn;
	private String directoryOut;
	private String macro;
	private String extension;
	private String nameNewDataSet;

	private BatchListener listener;


	public BatchRunner(Client client) {
		super();
		this.client = client;
		this.progress = new ProgressLog(Logger.getLogger(getClass().getName()));
		this.bResults = new BatchResults();
	}


	public BatchRunner(Client client, ProcessingProgress progress) {
		super();
		this.client = client;
		this.progress = progress;
		this.bResults = new BatchResults();
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
	 * @see Thread#Thread(ThreadGroup, Runnable, String)
	 */
	@Override
	public void run() {
		if (progress instanceof ProgressDialog) {
			((ProgressDialog) progress).setVisible(true);
		}
		List<String> pathsImages;
		List<String> pathsAttach;
		List<Collection<ROIWrapper>> roisL;
		List<Long> imaIds;
		List<Long> imagesIds;
		boolean imaRes;


		try {
			if (!outputOnLocal) {
				setProgress("Temporary directory creation...");
				Path directoryOutf = Files.createTempDirectory("Fiji_analyse");
				directoryOut = directoryOutf.toString();
			}

			if (inputOnOMERO) {
				setProgress("Images recovery from Omero...");
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				setProgress("Macro running...");
				runMacro(images, macro, extension, directoryOut, saveResults, saveROIs);
				setState("");
				if (clearROIs) {
					deleteROIs(images);
				}
			} else {
				setProgress("Images recovery from input folder...");
				List<String> images = getImagesFromDirectory(getDirectoryIn());
				setProgress("Macro running...");
				runMacroOnLocalImages(images, macro, extension, directoryOut, saveResults, saveROIs);
				setState("");
			}
			pathsImages = bResults.getPathImages();
			pathsAttach = bResults.getPathAttach();
			roisL = bResults.getmROIS();
			imaIds = bResults.getImageIds();
			imaRes = bResults.getImaRes();

			if (newDataSet && !imaRes) {
				setProgress("New dataset creation...");
				ProjectWrapper project = client.getProject(projectIdOut);
				DatasetWrapper dataset = project.addDataset(client, nameNewDataSet, "");
				outputDatasetId = dataset.getId();
			}

			if (outputOnOMERO) {
				setProgress("import on omero...");
				// Default imageIds = input images
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				imagesIds = images.stream().map(GenericObjectWrapper::getId).collect(Collectors.toList());
				if (imaRes && saveImage) {
					imagesIds = importImagesInDataset(pathsImages, roisL, saveROIs);
				}
				if (!saveImage && saveROIs) {
					imagesIds = importRoisInImage(imaIds, roisL);
				}
				if (saveResults && !saveImage && !saveROIs) {
					setProgress("Attachment of results files...");
					uploadTagFiles(pathsAttach, imaIds);
				} else if (saveResults && saveImage) {
					setProgress("Attachment of results files...");
					uploadTagFiles(pathsAttach, imagesIds);
				}
				if (!imaRes && saveImage) {
					IJ.error("Impossible to save: \nOutput image must be different than input image");
				}
			}

			if (outputOnLocal) {
				setProgress("Temporary directory deletion...");
				if (!deleteTemp(directoryOut)) {
					IJ.log("Temp directory may not be deleted.");
				}
			}
			setDone();
		} catch (Exception e3) {
			if (e3.getMessage() != null && e3.getMessage().equals("Macro cancelled")) {
				setDone();
				setState("Macro cancelled");
				IJ.run("Close");
			}
			IJ.error(e3.getMessage());
		} finally {
			if (listener != null) listener.onThreadFinished();
		}
	}


	public List<Long> importImagesInDataset(List<String> pathsImages,
											List<Collection<ROIWrapper>> roisL,
											boolean saveRois)
	throws Exception {
		//""" Import images in Omero server from pathsImages and return ids of these images in datasetId """
		//pathsImages : String[]
		List<Long> imageIds = new ArrayList<>();

		Long datasetId = outputDatasetId;
		DatasetWrapper dataset = client.getDataset(datasetId);
		for (String path : pathsImages) {
			List<Long> newIds = dataset.importImage(client, path);
			imageIds.addAll(newIds);
		}

		int indice = 0;
		for (Long id : imageIds) {
			if (saveRois) {
				indice = uploadROIS(roisL, id, indice);
			}
		}
		return imageIds;
	}


	public void deleteROIs(List<ImageWrapper> images) {
		setProgress("ROIs deletion from OMERO");
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
		List<Long> imageIds = new ArrayList<>();
		try {
			DatasetWrapper dataset = client.getDataset(datasetId);
			List<ImageWrapper> images = dataset.getImages(client);
			images.forEach(image -> imageIds.add(image.getId()));
		} catch (ServiceException | AccessException exception) {
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


	List<ROIWrapper> getRoisFromIJ(Long id, ImagePlus imp, String property)
	throws ServiceException, AccessException {
		List<ROIWrapper> rois = new ArrayList<>();
		if (imp != null) {
			List<Roi> ijRois = new ArrayList<>();
			Overlay overlay = imp.getOverlay();
			if (overlay != null) {
				ijRois.addAll(Arrays.asList(overlay.toArray()));
			}
			RoiManager manager = RoiManager.getRoiManager();
			ijRois.addAll(Arrays.asList(manager.getRoisAsArray()));
			for (Roi roi : ijRois) roi.setImage(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
			for (ROIWrapper roi : rois) roi.setImage(client.getImage(id));
		}

		return rois;
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
				  boolean results,
				  boolean savRois)
	throws ServiceException, AccessException, ExecutionException {
		//""" Run a macro on images and save the result """
		int size = images.size();
		boolean imaRes = true;
		String property = "ROI";
		String[] pathsImages = new String[size];
		String[] pathsAttach = new String[size];
		IJ.run("Close All");
		String appel = "0";
		List<Collection<ROIWrapper>> mROIS = new ArrayList<>();
		List<Long> imageIds = new ArrayList<>();
		int index = 0;
		for (ImageWrapper image : images) {
			// Open the image
			setState("image " + (index + 1) + "/" + images.size());
			long id = image.getId();
			imageIds.add(id);
			ImagePlus imp = image.toImagePlus(client);
			long idocal = imp.getID();

			// Load ROIs
			if (loadROIs) {
				RoiManager rm = RoiManager.getRoiManager();
				rm.reset(); // Reset ROI manager to clear previous ROIs
				List<Roi> ijRois = ROIWrapper.toImageJ(image.getROIs(client));
				for (Roi ijRoi : ijRois) {
					ijRoi.setImage(imp);
					rm.addRoi(ijRoi);
				}
			}

			// Define paths
			String title = imp.getTitle();
			if ((title.matches("(.*)qptiff(.*)")))
				title = title.replace(".qptiff", "_");
			else title = FilenameUtils.removeExtension(title);
			String res =
					dir + File.separator + title + extensionChosen + ".tif";
			String attach =
					dir + File.separator + "Res_" +
					title + /* extensionChosen + */ "_" + todayDate() +
					".xls";
			imp.show();

			// Analyse the images.
			IJ.runMacroFile(macroChosen, appel);
			appel = "1";

			// Save and Close the various components
			if (savRois) {
				// save of ROIs
				if (outputOnLocal) {  //  local save
					RoiManager rm = RoiManager.getRoiManager();
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  todayDate() + "_RoiSet.zip");
				}
				if (outputOnOMERO) { // save on Omero
					mROIS.add(getRoisFromIJ(id, imp, property));
				}
					if (saveImage) {  // image results expected
						if (results) {
							saveAndCloseWithRes(res, attach);
						} else {
							saveAndCloseWithoutRes(res);
						}
					} else {
						if (results) {
							saveResultsOnly(attach);
						}
					}

			} else {
				if (saveImage) { // image results expected
					if (results) {
						saveAndCloseWithRes(res, attach);
					} else {
						saveAndCloseWithoutRes(res);
					}
				} else {
					if (results) {
						saveResultsOnly(attach);
					}
				}
			}

			imp.changes = false; // Prevent "Save Changes?" dialog
			imp = WindowManager.getCurrentImage();
			if (imp == null) {
				imaRes = false;
			} else {
				int newId = imp.getID(); // result image have to be selected in the end of the macro
				if (newId == idocal) {
					imaRes = false;
				}
			}
			IJ.run("Close All"); //  To do local and Omero saves on the same time
			pathsImages[index] = res;
			pathsAttach[index] = attach;
			index++;
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
							   boolean results,
							   boolean savRois)
	throws ServiceException, AccessException {
		//""" Run a macro on images from local computer and save the result """
		int size = images.size();
		String property = "ROI";
		String[] pathsImages = new String[size];
		String[] pathsAttach = new String[size];
		IJ.run("Close All");
		String appel = "0";
		List<Collection<ROIWrapper>> mROIS = new ArrayList<>();
		boolean imaRes = true;
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
			if (savRois) {
				//  save of ROIs
				RoiManager rm = RoiManager.getInstance2();
				if (outputOnLocal) {  //  local save
					rm.runCommand("Deselect"); // deselect ROIs to save them all
					rm.runCommand("Save", dir + File.separator + title + "_" +
										  todayDate() + "_RoiSet.zip");
				}
				if (outputOnOMERO) {  // save on Omero
					mROIS.add(getRoisFromIJ(id, imp, property));
				}
				if (saveImage) {
						if (results) {
							saveAndCloseWithRes(res, attach);
						} else {
							saveAndCloseWithoutRes(res);
						}
				} else {
						if (results) {
							saveResultsOnly(attach);
						}
				}

			} else {
				if (saveImage) { // image results expected
					if (results) {
						saveAndCloseWithRes(res, attach);
					} else {
						saveAndCloseWithoutRes(res);
					}
				} else {
					if (results) {
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
			index++;
		}
		bResults.setPathImages((Arrays.asList(pathsImages)));
		bResults.setPathAttach((Arrays.asList(pathsAttach)));
		bResults.setmROIS(mROIS);
		bResults.setImaRes(imaRes);
	}


	public int uploadROIS(List<Collection<ROIWrapper>> alROIS, Long id, int indice)
	throws ExecutionException, AccessException, ServiceException {
		if (alROIS.get(indice) != null) { // && (alROIS.get(indice)).size() > 0) { // Problem
			setProgress("Importing ROIs");
			ImageWrapper image = client.getImage(id);
			for (ROIWrapper roi : alROIS.get(indice)) {
				image.saveROI(client, roi);
			}
			return indice + 1;
		}
		return indice;
	}


	public List<Long> importRoisInImage(List<Long> imagesIds,
										List<Collection<ROIWrapper>> roisL)
	throws ExecutionException, AccessException, ServiceException {
		//""" Import Rois in Omero server on images and return ids of these images in datasetId """
		int indice = 0;
		long ind = 1;
		for (Long id : imagesIds) {
			indice = uploadROIS(roisL, id, indice);
			setState("image " + ind + "/" + imagesIds.size());
			ind++;
		}
		return imagesIds;
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


	public boolean deleteTemp(String tmpDir) {
		//""" Delete the local copy of temporary files and directory """
		boolean deleted = true;
		File dir = new File(tmpDir);
		File[] entries = dir.listFiles();
		if (entries != null) {
			try {
				for (File entry : entries) {
					deleted &= Files.deleteIfExists(entry.toPath());
				}
				deleted &= Files.deleteIfExists(dir.toPath());
			} catch (IOException e) {
				IJ.error("Could not delete files: " + e.getMessage());
			}
		}
		return deleted;
	}


	public Client getClient() {
		return client;
	}


	public long getOutputProjectId() {
		return outputProjectId;
	}


	public void setOutputProjectId(Long outputProjectId) {
		if (outputProjectId != null) this.outputProjectId = outputProjectId;
	}


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) this.outputDatasetId = outputDatasetId;
	}


	public long getInputProjectId() {
		return inputProjectId;
	}


	public void setInputProjectId(Long inputProjectId) {
		if (inputProjectId != null) this.inputProjectId = inputProjectId;
	}


	public long getInputDatasetId() {
		return inputDatasetId;
	}


	public void setInputDatasetId(Long inputDatasetId) {
		if (inputDatasetId != null) this.inputDatasetId = inputDatasetId;
	}


	public boolean shouldSaveROIs() {
		return saveROIs;
	}


	public void setSaveROIs(boolean saveROIs) {
		this.saveROIs = saveROIs;
	}


	public boolean shouldSaveResults() {
		return saveResults;
	}


	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}


	public boolean isInputOnOMERO() {
		return inputOnOMERO;
	}


	public void setInputOnOMERO(boolean inputOnOMERO) {
		this.inputOnOMERO = inputOnOMERO;
	}


	public boolean shouldSaveImage() {
		return saveImage;
	}


	public void setSaveImage(boolean saveImage) {
		this.saveImage = saveImage;
	}


	public boolean shouldLoadROIs() {
		return loadROIs;
	}


	public void setLoadROIS(boolean loadROIs) {
		this.loadROIs = loadROIs;
	}


	public boolean shouldClearROIs() {
		return clearROIs;
	}


	public void setClearROIS(boolean clearROIs) {
		this.clearROIs = clearROIs;
	}


	public String getDirectoryIn() {
		return directoryIn;
	}


	public void setDirectoryIn(String directoryIn) {
		this.directoryIn = directoryIn;
	}


	public String getDirectoryOut() {
		return directoryOut;
	}


	public void setDirectoryOut(String directoryOut) {
		this.directoryOut = directoryOut;
	}


	public String getMacro() {
		return macro;
	}


	public void setMacro(String macroChosen) {
		this.macro = macroChosen;
	}


	public String getExtension() {
		return extension;
	}


	public void setExtension(String extensionChosen) {
		this.extension = extensionChosen;
	}


	public boolean shouldnewDataSet() {
		return newDataSet;
	}


	public void setnewDataSet(boolean newDataSet) {
		this.newDataSet = newDataSet;
	}


	public String getNameNewDataSet() {
		return nameNewDataSet;
	}


	public void setNameNewDataSet(String nameNewDataSet) {
		this.nameNewDataSet = nameNewDataSet;
	}


	public long getProjectIdOut() {
		return projectIdOut;
	}


	public void setProjectIdOut(long projectIdOut) {
		this.projectIdOut = projectIdOut;
	}


	public boolean isOutputOnOMERO() {
		return outputOnOMERO;
	}


	public void setOutputOnOMERO(boolean outputOnOMERO) {
		this.outputOnOMERO = outputOnOMERO;
	}


	public boolean isOutputOnLocal() {
		return outputOnLocal;
	}


	public void setOutputOnLocal(boolean outputOnLocal) {
		this.outputOnLocal = outputOnLocal;
	}


	public void addListener(BatchListener listener) {
		this.listener = listener;
	}

}
