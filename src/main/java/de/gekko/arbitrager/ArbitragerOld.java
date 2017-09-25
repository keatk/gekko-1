package de.gekko.arbitrager;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bittrex.v1.BittrexExchange;
import org.knowm.xchange.btcmarkets.BTCMarketsExchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;

public class ArbitragerOld {

	private static final double arbitrageMargin = 0.45;

	private static final double bittrexEthWithdrawFee = 0.005;
	private static final double bittrexFee = 0.25;
	private static final double bittrexLtcWithdrawFee = 0.002;
	private static final double btceEthWithdrawFee = 0.001;

	// Trading Fees
	private static final double btceFee = 0.2;
	// Transaction Fees
	private static final double btceLtcWithdrawFee = 0.001;

	private static final double maxTradeLimit = 0.03; // ETH
	// Arbitrage Settings
	private static final double minTradeLimit = 0.01; // ETH

	public static Exchange createExchangeBittrex() {
		ExchangeSpecification exSpec = new ExchangeSpecification(BittrexExchange.class);
		exSpec.setApiKey("");
		exSpec.setSecretKey("");

		return ExchangeFactory.INSTANCE.createExchange(exSpec);
	}

	public static Exchange createExchangeBtcMarkets() throws IOException {
		ExchangeSpecification exSpec = new ExchangeSpecification(BTCMarketsExchange.class);
		exSpec.setApiKey("");
		exSpec.setSecretKey("");

		Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);

		return exchange;
	}

	private boolean balanceChanged = false;
	private AccountService bittrexAccountService;
	private double bittrexEth;

	private Exchange bittrexExchange;
	private double bittrexLtc;
	private MarketDataService bittrexMarketDataService;
	private OrderBook bittrexOrderBookEthBtc;
	private OrderBook bittrexOrderBookLtcEth;
	private TradeService bittrexTradeService;
	// Currency Pairs
	private CurrencyPair btc_eth = new CurrencyPair(Currency.BTC, Currency.ETH);
	private AccountService btceAccountService;

	// Balances
	private double btceEth;
	// Exchange Services
	private Exchange btceExchange;
	private double btceLtc;
	private MarketDataService btceMarketDataService;
	private OrderBook btceOrderBookEthBtc;

	// Order Books
	private OrderBook btceOrderBookEthLtc;
	private TradeService btceTradeService;
	private boolean debug = true;
	private CurrencyPair eth_btc = new CurrencyPair(Currency.ETH, Currency.BTC);
	private CurrencyPair eth_ltc = new CurrencyPair(Currency.ETH, Currency.LTC);
	private CurrencyPair ltc_eth = new CurrencyPair(Currency.LTC, Currency.ETH);;
	ExecutorService networkExecutor = Executors.newFixedThreadPool(2);

	private double startUpEth = -1;
	private double startUpLtc = -1;

	private double totalEth;

	private double totalLtc;

	// public void tradeTest() throws NotAvailableFromExchangeException,
	// NotYetImplementedForExchangeException, ExchangeException, IOException,
	// InterruptedException{
	// LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID,
	// ltc_eth).limitPrice(new BigDecimal("0.0001")).tradableAmount(new
	// BigDecimal("6")).build();
	// String uuid = bittrexTradeService.placeLimitOrder(limitOrder);
	// System.out.println("Order successfully placed. ID=" + uuid);
	//
	// Thread.sleep(7000); // wait for order to propagate
	//
	// System.out.println();
	// System.out.println(bittrexTradeService.getOpenOrders());
	//
	// System.out.println("Attempting to cancel order " + uuid);
	// boolean cancelled = bittrexTradeService.cancelOrder(uuid);
	//
	// if (cancelled) {
	// System.out.println("Order successfully canceled.");
	// } else {
	// System.out.println("Order not successfully canceled.");
	// }
	//
	// Thread.sleep(7000); // wait for cancellation to propagate
	//
	// System.out.println();
	// System.out.println(bittrexTradeService.getOpenOrders());
	// }

	public ArbitragerOld() throws IOException {
		this.btceExchange = createExchangeBtcMarkets();
		this.bittrexExchange = createExchangeBittrex();
		this.btceMarketDataService = initMarketServiceBtce();
		this.bittrexMarketDataService = initMarketServiceBittrex();

		this.btceTradeService = btceExchange.getTradeService();
		this.bittrexTradeService = bittrexExchange.getTradeService();
		this.btceAccountService = btceExchange.getAccountService();
		this.bittrexAccountService = bittrexExchange.getAccountService();

		checkBalances();
		this.startUpEth = totalEth;
		this.startUpLtc = totalLtc;
	}

	/***
	 * Litecoin Arbitrage
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 * @throws InterruptedException
	 */

	public void arbitrageEthLtc1() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException, InterruptedException {
		if (balanceChanged) {
			Thread.sleep(1000);
			checkBalances();
		}

		double btceAsk = 0;
		double btceAmount = 0;
		for (LimitOrder limitOrder : btceOrderBookEthLtc.getAsks()) {
			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				btceAsk = limitOrder.getLimitPrice().doubleValue();
				btceAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}

		double bittrexAmount = 0;
		double bittrexBid = 0;
		for (LimitOrder limitOrder : bittrexOrderBookLtcEth.getAsks()) {
			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				bittrexBid = limitOrder.getLimitPrice().doubleValue();
				bittrexAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}

		double arbitragePercentage = getArbitragePercentage(btceAsk, (1.0 / bittrexBid));
		if (arbitragePercentage > arbitrageMargin) { // (btceFee + bittrexFee)

			// Tradelimit ausloten BTC-E (ETH)
			double tradeAmountETH = 0; // <- müsste bittrexAmount sein? copy/paste-fehler
			if (btceAmount > maxTradeLimit) {
				tradeAmountETH = maxTradeLimit;
			} else {
				tradeAmountETH = btceAmount;
			}

			// Tradelimit ausloten Bittrex (LTC)
			double tradeAmountLTC = (tradeAmountETH * btceAsk);
			if (bittrexAmount < tradeAmountLTC) { // <--- müsste btceAmount sein? Copy/paste-fehler
				tradeAmountLTC = bittrexAmount;
				tradeAmountETH = tradeAmountLTC / btceAsk;
			}

			double bittrexFeeAbsolute = tradeAmountLTC * bittrexBid * 0.0025;
			double btceFeeAbsolute = tradeAmountETH * 0.002;
			double profit = tradeAmountETH - btceFeeAbsolute - ((tradeAmountLTC * bittrexBid) + bittrexFeeAbsolute);
			if (profit > 0) {
				if (tradeAmountLTC > btceLtc) {
					System.err.println("Insufficient funds LTC -> BTCE | Arbitrage = " + arbitragePercentage);
					return;
				}
				if (((tradeAmountLTC * bittrexBid) + bittrexFeeAbsolute) > bittrexEth) {
					System.err.println("Insufficient funds ETH -> Bittrex | Arbitrage = " + arbitragePercentage);
					return;
				}
				if (tradeAmountETH < 0.01) {
					System.err.println("Trade Amount < 0.01");
					return;
				}

				// Create Trade Numbers
				BigDecimal btceTradeBid = BigDecimal.valueOf(btceAsk).setScale(5, BigDecimal.ROUND_HALF_UP);
				BigDecimal btceTradeAmount = BigDecimal.valueOf(tradeAmountETH).setScale(5, BigDecimal.ROUND_HALF_UP);

				BigDecimal bittrexTradeBid = BigDecimal.valueOf(bittrexBid).setScale(8, BigDecimal.ROUND_HALF_UP);
				BigDecimal bittrexTradeAmount = BigDecimal.valueOf(tradeAmountLTC).setScale(8,
						BigDecimal.ROUND_HALF_UP);

				// Perform Trades - Multi-threaded Implementation
				Callable<String> callable_btceTrade = () -> {
					LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.BID, eth_ltc).limitPrice(btceTradeBid)
							.tradableAmount(btceTradeAmount).build();
					return btceTradeService.placeLimitOrder(btceLimitOrder);
				};

				Callable<String> callable_bittrexTrade = () -> {
					LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.BID, ltc_eth)
							.limitPrice(bittrexTradeBid).tradableAmount(bittrexTradeAmount).build();
					return bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				};

				Future<String> future_btceTrade = networkExecutor.submit(callable_btceTrade);
				Future<String> future_bittrexTrade = networkExecutor.submit(callable_bittrexTrade);

				System.out.println("====================ARBIT1====================");
				System.out.println("[btceAsk] Best profitable ask: " + btceAsk + " Amount: " + btceAmount);
				System.out.println("[bittrexBid] Best profitable bid: " + (1.0 / bittrexBid) + "[" + bittrexBid + "]"
						+ " Amount: " + bittrexAmount);
				System.out.println("Arbitrage = " + arbitragePercentage);
				System.out
						.println("BTC-e - BUY Amount[ETH]: " + tradeAmountETH + " SELL Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Bittrex - SELL Amount[ETH]: " + ((tradeAmountLTC * bittrexBid) + bittrexFeeAbsolute)
						+ " BUY Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Profit: " + String.format("%.8f", profit) + " ETH");

				try {
					String uuid = future_bittrexTrade.get();
					System.out.println("Order1 Placed:" + uuid);
					String uuid1 = future_btceTrade.get();
					System.out.println("Order2 Placed:" + uuid1);

				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				//
				//
				//
				// BigDecimal bittrexTradeBid = BigDecimal.valueOf(bittrexBid).setScale(8,
				// BigDecimal.ROUND_HALF_UP);
				// BigDecimal bittrexTradeAmount =
				// BigDecimal.valueOf(tradeAmountLTC).setScale(8,BigDecimal.ROUND_HALF_UP);
				// LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.BID,
				// ltc_eth).limitPrice(bittrexTradeBid).tradableAmount(bittrexTradeAmount).build();
				// String uuid = bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				//
				// System.out.println("Order1 Placed:" + uuid);
				//
				// BigDecimal btceTradeBid = BigDecimal.valueOf(btceAsk).setScale(5,
				// BigDecimal.ROUND_HALF_UP);
				// BigDecimal btceTradeAmount =
				// BigDecimal.valueOf(tradeAmountETH).setScale(5,BigDecimal.ROUND_HALF_UP);
				// LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.BID,
				// eth_ltc).limitPrice(btceTradeBid).tradableAmount(btceTradeAmount).build();
				// String uuid1 = btceTradeService.placeLimitOrder(btceLimitOrder);
				//
				// System.out.println("Order2 Placed:" + uuid1);
				// System.out.println("Arbitrage successfull.");

				balanceChanged = true;

			} else {
				System.err.println("Arbitrage = " + arbitragePercentage);
			}
		}
	}

	public void arbitrageEthLtc2() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException, InterruptedException {
		if (balanceChanged) {
			Thread.sleep(1000);
			checkBalances();
		}

		double btceBid = 0;
		double btceAmount = 0;
		for (LimitOrder limitOrder : btceOrderBookEthLtc.getBids()) {
			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				btceBid = limitOrder.getLimitPrice().doubleValue();
				btceAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}

		double bittrexAmount = 0;
		double bittrexAsk = 0;
		for (LimitOrder limitOrder : bittrexOrderBookLtcEth.getBids()) {
			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				bittrexAsk = limitOrder.getLimitPrice().doubleValue();
				bittrexAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}

		double arbitragePercentage = getArbitragePercentage((1.0 / bittrexAsk), btceBid);
		if (arbitragePercentage > arbitrageMargin) { // (btceFee + bittrexFee)

			// Tradelimit ausloten BTC-E (ETH)
			double tradeAmountETH = 0;
			if (btceAmount > maxTradeLimit) {
				tradeAmountETH = maxTradeLimit;
			} else {
				tradeAmountETH = btceAmount;
			}

			// Tradelimit ausloten Bittrex (LTC)
			double tradeAmountLTC = (tradeAmountETH * btceBid);
			if (bittrexAmount < tradeAmountLTC) {
				tradeAmountLTC = bittrexAmount;
				tradeAmountETH = tradeAmountLTC / btceBid;
			}

			//
			double bittrexDiscountETH = tradeAmountLTC * bittrexAsk;

			// Profit abzüglich trading und transaktionsgebühren
			// double profit = ((tradeAmountLTC*(1-bittrexFee) -
			// bittrexLtcWithdrawFee)*bittrexAsk) - (tradeAmountETH*(1-btceFee) -
			// btceEthWithdrawFee);
			// double profit = (tradeAmountETH*(1-btceFee)) -
			// ((tradeAmountLTC*(1-bittrexFee))*bittrexAsk);

			double btceFeeAbsolute = (tradeAmountLTC * 0.002);
			tradeAmountLTC = (tradeAmountLTC - btceFeeAbsolute);
			double bittrexFeeAbsolute = tradeAmountLTC * bittrexAsk * 0.0025;
			double profit = ((tradeAmountLTC * bittrexAsk) - tradeAmountETH) - bittrexFeeAbsolute; // bittrexFeeAbsolute;
			if (profit > 0) {
				if (tradeAmountETH > btceEth) {
					System.err.println("Insufficient funds ETH -> BTCE | Arbitrage = " + arbitragePercentage);
					return;
				}
				if (tradeAmountLTC > bittrexLtc) {
					System.err.println("Insufficient funds LTC -> Bittrex | Arbitrage = " + arbitragePercentage);
					return;
				}
				if (tradeAmountETH < 0.01) {
					System.err.println("Trade Amount < 0.01 | Arbitrage = " + arbitragePercentage);
					return;
				}
				System.out.println("====================ARBIT2====================");
				System.out.println("[bittrexAsk] Best profitable ask: " + (1.0 / bittrexAsk) + "[" + bittrexAsk + "]"
						+ " Amount: " + bittrexAmount);
				System.out.println("[btceBid] Best profitable bid: " + btceBid + " Amount: " + btceAmount);
				System.out.println("Arbitrage = " + arbitragePercentage);
				System.out
						.println("BTC-e - SELL Amount[ETH]: " + tradeAmountETH + " BUY Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Bittrex - BUY Amount[ETH]: " + String.format("%.8f", (tradeAmountLTC * bittrexAsk))
						+ " SELL Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Profit: " + String.format("%.8f", profit) + " ETH");

				BigDecimal bittrexTradeAsk = BigDecimal.valueOf(bittrexAsk).setScale(8, BigDecimal.ROUND_HALF_UP);
				BigDecimal bittrexTradeAmount = BigDecimal.valueOf(tradeAmountLTC).setScale(8,
						BigDecimal.ROUND_HALF_UP);

				BigDecimal btceTradeAsk = BigDecimal.valueOf(btceBid).setScale(5, BigDecimal.ROUND_HALF_UP);
				BigDecimal btceTradeAmount = BigDecimal.valueOf(tradeAmountETH).setScale(5, BigDecimal.ROUND_HALF_UP);
				String uuid = "";
				String uuid1 = "";

				// Perform Trades - Multi-threaded Implementation
				Callable<String> callable_btceTrade = () -> {
					LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.ASK, eth_ltc).limitPrice(btceTradeAsk)
							.tradableAmount(btceTradeAmount).build();
					return btceTradeService.placeLimitOrder(btceLimitOrder);
				};

				Callable<String> callable_bittrexTrade = () -> {
					LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.ASK, ltc_eth)
							.limitPrice(bittrexTradeAsk).tradableAmount(bittrexTradeAmount).build();
					return bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				};

				Future<String> future_btceTrade = networkExecutor.submit(callable_btceTrade);
				Future<String> future_bittrexTrade = networkExecutor.submit(callable_bittrexTrade);

				try {
					uuid = future_bittrexTrade.get();
					System.out.println("Order1 Placed:" + uuid);
					uuid1 = future_btceTrade.get();
					System.out.println("Order2 Placed:" + uuid1);

				}
				// catch (InterruptedException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// } catch (ExecutionException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
				catch (Exception exception) {
					exception.printStackTrace();
					boolean cancelled = bittrexTradeService.cancelOrder(uuid);
					boolean cancelled1 = btceTradeService.cancelOrder(uuid1);
					System.err.println("Trade Amount BTCE = " + btceTradeAmount + "ETH | Bittrex = "
							+ bittrexTradeAmount + " LTC");
					System.exit(1);
				}

				// try {
				// //Trades veranlassen
				//
				// LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.ASK,
				// ltc_eth).limitPrice(bittrexTradeAsk).tradableAmount(bittrexTradeAmount).build();
				// uuid = bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				//
				// System.out.println("Order1 Placed:" + uuid);
				//
				//
				// LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.ASK,
				// eth_ltc).limitPrice(btceTradeAsk).tradableAmount(btceTradeAmount).build();
				// uuid1 = btceTradeService.placeLimitOrder(btceLimitOrder);
				//
				// System.out.println("Order2 Placed:" + uuid1);
				//
				// } catch(Exception exception) {
				// exception.printStackTrace();
				// boolean cancelled = bittrexTradeService.cancelOrder(uuid);
				// boolean cancelled1 = btceTradeService.cancelOrder(uuid1);
				// System.err.println("Trade Amount BTCE = " + btceTradeAmount + "ETH | Bittrex
				// = " + bittrexTradeAmount + " LTC");
				// System.exit(1);
				// }

				balanceChanged = true;
			} else {
				System.err.println("Arbitrage = " + arbitragePercentage);
			}
		}
	}

	/**
	 * Check Balances
	 * 
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public void checkBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		Map<Currency, Balance> btceBalances = btceAccountService.getAccountInfo().getWallet().getBalances();
		double btceEth = btceBalances.get(Currency.ETH).getTotal().doubleValue();
		double btceLtc = btceBalances.get(Currency.LTC).getTotal().doubleValue();

		Map<Currency, Balance> bittrexBalances = bittrexAccountService.getAccountInfo().getWallet().getBalances();
		double bittrexEth = bittrexBalances.get(Currency.ETH).getTotal().doubleValue();
		double bittrexLtc = bittrexBalances.get(Currency.LTC).getTotal().doubleValue();

		double totalEth = btceEth + bittrexEth;
		double totalLtc = btceLtc + bittrexLtc;

		System.out.println("BTCE: ETH = " + btceEth + " LTC = " + btceLtc + " | Bittrex: ETH = " + bittrexEth
				+ " LTC = " + bittrexLtc);
		System.out.println("Total: ETH = " + totalEth + " (" + String.format("%.8f", (totalEth - this.totalEth)) + ")"
				+ " LTC = " + totalLtc + " (" + String.format("%.8f", ((totalLtc - this.totalLtc))) + ")");
		if (startUpEth != -1 && startUpLtc != -1) {
			System.out.println("Profit since Start: ETH = " + String.format("%.8f", ((totalEth - startUpEth)))
					+ " LTC = " + String.format("%.8f", ((totalLtc - startUpLtc))));
		}

		this.btceEth = btceEth;
		this.btceLtc = btceLtc;
		this.bittrexEth = bittrexEth;
		this.bittrexLtc = bittrexLtc;
		this.totalEth = btceEth + bittrexEth;
		this.totalLtc = btceLtc + bittrexLtc;
		balanceChanged = false;
	}

	// Calcultate Arbitrage
	private double getArbitragePercentage(double ask, double bid) {
		return (1 - ask / bid) * 100;
	}

	private MarketDataService initMarketServiceBittrex() {

		return bittrexExchange.getMarketDataService();
	}

	private MarketDataService initMarketServiceBtce() {

		return btceExchange.getMarketDataService();
	}

	public void updateMarketDataBtcEth() throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {

		// Multi-threaded Implementation
		Callable<OrderBook> callable_btcMarketsOrderBookEthBtc = () -> {
			return btceMarketDataService.getOrderBook(eth_btc);
		};

		Callable<OrderBook> callable_bittrexOrderBookBtcEth = () -> {
			return bittrexMarketDataService.getOrderBook(eth_btc);
		};

		Future<OrderBook> future_btceOrderBookEthLtc = networkExecutor.submit(callable_btcMarketsOrderBookEthBtc);
		Future<OrderBook> future_bittrexOrderBookLtcEth = networkExecutor.submit(callable_bittrexOrderBookBtcEth);

		try {
			btceOrderBookEthLtc = future_btceOrderBookEthLtc.get();
			bittrexOrderBookLtcEth = future_bittrexOrderBookLtcEth.get();

		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
