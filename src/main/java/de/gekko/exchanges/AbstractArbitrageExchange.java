package de.gekko.exchanges;

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

import de.gekko.enums.ExchangeType;

import org.knowm.xchange.service.account.AccountService;

/**
 * 
 * @author max boilerplate for implementing exchanges
 */
public abstract class AbstractArbitrageExchange {

	protected AccountService accountService;

	private String apiKey;

	protected double baseAmount = 0;
	protected double counterAmount = 0;

	protected CurrencyPair currencyPair;

	private int decimals;
	private Exchange exchange;
	private ExchangeType type;
	private MarketDataService marketDataService;

	private String name;
	private String secretKey;

	private TradeService tradeService;
	private double tradingFee;

	public AbstractArbitrageExchange(ExchangeType type, String apiKey, String secretKey, CurrencyPair currencyPair) {
		this.currencyPair = currencyPair;
		this.type = type;
		this.apiKey = apiKey;
		this.secretKey = secretKey;

		// Exchange Data
		ExchangeSpecification exSpec = new ExchangeSpecification(type.toString());
		exSpec.setApiKey(apiKey);
		exSpec.setSecretKey(secretKey);
		exchange = ExchangeFactory.INSTANCE.createExchange(exSpec);

		// Market Data
		marketDataService = exchange.getMarketDataService();

		// Trade Data
		tradeService = exchange.getTradeService();

		// Account Data
		accountService = exchange.getAccountService();
	}

	public String getApikey() {
		return apiKey;
	}

	public String getSecretkey() {
		return secretKey;
	}

	public ExchangeType getType() {
		return type;
	}

	public void checkBalances() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		Map<Currency, Balance> balances = accountService.getAccountInfo().getWallet().getBalances();
		baseAmount = balances.get(currencyPair.base).getTotal().doubleValue();
		counterAmount = balances.get(currencyPair.counter).getTotal().doubleValue();
	}

	public double getBaseAmount() {
		return baseAmount;
	}

	// public void setBaseAmount(double baseAmount) {
	// this.baseAmount = baseAmount;
	// }

	public double getCounterAmount() {
		return counterAmount;
	}

	// public void setCounterAmount(double counterAmount) {
	// this.counterAmount = counterAmount;
	// }

	public int getDecimals() {
		return decimals;
	}

	public String getName() {
		return name;
	}

	public OrderBook getOrderbook() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		return marketDataService.getOrderBook(currencyPair);
	}

	public double getTradingFee() {
		return tradingFee;
	}

	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}

	public void setTradingFee(double tradingFee) {
		this.tradingFee = tradingFee;
	}

	public String tradeAsk(double askPriceDouble, double askAmountDouble) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		BigDecimal askPrice = BigDecimal.valueOf(askPriceDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.ASK, currencyPair).limitPrice(askPrice)
				.tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}

	public String tradeBid(double askPriceDouble, double askAmountDouble) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		BigDecimal askPrice = BigDecimal.valueOf(askPriceDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, currencyPair).limitPrice(askPrice)
				.tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}

	public CurrencyPair getCurrenyPair() {
		return currencyPair;
	}
}
