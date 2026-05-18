package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Marker {
    private static final Pattern PATTERN = Pattern.compile("^\\s*//\\s*@(slot|section|block|end)(?:\\s+([A-Za-z0-9_.-]+))?\\s*$");

    final String kind;
    final String name;

    private Marker(String kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    static Marker parse(String line) {
        Matcher matcher = PATTERN.matcher(line);
        if (!matcher.matches())
            return null;
        String kind = matcher.group(1);
        String name = matcher.group(2);
        if ("end".equals(kind)) {
            if (name != null)
                throw new ShaderTemplateException("@end marker must not have a name: " + line);
        } else if (name == null) {
            throw new ShaderTemplateException("@" + kind + " marker must have a name: " + line);
        }
        return new Marker(kind, name);
    }
}
