package de.gekko.arbitrager;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gekko.exception.CurrencyMismatchException;
import de.gekko.exchanges.AbstractArbitrageExchange;
import de.gekko.wallet.AsyncWalletProvider;

/**
 * Class that performs triangular arbitrage (aka. inter market arbitrage).
 * @author Maximilian Pfister
 *
 */
public class TriangularArbitrager {
	
	private final double MAX_TRADE_AMOUNT; //based on base of baseCurrencyPair
	
	private int arbitCounter = 0;
	private boolean debug1 = false;
	private boolean debug2 = true;
	
	/**
	 * Speichert den Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger("Arbitrager");
	
	/**
	 * Speichert die Exchange
	 */
	private AbstractArbitrageExchange exchange;

	private OrderBook orderBook1;
	private OrderBook orderBook2;
	private OrderBook orderBook3;
	private Wallet exchangeWallet;
	
	/**
	 * Speichert die Currency Pairs
	 */
	private CurrencyPair basePair;
	private CurrencyPair crossPair1;
	private boolean twistCrossPair1 = false;
	private CurrencyPair crossPair2;
	private boolean twistCrossPair2 = false;
	
	private AsyncWalletProvider walletProvider;
	private Map<Currency, Double> balanceMap = new HashMap<>();
	
	/**
	 * Speichert den Executor Service für Netzwerkanfragen auf die Exchange.
	 */
	private ExecutorService networkExecutorService = Executors.newFixedThreadPool(3);
	
	boolean updateWallets = true;
	
	public TriangularArbitrager(AbstractArbitrageExchange exchange, AsyncWalletProvider walletProvider, CurrencyPair basePair,
			CurrencyPair crossPair1, CurrencyPair crossPair2, double MAX_TRADE_AMOUNT) throws IOException, CurrencyMismatchException {
		
		this.exchange = exchange;
		this.MAX_TRADE_AMOUNT = MAX_TRADE_AMOUNT;
		this.basePair = basePair;
		this.crossPair1 = crossPair1;
		this.crossPair2 = crossPair2;
		alignCurrencyPairs();
		this.walletProvider = walletProvider;

		updateWallet();
		System.out.println(twistCrossPair1);
		System.out.println(twistCrossPair2);

	}

	/**
	 * Align currencyPairs if possible, else throw exception.
	 * @throws CurrencyMismatchException
	 */
	public void alignCurrencyPairs() throws CurrencyMismatchException {
//		checkSwitchCrossPairs();
		if(!(checkBaseCross1Alignment() && checkBaseCross2Alignment() && checkCrossAlignment())){
			throw new CurrencyMismatchException();
		}
	}
	
//	/**
//	 * Checks if Crosspairs have to be switched for currency pair alignment.
//	 * @return
//	 */
//	private boolean checkSwitchCrossPairs() {
//		boolean switched = false;
//		boolean counterCross1Match = basePair.counter.equals(crossPair1.base) || basePair.counter.equals(crossPair1.counter);
//		boolean baseCross2Match = basePair.base.equals(crossPair2.base) || basePair.base.equals(crossPair2.counter);
//		if(counterCross1Match && baseCross2Match) {
//			CurrencyPair tmp = crossPair1;
//			crossPair1 = crossPair2;
//			crossPair2 = tmp;
//			switched = true;
//		}
//		return switched;
//	}
	
	/**
	 * Checks for currency alignment between base and cross currency #1. Sets alignment variable if necessary.
	 * @return 
	 */
	private boolean checkBaseCross1Alignment() {
		boolean alignmentPossible = false;

		if (basePair.base.equals(crossPair1.base)) {
			twistCrossPair1 = false;
			alignmentPossible = true;
		} else {
			if (basePair.base.equals(crossPair1.counter)) {
				twistCrossPair1 = true;
				alignmentPossible = true;
			}
		}

		return alignmentPossible;
	}
	
	/**
	 * Checks for currency alignment between base and cross currency #2. Sets alignment variable if necessary.
	 * @return 
	 */
	private boolean checkBaseCross2Alignment() {
		boolean alignmentPossible = false;

		if (basePair.counter.equals(crossPair2.counter)) {
			twistCrossPair2 = false;
			alignmentPossible = true;
		} else {
			if (basePair.counter.equals(crossPair2.base)) {
				twistCrossPair2 = true;
				alignmentPossible = true;
			}
		}

		return alignmentPossible;
	}
	
	/**
	 * Checks for alignment between cross currency #1 and cross currency #2.
	 * @return 
	 */
	private boolean checkCrossAlignment(){
		boolean aligned = false;
		
		if(!twistCrossPair1 && !twistCrossPair2){
			if(crossPair1.counter.equals(crossPair2.base)){
				aligned = true;
			}
		}
		if(!twistCrossPair1 && twistCrossPair2){
			if(crossPair1.counter.equals(crossPair2.counter)){
				aligned = true;
			}
		}
		if(twistCrossPair1 && !twistCrossPair2){
			if(crossPair1.base.equals(crossPair2.base)){
				aligned = true;
			}
		}
		if(twistCrossPair1 && twistCrossPair2){
			if(crossPair1.base.equals(crossPair2.counter)){
				aligned = true;
			}
		}

		return aligned;
	}
	
	public void restApitriangularArbitrage() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {
		updateOrderbooks();
		
		if(triangularArbitrage1(orderBook1, orderBook2, orderBook3)){
			updateOrderbooks();
			arbitCounter++;
		}
		if(triangularArbitrage2(orderBook1, orderBook2, orderBook3)){
			arbitCounter++;
		}
		LOGGER.info("Numer of Arbitrage Chances: {}", arbitCounter);
	}
	
	/**
	 * 
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public boolean triangularArbitrage1(OrderBook baseOrderBook, OrderBook cross1Orderbook, OrderBook cross2Orderbook) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {
		// Return value for couting arbitrage chances
		boolean ret = false;
		// Get bids and asks
		double basePairPrice = baseOrderBook.getAsks().get(0).getLimitPrice().doubleValue(); //Martktteilnehmer: BTC kaufen für ETH // ich: BTC verkaufen
		double basePairVolume = baseOrderBook.getAsks().get(0).getOriginalAmount().doubleValue();
		double crossPair1Price;
		double crossPair1Volume = 0;
		double crossPair2Price;
		double crossPair2Volume = 0;
//		if(twistCrossPair1){
//			crossPair1Price = cross1Orderbook.getBids().get(0).getLimitPrice().doubleValue();
//			crossPair1Volume = cross1Orderbook.getBids().get(0).getOriginalAmount().doubleValue();
//		} else {
			crossPair1Price = cross1Orderbook.getBids().get(0).getLimitPrice().doubleValue(); //Martktteilnehmer: BTC verkaufen für OMG // ich: OMG verkaufen
			crossPair1Volume = cross1Orderbook.getBids().get(0).getOriginalAmount().doubleValue();
//		}
//		if(twistCrossPair2){
			crossPair2Price = cross2Orderbook.getAsks().get(0).getLimitPrice().doubleValue(); //Martktteilnehmer: ETH kaufen für OMG // ich: ETH verkaufen
			crossPair2Volume = cross2Orderbook.getAsks().get(0).getOriginalAmount().doubleValue();
//		} else {
//			crossPair2Price = cross2Orderbook.getAsks().get(0).getLimitPrice().doubleValue();
//			crossPair2Volume = cross2Orderbook.getAsks().get(0).getOriginalAmount().doubleValue();
//		}

		// Calculate cross exchangerate and arbitrage
		double crossExchangeRate;
		crossExchangeRate = crossPair1Price/crossPair2Price;
//		if(twistCrossPair1){
//			crossExchangeRate = (1/crossPair1Price)*crossPair2Price;
//		} else {
//			crossExchangeRate = crossPair1Price*(1/crossPair2Price);
//		}
		double arb = (crossExchangeRate/basePairPrice -1)*100;
		
		// Abitrage info
		LOGGER.info("BID: {} [{}/{}] -- ASK: {} [X {}/{}] -- ARBITRAGE = {}", basePairPrice, basePair.base.toString(), basePair.counter.toString(), String.format("%.8f", crossExchangeRate), crossPair1.base.toString(), crossPair2.base.toString(), arb);

		
		if(debug1){
			// Simulated trading for debugging and testing
			LOGGER.info("=== DEBUG TRADE #1 ===");
			double tradeAmount = 0.0005;
			// Base pair
			double sellAmountBasePair = tradeAmount; //BTC Verkaufen
			double buyAmountBasePair = sellAmountBasePair/basePairPrice;
			LOGGER.info("Sell {}: {} for {}: {}", basePair.base, formatDecimals(sellAmountBasePair), basePair.counter, formatDecimals(buyAmountBasePair));
			
			// Cross pair 1
			double sellAmountCrossPair1;
			double buyAmountCrossPair1 = 0;
			if(twistCrossPair1) {
//				sellAmountCrossPair1 = (sellAmountBasePair/basePairPrice)/crossPair2Price;
//				buyAmountCrossPair1 = sellAmountCrossPair1/crossPair1Price;
//				LOGGER.info("Sell {}: {} for {}: {}", crossPair1.base, formatDecimals(sellAmountCrossPair1), crossPair1.counter, formatDecimals(buyAmountCrossPair1));
			} else {
				sellAmountCrossPair1 = (sellAmountBasePair/basePairPrice)/crossPair2Price; //OMG VERKAUFEN
				buyAmountCrossPair1 = sellAmountCrossPair1*crossPair1Price;
				LOGGER.info("Sell {}: {} for {}: {}", crossPair1.counter, formatDecimals(sellAmountCrossPair1), crossPair1.base, formatDecimals(buyAmountCrossPair1));
			}
			
			// Cross pair 2
			double sellAmountCrossPair2;
			double buyAmountCrossPair2;
			if(twistCrossPair2) {
				sellAmountCrossPair2 = sellAmountBasePair/basePairPrice;  //ETH VERKAUFEN
				buyAmountCrossPair2 = sellAmountCrossPair2/crossPair2Price;
				LOGGER.info("Sell {}: {} for {}: {}", crossPair2.base, formatDecimals(sellAmountCrossPair2), crossPair2.counter, formatDecimals(buyAmountCrossPair2));
			} else {
//				sellAmountCrossPair2 = sellAmountBasePair/basePairPrice;
//				buyAmountCrossPair2 = sellAmountCrossPair2/crossPair2Price;
//				LOGGER.info("Sell {}: {} for {}: {}", crossPair2.counter, formatDecimals(sellAmountCrossPair2), crossPair2.base, formatDecimals(buyAmountCrossPair2));
			}
		

			LOGGER.info("DEBUG ARBITRAGE = {}", (buyAmountCrossPair1/sellAmountBasePair -1)*100);

			// if arbitrage chance exists set return value to true
			if ((arb - 0.75) > 0) {
				ret = true;
			}
			
		} else {
			if ((arb - 0.8) > 0) {
				System.out.println("=====> Arbitrage (with fees): " + String.format("%.8f", arb - 0.75));
				
				double basePairAmount = 0.000525;
//				synchronized(walletProvider) {
//					basePairAmount = getTradeableAmount(basePair.base, MAX_TRADE_AMOUNT, basePairVolume); //BTC
//					
//					double crossPair1Amount = 0;
//					if(twistCrossPair1) {
//						//TODO
//					} else {
//						crossPair1Amount = getTradeableAmount(crossPair1.base, basePairAmount/basePairPrice, crossPair1Volume); //ETH
//						if(basePairAmount > crossPair1Amount*basePairPrice) {
//							basePairAmount = crossPair1Amount*basePairPrice;
//						}
//					}
//					
//					double crossPair2Amount = 0;
//					if(twistCrossPair2) {
//						crossPair2Amount = getTradeableAmount(crossPair2.counter, basePairAmount/crossPair2Price, crossPair2Volume); //OMG
//						if(basePairAmount > crossPair2Amount*crossPair2Price) {
//							basePairAmount = crossPair2Amount*crossPair2Price;
//							crossPair1Amount = basePairAmount/basePairPrice;
//						}
//					} else {
//						//TODO
//					}
//
//				}

				// Set up order for base pair
				double sellAmountBasePair = basePairAmount;
				Callable<String> callable_orderBasePair = () -> {
					String baseTrade;
//					try {
//						baseTrade = exchange.placeLimitOrderAsk(basePair, basePairPrice, sellAmountBasePair);
//					} catch (Exception e) {
//						e.printStackTrace();
						baseTrade = exchange.placeLimitOrderBid(new CurrencyPair(basePair.counter, basePair.base), basePairPrice, (sellAmountBasePair/basePairPrice)*1.0025); // BTC verkaufen bzw ETH kaufen korrekt
//					}
					return baseTrade;
				};
				
				// Set up orders for cross pair 1
				Callable<String> callable_orderCrossPair1;
//				if(twistCrossPair1) {
//					Double sellAmountCrossPair1 = (sellAmountBasePair/basePairPrice)/crossPair2Price; //OMG VERKAUFEN
//					callable_orderCrossPair1 = () -> {
//						String cross1Trade;
//						try {
//							cross1Trade = exchange.placeLimitOrderBid(crossPair1, crossPair1Price, sellAmountCrossPair1);
//						} catch (Exception e) {
//							e.printStackTrace();
//							cross1Trade = exchange.placeLimitOrderAsk(new CurrencyPair(crossPair2.counter, crossPair2.base), crossPair1Price, sellAmountCrossPair1);
//						}
//						return cross1Trade;
//					};	
//				} else {
					double sellAmountCrossPair1 = (sellAmountBasePair/basePairPrice)/crossPair2Price; 
					callable_orderCrossPair1 = () -> {
						String cross1Trade;
//						try {
//							cross1Trade = exchange.placeLimitOrderBid(crossPair1, crossPair1Price, sellAmountCrossPair1); //OMG verkaufen
//						} catch (Exception e) {
//							e.printStackTrace();
							cross1Trade = exchange.placeLimitOrderAsk(new CurrencyPair(crossPair1.counter, crossPair1.base), crossPair1Price, sellAmountCrossPair1); //OMG verkaufen korrekt
//						}
						return cross1Trade;
					};	
//				}

				
				// Set up order for cross pair 2
				Callable<String> callable_orderCrossPair2;
//				if(twistCrossPair2) {
					double sellAmountCrossPair2 = sellAmountBasePair/basePairPrice; 
					callable_orderCrossPair2 = () -> {
						String cross2Trade;
//						try {
//							cross2Trade = exchange.placeLimitOrderAsk(crossPair2, crossPair2Price, sellAmountCrossPair2);
//						} catch (Exception e) {
//							e.printStackTrace();
							cross2Trade = exchange.placeLimitOrderBid(new CurrencyPair(crossPair2.counter, crossPair2.base), crossPair2Price, (sellAmountCrossPair2/crossPair2Price)*1.0025);
//						}
						return cross2Trade;
					};
//				} else {
//					callable_orderCrossPair2 = () -> {
//						String cross2Trade;
//						try {
//							cross2Trade = exchange.placeLimitOrderAsk(crossPair2, crossPair2Price, sellAmountCrossPair2);
//						} catch (Exception e) {
//							e.printStackTrace();
//							cross2Trade = exchange.placeLimitOrderBid(new CurrencyPair(crossPair2.counter, crossPair2.base), crossPair2Price, sellAmountCrossPair2);
//						}
//						return cross2Trade;
//
//					};
//				}
				
				
				// Send orders
				Future<String> future_order1 = networkExecutorService.submit(callable_orderBasePair);
				Future<String> future_order2 = networkExecutorService.submit(callable_orderCrossPair1);
				Future<String> future_order3 = networkExecutorService.submit(callable_orderCrossPair2);

				// Order IDs
				String orderID_trade1 = "";
				String orderID_trade2 = "";
				String orderID_trade3 = "";
				try {
					// Get order IDs
					orderID_trade1 = future_order1.get();
					LOGGER.info("Order1 Placed. ID: {}", orderID_trade1);
					orderID_trade2 = future_order2.get();
					LOGGER.info("Order2 Placed. ID: {}", orderID_trade2);
					orderID_trade3 = future_order3.get();
					LOGGER.info("Order2 Placed. ID: {}", orderID_trade3);
				} catch (Exception e) {
					e.printStackTrace();
					// If something went wrong, try to cancel remaining orders
					boolean cancelledTrade1 = exchange.cancelOrder(orderID_trade1);
					boolean cancelledTrade2 = exchange.cancelOrder(orderID_trade2);
					boolean cancelledTrade3 = exchange.cancelOrder(orderID_trade3);
					System.err.println("Fatal trade error, terminating application.");
//					System.exit(1);
				}
				Thread.sleep(30000);
				updateWallets = true;
				updateWallet();
				System.exit(0);
				ret = true;
			}
		}
		return ret;
	}
	
	public boolean triangularArbitrage2(OrderBook baseOrderBook, OrderBook cross1Orderbook, OrderBook cross2Orderbook) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException, InterruptedException {
		// Return value
		boolean ret = false;
		
		double basePairPrice = baseOrderBook.getBids().get(0).getLimitPrice().doubleValue();
		double basePairVolume = baseOrderBook.getBids().get(0).getOriginalAmount().doubleValue();
		double crossPair1Price;
		double crossPair1Volume;
		double crossPair2Price;
		double crossPair2Volume;
		//TWIST 2
//		if(twistCrossPair1){
//			crossPair1Price = cross1Orderbook.getAsks().get(0).getLimitPrice().doubleValue();
//		} else {
			crossPair1Price = cross1Orderbook.getAsks().get(0).getLimitPrice().doubleValue();
			crossPair1Volume = cross1Orderbook.getAsks().get(0).getOriginalAmount().doubleValue();
//		}
//		if(twistCrossPair2){
			crossPair2Price = cross2Orderbook.getBids().get(0).getLimitPrice().doubleValue();
			crossPair2Volume = cross1Orderbook.getBids().get(0).getOriginalAmount().doubleValue();
//		} else {
//			crossPair2Price = cross2Orderbook.getBids().get(0).getLimitPrice().doubleValue();
//		}
		
		// Calculate cross exchangerate and arbitrage

		double crossExchangeRate;
		crossExchangeRate = crossPair1Price/crossPair2Price;
//		if(twistCrossPair1){
//			crossExchangeRate = (1/crossPair1Price)*crossPair2Price;
//		} else {
//			crossExchangeRate = crossPair1Price*(1/crossPair2Price);
//		}
		double arb = (basePairPrice/crossExchangeRate -1)*100;

		LOGGER.info("ASK: {} [{}/{}] -- BID: {} [X {}/{}] -- ARBITRAGE = {}", basePairPrice, basePair.base.toString(), basePair.counter.toString(), String.format("%.8f", crossExchangeRate), crossPair1.counter.toString(), crossPair2.counter.toString(), arb);

		if(debug2){
			 //Simulated trading for debugging and testing
			LOGGER.info("=== DEBUG TRADE #2 ===");
			double tradeAmount = 1;
			// Base pair
			double sellBasePair = tradeAmount/basePairPrice;
			double buyBasePair = tradeAmount;
			LOGGER.info("Sell {}: {} for {}: {}", basePair.counter, formatDecimals(sellBasePair), basePair.base, formatDecimals(buyBasePair));
			
			// Cross pair 1
			double sellCrossPair1;
			double buyCrossPair1;
//			if(twistCrossPair1) {
//				sellCrossPair1 = tradeAmount;
//				buyCrossPair1 = sellCrossPair1/crossPair1Price;
//				LOGGER.info("Sell {}: {} for {}: {}", crossPair1.counter, formatDecimals(sellCrossPair1), crossPair1.base, formatDecimals(buyCrossPair1));
//			} else {
				sellCrossPair1 = tradeAmount;
				buyCrossPair1 = sellCrossPair1/crossPair1Price;
				LOGGER.info("Sell {}: {} for {}: {}", crossPair1.base, formatDecimals(sellCrossPair1), crossPair1.counter, formatDecimals(buyCrossPair1));
//			}

			// Cross pair 2
			double sellCrossPair2;
			double buyCrossPair2;
//			if(twistCrossPair2) {
				sellCrossPair2 = (tradeAmount/crossPair1Price);
				buyCrossPair2 = sellCrossPair2*crossPair2Price;
				LOGGER.info("Sell {}: {} for {}: {}", crossPair2.counter, formatDecimals(sellCrossPair2), crossPair2.base, formatDecimals(buyCrossPair2));
//			} else {
//				sellCrossPair2 = sellCrossPair1/crossPair1Price;
//				buyCrossPair2 = sellCrossPair2*crossPair2Price;
//				LOGGER.info("Sell {}: {} for {}: {}", crossPair2.base, formatDecimals(sellCrossPair2), crossPair2.counter, formatDecimals(buyCrossPair2));
//			}
			
			LOGGER.info("DEBUG ARBITRAGE = {}", (buyCrossPair2/sellBasePair -1)*100);
			
			if((arb - 0.75) > 0) {
				ret = true;
			}
			
		} else {
		
		if((arb - 0.75) > 0) {
			System.out.println("=====> Arbitrage2 (with fees): " + String.format("%.8f", arb - 0.75));
			
			double tradeAmount = 0.3;
			
			// Trade basePair

			double sellAmountbasePair = tradeAmount*basePairPrice;
			Callable<String> callable_orderBasePair = () -> {
				String baseTrade;
				try {
					baseTrade = exchange.placeLimitOrderBid(basePair, basePairPrice, sellAmountbasePair);
				} catch (Exception e) {
//					e.printStackTrace();
					baseTrade = exchange.placeLimitOrderAsk(new CurrencyPair(basePair.counter, basePair.base), basePairPrice, sellAmountbasePair);
				}
				return baseTrade;
			};
			
			// Trade crossPair1
			double sellAmountCrossPair1 = tradeAmount;
			Callable<String> callable_orderCrossPair1;
			if(twistCrossPair1) {
				callable_orderCrossPair1 = () -> {
					String cross1Trade;
					try {
						cross1Trade = exchange.placeLimitOrderBid(crossPair1, crossPair1Price, sellAmountCrossPair1);
					} catch (Exception e) {
//						e.printStackTrace();
						cross1Trade = exchange.placeLimitOrderAsk(new CurrencyPair(crossPair1.counter, crossPair1.base), crossPair1Price, sellAmountCrossPair1);
					}
					return cross1Trade;
				};
			} else {
				callable_orderCrossPair1 = () -> {
					String cross1Trade;
					try {
						cross1Trade = exchange.placeLimitOrderAsk(crossPair1, crossPair1Price, sellAmountCrossPair1);
					} catch (Exception e) {
//						e.printStackTrace();
						cross1Trade = exchange.placeLimitOrderBid(new CurrencyPair(crossPair1.counter, crossPair1.base), crossPair1Price, sellAmountCrossPair1);
					}
					return cross1Trade;
				};
			}

			// Trade crossPair2
			Callable<String> callable_orderCrossPair2;
			if(twistCrossPair2) {
				double sellAmountCrossPair2 = sellAmountCrossPair1*crossPair1Price;
				callable_orderCrossPair2 = () -> {
					String cross2Trade;
					try {
						cross2Trade = exchange.placeLimitOrderBid(crossPair1, crossPair2Price, sellAmountCrossPair2);
					} catch (Exception e) {
//						e.printStackTrace();
						cross2Trade = exchange.placeLimitOrderAsk(new CurrencyPair(crossPair1.counter, crossPair1.base), crossPair2Price, sellAmountCrossPair2);
					}
					return cross2Trade;
				};
			} else {
				double sellAmountCrossPair2 = sellAmountCrossPair1/crossPair1Price;
				callable_orderCrossPair2 = () -> {
					String cross2Trade;
					try {
						cross2Trade = exchange.placeLimitOrderAsk(crossPair1, crossPair2Price, sellAmountCrossPair2);
					} catch (Exception e) {
//						e.printStackTrace();
						cross2Trade = exchange.placeLimitOrderBid(new CurrencyPair(crossPair1.counter, crossPair1.base), crossPair2Price, sellAmountCrossPair2);
					}
					return cross2Trade;
				};
			}


			Future<String> future_order1 = networkExecutorService.submit(callable_orderBasePair);
			Future<String> future_order2 = networkExecutorService.submit(callable_orderCrossPair1);
			Future<String> future_order3 = networkExecutorService.submit(callable_orderCrossPair2);

			String orderID_trade1 = "";
			String orderID_trade2 = "";
			String orderID_trade3 = "";
			try {
				orderID_trade1 = future_order1.get();
				LOGGER.info("Order1 Placed. ID: {}", orderID_trade1);
				orderID_trade2 = future_order2.get();
				LOGGER.info("Order2 Placed. ID: {}", orderID_trade2);
				orderID_trade3 = future_order3.get();
				LOGGER.info("Order2 Placed. ID: {}", orderID_trade3);

			} catch (Exception e) {
				e.printStackTrace();
//				 boolean exchange1_cancelled =
//				 exchange1.cancelOrder(orderID_exchange1);
//				 boolean exchange2_cancelled =
//				 exchange2.cancelOrder(orderID_exchange1);
				System.err.println("Fatal trade error, terminating application.");
				System.exit(1);
			}
			Thread.sleep(2000);
			updateWallet();
			System.exit(0);
			ret = true;
			}

		}
		return ret;
		
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
		Callable<OrderBook> callable_Orderbook1 = () -> {
			return exchange.fetchOrderbook(basePair);
		};

		Callable<OrderBook> callable_Orderbook2 = () -> {
			return exchange.fetchOrderbook(crossPair1);
		};
		
		Callable<OrderBook> callable_Orderbook3 = () -> {
			return exchange.fetchOrderbook(crossPair2);
		};

		Future<OrderBook> future_Orderbook1 = networkExecutorService.submit(callable_Orderbook1);
		Future<OrderBook> future_Orderbook2 = networkExecutorService.submit(callable_Orderbook2);
		Future<OrderBook> future_Orderbook3 = networkExecutorService.submit(callable_Orderbook3);

		try {
			orderBook1 = future_Orderbook1.get();
//			LOGGER.info("[{}] Orderbook: ask = {}", exchange1, exchange1Orderbook.getAsks().get(0));
//			LOGGER.info("[{}] Orderbook: bid = {}", exchange1, exchange1Orderbook.getBids().get(0));
			orderBook2 = future_Orderbook2.get();
//			LOGGER.info("[{}] Orderbook: ask = {}", exchange2, exchange2Orderbook.getAsks().get(0));
//			LOGGER.info("[{}] Orderbook: bid = {}", exchange2, exchange2Orderbook.getBids().get(0));
			orderBook3 = future_Orderbook3.get();

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

			exchangeWallet = exchange.fetchWallet();
			
			for(Currency currency : getCurrencies()){
				double currencyBalance = exchangeWallet.getBalance(currency).getAvailable().doubleValue();
				
				double old;
				if(balanceMap.containsKey(currency)){
					old = balanceMap.get(currency);
				} else {
					old = currencyBalance;
				}
				balanceMap.put(currency, currencyBalance);
				LOGGER.info("[{}, {}] Balance: {}, Change: {}", exchange, currency, currencyBalance, String.format("%.8f", currencyBalance-old));
			}

			// Kontostände wieder aktuell, deswegen false.
			updateWallets = false;
		}
	}
	
	public Set<Currency> getCurrencies(){
		Set<Currency> currencies = new HashSet<>();
		currencies.add(basePair.base);
		currencies.add(basePair.counter);
		currencies.add(crossPair1.base);
		currencies.add(crossPair1.counter);
		currencies.add(crossPair2.base);
		currencies.add(crossPair2.counter);
		return currencies;
	}
	
	public Set<CurrencyPair> getCurrencyPairs(){
		Set<CurrencyPair> currencyPairs = new HashSet<>();
		currencyPairs.add(basePair);
		currencyPairs.add(crossPair1);
		currencyPairs.add(crossPair2);
		return currencyPairs;		
	}
	
	public CurrencyPair getBasePair() {
		return basePair;
	}

	public CurrencyPair getCrossPair1() {
		return crossPair1;
	}

	public CurrencyPair getCrossPair2() {
		return crossPair2;
	}
	
	public String formatDecimals(double doubleVal) {
		return String.format("%." + exchange.getDecimals() + "f", doubleVal);
	}
	
	/**
	 * Get the tradeableAmount of a currency depending on arbitrageAmount and walletAmount;
	 * @param currency
	 * @param requestedAmount
	 * @param arbitrageAmount
	 * @return
	 */
	private double getTradeableAmount (Currency currency, double requestedAmount, double arbitrageAmount) {
		double tradeAbleAmount = requestedAmount;
		double walletAmount = walletProvider.getBalance(currency);
		if(tradeAbleAmount > walletAmount) {
			tradeAbleAmount = walletAmount;
		}
		if(tradeAbleAmount > arbitrageAmount) {
			tradeAbleAmount = arbitrageAmount;
		}
		return tradeAbleAmount;
	}
}
