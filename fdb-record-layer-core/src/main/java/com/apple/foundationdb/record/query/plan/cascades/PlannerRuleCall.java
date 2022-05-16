/*
 * PlannerRuleCall.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2018 Apple Inc. and the FoundationDB project authors
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

package com.apple.foundationdb.record.query.plan.cascades;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.record.query.plan.cascades.Quantifiers.AliasResolver;
import com.apple.foundationdb.record.query.plan.cascades.expressions.RelationalExpression;
import com.apple.foundationdb.record.query.plan.cascades.matching.structure.BindingMatcher;
import com.apple.foundationdb.record.query.plan.cascades.matching.structure.PlannerBindings;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * A <code>PlannerRuleCall</code> is a context object that supports a single application of a rule to a particular
 * expression. It stores and provides convenient access to various context related to the transformation, such as any
 * bindings, access to the {@link PlanContext}, etc. A <code>PlannerRuleCall</code> is passed to every rule's
 * {@link PlannerRule#onMatch(PlannerRuleCall)} method.
 *
 * Additionally, the rule call implementation is responsible for registering any new expressions generated by the rule
 * via the {@link #yield(ExpressionRef)} method. This registration process includes updating any planner data
 * structures to include the new expression. In general, each planner will have an associated implementation of
 * <code>PlannerRuleCall</code>.
 */
@API(API.Status.EXPERIMENTAL)
public interface PlannerRuleCall {
    /**
     * Returns the alias resolver that is currently in use and maintained by the planner.
     *
     * @return the alias resolver
     */
    @Nonnull
    AliasResolver getAliasResolver();

    /**
     * Return the map of bindings that this rule's matcher expression produced, which includes (by contract) all of the
     * bindings specified by the rule associated with this call.
     * This method should be implemented by rule call implementations, but users of the rule should usually access these
     * via {@link #get(BindingMatcher)}.
     *
     * @return the map of bindings that the rule's matcher expression produced
     */
    @Nonnull
    PlannerBindings getBindings();

    /**
     * Get the planning context with metadata that might be relevant to the planner, such as the list of available
     * indexes.
     *
     * @return a {@link PlanContext} object with various metadata that could affect planning
     */
    @Nonnull
    PlanContext getContext();

    /**
     * Return the bindable that is bound to the given key.
     *
     * @param key the binding from the rule's matcher expression
     * @param <U> the requested return type
     *
     * @return the bindable bound to <code>name</code> in the rule's matcher expression
     *
     * @throws java.util.NoSuchElementException when <code>key</code> is not a valid binding, or is not bound to a
     * bindable
     */
    @Nonnull
    default <U> U get(@Nonnull BindingMatcher<U> key) {
        return getBindings().get(key);
    }

    @Nonnull
    <T> Optional<T> getPlannerConstraint(@Nonnull PlannerConstraint<T> plannerConstraint);

    /**
     * Notify the planner's data structures that the new expression contained in <code>expression</code> has been
     * produced by the rule. This method may be called zero or more times by the rule's <code>onMatch()</code> method.
     *
     * @param expression the expression produced by the rule
     */
    void yield(@Nonnull ExpressionRef<? extends RelationalExpression> expression);

    /**
     * Notify the planner's data structures that a new partial match has been produced by the rule. This method may be
     * called zero or more times by the rule's <code>onMatch()</code> method.
     *
     * @param boundAliasMap the alias map of bound correlated identifiers between query and candidate
     * @param matchCandidate the match candidate
     * @param queryExpression the query expression
     * @param candidateRef the matching reference on the candidate side
     * @param matchInfo an auxiliary structure to keep additional information about the match
     */
    void yieldPartialMatch(@Nonnull final AliasMap boundAliasMap,
                           @Nonnull final MatchCandidate matchCandidate,
                           @Nonnull final RelationalExpression queryExpression,
                           @Nonnull final ExpressionRef<? extends RelationalExpression> candidateRef,
                           @Nonnull final MatchInfo matchInfo);

    <T> void pushConstraint(@Nonnull final ExpressionRef<? extends RelationalExpression> reference,
                            @Nonnull final PlannerConstraint<T> plannerConstraint,
                            @Nonnull final T requirement);

    /**
     * Wrap the given planner expression in an implementation of {@link ExpressionRef} suitable for the planner
     * associated
     * with this rule. Different rule call implementations might use different reference types depending on the
     * specifics
     * of the associated planner.
     *
     * @param expression the planner expression to wrap in a reference type
     * @param <U> the type of the planner expression
     *
     * @return {@code expression} wrapped in a reference
     */
    <U extends RelationalExpression> ExpressionRef<U> ref(U expression);
}
