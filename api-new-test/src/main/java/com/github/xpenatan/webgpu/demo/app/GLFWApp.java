package com.github.xpenatan.webgpu.demo.app;

import com.github.xpenatan.webgpu.JWebGPULoader;
import org.lwjgl.glfw.GLFWErrorCallback;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow;
import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay;
import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandWindow;
import static org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Window;
import static org.lwjgl.system.MemoryUtil.NULL;

public class GLFWApp {
    long window;
    long windowHandle;

    private WGPUApp wgpu;
    private int wGPUInit = 0;

    int windowWidth = 800;
    int windowHeight = 600;

    public GLFWApp(ApplicationListener applicationInterface) {
        openWindow();

        JWebGPULoader.init((isSuccess, e) -> {
            System.out.println("WebGPU Init Success: " + isSuccess);
            if(isSuccess) {
                wGPUInit = 1;
            }
            else {
                e.printStackTrace();
            }
        });

        wgpu = new WGPUApp();

        while(!glfwWindowShouldClose(window)) {
            if(wGPUInit == 3) {
                applicationInterface.render(wgpu);
            }
            else if(wGPUInit > 0) {
                if(wGPUInit == 1) {
                    wGPUInit = 2;
                    wgpu.width = windowWidth;
                    wgpu.height = windowHeight;
                    wgpu.init();
                }
                else if(wGPUInit == 2 && wgpu.isReady()) {
                    wGPUInit = 3;
                    createSurface();
                    applicationInterface.create(wgpu);
                }
            }
            wgpu.update();
            glfwPollEvents();
        }
        closeWindow();
    }

    public void openWindow() {
        // Set up an error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        // Configure GLFW
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // Hide window until ready
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // Make window resizable
        glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // because we will use webgpu

        // Create the window
        window = glfwCreateWindow(windowWidth, windowHeight, "LWJGL Window", NULL, NULL);
        if (window == NULL) {
            glfwTerminate();
            throw new RuntimeException("Failed to create GLFW window");
        }

        String osName = System.getProperty("os.name").toLowerCase();
        if(osName.contains("win")) {
            windowHandle = glfwGetWin32Window(window);
        }
        else if(osName.contains("linux")) {
            if(glfwGetPlatform() == org.lwjgl.glfw.GLFW.GLFW_PLATFORM_WAYLAND) {
                windowHandle = glfwGetWaylandWindow(window);
            }
            else {
                windowHandle = glfwGetX11Window(window);
            }
        }
        else if(osName.contains("mac")) {
            windowHandle = glfwGetCocoaWindow(window);
        }

        if(glfwGetPlatform() != GLFW_PLATFORM_WAYLAND) {
            var vidMode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(
                    window,
                    (vidMode.width() - windowWidth) / 2,
                    (vidMode.height() - windowHeight) / 2
            );
        }

        // Make the window visible
        glfwShowWindow(window);
    }

    public void closeWindow () {
        // Free the window callbacks and destroy the window
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);

        // Terminate GLFW and free the error callback
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void createSurface() {
        String osName = System.getProperty("os.name").toLowerCase();
        if(osName.contains("win")) {
            wgpu.surface = wgpu.instance.createWindowsSurface(windowHandle);
        }
        else if(osName.contains("linux")) {
            if(glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
                long display = glfwGetWaylandDisplay();
                wgpu.surface = wgpu.instance.createLinuxSurface(true, windowHandle, display);
            }
            else {
                long display = glfwGetX11Display();
                wgpu.surface = wgpu.instance.createLinuxSurface(false, windowHandle, display);
            }
        }
        else if(osName.contains("mac")) {
            wgpu.surface = wgpu.instance.createMacSurface(windowHandle);
        }
    }
}
