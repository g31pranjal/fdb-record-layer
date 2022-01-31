/*
 * SynonymAnalyzer.java
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

package com.apple.foundationdb.record.lucene.synonym;

import com.apple.foundationdb.record.RecordCoreException;
import com.apple.foundationdb.record.lucene.LuceneAnalyzerFactory;
import com.apple.foundationdb.record.metadata.Index;
import com.apple.foundationdb.record.metadata.IndexOptions;
import com.google.auto.service.AutoService;
import com.google.common.collect.Iterators;
import com.google.common.io.Closer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.StopwordAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.FlattenGraphFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

/**
 * The analyzer for index with synonym enabled.
 * Only for in fly analysis during query time.
 * The synonyms are not indexed to disk. So no index rebuilding is needed if the wordnet file gets updated.
 */
public class SynonymAnalyzer extends StopwordAnalyzerBase {
    @Nonnull
    private final SynonymMap synonymMap;

    @Nonnull
    private final SynonymSetConfig synonymSetConfig;

    @Nonnull
    public SynonymSetConfig getConfig() {
        return synonymSetConfig;
    }

    @Nullable
    public CharArraySet getStopwords() {
        return stopwords;
    }

    public SynonymAnalyzer(@Nullable CharArraySet stopwords) {
        this(stopwords, SynonymSetConfig.DEFAULT);
    }

    public SynonymAnalyzer(@Nullable CharArraySet stopwords, @Nonnull SynonymSetConfig synonymSetConfig) {
        super(stopwords);
        this.synonymSetConfig = synonymSetConfig;
        this.synonymMap = buildSynonymMap();
    }

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        final StandardTokenizer src = new StandardTokenizer();
        TokenStream tok = new LowerCaseFilter(src);
        tok = new StopFilter(tok, stopwords);
        tok = new SynonymGraphFilter(tok, getSynonymMap(), true);

        return new TokenStreamComponents(src, tok);
    }

    @Override
    protected TokenStream normalize(String fieldName, TokenStream in) {
        return new LowerCaseFilter(in);
    }

    @Nonnull
    private SynonymMap getSynonymMap() {
        return synonymMap;
    }

    private SynonymMap buildSynonymMap() {
        try (Closer closer = Closer.create()) {
            InputStream is = new SequenceInputStream(Iterators.asEnumeration(
                    synonymSetConfig.getFileNames().stream()
                            .map(fileName -> closer.register(Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)))
                            .iterator()));
            SynonymMap.Parser parser = new SolrSynonymParser(true, true, new Analyzer() {
                @Override
                protected TokenStreamComponents createComponents(String fieldName) {
                    final StandardTokenizer src = new StandardTokenizer();
                    TokenStream tok = new LowerCaseFilter(src);
                    tok = new FlattenGraphFilter(tok);
                    return new TokenStreamComponents(src, tok);
                }
            });
            parser.parse(new InputStreamReader(is, StandardCharsets.UTF_8));
            return parser.build();
        } catch (IOException | ParseException ex) {
            throw new RecordCoreException("Failed to parse wordnet for synonym analyzer", ex);
        }
    }

    @AutoService(LuceneAnalyzerFactory.class)
    public static class SynonymAnalyzerFactory implements LuceneAnalyzerFactory {
        public static final String ANALYZER_NAME = "SYNONYM";

        @Nonnull
        @Override
        public String getName() {
            return ANALYZER_NAME;
        }

        @SuppressWarnings("deprecation")
        @Nonnull
        @Override
        public Analyzer getIndexAnalyzer(@Nonnull Index index) {
            return new StandardAnalyzer();
        }

        @SuppressWarnings("deprecation")
        @Nonnull
        @Override
        public Analyzer getQueryAnalyzer(@Nonnull Index index, @Nonnull Analyzer indexAnalyzer) {
            String name = index.getOption(IndexOptions.TEXT_SYNONYM_SET_NAME_OPTION);
            if (name == null) {
                name = SynonymSetConfig.DEFAULT.getName();
            }
            return SynonymAnalyzerRegistryImpl.instance().getAnalyzer(name);
        }
    }
}
