package de.gekko.websocketNew;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.knowm.xchange.dto.trade.LimitOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exception.CurrencyMismatchException;
import de.gekko.websocket.UpdateableOrderbook;

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
	private boolean active;
	private boolean stop;

	private Set<UpdateableOrderbook> subscribers = new HashSet<>();
	private ExecutorService broadcastExecutorService = Executors.newFixedThreadPool(20); // TODO USE CACHED EXECUTOR																					// POOLS

	private final TreeMap<BigDecimal, LimitOrder> asks = new TreeMap<>();
	private final TreeMap<BigDecimal, LimitOrder> bids = new TreeMap<>((k1, k2) -> -k1.compareTo(k2));
	
	private ChannelHandler() {
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		while (!stop) {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	/**
	 * Static factory method that creates an ChannelHandler instance and runs it in
	 * a new thread.
	 * 
	 * @param exchange
	 * @param basePair
	 * @param crossPair1
	 * @param crossPair2
	 * @return
	 * @throws IOException
	 * @throws CurrencyMismatchException
	 */
	public static ChannelHandler createInstance() {
		ChannelHandler channelHandler = new ChannelHandler();
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
	public void feedUpdate(ChannelHandlerUpdate update) {
		LOGGER.info("Received: " + update.toString());
	}

}
