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
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgTextureData;

import java.nio.ByteBuffer;

public class WgTexture extends Texture {

    private final WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();

    private WGPUTexture texture;
    private WGPUTextureView textureView;
    private WGPUSampler sampler;
    private WGPUSampler depthSampler;
    protected WGPUTextureFormat format;
    protected String label;
    protected int mipLevelCount;
    private int numSamples;
    protected TextureData data; // cannot access data of Texture which is package private

    public WgTexture(String label, int width, int height, boolean mipMapping, boolean renderAttachment, WGPUTextureFormat format, int numSamples ) {
        this.data = new WgTextureData(width, height, 0, 0, 0);
        this.label = label;

        this.numSamples = numSamples;
        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst);
        if (renderAttachment)
            textureUsage = textureUsage.or(WGPUTextureUsage.RenderAttachment).or(WGPUTextureUsage.CopySrc);    // todo COPY_SRC is temp
        create( label, mipLevelCount, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, int mipLevelCount, WGPUTextureUsage textureUsage, WGPUTextureFormat format, int numSamples ) {
        this.data = new WgTextureData(width, height, 0, 0, 0);
        this.label = label;
        this.numSamples = numSamples;
        this.mipLevelCount = mipLevelCount;
        this.format = format;

        create( label, mipLevelCount, textureUsage, format, 1, numSamples, null);
    }

    public WgTexture(String label, int width, int height, int mipLevelCount, WGPUTextureUsage textureUsage, WGPUTextureFormat format, int numSamples, WGPUTextureFormat viewFormat ) {
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

//    public void load(TextureArrayData data, String label){
//        this.label = label;
//        this.format = WGPUTextureFormat.RGBA8Unorm; // force format
//        if (!data.isPrepared()) data.prepare();
//
//        // this will create a WebGPU texture and upload the images
//        data.consumeTextureArrayData();
//    }

    private void uploadImageData( TextureData data ){
        mipLevelCount = data.useMipMaps() ? Math.max(1, bitWidth(Math.min(data.getWidth(), data.getHeight()))) : 1;
        numSamples = 1;
        format = WGPUTextureFormat.RGBA8Unorm; // assumption
        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst);
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

        load(texture, pixmap.getPixels(),data.getWidth(), data.getHeight(), mipLevelCount, 0);
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

    public WGPUTextureView getTextureView(){
        return textureView;
    }

    public WGPUTextureFormat getFormat(){
        return format;
    }

    public WGPUTexture getHandle(){
        return texture;
    }

    public int getNumSamples(){
        return numSamples;
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

    // renderAttachment - will this texture be used for render output
    // numLayers - normally 1, e.g. 6 for a cube map
    // numSamples - for anti-aliasing
    //
    protected void create(String label, int mipLevelCount, WGPUTextureUsage textureUsage, WGPUTextureFormat format, int numLayers, int numSamples, WGPUTextureFormat viewFormat) {
        if (webgpu.device == null || webgpu.queue == null)
            throw new RuntimeException("Texture creation requires device and queue to be available\n");

        texture = createTexture(label, data.getWidth(), data.getHeight(), mipLevelCount, textureUsage, format, numLayers, numSamples, viewFormat);
        this.label = label;
        this.format = format;

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
        textureView = buildTextureView(texture, aspect, dimension, format, 0, mipLevelCount, 0, numLayers);
    }

    public static WGPUTexture createTexture(String label, int width, int height, int mipLevelCount, WGPUTextureUsage textureUsage, WGPUTextureFormat format, int numLayers, int numSamples, WGPUTextureFormat viewFormat) {
        // Create the texture
        WGPUTextureDescriptor textureDesc = WGPUTextureDescriptor.obtain();
        textureDesc.setNextInChain(null);
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

        WGPUTexture texture = new WGPUTexture();
        WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();
        webgpu.device.createTexture(textureDesc, texture);
        return texture;
    }

    public WGPUTextureView buildTextureView(WGPUTexture texture, WGPUTextureAspect aspect, WGPUTextureViewDimension dimension, WGPUTextureFormat format,
                             int baseMipLevel, int mipLevelCount, int baseArrayLayer, int arrayLayerCount) {

        // Create the view of the  texture manipulated by the rasterizer
        WGPUTextureViewDescriptor textureViewDesc = WGPUTextureViewDescriptor.obtain();
        textureViewDesc.setAspect(WGPUTextureAspect.All);
        textureViewDesc.setBaseArrayLayer(baseArrayLayer);
        textureViewDesc.setArrayLayerCount(arrayLayerCount);
        textureViewDesc.setBaseMipLevel(baseMipLevel);
        textureViewDesc.setMipLevelCount(mipLevelCount);
        textureViewDesc.setDimension(dimension);
        textureViewDesc.setFormat(texture.getFormat());         // should this match surface format instead?
        textureView = new WGPUTextureView();
        texture.createView(textureViewDesc, textureView);
        return textureView;
    }

    public WGPUSampler getSampler() {
        if(sampler == null) {
            // Create a sampler
            //
            WGPUSamplerDescriptor samplerDesc = WGPUSamplerDescriptor.obtain();
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
            sampler = new WGPUSampler();
            webgpu.device.createSampler(samplerDesc, sampler);
        }
        return sampler;
    }

    public WGPUSampler getDepthSampler(){
        if(depthSampler == null) {
            // Create a sampler
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

    // override this method to avoid drop to GL functions
    @Override
    public void setFilter(TextureFilter minFilter, TextureFilter magFilter){
        if(minFilter == this.minFilter && magFilter == this.magFilter) return;
        // note: this may invalidate the sampler if it was built already and had other values
        if(sampler != null)
            sampler.release();
        sampler = null; // invalidate sampler
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
        if(sampler != null)
            sampler.release();
        sampler = null; // invalidate sampler

        // ignored
        this.uWrap = u;
        this.vWrap = v;
    }

    /** Load pixel data into texture.
     *
     * @param pixelPtr
     * @param layer which layer to load in case of a 3d texture, otherwise 0
     */
    public static void load(WGPUTexture texture, ByteBuffer pixelPtr, int width, int height, int mipLevelCount, int layer) {

        // Generate mipmap levels
        // candidate for compute shader

        int mipLevelWidth = width;
        int mipLevelHeight = height;
        int numComponents = 4; //numComponents(format);

        ByteBuffer prev = pixelPtr;
        ByteBuffer next = pixelPtr;

        for(int mipLevel = 0; mipLevel < mipLevelCount; mipLevel++) {


            if(mipLevel != 0){

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
                        int r = toUnsignedInt(prev.get(offset00)) + toUnsignedInt(prev.get(offset01)) + toUnsignedInt(prev.get(offset10)) + toUnsignedInt(prev.get(offset11));
                        int g = toUnsignedInt(prev.get(offset00 + 1)) + toUnsignedInt(prev.get(offset01 + 1)) + toUnsignedInt(prev.get(offset10 + 1)) + toUnsignedInt(prev.get(offset11 + 1));
                        int b = toUnsignedInt(prev.get(offset00 + 2)) + toUnsignedInt(prev.get(offset01 + 2)) + toUnsignedInt(prev.get(offset10 + 2)) + toUnsignedInt(prev.get(offset11 + 2));
                        int a = toUnsignedInt(prev.get(offset00 + 3)) + toUnsignedInt(prev.get(offset01 + 3)) + toUnsignedInt(prev.get(offset10 + 3)) + toUnsignedInt(prev.get(offset11 + 3));

                        next.put( (byte) (r >> 2) );    // divide by 4
                        next.put( (byte) (g >> 2) );
                        next.put( (byte) (b >> 2) );
                        next.put( (byte) (a >> 2) );  // alpha
                    }
                }
                next.flip();
            }
            loadMipLevel(texture, next, mipLevelWidth, mipLevelHeight, 0,  mipLevel);

            mipLevelWidth /= 2;
            mipLevelHeight /= 2;

            if(prev != pixelPtr) {
                BufferUtils.disposeUnsafeByteBuffer(prev);
            }
            prev = next;
            next = BufferUtils.newUnsafeByteBuffer( mipLevelWidth * mipLevelHeight * 4);
        }
        if(prev != pixelPtr) {
            BufferUtils.disposeUnsafeByteBuffer(prev);
        }
        if(next != pixelPtr) {
            BufferUtils.disposeUnsafeByteBuffer(next);
        }
    }

    /** Load image data into a specific layer and mip level
     *
     */
     public static void loadMipLevel(WGPUTexture texture, ByteBuffer data, int width, int height, int layer, int mipLevel) {

        // Arguments telling which part of the texture we upload to
        // (together with the last argument of writeTexture)
        WGPUTexelCopyTextureInfo destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(texture);
        destination.setMipLevel(mipLevel);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(layer);
        destination.setAspect(WGPUTextureAspect.All);   // not relevant

        // Arguments telling how the pixel data is laid out
        WGPUTexelCopyBufferLayout source = WGPUTexelCopyBufferLayout.obtain();
        source.setOffset(0);
        source.setBytesPerRow(4 *width);
        source.setRowsPerImage(height);

        WGPUExtent3D extent = WGPUExtent3D.obtain();
        extent.setWidth(width);
        extent.setHeight(height);
        extent.setDepthOrArrayLayers(1);
        WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();
        webgpu.queue.writeTexture(destination, data, 4*width*height, source, extent);
    }

    // use at own risk
    public void load(ByteBuffer pixels) {

        // Arguments telling which part of the texture we upload to
        // (together with the last argument of writeTexture)
        WGPUTexelCopyTextureInfo destination = WGPUTexelCopyTextureInfo.obtain();
        destination.setTexture(texture);
        destination.setMipLevel(0);
        destination.getOrigin().setX(0);
        destination.getOrigin().setY(0);
        destination.getOrigin().setZ(0);
        destination.setAspect(WGPUTextureAspect.All);   // not relevant

        // Arguments telling how the pixel data is laid out
        WGPUTexelCopyBufferLayout source = WGPUTexelCopyBufferLayout.obtain();
        source.setOffset(0);
        source.setBytesPerRow(4 *data.getWidth());
        source.setRowsPerImage( data.getHeight());

        WGPUExtent3D extent = WGPUExtent3D.obtain();
        extent.setWidth(data.getWidth());
        extent.setHeight(data.getHeight());
        extent.setDepthOrArrayLayers(1);

        webgpu.queue.writeTexture(destination, pixels, pixels.limit(), source, extent);
    }




    private static int toUnsignedInt(byte x) {
        return ((int) x) & 0xff;
    }

    public static int numComponents(WGPUTextureFormat format ){
        int n;
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
            if(sampler != null) {
                sampler.release();
                sampler.dispose();
            }
            textureView.release();
            textureView.dispose();
            texture.destroy();
            texture.dispose();
            texture = null;
        }
        super.dispose();
    }


}
