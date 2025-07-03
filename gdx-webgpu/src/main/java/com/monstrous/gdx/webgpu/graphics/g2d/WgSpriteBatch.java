package com.monstrous.gdx.webgpu.graphics.g2d;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Null;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Class to render textured rectangles in batches.
 */
public class WgSpriteBatch implements Batch {

    private final static int VERTS_PER_SPRITE = 4;
    private final static int INDICES_PER_SPRITE = 6;

    private final WgGraphics gfx;
    private final WebGPUApplication webgpu;
    private final WgShaderProgram specificShader;
    private final int maxSprites;
    private boolean drawing;
    private final int vertexSize;
    private final ByteBuffer vertexBB;
    private final FloatBuffer vertexData;     // float buffer view on byte buffer
    public int numSprites;
    private final Color tint;
    private float tintPacked;
    private WebGPUVertexBuffer vertexBuffer;
    private WebGPUIndexBuffer indexBuffer;
    private WebGPUUniformBuffer uniformBuffer;
    private final WebGPUBindGroupLayout bindGroupLayout;
    private final VertexAttributes vertexAttributes;
    private final WebGPUPipelineLayout pipelineLayout;
    private final PipelineSpecification pipelineSpec;
    private int uniformBufferSize;
    private WgTexture lastTexture;
    private final Matrix4 projectionMatrix;
    private final Matrix4 transformMatrix;
    private final Matrix4 combinedMatrix;
    private final Matrix4 shiftDepthMatrix;
    private WebGPURenderPass renderPass;
    private int vbOffset;
    private final PipelineCache pipelines;
    private WebGPUPipeline prevPipeline;
    public int maxSpritesInBatch;    // most nr of sprites in the batch over its lifetime
    public int renderCalls;
    public int pipelineCount;
    public int numFlushes;          // number of flushes since begin()
    public int maxFlushes;
    private float invTexWidth;
    private float invTexHeight;
    private final Map<Integer, WGPUBlendFactor> blendConstantMap = new HashMap<>(); // mapping GL vs WebGPU constants
    private final Map<WGPUBlendFactor, Integer> blendGLConstantMap = new HashMap<>(); // vice versa
    private final Binder binder;
    private static String defaultShader;

    public WgSpriteBatch() {
        this(1000); // default nr
    }

    public WgSpriteBatch(int maxSprites) {
        this(maxSprites, null);
    }

    /** Create a SpriteBatch.
     *
     * @param maxSprites        maximum number of sprite to be supported (default is 1000)
     * @param specificShader    specific ShaderProgram to use, must be compatible with "sprite.wgsl". Leave null to use the default shader.
     */
    public WgSpriteBatch(int maxSprites, WgShaderProgram specificShader) {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        this.maxSprites = maxSprites;
        this.specificShader = specificShader;

        vertexAttributes = new VertexAttributes(
                            new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE),
                            VertexAttribute.ColorPacked(),
                            VertexAttribute.TexCoords(0) );

        // vertex: x, y, rgba, u, v
        vertexSize = vertexAttributes.vertexSize; // bytes

        initBlendMap(); // fill constants mapping table

        // allow for a different projectionView matrix per flush.
        maxFlushes = 100;

        // allocate data buffers based on default vertex attributes which are assumed to be the worst case.
        // i.e. with setVertexAttributes() you can specify a subset
        createBuffers(maxFlushes);
        fillIndexBuffer(maxSprites);

        // Create FloatBuffer to hold vertex data per batch, is reset every flush
        vertexBB = BufferUtils.newUnsafeByteBuffer(maxSprites * VERTS_PER_SPRITE * vertexSize);
        vertexBB.order(ByteOrder.LITTLE_ENDIAN);
        // important, webgpu expects little endian.  ByteBuffer defaults to big endian.
        vertexData = vertexBB.asFloatBuffer();

        projectionMatrix = new Matrix4();
        transformMatrix = new Matrix4();
        combinedMatrix = new Matrix4();

        // matrix which will transform an opengl ortho matrix to a webgpu ortho matrix
        // by scaling the Z range from [-1..1] to [0..1]
        shiftDepthMatrix = new Matrix4().idt().scl(1,1,-0.5f).trn(0,0,0.5f);

        tint = new Color(Color.WHITE);

        invTexWidth = 0f;
        invTexHeight = 0f;

        bindGroupLayout = createBindGroupLayout();

        binder = new Binder();
        // define group
        binder.defineGroup(0, bindGroupLayout);
        // define bindings in the group
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("texture", 0, 1);
        binder.defineBinding("textureSampler", 0, 2);
        // define uniforms in uniform buffer (binding 0) with their offset
        binder.defineUniform("projectionViewTransform", 0, 0, 0);

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);
        // bindings 1 and 2 are done in switchTexture()

        // get pipeline layout which aggregates all the bind group layouts
        pipelineLayout = binder.getPipelineLayout("SpriteBatch pipeline layout");

        pipelines = new PipelineCache();
        pipelineSpec = new PipelineSpecification(vertexAttributes, this.specificShader);
        pipelineSpec.name = "SpriteBatch pipeline";

        // default blending values
        pipelineSpec.enableBlending();
        pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);

        // use provided (compiled) shader or else use default shader (source)
        // this can be overruled with setShader()
        pipelineSpec.shader = specificShader;
        if(specificShader == null) {
            pipelineSpec.shaderSource = getDefaultShaderSource();
        }

        projectionMatrix.setToOrtho2D(0, 0, Gdx.graphics.getWidth(),  Gdx.graphics.getHeight(), 0, 100);
        transformMatrix.idt();

        drawing = false;
    }

    // the index buffer is fixed and only has to be filled on start-up
    private void fillIndexBuffer(int maxSprites){
        ByteBuffer bb = BufferUtils.newUnsafeByteBuffer(maxSprites*INDICES_PER_SPRITE*Short.BYTES);
        ShortBuffer indexData = bb.asShortBuffer();
        for(int i = 0; i < maxSprites; i++){
            short vertexOffset = (short)(i * 4);
            // two triangles per sprite
            indexData.put(vertexOffset);
            indexData.put((short)(vertexOffset + 1));
            indexData.put((short)(vertexOffset + 2));

            indexData.put(vertexOffset);
            indexData.put((short)(vertexOffset + 2));
            indexData.put((short)(vertexOffset + 3));
        }
        indexData.flip();
        indexBuffer.setIndices(bb);
        BufferUtils.disposeUnsafeByteBuffer(bb);
    }


    public void setColor(float r, float g, float b, float a){
        tint.set(r,g,b,a);
    }

    public void setColor(Color color){
        tint.set(color);
    }

    public Color getColor() {
        return tint;
    }

    @Override
    public void setPackedColor (float packedColor) {
        Color.abgr8888ToColor(tint, packedColor);
        this.tintPacked = packedColor;
    }

    @Override
    public float getPackedColor () {
        return tintPacked;
    }


    public void setBlendFactor(WGPUBlendFactor srcFunc, WGPUBlendFactor dstFunc) {
        pipelineSpec.setBlendFactorSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    public void setBlendFactorSeparate(WGPUBlendFactor srcFuncColor, WGPUBlendFactor dstFuncColor, WGPUBlendFactor srcFuncAlpha, WGPUBlendFactor dstFuncAlpha) {
        if (pipelineSpec.getBlendSrcFactor() == srcFuncColor && pipelineSpec.getBlendDstFactor() == dstFuncColor &&
            pipelineSpec.getBlendSrcFactorAlpha() == srcFuncAlpha && pipelineSpec.getBlendDstFactorAlpha() == dstFuncAlpha )
            return;

        flush();
        pipelineSpec.setBlendFactorSeparate(srcFuncColor, dstFuncColor, srcFuncAlpha, dstFuncAlpha);
        setPipeline();
    }


    public WGPUBlendFactor getBlendSrcFactor() {
        return pipelineSpec.getBlendSrcFactor();
    }

    public WGPUBlendFactor getBlendDstFactor() {
        return pipelineSpec.getBlendDstFactor();
    }

    public WGPUBlendFactor getBlendSrcFactorAlpha() {
        return pipelineSpec.getBlendSrcFactorAlpha();
    }

    public WGPUBlendFactor getBlendDstFactorAlpha() {
        return pipelineSpec.getBlendDstFactorAlpha();
    }

    // for compatibility with GL based methods
    public void setBlendFunction(int srcFunc, int dstFunc) {
        setBlendFunctionSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    public void setBlendFunctionSeparate(int srcFuncColor, int dstFuncColor, int srcFuncAlpha, int dstFuncAlpha) {
        WGPUBlendFactor srcFactorColor = blendConstantMap.get(srcFuncColor);
        WGPUBlendFactor dstFactorColor = blendConstantMap.get(dstFuncColor);
        WGPUBlendFactor srcFactorAlpha = blendConstantMap.get(srcFuncAlpha);
        WGPUBlendFactor dstFactorAlpha = blendConstantMap.get(dstFuncAlpha);
        if (pipelineSpec.getBlendSrcFactor() == srcFactorColor && pipelineSpec.getBlendDstFactor() == dstFactorColor &&
                pipelineSpec.getBlendSrcFactorAlpha() == srcFactorAlpha && pipelineSpec.getBlendDstFactorAlpha() == dstFactorAlpha )
            return;

        flush();
        pipelineSpec.setBlendFactorSeparate(srcFactorColor, dstFactorColor, srcFactorAlpha, dstFactorAlpha);
        setPipeline();
    }


    public int getBlendSrcFunc() {
        return blendGLConstantMap.get(pipelineSpec.getBlendSrcFactor());
    }


    public int getBlendDstFunc() {
        return blendGLConstantMap.get(pipelineSpec.getBlendDstFactor());
    }


    public int getBlendSrcFuncAlpha() {
        return blendGLConstantMap.get(pipelineSpec.getBlendSrcFactorAlpha());
    }

    public int getBlendDstFuncAlpha() {
        return blendGLConstantMap.get(pipelineSpec.getBlendDstFactorAlpha());
    }

    public void enableBlending(){
        if(pipelineSpec.isBlendingEnabled())
            return;
        flush();
        pipelineSpec.enableBlending();
    }
    public void disableBlending(){
        if(!pipelineSpec.isBlendingEnabled())
            return;
        flush();
        pipelineSpec.disableBlending();
    }

    public boolean isBlendingEnabled(){
        return pipelineSpec.isBlendingEnabled();
    }

    public boolean isDrawing () {
        return drawing;
    }

    public void begin(){
        begin(null);
    }

    public void begin(Color clearColor) {
        renderPass = RenderPassBuilder.create("SpriteBatch", clearColor, webgpu.getSamples());
        Rectangle view = webgpu.getViewportRectangle();
        renderPass.setViewport(view.x, view.y, view.width, view.height, 0, 1);


        if (drawing)
            throw new RuntimeException("Must end() before begin()");

        numSprites = 0;
        vbOffset = 0;
        vertexData.clear();
        maxSpritesInBatch = 0;
        renderCalls = 0;
        numFlushes = 0;
        uniformBuffer.setDynamicOffsetIndex(numFlushes);

        prevPipeline = null;

        // set default state
        tint.set(Color.WHITE);

        pipelineSpec.enableBlending();
        pipelineSpec.disableDepthTest();

        pipelineSpec.vertexAttributes = vertexAttributes;
        pipelineSpec.numSamples = webgpu.getSamples();

        // don't reset the matrices because setProjectionMatrix() and setTransformMatrix()
        // may be called before begin() and need to be respected.


        drawing = true;
    }

    protected void switchTexture (Texture texture) {
        flush();
        if(!(texture instanceof WgTexture))
            throw new IllegalArgumentException("texture must be WebGPUTexture");
        lastTexture = (WgTexture)texture;
        invTexWidth = 1.0f / texture.getWidth();
        invTexHeight = 1.0f / texture.getHeight();

        binder.setTexture("texture",lastTexture.getTextureView());
        binder.setSampler("textureSampler",lastTexture.getSampler());
    }

    public void flush() {
        if(numSprites == 0)
            return;
        if(numSprites > maxSpritesInBatch)
            maxSpritesInBatch = numSprites;
        if(numFlushes >= maxFlushes){
            Gdx.app.error("WebGPUSpriteBatch", "too many flushes");
            return;
        }
        renderCalls++;

        setPipeline();

        // bind group
        updateMatrices();
        renderPass.setBindGroup( 0, binder.getBindGroup(0).getHandle(), numFlushes*uniformBuffer.getUniformStride());

        // append new vertex data to GPU vertex buffer
        int numBytes = numSprites * VERTS_PER_SPRITE * vertexSize;
        vertexBuffer.setVertices(vertexBB, vbOffset, numBytes);

        // Set vertex buffer while encoding the render pass
        // use an offset to set the vertex buffer for this batch
        renderPass.setVertexBuffer( 0, vertexBuffer.getHandle(), vbOffset, numBytes);
        renderPass.setIndexBuffer( indexBuffer.getHandle(), WGPUIndexFormat.Uint16, 0, (long) numSprites *6*Short.BYTES);

        renderPass.drawIndexed( numSprites *6, 1, 0, 0, 0);

        //bg.release();

        vbOffset += numBytes;
        vertexData.clear(); // reset fill position for next batch
        numSprites = 0;   // reset
        numFlushes++;
        uniformBuffer.setDynamicOffsetIndex(numFlushes);
    }

    public void end() {
        if (!drawing) // catch incorrect usage
            throw new RuntimeException("Cannot end() without begin()");
        drawing = false;
        flush();
        renderPass.end();
        renderPass = null;
        pipelineCount = pipelines.size();   // statistics
    }

    // create or reuse pipeline on demand to match the pipeline spec
    private void setPipeline() {
        WebGPUPipeline pipeline = pipelines.findPipeline( pipelineLayout.getHandle(), pipelineSpec);
        if (pipeline != prevPipeline) { // avoid unneeded switches
            renderPass.setPipeline(pipeline.getHandle());
            prevPipeline = pipeline;
        }
    }

    /** Set shader to use instead of the default shader or the shader provided
     * in the constructor.
     * Use null to reset to default shader
     */
    public void setShader(@Null WgShaderProgram shaderProgram) {
        if(pipelineSpec.shader == shaderProgram) return;
        if(drawing)
            flush();
        if (shaderProgram == null) {
            pipelineSpec.shader = specificShader;
            if (specificShader == null)
                pipelineSpec.shaderSource = getDefaultShaderSource();
            pipelineSpec.recalcHash();
        }
        else {
            pipelineSpec.shader = shaderProgram;
            pipelineSpec.shaderSource = "precompiled"; //shaderProgram.getName();   // todo
            pipelineSpec.recalcHash();
        }
    }



    @Override
    public Matrix4 getProjectionMatrix() {
        return projectionMatrix;
    }

    @Override
    public Matrix4 getTransformMatrix() {
        return transformMatrix;
    }


    /** Set projection matrix.
     * Expects an OpenGL standard projection matrix, i.e. mapping Z to [-1 .. 1]
     *
     */
    @Override
    public void setProjectionMatrix(Matrix4 projection) {
        if(drawing)
            flush();
        projectionMatrix.set(projection);
    }

    @Override
    public void setTransformMatrix(Matrix4 transform) {
        if(drawing)
            flush();
        transformMatrix.set(transform);
    }

    @Override
    public void setShader(ShaderProgram shader) {
        throw new IllegalStateException("not implemented, provide WebGPUShaderProgram");
    }

    @Override
    public ShaderProgram getShader() {
        return null;
    }

    public void draw(Texture texture, float x, float y) {
        draw(texture, x, y, texture.getWidth(), texture.getHeight());
    }

    public void draw(Texture texture, float x, float y, float w, float h){
        this.draw(texture, x, y, w, h, 0f, 1f, 1f, 0f);
    }

    public void draw(TextureRegion region, float x, float y){
        // note: v2 is top of glyph, v the bottom
        draw(region, x, y, region.getRegionWidth(), region.getRegionHeight());
    }

    private boolean check() {
        if (!drawing)
            throw new RuntimeException("SpriteBatch: Must call begin() before draw().");
        if (numSprites == maxSprites) {
            Gdx.app.error("WebGPUSpriteBatch", "Too many sprites (more than " + maxSprites + "). Enlarge maxSprites.");
            return false;
        }
        return true;
    }


    public void draw(TextureRegion region, float x, float y, float w, float h){
        if(!check()) return;

        if(region.getTexture() != lastTexture) { // changing texture, need to flush what we have so far
            switchTexture(region.getTexture());
        }
        addRect(x, y, w, h, region.getU(), region.getV2(), region.getU2(), region.getV());  // flip v and v2
        numSprites++;
    }


    public void draw (Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
        if(!check()) return;

        if(texture != lastTexture) { // changing texture, need to flush what we have so far
            switchTexture(texture);
        }
        addRect(x, y, width, height, u, v, u2, v2);
        numSprites++;
    }


    public void draw (Texture texture, float x, float y, float originX, float originY, float width, float height, float scaleX,
                      float scaleY, float rotation, int srcX, int srcY, int srcWidth, int srcHeight, boolean flipX, boolean flipY) {
        if(!check()) return;

        if(texture != lastTexture) { // changing texture, need to flush what we have so far
            switchTexture(texture);
        }

//        if (idx == vertices.length) //
//            flush();

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }
        addVertex(x1, y1, u, v);
        addVertex(x2, y2, u, v2);
        addVertex(x3, y3, u2, v2);
        addVertex(x4, y4, u2, v);
        numSprites++;
    }



    public void draw (Texture texture, float x, float y, float width, float height, int srcX, int srcY, int srcWidth,
                      int srcHeight, boolean flipX, boolean flipY) {
        if(!check()) return;

        if (texture != lastTexture)
            switchTexture(texture);

        float u = srcX * invTexWidth;
        float v = (srcY + srcHeight) * invTexHeight;
        float u2 = (srcX + srcWidth) * invTexWidth;
        float v2 = srcY * invTexHeight;
        final float fx2 = x + width;
        final float fy2 = y + height;

        if (flipX) {
            float tmp = u;
            u = u2;
            u2 = tmp;
        }

        if (flipY) {
            float tmp = v;
            v = v2;
            v2 = tmp;
        }

        addVertex(x, y, u, v);
        addVertex(x, fy2, u, v2);
        addVertex(fx2,fy2, u2, v2);
        addVertex(fx2, y, u2, v);
        numSprites++;
    }


    public void draw (Texture texture, float x, float y, int srcX, int srcY, int srcWidth, int srcHeight) {
        if(!check()) return;

        if (texture != lastTexture)
            switchTexture(texture);

        final float u = srcX * invTexWidth;
        final float v = (srcY + srcHeight) * invTexHeight;
        final float u2 = (srcX + srcWidth) * invTexWidth;
        final float v2 = srcY * invTexHeight;
        final float fx2 = x + srcWidth;
        final float fy2 = y + srcHeight;

        addVertex(x, y, u, v);
        addVertex(x, fy2, u, v2);
        addVertex(fx2,fy2, u2, v2);
        addVertex(fx2, y, u2, v);
        numSprites++;
    }


//    public void draw (Texture texture, float x, float y, float width, float height, float u, float v, float u2, float v2) {
//        if (!drawing) throw new IllegalStateException("SpriteBatch.begin must be called before draw.");
//
//        if (texture != lastTexture)
//            switchTexture(texture);
//        final float fx2 = x + width;
//        final float fy2 = y + height;
//
//        addVertex(x, y, u, v);
//        addVertex(x, fy2, u, v2);
//        addVertex(fx2,fy2, u2, v2);
//        addVertex(fx2, y, u2, v);
//    }

//    @Override
//    public void draw (Texture texture, float x, float y) {
//        draw(texture, x, y, texture.getWidth(), texture.getHeight());
//    }

    // used by Sprite class and BitmapFont
    public void draw(Texture texture, float[] spriteVertices, int offset, int numFloats){
        if(!check()) return;

        if(texture != lastTexture) { // changing texture, need to flush what we have so far
            switchTexture(texture);
        }
        int remaining = 20*(maxSprites - numSprites);
        if(numFloats > remaining)   // avoid buffer overflow by truncating as needed
            numFloats = remaining;
        vertexData.put(spriteVertices, offset, numFloats);
        numSprites += numFloats/20;
    }


    public void draw (TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                      float scaleX, float scaleY, float rotation) {
        if(!check()) return;
        Texture texture = region.getTexture();
        if (texture != lastTexture)
            switchTexture(texture);

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        final float u = region.getU();
        final float v = region.getV2();
        final float u2 = region.getU2();
        final float v2 = region.getV();

        addVertex(x1, y1, u, v);
        addVertex(x2, y2, u, v2);
        addVertex(x3, y3, u2, v2);
        addVertex(x4, y4, u2, v);
        numSprites++;
    }


    public void draw (TextureRegion region, float x, float y, float originX, float originY, float width, float height,
                      float scaleX, float scaleY, float rotation, boolean clockwise) {
        if(!check()) return;
        Texture texture = region.getTexture();
        if (texture != lastTexture)
            switchTexture(texture);

        // bottom left and top right corner points relative to origin
        final float worldOriginX = x + originX;
        final float worldOriginY = y + originY;
        float fx = -originX;
        float fy = -originY;
        float fx2 = width - originX;
        float fy2 = height - originY;

        // scale
        if (scaleX != 1 || scaleY != 1) {
            fx *= scaleX;
            fy *= scaleY;
            fx2 *= scaleX;
            fy2 *= scaleY;
        }

        // construct corner points, start from top left and go counter clockwise
        final float p1x = fx;
        final float p1y = fy;
        final float p2x = fx;
        final float p2y = fy2;
        final float p3x = fx2;
        final float p3y = fy2;
        final float p4x = fx2;
        final float p4y = fy;

        float x1;
        float y1;
        float x2;
        float y2;
        float x3;
        float y3;
        float x4;
        float y4;

        // rotate
        if (rotation != 0) {
            final float cos = MathUtils.cosDeg(rotation);
            final float sin = MathUtils.sinDeg(rotation);

            x1 = cos * p1x - sin * p1y;
            y1 = sin * p1x + cos * p1y;

            x2 = cos * p2x - sin * p2y;
            y2 = sin * p2x + cos * p2y;

            x3 = cos * p3x - sin * p3y;
            y3 = sin * p3x + cos * p3y;

            x4 = x1 + (x3 - x2);
            y4 = y3 - (y2 - y1);
        } else {
            x1 = p1x;
            y1 = p1y;

            x2 = p2x;
            y2 = p2y;

            x3 = p3x;
            y3 = p3y;

            x4 = p4x;
            y4 = p4y;
        }

        x1 += worldOriginX;
        y1 += worldOriginY;
        x2 += worldOriginX;
        y2 += worldOriginY;
        x3 += worldOriginX;
        y3 += worldOriginY;
        x4 += worldOriginX;
        y4 += worldOriginY;

        float u1, v1, u2, v2, u3, v3, u4, v4;
        if (clockwise) {
            u1 = region.getU2();
            v1 = region.getV2();
            u2 = region.getU();
            v2 = region.getV2();
            u3 = region.getU();
            v3 = region.getV();
            u4 = region.getU2();
            v4 = region.getV();
        } else {
            u1 = region.getU();
            v1 = region.getV();
            u2 = region.getU2();
            v2 = region.getV();
            u3 = region.getU2();
            v3 = region.getV2();
            u4 = region.getU();
            v4 = region.getV2();
        }

        addVertex(x1, y1, u1, v1);
        addVertex(x2, y2, u2, v2);
        addVertex(x3, y3, u3, v3);
        addVertex(x4, y4, u4, v4);
        numSprites++;
    }


    public void draw (TextureRegion region, float width, float height, Affine2 transform) {
        if(!check()) return;
        Texture texture = region.getTexture();
        if (texture != lastTexture)
            switchTexture(texture);

        // construct corner points
        float x1 = transform.m02;
        float y1 = transform.m12;
        float x2 = transform.m01 * height + transform.m02;
        float y2 = transform.m11 * height + transform.m12;
        float x3 = transform.m00 * width + transform.m01 * height + transform.m02;
        float y3 = transform.m10 * width + transform.m11 * height + transform.m12;
        float x4 = transform.m00 * width + transform.m02;
        float y4 = transform.m10 * width + transform.m12;

        float u = region.getU();
        float v = region.getV2();
        float u2 = region.getU2();
        float v2 = region.getV();

        addVertex(x1, y1, u, v);
        addVertex(x2, y2, u, v2);
        addVertex(x3, y3, u2, v2);
        addVertex(x4, y4, u2, v);
        numSprites++;
    }




    private void addRect(float x, float y, float w, float h, float u, float v, float u2, float v2) {
        addVertex(x, y, u, v);
        addVertex(x, y+h, u, v2);
        addVertex(x+w, y+h, u2, v2);
        addVertex(x+w, y, u2, v);
    }

    private void addVertex(float x, float y, float u, float v) {
        boolean hasColor = (vertexAttributes.getMask() & VertexAttributes.Usage.ColorPacked) != 0;
        boolean hasUV = (vertexAttributes.getMask() & VertexAttributes.Usage.TextureCoordinates) != 0;
        float col = tint.toFloatBits();


        vertexData.put(x);
        vertexData.put(y);
        if (hasColor) {
            vertexData.put(col);
        }
        if (hasUV) {
            vertexData.put(u);
            vertexData.put(v);
        }


    }


    private void createBuffers(int maxFlushes) {
        int indexSize = maxSprites * INDICES_PER_SPRITE * Short.BYTES;

        // Create vertex buffer and index buffer
        vertexBuffer = new WebGPUVertexBuffer(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Vertex, (long) maxSprites * 4 * vertexSize);
        indexBuffer = new WebGPUIndexBuffer(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Index, indexSize, Short.BYTES);

        // Create uniform buffer with dynamic offset for the view projection matrix
        // dynamic offset will be incremented per flush so that it can have a specific view projection matrix
        uniformBufferSize = 16 * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize,WGPUBufferUsage.CopyDst |WGPUBufferUsage.Uniform, maxFlushes  );
    }

    private void updateMatrices(){
        combinedMatrix.set(shiftDepthMatrix).mul(projectionMatrix).mul(transformMatrix);
        binder.setUniform("projectionViewTransform", combinedMatrix);
    }

    private WebGPUBindGroupLayout createBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("SpriteBatch bind group layout");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.Uniform, uniformBufferSize, true);
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );

        layout.end();
        return layout;
    }




    @Override
    public void dispose(){
        binder.dispose();
        pipelines.dispose();
        vertexBuffer.dispose();
        indexBuffer.dispose();
        uniformBuffer.dispose();
        bindGroupLayout.dispose();
        //pipelineLayout.dispose();
        BufferUtils.disposeUnsafeByteBuffer(vertexBB);
    }


    private String getDefaultShaderSource() {
        if (defaultShader == null)
            defaultShader = Gdx.files.classpath("shaders/spritebatch.wgsl").readString();
        return defaultShader;
    }

    private void initBlendMap(){
        blendConstantMap.put(GL20.GL_ZERO, WGPUBlendFactor.Zero);
        blendConstantMap.put(GL20.GL_ONE, WGPUBlendFactor.One);
        blendConstantMap.put(GL20.GL_SRC_ALPHA, WGPUBlendFactor.SrcAlpha);
        blendConstantMap.put(GL20.GL_ONE_MINUS_SRC_ALPHA, WGPUBlendFactor.OneMinusSrcAlpha);
        blendConstantMap.put(GL20.GL_DST_ALPHA, WGPUBlendFactor.DstAlpha);
        blendConstantMap.put(GL20.GL_ONE_MINUS_DST_ALPHA, WGPUBlendFactor.OneMinusDstAlpha);
        blendConstantMap.put(GL20.GL_SRC_COLOR, WGPUBlendFactor.Src);
        blendConstantMap.put(GL20.GL_ONE_MINUS_SRC_COLOR, WGPUBlendFactor.OneMinusSrc);
        blendConstantMap.put(GL20.GL_DST_COLOR, WGPUBlendFactor.Dst);
        blendConstantMap.put(GL20.GL_ONE_MINUS_DST_COLOR, WGPUBlendFactor.OneMinusDst);
        blendConstantMap.put(GL20.GL_SRC_ALPHA_SATURATE, WGPUBlendFactor.SrcAlphaSaturated);

        // and build the inverse mapping
        for(int key : blendConstantMap.keySet()){
            WGPUBlendFactor factor = blendConstantMap.get(key);
            blendGLConstantMap.put(factor, key);
        }
    }
}
