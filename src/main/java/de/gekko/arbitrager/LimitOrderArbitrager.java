package de.gekko.arbitrager;

import java.io.IOException;
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

public class LimitOrderArbitrager {

	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("Arbitrager");

	/**
	 * Speichert das CurrencyPair, das getraded wird.
	 */
	private CurrencyPair currencyPair;

	/**
	 * Speichert die DEBUG-Flag. Bei True erfolgen keine echten Transaktionen, dafür
	 * Output in der Konsole.
	 */
	private boolean DEBUG = true;

	/**
	 * Speichert den ersten Exchange.
	 */
	private AbstractArbitrageExchange exchange1;

	/**
	 * Speichert den zweiten Exchange.
	 */
	private AbstractArbitrageExchange exchange2;

	/**
	 * Speichert den Executor Service für Netzwerkanfragen auf die Exchanges.
	 */
	private ExecutorService networkExecutorService = Executors.newFixedThreadPool(2);

	/**
	 * Speichert die Flag, die bestimmt ob Wallets aktualisiert werden. Zum
	 * Programmstart und nachdem erfolgreich Trades durchgeführt, wird die Flag auf
	 * true gesetzt.
	 */
	private boolean updateWallets = true;

	public LimitOrderArbitrager(AbstractArbitrageExchange exchange1, AbstractArbitrageExchange exchange2,
			CurrencyPair currencyPair) throws IOException {
		this.exchange1 = exchange1;
		this.exchange2 = exchange2;
		this.currencyPair = currencyPair;

		/**
		 * Einmal benötigte Daten können hier erfragt und gesetzt werden.
		 */
		exchange1.setTradingFee(exchange1.fetchTradingFee(currencyPair));
		exchange2.setTradingFee(exchange2.fetchTradingFee(currencyPair));

		exchange1.setMinimumAmount(exchange1.fetchMinimumAmount(currencyPair));
		exchange2.setMinimumAmount(exchange2.fetchMinimumAmount(currencyPair));
		// TODO: sanity checks for currency mismatch
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
	private double calculateArbitragePercentage(double priceAsk, double priceBid, double tradingFeeExchange1,
			double tradingFeeExchange2) {
		double grossMargin = 1 - priceAsk / priceBid;
		return (grossMargin - tradingFeeExchange1 - tradingFeeExchange2) * 100;
	}

	/**
	 * Aktualisiert zunächst die Wallets der Exchages bei Bedarf. Dann werden die
	 * Ticker der Exchanges aktualisiert. Zuletzt wird auf eine Arbitragemöglichkeit
	 * geprüft und ggf. durchgeführt.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void limitOrderArbitrage() {
		if (updateWallets) {
			// Wallets aktualisieren
			updateWallet(exchange1, exchange2);
			updateWallets = false;
		}

		// Tickets aktualisieren
		updateOrderbook(exchange1, exchange2);

		// Arbitrage prüfen und ggf. durchführen
		try {
			oneWay_limitOrderArbitrage(exchange1, exchange2);
			oneWay_limitOrderArbitrage(exchange2, exchange1);
		} catch (NotAvailableFromExchangeException | NotYetImplementedForExchangeException | ExchangeException
				| IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Arbitrage mit Limit Orders für eine Richtung.
	 * 
	 * @param askExchange
	 * @param askExchangeOrderBook
	 * @param bidExchange
	 * @param bidExchangeOrderBook
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	void oneWay_limitOrderArbitrage(AbstractArbitrageExchange askExchange, AbstractArbitrageExchange bidExchange)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		LOGGER.trace("Checking for Arbitrage opportunity...");

		// ASK-Exchange
		double priceAskExchange = askExchange.getOrderbook().getAsks().get(0).getLimitPrice().doubleValue();
		LOGGER.trace("[{}, ASK] Price: {}", askExchange, priceAskExchange);

		// BID-Exchange
		double priceBidExchange = bidExchange.getOrderbook().getBids().get(0).getLimitPrice().doubleValue();
		LOGGER.trace("[{}, BID] Price: {}", bidExchange, priceBidExchange);

		// Arbitrage berechnen
		// getMakerFee gibt TradingFee, falls nicht explizit gesetzt.
		double tradingFeeExchange1 = askExchange.getMakerFee();
		double tradingFeeExchange2 = bidExchange.getMakerFee();
		double arbitragePercentage = calculateArbitragePercentage(priceAskExchange, priceBidExchange,
				tradingFeeExchange1, tradingFeeExchange2);
		LOGGER.info("[{} -> {}] Arbitrage: {}", askExchange.toString(), bidExchange.toString(),
				String.format("%.8f", arbitragePercentage));

		// Wenn Arbitrage positiv, führe Trade durch
		if (arbitragePercentage > 0) {
			LOGGER.info("");
			LOGGER.info("FOUND ARBITRAGE OPPORTUNITY");
			LOGGER.info("");
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
					boolean exchange1_cancelled = exchange1.cancelOrder(orderID_exchange1);
					boolean exchange2_cancelled = exchange2.cancelOrder(orderID_exchange1);
					System.err.println("Fatal trade error, terminating application.");
					System.exit(1);
				}

				// Nach Tradeaktivität ändert sich Kontostand, deswegen true.
				updateWallets = true;
			}

		}

	}

	/**
	 * Aktualisiert das OrderBook für einen gegebenen Exchange.
	 * 
	 * @param exchangeOne
	 */
	void updateOrderbook(AbstractArbitrageExchange exchangeOne, AbstractArbitrageExchange exchangeTwo) {
		LOGGER.trace("Updating Orderbooks for {} and {}", exchangeOne, exchangeTwo);

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
		LOGGER.trace("Updating Ticker for {} and {}", exchangeOne, exchangeTwo);

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
		LOGGER.trace("Updating Wallets for {} and {}", exchangeOne, exchangeTwo);

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