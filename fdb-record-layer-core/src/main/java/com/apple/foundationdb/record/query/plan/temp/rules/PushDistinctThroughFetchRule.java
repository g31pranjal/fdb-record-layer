/*
 * PushDistinctThroughFetchRule.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2019 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.query.plan.temp.rules;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryFetchFromPartialRecordPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryPlan;
import com.apple.foundationdb.record.query.plan.plans.RecordQueryUnorderedPrimaryKeyDistinctPlan;
import com.apple.foundationdb.record.query.plan.temp.CorrelationIdentifier;
import com.apple.foundationdb.record.query.plan.temp.GroupExpressionRef;
import com.apple.foundationdb.record.query.plan.temp.PlannerRule;
import com.apple.foundationdb.record.query.plan.temp.PlannerRuleCall;
import com.apple.foundationdb.record.query.plan.temp.Quantifier;
import com.apple.foundationdb.record.query.plan.temp.matchers.BindingMatcher;
import com.apple.foundationdb.record.query.plan.temp.matchers.PlannerBindings;

import javax.annotation.Nonnull;

import static com.apple.foundationdb.record.query.plan.temp.matchers.RecordQueryPlanMatchers.anyPlan;
import static com.apple.foundationdb.record.query.plan.temp.matchers.RecordQueryPlanMatchers.fetchFromPartialRecordPlan;
import static com.apple.foundationdb.record.query.plan.temp.matchers.RecordQueryPlanMatchers.unorderedPrimaryKeyDistinctPlan;

/**
 * A rule that pushes a {@link RecordQueryUnorderedPrimaryKeyDistinctPlan} <em>through</em> a
 * {@link RecordQueryFetchFromPartialRecordPlan} in order to reduce the number of records
 * prior to a potentially expensive fetch operation.
 *
 * <pre>
 * {@code
 *         +-----------------------------------+                          +---------------------------------+
 *         |                                   |                          |                                 |
 *         |  UnorderedPrimaryKeyDistinctPlan  |                          |  FetchFromPartialRecordPlan     |
 *         |                                   |                          |                                 |
 *         +-----------------+-----------------+                          +----------------+----------------+
 *                           |                                                             |
 *                           |                                                             |
 *                           |                    +------------------->                    |
 *           +---------------+--------------+                            +-----------------+-----------------+
 *           |                              |                            |                                   |
 *           |  FetchFromPartialRecordPlan  |                            |  UnorderedPrimaryKeyDistinctPlan  |
 *           |                              |                            |                                   |
 *           +---------------+--------------+                            +-----------------+-----------------+
 *                           |                                                             |
 *                           |                                                             |
 *                           |                                                             |
 *                    +------+------+                                                      |
 *                    |             |                                                      |
 *                    |  innerPlan  |   +--------------------------------------------------+
 *                    |             |
 *                    +-------------+
 * }
 * </pre>
 *
 */
@API(API.Status.EXPERIMENTAL)
public class PushDistinctThroughFetchRule extends PlannerRule<RecordQueryUnorderedPrimaryKeyDistinctPlan> {
    @Nonnull
    private static final BindingMatcher<RecordQueryPlan> innerPlanMatcher = anyPlan();
    @Nonnull
    private static final BindingMatcher<RecordQueryFetchFromPartialRecordPlan> fetchPlanMatcher =
            fetchFromPartialRecordPlan(innerPlanMatcher);
    @Nonnull
    private static final BindingMatcher<RecordQueryUnorderedPrimaryKeyDistinctPlan> root =
            unorderedPrimaryKeyDistinctPlan(fetchPlanMatcher);

    public PushDistinctThroughFetchRule() {
        super(root);
    }

    @Override
    public void onMatch(@Nonnull PlannerRuleCall call) {
        final PlannerBindings bindings = call.getBindings();

        final RecordQueryFetchFromPartialRecordPlan fetchPlan = bindings.get(fetchPlanMatcher);
        final RecordQueryPlan innerPlan = bindings.get(innerPlanMatcher);

        final CorrelationIdentifier newInnerAlias = CorrelationIdentifier.uniqueID();
        
        final Quantifier.Physical newInnerQuantifier = Quantifier.physical(GroupExpressionRef.of(innerPlan), newInnerAlias);

        final RecordQueryUnorderedPrimaryKeyDistinctPlan pushedDistinctPlan =
                new RecordQueryUnorderedPrimaryKeyDistinctPlan(newInnerQuantifier);

        final RecordQueryFetchFromPartialRecordPlan newFetchPlan =
                new RecordQueryFetchFromPartialRecordPlan(pushedDistinctPlan, fetchPlan.getPushValueFunction());

        // case 2
        call.yield(call.ref(newFetchPlan));
    }
}
