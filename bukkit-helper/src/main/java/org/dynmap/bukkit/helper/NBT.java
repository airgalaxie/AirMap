package org.dynmap.bukkit.helper;

import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.dynmap.common.chunk.GenericBitStorage;
import org.dynmap.common.chunk.GenericNBTCompound;
import org.dynmap.common.chunk.GenericNBTList;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class NBT {

	public static class NBTCompound implements GenericNBTCompound {
		private final CompoundTag obj;
		public NBTCompound(CompoundTag t) {
			this.obj = t;
		}
		@Override
		public Set<String> getAllKeys() {
			return obj.keySet();
		}
		@Override
		public boolean contains(String s) {
			return obj.contains(s);
		}
		@Override
		public boolean contains(String s, int i) {
			Tag tag = obj.get(s);

			if(tag == null) {
				return false;
			}

			int type = tag.getId();

			if(type == i) {
				return true;
			} else if(i != TAG_ANY_NUMERIC) {
				return false;
			} else {
				return type == TAG_BYTE || type == TAG_SHORT || type == TAG_INT || type == TAG_LONG || type == TAG_FLOAT
					|| type == TAG_DOUBLE;
			}
		}
		@Override
		public byte getByte(String s) {
			return obj.getByteOr(s, (byte) 0);
		}
		@Override
		public short getShort(String s) {
			return obj.getShortOr(s, (short) 0);
		}
		@Override
		public int getInt(String s) {
			return obj.getIntOr(s, 0);
		}
		@Override
		public long getLong(String s) {
			return obj.getLongOr(s, 0);
		}
		@Override
		public float getFloat(String s) {
			return obj.getFloatOr(s, 0.0f);
		}
		@Override
		public double getDouble(String s) {
			return obj.getDoubleOr(s, 0.0);
		}
		@Override
		public String getString(String s) {
			return obj.getStringOr(s, "");
		}
		@Override
		public byte[] getByteArray(String s) {
			return obj.getByteArray(s).orElseGet(() -> new byte[0]);
		}
		@Override
		public int[] getIntArray(String s) {
			return obj.getIntArray(s).orElseGet(() -> new int[0]);
		}
		@Override
		public long[] getLongArray(String s) {
			return obj.getLongArray(s).orElseGet(() -> new long[0]);
		}
		@Override
		public GenericNBTCompound getCompound(String s) {
			return new NBTCompound(obj.getCompoundOrEmpty(s));
		}
		@Override
		public GenericNBTList getList(String s, int i) {
			return new NBTList(obj.getListOrEmpty(s));
		}
		@Override
		public boolean getBoolean(String s) {
			return obj.getBooleanOr(s, false);
		}
		@Override
		public String getAsString(String s) {
			Tag tag = obj.get(s);

			return tag != null ? tag.asString().orElse("") : "";
		}
		@Override
		public GenericBitStorage makeBitStorage(int bits, int count, long[] data) {
			return new OurBitStorage(bits, count, data);
		}
		public String toString() {
			return obj.toString();
		}
	}
	public static class NBTList implements GenericNBTList {
		private final ListTag obj;
		public NBTList(ListTag t) {
			obj = t;
		}
		@Override
		public int size() {
			return obj.size();
		}
		@Override
		public String getString(int idx) {
			return obj.getString(idx).orElseGet(() -> obj.getCompound(idx)
					.filter(compound -> compound.size() == 1)
					.map(compound -> compound.get(""))
					.flatMap(Tag::asString)
					.orElse(""));
		}
		@Override
		public GenericNBTCompound getCompound(int idx) {
			CompoundTag compound = obj.getCompound(idx).orElse(null);
			if (compound != null) {
				Tag wrapped = compound.size() == 1 ? compound.get("") : null;
				if (wrapped != null) {
					return scalarBlockState(wrapped.asString().orElse(null));
				}
				return new NBTCompound(compound);
			}
			return scalarBlockState(obj.getString(idx).orElse(null));
		}
		private static GenericNBTCompound scalarBlockState(String blockName) {
			CompoundTag stateTag = new CompoundTag();
			if (blockName == null) {
				return new NBTCompound(stateTag);
			}
			stateTag.putString("Name", blockName);
			Identifier id = Identifier.tryParse(blockName);
			Block block = id != null ? BuiltInRegistries.BLOCK.getValue(id) : null;
			if (block != null) {
				BlockState state = block.defaultBlockState();
				CompoundTag properties = new CompoundTag();
				state.getValues().forEach(value -> properties.putString(value.property().getName(), value.valueName()));
				if (!properties.isEmpty()) {
					stateTag.put("Properties", properties);
				}
			}
			return new NBTCompound(stateTag);
		}
		public String toString() {
			return obj.toString();
		}
	}
	public static class OurBitStorage implements GenericBitStorage {
		private final SimpleBitStorage bs;
		public OurBitStorage(int bits, int count, long[] data) {
			bs = new SimpleBitStorage(serializedBits(bits, count, data.length), count, data);
		}
		private static int serializedBits(int requestedBits, int count, int dataLength) {
			if (count != 64 || dataLength == 0) {
				return requestedBits;
			}
			// Biome containers have 64 entries.  Their on-disk longs pack only whole
			// values, so four longs represent 3-bit values (21 per long), not 4-bit
			// values.  Recover the serialized width before exposing them to Core.
			for (int bits = 1; bits <= 32; bits++) {
				int valuesPerLong = 64 / bits;
				if ((count + valuesPerLong - 1) / valuesPerLong == dataLength) {
					return bits;
				}
			}
			return requestedBits;
		}
		@Override
		public int get(int idx) {
			return bs.get(idx);
		}
	}
}
