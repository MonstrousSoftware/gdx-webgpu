
package com.monstrous.gdx.webgpu.backends.desktop;

/**
 * Convenience implementation of {@link WgDesktopWindowListener}. Derive from this class and only overwrite the methods
 * you are interested in.
 * 
 * @author badlogic
 */
public class WgDesktopWindowAdapter implements WgDesktopWindowListener {
    @Override
    public void created(WgDesktopWindow window) {
    }

    @Override
    public void iconified(boolean isIconified) {
    }

    @Override
    public void maximized(boolean isMaximized) {
    }

    @Override
    public void focusLost() {
    }

    @Override
    public void focusGained() {
    }

    @Override
    public boolean closeRequested() {
        return true;
    }

    @Override
    public void filesDropped(String[] files) {
    }

    @Override
    public void refreshRequested() {
    }
}
