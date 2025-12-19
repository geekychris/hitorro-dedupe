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
 * Unit tests for WriteBucket class
 */
class WriteBucketTest {
    
    @TempDir
    Path tempDir;
    
    private File testFile;
    private ReadBucket readBucket;
    private WriteBucket writeBucket;
    private static final int MAX_SIZE = 1000;
    
    @BeforeEach
    void setUp() throws IOException {
        testFile = tempDir.resolve("test-bucket.map").toFile();
        readBucket = new ReadBucket(MAX_SIZE, testFile);
        writeBucket = new WriteBucket(MAX_SIZE, testFile, readBucket);
    }
    
    @AfterEach
    void tearDown() {
        if (testFile != null && testFile.exists()) {
            testFile.delete();
        }
    }
    
    @Test
    void testConstructorCreatesWriteBucket() {
        assertNotNull(writeBucket);
    }
    
    @Test
    void testSetAndExists() {
        long testHash = 12345L;
        
        assertFalse(writeBucket.exists(testHash), "Hash should not exist initially");
        
        writeBucket.set(testHash);
        
        assertTrue(writeBucket.exists(testHash), "Hash should exist in write bucket after being set");
        assertTrue(readBucket.exists(testHash), "Hash should also exist in read bucket after being set");
    }
    
    @Test
    void testMultipleHashes() {
        long[] testHashes = {100L, 200L, 300L, 400L, 500L};
        
        for (long hash : testHashes) {
            assertFalse(writeBucket.exists(hash));
            writeBucket.set(hash);
            assertTrue(writeBucket.exists(hash));
            assertTrue(readBucket.exists(hash), "ReadBucket should also contain the hash");
        }
    }
    
    @Test
    void testFlushClearsWriteBucket() throws IOException {
        long testHash1 = 111L;
        long testHash2 = 222L;
        
        writeBucket.set(testHash1);
        writeBucket.set(testHash2);
        
        assertTrue(writeBucket.exists(testHash1));
        assertTrue(writeBucket.exists(testHash2));
        
        writeBucket.flush();
        
        // After flush, write bucket should be cleared
        assertFalse(writeBucket.exists(testHash1), "Write bucket should not contain hash after flush");
        assertFalse(writeBucket.exists(testHash2), "Write bucket should not contain hash after flush");
        
        // But read bucket should still have them
        assertTrue(readBucket.exists(testHash1), "Read bucket should still contain hash after flush");
        assertTrue(readBucket.exists(testHash2), "Read bucket should still contain hash after flush");
    }
    
    @Test
    void testFlushCreatesFile() throws IOException {
        writeBucket.set(123L);
        writeBucket.set(456L);
        writeBucket.set(789L);
        
        writeBucket.flush();
        
        assertTrue(testFile.exists(), "File should exist after flush");
        assertTrue(testFile.length() > 0, "File should have content after flush");
    }
    
    @Test
    void testDuplicateHashHandling() {
        long testHash = 12345L;
        
        writeBucket.set(testHash);
        assertTrue(writeBucket.exists(testHash));
        assertEquals(1, readBucket.size());
        
        // Set the same hash again
        writeBucket.set(testHash);
        assertTrue(writeBucket.exists(testHash));
        assertEquals(1, readBucket.size(), "Duplicate should not increase size");
    }
    
    @Test
    void testFlushMultipleTimes() throws IOException {
        writeBucket.set(100L);
        writeBucket.flush();
        
        writeBucket.set(200L);
        writeBucket.flush();
        
        writeBucket.set(300L);
        writeBucket.flush();
        
        assertTrue(readBucket.exists(100L));
        assertTrue(readBucket.exists(200L));
        assertTrue(readBucket.exists(300L));
        assertEquals(3, readBucket.size());
    }
    
    @Test
    void testSynchronizationBetweenWriteAndReadBucket() {
        long hash1 = 1000L;
        long hash2 = 2000L;
        
        // Write bucket gets the hash
        writeBucket.set(hash1);
        
        // Both should have it
        assertTrue(writeBucket.exists(hash1));
        assertTrue(readBucket.exists(hash1));
        
        // Read bucket gets a different hash
        readBucket.set(hash2);
        
        // Read bucket should have both
        assertTrue(readBucket.exists(hash1));
        assertTrue(readBucket.exists(hash2));
        
        // Write bucket should only have the one it set
        assertTrue(writeBucket.exists(hash1));
        assertFalse(writeBucket.exists(hash2));
    }
    
    @Test
    void testExistsWithEdgeCaseValues() {
        writeBucket.set(0L);
        writeBucket.set(-1L);
        writeBucket.set(Long.MAX_VALUE);
        writeBucket.set(Long.MIN_VALUE);
        
        assertTrue(writeBucket.exists(0L));
        assertTrue(writeBucket.exists(-1L));
        assertTrue(writeBucket.exists(Long.MAX_VALUE));
        assertTrue(writeBucket.exists(Long.MIN_VALUE));
    }
    
    @Test
    void testEmptyFlush() throws IOException {
        // Flushing an empty write bucket should not cause issues
        assertDoesNotThrow(() -> writeBucket.flush());
        
        // File should still be created
        assertTrue(testFile.exists());
    }
}
