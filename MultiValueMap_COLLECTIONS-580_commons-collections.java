public class MultiValueMap {
            protected Iterator<? extends Entry<K, V>> nextIterator(int count) {
                if ( ! keyIterator.hasNext() ) {
                    return null;
                }
                final K key = keyIterator.next();
                final Transformer<V, Entry<K, V>> transformer = new Transformer<V, Entry<K, V>>() {
                    @Override
                    public Entry<K, V> transform(final V input) {
                        return new Entry<K, V>() {
                            @Override
                            public K getKey() {
                                return key;
                            }
                            @Override
                            public V getValue() {
                                return input;
                            }
                            @Override
                            public V setValue(V value) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
                return new TransformIterator<V, Entry<K, V>>(new ValuesIterator(key), transformer);
            }

        @Override
        public V next() {
            return iterator.next();
        }
    }

    private static class ReflectionFactory<T extends Collection<?>> implements Factory<T>, Serializable {

        private static final long serialVersionUID = 2986114157496788874L;

        private final Class<T> clazz;

        public ReflectionFactory(final Class<T> clazz) {
            this.clazz = clazz;
        }

        @Override
        public T create() {
            try {
                return clazz.newInstance();
            } catch (final Exception ex) {
                throw new FunctorException("Cannot instantiate class: " + clazz, ex);
            }
        }

        private void readObject(ObjectInputStream is) throws IOException, ClassNotFoundException {
            is.defaultReadObject();
            // ensure that the de-serialized class is a Collection, COLLECTIONS-580
            if (clazz != null && !Collection.class.isAssignableFrom(clazz)) {
                throw new UnsupportedOperationException();
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

                    public Entry<K, V> transform(final V input) {
                        return new Entry<K, V>() {
                            @Override
                            public K getKey() {
                                return key;
                            }
                            @Override
                            public V getValue() {
                                return input;
                            }
                            @Override
                            public V setValue(V value) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }

        public T create() {
            try {
                return clazz.newInstance();
            } catch (final Exception ex) {
                throw new FunctorException("Cannot instantiate class: " + clazz, ex);
            }
        }

        public void remove() {
            iterator.remove();
            if (values.isEmpty()) {
                MultiValueMap.this.remove(key);
            }
        }

    @Override
    public boolean removeMapping(final Object key, final Object value) {
        final Collection<V> valuesForKey = getCollection(key);
        if (valuesForKey == null) {
            return false;
        }
        final boolean removed = valuesForKey.remove(value);
        if (removed == false) {
            return false;
        }
        if (valuesForKey.isEmpty()) {
            remove(key);
        }
        return true;
    }

                            @Override
                            public V setValue(V value) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public V getValue() {
                                return input;
                            }

                            public K getKey() {
                                return key;
                            }

        @Override
        public Iterator<V> iterator() {
            final IteratorChain<V> chain = new IteratorChain<V>();
            for (final K k : keySet()) {
                chain.addIterator(new ValuesIterator(k));
            }
            return chain;
        }

}