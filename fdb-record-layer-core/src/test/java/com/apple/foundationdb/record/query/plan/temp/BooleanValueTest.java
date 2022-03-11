/*
 * RelOpTest.java
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

package com.apple.foundationdb.record.query.plan.temp;

import com.apple.foundationdb.record.query.expressions.Comparisons;
import com.apple.foundationdb.record.query.plan.temp.dynamic.DynamicSchema;
import com.apple.foundationdb.record.query.predicates.AndPredicate;
import com.apple.foundationdb.record.query.predicates.ConstantPredicate;
import com.apple.foundationdb.record.query.predicates.FieldValue;
import com.apple.foundationdb.record.query.predicates.LiteralValue;
import com.apple.foundationdb.record.query.predicates.OrPredicate;
import com.apple.foundationdb.record.query.predicates.QuantifiedColumnValue;
import com.apple.foundationdb.record.query.predicates.QueryPredicate;
import com.apple.foundationdb.record.query.predicates.Value;
import com.apple.foundationdb.record.query.predicates.ValuePredicate;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Tests different aspects of functionality provided by {@link BooleanValue}.
 * <ul>
 *   <li>Compile-time evaluation of simple constant expressions.</li>
 *   <li>Manipulation of field expressions such that they become map-able to {@link QueryPredicate}.</li>
 *   <li>Correctness of mapping {@link RelOpValue} functions to corresponding {@link QueryPredicate}s.</li>
 * </ul>
 */
class BooleanValueTest {
    private static final FieldValue F = new FieldValue(QuantifiedColumnValue.of(CorrelationIdentifier.UNGROUNDED, 0), ImmutableList.of("f"), Type.primitiveType(Type.TypeCode.INT));
    private static final LiteralValue<Integer> INT_1 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.INT), 1);
    private static final LiteralValue<Integer> INT_2 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.INT), 2);
    private static final LiteralValue<Integer> INT_NULL = new LiteralValue<>(Type.primitiveType(Type.TypeCode.INT), null);
    private static final LiteralValue<Long> LONG_1 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.LONG), 1L);
    private static final LiteralValue<Long> LONG_2 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.LONG), 2L);
    private static final LiteralValue<Float> FLOAT_1 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.FLOAT), 1.0F);
    private static final LiteralValue<Float> FLOAT_2 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.FLOAT), 2.0F);
    private static final LiteralValue<Double> DOUBLE_1 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.DOUBLE), 1.0);
    private static final LiteralValue<Double> DOUBLE_2 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.DOUBLE), 2.0);
    private static final LiteralValue<String> STRING_1 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.STRING), "a");
    private static final LiteralValue<String> STRING_2 = new LiteralValue<>(Type.primitiveType(Type.TypeCode.STRING), "b");
    private static final DynamicSchema.Builder dynamicSchemaBuilder = DynamicSchema.newBuilder().setName("foo").setPackage("a.b.c");
    @SuppressWarnings({"ConstantConditions"})
    private static final ParserContext parserContext = new ParserContext(null, dynamicSchemaBuilder, null, null);

    static class BinaryPredicateTestProvider implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
            return Stream.of(
                    Arguments.of(List.of(INT_1, INT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, INT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, INT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, INT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, INT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, INT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(LONG_1, LONG_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, LONG_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, LONG_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, LONG_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, LONG_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(FLOAT_1, FLOAT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, FLOAT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, FLOAT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(DOUBLE_1, DOUBLE_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, DOUBLE_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, DOUBLE_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(STRING_1, STRING_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_1, STRING_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_2, STRING_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_2, STRING_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_2, STRING_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_1, STRING_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_1, STRING_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_2, STRING_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(STRING_1, STRING_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(LONG_1, INT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, INT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, INT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, INT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, INT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, INT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, INT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(INT_1, LONG_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, LONG_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, LONG_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, LONG_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, LONG_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(FLOAT_1, INT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, INT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, INT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, INT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, INT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, INT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, INT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(INT_1, FLOAT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, FLOAT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, FLOAT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, FLOAT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, FLOAT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(DOUBLE_1, INT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, INT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, INT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, INT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, INT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, INT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, INT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, INT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(INT_1, DOUBLE_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, DOUBLE_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, DOUBLE_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, DOUBLE_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_2, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, DOUBLE_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_1, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(FLOAT_1, LONG_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, LONG_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, LONG_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, LONG_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, LONG_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(LONG_1, FLOAT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, FLOAT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, FLOAT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, FLOAT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, FLOAT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(DOUBLE_1, LONG_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, LONG_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, LONG_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, LONG_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, LONG_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, LONG_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, LONG_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),


                    Arguments.of(List.of(LONG_1, DOUBLE_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, DOUBLE_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, DOUBLE_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, DOUBLE_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_2, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, DOUBLE_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_2, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(LONG_1, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(DOUBLE_1, FLOAT_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, FLOAT_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, FLOAT_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_2, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_2, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(DOUBLE_1, FLOAT_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(FLOAT_1, DOUBLE_1), new RelOpValue.EqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_1), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.LtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, DOUBLE_1), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, DOUBLE_1), new RelOpValue.GtFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_2, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_1), new RelOpValue.LteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_2, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(FLOAT_1, DOUBLE_1), new RelOpValue.GteFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(INT_NULL, INT_NULL), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.EqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_NULL), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.NotEqualsFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_NULL), new RelOpValue.LtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_NULL), new RelOpValue.GtFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_NULL), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_NULL), new RelOpValue.LteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_2), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_2, INT_NULL), new RelOpValue.GteFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_NULL, INT_NULL), new RelOpValue.GteFn(), ConstantPredicate.FALSE),

                    /* translation of predicates involving a field value, make sure field value is always LHS */
                    Arguments.of(List.of(F, INT_1), new RelOpValue.EqualsFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.EQUALS, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.EqualsFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.EQUALS, 1))),
                    Arguments.of(List.of(F, INT_1), new RelOpValue.NotEqualsFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.NOT_EQUALS, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.NotEqualsFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.NOT_EQUALS, 1))),
                    Arguments.of(List.of(F, INT_1), new RelOpValue.LtFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.LESS_THAN, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.LtFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.GREATER_THAN, 1))),
                    Arguments.of(List.of(F, INT_1), new RelOpValue.GtFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.GREATER_THAN, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.GtFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.LESS_THAN, 1))),
                    Arguments.of(List.of(F, INT_1), new RelOpValue.LteFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.LESS_THAN_OR_EQUALS, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.LteFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.GREATER_THAN_OR_EQUALS, 1))),
                    Arguments.of(List.of(F, INT_1), new RelOpValue.GteFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.GREATER_THAN_OR_EQUALS, 1))),
                    Arguments.of(List.of(INT_1, F), new RelOpValue.GteFn(), new ValuePredicate(F, new Comparisons.SimpleComparison(Comparisons.Type.LESS_THAN_OR_EQUALS, 1))),

                    Arguments.of(List.of(INT_1), new RelOpValue.IsNullFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(INT_1), new RelOpValue.NotNullFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(LONG_1), new RelOpValue.IsNullFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(LONG_1), new RelOpValue.NotNullFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(FLOAT_1), new RelOpValue.IsNullFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(FLOAT_1), new RelOpValue.NotNullFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(DOUBLE_1), new RelOpValue.IsNullFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(DOUBLE_1), new RelOpValue.NotNullFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(STRING_1), new RelOpValue.IsNullFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(STRING_1), new RelOpValue.NotNullFn(), ConstantPredicate.TRUE),

                    Arguments.of(List.of(INT_NULL), new RelOpValue.IsNullFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(INT_NULL), new RelOpValue.NotNullFn(), ConstantPredicate.FALSE),

                    Arguments.of(List.of(F), new RelOpValue.IsNullFn(), new ValuePredicate(F, new Comparisons.NullComparison(Comparisons.Type.IS_NULL))),
                    Arguments.of(List.of(F), new RelOpValue.NotNullFn(), new ValuePredicate(F, new Comparisons.NullComparison(Comparisons.Type.NOT_NULL))),

                    /* AND */
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_1, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_2))), new AndOrValue.AndValue.AndFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_2))), new AndOrValue.AndValue.AndFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(F, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_2))), new AndOrValue.AndValue.AndFn(), new AndPredicate(List.of(new ValuePredicate(F,
                                    new Comparisons.SimpleComparison(Comparisons.Type.EQUALS, 1)), ConstantPredicate.TRUE))),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_1, INT_2)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(F, INT_1))), new AndOrValue.AndValue.AndFn(), ConstantPredicate.FALSE),
                    /* OR */
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_1, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_1))), new AndOrValue.OrValue.OrFn(), ConstantPredicate.TRUE),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_1, INT_2))), new AndOrValue.OrValue.OrFn(), ConstantPredicate.FALSE),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(F, INT_1)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_2))), new AndOrValue.OrValue.OrFn(), new OrPredicate(List.of(new ValuePredicate(F,
                                    new Comparisons.SimpleComparison(Comparisons.Type.EQUALS, 1)), ConstantPredicate.TRUE))),
                    Arguments.of(List.of(new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(INT_2, INT_2)),
                            new RelOpValue.EqualsFn().encapsulate(parserContext, List.of(F, INT_1))), new AndOrValue.OrValue.OrFn(), ConstantPredicate.TRUE)
                    );
        }
    }

    @ParameterizedTest
    @SuppressWarnings({"rawtypes", "unchecked"})
    @ArgumentsSource(BinaryPredicateTestProvider.class)
    void testPredicate(List<Value> args, BuiltInFunction function, QueryPredicate result) {
        Typed value = function.encapsulate(parserContext, args);
        Assertions.assertTrue(value instanceof BooleanValue);
        Optional<QueryPredicate> maybePredicate = ((BooleanValue)value).toQueryPredicate(CorrelationIdentifier.UNGROUNDED);
        Assertions.assertFalse(maybePredicate.isEmpty());
        Assertions.assertEquals(result, maybePredicate.get());
    }
}
