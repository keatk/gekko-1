package de.gekko;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;

import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

import de.gekko.arbitrager.Arbitrager;
import de.gekko.exchanges.AbstractArbitrageExchange;
import de.gekko.io.ResourceManager;

public class Main {

	public static void main(String[] args) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");

		List<AbstractArbitrageExchange> listExhanges = ResourceManager.loadConfigFile();

		Arbitrager arb1 = new Arbitrager(listExhanges.get(0), listExhanges.get(1), listExhanges.get(0).getCurrenyPair());

		long startTime = System.nanoTime();

		while (true) {

			try {
				arb1.updateOrderbooks();
				arb1.arbTest1();
				arb1.arbTest2();

				Thread.sleep(3000);

			} catch (ConnectException | SocketTimeoutException | UnknownHostException
					| si.mazi.rescu.HttpStatusIOException Exception) {
				System.err.println("Connection Failed. Retry in 30 sec...");
				Thread.sleep(30000);
			}
		}
	}

}
