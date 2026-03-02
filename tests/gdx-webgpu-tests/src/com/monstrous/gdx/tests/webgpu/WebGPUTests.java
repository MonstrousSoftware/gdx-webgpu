/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
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
/*
 * Copyright 2010 Mario Zechner (contact@badlogicgames.com), Nathan Sweet (admin@esotericsoftware.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.monstrous.gdx.tests.webgpu;

import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.StreamUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * List of GdxTest classes. To be used by the test launchers. If you write your own test, add it in here!
 *
 * @author badlogicgames@gmail.com
 */
public class WebGPUTests {
    public static final List<Class<? extends GdxTest>> tests = new ArrayList<Class<? extends GdxTest>>(
            Arrays.<Class<? extends GdxTest>>asList(
                    // @off
                    AssetManagerTest.class, ClearScreen.class, SpriteBatchTest.class, StageTest.class, ColorTest.class,
                    FontTest.class, Scene2dTest.class, ImmediateModeRendererTest.class, ShapeRendererTest.class,
                    ShapeRenderer2DTest.class, NinePatchTest.class, ModelBatchTest.class, ModelBatchMaskingTest.class,
                    ModelBatchOutlineTest.class, SpriteHighlightTest.class, WrapAndFilterTest.class, LoadObjTest.class,
                    LoadG3DJTest.class, LoadModelTest.class, LoadGLTFTest.class, LightingTest.class,
                    InstancingTest.class, ViewportTest.class, ScissorTest.class, ASimpleGame.class,
                    ParticleEmitterTest.class, ParticleEmittersTest.class, HeightMapTest.class, FullScreenTest.class,
                    GPUTimerTest.class, FrameBufferTest.class, PostProcessing.class, SuperKoalio.class,
                    DistanceFontTest.class, DepthClearTest.class, FogTest.class, ShadowTest.class,
                    TextureAtlasTest.class, TestTextureMipMap.class, Basic3DTest.class, TestMesh.class,
                    TestMeshNoIndices.class, TestMeshBuilder.class, TestTexture.class, SpriteBatchDoubleLoop.class,
                    TransparencyTest.class, TestCompute.class, ComputeMoldSlime.class, SpriteBatchBasic.class,
                    SpriteBatchClear.class, SpriteBatchDraw.class, SpriteBatchLimit.class, SpriteBatchTextures.class,
                    SpriteBatchCount.class, EnvironmentMapTest.class, Gamma3D.class, TextureArrayTest.class,
                    SkyBoxTest.class, DuckField.class, IBL_Sliders.class, IBL_Spheres.class, IBL_GenerateOutdoor.class,
                    GLTFAnimation.class, GLTFMorphAnimation.class, GLTFSkinning.class, Scene2dTestScrollPane.class,
                    GLTFSkinningMultiple.class, GLTFSkinningShadow.class, ParticleControllerTest.class,
                    Particles3D.class, Particles3DSnow.class, Particles3DmodelInstance.class, ScreenReaderTest.class

            // @on

            ));

    static final ObjectMap<String, String> obfuscatedToOriginal = new ObjectMap();
    static final ObjectMap<String, String> originalToObfuscated = new ObjectMap();

    static {
        InputStream mappingInput = WebGPUTests.class.getResourceAsStream("/mapping.txt");
        if (mappingInput != null) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(mappingInput), 512);
                while (true) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    if (line.startsWith("    "))
                        continue;
                    String[] split = line.replace(":", "").split(" -> ");
                    String original = split[0];
                    if (original.indexOf('.') != -1)
                        original = original.substring(original.lastIndexOf('.') + 1);
                    originalToObfuscated.put(original, split[1]);
                    obfuscatedToOriginal.put(split[1], original);
                }
                reader.close();
            } catch (Exception ex) {
                System.out.println("GdxTests: Error reading mapping file: mapping.txt");
                ex.printStackTrace();
            } finally {
                StreamUtils.closeQuietly(reader);
            }
        }
    }

    public static List<String> getNames() {
        List<String> names = new ArrayList<String>(tests.size());
        for (Class clazz : tests)
            names.add(obfuscatedToOriginal.get(clazz.getSimpleName(), clazz.getSimpleName()));
        Collections.sort(names);
        return names;
    }

    public static Class<? extends GdxTest> forName(String name) {
        name = originalToObfuscated.get(name, name);
        for (Class clazz : tests)
            if (clazz.getSimpleName().equals(name))
                return clazz;
        return null;
    }

    public static GdxTest newTest(String testName) {
        testName = originalToObfuscated.get(testName, testName);
        try {
            return forName(testName).newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
