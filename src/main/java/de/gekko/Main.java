package de.gekko;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import de.gekko.arbitrager.Arbitrager;
import de.gekko.exchanges.AbstractArbitrageExchange;
import de.gekko.io.ResourceManager;

public class Main {

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
				listArbitrager.add(new Arbitrager(listExhanges.get(i), listExhanges.get(j), currencyPair));
			}
		}

		while (true) {
			try {
				for (Arbitrager arbitrager : listArbitrager) {
					arbitrager.limitOrderArbitrage();

					Thread.sleep(3000);
				}
			} catch (Exception e) {
				System.err.println("Connection Failed. Retry in 30 sec...");
				Thread.sleep(30000);
			}
		}
	}

}
