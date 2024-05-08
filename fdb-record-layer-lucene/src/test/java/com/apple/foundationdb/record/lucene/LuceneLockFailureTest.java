/*
 * LucenOnlineIndexingTest.java
 *
 * This source file is part of the FoundationDB open source project
 *
 * Copyright 2015-2023 Apple Inc. and the FoundationDB project authors
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

import com.apple.foundationdb.record.TestRecordsTextProto;
import com.apple.foundationdb.record.lucene.directory.FDBDirectory;
import com.apple.foundationdb.record.metadata.Index;
import com.apple.foundationdb.record.metadata.IndexOptions;
import com.apple.foundationdb.record.provider.common.text.AllSuffixesTextTokenizer;
import com.apple.foundationdb.record.provider.foundationdb.FDBExceptions;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStore;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordStoreTestBase;
import com.apple.foundationdb.record.provider.foundationdb.indexes.TextIndexTestUtils;
import com.apple.foundationdb.record.query.expressions.Query;
import com.apple.foundationdb.record.query.plan.QueryPlanner;
import com.apple.foundationdb.record.util.pair.Pair;
import com.apple.foundationdb.tuple.Tuple;
import org.apache.lucene.index.IndexWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static com.apple.foundationdb.record.lucene.LuceneIndexOptions.INDEX_PARTITION_BY_FIELD_NAME;
import static com.apple.foundationdb.record.lucene.LuceneIndexOptions.INDEX_PARTITION_HIGH_WATERMARK;
import static com.apple.foundationdb.record.lucene.LuceneIndexTest.ENGINEER_JOKE;
import static com.apple.foundationdb.record.lucene.LuceneIndexTest.complexPartitionedIndex;
import static com.apple.foundationdb.record.lucene.LuceneIndexTestUtils.SIMPLE_TEXT_SUFFIXES;
import static com.apple.foundationdb.record.lucene.LuceneIndexTestUtils.createComplexDocument;
import static com.apple.foundationdb.record.lucene.LuceneIndexTestUtils.createSimpleDocument;
import static com.apple.foundationdb.record.lucene.LuceneIndexTestUtils.openRecordStore;
import static com.apple.foundationdb.record.provider.foundationdb.indexes.TextIndexTestUtils.COMPLEX_DOC;
import static com.apple.foundationdb.record.provider.foundationdb.indexes.TextIndexTestUtils.SIMPLE_DOC;

class LuceneLockFailureTest extends FDBRecordStoreTestBase {

    private static final Index SIMPLE_INDEX = SIMPLE_TEXT_SUFFIXES;

    protected static final Index COMPLEX_PARTITIONED = complexPartitionedIndex(Map.of(
            IndexOptions.TEXT_TOKENIZER_NAME_OPTION, AllSuffixesTextTokenizer.NAME,
            INDEX_PARTITION_BY_FIELD_NAME, "timestamp",
            INDEX_PARTITION_HIGH_WATERMARK, "10"));

    private void rebuildIndexMetaData(final FDBRecordContext context, final String document, final Index index) {
        Pair<FDBRecordStore, QueryPlanner> pair = LuceneIndexTestUtils.rebuildIndexMetaData(context, path, document, index, useCascadesPlanner);
        this.recordStore = pair.getLeft();
        this.planner = pair.getRight();
    }

    private void openStoreWithPrefixes(final FDBRecordContext context, final String document, final Index index) {
        this.recordStore = openRecordStore(context, path, metaDataBuilder -> {
//            metaDataBuilder.getRecordType(document)
//                    .setPrimaryKey(concat(recordType(), Key.Expressions.concatenateFields("score", "doc_id")));
            TextIndexTestUtils.addRecordTypePrefix(metaDataBuilder);
            metaDataBuilder.removeIndex(TextIndexTestUtils.SIMPLE_DEFAULT_NAME);
            metaDataBuilder.addIndex(document, index);
        });
    }

    @Test
    void testAddDocument() throws IOException {
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            // Try to add a document - this would fail as the lock is taken by a different directory
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    recordStore.saveRecord(createSimpleDocument(1623L, ENGINEER_JOKE, 2)));
        }
    }

    @Test
    void testDeleteDocument() throws IOException {
        TestRecordsTextProto.SimpleDocument doc = createSimpleDocument(1623L, ENGINEER_JOKE, 2);
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            recordStore.saveRecord(doc);
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock in a second transaction and a separate directory
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            // This fails since the default directory is trying to take a second lock
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    recordStore.deleteRecord(Tuple.from(doc.getDocId())));
        }
    }

    @Test
    void testUpdateDocument() throws IOException {
        TestRecordsTextProto.SimpleDocument doc = createSimpleDocument(1623L, ENGINEER_JOKE, 2);
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            recordStore.saveRecord(doc);
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            // This fails since the default directory is trying to take a second lock
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    recordStore.updateRecord(doc.toBuilder().setText("Blah").build()));
        }
    }

    @Test
    void testDeleteAll() throws IOException {
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            TestRecordsTextProto.SimpleDocument doc = createSimpleDocument(1623L, ENGINEER_JOKE, 2);
            recordStore.saveRecord(doc);
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            // Delete all just deletes the index, not through Lucene
            recordStore.deleteAllRecords();
            context.commit();
        }
    }

    @Test
    void testDeleteWhere() throws IOException {
        try (final FDBRecordContext context = openContext()) {
            openStoreWithPrefixes(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            recordStore.saveRecord(createSimpleDocument(1623L, ENGINEER_JOKE, 2));
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            openStoreWithPrefixes(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            context.commit();
        }
        try (final FDBRecordContext context = openContext()) {
            openStoreWithPrefixes(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    recordStore.deleteRecordsWhere(COMPLEX_DOC, Query.field("group").equalsValue(2)));
        }
    }

    @Test
    void testMerge() throws IOException {
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            recordStore.saveRecord(createSimpleDocument(1623L, ENGINEER_JOKE, 2));
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(SIMPLE_INDEX), context, SIMPLE_INDEX.getOptions());
            // Take the lock, commit the transaction (with the lock) to make the merge fail later
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, SIMPLE_DOC, SIMPLE_INDEX);
            // This fails since the merge tries to take a lock
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    LuceneIndexTestUtils.mergeSegments(recordStore, SIMPLE_INDEX));
        }
    }

    @Test
    void testPartition() throws IOException {
        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            // partition size is 10 for tests
            for (int i = 0; i < 50; i++) {
                recordStore.saveRecord(createComplexDocument(6666L + i, ENGINEER_JOKE, 1, Instant.now().toEpochMilli()));
            }
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            final FDBDirectory directory = new FDBDirectory(recordStore.indexSubspace(COMPLEX_PARTITIONED), context, COMPLEX_PARTITIONED.getOptions());
            // Take the lock, commit the transaction (with the lock) to make the merge fail later
            directory.obtainLock(IndexWriter.WRITE_LOCK_NAME);
            context.commit();
        }

        try (final FDBRecordContext context = openContext()) {
            rebuildIndexMetaData(context, COMPLEX_DOC, COMPLEX_PARTITIONED);
            // This fails since the merge tries to take a lock
            // TODO: Why does this not fail?
            Assertions.assertThrows(FDBExceptions.FDBStoreLockTakenException.class, () ->
                    LuceneIndexTestUtils.rebalancePartitions(recordStore, COMPLEX_PARTITIONED));
        }
    }
}

