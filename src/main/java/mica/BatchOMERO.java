package mica;


import fr.igred.omero.Client;
import ij.plugin.PlugIn;
import mica.gui.ConnectDialog;
import mica.gui.BatchWindow;


public class BatchOMERO implements PlugIn {

    @Override
    public void run(String s) {
		Client client = new Client();
        try {
            // Ask for parameters:
            ConnectDialog connectDialog = new ConnectDialog(client);
            if(connectDialog.wasCancelled()) return;
            BatchData data = new BatchData(client);
            new BatchWindow(data);
        } finally {
            client.disconnect();
        }
    }

}