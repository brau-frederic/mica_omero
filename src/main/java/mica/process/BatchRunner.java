package mica.process;

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.OMEROServerError;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.DatasetWrapper;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
import mica.gui.ProgressDialog;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;


public class BatchRunner extends Thread {

	private final Client client;
	private final ProcessingProgress progress;

	private boolean inputOnOMERO;
	private boolean saveImage;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean loadROIs;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long inputDatasetId;
	private long outputDatasetId;
	private String directoryIn;
	private String directoryOut;
	private String macro;
	private String extension;

	private BatchListener listener;


	public BatchRunner(Client client) {
		super();
		this.client = client;
		this.progress = new ProgressLog(Logger.getLogger(getClass().getName()));
	}


	public BatchRunner(Client client, ProcessingProgress progress) {
		super();
		this.client = client;
		this.progress = progress;
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

		try {
			if (!outputOnLocal) {
				setProgress("Temporary directory creation...");
				directoryOut = Files.createTempDirectory("Fiji_analysis").toString();
			}

			if (inputOnOMERO) {
				setProgress("Images recovery from OMERO...");
				DatasetWrapper dataset = client.getDataset(inputDatasetId);
				List<ImageWrapper> images = dataset.getImages(client);
				setProgress("Macro running...");
				runMacro(images);
			} else {
				setProgress("Images recovery from input folder...");
				List<String> images = getImagesFromDirectory(getDirectoryIn());
				setProgress("Macro running...");
				runMacroOnLocalImages(images);
			}
			setState("");

			if (!outputOnLocal) {
				setProgress("Temporary directory deletion...");
				if (!deleteTemp(directoryOut)) {
					IJ.log("Temp directory may not be deleted.");
				}
			}
			setDone();
		} catch (Exception e3) {
			setDone();
			setState("Macro cancelled");
			if (e3.getMessage() != null && e3.getMessage().equals("Macro cancelled")) {
				IJ.run("Close");
			}
			IJ.error(e3.getMessage());
		} finally {
			if (listener != null) listener.onThreadFinished();
		}
	}


	public void deleteROIs(ImageWrapper image) {
		setProgress("ROIs deletion from OMERO");
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


	private List<ROIWrapper> getRoisFromIJ(ImagePlus imp, String property) {
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
		}

		return rois;
	}


	String todayDate() {
		return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
	}


	void runMacro(List<ImageWrapper> images) {
		//""" Run a macro on images and save the result """
		String property = "ROI";
		ij.WindowManager.closeAllWindows();
		String appel = "0";
		int index = 0;
		for (ImageWrapper image : images) {
			setState("Image " + (index + 1) + "/" + images.size());
			long outputImageId = image.getId();

			// Open image from OMERO
			ImagePlus imp = openImage(image);
			// If image could not be loaded, continue to next image.
			if (imp == null) continue;
			long ijId = imp.getID();

			// Load ROIs
			loadROIs(image, imp);

			// Define paths
			String title = removeExtension(imp.getTitle());
			String res = directoryOut + File.separator + title + extension + ".tif";
			String attach = directoryOut + File.separator + "Res_" + title + /*extension +*/ "_" + todayDate() + ".xls";

			imp.show();

			// Analyse the images.
			IJ.runMacroFile(macro, appel);
			appel = "1";

			imp.changes = false; // Prevent "Save Changes?" dialog
			imp = WindowManager.getCurrentImage();
			if (saveImage && imp != null && imp.getID() != ijId) {
				List<Long> ids = saveImage(res);
				if (!ids.isEmpty()) {
					outputImageId = ids.get(0);
				}
			} else {
				IJ.error("Impossible to save: output image must be different from input image.");
			}

			saveROIs(outputImageId, imp, title, property);
			saveResults(outputImageId, attach);

			WindowManager.closeAllWindows(); //  To do local and Omero saves on the same time
			index++;
		}
	}


	void runMacroOnLocalImages(List<String> images) {
		//""" Run a macro on images from local computer and save the result """
		String property = "ROI";
		WindowManager.closeAllWindows();
		String appel = "0";
		int index = 0;
		for (String image : images) {

			// Open the image
			setState("image " + (index + 1) + "/" + images.size());
			ImagePlus imp = IJ.openImage(image);
			long ijId = imp.getID();
			/*IJ.run("Bio-Formats Importer",
				   "open=" + Image +
				   " autoscale color_mode=Default view=Hyperstack stack_order=XYCZT");*/
			// Define paths
			String title = removeExtension(imp.getTitle());
			String res = directoryOut + File.separator + title + extension + ".tif";
			String attach = directoryOut + File.separator + "Res_" + title + /*extension +*/ "_" + todayDate() + ".xls";

			// Analyse the images
			IJ.runMacroFile(macro, appel);
			appel = "1";

			// Save and Close the various components
			long outputImageId = -1L;
			imp.changes = false; // Prevent "Save Changes?" dialog
			imp = WindowManager.getCurrentImage();
			if (saveImage && imp != null && imp.getID() != ijId) {
				List<Long> ids = saveImage(res);
				if (!ids.isEmpty()) {
					outputImageId = ids.get(0);
				}
			} else {
				IJ.error("Impossible to save: output image must be different from input image.");
			}

			saveROIs(outputImageId, imp, title, property);
			saveResults(outputImageId, attach);
			WindowManager.closeAllWindows(); //  To do local and Omero saves on the same time
			index++;
		}
	}


	private String removeExtension(String title) {
		if (title.matches("(.*)qptiff(.*)")) {
			return title.replace(".qptiff", "_");
		} else {
			return FilenameUtils.removeExtension(title);
		}
	}


	private ImagePlus openImage(ImageWrapper image) {
		setProgress("Opening image from OMERO...");
		ImagePlus imp = null;
		try {
			imp = image.toImagePlus(client);
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load image: " + e.getMessage());
		}
		return imp;
	}


	private void loadROIs(ImageWrapper image, ImagePlus imp) {
		if (loadROIs) {
			RoiManager rm = RoiManager.getRoiManager();
			rm.reset(); // Reset ROI manager to clear previous ROIs
			List<Roi> ijRois = new ArrayList<>();
			try {
				ijRois = ROIWrapper.toImageJ(image.getROIs(client));
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Could not import ROIs: " + e.getMessage());
			}
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				rm.addRoi(ijRoi);
			}
		}
	}


	private List<Long> saveImage(String path) {
		List<Long> ids = new ArrayList<>();
		if (saveImage) {
			IJ.saveAs("TIFF", path);
			if (outputOnOMERO) {
				try {
					setProgress("Import on OMERO...");
					DatasetWrapper dataset = client.getDataset(outputDatasetId);
					ids = dataset.importImage(client, path);
				} catch (Exception e) {
					IJ.error("Could not import image: " + e.getMessage());
				}
			}
		}
		return ids;
	}


	private void saveROIs(long imageId, ImagePlus imp, String title, String property) {
		// save of ROIs
		if (outputOnLocal) {  //  local save
			RoiManager rm = RoiManager.getRoiManager();
			rm.runCommand("Deselect"); // deselect ROIs to save them all
			rm.runCommand("Save", directoryOut + File.separator + title + "_" + todayDate() + "_RoiSet.zip");
		}
		if (outputOnOMERO && imageId != -1L) { // save on Omero
			List<ROIWrapper> rois = getRoisFromIJ(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (clearROIs) {
					deleteROIs(image);
				}
				for (ROIWrapper roi : rois) {
					roi.setImage(image);
					image.saveROI(client, roi);
				}
			} catch (ServiceException | AccessException | ExecutionException e) {
				IJ.error("Could not import ROIs to OMERO: " + e.getMessage());
			}

		}
	}


	private void saveResults(long imageId, String path) {
		if (saveResults) {
			setProgress("Saving results files...");
			IJ.saveAs("Results", path);
			ImageWrapper image;
			try {
				setProgress("Attachment of results files...");
				image = client.getImage(imageId);
				image.addFile(client, new File(path));
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


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) this.outputDatasetId = outputDatasetId;
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


	public void setMacro(String macro) {
		this.macro = macro;
	}


	public String getExtension() {
		return extension;
	}


	public void setExtension(String extension) {
		this.extension = extension;
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
