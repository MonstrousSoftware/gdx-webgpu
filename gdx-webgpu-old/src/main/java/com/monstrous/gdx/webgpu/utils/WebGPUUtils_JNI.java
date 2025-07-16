
package com.monstrous.gdx.webgpu.utils;

import jnr.ffi.Pointer;

/** A representation in Java of the native C interface for wgpuUtils.dll. */
public interface WebGPUUtils_JNI {

	/** Bridge between GLFW and WebGPU: Provides a Surface corresponding to a GLFW window. */
	Pointer glfwGetWGPUSurface (Pointer instance, long HWND);

	/** Image file handling */
	Pointer gdx2d_load (Pointer buffer, int len);

	void gdx2d_free (Pointer pixmapInfo);
}
