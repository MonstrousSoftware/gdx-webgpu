/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.backends.desktop;

import com.badlogic.gdx.ApplicationListener;


/** Receives notifications of various window events, such as iconification, focus loss and gain, and window close events. Can be
 * set per window via {@link WgDesktopApplicationConfiguration} and {@link WgDesktopWindowConfiguration}. Close events can be canceled
 * by returning false.
 *
 * @author badlogic */
public interface WgDesktopWindowListener {

	/** Called after the GLFW window is created. Before this callback is received, it's unsafe to use any {@link WgDesktopWindow}
	 * member functions which, for their part, involve calling GLFW functions.
	 *
	 * For the main window, this is an immediate callback from inside
	 * {@link WgDesktopApplication#WgDesktopApplication(ApplicationListener, WgDesktopApplicationConfiguration)}.
	 *
	 * @param window the window instance
	 *
	 * @see WgDesktopApplication#newWindow(ApplicationListener, WgDesktopWindowConfiguration) */
	void created (WgDesktopWindow window);

	/** Called when the window is iconified (i.e. its minimize button was clicked), or when restored from the iconified state. When
	 * a window becomes iconified, its {@link ApplicationListener} will be paused, and when restored it will be resumed.
	 *
	 * @param isIconified True if window is iconified, false if it leaves the iconified state */
	void iconified (boolean isIconified);

	/** Called when the window is maximized, or restored from the maximized state.
	 *
	 * @param isMaximized true if window is maximized, false if it leaves the maximized state */
	void maximized (boolean isMaximized);

	/** Called when the window lost focus to another window. The window's {@link ApplicationListener} will continue to be
	 * called. */
	void focusLost ();

	/** Called when the window gained focus. */
	void focusGained ();

	/** Called when the user requested to close the window, e.g. clicking the close button or pressing the window closing keyboard
	 * shortcut.
	 *
	 * @return whether the window should actually close **/
	boolean closeRequested ();

	/** Called when external files are dropped into the window, e.g from the Desktop.
	 *
	 * @param files array with absolute paths to the files */
	void filesDropped (String[] files);

	/** Called when the window content is damaged and needs to be refreshed. When this occurs,
	 * {@link WgDesktopGraphics#requestRendering()} is automatically called. */
	void refreshRequested ();

}
