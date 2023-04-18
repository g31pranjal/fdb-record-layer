/*
 * AlphanumericLengthFilter.java
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

import com.google.common.base.Preconditions;
import org.apache.lucene.analysis.FilteringTokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.util.Objects;

/**
 * A Length Filter which ignores non-alphanumeric types.
 */
public final class AlphanumericLengthFilter extends FilteringTokenFilter {
    private final int minAlphanumLength;
    private final int maxAlphanumLength;

    private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    AlphanumericLengthFilter(final TokenStream in,
                             final int minAlphanumLength,
                             final int maxAlphanumLength) {
        super(in);
        Preconditions.checkArgument(minAlphanumLength >= 0,
                "minimum length must be greater than or equal to zero");

        Preconditions.checkArgument(minAlphanumLength <= maxAlphanumLength,
                "maximum length must not be greater than minimum length");

        this.minAlphanumLength = minAlphanumLength;
        this.maxAlphanumLength = maxAlphanumLength;
    }

    @Override
    protected boolean accept() {
        String typeAttStr = typeAtt.type();
        if (UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.IDEOGRAPHIC].equals(typeAttStr) ||
                UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.HANGUL].equals(typeAttStr) ||
                UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.HIRAGANA].equals(typeAttStr) ||
                UAX29URLEmailTokenizer.TOKEN_TYPES[UAX29URLEmailTokenizer.KATAKANA].equals(typeAttStr)
        ) {
            return true;
        } else {
            int len = termAtt.length();
            return (len >= minAlphanumLength && len <= maxAlphanumLength);
        }
    }

    @Override
    @SuppressWarnings("PMD.CloseResource") //nothing to actually close
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlphanumericLengthFilter)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final AlphanumericLengthFilter that = (AlphanumericLengthFilter)o;
        return minAlphanumLength == that.minAlphanumLength && maxAlphanumLength == that.maxAlphanumLength;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), minAlphanumLength, maxAlphanumLength);
    }
}
