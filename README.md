# Gdx-webgpu
by Monstrous Software

## What is it?
This is an extension for LibGDX which allows you to use the WebGPU graphics API instead of OpenGL.

WebGPU is a modern graphics API that was developed for browsers, but can also be used for native applications.
WebGPU can make use of different backends, such as Vulkan, Metal or DirectX.

LibGDX extension based on the WebGPU graphics API instead of OpenGL.

Work in progress: Currently, uses the Java-to-WebGPU repo as underlying API and only works on Windows desktop using the Dawn library.
At some point this is supposed to become multi-platform.


## How to use it?
Instead of the regular application launcher, use the gdx-webgpu launcher for your platform as described below. (At this moment, only Windows desktop is supported).

Then in your application, you can generally code as normal for LibGDX applications, except that for some graphics classes you need to use an alternative class.
Gdx-webgpu provides substitute classes for many of the LibGDX graphics classes.  
For example, instead of `Texture`, you would use `WgTexture`, instead of `SpriteBatch` you would use `WgSpriteBatch`, etcetera.

When using the WebGPU for rendering, the substitute classes need to be used as the original classes will not work without a GL context and this will generally lead to
a run-time exception.

Sometimes the substitute class is subclassed from the original class, so you can provide the subclass where the original class is expected,
as long as you use the constructor of the substitute class (i.e. the `new` operator).  This allows a lot of existing code to remain unchanged.

Example:

    Texture texture = new WgTexture(128, 128);
    TextureRegion region = new TextureRegion(texture);
    Sprite sprite = new Sprite(texture);

For more information see the [User Guide](docs/user_guide.md) in the `docs` folder

## Can I see a demo?

If you run the `WebGPUTestStarter` application in the tests module, you get a menu with lots of 
different test cases.





