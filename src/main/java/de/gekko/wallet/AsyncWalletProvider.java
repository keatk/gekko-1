package de.gekko.wallet;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.knowm.xchange.currency.Currency;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exchanges.AbstractArbitrageExchange;

/**
 * Class that provides an local image of an exchanges wallet. It is only possible to consume available balances and not to add to them.
 * Asynchronously syncs the local wallet image with the exchange after a specified amount of time or after balances have been consumed.
 * @author max
 *
 */
public class AsyncWalletProvider implements Runnable {
	
	private final BinarySemaphore walletUpdateSemaphore = new BinarySemaphore(false);
	private AbstractArbitrageExchange exchange;
	private Map<Currency, Double> balances;
	private Lock balanceLock = new ReentrantLock();
	
	public AsyncWalletProvider(AbstractArbitrageExchange exchange) {
		this.exchange = exchange;
	}

	/**
	 * Update routine to sync local wallet image with exchange.
	 */
	@Override
	public void run() {
		try {
			walletUpdateSemaphore.acquire();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		balanceLock.lock();
		updateBalances();
		balanceLock.unlock();
	}
	
	private void updateBalances() {
		
	}
	
	/**
	 * Gets the available amount of the passed currency.
	 * @param currency
	 * @return
	 */
	public double getBalance(Currency currency) {
		return 0;
	}
	
	/**
	 * Consumes the specified amount of the passed currency in the local wallet
	 * image, then releases update semaphore to unblock update thread.
	 * @param currency
	 * @param amount
	 */
	public void consumeAmount(Currency currency, double amount) {
		
	}

}
