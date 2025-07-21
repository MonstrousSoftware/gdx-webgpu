# Gdx-webgpu
by Monstrous Software

## What is it?
This is an extension for LibGDX which allows you to use the WebGPU graphics API instead of OpenGL.

WebGPU is a modern graphics API that was developed for browsers, but can also be used for native applications.
WebGPU can make use of different backends, such as Vulkan, Metal or DirectX.

## How does it work?
The gdx-webgpu extension provides a number of graphics classes (WgSpriteBatch, WgModelBatch, WgStage, etc.) to use instead of the ones from LibGDX.  These provide the same behaviour without using OpenGL.

The gdx-webgpu extension uses [jWebGPU](https://github.com/xpenatan/jWebGPU) by Xpenatan as underlying API which provides a multi-platform Java interface to a native WebGPU implementation, in particular to WGPU. 

![layers](https://github.com/user-attachments/assets/35e49a65-36bd-42b4-98e9-f2de14f61f02)

## How to use it?
Instead of the regular application launcher, use the gdx-webgpu launcher for your platform as described below. 

Then in your application, you can generally code as normal for LibGDX applications, except that for some graphics classes you need to use an alternative class.
Gdx-webgpu provides substitute classes for many of the LibGDX graphics classes.  
For example, instead of `Texture`, you would use `WgTexture`, instead of `SpriteBatch` you would use `WgSpriteBatch`, etcetera. It's not possible to mix and match;
there is no OpenGL context so using any classes that rely on OpenGL will result in error messages.


Here is an example that should look very familiar to LibGDX users:
```java
package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class HelloTexture extends ApplicationAdapter {

    private Texture texture;
    private SpriteBatch batch;

    @Override
    public void create() {
        texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg")); // note: WgTexture
        batch = new WgSpriteBatch();                                      // note: WgSpriteBatch
    }

    @Override
    public void render() {
        batch.begin();
        batch.draw(texture, 0, 0);
        batch.end();
    }

    @Override
    public void dispose(){
        batch.dispose();
        texture.dispose();
    }
}
```
Note in the example that WgTexture was used to create the Texture object.  WgTexture is a subclass of Texture, suitable for WebGPU.  Also note that WgSpriteBatch was used instead of SpriteBatch.  In this example, these are the only two changes from a regular LibGDX application: using types with a Wg- prefix instead of the standard LibGDX graphics classes. 


For more information see the [User Guide](docs/user_guide.md) in the `docs` folder

## Launcher code

To start up a gdx-webgpu application, a platform-specific [starter class](https://libgdx.com/wiki/app/starter-classes-and-configuration) will call the relevant back-end and run the application specific code.

For example to launch a gdx-webgpu application for desktop, create a `WgApplication` and pass it an instance of `ApplicationListener` and optionally a configuration object.

```java
package com.example.mygame;

import com.example.mygame.MyGame;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;

public class Launcher {

    public static void main (String[] argv) {
  		WgApplicationConfiguration config = new WgApplicationConfiguration();
  		config.setWindowedMode(1200, 800);
  		new WgApplication(new MyGame(), config);
  	}
  }
```


## Can I see a demo?

If you run the `WebGPUTestStarter` application in the tests module, you get a menu with lots of 
different test cases.

## New features 

Apart from the graphics platform, gdx-webgpu offers some new features with regard to LibGDX:
- support for 32-bit index values for a mesh allowing for larger meshes.
- support for GLTF and GLB model format (still with some limitations)
- debug feature to measure GPU time per render pass




