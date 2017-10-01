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
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;

import de.gekko.enums.ExchangeType;

/**
 * @author max boilerplate for implementing exchanges
 */
public abstract class AbstractArbitrageExchange {

	/**
	 * Speichert den AccountService. (?)
	 */
	protected AccountService accountService;

	/**
	 * Speichert den ApiKey des Exchanges, wird aus Configfile gelesen.
	 */
	private final String apiKey;

	/**
	 * Speichert die Anzahl Nachkommastellen die bei Trades erlaubt sind.
	 */
	private int decimals;

	/**
	 * Speichert den Exchange.
	 */
	private Exchange exchange;

	/**
	 * Speichert den MarketDataService.
	 */
	private MarketDataService marketDataService;

	/**
	 * Speichert den SecretKey des Exchanges, wird aus dem Configfile gelesen.
	 */
	private final String secretKey;

	/**
	 * Speichert den TradeService.
	 */

	private TradeService tradeService;

	/**
	 * Speichert die Tradingfee des Exchanges.
	 */
	private double tradingFee;

	/**
	 * Speichert den Typen. Der Typ bestimmt, welche Unterklasse initialisiert wird.
	 */
	private ExchangeType type;

	public AbstractArbitrageExchange(ExchangeType type, String apiKey, String secretKey, CurrencyPair currencyPair) {
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

	public double checkBalance(Currency currency) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {

		Map<Currency, Balance> balances = accountService.getAccountInfo().getWallet().getBalances();
		return balances.get(currency).getTotal().doubleValue();
	}

	public String getApikey() {
		return apiKey;
	}

	public int getDecimals() {
		return decimals;
	}

	public String getName() {
		return toString();
	}

	public OrderBook getOrderbook(CurrencyPair currencyPair) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return marketDataService.getOrderBook(currencyPair);
	}

	public String getSecretkey() {
		return secretKey;
	}

	public double getTradingFee() {
		return tradingFee;
	}

	public ExchangeType getType() {
		return type;
	}

	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}

	public void setTradingFee(double tradingFee) {
		this.tradingFee = tradingFee;
	}

	/**
	 * Führt Limitorder als Ask aus.
	 * 
	 * @param currencyPair
	 * @param askPriceDouble
	 * @param askAmountDouble
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public String placeLimitOrderAsk(CurrencyPair currencyPair, double askPriceDouble, double askAmountDouble)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		BigDecimal askPrice = BigDecimal.valueOf(askPriceDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.ASK, currencyPair).limitPrice(askPrice)
				.tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}

	/**
	 * Führt Limitorder als Bid aus.
	 * 
	 * @param currencyPair
	 * @param bidPrice
	 * @param bidAmount
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public String placeLimitOrderBid(CurrencyPair currencyPair, double bidPrice, double bidAmount)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		BigDecimal askPrice = BigDecimal.valueOf(bidPrice).setScale(decimals, BigDecimal.ROUND_HALF_UP);
		BigDecimal askAmount = BigDecimal.valueOf(bidAmount).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		LimitOrder limitOrder = new LimitOrder.Builder(OrderType.BID, currencyPair).limitPrice(askPrice)
				.tradableAmount(askAmount).build();
		return tradeService.placeLimitOrder(limitOrder);
	}

	/**
	 * Bricht Order ab.
	 * 
	 * @param orderID
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public boolean cancelOrder(String orderID) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return tradeService.cancelOrder(orderID);
	}
}
