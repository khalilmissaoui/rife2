/*
 * Copyright 2001-2022 Geert Bevin (gbevin[remove] at uwyn dot com)
 * Licensed under the Apache License, Version 2.0 (the "License")
 */
package rife.engine.annotations;

import java.lang.annotation.*;

import static rife.engine.annotations.ContextAction.GET;

/**
 * Declares a request cookie.
 *
 * @author Geert Bevin (gbevin[remove] at uwyn dot com)
 * @since 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Documented
public @interface Cookie {
    /**
     * The name of the cookie.
     *
     * @since 2.0
     */
    String name() default "";

    /**
     * Determines which action should be performed when the element is processing this field.
     *
     * @return the action that will be performed during the element processing
     * @since 2.0
     */
    ContextAction action() default GET;
}
