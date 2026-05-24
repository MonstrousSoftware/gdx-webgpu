package com.monstrous.gdx.benchmarks.graalvm;

import com.badlogic.gdx.utils.SharedLibraryLoader;
import com.monstrous.gdx.benchmarks.webgpu.WebGPUBenchmarkLauncher;
import java.io.File;

public class GraalVMWebGPUBenchmarkLauncher {
    private static final String LWJGL_JNI_FUNCTION_COUNT = "org.lwjgl.system.JNINativeInterfaceSize";
    private static final String JAVA_24_PLUS_JNI_FUNCTION_COUNT = "233";
    private static final String NATIVE_IMAGE_IMAGE_CODE = "org.graalvm.nativeimage.imagecode";

    public static void main(String[] args) {
        launch(args, "graalvm-jni");
    }

    static void launch(String[] args, String defaultBinding) {
        configureExternalNativeLibraries();
        if(System.getProperty(LWJGL_JNI_FUNCTION_COUNT) == null) {
            System.setProperty(LWJGL_JNI_FUNCTION_COUNT, JAVA_24_PLUS_JNI_FUNCTION_COUNT);
        }
        if(System.getProperty("benchmark.binding") == null) {
            System.setProperty("benchmark.binding", defaultBinding);
        }

        WebGPUBenchmarkLauncher.main(args);
    }

    private static void configureExternalNativeLibraries() {
        String workingDirectory = System.getProperty("user.dir");
        if(workingDirectory == null || workingDirectory.length() == 0) {
            return;
        }
        if(!isNativeImageRuntime() && !hasNativeLibraries(workingDirectory)) {
            return;
        }

        System.setProperty("org.lwjgl.librarypath", workingDirectory);
        System.setProperty("org.lwjgl.system.SharedLibraryExtractPath", workingDirectory);
        System.setProperty("java.library.path", workingDirectory);
        loadGdxNativeLibrary(workingDirectory);
    }

    private static boolean isNativeImageRuntime() {
        String imageCode = System.getProperty(NATIVE_IMAGE_IMAGE_CODE);
        return imageCode != null && imageCode.indexOf("runtime") >= 0;
    }

    private static boolean hasNativeLibraries(String workingDirectory) {
        File directory = new File(workingDirectory);
        String[] names = directory.list((dir, name) -> isNativeLibraryName(name));
        return names != null && names.length > 0;
    }

    private static boolean isNativeLibraryName(String name) {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".dll") || lowerName.endsWith(".so") || lowerName.endsWith(".dylib");
    }

    private static void loadGdxNativeLibrary(String workingDirectory) {
        String mappedName = new SharedLibraryLoader().mapLibraryName("gdx");
        File libraryFile = new File(workingDirectory, mappedName);
        if(!libraryFile.isFile()) {
            return;
        }
        System.load(libraryFile.getAbsolutePath());
        SharedLibraryLoader.setLoaded("gdx");
    }
}
