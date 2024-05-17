/*
 * LuceneAutoCompletedMatchesTest.java
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

package com.apple.foundationdb.record.lucene;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.lucene.analysis.Analyzer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LuceneAutoCompletedMatchesTest {
    private static Analyzer getTestAnalyzer() {
        return new EmailCjkSynonymAnalyzer(EmailCjkSynonymAnalyzerFactory.MINIMAL_STOP_WORDS, 3, 3, 255, true, false, null);
    }

    @SuppressWarnings("unused") // used as argument source for parameterized test
    static Stream<Arguments> searchForQua() {
        return ImmutableList.of(
                Arguments.of("quality", 0, ImmutableList.of("quality")),
                Arguments.of("The basic qualia of objects", 0, ImmutableList.of("qualia")),
                Arguments.of("Quality over quantity!", 0, ImmutableList.of("Quality", "quantity")),
                Arguments.of("Quality over quantity!", 1, ImmutableList.of("Quality over", "quantity")),
                Arguments.of("Quality over quantity!", 5, ImmutableList.of("Quality over quantity", "quantity")),
                Arguments.of("quorum logic", 0, ImmutableList.of()),
                Arguments.of("example qua example", 0, ImmutableList.of("qua")),
                Arguments.of("example qua example", 1, ImmutableList.of("qua example"))).stream();
    }

    @ParameterizedTest(name = "searchForQua[text={1}]")
    @MethodSource
    void searchForQua(String text, int numAdditionalTokens, List<String> expected) throws IOException {
        assertComputeAllMatches("qua", Collections.emptyList(), ImmutableSet.of("qua"), text, numAdditionalTokens, expected);
    }

    static Stream<Arguments> searchForCjk() {
        return ImmutableList.of(
                Arguments.of("世上无难事", "上无", ImmutableList.of("上"), "无", 0, ImmutableList.of("上无")),
                Arguments.of("世上无难事 上无", "上无 ", ImmutableList.of("上", "无"), null, 2, ImmutableList.of("上无难事", "上无")),
                Arguments.of("世上无难事 上无 english word", "上无 eng", ImmutableList.of("上", "无"), "eng", 1, ImmutableList.of("上无 english word")),
                Arguments.of("世の中に難しいことなんてないよ の中に難し", "の中に", ImmutableList.of("の", "中"), "に", 3, ImmutableList.of("の中に難しい", "の中に難し")),
                Arguments.of("世ノ中ニシイコトナンテナ", "シイコ", ImmutableList.of("シ", "イ"), "コ", 3, ImmutableList.of("シイコトナン")),
                Arguments.of("世ノ中ニシイenglishコトナンテナ", "シイ", ImmutableList.of("シ"), "イ", 5, ImmutableList.of("シイenglishコトナン")),
                Arguments.of("世ノ中 世の中に難しい", "世", ImmutableList.of(), "世", 3, ImmutableList.of("世ノ中 世", "世の中に")),
                Arguments.of("세상에 english words 어려운 일은 없다", "english wor", ImmutableList.of("english"), "wor", 4, ImmutableList.of("english words 어려운 일")),
                Arguments.of("세상에english words 어려운 일은 없다", "세상에english wor", ImmutableList.of("세상에english"), "wor", 4, ImmutableList.of("세상에english words 어려운 일")),
                Arguments.of("세상에english words 어려운 일은 없다", "에english wor", ImmutableList.of("에english"), "wor", 4, ImmutableList.of()), // Hangul character is treated as alphanumeric here, since it's treated like other scripts' letters
                Arguments.of("세상에 어려운 일은 없다", "어려", ImmutableList.of("어"), "려", 2, ImmutableList.of("어려운 일"))).stream();
    }

    @ParameterizedTest(name = "searchForCjk[text={1}]")
    @MethodSource
    void searchForCjk(String text, String query, List<String> expectedTokens, String expectedPrefix, int numAdditionalTokens, List<String> expected) throws IOException {
        assertComputeAllMatchesForPhrase(query, expectedTokens, expectedPrefix, text, numAdditionalTokens, expected);
    }

    @SuppressWarnings("unused") // used as argument source for parameterized test
    static Stream<Arguments> searchForGoodMor() {
        return ImmutableList.of(
                Arguments.of("Good morning!", 0, ImmutableList.of("Good", "morning")),
                Arguments.of("It is all for the good, and I'll see you on the morrow", 0, ImmutableList.of("good", "morrow")),
                Arguments.of("The more good we do, the more good we see", 0, ImmutableList.of("more", "good", "more", "good")),
                Arguments.of("Good day!", 0, ImmutableList.of("Good")),
                Arguments.of("Morning!", 0, ImmutableList.of("Morning"))).stream();
    }

    @ParameterizedTest(name = "searchForGoodMor[text={0}]")
    @MethodSource
    void searchForGoodMor(String text, int numAdditionalTokens, List<String> expected) {
        assertComputeAllMatches("Good mor", List.of("good"), ImmutableSet.of("mor"), text, numAdditionalTokens, expected);
    }
    
    @SuppressWarnings("unused") // used as argument source for parameterized test
    static Stream<Arguments> searchForHelloWorld() {
        return ImmutableList.of(
                Arguments.of("Hello, world!", 0, ImmutableList.of("Hello", "world")),
                Arguments.of("Hello, worldlings!", 0, ImmutableList.of("Hello")),
                Arguments.of("World--hello!", 0, ImmutableList.of("World", "hello")),
                Arguments.of("Worldly--hello!", 0, ImmutableList.of("hello"))).stream();
    }

    @ParameterizedTest(name = "searchForHelloWorld[text={1}]")
    @MethodSource
    void searchForHelloWorld(String text, int numAdditionalTokens, List<String> expected) {
        assertComputeAllMatches("Hello World ", List.of("hello", "world"), ImmutableSet.of(), text, numAdditionalTokens, expected);
    }

    private static void assertComputeAllMatches(@Nonnull final String queryString, @Nonnull final List<String> expectedTokens,
                                                @Nullable final Set<String> expectedPrefixTokens,
                                                @Nonnull final String text,
                                                final int numAdditionalTokens,
                                                @Nonnull final List<String> expectedMatches) {
        final Analyzer analyzer = getTestAnalyzer();
        LuceneAutoCompleteHelpers.AutoCompleteTokens tokens = LuceneAutoCompleteHelpers.getQueryTokens(analyzer, queryString);
        assertEquals(expectedTokens, tokens.getQueryTokens());
        assertEquals(expectedPrefixTokens, tokens.getPrefixTokens());
        List<String> match =
                LuceneAutoCompleteHelpers.computeAllMatches("text", analyzer, text, tokens, numAdditionalTokens);
        assertEquals(expectedMatches, match);
    }

    @SuppressWarnings("unused") // used as argument source for parameterized test
    static Stream<Arguments> searchForHelloWorldInPhrase() {
        return ImmutableList.of(
                Arguments.of("Hello, world!", 0, ImmutableList.of("Hello, world")),
                Arguments.of("world, Hello!", 0, ImmutableList.of()),
                Arguments.of("Hello to the entire world!", 0, ImmutableList.of()),
                Arguments.of("Hello to the entire hello, world!", 0, ImmutableList.of("hello, world")),
                Arguments.of("Hello, hello, world, world!", 0, ImmutableList.of("hello, world")),
                Arguments.of("Hello, hello, world, hello World!", 0, ImmutableList.of("hello, world", "hello World")),
                Arguments.of("Hello, hello, world, hello World!", 1, ImmutableList.of("hello, world, hello", "hello World"))).stream();
    }

    @ParameterizedTest(name = "searchForHelloWorldInPhrase[text={1}]")
    @MethodSource
    void searchForHelloWorldInPhrase(final String text, int numAdditionalTokens, final List<String> expected) {
        assertComputeAllMatchesForPhrase("Hello World ", List.of("hello", "world"), null, text, numAdditionalTokens, expected);
    }

    @SuppressWarnings("unused") // used as argument source for parameterized test
    static Stream<Arguments> searchForGoodMorInPhrase() {
        return ImmutableList.of(
                Arguments.of("Good Morning!", 0, ImmutableList.of("Good Morning")),
                Arguments.of("Good Morrow Morning!", 0, ImmutableList.of("Good Morrow")),
                Arguments.of("Morning!", 0, ImmutableList.of()),
                Arguments.of("Morning, Good it is! (Yoda)", 0, ImmutableList.of()),
                Arguments.of("Good, Good, good, more mornings!", 0, ImmutableList.of("good, more"))).stream();
    }

    @ParameterizedTest(name = "searchForGoodMorInPhrase[text={1}]")
    @MethodSource
    void searchForGoodMorInPhrase(final String text, int numAdditionalTokens, final List<String> expected) {
        assertComputeAllMatchesForPhrase("Good Mor", List.of("good"), "mor", text, numAdditionalTokens, expected);
    }

    private static void assertComputeAllMatchesForPhrase(@Nonnull final String queryString,
                                                         @Nonnull final List<String> expectedTokens,
                                                         @Nullable final String expectedPrefixToken,
                                                         @Nonnull final String text,
                                                         final int numAdditionalTokens,
                                                         @Nonnull final List<String> expectedMatches) {
        final Analyzer analyzer = getTestAnalyzer();
        LuceneAutoCompleteHelpers.AutoCompleteTokens tokens = LuceneAutoCompleteHelpers.getQueryTokens(analyzer, queryString);
        assertEquals(expectedTokens, tokens.getQueryTokens());
        assertEquals(expectedPrefixToken, tokens.getPrefixTokenOrNull());
        List<String> match =
                LuceneAutoCompleteHelpers.computeAllMatchesForPhrase("text", analyzer, text, tokens, numAdditionalTokens);
        assertEquals(expectedMatches, match);
    }

    @Test
    void autoCompleteMatchesWithStopWord() {
        assertComputeAllMatchesForPhrase("United States of Ameri", List.of("united", "states"), "ameri", "United States of America", 0, ImmutableList.of("United States of America"));
        assertComputeAllMatchesForPhrase("United States of", List.of("united", "states"), null, "United States of America", 1, ImmutableList.of("United States of America"));
        assertComputeAllMatchesForPhrase("United States", List.of("united"), "states", "United States of America", 1, ImmutableList.of("United States of America"));
    }
}
