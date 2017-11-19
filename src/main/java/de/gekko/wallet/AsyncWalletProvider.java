package de.gekko.wallet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exchanges.AbstractArbitrageExchange;

/**
 * Class that provides an local image of an exchanges wallet. It is only possible to consume available balances and not to add to them.
 * Asynchronously syncs the local wallet image with the exchange after a specified amount of time or after balances have been consumed.
 * @author Maximilian Pfister
 *
 */
public class AsyncWalletProvider implements Runnable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncWalletProvider.class);
	private final BinarySemaphore walletUpdateSemaphore = new BinarySemaphore(true);
	private final Lock balanceLock = new ReentrantLock();
	private final AbstractArbitrageExchange exchange;
	private Map<Currency, Double> balances = new HashMap<>();
	private Map<Currency, Double> startupBalances = new HashMap<>();
	private boolean stop = false;
	private boolean active = false;
	private long updateInterval = 60; // seconds
	private long lastUpdated = 0;
	private long apiLimit = 1000; // miliseconds
	
	private AsyncWalletProvider(AbstractArbitrageExchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Update routine to sync local wallet image with exchange.
	 */
	@Override
	public void run() {
		active = true;
		// executor service for update interval thread
		ExecutorService signalUpdateService = Executors.newSingleThreadExecutor();
		
		// launch update interval thread
		signalUpdateService.submit(() -> {
			while (!stop) {
				// wait for seconds specified by updateInterval variable
				try {
					Thread.sleep(updateInterval * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// release semaphore to perform wallet update
				walletUpdateSemaphore.release();
			}
		});
		
		while(!stop) {
			
			// wait until semaphore can be acquired 
			try {
				walletUpdateSemaphore.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// wait to prevent running into API limit of exchange
			long currentUpdateInterval = System.currentTimeMillis() - lastUpdated;
			if(currentUpdateInterval < apiLimit) {
				try {
					Thread.sleep(apiLimit - currentUpdateInterval);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			// get wallet from exchange
			Wallet wallet = null;
			try {
				wallet = exchange.fetchWallet();
				lastUpdated = System.currentTimeMillis();
			} catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException | ExchangeException
					| IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			// update local wallet image
			if(wallet != null) {
				updateBalances(wallet);
			}
			
			// Logging / printing wallet info
			info();
		}
		
		// Shut down executor service
		signalUpdateService.shutdown();
		active = false;
	}
	
	/**
	 * Updates local wallet image.
	 * @param wallet
	 */
	private void updateBalances(Wallet wallet) {
		Map<Currency, Balance> walletBalances = wallet.getBalances();
		balanceLock.lock();
		walletBalances.forEach((currency, balance) -> {
			if(balance.getAvailable().doubleValue() > 0) {
				balances.put(currency, balance.getAvailable().doubleValue());
			}
		});
		balanceLock.unlock();
	}
	
	/**
	 * Gets the available amount of the passed currency.
	 * @param currency
	 * @return
	 */
	public double getBalance(Currency currency) {
		if(balances.containsKey(currency)) {
			return balances.get(currency);		
		} else {
			return 0;
		}
	}
	
	/**
	 * Gets all balances.
	 * @return
	 */
	public Map<Currency, Double> getBalances(){
		return balances;
	}
	
	/**
	 * Consumes the specified amount of the passed currency in the local wallet
	 * image, then releases update semaphore to unblock update thread.
	 * @param currency
	 * @param amount
	 */
	public void consumeAmount(Currency currency, double amount) {
		balanceLock.lock();
		double balance = balances.get(currency);
		balance -= amount;
		if(balance < 0) {
			//TODO throw exception
		}
		balances.put(currency, balance);
		balanceLock.unlock();
		walletUpdateSemaphore.release();
	}
	
	/**
	 * Static factory method that creates an AsyncWalletProvider instance and runs it in a new thread.
	 * @param exchange
	 * @return
	 */
	public static AsyncWalletProvider createInstance(AbstractArbitrageExchange exchange) {
		AsyncWalletProvider ret = new AsyncWalletProvider(exchange);
		Thread thread = new Thread(ret);
		thread.start();
		return ret;
	}
	
	/**
	 * Launches update thread.
	 */
	public void start() {
		if(!active) {
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
	 * Forces the AsyncWalletProvider to update its state immediately. 
	 * WARNING: Ignores safety measures intended to prevent running into API limit.
	 */
	public void forceUpdate() {
		lastUpdated = 0;
		walletUpdateSemaphore.release();
	}
	
	/**
	 * Prints/logs the current wallet state and deltas.
	 */
	public void info() {
		LOGGER.info("----- Wallet {} -----", exchange);
		 Map<Currency, Double> deltas = getStartupDeltas();
		balances.forEach((currency, balance) -> {
			String deltaString;
			if(deltas.get(currency) > 0) {
				deltaString = "+" + deltas.get(currency);
			} else {
				deltaString = "" + deltas.get(currency);
			}
			LOGGER.info("[{}] = {} ({}) ", currency, balance, deltaString);
		});		
	}
	
	/**
	 * Gets profits/losses since startup of this AsyncWalletProvider.
	 * @return
	 */
	public Map<Currency, Double> getStartupDeltas(){
		Map<Currency, Double> deltas = new HashMap<>();
		//balanceLock.lock();
		balances.forEach((currency, balance) -> {
			if(startupBalances.containsKey(currency)) {
				deltas.put(currency, balance - startupBalances.get(currency));
			} else {
				// detected new currency
				deltas.put(currency, 0.d);
				startupBalances.put(currency, balance);
			}
		});
		//balanceLock.unlock();
		return deltas;
	}

}