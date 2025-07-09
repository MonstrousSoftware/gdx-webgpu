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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUSType;
import com.github.xpenatan.webgpu.WGPUShaderModule;
import com.github.xpenatan.webgpu.WGPUShaderModuleDescriptor;
import com.github.xpenatan.webgpu.WGPUShaderSourceWGSL;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;


public class WgShaderProgram implements Disposable {

    private final WgGraphics gfx = (WgGraphics) Gdx.graphics;
    private final WebGPUContext webgpu = gfx.getContext();
    private String name;
    private WGPUShaderModule shaderModule;

    public WgShaderProgram(FileHandle fileHandle) {
        this(fileHandle, "");
    }

    public WgShaderProgram(FileHandle fileHandle, String prefix) {
        String source = fileHandle.readString();
        compile(fileHandle.file().getName(), prefix+source);
    }

    public WgShaderProgram(String name, String shaderSource, String prefix){
        compile(name, prefix+shaderSource);
    }

    private void compile(String name, String shaderSource){
        this.name = name;

        String processedSource = Preprocessor.process(shaderSource);

        // Create Shader Module
        WGPUShaderModuleDescriptor shaderDesc = WGPUShaderModuleDescriptor.obtain();
        shaderDesc.setLabel(name);

        WGPUShaderSourceWGSL shaderCodeDesc = WGPUShaderSourceWGSL.obtain();
        shaderCodeDesc.getChain().setNext(null);
        shaderCodeDesc.getChain().setSType(WGPUSType.ShaderSourceWGSL);
        shaderCodeDesc.setCode(processedSource);

        shaderDesc.setNextInChain(shaderCodeDesc.getChain());
        shaderModule = WGPUShaderModule.obtain();
        webgpu.device.createShaderModule(shaderDesc, shaderModule);
        // todo how to detect compile errors?
//        if(shaderModule == null)
//            throw new GdxRuntimeException("WgShaderProgram: compile failed "+name);

        //System.out.println(name+": "+processedSource);
    }

    public WGPUShaderModule getShaderModule() {
        return shaderModule;
    }

    public String getName() {
        return name;
    }

    @Override
    public void dispose(){
        shaderModule.release();
        shaderModule.dispose();
        shaderModule = null;
    }

}
