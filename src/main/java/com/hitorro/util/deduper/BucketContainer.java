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

import com.hitorro.util.core.ArrayUtil;
import com.hitorro.util.core.string.Fmt;
import com.hitorro.util.io.FileUtil;
import com.hitorro.util.io.filefilters.FilenameExtensionFilter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 *
 */
public class BucketContainer {
    private static FilenameExtensionFilter filter = new FilenameExtensionFilter("map", true);
    private int counter = 0;

    private int maxPerBucket;
    private int maxBuckets;
    private int numBuckets;

    private File rootDir;
    private File expiredBuckets;

    private WriteBucket writeBucket;
    private ReadBucket[] readBuckets;

    public BucketContainer(File dir, int maxPerBucket, int maxBucket) throws IOException {
        init(dir, maxPerBucket, maxBucket);
    }

    private void init(final File dir, final int maxPerBucket, final int maxBucket) throws IOException {
        rootDir = dir;
        expiredBuckets = new File(dir, "expired");
        FileUtil.ensureDirectoryExists(rootDir);
        FileUtil.ensureDirectoryExists(expiredBuckets);
        this.maxPerBucket = maxPerBucket;
        this.maxBuckets = maxBucket;
        readBuckets = new ReadBucket[this.maxBuckets];
        loadBuckets(rootDir);
    }

    public boolean exists(long hash) {
        if (writeBucket.exists(hash)) {
            return true;
        }
        for (int i = 0; i < numBuckets; i++) {
            if (readBuckets[i].exists(hash)) {
                return true;
            }
        }
        writeBucket.set(hash);
        return false;
    }

    public void flush() throws IOException {
        writeBucket.flush();
        if (readBuckets[0].isOverCapacity()) {
            ReadBucket b = readBuckets[maxBuckets - 1];
            numBuckets = Math.min(numBuckets + 1, maxBuckets);
            if (b != null) {
                b.moveAndClose(expiredBuckets);
            }
            for (int i = maxBuckets - 1; i > 0; i--) {
                readBuckets[i] = readBuckets[i - 1];
            }
            readBuckets[0] = newBucket();
        }
    }

    private ReadBucket newBucket() throws IOException {
        if (writeBucket != null) {
            writeBucket.flush();
        }
        File file = new File(rootDir, Fmt.S("%s-%s.apply", System.currentTimeMillis(), getCounter()));
        ReadBucket roBucket = new ReadBucket(maxPerBucket, file);
        writeBucket = new WriteBucket(maxPerBucket, file, roBucket);
        return roBucket;
    }

    private boolean loadBuckets(File dir) throws IOException {
        File list[] = dir.listFiles(filter);
        Arrays.sort(list, FileDateComp.comp);
        if (ArrayUtil.nullOrEmpty(list)) {
            numBuckets = 1;
            readBuckets[0] = newBucket();
            return false;
        }
        for (int i = 0; i < list.length; i++) {
            readBuckets[i] = new ReadBucket(maxPerBucket, list[i]);
        }
        numBuckets = list.length;
        writeBucket = new WriteBucket(maxPerBucket, list[0], readBuckets[0]);
        return true;
    }

    private long getCounter() {
        return counter++;
    }


}
