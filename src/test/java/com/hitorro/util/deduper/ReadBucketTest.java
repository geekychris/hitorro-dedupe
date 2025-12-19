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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ReadBucket class
 */
class ReadBucketTest {
    
    @TempDir
    Path tempDir;
    
    private File testFile;
    private ReadBucket readBucket;
    private static final int MAX_SIZE = 1000;
    
    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test-bucket.map").toFile();
        readBucket = new ReadBucket(MAX_SIZE, testFile);
    }
    
    @AfterEach
    void tearDown() {
        if (testFile != null && testFile.exists()) {
            testFile.delete();
        }
    }
    
    @Test
    void testConstructorCreatesNewBucket() throws IOException {
        assertNotNull(readBucket);
        assertEquals(0, readBucket.size());
    }
    
    @Test
    void testSetAndExists() {
        long testHash = 12345L;
        
        assertFalse(readBucket.exists(testHash), "Hash should not exist initially");
        
        readBucket.set(testHash);
        
        assertTrue(readBucket.exists(testHash), "Hash should exist after being set");
        assertEquals(1, readBucket.size());
    }
    
    @Test
    void testMultipleHashes() {
        long[] testHashes = {100L, 200L, 300L, 400L, 500L};
        
        for (long hash : testHashes) {
            assertFalse(readBucket.exists(hash));
            readBucket.set(hash);
            assertTrue(readBucket.exists(hash));
        }
        
        assertEquals(testHashes.length, readBucket.size());
    }
    
    @Test
    void testDuplicateHashDoesNotIncreaseSize() {
        long testHash = 12345L;
        
        readBucket.set(testHash);
        assertEquals(1, readBucket.size());
        
        readBucket.set(testHash);
        assertEquals(1, readBucket.size(), "Size should not increase when adding duplicate");
        
        assertTrue(readBucket.exists(testHash));
    }
    
    @Test
    void testIsOverCapacity() {
        assertFalse(readBucket.isOverCapacity(), "New bucket should not be over capacity");
        
        // Add more than MAX_SIZE elements
        for (int i = 0; i <= MAX_SIZE; i++) {
            readBucket.set(i);
        }
        
        assertTrue(readBucket.isOverCapacity(), "Bucket should be over capacity after adding > MAX_SIZE elements");
    }
    
    @Test
    void testSize() {
        assertEquals(0, readBucket.size());
        
        readBucket.set(1L);
        assertEquals(1, readBucket.size());
        
        readBucket.set(2L);
        assertEquals(2, readBucket.size());
        
        readBucket.set(1L); // Duplicate
        assertEquals(2, readBucket.size());
    }
    
    @Test
    void testMoveAndClose() throws IOException {
        long testHash = 99999L;
        readBucket.set(testHash);
        
        // Need to flush the data to create the file first
        WriteBucket writeBucket = new WriteBucket(MAX_SIZE, testFile, readBucket);
        writeBucket.set(testHash);
        writeBucket.flush();
        
        assertTrue(testFile.exists(), "Test file should exist before move");
        
        File targetDir = tempDir.resolve("expired").toFile();
        readBucket.moveAndClose(targetDir);
        
        assertTrue(targetDir.exists(), "Target directory should be created");
        File movedFile = new File(targetDir, testFile.getName());
        assertTrue(movedFile.exists(), "File should be moved to target directory");
        assertFalse(testFile.exists(), "Original file should not exist after move");
    }
    
    @Test
    void testExistsWithNegativeHash() {
        long negativeHash = -12345L;
        
        assertFalse(readBucket.exists(negativeHash));
        readBucket.set(negativeHash);
        assertTrue(readBucket.exists(negativeHash));
    }
    
    @Test
    void testExistsWithZeroHash() {
        long zeroHash = 0L;
        
        assertFalse(readBucket.exists(zeroHash));
        readBucket.set(zeroHash);
        assertTrue(readBucket.exists(zeroHash));
    }
    
    @Test
    void testExistsWithMaxLongValue() {
        long maxHash = Long.MAX_VALUE;
        
        assertFalse(readBucket.exists(maxHash));
        readBucket.set(maxHash);
        assertTrue(readBucket.exists(maxHash));
    }
}
