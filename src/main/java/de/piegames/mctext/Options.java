package de.piegames.mctext;

public class Options {

	public final boolean prettyPrinting;
	public final boolean keepUnusedData;
	public final boolean dryRun;
	public final boolean overwriteExisting;

	public Options() {
		prettyPrinting = false;
		keepUnusedData = false;
		dryRun = false;
		overwriteExisting = false;
	}

	public Options(boolean prettyPrinting, boolean keepUnusedData, boolean dryRun, boolean overwriteExisting) {
		this.prettyPrinting = prettyPrinting;
		this.keepUnusedData = keepUnusedData;
		this.dryRun = dryRun;
		this.overwriteExisting = overwriteExisting;
	}

	public static final Options DEFAULT_OPTIONS = new Options();
	public static final Options DEBUG_OPTIONS = new Options(true, true, false, false);
}