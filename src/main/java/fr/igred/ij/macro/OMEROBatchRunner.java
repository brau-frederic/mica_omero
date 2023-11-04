/*
 *  Copyright (C) 2021-2023 MICA & GReD
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.

 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51 Franklin
 * Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package fr.igred.ij.macro;


import fr.igred.ij.gui.ProgressDialog;
import fr.igred.ij.io.BatchImage;
import fr.igred.ij.io.ROIMode;
import fr.igred.omero.Client;
import fr.igred.omero.annotations.TableWrapper;
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
import ij.io.RoiEncoder;
import ij.measure.ResultsTable;
import ij.plugin.frame.RoiManager;
import ij.text.TextWindow;

import java.awt.Component;
import java.awt.Frame;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


/**
 * Runs a script over multiple images retrieved from local files or from OMERO.
 */
public class OMEROBatchRunner extends Thread {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final int[] EMPTY_INT_ARRAY = new int[0];

	private static final Pattern TITLE_AFTER_EXT = Pattern.compile("\\w+\\s?\\[?([^\\[\\]]*)]?");

	private final List<BatchImage> images;
	private final ScriptRunner script;
	private final Client client;
	private final ProgressMonitor progress;
	private final BatchParameters params;

	private final Map<String, TableWrapper> tables = new HashMap<>(5);

	private RoiManager rm;

	private BatchListener listener;


	public OMEROBatchRunner(ScriptRunner script, List<BatchImage> images, BatchParameters params, Client client) {
		this(script, images, params, client, new ProgressLog(LOGGER));
	}


	public OMEROBatchRunner(ScriptRunner script,
							List<BatchImage> images,
							BatchParameters params,
							Client client,
							ProgressMonitor progress) {
		this.script = script;
		this.images = new ArrayList<>(images);
		this.params = new BatchParameters(params);
		this.client = client;
		this.progress = progress;
		this.rm = null;
		this.listener = null;
	}


	/**
	 * Generates the timestamp for current time.
	 *
	 * @return See above.
	 */
	private static String timestamp() {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").format(ZonedDateTime.now());
	}


	/**
	 * Removes file extension from image title.
	 *
	 * @param title Image title.
	 *
	 * @return The title, without the extension.
	 */
	private static String removeExtension(String title) {
		if (title != null) {
			int index = title.lastIndexOf('.');
			if (index == 0 || index == -1) {
				return title;
			} else {
				String afterExt = TITLE_AFTER_EXT.matcher(title.substring(index + 1)).replaceAll("$1");
				String beforeExt = title.substring(0, index);
				if (beforeExt.toLowerCase().endsWith(".ome") && beforeExt.lastIndexOf('.') > 0) {
					beforeExt = beforeExt.substring(0, beforeExt.lastIndexOf('.'));
				}
				return afterExt.isEmpty() ? beforeExt : beforeExt + "_" + afterExt;
			}
		} else {
			return null;
		}
	}


	/**
	 * Deletes the temp folder.
	 *
	 * @param tmpDir The temp folder.
	 *
	 * @return True if the deletion was successful.
	 */
	private static boolean deleteTemp(String tmpDir) {
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


	/**
	 * Retrieves the list of images open after the script was run.
	 *
	 * @param inputImage The input image.
	 *
	 * @return See above.
	 */
	private static List<ImagePlus> getOutputImages(ImagePlus inputImage) {
		ImagePlus outputImage = WindowManager.getCurrentImage();
		if (outputImage == null) {
			outputImage = inputImage;
		}
		int ijOutputId = outputImage.getID();

		int[] imageIds = WindowManager.getIDList();
		if (imageIds == null) {
			imageIds = EMPTY_INT_ARRAY;
		}
		List<Integer> idList = Arrays.stream(imageIds).boxed().collect(Collectors.toList());
		idList.removeIf(i -> i.equals(ijOutputId));
		idList.add(0, ijOutputId);
		return idList.stream()
					 .map(WindowManager::getImage)
					 .filter(Objects::nonNull)
					 .collect(Collectors.toList());
	}


	/**
	 * Retrieves the list of ROIs from an image overlay.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private static List<Roi> getOverlay(ImagePlus imp) {
		Overlay overlay = imp.getOverlay();
		List<Roi> ijRois = new ArrayList<>(0);
		if (overlay != null) {
			ijRois = new ArrayList<>(Arrays.asList(overlay.toArray()));
		}
		for (Roi roi : ijRois) {
			roi.setImage(imp);
		}
		return ijRois;
	}


	/**
	 * Converts ROIs from an image overlay to OMERO ROIs.
	 *
	 * @param imp      The image ROIs are linked to.
	 * @param property The ROI property used to group shapes in OMERO.
	 *
	 * @return A list of OMERO ROIs.
	 */
	private static List<ROIWrapper> getROIsFromOverlay(ImagePlus imp, String property) {
		List<ROIWrapper> rois = new ArrayList<>(0);
		if (imp != null) {
			List<Roi> ijRois = getOverlay(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
		}

		return rois;
	}


	/**
	 * Saves ImageJ ROIs to a file.
	 *
	 * @param ijRois The ROIs.
	 * @param path   The path to the file.
	 */
	private static void saveRoiFile(List<? extends Roi> ijRois, String path) {
		try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
			 DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(zos))) {
			RoiEncoder re = new RoiEncoder(dos);
			for (int i = 0; i < ijRois.size(); i++) {
				if (ijRois.get(i) != null) {
					// WARNING: Prepending index does not ensure label is unique.
					String label = i + "-" + ijRois.get(i).getName() + ".roi";
					zos.putNextEntry(new ZipEntry(label));
					re.write(ijRois.get(i));
					dos.flush();
				}
			}
		} catch (IOException e) {
			IJ.error("Error while saving ROI file: " + e.getMessage());
		}
	}


	/**
	 * Initializes the ROI manager.
	 */
	private void initRoiManager() {
		rm = RoiManager.getInstance2();
		if (rm == null) {
			rm = RoiManager.getRoiManager();
		}
		rm.setVisible(false);
	}


	/**
	 * Sets the current state.
	 *
	 * @param text The text for the current state.
	 */
	private void setState(String text) {
		if (progress != null) {
			progress.setState(text);
		}
	}


	/**
	 * Sets the current progress.
	 *
	 * @param text The text for the current progress.
	 */
	private void setProgress(String text) {
		if (progress != null) {
			progress.setProgress(text);
		}
	}


	/**
	 * Signals the process is done.
	 */
	private void setDone() {
		if (progress != null) {
			progress.setDone();
		}
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
		boolean finished = false;
		if (progress instanceof ProgressDialog) {
			((Component) progress).setVisible(true);
		}

		try {
			if (!params.isOutputOnLocal()) {
				setState("Temporary directory creation...");
				params.setDirectoryOut(Files.createTempDirectory("Fiji_analysis").toString());
			}

			setState("Macro running...");
			runMacro();
			setProgress("");
			uploadTables();

			if (!params.isOutputOnLocal()) {
				setState("Temporary directory deletion...");
				if (!deleteTemp(params.getDirectoryOut())) {
					LOGGER.warning("Temp directory may not be deleted.");
				}
			}
			finished = true;
			setState("");
			setDone();
		} catch (IOException e) {
			finished = true;
			setDone();
			setProgress("Macro cancelled");
			if (e.getMessage() != null && "Macro cancelled".equals(e.getMessage())) {
				IJ.run("Close");
			}
			IJ.error(e.getMessage());
		} finally {
			if (!finished) {
				setDone();
				setProgress("An unexpected error occurred.");
			}
			if (listener != null) {
				listener.onThreadFinished();
			}
			rm.setVisible(true);
			rm.close();
		}
	}


	/**
	 * Deletes all owned ROIs from an image on OMERO.
	 *
	 * @param image The image on OMERO.
	 */
	private void deleteROIs(ImageWrapper image) {
		setState("ROIs deletion from OMERO");
		try {
			List<ROIWrapper> rois = image.getROIs(client);
			for (ROIWrapper roi : rois) {
				if (roi.getOwner().getId() == client.getId()) {
					client.delete(roi);
				}
			}
		} catch (ExecutionException | OMEROServerError | ServiceException | AccessException exception) {
			LOGGER.warning(exception.getMessage());
		} catch (InterruptedException e) {
			LOGGER.warning(e.getMessage());
			Thread.currentThread().interrupt();
		}
	}


	/**
	 * Retrieves the list of ROIs from the ROI manager.
	 *
	 * @param imp The image ROIs are linked to.
	 *
	 * @return See above.
	 */
	private List<Roi> getManagedRois(ImagePlus imp) {
		List<Roi> ijRois = new ArrayList<>(Arrays.asList(rm.getRoisAsArray()));
		for (Roi roi : ijRois) {
			roi.setImage(imp);
		}
		return ijRois;
	}


	/**
	 * Converts ROIs from the ROI Manager to OMERO ROIs.
	 *
	 * @param imp      The image ROIs are linked to.
	 * @param property The ROI property used to group shapes in OMERO.
	 *
	 * @return A list of OMERO ROIs.
	 */
	private List<ROIWrapper> getROIsFromManager(ImagePlus imp, String property) {
		List<ROIWrapper> rois = new ArrayList<>(0);
		if (imp != null) {
			List<Roi> ijRois = getManagedRois(imp);
			rois = ROIWrapper.fromImageJ(ijRois, property);
		}

		return rois;
	}


	/**
	 * Runs a macro on images and saves the results.
	 */
	void runMacro() {
		String property = ROIWrapper.IJ_PROPERTY;
		WindowManager.closeAllWindows();

		// Initialize ROI Manager
		initRoiManager();
		ROIMode roiMode = ROIMode.DO_NOT_LOAD;
		if (params.shouldLoadROIs()) {
			roiMode = ROIMode.MANAGER;
		}

		int index = 0;
		for (BatchImage image : images) {
			setProgress("Image " + (index + 1) + "/" + images.size());
			ImagePlus imp = image.getImagePlus(roiMode);
			// If image could not be loaded, continue to next image.
			if (imp != null) {
				ImageWrapper imageWrapper = image.getImageWrapper();
				Long inputImageId = imageWrapper != null ? imageWrapper.getId() : null;
				imp.show();

				// Process the image
				script.setImage(imp);
				script.run();

				imp.changes = false; // Prevent "Save Changes?" dialog
				save(imp, inputImageId, property);
			}
			closeWindows();
			index++;
		}
	}


	/**
	 * Loads ROIs from an image in OMERO into ImageJ.
	 *
	 * @param image     The OMERO image.
	 * @param imp       The image in ImageJ ROIs should be linked to.
	 * @param toOverlay Whether the ROIs should be loaded to the ROI Manager (false) or the overlay (true).
	 */
	private void loadROIs(ImageWrapper image, ImagePlus imp, boolean toOverlay) {
		List<Roi> ijRois = new ArrayList<>(0);
		try {
			ijRois = ROIWrapper.toImageJ(image.getROIs(client));
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load ROIs: " + e.getMessage());
		}
		if (toOverlay) {
			Overlay overlay = imp.getOverlay();
			if (overlay != null) {
				overlay.clear();
			} else {
				overlay = new Overlay();
			}
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				overlay.add(ijRoi, ijRoi.getName());
			}
		} else {
			rm.reset(); // Reset ROI manager to clear previous ROIs
			for (Roi ijRoi : ijRois) {
				ijRoi.setImage(imp);
				rm.addRoi(ijRoi);
			}
		}
	}


	/**
	 * Saves the images, results and ROIs.
	 *
	 * @param inputImage   The input image in ImageJ.
	 * @param omeroInputId The OMERO image input ID.
	 * @param property     The ROI property used to group shapes in OMERO.
	 */
	private void save(ImagePlus inputImage, Long omeroInputId, String property) {
		String inputTitle = removeExtension(inputImage.getTitle());

		Long omeroOutputId = omeroInputId;
		List<ImagePlus> outputs = getOutputImages(inputImage);

		ImagePlus outputImage = inputImage;
		if (!outputs.isEmpty()) {
			outputImage = outputs.get(0);
		}

		// If input image is expected as output for ROIs on OMERO but is not annotable, import it.
		boolean annotatable = Boolean.parseBoolean(inputImage.getProp("Annotable"));
		boolean outputIsNotInput = !inputImage.equals(outputImage);
		if (!params.isOutputOnOMERO() || !params.shouldSaveROIs() || annotatable || outputIsNotInput) {
			outputs.removeIf(inputImage::equals);
		}

		if (params.shouldSaveImage()) {
			if (outputs.isEmpty()) {
				LOGGER.info("Warning: there is no new image.");
			}
			List<Long> outputIds = new ArrayList<>(outputs.size());
			outputs.forEach(imp -> outputIds.addAll(saveImage(imp, property)));
			if (!outputIds.isEmpty() && outputIsNotInput) {
				omeroOutputId = outputIds.get(0);
			}
		}

		if (params.shouldSaveROIs()) {
			if (!params.shouldSaveImage()) {
				saveOverlay(outputImage, omeroOutputId, inputTitle, property);
			}
			saveROIManager(outputImage, omeroOutputId, inputTitle, property);
		}
		if (params.shouldSaveResults()) {
			saveResults(outputImage, omeroOutputId, inputTitle, property);
		}
		if (params.shouldSaveLog()) {
			saveLog(omeroOutputId, inputTitle);
		}

		for (ImagePlus imp : outputs) {
			imp.changes = false;
			imp.close();
		}
	}


	/**
	 * Saves an image.
	 *
	 * @param image    The image to save.
	 * @param property The ROI property to group shapes in OMERO.
	 *
	 * @return The OMERO IDs of the (possibly) uploaded image. Should be empty or contain one value.
	 */
	private List<Long> saveImage(ImagePlus image, String property) {
		List<Long> ids = new ArrayList<>(0);
		String title = removeExtension(image.getTitle());
		String path = params.getDirectoryOut() + File.separator +
					  title + params.getSuffix() + ".tif";
		IJ.saveAsTiff(image, path);
		if (params.isOutputOnOMERO()) {
			try {
				setState("Import on OMERO...");
				DatasetWrapper dataset = client.getDataset(params.getOutputDatasetId());
				ids = dataset.importImage(client, path);
				if (params.shouldSaveROIs() && !ids.isEmpty()) {
					saveOverlay(image, ids.get(0), title, property);
				}
			} catch (AccessException | ServiceException | OMEROServerError | ExecutionException e) {
				IJ.error("Could not import image: " + e.getMessage());
			}
		}
		return ids;
	}


	/**
	 * Saves the ROIs from an image overlay in ImageJ.
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveOverlay(ImagePlus imp, Long imageId, String title, String property) {
		if (params.isOutputOnLocal()) {  //  local save
			setState("Saving overlay ROIs...");
			String path = params.getDirectoryOut() + File.separator +
						  title + "_" + timestamp() + "_RoiSet.zip";
			List<Roi> ijRois = getOverlay(imp);
			saveRoiFile(ijRois, path);
		}
		if (params.isOutputOnOMERO() && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromOverlay(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (params.shouldClearROIs()) {
					deleteROIs(image);
				}
				setState("Saving overlay ROIs on OMERO...");
				image.saveROIs(client, rois);
				loadROIs(image, imp, true); // reload ROIs
			} catch (ServiceException | AccessException | ExecutionException e) {
				IJ.error("Could not import overlay ROIs to OMERO: " + e.getMessage());
			}
		}
	}


	/**
	 * Saves the ROIs from the ROI Manager (for an image).
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveROIManager(ImagePlus imp, Long imageId, String title, String property) {
		if (params.isOutputOnLocal()) {  //  local save
			setState("Saving ROIs...");
			String path = params.getDirectoryOut() + File.separator +
						  title + "_" + timestamp() + "_RoiSet.zip";
			List<Roi> ijRois = getManagedRois(imp);
			saveRoiFile(ijRois, path);
		}
		if (params.isOutputOnOMERO() && imageId != null) { // save on Omero
			List<ROIWrapper> rois = getROIsFromManager(imp, property);
			try {
				ImageWrapper image = client.getImage(imageId);
				if (params.shouldClearROIs()) {
					deleteROIs(image);
				}
				setState("Saving ROIs on OMERO...");
				image.saveROIs(client, rois);
				loadROIs(image, imp, false); // reload ROIs
			} catch (ServiceException | AccessException | ExecutionException e) {
				IJ.error("Could not import ROIs to OMERO: " + e.getMessage());
			}
		}
	}


	/**
	 * Saves the results (linked to an image).
	 *
	 * @param imp      The image.
	 * @param imageId  The image ID on OMERO.
	 * @param title    The image title used to name the file when saving locally.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void saveResults(ImagePlus imp, Long imageId, String title, String property) {
		List<Roi> ijRois = getOverlay(imp);
		ijRois.addAll(getManagedRois(imp));

		setState("Saving results files...");
		String[] candidates = WindowManager.getNonImageTitles();
		List<ResultsTable> results = Arrays.stream(candidates)
										   .map(ResultsTable::getResultsTable)
										   .collect(Collectors.toList());
		results.add(0, ResultsTable.getResultsTable());
		Map<String, Boolean> processed = new HashMap<>(results.size());
		for (ResultsTable rt : results) {
			if (rt != null) {
				String name = rt.getTitle();
				if (!Boolean.TRUE.equals(processed.get(name)) && rt.getHeadings().length > 0) {
					String path = params.getDirectoryOut() +
								  File.separator +
								  name + "_" +
								  title + "_" +
								  timestamp() + ".csv";
					rt.save(path);
					if (params.isOutputOnOMERO()) {
						appendTable(rt, imageId, ijRois, property);
						uploadFile(imageId, path);
					}
					rt.reset();
					processed.put(name, true);
				}
			}
		}
	}


	/**
	 * Saves the log.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param title   The image title used to name the file when saving locally.
	 */
	private void saveLog(Long imageId, String title) {
		String path = params.getDirectoryOut() + File.separator + title + "_log.txt";
		IJ.selectWindow("Log");
		IJ.saveAs("txt", path);
		if (params.isOutputOnOMERO()) {
			uploadFile(imageId, path);
		}
	}


	/**
	 * Uploads a file to an image on OMERO.
	 *
	 * @param imageId The image ID on OMERO.
	 * @param path    The path to the file.
	 */
	private void uploadFile(Long imageId, String path) {
		if (imageId != null) {
			try {
				setState("Uploading results files...");
				ImageWrapper image = client.getImage(imageId);
				image.addFile(client, new File(path));
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
			} catch (InterruptedException e) {
				IJ.error("Error adding file to image:" + e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}


	/**
	 * Adds the current results to the corresponding table.
	 *
	 * @param results  The results table.
	 * @param imageId  The image ID on OMERO.
	 * @param ijRois   The ROIs in ImageJ.
	 * @param property The ROI property used to group shapes on OMERO.
	 */
	private void appendTable(ResultsTable results, Long imageId, List<? extends Roi> ijRois, String property) {
		String resultsName = results.getTitle();
		TableWrapper table = tables.get(resultsName);
		try {
			if (table == null) {
				tables.put(resultsName, new TableWrapper(client, results, imageId, ijRois, property));
			} else {
				table.addRows(client, results, imageId, ijRois, property);
			}
		} catch (ServiceException | AccessException | ExecutionException e) {
			IJ.error("Could not create or append table: " + e.getMessage());
		}
	}


	/**
	 * Upload the tables to OMERO.
	 */
	private void uploadTables() {
		if (params.isOutputOnOMERO() && params.shouldSaveResults()) {
			setState("Uploading tables...");
			try {
				ProjectWrapper project = client.getProject(params.getOutputProjectId());
				for (Map.Entry<String, TableWrapper> entry : tables.entrySet()) {
					String name = entry.getKey();
					TableWrapper table = entry.getValue();
					String newName;
					if (name == null || name.isEmpty()) {
						newName = timestamp() + "_" + table.getName();
					} else {
						newName = timestamp() + "_" + name;
					}
					table.setName(newName);
					project.addTable(client, table);
					String path = params.getDirectoryOut() + File.separator +
								  newName + ".csv";
					table.saveAs(path, 'c');
					project.addFile(client, new File(path));
				}
			} catch (ExecutionException | ServiceException | AccessException e) {
				IJ.error("Could not save table: " + e.getMessage());
			} catch (IOException e) {
				IJ.error("Could not save table as file: " + e.getMessage());
			} catch (InterruptedException e) {
				IJ.error("Could not upload CSV to project: " + e.getMessage());
				Thread.currentThread().interrupt();
			}
		}
	}


	/**
	 * Closes all open windows in ImageJ.
	 */
	private void closeWindows() {
		for (Frame frame : WindowManager.getNonImageWindows()) {
			if (frame instanceof TextWindow) {
				((TextWindow) frame).close(false);
			}
		}
		rm.reset();
		WindowManager.closeAllWindows();
	}


	public Client getClient() {
		return client;
	}


	public void setListener(BatchListener listener) {
		this.listener = listener;
	}

}
