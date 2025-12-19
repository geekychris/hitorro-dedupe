# Hitorro Dedupe

This is the **hitorro-dedupe** project, providing data deduplication utilities including bloom filters and hash-based deduplication algorithms.

## Overview

The Hitorro Dedupe project provides efficient data deduplication capabilities:

- **Bloom Filters**: Space-efficient probabilistic data structures for membership testing
- **Persisted Bloom Filters**: Disk-backed bloom filters for large-scale deduplication
- **Hash-Based Deduplication**: File and data deduplication using content hashing
- **Bucket-Based Storage**: Efficient storage and retrieval of deduplicated content

## Project Structure

This is a standalone Maven JAR project.

```
hitorro-dedupe/
├── src/
│   ├── main/
│   │   └── java/
│   │       ├── com/hitorro/util/
│   │       │   ├── deduper/              # Core deduplication algorithms
│   │       │   └── persistedbloomfilter/ # Bloom filter implementation
│   │       └── ht/                       # Additional utilities
│   └── test/
├── pom.xml
└── README.md
```

## Key Components

### Deduplication (`com.hitorro.util.deduper`)
- **BucketContainer**: Manages deduplicated content in buckets
- **BucketHash**: Hash-based content addressing
- **FileDateComp**: File comparison utilities
- **ReadBucket/WriteBucket**: I/O operations for bucketed storage

### Bloom Filters (`com.hitorro.util.persistedbloomfilter`)
- **PersistedBloomFilter**: Disk-backed bloom filter implementation
- **BitVectorContext**: Bit vector storage for bloom filters
- **OldestBVCS**: Aging and expiration for bloom filter entries

## Use Cases

### 1. File Deduplication
```java
// Deduplicate files based on content hash
BucketHash hasher = new BucketHash();
WriteBucket bucket = new WriteBucket(outputPath);
// Process files and write unique content to buckets
```

### 2. Stream Deduplication
```java
// Use bloom filter to detect duplicates in data streams
PersistedBloomFilter filter = new PersistedBloomFilter(storageDir);
if (!filter.contains(hash)) {
    filter.add(hash);
    // Process unique item
}
```

### 3. Large-Scale Duplicate Detection
- Use persisted bloom filters for memory-efficient duplicate detection
- Supports aging/expiration of old entries
- Handles billions of items with acceptable false positive rates

## Building

```bash
mvn clean install
```

**Note:** This project originally depended on `hitorro-util` which has been deleted. Some refactoring may be needed.

## Dependencies

### External Dependencies
- **Trove4j 3.0.3**: High-performance collections
- **Google Guava 33.0.0**: Hashing and utilities
- **Apache Commons IO 2.15.1**: File I/O utilities
- **Apache Commons Codec 1.16.1**: Hashing algorithms

## Features

### Bloom Filter Features
- **Space Efficient**: Uses bit vectors for compact storage
- **Persistent**: Survives process restarts
- **Scalable**: Handles large datasets
- **Configurable**: Adjustable false positive rates
- **Aging Support**: Automatically expires old entries

### Deduplication Features
- **Content Addressing**: Hash-based unique identification
- **Bucket Storage**: Organized storage of deduplicated content
- **Fast Lookup**: Efficient duplicate detection
- **File System Based**: No external database required

## Performance Characteristics

- **Bloom Filter Space Complexity**: O(n) where n is the number of items
- **Lookup Time**: O(1) constant time lookups
- **False Positive Rate**: Configurable, typically 0.01-0.1%
- **Throughput**: Millions of operations per second

## Requirements

- Java 19 or higher
- Maven 3.6+

## Usage Example

```java
// Create a persisted bloom filter
PersistedBloomFilter filter = new PersistedBloomFilter(
    storagePath,
    expectedItems,
    falsePositiveRate
);

// Check and add items
String content = readData();
long hash = computeHash(content);

if (filter.contains(hash)) {
    // Duplicate detected
    skipProcessing();
} else {
    // New item
    filter.add(hash);
    processUniqueItem(content);
}
```

## License

See LICENSE file for details.
