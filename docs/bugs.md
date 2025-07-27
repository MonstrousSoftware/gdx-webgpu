# Known Bugs

1. If you loop multiple times with WgModelBatch in one frame (i.e. begin/render/end/begin/render/end), the last loop will overwrite the earlier ones.

2. If you supply a ShaderProgram to SpriteBatch it will not have been compiled with the right prefix, so the behaviour
may be different even with the same shader source.

3. If you call WebGPUTestStarter and then CubeMapTest it will crash with 
the mention of "invalid texture" in the log. If you first call some other tests from
WebGPUTestStarter it doesn't happen.  It also depends on which textures are
loaded. E.g. data/g3d/environment/debug_* is okay, but data/g3d/environment/environment_01_* is 
not okay.  (which also happens to lack an alpha channel).

4. LoadModelTest will issue an error in the log about WGPUTexture and WGPUTextureView "not disposed properly".
THis happens when the file list contains the following:

    "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf",
    "data/g3d/ship.obj",
    "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf",
    "data/g3d/gltf/waterbottle/waterbottle.glb",
    "data/g3d/head.g3db",
    "data/g3d/monkey.g3db",
    "data/g3d/skydome.g3db",
    "data/g3d/teapot.g3db",
Deleting one of the names makes the error disappear. Presumably somewhere
a WGPUTexture is dropped and garbage collected, without having been disposed.
