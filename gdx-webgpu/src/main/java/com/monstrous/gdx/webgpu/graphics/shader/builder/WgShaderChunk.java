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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A named snippet of WGSL source used by {@link WgShaderBuilder}.
 *
 * <p>Chunks come in two forms:</p>
 *
 * <h3>Flat chunk</h3>
 * <p>A simple named string of WGSL — the original form. Created with
 * {@link #WgShaderChunk(String, String)}.</p>
 * <pre>{@code
 * new WgShaderChunk("pi", "const pi : f32 = 3.14159265359;\n")
 * }</pre>
 *
 * <h3>Structured chunk</h3>
 * <p>A chunk with optional {@code begin}/{@code end} text wrapping an ordered list of named
 * <em>sub-chunks</em>. Use {@link WgShaderChunk.Builder} to construct these.  Every sub-chunk
 * is individually addressable in {@link WgShaderBuilder} using the dotted path
 * {@code "parentName.subName"}.</p>
 *
 * <pre>{@code
 * // A struct chunk whose fields are each individually replaceable
 * WgShaderChunk.struct("struct_directional_light", "DirectionalLight")
 *     .field("color",     "    color: vec4f,\n")
 *     .field("direction", "    direction: vec4f\n")
 *     .build();
 *
 * // A function chunk whose body lines are each individually replaceable
 * WgShaderChunk.fn("vs_open", "@vertex\nfn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {\n")
 *     .line("out_decl", "   var out: VertexOutput;\n")
 *     .build();
 * }</pre>
 */
public final class WgShaderChunk {

    /** Unique name within its parent ({@link WgShaderBuilder} or parent chunk). */
    public final String name;

    /**
     * For flat chunks: the full WGSL source.
     * For structured chunks: {@code null} — content is derived from {@link #begin},
     * {@link #subChunks}, and {@link #end}.
     */
    public final String source;

    /** Opening text emitted before sub-chunks (e.g. {@code "struct Foo {\n"}). May be empty. */
    public final String begin;

    /** Closing text emitted after sub-chunks (e.g. {@code "};\n"}). May be empty. */
    public final String end;

    /** Ordered map of named sub-chunks. Empty for flat chunks. */
    final LinkedHashMap<String, WgShaderChunk> subChunks;

    // -------------------------------------------------------------------------
    // Flat chunk constructor (backward-compatible)
    // -------------------------------------------------------------------------

    public WgShaderChunk(String name, String source) {
        if (name == null || name.isEmpty())
            throw new IllegalArgumentException("WgShaderChunk name must not be null or empty");
        if (source == null)
            throw new IllegalArgumentException("WgShaderChunk source must not be null");
        this.name = name;
        this.source = source;
        this.begin = null;
        this.end = null;
        this.subChunks = new LinkedHashMap<>();
    }

    // -------------------------------------------------------------------------
    // Structured chunk constructor (used by Builder only)
    // -------------------------------------------------------------------------

    private WgShaderChunk(String name, String begin, String end, LinkedHashMap<String, WgShaderChunk> subChunks) {
        this.name = name;
        this.source = null;
        this.begin = begin;
        this.end = end;
        this.subChunks = subChunks;
    }

    /** Returns {@code true} if this chunk has sub-chunks (i.e. is structured). */
    public boolean isStructured() {
        return source == null;
    }

    /**
     * Emit the full WGSL source for this chunk: for flat chunks returns {@link #source};
     * for structured chunks concatenates {@link #begin}, all sub-chunk sources, and {@link #end}.
     */
    public String emit() {
        if (!isStructured()) return source;
        StringBuilder sb = new StringBuilder();
        if (begin != null) sb.append(begin);
        for (WgShaderChunk sub : subChunks.values()) {
            sb.append(sub.emit());
        }
        if (end != null) sb.append(end);
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Static factory entry points
    // -------------------------------------------------------------------------

    /**
     * Start building a {@code struct} chunk.
     * The generated begin will be {@code "struct <structName> {\n"} and end {@code "};\n"}.
     *
     * @param chunkName  the chunk name used for addressing (e.g. {@code "struct_directional_light"})
     * @param structName the WGSL struct name (e.g. {@code "DirectionalLight"})
     */
    public static Builder struct(String chunkName, String structName) {
        return new Builder(chunkName, "struct " + structName + " {\n", "};\n");
    }

    /**
     * Start building a self-contained function chunk where ALL body lines are sub-chunks of
     * this single chunk. The generated end will be {@code "}\n"}.
     *
     * <p><strong>Warning:</strong> only use this when the entire function body lives inside
     * this one chunk. If the function body is spread across multiple top-level builder chunks
     * (the common case in {@code WgModelBatchShaderBuilder}), use
     * {@link #block(String, String, String)} with {@code end = ""} for the opening chunk
     * and a separate flat chunk for the {@code return}/closing brace.</p>
     *
     * @param chunkName the chunk name used for addressing
     * @param signature everything from attributes through the opening brace, e.g.
     *                  {@code "@vertex\nfn vs_main(...) -> VertexOutput {\n"}
     */
    public static Builder fn(String chunkName, String signature) {
        return new Builder(chunkName, signature, "}\n");
    }

    /**
     * Start building a generic structured chunk with explicit begin/end text.
     *
     * @param chunkName chunk name used for addressing
     * @param begin     text emitted before sub-chunks
     * @param end       text emitted after sub-chunks
     */
    public static Builder block(String chunkName, String begin, String end) {
        return new Builder(chunkName, begin, end);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /**
     * Fluent builder for structured {@link WgShaderChunk}s.
     *
     * <pre>{@code
     * WgShaderChunk chunk = WgShaderChunk.struct("struct_point_light", "PointLight")
     *     .field("color",     "    color: vec4f,\n")
     *     .field("position",  "    position: vec4f,\n")
     *     .field("intensity", "    intensity: f32\n")
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private final String chunkName;
        private final String begin;
        private final String end;
        private final LinkedHashMap<String, WgShaderChunk> subChunks = new LinkedHashMap<>();

        private Builder(String chunkName, String begin, String end) {
            this.chunkName = chunkName;
            this.begin = begin;
            this.end = end;
        }

        /**
         * Add a named sub-chunk with flat source text.
         * Convenience alias for {@link #sub(String, String)} — both names are valid;
         * use whichever reads best ({@code .field()} inside structs, {@code .line()} inside
         * functions).
         */
        public Builder field(String subName, String source) {
            subChunks.put(subName, new WgShaderChunk(subName, source));
            return this;
        }

        /** Add a named sub-chunk inside a function body. Alias for {@link #field}. */
        public Builder line(String subName, String source) {
            return field(subName, source);
        }

        /** Add a named sub-chunk inside an {@code #ifdef} block or other generic block. */
        public Builder sub(String subName, String source) {
            return field(subName, source);
        }

        /**
         * Add a pre-built (possibly nested) sub-chunk.
         * Use this when a sub-chunk is itself structured (e.g. an {@code #ifdef} block with
         * its own named lines).
         */
        public Builder sub(WgShaderChunk chunk) {
            subChunks.put(chunk.name, chunk);
            return this;
        }

        /** Finalise and return the structured chunk. */
        public WgShaderChunk build() {
            return new WgShaderChunk(chunkName, begin, end, new LinkedHashMap<>(subChunks));
        }
    }

    @Override
    public String toString() {
        return isStructured()
            ? "WgShaderChunk[" + name + " (structured, " + subChunks.size() + " sub-chunks)]"
            : "WgShaderChunk[" + name + "]";
    }
}
