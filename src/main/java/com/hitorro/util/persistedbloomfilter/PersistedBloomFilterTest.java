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
package com.hitorro.util.persistedbloomfilter;

import com.hitorro.util.io.FileUtil;
import com.hitorro.util.testframework.EnhancedTestCase;
import com.hitorro.util.testframework.HTTest;
import com.hitorro.util.testframework.RunLevel;

import java.io.File;
import java.io.IOException;

/**
 *
 */
@HTTest(runlevel = RunLevel.Smoke,
        email = "chris@hitorro.com",
        description = "Persisted bloom filter")
public class PersistedBloomFilterTest extends EnhancedTestCase {
    public static RangeBasedBVExerciser r1 = new RangeBasedBVExerciser(0, 123, 10000);
    public static RangeBasedBVExerciser r2 = new RangeBasedBVExerciser((long) 3 << 24, 124, 12665);
    public static RangeBasedBVExerciser r3 = new RangeBasedBVExerciser((long) 5 << 24, 12, 12345);
    public static RangeBasedBVExerciser r4 = new RangeBasedBVExerciser((long) 7 << 24, 175, 12345);

    public void testSetGet() throws IOException {
        File dir = getOutputFileRelative("ids");
        FileUtil.deleteAndRecreateDir(dir);
        FileUtil.ensureDirectoryExists(dir);
        PersistedBloomFilter manager = new PersistedBloomFilter(dir, -1, 2, 24, 10, 1000);
        assertTrue(r1.assertAll(false, manager));
        assertTrue(r2.assertAll(false, manager));
        assertTrue(r3.assertAll(false, manager));
        assertTrue(r4.assertAll(false, manager));
        manager.flushToDisk();
        r1.set(manager);
        r2.set(manager);
        r3.set(manager);
        r4.set(manager);
        manager.flushToDisk();
        assertTrue(r1.assertAll(true, manager));
        assertTrue(r2.assertAll(true, manager));
        assertTrue(r3.assertAll(true, manager));
        assertTrue(r4.assertAll(true, manager));
    }
}

class RangeBasedBVExerciser {
    private long start;
    private int incrementor;
    private int iters;

    public RangeBasedBVExerciser(long start, int incrementor, int iters) {
        this.start = start;
        this.incrementor = incrementor;
        this.iters = iters;
    }

    public void set(PersistedBloomFilter manager) throws IOException {
        for (int i = 0; i < iters; i++) {
            manager.addIdToSet(start + (i * incrementor));
        }
    }

    public boolean assertAll(boolean flag, PersistedBloomFilter manager) throws IOException {
        for (int i = 0; i < iters; i++) {
            boolean f = manager.idExists(start + (i * incrementor));
            if (f != flag) {
                return false;
            }
        }
        return true;
    }
}
