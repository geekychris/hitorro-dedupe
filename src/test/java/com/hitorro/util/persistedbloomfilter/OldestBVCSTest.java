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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OldestBVCS comparator
 */
class OldestBVCSTest {
    
    @TempDir
    Path tempDir;
    
    private BitVectorContext bvc1;
    private BitVectorContext bvc2;
    private BitVectorContext bvc3;
    
    @BeforeEach
    void setUp() {
        long baseTime = System.currentTimeMillis();
        
        // Create contexts with different lastMarked times
        bvc1 = new BitVectorContext(tempDir.toFile(), 1, 1000, baseTime + 1000, 10);
        bvc2 = new BitVectorContext(tempDir.toFile(), 2, 1000, baseTime + 2000, 10);
        bvc3 = new BitVectorContext(tempDir.toFile(), 3, 1000, baseTime + 3000, 10);
    }
    
    @Test
    void testComparatorExists() {
        assertNotNull(OldestBVCS.Comp);
    }
    
    @Test
    void testCompareFirstOlderThanSecond() {
        int result = OldestBVCS.Comp.compare(bvc1, bvc2);
        assertTrue(result < 0, "First context should be 'less than' (older) than second");
    }
    
    @Test
    void testCompareFirstNewerThanSecond() {
        int result = OldestBVCS.Comp.compare(bvc3, bvc1);
        assertTrue(result > 0, "First context should be 'greater than' (newer) than second");
    }
    
    @Test
    void testCompareEqual() {
        long sameTime = System.currentTimeMillis();
        BitVectorContext bvcA = new BitVectorContext(tempDir.toFile(), 10, 1000, sameTime, 10);
        BitVectorContext bvcB = new BitVectorContext(tempDir.toFile(), 11, 1000, sameTime, 10);
        
        int result = OldestBVCS.Comp.compare(bvcA, bvcB);
        assertEquals(0, result, "Contexts with same lastMarked time should be equal");
    }
    
    @Test
    void testSortingList() {
        List<BitVectorContext> contexts = new ArrayList<>();
        
        // Add in random order
        contexts.add(bvc3); // newest
        contexts.add(bvc1); // oldest
        contexts.add(bvc2); // middle
        
        // Sort using the comparator
        Collections.sort(contexts, OldestBVCS.Comp);
        
        // Verify sorted order (oldest first)
        assertEquals(bvc1, contexts.get(0), "Oldest should be first");
        assertEquals(bvc2, contexts.get(1), "Middle should be second");
        assertEquals(bvc3, contexts.get(2), "Newest should be last");
    }
    
    @Test
    void testSortingWithManyElements() {
        List<BitVectorContext> contexts = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        
        // Create 10 contexts with increasing times
        for (int i = 0; i < 10; i++) {
            contexts.add(new BitVectorContext(
                tempDir.toFile(), 
                i, 
                1000, 
                baseTime + (i * 1000), 
                10
            ));
        }
        
        // Shuffle the list
        Collections.shuffle(contexts);
        
        // Sort using the comparator
        Collections.sort(contexts, OldestBVCS.Comp);
        
        // Verify order
        for (int i = 0; i < contexts.size() - 1; i++) {
            assertTrue(
                contexts.get(i).getLastMarked() <= contexts.get(i + 1).getLastMarked(),
                "Contexts should be sorted by lastMarked time"
            );
        }
    }
    
    @Test
    void testCompareWithNegativeTimeDifference() {
        long time1 = Long.MAX_VALUE - 1000;
        long time2 = Long.MAX_VALUE;
        
        BitVectorContext bvcA = new BitVectorContext(tempDir.toFile(), 1, 1000, time1, 10);
        BitVectorContext bvcB = new BitVectorContext(tempDir.toFile(), 2, 1000, time2, 10);
        
        int result = OldestBVCS.Comp.compare(bvcA, bvcB);
        assertTrue(result < 0, "Context with earlier time should be less than");
    }
    
    @Test
    void testCompareWithZeroTime() {
        BitVectorContext bvcZero = new BitVectorContext(tempDir.toFile(), 1, 1000, 0, 10);
        BitVectorContext bvcPositive = new BitVectorContext(tempDir.toFile(), 2, 1000, 1000, 10);
        
        int result = OldestBVCS.Comp.compare(bvcZero, bvcPositive);
        assertTrue(result < 0, "Context with time 0 should be less than positive time");
    }
    
    @Test
    void testCompareConsistency() {
        // Compare same pair multiple times should give consistent results
        int result1 = OldestBVCS.Comp.compare(bvc1, bvc2);
        int result2 = OldestBVCS.Comp.compare(bvc1, bvc2);
        int result3 = OldestBVCS.Comp.compare(bvc1, bvc2);
        
        assertEquals(result1, result2);
        assertEquals(result2, result3);
    }
    
    @Test
    void testCompareTransitivity() {
        // If A < B and B < C, then A < C
        assertTrue(OldestBVCS.Comp.compare(bvc1, bvc2) < 0, "bvc1 < bvc2");
        assertTrue(OldestBVCS.Comp.compare(bvc2, bvc3) < 0, "bvc2 < bvc3");
        assertTrue(OldestBVCS.Comp.compare(bvc1, bvc3) < 0, "bvc1 < bvc3 (transitivity)");
    }
    
    @Test
    void testCompareSymmetry() {
        // If compare(a, b) < 0, then compare(b, a) > 0
        int result1 = OldestBVCS.Comp.compare(bvc1, bvc2);
        int result2 = OldestBVCS.Comp.compare(bvc2, bvc1);
        
        assertTrue(result1 < 0);
        assertTrue(result2 > 0);
        assertEquals(Math.signum(-result1), Math.signum(result2), 0.01);
    }
}
