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
 * Unit tests for BitVectorContext class
 */
class BitVectorContextTest {
    
    @TempDir
    Path tempDir;
    
    private BitVectorContext bvc;
    private static final int SEGMENT = 1;
    private static final int MAX_VALUE = 1000;
    private static final int MAX_DIRTY = 10;
    
    @BeforeEach
    void setUp() {
        long currentTime = System.currentTimeMillis();
        bvc = new BitVectorContext(tempDir.toFile(), SEGMENT, MAX_VALUE, currentTime, MAX_DIRTY);
    }
    
    @AfterEach
    void tearDown() {
        // Clean up any files created during tests
        File[] files = tempDir.toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
    }
    
    @Test
    void testConstructor() {
        assertNotNull(bvc);
        assertEquals(SEGMENT, bvc.getSegment());
        assertTrue(bvc.getLastMarked() > 0);
    }
    
    @Test
    void testSetAndGetLastMarked() {
        long newTime = System.currentTimeMillis() + 1000;
        bvc.setLastMarked(newTime);
        assertEquals(newTime, bvc.getLastMarked());
    }
    
    @Test
    void testSetAndHasValue() throws IOException {
        int testId = 42;
        
        assertFalse(bvc.hasValue(testId), "Value should not exist initially");
        
        bvc.setValue(testId);
        
        assertTrue(bvc.hasValue(testId), "Value should exist after being set");
    }
    
    @Test
    void testMultipleValues() throws IOException {
        int[] testIds = {1, 10, 100, 500, 999};
        
        for (int id : testIds) {
            assertFalse(bvc.hasValue(id));
            bvc.setValue(id);
            assertTrue(bvc.hasValue(id));
        }
        
        // Verify all values are still present
        for (int id : testIds) {
            assertTrue(bvc.hasValue(id));
        }
    }
    
    @Test
    void testIsDirty() throws IOException {
        assertFalse(bvc.isDirty(), "Should not be dirty initially");
        
        // Add values up to the dirty threshold
        for (int i = 0; i < MAX_DIRTY; i++) {
            bvc.setValue(i);
        }
        
        assertFalse(bvc.isDirty(), "Should not be dirty at threshold");
        
        // Add one more value to exceed threshold
        bvc.setValue(MAX_DIRTY);
        
        assertTrue(bvc.isDirty(), "Should be dirty after exceeding threshold");
    }
    
    @Test
    void testWriteWhenNotDirty() throws IOException {
        bvc.setValue(1);
        // Should not throw exception even if not dirty
        assertDoesNotThrow(() -> bvc.write());
    }
    
    @Test
    void testWriteWhenDirty() throws IOException {
        // Make it dirty
        for (int i = 0; i <= MAX_DIRTY; i++) {
            bvc.setValue(i);
        }
        
        assertTrue(bvc.isDirty());
        
        bvc.write();
        
        assertFalse(bvc.isDirty(), "Should not be dirty after write");
        
        // Verify all values are still present
        for (int i = 0; i <= MAX_DIRTY; i++) {
            assertTrue(bvc.hasValue(i));
        }
    }
    
    @Test
    void testPersistence() throws IOException {
        int testId = 123;
        bvc.setValue(testId);
        
        // Make it dirty and write
        for (int i = 0; i <= MAX_DIRTY; i++) {
            bvc.setValue(i);
        }
        bvc.write();
        
        // Create a new context with the same segment
        BitVectorContext newBvc = new BitVectorContext(
            tempDir.toFile(), 
            SEGMENT, 
            MAX_VALUE, 
            System.currentTimeMillis(), 
            MAX_DIRTY
        );
        
        // The value should still be present
        assertTrue(newBvc.hasValue(testId), "Value should persist after write and reload");
        
        // All other values should also be present
        for (int i = 0; i <= MAX_DIRTY; i++) {
            assertTrue(newBvc.hasValue(i));
        }
    }
    
    @Test
    void testToString() {
        String str = bvc.toString();
        assertNotNull(str);
        assertTrue(str.contains("seg"));
        assertTrue(str.contains(String.valueOf(SEGMENT)));
    }
    
    @Test
    void testDuplicateSetDoesNotIncreaseDirtiness() throws IOException {
        int testId = 5;
        
        // Set the value once
        bvc.setValue(testId);
        
        // Set it again - this should still increase dirtiness because 
        // the log file is appended to
        bvc.setValue(testId);
        
        assertTrue(bvc.hasValue(testId));
    }
    
    @Test
    void testZeroId() throws IOException {
        assertFalse(bvc.hasValue(0));
        bvc.setValue(0);
        assertTrue(bvc.hasValue(0));
    }
    
    @Test
    void testMaxValueMinusOne() throws IOException {
        int maxId = MAX_VALUE - 1;
        assertFalse(bvc.hasValue(maxId));
        bvc.setValue(maxId);
        assertTrue(bvc.hasValue(maxId));
    }
    
    @Test
    void testGetSegment() {
        assertEquals(SEGMENT, bvc.getSegment());
    }
    
    @Test
    void testMultipleWrites() throws IOException {
        // Add some values
        for (int i = 0; i < 5; i++) {
            bvc.setValue(i);
        }
        
        // Make dirty and write
        for (int i = 5; i <= MAX_DIRTY + 5; i++) {
            bvc.setValue(i);
        }
        bvc.write();
        
        // Add more values
        for (int i = MAX_DIRTY + 6; i < MAX_DIRTY + 10; i++) {
            bvc.setValue(i);
        }
        
        // Make dirty and write again
        for (int i = MAX_DIRTY + 10; i <= 2 * MAX_DIRTY + 10; i++) {
            bvc.setValue(i);
        }
        bvc.write();
        
        // Verify all values are present
        for (int i = 0; i <= 2 * MAX_DIRTY + 10; i++) {
            assertTrue(bvc.hasValue(i), "Value " + i + " should be present");
        }
    }
}
