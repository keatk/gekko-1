package de.gekko.exchanges;

import java.io.IOException;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.bitstamp.BitstampExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.OrderBook;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;

public class BitstampArbitragerExchange extends AbstractArbitrageExchange {

	public BitstampArbitragerExchange(String apiKey, String secretKey, String userName) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BitstampExchange.class.getName());
		exchangeSpecification.setUserName(userName);
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		exchange = ExchangeFactory.INSTANCE.createExchange(exchangeSpecification);

		initServices();
	}

}
