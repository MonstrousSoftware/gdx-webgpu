# Known Bugs

1. If you loop multiple times with WgModelBatch in one frame (i.e. begin/render/end/begin/render/end), the last loop will overwrite the earlier ones.

2. If you supply a ShaderProgram to SpriteBatch it will not have been compiled with the right prefix, so the behaviour
may be different even with the same shader source.


