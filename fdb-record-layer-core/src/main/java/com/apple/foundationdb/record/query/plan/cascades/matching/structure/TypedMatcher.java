/*
 * TypedMatcher.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2022 Apple Inc. and the FoundationDB project authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.apple.foundationdb.record.query.plan.cascades.matching.structure;

import com.apple.foundationdb.annotation.API;

import javax.annotation.Nonnull;
import java.util.stream.Stream;

/**
 * A {@code TypedMatcher} matches a data structure that is at least of a certain type.
 *
 * <p>
 * Extreme care should be taken when implementing <code>BindingMatcher</code>s, since it can be very delicate.
 * In particular, matchers may (or may not) be reused between successive rule calls and should be stateless.
 * Additionally, implementors of <code>TypedMatcher</code> must use the (default) reference equals.
 * </p>
 * @param <T> the type that this matcher binds to
 */
@API(API.Status.EXPERIMENTAL)
public class TypedMatcher<T> implements BindingMatcher<T> {
    @Nonnull
    private final Class<T> bindableClass;

    public TypedMatcher(@Nonnull final Class<T> bindableClass) {
        this.bindableClass = bindableClass;
    }

    @Nonnull
    @Override
    public Class<T> getRootClass() {
        return bindableClass;
    }

    @Nonnull
    @Override
    public Stream<PlannerBindings> bindMatchesSafely(@Nonnull PlannerBindings outerBindings, @Nonnull T in) {
        return Stream.of(PlannerBindings.from(this, in));
    }

    @Override
    public String explainMatcher(@Nonnull final Class<?> atLeastType, @Nonnull final String boundId, @Nonnull final String indentation) {
        if (getRootClass().isAssignableFrom(atLeastType)) {
            return "case _ => success ";
        } else {
            return "case _: " + getRootClass().getSimpleName() + " => success ";
        }
    }

    @Nonnull
    public static <T> TypedMatcher<T> typed(@Nonnull final Class<T> bindableClass) {
        return new TypedMatcher<>(bindableClass);
    }
}
