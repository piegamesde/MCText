package de.piegames.mctext;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;

public class BackupHelper {
	final Logger log = LogManager.getLogger(this);

	public static final PathMatcher nbt = FileSystems.getDefault().getPathMatcher("glob:**.{dat,dat_old,dat_new,nbt}");
	public static final PathMatcher anvil = FileSystems.getDefault().getPathMatcher("glob:**.{mca,mcr}");

	protected Converter converter;

	public boolean writeJSON;
	public final boolean overwriteExisting;
	public final boolean dryRun;
	public final boolean failFast;
	public final boolean delete;
	public final boolean checkTimestamps;

	public BackupHelper(boolean prettyPrinting, boolean keepUnusedData, boolean dryRun, boolean overwriteExisting, boolean failFast, boolean delete,
			boolean checkTimestamps) {
		this(new Converter(prettyPrinting, keepUnusedData), dryRun, overwriteExisting, failFast, delete, checkTimestamps);
	}

	public BackupHelper(Converter converter, boolean dryRun, boolean overwriteExisting, boolean failFast, boolean delete,
			boolean checkTimestamps) {
		this.converter = Objects.requireNonNull(converter);

		this.dryRun = dryRun;
		this.overwriteExisting = overwriteExisting;
		this.failFast = failFast;
		this.delete = delete;
		this.checkTimestamps = checkTimestamps;
	}

	public void backupFile(Path source, Path destination) throws IOException {
		if (nbt.matches(source))
			backupNBT(source, destination);
		else if (anvil.matches(source)) {
			backupAnvil(source, destination);
		} else {
			log.debug(source + " does not seem to be an nbt file and will be copied");
			if (!dryRun) {
				if (overwriteExisting)
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				else
					Files.copy(source, destination);
			}
		}
	}

	public void backupNBT(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as nbt file");
		if (Files.exists(destination) && !overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (dryRun)
			return;
		try (NBTInputStream s = new NBTInputStream(Files.newInputStream(source));
				Writer writer = Files.newBufferedWriter(destination)) {
			writer.write(converter.gson.toJson(s.readTag()));
		} catch (ZipException e) {
			log.warn("Could not unzip file, trying uncompressed nbt");
			try (NBTInputStream s = new NBTInputStream(Files.newInputStream(source), NBTInputStream.NO_COMPRESSION);
					Writer writer = Files.newBufferedWriter(destination)) {
				writer.write(converter.gson.toJson(s.readTag()));
			}
		}
	}

	public void backupAnvil(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as anvil file");
		if (Files.exists(destination) && !overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (dryRun)
			return;
		try (Writer writer = Files.newBufferedWriter(destination)) {
			writer.write(converter.gson.toJson(new RegionFile(source)));
		}
	}

	public void backupWorld(Path source, Path destination) throws IOException {
		for (Path file : walkTree(source, destination))
			try {
				backupFile(file, destination.resolve(source.relativize(file)));
			} catch (IOException | RuntimeException e) {
				if (failFast)
					throw e;
				else
					log.error("Could not back up file " + file, e);
			}
	}

	public void restoreFile(Path source, Path destination) throws IOException {
		if (nbt.matches(source))
			restoreNBT(source, destination);
		else if (anvil.matches(source)) {
			restoreAnvil(source, destination);
		} else {
			log.debug(source + " does not seem to be an nbt file and will be copied");
			if (!dryRun) {
				if (overwriteExisting)
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				else
					Files.copy(source, destination);
			}
		}
	}

	public void restoreNBT(Path source, Path destination) throws IOException {
		log.debug("Restoring " + source + " as nbt file");
		if (Files.exists(destination) && !overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (dryRun)
			return;
		try (NBTOutputStream s = new NBTOutputStream(Files.newOutputStream(destination))) {
			s.writeTag(converter.gson.fromJson(new String(Files.readAllBytes(source)), CompoundTag.class));
		}
	}

	public void restoreAnvil(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as anvil file");
		if (Files.exists(destination) && !overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (dryRun)
			return;
		converter.gson.fromJson(new String(Files.readAllBytes(source)), RegionFile.class).write(destination);
	}

	public void restoreWorld(Path source, Path destination) throws IOException {
		for (Path file : walkTree(source, destination))
			try {
				restoreFile(file, destination.resolve(source.relativize(file)));
			} catch (IOException | RuntimeException e) {
				if (failFast)
					throw e;
				else
					log.error("Could not restore file " + file, e);
			}
	}

	Queue<Path> walkTree(Path source, Path destination) throws IOException {
		if (delete)
			FileUtils.deleteDirectory(destination.toFile());

		Queue<Path> files = new LinkedList<>();
		Files.walkFileTree(source, new FileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				dir = destination.resolve(source.relativize(dir));
				if (!Files.exists(dir))
					log.debug("Creating folder " + dir);
				Files.createDirectories(dir);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path localSource, BasicFileAttributes attrs) throws IOException {
				Path localDestination = destination.resolve(source.relativize(localSource));
				if (Files.exists(localDestination) && !overwriteExisting) {
					IOException e = new FileAlreadyExistsException(localDestination.toString(), null, "Run with --overwrite-existing or --delete");
					if (failFast) {
						throw e;
					} else {
						log.error("Could not back up", e);
					}
				} else if (checkTimestamps && Files.getLastModifiedTime(localSource).compareTo(Files.getLastModifiedTime(localDestination)) < 0)
					log.debug("Skipping " + localSource + " based on file time");
				else {
					files.add(localSource);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				log.error("Visiting file " + file + " failed", exc);
				return failFast ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}
}