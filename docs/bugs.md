# Known Bugs

1. If you use WgSpriteBatch multiple times in a frame (begin/draw/end) the result
is not what you'd expect, because the last loop overwrites the previous content.
The batch is not really flushed until the end of the frame.
Example:
    batch.begin(null);
    batch.draw(texture, dx+w/4f, 0, w/2f, 480);
    batch.end();
    batch.begin(null);
    batch.draw(texture, dx, h/4f, w, h/2f);
    batch.end();
Workaround: use different WgSpriteBatch objects.

2. If you supply a ShaderProgram to SpriteBatch it will not have been compiled with the right prefix, so the behaviour
may be different even with the same shader source.

3. In LoadModelTest the appearance of e.g. Stanford Dragon is worse if another model is loaded first.  Perhaps a suboptimal pipeline is used.

