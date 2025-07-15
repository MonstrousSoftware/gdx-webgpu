# Gdx-webgpu
by Monstrous Software

## What is it?
This is an extension for LibGDX which allows you to use the WebGPU graphics API instead of OpenGL.

WebGPU is a modern graphics API that was developed for browsers, but can also be used for native applications.
WebGPU can make use of different backends, such as Vulkan, Metal or DirectX.

LibGDX extension based on the WebGPU graphics API instead of OpenGL.

Work in progress: Currently, gdx-webgpu uses my Java-to-WebGPU repo as underlying API and therefore only works on Windows desktop. Work is ongoing to convert to a multi-platform technology via the jWebGPU repo by Xpenatan.


## How to use it?
Instead of the regular application launcher, use the gdx-webgpu launcher for your platform as described below. (At this moment, only Windows desktop is supported).

Then in your application, you can generally code as normal for LibGDX applications, except that for some graphics classes you need to use an alternative class.
Gdx-webgpu provides substitute classes for many of the LibGDX graphics classes.  
For example, instead of `Texture`, you would use `WgTexture`, instead of `SpriteBatch` you would use `WgSpriteBatch`, etcetera.

For more information see the [User Guide](docs/user_guide.md) in the `docs` folder

## Can I see a demo?

If you run the `WebGPUTestStarter` application in the tests module, you get a menu with lots of 
different test cases.





