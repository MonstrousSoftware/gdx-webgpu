package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ShaderDefines {
    private final LinkedHashMap<String, String> defines = new LinkedHashMap<>();

    public void define(String name) {
        defineInternal(name, null);
    }

    public void define(String name, int value) {
        defineInternal(name, Integer.toString(value));
    }

    public void define(String name, float value) {
        defineInternal(name, Float.toString(value));
    }

    public void define(String name, String value) {
        defineInternal(name, value);
    }

    public boolean isDefined(String name) {
        return defines.containsKey(name);
    }

    public String buildSource() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : defines.entrySet()) {
            sb.append("#define ").append(entry.getKey());
            if (entry.getValue() != null)
                sb.append(' ').append(entry.getValue());
            sb.append('\n');
        }
        return sb.toString();
    }

    private void defineInternal(String name, String value) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*"))
            throw new ShaderTemplateException("Invalid shader define name: " + name);
        if (defines.containsKey(name)) {
            String oldValue = defines.get(name);
            boolean same = oldValue == null ? value == null : oldValue.equals(value);
            if (!same)
                throw new ShaderTemplateException("Shader define '" + name + "' defined with conflicting values.");
            return;
        }
        defines.put(name, value);
    }
}
