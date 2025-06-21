
package com.monstrous.gdx.webgpu.backends.lwjgl3;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Input;
import com.badlogic.gdx.backends.lwjgl3.audio.Lwjgl3Audio;


public interface WgApplicationBase extends Application {

	Lwjgl3Audio createAudio (WgApplicationConfiguration config);

	Lwjgl3Input createInput (WgWindow window);
}
