package com.monstrous.gdx.tests.webgpu.utils;

import com.badlogic.gdx.ApplicationListener;

/**
 * Abstraction for opening a test in a new window. Implemented by desktop module.
 */
public interface WindowOpener {
    /**
     * Open the test with the given name in a new window.
     * @param testName name of the test (as returned by WebGPUTests.getNames())
     * @return true if a new window was opened, false otherwise
     */
    boolean open(String testName);
}
