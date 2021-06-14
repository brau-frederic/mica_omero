/* Test macro for analysis on a datset Omero with groovy script
 *  Input : 2D TIF image
 *  Thresholding and analyze particles with ROIs and results
 *  Output : 2D TIF Image, Results tab and ROIs depending on choices
 *  F. Brau for Fiji/ImageJ 1.53j May 2021
*/

//Initialisation
run("Colors...", "foreground=white background=black selection=red");
run("Options...", "iterations=1 count=1 black edm=16-bit");
run("Set Measurements...", "area mean limit redirect=None decimal=2");
run("Clear Results");
chemin=getDirectory("macros");

/* The dialog box is executed only if the macro is called for the first time by the groovy script.
 In this case values are stored in a temporary file "Parameters_Macro_toBatch.txt" (which is previously deleted)
 in the Fiji\macros directory. The values are read in the file during the following executions.
 */
execution=getArgument();
if (execution=='0'){
	Dialog.create("Test Macro for Batch Macro on Omero");
	Dialog.addCheckbox("Load ROIs from Omero", false);
	Dialog.addCheckbox("Returns a new image at the end", false);
	Dialog.show();
	ROIs_from_Omero=Dialog.getCheckbox();
	New_Image=Dialog.getCheckbox();
	File.delete(chemin+"Parameters_Macro_toBatch.txt");
	file_temp = File.open(chemin+"Parameters_Macro_toBatch.txt");
	print(file_temp, ROIs_from_Omero + "  \t" + New_Image);
	File.close(file_temp);
}
if (execution!='0'){
	str=File.openAsString(chemin+"Parameters_Macro_toBatch.txt"); 
	lines=split(str,"\t");
  	ROIs_from_Omero=parseFloat(lines[0]);
 	New_Image=parseFloat(lines[1]);
}

while (nImages==0) waitForUser("Ouvrez une image");
rename("Image");

if (ROIs_from_Omero==true) roiManager("multi-measure measure_all");
else {
	roiManager("reset");
	if (roiManager("count")>0) {
		roiManager("deselect");
		roiManager("delete");
	}
	run("Clear Results");
	setAutoThreshold("Otsu dark stack");
	run("Set Measurements...", "area mean limit redirect=None decimal=2");
	run("Analyze Particles...", "display clear add");
}
if (New_Image==true){
	selectWindow("Image");
	resetThreshold();
	wait(100);
	run("Flatten");
	close("Image");
}
else close("Image");
