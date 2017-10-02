package de.gekko.exchanges;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.dto.trade.MarketOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;

/**
 * @author max boilerplate for implementing exchanges
 */
public abstract class AbstractArbitrageExchange {

	/**
	 * Speichert den AccountService.
	 */
	private AccountService accountService;

	/**
	 * Speichert die Map der CurrencyPairs mit ihren Metadaten. Enthält alle
	 * CurrencyPairs, die auf dem Exhange gehandelt werden. Die Metadaten enthalten
	 * beispielsweise die TradingFees. Hält ebenso Informationen über
	 * Min/Max-Amounts der Exchanges.
	 */
	private Map<CurrencyPair, CurrencyPairMetaData> currencyPairs;

	/**
	 * Speichert die Anzahl Nachkommastellen die bei Trades erlaubt sind.
	 */
	private int decimals = 8;

	/**
	 * Speichert den Exchange.
	 */
	private Exchange exchange;

	/**
	 * Speichert den MarketDataService.
	 */
	private MarketDataService marketDataService;

	/**
	 * Speichert die minimale Menge, die getraded werden muss auf dem Exchange.
	 */
	private double minimumAmount;

	/**
	 * Speichert das OrderBook des Exchanges.
	 */
	private OrderBook orderBook;

	/**
	 * Speichert den Ticker des Exchanges
	 */
	private Ticker ticker;

	/**
	 * Speichert den TradeService.
	 */
	private TradeService tradeService;

	/**
	 * Speichert die TradingFee des Exchanges;
	 */
	private double tradingFee;

	/**
	 * Speichert den Wallet.
	 */
	private Wallet wallet;

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

	/**
	 * Liefert die minimale Menge, die auf dem Exchange getraded werden muss
	 * abhängig vom CurrencyPair.
	 * 
	 * @param currencyPair
	 * @return minimum trading amount. Liefert -1, wenn nicht anwendbar.
	 */
	public double fetchMinimumAmount(CurrencyPair currencyPair) {
		try {
			return currencyPairs.get(currencyPair).getMinimumAmount().doubleValue();
		} catch (NullPointerException npe) {
			// Keine Minimum Amount für Exchange.
			return -1;
		}
	}

	public OrderBook fetchOrderbook(CurrencyPair currencyPair) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return getMarketDataService().getOrderBook(currencyPair);
	}

	public Ticker fetchTicker(CurrencyPair currencyPair) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return getMarketDataService().getTicker(currencyPair);
	}

	/**
	 * Liefert die TradingFees für ein gegebenes CurrencyPair.
	 * 
	 * @param currencyPair
	 * @return die TradingFees für ein gegebenes CurrencyPair.
	 */
	public double fetchTradingFee(CurrencyPair currencyPair) {
		try {
			return currencyPairs.get(currencyPair).getTradingFee().doubleValue();
		} catch (NullPointerException npe) {
			// Keine Fees für Exchange gefunden.
			/**
			 * TODO: Hier einen Fallback einbauen, falls Fees nicht über die API erfragt
			 * werden können: Fees über einen kleinen Trade errechnen.
			 */
			return 0;
		}
	}

	public Wallet fetchWallet() throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException,
			ExchangeException, IOException {
		Map<String, Wallet> mapWallets = getAccountService().getAccountInfo().getWallets();
		if (mapWallets.keySet().size() == 1) {
			// Bisher alle Fälle immer == 1, gebe also erstes Element
			return mapWallets.values().iterator().next();
		} else {
			throw new ExchangeException("More than one Wallet in WalletMap!");
		}
	}

	protected AccountService getAccountService() {
		return accountService;
	}

	public int getDecimals() {
		return decimals;
	}

	protected Exchange getExchange() {
		return exchange;
	}

	protected MarketDataService getMarketDataService() {
		return marketDataService;
	}

	public double getMinimumAmount() {
		return minimumAmount;
	}

	public OrderBook getOrderbook() {
		return orderBook;
	}

	public Ticker getTicker() {
		return ticker;
	}

	public double getTradingFee() {
		return tradingFee;
	}

	public Wallet getWallet() {
		return wallet;
	}

	protected void initServices() {
		marketDataService = exchange.getMarketDataService();
		tradeService = exchange.getTradeService();
		accountService = exchange.getAccountService();

		currencyPairs = exchange.getExchangeMetaData().getCurrencyPairs();
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
	 * Führt eine MarketOrder als ASK aus.
	 * 
	 * @param currencyPair
	 * @param askAmountDouble
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public String placeMarketOrderAsk(CurrencyPair currencyPair, double askAmountDouble)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		BigDecimal askAmount = BigDecimal.valueOf(askAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		return getTradeService().placeMarketOrder(new MarketOrder(OrderType.ASK, askAmount, currencyPair));
	}

	/**
	 * Führt eine MarketOrder als BID aus.
	 * 
	 * @param currencyPair
	 * @param bidAmountDouble
	 * @return
	 * @throws NotAvailableFromExchangeException
	 * @throws NotYetImplementedForExchangeException
	 * @throws ExchangeException
	 * @throws IOException
	 */
	public String placeMarketOrderBid(CurrencyPair currencyPair, double bidAmountDouble)
			throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException,
			IOException {
		BigDecimal askAmount = BigDecimal.valueOf(bidAmountDouble).setScale(decimals, BigDecimal.ROUND_HALF_UP);

		return getTradeService().placeMarketOrder(new MarketOrder(OrderType.BID, askAmount, currencyPair));
	}

	protected TradeService getTradeService() {
		return tradeService;
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

	public void setDecimals(int decimals) {
		this.decimals = decimals;
	}

	protected void setExchange(Exchange exchange) {
		this.exchange = exchange;
	}

	public void setMinimumAmount(double minimumAmount) {
		this.minimumAmount = minimumAmount;
	}

	public void setOrderBook(OrderBook orderBook) {
		this.orderBook = orderBook;
	}

	public void setTicker(Ticker ticker) {
		this.ticker = ticker;
	}

	public void setTradingFee(double tradingFee) {
		this.tradingFee = tradingFee;
	}

	public void setWallet(Wallet wallet) {
		this.wallet = wallet;
	}

	@Override
	public String toString() {
		return exchange.getDefaultExchangeSpecification().getExchangeName();
	}

}
