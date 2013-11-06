/*
 * Copyright 2013 Samppa Saarela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.javersion.util;

import static com.google.common.base.Objects.equal;
import static java.lang.System.arraycopy;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;

public abstract class AbstractTrieMap<K, V, M extends AbstractTrieMap<K, V, M>> implements Iterable<Map.Entry<K, V>>{

    protected static final class UpdateContext<K, V> implements Merger<K, V> {
        
        int expectedUpdates;
        
        Merger<K, V> merger;
        
        private int change = 0;
        
        UpdateContext(int expectedUpdates) {
            this(expectedUpdates, null);
        }
        UpdateContext(int expectedUpdates, Merger<K, V> merger) {
            this.expectedUpdates = expectedUpdates;
            this.merger = merger;
        }
        
        int getChangeAndReset() {
            try {
                return change;
            } finally {
                change = 0;
            }
        }

        @Override
        public void insert(Entry<K, V> newEntry) {
            change = 1;
            if (merger != null) {
                merger.insert(newEntry);
            }
        }

        @Override
        public Entry<K, V> merge(Entry<K, V> oldEntry, Entry<K, V> newEntry) {
            return merger == null ? newEntry : merger.merge(oldEntry, newEntry);
        }
        
        @Override
        public void delete(Entry<K, V> oldEntry) {
            change = -1;
            if (merger != null) {
                merger.delete(oldEntry);
            }
        }
        
    }
    
    public static class ContextReference<K, V> {
        private UpdateContext<K, V> context;
        ContextReference(UpdateContext<K, V> context) {
            this.context = context;
        }
        void commit() {
            this.context = null;
        }
        UpdateContext<K, V> get() {
            return context;
        }
        boolean isCommitted() {
            return context == null;
        }
        void validate() {
            if (isCommitted()) {
                throw new IllegalStateException("This update is already committed");
            }
        }
    }
    
    public abstract int size();

    
    public M assoc(K key, V value) {
        return assoc(new Entry<K, V>(key, value));
    }
    
    public final M assoc(java.util.Map.Entry<? extends K, ? extends V> entry) {
        return doAssoc(contextReference(1, null), entry);
    }

    
    public final M assocAll(Map<? extends K, ? extends V> map) {
        return doAssocAll(contextReference(map.size(), null), map.entrySet());
    }

    public final M assocAll(Collection<Map.Entry<? extends K, ? extends V>> entries) {
        return doAssocAll(contextReference(entries.size(), null), entries);
    }

    public final M assocAll(Iterable<Map.Entry<? extends K, ? extends V>> entries, int expectedUpdates) {
        return doAssocAll(contextReference(expectedUpdates, null), entries);
    }


    
    public M merge(K key, V value, Merger<K, V> merger) {
        return merge(new Entry<K, V>(key, value), merger);
    }
    
    public final M merge(java.util.Map.Entry<? extends K, ? extends V> entry, Merger<K, V> merger) {
        return doAssoc(contextReference(1, merger), entry);
    }

    
    public final M mergeAll(Map<? extends K, ? extends V> map, Merger<K, V> merger) {
        return doAssocAll(contextReference(map.size(), merger), map.entrySet());
    }

    public final M mergeAll(Collection<Map.Entry<? extends K, ? extends V>> entries, Merger<K, V> merger) {
        return doAssocAll(contextReference(entries.size(), merger), entries);
    }

    public final M mergeAll(PersistentMap<K, V> map, Merger<K, V> merger) {
        return doAssocAll(contextReference(map.size(), merger), map);
    }

    public final M mergeAll(Iterable<Map.Entry<? extends K, ? extends V>> entries, int expectedUpdates, Merger<K, V> merger) {
        return doAssocAll(contextReference(expectedUpdates, merger), entries);
    }

    
    public final M dissoc(Object key) {
        return doDissoc(contextReference(1, null), key);
    }

    public final M dissoc(Object key, Merger<K, V> merger) {
        return doDissoc(contextReference(1, merger), key);
    }


    public M update(MapUpdate<K, V> updateFunction) {
        return update(32, updateFunction);
    }

    public M update(int expectedUpdates, MapUpdate<K, V> updateFunction) {
        return update(expectedUpdates, updateFunction, null);
    }

    public abstract M update(int expectedUpdates, MapUpdate<K, V> updateFunction, Merger<K, V> merger);
    
    
    public V get(Object key) {
        Entry<K, V> entry = getRoot().find(key);
        return entry != null ? entry.getValue() : null;
    }
    
    public boolean containsKey(Object key) {
        return getRoot().find(key) != null;
    }

    public Iterator<Map.Entry<K, V>> iterator() {
        return getRoot().iterator();
    }
    
    
    @SuppressWarnings("unchecked")
    protected static <K, V> Entry<K, V> toEntry(Map.Entry<? extends K, ? extends V> entry) {
        if (entry instanceof Entry) {
            return (Entry<K, V>) entry;
        } else {
            return new Entry<K, V>(entry.getKey(), entry.getValue());
        }
    }

    
    private final M doAssoc(ContextReference<K, V> contextReference, Map.Entry<? extends K, ? extends V> entry) {
        Node<K, V> newRoot = getRoot().assoc(contextReference, toEntry(entry));
        return doReturn(contextReference, newRoot, size() + contextReference.get().getChangeAndReset());
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private final M doAssocAll(ContextReference<K, V> contextReference, Iterable entries) {
        Node<K, V> newRoot = getRoot();
        int size = size();
        for (Map.Entry<K, V> entry : (Iterable<Map.Entry<K, V>>) entries) {
            newRoot = newRoot.assoc(contextReference, toEntry(entry));
            size += contextReference.get().getChangeAndReset();
        }
        
        return doReturn(contextReference, newRoot, size);
    }
        
    private M doDissoc(ContextReference<K, V> contextReference, Object key) {
        Node<K, V> newRoot = getRoot().dissoc(contextReference, key);
        return doReturn(contextReference, newRoot, size() + contextReference.get().getChangeAndReset());
    }
    
    
    static abstract class Node<K, V> implements Iterable<Map.Entry<K, V>> {

        Entry<K, V> find(Object key) {
            return findInternal(0, hash(key), key);
        }

        Node<K, V> assoc(ContextReference<K, V>  currentContext, Entry<K, V> newEntry) {
            return assocInternal(currentContext, 0, newEntry);
        }

        Node<K, V> dissoc(ContextReference<K, V>  currentContext, Object key) {
            return dissocInternal(currentContext, 0, hash(key), key);
        }
        
        static int hash(Object key) {
            return key == null ? 0 : key.hashCode();
        }

        final int index(int bitmap, int bit){
            return Integer.bitCount(bitmap & (bit - 1));
        }

        int bit(int hash, int level) {
            return bit(bitIndex(hash, level * 5));
        }

        int bit(int bitIndex) {
            // (bitpos + 1)'th bit
            return 1 << bitIndex;
        }
        
        int bitIndex(int hash, int shift) {
            // xx xxxxx xxxxx xxxxx xxxxx NNNNN xxxxx   >>> 5
            // 00 00000 00000 00000 00000 00000 NNNNN   & 0x01f
            // return number (NNNNN) between 0..31
            return (hash >>> shift) & 0x01f;
        }
        
        
        abstract Entry<K, V> findInternal(int level, int hash, Object key);

        abstract Node<K, V> assocInternal(ContextReference<K, V>  currentContext, int level, Entry<K, V> newEntry);

        abstract Node<K, V> dissocInternal(ContextReference<K, V>  currentContext, int level, int hash, Object key);
        
    }
    
    @SuppressWarnings("rawtypes")
    private static final Iterator<Map.Entry> EMTPY_ITER = Iterators.emptyIterator();
    
    @SuppressWarnings("rawtypes")
    static final Node EMPTY_NODE = new Node() {
        
        @Override
        public Iterator<Map.Entry> iterator() {
            return EMTPY_ITER;
        }
        
        @Override
        Entry findInternal(int level, int hash, Object key) {
            return null;
        }
        
        @Override
        Node dissocInternal(ContextReference  currentContext, int level, int hash, Object key) {
            return this;
        }
        
        @SuppressWarnings("unchecked")
        @Override
        Node assocInternal(ContextReference  currentContext, int level, Entry newEntry) {
            Node node = new HashNode<>(currentContext);
            return node.assocInternal(currentContext, level, newEntry);
        }
    };
    
    public static final class Entry<K, V> extends Node<K, V> implements Map.Entry<K, V> {
        
        final int hash;
        
        final K key; 
        
        final V value;
        
        public Entry(K key, V value) {
            this(hash(key), key, value);
        }
        
        Entry(int hash, K key, V value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            throw new UnsupportedOperationException();
        }
     
        public String toString() {
            return "" + key + ": " + value;
        }

        public Node<K, V> assocInternal(final ContextReference<K, V>  currentContext, final int level, final Entry<K, V> newEntry) {
            if (equal(newEntry.key, key)) {
                if (equal(newEntry.value, value)) {
                    return this;
                } else {
                    return currentContext.get().merge(this, newEntry);
                }
            }
            else if (newEntry.hash == hash) {
                currentContext.get().insert(newEntry);
                return new CollisionNode<K, V>(this, newEntry);
            }
            else {
                return new HashNode<K, V>(currentContext, 4)
                        .assocInternal(currentContext, level, this)
                        .assocInternal(currentContext, level, newEntry);
            }
        }

        @Override
        Node<K, V> dissocInternal(ContextReference<K, V>  currentContext, int level, int hash, Object key) {
            if (equal(key, this.key)) {
                currentContext.get().delete(this);
                return null;
            }
            return this;
        }
        
        @Override
        public Entry<K, V> findInternal(int level, int hash, Object key) {
            if (equal(this.key, key)) {
                return this;
            }
            return null;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return Iterators.<Map.Entry<K, V>>singletonIterator(this);
        }
    }
    
    
    static final class HashNode<K, V> extends Node<K, V> {
        
        private final ContextReference<K, V>  contextReference;
        
        private int bitmap; 
        
        private Node<K, V>[] children;

        HashNode(ContextReference<K, V>  contextReference) {
            this(contextReference, contextReference.get().expectedUpdates);
        }

        @SuppressWarnings("unchecked")
        HashNode(ContextReference<K, V>  contextReference, int expectedSize) {
            this(contextReference, 0, new Node[expectedSize < 32 ? expectedSize : 32]);
        }
        
        HashNode(ContextReference<K, V>  contextReference, int bitmap, Node<K, V>[] children) {
            this.contextReference = contextReference;
            this.bitmap = bitmap;
            this.children = children;
        }

        @Override
        Node<K, V> assocInternal(final ContextReference<K, V>  currentContext, final int level, final Entry<K, V> newEntry) {
            int bit = bit(newEntry.hash, level);
            int index = index(bitmap, bit);
            if ((bitmap & bit) != 0) {
                Node<K, V> oldNode = children[index];
                Node<K, V> newNode = oldNode.assocInternal(currentContext, level + 1, newEntry);
                if (newNode == oldNode) {
                    return this;
                } else {
                    HashNode<K, V> editable = cloneForReplace(currentContext);
                    editable.children[index] = newNode;

                    return editable;
                }
            } else {
                currentContext.get().insert(newEntry);
                HashNode<K, V> editable = cloneForInsert(currentContext, index);
                editable.children[index] = newEntry;
                editable.bitmap |= bit;
                
                return editable;
            }
        }

        @Override
        Node<K, V> dissocInternal(ContextReference<K, V>  currentContext, int level, int hash, Object key) {
            int bit = bit(hash, level);
            if ((bitmap & bit) == 0) {
                return this;
            }
            int index = index(bitmap, bit);
            Node<K, V> oldNode = children[index];
            Node<K, V> newNode = oldNode.dissocInternal(currentContext, level + 1, hash, key);

            if (newNode == oldNode) {
                return this;
            } else if (newNode == null) {
                int childCount = childCount();
                if (childCount == 1) {
                    return null;
                } else {
                    HashNode<K, V> editable = cloneForDelete(currentContext, index);
                    editable.bitmap = bitmap ^ bit;
                    return editable;
                }
            } else {
                HashNode<K, V> editable = cloneForReplace(currentContext);
                editable.children[index] = newNode;

                return editable;
            }
        }
        
        @Override
        public Entry<K, V> findInternal(int level, int hash, Object key) {
            int bit = bit(hash, level);
            if ((bitmap & bit) == 0) {
                return null;
            }
            int index = index(bitmap, bit);
            Node<K, V> nodeOrEntry = children[index];
            return nodeOrEntry.findInternal(level + 1, hash, key);
        }
        

        @SuppressWarnings("unchecked")
        private HashNode<K, V> cloneForInsert(ContextReference<K, V>  currentContext, int index) {
            int childCount = childCount();
            boolean editInPlace = isEditInPlace(currentContext);

            Node<K, V>[] newChildren;
            if (editInPlace && childCount < children.length) {
                newChildren = this.children;
            } else {
                newChildren = new Node[newSize(currentContext, childCount)];
                if (index > 0) {
                    arraycopy(children, 0, newChildren, 0, index);
                }
            }

            // make room for insertion
            if (index < childCount) {
                arraycopy(children, index, newChildren, index + 1, childCount - index);
            }
            
            return withNewChildren(currentContext, editInPlace, newChildren);
        }

        private int childCount() {
            return Integer.bitCount(bitmap);
        }

        @SuppressWarnings("unchecked")
        private HashNode<K, V> cloneForDelete(ContextReference<K, V>  currentContext, int index) {
            int childCount = childCount();
            boolean editInPlace = isEditInPlace(currentContext);

            Node<K, V>[] newChildren;
            if (editInPlace) {
                newChildren = this.children;
            } else {
                newChildren = new Node[childCount - 1];
                if (index > 0) {
                    arraycopy(children, 0, newChildren, 0, index);
                }
            }

            // Delete given node
            if (index + 1 < children.length) {
                arraycopy(children, index + 1, newChildren, index, childCount - index - 1);
                if (newChildren.length >= childCount) {
                    newChildren[childCount - 1] = null;
                }
            }
            
            return withNewChildren(currentContext, editInPlace, newChildren);
        }

        private HashNode<K, V> withNewChildren(ContextReference<K, V>  currentContext,
                boolean editInPlace, Node<K, V>[] newChildren) {
            if (editInPlace) {
                children = newChildren;
                return this;
            } else {
                return new HashNode<K, V>(currentContext, bitmap, newChildren);
            }
        }

        private boolean isEditInPlace(ContextReference<K, V>  currentContext) {
            boolean editInPlace = currentContext == this.contextReference;
            return editInPlace;
        }
        
        private int newSize(ContextReference<K, V>  currentContext, int childCount) {
            if (currentContext.get().expectedUpdates == 1) {
                return childCount + 1;
            } else {
                return childCount < 16 ? 2*(childCount + 1) : 32;
            }
        }
        
        private HashNode<K, V> cloneForReplace(ContextReference<K, V>  currentContext) {
            if (currentContext.get() == this.contextReference.get()) {
                return this;
            } else {
                return new HashNode<>(currentContext, bitmap, children.clone());
            }
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new ArrayIterator<>(children, childCount());
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("#{");
            boolean first = true;
            for (Node<K, V> child : children) {
                if (child != null) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(child);
                }
            }
            sb.append("}");
            return sb.toString();
        }
    }

    
    static final class CollisionNode<K, V> extends Node<K, V> {
        
        final int hash;
        
        private Entry<K, V>[] entries;

        @SuppressWarnings("unchecked")
        public CollisionNode(Entry<K, V> first, Entry<K, V> second) {
            this.hash = first.hash;
            this.entries = new Entry[] { first, second };
        }
        @SuppressWarnings("unchecked")
        private CollisionNode(Entry<? extends K, ? extends V>[] entries) {
            this.hash = entries[0].hash;
            this.entries = (Entry<K, V>[]) entries;
        }

        @Override
        public Entry<K, V> findInternal(int level, int hash, Object key) {
            for (Entry<K, V> entry : entries) {
                if (equal(entry.key, key)) {
                    return entry;
                }
            }
            return null;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Node<K, V> assocInternal(final ContextReference<K, V>  currentContext, final int level, final Entry<K, V> newEntry) {
            if (newEntry.hash == this.hash) {
                for (int i=0; i < entries.length; i++) {
                    if (equal(entries[i].key, newEntry.key)) {
                        if (equal(entries[i].value, newEntry.value)) {
                            return this;
                        }
                        Entry<K, V>[] newEntries = entries.clone();
                        newEntries[i] = currentContext.get().merge(entries[i], newEntry);
                        return new CollisionNode<K, V>(newEntries);
                    }
                }
                
                currentContext.get().insert(newEntry);

                Entry<K, V>[] newEntries = new Entry[entries.length + 1];
                arraycopy(entries, 0, newEntries, 0, entries.length);
                newEntries[entries.length] = newEntry;
                return new CollisionNode<K, V>(newEntries);
            }
            
            
            Node<K, V>[] newChildren = (currentContext.get().expectedUpdates == 1 
                    ? new Node[] { this, null } : new Node[] { this, null, null, null });

            Node<K, V> newNode = new HashNode<K, V>(currentContext, bit(this.hash, level), newChildren);
            return newNode.assocInternal(currentContext, level, newEntry);
        }

        @Override
        Node<K, V> dissocInternal(ContextReference<K, V>  currentContext, int level, int hash, Object key) {
            if (hash == this.hash) {
                for (int i=0; i < entries.length; i++) {
                    if (equal(entries[i].key, key)) {
                        currentContext.get().delete(entries[i]);
    
                        if (entries.length == 2) {
                            if (i == 1) {
                                return entries[0];
                            } else {
                                return entries[1];
                            }
                        }
                        @SuppressWarnings("unchecked")
                        Entry<K, V>[] newEntries = new Entry[entries.length - 1];
                        arraycopy(entries, 0, newEntries, 0, i);
                        if (i + 1 < entries.length) {
                            arraycopy(entries, i + 1, newEntries, i, entries.length - i - 1);
                        }
                        return new CollisionNode<K, V>(newEntries);
                    }
                }
            }
            return this;
        }
        
        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new ArrayIterator<>(entries);
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Entry<K, V> entry : entries) {
                if (entry != null) {
                    if (!first) {
                        sb.append(", ");
                    }
                    first = false;
                    sb.append(entry);
                }
            }
            sb.append("]");
            return sb.toString();
        }
    }
    
    static class ArrayIterator<K, V, T extends Node<K, V>> extends UnmodifiableIterator<Map.Entry<K, V>> {
        
        private final T[] array;
        
        private final int limit;
        
        private Iterator<Map.Entry<K, V>> subIterator;
        
        private int pos = 0;
        
        public ArrayIterator(T[] array) {
            this(array, array.length);
        }
        
        public ArrayIterator(T[] array, int limit) {
            this.array = array;
            this.limit = limit;
        }
        
        @Override
        public boolean hasNext() {
            if (subIterator != null) {
                if (subIterator.hasNext()) {
                    return true;
                } else {
                    pos++;
                }
            }
            if (pos < limit) {
                if (array[pos] instanceof Entry) {
                    subIterator = null;
                } else {
                    subIterator = array[pos].iterator();
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            if (subIterator != null) {
                return subIterator.next();
            } else {
                return (Entry<K, V>) array[pos++];
            }
        }
    }

    protected abstract ContextReference<K, V> contextReference(int expectedUpdates, Merger<K, V> merger);
    
    protected abstract M self();
    
    protected abstract M doReturn(ContextReference<K, V> context, Node<K, V> newRoot, int newSize);
    
    abstract Node<K, V> getRoot();
}