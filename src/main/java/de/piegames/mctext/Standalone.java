package de.piegames.mctext;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

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

@Command(name = "mctext",
		subcommands = {
				HelpCommand.class,
				BackupFileCommand.class,
				BackupWorldCommand.class,
				RestoreFileCommand.class,
				RestoreWorldCommand.class })
public class Standalone implements Runnable {

	public static enum CommandType {
		BACKUP_FILE, BACKUP_WORLD, RESTORE_FILE, RESTORE_WORLD;
	}

	public static abstract class ConvertCommand implements Callable<BackupHelper> {
		@Option(names = { "--dry-run", "-n" }, description = "Spam the log without actually doing anything")
		public boolean	dryRun;

		@Option(names = { "--verbose", "-v" }, description = "More log information")
		public boolean	verbose;

		@Option(names = { "--force", "-f" }, description = "Overwrite existing files")
		public boolean	overwriteExisting;

		@Option(names = { "--keep-unused", "-u" },
				description = "Minecraft worlds contain a certain amount of unused data. This is due to aligning chunks"
						+ " to 4k sectors in the file and the fragmentation resulting from it. By default, MCText omits this data to save space, which will result"
						+ " in slightly different files when restoring. If this flag is set, saving and restoring files will have them to be the same down to every"
						+ " single byte. If this flag is set while restoring but not while creating the backup in the first place, it will silently be ignored.")
		public boolean	keepUnusedData;

		@Option(names = { "--decompress" },
				description = "Do not convert the files to json text, but to NBT instead. This NBT will not be compressed. Region/Anvil files will be converted to NBT too. Compared to plain text, "
						+ "the resulting files will be much smaller. Compared to the original, it will be easier to back it up with compression or deduplication. If you want to compress entire worlds,"
						+ "decompress their data first because compressing multiple times usually reduces the efficiency. If this has been applied to backup data, you must"
						+ "specify this flag when restoring too. The application will make no checks regarding this.")
		public boolean	decompress;

		@Option(names = { "--nbt-compression", "-c" },
				description = "NBT files may have one of three compressions: 0 - None, 1 - Gzip, 2 - Zlib. Normally, all Minecraft NBT files have compression 1. Compression 2 is only used for NBT data"
						+ "whithin Anvil files, which is not affected by this option. Sometimes, there are uncompressed NBT files. These are mostly from third-party tools like MCEdit. Within a backup or restore"
						+ "command, all nbt files must have been compressed the same way. With this option, you can change the compression method that will be used to process these files.",
				defaultValue = "1")
		public int		nbtCompression;

		@Option(names = { "--fail-fast" },
				description = "Fail and abort the process on the first exception. If not set, the backup will continue normally"
						+ " and just skip all failed files instead.")
		public boolean	failFast;

		@Option(names = { "--delete-destination", "-d" },
				description = "Delete all existing files in the destination directory before converting. You should always check this unless you know"
						+ " that the a) the destination contains an earlier version of the same data and b) no files got renamed or deleted since the"
						+ " destination last got written.")
		public boolean	delete;

		@Option(names = { "--lazy", "-l" },
				description = "Skip files that have a newer timestamp than the original, assuming that the original hasn't changed since then. Use in "
						+ "combination with --force to speed up repeated conversions to the same destination over time. The result may not be correct if "
						+ "files in the input data got deleted or renamed, run with --delete instead in those cases.")
		public boolean	lazy;

		@Parameters(index = "0",
				paramLabel = "SOURCE",
				description = "The location of the file or folder containing the original data when backing up, and"
						+ " the location of the backup when restoring")
		public Path		source;
		@Parameters(index = "1",
				paramLabel = "DESTINATION",
				description = "The location of the file or folder that will contain the converted data after"
						+ "executing the operation. This will be your backup when backing up and your broken world when restoring")
		public Path		destination;
	}

	public static abstract class BackupCommand extends ConvertCommand {
		@Option(names = { "--pretty", "-p" },
				description = "Format and indent the resulting json code. Slightly increases file size, but git"
						+ " works a lot better when checking this.")
		public boolean prettyPrinting;

	}

	public static abstract class RestoreCommand extends ConvertCommand {
	}

	@Command(name = "backup-file", description = "Backs up a single file", showDefaultValues = true)
	public static class BackupFileCommand extends BackupCommand {

		@Override
		public BackupHelper call() throws IOException {
			if (verbose)
				Configurator.setRootLevel(Level.DEBUG);
			BackupHelper backup = new BackupHelper(prettyPrinting, keepUnusedData, dryRun, nbtCompression, decompress, overwriteExisting, failFast, delete,
					lazy);
			backup.backupFile(source, destination);
			return backup;
		}
	}

	@Command(name = "backup-world", description = "Back up a folder containing a whole world", showDefaultValues = true)
	public static class BackupWorldCommand extends BackupCommand {

		@Override
		public BackupHelper call() throws IOException {
			if (verbose)
				Configurator.setRootLevel(Level.DEBUG);
			BackupHelper backup = new BackupHelper(prettyPrinting, keepUnusedData, dryRun, nbtCompression, decompress, overwriteExisting, failFast, delete,
					lazy);
			backup.backupWorld(source, destination);
			return backup;
		}
	}

	@Command(name = "restore-file", description = "Restore a single file", showDefaultValues = true)
	public static class RestoreFileCommand extends RestoreCommand {

		@Override
		public BackupHelper call() throws IOException {
			if (verbose)
				Configurator.setRootLevel(Level.DEBUG);
			BackupHelper backup = new BackupHelper(false, keepUnusedData, dryRun, nbtCompression, decompress, overwriteExisting, failFast, delete, lazy);
			backup.restoreFile(source, destination);
			return backup;
		}
	}

	@Command(name = "restore-world", description = "Restore a folder containing a whole world", showDefaultValues = true)
	public static class RestoreWorldCommand extends RestoreCommand {

		@Override
		public BackupHelper call() throws IOException {
			if (verbose)
				Configurator.setRootLevel(Level.DEBUG);
			BackupHelper backup = new BackupHelper(false, keepUnusedData, dryRun, nbtCompression, decompress, overwriteExisting, failFast, delete, lazy);
			backup.restoreWorld(source, destination);
			return backup;
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