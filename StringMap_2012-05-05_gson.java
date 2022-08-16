public class StringMap {
  @Override public V get(Object key) {
    if (key instanceof String) {
      LinkedEntry<V> entry = getEntry((String) key);
      return entry != null ? entry.value : null;
    } else {
      return null;
    }
  }

  private boolean removeMapping(Object key, Object value) {
    if (key == null || !(key instanceof String)) {
      return false;
    }

    int hash = hash((String) key);
    LinkedEntry<V>[] tab = table;
    int index = hash & (tab.length - 1);
    for (LinkedEntry<V> e = tab[index], prev = null; e != null; prev = e, e = e.next) {
      if (e.hash == hash && key.equals(e.key)) {
        if (value == null ? e.value != null : !value.equals(e.value)) {
          return false;  // Map has wrong value for key
        }
        if (prev == null) {
          tab[index] = e.next;
        } else {
          prev.next = e.next;
        }
        size--;
        unlink(e);
        return true;
      }
    }
    return false; // No entry for key
  }

  private static int hash(String key) {
    // Ensuring that the hash is unpredictable and well distributed.
    //
    // Finding unpredictable hash functions is a bit of a dark art as we need to balance
    // good unpredictability (to avoid DoS) and good distribution (for performance).
    //
    // We achieve this by using the same algorithm as the Perl version, but this implementation
    // is being written from scratch by inder who has never seen the
    // Perl version (for license compliance).
    //
    // TODO: investigate http://code.google.com/p/cityhash/ and http://code.google.com/p/smhasher/
    // both of which may have better distribution and/or unpredictability.
    int h = seed;
    for (int i = 0; i < key.length(); ++i) {
      int h2 = h + key.charAt(i);
      int h3 = h2 + h2 << 10; // h2 * 1024
      h = h3 ^ (h3 >>> 6); // h3 / 64
    }

    h ^= (h >>> 20) ^ (h >>> 12);
    return h ^ (h >>> 7) ^ (h >>> 4);
  }

    public boolean remove(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<?, ?> e = (Entry<?, ?>) o;
      return removeMapping(e.getKey(), e.getValue());
    }

  @Override public boolean containsKey(Object key) {
    return key instanceof String && getEntry((String) key) != null;
  }

  public Set<Entry<String, V>> entrySet() {
    Set<Entry<String, V>> es = entrySet;
    return (es != null) ? es : (entrySet = new EntrySet());
  }

  @Override public Set<String> keySet() {
    Set<String> ks = keySet;
    return (ks != null) ? ks : (keySet = new KeySet());
  }

  private LinkedEntry<V>[] makeTable(int newCapacity) {
    @SuppressWarnings("unchecked")
    LinkedEntry<V>[] newTable = (LinkedEntry<V>[]) new LinkedEntry[newCapacity];
    table = newTable;
    threshold = (newCapacity >> 1) + (newCapacity >> 2); // 3/4 capacity
    return newTable;
  }

  private void addNewEntry(String key, V value, int hash, int index) {
    LinkedEntry<V> header = this.header;

    // Create new entry, link it on to list, and put it into table
    LinkedEntry<V> oldTail = header.prv;
    LinkedEntry<V> newTail = new LinkedEntry<V>(
        key, value, hash, table[index], header, oldTail);
    table[index] = oldTail.nxt = header.prv = newTail;
  }

  private void unlink(LinkedEntry<V> e) {
    e.prv.nxt = e.nxt;
    e.nxt.prv = e.prv;
    e.nxt = e.prv = null; // Help the GC (for performance)
  }

  private LinkedEntry<V>[] doubleCapacity() {
    LinkedEntry<V>[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity == MAXIMUM_CAPACITY) {
      return oldTable;
    }
    int newCapacity = oldCapacity * 2;
    LinkedEntry<V>[] newTable = makeTable(newCapacity);
    if (size == 0) {
      return newTable;
    }

    for (int j = 0; j < oldCapacity; j++) {
      LinkedEntry<V> e = oldTable[j];
      if (e == null) {
        continue;
      }
      int highBit = e.hash & oldCapacity;
      LinkedEntry<V> broken = null;
      newTable[j | highBit] = e;
      for (LinkedEntry<V> n = e.next; n != null; e = n, n = n.next) {
        int nextHighBit = n.hash & oldCapacity;
        if (nextHighBit != highBit) {
          if (broken == null) {
            newTable[j | nextHighBit] = n;
          } else {
            broken.next = n;
          }
          broken = e;
          highBit = nextHighBit;
        }
      }
      if (broken != null) {
        broken.next = null;
      }
    }
    return newTable;
  }

    public final String getKey() {
      return key;
    }

  @Override public V put(String key, V value) {
    if (key == null) {
      throw new NullPointerException("key == null");
    }

    int hash = hash(key);
    LinkedEntry<V>[] tab = table;
    int index = hash & (tab.length - 1);
    for (LinkedEntry<V> e = tab[index]; e != null; e = e.next) {
      if (e.hash == hash && key.equals(e.key)) {
        V oldValue = e.value;
        e.value = value;
        return oldValue;
      }
    }

    // No entry for (non-null) key is present; create one
    if (size++ > threshold) {
      tab = doubleCapacity();
      index = hash & (tab.length - 1);
    }
    addNewEntry(key, value, hash, index);
    return null;
  }

  private LinkedEntry<V> getEntry(String key) {
    if (key == null) {
      return null;
    }

    int hash = hash(key);
    LinkedEntry<V>[] tab = table;
    for (LinkedEntry<V> e = tab[hash & (tab.length - 1)]; e != null; e = e.next) {
      String eKey = e.key;
      if (eKey == key || (e.hash == hash && key.equals(eKey))) {
        return e;
      }
    }
    return null;
  }

    public void clear() {
      StringMap.this.clear();
    }
  }

  private static final int seed = new Random().nextInt();
  private static int hash(String key) {
    // Ensuring that the hash is unpredictable and well distributed.
    //
    // Finding unpredictable hash functions is a bit of a dark art as we need to balance
    // good unpredictability (to avoid DoS) and good distribution (for performance).
    //
    // We achieve this by using the same algorithm as the Perl version, but this implementation
    // is being written from scratch by inder who has never seen the
    // Perl version (for license compliance).
    //
    // TODO: investigate http://code.google.com/p/cityhash/ and http://code.google.com/p/smhasher/
    // both of which may have better distribution and/or unpredictability.
    int h = seed;
    for (int i = 0; i < key.length(); ++i) {
      int h2 = h + key.charAt(i);
      int h3 = h2 + h2 << 10; // h2 * 1024
      h = h3 ^ (h3 >>> 6); // h3 / 64
    }

        public final Map.Entry<String, V> next() {
          return nextEntry();
        }

    public Iterator<Entry<String, V>> iterator() {
      return new LinkedHashIterator<Map.Entry<String, V>>() {
        public final Map.Entry<String, V> next() {
          return nextEntry();
        }
      };
    }

    final LinkedEntry<V> nextEntry() {
      LinkedEntry<V> e = next;
      if (e == header) {
        throw new NoSuchElementException();
      }
      next = e.nxt;
      return lastReturned = e;
    }

}