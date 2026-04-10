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
import com.hitorro.util.io.FileUtil;

import java.io.File;
import java.io.IOException;


public class ReadBucket implements BucketHash {
    private TLongHashSet hashSet;
    private File file;
    private int maxSize;

    public ReadBucket(int maxSize, File file) throws IOException {
        hashSet = new TLongHashSet(maxSize);
        this.file = file;
        this.maxSize = maxSize;
        if (file.exists()) {
            hashSet = TroveMapUtil.getTLongHashSetFromFile(file, maxSize);
            if (hashSet == null) {
                hashSet = new TLongHashSet(maxSize);
            }
        }
    }

    public void moveAndClose(File targetDir) {
        FileUtil.ensureDirectoryExists(targetDir);
        File targetFile = new File(targetDir, file.getName());
        file.renameTo(targetFile);
        hashSet = null;
    }

    public boolean exists(long hash) {
        return hashSet.contains(hash);
    }

    public void set(long hash) {
        hashSet.add(hash);
    }

    public boolean isOverCapacity() {
        return size() > maxSize;
    }

    public int size() {
        return hashSet.size();
    }
}

