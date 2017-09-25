package de.gekko.old;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

public class Old_Arbitrager {
	private static final double arbitrageMargin = 0.45;
    
	private static final double maxTradeLimit = 0.03; //ETH
	// Arbitrage Settings
	private static final double minTradeLimit = 0.01; //ETH
	private boolean balanceChanged = false;

	// Currency Pair
	private CurrencyPair currencyPair;
	
	private final boolean DEBUG = true;
	//Exchanges
	private Old_AbstractArbitrageExchange exchange1;
	
	// Balances
	private double exchange1Base;
	
	private double exchange1Counter;
	// Order Books
	private OrderBook exchange1Orderbook;
	
	private Old_AbstractArbitrageExchange exchange2;
	private double exchange2Base;
	private double exchange2Counter;
	private OrderBook exchange2Orderbook;
	//Networking Executor Service
	ExecutorService networkExecutorService = Executors.newFixedThreadPool(2);
	private boolean startup = true;
	private double startUpBase = -1;
	private double startUpCounter = -1;
	private double totalBase;
	private double totalCounter;
	
	public Old_Arbitrager(Old_AbstractArbitrageExchange exchange1, Old_AbstractArbitrageExchange exchange2, CurrencyPair currencyPair) throws IOException{
		this.exchange1 = exchange1;
		this.exchange2 = exchange2;
		this.currencyPair = currencyPair;
		
		updateBalances();
		//TODO: sanity checks for currency mismatch
	}
	
	public void arbitrageEthLtc1() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException{
		if(balanceChanged){
			Thread.sleep(1000);
			updateBalances();
		}
		
		double btceAsk = 0;
	    double btceAmount = 0;
		for (LimitOrder limitOrder : exchange1Orderbook.getAsks()) {
//			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				btceAsk = limitOrder.getLimitPrice().doubleValue();
				btceAmount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}

		double bittrexAmount = 0;
	    double bittrexBid = 0;
		for (LimitOrder limitOrder : exchange2Orderbook.getAsks()) {
//			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				bittrexBid = limitOrder.getLimitPrice().doubleValue();
				bittrexAmount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}
		

		double arbitragePercentage = getArbitragePercentage(btceAsk, (1.0 / bittrexBid));
		if (arbitragePercentage > arbitrageMargin) { //(btceFee + bittrexFee)

			// Tradelimit ausloten BTC-E (ETH)
			double tradeAmountETH = 0;    // <- müsste bittrexAmount sein? copy/paste-fehler
			if (btceAmount > maxTradeLimit) {
				tradeAmountETH = maxTradeLimit;
			} else {
				tradeAmountETH = btceAmount;
			}
			
			// Tradelimit ausloten Bittrex (LTC)
			double tradeAmountLTC = (tradeAmountETH * btceAsk);
			if(bittrexAmount < tradeAmountLTC){  //<--- müsste btceAmount sein? Copy/paste-fehler
				tradeAmountLTC = bittrexAmount;
				tradeAmountETH = tradeAmountLTC / btceAsk;
			}
			

			
			double bittrexFeeAbsolute = tradeAmountLTC * bittrexBid* 0.0025;
			double btceFeeAbsolute = tradeAmountETH * 0.002;
			double profit = tradeAmountETH - btceFeeAbsolute - ((tradeAmountLTC*bittrexBid)+bittrexFeeAbsolute);
			if(profit > 0){
				if(tradeAmountLTC > exchange1Counter){
					System.err.println("Insufficient funds LTC -> BTCE | Arbitrage = " + arbitragePercentage);
					return;
				}
				if(((tradeAmountLTC*bittrexBid)+bittrexFeeAbsolute) > exchange2Base){
					System.err.println("Insufficient funds ETH -> Bittrex | Arbitrage = " + arbitragePercentage);
					return;
				}
				if(tradeAmountETH < 0.01){
					System.err.println("Trade Amount < 0.01");
					return;
				}
				
				// Create Trade Numbers
				BigDecimal btceTradeBid = BigDecimal.valueOf(btceAsk).setScale(5, BigDecimal.ROUND_HALF_UP);
				BigDecimal btceTradeAmount = BigDecimal.valueOf(tradeAmountETH).setScale(5,BigDecimal.ROUND_HALF_UP);
				
				BigDecimal bittrexTradeBid = BigDecimal.valueOf(bittrexBid).setScale(8, BigDecimal.ROUND_HALF_UP);
				BigDecimal bittrexTradeAmount = BigDecimal.valueOf(tradeAmountLTC).setScale(8,BigDecimal.ROUND_HALF_UP);
				
				// Perform Trades - Multi-threaded Implementation
				Callable<String> callable_btceTrade = () -> {
					LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.BID, eth_ltc).limitPrice(btceTradeBid).tradableAmount(btceTradeAmount).build();
					return btceTradeService.placeLimitOrder(btceLimitOrder);
				};
				
				Callable<String> callable_bittrexTrade = () -> {
					LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.BID, ltc_eth).limitPrice(bittrexTradeBid).tradableAmount(bittrexTradeAmount).build();
					return bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				};
				
				Future<String> future_btceTrade = networkExecutorService.submit(callable_btceTrade);
				Future<String> future_bittrexTrade = networkExecutorService.submit(callable_bittrexTrade);	
				
				System.out.println("====================ARBIT1====================");
				System.out.println("[btceAsk] Best profitable ask: " + btceAsk + " Amount: " + btceAmount);
				System.out.println("[bittrexBid] Best profitable bid: " + (1.0 / bittrexBid) + "[" + bittrexBid + "]" + " Amount: " + bittrexAmount);
				System.out.println("Arbitrage = " + arbitragePercentage);
				System.out.println("BTC-e - BUY Amount[ETH]: " + tradeAmountETH + " SELL Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Bittrex - SELL Amount[ETH]: " + ((tradeAmountLTC*bittrexBid)+bittrexFeeAbsolute) + " BUY Amount[LTC]: " + tradeAmountLTC);
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
//				BigDecimal bittrexTradeBid = BigDecimal.valueOf(bittrexBid).setScale(8, BigDecimal.ROUND_HALF_UP);
//				BigDecimal bittrexTradeAmount = BigDecimal.valueOf(tradeAmountLTC).setScale(8,BigDecimal.ROUND_HALF_UP);
//				LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.BID, ltc_eth).limitPrice(bittrexTradeBid).tradableAmount(bittrexTradeAmount).build();
//				String uuid = bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
//				
//				System.out.println("Order1 Placed:" + uuid);
//				
//				BigDecimal btceTradeBid = BigDecimal.valueOf(btceAsk).setScale(5, BigDecimal.ROUND_HALF_UP);
//				BigDecimal btceTradeAmount = BigDecimal.valueOf(tradeAmountETH).setScale(5,BigDecimal.ROUND_HALF_UP);
//				LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.BID, eth_ltc).limitPrice(btceTradeBid).tradableAmount(btceTradeAmount).build();
//				String uuid1 = btceTradeService.placeLimitOrder(btceLimitOrder);
//
//				System.out.println("Order2 Placed:" + uuid1);
//				System.out.println("Arbitrage successfull.");

				balanceChanged = true;
				
			} else {
				System.err.println("Arbitrage = " + arbitragePercentage);
			}
		}
	}
	
	public void arbitrageEthLtc2() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException{
		if(balanceChanged){
			Thread.sleep(1000);
			updateBalances();
		}
		
	    double btceBid = 0;
	    double btceAmount = 0;
		for (LimitOrder limitOrder : exchange1Orderbook.getBids()) {
			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				btceBid = limitOrder.getLimitPrice().doubleValue();
				btceAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}

		double bittrexAmount = 0;
	    double bittrexAsk = 0;
		for (LimitOrder limitOrder : exchange2Orderbook.getBids()) {
			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				bittrexAsk = limitOrder.getLimitPrice().doubleValue();
				bittrexAmount = limitOrder.getTradableAmount().doubleValue();
				break;
			}
		}
		
		double arbitragePercentage = getArbitragePercentage((1.0 / bittrexAsk), btceBid);
		if (arbitragePercentage > arbitrageMargin) { //(btceFee + bittrexFee)

			// Tradelimit ausloten BTC-E (ETH)
			double tradeAmountETH = 0;
			if (btceAmount > maxTradeLimit) {
				tradeAmountETH = maxTradeLimit;
			} else {
				tradeAmountETH = btceAmount;
			}
			
			// Tradelimit ausloten Bittrex (LTC)
			double tradeAmountLTC = (tradeAmountETH * btceBid);
			if(bittrexAmount < tradeAmountLTC){
				tradeAmountLTC = bittrexAmount;
				tradeAmountETH = tradeAmountLTC / btceBid;
			}
			
			//
			double bittrexDiscountETH = tradeAmountLTC * bittrexAsk;
			
			// Profit abzüglich trading und transaktionsgebühren
//			double profit =  ((tradeAmountLTC*(1-bittrexFee) - bittrexLtcWithdrawFee)*bittrexAsk) - (tradeAmountETH*(1-btceFee) - btceEthWithdrawFee);
//			double profit =  (tradeAmountETH*(1-btceFee)) - ((tradeAmountLTC*(1-bittrexFee))*bittrexAsk);
			

			double btceFeeAbsolute = (tradeAmountLTC * 0.002);
			tradeAmountLTC = (tradeAmountLTC - btceFeeAbsolute);
			double bittrexFeeAbsolute = tradeAmountLTC * bittrexAsk* 0.0025;
			double profit = ((tradeAmountLTC*bittrexAsk) - tradeAmountETH) - bittrexFeeAbsolute; //bittrexFeeAbsolute;
			if(profit > 0){
				if(tradeAmountETH > exchange1Base){
					System.err.println("Insufficient funds ETH -> BTCE | Arbitrage = " + arbitragePercentage);
					return;
				}
				if(tradeAmountLTC > exchange2Counter){
					System.err.println("Insufficient funds LTC -> Bittrex | Arbitrage = " + arbitragePercentage);
					return;
				}
				if(tradeAmountETH < 0.01){
					System.err.println("Trade Amount < 0.01 | Arbitrage = " + arbitragePercentage);
					return;
				}
				System.out.println("====================ARBIT2====================");
				System.out.println("[bittrexAsk] Best profitable ask: " + (1.0 / bittrexAsk) + "[" + bittrexAsk + "]" + " Amount: " + bittrexAmount);
				System.out.println("[btceBid] Best profitable bid: " + btceBid + " Amount: " + btceAmount);
				System.out.println("Arbitrage = " + arbitragePercentage);
				System.out.println("BTC-e - SELL Amount[ETH]: " + tradeAmountETH + " BUY Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Bittrex - BUY Amount[ETH]: " + String.format("%.8f", (tradeAmountLTC * bittrexAsk)) + " SELL Amount[LTC]: " + tradeAmountLTC);
				System.out.println("Profit: " + String.format("%.8f", profit) + " ETH");
				
				BigDecimal bittrexTradeAsk = BigDecimal.valueOf(bittrexAsk).setScale(8, BigDecimal.ROUND_HALF_UP);
				BigDecimal bittrexTradeAmount = BigDecimal.valueOf(tradeAmountLTC).setScale(8,BigDecimal.ROUND_HALF_UP);
				
				BigDecimal btceTradeAsk = BigDecimal.valueOf(btceBid).setScale(5, BigDecimal.ROUND_HALF_UP);
				BigDecimal btceTradeAmount = BigDecimal.valueOf(tradeAmountETH).setScale(5,BigDecimal.ROUND_HALF_UP);
				String uuid = "";
				String uuid1 = "";
				
				// Perform Trades - Multi-threaded Implementation
				Callable<String> callable_btceTrade = () -> {
					LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.ASK, eth_ltc).limitPrice(btceTradeAsk).tradableAmount(btceTradeAmount).build();
					return btceTradeService.placeLimitOrder(btceLimitOrder);
				};
				
				Callable<String> callable_bittrexTrade = () -> {
					LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.ASK, ltc_eth).limitPrice(bittrexTradeAsk).tradableAmount(bittrexTradeAmount).build();
					return bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
				};
				
				Future<String> future_btceTrade = networkExecutorService.submit(callable_btceTrade);
				Future<String> future_bittrexTrade = networkExecutorService.submit(callable_bittrexTrade);	
				
				
				try {
					uuid = future_bittrexTrade.get();
					System.out.println("Order1 Placed:" + uuid);
					uuid1 = future_btceTrade.get();
					System.out.println("Order2 Placed:" + uuid1);
					
				} 
//				catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				} catch (ExecutionException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
				catch(Exception exception) {
					exception.printStackTrace();
					boolean cancelled = bittrexTradeService.cancelOrder(uuid);
					boolean cancelled1 = btceTradeService.cancelOrder(uuid1);
					System.err.println("Trade Amount BTCE = " + btceTradeAmount +  "ETH | Bittrex = " + bittrexTradeAmount + " LTC");
					System.exit(1);
				}
				
//				try {
//					//Trades veranlassen
//
//					LimitOrder bittrexLimitOrder = new LimitOrder.Builder(OrderType.ASK, ltc_eth).limitPrice(bittrexTradeAsk).tradableAmount(bittrexTradeAmount).build();
//					uuid = bittrexTradeService.placeLimitOrder(bittrexLimitOrder);
//					
//					System.out.println("Order1 Placed:" + uuid);
//					
//
//					LimitOrder btceLimitOrder = new LimitOrder.Builder(OrderType.ASK, eth_ltc).limitPrice(btceTradeAsk).tradableAmount(btceTradeAmount).build();
//					uuid1 = btceTradeService.placeLimitOrder(btceLimitOrder);
//
//					System.out.println("Order2 Placed:" + uuid1);
//
//				} catch(Exception exception) {
//					exception.printStackTrace();
//					boolean cancelled = bittrexTradeService.cancelOrder(uuid);
//					boolean cancelled1 = btceTradeService.cancelOrder(uuid1);
//					System.err.println("Trade Amount BTCE = " + btceTradeAmount +  "ETH | Bittrex = " + bittrexTradeAmount + " LTC");
//					System.exit(1);
//				}

				
				balanceChanged = true;
			} else {
				System.err.println("Arbitrage = " + arbitragePercentage);
			}
		}
	}
	
	public void arbTest1(){
		double exchange1Ask = 0;
	    double exchange1Amount = 0;
		for (LimitOrder limitOrder : exchange1Orderbook.getAsks()) {
//			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				exchange1Ask = limitOrder.getLimitPrice().doubleValue();
				exchange1Amount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}


	    double exchange2Bid = 0;
		double exchange2Amount = 0;
		for (LimitOrder limitOrder : exchange2Orderbook.getBids()) {
//			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				exchange2Bid = limitOrder.getLimitPrice().doubleValue();
				exchange2Amount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}
		
		double arbitragePercentage = getArbitragePercentage(exchange1Ask, exchange2Bid);
		if(arbitragePercentage > 0) {
			System.out.println("Arb1: " + String.format("%.8f",arbitragePercentage));
		}
	}
	
	public void arbTest2(){
		double exchange1Ask = 0;
	    double exchange1Amount = 0;
		for (LimitOrder limitOrder : exchange1Orderbook.getBids()) {
//			if (limitOrder.getTradableAmount().doubleValue() > btceEthWithdrawFee) {
				exchange1Ask = limitOrder.getLimitPrice().doubleValue();
				exchange1Amount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}


	    double exchange2Bid = 0;
		double exchange2Amount = 0;
		for (LimitOrder limitOrder : exchange2Orderbook.getAsks()) {
//			if (limitOrder.getTradableAmount().doubleValue() > bittrexLtcWithdrawFee) {
				exchange2Bid = limitOrder.getLimitPrice().doubleValue();
				exchange2Amount = limitOrder.getTradableAmount().doubleValue();
				break;
//			}
		}
		
		double arbitragePercentage = getArbitragePercentage(exchange2Bid, exchange1Ask);
		if(arbitragePercentage > 0) {
			System.out.println("Arb2: " +  String.format("%.8f",arbitragePercentage));
		}
	}
	
	// Calcultate Arbitrage
	private double getArbitragePercentage(double ask, double bid){
		return (1 - ask/bid) * 100;
	}
	
	public void updateBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
		//Update Balances on Exchanges
		//TODO: multithreaded balance check
		exchange1.checkBalances();
		exchange2.checkBalances();
		
		//Get Amounts
		double exchange1BaseUpdated = exchange1.getBaseAmount();
		double exchange1CounterUpdated = exchange1.getCounterAmount();
		double exchange2BaseUpdated = exchange2.getBaseAmount();
		double exchange2CounterUpdated = exchange2.getCounterAmount();
		
		double totalBase = exchange1Base + exchange2Base;
		double totalCounter = exchange1Counter + exchange2Counter;
			    
	    System.out.println(exchange1.getName() + ": "  + currencyPair.base.toString() + " = " + String.format("%.8f", exchange1BaseUpdated) + " | " + currencyPair.counter.toString() + " = "  +  String.format("%.8f", exchange1CounterUpdated));
	    System.out.println(exchange2.getName() + ": "  + currencyPair.base.toString() + " = " + String.format("%.8f", exchange2BaseUpdated) + " | " + currencyPair.counter.toString() + " = "  +  String.format("%.8f", exchange2CounterUpdated));
	    
		if(startup){
		    System.out.println("Total: " + currencyPair.base.toString()  + " = " + totalBase + " | " + currencyPair.counter.toString()  + " = " + totalCounter);
			startUpBase = totalBase;
			startUpCounter = totalCounter;
			startup = false;
		} else {
		    System.out.println("Total: " + currencyPair.base.toString()  + " = " + totalBase + " (" + String.format("%.8f", (totalBase - this.totalBase)) + ") " + currencyPair.counter.toString()  + " = " + totalCounter + " (" + String.format("%.8f", ((totalCounter - this.totalCounter))) + ")");
	    	System.out.println("Profit since Start: ETH = " + String.format("%.8f", ((totalBase - startUpBase))) + " LTC = " + String.format("%.8f", ((totalCounter - startUpCounter))));
		}
	    
	    //Update Class Variables
		this.exchange1Base = exchange1BaseUpdated;
		this.exchange1Counter = exchange1CounterUpdated;
		this.exchange2Base = exchange2BaseUpdated;
		this.exchange2Counter = exchange2CounterUpdated;
		this.totalBase = totalBase;
		this.totalCounter = totalCounter;
		balanceChanged = false;
	}
	
	public void updateMarketData() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
				
		// Multi-threaded Implementation
		Callable<OrderBook> callable_btcMarketsOrderBookEthBtc = () -> {
		    return exchange1.getOrderbook();
		};
		
		Callable<OrderBook> callable_bittrexOrderBookBtcEth = () -> {
		    return exchange2.getOrderbook();
		};
		
		Future<OrderBook> future_btceOrderBookEthLtc = networkExecutorService.submit(callable_btcMarketsOrderBookEthBtc);
		Future<OrderBook> future_bittrexOrderBookLtcEth = networkExecutorService.submit(callable_bittrexOrderBookBtcEth);	
		
		
		try {
			exchange1Orderbook= future_btceOrderBookEthLtc.get();
			exchange2Orderbook = future_bittrexOrderBookLtcEth.get();
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
//	public void tradeTest() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException{
//	 LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, ltc_eth).limitPrice(new BigDecimal("0.0001")).tradableAmount(new BigDecimal("6")).build();
//    String uuid = bittrexTradeService.placeLimitOrder(limitOrder);
//     System.out.println("Order successfully placed. ID=" + uuid);
//
//     Thread.sleep(7000); // wait for order to propagate
//
//     System.out.println();
//     System.out.println(bittrexTradeService.getOpenOrders());
//
//     System.out.println("Attempting to cancel order " + uuid);
//     boolean cancelled = bittrexTradeService.cancelOrder(uuid);
//
//     if (cancelled) {
//       System.out.println("Order successfully canceled.");
//     } else {
//       System.out.println("Order not successfully canceled.");
//     }
//
//     Thread.sleep(7000); // wait for cancellation to propagate
//
//     System.out.println();
//     System.out.println(bittrexTradeService.getOpenOrders());
//}

}
