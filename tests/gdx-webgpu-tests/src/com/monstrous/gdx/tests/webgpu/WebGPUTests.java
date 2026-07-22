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
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.ReflectionException;

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
    /** Stable public names mapped to classes; the string identifiers survive compiler obfuscation. */
    private static final ObjectMap<String, Class<? extends GdxTest>> testsByName =
            new ObjectMap<String, Class<? extends GdxTest>>();

    public static final List<Class<? extends GdxTest>> tests = new ArrayList<Class<? extends GdxTest>>(
            Arrays.<Class<? extends GdxTest>>asList(
                    // @off
                    register("AssetManagerTest", AssetManagerTest.class),
                    register("ClearScreen", ClearScreen.class),
                    register("SpriteBatchTest", SpriteBatchTest.class),
                    register("StageTest", StageTest.class),
                    register("ColorTest", ColorTest.class),
                    register("FontTest", FontTest.class),
                    register("Scene2dTest", Scene2dTest.class),
                    register("ImmediateModeRendererTest", ImmediateModeRendererTest.class),
                    register("ShapeRendererTest", ShapeRendererTest.class),
                    register("ShapeRenderer2DTest", ShapeRenderer2DTest.class),
                    register("NinePatchTest", NinePatchTest.class),
                    register("ModelBatchTest", ModelBatchTest.class),
                    register("ModelBatchMaskingTest", ModelBatchMaskingTest.class),
                    register("ModelBatchOutlineTest", ModelBatchOutlineTest.class),
                    register("SpriteHighlightTest", SpriteHighlightTest.class),
                    register("WrapAndFilterTest", WrapAndFilterTest.class),
                    register("LoadObjTest", LoadObjTest.class),
                    register("LoadG3DJTest", LoadG3DJTest.class),
                    register("LoadModelTest", LoadModelTest.class),
                    register("LoadGLTFTest", LoadGLTFTest.class),
                    register("LightingTest", LightingTest.class),
                    register("InstancingTest", InstancingTest.class),
                    register("ViewportTest", ViewportTest.class),
                    register("ScissorTest", ScissorTest.class),
                    register("ASimpleGame", ASimpleGame.class),
                    register("ParticleEmitterTest", ParticleEmitterTest.class),
                    register("ParticleEmittersTest", ParticleEmittersTest.class),
                    register("HeightMapTest", HeightMapTest.class),
                    register("FullScreenTest", FullScreenTest.class),
                    register("GPUTimerTest", GPUTimerTest.class),
                    register("FrameBufferTest", FrameBufferTest.class),
                    register("PostProcessing", PostProcessing.class),
                    register("SuperKoalio", SuperKoalio.class),
                    register("DistanceFontTest", DistanceFontTest.class),
                    register("DepthClearTest", DepthClearTest.class),
                    register("FogTest", FogTest.class),
                    register("ShadowTest", ShadowTest.class),
                    register("CSMShadowTest", CSMShadowTest.class),
                    register("TextureAtlasTest", TextureAtlasTest.class),
                    register("TestTextureMipMap", TestTextureMipMap.class),
                    register("Basic3DTest", Basic3DTest.class),
                    register("TestMesh", TestMesh.class),
                    register("TestMeshNoIndices", TestMeshNoIndices.class),
                    register("TestMeshBuilder", TestMeshBuilder.class),
                    register("TestTexture", TestTexture.class),
                    register("SpriteBatchDoubleLoop", SpriteBatchDoubleLoop.class),
                    register("TransparencyTest", TransparencyTest.class),
                    register("TestCompute", TestCompute.class),
                    register("ComputeMoldSlime", ComputeMoldSlime.class),
                    register("SpriteBatchBasic", SpriteBatchBasic.class),
                    register("SpriteBatchClear", SpriteBatchClear.class),
                    register("SpriteBatchDraw", SpriteBatchDraw.class),
                    register("SpriteBatchTextures", SpriteBatchTextures.class),
                    register("SpriteBatchCount", SpriteBatchCount.class),
                    register("EnvironmentMapTest", EnvironmentMapTest.class),
                    register("Gamma3D", Gamma3D.class),
                    register("TextureArrayTest", TextureArrayTest.class),
                    register("SkyBoxTest", SkyBoxTest.class),
                    register("DuckField", DuckField.class),
                    register("IBL_Sliders", IBL_Sliders.class),
                    register("IBL_Spheres", IBL_Spheres.class),
                    register("IBL_GenerateOutdoor", IBL_GenerateOutdoor.class),
                    register("GLTFAnimation", GLTFAnimation.class),
                    register("GLTFMorphAnimation", GLTFMorphAnimation.class),
                    register("GLTFSkinning", GLTFSkinning.class),
                    register("Scene2dTestScrollPane", Scene2dTestScrollPane.class),
                    register("GLTFSkinningMultiple", GLTFSkinningMultiple.class),
                    register("GLTFSkinningShadow", GLTFSkinningShadow.class),
                    register("ParticleControllerTest", ParticleControllerTest.class),
                    register("Particles3D", Particles3D.class),
                    register("Particles3DSnow", Particles3DSnow.class),
                    register("Particles3DmodelInstance", Particles3DmodelInstance.class),
                    register("ScreenReaderTest", ScreenReaderTest.class),
                    register("MRTTest2D", MRTTest2D.class),
                    register("MRTTest3D", MRTTest3D.class),
                    register("Picking3DTest", Picking3DTest.class),
                    register("EdgeDetectionOutlineTest", EdgeDetectionOutlineTest.class),
                    register("UITest", UITest.class),
                    register("DebugLinesDepthTest", DebugLinesDepthTest.class),
                    register("SpriteBatchUniforms", SpriteBatchUniforms.class),
                    register("SpriteBatchExtraVertAttrib", SpriteBatchExtraVertAttrib.class),
                    register("SpriteBatchWideIndices", SpriteBatchWideIndices.class),
                    register("DynamicTexture", DynamicTexture.class),
                    register("FogOfWar2DTest", FogOfWar2DTest.class),
                    register("FogOfWar3DTest", FogOfWar3DTest.class),
                    register("TextureGreyscale", TextureGreyscale.class)

            // @on

            ));

    private static Class<? extends GdxTest> register(String name, Class<? extends GdxTest> testClass) {
        if (testsByName.containsKey(name)) {
            throw new IllegalArgumentException("Duplicate test name: " + name);
        }
        testsByName.put(name, testClass);
        return testClass;
    }

    public static List<String> getNames() {
        List<String> names = new ArrayList<String>(testsByName.size);
        for (ObjectMap.Entry<String, Class<? extends GdxTest>> test : testsByName)
            names.add(test.key);
        Collections.sort(names);
        return names;
    }

    public static Class<? extends GdxTest> forName(String name) {
        return testsByName.get(name);
    }

    public static GdxTest newTest(String testName) {
        Class<? extends GdxTest> testClass = forName(testName);
        if (testClass == null) return null;
        try {
            return ClassReflection.newInstance(testClass);
        } catch (ReflectionException e) {
            e.printStackTrace();
        }
        return null;
    }
}
