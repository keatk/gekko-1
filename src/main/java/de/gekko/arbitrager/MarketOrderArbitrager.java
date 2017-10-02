package de.gekko.arbitrager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.exchanges.AbstractArbitrageExchange;

public class MarketOrderArbitrager implements Runnable {

	private static boolean DEBUG = true;
	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("MarketOrderArbitrager");
	private CurrencyPair currencyPair;
	private Map<String, AbstractArbitrageExchange> mapExchanges;
	private String nameExchangeOne;
	private String nameExchangeTwo;
	/**
	 * Speichert den Executor Service für Netzwerkanfragen auf die Exchanges.
	 */
	private ExecutorService networkExecutorService = Executors.newFixedThreadPool(2);
	/**
	 * Speichert die Flag, die bestimmt ob Wallets aktualisiert werden. Zum
	 * Programmstart und nachdem erfolgreich Trades durchgeführt, wird die Flag auf
	 * true gesetzt.
	 */
	private boolean updateWallets;

	public MarketOrderArbitrager(CurrencyPair currencyPair, AbstractArbitrageExchange exchangeOne,
			AbstractArbitrageExchange exchangeTwo) {
		this.currencyPair = currencyPair;
		nameExchangeOne = exchangeOne.toString();
		nameExchangeTwo = exchangeTwo.toString();
		mapExchanges = new HashMap<>();
		mapExchanges.put(nameExchangeOne, exchangeOne);
		mapExchanges.put(nameExchangeTwo, exchangeTwo);

		/**
		 * Einmal benötigte Daten können hier erfragt werden.
		 */
		getExchangeOne().setTradingFee(getExchangeOne().fetchTradingFee(currencyPair));
		getExchangeTwo().setTradingFee(getExchangeTwo().fetchTradingFee(currencyPair));

		getExchangeOne().setMinimumAmount(getExchangeOne().fetchMinimumAmount(currencyPair));
		getExchangeTwo().setMinimumAmount(getExchangeTwo().fetchMinimumAmount(currencyPair));

		updateWallets = true;
	}

	/**
	 * Berechnet prozentuale Arbitrage. Dabei werden die Fees der Exchanges
	 * berücksichtigt. Theoretisch ist ein Arbitrage profitabel, sobald diese
	 * Funktion > 0 zurück gibt.
	 * 
	 * @param priceAsk
	 * @param priceBid
	 * @return die Arbitrage unter Berücksichtigung der Fees.
	 */
	private double calculateArbitragePercentage(double priceAsk, double priceBid, double takerFeeAsk,
			double takerFeeBid) {
		double grossMargin = 1 - priceAsk / priceBid;
		return (grossMargin - takerFeeAsk - takerFeeBid) * 100;
	}

	private AbstractArbitrageExchange getExchangeOne() {
		return mapExchanges.get(nameExchangeOne);
	}

	private AbstractArbitrageExchange getExchangeTwo() {
		return mapExchanges.get(nameExchangeTwo);
	}

	private void performMarketOrderArbitrage(AbstractArbitrageExchange askExchange,
			AbstractArbitrageExchange bidExchange) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		LOGGER.info("Checking for Arbitrage opportunity...");

		// ASK-Exchange
		double priceAskExchange = askExchange.getTicker().getAsk().doubleValue();
		LOGGER.info("[{}, ASK] Price: {}", askExchange, priceAskExchange);

		// BID-Exchange
		double priceBidExchange = bidExchange.getTicker().getBid().doubleValue();
		LOGGER.info("[{}, BID] Price: {}", bidExchange, priceBidExchange);

		// Arbitrage berechnen
		double arbitragePercentage = calculateArbitragePercentage(priceAskExchange, priceBidExchange,
				askExchange.getTakerFee(), bidExchange.getTakerFee());
		LOGGER.info("[{} -> {}] Arbitrage: {}", askExchange.toString(), bidExchange.toString(),
				String.format("%.8f", arbitragePercentage));

		// Wenn Arbitrage positiv, führe Trade durch
		if (arbitragePercentage > 0) {
			// final double potentialAskAmount =
			// exchange1Wallet.getBalance(currencyPair.base).getAvailable().doubleValue();

			// TODO Tradeamount ausloten implementieren. Falls maximaler Amount nicht geht
			// muss geschaut werden was das Maximale ist, was die Bid und Ask Amounts
			// zulassen. Achtung: erfordert teilweise umrechnung zwischen Coins.
			// double tradeAmount = 0;
			// if (btceAmount > maxTradeLimit) {
			// tradeAmountETH = maxTradeLimit;
			// } else {
			// tradeAmountETH = btceAmount;
			// }
			/**
			 * TODO: Minimales TradeLimit der Exchanges kann in der
			 * AbstractArbitrageExchange-Klasse erfragt werden.
			 */
			final double tradeAmount = 0;

			if (DEBUG == false) {
				// TODO: Ausgabe/Logging der trades implementieren

				// Konstanten für Zugriff aus anonymer Klasse bzw. Lambda
				final double exchange1Price = priceAskExchange;
				final double exchange2Price = priceBidExchange;

				// Perform Trades - Multi-threaded Implementation

				// TODO: genaue Tradeamounts richtig berechnen, bisher nur Platzhalter. Dazu
				// gehört umrechnung der Währungen und Berücksichtigung der Fees. (Achtung
				// Brainfuck :D)
				// TODO: Ziel sollte am Ende sein dass sich die Kontostände nur in richtung der
				// Arbitrage verändern, also ein plus auf einer Währungsseite rauskommt.
				// TODO: hier Amounts anpassen, zwischen Coins umrechnen und Fees
				// berücksichtigen.
				Callable<String> callable_exchange1Trade = () -> {
					return askExchange.placeLimitOrderBid(currencyPair, exchange1Price, tradeAmount);
				};

				// TODO: hier Amounts anpassen, zwischen Coins umrechnen und Fees
				// berücksichtigen.
				Callable<String> callable_exchange2Trade = () -> {
					return bidExchange.placeLimitOrderBid(currencyPair, exchange2Price, tradeAmount);
				};

				Future<String> future_exchange1Order = networkExecutorService.submit(callable_exchange1Trade);
				Future<String> future_exchange2Order = networkExecutorService.submit(callable_exchange2Trade);

				String orderID_exchange1 = "";
				String orderID_exchange2 = "";
				try {
					orderID_exchange1 = future_exchange1Order.get();
					LOGGER.info("Order1 Placed. ID: {}", orderID_exchange1);
					orderID_exchange2 = future_exchange2Order.get();
					LOGGER.info("Order2 Placed. ID: {}", orderID_exchange2);

				}
				// Falls irgendetwas schief geht, Trades wenn möglich abbrechen. Beendet
				// Programm weil dieser Fehler nicht auftreten sollte.
				// TODO: exceptions genauer definieren, z.B. unterscheidung zwischen
				// Netzwerkfehlern (nicht fatal) und zu wenig Funds (fatal, weil das vorher
				// sichergestellt werden sollte)
				catch (Exception e) {
					e.printStackTrace();
					boolean exchange1_cancelled = getExchangeOne().cancelOrder(orderID_exchange1);
					boolean exchange2_cancelled = getExchangeOne().cancelOrder(orderID_exchange1);
					System.err.println("Fatal trade error, terminating application.");
					System.exit(1);
				}

				// Nach Tradeaktivität ändert sich Kontostand, deswegen true.
				updateWallets = true;
			}

		}
	}

	@Override
	public void run() {
		if (updateWallets) {
			// Wallets aktualisieren
			updateWallet(getExchangeOne(), getExchangeTwo());
			updateWallets = false;
		}

		updateOrderbook(getExchangeOne(), getExchangeTwo());

		try {
			performMarketOrderArbitrage(getExchangeOne(), getExchangeTwo());
			performMarketOrderArbitrage(getExchangeTwo(), getExchangeOne());
		} catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException | ExchangeException
				| IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Aktualisiert das OrderBook für einen gegebenen Exchange.
	 * 
	 * @param exchangeOne
	 */
	void updateOrderbook(AbstractArbitrageExchange exchangeOne, AbstractArbitrageExchange exchangeTwo) {
		LOGGER.info("Updating Orderbooks for {} and {}", exchangeOne, exchangeTwo);

		// Multi-threaded Implementation
		Callable<OrderBook> callableOrderbook1 = () -> {
			return exchangeOne.fetchOrderbook(currencyPair);
		};

		Callable<OrderBook> callableOrderbook2 = () -> {
			return exchangeTwo.fetchOrderbook(currencyPair);
		};

		Future<OrderBook> futureOrderbook1 = networkExecutorService.submit(callableOrderbook1);
		Future<OrderBook> futureOrderbook2 = networkExecutorService.submit(callableOrderbook2);

		try {
			exchangeOne.setOrderBook(futureOrderbook1.get());
			exchangeTwo.setOrderBook(futureOrderbook2.get());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Aktualisiert den Ticker für eine gegebene Exchange.
	 * 
	 * @param exchangeOne
	 */
	void updateTicker(AbstractArbitrageExchange exchangeOne, AbstractArbitrageExchange exchangeTwo) {
		LOGGER.info("Updating Ticker for {} and {}", exchangeOne, exchangeTwo);

		// Multi-threaded Implementation
		Callable<Ticker> callableTicker1 = () -> {
			return exchangeOne.fetchTicker(currencyPair);
		};

		Callable<Ticker> callableTicker2 = () -> {
			return exchangeTwo.fetchTicker(currencyPair);
		};

		Future<Ticker> futureTicker1 = networkExecutorService.submit(callableTicker1);
		Future<Ticker> futureTicker2 = networkExecutorService.submit(callableTicker2);

		try {
			exchangeOne.setTicker(futureTicker1.get());
			exchangeTwo.setTicker(futureTicker2.get());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Aktualisiert den Wallet für einen gegeben Exchange.
	 * 
	 * @param exchangeOne
	 */
	void updateWallet(AbstractArbitrageExchange exchangeOne, AbstractArbitrageExchange exchangeTwo) {
		LOGGER.info("Updating Wallets for {} and {}", exchangeOne, exchangeTwo);

		// Multi-threaded Implementation
		Callable<Wallet> callableExchange1Wallet = () -> {
			return exchangeOne.fetchWallet();
		};

		Callable<Wallet> callableExchange2Wallet = () -> {
			return exchangeTwo.fetchWallet();
		};

		Future<Wallet> futureExchange1Wallets = networkExecutorService.submit(callableExchange1Wallet);
		Future<Wallet> futureExchange2Wallets = networkExecutorService.submit(callableExchange2Wallet);

		try {
			exchangeOne.setWallet(futureExchange1Wallets.get());
			exchangeTwo.setWallet(futureExchange2Wallets.get());
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
