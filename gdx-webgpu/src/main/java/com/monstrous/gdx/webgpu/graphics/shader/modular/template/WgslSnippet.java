package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

import com.badlogic.gdx.files.FileHandle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class WgslSnippet {
    private enum Kind { TEXT, FILE, BLOCK }

    private final Kind kind;
    private final String text;
    private final FileHandle file;
    private final String blockName;

    private WgslSnippet(Kind kind, String text, FileHandle file, String blockName) {
        this.kind = kind;
        this.text = text;
        this.file = file;
        this.blockName = blockName;
    }

    public static WgslSnippet text(String wgsl) {
        if (wgsl == null)
            throw new ShaderTemplateException("WGSL snippet text must not be null.");
        return new WgslSnippet(Kind.TEXT, wgsl, null, null);
    }

    public static WgslSnippet file(FileHandle file) {
        if (file == null)
            throw new ShaderTemplateException("WGSL snippet file must not be null.");
        return new WgslSnippet(Kind.FILE, null, file, null);
    }

    public static WgslSnippet block(FileHandle file, String blockName) {
        if (file == null)
            throw new ShaderTemplateException("WGSL block file must not be null.");
        if (blockName == null || blockName.length() == 0)
            throw new ShaderTemplateException("WGSL block name must not be empty.");
        return new WgslSnippet(Kind.BLOCK, null, file, blockName);
    }

    Resolved resolve() {
        switch (kind) {
            case TEXT:
                return new Resolved(text, "inline snippet");
            case FILE:
                ensureExists(file);
                return new Resolved(file.readString(), file.path());
            case BLOCK:
                ensureExists(file);
                Map<String, String> blocks = parseBlocks(file.path(), file.readString());
                String block = blocks.get(blockName);
                if (block == null)
                    throw new ShaderTemplateException("Missing WGSL block '" + blockName + "' in " + file.path());
                return new Resolved(block, file.path() + "#" + blockName);
            default:
                throw new ShaderTemplateException("Unknown WGSL snippet kind.");
        }
    }

    static LinkedHashMap<String, String> parseBlocks(String sourceName, String source) {
        LinkedHashMap<String, String> blocks = new LinkedHashMap<>();
        String[] lines = source.split("\\r?\\n", -1);
        String activeName = null;
        StringBuilder active = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Marker marker = Marker.parse(line);
            if (marker == null) {
                if (active != null)
                    active.append(line).append('\n');
                continue;
            }
            if ("block".equals(marker.kind)) {
                if (activeName != null)
                    throw new ShaderTemplateException("Nested @block in " + sourceName + " at line " + (i + 1));
                if (blocks.containsKey(marker.name))
                    throw new ShaderTemplateException("Duplicate @block '" + marker.name + "' in " + sourceName);
                activeName = marker.name;
                active = new StringBuilder();
            } else if ("end".equals(marker.kind)) {
                if (activeName == null)
                    throw new ShaderTemplateException("@end without @block in " + sourceName + " at line " + (i + 1));
                blocks.put(activeName, active.toString());
                activeName = null;
                active = null;
            } else if (active != null) {
                active.append(line).append('\n');
            }
        }
        if (activeName != null)
            throw new ShaderTemplateException("Unterminated @block '" + activeName + "' in " + sourceName);
        return blocks;
    }

    private static void ensureExists(FileHandle file) {
        if (!file.exists())
            throw new ShaderTemplateException("WGSL file does not exist: " + file.path());
    }

    static final class Resolved {
        final String source;
        final String origin;

        Resolved(String source, String origin) {
            this.source = source;
            this.origin = origin;
        }
    }
}
