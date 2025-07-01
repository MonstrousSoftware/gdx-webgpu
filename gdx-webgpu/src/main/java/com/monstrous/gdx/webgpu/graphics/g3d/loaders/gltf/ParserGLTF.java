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

//    public static GLTF load(String filePath) {
//        int slash = filePath.lastIndexOf('/');
//        String path = filePath.substring(0, slash + 1);
//        String name = filePath.substring(slash + 1);
//        MaterialData materialData = null;
//
//        FileHandle handle = Gdx.files.internal(filePath);
//        String contents = handle.readString();
//
//        GLTF gltf = parseJSON(contents, path);
//        if(!gltf.buffers.isEmpty())
//            gltf.rawBuffer = new GLTFRawBuffer(gltf.buffers.get(0).uri);           // read .bin file, assume 1 buffer
//        return gltf;
//    }

    public static GLTF parseJSON(String jsonAsString, String path) {
        GLTF gltf = new GLTF();

        JsonValue fromJson = new JsonReader().parse(jsonAsString);

        JsonValue.JsonIterator ims = fromJson.iterator("images");
        if (ims != null) {
            while (ims.hasNext()) {
                GLTFImage im = new GLTFImage();

                JsonValue image = ims.next();

                if (image.has("uri")) {
                    String imagepath = image.get("uri").asString();
                    // texture file
                    im.uri = path + imagepath;
                    //System.out.println("image path: " + imagepath);
                } else {
                    // section in binary buffer
                    im.mimeType = image.get("mimeType").asString();
                    Long view = image.get("bufferView").asLong();
                    im.bufferView = view.intValue();
                    if(image.has("name"))
                        im.name = image.get("name").asString();
                    else
                        im.name = "anon";
                    //System.out.println("image : " + im.mimeType+" "+im.name);
                }

                gltf.images.add(im);
            }
        }

        JsonValue.JsonIterator sampls = fromJson.iterator("images");
        if(sampls != null) {
            while (sampls.hasNext()) {
                GLTFSampler sampler = new GLTFSampler();

                JsonValue smpl = sampls.next();
                if(smpl.has("name"))
                    sampler.name = smpl.get("name").asString();
                if(smpl.has("wrapS"))
                    sampler.wrapS = smpl.get("wrapS").asInt();
                else
                    sampler.wrapS = 10497;
                if(smpl.has("wrapT"))
                    sampler.wrapT = smpl.get("wrapT").asInt();
                else
                    sampler.wrapT = 10497;

                gltf.samplers.add(sampler);
            }
        }

        JsonValue.JsonIterator textures = fromJson.iterator("textures");
        if(textures != null) {
            while (textures.hasNext()) {
                GLTFTexture texture = new GLTFTexture();

                JsonValue tex = textures.next();
                if(tex.has("name"))
                    texture.name = tex.get("name").asString();
                if(tex.has("source"))
                    texture.source = tex.get("source").asInt();
                if(tex.has("sampler"))
                    texture.sampler = tex.get("sampler").asInt();
                gltf.textures.add(texture);
            }
        }

        JsonValue.JsonIterator materials = fromJson.iterator("materials");
        if(materials != null) {
            while (materials.hasNext()) {
                GLTFMaterialPBR pbr = new GLTFMaterialPBR();
                GLTFMaterial material = new GLTFMaterial();
                material.pbrMetallicRoughness = pbr;

                JsonValue mat = materials.next();
                if(mat.has("name"))
                    material.name = mat.get("name").asString();
                JsonValue pbrMR = mat.get("pbrMetallicRoughness");
                if(pbrMR.has("baseColorTexture")) {
                    JsonValue base = pbrMR.get("baseColorTexture");
                    pbr.baseColorTexture = base.get("index").asInt();
                }
                if(pbrMR.has("baseColorFactor")) {
                    float[] bc = pbrMR.get("baseColorFactor").asFloatArray();
                    pbr.baseColorFactor = new Color(bc[0], bc[1], bc[2], bc[3]);
                } else
                    pbr.baseColorFactor = Color.WHITE;

                if(pbrMR.has("roughnessFactor")) {
                    pbr.roughnessFactor = pbrMR.get("roughnessFactor").asFloat();
                }
                if(pbrMR.has("metallicFactor")) {
                    pbr.metallicFactor = pbrMR.get("metallicFactor").asFloat();
                }
                if(pbrMR.has("metallicRoughnessTexture")) {
                    JsonValue mrTex = pbrMR.get("metallicRoughnessTexture");
                    pbr.metallicRoughnessTexture = mrTex.get("index").asInt();
                    //mat.getInt("metallicRoughnessTexture", -1);
                }
                if(mat.has("normalTexture")){
                    JsonValue tex = mat.get("normalTexture");
                    material.normalTexture = tex.get("index").asInt();
                }
                if(mat.has("emissiveTexture")){
                    JsonValue tex = mat.get("emissiveTexture");
                    material.emissiveTexture = tex.get("index").asInt();
                }
                if(mat.has("occlusionTexture")){
                    JsonValue tex = mat.get("occlusionTexture");
                    material.occlusionTexture = tex.get("index").asInt();
                }

                gltf.materials.add(material);
            }
        }

        JsonValue.JsonIterator meshes = fromJson.iterator("meshes");
        if(meshes != null) {
            while (meshes.hasNext()) {
                GLTFMesh mesh = new GLTFMesh();
                JsonValue m = meshes.next();
                if(m.has("name"))
                    mesh.name = m.get("name").asString();

                JsonValue.JsonIterator primitives = m.iterator("primitives");
                if(primitives != null) {
                    while (primitives.hasNext()) {
                        GLTFPrimitive primitive = new GLTFPrimitive();
                        JsonValue p = primitives.next();

                        primitive.mode = p.getInt("mode", 4);
                        primitive.indices = p.getInt("indices", -1);
                        primitive.material = p.getInt("material", -1);

                        JsonValue attribs = p.get("attributes");
                        JsonValue attrib = attribs.child;
                        while(attrib != null){
                            System.out.println(attrib.name);
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
        if(buffers != null) {
            while (buffers.hasNext()) {
                GLTFBuffer buffer = new GLTFBuffer();
                JsonValue buf = buffers.next();
                if (buf.has("name"))
                    buffer.name = buf.get("name").asString();
                if (buf.has("uri")) {
                    String uri = buf.getString("uri");
                    if(!uri.startsWith("data:"))
                        uri = path + uri;
                    buffer.uri = uri;
                }
                //buffer.uri = path + buf.getString("uri");
                buffer.byteLength = buf.getInt("byteLength");

                gltf.buffers.add(buffer);
            }
        }

        JsonValue.JsonIterator bufferViews = fromJson.iterator("bufferViews");
        if(bufferViews != null) {
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
        if(accessors != null) {
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
        if(nodes != null) {
            while (nodes.hasNext()) {
                GLTFNode node = new GLTFNode();
                JsonValue nd = nodes.next();
                if (nd.has("name"))
                    node.name = nd.get("name").asString();
                node.camera = nd.getInt("camera", -1);
                node.skin = nd.getInt("skin", -1);
                node.mesh = nd.getInt("mesh", -1);

                if(nd.has("translation")){
                    float[] tr = nd.get("translation").asFloatArray();
                    node.translation = new Vector3(tr);
                }
                if(nd.has("scale")){
                    float[] tr = nd.get("scale").asFloatArray();
                    node.scale = new Vector3(tr);
                }
                if(nd.has("rotation")){
                    float[] tr = nd.get("rotation").asFloatArray();
                    node.rotation = new Quaternion(tr[0], tr[1], tr[2], tr[3]);
                }
                if(nd.has("matrix")){
                    float[] tr = nd.get("matrix").asFloatArray();
                    node.matrix = new Matrix4(tr);
                }
                if(nd.has("children")){
                    int[] ch = nd.get("children").asIntArray();
                    for(int i = 0; i < ch.length; i++)
                        node.children.add(ch[i]);
                }

                gltf.nodes.add(node);
            }
        }

        JsonValue.JsonIterator scenes = fromJson.iterator("scenes");
        if(scenes != null) {
            while (scenes.hasNext()) {
                GLTFScene scene = new GLTFScene();
                JsonValue sc = scenes.next();
                if (sc.has("name"))
                    scene.name = sc.get("name").asString();
                if(sc.has("nodes")){
                    int[] ch = sc.get("nodes").asIntArray();
                    for(int i = 0; i < ch.length; i++)
                        scene.nodes.add(ch[i]);
                }

                gltf.scenes.add(scene);
            }
        }
        gltf.scene = fromJson.getInt("scene", 0);


// todo


//        JSONArray skins = (JSONArray)file.get("skins");
//        if(skins != null) {
//            System.out.println("skins: " + skins.size());
//            for (int i = 0; i < skins.size(); i++) {
//                GLTFSkin skin = new GLTFSkin();
//
//                JSONObject sk = (JSONObject) skins.get(i);
//                skin.name = (String) sk.get("name");
//                System.out.println("skin: " + skin.name);
//
//                Number ibm = (Number)sk.get("inverseBindMatrices");
//                skin.inverseBindMatrices = ibm.intValue();
//
//                Number skel = (Number)sk.get("skeleton");
//                skin.skeleton = skel != null ? skel.intValue() : -1;
//
//
//                JSONArray joints = (JSONArray) sk.get("joints");
//                for (int j = 0; j < joints.size(); j++) {
//                    Number nr = (Number) joints.get(j);
//                    skin.joints.add(nr.intValue());
//                }
//
//                gltf.skins.add(skin);
//            }
//        }
//
//        JSONArray animations = (JSONArray)file.get("animations");
//        if(animations != null) {
//            System.out.println("animations: " + animations.size());
//            for (int i = 0; i < animations.size(); i++) {
//                GLTFAnimation animation = new GLTFAnimation();
//
//                JSONObject an = (JSONObject) animations.get(i);
//                animation.name = (String) an.get("name");
//                System.out.println("animations: " + animation.name);
//
//                JSONArray chs = (JSONArray) an.get("channels");
//                for (int j = 0; j < chs.size(); j++) {
//                    GLTFAnimationChannel channel = new GLTFAnimationChannel();
//                    JSONObject ch = (JSONObject) chs.get(j);
//                    Long sam = (Long) ch.get("sampler");
//                    channel.sampler = sam.intValue();
//                    System.out.println("sampler: " + channel.sampler);
//
//
//                    JSONObject tgt = (JSONObject) ch.get("target");
//                    Long node = (Long) tgt.get("node");
//                    channel.node = node.intValue();
//                    channel.path = (String) tgt.get("path");
//
//                    animation.channels.add(channel);
//                }
//                JSONArray smplrs = (JSONArray) an.get("samplers");
//                for (int j = 0; j < smplrs.size(); j++) {
//                    GLTFAnimationSampler sampler = new GLTFAnimationSampler();
//                    JSONObject sm = (JSONObject) smplrs.get(j);
//                    Long in = (Long) sm.get("input");
//                    sampler.input = in.intValue();
//                    Long out = (Long) sm.get("output");
//                    sampler.output = out.intValue();
//                    sampler.interpolation = (String) sm.get("interpolation");
//
//                    animation.samplers.add(sampler);
//                }
//                gltf.animations.add(animation);
//            }
//        }
//

        return gltf;
    }

}
