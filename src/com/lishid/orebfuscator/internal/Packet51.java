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

package com.lishid.orebfuscator.internal;

import java.util.Arrays;
import java.util.zip.Deflater;

import net.minecraft.server.v1_6_R3.Packet;
import net.minecraft.server.v1_6_R3.Packet51MapChunk;

import com.lishid.orebfuscator.OrebfuscatorConfig;
import com.lishid.orebfuscator.utils.ReflectionHelper;

public class Packet51 {
	private Packet51MapChunk packet;

	private byte[] inflatedBuffer;

	private byte[] buildBuffer;

	public Packet51(Packet packet) {
		this.packet = (Packet51MapChunk) packet;
		inflatedBuffer = (byte[]) ReflectionHelper.getPrivateField(packet, Fields.Packet51Fields.getInflatedBufferFieldName());
		buildBuffer = Arrays.copyOf(inflatedBuffer, inflatedBuffer.length);
	}

	public int getX() {
		return packet.a;
	}

	public int getZ() {
		return packet.b;
	}

	public int getChunkMask() {
		return packet.c;
	}

	public int getExtraMask() {
		return packet.d;
	}

	public byte[] getInflatedBuffer() {
		return inflatedBuffer;
	}

	public byte[] getBuildBuffer() {
		return buildBuffer;
	}

	public void compress() {
		Deflater deflater = new Deflater(OrebfuscatorConfig.CompressionLevel);
		deflater.setInput(buildBuffer);
		deflater.finish();

		byte[] outputBuffer = (byte[]) ReflectionHelper.getPrivateField(packet, Fields.Packet51Fields.getOutputBufferFieldName());
		ReflectionHelper.setPrivateField(packet, Fields.Packet51Fields.getCompressedSizeFieldName(), deflater.deflate(outputBuffer));
	}

}
