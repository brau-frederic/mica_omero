package mica.process;

public interface ProcessingProgress {

	public void setProgress(String text);

	public void setState(String text);

	public void setDone();
}
