/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgTextureData;

import java.nio.ByteBuffer;

public class WgTexture extends Texture {

    private final WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();

    protected WGPUTexture texture;
    protected WGPUTextureView textureView;
    protected WGPUSampler sampler;
    protected WGPUSampler depthSampler;
    protected WGPUTextureFormat format;
    protected String label;
    protected int mipLevelCount;
    protected int numSamples;
    protected TextureData data; // cannot access data of Texture which is package private

    /**
     * Wraps an externally-owned texture view without owning the underlying GPU texture.
     * {@link #dispose()} will release the view but will NOT destroy the backing {@link WGPUTexture}.
     * <p>
     * Typical use: wrap a single-layer 2D view of a texture array for per-layer rendering targets.
     *
     * @param view   the texture view to wrap (this WgTexture takes ownership of the view)
     * @param format the texture format
     * @param width  texture width in pixels
     * @param height texture height in pixels
     */
    public WgTexture(WGPUTextureView view, WGPUTextureFormat format, int width, int height) {
        this.data = new WgTextureData(width, height, false, 0, 0);
        this.label = "layer-view";
        this.numSamples = 1;
        this.format = format;
        this.mipLevelCount = 1;
        this.textureView = view;
        this.texture = null; // not owned — prevents dispose() from destroying the underlying texture
    }

    public WgTexture(String label, int width, int height, boolean useMipMaps, boolean renderAttachment,
            WGPUTextureFormat format, int numSamples) {
        this.data = new WgTextureData(width, height, useMipMaps, 0, 0);
        this.label = label;

        this.numSamples = numSamples;
        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst);
        if (renderAttachment)
            textureUsage = textureUsage.or(WGPUTextureUsage.RenderAttachment);
        create(label, useMipMaps, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, boolean useMipMaps, WGPUTextureUsage textureUsage,
            WGPUTextureFormat format, int numSamples) {
        this.data = new WgTextureData(width, height, useMipMaps, 0, 0);
        this.label = label;
        this.numSamples = numSamples;
        this.format = format;

        create(label, useMipMaps, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, boolean useMipMaps, WGPUTextureUsage textureUsage,
            WGPUTextureFormat format, int numSamples, WGPUTextureFormat viewFormat) {
        this.data = new WgTextureData(width, height, useMipMaps, 0, 0);
        this.label = label;
        this.numSamples = numSamples;
        create(label, useMipMaps, textureUsage, format, 1, numSamples, viewFormat);
    }

    // for cube map or texture array
    public WgTexture(String label, int width, int height, int numLayers, boolean useMipMaps,
            WGPUTextureUsage textureUsage, boolean isColor) {
        this.data = new WgTextureData(width, height, useMipMaps, 0, 0);
        this.label = label;

        this.numSamples = 1;

        WGPUTextureFormat fmt = isColor ? WGPUTextureFormat.RGBA8UnormSrgb : WGPUTextureFormat.RGBA8Unorm;

        create(label, useMipMaps, textureUsage, fmt, numLayers, numSamples, null);
    }

    /**
     * Create a texture array with an explicit format (e.g. for depth array textures).
     * The default texture view will be a 2DArray view spanning all layers.
     */
    public WgTexture(String label, int width, int height, int numLayers, boolean useMipMaps,
            WGPUTextureUsage textureUsage, WGPUTextureFormat format) {
        this.data = new WgTextureData(width, height, useMipMaps, 0, 0);
        this.label = label;
        this.numSamples = 1;
        create(label, useMipMaps, textureUsage, format, numLayers, numSamples, null);
    }

    /*
     * File loading.
     */

    public WgTexture(String fileName) {
        this(fileName, true, true);
    }

    public WgTexture(String fileName, boolean useMipMaps) {
        this(Gdx.files.internal(fileName), useMipMaps, true);
    }

    public WgTexture(String fileName, boolean useMipMaps, boolean isColor) {
        this(Gdx.files.internal(fileName), useMipMaps, isColor);
    }

    public WgTexture(FileHandle file) {
        this(file, null, true, true);
    }

    public WgTexture(FileHandle file, boolean useMipMaps) {
        this(file, null, useMipMaps, true);
    }

    public WgTexture(FileHandle file, boolean useMipMaps, boolean isColor) {
        this(file, null, useMipMaps, isColor);
    }

    public WgTexture(FileHandle file, Pixmap.Format format, boolean useMipMaps) {
        this(TextureData.Factory.loadFromFile(file, format, useMipMaps), file.name(), true);
    }

    public WgTexture(FileHandle file, Pixmap.Format format, boolean useMipMaps, boolean isColor) {
            this(TextureData.Factory.loadFromFile(file, format, useMipMaps), file.name(), isColor);
    }

    public WgTexture(Pixmap pixmap) {
        this(pixmap, "pixmap");
    }

    public WgTexture(Pixmap pixmap, String label) {
        this(new PixmapTextureData(pixmap, null, true, false), label, true);
    }

    public WgTexture(Pixmap pixmap, String label, boolean isColor) {
        this(new PixmapTextureData(pixmap, null, true, false), label, isColor);
    }

    public WgTexture(TextureData data) {
        this(data, "texture", true);
    }

    public WgTexture(TextureData data, String label) {
        this(data, label, true);
    }

    public WgTexture(TextureData data, String label, boolean isColor) {
        this.data = data;
        this.label = label;

        if (!data.isPrepared())
            data.prepare();

        numSamples = 1;

        // for color textures set format to Srgb so that on sampling it will be inverse gamma corrected to provide a
        // linear color value
        // for non-color texture, e.g. normal map, leave content as is.
        WGPUTextureFormat fmt = isColor ? WGPUTextureFormat.RGBA8UnormSrgb : WGPUTextureFormat.RGBA8Unorm;
        Pixmap.Format dataFormat = Pixmap.Format.RGBA8888;
        int numComponents = 4;
        if(data.getFormat() == Pixmap.Format.Alpha) { // todo complete for all format options
            fmt = WGPUTextureFormat.R8Unorm;
            dataFormat = Pixmap.Format.Alpha;
            numComponents = 1;
        }

        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst);
        create(label, data.useMipMaps(), textureUsage, fmt, 1, numSamples, null);
        Pixmap pixmap = data.consumePixmap();
        boolean mustDisposePixmap = data.disposePixmap();



        // data format is desired format, pixmap format is format from file
        // convert to desired format (typically RGBA) as needed
        // Note that for WebGPU, RGB (without alpha) is not a valid texture format
        // R8Unorm could be uses as one-channel format, in practice we will just force RGBA8888

        if (dataFormat != pixmap.getFormat()) {
            Pixmap tmp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), dataFormat);
            tmp.setBlending(Pixmap.Blending.None);
            tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
            if (mustDisposePixmap) {
                pixmap.dispose();
            }
            pixmap = tmp;
            mustDisposePixmap = true;
        }

        load(pixmap.getPixels(), numComponents, data.getWidth(), data.getHeight(), 0);
        if (mustDisposePixmap)
            pixmap.dispose();
    }

    @Override
    public int getWidth() {
        return data.getWidth();
    }

    @Override
    public int getHeight() {
        return data.getHeight();
    }

    public TextureData getTextureData() {
        return data;
    }

    public int getMipLevelCount() {
        return mipLevelCount;
    }

    public WGPUTextureView getTextureView() {
        return textureView;
    }

    public WGPUTextureFormat getFormat() {
        return format;
    }

    public WGPUTexture getHandle() {
        return texture;
    }

    public int getNumSamples() {
        return numSamples;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String lab) {
        label = lab;
    }

    public static int bitWidth(int value) {
        if (value == 0)
            return 0;
        else {
            int w = 0;
            while ((value >>= 1) > 0)
                ++w;
            return w;
        }
    }

    /** determine a preferred number of mip levels based on texture dimensions */
    public static int calculateMipLevelCount(int width, int height) {
        return Math.max(1, bitWidth(Math.min(width, height)));
    }

    // renderAttachment - will this texture be used for render output
    // numLayers - normally 1, e.g. 6 for a cube map
    // numSamples - for anti-aliasing
    //
    protected void create(String label, boolean useMipMaps, WGPUTextureUsage textureUsage, WGPUTextureFormat format,
            int numLayers, int numSamples, WGPUTextureFormat viewFormat) {
        if (webgpu.device == null || webgpu.queue == null)
            throw new RuntimeException("Texture creation requires device and queue to be available\n");

        this.mipLevelCount = useMipMaps ? Math.max(1, bitWidth(Math.min(data.getWidth(), data.getHeight()))) : 1;

        this.texture = createTexture(label, data.getWidth(), data.getHeight(), mipLevelCount, textureUsage, format,
                numLayers, numSamples, viewFormat);
        this.label = label;
        this.format = format;

        // Create the view of the texture manipulated by the rasterizer
        WGPUTextureViewDimension dimension = (numLayers == 1 ? WGPUTextureViewDimension._2D
                : (numLayers == 6 ? WGPUTextureViewDimension.Cube : WGPUTextureViewDimension._2DArray));

        this.textureView = buildTextureView(dimension, format, 0, mipLevelCount, 0, numLayers);
    }

    public static WGPUTexture createTexture(String label, int width, int height, int mipLevelCount,
            WGPUTextureUsage textureUsage, WGPUTextureFormat format, int numLayers, int numSamples,
            WGPUTextureFormat viewFormat) {
        // Create the texture
        WGPUTextureDescriptor textureDesc = WGPUTextureDescriptor.obtain();
        textureDesc.setNextInChain(WGPUChainedStruct.NULL);
        textureDesc.setLabel(label);
        textureDesc.setDimension(WGPUTextureDimension._2D);

        textureDesc.setFormat(format);
        textureDesc.setMipLevelCount(mipLevelCount);
        textureDesc.setSampleCount(numSamples);
        textureDesc.getSize().setWidth(width);
        textureDesc.getSize().setHeight(height);
        textureDesc.getSize().setDepthOrArrayLayers(numLayers);
        textureDesc.setUsage(textureUsage);
        WGPUVectorTextureFormat viewFormats = WGPUVectorTextureFormat.obtain();

        if (viewFormat != null) {
            viewFormats.push_back(viewFormat);
        }
        textureDesc.setViewFormats(viewFormats);

        WGPUTexture tex = new WGPUTexture();
        WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();
        webgpu.device.createTexture(textureDesc, tex);
        return tex;
    }

    public WGPUTextureView buildTextureView(WGPUTextureViewDimension dimension, WGPUTextureFormat format,
            int baseMipLevel, int mipLevelCount, int baseArrayLayer, int arrayLayerCount) {

        // Create the view of the texture manipulated by the rasterizer
        WGPUTextureViewDescriptor textureViewDesc = WGPUTextureViewDescriptor.obtain();
        textureViewDesc.setAspect(WGPUTextureAspect.All);
        textureViewDesc.setBaseArrayLayer(baseArrayLayer);
        textureViewDesc.setArrayLayerCount(arrayLayerCount);
        textureViewDesc.setBaseMipLevel(baseMipLevel);
        textureViewDesc.setMipLevelCount(mipLevelCount);
        textureViewDesc.setDimension(dimension);
        textureViewDesc.setFormat(format);
        WGPUTextureView view = new WGPUTextureView();
        texture.createView(textureViewDesc, view);
        return view;
    }

    public WGPUSampler getSampler() {
        if (sampler == null) {
            WGPUSamplerDescriptor samplerDesc = WGPUSamplerDescriptor.obtain();
            samplerDesc.setLabel("Standard texture sampler");
            samplerDesc.setAddressModeU(convertWrap(uWrap));
            samplerDesc.setAddressModeV(convertWrap(vWrap));
            samplerDesc.setAddressModeW(WGPUAddressMode.Repeat);
            samplerDesc.setMagFilter(convertFilter(magFilter));
            samplerDesc.setMinFilter(convertFilter(minFilter));
            samplerDesc.setMipmapFilter(WGPUMipmapFilterMode.Linear); // todo

            samplerDesc.setLodMinClamp(0);
            samplerDesc.setLodMaxClamp(mipLevelCount);
            samplerDesc.setCompare(WGPUCompareFunction.Undefined);
            samplerDesc.setMaxAnisotropy(1);
            sampler = new WGPUSampler();
            webgpu.device.createSampler(samplerDesc, sampler);
        }
        return sampler;
    }

    public WGPUSampler getDepthSampler() {
        if (depthSampler == null) {
            WGPUSamplerDescriptor samplerDesc = WGPUSamplerDescriptor.obtain();
            samplerDesc.setAddressModeU(WGPUAddressMode.ClampToEdge);
            samplerDesc.setAddressModeV(WGPUAddressMode.ClampToEdge);
            samplerDesc.setAddressModeW(WGPUAddressMode.ClampToEdge);
            samplerDesc.setMagFilter(WGPUFilterMode.Linear);
            samplerDesc.setMinFilter(WGPUFilterMode.Linear);
            samplerDesc.setMipmapFilter(WGPUMipmapFilterMode.Linear);

            samplerDesc.setLodMinClamp(0);
            samplerDesc.setLodMaxClamp(1);
            samplerDesc.setCompare(WGPUCompareFunction.Less);
            samplerDesc.setMaxAnisotropy(1);
            depthSampler = new WGPUSampler();
            webgpu.device.createSampler(samplerDesc, depthSampler);
        }
        return depthSampler;
    }

    /** convert from LibGDX enum value to WebGPU enum value */
    private WGPUFilterMode convertFilter(TextureFilter filter) {
        if (filter == null)
            return WGPUFilterMode.Nearest;

        WGPUFilterMode mode;
        switch (filter) {
            case Nearest:
                mode = WGPUFilterMode.Nearest;
                break;
            case Linear:
                mode = WGPUFilterMode.Linear;
                break;
            // todo fix others and test all combinations
            case MipMap:
                mode = WGPUFilterMode.Nearest;
                break;
            case MipMapNearestNearest:
                mode = WGPUFilterMode.Nearest;
                break;
            case MipMapNearestLinear:
                mode = WGPUFilterMode.Nearest;
                break;
            case MipMapLinearNearest:
                mode = WGPUFilterMode.Nearest;
                break;
            case MipMapLinearLinear:
                mode = WGPUFilterMode.Linear;
                break;
            default:
                throw new IllegalArgumentException("Unknown TextureFilter value.");

        }
        return mode;
    }

    // override this method to avoid drop to GL functions
    @Override
    public void setFilter(TextureFilter minFilter, TextureFilter magFilter) {
        if (minFilter == this.minFilter && magFilter == this.magFilter)
            return;
        // note: this may invalidate the sampler if it was built already and had other values
        if (sampler != null)
            sampler.release();
        sampler = null; // invalidate sampler
        this.minFilter = minFilter;
        this.magFilter = magFilter;
    }

    /** convert from LibGDX enum value to WebGPU enum value */
    private WGPUAddressMode convertWrap(TextureWrap wrap) {
        WGPUAddressMode mode;
        switch (wrap) {
            case MirroredRepeat:
                mode = WGPUAddressMode.MirrorRepeat;
                break;
            case Repeat:
                mode = WGPUAddressMode.Repeat;
                break;
            case ClampToEdge:
            default:
                mode = WGPUAddressMode.ClampToEdge;
                break;
        }
        return mode;
    }

    // override this method to avoid drop to GL functions
    @Override
    public void setWrap(TextureWrap u, TextureWrap v) {
        if (u == this.uWrap && v == this.vWrap)
            return;
        if (u == null && v == null)
            return;
        if (sampler != null)
            sampler.release();
        sampler = null; // invalidate sampler

        if (u != null)
            this.uWrap = u;
        if (v != null)
            this.vWrap = v;
    }

    /**
     * Load pixel data into texture.
     *
     * @param pixelPtr pixel data
     * @param layer which layer to load in case of a 3d texture, otherwise 0
     */
    public void load(ByteBuffer pixelPtr, int numComponents, int width, int height, int layer) {

        // Generate mipmap levels if this.mipLevelCount > 1
        // candidate for compute shader

        if (pixelPtr.limit() != width * height * numComponents)
            throw new GdxRuntimeException(
                    "ByteBuffer content size does not match the texture size (pixels must be RGBA8888).");

        ByteBuffer prev = null;
        ByteBuffer next = pixelPtr;

        int mipLevelWidth = width;
        int mipLevelHeight = height;

        for (int mipLevel = 0; mipLevel < mipLevelCount; mipLevel++) {

            if (mipLevel != 0) {

                // todo with compute shader
                for (int y = 0; y < mipLevelHeight; y++) {
                    for (int x = 0; x < mipLevelWidth; x++) {

                        // Get the corresponding 4 pixels from the previous level
                        int offset00 = numComponents * ((2 * y + 0) * (2 * mipLevelWidth) + (2 * x + 0));
                        int offset01 = numComponents * ((2 * y + 0) * (2 * mipLevelWidth) + (2 * x + 1));
                        int offset10 = numComponents * ((2 * y + 1) * (2 * mipLevelWidth) + (2 * x + 0));
                        int offset11 = numComponents * ((2 * y + 1) * (2 * mipLevelWidth) + (2 * x + 1));

                        // Average each color components (R, G, B and A or a subset)
                        // beware that java bytes are signed. So we convert to integer first
                        for(int i = 0; i < numComponents; i++){
                            int component = toUnsignedInt(prev.get(offset00+i)) + toUnsignedInt(prev.get(offset01+i))
                                + toUnsignedInt(prev.get(offset10+i)) + toUnsignedInt(prev.get(offset11+i));
                            next.put((byte) (component >> 2)); // divide by 4 to take average
                        }
                    }
                }
                next.flip();
            }
            loadMipLevel(next, numComponents, mipLevelWidth, mipLevelHeight, layer, mipLevel);

            mipLevelWidth /= 2;
            mipLevelHeight /= 2;

            if (prev != null && prev != pixelPtr) {
                BufferUtils.disposeUnsafeByteBuffer(prev);
            }
            // todo we should be able to avoid one malloc/free
            prev = next;
            next = BufferUtils.newUnsafeByteBuffer(mipLevelWidth * mipLevelHeight * numComponents );
        }

        if (next != pixelPtr) {
            BufferUtils.disposeUnsafeByteBuffer(next);
        }
    }

    /**
     * Load image data into a specific layer and mip level
     */
    public void loadMipLevel(ByteBuffer data, int numComponents, int width, int height, int layer, int mipLevel) {

        // Arguments telling which part of the texture we upload to
        // (together with the last argument of writeTexture)
        WGPUTexelCopyTextureInfo destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(texture);
        destination.setMipLevel(mipLevel);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(layer);
        destination.setAspect(WGPUTextureAspect.All); // not relevant

        // Arguments telling how the pixel data is laid out
        WGPUTexelCopyBufferLayout source = WGPUTexelCopyBufferLayout.obtain();
        source.setOffset(0);
        source.setBytesPerRow(numComponents * width);
        source.setRowsPerImage(height);

        WGPUExtent3D extent = WGPUExtent3D.obtain();
        extent.setWidth(width);
        extent.setHeight(height);
        extent.setDepthOrArrayLayers(1);
        WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();
        if (!webgpu.isFrameStarted()) {
            Gdx.app.error("WgTexture", "writeTexture called outside of beginFrame/endFrame window. This may cause a crash.");
        }
        webgpu.queue.writeTexture(destination, data, numComponents * width * height, source, extent);
    }

    /**
     * Load image data into layer 0 and mip level 0.
     */
    public void load(ByteBuffer pixels, int numComponents, int width, int height) {
        loadMipLevel(pixels, numComponents, width, height, 0, 0);
    }

    private static int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    public static int numComponents(WGPUTextureFormat format) {
        int n;
        switch (format) {
            case R8Unorm:
                n = 1;
                break;
            case RG8Uint:
                n = 2;
                break;
            case RGBA8Uint:
                n = 4;
                break;
            case RGBA8Unorm:
                n = 4;
                break;
            case BGRA8Unorm:
                n = 4;
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
        return n;
    }

    @Override
    public void dispose() {
        if (texture != null) { // guard against double dispose
            if (sampler != null) {
                sampler.release();
                sampler.dispose();
                sampler = null;
            }
            if (depthSampler != null) {
                depthSampler.release();
                depthSampler.dispose();
                depthSampler = null;
            }
            if (textureView != null) {
                textureView.release();
                textureView.dispose();
                textureView = null;
            }
            texture.destroy();
            texture.dispose();
            texture = null;
        } else if (textureView != null) {
            // Layer-view wrapper (texture == null): we own the view but not the underlying texture.
            textureView.release();
            textureView.dispose();
            textureView = null;
        }
        super.dispose();
    }

}
