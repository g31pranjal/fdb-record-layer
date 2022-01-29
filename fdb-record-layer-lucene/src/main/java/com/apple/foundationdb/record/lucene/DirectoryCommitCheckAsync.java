/*
 * DirectoryCheck.java
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

package com.apple.foundationdb.record.lucene;

import com.apple.foundationdb.annotation.API;
import com.apple.foundationdb.record.RecordCoreStorageException;
import com.apple.foundationdb.record.lucene.directory.FDBDirectory;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext;
import com.apple.foundationdb.record.provider.foundationdb.IndexMaintainerState;
import com.apple.foundationdb.subspace.Subspace;
import com.apple.foundationdb.tuple.Tuple;
import com.google.common.annotations.VisibleForTesting;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Closes the directory before commit.
 *
 */
@API(API.Status.EXPERIMENTAL)
public class DirectoryCommitCheckAsync implements FDBRecordContext.CommitCheckAsync {
    private static final Logger LOGGER = LoggerFactory.getLogger(LuceneIndexMaintainer.class);
    private final FDBDirectory directory;

    /**
     * Creates a lucene directory from a subspace (keyspace) and a transaction.
     *
     * @param subspace the index subspace that contains the Directory/Files/etc.
     * @param context context
     */
    public DirectoryCommitCheckAsync(@Nonnull Subspace subspace, @Nonnull FDBRecordContext context) {
        this.directory = new FDBDirectory(subspace, context);
    }

    /**
     * Close Lucene Directory.
     *
     * @return CompletableFuture
     */
    @Nonnull
    @Override
    public CompletableFuture<Void> checkAsync() {
        LOGGER.trace("closing directory check");
        return CompletableFuture.runAsync(() -> {
            try {
                IOUtils.close(directory);
            } catch (IOException ioe) {
                LOGGER.error("DirectoryCommitCheckAsync Failed", ioe);
                throw new RecordCoreStorageException("DirectCommitCheckAsyncFailed for directory=" + directory.getSubspace(), ioe);
            }
        },
        directory.getContext().getExecutor());
    }

    @Nonnull
    public Directory getDirectory() {
        return directory;
    }

    /**
     * Attempts to get the commit check from the context and if it cannot find it, creates one and adds it to the context.
     *
     * @param state state
     * @param groupingKey tuple
     * @return DirectoryCommitCheckAsync
     */
    @Nonnull
    protected static DirectoryCommitCheckAsync getOrCreateDirectoryCommitCheckAsync(@Nonnull final IndexMaintainerState state, @Nonnull Tuple groupingKey) {
        synchronized (state.context) {
            DirectoryCommitCheckAsync directoryCheck = state.context.getInSession(getReaderSubspace(state, groupingKey), DirectoryCommitCheckAsync.class);
            if (directoryCheck == null) {
                directoryCheck = new DirectoryCommitCheckAsync(state.indexSubspace.subspace(groupingKey), state.context);
                state.context.addCommitCheck(directoryCheck);
                state.context.putInSessionIfAbsent(getReaderSubspace(state, groupingKey), directoryCheck);
            }
            return directoryCheck;
        }
    }

    /**
     * Get a new instance for {@link DirectoryCommitCheckAsync} and its underlying {@link Directory} for auto-complete suggestions index.
     * This is needed because {@link org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester}
     * needs its directory to be private to it, and have same lifecycle with it.
     */
    @Nonnull
    public static DirectoryCommitCheckAsync getNewDirectoryCommitCheckAsyncForSuggestions(@Nonnull final IndexMaintainerState state, @Nonnull Tuple groupingKey) {
        return new DirectoryCommitCheckAsync(getSuggestionIndexSubspace(state, groupingKey), state.context);
    }

    @Nonnull
    private static Subspace getReaderSubspace(@Nonnull final IndexMaintainerState state, @Nonnull final Tuple groupingKey) {
        return state.indexSubspace.subspace(groupingKey).subspace(Tuple.from("r"));
    }

    @Nonnull
    private static Subspace getSuggestionIndexSubspace(@Nonnull final IndexMaintainerState state, @Nonnull final Tuple groupingKey) {
        return getSuggestionIndexSubspace(state.indexSubspace, groupingKey);
    }

    @VisibleForTesting
    @Nonnull
    public static Subspace getSuggestionIndexSubspace(@Nonnull final Subspace indexSubspace, @Nonnull final Tuple groupingKey) {
        return indexSubspace.subspace(groupingKey).subspace(Tuple.from("s"));
    }
}

