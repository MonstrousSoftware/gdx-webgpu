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
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;

public class ShaderPrefix {
    private static final StringBuffer sb = new StringBuffer();

    /**
     * create a shader prefix depending on the defined attributes which allows to skip some shader code via conditional
     * compilation. The prefix consists of a number of #define statements.
     */
    // todo add material attributes
    public static String buildPrefix(VertexAttributes vertexAttributes, Environment environment, int maxDirLights,
            int maxPointLights, boolean usePBR) {
        sb.setLength(0);

        if (vertexAttributes != null) {
            long mask = vertexAttributes.getMask();
            if ((mask & VertexAttributes.Usage.TextureCoordinates) != 0) {
                sb.append("#define TEXTURE_COORDINATE\n");
            }
            if ((mask & VertexAttributes.Usage.ColorUnpacked) != 0) {
                sb.append("#define COLOR\n");
            }
            if ((mask & VertexAttributes.Usage.ColorPacked) != 0) {
                sb.append("#define COLOR\n");
            }
            if ((mask & VertexAttributes.Usage.Normal) != 0) {
                sb.append("#define NORMAL\n");
            }
            if ((mask & VertexAttributes.Usage.Tangent) != 0) { // this is taken as indication that a normal map is used
                sb.append("#define NORMAL_MAP\n");
            }
            if ((mask & VertexAttributes.Usage.BoneWeight) != 0) {
                sb.append("#define SKIN\n");
            }
            // todo this is a very crude test
            if ((mask & VertexAttributes.Usage.Normal) != 0 && environment != null) {
                // only perform lighting calculations if we have vertex normals and an environment
                sb.append("#define LIGHTING\n");
            }
        }
        if (environment != null) {
            if ((environment.getMask() & ColorAttribute.Fog) != 0) {
                sb.append("#define FOG\n");
            }
            if ((environment.getMask() & WgCubemapAttribute.EnvironmentMap) != 0) {
                sb.append("#define ENVIRONMENT_MAP\n");
            }
            if (environment.shadowMap != null) {
                sb.append("#define SHADOW_MAP\n");
            }
            if (usePBR && (environment.getMask() & WgCubemapAttribute.DiffuseCubeMap) != 0) {
                sb.append("#define USE_IBL\n");
            }
        }

        // todo select this based on material
        if (usePBR)
            sb.append("#define PBR\n");

        if (maxDirLights > 0)
            sb.append("#define MAX_DIR_LIGHTS ").append(maxDirLights).append('\n');
        if (maxPointLights > 0)
            sb.append("#define MAX_POINT_LIGHTS ").append(maxPointLights).append('\n');

        // SPECULAR is ignored if PBR is active
        // sb.append("#define SPECULAR\n");

        // if (environment != null && !environment.depthPass && environment.renderShadows) {
        // sb.append("#define SHADOWS\n");
        // }

        // Add gamma correction if surface format is not Srgb
        if (!hasLinearOutput()) {
            sb.append("#define GAMMA_CORRECTION\n");
        }

        // System.out.println("Prefix: "+sb.toString());
        return sb.toString();
    }

    public static boolean hasLinearOutput() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        return webgpu.hasLinearOutput();
    }
}
