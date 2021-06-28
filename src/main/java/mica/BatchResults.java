package mica;

import java.util.Collection;
import java.util.List;

import omero.gateway.model.ROIData;

public class BatchResults {

private List<String> pathsImages;
private List<String> pathsAttach;
private List<Collection<ROIData>> mROIS;
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

public void setmROIS(List<Collection<ROIData>> mROIS ) {
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

public List<Collection<ROIData>> getmROIS() {
    return mROIS;
}

public List<Long> getImageIds() {
    return imageIds;
}

public boolean getImaRes() {
    return imaRes;
}

}