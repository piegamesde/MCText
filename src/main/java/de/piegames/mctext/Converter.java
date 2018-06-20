package de.piegames.mctext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.zip.ZipException;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.flowpowered.nbt.ByteArrayTag;
import com.flowpowered.nbt.ByteTag;
import com.flowpowered.nbt.CompoundMap;
import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.DoubleTag;
import com.flowpowered.nbt.EndTag;
import com.flowpowered.nbt.FloatTag;
import com.flowpowered.nbt.IntArrayTag;
import com.flowpowered.nbt.IntTag;
import com.flowpowered.nbt.ListTag;
import com.flowpowered.nbt.LongArrayTag;
import com.flowpowered.nbt.LongTag;
import com.flowpowered.nbt.ShortArrayTag;
import com.flowpowered.nbt.ShortTag;
import com.flowpowered.nbt.StringTag;
import com.flowpowered.nbt.Tag;
import com.flowpowered.nbt.TagType;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class Converter {

	public static Logger log = LogManager.getLogger(Converter.class);

	public static final PathMatcher nbt = FileSystems.getDefault().getPathMatcher("glob:**.{dat,dat_old,dat_new,nbt}");
	public static final PathMatcher anvil = FileSystems.getDefault().getPathMatcher("glob:**.{mca,mcr}");

	protected Options options;

	public final Gson gson;

	public Converter() {
		this(new Options());
	}

	public Converter(Options options) {
		this.options = Objects.requireNonNull(options);

		GsonBuilder builder = new GsonBuilder();
		builder.setLenient();
		if (options.prettyPrinting)
			builder.setPrettyPrinting();
		builder.registerTypeAdapter(CompoundTag.class, TAG_ADAPTER);
		builder.registerTypeAdapter(RegionFile.class, REGION_ADAPTER);
		gson = builder.create();
	}

	public final TypeAdapter<CompoundTag> TAG_ADAPTER = new TypeAdapter<CompoundTag>() {

		@Override
		public void write(JsonWriter out, CompoundTag value) throws IOException {
			Converter.this.write(out, value, "", false);
		}

		@Override
		public CompoundTag read(JsonReader in) throws IOException {
			return (CompoundTag) Converter.this.read(in, "", TagType.TAG_COMPOUND);
		}
	};

	public final TypeAdapter<RegionFile> REGION_ADAPTER = new TypeAdapter<RegionFile>() {

		@Override
		public void write(JsonWriter out, RegionFile value) throws IOException {
			TAG_ADAPTER.write(out, writeNBT(value));
		}

		@Override
		public RegionFile read(JsonReader in) throws IOException {
			return readNBT(TAG_ADAPTER.read(in));
		}
	};

	public RegionFile readNBT(CompoundTag in) throws IOException {
		ByteBuffer locations, timestamps;
		IntBuffer locations2, timestamps2;
		ByteBuffer[] chunks = new ByteBuffer[1024];

		locations = ByteBuffer.allocate(4096);
		timestamps = ByteBuffer.allocate(4096);
		locations2 = locations.asIntBuffer();
		timestamps2 = timestamps.asIntBuffer();

		Map<Integer, ByteBuffer> unused = new HashMap<>();

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
		return new RegionFile(locations, timestamps, chunks, unused);
	}

	public CompoundTag writeNBT(RegionFile file) throws IOException {
		CompoundMap map = new CompoundMap();
		CompoundTag ret = new CompoundTag("", map);

		for (int i = 0; i < 1024; i++) {
			int chunkPos = file.locations2.get(i) >>> 8;
			int chunkLength = file.locations2.get(i) & 0xFF;
			int timestamp = file.timestamps2.get(i);

			if (file.chunks[i] != null) {
				CompoundMap chunkMap = new CompoundMap();
				CompoundTag chunk = new CompoundTag(Integer.toString(chunkPos), chunkMap);
				map.put(Integer.toString(chunkPos), chunk);

				chunkMap.put("index", new IntTag("index", i));
				chunkMap.put("timestamp", new IntTag("timestamp", timestamp));

				ByteBuffer data = file.chunks[i];
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

		if (file.unused != null && options.keepUnusedData)
			for (Entry<Integer, ByteBuffer> e : file.unused.entrySet()) {
				map.put(e.getKey().toString(), new ByteArrayTag(e.getKey().toString(), e.getValue().array()));
			}

		return ret;
	}

	void write(JsonWriter out, Tag<?> nbt, String name, boolean writeName) throws IOException {
		if (nbt.getType() != TagType.TAG_LIST && name != null && writeName)
			out.name(encode(name, nbt.getType()));

		switch (nbt.getType()) {
		case TAG_BYTE:
			out.value((byte) nbt.getValue());
			break;
		case TAG_DOUBLE:
			out.value((double) nbt.getValue());
			break;
		case TAG_FLOAT:
			out.value((float) nbt.getValue());
			break;
		case TAG_INT:
			out.value((int) nbt.getValue());
			break;
		case TAG_LONG:
			out.value((long) nbt.getValue());
			break;
		case TAG_SHORT:
			out.value((short) nbt.getValue());
			break;
		case TAG_STRING:
			out.value((String) nbt.getValue());
			break;
		case TAG_BYTE_ARRAY:
			out.value(Base64.getEncoder().encodeToString((byte[]) nbt.getValue()));
			break;
		case TAG_INT_ARRAY: {
			int[] data = (int[]) nbt.getValue();
			ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 4);
			IntBuffer intBuffer = byteBuffer.asIntBuffer();
			intBuffer.put(data);
			out.value(Base64.getEncoder().encodeToString(byteBuffer.array()));
			break;
		}
		case TAG_SHORT_ARRAY: {
			short[] data = (short[]) nbt.getValue();
			ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 2);
			ShortBuffer shortBuffer = byteBuffer.asShortBuffer();
			shortBuffer.put(data);
			out.value(Base64.getEncoder().encodeToString(byteBuffer.array()));
			break;
		}
		case TAG_LONG_ARRAY: {
			long[] data = (long[]) nbt.getValue();
			ByteBuffer byteBuffer = ByteBuffer.allocate(data.length * 8);
			LongBuffer longBuffer = byteBuffer.asLongBuffer();
			longBuffer.put(data);
			out.value(Base64.getEncoder().encodeToString(byteBuffer.array()));
			break;
		}
		case TAG_COMPOUND: {
			CompoundMap map = ((CompoundTag) nbt).getValue();
			out.beginObject();
			for (Entry<String, Tag<?>> tags : map.entrySet()) {
				String key = tags.getKey();
				Tag<?> value = tags.getValue();
				write(out, value, key, true);
			}
			out.endObject();
			break;
		}
		case TAG_LIST: {
			@SuppressWarnings("unchecked")
			ListTag<Tag<?>> list = (ListTag<Tag<?>>) nbt;
			out.name(encode(encode(name, TagType.getByTagClass(list.getElementType())), TagType.TAG_LIST));
			out.beginArray();
			for (Tag<?> tag : list.getValue())
				write(out, tag, "", false);
			out.endArray();
			break;
		}
		case TAG_END:
			break;
		default:
			throw new Error();
		}
	}

	/**
	 * @param name
	 *            the real name of the key. If type==LIST, the first two chars of
	 *            the name indicate the type of the list
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	Tag<?> read(JsonReader in, String name, TagType type) throws IOException {
		switch (type) {
		case TAG_BYTE:
			return new ByteTag(name, (byte) in.nextInt());
		case TAG_DOUBLE:
			return new DoubleTag(name, in.nextDouble());
		case TAG_FLOAT:
			return new FloatTag(name, (float) in.nextDouble());
		case TAG_INT:
			return new IntTag(name, in.nextInt());
		case TAG_LONG:
			return new LongTag(name, in.nextLong());
		case TAG_SHORT:
			return new ShortTag(name, (short) in.nextInt());
		case TAG_STRING:
			return new StringTag(name, in.nextString());
		case TAG_BYTE_ARRAY:
			return new ByteArrayTag(name, Base64.getDecoder().decode(in.nextString()));
		case TAG_INT_ARRAY: {
			IntBuffer intBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(in.nextString())).asIntBuffer();
			int[] array = new int[intBuffer.remaining()];
			intBuffer.get(array);
			return new IntArrayTag(name, array);
		}
		case TAG_SHORT_ARRAY: {
			ShortBuffer shortBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(in.nextString())).asShortBuffer();
			short[] array = new short[shortBuffer.remaining()];
			shortBuffer.get(array);
			return new ShortArrayTag(name, shortBuffer.array());
		}
		case TAG_LONG_ARRAY: {
			LongBuffer longBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(in.nextString())).asLongBuffer();
			long[] array = new long[longBuffer.remaining()];
			longBuffer.get(array);
			return new LongArrayTag(name, longBuffer.array());
		}
		case TAG_COMPOUND: {
			CompoundMap map = new CompoundMap();
			CompoundTag compound = new CompoundTag(name, map);
			in.beginObject();
			while (in.peek() != JsonToken.END_OBJECT) {
				String key = in.nextName();
				TagType t = decode(key);
				if (t == TagType.TAG_END)
					break;
				key = key.substring(2);
				map.put(read(in, key, t));
			}
			in.endObject();
			return compound;
		}
		case TAG_LIST: {
			TagType listType = decode(name);
			name = name.substring(2);
			List<Tag> tags = new LinkedList<Tag>();
			in.beginArray();
			while (in.peek() != JsonToken.END_ARRAY)
				tags.add(read(in, "", listType));
			in.endArray();
			return new ListTag<Tag>(name, (Class<Tag>) listType.getTagClass(), tags);
		}
		case TAG_END:
			return new EndTag();
		default:
			throw new Error();
		}
	}

	static TagType decode(String key) {
		return TagType.getById(Integer.parseInt(key.substring(0, 2), 16));
	}

	static String encode(String key, TagType type) {
		return String.format("%02x", type.getId()) + key;
	}

	public void backupFile(Path source, Path destination) throws IOException {
		if (nbt.matches(source))
			backupNBT(source, destination);
		else if (anvil.matches(source)) {
			backupAnvil(source, destination);
		} else {
			log.debug(source + " does not seem to be an nbt file and will be copied");
			if (!options.dryRun) {
				if (options.overwriteExisting)
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				else
					Files.copy(source, destination);
			}
		}
	}

	public void backupNBT(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as nbt file");
		if (Files.exists(destination) && !options.overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (options.dryRun)
			return;
		try (NBTInputStream s = new NBTInputStream(Files.newInputStream(source));
				Writer writer = Files.newBufferedWriter(destination)) {
			writer.write(gson.toJson(s.readTag()));
		} catch (ZipException e) {
			log.warn("Could not unzip file, trying uncompressed nbt");
			try (NBTInputStream s = new NBTInputStream(Files.newInputStream(source), NBTInputStream.NO_COMPRESSION);
					Writer writer = Files.newBufferedWriter(destination)) {
				writer.write(gson.toJson(s.readTag()));
			}
		}
	}

	public void backupAnvil(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as anvil file");
		if (Files.exists(destination) && !options.overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (options.dryRun)
			return;
		try (Writer writer = Files.newBufferedWriter(destination)) {
			writer.write(gson.toJson(new RegionFile(source)));
		}
	}

	public void backupWorld(Path source, Path destination) throws IOException {
		for (Path file : walkTree(source, destination))
			try {
				backupFile(file, destination.resolve(source.relativize(file)));
			} catch (IOException | RuntimeException e) {
				if (options.failFast)
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
			if (!options.dryRun) {
				if (options.overwriteExisting)
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
				else
					Files.copy(source, destination);
			}
		}
	}

	public void restoreNBT(Path source, Path destination) throws IOException {
		log.debug("Restoring " + source + " as nbt file");
		if (Files.exists(destination) && !options.overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (options.dryRun)
			return;
		try (NBTOutputStream s = new NBTOutputStream(Files.newOutputStream(destination))) {
			s.writeTag(gson.fromJson(new String(Files.readAllBytes(source)), CompoundTag.class));
		}
	}

	public void restoreAnvil(Path source, Path destination) throws IOException {
		log.debug("Backing up " + source + " as anvil file");
		if (Files.exists(destination) && !options.overwriteExisting)
			throw new FileAlreadyExistsException(destination.toString(), null, "Run with --overwrite-existing or --delete");
		if (options.dryRun)
			return;
		gson.fromJson(new String(Files.readAllBytes(source)), RegionFile.class).write(destination);
	}

	public void restoreWorld(Path source, Path destination) throws IOException {
		for (Path file : walkTree(source, destination))
			try {
				restoreFile(file, destination.resolve(source.relativize(file)));
			} catch (IOException | RuntimeException e) {
				if (options.failFast)
					throw e;
				else
					log.error("Could not restore file " + file, e);
			}
	}

	Queue<Path> walkTree(Path source, Path destination) throws IOException {
		if (options.delete)
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
				if (Files.exists(localDestination) && !options.overwriteExisting) {
					IOException e = new FileAlreadyExistsException(localDestination.toString(), null, "Run with --overwrite-existing or --delete");
					if (options.failFast) {
						throw e;
					} else {
						log.error("Could not back up", e);
					}
				} else if (options.checkTimestamps && Files.getLastModifiedTime(localSource).compareTo(Files.getLastModifiedTime(localDestination)) < 0)
					log.debug("Skipping " + localSource + " based on file time");
				else {
					files.add(localSource);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
				log.error("Visiting file " + file + " failed", exc);
				return options.failFast ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}
}