package de.gekko.old;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.account.AccountService;

/**
 * 
 * @author max
 * boilerplate for implementing exchanges
 */
public abstract class Old_AbstractArbitrageExchange {
	
	private Class exchangeClass;
	
	private String name;
	
	private String apiKey;
	private String secretKey;
	
	protected CurrencyPair currencyPair;
	
	private Exchange exchange;
	private MarketDataService marketDataService;
	private TradeService tradeService;
	protected AccountService accountService;
	
	protected double baseAmount = 0;
	protected double counterAmount = 0;
	
	private double tradingFee;
	private int decimals;
	
	public Old_AbstractArbitrageExchange(Class exchangeClass, String name, CurrencyPair currencyPair, String apiKey, String secretKey){
		this.currencyPair = currencyPair;
		this.exchangeClass = exchangeClass;
		this.name = name;
		this.apiKey = apiKey;
		this.secretKey = secretKey;
		initExchange();
		initMarketDataService();
		initTradeService();
		initAccountService();
	}
	
	public String getName(){
		return name;
	}


	public double getBaseAmount() {
		return baseAmount;
	}

//	public void setBaseAmount(double baseAmount) {
//		this.baseAmount = baseAmount;
//	}

	public double getCounterAmount() {
		return counterAmount;
	}

//	public void setCounterAmount(double counterAmount) {
//		this.counterAmount = counterAmount;
//	}

	public double getTradingFee() {
		return tradingFee;
	}

	public void setTradingFee(double tradingFee) {
		this.tradingFee = tradingFee;
	}

	public int getDecimals() {
		return decimals;
	}

	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}

	private void initExchange(){
		ExchangeSpecification exSpec = new ExchangeSpecification(exchangeClass);
		exSpec.setApiKey(apiKey);
		exSpec.setSecretKey(secretKey);

		exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);
	}
	
	private void initMarketDataService(){
		marketDataService = exchange.getMarketDataService();
	}
	
	private void initTradeService(){
		tradeService = exchange.getTradeService();
	}
	
	private void initAccountService(){
		accountService = exchange.getAccountService();
	}
	
	public void checkBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
	    Map<Currency, Balance> balances = accountService.getAccountInfo().getWallet().getBalances();
	    baseAmount = balances.get(currencyPair.base).getTotal().doubleValue();
	    counterAmount = balances.get(currencyPair.counter).getTotal().doubleValue();
	}
	
	public OrderBook getOrderbook() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
		return marketDataService.getOrderBook(currencyPair);
	}
	
	public String tradeAsk(double askPriceDouble, double askAmountDouble) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
		BigDecimal askPrice = BigDecimal.valueOf(askPriceDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		
		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.ASK, currencyPair).limitPrice(askPrice).tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}
	
	public String tradeBid(double askPriceDouble, double askAmountDouble) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException{
		BigDecimal askPrice = BigDecimal.valueOf(askPriceDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		
		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, currencyPair).limitPrice(askPrice).tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}
}
