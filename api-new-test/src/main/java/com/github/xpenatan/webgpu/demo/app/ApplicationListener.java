package com.github.xpenatan.webgpu.demo.app;

public interface ApplicationListener {

    void create(WGPUApp wgpu);

    void render(WGPUApp wgpu);
}
