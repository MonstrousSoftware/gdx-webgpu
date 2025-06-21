package com.monstrous.gdx.webgpu.graphics;

import java.util.HashMap;
import java.util.Map;

/** Dictionary to map names to binding identifiers
 *
 */
public class BindingDictionary {

    public static class BindingMap {
        int groupId;
        int bindingId;
        int offset;     // used for uniforms in a uniform buffer

        public BindingMap(int groupId, int bindingId, int offset) {
            this.groupId = groupId;
            this.bindingId = bindingId;
            this.offset  = offset;      // -1 if this is a binding group entry, >= 0 for uniform buffer offsets
        }
    }

    private final Map<String,BindingMap> map = new HashMap<>();
    private final Map<Integer, Integer> entriesPerGroup = new HashMap<>();

    public void defineUniform(String name, int groupId, int bindingId){
        // store binding information under the uniform name
        BindingMap bm = new BindingMap(groupId, bindingId, -1);
        map.put(name, bm);
    }

    /** define uniform for given offset within a uniform buffer at (groupId, bindingId) */
    public void defineUniform(String name, int groupId, int bindingId, int offset){
        // store binding information under the uniform name
        BindingMap bm = new BindingMap(groupId, bindingId, offset);
        map.put(name, bm);
    }

    public BindingMap findUniform(String name){
        return map.get(name);
    }

}
