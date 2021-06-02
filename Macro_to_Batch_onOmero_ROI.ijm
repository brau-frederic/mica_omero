/* Test macro for analysis on a datset Omero with groovy script
 *  Input : 2D TIF image
 *  Thresholding and analyze particles with ROIs and results
 *  Output : 2D TIF Image, Results tab and ROIs
 *  F. Brau for Fiji/ImageJ 1.53j May 2021
*/


//Initialisation
run("Colors...", "foreground=white background=black selection=red");
run("Options...", "iterations=1 count=1 black edm=16-bit");
run("Set Measurements...", "area mean limit redirect=None decimal=2");
run("Clear Results");
roiManager("reset");
if (roiManager("count")>0) {
		roiManager("deselect");
		roiManager("delete");
	}
run("Clear Results");

while (nImages==0) waitForUser("Ouvrez une image");
rename("Image");
setAutoThreshold("Otsu dark stack");
run("Set Measurements...", "area mean limit redirect=None decimal=2");
run("Analyze Particles...", "display clear add");
//selectWindow("Image");
//resetThreshold();
close("Image");
