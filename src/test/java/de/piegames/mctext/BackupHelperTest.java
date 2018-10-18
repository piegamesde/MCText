package de.piegames.mctext;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BackupHelperTest {

	/* NBT<->text */
	BackupHelper		helper1	= new BackupHelper(false, true, false, 1, false, false, true, false, false);
	/* NBT<->uncompressed nbt */
	BackupHelper		helper2	= new BackupHelper(false, true, false, 1, true, false, true, false, false);

	@BeforeAll
	public static void setLogger() {
		Configurator.setRootLevel(Level.DEBUG);
	}

	@Test
	public void testHelper1a() throws IOException {
		Path world = Paths.get(URI.create(getClass().getResource("/testworld/").toString()));
		Path backup = Files.createTempDirectory("mctext");
		Path restore = Files.createTempDirectory("mctext");
		helper1.backupWorld(world, backup);
		helper1.restoreWorld(backup, restore);
		foldersEqual(world, restore);
	}

	@Test
	public void testHelper1b() throws IOException {
		Path world = Paths.get(URI.create(getClass().getResource("/testworld/").toString()));
		Path backup = Files.createTempDirectory("mctext");
		Path restore = Files.createTempDirectory("mctext");
		helper2.backupWorld(world, backup);
		helper2.restoreWorld(backup, restore);
		foldersEqual(world, restore);
	}

	public static void foldersEqual(Path a, Path b) throws IOException {
		Files.walk(a).forEach(p -> {
			Path q = b.resolve(a.relativize(p));
			assertEquals(Files.isDirectory(p), Files.isDirectory(q));
			if (!Files.isDirectory(p))
				try {
					assertArrayEquals(
							Files.readAllBytes(p),
							Files.readAllBytes(q));
				} catch (IOException e) {
					throw new Error(e);
				}
		});
		Files.walk(b).forEach(p -> assertTrue(Files.exists(a.resolve(p.relativize(b)))));
	}
}
