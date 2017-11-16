package de.gekko.websocketNew;

import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;

/**
 * Class to hold updated order book and corresponding currency pair.
 * @author max
 */
public class OrderBookUpdate {
	
	private CurrencyPair currencyPair;
	private OrderBook orderBook;
	
	public OrderBookUpdate(CurrencyPair currencyPair, OrderBook orderBook) {
		this.currencyPair = currencyPair;
		this.orderBook = orderBook;
	}
	
	public CurrencyPair getCurrencyPair() {
		return currencyPair;
	}
	
	public void setCurrencyPair(CurrencyPair currencyPair) {
		this.currencyPair = currencyPair;
	}
	
	public OrderBook getOrderBook() {
		return orderBook;
	}
	
	public void setOrderBook(OrderBook orderBook) {
		this.orderBook = orderBook;
	}
}