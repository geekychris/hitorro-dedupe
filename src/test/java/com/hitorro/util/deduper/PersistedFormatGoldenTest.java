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

import com.hitorro.util.persistedbloomfilter.PersistedBloomFilter; import com.hitorro.util.deduper.*;
import java.io.*; import java.nio.file.*; import java.security.MessageDigest; import java.util.*;
/**
 * hitorro-dedupe has no Jackson code, but 4.0.0 moves it from util 3.0.2 to util/core 4.0.0. This
 * pins the files it persists (PersistedBloomFilter .bv segments, ReadBucket/WriteBucket maps,
 * compared by size and SHA-256) and reading them back to what 3.0.2 produced.
 * {@code dedupe-3.0.2.txt} is the output of the same code run on the 3.0.2 sources
 * (generator: ~/hitorro-j3/tools/Jackson2DedupeGolden.java.txt in the migration workspace).
 */
class PersistedFormatGoldenTest {
  static final Map<String, String> out = new LinkedHashMap<>();
  static void p(String k, Object v){ out.put(k, String.valueOf(v).replace("\\","\\\\").replace("\n","\\n").replace("\r","\\r")); }
  static String tryIt(java.util.concurrent.Callable<Object> c){ try { return String.valueOf(c.call()); } catch(Throwable t){ return "!"+(t instanceof IOException ? "IOE:" : "RTE:")+t.getClass().getSimpleName(); } }
  static String files(Path d) throws Exception { StringBuilder sb = new StringBuilder(); MessageDigest md = MessageDigest.getInstance("SHA-256");
    try (java.util.stream.Stream<Path> s = Files.walk(d)) { for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile).sorted()::iterator) {
      byte[] b = Files.readAllBytes(f); StringBuilder h = new StringBuilder(); for (byte x : md.digest(b)) h.append(String.format("%02x", x));
      sb.append(d.relativize(f)).append(":").append(b.length).append(":").append(h, 0, 16).append(" "); } }
    return sb.toString(); }
  public static Map<String, String> compute(Path dir) throws Exception {
    out.clear();
    long[] ids = {0L, 1L, 2L, 255L, 256L, 4095L, 4096L, 65535L, 65536L, 123456789L, (1L << 20) + 3, (5L << 12) | 77};
    Path bf = dir.resolve("bloom");
    PersistedBloomFilter f = new PersistedBloomFilter(bf.toFile(), -1, 4, 12, 8, 1);
    StringBuilder adds = new StringBuilder(); for (long id : ids) adds.append(f.addIdToSet(id)).append(f.addIdToSet(id)).append(',');
    p("bloom.adds", adds); f.flushToDisk();
    p("bloom.files", files(bf));
    PersistedBloomFilter g = new PersistedBloomFilter(bf.toFile(), -1, 4, 12, 8, 1);
    StringBuilder ex = new StringBuilder(); for (long id : ids) ex.append(g.idExists(id)).append(g.idExists(id + 1)).append(',');
    p("bloom.reopen.exists", ex);
    File bucket = dir.resolve("bucket.map").toFile();
    ReadBucket rb = new ReadBucket(1000, bucket); WriteBucket wb = new WriteBucket(1000, bucket, rb);
    for (long id : ids) wb.set(id * 7919L); wb.flush();
    p("bucket.files", files(dir.resolve("bucket.map").getParent()).replaceAll("bloom/[^ ]* ", ""));
    ReadBucket rb2 = new ReadBucket(1000, bucket); StringBuilder be = new StringBuilder(); for (long id : ids) be.append(rb2.exists(id * 7919L)).append(rb2.exists(id * 7919L + 1)).append(',');
    p("bucket.reopen.exists", be + " size=" + rb2.size());
    return out;
  }

  static Map<String, String> load(String name) throws IOException {
    Map<String, String> m = new LinkedHashMap<>();
    try (InputStream in = PersistedFormatGoldenTest.class.getResourceAsStream(name)) {
      if (in == null) return m;
      for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
        if (line.isEmpty()) continue;
        int i = line.indexOf('=');
        m.put(line.substring(0, i), line.substring(i + 1));
      }
    }
    return m;
  }

  @org.junit.jupiter.api.Test
  void matches302Output(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
    Map<String, String> expected = load("dedupe-3.0.2.txt");
    
    Map<String, String> actual = new LinkedHashMap<>();
    compute(dir).forEach((k, v) -> actual.put(k, v.replace("StringNode", "TextNode")));
    org.junit.jupiter.api.Assertions.assertEquals(5, expected.size());
    org.junit.jupiter.api.Assertions.assertEquals(new java.util.ArrayList<>(expected.entrySet()), new java.util.ArrayList<>(actual.entrySet()));
  }
}

