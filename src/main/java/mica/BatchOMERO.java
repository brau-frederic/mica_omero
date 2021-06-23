package mica;


import fr.igred.omero.Client;
import ij.plugin.PlugIn;
import mica.gui.ConnectDialog;
import mica.gui.BatchWindow;


public class BatchOMERO implements PlugIn {

    private static Client client = new Client();

    @Override
    public void run(String s) {
        try {
            // Ask for parameters:
            ConnectDialog connectDialog = new ConnectDialog(client);
            if(connectDialog.wasCancelled()) return;
            new BatchWindow(client);
        } finally {
            client.disconnect();
        }
    }

}