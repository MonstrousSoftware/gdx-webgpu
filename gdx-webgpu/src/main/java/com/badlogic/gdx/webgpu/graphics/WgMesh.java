package com.badlogic.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.webgpu.graphics.g3d.WgIndexData;
import com.badlogic.gdx.webgpu.graphics.g3d.WgVertexData;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.graphics.glutils.*;

public class WgMesh extends Mesh {

    protected VertexData vertices;  // we need to shadow Mesh.vertices which is package private

    protected WgMesh(VertexData vertices, IndexData indices, boolean isVertexArray) {
        super(vertices, indices, isVertexArray);
        this.vertices = vertices;
    }

    /** Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WgMesh(boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
        this(new WgVertexData(maxVertices,new VertexAttributes(attributes)), new WgIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WgMesh(boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        this(new WgVertexData(maxVertices, attributes), new WgIndexData(maxIndices), false);
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
    public WgMesh(boolean staticVertices, boolean staticIndices, int maxVertices, int maxIndices, VertexAttributes attributes) {
        this(new WgVertexData(maxVertices, attributes), new WgIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as position,
     *           normal or texture coordinate */
    public WgMesh(VertexDataType type, boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
        this(new WgVertexData(maxVertices,new VertexAttributes(attributes)), new WgIndexData(maxIndices), false);
    }

    /** Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. */
    public WgMesh(VertexDataType type, boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        this(new WgVertexData(maxVertices, attributes), new WgIndexData(maxIndices), false);
    }

    public VertexData getVertexData(){
        return vertices;
    }

    @Override
    public void render (ShaderProgram shader, int primitiveType, int offset, int count, boolean autoBind){
        Gdx.app.error("WebGPUMesh", "render(ShaderProgram shader, ....) (ignored)");
    }

    /** New method for WebGPU rendering (uses a renderPass).
     * note: primitiveType is ignored. Should be set in the pipeline.
     */
    public void render (WebGPURenderPass renderPass, int primitiveType, int offset, int size, int numInstances, int firstInstance){

        // bind vertices
        ((WgVertexData)vertices).bind(renderPass);

        if( getIndexData() != null) {   // is it an indexed mesh?
            ((WgIndexData)getIndexData()).bind(renderPass);// bind indices
            renderPass.drawIndexed(size, numInstances, offset, 0, firstInstance);
        } else {
            renderPass.draw(size, numInstances, offset, firstInstance);
        }

    }

}
