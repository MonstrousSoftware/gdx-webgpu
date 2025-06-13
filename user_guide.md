# Gdx-webgpu

## What is it?
This is an extension for LibGDX which allows you to use the WebGPU graphics API instead of OpenGL.

Gdx-webgpu provide substitute classes for many of the LibGDX graphics classes.  When using the WebGPU for rendering, the substitute classes need to be used as the original classes will not work without a GL context.

## New classes
The extension provides a set of new classes listed below intended to be used instead of LibGDX graphics classes.

### General
- WebGPUTexture replaces Texture (extends)

### 2d classes
- WebGPUBitmapFont replaces BitmapFont (extends)
- WebGPUSpriteBatch replaces SpriteBatch (replaces, implements Batch)
- WebGPUTextureData replaces TextureData (extends)
- WebGPUTextureAtlas implement TextureAtlas

### Scene2d
- WebGPUSkin replaces Skin (extends)
- WebGPUStage replaces Stage (extends)

### 3d classes
to do

## Class specific comments
This section provides some considerations to keep in mind for specific classes. In general, the classes should behave as their original LibGDX counterpart, but in a few cases there are some caveats.

## WebGPUSpriteBatch

### Maximum
Just like SpriteBatch you can pass a maximum number of sprites in the constructor, default is 10000:
```java
	WebGPUSpriteBatch batch = new WebGPUSpriteBatch(10000);
```
The maximum may need to be set higher than for `SpriteBatch` since `WebGPUSpriteBatch` cannot flush intermediate batches to the GPU when the buffer is full as the buffer content can only be reused after the end of the frame.
This also means that while for `SpriteBatch` the maximum value only affects performance, for `WebGPUSpriteBatch` the maximum is a hard limit and further sprites will be ignored and a warning will be issued in the log (to do).

### Blend Factor
There are two methods to set blending parameters. One is for backwards compatibility with and uses GL constants.
- setBlendFunction
- setBlendFunctionSeparate
- 
The new methods use WebGPU constants (enum WGPUBlendFactor): 
- setBlendFactor
- setBlendFactorSeparate

### Clear color
The `WebGPUSpriteBatch#begin()` method takes an optional `Color` parameter to clear the screen at the start of the render pass, which is slightly more efficient than using `ScreenUtils.clear()` which triggers a dedicated render pass.

### Set Shader
It is possible to set a shader program, either in the constructor or by using `setShader()`.  The shader program needs to be a WebGPUShaderProgram which encapsulates a shader written in WGSL. 
The shader code needs to be compatible with the standard sprite batch shader (res/shaders/spritebatch.wgsl) in terms of binding groups.

### Texture, TextureRegion
Where a Texture is passed one of the draw methods, it must be a WebGPUTexture. Where a TextureRegion is passed, it must be a region of a WebGPUTexture.

## WebGPUTexture
The constructors allow to specify a label per texture.  This can be useful for debugging, it has no functional impact and labels don’t have to be unique.

If a texture is intended to be used as render output, it needs to be constructed with the parameter `renderAttachment` set to true.  This can be used instead of OpenGL frame buffer objects (FBO).  
If anti-aliasing is desired, the parameter `numSamples` should be set to 4 (valid values are 1 and 4).

If a WebGPUTexture is constructed from a TextureData, it must be a WebGPUTextureData.
