package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

/** Runtime exception for shader template/module configuration and assembly failures. */
public class ShaderTemplateException extends RuntimeException {
    public ShaderTemplateException(String message) {
        super(message);
    }

    public ShaderTemplateException(String message, Throwable cause) {
        super(message, cause);
    }
}
