
package com.badlogic.gdx.webgpu.utils;

import java.lang.annotation.*;

/** This is simply a marker used by JNR-Gen to mark a pointer as a string pointer. When a field is marked with this, JNR-Gen will
 * make the setters/getters automatically convert from java strings to rust strings.
 *
 * @see RustCString */
@Retention(RetentionPolicy.SOURCE)
@Documented
@Target({ElementType.FIELD})
public @interface CStrPointer {
}
