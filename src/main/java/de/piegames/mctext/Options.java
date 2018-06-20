package de.piegames.mctext;

public class Options {

	public final boolean prettyPrinting;
	public final boolean keepUnusedData;
	public boolean writeJSON;

	public final boolean overwriteExisting;
	public final boolean dryRun;
	public final boolean failFast;
	public final boolean delete;
	public final boolean checkTimestamps;

	// Not implemented yet TODO
	// public final Level level;

	public Options() {
		this(false, false, false, false, false, false, false);
	}

	public Options(boolean prettyPrinting, boolean keepUnusedData, boolean dryRun, boolean overwriteExisting, boolean failFast, boolean delete,
			boolean checkTimestamps) {
		this.prettyPrinting = prettyPrinting;
		this.keepUnusedData = keepUnusedData;
		this.dryRun = dryRun;
		this.overwriteExisting = overwriteExisting;
		this.failFast = failFast;
		this.delete = delete;
		this.checkTimestamps = checkTimestamps;
	}
}