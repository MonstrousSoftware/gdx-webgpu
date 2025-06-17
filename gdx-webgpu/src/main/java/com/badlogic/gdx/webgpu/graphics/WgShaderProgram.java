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

package com.badlogic.gdx.webgpu.graphics;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.webgpu.WGPUSType;
import com.badlogic.gdx.webgpu.webgpu.WGPUShaderModuleDescriptor;
import com.badlogic.gdx.webgpu.webgpu.WGPUShaderModuleWGSLDescriptor;
import com.badlogic.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;

public class WgShaderProgram implements Disposable {
    WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
    WebGPU_JNI webGPU = gfx.getWebGPU();
    private String name;
    private Pointer shaderModule;

    public WgShaderProgram(FileHandle fileHandle) {
        this(fileHandle, "");
    }

    public WgShaderProgram(FileHandle fileHandle, String prefix) {
        String source = null;
        source = fileHandle.readString();
        compile(fileHandle.file().getName(), prefix+source);
    }

    public WgShaderProgram(String name, String shaderSource, String prefix){

        compile(name, prefix+shaderSource);
    }

    private void compile(String name, String shaderSource){
        this.name = name;

        String processedSource = Preprocessor.process(shaderSource);

        // Create Shader Module
        WGPUShaderModuleDescriptor shaderDesc = WGPUShaderModuleDescriptor.createDirect();
            shaderDesc.setLabel(name);

        WGPUShaderModuleWGSLDescriptor shaderCodeDesc = WGPUShaderModuleWGSLDescriptor.createDirect();
            shaderCodeDesc.getChain().setNext();
            shaderCodeDesc.getChain().setSType(WGPUSType.ShaderModuleWGSLDescriptor);
            shaderCodeDesc.setCode(processedSource);

            shaderDesc.getNextInChain().set(shaderCodeDesc.getPointerTo());

        shaderModule = webGPU.wgpuDeviceCreateShaderModule(gfx.getDevice().getHandle(), shaderDesc);
        if(shaderModule == null)
            throw new RuntimeException("ShaderModule: compile failed "+name);

        //System.out.println(name+": "+processedSource);
    }

    public Pointer getHandle(){
        return shaderModule;
    }

    public String getName() {
        return name;
    }

//    //public String getShaderSource() {
//        return shaderSource;
//    }

    @Override
    public void dispose(){
        webGPU.wgpuShaderModuleRelease(shaderModule);
        shaderModule = null;
    }

}
