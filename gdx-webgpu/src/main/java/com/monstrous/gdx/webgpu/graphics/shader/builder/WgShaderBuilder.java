/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics.shader.builder;

import com.monstrous.gdx.webgpu.graphics.Preprocessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Composable WGSL shader builder.
 *
 * <p>Assembles a final shader string from an ordered list of {@link WgShaderChunk}s.
 * Use the chunk management methods to customise the shader at any granularity:</p>
 * <ul>
 *   <li>{@link #replaceChunk(String, WgShaderChunk)} — swap a top-level chunk.</li>
 *   <li>{@link #replaceSubChunk(String, WgShaderChunk)} — replace a single sub-chunk inside a
 *       structured chunk, addressed as {@code "parentName.subName"}.</li>
 *   <li>{@link #insertBefore(String, WgShaderChunk)} / {@link #insertAfter(String, WgShaderChunk)}
 *       — splice new top-level chunks into the sequence.</li>
 *   <li>{@link #insertSubChunkBefore(String, WgShaderChunk)} /
 *       {@link #insertSubChunkAfter(String, WgShaderChunk)}
 *       — splice a new sub-chunk before/after a named sub-chunk.</li>
 *   <li>{@link #removeChunk(String)} — drop a top-level chunk.</li>
 *   <li>{@link #removeSubChunk(String)} — drop a single sub-chunk by dotted path.</li>
 * </ul>
 *
 * <h3>Sub-chunk addressing</h3>
 * <p>Sub-chunks are addressed with a dotted path: {@code "parentName.subName"}.
 * Nesting is supported: {@code "vertex_input.skin_block.joints"}.</p>
 *
 * <pre>{@code
 * builder
 *   // Replace one field inside a struct
 *   .replaceSubChunk("struct_directional_light.color",
 *                    new WgShaderChunk("color", "    color: vec3f,\n"))
 *   // Insert a new field after an existing one
 *   .insertSubChunkAfter("struct_frame_uniforms.numRoughnessLevels",
 *                        new WgShaderChunk("myField", "    myField: f32,\n"))
 *   // Replace one line inside a function body
 *   .replaceSubChunk("vs_clip_pos.world_pos",
 *                    new WgShaderChunk("world_pos", "   out.worldPos = myTransform();\n"));
 * }</pre>
 */
public class WgShaderBuilder {

    private final LinkedHashMap<String, WgShaderChunk> chunks = new LinkedHashMap<>();
    private final List<String> defines = new ArrayList<>();

    public WgShaderBuilder() {}

    // =========================================================================
    // Top-level chunk management
    // =========================================================================

    /** Append a chunk. If a chunk with the same name already exists it is replaced in place. */
    public WgShaderBuilder addChunk(WgShaderChunk chunk) {
        chunks.put(chunk.name, chunk);
        return this;
    }

    /** Convenience — create and append a flat chunk from raw source text. */
    public WgShaderBuilder addChunk(String name, String source) {
        return addChunk(new WgShaderChunk(name, source));
    }

    /**
     * Replace a top-level chunk by name without changing its position.
     * @throws IllegalArgumentException if the chunk is not found.
     */
    public WgShaderBuilder replaceChunk(String name, WgShaderChunk replacement) {
        if (!chunks.containsKey(name))
            throw new IllegalArgumentException("No chunk named '" + name + "' found in builder.");
        chunks.put(name, replacement);
        return this;
    }

    /** Convenience overload — replace a top-level chunk with new flat source. */
    public WgShaderBuilder replaceChunk(String name, String source) {
        return replaceChunk(name, new WgShaderChunk(name, source));
    }

    /** Remove a top-level chunk. No-op if not present. */
    public WgShaderBuilder removeChunk(String name) {
        chunks.remove(name);
        return this;
    }

    /**
     * Insert {@code newChunk} immediately before the top-level chunk named {@code anchor}.
     * @throws IllegalArgumentException if {@code anchor} is not found.
     */
    public WgShaderBuilder insertBefore(String anchor, WgShaderChunk newChunk) {
        if (!chunks.containsKey(anchor))
            throw new IllegalArgumentException("Anchor chunk '" + anchor + "' not found.");
        rebuildWithInsertion(chunks, anchor, newChunk, false);
        return this;
    }

    /**
     * Insert {@code newChunk} immediately after the top-level chunk named {@code anchor}.
     * @throws IllegalArgumentException if {@code anchor} is not found.
     */
    public WgShaderBuilder insertAfter(String anchor, WgShaderChunk newChunk) {
        if (!chunks.containsKey(anchor))
            throw new IllegalArgumentException("Anchor chunk '" + anchor + "' not found.");
        rebuildWithInsertion(chunks, anchor, newChunk, true);
        return this;
    }

    /** Whether a top-level chunk with the given name is currently registered. */
    public boolean hasChunk(String name) {
        return chunks.containsKey(name);
    }

    /** Return the top-level chunk registered under {@code name}, or {@code null} if absent. */
    public WgShaderChunk getChunk(String name) {
        return chunks.get(name);
    }

    // =========================================================================
    // Sub-chunk management  (dotted path: "parentName.subName[.deeperName...]")
    // =========================================================================

    /**
     * Replace the sub-chunk addressed by {@code dottedPath} with {@code replacement}.
     * The replacement's {@link WgShaderChunk#name} does not need to match the last path segment —
     * it is stored under the last path segment key.
     *
     * @param dottedPath path like {@code "struct_directional_light.color"}
     * @throws IllegalArgumentException if any segment in the path is not found.
     */
    public WgShaderBuilder replaceSubChunk(String dottedPath, WgShaderChunk replacement) {
        String[] parts = splitPath(dottedPath);
        WgShaderChunk parent = resolveParent(parts);
        String key = parts[parts.length - 1];
        if (!parent.subChunks.containsKey(key))
            throw new IllegalArgumentException("Sub-chunk '" + key + "' not found in '" + parts[parts.length - 2] + "'.");
        parent.subChunks.put(key, replacement);
        return this;
    }

    /** Convenience — replace a sub-chunk with a new flat-source chunk using the same sub-name. */
    public WgShaderBuilder replaceSubChunk(String dottedPath, String source) {
        String[] parts = splitPath(dottedPath);
        return replaceSubChunk(dottedPath, new WgShaderChunk(parts[parts.length - 1], source));
    }

    /**
     * Remove the sub-chunk addressed by {@code dottedPath}.
     * No-op if the sub-chunk doesn't exist.
     */
    public WgShaderBuilder removeSubChunk(String dottedPath) {
        String[] parts = splitPath(dottedPath);
        try {
            WgShaderChunk parent = resolveParent(parts);
            parent.subChunks.remove(parts[parts.length - 1]);
        } catch (IllegalArgumentException ignored) {}
        return this;
    }

    /**
     * Insert {@code newSubChunk} immediately before the sub-chunk addressed by {@code dottedPath}.
     *
     * @param dottedPath path to the anchor sub-chunk, e.g. {@code "struct_frame_uniforms.ambientLight"}
     * @throws IllegalArgumentException if any segment in the path is not found.
     */
    public WgShaderBuilder insertSubChunkBefore(String dottedPath, WgShaderChunk newSubChunk) {
        String[] parts = splitPath(dottedPath);
        WgShaderChunk parent = resolveParent(parts);
        String anchor = parts[parts.length - 1];
        if (!parent.subChunks.containsKey(anchor))
            throw new IllegalArgumentException("Sub-chunk anchor '" + anchor + "' not found.");
        rebuildWithInsertion(parent.subChunks, anchor, newSubChunk, false);
        return this;
    }

    /**
     * Insert {@code newSubChunk} immediately after the sub-chunk addressed by {@code dottedPath}.
     *
     * @param dottedPath path to the anchor sub-chunk
     * @throws IllegalArgumentException if any segment in the path is not found.
     */
    public WgShaderBuilder insertSubChunkAfter(String dottedPath, WgShaderChunk newSubChunk) {
        String[] parts = splitPath(dottedPath);
        WgShaderChunk parent = resolveParent(parts);
        String anchor = parts[parts.length - 1];
        if (!parent.subChunks.containsKey(anchor))
            throw new IllegalArgumentException("Sub-chunk anchor '" + anchor + "' not found.");
        rebuildWithInsertion(parent.subChunks, anchor, newSubChunk, true);
        return this;
    }

    /** Return the sub-chunk at {@code dottedPath}, or {@code null} if not found. */
    public WgShaderChunk getSubChunk(String dottedPath) {
        try {
            String[] parts = splitPath(dottedPath);
            WgShaderChunk parent = resolveParent(parts);
            return parent.subChunks.get(parts[parts.length - 1]);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // =========================================================================
    // Define macros
    // =========================================================================

    /** Add a value-less define ({@code #define NAME}). */
    public WgShaderBuilder define(String name) {
        defines.add(name);
        return this;
    }

    /** Add a valued define ({@code #define NAME VALUE}). */
    public WgShaderBuilder define(String name, String value) {
        defines.add(name + " " + value);
        return this;
    }

    /** Add a valued integer define ({@code #define NAME value}). */
    public WgShaderBuilder define(String name, int value) {
        return define(name, Integer.toString(value));
    }

    /** Remove all defines whose first token matches {@code name}. */
    public WgShaderBuilder undefine(String name) {
        defines.removeIf(d -> d.equals(name) || d.startsWith(name + " "));
        return this;
    }

    /** Clear all define macros. */
    public WgShaderBuilder clearDefines() {
        defines.clear();
        return this;
    }

    // =========================================================================
    // Build
    // =========================================================================

    /**
     * Concatenate all chunks (with any accumulated {@code #define} macros prepended) and
     * return the assembled WGSL source.
     *
     * <p>Structured chunks are expanded recursively via {@link WgShaderChunk#emit()}.
     * The {@link Preprocessor} is NOT run here — it is applied later by
     * {@code WgShaderProgram} after the per-pipeline prefix is prepended.</p>
     */
    public String build() {
        StringBuilder sb = new StringBuilder();
        for (String def : defines) {
            sb.append("#define ").append(def).append('\n');
        }
        for (WgShaderChunk chunk : chunks.values()) {
            sb.append('\n');
            sb.append("// --- chunk: ").append(chunk.name).append(" ---\n");
            String emitted = chunk.emit();
            sb.append(emitted);
            if (!emitted.endsWith("\n"))
                sb.append('\n');
        }
        return sb.toString();
    }

    /** Alias for {@link #build()} — kept for debugging convenience. */
    public String buildRaw() {
        return build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static String[] splitPath(String dottedPath) {
        String[] parts = dottedPath.split("\\.", -1);
        if (parts.length < 2)
            throw new IllegalArgumentException(
                "Sub-chunk path must contain at least one dot: '" + dottedPath + "'");
        return parts;
    }

    /**
     * Walk the path down to the direct parent of the last segment.
     * parts[0] is a top-level chunk name; parts[1..n-1] are sub-chunk names on the way down.
     */
    private WgShaderChunk resolveParent(String[] parts) {
        WgShaderChunk current = chunks.get(parts[0]);
        if (current == null)
            throw new IllegalArgumentException("Top-level chunk '" + parts[0] + "' not found.");
        for (int i = 1; i < parts.length - 1; i++) {
            WgShaderChunk next = current.subChunks.get(parts[i]);
            if (next == null)
                throw new IllegalArgumentException(
                    "Sub-chunk '" + parts[i] + "' not found in '" + current.name + "'.");
            current = next;
        }
        return current;
    }

    private static void rebuildWithInsertion(LinkedHashMap<String, WgShaderChunk> map,
                                             String anchor, WgShaderChunk newChunk, boolean after) {
        LinkedHashMap<String, WgShaderChunk> rebuilt = new LinkedHashMap<>();
        for (Map.Entry<String, WgShaderChunk> entry : map.entrySet()) {
            if (!after && entry.getKey().equals(anchor))
                rebuilt.put(newChunk.name, newChunk);
            rebuilt.put(entry.getKey(), entry.getValue());
            if (after && entry.getKey().equals(anchor))
                rebuilt.put(newChunk.name, newChunk);
        }
        map.clear();
        map.putAll(rebuilt);
    }
}
