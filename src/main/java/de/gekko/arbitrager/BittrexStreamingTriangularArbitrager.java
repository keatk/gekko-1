package de.gekko.arbitrager;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exception.CurrencyMismatchException;
import de.gekko.exchanges.BittrexArbitrageExchange;
import de.gekko.websocket.BittrexWebsocket;
import de.gekko.websocket.OrderBookUpdate;
import de.gekko.websocket.Updateable;


public class BittrexStreamingTriangularArbitrager extends TriangularArbitrager implements Runnable, Updateable {
	
	private boolean stop = false;
	private final BinarySemaphore processUpdateSem = new BinarySemaphore(false);
	Map<CurrencyPair, PriorityQueue<OrderBook>> orderBooks = new HashMap<>();	
	Map<CurrencyPair, ReentrantLock> locks = new HashMap<>();

	public BittrexStreamingTriangularArbitrager(BittrexArbitrageExchange exchange, CurrencyPair basePair,
			CurrencyPair crossPair1, CurrencyPair crossPair2) throws IOException, CurrencyMismatchException {
		super(exchange, basePair, crossPair1, crossPair2);
		
		Comparator<OrderBook> byTimeStamp = (orderBook1, orderBook2) ->{
			if(orderBook1.getTimeStamp() != null && orderBook1.getTimeStamp() != null) {
				if(orderBook1.getTimeStamp().before(orderBook2.getTimeStamp())) {
					return -1;
				} else {
					return 1;
				}
			} else {
				return 0;
			}

		};
		BittrexWebsocket bittrexWS = new BittrexWebsocket();
		
		getCurrencyPairs().forEach(currencyPair -> {
			locks.put(currencyPair, new ReentrantLock());
			orderBooks.put(currencyPair, new PriorityQueue<>(byTimeStamp));
			try {
				bittrexWS.registerSubscriber(currencyPair, this);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}

	@Override
	public void run() {
		while(!stop) {
			try {
				processUpdateSem.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			locks.values().forEach(lock -> lock.lock());
			System.out.println("woop");
			locks.values().forEach(lock -> lock.unlock());
		}
	}
	
	/**
	 * Thread safe updating of orderbooks.
	 */
	@Override
	public void receiveUpdate(OrderBookUpdate orderBookUpdate) {
		// aquire lock for specific orderbook
		locks.get(orderBookUpdate.getCurrencyPair()).lock();
		// push updated orderbook onto queue
		orderBooks.get(orderBookUpdate.getCurrencyPair()).add(orderBookUpdate.getOrderBook());
		// release the aquired lock
		locks.get(orderBookUpdate.getCurrencyPair()).unlock();
		// release update semaphore to start processing updates in processor thread
		processUpdateSem.release();
	}

}
