package de.gekko.concurrency;

import java.util.concurrent.Semaphore;

public class BinarySemaphore {

	private final Semaphore countingSemaphore;

	public BinarySemaphore(boolean available) {
		if (available) {
			countingSemaphore = new Semaphore(1, true);
		} else {
			countingSemaphore = new Semaphore(0, true);
		}
	}

	public void acquire() throws InterruptedException {
		countingSemaphore.acquire();
	}

	public synchronized void release() {
		if (countingSemaphore.availablePermits() != 1) {
			countingSemaphore.release();
		}
	}

}
