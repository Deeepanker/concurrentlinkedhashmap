package com.rc.util.concurrent;

import static com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.createWorkingSet;
import static com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.determineEfficiency;
import static java.lang.String.format;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.SerializationUtils;
import org.testng.annotations.Test;

import com.rc.util.concurrent.ConcurrentLinkedHashMap.EvictionPolicy;
import com.rc.util.concurrent.performance.Caches;
import com.rc.util.concurrent.performance.SecondChanceMap;
import com.rc.util.concurrent.performance.CacheEfficiencyTestHarness.Distribution;
import com.rc.util.concurrent.performance.Caches.Cache;

/**
 * The non-concurrent tests for the {@link ConcurrentLinkedHashMap}.
 *
 * @author <a href="mailto:ben.manes@reardencommerce.com">Ben Manes</a>
 */
@SuppressWarnings("unchecked")
public final class SingleThreadedTest extends BaseTest {

    /**
     * Tests {@link ConcurrentLinkedHashMap#ConcurrentLinkedHashMap(int)} is empty.
     */
    @Test
    public void empty() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        validator.state(cache);
        validator.empty(cache);
    }

    /**
     * Tests {@link Map#putAll(Map)}.
     */
    @Test
    public void putAll() {
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        cache.putAll(expected);

        validator.allNodesMarked(cache, false);
        validator.state(cache);
        assertEquals(cache, expected);
    }

    /**
     * Tests {@link Map#put()}.
     */
    @Test
    public void put() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = create();
        cache.put(0, 0);
        int old = cache.put(0, 1);
        int current = cache.get(0);

        assertEquals(old, 0);
        assertEquals(current, 1);

        validator.state(cache);
    }

    /**
     * Tests {@link Map#putIfAbsent()}.
     */
    @Test
    public void putIfAbsent() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createGuarded();
        for (Integer i=0; i<capacity; i++) {
            assertNull(cache.putIfAbsent(i, i));
            assertEquals(cache.putIfAbsent(i, -1), i);
            assertEquals(cache.data.get(i).getValue(), i);
        }
        assertEquals(cache.size(), capacity, "Not warmed to max size");
        validator.state(cache);
        validator.allNodesMarked(cache, false);
        assertEquals(cache, createWarmedMap());
    }

    /**
     * Tests {@link Map#containsKey(Object)}, {@link Map#containsValue(Object)}, {@link Map#get(Object)}.
     */
    @Test
    public void retrieval() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        for (Integer i=-capacity; i<0; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        for (Integer i=0; i<capacity; i++) {
            assertEquals(cache.get(i), i);
            assertTrue(cache.containsKey(i));
            assertTrue(cache.containsValue(i));
        }
        for (Integer i=capacity; i<capacity*2; i++) {
            assertNull(cache.get(i));
            assertFalse(cache.containsKey(i));
            assertFalse(cache.containsValue(i));
        }
        validator.state(cache);
        validator.allNodesMarked(cache, true);
    }

    /**
     * Tests {@link Map#remove()} and {@link java.util.concurrent.ConcurrentMap#remove(Object, Object)}
     */
    @Test
    public void remove() {
        EvictionMonitor monitor = EvictionMonitor.newMonitor();

        // Map#remove()
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(monitor);
        for (Integer i=0; i<capacity; i++) {
            assertEquals(cache.remove(i), i, format("Failure on index #%d", i));
            assertNull(cache.remove(i), "Not fully removed");
            assertFalse(cache.containsKey(i));
        }
        validator.state(cache);
        validator.empty(cache);
        assertEquals(monitor.evicted.size(), capacity);

        // ConcurrentMap#remove()
        monitor.evicted.clear();
        cache = createWarmedMap(monitor);
        for (Integer i=0; i<capacity; i++) {
            assertFalse(cache.remove(i, -1));
            assertTrue(cache.remove(i, i));
            assertFalse(cache.remove(i, -1));
            assertFalse(cache.containsKey(i));
        }
        validator.state(cache);
        validator.empty(cache);
        validator.allNodesMarked(cache, false);
        assertEquals(monitor.evicted.size(), capacity);
    }

    /**
     * Tests {@link java.util.concurrent.ConcurrentMap#replace(Object, Object)} and {@link java.util.concurrent.ConcurrentMap#replace(Object, Object, Object)}.
     */
    @Test
    public void replace() {
        Integer dummy = -1;
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();
        for (Integer i=0; i<capacity; i++) {
            assertNotNull(cache.replace(i, dummy));
            assertFalse(cache.replace(i, i, i));
            assertEquals(cache.data.get(i).getValue(), dummy);
            assertTrue(cache.replace(i, dummy, i));
            assertEquals(cache.remove(i), i);
            assertNull(cache.replace(i, i));
        }
        validator.state(cache);
        validator.empty(cache);
        validator.allNodesMarked(cache, false);
    }

    /**
     * Tests {@link Map#clear()}.
     */
    @Test
    public void clear() {
        EvictionMonitor monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(monitor);
        cache.clear();
        validator.state(cache);
        assertEquals(monitor.evicted.size(), capacity);
    }

    /**
     * Tests {@link ConcurrentLinkedHashMap#setCapacity(int)}.
     */
    @Test
    public void capacity() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap();

        int newMaxCapacity = 2*capacity;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache, createWarmedMap());
        validator.state(cache);

        newMaxCapacity = capacity/2;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);

        newMaxCapacity = 1;
        cache.setCapacity(newMaxCapacity);
        assertEquals(cache.capacity(), newMaxCapacity);
        assertEquals(cache.size(), newMaxCapacity);
        validator.state(cache);

        try {
            cache.setCapacity(-1);
            fail("Capacity must be positive");
        } catch (Exception e) {
            assertEquals(cache.capacity(), newMaxCapacity);
        }
    }

    /**
     * Tests that entries are evicted in FIFO order.
     */
    @Test
    public void evictAsFifo() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(EvictionPolicy.FIFO, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Tests that entries are evicted in FIFO order under a SECOND_CHANCE policy where none are saved.
     */
    @Test
    public void evictSecondChanceAsFifo() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(EvictionPolicy.SECOND_CHANCE, monitor);

        // perform test
        doFifoEvictionTest(cache, monitor);
    }

    /**
     * Executes a FIFO eviction test.
     */
    private void doFifoEvictionTest(ConcurrentLinkedHashMap<Integer, Integer> cache, EvictionMonitor<Integer, Integer> monitor) {
        for (Integer i=0; i<3*capacity; i++) {
            cache.put(i, i);
        }

        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(capacity);
        for (Integer i=2*capacity; i<3*capacity; i++) {
            expected.put(i, i);
        }

        validator.state(cache);
        validator.allNodesMarked(cache, false);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), 2*capacity);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order using a simple working set.
     */
    @Test
    public void evictAsSecondChanceFifoSimple() {
        Map<Integer, Integer> expected = new HashMap<Integer, Integer>(capacity);
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = create(EvictionPolicy.SECOND_CHANCE, monitor);
        for (Integer i=0; i<capacity; i++) {
            cache.put(i, i);
            if (i%2 == 0) {
                cache.get(i);
                expected.put(i, i);
                assertTrue(cache.data.get(i).isMarked());
            }
        }

        for (Integer i=capacity; i<(capacity+capacity/2); i++) {
            cache.put(i, i);
            expected.put(i, i);
        }

        validator.state(cache);
        assertEquals(cache, expected);
        assertEquals(monitor.evicted.size(), capacity/2);
    }

    /**
     * Tests that entries are evicted in FIFO order using a complex working set.
     */
    @Test
    public void efficencyTestAsFifo() {
        ConcurrentLinkedHashMap<Integer, Integer> actual = create(EvictionPolicy.FIFO);
        Map<Integer, Integer> expected = Caches.create(Cache.SYNC_FIFO, capacity, capacity, 1);
        doEfficencyTest(actual, expected, true);
    }

    /**
     * Tests that entries are evicted in Second Chance FIFO order using a complex working set.
     */
    @Test
    public void efficencyTestAsSecondChanceFifo() {
        ConcurrentLinkedHashMap<Integer, Integer> actual = create(EvictionPolicy.SECOND_CHANCE);
        Map<Integer, Integer> expected = new SecondChanceMap<Integer, Integer>(capacity);
        doEfficencyTest(actual, expected, true);
    }

    /**
     * Executes a complex eviction test.
     */
    private void doEfficencyTest(ConcurrentLinkedHashMap<Integer, Integer> actual, Map<Integer, Integer> expected, boolean strict) {
        List<Integer> workingSet = createWorkingSet(Distribution.EXPONENTIAL, 10*capacity, 10*capacity);
        long hitExpected = determineEfficiency(expected, workingSet);
        long hitActual = determineEfficiency(actual, workingSet);
        if (strict) {
            assertEquals(hitActual, hitExpected);
        }
        assertTrue(hitExpected > 0);
        assertTrue(hitActual > 0);
        validator.state(actual);
    }

    /**
     * Tests that entries are evicted in LRU order using a complex working set.
     *
     * This cannot be directly compared to a {@link java.util.LinkedHashMap} due to dead nodes on the list.
     */
    @Test
    public void evictAsLru() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.LRU, 10);
        
        debug("Initial: %s", validator.externalizeLinkedList(cache));        
        assertTrue(cache.keySet().containsAll(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // re-order
        cache.get(0);
        cache.get(1);
        cache.get(2);

        debug("Reordered #1: %s", validator.externalizeLinkedList(cache));
        assertTrue(cache.keySet().containsAll(Arrays.asList(3, 4, 5, 6, 7, 8, 9, 0, 1, 2)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // evict 3, 4, 5
        cache.put(10, 10);
        cache.put(11, 11);
        cache.put(12, 12);

        debug("Evict #1: %s", validator.externalizeLinkedList(cache));
        assertTrue(cache.keySet().containsAll(Arrays.asList(6, 7, 8, 9, 0, 1, 2, 10, 11, 12)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // re-order
        cache.get(6);
        cache.get(7);
        cache.get(8);

        debug("Reordered #2: %s", validator.externalizeLinkedList(cache));
        assertTrue(cache.keySet().containsAll(Arrays.asList(9, 0, 1, 2, 10, 11, 12, 6, 7, 8)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);

        // evict 9, 0, 1
        cache.put(13, 13);
        cache.put(14, 14);
        cache.put(15, 15);

        debug("Evict #2: %s", validator.externalizeLinkedList(cache));
        assertTrue(cache.keySet().containsAll(Arrays.asList(2, 10, 11, 12, 6, 7, 8, 13, 14, 15)), "Instead: " + cache.keySet());
        assertEquals(cache.size(), 10);
    }

    /**
     * Tests that a full scan was required to evict an entry.
     */
    @Test
    public void evictSecondChanceFullScan() {
        EvictionMonitor<Integer, Integer> monitor = EvictionMonitor.newMonitor();
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(EvictionPolicy.SECOND_CHANCE, capacity, monitor);
        for (int i=0; i<capacity; i++) {
            cache.get(i);
        }
        validator.allNodesMarked(cache, true);
        assertEquals(cache.size(), capacity);

        cache.put(capacity, capacity);
        assertEquals(cache.size(), capacity);
        validator.allNodesMarked(cache, false);
        assertEquals(monitor.evicted.size(), 1);
    }

    /**
     * Tests {@link Object#equals(Object)}, {@link Object#hashCode()}, {@link Object#toString()}.
     */
    @Test
    public void object() {
        ConcurrentLinkedHashMap<Integer, Integer> cache = createWarmedMap(guard);
        Map<Integer, Integer> expected = new ConcurrentHashMap<Integer, Integer>(capacity);
        for (Integer i=0; i<capacity; i++) {
            expected.put(i, i);
        }
        assertEquals(cache, expected);
        assertEquals(cache.hashCode(), expected.hashCode());
        assertEquals(cache.toString(), expected.toString());
    }

    /**
     * Tests serialization.
     */
    @Test
    public void serialize() {
        ConcurrentLinkedHashMap<Integer, Integer> expected = createWarmedMap(guard);
        Object cache = SerializationUtils.clone(expected);
        assertEquals(cache, expected);
        validator.state((ConcurrentLinkedHashMap<Integer, Integer>) cache);
    }
}