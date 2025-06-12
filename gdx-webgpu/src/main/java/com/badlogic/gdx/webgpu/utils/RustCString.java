
package com.badlogic.gdx.webgpu.utils;

import jnr.ffi.Pointer;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Utilities for converting between Rust CString and Java Strings. */
public class RustCString {

	/** returns a Java String made from the pointer using the {@link StandardCharsets#US_ASCII} charset. */
	public static String fromPointer (Pointer pointer) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		for (long i = 0;; i++) {
			byte nextChar = pointer.getByte(i);

			if (nextChar == 0x00) {
				break;
			}

			stream.write(nextChar);
		}

		try {
			return stream.toString("US-ASCII");
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}

	/** returns a pointer to direct memory of the string in {@link StandardCharsets#US_ASCII} */
	public static Pointer toPointer (String string) {
		if (string == null) return JavaWebGPU.createNullPointer();

		byte[] bytes = string.getBytes(StandardCharsets.US_ASCII);
		ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length + 1);

		buffer.put(bytes);
		buffer.put((byte)0x00);
		buffer.position(0);

		return JavaWebGPU.createByteBufferPointer(buffer);
	}
}
