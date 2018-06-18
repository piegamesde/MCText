package de.piegames.mctext;

public class Options {

	public final boolean prettyPrinting;
	public final boolean keepUnusedData;
	public final boolean dryRun;
	public final boolean overwriteExisting;

	// Not implemented yet TODO
	// public final Level level;
	public final boolean failFast;
	public final boolean delete;

	public Options() {
		prettyPrinting = false;
		keepUnusedData = false;
		dryRun = false;
		overwriteExisting = false;
		failFast = false;
		delete = false;
	}

	public Options(boolean prettyPrinting, boolean keepUnusedData, boolean dryRun, boolean overwriteExisting, boolean failFast, boolean delete) {
		this.prettyPrinting = prettyPrinting;
		this.keepUnusedData = keepUnusedData;
		this.dryRun = dryRun;
		this.overwriteExisting = overwriteExisting;
		this.failFast = failFast;
		this.delete = delete;
	}

	public static final Options DEFAULT_OPTIONS = new Options();
	public static final Options DEBUG_OPTIONS = new Options(true, true, false, false, true, false);
}