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

package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

// JSON parser of the GLTF file format into a set of GLTF class objects

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

public class ParserGLTF {

    /**
     * Creates a GLTF object from a JSON file. Uses path to find additional resources.
     *
     */
    public static GLTF parseJSON(String json, String path) {
        GLTF gltf = new GLTF();

        JsonValue fromJson = new JsonReader().parse(json);

        JsonValue.JsonIterator ims = fromJson.iterator("images");
        if (ims != null) {
            while (ims.hasNext()) {
                GLTFImage im = new GLTFImage();

                JsonValue image = ims.next();

                if (image.has("uri")) {
                    String imagepath = image.get("uri").asString();
                    // texture file
                    im.uri = path + imagepath;
                    // System.out.println("image path: " + imagepath);
                } else {
                    // section in binary buffer
                    im.mimeType = image.get("mimeType").asString();
                    Long view = image.get("bufferView").asLong();
                    im.bufferView = view.intValue();
                    if (image.has("name"))
                        im.name = image.get("name").asString();
                    else
                        im.name = "anon";
                    // System.out.println("image : " + im.mimeType+" "+im.name);
                }

                gltf.images.add(im);
            }
        }

        JsonValue.JsonIterator sampls = fromJson.iterator("images");
        if (sampls != null) {
            while (sampls.hasNext()) {
                GLTFSampler sampler = new GLTFSampler();

                JsonValue smpl = sampls.next();
                if (smpl.has("name"))
                    sampler.name = smpl.get("name").asString();
                if (smpl.has("wrapS"))
                    sampler.wrapS = smpl.get("wrapS").asInt();
                else
                    sampler.wrapS = 10497;
                if (smpl.has("wrapT"))
                    sampler.wrapT = smpl.get("wrapT").asInt();
                else
                    sampler.wrapT = 10497;

                gltf.samplers.add(sampler);
            }
        }

        JsonValue.JsonIterator textures = fromJson.iterator("textures");
        if (textures != null) {
            while (textures.hasNext()) {
                GLTFTexture texture = new GLTFTexture();

                JsonValue tex = textures.next();
                if (tex.has("name"))
                    texture.name = tex.get("name").asString();
                if (tex.has("source"))
                    texture.source = tex.get("source").asInt();
                if (tex.has("sampler"))
                    texture.sampler = tex.get("sampler").asInt();
                gltf.textures.add(texture);
            }
        }

        JsonValue.JsonIterator materials = fromJson.iterator("materials");
        if (materials != null) {
            while (materials.hasNext()) {
                GLTFMaterialPBR pbr = new GLTFMaterialPBR();
                GLTFMaterial material = new GLTFMaterial();
                material.pbrMetallicRoughness = pbr;

                JsonValue mat = materials.next();
                if (mat.has("name"))
                    material.name = mat.get("name").asString();
                JsonValue pbrMR = mat.get("pbrMetallicRoughness");
                if (pbrMR.has("baseColorTexture")) {
                    JsonValue base = pbrMR.get("baseColorTexture");
                    pbr.baseColorTexture = base.get("index").asInt();
                }
                if (pbrMR.has("baseColorFactor")) {
                    float[] bc = pbrMR.get("baseColorFactor").asFloatArray();
                    pbr.baseColorFactor = new Color(bc[0], bc[1], bc[2], bc[3]);
                } else
                    pbr.baseColorFactor = Color.WHITE;

                if (pbrMR.has("roughnessFactor")) {
                    pbr.roughnessFactor = pbrMR.get("roughnessFactor").asFloat();
                }
                if (pbrMR.has("metallicFactor")) {
                    pbr.metallicFactor = pbrMR.get("metallicFactor").asFloat();
                }
                if (pbrMR.has("metallicRoughnessTexture")) {
                    JsonValue mrTex = pbrMR.get("metallicRoughnessTexture");
                    pbr.metallicRoughnessTexture = mrTex.get("index").asInt();
                    // mat.getInt("metallicRoughnessTexture", -1);
                }
                if (mat.has("normalTexture")) {
                    JsonValue tex = mat.get("normalTexture");
                    material.normalTexture = tex.get("index").asInt();
                }
                if (mat.has("emissiveTexture")) {
                    JsonValue tex = mat.get("emissiveTexture");
                    material.emissiveTexture = tex.get("index").asInt();
                }
                if (mat.has("occlusionTexture")) {
                    JsonValue tex = mat.get("occlusionTexture");
                    material.occlusionTexture = tex.get("index").asInt();
                }

                gltf.materials.add(material);
            }
        }

        JsonValue.JsonIterator meshes = fromJson.iterator("meshes");
        if (meshes != null) {
            while (meshes.hasNext()) {
                GLTFMesh mesh = new GLTFMesh();
                JsonValue m = meshes.next();
                if (m.has("name"))
                    mesh.name = m.get("name").asString();

                JsonValue.JsonIterator primitives = m.iterator("primitives");
                if (primitives != null) {
                    while (primitives.hasNext()) {
                        GLTFPrimitive primitive = new GLTFPrimitive();
                        JsonValue p = primitives.next();

                        primitive.mode = p.getInt("mode", 4);
                        primitive.indices = p.getInt("indices", -1);
                        primitive.material = p.getInt("material", -1);

                        JsonValue attribs = p.get("attributes");
                        JsonValue attrib = attribs.child;
                        while (attrib != null) {
                            // System.out.println("GLTF attribute: " + attrib.name);
                            GLTFAttribute attribute = new GLTFAttribute(attrib.name, attrib.asInt());
                            primitive.attributes.add(attribute);
                            attrib = attrib.next;
                        }
                        mesh.primitives.add(primitive);
                    }
                }
                gltf.meshes.add(mesh);
            }
        }

        JsonValue.JsonIterator buffers = fromJson.iterator("buffers");
        if (buffers != null) {
            while (buffers.hasNext()) {
                GLTFBuffer buffer = new GLTFBuffer();
                JsonValue buf = buffers.next();
                if (buf.has("name"))
                    buffer.name = buf.get("name").asString();
                if (buf.has("uri")) {
                    String uri = buf.getString("uri");
                    if (!uri.startsWith("data:"))
                        uri = path + uri;
                    buffer.uri = uri;
                }
                // buffer.uri = path + buf.getString("uri");
                buffer.byteLength = buf.getInt("byteLength");

                gltf.buffers.add(buffer);
            }
        }

        JsonValue.JsonIterator bufferViews = fromJson.iterator("bufferViews");
        if (bufferViews != null) {
            while (bufferViews.hasNext()) {
                GLTFBufferView bufferView = new GLTFBufferView();
                JsonValue bufView = bufferViews.next();
                if (bufView.has("name"))
                    bufferView.name = bufView.get("name").asString();
                bufferView.buffer = bufView.getInt("buffer", 0);
                bufferView.byteOffset = bufView.getInt("byteOffset", 0);
                bufferView.byteStride = bufView.getInt("byteStride", 0);
                bufferView.byteLength = bufView.getInt("byteLength", 0);
                bufferView.target = bufView.getInt("target", 0);

                gltf.bufferViews.add(bufferView);
            }
        }

        JsonValue.JsonIterator accessors = fromJson.iterator("accessors");
        if (accessors != null) {
            while (accessors.hasNext()) {
                GLTFAccessor accessor = new GLTFAccessor();
                JsonValue ac = accessors.next();
                if (ac.has("name"))
                    accessor.name = ac.get("name").asString();
                accessor.bufferView = ac.getInt("bufferView", 0);
                accessor.byteOffset = ac.getInt("byteOffset", 0);
                accessor.componentType = ac.getInt("componentType", 0);
                accessor.count = ac.getInt("count", 0);
                accessor.type = ac.getString("type");
                accessor.normalized = (ac.getString("normalized", "false").contentEquals("true"));

                gltf.accessors.add(accessor);
            }
        }

        JsonValue.JsonIterator nodes = fromJson.iterator("nodes");
        if (nodes != null) {
            while (nodes.hasNext()) {
                GLTFNode node = new GLTFNode();
                JsonValue nd = nodes.next();
                if (nd.has("name"))
                    node.name = nd.get("name").asString();
                node.camera = nd.getInt("camera", -1);
                node.skin = nd.getInt("skin", -1);
                node.mesh = nd.getInt("mesh", -1);

                if (nd.has("translation")) {
                    float[] tr = nd.get("translation").asFloatArray();
                    node.translation = new Vector3(tr);
                }
                if (nd.has("scale")) {
                    float[] tr = nd.get("scale").asFloatArray();
                    node.scale = new Vector3(tr);
                }
                if (nd.has("rotation")) {
                    float[] tr = nd.get("rotation").asFloatArray();
                    node.rotation = new Quaternion(tr[0], tr[1], tr[2], tr[3]);
                }
                if (nd.has("matrix")) {
                    float[] tr = nd.get("matrix").asFloatArray();
                    node.matrix = new Matrix4(tr);
                }
                if (nd.has("children")) {
                    int[] ch = nd.get("children").asIntArray();
                    for (int i = 0; i < ch.length; i++)
                        node.children.add(ch[i]);
                }

                gltf.nodes.add(node);
            }
        }

        JsonValue.JsonIterator scenes = fromJson.iterator("scenes");
        if (scenes != null) {
            while (scenes.hasNext()) {
                GLTFScene scene = new GLTFScene();
                JsonValue sc = scenes.next();
                if (sc.has("name"))
                    scene.name = sc.get("name").asString();
                if (sc.has("nodes")) {
                    int[] ch = sc.get("nodes").asIntArray();
                    for (int i = 0; i < ch.length; i++)
                        scene.nodes.add(ch[i]);
                }

                gltf.scenes.add(scene);
            }
        }
        gltf.scene = fromJson.getInt("scene", 0);

        JsonValue.JsonIterator skins = fromJson.iterator("skins");
        if (skins != null) {
            while (skins.hasNext()) {
                GLTFSkin skin = new GLTFSkin();
                JsonValue sk = skins.next();
                if (sk.has("name"))
                    skin.name = sk.get("name").asString();

                if (sk.has("inverseBindMatrices"))
                    skin.inverseBindMatrices = sk.get("inverseBindMatrices").asInt();

                if (sk.has("skeleton"))
                    skin.skeleton = sk.get("skeleton").asInt();

                if (sk.has("joints")) {
                    int[] jnt = sk.get("joints").asIntArray();
                    for (int i = 0; i < jnt.length; i++)
                        skin.joints.add(jnt[i]);
                }

                gltf.skins.add(skin);
            }
        }

        JsonValue.JsonIterator animations = fromJson.iterator("animations");
        if (animations != null) {
            while (animations.hasNext()) {
                GLTFAnimation animation = new GLTFAnimation();
                JsonValue an = animations.next();
                if (an.has("name")) {
                    animation.name = an.get("name").asString();
                    // System.out.println("animation name: " + animation.name);
                }
                JsonValue.JsonIterator channels = an.iterator("channels");
                if (channels != null) {
                    while (channels.hasNext()) {
                        GLTFAnimationChannel channel = new GLTFAnimationChannel();
                        JsonValue ch = channels.next();

                        channel.sampler = ch.get("sampler").asInt();
                        // System.out.println("sampler: " + channel.sampler);

                        JsonValue tgt = ch.get("target");
                        channel.node = tgt.get("node").asInt();
                        channel.path = tgt.get("path").asString();
                        // System.out.println("target: " + channel.node+" "+channel.path);

                        animation.channels.add(channel);
                    }
                }

                JsonValue.JsonIterator samplers = an.iterator("samplers");
                if (samplers != null) {
                    while (samplers.hasNext()) {
                        GLTFAnimationSampler sampler = new GLTFAnimationSampler();
                        JsonValue sam = samplers.next();

                        sampler.input = sam.get("input").asInt();
                        sampler.output = sam.get("output").asInt();
                        if (sam.has("interpolation"))
                            sampler.interpolation = sam.get("interpolation").asString();
                        else
                            sampler.interpolation = "LINEAR";

                        // System.out.println("sampler: " + sampler.input+" "+sampler.interpolation+" "+sampler.output);

                        animation.samplers.add(sampler);
                    }
                }

                gltf.animations.add(animation);
            }
        }

        return gltf;
    }

}
