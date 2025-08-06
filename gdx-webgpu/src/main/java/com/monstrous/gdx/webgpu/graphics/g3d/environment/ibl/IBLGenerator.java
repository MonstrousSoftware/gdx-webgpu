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
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;


/** Utility methods to create IBL textures */
public class IBLGenerator implements Disposable {

    private final PerspectiveCamera snapCam;
    private final WebGPUContext webgpu;
    private final WgModelBatch modelBatch;

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
        snapCam.position.set(0,0,-1);
        snapCam.direction.set(0,0,1);
        snapCam.update();

//        WgTexture equiRectangular = new WgTexture(Gdx.files.internal("data/IBL/living-room.jpg"));
//        Material material = new Material(TextureAttribute.createDiffuse(equiRectangular));
//        Model cube = buildUnitCube(material);
//        ModelInstance cubeInstance = new ModelInstance(cube);
//        Renderable renderable = new Renderable();
//        cubeInstance.getRenderable(renderable);

        String shaderSource = Gdx.files.internal("shaders/modelbatchEquilateral.wgsl").readString();

       // IBLShader shader = new IBLShader(renderable, shaderSource);


        modelBatch = new WgModelBatch(new MyShaderProvider(shaderSource));
    }

    public WgCubemap buildCubeMapFromEquirectangularTexture(WgTexture equiRectangular, int textureSize){
        // Convert an equirectangular image to a cube map
        Material material = new Material(TextureAttribute.createDiffuse(equiRectangular));
        Model cube = buildUnitCube(material);
        ModelInstance cubeInstance = new ModelInstance(cube);



        //WGPUCommandEncoder encoder = new WGPUCommandEncoder();
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("A Command Encoder");
        webgpu.device.createCommandEncoder(encoderDesc, webgpu.encoder);

        WgTexture[] textureSides = new WgTexture[6];
        constructSideTextures(cubeInstance, textureSize, textureSides);
        WgCubemap environmentMap = copyTextures(textureSize, false, textureSides);

        WGPUCommandBuffer command = new WGPUCommandBuffer();
        WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
        cmdBufferDescriptor.setNextInChain(null);
        cmdBufferDescriptor.setLabel("Command buffer");
        webgpu.encoder.finish(cmdBufferDescriptor, command);
        webgpu.encoder.release();

        webgpu.queue.submit(1, command);
        command.release();
        command.dispose();

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
        new Vector3(1, 0, 0), new Vector3(-1, 0, 0),
        new Vector3(0, -1, 0), new Vector3(0, 1, 0),
        new Vector3(0,0,1), new Vector3(0, 0, -1)
    };


    private void constructSideTextures(ModelInstance instance, int size, WgTexture[] textureSides){

        for (int side = 0; side < 6; side++) {

            snapCam.direction.set(directions[side]);
            snapCam.position.set(new Vector3(directions[side]).scl(-1));
            if(side == 3)
                snapCam.up.set(0,0,-1);
            else if (side == 2)
                snapCam.up.set(0,0,1);
            else
                snapCam.up.set(0,1,0);
            snapCam.update();


            WGPUTextureUsage usage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst).or(WGPUTextureUsage.CopySrc).or(WGPUTextureUsage.RenderAttachment);
            textureSides[side] = new WgTexture("side", size, size, false, usage, WGPUTextureFormat.RGBA8Unorm, 1);
            WgTexture depthTexture = new WgTexture("depth", size, size, false, usage, WGPUTextureFormat.Depth24Plus, 1);

            // render to texture
            WebGPUContext.RenderOutputState save = webgpu.pushTargetView(textureSides[side], depthTexture);
            modelBatch.begin(snapCam);
            modelBatch.render(instance);
            modelBatch.end();
            webgpu.popTargetView(save);
        }

    }



    /** copy 6 textures (textureSides[]) into a new cube map */
    private WgCubemap copyTextures(int size, boolean useMipmaps, WgTexture[] textureSides) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.YELLOW);
        pixmap.fill();
        WgCubemap cube = new WgCubemap(size, useMipmaps, WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst));
        for (int side = 0; side < 6; side++) {
            cube.load(pixmap.getPixels(), pixmap.getWidth(), pixmap.getHeight(), side);
        }
        return copyTextures(cube, size, textureSides, 0);
    }

    // beware not to use .obtain() for 2 structures we need at the same time, because the memory is reused.
    private final WGPUTexelCopyTextureInfo source = new WGPUTexelCopyTextureInfo();
    private final WGPUTexelCopyTextureInfo destination = new WGPUTexelCopyTextureInfo();

    /** copy 6 textures to the sides of a cube map */
    private WgCubemap copyTextures(WgCubemap cube, int size, WgTexture[] textureSides, int mipLevel){
        for (int side = 0; side < 6; side++) {

            source.setTexture(textureSides[side].getHandle());
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
        return cube;
    }

    private Model buildUnitCube(Material material ){

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
