package com.badlogic.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.webgpu.graphics.g3d.WebGPUIndexData;
import com.badlogic.gdx.webgpu.graphics.g3d.WebGPUVertexData;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.graphics.glutils.*;

public class WebGPUMesh extends Mesh {

    /** Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WebGPUMesh (boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
        super(new WebGPUVertexData(maxVertices,new VertexAttributes(attributes)), new WebGPUIndexData(maxIndices), false);
    }


    /** Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WebGPUMesh (boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        super(new WebGPUVertexData(maxVertices, attributes), new WebGPUIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes. Adds extra optimizations for dynamic (frequently modified) meshes.
     *
     * @param staticVertices whether vertices of this mesh are static or not. Allows for internal optimizations.
     * @param staticIndices whether indices of this mesh are static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate
     *
     * @author Jaroslaw Wisniewski <j.wisniewski@appsisle.com> **/
    public WebGPUMesh (boolean staticVertices, boolean staticIndices, int maxVertices, int maxIndices, VertexAttributes attributes) {
        super(new WebGPUVertexData(maxVertices, attributes), new WebGPUIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WebGPUMesh (VertexDataType type, boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
        super(new WebGPUVertexData(maxVertices,new VertexAttributes(attributes)), new WebGPUIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. */
    public WebGPUMesh (VertexDataType type, boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        super(null, null, false);
        throw new RuntimeException("Use another constructor for WebGPUMesh");
    }


//    public VertexData getVertexData(){
//        return vertices;
//    }


    @Override
    public void render (ShaderProgram shader, int primitiveType, int offset, int count, boolean autoBind){
        //....
        Gdx.app.log("WebGPUMesh", "render() (ignored)");
        // Set vertex buffer while encoding the render pass
        // use an offset to set the vertex buffer for this batch
    }

    /** New method for WebGPU rendering (uses a renderPass).
     * note: primitiveType is ignored. Should be set in the pipeline.
     */
    public void render (WebGPURenderPass renderPass, int primitiveType, int offset, int size, int numInstances, int firstInstance){
        //....
        //Gdx.app.log("WebGPUMesh", "render(renderPass)");

        // bind indices
        if( getIndexData() != null)
            ((WebGPUIndexData)getIndexData()).bind(renderPass);

        // bind vertices
        // HACK: we had to make Mesh.vertices protected for this
   //     ((WebGPUVertexData)vertices).bind(renderPass);  todo!!!!!

        //renderPass.setBindGroup( 0, bg.getHandle(), 0, JavaWebGPU.createNullPointer());

        if( getIndexData() != null) {
            renderPass.drawIndexed(size, numInstances, offset, 0, firstInstance);
        } else {
            renderPass.draw(size, numInstances, offset, firstInstance);
        }

    }

}
