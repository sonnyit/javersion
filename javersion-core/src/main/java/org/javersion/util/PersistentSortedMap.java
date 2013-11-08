package org.javersion.util;

import static org.javersion.util.PersistentSortedMap.Color.BLACK;
import static org.javersion.util.PersistentSortedMap.Color.RED;
import static org.javersion.util.PersistentSortedMap.NodeTranslator.LEFT;
import static org.javersion.util.PersistentSortedMap.NodeTranslator.RIGHT;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;

import com.google.common.base.Objects;

public class PersistentSortedMap<K, V> {
    
    static class Path<K, V> implements Iterable<Node<K, V>> {
        
        private final ArrayList<Node<K, V>> path;
        
        public Path() {
            path = new ArrayList<Node<K, V>>();
        }
        
        public Path(int size) {
            path = new ArrayList<>(size);
        }
        
        public int size() {
            return path.size();
        }
        
        public void push(Node<K, V> node) {
            path.add(node);
        }
        
        public Node<K, V> root() {
            return path.isEmpty() ? null : path.get(0);
        }
        
        public Node<K, V> parent() {
            return path.isEmpty() ? null : path.get(path.size() - 1);
        }
        
        public Node<K, V> grandParent() {
            return path.size() < 2 ? null : path.get(path.size() - 2);
        }
        
        public Node<K, V> get(int i) {
            i = (i < 0 ? path.size() + i : i);
            if (0 <= i && i < path.size()) {
                return path.get(i);
            } else {
                return null;
            }
        }

        @Override
        public Iterator<Node<K, V>> iterator() {
            return path.iterator();
        }
        
        public Node<K, V> pop() {
            return pop(1);
        }
        
        public Node<K, V> pop(int count) {
            Node<K, V> result = null;
            while (count-- > 0) {
                result = path.remove(path.size() - 1);
            }
            return result;
        }
        
        public Path<K, V> clone() {
            Path<K, V> clone = new Path<>(path.size());
            Node<K, V> origParent = null;
            Node<K, V> newParent = null;
            for (Node<K, V> origNode : path) {
                Node<K, V> newNode = origNode.clone();
                clone.push(newNode);
                // Connect to cloned parent
                if (origParent != null) {
                    if (origNode == origParent.left) {
                        newParent.left = newNode;
                    } else {
                        newParent.right = newNode;
                    }
                }
                origParent = origNode;
                newParent = newNode;
            }
            return clone;
        }
        
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append('/');
            for (Node<K, V> n : path) {
                if (sb.length() > 1) {
                    sb.append(" > ");
                }
                sb.append(n.label());
            }
            sb.append('\n').append(root());
            return sb.toString();
        }
    }
    
    @SuppressWarnings("rawtypes")
    private final static Comparator<Comparable> NATURAL = new Comparator<Comparable>() {
        @SuppressWarnings("unchecked")
        @Override
        public int compare(Comparable left, Comparable right) {
            Check.notNull(left, "left");
            Check.notNull(right, "right");
            return left.compareTo(right);
        }
    };
    
    public static <K, V> PersistentSortedMap<K, V> empty() {
        return new PersistentSortedMap<K, V>();
    }
    
    private final Comparator<? super K> comparator;
    
    private Node<K, V> root;
    
    private int size;
    
    @SuppressWarnings("unchecked")
    public PersistentSortedMap() {
        this((Comparator<K>) NATURAL);
    }
    
    public PersistentSortedMap(Comparator<? super K> comparator) {
        this.comparator = Check.notNull(comparator, "comparator");
    }
    
    private PersistentSortedMap(Comparator<? super K> comparator, Node<K, V> root, int size) {
        this(comparator);
        this.root = root;
        this.size = size;
    }
    
    public int size() {
        return size;
    }
    
    public V get(K key) {
        if (root == null) {
            return null;
        } else {
            int cmpr;
            Node<K, V> x = root;
            do {
                cmpr = comparator.compare(key, x.key);
                if (cmpr < 0) {
                    x = x.left;
                } else if (cmpr > 0) {
                    x = x.right;
                } else {
                    return x.value;
                }
            } while (x != null);
            return null;
        }
    }
    
    public PersistentSortedMap<K, V> assoc(K key, V value) {
        if (root == null) {
            // Null and type check
            comparator.compare(key, key);
            return new PersistentSortedMap<K, V>(
                    comparator,
                    new Node<K, V>(key, value, BLACK),
                    1);
        } else {
            Path<K, V> path = new Path<>();
            int cmpr = findPathTo(key, path);
            Node<K, V> x;
            
            // Existing key
            if (cmpr == 0) {
                x = path.parent();
                if (Objects.equal(value, x.value)) {
                    return this;
                } else {
                    path = path.clone();
                    path.parent().value = value;
                    return new PersistentSortedMap<K, V>(comparator, path.root(), size);
                }
            }
            
            // New key
            path = path.clone();
            x = path.parent();
            Node<K, V> newNode = new Node<K, V>(key, value, RED);
            if (cmpr < 0) {
                x.left = newNode;
            } else {
                x.right = newNode;
            }
            return postInsert(path, newNode);
        }
    }
    
    private int findPathTo(K key, Path<K, V> path) {
        Node<K, V> x = root;
        int cmpr = 0;
        while (x != null) {
            path.push(x);
            cmpr = comparator.compare(key, x.key);
            if (cmpr < 0) {
                x = x.left;
            } else if (cmpr > 0) {
                x = x.right;
            } else {
                return cmpr;
            }
        }
        return cmpr;
    }
    
    public PersistentSortedMap<K, V> dissoc(Object keyObj) {
        @SuppressWarnings("unchecked")
        K key = (K) keyObj;
        Path<K, V> path = new Path<>();
        int cmpr = findPathTo(key, path);
        if (cmpr != 0) {
            return this;
        }
        path = path.clone();
        Node<K, V> z = path.pop(1);
        Node<K, V> y;
        Node<K, V> x;
        
        if (z.left == null || z.right == null) {
            y = z;
        } else {
            y = successor(path, z);
        }
        if (y.left != null) {
            x = y.left;
        } else {
            x = y.right;
        }
        Node<K, V> py = path.parent();
        if (py == null) {
            path.push(x);
        } else {
            if (y == py.left) {
                py.left = x;
            } else {
                py.right = x;
            }
        }
        if (y != z) {
            z.key = y.key;
            z.value = y.value;
        }
        if (y.color == BLACK) {
            deleteFixup(path, x);
        }
        if (path.size() > 0) {
            y = path.root();
        }
        return new PersistentSortedMap<>(comparator, y, size - 1);
    }
    
    private void deleteFixup(Path<K, V> path, Node<K, V> x) {
        Node<K, V> px = path.parent();
        Node<K, V> w;
        while (px != null && x.color == BLACK) {
            NodeTranslator translator;
            if (x == px.left) {
                translator = LEFT;
            } else {
                translator = RIGHT;
            }
            w = translator.right(px);
            if (w.color == RED) {
                w.color = BLACK;
                px.color = RED;
                path.pop();
                rotate(translator.left(), path, px);
                w = px.right;
            }
            if (translator.left(w).color == BLACK && translator.right(w).color == BLACK) {
                w.color = RED;
                x = px;
            } else {
                if (translator.right(w).color == BLACK) {
                    translator.left(w).color = BLACK;
                    w.color = RED;
                    rotate(translator.right(), path, w);
                    w = translator.right(px);
                }
                w.color = px.color;
                px.color = BLACK;
                translator.right(w).color = BLACK;
                path.pop();
                rotate(translator.left(), path, px);
                x = path.root();
                px = null;
            }
        }
    }

    private Node<K, V> successor(Path<K, V> path, Node<K, V> x) {
        if (x.right != null) {
            return minimum(path, x);
        }
        Node<K, V> y = path.pop();
        while (y != null && x == y.right) {
            x = y;
            y = path.pop();
        }
        return y;
    }
    
    private Node<K, V> minimum(Path<K, V> path, Node<K, V> x) {
        while (x.left != null) {
            path.push(x);
            x = x.left;
        }
        return x;
    }
    
    private Node<K, V> maximum(Node<K, V> x) {
        while (x.right != null) {
            x = x.right;
        }
        return x;
    }

    private PersistentSortedMap<K, V> postInsert(Path<K, V> path, Node<K, V> newNode) {
        Node<K, V> x = newNode;
        Node<K, V> y;
        Node<K, V> px = path.parent();
        Node<K, V> ppx = path.grandParent();
        
        while (px != null && isRed(px)) {
            NodeTranslator translator;
            if (px == ppx.left) {
                translator = LEFT;
            } else {
                translator = RIGHT;
            }
            y = translator.right(ppx);
            if (isRed(y)) {
                y = y.clone();
                translator.setRight(ppx, y);
                px.color = BLACK;
                y.color = BLACK;
                ppx.color = RED;
                x = ppx;
                path.pop(2);
                px = path.parent();
                ppx = path.grandParent();
            } else {
                if (x == translator.right(px)) {
                    x = px;
                    path.pop();
                    rotate(translator.left(), path, x);
                    px = path.parent();
                    ppx = path.grandParent();
                }
                px.color = BLACK;
                ppx.color = RED;
                path.pop(2);
                rotate(translator.right(), path, ppx);
            }
        }
        if (path.size() > 0) {
            x = path.root();
        }
        x.color = BLACK;
        return new PersistentSortedMap<K, V>(comparator, x, size + 1);
    }
    
    private void rotate(NodeTranslator translator, Path<K, V> path, Node<K, V> x) {
        Node<K, V> y = translator.right(x);
        translator.setRight(x, translator.left(y));
        Node<K, V> px = path.parent();
        path.push(y);
        if (x == translator.left(px)) {
            translator.setLeft(px, y);
        } else {
            translator.setRight(px, y);
        }
        translator.setLeft(y, x);
    }

    private boolean isRed(Node<K, V> node) {
        return node != null && node.color == RED;
    }
    
    Node<K, V> root() {
        return root;
    }
    
    public String toString() {
        return root == null ? "NIL" : root.toString();
    }
    
    static enum NodeTranslator {
        LEFT,
        /**
         * Inverse of LEFT
         */
        RIGHT {
            public <K, V> Node<K, V> right(Node<K, V> node) {
                return node == null ? null : node.left;
            }
            public <K, V> Node<K, V> left(Node<K, V> node) {
                return node == null ? null : node.right;
            }
            public <K, V> void setRight(Node<K, V> node, Node<K, V> newRight) {
                if (node != null) {
                    node.left = newRight;
                }
            }
            public <K, V> void setLeft(Node<K, V> node, Node<K, V> newLeft) {
                if (node != null) {
                    node.right = newLeft;
                }
            }
            public NodeTranslator left() {
                return RIGHT;
            }
            public NodeTranslator right() {
                return LEFT;
            }
        };
        public <K, V> Node<K, V> right(Node<K, V> node) {
            return node == null ? null : node.right;
        }
        public <K, V> Node<K, V> left(Node<K, V> node) {
            return node == null ? null : node.left;
        }
        public <K, V> void setRight(Node<K, V> node, Node<K, V> newRight) {
            if (node != null) {
                node.right = newRight;
            }
        }
        public <K, V> void setLeft(Node<K, V> node, Node<K, V> newLeft) {
            if (node != null) {
                node.left = newLeft;
            }
        }
        public NodeTranslator left() {
            return LEFT;
        }
        public NodeTranslator right() {
            return RIGHT;
        }
    }
    
    static enum Color {
        RED,
        BLACK
    }

    static class Node<K, V> implements Map.Entry<K, V>, Cloneable {
        K key;
        V value;
        Color color;
        Node<K, V> left;
        Node<K, V> right;
        public Node(K key, V value, Color color) {
            this.key = key;
            this.value = value;
            this.color = color;
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
        @SuppressWarnings("unchecked")
        public Node<K, V> clone() {
            try {
                return (Node<K, V>) super.clone();
            } catch (CloneNotSupportedException e) {
                // Should never happen: die horribly!
                throw new Error(e);
            }
        }
        public String label() {
            return label(new StringBuilder()).toString();
        }
        private StringBuilder label(StringBuilder sb) {
            sb.append(color).append('(').append(key).append(':').append(value).append(')');
            return sb;
        }
        public String toString() {
            return toString(new StringBuilder(), 0).toString();
        }
        private StringBuilder toString(StringBuilder sb, int level) {
            label(sb);
            
            indent(sb, level+1).append("left:");
            if (left != null) {
                left.toString(sb, level+1);
            } else {
                sb.append("NIL");
            }

            indent(sb, level+1).append("right:");
            if (right != null) {
                right.toString(sb, level+1);
            } else {
                sb.append("NIL");
            }
            return sb;
        }
        private StringBuilder indent(StringBuilder sb, int level) {
            sb.append('\n');
            for (int i=0; i < level; i++) {
                sb.append("   ");
            }
            return sb;
        }
    }
    
    
}
