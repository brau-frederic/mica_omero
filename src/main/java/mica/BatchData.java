package mica;

import fr.igred.omero.Client;

public class BatchData {

	private final Client client;

	private boolean inputOnOMERO;
	private boolean saveROIs;
	private boolean saveResults;
	private long inputDatasetId;
	private long outputDatasetId;


	public BatchData(Client client) {
		this.client = client;
	}


	public long getOutputDatasetId() {
		return outputDatasetId;
	}


	public void setOutputDatasetId(long outputDatasetId) {
		this.outputDatasetId = outputDatasetId;
	}


	public long getInputDatasetId() {
		return inputDatasetId;
	}


	public void setInputDatasetId(long inputDatasetId) {
		this.inputDatasetId = inputDatasetId;
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

}
