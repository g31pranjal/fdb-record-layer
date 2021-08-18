/*
 * FDBStreamAggregateTest.java
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

package com.apple.foundationdb.record.provider.foundationdb.query;

import com.apple.foundationdb.record.Bindings;
import com.apple.foundationdb.record.EvaluationContext;
import com.apple.foundationdb.record.ExecuteProperties;
import com.apple.foundationdb.record.TestRecords1Proto;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext;
import com.apple.foundationdb.record.query.plan.ScanComparisons;
import com.apple.foundationdb.record.query.plan.plans.QueryResult;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryScanPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryStreamingAggregatePlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryTypeFilterPlan;
import com.apple.foundationdb.record.query.plan.temp.GroupExpressionRef;
import com.apple.foundationdb.record.query.plan.temp.Quantifier;
import com.apple.foundationdb.record.query.predicates.AggregateValue;
import com.apple.foundationdb.record.query.predicates.AggregateValues;
import com.apple.foundationdb.record.query.predicates.FieldValue;
import com.apple.foundationdb.record.query.predicates.QuantifiedColumnValue;
import com.apple.foundationdb.record.query.predicates.Value;
import com.apple.test.Tags;
import com.google.common.collect.ImmutableSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Tests related to planning and executing queries with string collation.
 */
@Tag(Tags.RequiresFDB)
public class FDBStreamAggregateTest extends FDBRecordStoreQueryTestBase {

    /**
     * The database contains:
     * -----------------------------------------------------------------------------------------------------------
     * | recno | NumValue2 | NumValue3Indexed | StrValueIndexed |
     * ----------------------------------------------------------
     * |     0 |         0 |                0 |             "0" |
     * ----------------------------------------------------------
     * |     1 |         1 |                0 |             "0" |
     * ----------------------------------------------------------
     * |     2 |         2 |                1 |             "0" |
     * ----------------------------------------------------------
     * |     3 |         3 |                1 |             "1" |
     * ----------------------------------------------------------
     * |     4 |         4 |                2 |             "1" |
     * ----------------------------------------------------------
     * |     5 |         5 |                2 |             "1" |
     * ----------------------------------------------------------
     */
    @BeforeEach
    public void setup() throws Exception {
        populateDB(5);
    }

    @Test
    public void noAggregateGroupByNone() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf());
        }
    }

    @Test
    public void aggregateOneGroupByOne() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            AggregatePlanBuilder builder = new AggregatePlanBuilder();
            RecordQueryPlan plan = builder
                    .withAggregateValue("num_value_2", "SumInteger")
                    .withGroupCriterion("num_value_3_indexed")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(0, 1), resultOf(1, 5), resultOf(2, 9));
        }
    }

    @Test
    public void aggregateOneGroupByNone() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .withAggregateValue("num_value_2", "SumInteger")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(15));
        }
    }

    @Test
    public void noAggregateGroupByOne() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .withGroupCriterion("num_value_3_indexed")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(0), resultOf(1), resultOf(2));
        }
    }

    @Test
    public void aggregateOneGroupByTwo() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .withAggregateValue("num_value_2", "SumInteger")
                    .withGroupCriterion("num_value_3_indexed")
                    .withGroupCriterion("str_value_indexed")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(0, "0", 1), resultOf(1, "0", 2), resultOf(1, "1", 3), resultOf(2, "1", 9));
        }
    }

    @Test
    public void aggregateTwoGroupByTwo() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .withAggregateValue("num_value_2", "SumInteger")
                    .withAggregateValue("num_value_2", "MinInteger")
                    .withGroupCriterion("num_value_3_indexed")
                    .withGroupCriterion("str_value_indexed")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(0, "0", 1, 0), resultOf(1, "0", 2, 2), resultOf(1, "1", 3, 3), resultOf(2, "1", 9, 4));
        }
    }

    @Test
    public void aggregateThreeGroupByTwo() throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context, NO_HOOK);

            RecordQueryPlan plan = new AggregatePlanBuilder()
                    .withAggregateValue("num_value_2", "SumInteger")
                    .withAggregateValue("num_value_2", "MinInteger")
                    .withAggregateValue("num_value_2", "AvgInteger")
                    .withGroupCriterion("num_value_3_indexed")
                    .withGroupCriterion("str_value_indexed")
                    .build();

            List<QueryResult> result = executePlan(plan);
            assertResults(result, resultOf(0, "0", 1, 0, 0.5), resultOf(1, "0", 2, 2, 2.0), resultOf(1, "1", 3, 3, 3.0), resultOf(2, "1", 9, 4, 4.5));
        }
    }

    private void populateDB(final int numRecords) throws Exception {
        try (FDBRecordContext context = openContext()) {
            openSimpleRecordStore(context);

            TestRecords1Proto.MySimpleRecord.Builder recBuilder = TestRecords1Proto.MySimpleRecord.newBuilder();
            for (int i = 0; i <= numRecords; i++) {
                recBuilder.setRecNo(i);
                recBuilder.setNumValue2(i);
                recBuilder.setNumValue3Indexed(i / 2); // some field that changes every 2nd record
                recBuilder.setStrValueIndexed(Integer.toString(i / 3)); // some field that changes every 3rd record
                recordStore.saveRecord(recBuilder.build());
            }
            commit(context);
        }
    }

    @Nonnull
    private List<QueryResult> executePlan(final RecordQueryPlan plan) throws InterruptedException, java.util.concurrent.ExecutionException {
        Bindings bindings = Bindings.newBuilder().build();
        return plan.executePlan(recordStore, EvaluationContext.forBindings(bindings), null, ExecuteProperties.SERIAL_EXECUTE).asList().get();
    }

    private void assertResults(List<QueryResult> actual, final List<?>... expected) {
        Assertions.assertEquals(expected.length, actual.size());
        for (int i = 0 ; i < actual.size() ; i++) {
            assertResult(actual.get(i), expected[i]);
        }
    }

    private void assertResult(final QueryResult actual, final List<?> expected) {
        Assertions.assertEquals(actual.size(), expected.size());
        for (int i = 0 ; i < actual.size() ; i++) {
            Assertions.assertEquals(expected.get(i), actual.get(i).getResultElement());
        }
    }

    private List<?> resultOf(Object... objs) {
        return Arrays.asList(objs);
    }

    private static class AggregatePlanBuilder {
        private final Quantifier.Physical quantifier;
        private final List<AggregateValue<?>> aggregateValues;
        private final List<Value> groupValues;

        public AggregatePlanBuilder() {
            this.quantifier = createBaseQuantifier();
            aggregateValues = new ArrayList<>();
            groupValues = new ArrayList<>();
        }

        public AggregatePlanBuilder withAggregateValue(final String fieldName, final String aggregateType) {
            this.aggregateValues.add(createAggregateValue(createValue(fieldName), aggregateType));
            return this;
        }

        public AggregatePlanBuilder withGroupCriterion(final String fieldName) {
            this.groupValues.add(createValue(fieldName));
            return this;
        }

        public RecordQueryPlan build() {
            return new RecordQueryStreamingAggregatePlan(quantifier, groupValues, aggregateValues);
        }

        private Value createValue(final String fieldName) {
            return new FieldValue(QuantifiedColumnValue.of(quantifier.getAlias(), 1), Collections.singletonList(fieldName));
        }

        private Quantifier.Physical createBaseQuantifier() {
            RecordQueryScanPlan scanPlan = new RecordQueryScanPlan(ImmutableSet.of("MySimpleRecord", "MyOtherRecord"), ScanComparisons.EMPTY, false);
            RecordQueryTypeFilterPlan filterPlan = new RecordQueryTypeFilterPlan(scanPlan, Collections.singleton("MySimpleRecord"));
            return Quantifier.physical(GroupExpressionRef.of(filterPlan));
        }

        private AggregateValue<?> createAggregateValue(Value value, String aggregateType) {
            switch (aggregateType) {
                case ("SumInteger"):
                    return new AggregateValues.SumInteger(value);
                case ("MinInteger"):
                    return new AggregateValues.MinInteger(value);
                case ("AvgInteger"):
                    return new AggregateValues.AvgInteger(value);
                default:
                    throw new IllegalArgumentException("Cannot parse function name " + aggregateType);
            }
        }
    }
}
