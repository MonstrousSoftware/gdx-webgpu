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
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
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
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;


/** Utility methods to create IBL textures */
public class IBLGenerator implements Disposable {

    private final PerspectiveCamera snapCam;
    private final WebGPUContext webgpu;
    private final WgModelBatch mapBatch;
    public WgTexture[] textureSides;

    public static class MyShaderProvider extends BaseShaderProvider {
        public final IBLShader.Config config = new IBLShader.Config("");

        public MyShaderProvider(String shaderSource) {
            config.shaderSource = shaderSource;
        }

        @Override
        protected Shader createShader (final Renderable renderable) {
            return new IBLShader(renderable, config);
        }
    };

    public IBLGenerator() {
        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        webgpu = gfx.getContext();

        snapCam = new PerspectiveCamera(90, 1, 1);
        snapCam.position.set(0,0,0);
        snapCam.direction.set(0,0,1);
        snapCam.near = 0; // important because default is 1
        snapCam.update();

        String shaderSource = Gdx.files.internal("shaders/modelbatchEquilateral.wgsl").readString();

        mapBatch = new WgModelBatch(new MyShaderProvider(shaderSource));
    }

    public WgCubemap buildCubeMapFromEquirectangularTexture(WgTexture equiRectangular, int textureSize){
        // Convert an equirectangular image to a cube map
        Material material = new Material(TextureAttribute.createDiffuse(equiRectangular));
        Model cube = buildUnitCube(material);
        ModelInstance cubeInstance = new ModelInstance(cube);

        WgCubemap environmentMap = constructSideTextures(cubeInstance, textureSize);

        cube.dispose();


        return environmentMap;
    }
//
//    public WgCubemap buildIrradianceMap(WgCubemap environmentMap, int size){
//        // Convert an environment cube map to an irradiance cube map
//        Model cube = buildUnitCube(new Material(ColorAttribute.createDiffuse(Color.WHITE)));
//        ModelInstance instance = new ModelInstance(cube);
//        environment.shaderSourcePath = "shaders/modelbatchCubeMapIrradiance.wgsl";
//
//        environment.set(new WgCubemapAttribute(EnvironmentMap, environmentMap));
//
//        CommandEncoder encoder = new CommandEncoder(LibGPU.device);
//        LibGPU.commandEncoder = encoder.getHandle(); //LibGPU.app.prepareEncoder();
//        LibGPU.graphics.passNumber = 0;
//
//        constructSideTextures(instance, size);
//        CubeMap irradianceMap = copyTextures(size);
//        LibGPU.app.finishEncoder(encoder);
//        encoder.dispose();
//        environment.shaderSourcePath = null;
//        cube.dispose();
//        return irradianceMap;
//    }
//
//    public CubeMap buildRadianceMap(CubeMap environmentMap, int size){
//        CubeMap prefilterMap = new CubeMap(size, size, true);  // mipmapped cube map
//        int mipLevels = prefilterMap.getMipLevelCount();
//        //System.out.println("radiance map mips:"+mipLevels);
//        Model cube = new Model(buildUnitCube(), new Material(Color.WHITE));
//        ModelInstance instance = new ModelInstance(cube);
//        // Convert an environment cube map to a radiance cube map
//        environment.shaderSourcePath = "shaders/modelbatchCubeMapRadiance.wgsl";        // hacky
//        environment.setCubeMap(environmentMap);
//
//        for(int mip = 0; mip < mipLevels; mip++) {
//            CommandEncoder encoder = new CommandEncoder(LibGPU.device);
//            LibGPU.commandEncoder = encoder.getHandle(); //LibGPU.app.prepareEncoder();
//            LibGPU.graphics.passNumber = 0;
//            environment.ambientLightLevel = (float)mip/(mipLevels-1);   // hacky; use this to pass roughness level
//            constructSideTextures(instance, size);
//            copyTextures(prefilterMap, size, mip);
//            LibGPU.app.finishEncoder(encoder);
//            encoder.dispose();
//            size /= 2;
//        }
//        environment.shaderSourcePath = null;
//        cube.dispose();
//        return prefilterMap;
//    }

    public WgTexture getBRDFLookUpTable(){
        return new WgTexture(Gdx.files.internal("brdfLUT.png"), false);
    }


    // the order of the layers is +X, -X, +Y, -Y, +Z, -Z
    private final Vector3[] directions = {
//        new Vector3(1, 0, 0), new Vector3(-1, 0, 0),
//        new Vector3(0, -1, 0), new Vector3(0, 1, 0),
//        new Vector3(0,0,1), new Vector3(0, 0, -1)
        new Vector3(-1, 0, 0), new Vector3(1, 0, 0),
        new Vector3(0, 1, 0), new Vector3(0, -1, 0),
        new Vector3(0,0,1), new Vector3(0, 0, -1)
    };



    private WgCubemap constructSideTextures(ModelInstance instance, int size){


        WgCubemap cube = new WgCubemap(size, false, WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst));

        final WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or( WGPUTextureUsage.CopyDst).or(WGPUTextureUsage.RenderAttachment).or( WGPUTextureUsage.CopySrc);
        WGPUTextureFormat format = WGPUTextureFormat.RGBA8UnormSrgb;
        WgTexture colorTexture = new WgTexture("fbo color", size, size, false, textureUsage, format, 1, format);
        WgTexture depthTexture = new WgTexture("fbo depth", size, size, false, textureUsage, WGPUTextureFormat.Depth24Plus, 1, WGPUTextureFormat.Depth24Plus);



        for (int side = 0; side < 6; side++) {

            snapCam.direction.set(directions[side]);
            snapCam.position.set(Vector3.Zero);
            if(side == 3)
                snapCam.up.set(0,0,1);
            else if (side == 2)
                snapCam.up.set(0,0,-1);
            else
                snapCam.up.set(0,1,0);
            snapCam.update();

            webgpu.setViewportRectangle(0,0, size,size);


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
            copyTexture(cube, side, 0, colorTexture, size);

            //copyTexture(cube, side, size, (WgTexture)textureSides[side].getColorBufferTexture(), 0);

            webgpu.encoder.finish(cmdBufferDescriptor, command);
            webgpu.encoder.release();

            webgpu.queue.submit(1, command);
            command.release();

        }
        webgpu.setViewportRectangle(0,0,Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        return cube;
    }



    /** copy 6 textures (textureSides[]) into a new cube map */
    private WgCubemap copyTextures(int size, boolean useMipmaps, WgTexture[] textureSides) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);

        // create a cube map texture
        WgCubemap cube = new WgCubemap(size, useMipmaps, WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst));
        for (int side = 0; side < 6; side++) {
            pixmap.setColor(MathUtils.random(), MathUtils.random(),MathUtils.random(), 1);
            pixmap.fill();
            cube.load(pixmap.getPixels(), pixmap.getWidth(), pixmap.getHeight(), side);
        }
        return cube; //copyTextures(cube, size, textureSides, 0);
    }

    // beware not to use .obtain() for 2 structures we need at the same time, because the memory is reused.
    public final WGPUTexelCopyTextureInfo source = new WGPUTexelCopyTextureInfo();
    public final WGPUTexelCopyTextureInfo destination = new WGPUTexelCopyTextureInfo();

    /** Copy a texture to one side (and one mip level) of a cube map
     * Note: there need to be an active encoder.
     * */
    public void copyTexture(WgCubemap cube, int side, int mipLevel, WgTexture sideTexture, int size){

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

            webgpu.encoder.copyTextureToTexture(source, destination, ext);
    }

    public Model buildUnitCube(Material material){

        ModelBuilder modelBuilder = new WgModelBuilder();
        return modelBuilder.createBox(1f, 1f, 1f, material,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
    }


    @Override
    public void dispose() {
        source.dispose();
        destination.dispose();
    }
}
