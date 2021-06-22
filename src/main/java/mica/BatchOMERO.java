package mica;


import ij.plugin.PlugIn;


public class BatchOMERO implements PlugIn {

    @Override
    public void run(String s) {
        // Entry point
        new Connexion();
    }

}