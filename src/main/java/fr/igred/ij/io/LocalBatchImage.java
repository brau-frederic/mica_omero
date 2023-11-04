/*
 *  Copyright (C) 2021-2022 MICA & GReD
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
package fr.igred.ij.io;

import fr.igred.omero.repository.ImageWrapper;
import ij.ImagePlus;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Image stored in a local file.
 */
public class LocalBatchImage implements BatchImage {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private static final File[] EMPTY_FILE_ARRAY = new File[0];

	private final String path;
	private final Integer index;


	public LocalBatchImage(String path, Integer index, boolean loadROIs) {
		this.path = path;
		this.index = index;
	}


	/**
	 * List the files contained in the directory.
	 *
	 * @param directory The directory.
	 * @param recursive Whether files should be listed recursively.
	 *
	 * @return The list of file paths.
	 */
	private static List<String> getFilesFromDirectory(String directory, boolean recursive) {
		File dir = new File(directory);
		File[] files = dir.listFiles();
		if (files == null) files = EMPTY_FILE_ARRAY;
		List<String> paths = new ArrayList<>(files.length);
		for (File file : files) {
			String path = file.getAbsolutePath();
			if (!file.isDirectory()) {
				paths.add(path);
			} else if (recursive) {
				paths.addAll(getFilesFromDirectory(path, true));
			}
		}
		return paths;
	}


	/**
	 * Retrieves the images in a list of files using Bio-Formats.
	 *
	 * @param files   The list of files.
	 * @param options The Bio-Formats importer options.
	 *
	 * @return The list of images.
	 */
	private static List<BatchImage> getImagesFromFiles(Collection<String> files, ImporterOptions options) {
		List<String> used = new ArrayList<>(files.size());
		Map<String, Integer> imageFiles = new LinkedHashMap<>(files.size());
		for (String file : files) {
			if (!used.contains(file)) {
				// Open the image
				options.setId(file);
				ImportProcess process = new ImportProcess(options);
				try {
					process.execute();
					int n = process.getSeriesCount();
					FileStitcher fs = process.getFileStitcher();
					if (fs != null) used = Arrays.asList(fs.getUsedFiles());
					else used.add(file);
					imageFiles.put(file, n);
				} catch (IOException | FormatException e) {
					LOGGER.info(e.getMessage());
				}
			}
		}
		return imageFiles;
	}


	/**
	 * Initializes the Bio-Formats importer options.
	 *
	 * @return See above.
	 *
	 * @throws IOException If the importer options could not be initialized.
	 */
	private static ImporterOptions initImporterOptions() throws IOException {
		ImporterOptions options = new ImporterOptions();
		options.setStackFormat(ImporterOptions.VIEW_HYPERSTACK);
		options.setSwapDimensions(false);
		options.setOpenAllSeries(false);
		options.setSpecifyRanges(false);
		options.setShowMetadata(false);
		options.setShowOMEXML(false);
		options.setCrop(false);
		options.setSplitChannels(false);
		options.setSplitFocalPlanes(false);
		options.setSplitTimepoints(false);
		return options;
	}


	/**
	 * Returns null.
	 *
	 * @return See above.
	 */
	@Override
	public ImageWrapper getImageWrapper() {
		return null;
	}


	/**
	 * Runs a macro on local files and saves the results.
	 *
	 * @param files List of image files.
	 *
	 * @throws IOException A problem occurred reading a file.
	 */
	void runMacroOnLocalImages(Collection<String> files) throws IOException {
		String property = ROIWrapper.IJ_PROPERTY;
		WindowManager.closeAllWindows();

		ImporterOptions options = initImporterOptions();
		Map<String, Integer> imageFiles = getImagesFromFiles(files, options);
		int nFile = 1;
		for (Map.Entry<String, Integer> entry : imageFiles.entrySet()) {
			int n = entry.getValue();
			options.setId(entry.getKey());
			for (int i = 0; i < n; i++) {
				String msg = String.format("File %d/%d, image %d/%d", nFile, imageFiles.size(), i + 1, n);
				setProgress(msg);
				options.setSeriesOn(i, true);
				try {
					ImagePlus[] imps = BF.openImagePlus(options);
					ImagePlus imp = imps[0];
					imp.show();

					// Initialize ROI Manager
					initRoiManager();

					// Analyse the image
					script.setImage(imp);
					script.run();

					// Save and Close the various components
					imp.changes = false; // Prevent "Save Changes?" dialog
					save(imp, null, property);
				} catch (FormatException e) {
					IJ.error(e.getMessage());
				}
				closeWindows();
				options.setSeriesOn(i, false);
			}
			nFile++;
		}
	}

}
