package de.piegames.mctext;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.flowpowered.nbt.*;
import com.flowpowered.nbt.stream.NBTInputStream;
import com.flowpowered.nbt.stream.NBTOutputStream;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class Converter {

	public final boolean prettyPrinting;
	public final boolean keepUnusedData;

	public final Gson gson;

	public Converter() {
		this(false, false);
	}

	public Converter(boolean prettyPrinting, boolean keepUnusedData) {
		this.prettyPrinting = prettyPrinting;
		this.keepUnusedData = keepUnusedData;

		GsonBuilder builder = new GsonBuilder();
		builder.setLenient();
		builder.disableHtmlEscaping();
		if (prettyPrinting)
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

				if (keepUnusedData && chunkMap.containsKey("unused"))
					chunks[i].put((byte[]) chunkMap.get("unused").getValue());

				chunks[i].flip();

				locations2.put(i, chunkPos << 8 | (chunkLength & 0xFF));
				timestamps2.put(i, timestamp);

			} else if (keepUnusedData) { // Unused data
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

				nbtIn.close();

				if (keepUnusedData) {
					byte[] unusedData = new byte[(chunkLength << 12) - realChunkLength - 5];
					System.arraycopy(data.array(), realChunkLength + 5, unusedData, 0, unusedData.length);
					chunkMap.put("unused", new ByteArrayTag("unused", unusedData));
				}
			}
		}

		if (file.unused != null && keepUnusedData)
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
			if (writeName)
				out.name(encode(name, TagType.TAG_LIST));
			out.beginArray();
			out.value(encode("", TagType.getByTagClass(list.getElementType())));
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
			return new ShortArrayTag(name, array);
		}
		case TAG_LONG_ARRAY: {
			LongBuffer longBuffer = ByteBuffer.wrap(Base64.getDecoder().decode(in.nextString())).asLongBuffer();
			long[] array = new long[longBuffer.remaining()];
			longBuffer.get(array);
			return new LongArrayTag(name, array);
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
			List<Tag> tags = new LinkedList<Tag>();
			in.beginArray();
			TagType listType = decode(in.nextString());
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
}