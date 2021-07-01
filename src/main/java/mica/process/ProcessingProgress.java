package mica.process;

public interface ProcessingProgress {

	void setProgress(String text);

	void setState(String text);

	void setDone();
}
