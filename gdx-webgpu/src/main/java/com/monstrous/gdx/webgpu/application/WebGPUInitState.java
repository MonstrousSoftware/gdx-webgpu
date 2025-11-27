package com.monstrous.gdx.webgpu.application;

public enum WebGPUInitState {
    NOT_INITIALIZED(0), DEVICE_VALID(1), INSTANCE_NOT_VALID(
        -1), ADAPTER_NOT_VALID(-2), DEVICE_NOT_VALID(-3);

    final int status;

    WebGPUInitState(int status) {
        this.status = status;
    }
}
