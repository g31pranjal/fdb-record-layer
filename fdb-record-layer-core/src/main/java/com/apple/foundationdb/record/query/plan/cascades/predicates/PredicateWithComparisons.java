/*
 * PredicateWithComparisons.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2024 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.query.plan.cascades.predicates;

import com.apple.foundationdb.record.query.expressions.Comparisons;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Tag interface to announce that the implementing class can provide
 * {@link com.apple.foundationdb.record.query.expressions.Comparisons.Comparison}s upon request.
 */
public interface PredicateWithComparisons {
    /**
     * Return all comparisons this predicate uses.
     * @return a list of comparisons
     */
    @Nonnull
    List<Comparisons.Comparison> getComparisons();
}
