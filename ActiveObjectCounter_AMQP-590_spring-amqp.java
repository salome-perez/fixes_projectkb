public class ActiveObjectCounter {
	public void add(T object) {
		CountDownLatch lock = new CountDownLatch(1);
		this.locks.putIfAbsent(object, lock);
	}

	public int getCount() {
		return this.locks.size();
	}

	public void release(T object) {
		CountDownLatch remove = this.locks.remove(object);
		if (remove != null) {
			remove.countDown();
		}
	}

	public boolean await(Long timeout, TimeUnit timeUnit) throws InterruptedException {
		long t0 = System.currentTimeMillis();
		long t1 = t0 + TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
		while (System.currentTimeMillis() <= t1) {
			if (this.locks.isEmpty()) {
				return true;
			}
			Collection<T> objects = new HashSet<T>(this.locks.keySet());
			for (T object : objects) {
				CountDownLatch lock = this.locks.get(object);
				if (lock==null) {
					continue;
				}
				t0 = System.currentTimeMillis();
				if (lock.await(t1 - t0, TimeUnit.MILLISECONDS)) {
					this.locks.remove(object);
				}
			}
		}
		return false;
	}

	public void reset() {
		this.locks.clear();
	}

}