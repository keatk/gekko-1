package de.gekko.arbitrager;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.exchanges.AbstractArbitrageExchange;

public class Arbitrager {

	/**
	 * Speichert Schwellenwert für Arbitrage TODO: weg vom hardcoding (könnte man so
	 * lösen, dass Trades durchgeführt werden, sobald die profitabilität positiv
	 */
	private static final double arbitrageMargin = 0.45;

	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("Arbitrager");

	/**
	 * Speichert das maximale Tradelimit in ETH. Menge an Coints, die maximial
	 * benutzt werden dürfen. TODO: Werte der Exchange CurrencyPairMetaData
	 * verwenden.
	 */
	private static final double maxTradeLimit = 0.03;

	/**
	 * Speichert das minimale Tradelimit in ETH. Menge an Coints, die minimal
	 * benutzt werden müssen.
	 */
	private static final double minTradeLimit = 0.01;

	/**
	 * Speichert die Flag, die bestimmt ob Wallets aktualisiert werden. Zum
	 * Programmstart und nachdem erfolgreich Trades durchgeführt, wird die Flag auf
	 * true gesetzt.
	 */
	private boolean updateWallets = true;

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
	// private double exchange1BaseAmount;
	// private double exchange1CounterAmount;
	private OrderBook exchange1Orderbook;
	private Wallet exchange1Wallet;

	/**
	 * Speichert den zweiten Exchange.
	 */
	private AbstractArbitrageExchange exchange2;
	// private double exchange2BaseAmount;
	// private double exchange2CounterAmount;
	private OrderBook exchange2Orderbook;
	private Wallet exchange2Wallet;

	/**
	 * Speichert den Executor Service für Netzwerkanfragen auf die Exchanges.
	 */
	private ExecutorService networkExecutorService = Executors.newFixedThreadPool(2);

	// /**
	// * Startup variable um programmstart zu erkennen
	// */
	// private boolean startup = true;

	/**
	 * Menge der Coins auf den Exchanges zum Start des Programms. Wird zur
	 * Berechnung des Gewinns einer Session gebraucht
	 */
	private double startUpBase = -1;
	private double startUpCounter = -1;

	/**
	 * Summe der Coins auf beiden Exchanges.
	 */
	private double totalBase;
	private double totalCounter;

	public Arbitrager(AbstractArbitrageExchange exchange1, AbstractArbitrageExchange exchange2,
			CurrencyPair currencyPair) throws IOException {
		this.exchange1 = exchange1;
		this.exchange2 = exchange2;
		this.currencyPair = currencyPair;

		updateWallet();
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
	private double getArbitragePercentage(double priceAsk, double priceBid) {
		double grossMargin = 1 - priceAsk / priceBid;
		double tradingFeeExchange1 = exchange1.getTradingFee(currencyPair);
		double tradingFeeExchange2 = exchange2.getTradingFee(currencyPair);
		return (grossMargin - tradingFeeExchange1 - tradingFeeExchange2) * 100;
	}

	/**
	 * Aktualisiert zunächst die OrderBooks der Exchages. Führt Arbitrage in beide
	 * Richtungen aus. Dazu wird die Profitabilität geprüft und ggf. ein Trade
	 * durchgeführt.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void limitOrderArbitrage() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		updateOrderbooks();
		oneWay_limitOrderArbitrage(exchange1, exchange1Orderbook, exchange2, exchange2Orderbook);
		oneWay_limitOrderArbitrage(exchange2, exchange2Orderbook, exchange1, exchange1Orderbook);
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

	public void oneWay_limitOrderArbitrage(AbstractArbitrageExchange askExchange, OrderBook askExchangeOrderBook,
			AbstractArbitrageExchange bidExchange, OrderBook bidExchangeOrderBook)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		LOGGER.info("Checking for Arbitrage opportunity...");

		/**
		 * Ausdrücke sollte den Block oben ersetzen. Benennung präzisiert.
		 */
		double priceAskExchange = askExchangeOrderBook.getAsks().get(0).getLimitPrice().doubleValue();
		double amountAskExchange = bidExchangeOrderBook.getAsks().get(0).getRemainingAmount().doubleValue();

		/**
		 * Ausdrücke sollte den Block oben ersetzen. Benennung präzisiert.
		 */
		double priceBidExchange = askExchangeOrderBook.getBids().get(0).getLimitPrice().doubleValue();
		double amountBidExchange = bidExchangeOrderBook.getAsks().get(0).getTradableAmount().doubleValue();

		// Arbitrage berechnen
		double arbitragePercentage = getArbitragePercentage(priceAskExchange, priceBidExchange);
		// if (arbitragePercentage > 0) {
		LOGGER.info("[{} -> {}] Arbitrage: {}, Min: {}", askExchange.toString(), bidExchange.toString(),
				String.format("%.8f", arbitragePercentage), arbitrageMargin);
		// }

		// Falls Arbitrage-Schwellenwert erreicht dann trade
		if (arbitragePercentage > arbitrageMargin) {
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
			final double tradeAmount = maxTradeLimit;

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
	 * Aktualisiert Orderbooks.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void updateOrderbooks() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		LOGGER.info("Updating Orderbooks...");

		// Multi-threaded Implementation
		Callable<OrderBook> callable_exchange1Orderbook = () -> {
			return exchange1.getOrderbook(currencyPair);
		};

		Callable<OrderBook> callable_exchange2Orderbook = () -> {
			return exchange2.getOrderbook(currencyPair);
		};

		Future<OrderBook> future_exchange1Orderbook = networkExecutorService.submit(callable_exchange1Orderbook);
		Future<OrderBook> future_exchange2Orderbook = networkExecutorService.submit(callable_exchange2Orderbook);

		try {
			exchange1Orderbook = future_exchange1Orderbook.get();
			LOGGER.info("[{}] Orderbook: ask = {}", exchange1, exchange1Orderbook.getAsks().get(0));
			LOGGER.info("[{}] Orderbook: bid = {}", exchange1, exchange1Orderbook.getBids().get(0));
			exchange2Orderbook = future_exchange2Orderbook.get();
			LOGGER.info("[{}] Orderbook: ask = {}", exchange2, exchange2Orderbook.getAsks().get(0));
			LOGGER.info("[{}] Orderbook: bid = {}", exchange2, exchange2Orderbook.getBids().get(0));

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Kontostände aktualisieren.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void updateWallet() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		if (updateWallets) {
			LOGGER.info("Updating Balances...");

			// Multi-threaded Implementation
			Callable<Wallet> callableExchange1Wallets = () -> {
				return exchange1.getWallets();
			};

			Callable<Wallet> callableExchange2Wallets = () -> {
				return exchange2.getWallets();
			};

			Future<Wallet> futureExchange1Wallets = networkExecutorService.submit(callableExchange1Wallets);
			Future<Wallet> futureExchange2Wallets = networkExecutorService.submit(callableExchange2Wallets);

			try {
				exchange1Wallet = futureExchange1Wallets.get();
				double exchange1BaseBalance = exchange1Wallet.getBalance(currencyPair.base).getAvailable()
						.doubleValue();
				double exchange1CounterBalance = exchange1Wallet.getBalance(currencyPair.counter).getAvailable()
						.doubleValue();
				LOGGER.info("[{}, {}] Balance: {}", exchange1, currencyPair.base, exchange1BaseBalance);
				LOGGER.info("[{}, {}] Balance: {}", exchange1, currencyPair.counter, exchange1CounterBalance);

				exchange2Wallet = futureExchange2Wallets.get();
				double exchange2BaseBalance = exchange2Wallet.getBalance(currencyPair.base).getAvailable()
						.doubleValue();
				double exchange2CounterBalance = exchange2Wallet.getBalance(currencyPair.counter).getAvailable()
						.doubleValue();
				LOGGER.info("[{}, {}] Balance: {}", exchange2, currencyPair.base, exchange2BaseBalance);
				LOGGER.info("[{}, {}] Balance: {}", exchange2, currencyPair.counter, exchange2CounterBalance);

				LOGGER.info("[Total, {}] Balance: {}", currencyPair.base,
						(exchange1BaseBalance + exchange2BaseBalance));
				LOGGER.info("[Total, {}] Balance: {}", currencyPair.counter,
						(exchange1CounterBalance + exchange2CounterBalance));
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// Kontostände wieder aktuell, deswegen false.
			updateWallets = false;
		}

	}

}