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

package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;


/** Cache for pipelines
 *
 */

public class PipelineCache implements Disposable {
    public Array<WebGPUPipeline> pipelines;      // todo or use map? using spec hash code as key

    public PipelineCache() {
        pipelines = new Array<>();
    }

    public WebGPUPipeline findPipeline(WGPUPipelineLayout pipelineLayout, PipelineSpecification spec){
        // try to find suitable pipeline from the cache
        for(WebGPUPipeline pipeline : pipelines){
            if(pipeline.canRender(spec))
                return pipeline;
        }
        // if not found, create a new pipeline
        WebGPUPipeline pipeline = new WebGPUPipeline(pipelineLayout, spec);
        pipelines.add(pipeline);    // add to cache
        System.out.println("Added pipeline to the cache, count: "+pipelines.size);

        return pipeline;
    }

    // may be useful to hot-load shaders, forces all pipelines to be rebuilt
    public void clear(){
        dispose();
        pipelines.clear();
    }

    /** returns number of pipelines managed */
    public int size() {
        return pipelines.size;
    }

    @Override
    public void dispose() {
        for(WebGPUPipeline pipeline : pipelines)
            pipeline.dispose();
    }
}
