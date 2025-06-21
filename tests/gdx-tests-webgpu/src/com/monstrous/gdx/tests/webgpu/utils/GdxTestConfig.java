
package com.monstrous.gdx.tests.webgpu.utils;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target(TYPE)
public @interface GdxTestConfig {
	boolean requireGL30() default false;

	boolean requireGL31() default false;

	boolean requireGL32() default false;

	boolean OnlyGL20() default false;
}
