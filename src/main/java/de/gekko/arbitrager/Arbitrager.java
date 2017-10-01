package de.gekko.arbitrager;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.exchanges.AbstractArbitrageExchange;

public class Arbitrager {

	/**
	 * Speichert Schwellenwert für Arbitrage TODO: weg vom hardcoding
	 */
	private static final double arbitrageMargin = 0.45;

	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("Arbitrager");

	/**
	 * Speichert das maximale Tradelimit in ETH. Menge an Coints, die maximial
	 * benutzt werden dürfen.
	 */
	private static final double maxTradeLimit = 0.03;

	/**
	 * Speichert das minimale Tradelimit in ETH. Menge an Coints, die minimal
	 * benutzt werden müssen.
	 */
	private static final double minTradeLimit = 0.01;

	/**
	 * Falls Trades durchgeführt werden, wird auf true gesetzt. Bevor ein neuer
	 * Trade gemacht wird, müssen balances gechecks werden.
	 */
	private boolean balanceChanged = false;

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

	private double exchange1Base;
	private double exchange1Counter;
	private OrderBook exchange1Orderbook;
	/**
	 * Speichert den zweiten Exchange.
	 */
	private AbstractArbitrageExchange exchange2;

	private double exchange2Base;
	private double exchange2Counter;
	private OrderBook exchange2Orderbook;
	/**
	 * Speichert den Executor Service für Netzwerkanfragen auf die Exchanges.
	 */
	private ExecutorService networkExecutorService = Executors.newFixedThreadPool(2);

	/**
	 * Startup variable um programmstart zu erkennen
	 */
	private boolean startup = true;

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

		// updateBalances();
		// TODO: sanity checks for currency mismatch
	}

	/**
	 * Berechnet prozentuale Arbitrage.
	 * 
	 * @param ask
	 * @param bid
	 * @return
	 */
	private double getArbitragePercentage(double ask, double bid) {
		return (1 - ask / bid) * 100;
	}

	/**
	 * Führt Arbitrage in beide Richtungen aus.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void limitOrderArbitrage() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		updateOrderbooks();
		oneWay_limitOrderArbitrage(currencyPair.base, exchange1, currencyPair.counter, exchange2);
		oneWay_limitOrderArbitrage(currencyPair.base, exchange2, currencyPair.counter, exchange1);
	}

	/**
	 * Arbitrage mit Limit Orders für eine Richtung.
	 * 
	 * @param currency1
	 * @param arbitrageExchange1
	 * @param currency2
	 * @param arbitrageExchange2
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void oneWay_limitOrderArbitrage(Currency currency1, AbstractArbitrageExchange arbitrageExchange1,
			Currency currency2, AbstractArbitrageExchange arbitrageExchange2) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {

		// Den besten Ask auf Exchange 1 abrufen
		double exchange1Ask = 0;
		double exchange1Amount = 0;
		for (LimitOrder limitOrder : exchange1Orderbook.getAsks()) {
			exchange1Ask = limitOrder.getLimitPrice().doubleValue();
			exchange1Amount = limitOrder.getTradableAmount().doubleValue();
			break;
		}

		// Den besten Bid auf Exchange 1 abrufen
		double exchange2Bid = 0;
		double exchange2Amount = 0;
		for (LimitOrder limitOrder : exchange2Orderbook.getBids()) {
			exchange2Bid = limitOrder.getLimitPrice().doubleValue();
			exchange2Amount = limitOrder.getTradableAmount().doubleValue();
			break;
		}

		// Arbitrage berechnen
		double arbitragePercentage = getArbitragePercentage(exchange1Ask, exchange2Bid);
		if (arbitragePercentage > 0) {
			System.out.println("Arbitrage: " + String.format("%.8f", arbitragePercentage));
		}

		// Falls Arbitrage-Schwellenwert erreicht dann trade
		if (arbitragePercentage > arbitrageMargin) {

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
				final double exchange1Price = exchange1Ask;
				final double exchange2Price = exchange2Bid;

				// Perform Trades - Multi-threaded Implementation

				// TODO: genaue Tradeamounts richtig berechnen, bisher nur Platzhalter. Dazu
				// gehört umrechnung der Währungen und Berücksichtigung der Fees. (Achtung
				// Brainfuck :D)
				// TODO: Ziel sollte am Ende sein dass sich die Kontostände nur in richtung der
				// Arbitrage verändern, also ein plus auf einer Währungsseite rauskommt.
				// TODO: Hier Amounts anpassen, zwischen Coins umtechnen und Fees
				// berücksichtigen.
				Callable<String> callable_exchange1Trade = () -> {
					return arbitrageExchange1.placeLimitOrderBid(currencyPair, exchange1Price, tradeAmount);
				};

				// TODO: Hier Amounts anpassen, zwischen Coins umtechnen und Fees
				// berücksichtigen.
				Callable<String> callable_exchange2Trade = () -> {
					return arbitrageExchange2.placeLimitOrderBid(currencyPair, exchange2Price, tradeAmount);
				};

				Future<String> future_exchange1Order = networkExecutorService.submit(callable_exchange1Trade);
				Future<String> future_exchange2Order = networkExecutorService.submit(callable_exchange2Trade);

				String orderID_exchange1 = "";
				String orderID_exchange2 = "";
				try {
					orderID_exchange1 = future_exchange1Order.get();
					System.out.println("Order1 Placed. ID: " + orderID_exchange1);
					orderID_exchange2 = future_exchange2Order.get();
					System.out.println("Order2 Placed. ID: " + orderID_exchange2);

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
				balanceChanged = true;
			}
		}
	}

	@Override
	public String toString() {
		return "[Arbitrager for " + exchange1.toString() + " and " + exchange2.toString() + "]";
	}

	/**
	 * Kontostände aktualisieren.
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void updateBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		LOGGER.trace("updateBalances()");

		// Kontostände abrufen
		double exchange1BaseUpdated = exchange1.checkBalance(currencyPair.base);
		double exchange1CounterUpdated = exchange1.checkBalance(currencyPair.counter);
		double exchange2BaseUpdated = exchange2.checkBalance(currencyPair.base);
		double exchange2CounterUpdated = exchange2.checkBalance(currencyPair.counter);

		double totalBase = exchange1Base + exchange2Base;
		double totalCounter = exchange1Counter + exchange2Counter;

		// Logging bzw. Printing der Veränderungen und aktuellen Kontostände.
		LOGGER.trace("{}: {} = {} | {} = {}", exchange1.getName(), currencyPair.base,
				String.format("%.8f", exchange1BaseUpdated), currencyPair.counter,
				String.format("%.8f", exchange1CounterUpdated));
		LOGGER.trace("{}: {} = {} | {} = {}", exchange2.getName(), currencyPair.base,
				String.format("%.8f", exchange2BaseUpdated), currencyPair.counter,
				String.format("%.8f", exchange2CounterUpdated));

		if (startup) {
			LOGGER.trace("Total: {} = {} | {} = {}", currencyPair.base.toString(), totalBase,
					currencyPair.counter.toString(), totalCounter);
			startUpBase = totalBase;
			startUpCounter = totalCounter;
			startup = false;
		} else {
			LOGGER.trace("Total: {} = {} ({}) {} = {} ({})", currencyPair.base, totalBase,
					String.format("%.8f", (totalBase - this.totalBase)), currencyPair.counter, totalCounter,
					String.format("%.8f", ((totalCounter - this.totalCounter))));
			LOGGER.trace("Profit since Start: ETH = {}, LTC = {}", String.format("%.8f", ((totalBase - startUpBase))),
					String.format("%.8f", ((totalCounter - startUpCounter))));
		}

		// Klassenvariablen des Arbitragers updaten.
		this.exchange1Base = exchange1BaseUpdated;
		this.exchange1Counter = exchange1CounterUpdated;
		this.exchange2Base = exchange2BaseUpdated;
		this.exchange2Counter = exchange2CounterUpdated;
		this.totalBase = totalBase;
		this.totalCounter = totalCounter;
		// Kontostände wieder aktuell, deswegen false.
		balanceChanged = false;
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
			exchange2Orderbook = future_exchange2Orderbook.get();

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
