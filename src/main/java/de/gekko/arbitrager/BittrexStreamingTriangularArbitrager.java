package de.gekko.arbitrager;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exception.CurrencyMismatchException;
import de.gekko.exchanges.BittrexArbitrageExchange;
import de.gekko.wallet.AsyncWalletProvider;
import de.gekko.websocket.BittrexWebsocket;
import de.gekko.websocket.OrderBookUpdate;
import de.gekko.websocket.ReceiveOrderbook;

/**
 * Class that performs triangular arbitrage (aka. inter market arbitrage) on the Bittrex exchange.
 * Uses websockets and multithreading to achieve fast response times.
 * @author Maximilian Pfister
 *
 */
public class BittrexStreamingTriangularArbitrager extends TriangularArbitrager implements Runnable, ReceiveOrderbook {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexStreamingTriangularArbitrager.class);
	
	private boolean stop = false;
	private boolean active = false;
	private final BinarySemaphore processUpdateSem = new BinarySemaphore(false);
	Map<CurrencyPair, PriorityQueue<OrderBook>> orderBookQueues = new HashMap<>();	
	Map<CurrencyPair, ReentrantLock> locks = new HashMap<>();

	private BittrexStreamingTriangularArbitrager(BittrexArbitrageExchange exchange, AsyncWalletProvider walletProvider, CurrencyPair basePair,
			CurrencyPair crossPair1, CurrencyPair crossPair2, double MAX_TRADE_AMOUNT) throws IOException, CurrencyMismatchException, URISyntaxException, InterruptedException {
		super(exchange, walletProvider, basePair, crossPair1, crossPair2, MAX_TRADE_AMOUNT);
		
		BittrexWebsocket bittrexWebsocket = BittrexWebsocket.getInstance();
		
		Comparator<OrderBook> byTimeStamp = (orderBook1, orderBook2) -> {
			if(orderBook1.getTimeStamp() != null && orderBook1.getTimeStamp() != null) {
				if(orderBook1.getTimeStamp().before(orderBook2.getTimeStamp())) {
					return 1;
				} else {
					return -1;
				}
			} else {
				return 0;
			}

		};
		
		getCurrencyPairs().forEach(currencyPair -> {
			locks.put(currencyPair, new ReentrantLock());
			orderBookQueues.put(currencyPair, new PriorityQueue<>(byTimeStamp));
			try {
				bittrexWebsocket.registerSubscriber(currencyPair, this);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}

	/**
	 * Main arbitrage routine.
	 */
	@Override
	public void run() {
		active = true;
		Map<CurrencyPair, OrderBook> orderBooks = new HashMap<>();
		int arbitCounter = 0;
		
		while(!stop) {
			try {
				// wait for updates
				processUpdateSem.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			orderBookQueues.forEach((currencyPair, queue) -> {
				// lock access to queue for thread safe clearing
				locks.get(currencyPair).lock();
				// if update is available
				if(queue.peek() != null) {
					// get most recently updated orderbook
					orderBooks.put(currencyPair, queue.poll());
					// clear queue because older updates are irrelevant
					queue.clear();
				}
				// done editing queue, release lock
				locks.get(currencyPair).unlock();
			});
			
			if(orderBooks.containsKey(getBasePair()) && orderBooks.containsKey(getCrossPair1()) && orderBooks.containsKey(getCrossPair2())) {
				try {
					if(triangularArbitrageAskBid(orderBooks.get(getBasePair()), orderBooks.get(getCrossPair1()), orderBooks.get(getCrossPair2()))){
						arbitCounter++;
					}
					if(triangularArbitrageBidAsk(orderBooks.get(getBasePair()), orderBooks.get(getCrossPair1()), orderBooks.get(getCrossPair2()))){
						arbitCounter++;
					}
				} catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException | ExchangeException
						| IOException | InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				LOGGER.info("Number of Arbitrage Chances: {}", arbitCounter);
			}
		}
		active = false;
	}
	
	/**
	 * Thread safe updating of orderbooks.
	 */
	@Override
	public void receiveUpdate(OrderBookUpdate orderBookUpdate) {
		// acquire lock for specific orderbook
		locks.get(orderBookUpdate.getCurrencyPair()).lock();
		// push updated orderbook onto queue
		orderBookQueues.get(orderBookUpdate.getCurrencyPair()).add(orderBookUpdate.getOrderBook());
		// release the acquired lock
		locks.get(orderBookUpdate.getCurrencyPair()).unlock();
		// release update semaphore to start processing updates in processor thread
		processUpdateSem.release();
	}
	
	/**
	 * Static factory method that creates an BittrexStreamingTriangularArbitrager instance and runs it in a new thread.
	 * @param exchange
	 * @param basePair
	 * @param crossPair1
	 * @param crossPair2
	 * @return
	 * @throws IOException
	 * @throws CurrencyMismatchException
	 * @throws InterruptedException 
	 * @throws URISyntaxException 
	 */
	public static BittrexStreamingTriangularArbitrager createInstance(BittrexArbitrageExchange exchange, AsyncWalletProvider walletProvider, CurrencyPair basePair,
			CurrencyPair crossPair1, CurrencyPair crossPair2, double MAX_TRADE_AMOUNT) throws IOException, CurrencyMismatchException, URISyntaxException, InterruptedException {
		BittrexStreamingTriangularArbitrager arbitrager = new BittrexStreamingTriangularArbitrager(exchange, walletProvider, basePair, crossPair1, crossPair2, MAX_TRADE_AMOUNT);
		Thread thread = new Thread(arbitrager);
		thread.start();
		return arbitrager;
	}
	
	/**
	 * Launches arbitrage thread.
	 */
	public void start() {
		if(!active) {
			Thread thread = new Thread(this);
			thread.start();
		}
	}
	
	/**
	 * Stops arbitrage thread.
	 */
	public void stop() {
		stop = true;
	}

}
