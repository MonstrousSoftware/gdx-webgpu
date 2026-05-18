package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.shader.modular.WgShaderModule;
import com.monstrous.gdx.webgpu.graphics.shader.modular.layout.ShaderLayoutBuilder;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public final class WgShaderTemplate {
    private final String templateName;
    private final String templateSource;
    private final LinkedHashMap<String, Slot> slots = new LinkedHashMap<>();
    private final LinkedHashMap<String, Section> sections = new LinkedHashMap<>();
    private final Array<String> contributions = new Array<>();

    public WgShaderTemplate(FileHandle templateFile) {
        if (templateFile == null)
            throw new ShaderTemplateException("Template file must not be null.");
        if (!templateFile.exists())
            throw new ShaderTemplateException("Template file does not exist: " + templateFile.path());
        this.templateName = templateFile.path();
        this.templateSource = resolveIncludes(templateFile, templateFile.readString(), new Array<String>());
        parseTemplate();
    }

    public WgShaderTemplate(String templateName, String templateSource) {
        if (templateName == null || templateName.length() == 0)
            throw new ShaderTemplateException("Template name must not be empty.");
        if (templateSource == null)
            throw new ShaderTemplateException("Template source must not be null.");
        this.templateName = templateName;
        this.templateSource = templateSource;
        parseTemplate();
    }

    public void insert(String slotName, WgslSnippet snippet) {
        Slot slot = slots.get(slotName);
        if (slot == null)
            throw new ShaderTemplateException("Unknown shader template slot: " + slotName);
        slot.inserts.add(snippet);
    }

    public void appendToSection(String sectionName, WgslSnippet snippet) {
        Section section = sections.get(sectionName);
        if (section == null)
            throw new ShaderTemplateException("Unknown shader template section: " + sectionName);
        section.appends.add(snippet);
    }

    public void replaceSection(String sectionName, WgslSnippet snippet) {
        Section section = sections.get(sectionName);
        if (section == null)
            throw new ShaderTemplateException("Unknown shader template section: " + sectionName);
        if (section.replacement != null)
            throw new ShaderTemplateException("Section '" + sectionName + "' has already been replaced.");
        section.replacement = snippet;
    }

    public void insertMatchingBlocks(FileHandle wgslFile) {
        if (wgslFile == null)
            throw new ShaderTemplateException("WGSL block file must not be null.");
        if (!wgslFile.exists())
            throw new ShaderTemplateException("WGSL block file does not exist: " + wgslFile.path());
        LinkedHashMap<String, String> blocks = WgslSnippet.parseBlocks(wgslFile.path(), wgslFile.readString());
        for (Map.Entry<String, String> entry : blocks.entrySet()) {
            if (!slots.containsKey(entry.getKey()))
                throw new ShaderTemplateException("Block '" + entry.getKey() + "' from " + wgslFile.path()
                        + " has no matching shader template slot.");
            insert(entry.getKey(), WgslSnippet.block(wgslFile, entry.getKey()));
        }
    }

    public ShaderBuildResult build(ShaderDefines defines, ShaderLayoutBuilder layout,
                                   Array<WgShaderModule> modules) {
        return build(defines, layout, modules, null);
    }

    public ShaderBuildResult build(ShaderDefines defines, ShaderLayoutBuilder layout,
                                   Array<WgShaderModule> modules, ShaderTemplateConfig config) {
        contributions.clear();
        String assembled = assemble();
        String definesSource = defines == null ? "" : defines.buildSource();
        String shaderSourceForPipeline = definesSource + assembled;
        String layoutSummary = layout == null ? "" : layout.summary();
        Array<String> moduleNames = new Array<>();
        Array<String> moduleSignatures = new Array<>();
        if (modules != null) {
            for (WgShaderModule module : modules) {
                moduleNames.add(module.getName());
                moduleSignatures.add(module.getSignature());
            }
        }
        String hashInput = templateName + '\n' + definesSource + '\n' + assembled + '\n'
                + join(moduleSignatures) + '\n' + layoutSummary;
        ShaderBuildResult result = new ShaderBuildResult(templateName, templateSource, assembled, definesSource,
                shaderSourceForPipeline, hash(hashInput), moduleNames, moduleSignatures,
                new Array<>(contributions), layoutSummary);
        ShaderBuildDumper.dump(result, config);
        return result;
    }

    public String build() {
        return build(new ShaderDefines(), null, null).shaderSourceForPipeline;
    }

    private void parseTemplate() {
        String[] lines = templateSource.split("\\r?\\n", -1);
        String activeSection = null;
        for (int i = 0; i < lines.length; i++) {
            Marker marker = Marker.parse(lines[i]);
            if (marker == null)
                continue;
            if ("slot".equals(marker.kind)) {
                if (slots.containsKey(marker.name))
                    throw new ShaderTemplateException("Duplicate @slot '" + marker.name + "' in " + templateName);
                slots.put(marker.name, new Slot(marker.name));
            } else if ("section".equals(marker.kind)) {
                if (activeSection != null)
                    throw new ShaderTemplateException("Nested @section '" + marker.name + "' in " + templateName);
                if (sections.containsKey(marker.name))
                    throw new ShaderTemplateException("Duplicate @section '" + marker.name + "' in " + templateName);
                activeSection = marker.name;
                sections.put(marker.name, new Section(marker.name));
            } else if ("block".equals(marker.kind)) {
                throw new ShaderTemplateException("@block is only valid in snippet files: " + templateName);
            } else if ("end".equals(marker.kind)) {
                if (activeSection == null)
                    throw new ShaderTemplateException("@end without @section in " + templateName + " at line " + (i + 1));
                activeSection = null;
            }
        }
        if (activeSection != null)
            throw new ShaderTemplateException("Unterminated @section '" + activeSection + "' in " + templateName);
    }

    private String assemble() {
        StringBuilder out = new StringBuilder();
        String[] lines = templateSource.split("\\r?\\n", -1);
        String activeSection = null;
        StringBuilder sectionBody = null;
        Section section = null;
        for (String line : lines) {
            Marker marker = Marker.parse(line);
            if (activeSection != null) {
                if (marker != null && "end".equals(marker.kind)) {
                    emitSectionBody(out, section, sectionBody.toString());
                    out.append(line).append('\n');
                    activeSection = null;
                    section = null;
                    sectionBody = null;
                } else {
                    sectionBody.append(line).append('\n');
                }
                continue;
            }
            out.append(line).append('\n');
            if (marker == null)
                continue;
            if ("slot".equals(marker.kind)) {
                emitSnippets(out, slots.get(marker.name).inserts, "slot " + marker.name);
            } else if ("section".equals(marker.kind)) {
                activeSection = marker.name;
                section = sections.get(marker.name);
                sectionBody = new StringBuilder();
            }
        }
        return out.toString();
    }

    private void emitSectionBody(StringBuilder out, Section section, String originalBody) {
        if (section.replacement != null)
            emitSnippet(out, section.replacement, "section " + section.name + " replacement");
        else
            out.append(originalBody);
        emitSnippets(out, section.appends, "section " + section.name + " append");
    }

    private void emitSnippets(StringBuilder out, Array<WgslSnippet> snippets, String destination) {
        for (WgslSnippet snippet : snippets)
            emitSnippet(out, snippet, destination);
    }

    private void emitSnippet(StringBuilder out, WgslSnippet snippet, String destination) {
        WgslSnippet.Resolved resolved = snippet.resolve();
        contributions.add(destination + " <- " + resolved.origin);
        out.append("// @module ").append(resolved.origin).append('\n');
        out.append(resolved.source);
        if (!resolved.source.endsWith("\n"))
            out.append('\n');
    }

    private String resolveIncludes(FileHandle file, String source, Array<String> stack) {
        if (stack.contains(file.path(), false))
            throw new ShaderTemplateException("Shader include cycle at " + file.path());
        stack.add(file.path());
        StringBuilder out = new StringBuilder();
        String[] lines = source.split("\\r?\\n", -1);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#include")) {
                String includePath = parseIncludePath(trimmed);
                FileHandle child = file.parent().child(includePath);
                if (!child.exists())
                    throw new ShaderTemplateException("Included WGSL file does not exist: " + child.path());
                out.append("// @include ").append(child.path()).append('\n');
                out.append(resolveIncludes(child, child.readString(), stack));
                out.append("// @endinclude ").append(child.path()).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        stack.removeValue(file.path(), false);
        return out.toString();
    }

    private static String parseIncludePath(String line) {
        int first = line.indexOf('"');
        int last = line.lastIndexOf('"');
        if (first < 0 || last <= first)
            throw new ShaderTemplateException("Invalid #include syntax: " + line);
        return line.substring(first + 1, last);
    }

    private static String join(Array<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values)
            sb.append(value).append('\n');
        return sb.toString();
    }

    private static String hash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++)
                sb.append(String.format("%02x", bytes[i] & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new ShaderTemplateException("Failed to hash shader template output.", e);
        }
    }

    private static final class Slot {
        final String name;
        final Array<WgslSnippet> inserts = new Array<>();

        Slot(String name) {
            this.name = name;
        }
    }

    private static final class Section {
        final String name;
        WgslSnippet replacement;
        final Array<WgslSnippet> appends = new Array<>();

        Section(String name) {
            this.name = name;
        }
    }
}
