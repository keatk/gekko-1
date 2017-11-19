package de.gekko.websocketNew;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.PriorityQueue;

import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exception.CurrencyMismatchException;
import de.gekko.websocketNew.pojo.ExchangeStateUpdate;
import de.gekko.websocketNew.pojo.OrderUpdate;

/**
 * Concurrent channel handler that processes orderbook updates fed by BittrexWebsocket.
 * @author Maximilian Pfister
 *
 */
public class ChannelHandler implements Runnable {
	
	/* constants */
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BittrexWebsocket.class);
	
	/* variables */

	private final BinarySemaphore processUpdateSem = new BinarySemaphore(false);
	private boolean active = false;
	private boolean stop = false;
	private int keepAliveTimeout = 30; // seconds
	private long lastKeepAlive;
	private CurrencyPair currencyPair;
	
	private Lock queueLock = new ReentrantLock();
	private PriorityQueue<ExchangeStateUpdate> queue = new PriorityQueue<>((u1, u2) -> {
		if(u1.getNounce() > u2.getNounce()) {
			return 1;
		} else {
			return -1;
		}
	});
	private long nounce;
	private boolean initalStateReceived = false;
	private OrderBook orderBook = null;

	private Set<ReceiveOrderbook> subscribers = new HashSet<>();
	private ExecutorService broadcastExecutorService = Executors.newCachedThreadPool();
	private ExecutorService keepAliveExecutor;

	private final TreeMap<BigDecimal, LimitOrder> asks = new TreeMap<>();
	private final TreeMap<BigDecimal, LimitOrder> bids = new TreeMap<>((k1, k2) -> -k1.compareTo(k2));
	
	/* constructors */
	
	private ChannelHandler(CurrencyPair currencyPair) {
		this.currencyPair = currencyPair;
	}

	/**
	 * Update processing routine.
	 */
	@Override
	public void run() {
		active = true;
		//TODO Keep alive counter in seperatem thread
		keepAliveExecutor = Executors.newSingleThreadExecutor();
		
		// launch keep alive thread
		keepAliveExecutor.submit(() -> {
			while (!stop) {
				// wait for seconds specified by keepAliveTimeout
				try {
					Thread.sleep(keepAliveTimeout * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				if((System.currentTimeMillis() - lastKeepAlive) > keepAliveTimeout * 1000) {
					LOGGER.info("Keep alive timeout[{}]", currencyPair);
					System.exit(1);
				};
			}
		});
		
		// TODO Auto-generated method stub
		while (!stop) {
			try {
				processUpdateSem.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// reset orderbook
			orderBook = null;
			
			// Wait for inital state
			if(!initalStateReceived) {
				queueLock.lock();
				// Get inital state
				ExchangeStateUpdate initalState = queue.stream().filter(update -> update.getMarketName() == null).findFirst().orElse(null);
				// Process inital state
				if(initalState != null) {
					LOGGER.info("Processing inital exchange state [{}]", currencyPair);
					processInitalState(initalState);
					// Deal with rest of queue
					if(queue.size() == 1) {
						queue.clear();
					} else {
						LOGGER.info("OHNOOOOO");
						queue.forEach(update -> processUpdate(update));
					}
				}
				queueLock.unlock();
			} else {
				LOGGER.info("Processing Update [{}]", currencyPair);
				queueLock.lock();
				// Process updates
				queue.forEach(update -> processUpdate(update));
				// Clear queue
				queue.clear();
				// Broadcast new orderbook
				broadcastOrderbook();
				queueLock.unlock();
			}
			
		}
		active = false;
	}
	
	/**
	 * Process inital exchange state.
	 * @param exchangeUpdate
	 * @throws InterruptedException 
	 */
	private void processInitalState(ExchangeStateUpdate exchangeUpdate)  {
		initalStateReceived = true;
//        nounce = exchangeUpdate.getNounce();
        bids.clear();
        asks.clear();
        exchangeUpdate.getBuys().forEach(buy -> bids.put(buy.getRate(), new LimitOrder(Order.OrderType.BID, buy.getQuantity(), currencyPair, null, null, buy.getRate())));
        exchangeUpdate.getSells().forEach(sell -> asks.put(sell.getRate(), new LimitOrder(Order.OrderType.ASK, sell.getQuantity(), currencyPair, null, null, sell.getRate())));
        queue.forEach(update -> {
        		if(update.getMarketName() != null) {
            		processUpdate(update);
        		}
        });
        queue.clear();
	}
	
	/**
	 * Process exchange state update.
	 * @param exchangeUpdate
	 */
	private void processUpdate(ExchangeStateUpdate exchangeUpdate) {
		if(nounce == 0) {
			nounce = exchangeUpdate.getNounce();
		} else {
			if (exchangeUpdate.getNounce() <= nounce) {
				LOGGER.info("MISSING DATA");
				LOGGER.info("Current nounce: {}, update nounce: {}", nounce, exchangeUpdate.getNounce() );
				System.exit(1);
				return;
			}
			if (exchangeUpdate.getNounce() - nounce == 1) {
				nounce++;
			} else {
				LOGGER.warn("Missing data, going to resubscribe " + currencyPair);
				//TODO RESUBSCRIBE
				LOGGER.info("Current nounce: {}, update nounce: {}", nounce, exchangeUpdate.getNounce() );
				System.exit(1);
				return;
			}
		}


		// Processes bid updates
		BiConsumer<OrderUpdate, TreeMap<BigDecimal, LimitOrder>> bidsProcessor = (update, bids) -> {
			switch (update.getType()) {
			case ADD:
			case UPDATE:
				bids.put(update.getRate(), new LimitOrder(Order.OrderType.BID, update.getQuantity(), currencyPair, null, null, update.getRate()));
				break;
			case REMOVE:
				bids.remove(update.getRate());
				break;
			default:
				throw new RuntimeException("Unknown update type " + update.getRate()); // should never happen
			}
		};

		// Processes ask updates
		BiConsumer<OrderUpdate, TreeMap<BigDecimal, LimitOrder>> asksProcessor = (update, asks) -> {
			switch (update.getType()) {
			case ADD:
			case UPDATE:
				asks.put(update.getRate(), new LimitOrder(Order.OrderType.ASK, update.getQuantity(), currencyPair, null, null, update.getRate()));
				break;
			case REMOVE:
				asks.remove(update.getRate());
				break;
			default:
				throw new RuntimeException("Unknown update type " + update.getType()); // should never happen
			}
		};

		// Feed updates to processors 
		exchangeUpdate.getBuys().forEach(update -> bidsProcessor.accept(update, bids));
		exchangeUpdate.getSells().forEach(update -> asksProcessor.accept(update, asks));

	}
	
	/**
	 * Broadcasts current orderbook to all subscribers.
	 */
    public void broadcastOrderbook() {
    		OrderBook orderBook = getOrderBook();
    		OrderBookUpdate orderBookUpdate = new OrderBookUpdate(currencyPair, orderBook);
		if(!subscribers.isEmpty()) {
			subscribers.forEach(subscriber -> broadcastExecutorService.submit(
					() -> {subscriber.receiveUpdate(orderBookUpdate);
					}));
		}
	}
    
    /**
     * Gets current orderBook.
     * @return
     */
	public OrderBook getOrderBook() {
		OrderBook old = orderBook;
		if (old != null) {
			return old;
		}

		orderBook = new OrderBook(new Date(), new ArrayList<>(asks.values()), new ArrayList<>(bids.values()));
		return orderBook;
	}

	/**
	 * Static factory method that creates an ChannelHandler instance and runs it in a new thread.
	 * @param exchange
	 * @param basePair
	 * @param crossPair1
	 * @param crossPair2
	 * @return
	 * @throws IOException
	 * @throws CurrencyMismatchException
	 */
	public static ChannelHandler createInstance(CurrencyPair currencyPair) {
		ChannelHandler channelHandler = new ChannelHandler(currencyPair);
		Thread thread = new Thread(channelHandler);
		thread.start();
		return channelHandler;
	}

	/**
	 * Launches update thread.
	 */
	public void start() {
		if (!active) {
			Thread thread = new Thread(this);
			thread.start();
		}
	}

	/**
	 * Stops update thread.
	 */
	public void stop() {
		stop = true;
	}
	
	/**
	 * Adds update to channelHandler.
	 * @param update
	 */
	public void feedUpdate(ExchangeStateUpdate update) {
		queueLock.lock();
		queue.add(update);
		queueLock.unlock();
		processUpdateSem.release();
		singalAlive();
	}
	
	/**
	 * Adds a subscriber to this ChannelHandler.
	 * @param updateableObject
	 */
	public void addSubscriber(ReceiveOrderbook updateableObject) {
		subscribers.add(updateableObject);
	}

	/**
	 * Removes a subsciber to this ChannelHandler.
	 * @param updateableObject
	 */
	public void removeSubscriber(ReceiveOrderbook updateableObject) {
		subscribers.remove(updateableObject);
	}
	
	public void singalAlive() {
		lastKeepAlive = System.currentTimeMillis();
	}

}
