package de.gekko.exchanges;

import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.ExchangeSpecification;
import org.knowm.xchange.btcmarkets.BTCMarketsExchange;

public class BTCMarketsArbitrageExchange extends AbstractArbitrageExchange {
	public BTCMarketsArbitrageExchange(String apiKey, String secretKey) {
		ExchangeSpecification exchangeSpecification = new ExchangeSpecification(BTCMarketsExchange.class.getName());
		exchangeSpecification.setApiKey(apiKey);
		exchangeSpecification.setSecretKey(secretKey);
		setExchange(ExchangeFactory.INSTANCE.createExchange(exchangeSpecification));

		initServices();
	}
}
