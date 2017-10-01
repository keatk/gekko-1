package de.gekko;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.arbitrager.Arbitrager;
import de.gekko.exchanges.AbstractArbitrageExchange;
import de.gekko.io.ResourceManager;

public class Main {

	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("Main");
	
	public static void main(String[] args) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {

		/**
		 * Hier das derzeitige CurrencyPair eintragen.
		 */
		CurrencyPair currencyPair = new CurrencyPair(Currency.ETH, Currency.BTC);

		/**
		 * Lade Exchanges aus Configfile und erstelle Arbitrager entsprechend der
		 * Anzahl.
		 */
		List<Arbitrager> listArbitrager = new ArrayList<>();
		List<AbstractArbitrageExchange> listExhanges = ResourceManager.loadConfigFile();

		for (int i = 0; i < listExhanges.size() - 1; i++) {
			for (int j = i + 1; j < listExhanges.size(); j++) {
				Arbitrager arbitrager = new Arbitrager(listExhanges.get(i), listExhanges.get(j), currencyPair);
				listArbitrager.add(arbitrager);
				LOGGER.info("Created new Arbitrager: {}", arbitrager);
			}
		}

		long startTime = System.nanoTime();

		while (true) {
			try {

				for (Arbitrager arbitrager : listArbitrager) {
					arbitrager.updateOrderbooks();
					arbitrager.limitOrderArbitrage();

					Thread.sleep(3000);
				}

			} catch (ConnectException | SocketTimeoutException | UnknownHostException
					| si.mazi.rescu.HttpStatusIOException Exception) {
				System.err.println("Connection Failed. Retry in 30 sec...");
				Thread.sleep(30000);
			}
		}
	}

}
