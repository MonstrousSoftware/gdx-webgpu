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
