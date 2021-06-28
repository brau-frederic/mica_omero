package mica;

import fr.igred.omero.Client;

public class BatchData {

	private final Client client;

	private boolean inputOnOMERO;
	private boolean newDataSet;
	private boolean saveImage;
	private boolean saveROIs;
	private boolean saveResults;
	private boolean loadROIs;
	private boolean clearROIs;
	private boolean outputOnOMERO;
	private boolean outputOnLocal;
	private long inputDatasetId;
	private long outputDatasetId;
	private long inputProjectId;
	private long outputProjectId;

	// suffixe?

	public BatchData(Client client) {
		this.client = client;
	}


	public long getOutputProjectId() {
		return outputProjectId;
	}


	public void setOutputProjectId(Long outputProjectId) {
		if (outputProjectId != null) this.outputProjectId = outputProjectId;
	}


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(Long outputDatasetId) {
		if (outputDatasetId != null) this.outputDatasetId = outputDatasetId;
	}


	public long getInputProjectId() {
		return inputProjectId;
	}


	public void setInputProjectId(Long inputProjectId) {
		if (inputProjectId != null) this.inputProjectId = inputProjectId;
	}


	public long getInputDatasetId() {
		return inputDatasetId;
	}


	public void setInputDatasetId(Long inputDatasetId) {
		if (inputDatasetId != null) this.inputDatasetId = inputDatasetId;
	}


	public boolean shouldSaveROIs() {
		return saveROIs;
	}


	public void setSaveROIs(boolean saveROIs) {
		this.saveROIs = saveROIs;
	}


	public boolean shouldSaveResults() {
		return saveResults;
	}


	public void setSaveResults(boolean saveResults) {
		this.saveResults = saveResults;
	}


	public boolean isInputOnOMERO() {
		return inputOnOMERO;
	}


	public void setInputOnOMERO(boolean inputOnOMERO) {
		this.inputOnOMERO = inputOnOMERO;
	}


	public Client getClient() {
		return client;
	}

	public boolean shouldSaveImage() {
		return saveImage;
	}


	public void setSaveImage(boolean saveImage) {
		this.saveImage = saveImage;
	}

	public boolean shouldLoadROIs() {
		return loadROIs;
	}


	public void setLoadROIS(boolean loadROIs) {
		this.loadROIs = loadROIs;
	}

	public boolean shouldClearROIs() {
		return clearROIs;
	}


	public void setClearROIS(boolean clearROIs) {
		this.clearROIs = clearROIs;
	}

	public String getDirectoryIn() {
		return directoryIn;
	}


	public void setDirectoryIn(String directoryIn) {
		this.directoryIn = directoryIn;
	}

	public String getDirectoryOut() {
		return directoryOut;
	}


	public void setDirectoryOut(String directoryOut) {
		this.directoryOut = directoryOut;
	}

	public String getMacro() {
		return macroChosen;
	}


	public void setMacro(String macroChosen) {
		this.macroChosen = macroChosen;
	}

	public String getExtension() {
		return extensionChosen;
	}


	public void setExtension(String extensionChosen) {
		this.extensionChosen = extensionChosen;
	}

	public boolean shouldnewDataSet() {
		return newDataSet;
	}


	public void setnewDataSet(boolean newDataSet) {
		this.newDataSet = newDataSet;
	}

	public String getNameNewDataSet() {
		return nameNewDataSet;
	}


	public void setNameNewDataSet(String nameNewDataSet) {
		this.nameNewDataSet = nameNewDataSet;
	}

	public long getProjectIdOut() {
		return projectIdOut;
	}


	public void setProjectIdOut(long projectIdOut) {
		this.projectIdOut = projectIdOut;
	}

	public boolean isOutputOnOMERO() {
		return outputOnOMERO;
	}


	public void setOutputOnOMERO(boolean outputOnOMERO) {
		this.outputOnOMERO = outputOnOMERO;
	}

	public boolean isOutputOnLocal() {
		return outputOnLocal;
	}


	public void setOutputOnLocal(boolean outputOnLocal) {
		this.outputOnLocal = outputOnLocal;
	}
}
