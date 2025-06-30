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
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BaseJsonReader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** Loader for glb model format (binary GLTF).
 *
 */


public class WgGLBModelLoader extends WgModelLoader<WgModelLoader.ModelParameters> {
    private final Map<GLTFPrimitive, String> meshMap = new HashMap<>();
    //private final ArrayList<ModelNode> nodes = new ArrayList<>();
    final BaseJsonReader reader;

	public WgGLBModelLoader(final BaseJsonReader reader) {
		this(reader, null);
	}

	public WgGLBModelLoader(BaseJsonReader reader, FileHandleResolver resolver) {
		super(resolver);
        this.reader = reader;
	}

	@Override
	public ModelData loadModelData (FileHandle fileHandle, ModelParameters parameters) {
		return parseModel(fileHandle);
	}

	public ModelData parseModel (@NotNull FileHandle handle) {
        // create path to find additional resources
        int lastSlashPos = handle.path().lastIndexOf('/');
        String path = handle.path().substring(0, lastSlashPos + 1);

        /* Read file into a GLTF class hierarchy. */
        byte[] contents = handle.readBytes();
        GLTF gltf = parseBinaryFile(handle.name(), path, contents);

        /* Then convert it to ModelData. */
		return new WgGLTFModelLoader(reader).load(gltf);
	}

    private static GLTF parseBinaryFile( String name, String path, byte[] contents ){
        ByteBuffer bb = ByteBuffer.wrap( contents );
        bb.order(ByteOrder.LITTLE_ENDIAN);

        bb.rewind();

        // parse header
        int magic = bb.getInt();
        int version = bb.getInt();
        int len = bb.getInt();

        System.out.println("Magic: "+Integer.toHexString(magic)); //  0x46546C67
        System.out.println("Version: "+version);
        System.out.println("Length: "+len);

        if(magic != 0x46546C67)
            throw new RuntimeException("GLB file invalid: "+name);
        if(version != 2)
            System.out.println("Warning: GLB version unsupported (!=2) : "+name);
        if(len != contents.length)
            throw new RuntimeException("GLB file length invalid: "+name);

        // read chunk
        int chunkLength = bb.getInt();
        int chunkType = bb.getInt();

//        System.out.println("Chunk length: "+chunkLength+" type: "+Integer.toHexString(chunkType));
        if(chunkType != 0x4e4f534a) // "JSON"
            throw new RuntimeException("GLB file invalid, first chunk must be type JSON: "+name);


        Charset charset = StandardCharsets.US_ASCII;
        ByteBuffer chunkData = bb.slice();
        chunkData.limit(chunkLength);
        CharBuffer charBuffer = charset.decode(chunkData);
        char[] jsonArray = new char[chunkLength];
        charBuffer.get(jsonArray);
        String json = new String(jsonArray);

        //System.out.println("JSON ["+json+"]");

        bb.position(bb.position() + chunkLength);

        chunkLength = bb.getInt();
        chunkType = bb.getInt();
        if(chunkType != 0x4E4942) // "BIN"
            throw new RuntimeException("GLB file invalid, second chunk must be type BIN: "+name);

        //System.out.println("Chunk length: "+chunkLength+" type: "+Integer.toHexString(chunkType));

        ByteBuffer chunkBinaryData = bb.slice();
        chunkBinaryData.limit(chunkLength);

        GLTF gltf = GLTFParser.parseJSON(json, path);
        gltf.rawBuffer = new GLTFRawBuffer(chunkBinaryData);
        return gltf;
    }

}
