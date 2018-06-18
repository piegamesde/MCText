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
		public boolean safeMode;

		@Option(names = { "--force", "-f" }, description = "Overwrite existing files")
		public boolean overwriteExisting;

		@Option(names = { "--keep-unused", "-u" }, description = "Minecraft worlds contain a certain amount of unused data. This is due to aligning chunks"
				+ " to 4k sectors in the file and the fragmentation resulting from it. By default, MCText omits this data to save space, which will result"
				+ " in slightly different files when restoring. If this flag is set, saving and restoring files will have them to be the same down to every"
				+ " single byte. If this flag is set while restoring but not while creating the backup in the first place, it will silently be ignored.")
		public boolean keepUnusedData;
	}

	public static abstract class BackupCommand extends ConvertCommand {
		@Option(names = { "--pretty", "-p" }, description = "Format and indent the resulting json code. Increases file size, but useful for debugging or manual"
				+ " intervention.")
		public boolean prettyPrinting;

		@Parameters(index = "0", paramLabel = "ORIGINAL", description = "The location of the file or folder containing the original data")
		public Path source;
		@Parameters(index = "1", paramLabel = "BACKUP", description = "The location of the file ore folder containing the destination data for the backup")
		public Path destination;

	}

	public static abstract class RestoreCommand extends ConvertCommand {

		@Parameters(index = "0", paramLabel = "SOURCE")
		public Path source;
		@Parameters(index = "1", paramLabel = "DEST")
		public Path destination;
	}

	@Command(name = "backup-file")
	public static class BackupFileCommand extends BackupCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(prettyPrinting, keepUnusedData, safeMode, overwriteExisting, false, false));
			converter.backupFile(source, destination);
			return converter;
		}
	}

	@Command(name = "backup-world")
	public static class BackupWorldCommand extends BackupCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(prettyPrinting, keepUnusedData, safeMode, overwriteExisting, false, false));
			converter.backupWorld(source, destination);
			return converter;
		}
	}

	@Command(name = "restore-file")
	public static class RestoreFileCommand extends RestoreCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(false, keepUnusedData, safeMode, overwriteExisting, false, false));
			converter.restoreFile(source, destination);
			return converter;
		}
	}

	@Command(name = "restore-world")
	public static class RestoreWorldCommand extends RestoreCommand {

		@Override
		public Converter call() throws IOException {
			Converter converter = new Converter(new Options(false, keepUnusedData, safeMode, overwriteExisting, false, false));
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