# Modular Shader Architecture

This file is an index for the modular shader documentation.

The architecture is split into two systems:

1. [Shader Template System](modular-shader/shader-template-system.md)
   - Generates customized WGSL source from readable shader templates.
   - Owns template markers, snippets, module WGSL contributions, and defines.

2. [Shader Layout System](modular-shader/shader-layout-system.md)
   - Describes uniforms, textures, samplers, bind groups, and binding placement for generated shaders.
   - Owns layout objects and metadata that can be passed to current renderer APIs.
