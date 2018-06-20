package de.piegames.mctext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import de.piegames.mctext.Standalone.BackupFileCommand;
import de.piegames.mctext.Standalone.BackupWorldCommand;
import de.piegames.mctext.Standalone.RestoreFileCommand;
import de.piegames.mctext.Standalone.RestoreWorldCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.RunLast;

@Command(name = "mctext", subcommands = {
		HelpCommand.class,
		BackupFileCommand.class,
		BackupWorldCommand.class,
		RestoreFileCommand.class,
		RestoreWorldCommand.class })

public class Standalone implements Runnable {

	public static enum CommandType {
		BACKUP_FILE, BACKUP_WORLD, RESTORE_FILE, RESTORE_WORLD;
	}

	public static abstract class ConvertCommand implements Callable<Converter> {
		@Option(names = { "--dry-run", "-n" }, description = "Spam the log without actually doing anything")
		public boolean dryRun;

		@Option(names = { "--force", "-f" }, description = "Overwrite existing files")
		public boolean overwriteExisting;

		@Option(names = { "--keep-unused", "-u" }, description = "Minecraft worlds contain a certain amount of unused data. This is due to aligning chunks"
				+ " to 4k sectors in the file and the fragmentation resulting from it. By default, MCText omits this data to save space, which will result"
				+ " in slightly different files when restoring. If this flag is set, saving and restoring files will have them to be the same down to every"
				+ " single byte. If this flag is set while restoring but not while creating the backup in the first place, it will silently be ignored.")
		public boolean keepUnusedData;

		@Option(names = { "--fail-fast" }, description = "Fail and abort the process on the first exception. If not set, the backup will continue normally"
				+ " and just skip all failed files instead.")
		public boolean failFast;

		@Option(names = { "--delete-destination",
				"-d" }, description = "Delete all existing files in the destination directory before converting. You should always check this unless you know"
						+ " that the a) the destination contains an earlier version of the same data and b) no files got renamed or deleted since the"
						+ " destination last got written.")
		public boolean delete;

		@Parameters(index = "0", paramLabel = "SOURCE", description = "The location of the file or folder containing the original data when backing up, and"
				+ " the location of the backup when restoring")
		public Path source;
		@Parameters(index = "1", paramLabel = "DESTINATION", description = "The location of the file or folder that will contain the converted data after"
				+ "executing the operation. This will be your backup when backing up and your broken world when restoring")
		public Path destination;
	}

	public static abstract class BackupCommand extends ConvertCommand {
		@Option(names = { "--pretty", "-p" }, description = "Format and indent the resulting json code. Slightly increases file size, but git"
				+ " works a lot better when checking this.")
		public boolean prettyPrinting;

	}

	public static abstract class RestoreCommand extends ConvertCommand {
	}

	@Command(name = "backup-file", description = "Backs up a single file", showDefaultValues = true)
	public static class BackupFileCommand extends BackupCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(prettyPrinting, keepUnusedData, dryRun, overwriteExisting, false, false, false));
			converter.backupFile(source, destination);
			return converter;
		}
	}

	@Command(name = "backup-world", description = "Back up a folder containing a whole world", showDefaultValues = true)
	public static class BackupWorldCommand extends BackupCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(prettyPrinting, keepUnusedData, dryRun, overwriteExisting, false, false, false));
			converter.backupWorld(source, destination);
			return converter;
		}
	}

	@Command(name = "restore-file", description = "Restore a single file", showDefaultValues = true)
	public static class RestoreFileCommand extends RestoreCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(false, keepUnusedData, dryRun, overwriteExisting, false, false, false));
			converter.restoreFile(source, destination);
			return converter;
		}
	}

	@Command(name = "restore-world", description = "Restore a folder containing a whole world", showDefaultValues = true)
	public static class RestoreWorldCommand extends RestoreCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(false, keepUnusedData, dryRun, overwriteExisting, false, false, false));
			converter.restoreWorld(source, destination);
			return converter;
		}
	}

	public Standalone() {
	}

	@Override
	public void run() {
		CommandLine.usage(this, System.err);
	}

	public static void main(String[] args) {
		CommandLine cli = new CommandLine(new Standalone());
		cli.parseWithHandler(new RunLast(), args);
	}
}