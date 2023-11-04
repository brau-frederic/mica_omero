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

import fr.igred.omero.Client;
import fr.igred.omero.exception.AccessException;
import fr.igred.omero.exception.ServiceException;
import fr.igred.omero.repository.ImageWrapper;
import fr.igred.omero.roi.ROIWrapper;
import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Image from OMERO.
 */
public class OMEROBatchImage implements BatchImage {

	private static final Logger LOGGER = Logger.getLogger(MethodHandles.lookup().lookupClass().getName());

	private final Client client;

	public OMEROBatchImage(ScriptRunner script, Client client) {
		this.client = client;
		this.progress = progress;
		this.directoryIn = "";
		this.suffix = "";
		this.directoryOut = null;
		this.rm = null;
		this.listener = null;
	}


	/**
	 * Opens an image from OMERO.
	 *
	 * @param image An OMERO image.
	 *
	 * @return An ImagePlus.
	 */
	private ImagePlus openImage(ImageWrapper image) {
		setState("Opening image from OMERO...");
		ImagePlus imp = null;
		try {
			imp = image.toImagePlus(client);
			// Store image "annotate" permissions as a property in the ImagePlus object
			imp.setProp("Annotatable", String.valueOf(image.canAnnotate()));
		} catch (ExecutionException | ServiceException | AccessException e) {
			IJ.error("Could not load image: " + e.getMessage());
		}
		return imp;
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

}
