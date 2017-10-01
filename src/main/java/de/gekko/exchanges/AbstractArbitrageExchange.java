package de.gekko.exchanges;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order.OrderType;
import org.knowm.xchange.dto.account.Balance;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
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
	protected AccountService accountService;

	/**
	 * Speichert die Anzahl Nachkommastellen die bei Trades erlaubt sind.
	 */
	private int decimals;

	/**
	 * Speichert den Exchange.
	 */
	protected Exchange exchange;

	/**
	 * Speichert den MarketDataService.
	 */
	protected MarketDataService marketDataService;

	/**
	 * Speichert den TradeService.
	 */
	protected TradeService tradeService;

	/**
	 * Speichert die Map der CurrencyPairs mit ihren Metadaten. Enthält alle
	 * CurrencyPairs, die auf dem Exhange gehandelt werden. Die Metadaten enthalten
	 * beispielsweise die TradingFees. Hält ebenso Informationen über
	 * Min/Max-Amounts der Exchanges.
	 */
	protected Map<CurrencyPair, CurrencyPairMetaData> currencyPairs;

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

	public double checkBalance(Currency currency) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		Map<Currency, Balance> balances = accountService.getAccountInfo().getWallet().getBalances();
		return balances.get(currency).getTotal().doubleValue();
	}

	public int getDecimals() {
		return decimals;
	}

	public OrderBook getOrderbook(CurrencyPair currencyPair) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return marketDataService.getOrderBook(currencyPair);
	}

	/**
	 * Liefert die TradingFees für ein gegebenes CurrencyPair.
	 * 
	 * @param currencyPair
	 * @return die TradingFees für ein gegebenes CurrencyPair.
	 */
	public double getTradingFee(CurrencyPair currencyPair) {
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

	/**
	 * Liefert die minimale Menge, die auf dem Exchange getraded werden muss
	 * abhängig vom CurrencyPair.
	 * 
	 * @param currencyPair
	 * @return minimum trading amount. Liefert -1, wenn nicht anwendbar.
	 */
	public double getMinimumAmount(CurrencyPair currencyPair) {
		try {
			return currencyPairs.get(currencyPair).getMinimumAmount().doubleValue();
		} catch (NullPointerException npe) {
			// Keine Minimum Amount für Exchange.
			return -1;
		}
	}

	/**
	 * Liefert die minimale Menge, die auf dem Exchange getraded werden muss
	 * abhängig vom CurrencyPair.
	 * 
	 * @param currencyPair
	 * @return maximum trading amount. Liefert -1, wenn nicht anwendbar.
	 */
	public double getMaximumAmount(CurrencyPair currencyPair) {
		try {
			return currencyPairs.get(currencyPair).getMaximumAmount().doubleValue();
		} catch (NullPointerException npe) {
			// Keine Maximum Amount für Exchange.
			return -1;
		}
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

	@Override
	public String toString() {
		return exchange.getDefaultExchangeSpecification().getExchangeName();
	}
}
