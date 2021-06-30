package mica;


import fr.igred.omero.Client;
import ij.plugin.PlugIn;
import mica.gui.ConnectDialog;
import mica.gui.BatchWindow;
import mica.gui.ProgressDialog;
import mica.process.BatchRunner;


public class BatchOMERO implements PlugIn {

    @Override
    public void run(String s) {
		Client client = new Client();
		// Ask for parameters:
		ConnectDialog connectDialog = new ConnectDialog(client);
		if(connectDialog.wasCancelled()) return;

		ProgressDialog progress = new ProgressDialog();
		BatchRunner runner = new BatchRunner(client, progress);

		new BatchWindow(runner);
    }

}