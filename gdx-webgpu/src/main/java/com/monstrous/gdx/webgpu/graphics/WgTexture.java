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
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgTextureData;
import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.WebGPUTextureView;
import jnr.ffi.Pointer;
import java.nio.ByteBuffer;



public class WgTexture extends Texture {

    private final WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).webgpu;
    private final WebGPU_JNI webGPU = ((WgGraphics) Gdx.graphics).getWebGPU();
    protected int mipLevelCount;
    private Pointer texture;
    private WebGPUTextureView textureView;
    private Pointer sampler;
    private Pointer depthSampler;
    protected WGPUTextureFormat format;
    protected String label;
    private int numSamples;
//    private TextureFilter minFilter;
//    private TextureFilter magFilter;
    protected TextureData data; // cannot access data of Texture which is package private

    public WgTexture(String label, int width, int height, boolean mipMapping, boolean renderAttachment, WGPUTextureFormat format, int numSamples ) {
        this.data = new WgTextureData(width, height, 0, 0, 0);
        this.label = label;

        this.numSamples = numSamples;
        int textureUsage = WGPUTextureUsage.TextureBinding | WGPUTextureUsage.CopyDst;
        if (renderAttachment)
            textureUsage |= (WGPUTextureUsage.RenderAttachment | WGPUTextureUsage.CopySrc);    // todo COPY_SRC is temp
        create( label, mipLevelCount, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numSamples ) {
        this.data = new WgTextureData(width, height, 0, 0, 0);
        this.label = label;
        this.numSamples = numSamples;
        this.mipLevelCount = mipLevelCount;
        this.format = format;

        create( label, mipLevelCount, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numSamples, WGPUTextureFormat viewFormat ) {
        this.data = new WgTextureData(width, height, 0, 0, 0);
        this.label = label;
        this.numSamples = numSamples;
        this.mipLevelCount = mipLevelCount;
        this.format = format;
        create( label, mipLevelCount, textureUsage, format, 1, numSamples, viewFormat);
    }

    /*
     * File loading.
     */

    public WgTexture(String fileName) {
        this(fileName, true);
    }

    public WgTexture(String fileName, boolean mipMapping) {
        this(Gdx.files.internal(fileName), mipMapping);
    }


    public WgTexture(FileHandle file) {
        this(file, Pixmap.Format.RGBA8888, false);
    }

    public WgTexture(FileHandle file, boolean useMipMaps) {
        this(file, Pixmap.Format.RGBA8888, useMipMaps);
    }

    public WgTexture(FileHandle file, Pixmap.Format format, boolean useMipMaps) {
        this(TextureData.Factory.loadFromFile(file, format, useMipMaps), file.name());
    }

    public WgTexture(Pixmap pixmap) {
        this(pixmap, "pixmap");
    }

    public WgTexture(Pixmap pixmap, String label) {
        this(new PixmapTextureData(pixmap, null, false, false), label);
    }

    public WgTexture(TextureData data) {
        this(data, "texture");
    }



    public WgTexture(TextureData data, String label) {
        load(data, label);
    }


    public void load (TextureData data, String label) {
        this.data = data;
        this.label = label;
        this.format = WGPUTextureFormat.RGBA8Unorm; // force format


        if (!data.isPrepared()) data.prepare();

        uploadImageData(data);

    }

    private void uploadImageData( TextureData data ){
        mipLevelCount = data.useMipMaps() ? Math.max(1, bitWidth(Math.max(data.getWidth(), data.getHeight()))) : 1;
        numSamples = 1;
        format = WGPUTextureFormat.RGBA8Unorm; // assumption
        int textureUsage = WGPUTextureUsage.TextureBinding | WGPUTextureUsage.CopyDst;
        create( label, mipLevelCount, textureUsage, format, 1, numSamples, null);
        Pixmap pixmap = data.consumePixmap();

        Pixmap.Format dataFormat = data.getFormat();
        Pixmap.Format pixmapFormat = pixmap.getFormat();

        // data format is desired format, pixmap format is format from file
        // force 4 byte format!

        dataFormat = Pixmap.Format.RGBA8888;
        //if (data.getFormat() != pixmap.getFormat()) {
        if ( dataFormat != pixmap.getFormat()) {
            Pixmap tmp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), dataFormat);
            tmp.setBlending(Pixmap.Blending.None);
            tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
            if (data.disposePixmap()) {
                pixmap.dispose();
            }
            pixmap = tmp;
            //disposePixmap = true;
        }

        load(pixmap.getPixels(), 0);
    }


    @Override
    public int getWidth () {
        return data.getWidth();
    }

    @Override
    public int getHeight () {
        return data.getHeight();
    }

    public TextureData getTextureData () {
        return data;
    }

    public int getMipLevelCount() {
        return mipLevelCount;
    }

    public WebGPUTextureView getTextureView(){
        return textureView;
    }

    public Pointer getSampler() {
        if(sampler == null)
            buildSampler();
        return sampler;
    }

    public WGPUTextureFormat getFormat(){
        return format;
    }

    public Pointer getHandle(){
        return texture;
    }

    public int getNumSamples(){
        return numSamples;
    }


    protected int bitWidth(int value) {
        if (value == 0)
            return 0;
        else {
            int w = 0;
            while ((value >>= 1) > 0)
                ++w;
            return w;
        }
    }

    // renderAttachment - will this texture be used for render output
    // numLayers - normally 1, e.g. 6 for a cube map
    // numSamples - for anti-aliasing
    //
    protected void create( String label, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numLayers, int numSamples, WGPUTextureFormat viewFormat) {
        if (webgpu.device == null || webgpu.queue == null)
            throw new RuntimeException("Texture creation requires device and queue to be available\n");

        this.label = label;

        // Create the texture
        WGPUTextureDescriptor textureDesc = WGPUTextureDescriptor.createDirect();
        textureDesc.setNextInChain();
        textureDesc.setLabel(label);
        textureDesc.setDimension(WGPUTextureDimension._2D);
        this.format = format; //
        textureDesc.setFormat(format);
        textureDesc.setMipLevelCount(mipLevelCount);
        textureDesc.setSampleCount(numSamples);
        textureDesc.getSize().setWidth(data.getWidth());
        textureDesc.getSize().setHeight(data.getHeight());
        textureDesc.getSize().setDepthOrArrayLayers(numLayers);
        textureDesc.setUsage(textureUsage);
        if (viewFormat == null) {
            textureDesc.setViewFormatCount(0);
            textureDesc.setViewFormats(JavaWebGPU.createNullPointer());
        } else {
            long[] formats = new long[1];       // TMP
            formats[0] = viewFormat.ordinal();
            Pointer formatPtr = JavaWebGPU.createLongArrayPointer(formats);
            textureDesc.setViewFormatCount(1);
            textureDesc.setViewFormats(formatPtr);
        }

        texture = webGPU.wgpuDeviceCreateTexture(webgpu.device.getHandle(), textureDesc);

        //System.out.println("dimensions: "+textureDesc.getSize().getDepthOrArrayLayers());

        // Create the view of the  texture manipulated by the rasterizer
        WGPUTextureViewDimension dimension = (numLayers == 1 ? WGPUTextureViewDimension._2D : (numLayers == 6 ? WGPUTextureViewDimension.Cube : WGPUTextureViewDimension._2DArray));

        // if this is a depth format, use only the depth aspect for the texture view
        WGPUTextureAspect aspect;
        switch (format) {
            case Depth24Plus:
            case Depth32Float:
            case Depth24PlusStencil8:
            case Depth16Unorm:
            case Depth32FloatStencil8:
                aspect = WGPUTextureAspect.DepthOnly;
                break;
            default:
                aspect = WGPUTextureAspect.All;
                break;
        }
        textureView = new WebGPUTextureView(this, aspect, dimension, format, 0,
                mipLevelCount, 0, numLayers);
    }

    private void buildSampler(){

        // Create a sampler
        //
        WGPUSamplerDescriptor samplerDesc = WGPUSamplerDescriptor.createDirect();
        samplerDesc.setLabel("Standard texture sampler");
        samplerDesc.setAddressModeU(convertWrap(uWrap));
        samplerDesc.setAddressModeV(convertWrap(vWrap));
        samplerDesc.setAddressModeW(WGPUAddressMode.Repeat);
        samplerDesc.setMagFilter(convertFilter(magFilter));       // default filter in LibGDX is nearest for min and mag filter
        samplerDesc.setMinFilter(convertFilter(minFilter));
        samplerDesc.setMipmapFilter(WGPUMipmapFilterMode.Linear);       // todo

        samplerDesc.setLodMinClamp(0);
        samplerDesc.setLodMaxClamp(mipLevelCount);
        samplerDesc.setCompare(WGPUCompareFunction.Undefined);
        samplerDesc.setMaxAnisotropy(1);
        sampler = webGPU.wgpuDeviceCreateSampler(webgpu.device.getHandle(), samplerDesc);

    }

    public Pointer getDepthSampler(){
        if(depthSampler == null) {
            // Create a sampler
            WGPUSamplerDescriptor samplerDesc = WGPUSamplerDescriptor.createDirect();
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
            depthSampler = webGPU.wgpuDeviceCreateSampler(webgpu.device.getHandle(), samplerDesc);
        }
        return depthSampler;
    }


    /** convert from LibGDX enum value to WebGPU enum value */
    private WGPUFilterMode convertFilter(TextureFilter filter ){
        WGPUFilterMode mode;
        switch(filter){
            case Nearest:mode = WGPUFilterMode.Nearest; break;
            case Linear:mode = WGPUFilterMode.Linear; break;
            // todo fix others and test all combinations
            case MipMap:mode = WGPUFilterMode.Nearest; break;
            case MipMapNearestNearest:mode = WGPUFilterMode.Nearest; break;
            case MipMapNearestLinear:mode = WGPUFilterMode.Nearest; break;
            case MipMapLinearNearest:mode = WGPUFilterMode.Nearest; break;
            case MipMapLinearLinear:mode = WGPUFilterMode.Linear; break;
            default:
                throw new IllegalArgumentException("Unknown TextureFilter value.");

        }
        return mode;
    }

    // override this methods to avoid drop to GL functions
    @Override
    public void setFilter(TextureFilter minFilter, TextureFilter magFilter){
        if(minFilter == this.minFilter && magFilter == this.magFilter) return;
        // note: this may invalidate the sampler if it was built already and had other values
        sampler = null; // invalidate sampler todo release?
        this.minFilter = minFilter;
        this.magFilter = magFilter;
    }

    /** convert from LibGDX enum value to WebGPU enum value */
    private WGPUAddressMode convertWrap( TextureWrap wrap ){
        WGPUAddressMode mode;
        switch(wrap){
            case MirroredRepeat:        mode = WGPUAddressMode.MirrorRepeat; break;
            case Repeat:                mode = WGPUAddressMode.Repeat; break;
            case ClampToEdge:
            default:                    mode = WGPUAddressMode.ClampToEdge; break;
        }
        return mode;
    }

    // override this method to avoid drop to GL functions
    @Override
    public void setWrap (TextureWrap u, TextureWrap v){
        if(u == this.uWrap && v == this.vWrap) return;
        sampler = null; // invalidate sampler todo release?

        // ignored
        this.uWrap = u;
        this.vWrap = v;
    }

    /** Load pixel data into texture.
     *
     * @param pixelPtr
     * @param layer which layer to load in case of a 3d texture, otherwise 0
     */
    public void load(ByteBuffer pixelPtr, int layer) {

        // Arguments telling which part of the texture we upload to
        // (together with the last argument of writeTexture)
        WGPUImageCopyTexture destination = WGPUImageCopyTexture.createDirect();
        destination.setTexture(texture);
        destination.setMipLevel(0);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(0);
        destination.setAspect(WGPUTextureAspect.All);   // not relevant

        // Arguments telling how the C++ side pixel memory is laid out
        WGPUTextureDataLayout source = WGPUTextureDataLayout.createDirect();
        source.setOffset(0);
        source.setBytesPerRow(4L *data.getWidth());
        source.setRowsPerImage( data.getHeight());

        // Generate mipmap levels
        // candidate for compute shader

        int mipLevelWidth = data.getWidth();
        int mipLevelHeight = data.getHeight();
        int numComponents = numComponents(format);

        WGPUExtent3D ext = WGPUExtent3D.createDirect();

        byte[] prevPixels = null;
        for(int mipLevel = 0; mipLevel < mipLevelCount; mipLevel++) {

            byte[] pixels = new byte[4 * mipLevelWidth * mipLevelHeight];

            if(mipLevel == 0){
                // fast copy for most common case: mip level 0
                pixelPtr.position(0);
                pixelPtr.get(pixels, 0, numComponents * mipLevelWidth * mipLevelHeight);
            }
            else {
                // todo with compute shader
                int offset = 0;
                for (int y = 0; y < mipLevelHeight; y++) {
                    for (int x = 0; x < mipLevelWidth; x++) {

                        // Get the corresponding 4 pixels from the previous level
                        int offset00 = 4 * ((2 * y + 0) * (2 * mipLevelWidth) + (2 * x + 0));
                        int offset01 = 4 * ((2 * y + 0) * (2 * mipLevelWidth) + (2 * x + 1));
                        int offset10 = 4 * ((2 * y + 1) * (2 * mipLevelWidth) + (2 * x + 0));
                        int offset11 = 4 * ((2 * y + 1) * (2 * mipLevelWidth) + (2 * x + 1));

                        // Average r, g and b components
                        // beware that java bytes are signed. So we convert to integer first
                        int r = toUnsignedInt(prevPixels[offset00]) + toUnsignedInt(prevPixels[offset01]) + toUnsignedInt(prevPixels[offset10]) + toUnsignedInt(prevPixels[offset11]);
                        int g = toUnsignedInt(prevPixels[offset00 + 1]) + toUnsignedInt(prevPixels[offset01 + 1]) + toUnsignedInt(prevPixels[offset10 + 1]) + toUnsignedInt(prevPixels[offset11 + 1]);
                        int b = toUnsignedInt(prevPixels[offset00 + 2]) + toUnsignedInt(prevPixels[offset01 + 2]) + toUnsignedInt(prevPixels[offset10 + 2]) + toUnsignedInt(prevPixels[offset11 + 2]);
                        int a = toUnsignedInt(prevPixels[offset00 + 3]) + toUnsignedInt(prevPixels[offset01 + 3]) + toUnsignedInt(prevPixels[offset10 + 3]) + toUnsignedInt(prevPixels[offset11 + 3]);
                        pixels[offset++] = (byte) (r >> 2);    // divide by 4
                        pixels[offset++] = (byte) (g >> 2);
                        pixels[offset++] = (byte) (b >> 2);
                        pixels[offset++] = (byte) (a >> 2);  // alpha
                    }
                }
            }


            destination.setMipLevel(mipLevel);
            destination.getOrigin().setZ(layer);

            source.setBytesPerRow(4L *mipLevelWidth);
            source.setRowsPerImage(mipLevelHeight);

            ext.setWidth(mipLevelWidth);
            ext.setHeight(mipLevelHeight);
            ext.setDepthOrArrayLayers(1);

            pixelPtr.position(0);
            Pointer pp = JavaWebGPU.createByteBufferPointer(pixelPtr);  // convert ByteBuffer to Pointer

            if(mipLevel == 0){
                webGPU.wgpuQueueWriteTexture(webgpu.queue.getHandle(), destination, pp, (long) mipLevelWidth * mipLevelHeight * 4, source, ext);
            } else {

                // wrap byte array in native pointer
                Pointer pixelData = JavaWebGPU.createByteArrayPointer(pixels);
                // N.B. using textureDesc.getSize() for param won't work!
                webGPU.wgpuQueueWriteTexture(webgpu.queue.getHandle(), destination, pixelData, (long) mipLevelWidth * mipLevelHeight * 4, source, ext);
            }

            mipLevelWidth /= 2;
            mipLevelHeight /= 2;
            prevPixels = pixels;
        }
    }

    /** Load image data into a specific layer and mip level
     *
     */

    protected void loadMipLevel(Pointer data, int width, int height, int layer, int mipLevel) {

        // Arguments telling which part of the texture we upload to
        // (together with the last argument of writeTexture)
        WGPUImageCopyTexture destination = WGPUImageCopyTexture.createDirect();
        destination.setTexture(texture);
        destination.setMipLevel(mipLevel);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(layer);
        destination.setAspect(WGPUTextureAspect.All);   // not relevant

        // Arguments telling how the C++ side pixel memory is laid out
        WGPUTextureDataLayout source = WGPUTextureDataLayout.createDirect();
        source.setOffset(0);
        source.setBytesPerRow(4L *width);
        source.setRowsPerImage(height);

        WGPUExtent3D ext = WGPUExtent3D.createDirect();
        ext.setWidth(width);
        ext.setHeight(height);
        ext.setDepthOrArrayLayers(1);

        webGPU.wgpuQueueWriteTexture(webgpu.queue.getHandle(), destination, data, 4L * width * height, source, ext);
    }




    private static int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    public static int numComponents(WGPUTextureFormat format ){
        int n = 4;
        switch(format) {
            case R8Unorm: n = 1; break;
            case RG8Uint: n = 2; break;
            case RGBA8Uint: n = 4; break;
            case RGBA8Unorm: n = 4; break;
            case BGRA8Unorm: n = 4; break;
            default: throw new IllegalArgumentException("Unsupported format: "+format);

        }
        return n;
    }

    @Override
    public void dispose(){

        if(texture != null) {   // guard against double dispose
            //System.out.println("Destroy texture " + label);
            // todo released when?
            //LibGPU.webGPU.wgpuSamplerRelease(sampler);
            textureView.dispose();

            webGPU.wgpuTextureDestroy(texture);
            webGPU.wgpuTextureRelease(texture);
            texture = null;
        }
        super.dispose();
    }


}
