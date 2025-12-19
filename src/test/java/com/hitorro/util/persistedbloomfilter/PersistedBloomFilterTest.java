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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PersistedBloomFilter class
 */
class PersistedBloomFilterTest {
    
    @TempDir
    Path tempDir;
    
    private PersistedBloomFilter bloomFilter;
    private File rootDir;
    
    // Common test parameters
    private static final int FLUSH_TIMER_SECS = -1; // Disable auto-flush for tests
    private static final int MAX_OPEN_VECTORS = 5;
    private static final int VALUE_WIDTH = 24; // 16M address space (2MB RAM)
    private static final int SEGMENT_WIDTH = 10;
    private static final int DIRTY_THRESHOLD = 100;
    
    @BeforeEach
    void setUp() throws IOException {
        rootDir = tempDir.resolve("bloom-filter").toFile();
        rootDir.mkdirs();
        bloomFilter = new PersistedBloomFilter(
            rootDir, 
            FLUSH_TIMER_SECS, 
            MAX_OPEN_VECTORS, 
            VALUE_WIDTH, 
            SEGMENT_WIDTH, 
            DIRTY_THRESHOLD
        );
    }
    
    @AfterEach
    void tearDown() {
        // Clean up
        if (rootDir != null && rootDir.exists()) {
            deleteDirectory(rootDir);
        }
    }
    
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
    
    @Test
    void testConstructor() {
        assertNotNull(bloomFilter);
        assertTrue(rootDir.exists(), "Root directory should be created");
    }
    
    @Test
    void testAddAndCheck() throws IOException {
        long testId = 12345L;
        
        assertFalse(bloomFilter.idExists(testId), "ID should not exist initially");
        
        bloomFilter.addIdToSet(testId);
        
        assertTrue(bloomFilter.idExists(testId), "ID should exist after being added");
    }
    
    @Test
    void testMultipleIds() throws IOException {
        long[] testIds = {1L, 100L, 1000L, 10000L, 100000L};
        
        // Add all IDs
        for (long id : testIds) {
            assertFalse(bloomFilter.idExists(id));
            bloomFilter.addIdToSet(id);
        }
        
        // Verify all IDs exist
        for (long id : testIds) {
            assertTrue(bloomFilter.idExists(id), "ID " + id + " should exist");
        }
    }
    
    @Test
    void testDuplicateAddition() throws IOException {
        long testId = 999L;
        
        bloomFilter.addIdToSet(testId);
        assertTrue(bloomFilter.idExists(testId));
        
        // Add again
        bloomFilter.addIdToSet(testId);
        assertTrue(bloomFilter.idExists(testId));
    }
    
    @Test
    void testFlushToDisk() throws IOException {
        long testId1 = 111L;
        long testId2 = 222L;
        
        bloomFilter.addIdToSet(testId1);
        bloomFilter.addIdToSet(testId2);
        
        boolean flushed = bloomFilter.flushToDisk();
        assertTrue(flushed, "Flush should succeed");
        
        // IDs should still exist after flush
        assertTrue(bloomFilter.idExists(testId1));
        assertTrue(bloomFilter.idExists(testId2));
    }
    
    @Test
    void testPersistenceAcrossInstances() throws IOException {
        long testId = 54321L;
        
        // Add ID to first instance
        bloomFilter.addIdToSet(testId);
        bloomFilter.flushToDisk();
        
        // Create new instance with same root directory
        PersistedBloomFilter newFilter = new PersistedBloomFilter(
            rootDir,
            FLUSH_TIMER_SECS,
            MAX_OPEN_VECTORS,
            VALUE_WIDTH,
            SEGMENT_WIDTH,
            DIRTY_THRESHOLD
        );
        
        // ID should exist in new instance
        assertTrue(newFilter.idExists(testId), "ID should persist across instances");
    }
    
    @Test
    void testMultipleSegments() throws IOException {
        // Create IDs that will map to different segments
        // Segment is determined by the high bits
        long segment0Id = 0L;
        long segment1Id = (1L << VALUE_WIDTH);
        long segment2Id = (2L << VALUE_WIDTH);
        
        bloomFilter.addIdToSet(segment0Id);
        bloomFilter.addIdToSet(segment1Id);
        bloomFilter.addIdToSet(segment2Id);
        
        assertTrue(bloomFilter.idExists(segment0Id));
        assertTrue(bloomFilter.idExists(segment1Id));
        assertTrue(bloomFilter.idExists(segment2Id));
    }
    
    @Test
    void testLargeNumberOfIds() throws IOException {
        int numIds = 10000;
        
        // Add many IDs
        for (int i = 0; i < numIds; i++) {
            bloomFilter.addIdToSet(i);
        }
        
        // Verify all exist
        for (int i = 0; i < numIds; i++) {
            assertTrue(bloomFilter.idExists(i), "ID " + i + " should exist");
        }
    }
    
    @Test
    void testFlushAfterManyOperations() throws IOException {
        // Add many IDs to trigger dirty threshold
        for (int i = 0; i < DIRTY_THRESHOLD * 2; i++) {
            bloomFilter.addIdToSet(i);
        }
        
        bloomFilter.flushToDisk();
        
        // Verify all IDs still exist
        for (int i = 0; i < DIRTY_THRESHOLD * 2; i++) {
            assertTrue(bloomFilter.idExists(i));
        }
    }
    
    @Test
    void testZeroId() throws IOException {
        assertFalse(bloomFilter.idExists(0L));
        bloomFilter.addIdToSet(0L);
        assertTrue(bloomFilter.idExists(0L));
    }
    
    @Test
    void testNegativeId() throws IOException {
        long negativeId = -12345L;
        assertFalse(bloomFilter.idExists(negativeId));
        bloomFilter.addIdToSet(negativeId);
        assertTrue(bloomFilter.idExists(negativeId));
    }
    
    @Test
    void testMaxLongValue() throws IOException {
        long maxId = Long.MAX_VALUE;
        assertFalse(bloomFilter.idExists(maxId));
        bloomFilter.addIdToSet(maxId);
        assertTrue(bloomFilter.idExists(maxId));
    }
    
    @Test
    void testCallback() {
        // Test the timer callback method
        boolean result = bloomFilter.callback();
        assertTrue(result, "Callback should return true");
    }
    
    @Test
    void testMultipleFlushes() throws IOException {
        bloomFilter.addIdToSet(1L);
        bloomFilter.flushToDisk();
        
        bloomFilter.addIdToSet(2L);
        bloomFilter.flushToDisk();
        
        bloomFilter.addIdToSet(3L);
        bloomFilter.flushToDisk();
        
        assertTrue(bloomFilter.idExists(1L));
        assertTrue(bloomFilter.idExists(2L));
        assertTrue(bloomFilter.idExists(3L));
    }
    
    @Test
    void testEmptyFilterFlush() throws IOException {
        // Flushing an empty filter should not cause issues
        boolean result = bloomFilter.flushToDisk();
        assertTrue(result);
    }
    
    @Test
    void testIdExistsDoesNotModifyFilter() throws IOException {
        long testId = 777L;
        
        assertFalse(bloomFilter.idExists(testId));
        assertFalse(bloomFilter.idExists(testId)); // Check again
        
        // ID should still not exist after multiple checks
        assertFalse(bloomFilter.idExists(testId));
    }
    
    @Test
    void testMixedOperations() throws IOException {
        // Mix of add, check, flush operations
        bloomFilter.addIdToSet(1L);
        assertTrue(bloomFilter.idExists(1L));
        
        bloomFilter.addIdToSet(2L);
        bloomFilter.flushToDisk();
        
        assertTrue(bloomFilter.idExists(1L));
        assertTrue(bloomFilter.idExists(2L));
        
        bloomFilter.addIdToSet(3L);
        assertFalse(bloomFilter.idExists(999L));
        
        bloomFilter.flushToDisk();
        
        assertTrue(bloomFilter.idExists(1L));
        assertTrue(bloomFilter.idExists(2L));
        assertTrue(bloomFilter.idExists(3L));
        assertFalse(bloomFilter.idExists(999L));
    }
    
    @Test
    void testSparseIdDistribution() throws IOException {
        // Test with IDs spread across the address space
        long[] sparseIds = {
            1L,
            (1L << 20),
            (1L << 30),
            (1L << 40),
            Long.MAX_VALUE / 2
        };
        
        for (long id : sparseIds) {
            bloomFilter.addIdToSet(id);
        }
        
        for (long id : sparseIds) {
            assertTrue(bloomFilter.idExists(id), "Sparse ID " + id + " should exist");
        }
    }
}
