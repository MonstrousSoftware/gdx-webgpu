package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.monstrous.gdx.webgpu.graphics.g3d.WgIndexBuffer;
import com.monstrous.gdx.webgpu.graphics.g3d.WgVertexBuffer;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class WgMesh extends Mesh {

    protected VertexData vertices; // we need to shadow Mesh.vertices which is package private

    protected WgMesh(VertexData vertices, IndexData indices, boolean isVertexArray) {
        super(vertices, indices, isVertexArray);
        this.vertices = vertices;
    }

    /**
     * Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as
     *            position, normal or texture coordinate
     */
    public WgMesh(boolean isStatic, int maxVertices, int maxIndices, VertexAttribute... attributes) {
        this(new WgVertexBuffer(isStatic, maxVertices, new VertexAttributes(attributes)),
                new WgIndexBuffer(isStatic, maxIndices), false);
    }

    /**
     * Creates a new Mesh with the given attributes.
     *
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. Each vertex attribute defines one property of a vertex such as
     *            position, normal or texture coordinate
     */
    public WgMesh(boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        this(new WgVertexBuffer(isStatic, maxVertices, attributes), new WgIndexBuffer(isStatic, maxIndices), false);
    }

    /**
     * Creates a new Mesh with the given attributes. Adds extra optimizations for dynamic (frequently modified) meshes.
     *
     * @param staticVertices whether vertices of this mesh are static or not. Allows for internal optimizations.
     * @param staticIndices whether indices of this mesh are static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}. Each vertex attribute defines one property of a vertex such as
     *            position, normal or texture coordinate
     *
     *            original author Jaroslaw Wisniewski (j.wisniewski@appsisle.com)
     **/
    public WgMesh(boolean staticVertices, boolean staticIndices, int maxVertices, int maxIndices,
            VertexAttributes attributes) {
        this(new WgVertexBuffer(staticVertices, maxVertices, attributes), new WgIndexBuffer(staticIndices, maxIndices),
                false);
    }

    /**
     * Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own
     * risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttribute}s. Each vertex attribute defines one property of a vertex such as
     *            position, normal or texture coordinate
     */
    public WgMesh(VertexDataType type, boolean isStatic, int maxVertices, int maxIndices,
            VertexAttribute... attributes) {
        this(new WgVertexBuffer(isStatic, maxVertices, new VertexAttributes(attributes)),
                new WgIndexBuffer(isStatic, maxIndices), false);
    }

    /**
     * Creates a new Mesh with the given attributes. This is an expert method with no error checking. Use at your own
     * risk.
     *
     * @param type the {@link VertexDataType} to be used, VBO or VA.
     * @param isStatic whether this mesh is static or not. Allows for internal optimizations.
     * @param maxVertices the maximum number of vertices this mesh can hold
     * @param maxIndices the maximum number of indices this mesh can hold
     * @param attributes the {@link VertexAttributes}.
     */
    public WgMesh(VertexDataType type, boolean isStatic, int maxVertices, int maxIndices, VertexAttributes attributes) {
        this(new WgVertexBuffer(isStatic, maxVertices, attributes), new WgIndexBuffer(isStatic, maxIndices), false);
    }

    // version that allows to request wide indices
    public WgMesh(boolean isStatic, int maxVertices, int maxIndices, boolean wideIndices, VertexAttributes attributes) {
        this(new WgVertexBuffer(isStatic, maxVertices, attributes),
                new WgIndexBuffer(isStatic, maxIndices, wideIndices), false);
    }

    public VertexData getVertexData() {
        return vertices;
    }

    @Override
    public void render(ShaderProgram shader, int primitiveType) {
        Gdx.app.error("WebGPUMesh", "render(ShaderProgram shader, ....) (ignored)");
    }

    @Override
    public void render(ShaderProgram shader, int primitiveType, int offset, int count, boolean autoBind) {
        Gdx.app.error("WebGPUMesh", "render(ShaderProgram shader, ....) (ignored)");
    }

    /**
     * New method for WebGPU rendering (uses a renderPass). note: primitiveType is ignored. Should be set in the
     * pipeline.
     */
    public void render(WebGPURenderPass renderPass, int primitiveType, int offset, int size, int numInstances,
            int firstInstance) {

        // bind vertices
        ((WgVertexBuffer) vertices).bind(renderPass);

        if (getIndexData().getNumIndices() > 0) { // is it an indexed mesh?
            ((WgIndexBuffer) getIndexData()).bind(renderPass);// bind indices
            renderPass.drawIndexed(size, numInstances, offset, 0, firstInstance);
        } else {
            renderPass.draw(size, numInstances, offset, firstInstance);
        }
    }

    private final Vector3 tmpV = new Vector3();

    private ShortBuffer index;
    private IntBuffer index32;

    private int getIndex(int i) {
        if (index != null)
            return (index.get(i) & 0xFFFF);
        else
            return index32.get(i);
    }

    /**
     * Extends the specified {@link BoundingBox} with the specified part.
     * 
     * @param out the bounding box to store the result in.
     * @param offset the start of the part.
     * @param count the size of the part.
     * @return the value specified by out.
     */
    @Override
    public BoundingBox extendBoundingBox(final BoundingBox out, int offset, int count, final Matrix4 transform) {
        final int numIndices = getNumIndices();
        final int numVertices = getNumVertices();
        final int max = numIndices == 0 ? numVertices : numIndices;
        if (offset < 0 || count < 1 || offset + count > max)
            throw new GdxRuntimeException(
                    "Invalid part specified ( offset=" + offset + ", count=" + count + ", max=" + max + " )");

        final FloatBuffer verts = vertices.getBuffer(false);
        WgIndexBuffer indexBuffer = (WgIndexBuffer) getIndexData();
        if (indexBuffer.getIndexSize() == 2)
            index = indexBuffer.getBuffer(false);
        else
            index32 = indexBuffer.getIntBuffer(false);
        final VertexAttribute posAttrib = getVertexAttribute(VertexAttributes.Usage.Position);
        final int posoff = posAttrib.offset / 4;
        final int vertexSize = vertices.getAttributes().vertexSize / 4;
        final int end = offset + count;

        switch (posAttrib.numComponents) {
            case 1:
                if (numIndices > 0) {
                    for (int i = offset; i < end; i++) {
                        final int idx = getIndex(i) * vertexSize + posoff;
                        tmpV.set(verts.get(idx), 0, 0);
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                } else {
                    for (int i = offset; i < end; i++) {
                        final int idx = i * vertexSize + posoff;
                        tmpV.set(verts.get(idx), 0, 0);
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                }
                break;
            case 2:
                if (numIndices > 0) {
                    for (int i = offset; i < end; i++) {
                        final int idx = getIndex(i) * vertexSize + posoff;
                        tmpV.set(verts.get(idx), verts.get(idx + 1), 0);
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                } else {
                    for (int i = offset; i < end; i++) {
                        final int idx = i * vertexSize + posoff;
                        tmpV.set(verts.get(idx), verts.get(idx + 1), 0);
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                }
                break;
            case 3:
                if (numIndices > 0) {
                    for (int i = offset; i < end; i++) {
                        final int idx = getIndex(i) * vertexSize + posoff;
                        tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2));
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                } else {
                    for (int i = offset; i < end; i++) {
                        final int idx = i * vertexSize + posoff;
                        tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2));
                        if (transform != null)
                            tmpV.mul(transform);
                        out.ext(tmpV);
                    }
                }
                break;
        }
        return out;
    }

    /**
     * Calculates the squared radius of the bounding sphere around the specified center for the specified part.
     * 
     * @param centerX The X coordinate of the center of the bounding sphere
     * @param centerY The Y coordinate of the center of the bounding sphere
     * @param centerZ The Z coordinate of the center of the bounding sphere
     * @param offset the start index of the part.
     * @param count the amount of indices the part contains.
     * @return the squared radius of the bounding sphere.
     */
    @Override
    public float calculateRadiusSquared(final float centerX, final float centerY, final float centerZ, int offset,
            int count, final Matrix4 transform) {
        int numIndices = getNumIndices();
        if (offset < 0 || count < 1 || offset + count > numIndices)
            throw new GdxRuntimeException("Not enough indices");

        final FloatBuffer verts = vertices.getBuffer(false);
        WgIndexBuffer indexBuffer = (WgIndexBuffer) getIndexData();
        if (indexBuffer.getIndexSize() == 2)
            index = indexBuffer.getBuffer(false);
        else
            index32 = indexBuffer.getIntBuffer(false);
        final VertexAttribute posAttrib = getVertexAttribute(VertexAttributes.Usage.Position);
        final int posoff = posAttrib.offset / 4;
        final int vertexSize = vertices.getAttributes().vertexSize / 4;
        final int end = offset + count;

        float result = 0;

        switch (posAttrib.numComponents) {
            case 1:
                for (int i = offset; i < end; i++) {
                    final int idx = getIndex(i) * vertexSize + posoff;
                    tmpV.set(verts.get(idx), 0, 0);
                    if (transform != null)
                        tmpV.mul(transform);
                    final float r = tmpV.sub(centerX, centerY, centerZ).len2();
                    if (r > result)
                        result = r;
                }
                break;
            case 2:
                for (int i = offset; i < end; i++) {
                    final int idx = getIndex(i) * vertexSize + posoff;
                    tmpV.set(verts.get(idx), verts.get(idx + 1), 0);
                    if (transform != null)
                        tmpV.mul(transform);
                    final float r = tmpV.sub(centerX, centerY, centerZ).len2();
                    if (r > result)
                        result = r;
                }
                break;
            case 3:
                for (int i = offset; i < end; i++) {
                    final int idx = getIndex(i) * vertexSize + posoff;
                    tmpV.set(verts.get(idx), verts.get(idx + 1), verts.get(idx + 2));
                    if (transform != null)
                        tmpV.mul(transform);
                    final float r = tmpV.sub(centerX, centerY, centerZ).len2();
                    if (r > result)
                        result = r;
                }
                break;
        }
        return result;
    }

}
