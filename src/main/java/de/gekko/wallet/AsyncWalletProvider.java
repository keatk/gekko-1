package de.gekko.wallet;

import java.io.IOException;
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
	private final BinarySemaphore walletUpdateSemaphore = new BinarySemaphore(false);
	private AbstractArbitrageExchange exchange;
	private Map<Currency, Double> balances;
	private Lock balanceLock = new ReentrantLock();
	private boolean stop = false;
	private long updateInterval = 1; //seconds
	
	private AsyncWalletProvider(AbstractArbitrageExchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Update routine to sync local wallet image with exchange.
	 */
	@Override
	public void run() {
		// executor service for update interval thread
		ExecutorService signalUpdateService = Executors.newFixedThreadPool(1);
		
		while(!stop) {
			// launch update interval thread
			signalUpdateService.submit(() -> {
				//wait for seconds specified by updateInterval variable
				try {
					Thread.sleep(updateInterval*1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				// release semaphore to perform wallet update
				walletUpdateSemaphore.release();
			});
			
			// wait until semaphore can be acquired 
			try {
				walletUpdateSemaphore.acquire();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// get wallet from exchange
			Wallet wallet = null;
			try {
				wallet = exchange.fetchWallet();
			} catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException | ExchangeException
					| IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			// update local wallet image
			if(wallet != null) {
				updateBalances(wallet);
			}
		}

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
	 * Stops update thread.
	 */
	public void stop() {
		stop = true;
	}
	
	/**
	 * Prints/logs the current wallet state and profits.
	 */
	public void info() {
		
	}
	
	/**
	 * Gets profits since startup of this AsyncWalletProvider.
	 * @return
	 */
	public Map<Currency, Double> getProfits(){
		return null;
	}

}
