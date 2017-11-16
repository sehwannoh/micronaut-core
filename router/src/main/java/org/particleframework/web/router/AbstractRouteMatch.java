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
package org.particleframework.web.router;

import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.bind.annotation.Bindable;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionError;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.exceptions.ConversionErrorException;
import org.particleframework.http.HttpRequest;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.MethodExecutionHandle;
import org.particleframework.core.type.ReturnType;
import org.particleframework.web.router.exceptions.UnsatisfiedRouteException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Abstract implementation of the {@link RouteMatch} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
abstract class AbstractRouteMatch<R> implements RouteMatch<R> {

    protected final MethodExecutionHandle<R> executableMethod;
    protected final ConversionService<?> conversionService;
    protected final Map<String, Argument> requiredInputs;
    protected final DefaultRouteBuilder.AbstractRoute abstractRoute;

    protected AbstractRouteMatch(DefaultRouteBuilder.AbstractRoute abstractRoute, ConversionService<?> conversionService) {
        this.abstractRoute = abstractRoute;
        this.executableMethod = abstractRoute.targetMethod;
        this.conversionService = conversionService;
        Argument[] requiredArguments = executableMethod.getArguments();
        this.requiredInputs = new LinkedHashMap<>(requiredArguments.length);
        for (Argument requiredArgument : requiredArguments) {
            Optional<Annotation> ann = requiredArgument.findAnnotationWithStereoType(Bindable.class);
            if(ann.isPresent()) {
                Optional<String> value = AnnotationUtil.findValueOfType(ann.get(), String.class);
                requiredInputs.put(value.orElse(requiredArgument.getName()), requiredArgument);
            }
            else {
                requiredInputs.put(requiredArgument.getName(), requiredArgument);
            }
        }

    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return executableMethod.getAnnotationMetadata();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Argument<?>> getBodyArgument() {
        String bodyArgument = abstractRoute.bodyArgument;
        return Optional.ofNullable(requiredInputs.get(bodyArgument));
    }

    @Override
    public boolean isRequiredInput(String name) {
        return requiredInputs.containsKey(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Argument<?>> getRequiredInput(String name) {
        return Optional.ofNullable(requiredInputs.get(name));
    }

    @Override
    public boolean isExecutable() {
        Map<String, Object> variables = getVariables();
        for (Argument argument : requiredInputs.values()) {
            Object value = variables.get(argument.getName());
            if( value == null || value instanceof UnresolvedArgument)
                return false;
        }
        return true;
    }

    @Override
    public Method getTargetMethod() {
        return executableMethod.getTargetMethod();
    }

    @Override
    public String getMethodName() {
        return this.executableMethod.getMethodName();
    }

    @Override
    public Class getDeclaringType() {
        return executableMethod.getDeclaringType();
    }

    @Override
    public Argument[] getArguments() {
        return executableMethod.getArguments();
    }

    @Override
    public boolean test(HttpRequest request) {
        for (Predicate<HttpRequest> condition : abstractRoute.conditions) {
            if (!condition.test(request)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ReturnType<R> getReturnType() {
        return executableMethod.getReturnType();
    }


    @Override
    public R invoke(Object... arguments) {
        ConversionService conversionService = this.conversionService;

        Argument[] targetArguments = getArguments();
        if (targetArguments.length == 0) {
            return executableMethod.invoke();
        } else {
            List argumentList = new ArrayList();
            Map<String, Object> variables = getVariables();
            Iterator<Object> valueIterator = variables.values().iterator();
            int i = 0;
            for (Argument targetArgument : targetArguments) {
                String name = targetArgument.getName();
                Object value = variables.get(name);
                if (value != null) {
                    Optional<?> result = conversionService.convert(value, targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (valueIterator.hasNext()) {
                    Optional<?> result = conversionService.convert(valueIterator.next(), targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else if (i < arguments.length) {
                    Optional<?> result = conversionService.convert(arguments[i++], targetArgument.getType());
                    argumentList.add(result.orElseThrow(() -> new IllegalArgumentException("Wrong argument types to method: " + executableMethod)));
                } else {
                    throw new IllegalArgumentException("Wrong number of arguments to method: " + executableMethod);
                }
            }
            return executableMethod.invoke(argumentList.toArray());
        }
    }

    @Override
    public R execute(Map<String, Object> argumentValues) {
        Argument[] targetArguments = getArguments();

        if (targetArguments.length == 0) {
            return executableMethod.invoke();
        } else {
            ConversionService conversionService = this.conversionService;
            Map<String, Object> uriVariables = getVariables();
            List argumentList = new ArrayList();
            for (Argument argument : targetArguments) {
                String name = argument.getName();
                Object value = DefaultRouteBuilder.NO_VALUE;
                if (uriVariables.containsKey(name)) {
                    value = uriVariables.get(name);
                } else if (argumentValues.containsKey(name)) {
                    value = argumentValues.get(name);
                }
                if(value instanceof UnresolvedArgument) {
                    UnresolvedArgument<?> unresolved = (UnresolvedArgument<?>) value;
                    Optional o = unresolved.get();


                    if(o.isPresent()) {
                        Object resolved = o.get();
                        if(resolved instanceof ConversionError) {
                            ConversionError conversionError = (ConversionError) resolved;
                            throw new ConversionErrorException(argument, conversionError);
                        }
                        else {
                            ConversionContext conversionContext = ConversionContext.of(argument);
                            Optional<?> result = conversionService.convert(resolved, argument.getType(), conversionContext);
                            argumentList.add(resolveValueOrError(argument, conversionContext, result));
                        }
                    }
                    else {
                        throw new UnsatisfiedRouteException(argument);
                    }
                }
                else if(value instanceof ConversionError) {
                    throw new ConversionErrorException(argument, (ConversionError) value);
                }
                else if (value == DefaultRouteBuilder.NO_VALUE) {
                    throw new UnsatisfiedRouteException(argument);
                } else {
                    ConversionContext conversionContext = ConversionContext.of(argument);
                    Optional<?> result = conversionService.convert(value, argument.getType(), conversionContext);
                    argumentList.add(resolveValueOrError(argument, conversionContext, result));
                }
            }

            return executableMethod.invoke(argumentList.toArray());
        }
    }

    protected Object resolveValueOrError(Argument argument, ConversionContext conversionContext, Optional<?> result) {
        return result.orElseThrow(() -> {
            RuntimeException routingException;
            Optional<ConversionError> lastError = conversionContext.getLastError();
            routingException = lastError.map(conversionError -> (RuntimeException) new ConversionErrorException(argument, conversionError))
                                        .orElseGet(() -> new UnsatisfiedRouteException(argument));
            return routingException;
        });
    }

    @Override
    public RouteMatch<R> fulfill(Map<String, Object> argumentValues) {
        Map<String, Object> oldVariables = getVariables();
        Map<String, Object> newVariables = new LinkedHashMap<>(oldVariables);
        for (Argument requiredArgument : getArguments()) {
            String name = requiredArgument.getName();
            Object value = argumentValues.get(name);
            if(value != null) {
                if(value instanceof UnresolvedArgument) {
                    newVariables.put(name, value);
                }
                else {
                    ArgumentConversionContext conversionContext = ConversionContext.of(requiredArgument);
                    Optional converted = conversionService.convert(value, conversionContext);
                    Object result = converted.isPresent() ? converted.get() : conversionContext.getLastError().orElse(null);
                    if(result != null) {
                        newVariables.put(name, result);
                    }
                }
            }
        }
        Set<String> argumentNames = argumentValues.keySet();
        List<Argument> requiredArguments = getRequiredArguments()
                .stream()
                .filter(arg -> !argumentNames.contains(arg.getName()))
                .collect(Collectors.toList());


        return newFulfilled(newVariables, requiredArguments);
    }

    protected abstract RouteMatch<R> newFulfilled(Map<String, Object> newVariables, List<Argument> requiredArguments);
}