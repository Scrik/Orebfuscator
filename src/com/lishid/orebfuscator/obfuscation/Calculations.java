/*
 * Copyright (C) 2011-2014 lishid.  All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation,  version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.lishid.orebfuscator.obfuscation;

import java.util.zip.Deflater;

import org.bukkit.World.Environment;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.lishid.orebfuscator.OrebfuscatorConfig;
import com.lishid.orebfuscator.internal.IPacket51;
import com.lishid.orebfuscator.internal.IPacket56;
import com.lishid.orebfuscator.internal.InternalAccessor;

public class Calculations {

	public static final ThreadLocal<Deflater> localDeflater = new ThreadLocal<Deflater>() {
		@Override
		protected Deflater initialValue() {
			return new Deflater(OrebfuscatorConfig.CompressionLevel);
		}
	};

	public static void Obfuscate(PacketContainer container, Player player) {
		if (container.getType().equals(PacketType.Play.Server.MAP_CHUNK)) {
			IPacket51 packet = InternalAccessor.Instance.newPacket51();
			packet.setPacket(container.getHandle());
			Calculations.Obfuscate(packet, player);
		} else if (container.getType().equals(PacketType.Play.Server.MAP_CHUNK_BULK)) {
			IPacket56 packet = InternalAccessor.Instance.newPacket56();
			packet.setPacket(container.getHandle());
			Calculations.Obfuscate(packet, player);
		}
	}

	private static void Obfuscate(IPacket56 packet, Player player) {
		if (packet.getFieldData(packet.getOutputBuffer()) != null) {
			return;
		}

		ChunkInfo[] infos = getInfo(packet, player);

		for (int chunkNum = 0; chunkNum < infos.length; chunkNum++) {
			// Create an info objects
			ChunkInfo info = infos[chunkNum];
			ComputeChunkInfoAndObfuscate(info, (byte[]) packet.getFieldData(packet.getBuildBuffer()));
		}

		Deflater deflater = localDeflater.get();
		packet.compress(deflater);
	}

	private static void Obfuscate(IPacket51 packet, Player player) {
		ChunkInfo info = getInfo(packet, player);

		if (info.chunkMask == 0 && info.extraMask == 0) {
			return;
		}

		ComputeChunkInfoAndObfuscate(info, packet.getBuffer());

		Deflater deflater = localDeflater.get();
		packet.compress(deflater);
	}

	private static ChunkInfo[] getInfo(IPacket56 packet, Player player) {
		ChunkInfo[] infos = new ChunkInfo[packet.getPacketChunkNumber()];

		int dataStartIndex = 0;

		int[] x = packet.getX();
		int[] z = packet.getZ();

		byte[][] inflatedBuffers = (byte[][]) packet.getFieldData(packet.getInflatedBuffers());

		int[] chunkMask = packet.getChunkMask();
		int[] extraMask = packet.getExtraMask();

		byte[] buildBuffer = (byte[]) packet.getFieldData(packet.getBuildBuffer());

		// Check for spigot and fix accordingly
		if (buildBuffer.length == 0) {
			int finalBufferSize = 0;
			for (int i = 0; i < inflatedBuffers.length; i++) {
				finalBufferSize += inflatedBuffers[i].length;
			}

			buildBuffer = new byte[finalBufferSize];
			int bufferLocation = 0;
			for (int i = 0; i < inflatedBuffers.length; i++) {
				System.arraycopy(inflatedBuffers[i], 0, buildBuffer, bufferLocation, inflatedBuffers[i].length);
				bufferLocation += inflatedBuffers[i].length;
			}

			packet.setFieldData(packet.getBuildBuffer(), buildBuffer);
		}

		for (int chunkNum = 0; chunkNum < packet.getPacketChunkNumber(); chunkNum++) {
			// Create an info objects
			ChunkInfo info = new ChunkInfo();
			infos[chunkNum] = info;
			info.world = player.getWorld();
			info.chunkX = x[chunkNum];
			info.chunkZ = z[chunkNum];
			info.chunkMask = chunkMask[chunkNum];
			info.extraMask = extraMask[chunkNum];
			info.data = buildBuffer;
			info.startIndex = dataStartIndex;

			dataStartIndex += inflatedBuffers[chunkNum].length;
		}

		return infos;
	}

	private static ChunkInfo getInfo(IPacket51 packet, Player player) {
		// Create an info objects
		ChunkInfo info = new ChunkInfo();
		info.world = player.getWorld();
		info.chunkX = packet.getX();
		info.chunkZ = packet.getZ();
		info.chunkMask = packet.getChunkMask();
		info.extraMask = packet.getExtraMask();
		info.data = packet.getBuffer();
		info.startIndex = 0;
		return info;
	}

	private static void ComputeChunkInfoAndObfuscate(ChunkInfo info, byte[] original) {
		// Compute chunk number
        for (int i = 0; i < 16; i++) {
            if ((info.chunkMask & 1 << i) > 0) {
                info.chunkSectionToIndexMap[i] = info.chunkSectionNumber;
                info.chunkSectionNumber++;
            }
            if ((info.extraMask & 1 << i) > 0) {
                info.extraSectionToIndexMap[i] = info.extraSectionNumber;
                info.extraSectionNumber++;
            }
        }

		if (info.startIndex + 4096 * info.chunkSectionNumber > info.data.length) {
			return;
		}

		info.typeBuffer = new byte[info.chunkSectionNumber * 4096];
		info.extraBuffer = new byte[info.extraSectionNumber * 2048];

		// Obfuscate
		if (!OrebfuscatorConfig.isWorldDisabled(info.world.getName()) && OrebfuscatorConfig.Enabled) {
			Obfuscate(info);
		}
	}

	private static void Obfuscate(ChunkInfo info) {
		boolean isNether = info.world.getEnvironment() == Environment.NETHER;

		int engineMode = OrebfuscatorConfig.EngineMode;

		int currentTypeIndex = 0;
		int addExtendedIndex = 10240 * info.chunkSectionNumber;
		int currentExtendedIndex = 0;
		int startX = info.chunkX << 4;
		int startZ = info.chunkZ << 4;
		int blockY = 0;

		// Loop over 16x16x16 chunks in the 16x256x16 column
		for (int i = 0; i < 16; i++) {
			if ((info.chunkMask & 1 << i) != 0) {

				boolean usesExtra = ((info.extraMask & 1 << i) != 0);
				int block1extra = 0;

				for (int y = 0; y < 16; y++) {

					blockY++;

					for (int z = 0; z < 16; z++) {
						for (int x = 0; x < 16; x++) {

							int typeID = info.data[info.startIndex + currentTypeIndex];
							if (typeID < 0) {
								typeID += 256;
							}
							if (usesExtra) {
								byte extra = 0;
								if (currentTypeIndex % 2 == 0) {
									extra = (byte) (info.data[info.startIndex + addExtendedIndex + currentExtendedIndex] & 0x0F);
								} else {
									extra = (byte) (info.data[info.startIndex + addExtendedIndex + currentExtendedIndex] >> 4);
								}
								if (extra < 0) {
									extra += 16;
								}
								typeID += extra * 256;
							}

							// Obfuscate block if needed or copy old
							int newBlockID = typeID;
							if (OrebfuscatorConfig.isObfuscated(typeID, isNether) && !areAjacentBlocksTransparent(info, startX + x, blockY, startZ + z)) {
								if (engineMode == 1) {
									// Engine mode 1, use stone
									newBlockID = (isNether ? 87 : 1);
								} else if (engineMode == 2) {
									// Ending mode 2, get random block
									newBlockID = OrebfuscatorConfig.getRandomBlockID(isNether);
								}
							}
							info.typeBuffer[currentTypeIndex] = (byte) newBlockID;
							if (usesExtra) {
								byte extra = (byte) (newBlockID / 256);
								if (currentTypeIndex % 2 == 0) {
									block1extra = extra;
								} else {
									info.extraBuffer[currentExtendedIndex] = (byte) (extra * 16 + block1extra);
								}
							}

							if (usesExtra) {
								if (currentTypeIndex % 2 == 1) {
									currentExtendedIndex++;
								}
							}
							currentTypeIndex++;
						}
					}
				}
			}
		}

		// Copy obfuscated buffer to data
		System.arraycopy(info.typeBuffer, 0, info.data, info.startIndex, info.typeBuffer.length);
		System.arraycopy(info.extraBuffer, 0, info.data, info.startIndex + addExtendedIndex, info.extraBuffer.length);

		// Clear buffer
		info.typeBuffer = null;
		info.extraBuffer = null;
	}

	private static boolean areAjacentBlocksTransparent(ChunkInfo info, int x, int y, int z) {

		if (isTransparent(info, x + 1, y, z)) {
			return true;
		}
		if (isTransparent(info, x - 1, y, z)) {
			return true;
		}
		if (isTransparent(info, x, y, z + 1)) {
			return true;
		}
		if (isTransparent(info, x, y, z - 1)) {
			return true;
		}
		if (isTransparent(info, x, y + 1, z)) {
			return true;
		}
		if (isTransparent(info, x, y - 1, z)) {
			return true;
		}

		return false;
	}

	@SuppressWarnings("deprecation")
	private static boolean isTransparent(ChunkInfo info, int x, int y, int z) {
		if (y < 0 || y > info.world.getMaxHeight()) {
			return true;
		}

		int id = 1;
		if (CalculationsUtil.isChunkLoaded(info.world, x >> 4, z >> 4)) {
			id = info.world.getBlockTypeIdAt(x, y, z);
		}

		if (OrebfuscatorConfig.isBlockTransparent(id)) {
			return true;
		}

		return false;
	}

}