package mica;

import fr.igred.omero.roi.ROIWrapper;

import java.util.Collection;
import java.util.List;

public class BatchResults {

private List<String> pathsImages;
private List<String> pathsAttach;
private List<Collection<ROIWrapper>> mROIS;
private List<Long> imageIds;
private boolean imaRes;

public BatchResults() {
    // Crée un objet BatchResults
}

public void setPathImages(List<String> pathsImages ) {
    this.pathsImages = pathsImages;
}

public void setPathAttach(List<String> pathsAttach ) {
    this.pathsAttach = pathsAttach;
}

public void setmROIS(List<Collection<ROIWrapper>> mROIS ) {
    this.mROIS = mROIS;
}

public void setImageIds(List<Long> imageIds ) {
    this.imageIds = imageIds;
}

public void setImaRes(boolean imaRes ) {
    this.imaRes = imaRes;
}

public List<String> getPathImages() {
    return pathsImages;
}

public List<String> getPathAttach() {
    return pathsAttach;
}

public List<Collection<ROIWrapper>> getmROIS() {
    return mROIS;
}

public List<Long> getImageIds() {
    return imageIds;
}

public boolean getImaRes() {
    return imaRes;
}

}