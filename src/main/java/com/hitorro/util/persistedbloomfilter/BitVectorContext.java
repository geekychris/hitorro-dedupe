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

import com.hitorro.util.core.string.Fmt;
import com.hitorro.util.io.FileUtil;
import com.hitorro.util.io.largedata.compressedstreams.BitVector;
import com.hitorro.util.io.largedata.compressedstreams.FSInputStream;
import com.hitorro.util.io.largedata.compressedstreams.FSOutputStream;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

/**
 *
 */
public class BitVectorContext {
    long lastMarked = -1;
    private BitVector bv = null;
    private File file;
    private File dosFile;
    private DataOutputStream dos = null;
    private int segment;
    private int maxValue;
    private int howDirty = 0;
    private int maxDirty;


    private boolean gotBV = false;

    public BitVectorContext(File directory, int segment, int maxValue, long lastMarked, int maxDirty) {
        this.maxValue = maxValue;
        this.lastMarked = lastMarked;
        file = new File(directory, Fmt.S("%s.bv", segment));
        dosFile = new File(directory, Fmt.S("%s.log", segment));
        this.segment = segment;
        this.maxDirty = maxDirty;
    }

    public String toString() {
        return Fmt.S("seg: %s, lastmarked:%s dirty:%s", segment, lastMarked, isDirty());
    }

    public long getLastMarked() {
        return lastMarked;
    }

    public void setLastMarked(long mark) {
        lastMarked = mark;
    }


    private void getBV() throws IOException {
        if (gotBV) {
            return;
        }
        if (file.exists()) {
            FSInputStream is = new FSInputStream(file);
            bv = new BitVector(is);
        } else {
            bv = new BitVector(maxValue);
        }
        if (dosFile.exists()) {
            // take log entries and update since last time we didnt commit the bv to disk with changes.
            writeInternal();
        }
        gotBV = true;
    }

    public int getSegment() {
        return segment;
    }

    public boolean isDirty() {
        return this.howDirty > this.maxDirty;
    }

    public void write() throws IOException {
        if (isDirty()) {
            writeInternal();
        }
    }

    private void writeInternal() throws IOException {
        boolean closeDos = false;
        if (dosFile.exists()) {
            DataInputStream dis = FileUtil.getDataInputStreamForFile(dosFile);
            long size = dosFile.length() / 4;
            for (long i = 0; i < size; i++) {
                int id = dis.readInt();
                bv.set(id);
            }
            closeDos = true;
        }
        File tmp = FileUtil.getTempFileWithFromPeerFileWithExtension(file, "tmp");
        FSOutputStream fsos = new FSOutputStream(tmp);
        bv.write(fsos);
        tmp.renameTo(file);
        if (closeDos) {
            dosFile.delete();
            if (dos != null) {
                dos.close();
                dos = null;
            }
        }

        this.howDirty = 0;
    }

    public boolean hasValue(int id) throws IOException {
        getBV();
        return bv.get(id);
    }

    public void setValue(int id) throws IOException {
        getBV();
        writeToLog(id);
        bv.set(id);

        this.howDirty++;
    }

    private void writeToLog(int id) throws IOException {
        if (dos == null) {
            dos = FileUtil.getDataOutputStreamForFile(dosFile);
        }
        dos.writeInt(id);
        dos.flush();
    }
}
