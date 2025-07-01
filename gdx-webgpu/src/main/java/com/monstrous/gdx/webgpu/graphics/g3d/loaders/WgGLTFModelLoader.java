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

package com.monstrous.gdx.webgpu.graphics.g3d.loaders;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.*;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf.*;
import com.monstrous.gdx.webgpu.graphics.g3d.model.PBRModelTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgModelMeshPart;
import org.jetbrains.annotations.NotNull;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Loader for gltf model format.
 *
 */


public class WgGLTFModelLoader extends WgModelLoader<WgModelLoader.ModelParameters> {
    private final Map<GLTFPrimitive, String> meshMap = new HashMap<>();

	public WgGLTFModelLoader(final BaseJsonReader reader) {
		this(reader, null);
	}

	public WgGLTFModelLoader(BaseJsonReader reader, FileHandleResolver resolver) {
		super(resolver);
	}

	@Override
	public ModelData loadModelData (FileHandle fileHandle, ModelParameters parameters) {
		return parseModel(fileHandle);
	}

	public ModelData parseModel (@NotNull FileHandle handle) {
        // create path to find additional resources
        int lastSlashPos = handle.path().lastIndexOf('/');
        String path = handle.path().substring(0, lastSlashPos + 1);
        String json = handle.readString();

        /* Read file into a GLTF class hierarchy. */
        GLTF gltf = GLTFParser.parseJSON(json, path);
        gltf.rawBuffer = new GLTFRawBuffer(gltf.buffers.get(0).uri);           // read .bin file, assume 1 buffer

        /* Then convert it to ModelData. */
		return load(gltf);
	}

    /** load GLTF contents into a ModelData object */
    public ModelData load( GLTF gltf ){
        ModelData modelData = new ModelData();
        meshMap.clear();

        loadMaterials(modelData, gltf);
        loadMeshes(modelData, gltf);
        loadNodes(modelData, gltf);
        loadScenes(modelData, gltf);

        // todo give priority to the default scene
        GLTFScene scene = gltf.scenes.get(gltf.scene);

//        for(GLTFSkin skin : gltf.skins) {
//            // skin.inverseBindMatrices points to an accessor to get mat4 data
//            GLTFAccessor ibmAccessor = gltf.accessors.get(skin.inverseBindMatrices);
//            if(ibmAccessor.componentType != GLTF.FLOAT32 || !ibmAccessor.type.contentEquals("MAT4"))
//                throw new RuntimeException("GLTF: Expected inverseBindMatrices of MAT4(float32)");
//            GLTFBufferView ibmView = gltf.bufferViews.get(ibmAccessor.bufferView);
//
//            if(ibmView.buffer != 0)
//                throw new RuntimeException("GLTF can only support buffer 0");
//            gltf.rawBuffer.byteBuffer.position(ibmAccessor.byteOffset+ ibmView.byteOffset);
//            FloatBuffer matBuf = gltf.rawBuffer.byteBuffer.asFloatBuffer();
//
//            float[] floats = new float[16];
//            for(int i = 0; i < ibmAccessor.count; i++) {    // read each matrix
//                matBuf.get(floats, 0,  16); // get next 16 floats from the float buffer
//                Matrix4 mat = new Matrix4();
//                mat.set(floats);
//                model.inverseBoneTransforms.add(mat);
//            }
//
//            //skin.joints
//            for(int i = 0; i < skin.joints.size(); i++){
//                Node jointNode = nodes.get(skin.joints.get(i));
//                model.joints.add(jointNode);
//            }
//        }
//
//        for(GLTFAnimation gltfAnim : gltf.animations ){
//            Animation animation = new Animation();
//            animation.name = gltfAnim.name;
//            float maxDuration = 0f;
//            for(GLTFAnimationChannel gltfChannel : gltfAnim.channels){
//                NodeAnimation nodeAnimation = new NodeAnimation();
//                nodeAnimation.node = nodes.get(gltfChannel.node);
//
//                int numComponents = 3;
//                if(gltfChannel.path.contentEquals("rotation"))
//                    numComponents = 4; // 4 floats per quaternion
//
//                GLTFAnimationSampler sampler = gltfAnim.samplers.get(gltfChannel.sampler);
//                GLTFAccessor inAccessor = gltf.accessors.get(sampler.input);
//                GLTFAccessor outAccessor = gltf.accessors.get(sampler.output);
//                // ignore interpolation, we only do linear
//
//                GLTFBufferView inView = gltf.bufferViews.get(inAccessor.bufferView);
//                if(inView.buffer != 0)
//                    throw new RuntimeException("GLTF can only support buffer 0");
//                gltf.rawBuffer.byteBuffer.position(inView.byteOffset+ inAccessor.byteOffset);  // does this carry over to floatbuf?
//                FloatBuffer timeBuf = gltf.rawBuffer.byteBuffer.asFloatBuffer();
//                float[] times = new float[inAccessor.count];
//                timeBuf.get(times, 0, inAccessor.count);
//
//                GLTFBufferView outView = gltf.bufferViews.get(outAccessor.bufferView);
//                if(outView.buffer != 0)
//                    throw new RuntimeException("GLTF can only support buffer 0");
//                gltf.rawBuffer.byteBuffer.position(outView.byteOffset+outAccessor.byteOffset);  // does this carry over to floatbuf?
//                FloatBuffer floatBuf = gltf.rawBuffer.byteBuffer.asFloatBuffer();
//                float[] floats = new float[numComponents * outAccessor.count];
//                floatBuf.get(floats, 0, numComponents * outAccessor.count);
//
//
//                for(int key = 0; key < inAccessor.count; key++){
//                    float time = times[key];
//                    if(gltfChannel.path.contentEquals("translation")) {
//                        Vector3 tr = new Vector3(floats[3 * key], floats[3*key + 1], floats[3*key + 2]);
//                        NodeKeyframe<Vector3> keyFrame = new NodeKeyframe<Vector3>(time, tr);
//                        nodeAnimation.addTranslation(keyFrame);
//                    } else  if(gltfChannel.path.contentEquals("rotation")) {
//                        Quaternion q = new Quaternion(floats[4 * key], floats[key * 4 + 1], floats[key * 4 + 2], floats[key * 4 + 3]);
//                        q.nor();
//                        NodeKeyframe<Quaternion> keyFrame = new NodeKeyframe<Quaternion>(time, q);
//                        nodeAnimation.addRotation(keyFrame);
//                    } else if(gltfChannel.path.contentEquals("scale")) {
//                        Vector3 tr = new Vector3(floats[3 * key], floats[3*key + 1], floats[3*key + 2]);
//                        NodeKeyframe<Vector3> keyFrame = new NodeKeyframe<Vector3>(time, tr);
//                        nodeAnimation.addScaling(keyFrame);
//                    }
//                }
//                maxDuration = Math.max(maxDuration,times[inAccessor.count-1]);
//                animation.addNodeAnimation(nodeAnimation);
//            }
//            animation.duration = maxDuration;
//
//            model.addAnimation(animation);
//        }
        return modelData;
    }

    private void loadMaterials(ModelData modelData, GLTF gltf) {
        long startLoad = System.currentTimeMillis();
        int index = 0;
        for(GLTFMaterial gltfMat :  gltf.materials){
            ModelMaterial modelMaterial = new ModelMaterial();
            modelMaterial.textures = new Array<>();

            modelMaterial.id = gltfMat.name != null ? gltfMat.name : "mat"+index;   // copy name or generate one to be used as reference
            index++;

            if(gltfMat.pbrMetallicRoughness.baseColorFactor != null)
                modelMaterial.diffuse = gltfMat.pbrMetallicRoughness.baseColorFactor;
//            if(gltfMat.pbrMetallicRoughness.roughnessFactor >= 0)
//                materialData.roughnessFactor = gltfMat.pbrMetallicRoughness.roughnessFactor;
//            if(gltfMat.pbrMetallicRoughness.metallicFactor >= 0)
//                materialData.metallicFactor = gltfMat.pbrMetallicRoughness.metallicFactor;

            if(gltfMat.pbrMetallicRoughness.baseColorTexture >= 0){
                int textureId = gltfMat.pbrMetallicRoughness.baseColorTexture;
                GLTFImage image = gltf.images.get( gltf.textures.get(textureId).source );
                ModelTexture tex = new ModelTexture();
                tex.usage = ModelTexture.USAGE_DIFFUSE;
                tex.id = gltfMat.name;
                tex.fileName = image.uri;   // todo can be embedded in buffer

                modelMaterial.textures.add(tex);
            }
            if(gltfMat.pbrMetallicRoughness.metallicRoughnessTexture >= 0){
                int textureId = gltfMat.pbrMetallicRoughness.metallicRoughnessTexture;
                GLTFImage image = gltf.images.get( gltf.textures.get(textureId).source );
                ModelTexture tex = new ModelTexture();
                tex.usage = PBRModelTexture.USAGE_METALLIC_ROUGHNESS;
                tex.id = gltfMat.name;
                tex.fileName = image.uri;   // todo can be embedded in buffer

                modelMaterial.textures.add(tex);
            }
            if(gltfMat.normalTexture >= 0){
                int textureId = gltfMat.normalTexture;
                GLTFImage image = gltf.images.get( gltf.textures.get(textureId).source );
                ModelTexture tex = new ModelTexture();
                tex.usage = ModelTexture.USAGE_NORMAL;
                tex.id = gltfMat.name;
                tex.fileName = image.uri;   // todo can be embedded in buffer

                modelMaterial.textures.add(tex);
            }
            // todo
//            if(gltfMat.emissiveTexture >= 0)
//                materialData.emissiveMapData =  readImageData(gltf, gltfMat.emissiveTexture);
//            if(gltfMat.occlusionTexture >= 0)
//                materialData.occlusionMapData =  readImageData(gltf, gltfMat.occlusionTexture);


            modelData.materials.add(modelMaterial);
        }
        long endLoad = System.currentTimeMillis();
        System.out.println("Material loading/generation time (ms): "+(endLoad - startLoad));
    }

    private void loadMeshes(ModelData modelData, GLTF gltf){
        long startLoad = System.currentTimeMillis();
        for(GLTFMesh gltfMesh : gltf.meshes){
            // in GLTF a "mesh" is a set of primitives, not a shared vertex/index buffer.
            // we will create one ModelMesh and one ModelMeshPart per primitive

            buildMesh(modelData, gltf,  gltf.rawBuffer, gltfMesh );
        }

        long endLoad = System.currentTimeMillis();
        System.out.println("Mesh loading time (ms): "+(endLoad - startLoad));
    }

    /** convert all nodes, but does not yet create a node hierarchy */
    private void loadNodes(ModelData modelData, GLTF gltf){
        modelData.nodes.clear();
        for( GLTFNode gltfNode : gltf.nodes ) {
            ModelNode node = addNode(modelData, gltf, gltfNode);
            modelData.nodes.add(node);
        }
    }

    /** convert all scenes */
    private void loadScenes(ModelData modelData, GLTF gltf) {
        for (GLTFScene scene : gltf.scenes) {

            for (int nodeId : scene.nodes) {
                GLTFNode gltfNode = gltf.nodes.get(nodeId);
                ModelNode rootNode = modelData.nodes.get(nodeId);

                addNodeHierarchy(modelData, gltf, gltfNode, rootNode);     // recursively add the node hierarchy
                //rootNode.updateMatrices(true);
                //model.addNode(rootNode);
            }
        }
    }





//    private byte[] readImageData( GLTF gltf, int textureId )  {
//        byte[] bytes;
//
//        GLTFImage image = gltf.images.get( gltf.textures.get(textureId).source );
//        if(image.uri != null){
//            bytes = Files.internal(image.uri).readAllBytes();
//        } else {
//            GLTFBufferView view = gltf.bufferViews.get(image.bufferView);
//            if(view.buffer != 0)
//                throw new RuntimeException("GLTF can only support buffer 0");
//
//            bytes = new byte[view.byteLength];
//
//            gltf.rawBuffer.byteBuffer.position(view.byteOffset);
//            gltf.rawBuffer.byteBuffer.get(bytes);
//        }
//        return bytes;
//    }

    private ModelNode addNode(ModelData modelData, GLTF gltf, GLTFNode gltfNode){
        ModelNode node = new ModelNode();
        node.id = gltfNode.name;

        // optional transforms
        if(gltfNode.matrix != null){
            node.translation = new Vector3();
            node.scale = new Vector3();
            node.rotation = new Quaternion();
            gltfNode.matrix.getTranslation(node.translation);
            gltfNode.matrix.getScale(node.scale);
            gltfNode.matrix.getRotation(node.rotation);
        }
        if(gltfNode.translation != null)
            node.translation = new Vector3(gltfNode.translation);
        if(gltfNode.scale != null)
            node.scale = new Vector3(gltfNode.scale);
        if(gltfNode.rotation != null)
            node.rotation = new Quaternion(gltfNode.rotation);

        // does this node have a mesh?
        if(gltfNode.mesh >= 0){ // this node refers to a mesh
            // translate to ModelNodePart per GLTF primitive
            GLTFMesh gltfMesh = gltf.meshes.get(gltfNode.mesh);

            node.parts = new ModelNodePart[gltfMesh.primitives.size()];
            // assumes mesh is dedicated to this node

            int partId = 0;
            for( GLTFPrimitive primitive : gltfMesh.primitives) {

                ModelNodePart nodePart = new ModelNodePart();
                nodePart.meshPartId = meshMap.get(primitive);   // get id of ModelMeshPart
                // cross reference to material.id (a String)
                nodePart.materialId = modelData.materials.get(primitive.material).id;
                System.out.println("Node refers to mesh part :" + nodePart.meshPartId + " material: "+nodePart.materialId);
                // todo
//                nodePart.bones = 1;
//                nodePart.uvMapping = 1;

                node.parts[partId++] = nodePart;
            }
        }
        return node;
    }

    private void addNodeHierarchy(ModelData model, GLTF gltf, GLTFNode gltfNode, ModelNode root){
        // now add any children
        root.children = new ModelNode[gltfNode.children.size()];
        int i = 0;
        for(int j : gltfNode.children ){
            GLTFNode gltfChild = gltf.nodes.get(j);
            ModelNode child = model.nodes.get(j);
            root.children[i++] = child;
            addNodeHierarchy(model, gltf, gltfChild, child);
        }
    }


    private void buildMesh(ModelData modelData, GLTF gltf, GLTFRawBuffer rawBuffer, GLTFMesh gltfMesh){


        int primitiveIndex = 0;
        for(GLTFPrimitive primitive : gltfMesh.primitives) {

            ModelMesh modelMesh = new ModelMesh();
            modelMesh.id = gltfMesh.name + "."+primitiveIndex;
            modelMesh.parts = new WgModelMeshPart[1]; // one part per mesh


            ArrayList<Vector3> positions = new ArrayList<>();
            ArrayList<Vector3> normals = new ArrayList<>();
            ArrayList<Vector3> tangents = new ArrayList<>();
            ArrayList<Vector2> textureCoordinates = new ArrayList<>();
            ArrayList<Vector4> joints = new ArrayList<>();
            ArrayList<Vector4> weights = new ArrayList<>();
            ArrayList<Vector3> bitangents = new ArrayList<>();
            boolean hasNormalMap = false;

            // if the primitive has a normal texture, the mesh will need tangents and binormals
            if( gltf.materials.get(primitive.material).normalTexture >= 0 )
                hasNormalMap = true;

            // todo adjust this based on the file contents:
            Array<VertexAttribute> va = new Array<>();
            va.add(VertexAttribute.Position());
            va.add(VertexAttribute.TexCoords(0));
            va.add(VertexAttribute.Normal());

            if (hasNormalMap) {
                va.add(VertexAttribute.Tangent());
                va.add(VertexAttribute.Binormal());
            }
            for (GLTFAttribute attrib : primitive.attributes) {
                // todo not supported?
//            if(attrib.name.contentEquals("JOINTS_0")){  // todo only supports _0
//                vaFlags |= VertexAttributes.Usage.JOINTS;
//            }
                if (attrib.name.contentEquals("WEIGHTS_0")) {
                    va.add(VertexAttribute.BoneWeight(0));
                }
            }

            modelMesh.attributes = new VertexAttribute[va.size];
            for (int i = 0; i < va.size; i++)
                modelMesh.attributes[i] = va.get(i);

            // primitive indices
            int indexAccessorId = primitive.indices;        // todo indices are optional
            GLTFAccessor indexAccessor = gltf.accessors.get(indexAccessorId);


            WgModelMeshPart modelMeshPart = new WgModelMeshPart();
            modelMeshPart.id = modelMesh.id; // must be unique as it is used in modelNodePart as a reference
            modelMeshPart.primitiveType = primitive.mode; // GLTF uses OpenGL constants, e.g. GL_TRIANGLES

            meshMap.put(primitive,  modelMeshPart.id);

            GLTFBufferView view = gltf.bufferViews.get(indexAccessor.bufferView);
            if(view.buffer != 0)
                throw new RuntimeException("GLTF: Can only support buffer 0");
            int offset = view.byteOffset + indexAccessor.byteOffset;
            rawBuffer.byteBuffer.position(offset);

            if(indexAccessor.componentType == GLTF.USHORT16) {
                modelMeshPart.indices = new short[indexAccessor.count];

                for (int i = 0; i < indexAccessor.count; i++) {
                    modelMeshPart.indices[i] = rawBuffer.byteBuffer.getShort();
                }
            } else {
                modelMeshPart.indices32 = new int[indexAccessor.count];
                for(int i = 0; i < indexAccessor.count; i++){
                    modelMeshPart.indices32[i] = rawBuffer.byteBuffer.getInt();
                }
                System.out.println("Using 32 bit indices:  "+indexAccessor.count);
            }

            modelMesh.parts[0] = modelMeshPart;
            primitiveIndex++;


            //boolean hasNormalMap = meshData.vertexAttributes.hasUsage(VertexAttribute.Usage.TANGENT);

            int positionAccessorId = -1;
            int normalAccessorId = -1;
            int uvAccessorId = -1;
            int tangentAccessorId = -1;
            int jointsAccessorId = -1;
            int weightsAccessorId = -1;
            ArrayList<GLTFAttribute> attributes = primitive.attributes;
            for (GLTFAttribute attribute : attributes) {
                if (attribute.name.contentEquals("POSITION")) {
                    positionAccessorId = attribute.value;
                } else if (attribute.name.contentEquals("NORMAL")) {
                    normalAccessorId = attribute.value;
                } else if (attribute.name.contentEquals("TEXCOORD_0")) {
                    uvAccessorId = attribute.value;
                } else if (attribute.name.contentEquals("TANGENT")) {
                    tangentAccessorId = attribute.value;
                } else if (attribute.name.contentEquals("JOINTS_0")) {
                    jointsAccessorId = attribute.value;
                } else if (attribute.name.contentEquals("WEIGHTS_0")) {
                    weightsAccessorId = attribute.value;
                }
            }
            if (positionAccessorId < 0)
                throw new RuntimeException("GLTF: need POSITION attribute");
            GLTFAccessor positionAccessor = gltf.accessors.get(positionAccessorId);
            view = gltf.bufferViews.get(positionAccessor.bufferView);
            if (view.buffer != 0)
                throw new RuntimeException("GLTF: Can only support buffer 0");
            offset = view.byteOffset;
            offset += positionAccessor.byteOffset;

            //System.out.println("Position offset: "+offset);
            //System.out.println("Position count: "+positionAccessor.count);

            if (positionAccessor.componentType != GLTF.FLOAT32 || !positionAccessor.type.contentEquals("VEC3"))
                throw new RuntimeException("GLTF: Can only support float positions as VEC3");


            rawBuffer.byteBuffer.position(offset);
            for (int i = 0; i < positionAccessor.count; i++) {
                // assuming float32
                float f1 = rawBuffer.byteBuffer.getFloat();
                float f2 = rawBuffer.byteBuffer.getFloat();
                float f3 = rawBuffer.byteBuffer.getFloat();
                //System.out.println("float  "+f1 + " "+ f2 + " "+f3);
                positions.add(new Vector3(f1, f2, f3));
            }


            if (normalAccessorId >= 0) {
                GLTFAccessor normalAccessor = gltf.accessors.get(normalAccessorId);
                view = gltf.bufferViews.get(normalAccessor.bufferView);
                if (view.buffer != 0)
                    throw new RuntimeException("GLTF: Can only support buffer 0");
                offset = view.byteOffset;
                offset += normalAccessor.byteOffset;


                if (normalAccessor.componentType != GLTF.FLOAT32 || !positionAccessor.type.contentEquals("VEC3"))
                    throw new RuntimeException("GLTF: Can only support float normals as VEC3");

                rawBuffer.byteBuffer.position(offset);
                for (int i = 0; i < normalAccessor.count; i++) {
                    // assuming float32
                    float f1 = rawBuffer.byteBuffer.getFloat();
                    float f2 = rawBuffer.byteBuffer.getFloat();
                    float f3 = rawBuffer.byteBuffer.getFloat();
                    //System.out.println("float  "+f1 + " "+ f2 + " "+f3);
                    normals.add(new Vector3(f1, f2, f3));
                }
            }


            if (tangentAccessorId >= 0) {
                GLTFAccessor tangentAccessor = gltf.accessors.get(tangentAccessorId);
                view = gltf.bufferViews.get(tangentAccessor.bufferView);
                if (view.buffer != 0)
                    throw new RuntimeException("GLTF: Can only support buffer 0");
                offset = view.byteOffset;
                offset += tangentAccessor.byteOffset;

                if (tangentAccessor.componentType != GLTF.FLOAT32 || !positionAccessor.type.contentEquals("VEC3"))
                    throw new RuntimeException("GLTF: Can only support float tangents as VEC3");

                rawBuffer.byteBuffer.position(offset);
                for (int i = 0; i < tangentAccessor.count; i++) {
                    // assuming float32
                    float f1 = rawBuffer.byteBuffer.getFloat();
                    float f2 = rawBuffer.byteBuffer.getFloat();
                    float f3 = rawBuffer.byteBuffer.getFloat();
                    //System.out.println("float  "+f1 + " "+ f2 + " "+f3);
                    tangents.add(new Vector3(f1, f2, f3));
                }
            }


            if (uvAccessorId >= 0) {

                GLTFAccessor uvAccessor = gltf.accessors.get(uvAccessorId);
                view = gltf.bufferViews.get(uvAccessor.bufferView);
                if (view.buffer != 0)
                    throw new RuntimeException("GLTF: Can only support buffer 0");
                offset = view.byteOffset;
                offset += uvAccessor.byteOffset;

                //System.out.println("UV offset: " + offset);

                if (uvAccessor.componentType != GLTF.FLOAT32 || !uvAccessor.type.contentEquals("VEC2"))
                    throw new RuntimeException("GLTF: Can only support float positions as VEC2");


                rawBuffer.byteBuffer.position(offset);
                for (int i = 0; i < uvAccessor.count; i++) {
                    // assuming float32
                    float f1 = rawBuffer.byteBuffer.getFloat();
                    float f2 = rawBuffer.byteBuffer.getFloat();
                    //System.out.println("float  "+f1 + " "+ f2 );
                    textureCoordinates.add(new Vector2(f1, f2));
                }
            }


            if (jointsAccessorId >= 0) {
                GLTFAccessor jointsAccessor = gltf.accessors.get(jointsAccessorId);
                if (jointsAccessor.componentType != GLTF.USHORT16 && jointsAccessor.componentType != GLTF.UBYTE8)
                    throw new RuntimeException("GLTF: Can only joints defined as USHORT16 or UBYTE8, type = " + jointsAccessor.componentType);
                if (!jointsAccessor.type.contentEquals("VEC4"))
                    throw new RuntimeException("GLTF: Can only support joints as vec4, type = " + jointsAccessor.type);
                view = gltf.bufferViews.get(jointsAccessor.bufferView);
                if (view.buffer != 0)
                    throw new RuntimeException("GLTF: Can only support buffer 0");
                offset = view.byteOffset;
                offset += jointsAccessor.byteOffset;
                rawBuffer.byteBuffer.position(offset);
                boolean isByte = (jointsAccessor.componentType == GLTF.UBYTE8);
                short u1, u2, u3, u4;
                for (int i = 0; i < jointsAccessor.count; i++) {
                    // assuming ubyte8 or ushort16 (handled as (signed) short here)
                    if (isByte) {
                        u1 = rawBuffer.byteBuffer.get();
                        u2 = rawBuffer.byteBuffer.get();
                        u3 = rawBuffer.byteBuffer.get();
                        u4 = rawBuffer.byteBuffer.get();
                    } else {
                        u1 = rawBuffer.byteBuffer.getShort();
                        u2 = rawBuffer.byteBuffer.getShort();
                        u3 = rawBuffer.byteBuffer.getShort();
                        u4 = rawBuffer.byteBuffer.getShort();
                    }

                    Vector4 jj = new Vector4(u1, u2, u3, u4);
                    joints.add(jj);

//                int jointInt = (u1&0xFF) << 24 | (u2 &0xFF) << 16 | (u3&0xFF) << 8 | (u4&0xFF);
//                System.out.println("joints  "+u1 + " "+ u2 + " "+u3+" "+u4+": "+Integer.toHexString(jointInt));
//                joints.add(jointInt);
                }
            }


            if (weightsAccessorId >= 0) {
                GLTFAccessor accessor = gltf.accessors.get(weightsAccessorId);
                if (accessor.componentType != GLTF.FLOAT32 || !accessor.type.contentEquals("VEC4"))
                    throw new RuntimeException("GLTF: Can only support vec4(FLOAT32) for joints, type = " + accessor.componentType);
                view = gltf.bufferViews.get(accessor.bufferView);
                if (view.buffer != 0)
                    throw new RuntimeException("GLTF: Can only support buffer 0");
                offset = view.byteOffset;
                offset += accessor.byteOffset;
                rawBuffer.byteBuffer.position(offset);

                for (int i = 0; i < accessor.count; i++) {
                    float f1 = rawBuffer.byteBuffer.getFloat();
                    float f2 = rawBuffer.byteBuffer.getFloat();
                    float f3 = rawBuffer.byteBuffer.getFloat();
                    float f4 = rawBuffer.byteBuffer.getFloat();
                    Vector4 w = new Vector4(f1, f2, f3, f4);
                    System.out.println("weights  " + w.toString());
                    weights.add(w);
                }


            }

            // if the material has a normal map and tangents are not provided we need to calculate them

            if(hasNormalMap && (tangents.size() == 0  || bitangents.size() == 0))
                addTBN(modelMeshPart, positions, textureCoordinates, normals, tangents, bitangents);

            // now fill a vertex buffer including all primitives

            // x y z   u v   nx ny nz (tx ty tz   bx by bz)
            //modelMesh.id = gltf.nodes.get(0).name;
            Vector3 normal = new Vector3(0, 1, 0);
            Vector2 uv = new Vector2();
            int ix = 0;
            int vertSize = 8;
            if(hasNormalMap)
                vertSize += 6;
            if(!joints.isEmpty())
                vertSize += 4;
            if(!weights.isEmpty())
                vertSize += 4;

            float[] verts = new float[positions.size() * vertSize ];
            modelMesh.vertices = verts;
            for(int i = 0; i < positions.size(); i++){
                Vector3 pos = positions.get(i);
                verts[ix++] = pos.x;
                verts[ix++] = pos.y;
                verts[ix++] = pos.z;

                if(!textureCoordinates.isEmpty())
                    uv =  textureCoordinates.get(i);
                verts[ix++] = uv.x;
                verts[ix++] = uv.y;

                if(!normals.isEmpty())
                    normal = normals.get(i);
                verts[ix++] = normal.x;
                verts[ix++] = normal.y;
                verts[ix++] = normal.z;

                if(hasNormalMap) {
                    Vector3 tangent = tangents.get(i);
                    verts[ix++] = tangent.x;
                    verts[ix++] = tangent.y;
                    verts[ix++] = tangent.z;

                    // calculate bitangent from normal x tangent
                    Vector3 bitangent = bitangents.get(i);
                    verts[ix++] = bitangent.x;
                    verts[ix++] = bitangent.y;
                    verts[ix++] = bitangent.z;
                }

                if(!joints.isEmpty()) {
//                float jointF = Float.intBitsToFloat(joints.get(i));
//                meshData.vertFloats.add(jointF);        // masquerade integer value as float
                    Vector4 jnt = joints.get(i);
                    verts[ix++] = jnt.x;
                    verts[ix++] = jnt.y;
                    verts[ix++] = jnt.z;
                    verts[ix++] = jnt.w;
                }

                if(!weights.isEmpty()) {
                    Vector4 weight = weights.get(i);
                    verts[ix++] = weight.x;
                    verts[ix++] = weight.y;
                    verts[ix++] = weight.z;
                    verts[ix++] = weight.w;
                }
            }
            modelData.addMesh(modelMesh);
        }


    }

    private static class Vertex {
        Vector3 position;
        Vector3 normal;
        Vector2 uv;
    }

    private void addTBN( final ModelMeshPart modelData,
                         final ArrayList<Vector3>positions, final ArrayList<Vector2>textureCoordinates,
                         final ArrayList<Vector3>normals,
                         ArrayList<Vector3>tangents,
                         ArrayList<Vector3>bitangents){

        // add tangent and bitangent to vertices of each triangle
        Vector3 T = new Vector3();
        Vector3 B = new Vector3();
        Vertex[] corners = new Vertex[3];
        for(int i= 0; i < 3; i++)
            corners[i] = new Vertex();

        // todo assumes short index
        for (int j = 0; j < modelData.indices.length; j+= 3) {   // for each triangle
            for(int i= 0; i < 3; i++) {                 // for each corner
                int index = modelData.indices[i];        // assuming we use an indexed mesh

                corners[i].position = positions.get(index);
                corners[i].normal = normals.get(index);
                corners[i].uv = textureCoordinates.get(index);
            }

            calculateBTN(corners, T, B);

            for(int i= 0; i < 3; i++) {
                tangents.add(T);
                bitangents.add(B);
            }
        }
    }

    private static Vector3 Ntmp = new Vector3();
    private static Vector3 N = new Vector3();

    private static Vector3 edge1 = new Vector3();
    private static Vector3 edge2 = new Vector3();
    private static Vector2 eUV1 = new Vector2();
    private static Vector2 eUV2 = new Vector2();



    private static void calculateBTN(Vertex corners[], Vector3 T, Vector3 B) {
        edge1.set(corners[1].position).sub(corners[0].position);
        edge2.set(corners[2].position).sub(corners[0].position);

        eUV1.set(corners[1].uv).sub(corners[0].uv);
        eUV2.set(corners[2].uv).sub(corners[0].uv);

        T.set(edge1.cpy().scl(eUV2.y).sub(edge2.cpy().scl(eUV1.y)));
        B.set(edge2.cpy().scl(eUV1.x).sub(edge1.cpy().scl(eUV2.x)));
        T.scl(-1);
        B.scl(-1);
        N.set(T).crs(B);

        // average normal
        Ntmp.set(corners[0].normal).add(corners[1].normal).add(corners[2].normal).scl(1/3f);

//        if(Ntmp.dot(N) < 0){
//            T.scl(-1);
//            B.scl(-1);
//        }

        float dot = T.dot(Ntmp);
        T.sub(Ntmp.cpy().scl(dot));
        T.nor();
        // T = normalize(T - dot(T, N) * N);
        //B = cross(N,T);
        B.set(Ntmp).crs(T);
    }

//
//
//    protected void parseMeshes (ModelData model, JsonValue json) {
//		JsonValue meshes = json.get("meshes");
//		if (meshes != null) {
//
//			model.meshes.ensureCapacity(meshes.size);
//			for (JsonValue mesh = meshes.child; mesh != null; mesh = mesh.next) {
//				ModelMesh jsonMesh = new ModelMesh();
//
//				String id = mesh.getString("id", "");
//				jsonMesh.id = id;
//
//				JsonValue attributes = mesh.require("attributes");
//				jsonMesh.attributes = parseAttributes(attributes);
//				jsonMesh.vertices = mesh.require("vertices").asFloatArray();
//
//				JsonValue meshParts = mesh.require("parts");
//				Array<ModelMeshPart> parts = new Array<ModelMeshPart>();
//				for (JsonValue meshPart = meshParts.child; meshPart != null; meshPart = meshPart.next) {
//					ModelMeshPart jsonPart = new ModelMeshPart();
//					String partId = meshPart.getString("id", null);
//					if (partId == null) {
//						throw new GdxRuntimeException("Not id given for mesh part");
//					}
//					for (ModelMeshPart other : parts) {
//						if (other.id.equals(partId)) {
//							throw new GdxRuntimeException("Mesh part with id '" + partId + "' already in defined");
//						}
//					}
//					jsonPart.id = partId;
//
//					String type = meshPart.getString("type", null);
//					if (type == null) {
//						throw new GdxRuntimeException("No primitive type given for mesh part '" + partId + "'");
//					}
//					jsonPart.primitiveType = parseType(type);
//
//					jsonPart.indices = meshPart.require("indices").asShortArray();
//					parts.add(jsonPart);
//				}
//				jsonMesh.parts = parts.toArray(ModelMeshPart[]::new);
//				model.meshes.add(jsonMesh);
//			}
//		}
//	}
//
//	protected int parseType (String type) {
//		if (type.equals("TRIANGLES")) {
//			return GL20.GL_TRIANGLES;
//		} else if (type.equals("LINES")) {
//			return GL20.GL_LINES;
//		} else if (type.equals("POINTS")) {
//			return GL20.GL_POINTS;
//		} else if (type.equals("TRIANGLE_STRIP")) {
//			return GL20.GL_TRIANGLE_STRIP;
//		} else if (type.equals("LINE_STRIP")) {
//			return GL20.GL_LINE_STRIP;
//		} else {
//			throw new GdxRuntimeException(
//				"Unknown primitive type '" + type + "', should be one of triangle, trianglestrip, line, linestrip or point");
//		}
//	}
//
//	protected VertexAttribute[] parseAttributes (JsonValue attributes) {
//		Array<VertexAttribute> vertexAttributes = new Array<VertexAttribute>();
//		int unit = 0;
//		int blendWeightCount = 0;
//		for (JsonValue value = attributes.child; value != null; value = value.next) {
//			String attribute = value.asString();
//			String attr = (String)attribute;
//			if (attr.equals("POSITION")) {
//				vertexAttributes.add(VertexAttribute.Position());
//			} else if (attr.equals("NORMAL")) {
//				vertexAttributes.add(VertexAttribute.Normal());
//			} else if (attr.equals("COLOR")) {
//				vertexAttributes.add(VertexAttribute.ColorUnpacked());
//			} else if (attr.equals("COLORPACKED")) {
//				vertexAttributes.add(VertexAttribute.ColorPacked());
//			} else if (attr.equals("TANGENT")) {
//				vertexAttributes.add(VertexAttribute.Tangent());
//			} else if (attr.equals("BINORMAL")) {
//				vertexAttributes.add(VertexAttribute.Binormal());
//			} else if (attr.startsWith("TEXCOORD")) {
//				vertexAttributes.add(VertexAttribute.TexCoords(unit++));
//			} else if (attr.startsWith("BLENDWEIGHT")) {
//				vertexAttributes.add(VertexAttribute.BoneWeight(blendWeightCount++));
//			} else {
//				throw new GdxRuntimeException(
//					"Unknown vertex attribute '" + attr + "', should be one of position, normal, uv, tangent or binormal");
//			}
//		}
//		return vertexAttributes.toArray(VertexAttribute[]::new);
//	}
//
//	protected void parseMaterials (ModelData model, JsonValue json, String materialDir) {
//		JsonValue materials = json.get("materials");
//		if (materials == null) {
//			// we should probably create some default material in this case
//		} else {
//			model.materials.ensureCapacity(materials.size);
//			for (JsonValue material = materials.child; material != null; material = material.next) {
//				ModelMaterial jsonMaterial = new ModelMaterial();
//
//				String id = material.getString("id", null);
//				if (id == null) throw new GdxRuntimeException("Material needs an id.");
//
//				jsonMaterial.id = id;
//
//				// Read material colors
//				final JsonValue diffuse = material.get("diffuse");
//				if (diffuse != null) jsonMaterial.diffuse = parseColor(diffuse);
//				final JsonValue ambient = material.get("ambient");
//				if (ambient != null) jsonMaterial.ambient = parseColor(ambient);
//				final JsonValue emissive = material.get("emissive");
//				if (emissive != null) jsonMaterial.emissive = parseColor(emissive);
//				final JsonValue specular = material.get("specular");
//				if (specular != null) jsonMaterial.specular = parseColor(specular);
//				final JsonValue reflection = material.get("reflection");
//				if (reflection != null) jsonMaterial.reflection = parseColor(reflection);
//				// Read shininess
//				jsonMaterial.shininess = material.getFloat("shininess", 0.0f);
//				// Read opacity
//				jsonMaterial.opacity = material.getFloat("opacity", 1.0f);
//
//				// Read textures
//				JsonValue textures = material.get("textures");
//				if (textures != null) {
//					for (JsonValue texture = textures.child; texture != null; texture = texture.next) {
//						ModelTexture jsonTexture = new ModelTexture();
//
//						String textureId = texture.getString("id", null);
//						if (textureId == null) throw new GdxRuntimeException("Texture has no id.");
//						jsonTexture.id = textureId;
//
//						String fileName = texture.getString("filename", null);
//						if (fileName == null) throw new GdxRuntimeException("Texture needs filename.");
//						jsonTexture.fileName = materialDir + (materialDir.length() == 0 || materialDir.endsWith("/") ? "" : "/")
//							+ fileName;
//
//						jsonTexture.uvTranslation = readVector2(texture.get("uvTranslation"), 0f, 0f);
//						jsonTexture.uvScaling = readVector2(texture.get("uvScaling"), 1f, 1f);
//
//						String textureType = texture.getString("type", null);
//						if (textureType == null) throw new GdxRuntimeException("Texture needs type.");
//
//						jsonTexture.usage = parseTextureUsage(textureType);
//
//						if (jsonMaterial.textures == null) jsonMaterial.textures = new Array<ModelTexture>();
//						jsonMaterial.textures.add(jsonTexture);
//					}
//				}
//
//				model.materials.add(jsonMaterial);
//			}
//		}
//	}
//
//	protected int parseTextureUsage (final String value) {
//		if (value.equalsIgnoreCase("AMBIENT"))
//			return ModelTexture.USAGE_AMBIENT;
//		else if (value.equalsIgnoreCase("BUMP"))
//			return ModelTexture.USAGE_BUMP;
//		else if (value.equalsIgnoreCase("DIFFUSE"))
//			return ModelTexture.USAGE_DIFFUSE;
//		else if (value.equalsIgnoreCase("EMISSIVE"))
//			return ModelTexture.USAGE_EMISSIVE;
//		else if (value.equalsIgnoreCase("NONE"))
//			return ModelTexture.USAGE_NONE;
//		else if (value.equalsIgnoreCase("NORMAL"))
//			return ModelTexture.USAGE_NORMAL;
//		else if (value.equalsIgnoreCase("REFLECTION"))
//			return ModelTexture.USAGE_REFLECTION;
//		else if (value.equalsIgnoreCase("SHININESS"))
//			return ModelTexture.USAGE_SHININESS;
//		else if (value.equalsIgnoreCase("SPECULAR"))
//			return ModelTexture.USAGE_SPECULAR;
//		else if (value.equalsIgnoreCase("TRANSPARENCY")) return ModelTexture.USAGE_TRANSPARENCY;
//		return ModelTexture.USAGE_UNKNOWN;
//	}
//
//	protected Color parseColor (JsonValue colorArray) {
//		if (colorArray.size >= 3)
//			return new Color(colorArray.getFloat(0), colorArray.getFloat(1), colorArray.getFloat(2), 1.0f);
//		else
//			throw new GdxRuntimeException("Expected Color values <> than three.");
//	}
//
//	protected Vector2 readVector2 (JsonValue vectorArray, float x, float y) {
//		if (vectorArray == null)
//			return new Vector2(x, y);
//		else if (vectorArray.size == 2)
//			return new Vector2(vectorArray.getFloat(0), vectorArray.getFloat(1));
//		else
//			throw new GdxRuntimeException("Expected Vector2 values <> than two.");
//	}
//
//	protected Array<ModelNode> parseNodes (ModelData model, JsonValue json) {
//		JsonValue nodes = json.get("nodes");
//		if (nodes != null) {
//			model.nodes.ensureCapacity(nodes.size);
//			for (JsonValue node = nodes.child; node != null; node = node.next) {
//				model.nodes.add(parseNodesRecursively(node));
//			}
//		}
//
//		return model.nodes;
//	}
//
//	protected final Quaternion tempQ = new Quaternion();
//
//	protected ModelNode parseNodesRecursively (JsonValue json) {
//		ModelNode jsonNode = new ModelNode();
//
//		String id = json.getString("id", null);
//		if (id == null) throw new GdxRuntimeException("Node id missing.");
//		jsonNode.id = id;
//
//		JsonValue translation = json.get("translation");
//		if (translation != null && translation.size != 3) throw new GdxRuntimeException("Node translation incomplete");
//		jsonNode.translation = translation == null ? null
//			: new Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2));
//
//		JsonValue rotation = json.get("rotation");
//		if (rotation != null && rotation.size != 4) throw new GdxRuntimeException("Node rotation incomplete");
//		jsonNode.rotation = rotation == null ? null
//			: new Quaternion(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3));
//
//		JsonValue scale = json.get("scale");
//		if (scale != null && scale.size != 3) throw new GdxRuntimeException("Node scale incomplete");
//		jsonNode.scale = scale == null ? null : new Vector3(scale.getFloat(0), scale.getFloat(1), scale.getFloat(2));
//
//		String meshId = json.getString("mesh", null);
//		if (meshId != null) jsonNode.meshId = meshId;
//
//		JsonValue materials = json.get("parts");
//		if (materials != null) {
//			jsonNode.parts = new ModelNodePart[materials.size];
//			int i = 0;
//			for (JsonValue material = materials.child; material != null; material = material.next, i++) {
//				ModelNodePart nodePart = new ModelNodePart();
//
//				String meshPartId = material.getString("meshpartid", null);
//				String materialId = material.getString("materialid", null);
//				if (meshPartId == null || materialId == null) {
//					throw new GdxRuntimeException("Node " + id + " part is missing meshPartId or materialId");
//				}
//				nodePart.materialId = materialId;
//				nodePart.meshPartId = meshPartId;
//
//				JsonValue bones = material.get("bones");
//				if (bones != null) {
//					nodePart.bones = new ArrayMap<>(true, bones.size, String[]::new, Matrix4[]::new);
//					int j = 0;
//					for (JsonValue bone = bones.child; bone != null; bone = bone.next, j++) {
//						String nodeId = bone.getString("node", null);
//						if (nodeId == null) throw new GdxRuntimeException("Bone node ID missing");
//
//						Matrix4 transform = new Matrix4();
//
//						JsonValue val = bone.get("translation");
//						if (val != null && val.size >= 3) transform.translate(val.getFloat(0), val.getFloat(1), val.getFloat(2));
//
//						val = bone.get("rotation");
//						if (val != null && val.size >= 4)
//							transform.rotate(tempQ.set(val.getFloat(0), val.getFloat(1), val.getFloat(2), val.getFloat(3)));
//
//						val = bone.get("scale");
//						if (val != null && val.size >= 3) transform.scale(val.getFloat(0), val.getFloat(1), val.getFloat(2));
//
//						nodePart.bones.put(nodeId, transform);
//					}
//				}
//
//				jsonNode.parts[i] = nodePart;
//			}
//		}
//
//		JsonValue children = json.get("children");
//		if (children != null) {
//			jsonNode.children = new ModelNode[children.size];
//
//			int i = 0;
//			for (JsonValue child = children.child; child != null; child = child.next, i++) {
//				jsonNode.children[i] = parseNodesRecursively(child);
//			}
//		}
//
//		return jsonNode;
//	}
//
//	protected void parseAnimations (ModelData model, JsonValue json) {
//		JsonValue animations = json.get("animations");
//		if (animations == null) return;
//
//		model.animations.ensureCapacity(animations.size);
//
//		for (JsonValue anim = animations.child; anim != null; anim = anim.next) {
//			JsonValue nodes = anim.get("bones");
//			if (nodes == null) continue;
//			ModelAnimation animation = new ModelAnimation();
//			model.animations.add(animation);
//			animation.nodeAnimations.ensureCapacity(nodes.size);
//			animation.id = anim.getString("id");
//			for (JsonValue node = nodes.child; node != null; node = node.next) {
//				ModelNodeAnimation nodeAnim = new ModelNodeAnimation();
//				animation.nodeAnimations.add(nodeAnim);
//				nodeAnim.nodeId = node.getString("boneId");
//
//				// For backwards compatibility (version 0.1):
//				JsonValue keyframes = node.get("keyframes");
//				if (keyframes != null && keyframes.isArray()) {
//					for (JsonValue keyframe = keyframes.child; keyframe != null; keyframe = keyframe.next) {
//						final float keytime = keyframe.getFloat("keytime", 0f) / 1000.f;
//						JsonValue translation = keyframe.get("translation");
//						if (translation != null && translation.size == 3) {
//							if (nodeAnim.translation == null) nodeAnim.translation = new Array<ModelNodeKeyframe<Vector3>>();
//							ModelNodeKeyframe<Vector3> tkf = new ModelNodeKeyframe<Vector3>();
//							tkf.keytime = keytime;
//							tkf.value = new Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2));
//							nodeAnim.translation.add(tkf);
//						}
//						JsonValue rotation = keyframe.get("rotation");
//						if (rotation != null && rotation.size == 4) {
//							if (nodeAnim.rotation == null) nodeAnim.rotation = new Array<ModelNodeKeyframe<Quaternion>>();
//							ModelNodeKeyframe<Quaternion> rkf = new ModelNodeKeyframe<Quaternion>();
//							rkf.keytime = keytime;
//							rkf.value = new Quaternion(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2),
//								rotation.getFloat(3));
//							nodeAnim.rotation.add(rkf);
//						}
//						JsonValue scale = keyframe.get("scale");
//						if (scale != null && scale.size == 3) {
//							if (nodeAnim.scaling == null) nodeAnim.scaling = new Array<ModelNodeKeyframe<Vector3>>();
//							ModelNodeKeyframe<Vector3> skf = new ModelNodeKeyframe();
//							skf.keytime = keytime;
//							skf.value = new Vector3(scale.getFloat(0), scale.getFloat(1), scale.getFloat(2));
//							nodeAnim.scaling.add(skf);
//						}
//					}
//				} else { // Version 0.2:
//					JsonValue translationKF = node.get("translation");
//					if (translationKF != null && translationKF.isArray()) {
//						nodeAnim.translation = new Array<ModelNodeKeyframe<Vector3>>();
//						nodeAnim.translation.ensureCapacity(translationKF.size);
//						for (JsonValue keyframe = translationKF.child; keyframe != null; keyframe = keyframe.next) {
//							ModelNodeKeyframe<Vector3> kf = new ModelNodeKeyframe<Vector3>();
//							nodeAnim.translation.add(kf);
//							kf.keytime = keyframe.getFloat("keytime", 0f) / 1000.f;
//							JsonValue translation = keyframe.get("value");
//							if (translation != null && translation.size >= 3)
//								kf.value = new Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2));
//						}
//					}
//
//					JsonValue rotationKF = node.get("rotation");
//					if (rotationKF != null && rotationKF.isArray()) {
//						nodeAnim.rotation = new Array<ModelNodeKeyframe<Quaternion>>();
//						nodeAnim.rotation.ensureCapacity(rotationKF.size);
//						for (JsonValue keyframe = rotationKF.child; keyframe != null; keyframe = keyframe.next) {
//							ModelNodeKeyframe<Quaternion> kf = new ModelNodeKeyframe<Quaternion>();
//							nodeAnim.rotation.add(kf);
//							kf.keytime = keyframe.getFloat("keytime", 0f) / 1000.f;
//							JsonValue rotation = keyframe.get("value");
//							if (rotation != null && rotation.size >= 4) kf.value = new Quaternion(rotation.getFloat(0),
//								rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3));
//						}
//					}
//
//					JsonValue scalingKF = node.get("scaling");
//					if (scalingKF != null && scalingKF.isArray()) {
//						nodeAnim.scaling = new Array<ModelNodeKeyframe<Vector3>>();
//						nodeAnim.scaling.ensureCapacity(scalingKF.size);
//						for (JsonValue keyframe = scalingKF.child; keyframe != null; keyframe = keyframe.next) {
//							ModelNodeKeyframe<Vector3> kf = new ModelNodeKeyframe<Vector3>();
//							nodeAnim.scaling.add(kf);
//							kf.keytime = keyframe.getFloat("keytime", 0f) / 1000.f;
//							JsonValue scaling = keyframe.get("value");
//							if (scaling != null && scaling.size >= 3)
//								kf.value = new Vector3(scaling.getFloat(0), scaling.getFloat(1), scaling.getFloat(2));
//						}
//					}
//				}
//			}
//		}
//	}

}
