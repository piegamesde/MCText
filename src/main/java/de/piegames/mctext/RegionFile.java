package de.piegames.mctext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.flowpowered.nbt.ByteArrayTag;
import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.Tag;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;

public class RegionFile {

	protected Path file;
	ByteBuffer locations, timestamps;
	IntBuffer locations2, timestamps2;
	ByteBuffer[] chunks = new ByteBuffer[1024];
	Map<Integer, ByteBuffer> unused;

	public RegionFile(Path file) throws IOException {
		this.file = file;
		unused = new HashMap<>();

		FileChannel raf = FileChannel.open(file, StandardOpenOption.READ);

		locations = ByteBuffer.allocate(4096);
		raf.read(locations);
		locations.flip();
		locations2 = locations.asIntBuffer();

		timestamps = ByteBuffer.allocate(4096);
		raf.read(timestamps);
		timestamps.flip();
		timestamps2 = timestamps.asIntBuffer();

		Set<Integer> unused = IntStream.range(2, (int) Math.ceil(raf.size() / 4096d)).mapToObj(i -> i).collect(Collectors.toSet());

		for (int i = 0; i < 1024; i++) {
			int chunkPos = locations2.get(i) >>> 8;
			int chunkLength = locations2.get(i) & 0xFF;
			if (chunkPos > 0) {
				chunks[i] = ByteBuffer.allocate(chunkLength << 12);
				for (int j = 0; j < chunkLength; j++)
					unused.remove(chunkPos + j);
				raf.read(chunks[i], chunkPos << 12);
				chunks[i].flip();
			}
		}
		for (int i : unused) {
			ByteBuffer buffer = ByteBuffer.allocate(4096);
			raf.read(buffer, i << 12);
			buffer.flip();
			this.unused.put(i, buffer);
		}
		raf.close();
	}

	public RegionFile(ByteBuffer locations, ByteBuffer timestamps, ByteBuffer[] chunks,
			Map<Integer, ByteBuffer> unused) {
		this.locations = Objects.requireNonNull(locations);
		this.timestamps = Objects.requireNonNull(timestamps);
		locations2 = locations.asIntBuffer();
		timestamps2 = timestamps.asIntBuffer();
		this.chunks = Objects.requireNonNull(chunks);
		this.unused = unused;
	}

	public RegionFile(CompoundTag in, Options options) throws IOException {
		// ByteBuffer locations, timestamps;
		// IntBuffer locations2, timestamps2;
		// ByteBuffer[]
		chunks = new ByteBuffer[1024];

		locations = ByteBuffer.allocate(4096);
		timestamps = ByteBuffer.allocate(4096);
		locations2 = locations.asIntBuffer();
		timestamps2 = timestamps.asIntBuffer();

		// Map<Integer, ByteBuffer>
		unused = new HashMap<>();

		CompoundMap map = in.getValue();

		for (Entry<String, Tag<?>> entry : map.entrySet()) {
			String name = entry.getKey();
			int chunkPos = Integer.parseInt(name);
			if (entry.getValue() instanceof CompoundTag) { // Actual chunk data
				CompoundTag chunk = (CompoundTag) entry.getValue();
				CompoundMap chunkMap = chunk.getValue();

				int i = (int) chunkMap.get("index").getValue();
				int timestamp = (int) chunkMap.get("timestamp").getValue();
				byte compression = (byte) chunkMap.get("compression").getValue();

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				NBTOutputStream s = new NBTOutputStream(new BufferedOutputStream(baos = new ByteArrayOutputStream()), compression);
				s.writeTag(new CompoundTag("", ((CompoundTag) chunkMap.get("chunk")).getValue()));
				s.flush();
				s.close();

				byte[] chunkData = baos.toByteArray();
				int chunkLength = (int) Math.ceil((chunkData.length + 6) / 4096d);
				chunks[i] = ByteBuffer.allocate(chunkLength << 12);
				chunks[i].putInt(chunkData.length + 1);
				chunks[i].put(compression);
				chunks[i].put(chunkData);

				if (options.keepUnusedData && chunkMap.containsKey("unused"))
					chunks[i].put((byte[]) chunkMap.get("unused").getValue());

				chunks[i].flip();

				locations2.put(i, chunkPos << 8 | (chunkLength & 0xFF));
				timestamps2.put(i, timestamp);

			} else if (options.keepUnusedData) { // Unused data
				ByteBuffer value = ByteBuffer.wrap((byte[]) entry.getValue().getValue());
				unused.put(chunkPos, value);
			}
		}
	}

	public CompoundTag writeNBT(Options options) throws IOException {
		CompoundMap map = new CompoundMap();
		CompoundTag ret = new CompoundTag("", map);

		for (int i = 0; i < 1024; i++) {
			int chunkPos = locations2.get(i) >>> 8;
			int chunkLength = locations2.get(i) & 0xFF;
			int timestamp = timestamps2.get(i);

			if (chunks[i] != null) {
				CompoundMap chunkMap = new CompoundMap();
				CompoundTag chunk = new CompoundTag(Integer.toString(chunkPos), chunkMap);
				map.put(Integer.toString(chunkPos), chunk);

				chunkMap.put("index", new IntTag("index", i));
				chunkMap.put("timestamp", new IntTag("timestamp", timestamp));

				ByteBuffer data = chunks[i];
				int realChunkLength = data.getInt() - 1;
				byte compression = data.get();
				chunkMap.put("compression", new ByteTag("compression", compression));

				NBTInputStream nbtIn = new NBTInputStream(new ByteArrayInputStream(data.array(), 5, realChunkLength), compression);
				chunkMap.put("chunk", new CompoundTag("chunk", ((CompoundTag) nbtIn.readTag()).getValue()));
				// chunkMap.put("chunk", nbtIn.readTag());

				nbtIn.close();

				if (options.keepUnusedData) {
					byte[] unusedData = new byte[(chunkLength << 12) - realChunkLength - 5];
					System.arraycopy(data.array(), realChunkLength + 5, unusedData, 0, unusedData.length);
					chunkMap.put("unused", new ByteArrayTag("unused", unusedData));
				}
			}
		}

		if (unused != null && options.keepUnusedData)
			for (Entry<Integer, ByteBuffer> e : unused.entrySet()) {
				map.put(e.getKey().toString(), new ByteArrayTag(e.getKey().toString(), e.getValue().array()));
			}

		return ret;
	}

	public void write(Path file) throws IOException {
		FileChannel raf = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);
		rewind();
		raf.position(0);
		raf.write(locations);
		raf.write(timestamps);
		for (int i = 0; i < 1024; i++) {
			if (chunks[i] != null) {
				int chunkPos = locations2.get(i) >> 8;
				raf.position(chunkPos << 12);
				raf.write(chunks[i]);
			}
		}
		if (unused != null)
			for (Entry<Integer, ByteBuffer> e : unused.entrySet()) {
				raf.position(e.getKey() << 12);
				raf.write(e.getValue());
			}
		raf.close();
		rewind();
	}

	public void rewind() {
		locations.rewind();
		locations2.rewind();
		timestamps.rewind();
		timestamps2.rewind();
		for (ByteBuffer b : chunks)
			if (b != null)
				b.rewind();
		if (unused != null)
			unused.values().forEach(ByteBuffer::rewind);
	}

	public Path getFile() {
		return file;
	}

	/**
	 * This method will take all the unused data out of this file by overwriting it
	 * with zeroes. This is completely useless except for testing.
	 */
	public void clearUnusedData() {
		// maxPos is used to remove trailing unused data at the end of the file. We
		// don't need to write it
		int maxPos = 2;
		for (int i = 0; i < 1024; i++)
			if (chunks[i] != null) {
				int length = chunks[i].getInt(0) - 1;
				chunks[i].limit(length + 5);
				maxPos = Math.max(maxPos, locations2.get(i) >> 8);
			} else {
				locations2.put(i, 0);
				timestamps2.put(i, 0);
			}
		ByteBuffer empty = ByteBuffer.allocate(4096);
		unused.values().forEach(ByteBuffer::clear);
		int maxPos2 = maxPos;
		unused.keySet().removeIf(key -> key >= maxPos2);
		for (int i : unused.keySet()) {
			unused.put(i, empty.duplicate());
		}
	}
}