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

import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.hash.TIntObjectHashMap;
import com.hitorro.util.core.ArrayUtil;
import com.hitorro.util.core.Constants;
import com.hitorro.util.core.Log;
import com.hitorro.util.core.longword.NamedBitsOfLong;
import com.hitorro.util.core.thread.ThreadTimer;
import com.hitorro.util.core.thread.TimerCallback;
import com.hitorro.util.io.FileUtil;
import com.hitorro.util.io.filefilters.FilenameExtensionFilter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manage a large bloom filter with backing store.  Represents the bloom filter as a set of bit vectors
 * as part of the bloom filter address space does not get used it will be aged out of memory.
 * All sets to the bloom filter are persisted to backing store as a log of ID's.  This allows
 * the filter to be re-loaded from disk.
 * The address space is divided into bitvector address regions.  These units of work are backed by a
 * bitvector (BV) file and a log file.  The log file is appended to and eventually the bitvector is
 * also persisted to reflect the log file.  The intent is that the BV is written to on startup or
 * on periodic write.  In this case there is a built in threshold that determines how many log entries
 * must be written before it flushes.
 */
public class PersistedBloomFilter implements TimerCallback {
    private File root;
    private TIntObjectHashMap<BitVectorContext> vectorMap = new TIntObjectHashMap();
    private int writeTimerSecs;
    private ThreadTimer threadTimer;

    private int maximumOpenVectorContexts = 5;

    private NamedBitsOfLong nbolVal = null;
    private NamedBitsOfLong nbolSeg = null;
    private long lastMarkedTimeEpoch = System.currentTimeMillis();
    private int valueWidth;
    private int segmentWidth;
    private int dirtyThreshold;


    /**
     * @param root
     * @param flushTimerSecs
     * @param maxOpenVectors
     * @param valueWidth     how many bits are used in the value part.  This determines the size of the bv.  For
     *                       instance 24 gives 16M address space of bits which is 2MB of ram.  Cost to read and
     *                       flushToDisk seems to be nominal pause.
     * @param dirtyThreshold the number of log entries that constitute "dirty enough"
     * @throws IOException
     * @segmentWidth how many bits are used in a segment
     */
    public PersistedBloomFilter(File root, int flushTimerSecs, int maxOpenVectors, int valueWidth, int segmentWidth, int dirtyThreshold) throws IOException {
        this.root = root;
        this.maximumOpenVectorContexts = maxOpenVectors;
        this.writeTimerSecs = flushTimerSecs;
        initBitMaps(valueWidth, segmentWidth);
        FileUtil.ensureDirectoryExists(root);
        discoverExistingVectors(root);
        if (flushTimerSecs != -1) {
            threadTimer = new ThreadTimer(this, flushTimerSecs * Constants.MillisInSecond, true);
        }
    }

    private void discoverExistingVectors(final File root) throws IOException {
        File files[] = root.listFiles(new FilenameExtensionFilter("bv", true));
        if (!ArrayUtil.nullOrEmpty(files)) {
            for (File file : files) {
                String name = FileUtil.getFileNameSansExtension(file);
                int segment = Integer.parseInt(name);
                getBVC(segment);
            }
        }
    }

    private void initBitMaps(final int valueWidth, final int segmentWidth) {
        this.valueWidth = valueWidth;
        this.segmentWidth = segmentWidth;
        nbolVal = new NamedBitsOfLong(null, "val", 0, valueWidth);
        nbolSeg = new NamedBitsOfLong(null, "seg", valueWidth, segmentWidth);
    }

    private BitVectorContext getBVC(int segment) throws IOException {
        BitVectorContext bvc = vectorMap.get(segment);
        if (bvc == null) {
            lastMarkedTimeEpoch = System.currentTimeMillis();
            closeOldSegments();
            bvc = new BitVectorContext(root, segment, 1 << valueWidth, lastMarkedTimeEpoch, this.dirtyThreshold);
            vectorMap.put(segment, bvc);
        }
        return bvc;
    }

    /**
     * flushToDisk out all rows if dirty
     *
     * @return
     * @throws IOException
     */
    public synchronized boolean flushToDisk() throws IOException {
        for (TIntObjectIterator<BitVectorContext> it = vectorMap.iterator(); it.hasNext(); ) {
            it.advance();
            it.value().write();
        }
        return true;
    }

    public synchronized boolean idExists(long id) throws IOException {
        int segment = highInt(id);
        int val = lowInt(id);
        BitVectorContext bvc = getBVC(segment);
        bvc.setLastMarked(lastMarkedTimeEpoch);
        return bvc.hasValue(val);
    }

    public synchronized boolean addIdToSet(long id) throws IOException {
        int segment = highInt(id);
        int val = lowInt(id);
        BitVectorContext bvc = getBVC(segment);
        bvc.setLastMarked(lastMarkedTimeEpoch);
        bvc.setValue(val);
        return true;
    }

    /*
        callback from the timer.  If implementor returns true and the callback thread is
        in loop mode, the timer will wait again and re-invoke.
    */
    public boolean callback() {
        try {
            flushToDisk();
        } catch (IOException e) {
            Log.idmanager.error("Unable to flushToDisk vectors %s %e", e, e);
            return false;
        }
        return true;
    }

    private int lowInt(long id) {
        return (int) nbolVal.get(id);
    }

    private int highInt(long id) {
        return (int) nbolSeg.get(id);
    }

    /**
     * Called whenever a new BV is opened we consider if we should remove some BV's from ram.
     */
    private synchronized void closeOldSegments() throws IOException {
        int purge = vectorMap.size() - maximumOpenVectorContexts;
        List<BitVectorContext> bvcs = new ArrayList();
        if (purge > 0) {
            // we have gone over max, lets reduce by removing the oldest
            for (TIntObjectIterator<BitVectorContext> it = vectorMap.iterator(); it.hasNext(); ) {
                it.advance();
                bvcs.add(it.value());
            }
            Collections.sort(bvcs, OldestBVCS.Comp);
            for (int i = 0; i < purge; i++) {
                BitVectorContext bvc = bvcs.get(i);
                bvc.write();
                vectorMap.remove(bvc.getSegment());
            }
        }
    }
}

