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
import org.knowm.xchange.dto.account.Wallet;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.dto.meta.CurrencyPairMetaData;
import org.knowm.xchange.dto.trade.LimitOrder;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.account.AccountService;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.trade.TradeService;

import de.gekko.enums.ExchangeType;

/**
 * TODO: Fehlende Getter/Setter Methoden ergänzen Verstehen was Decimals
 * bedeutet und dann entsprechend implementieren
 * 
 * @author Fabian
 */
public abstract class AbstractExchange {

	protected Exchange exchange;

	protected AccountService accountService;
	protected MarketDataService marketDataService;
	protected TradeService tradeService;

	protected String apiKey;
	protected String secretKey;

	protected Map<CurrencyPair, CurrencyPairMetaData> currencyPairs;
	protected Map<String, Wallet> balances;

	public AbstractExchange(ExchangeType exchangeType, String apiKey, String secretKey) throws NotAvailableFromExchangeException, NotYetImplementedForExchangeException, ExchangeException, IOException {
		/**
		 * Initialisierung der Exchange basierend auf dem ExchangeType mit vollem
		 * API-Zugriff.
		 */
		this.exchange = ExchangeFactory.INSTANCE.createExchangeWithApiKeys(exchangeType.toString(), apiKey, secretKey);

		/**
		 * Initialisierung der 3 Services (Account, Market, Trade)
		 */
		this.accountService = exchange.getAccountService();
		this.marketDataService = exchange.getMarketDataService();
		this.tradeService = exchange.getTradeService();

		/**
		 * Eine Auflistung aller unterstützer Currency Pairs. Die jeweiligen Fees
		 * sollten sich im Element "CurrencyPairMetaData" finden lassen.
		 */
		this.currencyPairs = getCurrencyPairs();

		/**
		 * Eine Auflistung aller unterstützen Wallets mit deren Guthaben
		 */
		this.balances = getBalances();
	}

	public Exchange getExchange() {
		return getExchange();
	}

	public AccountService getAccountService() {
		return this.accountService;
	}

	public MarketDataService getMarketDataService() {
		return this.marketDataService;
	}

	public TradeService getTradeService() {
		return this.tradeService;
	}

	public Map<CurrencyPair, CurrencyPairMetaData> getCurrencyPairs() {
		return this.exchange.getExchangeMetaData().getCurrencyPairs();
	}

	public Map<String, Wallet> getBalances() throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return this.accountService.getAccountInfo().getWallets();
	}

	public OrderBook getOrderbook(CurrencyPair currencyPair) throws NotAvailableFromExchangeException,
			NotYetImplementedForExchangeException, ExchangeException, IOException {
		return this.marketDataService.getOrderBook(currencyPair);
	}
}
