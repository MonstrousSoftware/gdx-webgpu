package com.monstrous.gdx.tests.webgpu.utils;

/**
 * Simple registry to provide a WindowOpener implementation at runtime.
 * Desktop module can register an implementation; other platforms leave it null.
 */
public final class WindowOpenerRegistry {
    private static volatile WindowOpener opener;

    public static void setOpener(WindowOpener o) {
        opener = o;
    }

    public static WindowOpener getOpener() {
        return opener;
    }

    private WindowOpenerRegistry() {}
}
