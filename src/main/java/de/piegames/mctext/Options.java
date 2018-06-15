package de.piegames.mctext;

public class Options {

	public final boolean prettyPrinting;
	public final boolean keepUnusedData;
	public final boolean safeMode;
	public final boolean overwriteExisting;

	public Options() {
		prettyPrinting = false;
		keepUnusedData = false;
		safeMode = false;
		overwriteExisting = false;
	}

	public Options(boolean prettyPrinting, boolean keepUnusedData, boolean safeMode, boolean overwriteExisting) {
		this.prettyPrinting = prettyPrinting;
		this.keepUnusedData = keepUnusedData;
		this.safeMode = safeMode;
		this.overwriteExisting = overwriteExisting;
	}
	public static final Options DEFAULT_OPTIONS = new Options();
	public static final Options DEBUG_OPTIONS = new Options(true, true, false, false);
}