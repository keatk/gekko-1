package de.gekko.wallet;

import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.knowm.xchange.currency.Currency;

import de.gekko.concurrency.BinarySemaphore;
import de.gekko.exchanges.AbstractArbitrageExchange;

public class AsyncWalletProvider implements Runnable {
	
	private final BinarySemaphore walletUpdateSemaphore = new BinarySemaphore(false);
	private AbstractArbitrageExchange exchange;
	private Map<Currency, Double> balances;
	private Lock balanceLock = new ReentrantLock();
	
	public AsyncWalletProvider(AbstractArbitrageExchange exchange) {
		this.exchange = exchange;
	}

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
	
	public void getBalance(Currency currency) {
		
	}
	
	public void consumeAmount(Currency currency, double amount)  {
		
	}

}
