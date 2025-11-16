package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Rectangle;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;

import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Fake implementation of GL20 The principal purpose is to prevent crashes if some low-level code calls GL functions,
 * e.g. ScissorStack which is called by ScrollPane. Generally the GL functions will just be ignored, because we are not
 * trying to emulate OpenGL.
 */
public class WgGL20 implements GL20 {

    @Override
    public void glViewport(int x, int y, int width, int height) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        Rectangle view = webgpu.getViewportRectangle();
        if (x != view.x || y != view.y || width != view.width || height != view.height) {
            // Gdx.app.log("glViewport", "x=" + x + " y=" + y + " w=" + width + " h=" + height);
            webgpu.setViewportRectangle(x, y, width, height);
        }
    }

    @Override
    public void glScissor(int x, int y, int width, int height) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        // note: we are not testing for glEnable(GL_SCISSOR_TEST)/glDisable(GL_SCISSOR_TEST)

        Rectangle scissor = webgpu.getScissor();
        if (x != scissor.x || y != scissor.y || width != scissor.width || height != scissor.height) {
            // Gdx.app.log("glScissor", "x=" + x + " y=" + y + " w=" + width + " h=" + height);
            webgpu.setScissor(x, y, width, height);
        }
    }

    @Override
    public void glEnable(int cap) {
        if (cap == GL20.GL_SCISSOR_TEST) {
            WgGraphics gfx = (WgGraphics) Gdx.graphics;
            WebGPUContext webgpu = gfx.getContext();
            webgpu.enableScissor(true);
        }
    }

    @Override
    public void glDisable(int cap) {
        if (cap == GL20.GL_SCISSOR_TEST) {
            WgGraphics gfx = (WgGraphics) Gdx.graphics;
            WebGPUContext webgpu = gfx.getContext();
            webgpu.enableScissor(false);
        }
    }

    // only empty placeholders below here ==================================
    private void error() {
        Gdx.app.error("gdx-webgpu", "GL methods not supported.");
    }

    @Override
    public void glActiveTexture(int texture) {
        error();
    }

    @Override
    public void glBindTexture(int target, int texture) {
        error();
    }

    @Override
    public void glBlendFunc(int sfactor, int dfactor) {
        error();
    }

    @Override
    public void glClear(int mask) {
        error();
    }

    @Override
    public void glClearColor(float red, float green, float blue, float alpha) {
        error();
    }

    @Override
    public void glClearDepthf(float depth) {
        error();
    }

    @Override
    public void glClearStencil(int s) {
        error();
    }

    @Override
    public void glColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        error();
    }

    @Override
    public void glCompressedTexImage2D(int target, int level, int internalformat, int width, int height, int border,
            int imageSize, Buffer data) {
        error();
    }

    @Override
    public void glCompressedTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height,
            int format, int imageSize, Buffer data) {
        error();
    }

    @Override
    public void glCopyTexImage2D(int target, int level, int internalformat, int x, int y, int width, int height,
            int border) {
        error();
    }

    @Override
    public void glCopyTexSubImage2D(int target, int level, int xoffset, int yoffset, int x, int y, int width,
            int height) {
        error();
    }

    @Override
    public void glCullFace(int mode) {
        error();
    }

    @Override
    public void glDeleteTextures(int n, IntBuffer textures) {
        error();
    }

    @Override
    public void glDeleteTexture(int texture) {
        error();
    }

    @Override
    public void glDepthFunc(int func) {
        error();
    }

    @Override
    public void glDepthMask(boolean flag) {
        error();
    }

    @Override
    public void glDepthRangef(float zNear, float zFar) {
        error();
    }

    @Override
    public void glDrawArrays(int mode, int first, int count) {
        error();
    }

    @Override
    public void glDrawElements(int mode, int count, int type, Buffer indices) {
        error();
    }

    @Override
    public void glFinish() {
        error();
    }

    @Override
    public void glFlush() {
        error();
    }

    @Override
    public void glFrontFace(int mode) {
        error();
    }

    @Override
    public void glGenTextures(int n, IntBuffer textures) {
        error();
    }

    @Override
    public int glGenTexture() {
        error();
        return 0;
    }

    @Override
    public int glGetError() {
        error();
        return 0;
    }

    @Override
    public void glGetIntegerv(int pname, IntBuffer params) {
        error();
    }

    @Override
    public String glGetString(int name) {
        error();
        return "";
    }

    @Override
    public void glHint(int target, int mode) {
        error();
    }

    @Override
    public void glLineWidth(float width) {
        error();
    }

    @Override
    public void glPixelStorei(int pname, int param) {
        error();
    }

    @Override
    public void glPolygonOffset(float factor, float units) {
        error();
    }

    @Override
    public void glReadPixels(int x, int y, int width, int height, int format, int type, Buffer pixels) {
        error();
    }

    @Override
    public void glStencilFunc(int func, int ref, int mask) {
        error();
    }

    @Override
    public void glStencilMask(int mask) {
        error();
    }

    @Override
    public void glStencilOp(int fail, int zfail, int zpass) {
        error();
    }

    @Override
    public void glTexImage2D(int target, int level, int internalformat, int width, int height, int border, int format,
            int type, Buffer pixels) {
        error();
    }

    @Override
    public void glTexParameterf(int target, int pname, float param) {
        error();
    }

    @Override
    public void glTexSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format,
            int type, Buffer pixels) {
        error();
    }

    @Override
    public void glAttachShader(int program, int shader) {
        error();
    }

    @Override
    public void glBindAttribLocation(int program, int index, String name) {
        error();
    }

    @Override
    public void glBindBuffer(int target, int buffer) {
        error();
    }

    @Override
    public void glBindFramebuffer(int target, int framebuffer) {
        error();
    }

    @Override
    public void glBindRenderbuffer(int target, int renderbuffer) {
        error();
    }

    @Override
    public void glBlendColor(float red, float green, float blue, float alpha) {
        error();
    }

    @Override
    public void glBlendEquation(int mode) {
        error();
    }

    @Override
    public void glBlendEquationSeparate(int modeRGB, int modeAlpha) {
        error();
    }

    @Override
    public void glBlendFuncSeparate(int srcRGB, int dstRGB, int srcAlpha, int dstAlpha) {
        error();
    }

    @Override
    public void glBufferData(int target, int size, Buffer data, int usage) {
        error();
    }

    @Override
    public void glBufferSubData(int target, int offset, int size, Buffer data) {
        error();
    }

    @Override
    public int glCheckFramebufferStatus(int target) {
        error();
        return 0;
    }

    @Override
    public void glCompileShader(int shader) {
        error();
    }

    @Override
    public int glCreateProgram() {
        error();
        return 0;
    }

    @Override
    public int glCreateShader(int type) {
        error();
        return 0;
    }

    @Override
    public void glDeleteBuffer(int buffer) {
        error();
    }

    @Override
    public void glDeleteBuffers(int n, IntBuffer buffers) {
        error();
    }

    @Override
    public void glDeleteFramebuffer(int framebuffer) {
        error();
    }

    @Override
    public void glDeleteFramebuffers(int n, IntBuffer framebuffers) {
        error();
    }

    @Override
    public void glDeleteProgram(int program) {
        error();
    }

    @Override
    public void glDeleteRenderbuffer(int renderbuffer) {
        error();
    }

    @Override
    public void glDeleteRenderbuffers(int n, IntBuffer renderbuffers) {
        error();
    }

    @Override
    public void glDeleteShader(int shader) {
        error();
    }

    @Override
    public void glDetachShader(int program, int shader) {
        error();
    }

    @Override
    public void glDisableVertexAttribArray(int index) {
        error();
    }

    @Override
    public void glDrawElements(int mode, int count, int type, int indices) {
        error();
    }

    @Override
    public void glEnableVertexAttribArray(int index) {
        error();
    }

    @Override
    public void glFramebufferRenderbuffer(int target, int attachment, int renderbuffertarget, int renderbuffer) {
        error();
    }

    @Override
    public void glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        error();
    }

    @Override
    public int glGenBuffer() {
        error();
        return 0;
    }

    @Override
    public void glGenBuffers(int n, IntBuffer buffers) {
        error();
    }

    @Override
    public void glGenerateMipmap(int target) {
        error();
    }

    @Override
    public int glGenFramebuffer() {
        error();
        return 0;
    }

    @Override
    public void glGenFramebuffers(int n, IntBuffer framebuffers) {
        error();
    }

    @Override
    public int glGenRenderbuffer() {
        error();
        return 0;
    }

    @Override
    public void glGenRenderbuffers(int n, IntBuffer renderbuffers) {
        error();
    }

    @Override
    public String glGetActiveAttrib(int program, int index, IntBuffer size, IntBuffer type) {
        error();
        return "";
    }

    @Override
    public String glGetActiveUniform(int program, int index, IntBuffer size, IntBuffer type) {
        error();
        return "";
    }

    @Override
    public void glGetAttachedShaders(int program, int maxcount, Buffer count, IntBuffer shaders) {
        error();
    }

    @Override
    public int glGetAttribLocation(int program, String name) {
        error();
        return 0;
    }

    @Override
    public void glGetBooleanv(int pname, Buffer params) {
        error();
    }

    @Override
    public void glGetBufferParameteriv(int target, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glGetFloatv(int pname, FloatBuffer params) {
        error();
    }

    @Override
    public void glGetFramebufferAttachmentParameteriv(int target, int attachment, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glGetProgramiv(int program, int pname, IntBuffer params) {
        error();
    }

    @Override
    public String glGetProgramInfoLog(int program) {
        error();
        return "";
    }

    @Override
    public void glGetRenderbufferParameteriv(int target, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glGetShaderiv(int shader, int pname, IntBuffer params) {
        error();
    }

    @Override
    public String glGetShaderInfoLog(int shader) {
        error();
        return "";
    }

    @Override
    public void glGetShaderPrecisionFormat(int shadertype, int precisiontype, IntBuffer range, IntBuffer precision) {
        error();
    }

    @Override
    public void glGetTexParameterfv(int target, int pname, FloatBuffer params) {
        error();
    }

    @Override
    public void glGetTexParameteriv(int target, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glGetUniformfv(int program, int location, FloatBuffer params) {
        error();
    }

    @Override
    public void glGetUniformiv(int program, int location, IntBuffer params) {
        error();
    }

    @Override
    public int glGetUniformLocation(int program, String name) {
        error();
        return 0;
    }

    @Override
    public void glGetVertexAttribfv(int index, int pname, FloatBuffer params) {
        error();
    }

    @Override
    public void glGetVertexAttribiv(int index, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glGetVertexAttribPointerv(int index, int pname, Buffer pointer) {
        error();
    }

    @Override
    public boolean glIsBuffer(int buffer) {
        error();
        return false;
    }

    @Override
    public boolean glIsEnabled(int cap) {
        error();
        return false;
    }

    @Override
    public boolean glIsFramebuffer(int framebuffer) {
        error();
        return false;
    }

    @Override
    public boolean glIsProgram(int program) {
        error();
        return false;
    }

    @Override
    public boolean glIsRenderbuffer(int renderbuffer) {
        error();
        return false;
    }

    @Override
    public boolean glIsShader(int shader) {
        error();
        return false;
    }

    @Override
    public boolean glIsTexture(int texture) {
        error();
        return false;
    }

    @Override
    public void glLinkProgram(int program) {
        error();
    }

    @Override
    public void glReleaseShaderCompiler() {
        error();
    }

    @Override
    public void glRenderbufferStorage(int target, int internalformat, int width, int height) {
        error();
    }

    @Override
    public void glSampleCoverage(float value, boolean invert) {
        error();
    }

    @Override
    public void glShaderBinary(int n, IntBuffer shaders, int binaryformat, Buffer binary, int length) {
        error();
    }

    @Override
    public void glShaderSource(int shader, String string) {
        error();
    }

    @Override
    public void glStencilFuncSeparate(int face, int func, int ref, int mask) {
        error();
    }

    @Override
    public void glStencilMaskSeparate(int face, int mask) {
        error();
    }

    @Override
    public void glStencilOpSeparate(int face, int fail, int zfail, int zpass) {
        error();
    }

    @Override
    public void glTexParameterfv(int target, int pname, FloatBuffer params) {
        error();
    }

    @Override
    public void glTexParameteri(int target, int pname, int param) {
        error();
    }

    @Override
    public void glTexParameteriv(int target, int pname, IntBuffer params) {
        error();
    }

    @Override
    public void glUniform1f(int location, float x) {
        error();
    }

    @Override
    public void glUniform1fv(int location, int count, FloatBuffer v) {
        error();
    }

    @Override
    public void glUniform1fv(int location, int count, float[] v, int offset) {
        error();
    }

    @Override
    public void glUniform1i(int location, int x) {
        error();
    }

    @Override
    public void glUniform1iv(int location, int count, IntBuffer v) {
        error();
    }

    @Override
    public void glUniform1iv(int location, int count, int[] v, int offset) {
        error();
    }

    @Override
    public void glUniform2f(int location, float x, float y) {
        error();
    }

    @Override
    public void glUniform2fv(int location, int count, FloatBuffer v) {
        error();
    }

    @Override
    public void glUniform2fv(int location, int count, float[] v, int offset) {
        error();
    }

    @Override
    public void glUniform2i(int location, int x, int y) {
        error();
    }

    @Override
    public void glUniform2iv(int location, int count, IntBuffer v) {
        error();
    }

    @Override
    public void glUniform2iv(int location, int count, int[] v, int offset) {
        error();
    }

    @Override
    public void glUniform3f(int location, float x, float y, float z) {
        error();
    }

    @Override
    public void glUniform3fv(int location, int count, FloatBuffer v) {
        error();
    }

    @Override
    public void glUniform3fv(int location, int count, float[] v, int offset) {
        error();
    }

    @Override
    public void glUniform3i(int location, int x, int y, int z) {
        error();
    }

    @Override
    public void glUniform3iv(int location, int count, IntBuffer v) {
        error();
    }

    @Override
    public void glUniform3iv(int location, int count, int[] v, int offset) {
        error();
    }

    @Override
    public void glUniform4f(int location, float x, float y, float z, float w) {
        error();
    }

    @Override
    public void glUniform4fv(int location, int count, FloatBuffer v) {
        error();
    }

    @Override
    public void glUniform4fv(int location, int count, float[] v, int offset) {
        error();
    }

    @Override
    public void glUniform4i(int location, int x, int y, int z, int w) {
        error();
    }

    @Override
    public void glUniform4iv(int location, int count, IntBuffer v) {
        error();
    }

    @Override
    public void glUniform4iv(int location, int count, int[] v, int offset) {
        error();
    }

    @Override
    public void glUniformMatrix2fv(int location, int count, boolean transpose, FloatBuffer value) {
        error();
    }

    @Override
    public void glUniformMatrix2fv(int location, int count, boolean transpose, float[] value, int offset) {
        error();
    }

    @Override
    public void glUniformMatrix3fv(int location, int count, boolean transpose, FloatBuffer value) {
        error();
    }

    @Override
    public void glUniformMatrix3fv(int location, int count, boolean transpose, float[] value, int offset) {
        error();
    }

    @Override
    public void glUniformMatrix4fv(int location, int count, boolean transpose, FloatBuffer value) {
        error();
    }

    @Override
    public void glUniformMatrix4fv(int location, int count, boolean transpose, float[] value, int offset) {
        error();
    }

    @Override
    public void glUseProgram(int program) {
        error();
    }

    @Override
    public void glValidateProgram(int program) {
        error();
    }

    @Override
    public void glVertexAttrib1f(int indx, float x) {
        error();
    }

    @Override
    public void glVertexAttrib1fv(int indx, FloatBuffer values) {
        error();
    }

    @Override
    public void glVertexAttrib2f(int indx, float x, float y) {
        error();
    }

    @Override
    public void glVertexAttrib2fv(int indx, FloatBuffer values) {
        error();
    }

    @Override
    public void glVertexAttrib3f(int indx, float x, float y, float z) {
        error();
    }

    @Override
    public void glVertexAttrib3fv(int indx, FloatBuffer values) {
        error();
    }

    @Override
    public void glVertexAttrib4f(int indx, float x, float y, float z, float w) {
        error();
    }

    @Override
    public void glVertexAttrib4fv(int indx, FloatBuffer values) {
        error();
    }

    @Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, Buffer ptr) {
        error();
    }

    @Override
    public void glVertexAttribPointer(int indx, int size, int type, boolean normalized, int stride, int ptr) {
        error();
    }
}
