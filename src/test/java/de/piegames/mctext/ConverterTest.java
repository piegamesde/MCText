package de.piegames.mctext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;

public class ConverterTest {

	public ConverterTest() {
	}

	Converter converter1 = new Converter(false, true);
	Converter converter2 = new Converter(false, false);

	@Test
	public void testNBT1() throws Exception {
		File dat = new File(getClass().getResource("/testworld/level.dat").toURI());
		File tmp = File.createTempFile("level", ".dat");
		NBTOutputStream out = new NBTOutputStream(new FileOutputStream(tmp));
		NBTInputStream in = new NBTInputStream(new FileInputStream(dat));
		out.writeTag(converter1.gson.fromJson(converter1.gson.toJson(in.readTag()), CompoundTag.class));
		out.close();
		in.close();
	}

	@Test
	public void testNBT2() throws Exception {
		File dat = new File(getClass().getResource("/testworld/mcedit_waypoints.dat").toURI());
		File tmp = File.createTempFile("mcedit_waypoints", ".dat");
		NBTOutputStream out = new NBTOutputStream(new FileOutputStream(tmp));
		NBTInputStream in = new NBTInputStream(new FileInputStream(dat));
		out.writeTag(converter1.gson.fromJson(converter1.gson.toJson(in.readTag()), CompoundTag.class));
		out.close();
		in.close();
	}

	@Test
	public void testRegionSerialization1a() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r0.mca").toURI()), true);
	}

	@Test
	public void testRegionSerialization1b() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r0.mca").toURI()), false);
	}

	@Test
	public void testRegionSerialization2a() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r1.mca").toURI()), true);
	}

	@Test
	public void testRegionSerialization2b() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r1.mca").toURI()), false);
	}

	@Test
	public void testRegionSerialization3a() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/r2.mca").toURI()), true);
	}

	@Test
	public void testRegionSerialization3b() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r2.mca").toURI()), false);
	}

	@Test
	public void testRegionSerialization4a() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r3.mca").toURI()), true);
	}

	@Test
	public void testRegionSerialization4b() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r3.mca").toURI()), false);
	}

	@Test
	public void testRegionSerialization5a() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r4.mca").toURI()), true);
	}

	@Test
	public void testRegionSerialization5b() throws Exception {
		testRegionSerialization(Paths.get(getClass().getResource("/testworld/r4.mca").toURI()), false);
	}

	private void testRegionSerialization(Path expected, boolean keepUnused) throws Exception {
		Converter converter = (keepUnused ? converter1 : converter2);
		RegionFile file = new RegionFile(expected);
		if (!keepUnused)
			file.clearUnusedData();
		Path tmp1 = Files.createTempFile("tmp", ".mca");
		file.write(tmp1);
		if (keepUnused)
			assertRegionFileEquals(expected, tmp1);

		Path tmp2 = Files.createTempFile("tmp", ".mca");
		{ // Check Anvil <--> Json
			RegionFile file2 = converter.gson.fromJson(converter.gson.toJson(file), RegionFile.class);
			file2.write(tmp2);

			assertRegionFileEquals(tmp1, tmp2);
		}
		file.rewind();
		{ // Check Anvil <--> NBT
			RegionFile file2 = converter.readNBT(converter.writeNBT(file));
			file2.write(tmp2);

			assertRegionFileEquals(tmp1, tmp2);
		}
		file.rewind();
		{ // Check Anvil <--> NBT <--> JSON
			CompoundTag tag1 = converter.writeNBT(file);
			CompoundTag tag2 = converter.gson.fromJson(converter.gson.toJson(tag1), CompoundTag.class);
			assertEquals(tag1, tag2);
			RegionFile file2 = converter.readNBT(tag2);
			file2.write(tmp2);

			assertRegionFileEquals(tmp1, tmp2);
		}
	}

	public static void assertRegionFileEquals(Path expected, Path actual) throws Exception {
		try (InputStream is1 = Files.newInputStream(expected); InputStream is2 = Files.newInputStream(actual);) {
			assertRegionFileEquals(is1, is2);
		}
	}

	public static void assertRegionFileEquals(InputStream expected, InputStream actual) throws Exception {
		byte[] buffer1 = new byte[4096], buffer2 = new byte[4096];
		List<String> lines1 = new ArrayList<>();
		List<String> lines2 = new ArrayList<>();
		while (true) {
			int len1 = expected.read(buffer1);
			int len2 = actual.read(buffer2);
			lines1.add(bytesToHex(buffer1));
			lines2.add(bytesToHex(buffer2));
			if (len1 == -1 && len2 == -1)
				break;
		}
		assertLinesMatch(lines1, lines2);
	}

	private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

	public static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int j = 0; j < 64; j++) {
			int v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
}
