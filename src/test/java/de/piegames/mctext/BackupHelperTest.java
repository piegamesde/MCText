package de.piegames.mctext;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.BeforeClass;
import org.junit.Test;

public class BackupHelperTest {

	private static Log log = LogFactory.getLog(BackupHelperTest.class);

	/* NBT<->text */
	BackupHelper	helper1	= new BackupHelper(false, true, false, 1, false, false, true, false, false);
	/* NBT<->uncompressed nbt */
	BackupHelper	helper2	= new BackupHelper(false, true, false, 1, true, false, true, false, false);

	@BeforeClass
	public static void setLogger() {
		System.err.println("Setting logger");
		BackupHelper.log.error("TEST");
		Configurator.setRootLevel(Level.DEBUG);
		log.fatal("TEST");
	}

	@Test
	public void testHelper1a() throws IOException {
		BackupHelper.log.error("TEST5");
		log.error("TEST§");
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
			System.out.println(p + " " + q);
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
