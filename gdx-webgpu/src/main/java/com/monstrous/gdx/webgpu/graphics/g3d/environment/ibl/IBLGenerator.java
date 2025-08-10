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

package com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.EnvironmentMap;


/** Utility methods to create IBL textures */
public class IBLGenerator  {


    public static class MyShaderProvider extends BaseShaderProvider {
        public final IBLShader.Config config = new IBLShader.Config("");

        public MyShaderProvider(String shaderSource) {
            config.shaderSource = shaderSource;
        }

        @Override
        protected Shader createShader(final Renderable renderable) {
            return new IBLShader(renderable, config);
        }
    }

    /** Create a cube map from an equirectangular texture */
    public static WgCubemap buildCubeMapFromEquirectangularTexture(WgTexture equiRectangular, int outputTextureSize) {
        // Convert an equirectangular image to a cube map
        Material material = new Material(TextureAttribute.createDiffuse(equiRectangular));
        Model cube = buildUnitCube(material);
        ModelInstance cubeInstance = new ModelInstance(cube);

        WgCubemap environmentMap = constructSideTextures(cubeInstance, outputTextureSize);

        cube.dispose();

        return environmentMap;
    }



    public static WgCubemap buildIrradianceMap(WgCubemap environmentMap, int size){
        System.out.println("Building irradiance map");
        // Convert an environment cube map to an irradiance cube map
        Model cube = buildUnitCube(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        ModelInstance cubeInstance = new ModelInstance(cube);

        Environment environment = new Environment();
        environment.set(new WgCubemapAttribute(EnvironmentMap, environmentMap));    // add cube map attribute

        String shaderSource = Gdx.files.internal("shaders/modelbatchCubeMapIrradiance.wgsl").readString();
        WgCubemap irradianceMap = constructMap(cubeInstance, shaderSource, environment, size, 1);

        cube.dispose();
        System.out.println("Built irradiance map");
        return irradianceMap;
    }


    public static WgCubemap buildRadianceMap(WgCubemap environmentMap, int size){
        System.out.println("Building irradiance map");
        // Convert an environment cube map to a radiance cube map
        Model cube = buildUnitCube(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
        ModelInstance cubeInstance = new ModelInstance(cube);

        Environment environment = new Environment();
        environment.set(new WgCubemapAttribute(EnvironmentMap, environmentMap));    // add cube map attribute

        String shaderSource = Gdx.files.internal("shaders/modelbatchCubeMapRadiance.wgsl").readString();
        WgCubemap radianceMap = constructMap(cubeInstance, shaderSource, environment, size, 5); // todo derive levels from size

        cube.dispose();
        System.out.println("Built irradiance map");
        return radianceMap;
    }


    public WgTexture getBRDFLookUpTable(){
        return new WgTexture(Gdx.files.internal("brdfLUT.png"), false);
    }


    // the order of the layers is +X, -X, +Y, -Y, +Z, -Z
    private static final Vector3[] directions = {
        new Vector3(-1, 0, 0), new Vector3(1, 0, 0),
        new Vector3(0, 1, 0), new Vector3(0, -1, 0),
        new Vector3(0,0,1), new Vector3(0, 0, -1)
    };


    private static WgCubemap constructMap(ModelInstance instance, String shaderSource, Environment environment, int size, int levels){
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        WgModelBatch mapBatch = new WgModelBatch(new MyShaderProvider(shaderSource));

        PerspectiveCamera snapCam = createCamera();

        WgCubemap cube = new WgCubemap(size, levels > 0, WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst));

        for(int mipLevel = 0; mipLevel < levels; mipLevel++) {


            final WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst).or(WGPUTextureUsage.RenderAttachment).or(WGPUTextureUsage.CopySrc);
            WGPUTextureFormat format = WGPUTextureFormat.RGBA8UnormSrgb;
            WgTexture colorTexture = new WgTexture("fbo color", size, size, false, textureUsage, format, 1, format);
            WgTexture depthTexture = new WgTexture("fbo depth", size, size, false, textureUsage, WGPUTextureFormat.Depth24Plus, 1, WGPUTextureFormat.Depth24Plus);

            webgpu.setViewportRectangle(0, 0, size, size);


            for (int side = 0; side < 6; side++) {
                // point the camera at one of the cube sides
                snapCam.direction.set(directions[side]);
                snapCam.position.set(Vector3.Zero);
                if (side == 3)
                    snapCam.up.set(0, 0, 1);
                else if (side == 2)
                    snapCam.up.set(0, 0, -1);
                else
                    snapCam.up.set(0, 1, 0);
                snapCam.update();


                WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
                encoderDesc.setLabel("encoder");
                webgpu.device.createCommandEncoder(encoderDesc, webgpu.encoder);

                WebGPUContext.RenderOutputState prevState = webgpu.pushTargetView(colorTexture.getTextureView(), format, size, size, depthTexture);

                mapBatch.begin(snapCam, Color.BLACK, true);
                mapBatch.render(instance, environment);
                mapBatch.end();

                WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
                cmdBufferDescriptor.setNextInChain(null);
                cmdBufferDescriptor.setLabel("Command buffer");
                WGPUCommandBuffer command = new WGPUCommandBuffer();

                webgpu.encoder.finish(cmdBufferDescriptor, command);
                webgpu.encoder.release();
                webgpu.queue.submit(1, command);
                command.release();

                webgpu.popTargetView(prevState);

                webgpu.device.createCommandEncoder(encoderDesc, webgpu.encoder);
                copyTexture(webgpu.encoder, cube, side, mipLevel, colorTexture, size);

                webgpu.encoder.finish(cmdBufferDescriptor, command);
                webgpu.encoder.release();

                webgpu.queue.submit(1, command);
                command.release();
                command.dispose();



            } // next side
            size /= 2;
        } // next mip level
        webgpu.setViewportRectangle(0,0,Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mapBatch.dispose();
        return cube;
    }


    private static WgCubemap constructSideTextures(ModelInstance instance, int size){
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        String shaderSource = Gdx.files.internal("shaders/modelbatchEquilateral.wgsl").readString();
        WgModelBatch mapBatch = new WgModelBatch(new MyShaderProvider(shaderSource));

        PerspectiveCamera snapCam = createCamera();
        WgCubemap cube = new WgCubemap(size, false, WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst));

        final WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or( WGPUTextureUsage.CopyDst).or(WGPUTextureUsage.RenderAttachment).or( WGPUTextureUsage.CopySrc);
        WGPUTextureFormat format = WGPUTextureFormat.RGBA8UnormSrgb;
        WgTexture colorTexture = new WgTexture("fbo color", size, size, false, textureUsage, format, 1, format);
        WgTexture depthTexture = new WgTexture("fbo depth", size, size, false, textureUsage, WGPUTextureFormat.Depth24Plus, 1, WGPUTextureFormat.Depth24Plus);

        webgpu.setViewportRectangle(0,0, size,size);


        for (int side = 0; side < 6; side++) {
            // point the camera at one of the cube sides
            snapCam.direction.set(directions[side]);
            snapCam.position.set(Vector3.Zero);
            if(side == 3)
                snapCam.up.set(0,0,1);
            else if (side == 2)
                snapCam.up.set(0,0,-1);
            else
                snapCam.up.set(0,1,0);
            snapCam.update();


            WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
            encoderDesc.setLabel("encoder");
            webgpu.device.createCommandEncoder(encoderDesc, webgpu.encoder);

            WebGPUContext.RenderOutputState prevState = webgpu.pushTargetView(colorTexture.getTextureView(), format, size, size, depthTexture);

            mapBatch.begin(snapCam, Color.BLACK, true);
            mapBatch.render(instance);
            mapBatch.end();

            WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
            cmdBufferDescriptor.setNextInChain(null);
            cmdBufferDescriptor.setLabel("Command buffer");
            WGPUCommandBuffer command = new WGPUCommandBuffer();

            webgpu.encoder.finish(cmdBufferDescriptor, command);
            webgpu.encoder.release();
            webgpu.queue.submit(1, command);
            command.release();

            webgpu.popTargetView(prevState);

            webgpu.device.createCommandEncoder(encoderDesc, webgpu.encoder);
            copyTexture( webgpu.encoder, cube, side, 0, colorTexture, size);

            webgpu.encoder.finish(cmdBufferDescriptor, command);
            webgpu.encoder.release();

            webgpu.queue.submit(1, command);
            command.release();
            command.dispose();

        }
        webgpu.setViewportRectangle(0,0,Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mapBatch.dispose();
        return cube;
    }





    /** Copy a texture to one side (and one mip level) of a cube map
     * Note: there need to be an active encoder.
     * */
    public static void copyTexture(WGPUCommandEncoder encoder, WgCubemap cube, int side, int mipLevel, WgTexture sideTexture, int size){

        // beware not to use .obtain() for 2 structures we need at the same time, because the memory is reused.
        final WGPUTexelCopyTextureInfo source = new WGPUTexelCopyTextureInfo();
        final WGPUTexelCopyTextureInfo destination = new WGPUTexelCopyTextureInfo();

            source.setTexture(sideTexture.getHandle());
            source.setMipLevel(0);
            source.setAspect(WGPUTextureAspect.All);
            source.getOrigin().setX(0);
            source.getOrigin().setY(0);
            source.getOrigin().setZ(0);

            destination.setTexture(cube.getHandle());
            destination.setMipLevel(mipLevel);                // put in specific mip level
            destination.setAspect(WGPUTextureAspect.All);
            destination.getOrigin().setX(0);
            destination.getOrigin().setY(0);
            destination.getOrigin().setZ(side);

            WGPUExtent3D ext = WGPUExtent3D.obtain();
            ext.setWidth(size);
            ext.setHeight(size);
            ext.setDepthOrArrayLayers(1);

            encoder.copyTextureToTexture(source, destination, ext);

            source.dispose();
            destination.dispose();
    }

    /** Create a camera to snap the inside of a unit cube.  View is 90 degrees.
     */
    private static PerspectiveCamera createCamera() {
        PerspectiveCamera snapCam =new PerspectiveCamera(90,1,1);
        snapCam.position.set(0,0,0);
        snapCam.direction.set(0,0,1);
        snapCam.near =0; // important because default is 1
        snapCam.update();
        return snapCam;
    }

    public static Model buildUnitCube(Material material){

        ModelBuilder modelBuilder = new WgModelBuilder();
        return modelBuilder.createBox(1f, 1f, 1f, material,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
    }

}
