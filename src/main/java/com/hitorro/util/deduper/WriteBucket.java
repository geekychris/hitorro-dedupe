/*
 * Copyright (c) 2006-2025 Chris Collins
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.hitorro.util.deduper;

import gnu.trove.set.hash.TLongHashSet;
import com.hitorro.util.core.map.TroveMapUtil;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public class WriteBucket implements BucketHash {
    private ReadBucket readBucket;
    private File file;
    private TLongHashSet set;

    public WriteBucket(int size, File f, ReadBucket readBucket) {
        file = f;
        set = new TLongHashSet(size);
        this.readBucket = readBucket;
    }

    public void flush() throws IOException {
        TroveMapUtil.writeLongSetToFile(file, set, true);
        set.clear();
    }

    @Override
    public boolean exists(long hash) {
        return set.contains(hash);
    }

    @Override
    public void set(long hash) {
        set.add(hash);
        readBucket.set(hash);
    }
}