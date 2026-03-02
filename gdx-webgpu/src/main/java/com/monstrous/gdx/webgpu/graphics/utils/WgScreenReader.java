package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferMapCallback;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUCommandBuffer;
import com.github.xpenatan.webgpu.WGPUCommandBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUCommandEncoder;
import com.github.xpenatan.webgpu.WGPUCommandEncoderDescriptor;
import com.github.xpenatan.webgpu.WGPUExtent3D;
import com.github.xpenatan.webgpu.WGPUMapAsyncStatus;
import com.github.xpenatan.webgpu.WGPUMapMode;
import com.github.xpenatan.webgpu.WGPUTexelCopyBufferInfo;
import com.github.xpenatan.webgpu.WGPUTexelCopyBufferLayout;
import com.github.xpenatan.webgpu.WGPUTexelCopyTextureInfo;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureAspect;
import com.github.xpenatan.webgpu.WGPUOrigin3D;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import java.nio.ByteBuffer;

/**
 * Helper class to read pixels from WebGPU textures.
 *
 * IMPORTANT: Pixel reading only works from framebuffer textures (offscreen rendering). You CANNOT read from the surface
 * texture directly. Always use WgFrameBuffer and read from the framebuffer's color texture.
 *
 * Usage: 1. Render your scene to a WgFrameBuffer 2. Call readPixelAsync() or readPixelsAsync() with the framebuffer
 */
public class WgScreenReader implements Disposable {

    private final WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();

    public WgScreenReader() {
    }

    public void readPixelAsync(WgFrameBuffer framebuffer, int x, int y, PixelReadCallback callback) {
        WGPUTexture texture = ((WgTexture) framebuffer.getColorBufferTexture()).getHandle();

        // WebGPU requires bytesPerRow to be a multiple of 256
        int bytesPerRow = 256; // For 1 pixel, minimum multiple of 256

        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.obtain();
        bufferDesc.setSize(bytesPerRow);
        bufferDesc.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead));
        WGPUBuffer stagingBuffer = webgpu.device.createBuffer(bufferDesc);

        // Command encoder
        WGPUCommandEncoder encoder = WGPUCommandEncoder.obtain();
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        webgpu.device.createCommandEncoder(encoderDesc, encoder);

        // Copy texture to buffer
        WGPUTexelCopyBufferInfo bufferInfo = WGPUTexelCopyBufferInfo.obtain();
        bufferInfo.setBuffer(stagingBuffer);
        WGPUTexelCopyBufferLayout layout = bufferInfo.getLayout();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(1);

        WGPUTexelCopyTextureInfo textureInfo = WGPUTexelCopyTextureInfo.obtain();
        textureInfo.setTexture(texture);
        textureInfo.setAspect(WGPUTextureAspect.All);
        WGPUOrigin3D origin = textureInfo.getOrigin();
        origin.setX(x);
        origin.setY(y);
        origin.setZ(0);

        WGPUExtent3D copySize = WGPUExtent3D.obtain();
        copySize.setWidth(1);
        copySize.setHeight(1);
        copySize.setDepthOrArrayLayers(1);

        encoder.copyTextureToBuffer(textureInfo, bufferInfo, copySize);

        // Submit
        WGPUCommandBuffer commandBuffer = WGPUCommandBuffer.obtain();
        WGPUCommandBufferDescriptor cmdDesc = WGPUCommandBufferDescriptor.obtain();
        encoder.finish(cmdDesc, commandBuffer);
        webgpu.queue.submit(commandBuffer);

        // Map buffer async
        stagingBuffer.mapAsync(WGPUMapMode.Read, 0, bytesPerRow, WGPUCallbackMode.AllowSpontaneous,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        if (status == WGPUMapAsyncStatus.Success) {
                            ByteBuffer data = BufferUtils.newUnsafeByteBuffer(bytesPerRow);
                            stagingBuffer.getConstMappedRange(0, bytesPerRow, data);
                            int r = data.get() & 0xFF;
                            int g = data.get() & 0xFF;
                            int b = data.get() & 0xFF;
                            int a = data.get() & 0xFF;
                            BufferUtils.disposeUnsafeByteBuffer(data);
                            callback.onPixelRead(r, g, b, a);
                        } else {
                            callback.onPixelRead(0, 0, 0, 255); // error, return black
                        }
                        stagingBuffer.unmap();
                        stagingBuffer.release();
                    }
                });
    }

    /**
     * Read entire texture as raw pixels (ASYNC). The ByteBuffer passed to the callback is temporary and will be
     * disposed after the callback returns.
     */
    public void readPixelsAsync(WgFrameBuffer framebuffer, PixelsReadCallback callback) {
        WGPUTexture texture = ((WgTexture) framebuffer.getColorBufferTexture()).getHandle();
        int width = framebuffer.getWidth();
        int height = framebuffer.getHeight();

        // WebGPU requires bytesPerRow to be a multiple of 256
        int bytesPerRow = ((width * 4 + 255) / 256) * 256;
        long bufferSize = (long) bytesPerRow * height;

        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.obtain();
        bufferDesc.setSize(bufferSize);
        bufferDesc.setUsage(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead));
        WGPUBuffer stagingBuffer = webgpu.device.createBuffer(bufferDesc);

        // Command encoder
        WGPUCommandEncoder encoder = WGPUCommandEncoder.obtain();
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        webgpu.device.createCommandEncoder(encoderDesc, encoder);

        // Copy texture to buffer
        WGPUTexelCopyBufferInfo bufferInfo = WGPUTexelCopyBufferInfo.obtain();
        bufferInfo.setBuffer(stagingBuffer);
        WGPUTexelCopyBufferLayout layout = bufferInfo.getLayout();
        layout.setOffset(0);
        layout.setBytesPerRow(bytesPerRow);
        layout.setRowsPerImage(height);

        WGPUTexelCopyTextureInfo textureInfo = WGPUTexelCopyTextureInfo.obtain();
        textureInfo.setTexture(texture);
        textureInfo.setAspect(WGPUTextureAspect.All);
        WGPUOrigin3D origin = textureInfo.getOrigin();
        origin.setX(0);
        origin.setY(0);
        origin.setZ(0);

        WGPUExtent3D copySize = WGPUExtent3D.obtain();
        copySize.setWidth(width);
        copySize.setHeight(height);
        copySize.setDepthOrArrayLayers(1);

        encoder.copyTextureToBuffer(textureInfo, bufferInfo, copySize);

        // Submit
        WGPUCommandBuffer commandBuffer = WGPUCommandBuffer.obtain();
        WGPUCommandBufferDescriptor cmdDesc = WGPUCommandBufferDescriptor.obtain();
        encoder.finish(cmdDesc, commandBuffer);
        webgpu.queue.submit(commandBuffer);

        // Map buffer async
        stagingBuffer.mapAsync(WGPUMapMode.Read, 0, (int) bufferSize, WGPUCallbackMode.AllowSpontaneous,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        if (status == WGPUMapAsyncStatus.Success) {
                            ByteBuffer data = BufferUtils.newUnsafeByteBuffer((int) bufferSize);
                            stagingBuffer.getConstMappedRange(0, (int) bufferSize, data);
                            // Remove padding: copy to continuous buffer
                            ByteBuffer dst = BufferUtils.newUnsafeByteBuffer(width * height * 4);
                            data.rewind();
                            for (int y = 0; y < height; y++) {
                                int srcPos = y * bytesPerRow;
                                for (int i = 0; i < width * 4; i++) {
                                    dst.put(data.get(srcPos + i));
                                }
                            }
                            dst.rewind();

                            callback.onPixelsRead(dst, width, height);

                            BufferUtils.disposeUnsafeByteBuffer(data);
                            BufferUtils.disposeUnsafeByteBuffer(dst);
                        } else {
                            callback.onPixelsRead(null, width, height);
                        }
                        stagingBuffer.unmap();
                        stagingBuffer.release();
                    }
                });
    }

    @Override
    public void dispose() {
        // No persistent buffers to dispose
    }

    public interface PixelReadCallback {
        void onPixelRead(int r, int g, int b, int a);
    }

    public interface PixelsReadCallback {
        void onPixelsRead(ByteBuffer data, int width, int height);
    }
}
