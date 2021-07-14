/*
 * OrderingAttribute.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2021 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.query.plan.temp;

import com.google.common.collect.ImmutableSet;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.Set;

public class OrderingAttribute implements PlannerAttribute<Set<Ordering>> {
    public static final PlannerAttribute<Set<Ordering>> ORDERING = new OrderingAttribute();

    @Nonnull
    @Override
    public Optional<Set<Ordering>> combine(@Nonnull final Set<Ordering> currentProperty, @Nonnull final Set<Ordering> newProperty) {
        if (currentProperty.containsAll(newProperty)) {
            return Optional.empty();
        }

        return Optional.of(ImmutableSet.<Ordering>builder()
                .addAll(currentProperty)
                .addAll(newProperty)
                .build());
    }
}
