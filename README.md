# Gdx-webgpu
by Monstrous Software

## What is it?
This is an extension for LibGDX which allows you to use the WebGPU graphics API instead of OpenGL.

WebGPU is a modern graphics API that was developed for browsers, but can also be used for native applications.
WebGPU can make use of different backends, such as Vulkan, Metal or DirectX.

## How does it work?
The gdx-webgpu provides a number of graphics classes (WgSpriteBatch, WgModelBatch, WgStage, etc.) to use instead of the ones from LibGDX.  These provide the same
behaviour without using OpenGL.

The gdx-webgpu extension uses jWebGPU by Xpenatan as underlying API which provides a Java interface to a native WebGPU implementation, in particular WGPU from the Firefox people. Work is ongoing to test this for different platforms (Windows, Web, Android, Linux, iOS, etc.)

![layers](https://github.com/user-attachments/assets/35e49a65-36bd-42b4-98e9-f2de14f61f02)

## How to use it?
Instead of the regular application launcher, use the gdx-webgpu launcher for your platform as described below. 

Then in your application, you can generally code as normal for LibGDX applications, except that for some graphics classes you need to use an alternative class.
Gdx-webgpu provides substitute classes for many of the LibGDX graphics classes.  
For example, instead of `Texture`, you would use `WgTexture`, instead of `SpriteBatch` you would use `WgSpriteBatch`, etcetera. It's not possible to mix and match;
there is no OpenGL context so using any classes that rely on OpenGL will crash or fail.

For more information see the [User Guide](docs/user_guide.md) in the `docs` folder

## Can I see a demo?

If you run the `WebGPUTestStarter` application in the tests module, you get a menu with lots of 
different test cases.





