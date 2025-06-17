
package com.badlogic.gdx.webgpu.backends.lwjgl3;


/** Convenience implementation of {@link WgWindowListener}. Derive from this class and only overwrite the methods you are
 * interested in.
 * @author badlogic */
public class WgWindowAdapter implements WgWindowListener {
	@Override
	public void created (WgWindow window) {
	}

	@Override
	public void iconified (boolean isIconified) {
	}

	@Override
	public void maximized (boolean isMaximized) {
	}

	@Override
	public void focusLost () {
	}

	@Override
	public void focusGained () {
	}

	@Override
	public boolean closeRequested () {
		return true;
	}

	@Override
	public void filesDropped (String[] files) {
	}

	@Override
	public void refreshRequested () {
	}
}
