/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.web.router.annotation;

import org.particleframework.context.annotation.AliasFor;
import org.particleframework.http.annotation.Consumes;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation that can be applied to method to signify the method receives a {@link org.particleframework.http.HttpMethod#PUT}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@Action
public @interface Put {
    /**
     * @return The URI of the PUT route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = Action.class, member = "value")
    String value() default "";

    /**
     * @return The URI of the PUT route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = Action.class, member = "value")
    String uri() default "";

    /**
     * @return The default consumes, otherwise override from controller
     */
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] consumes() default {};
}